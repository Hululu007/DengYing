package com.dengying.player.ui.viewmodel

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.net.NetPriority
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.core.CmsSources
import com.dengying.player.ui.UiDeps
import com.dengying.player.core.DiscoverItem
import com.dengying.player.core.DoubanRankingTag
import com.dengying.player.core.VideoItem
import com.dengying.player.data.source.discover.DiscoverAdapter
import com.dengying.player.domain.page.ListBatch
import com.dengying.player.domain.search.DiscoverRules
import com.dengying.player.domain.search.TitleKey
import com.dengying.player.player.PlaybackProbe
import kotlinx.coroutines.launch

/**
 * 首页 —— 分类浏览 + 视频网格（FR-301/302/303/304/315）
 *
 * 与鸿蒙版的对应：
 *  - 请求序号 `reqSeq` 丢弃过期响应（切分类时旧请求回来不覆盖新列表）
 *  - 上拉翻页失败不推进页码
 *  - 追加时按 TitleKey 去重（与搜索、分类路径同一函数）
 *
 * 【按分类分槽】横向翻页（HorizontalPager）会把相邻分类**同时组合**，
 * 若仍只存一份 `items`，滑过去的目标页必然是空白。故状态改成 `cats: Map<分类下标, CatState>`，
 * 每一页各读自己那一份；顺带「切走再切回」也不再需要重新加载。
 * 请求序号也随之改成**按分类**计数（`seqByCat`）：A 页刷新不该作废 B 页的在途请求。
 */
