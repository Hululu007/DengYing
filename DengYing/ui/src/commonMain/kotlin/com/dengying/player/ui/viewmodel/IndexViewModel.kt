package com.dengying.player.ui.viewmodel

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.net.NetPriority
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.core.CmsSource
import com.dengying.player.core.CmsSources
import com.dengying.player.core.HomeSection
import com.dengying.player.core.HomeTab
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
        /**
         * 「推荐」页当前 tab 的版块流（天翼 jsondata，FR-612）。
         * 其余分类恒为空表——版块流与网格 [items] 语义不同（栏目横滑条 vs 全量网格），
         * 混用会让空态/加载态判断互相踩。
         */
        val sections: List<HomeSection> = emptyList(),
        /** 「推荐」页顶部的内容导航 tab（5G专区/推荐/电影…），其余分类恒为空表 */
        val homeTabs: List<HomeTab> = emptyList(),
        /** 「推荐」页当前选中的 tab path；空串 = 尚未选中 */
        val homeTabPath: String = "",
    )

    data class UiState(
        // 「推荐」置顶：天翼运营版块流是"不知道看什么"的主入口，类主流 App 的精选页；
        // 其余六个聚合分类照旧（FR-301 分类不变，只增不改）
        val categories: List<String> = listOf(RECOMMEND) + CmsSources.HOME_CATEGORIES,
        val currentTab: Int = 0,
        val cats: Map<Int, CatState> = emptyMap(),
    ) {
        fun cat(idx: Int): CatState = cats[idx] ?: CatState()
    }

    var state by mutableStateOf(UiState())
        private set

    companion object {
        /** 首页第一 tab：天翼推荐版块流（见 [loadRecommend]） */
        const val RECOMMEND = "推荐"
    }

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
     * 推荐页各 tab 的版块流（key = "源key|path"，成败都记，口径同 [rankingByCat]）。
     *
     * 【为什么 VM 侧也存一份】仓库层的 TTL 缓存兜网络，这份兜「切回来不用重组」——
     * 用户在推荐页来回切 tab 时直接命中，不发请求也不闪加载态。
     * 声明顺序同 [rankingByCat] 的教训：必须先于任何使用点的初始化。
     */
    private val sectionsByPath = HashMap<String, List<HomeSection>>()

    /** 推荐页的天翼源；未启用时返回 null，由调用方给引导文案而不是空白页 */
    private fun tv189Source() = CmsSources.byKey("tv189")?.takeIf { it.enabled }

    /**
     * 推荐页首选首页源。
     *
     * 首选「蓝光4k」（ah_ap2233）：它的首页由 AhHomeAdapter 按分类现场合成，
     * 封面与年份完整、版块结构接近 AH 原版。该源未启用或合成失败时，
     * [fetchHomeFeed] 自动回落到天翼视讯的 jsondata。
     */
    private fun homeSource() = CmsSources.byKey("ah_ap2233")?.takeIf { it.enabled && it.caps.home }
        ?: tv189Source()

    private data class HomeFeed(
        val source: CmsSource,
        val tabs: List<HomeTab>,
        val tab: HomeTab,
        val sections: List<HomeSection>,
    )

    /**
     * 拉推荐页首页内容，带一次跨源回落。
     *
     * 主源 tabs 为空时继续试天翼；有 tabs 但没版块时也继续试天翼，
     * 只有两边都拿不到任何 tabs 才返回 null。返回值里的 source 是实际生效的源，
     * 后续缓存 key 与详情预取都以它为准。
     */
    private suspend fun fetchHomeFeed(
        primary: CmsSource,
        force: Boolean = false,
        reason: String = "cold",
        onPartial: (List<HomeSection>) -> Unit = {},
    ): HomeFeed? {
        var last: HomeFeed? = null
        val start = com.dengying.player.platform.Clock.nowMs()
        var attempt = 0
        for (src in listOfNotNull(primary, tv189Source()).distinctBy { it.key }) {
            attempt++
            val tabsAt = com.dengying.player.platform.Clock.nowMs()
            val tabs = repo.fetchHomeTabs(src, force = force)
            val tabsMs = com.dengying.player.platform.Clock.nowMs() - tabsAt
            if (tabs.isEmpty()) {
                PlaybackProbe.note(
                    "index-home-stage",
                    "reason=$reason src=${src.key} attempt=$attempt tabs_ms=$tabsMs skip=tabs-empty",
                )
                continue
            }
            val tab = tabs.firstOrNull { it.name == "推荐" } ?: tabs.first()
            val sectionsAt = com.dengying.player.platform.Clock.nowMs()
            val sections = repo.fetchHomeSections(src, tab.path, force = force, onPartial = onPartial)
            val sectionsMs = com.dengying.player.platform.Clock.nowMs() - sectionsAt
            val feed = HomeFeed(src, tabs, tab, sections)
            last = feed
            PlaybackProbe.note(
                "index-home-stage",
                "reason=$reason src=${src.key} attempt=$attempt tabs_ms=$tabsMs " +
                    "sections_ms=$sectionsMs sections=${sections.size} " +
                    "elapsed=${com.dengying.player.platform.Clock.nowMs() - start}",
            )
            if (sections.isNotEmpty()) return feed
        }
        PlaybackProbe.note(
            "index-home-fallback",
            "reason=$reason attempts=$attempt result=${if (last == null) "none" else "empty"} " +
                "elapsed=${com.dengying.player.platform.Clock.nowMs() - start}",
        )
        return last
    }

    /**
     * 某一页真正呈现给用户时调用（Pager 停稳、或 tab 被点）。
     *
     * 【为什么不在构造时一次性加载】横向预组合会把相邻页也组合出来，
     * 若在页面组合时无条件加载，用户只看一个分类却会为左右各一个分类发请求。
     */
    fun onShown(idx: Int) {
        val at = com.dengying.player.platform.Clock.nowMs()
        val wasStarted = state.cat(idx).started
        if (idx != state.currentTab) state = state.copy(currentTab = idx)
        if (!wasStarted) loadData(idx)
        loadRanking(idx)
        if (wasStarted && state.categories.getOrNull(idx) == RECOMMEND) {
            PlaybackProbe.note(
                "index-home-shown",
                "sections=${state.cat(idx).sections.size} " +
                    "ms=${com.dengying.player.platform.Clock.nowMs() - at}",
            )
        }
    }

    /**
     * 发现页混合片池的燃料：把六个内容分类后台拉起来（已启动的跳过）。
     *
     * 【为什么】发现页片池主体是分类聚合条目（多源混合、直链几乎必播），
     * tv189 版块只作辅料。用户冷启直奔发现页时这些分类还没加载，池子会
     * 退化成只剩 tv189 辅料（VIP 独播多、失败率高）——这里把它们全部预热。
     * 各分类本就有槽位缓存 + PageCache，重复调用无副作用。
     */
    fun warmAllCategories() {
        for (i in 1 until state.categories.size) {
            if (!state.cat(i).started) loadData(i)
        }
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
        val cat = state.categories[idx]
        // 推荐页不走聚合分类管线（无 PageCache/豆瓣榜/翻页），见 [loadRecommend]
        if (cat == RECOMMEND) {
            loadRecommend(idx)
            return
        }
        val seq = beginReq(idx)
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
        // 推荐页的刷新语义不同（无 PageCache 可失效），单走一条管线
        if (state.categories[idx] == RECOMMEND) {
            refreshRecommend(idx)
            return
        }
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

    /**
     * 推荐页首载：拉导航 tab → 默认选「推荐」→ 拉该 tab 的版块流（FR-612）。
     *
     * 【为什么默认选「推荐」】导航 9 个 tab（滤外链后 8 个）里 5G专区是运营活动位，
     * 内容最单薄；「推荐」才是版块流最全的首页（12 模块）。按名称匹配，
     * 运营改顺序也不受影响；匹配不到取首个。
     *
     * 【为什么没有磁盘缓存】版块流是运营位数据（轮播图/推荐位），过期重拉成本低，
     * 磁盘缓存收益小（与仓库层注释同一结论）。内存两级：仓库 TTL + [sectionsByPath]。
     */
    private fun loadRecommend(idx: Int) {
        val seq = beginReq(idx)
        val src = homeSource()
        if (src == null) {
            setCat(idx) {
                it.copy(
                    started = true,
                    loading = false,
                    hasMore = false,
                    errMsg = "推荐内容需要「蓝光4k」或「天翼视讯」源，当前都未启用——可在「源状态」里开启",
                )
            }
            return
        }
        setCat(idx) { it.copy(started = true, loading = true, errMsg = "") }
        viewModelScope.launch {
            try {
                val t0 = com.dengying.player.platform.Clock.nowMs()
                val feed = fetchHomeFeed(src, reason = "cold", onPartial = { early ->
                    if (isLatest(idx, seq) && early.isNotEmpty()) {
                        PlaybackProbe.note(
                            "index-home-partial",
                            "reason=cold sections=${early.size} cards=${early.sumOf { it.items.size }} " +
                                "elapsed=${com.dengying.player.platform.Clock.nowMs() - t0}",
                        )
                        setCat(idx) { it.copy(sections = early, items = emptyList(), loading = false, errMsg = "") }
                    }
                })
                if (!isLatest(idx, seq)) return@launch
                if (feed == null) {
                    setCat(idx) {
                        it.copy(loading = false, hasMore = false, errMsg = "推荐页暂时拉不到内容，请下拉重试")
                    }
                    return@launch
                }
                val sections = feed.sections
                sectionsByPath["${feed.source.key}|${feed.tab.path}"] = sections
                PlaybackProbe.note(
                    "index-recommend",
                    "source=${feed.source.key} tabs=${feed.tabs.size} sections=${sections.size} " +
                        "elapsed=${com.dengying.player.platform.Clock.nowMs() - t0}",
                )
                PlaybackProbe.note(
                    "index-home-first",
                    "reason=cold src=${feed.source.key} sections=${sections.size} " +
                        "cards=${sections.sumOf { it.items.size }} " +
                        "elapsed=${com.dengying.player.platform.Clock.nowMs() - t0}",
                )
                setCat(idx) {
                    it.copy(
                        homeTabs = feed.tabs,
                        homeTabPath = feed.tab.path,
                        sections = sections,
                        items = emptyList(),
                        loading = false,
                        hasMore = false,
                        // 版块全空不算失败（运营位可能临时下架），但要说清楚
                        errMsg = if (sections.isEmpty()) "版块暂时没有内容" else "",
                    )
                }
                // 版块卡片同样预取详情：推荐页是第一入口，点进去的频次最高
                prefetchDetails(idx, sections.flatMap { it.items })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isLatest(idx, seq)) {
                    val msg = if (e is IllegalStateException) "应用初始化失败，请重启应用（${e.message}）"
                    else "网络异常，请检查连接"
                    com.dengying.player.platform.Log.w(
                        "DY/Index",
                        "loadRecommend failed: ${e::class.simpleName ?: "Exception"}: ${e.message}",
                        e,
                    )
                    setCat(idx) { it.copy(sections = emptyList(), errMsg = msg, loading = false, hasMore = false) }
                }
            }
        }
    }

    /**
     * 推荐页内切换内容导航 tab：命中 [sectionsByPath] 直接显示，否则拉取。
     *
     * 【过期判定不用 seq】tab 切换不递增分类请求号（它不该作废下拉刷新的在途请求），
     * 用「返回时当前 path 是否还是发起时的 path」判定——更快发起的切 tab 自然作废慢的。
     */
    fun selectHomeTab(idx: Int, tab: HomeTab) {
        val cur = state.cat(idx)
        if (cur.homeTabPath == tab.path) return
        val src = tv189Source() ?: return
        val path = tab.path
        setCat(idx) { it.copy(homeTabPath = path) }
        val cached = sectionsByPath["${src.key}|$path"]
        if (cached != null) {
            setCat(idx) {
                it.copy(sections = cached, loading = false, errMsg = if (cached.isEmpty()) "版块暂时没有内容" else "")
            }
            return
        }
        setCat(idx) { it.copy(sections = emptyList(), loading = true, errMsg = "") }
        viewModelScope.launch {
            try {
                val sections = repo.fetchHomeSections(src, path)
                sectionsByPath["${src.key}|$path"] = sections
                // 在途期间用户又切了别的 tab：本结果过期，只写缓存不动 UI
                if (state.cat(idx).homeTabPath != path) return@launch
                setCat(idx) {
                    it.copy(
                        sections = sections,
                        loading = false,
                        errMsg = if (sections.isEmpty()) "版块暂时没有内容" else "",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (state.cat(idx).homeTabPath == path) {
                    setCat(idx) { it.copy(loading = false, errMsg = "网络异常，请检查连接") }
                }
            }
        }
    }

    /**
     * 下拉刷新推荐页：force 直拉网络（跳过仓库 TTL 与失败负缓存）+ 换回默认 tab。
     *
     * 【换回默认 tab】刷新是"重新开始"的语义；停留在小众 tab（如 5G专区）
     * 刷新完容易让人误以为刷新没生效。
     */
    private fun refreshRecommend(idx: Int) {
        if (state.cat(idx).refining) return
        val seq = beginReq(idx)
        val src = homeSource()
        if (src == null) {
            setCat(idx) { it.copy(refining = false, errMsg = "推荐内容需要「蓝光4k」或「天翼视讯」源，当前都未启用") }
            return
        }
        setCat(idx) { it.copy(refining = true, errMsg = "") }
        viewModelScope.launch {
            try {
                // 刷新要穿透 TTL：force=true 跳过仓库缓存与负缓存，再走同一套跨源回落。
                val t0 = com.dengying.player.platform.Clock.nowMs()
                val feed = fetchHomeFeed(src, force = true, reason = "refresh")
                if (!isLatest(idx, seq)) return@launch
                if (feed == null) {
                    setCat(idx) { it.copy(refining = false, sections = emptyList(), errMsg = "推荐页暂时拉不到内容") }
                    return@launch
                }
                // 源站这份首页是运营固定排的，直拉网络也换不出新片。带「换一换」
                // 备用池的版块（备用片已接在条目后面）每次刷新整体轮换一格，
                // 让下拉真正换出一批不同的片。
                val sections = rotateRefreshed(state.cat(idx).sections, feed.sections)
                PlaybackProbe.note(
                    "index-home-first",
                    "reason=refresh src=${feed.source.key} sections=${sections.size} " +
                        "cards=${sections.sumOf { it.items.size }} " +
                        "elapsed=${com.dengying.player.platform.Clock.nowMs() - t0}",
                )
                sectionsByPath["${feed.source.key}|${feed.tab.path}"] = sections
                setCat(idx) {
                    it.copy(
                        homeTabs = feed.tabs,
                        homeTabPath = feed.tab.path,
                        sections = sections,
                        errMsg = if (sections.isEmpty()) "版块暂时没有内容" else "",
                        refining = false,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isLatest(idx, seq)) {
                    setCat(idx) { it.copy(refining = false, errMsg = "网络异常，请检查连接") }
                }
            }
        }
    }

    /**
     * 刷新时轮换版块展示。
     *
     * 源站首页是运营固定排的，每次直拉都一样，而且「换一换」备用池只有
     * 「重磅推荐」「精选热剧」「爆款短剧」三个版块有——「今日焦点」「首播影院」
     * 这些占首屏的大版块一条备用片都没有，只轮换有备用池的版块用户看不出刷新。
     * 所以每次刷新把每个版块当前展示在最前面的几部挪到末尾，横滑条开头就换一批。
     * 片数有限，轮完一圈会回到原来的，但每次下拉都能看到不同的片。
     */
    private fun rotateRefreshed(
        previous: List<HomeSection>,
        fetched: List<HomeSection>,
    ): List<HomeSection> {
        val prevByTitle = previous.associateBy { it.title }
        return fetched.map { section ->
            val before = prevByTitle[section.title] ?: return@map section
            val items = section.items
            if (items.size < 2) return@map section
            // 每次挪走的条数跟横滑条一屏能看见的张数对齐（约 3 张），又不超过一半，
            // 避免片少的版块一下轮完。前后顺序一致说明没有轮换过，从 0 开始。
            val step = min(3, items.size / 2)
            val offset = if (
                items.take(before.items.size) == before.items
            ) {
                0
            } else {
                val pos = items.indexOfFirst { it.vod_id == before.items.first().vod_id }
                if (pos < 0) 0 else pos
            }
            val start = (offset + step) % items.size
            if (start == 0) return@map section
            HomeSection(title = section.title, items = items.drop(start) + items.take(start))
        }
    }

    /** 加载某分类的下一页（带并发锁，防止重复触发） */
    fun loadMore(idx: Int) {
        // 推荐页是版块流（无分页概念），hasMore 恒为 false，这里双保险
        if (state.categories[idx] == RECOMMEND) return
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