class IndexViewModel(
    private val repo: SourceRepository,
    private val cache: PageCache,
    private val detailCache: LruCache<String, VideoItem>,
) : ViewModel() {

    /** 预取详情时最多取多少张卡片：再往后用户很少点，带宽反而拖慢网格本身 */
    private val PREFETCH_DETAIL_MAX = 6

    /** 每个分类当前在跑的预取协程，后台刷新换片单时要能停掉上一轮 */
    private val prefetchJobs = HashMap<Int, Job>()

    /** 单个分类的内容状态。 */
    data class CatState(
        val items: List<VideoItem> = emptyList(),
        /** 默认 true：还没发过请求的分类该显示加载态，而不是先闪一下「暂无内容」 */
        val loading: Boolean = true,
        val errMsg: String = "",
        val sourceName: String = "",
        val page: Int = 1,
        val hasMore: Boolean = true,
        val loadingMore: Boolean = false,
        val refining: Boolean = false,
        /**
         * 该分类的豆瓣榜单（发现层）。
         *
         * 与 [items] 刻意分开：发现层条目是**站外元数据**，点进去无法在本应用内播放，
         * 只能当「搜这个词」的入口。混进 `items` 会让网格里出现点不动或跳站外的卡片。
         */
        val ranking: List<DiscoverItem> = emptyList(),
        /** 是否已发起过首次加载（见 [onShown]） */
        val started: Boolean = false,
    )

    data class UiState(
        val categories: List<String> = CmsSources.HOME_CATEGORIES,
        val currentTab: Int = 0,
        val cats: Map<Int, CatState> = emptyMap(),
    ) {
        fun cat(idx: Int): CatState = cats[idx] ?: CatState()
    }

    var state by mutableStateOf(UiState())
        private set

    /** 每个分类各自的请求序号：只作废**同一分类**的过期响应 */
    private val seqByCat = HashMap<Int, Int>()

    private fun beginReq(idx: Int): Int {
        val next = (seqByCat[idx] ?: 0) + 1
        seqByCat[idx] = next
        return next
    }

    private fun isLatest(idx: Int, seq: Int): Boolean = seqByCat[idx] == seq

    private fun setCat(idx: Int, f: (CatState) -> CatState) {
        state = state.copy(cats = state.cats + (idx to f(state.cat(idx))))
    }

    /**
     * 每个分类的豆瓣榜单结果（成败都记，口径见 [loadRanking]）。
     *
     * 【必须在任何使用它的初始化之前声明】Kotlin 按**声明顺序**执行属性初始化器与 init 块，
     * 写在使用点之后会拿到 null，真机直接 NPE（`HashMap.containsKey` on null）。
     */
    private val rankingByCat = HashMap<String, List<DiscoverItem>>()

    /**
     * 某一页真正呈现给用户时调用（Pager 停稳、或 tab 被点）。
     *
     * 【为什么不在构造时一次性加载】横向预组合会把相邻页也组合出来，
     * 若在页面组合时无条件加载，用户只看一个分类却会为左右各一个分类发请求。
     */
    fun onShown(idx: Int) {
        if (idx != state.currentTab) state = state.copy(currentTab = idx)
        if (!state.cat(idx).started) loadData(idx)
        loadRanking(idx)
    }

    /**
     * 拉某分类的豆瓣榜单（发现层）。
     *
     * 【为什么按分类缓存】六个分类各对应豆瓣一个榜单（映射见 `DoubanRankingTag`）。
     * 但豆瓣 `search_subjects` 端点对同 IP 的连续请求很敏感（实测间隔 15–20 秒才
     * 稳定），用户来回切 tab 会连发请求。故每个分类**只拉一次**，结果存在
     * [rankingByCat] 里，切回来直接复用、不再发请求。
     *
     * 【为什么不阻塞首屏】它是一次站外请求，且随时可能被 IP 风控拒绝。首屏的
     * 关键路径不该等它，故只在后台跑，失败就什么都不显示。
     *
     * 【为什么失败也记下来】与旧版 `rankingLoaded` 同一口径：成败都算「已经试过」，
     * 不在用户每次切 tab 时重试——豆瓣风控是 IP 级的，短时间重试只会加深限制。
     */
    private fun loadRanking(idx: Int) {
        val cat = state.categories[idx]
        // 已拉过（无论成败）：直接复用，不再发请求
        if (rankingByCat.containsKey(cat)) {
            setCat(idx) { it.copy(ranking = rankingByCat[cat] ?: emptyList()) }
            return
        }
        val m = DoubanRankingTag.of(cat)
        if (m == null) {
            // 未知分类：不显示榜单，也不发请求（猜一个错的比不显示更糟）
            rankingByCat[cat] = emptyList()
            setCat(idx) { it.copy(ranking = emptyList()) }
            return
        }
        viewModelScope.launch {
            val feed = try {
                DiscoverAdapter.doubanRanking(m.first, m.second, 0)
            } catch (e: CancellationException) {
                // 页面销毁导致的取消：不算「已经试过」，下次进来还要再来一次
                throw e
            } catch (e: Exception) {
                null
            }
            val shown = if (feed != null && feed.hasData) {
                PlaybackProbe.note(
                    "index-ranking",
                    "cat=$cat type=${m.first} tag=${m.second} " +
                        "n=${feed.items.size} shown=${DiscoverRules.rankingItems(feed).size}",
                )
                DiscoverRules.rankingItems(feed)
            } else {
                // 风控/失败都静默：首页不该为一条「猜你喜欢」弹出错误
                PlaybackProbe.note(
                    "index-ranking-skip",
                    "cat=$cat type=${m.first} tag=${m.second} err=${feed?.err ?: "exception"}",
                )
                emptyList()
            }
            rankingByCat[cat] = shown
            setCat(idx) { it.copy(ranking = shown) }
        }
    }

    /**
     * 预取首屏卡片的详情，把详情页的等待挪到浏览网格的时候。
     *
     * 【为什么值得做】真机 `net-split` 探针实测：详情请求 1.2~2.6s，其中
     * `read` 只有 0~400ms，**几乎全部花在 `conn`（建连）**——设备走代理，
     * 每个新连接要付 1.2~1.4s。这段时间应用侧无法再压缩，只能**提前付**：
     * 用户还在看网格时就把详情拉回来放进 `detailCache`，点进去即命中（实测 1ms）。
     *
     * 【只取前 N 张】第 7 张之后用户很少点，预取的带宽反而会拖慢网格本身。
     * 【Background 优先级】走 [NetPriority.Background]，只占 4/6 个出网空位，
     * 前台请求（列表、搜索、用户真正点的详情）永远有 2 个空位可用。
     */
    private fun prefetchDetails(idx: Int, items: List<VideoItem>) {
        val targets = items.asSequence()
            .filter { it._source != null && it.vod_id > 0 }
            .filter { detailCache.get(UiDeps.detailCacheKey(it._source!!, it.vod_id)) == null }
            .take(PREFETCH_DETAIL_MAX)
            .toList()
        if (targets.isEmpty()) return
        // 后台刷新换了片单会再调一次本函数：必须先停掉上一轮，否则两轮并行 = 12 个
        // 后台请求抢同一份带宽，恰好拖慢用户正要点的那个详情。
        prefetchJobs[idx]?.cancel()
        prefetchJobs[idx] = viewModelScope.launch(NetPriority.Background) {
            for (it in targets) {
                val src = CmsSources.byKey(it._source ?: continue) ?: continue
                if (!src.enabled) continue
                try {
                    val t0 = com.dengying.player.platform.Clock.nowMs()
                    val d = repo.fetchDetail(src, it.vod_id, it.vod_uri ?: "")
                    if (d != null) detailCache.put(UiDeps.detailCacheKey(src.key, it.vod_id), d)
                    PlaybackProbe.note(
                        "detail-prefetch",
                        "key=${src.key}/${it.vod_id} ok=${d != null}" +
                            " elapsed=${com.dengying.player.platform.Clock.nowMs() - t0}",
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 预取失败无所谓：用户点进去时详情页会自己再拉一次
                }
            }
        }
    }

    /** 重新加载某分类第一页 */
    fun loadData(idx: Int) {
        val seq = beginReq(idx)
        val cat = state.categories[idx]
        setCat(idx) { it.copy(started = true) }

        // 【FR-701 stale-while-revalidate：有缓存就先给内容，用户零等待】
        //
        // 初版把"陈旧"与"失效"合一（超过 30 分钟完全不命中），于是隔久一点进来
        // 又要等 4-8 秒——真机探针抓到过 `[index-load] elapsed=3786`。
        //
        // 现在：保留期（24 小时）内一律命中并立即显示，**同时**后台拉新内容，
        // 真的变了才替换不闪、不跳）。于是"隔多久进来"都接近零等待。
        val cached = cache.get(cat, 1)
        if (cached != null && cached.isNotEmpty()) {
            val stale = cache.isStale(cat, 1)
            PlaybackProbe.note("index-cache-hit", "cat=$cat page=1 n=${cached.size} stale=$stale")
            setCat(idx) {
                it.copy(
                    items = cached,
                    loading = false,
                    errMsg = "",
                    page = 1,
                    // 缓存里没有 pageCount 信息，保守地允许翻页（翻页失败会自己收敛）
                    hasMore = true,
                    loadingMore = false,
                )
            }
            // 无论陈旧与否都后台刷新——反正不阻塞用户，
            // 且"内容变了"是唯一值得替换的条件（见 refreshInBackground）
            refreshInBackground(idx, seq, cat)
            prefetchDetails(idx, cached)
            return
        }

        setCat(idx) {
            it.copy(
                loading = true,
                errMsg = "",
                page = 1,
                hasMore = true,        // 从第一页重新开始（防旧的 hasMore=false 卡死新分页）
                loadingMore = false,
            )
        }
        viewModelScope.launch {
            try {
                val t0 = com.dengying.player.platform.Clock.nowMs()
                // 【首批即渲染】把"够填满首屏"的那一小批先交出去，
                // 用户不必等全部子分类拉完（判据见 domain/ListBatch）。
                // 回调运行在同一协程里，故 seq 校验对两批都有效。
                var firstBatchAt = 0L
                val res = repo.fetchByCategory(repo.allSources(), cat, 1, onFirstBatch = { partial ->
                    if (!isLatest(idx, seq) || partial.size < ListBatch.FIRST_BATCH_MIN) {
                        return@fetchByCategory
                    }
                    firstBatchAt = com.dengying.player.platform.Clock.nowMs()
                    PlaybackProbe.note(
                        "index-first-batch",
                        "cat=$cat n=${partial.size} elapsed=${firstBatchAt - t0}",
                    )
                    setCat(idx) { it.copy(items = partial, loading = false, errMsg = "") }
                })
                // 丢弃过期响应（同一分类又发起了更新的请求）
                if (!isLatest(idx, seq)) return@launch
                PlaybackProbe.note(
                    "index-load",
                    "cat=$cat elapsed=${com.dengying.player.platform.Clock.nowMs() - t0}" +
                        " firstBatch=${if (firstBatchAt > 0) firstBatchAt - t0 else -1}" +
                        " n=${res.items.size} ok=${res.hasData}",
                )

                if (res.hasData) {
                    setCat(idx) {
                        it.copy(
                            items = res.items,
                            sourceName = res.sourceName,
                            hasMore = 1 < res.pageCount,
                            loading = false,
                        )
                    }
                    // 写入缓存供下次命中
                    cache.put(cat, 1, res.items)
                    prefetchDetails(idx, res.items)
                } else {
                    // FR-108：无数据不是失败，但首页该分类确实没内容时也要说清楚
                    setCat(idx) {
                        it.copy(
                            items = emptyList(),
                            hasMore = false,
                            errMsg = res.err ?: "该分类暂无内容",
                            loading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                if (isLatest(idx, seq)) {
                    // 【分类而不是一律"网络异常"】初版这里把所有异常都说成网络问题，
                    // 而实际撞到的是"DengYingApp 未初始化"这类编程错误——
                    // 提示方向完全错，排查绕了远路。装配/状态错误必须与网络故障分开。
                    val msg = if (e is IllegalStateException) {
                        "应用初始化失败，请重启应用（${e.message}）"
                    } else {
                        "网络异常，请检查连接"
                    }
                    com.dengying.player.platform.Log.w("DY/Index", "loadData failed: ${e::class.simpleName ?: "Exception"}: ${e.message}", e)
                    setCat(idx) { it.copy(items = emptyList(), errMsg = msg, loading = false) }
                }
            }
        }
    }

    /**
     * 缓存命中后的后台静默刷新（FR-701 的 stale-while-revalidate）。
     *
     * **不改 loading 状态**——用户已经在看缓存内容了，界面上不该再出现转圈。
     *
     * 【替换条件】内容真的变了才替换。用 `vod_id` 序列比较：
     * 片单顺序或成员变化都算"变了"。这样正常情况（内容没变）完全不重组，
     * 用户的滚动位置不会被打断。
     */
    private fun refreshInBackground(idx: Int, seq: Int, cat: String) {
        viewModelScope.launch {
            try {
                val res = repo.fetchByCategory(repo.allSources(), cat, 1)
                if (!isLatest(idx, seq)) return@launch
                if (!res.hasData || res.items.isEmpty()) return@launch
                val changed = res.items.map { it.vod_id } != state.cat(idx).items.map { it.vod_id }
                PlaybackProbe.note("index-bg-refresh", "cat=$cat changed=$changed")
                if (changed) {
                    setCat(idx) {
                        it.copy(
                            items = res.items,
                            sourceName = res.sourceName,
                            hasMore = 1 < res.pageCount,
                        )
                    }
                    cache.put(cat, 1, res.items)
                } else {
                    // 内容没变也要**刷新时间戳**，否则每次进来都会重复拉一次
                    cache.touch(cat, 1)
                }
                // 后台刷新可能换了片单顺序（真机 `index-bg-refresh changed=true` 很常见），
                // 此时用户眼前的第一屏已经不是 [loadData] 预取的那 6 张了——必须按新列表再预取一次，
                // 否则「点了第一张恰好没预取到」。
                prefetchDetails(idx, state.cat(idx).items)
            } catch (e: Exception) {
                // 静默失败：用户已有内容可看，不必打扰
                PlaybackProbe.note("index-bg-refresh-failed", e::class.simpleName ?: "Exception")
            }
        }
    }

    /** 下拉刷新某分类：清缓存后重新加载（FR-103 + FR-701 的强制失效） */
    fun refresh(idx: Int) {
        // 防手抖重复触发：已经在刷就别再发一轮
        if (state.cat(idx).refining) return
        val seq = beginReq(idx)
        val cat = state.categories[idx]
        setCat(idx) { it.copy(refining = true, errMsg = "") }
        viewModelScope.launch {
            // 下拉刷新是用户显式要求"拿新内容" → 缓存必须失效，
            // 否则会命中刚写的缓存、表现成"刷新了但没变化"
            cache.invalidateAll()
            runCatching { repo.clearClassCache() }
            try {
                val t0 = com.dengying.player.platform.Clock.nowMs()
                // 【首批即上屏，与 loadData 同一判据】下拉刷新原本要等所有源返回
                // 才替换内容——真机实测 5560 ms，而同一分类冷加载（走同一
                // fetchByCategory）只要 2219 ms。差值不在网络，在于前者不等首批。
                // 刷新还多了 clearClassCache，源站全量重取，慢得更明显。
                var firstBatchAt = 0L
                val res = repo.fetchByCategory(repo.allSources(), cat, 1, onFirstBatch = { partial ->
                    if (!isLatest(idx, seq) || partial.size < ListBatch.FIRST_BATCH_MIN) {
                        return@fetchByCategory
                    }
                    firstBatchAt = com.dengying.player.platform.Clock.nowMs()
                    PlaybackProbe.note(
                        "index-refresh-first",
                        "cat=$cat n=${partial.size} elapsed=${firstBatchAt - t0}",
                    )
                    // 【收起加载条】用户要的是「刷新完成」这个信号，不是「整页拉完」。
                    // 首批够填首屏就先撤加载条，剩余条目继续在后台并入。
                    setCat(idx) {
                        it.copy(
                            items = partial,
                            refining = false,
                            loading = false,
                            errMsg = "",
                            page = 1,
                            loadingMore = false,
                        )
                    }
                })
                if (!isLatest(idx, seq)) return@launch
                PlaybackProbe.note(
                    "index-refresh",
                    "cat=$cat elapsed=${com.dengying.player.platform.Clock.nowMs() - t0}" +
                        " firstBatch=${if (firstBatchAt > 0) firstBatchAt - t0 else -1}" +
                        " n=${res.items.size}",
                )
                if (res.hasData) {
                    setCat(idx) {
                        it.copy(
                            items = res.items,
                            sourceName = res.sourceName,
                            hasMore = 1 < res.pageCount,
                            // 【必须把页码归 1】刷新后内容是**第一页**，若沿用刷新前的
                            // `page`（比如用户已翻到第 3 页），下一次 `loadMore()`
                            // 会去拉第 4 页 —— 第 2、3 页被永久跳过，且与当前列表
                            // 内容不连续。这是刷新逻辑最容易漏的一处状态复位。
                            page = 1,
                            // 刷新期间在途的翻页请求已被 seq 作废，锁要一起放开，
                            // 否则"上拉加载"会一直卡在 loadingMore=true 上不再触发。
                            loadingMore = false,
                            loading = false,
                        )
                    }
                    cache.put(cat, 1, res.items)
                } else {
                    setCat(idx) {
                        it.copy(
                            errMsg = res.err ?: "该分类暂无内容",
                            page = 1,
                            loadingMore = false,
                            loading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                if (isLatest(idx, seq)) {
                    setCat(idx) {
                        it.copy(
                            errMsg = if (e is IllegalStateException) "应用初始化失败，请重启应用"
                            else "网络异常，请检查连接",
                        )
                    }
                }
            } finally {
                // 无论成败都收起加载条，否则它会一直转
                if (isLatest(idx, seq)) setCat(idx) { it.copy(refining = false) }
            }
        }
    }

    /** 点 tab / 滑到某一页：只记当前下标，加载由 [onShown] 负责 */
    fun selectTab(idx: Int) {
        if (idx == state.currentTab) return
        state = state.copy(currentTab = idx)
    }

    /** 加载某分类的下一页（带并发锁，防止重复触发） */
    fun loadMore(idx: Int) {
        val s = state.cat(idx)
        if (s.loading || s.loadingMore || !s.hasMore) return
        val seq = beginReq(idx)
        val cat = state.categories[idx]
        val nextPage = s.page + 1
        setCat(idx) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val t0 = com.dengying.player.platform.Clock.nowMs()
                // 【首批即上屏】翻页同理：子分类一个个落地，凑够门槛就先并进列表，
                // 用户不必等最后一个子分类返回才看到新内容。
                // 回调与 `res` 在**同一协程**里，故 seq 校验对两批都有效；
                // 也在同一线程，`state` 的读写不构成竞态。
                var moreFirstBatchAt = 0L
                val res = repo.fetchByCategory(repo.allSources(), cat, nextPage, onFirstBatch = { partial ->
                    if (!isLatest(idx, seq) || partial.size < ListBatch.FIRST_BATCH_MIN) {
                        return@fetchByCategory
                    }
                    moreFirstBatchAt = com.dengying.player.platform.Clock.nowMs()
                    PlaybackProbe.note(
                        "index-more-first",
                        "cat=$cat page=$nextPage n=${partial.size} " +
                            "elapsed=${moreFirstBatchAt - t0}",
                    )
                    val cur = state.cat(idx).items
                    val exist = HashSet<String>()
                    for (it in cur) exist.add(TitleKey.ofRaw(it.vod_name, it.vod_year))
                    val merged = cur.toMutableList()
                    for (it in partial) {
                        val k = TitleKey.ofRaw(it.vod_name, it.vod_year)
                        if (exist.add(k)) merged.add(it)
                    }
                    setCat(idx) { it.copy(items = merged) }
                })
                // 同一分类又发起了新请求则丢弃
                if (!isLatest(idx, seq)) return@launch
                PlaybackProbe.note(
                    "index-more",
                    "cat=$cat page=$nextPage elapsed=${com.dengying.player.platform.Clock.nowMs() - t0} " +
                        "n=${res.items.size} pageCount=${res.pageCount}",
                )

                if (res.hasData && res.items.isNotEmpty()) {
                    // FR-304：与搜索、分类路径共用同一折叠键实现
                    val cur = state.cat(idx).items
                    val exist = HashSet<String>()
                    for (it in cur) exist.add(TitleKey.ofRaw(it.vod_name, it.vod_year))
                    val merged = cur.toMutableList()
                    for (it in res.items) {
                        val k = TitleKey.ofRaw(it.vod_name, it.vod_year)
                        if (exist.add(k)) merged.add(it)
                    }
                    setCat(idx) {
                        it.copy(
                            items = merged,
                            page = nextPage,
                            hasMore = nextPage < res.pageCount,
                        )
                    }
                    // 翻页结果也写缓存（用户要求"每一页都要"）
                    cache.put(cat, nextPage, res.items)
                } else {
                    // 请求失败或没数据：不推进页码，允许重试
                    setCat(idx) { it.copy(hasMore = false) }
                }
            } catch (e: Exception) {
                // 失败不推进页码
            } finally {
                setCat(idx) { it.copy(loadingMore = false) }
            }
        }
    }
}
