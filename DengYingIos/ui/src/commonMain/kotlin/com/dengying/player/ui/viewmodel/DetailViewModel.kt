package com.dengying.player.ui.viewmodel

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.core.CmsSources
import com.dengying.player.core.Episode
import com.dengying.player.core.FavoriteItem
import com.dengying.player.core.HistoryItem
import com.dengying.player.core.PlayLine
import com.dengying.player.core.VideoItem
import com.dengying.player.core.capsOf
import com.dengying.player.core.parsePlayLines
import com.dengying.player.domain.playback.ResumeDecide
import com.dengying.player.domain.search.SearchNotice
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.platform.Clock
import com.dengying.player.platform.Log
import com.dengying.player.platform.ioDispatcher
import com.dengying.player.player.PlaybackProbe
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.dengying.player.ui.UiDeps
import com.dengying.player.ui.navigation.Routes

/**
 * 一条跨源候选（FR-417 换源）。
 *
 * 顶层类型而非 [DetailViewModel] 的内部类：候选缓存挂在 `UiDeps` 上，
 * 生命周期是整进程，不属于任何一个详情页实例。
 */
data class AltItem(
    val sourceKey: String,
    val sourceName: String,
    val vodId: Long,
    val vodName: String,
)

/**
 * 详情页 —— 影片信息 + 线路/剧集选择 + 收藏（FR-305/306/307/308/309/417/506）
 *
 * 三处「不得静默」的语义在这里落地：
 *  - FR-305：来源 key 不在清单时**直接报错**，不回落第一项（否则会打开别家的同号片）
 *  - FR-309：解析不出剧集时给提示且**不渲染空网格**（死胡同）
 *  - FR-506：从观看记录进入时线路就绪即自动起播；该集失效则停在本页并说明下一步
 */
class DetailViewModel(
    private val vodId: Long,
    private val source: String,
    private val name: String,
    private val pic: String,
    private val vodUri: String,
    private val resumeEpisode: Int,
    private val autoPlayRequested: Boolean,
    private val repo: SourceRepository,
    private val store: Store,
    private val engineHost: PlayerEngineHost,
    /**
     * 应用级依赖，只为拿进程级的 [UiDeps.altSourceCache]。
     *
     * 候选缓存不能挂在 VM 上：VM 绑定 NavBackStackEntry，退出详情页即销毁，
     * 缓存跟着没了（真机实测同片进出 3 次、3 次全量重搜）。
     */
    private val deps: UiDeps,
) : ViewModel() {

    data class UiState(
        val item: VideoItem? = null,
        val lines: List<PlayLine> = emptyList(),
        val lineIdx: Int = 0,
        val loading: Boolean = true,
        val faved: Boolean = false,
        val errMsg: String = "",
        val altSources: List<AltItem> = emptyList(),
        val altLoading: Boolean = false,
        val descOrder: Boolean = false,
        val playGap: String = "",
        val resumeNotice: String = "",
        val epNotice: String = "",
        val epShowGrid: Boolean = true,
        val lastHistory: HistoryItem? = null,
    )

    var state by mutableStateOf(UiState())
        private set

    /** FR-506：本次进入是否需要在线路就绪后自动起播 */
    private var autoPlay = autoPlayRequested

    /** FR-506：自动起播只应发生一次，避免页面重载后反复跳转 */
    private var autoPlayed = false

    /** 导航副作用：需要跳到播放页 */
    var pendingPlayerRoute by mutableStateOf<String?>(null)
        private set

    private var curSource = source
    private var curVodId = vodId
    private var curName = name
    private var curPic = pic
    private var curVodUri = vodUri
    private var resumeEp = resumeEpisode

    /** 详情请求序号：换源会并发起第二次 [loadDetail] 而旧协程仍在跑，旧结果不得覆盖新状态 */
    private var detailSeq = 0

    /** 「打开详情页」的时刻，用于量出内容真正上屏的耗时（探针 detail-content） */
    private val openAtMs = Clock.nowMs()

    /** detail-content 只在整个 VM 生命周期里打一次（缓存命中 + 网络回来会写两遍 state） */
    private var contentShown = false

    init {
        loadDetail()
        // 【内嵌位状态 = 会话状态】单会话架构：session 是唯一事实源，
        // 内嵌位与全屏页都从它读。这里把 session 的 StateFlow 映射进本页 UiState。
        viewModelScope.launch {
            com.dengying.player.playback.PlaybackSession.state.collect { s ->
                // 只在本页显示会话（hasSession 且标题匹配本片）时同步，
                // 避免别的片的会话串到本页内嵌位
                if (!s.hasSession) return@collect
                inlineState = inlineState.copy(
                    active = true,
                    title = s.title,
                    isPlaying = s.status == com.dengying.player.playback.PlaybackSession.Status.Playing,
                    curTime = s.curTime,
                    totalTime = s.totalTime,
                    buffering = s.buffering,
                    status = when (s.status) {
                        com.dengying.player.playback.PlaybackSession.Status.Resolving -> "正在解析播放地址…"
                        com.dengying.player.playback.PlaybackSession.Status.Buffering -> "正在缓冲…"
                        else -> ""
                    },
                    errMsg = s.errMsg,
                )
            }
        }
    }

    fun loadDetail() {
        // 过期响应守卫：换源会再调一次本函数，旧协程可能在之后才返回，
        // 若不认账就会把**上一部片**的详情写到新源的状态上。
        val seq = ++detailSeq
        val t0 = Clock.nowMs()
        PlaybackProbe.note("detail-open", "key=${UiDeps.detailCacheKey(curSource, curVodId)}")
        state = state.copy(loading = true, errMsg = "")
        viewModelScope.launch {
            try {
                // 不跨源兜底：源 key 已不在清单（改源/改名）时直接报错，
                // 避免用同号 vod_id 取到别的片子（FR-305）
                val src = CmsSources.byKey(curSource)
                if (src == null) {
                    state = state.copy(errMsg = "来源已变更，请返回重进", loading = false)
                    return@launch
                }
                val gap = src.playGap ?: ""
                state = state.copy(playGap = gap)

                // 能力位前置判定：只支持搜索的源必须明确提示「点不进去」，
                // 不得发一次注定失败的请求后只报「加载失败」（FR-108/FR-309）
                if (!capsOf(src).detail) {
                    state = state.copy(
                        errMsg = "${src.name} 仅支持搜索，未开放详情。请从其他源打开同一部片",
                        loading = false,
                    )
                    viewModelScope.launch { loadAltSources() }
                    return@launch
                }

                // 【秒开】同一部片（同一源）在本进程内拉过就直接用。
                // 场景：换源失败退回、从收藏/历史点回来、返回后重进——每次都重打
                // 网络是不必要的，而详情在一次会话里几乎不会变。
                // 缓存命中只是**先画**，网络照发（stale-while-revalidate），
                // 拿到新结果再覆盖一次；两者内容相同则无可见变化。
                val cacheKey = UiDeps.detailCacheKey(curSource, curVodId)
                val cached = deps.detailCache.get(cacheKey)
                if (cached != null) {
                    PlaybackProbe.note("detail-cache-hit", "key=$cacheKey")
                    applyDetail(cached, seq)
                }

                val item = repo.fetchDetail(src, curVodId, curVodUri)
                if (seq != detailSeq) return@launch
                PlaybackProbe.note(
                    "detail-net",
                    "ok=${item != null} elapsed=${Clock.nowMs() - t0} hit=${cached != null}",
                )
                if (item != null) {
                    deps.detailCache.put(cacheKey, item)
                    applyDetail(item, seq)
                } else if (cached == null) {
                    state = state.copy(
                        errMsg = "详情加载失败：${src.name} 无数据或请求失败，试试返回重进",
                    )
                }

                // 【收藏态/历史态：挪到内容显示之后，且两者并发】
                // 它们各自要读一次本地存储并全量解析 JSON（Store.kt:221 / :410）。
                // 之前串行排在 loading=false 之前，用户盯着整屏转圈等两次磁盘 IO；
                // 而这两项只影响一个星标和剧集高亮，晚到几十毫秒无感。
                launch {
                    val tFav = Clock.nowMs()
                    val f = store.isFavorite(curVodId, curSource)
                    val favMs = Clock.nowMs() - tFav
                    if (seq == detailSeq) state = state.copy(faved = f)
                    PlaybackProbe.note("detail-local-io", "fav_ms=$favMs")
                }
                launch {
                    val tHis = Clock.nowMs()
                    val h = store.getLastEpisode(curVodId, curSource)
                    val hisMs = Clock.nowMs() - tHis
                    if (seq == detailSeq) state = state.copy(lastHistory = h)
                    PlaybackProbe.note("detail-local-io", "his_ms=$hisMs")
                }
            } catch (e: Exception) {
                if (seq == detailSeq) state = state.copy(errMsg = "详情加载失败")
            }
            if (seq != detailSeq) return@launch

            // 【候选搜索放在详情之后】曾与详情同时发起（"并发而不是串行"），
            // 但真机实测（docs/04 §16.34）：一次进详情会扇出 13 个候选源 × 2 个端点
            // ≈ 26 个请求，把链路带宽占满，主详情反而从 ~400ms 涨到 1~3s。
            // 候选是「多给几个换线选项」的锦上添花，晚 1 秒无感；详情是用户正在等的那一屏。
            // 放在这里（try/catch 之后）而不是 try 内：详情失败时更该给出候选，
            // 那条路径同样要走到。
            viewModelScope.launch { loadAltSources() }

            state = state.copy(loading = false)
            // FR-309：刷新剧集状态
            refreshEpState()
            // FR-506：线路就绪即直达该集续播
            tryAutoPlay()
        }
    }

    /**
     * 把拿到的 [item] 落到状态上并触发首集预解析。
     *
     * **只要 item 到手就立刻写 state**，不再等收藏态/历史态那次本地 IO——
     * 界面据此从整屏转圈切到真实内容（DetailScreen 的 `when` 首分支就是 item != null）。
     */
    private fun applyDetail(item: VideoItem, seq: Int) {
        if (seq != detailSeq) return
        val lines = parsePlayLines(item.vod_play_from ?: "", item.vod_play_url ?: "")
        state = state.copy(item = item, lines = lines)
        if (!contentShown) {
            contentShown = true
            PlaybackProbe.note(
                "detail-content",
                "name=${item.vod_name.take(20)} lines=${lines.size} " +
                    "elapsed=${Clock.nowMs() - openAtMs}",
            )
        }
        // FR-705：详情就绪后**立即后台预解析首集**。
        // 用户在浏览详情（看简介、挑剧集）时，地址已经准备好了，
        // 点击时就不用等解析链（那是"点了没反应"的主因）。
        // 缓存命中后网络结果再回来会走第二遍，已有解析结果就不重复打解析链。
        if (preResolved == null && !preResolving) startPreResolve()
    }

    /**
     * 跨源候选（FR-417）—— 带缓存的搜索。
     *
     * 【加载速度：两处】
     * 1. **并发**：调用点已提前到 [loadDetail] 一开始，与主详情同时跑，
     *    不再串行地等主详情拉完。
     * 2. **缓存**：同一部片反复进出详情（换源失败返回、从收藏再点进来）
     *    会重复搜一轮全源。用进程内缓存记住结果，命中即上屏、不发请求。
     */
    suspend fun loadAltSources() {
        val title = state.item?.vod_name ?: curName
        if (title.isEmpty() || curSource.isEmpty()) return

        // 命中缓存：立即给结果，不发网络。
        // [LruCache.get] 命中即把键移到表尾，淘汰因此按访问序而非插入序。
        val altCache = deps.altSourceCache
        val cached = altCache.get(title)
        if (cached != null) {
            com.dengying.player.player.PlaybackProbe.note(
                "alt-cache-hit", "title=$title n=${cached.size}",
            )
            state = state.copy(altSources = cached, altLoading = false)
            return
        }

        state = state.copy(altLoading = true)
        try {
            val t0 = Clock.nowMs()
            val alts = repo.findAltSources(repo.allSources(), curSource, title)
            val mapped = ArrayList<AltItem>(alts.size)
            for (a in alts) {
                val s = CmsSources.byKey(a.sourceKey) ?: continue
                mapped += AltItem(a.sourceKey, s.name, a.vodId, a.vodName)
            }
            com.dengying.player.player.PlaybackProbe.note(
                "alt-sources-loaded",
                "title=$title n=${mapped.size} elapsed=${Clock.nowMs() - t0}",
            )
            // 只缓存非空结果：空结果可能是抖动，缓存了会让用户永远看不到候选
            if (mapped.isNotEmpty()) {
                altCache.put(title, mapped)
            }
            state = state.copy(altSources = mapped)
        } catch (e: Exception) {
            // 候选失败不影响主详情
        }
        state = state.copy(altLoading = false)
    }

    /** FR-417：换源——跳到目标源的同名影片详情 */
    fun switchToSource(target: AltItem) {
        curSource = target.sourceKey
        curVodId = target.vodId
        curName = target.vodName
        curVodUri = ""
        resumeEp = -1
        state = UiState(loading = true)
        loadDetail()
    }

    /**
     * 收藏翻转（FR-507）。
     *
     * 写成功才翻转界面：`toggleFavorite` 返回「是否已落盘」，不是新收藏态。
     */
    fun onToggleFav() {
        val it = state.item
        val fav = FavoriteItem(
            vod_id = curVodId,
            vod_name = it?.vod_name ?: curName,
            vod_pic = it?.vod_pic ?: curPic,
            source = curSource,
            vod_uri = curVodUri,
            addTime = Clock.nowMs(),
        )
        val wasFav = state.faved
        viewModelScope.launch {
            if (store.toggleFavorite(fav)) {
                state = state.copy(faved = !wasFav)
                // FR-509：收藏超容量会淘汰最旧的——淘汰发生了就必须让用户知道
                val dropNotice = store.takeFavoriteDropNotice()
                if (dropNotice.isNotEmpty()) state = state.copy(resumeNotice = dropNotice)
            } else {
                state = state.copy(resumeNotice = "本地存储不可用，改动未保存；请重试")
            }
        }
    }

    fun selectLine(idx: Int) {
        state = state.copy(lineIdx = idx)
        // FR-309：不同线路的剧集数可能不同，切线路后重算状态
        refreshEpState()
    }

    fun toggleOrder() {
        state = state.copy(descOrder = !state.descOrder)
    }

    private fun refreshEpState() {
        val st = SearchNotice.decideEpisodeState(state.lines.size, currentEps().size)
        state = state.copy(epNotice = st.notice, epShowGrid = st.showGrid)
    }

    fun currentEps(): List<Episode> {
        if (state.lines.isEmpty()) return emptyList()
        val idx = minOf(state.lineIdx, state.lines.size - 1)
        return state.lines[idx].episodes
    }

    /** 显示顺序的剧集列表（正序=原始顺序；倒序=反转显示） */
    fun displayEps(): List<Episode> {
        val eps = currentEps()
        return if (!state.descOrder) eps else eps.reversed()
    }

    /** 显示序号 → 真实序号（倒序时反转回来，保证播放器换集/续播定位一致） */
    fun epRealIndex(displayIdx: Int): Int {
        val n = currentEps().size
        return if (state.descOrder) n - 1 - displayIdx else displayIdx
    }

    /** 是否为「上次看到的一集」（历史进入时高亮） */
    fun isResumeEp(idx: Int): Boolean {
        val h = state.lastHistory
        return if (h != null) idx == h.episodeIndex else idx == resumeEp
    }

    /**
     * 内嵌播放态（FR-705）。
     *
     * 与"点剧集跳播放页"并存：点剧集**就地播放**（内嵌），
     * 内嵌态点全屏键才进全屏播放页并带上当前进度。
     *
     * 【D6 单会话架构】内嵌位与全屏页共用同一个全局引擎
     * （`PlaybackEngineHolder` 唯一实例），本页只是它的视图宿主。
     * 初版注释写的"用独立的引擎实例"是 D6 改造前的旧设计，已按现状改正。
     */
    data class InlineState(
        val active: Boolean = false,
        val title: String = "",
        val isPlaying: Boolean = false,
        val curTime: Long = 0,
        val totalTime: Long = 0,
        val buffering: Boolean = false,
        val errMsg: String = "",
        /** 进行中的状态文案（"正在换源…"等）。**必须有**：换源耗时数秒，静默等于没反应 */
        val status: String = "",
        /** 页面正在退出：只留黑底，不画占位图形（修"返回后残留"） */
        val hidden: Boolean = false,
    )

    /**
     * 后台预解析首集地址（FR-705）。
     *
     * 只做**首集**：它是最可能被点击的那个。全集预解析会打爆网络，收益也不成比例。
     * 解析失败也不报错——用户点的时候会走正常链路并看到明确提示。
     */
    private fun startPreResolve() {
        val eps = currentEps()
        if (eps.isEmpty()) return
        if (preResolving) return
        preResolving = true
        preResolveIdx = 0
        viewModelScope.launch {
            try {
                val t0 = Clock.nowMs()
                val r = resolveForInline(eps[0].url, quiet = true)
                PlaybackProbe.note(
                    "pre-resolve",
                    "ok=${r != null} elapsed=${Clock.nowMs() - t0}",
                )
                preResolved = r
            } catch (e: Exception) {
                PlaybackProbe.note("pre-resolve-failed", e::class.simpleName ?: "Exception")
            } finally {
                preResolving = false
            }
        }
    }

    /** 内嵌播放器引擎；为 null 表示未开始播放 */
    var inlineEngine: com.dengying.player.player.PlayerEngine? = null
        private set

    /**
     * 【视图宿主绑定】把会话的引擎接到本页内嵌位（FR-711）。
     *
     * 单会话架构下引擎归 `PlaybackSession` 所有，本页**只借用**它来显示。
     * 初版重构时漏了这一步——会话在播、内嵌位却是空态
     * （用户反馈"占位符没内容啊"的直接原因）。
     *
     * 由 UI 在组合期间调用（见 `DisposableEffect`）；会话空闲时是 no-op。
     */
    fun attachInlineSurface() {
        val s = com.dengying.player.playback.PlaybackSession.currentState()
        if (!s.hasSession) return
        val e = engineHost.peek() ?: return
        if (inlineEngine === e) return
        inlineEngine = e
        if (!surfaceRegistered) {
            surfaceRegistered = true
            engineHost.surfaceActive()
        }
        PlaybackProbe.note("inline-surface-bind", "status=${s.status}")
    }

    /**
     * 详情页是否已登记为"活跃播放表面"。
     *
     * 【为什么用独立标志而不是"engine 是否非空"】真机踩坑：切全屏时
     * `toFullscreen()` 会把 `inlineEngine` 置 null，之后 `releaseInline()`
     * 里 `if (e != null)` 判断失败 → 计数没减 → 泄漏 → 引擎永不暂停
     * （"返回首页还有声音"）。
     *
     * 用独立标志记录"我登记过"，注销时无条件减——与字段是否被清无关。
     */
    private var surfaceRegistered = false

    /**
     * **预解析**的首集地址（FR-705）。
     *
     * 【为什么要预解析】真机探针显示：点击剧集后才开始解析，
     * 要串行跑「token 换链 → 分享页解析 → 跨源搜索」，
     * `[inline-alt-sources] n=5` 之后十几秒无输出，用户等不及返回
     * 触发 `JobCancellationException`——**从头到尾没有画面**。
     *
     * 用户在详情页浏览（看简介、挑剧集）通常有几秒，这段时间足够预解析。
     * 点击时直接用已就绪的地址，就是"秒播"。
     *
     * 只预解析**首集**（最可能被点的那个），不做全部——全集预解析会打爆网络。
     */
    private var preResolved: Pair<String, String>? = null
    private var preResolving = false
    private var preResolveIdx = -1

    var inlineState by mutableStateOf(InlineState())
        private set

    /** 内嵌播放的当前集真实序号（切全屏时带过去） */
    private var inlineEpisodeIndex = 0

    /** 内嵌播放的地址与请求头（切全屏时带过去，避免重新解析） */
    private var inlineUrl = ""
    private var inlineHeaders = ""

    private var inlineSaveTick = 0

    /**
     * 进度上报循环的 Job。
     *
     * 用单例保证"只有一个循环在跑"——见 `onPlaying` 里的说明：
     * 初版每次 `onPlaying` 都启一个新循环，在状态抖动时无限累积。
     */
    private var progressJob: kotlinx.coroutines.Job? = null

    /**
     * 本媒体会话是否已恢复过续播位置。
     *
     * ## 【关键守卫】真机"快速暂停又恢复、一直往复"的根因
     *
     * `onReady` 在播放器**每次**到达 READY 时都会触发。初版在这里
     * 无条件调 `restoreInlinePosition()` → `seekTo(历史位置)`：
     *
     * ```
     * READY → onReady → seekTo → BUFFERING → READY → onReady → seekTo → …
     * ```
     *
     * 探针记录到 150 对 `BUFFERING ↔ READY` 交替，且这部片有历史记录
     * （位置 > 3 秒），所以每次 READY 都往回 seek——用户看到的就是
     * "画面反复跳回、快速暂停又恢复"。
     *
     * **恢复续播必须每次媒体会话只做一次**。新会话（点剧集/换集）时复位。
     */
    private var inlinePositionRestored = false

    /**
     * 点剧集：**就地播放**（FR-705）。
     *
     * 与 [playEpisode] 的分工：
     *  - 本方法：在详情页内嵌位播放，不跳页
     *  - [playEpisode]：跳全屏播放页（内嵌态点全屏键时走它）
     */
    /**
     * 点剧集：**发起会话**（FR-705 + D6 单会话架构）。
     *
     * 【FR-710 可点性】调用前提是 `displayEps` 非空（UI 层已保证）——
     * 即主线路给出了剧集地址就**可点**，不等资源站搜索；
     * 解析/换源由会话负责并显示状态。
     *
     * 【不再自己解析/建引擎】初版在本方法里跑解析链、建引擎、setMedia——
     * 与全屏页各一套，产生了交接类全部问题。现在只调 [PlaybackSession.play]，
     * 解析/换源/引擎操作都在会话内。
     */
    fun playInline(realIdx: Int) {
        // 【先落探针再判早退】真机反馈"点击剧集没反应"——必须先能区分
        // "点击没到达"与"到达了但被早退条件挡住"，否则无法定位。
        PlaybackProbe.note(
            "inline-click-enter",
            "idx=$realIdx item=${state.item != null} eps=${currentEps().size} " +
                "loading=${state.loading} err=${state.errMsg.take(40)}",
        )

        val it = state.item ?: run {
            PlaybackProbe.note("inline-early-return", "reason=item-null")
            return
        }
        val eps = currentEps()
        if (realIdx < 0 || realIdx >= eps.size) {
            PlaybackProbe.note("inline-early-return", "reason=idx-out-of-range size=${eps.size}")
            return
        }
        val ep = eps[realIdx]

        // 会话重置续播恢复标志（新集新会话语义）
        inlinePositionRestored = false

        com.dengying.player.playback.PlaybackSession.play(
            repo = repo,
            rawUrl = ep.url,
            title = "${it.vod_name} ${ep.name}",
            vodName = it.vod_name,
            vodId = curVodId,
            source = curSource,
            vodUri = curVodUri,
            episodeIndex = realIdx,
            episodeName = ep.name,
            preResolved = if (realIdx == preResolveIdx) preResolved?.first else null,
            preHeaders = if (realIdx == preResolveIdx) preResolved?.second ?: "" else "",
        )
    }

    /**
     * 内嵌播放的地址解析：分享页 → m3u8；token 型源先换链。
     *
     * @return (最终地址, 请求头 JSON)；解析失败返回 null
     */
    /**
     * @param quiet 预解析模式：**不改 UI 状态**（用户还没点，不该看到"正在换源…"）
     */
    private suspend fun resolveForInline(raw: String, quiet: Boolean = false): Pair<String, String>? {
        val src = CmsSources.byKey(curSource)
        var url = raw
        var headers = ""
        if (src != null && capsOf(src).token) {
            val r = repo.resolveSourcePlay(src, curVodId, raw, "")
            if (r.url.isNotEmpty()) {
                url = r.url
                headers = r.headers
            }
        }
        if (headers.isEmpty()) {
            val outcome = com.dengying.player.data.net.HlsUtil.resolvePlayUrlOrFail(url)
            if (!outcome.resolved) {
                // 【内嵌播放也要有换线兜底】真机探针：`inline-resolve-done ok=false`，
                // 因为 jyzy 源的剧集地址是**分享页**，静态正则提取不到。
                // 全屏播放页有 `autoSwitchLine` 兜底所以能播；内嵌播放初版没有，
                // 于是解析失败就停住——用户看到的是"点了没反应"。
                PlaybackProbe.note("inline-resolve-failed-try-switch", "quiet=$quiet")
                if (!quiet) inlineState = inlineState.copy(status = "主线路不可用，正在换源…")
                val alt = resolveInlineByAltSource()
                if (alt != null) {
                    if (!quiet) inlineState = inlineState.copy(status = "正在连接…")
                    return alt
                }
                return null
            }
            url = outcome.url
        }
        return url to headers
    }

    /**
     * 内嵌播放的跨源兜底：**并发**找同名资源的其他源。
     *
     * ## 为什么要并发（真机反馈"点剧集没反应"的直接原因）
     *
     * 初版串行遍历候选源，每个要「搜索 → 详情 → 取集」三次请求。探针记录到
     * `[inline-alt-sources] n=5` 之后长时间无输出，最后用户等不及返回，
     * 协程被取消（`JobCancellationException`）——**从头到尾没有任何画面**。
     *
     * 5 个候选串行 × 每源约 2-3 秒 = 十几秒。这在内嵌预览场景完全不可接受。
     * 改为并发：总耗时约等于最慢的那个候选，通常 2-3 秒内出结果。
     *
     * ## 并发安全的取舍
     *
     * 用 `async` 并发、**取第一个成功的结果**即可返回；其余协程随作用域取消。
     * 不追求"探测最健康的那个"——内嵌位是快速预览，能播就行，
     * 逐个体检会让用户白等（那是全屏播放页的策略，那里用户可以接受等待）。
     */
    private suspend fun resolveInlineByAltSource(): Pair<String, String>? {
        val vodName = state.item?.vod_name ?: curName
        if (vodName.isEmpty() || curSource.isEmpty()) return null

        val alts = repo.findAltSources(repo.allSources(), curSource, vodName)
        PlaybackProbe.note("inline-alt-sources", "n=${alts.size}")
        if (alts.isEmpty()) return null

        // 并发试所有候选，谁先成功用谁
        return kotlinx.coroutines.coroutineScope {
            val deferred = alts.map { alt ->
                async(ioDispatcher) {
                    val altSrc = CmsSources.byKey(alt.sourceKey) ?: return@async null
                    val u = repo.fetchAltPlayUrl(altSrc, alt.vodId, inlineEpisodeIndex)
                    if (u.isEmpty()) return@async null
                    val outcome = com.dengying.player.data.net.HlsUtil.resolvePlayUrlOrFail(u)
                    if (!outcome.resolved) return@async null
                    PlaybackProbe.note(
                        "inline-alt-hit",
                        "src=${alt.sourceKey} urlLen=${outcome.url.length}",
                    )
                    outcome.url to ""
                }
            }
            // 【谁先完成用谁】原写法是 `for (d in deferred) d.await()`——按**列表顺序**
            // 逐个等，于是第 1 个候选若最慢，第 3 个候选早就成功了也照样要等第 1 个
            // 失败才轮到它。注释写的"先完成的先返回"在代码里从未成立。
            // 用 select 等"最早完成且非空"的那个，其余立即取消。
            val pending = deferred.toMutableList()
            while (pending.isNotEmpty()) {
                val r = runCatching {
                    kotlinx.coroutines.selects.select<Pair<String, String>?> {
                        pending.forEach { d -> d.onAwait { it } }
                    }
                }.getOrNull()
                if (r != null) {
                    // 取消其余候选——它们已经没用了，留着白耗流量
                    deferred.forEach { if (it.isActive) it.cancel() }
                    return@coroutineScope r
                }
                pending.removeAll { it.isCompleted }
            }
            null
        }
    }

    private fun bindInlineListener(eng: com.dengying.player.player.PlayerEngine) {
        eng.addListener(object : com.dengying.player.player.PlayerListener {
            override fun onReady(durationMs: Long) {
                inlineState = inlineState.copy(
                    totalTime = durationMs,
                    buffering = false,
                    errMsg = "",
                )
                viewModelScope.launch { restoreInlinePosition() }
            }

            override fun onPlaying() {
                inlineState = inlineState.copy(isPlaying = true, buffering = false)
                // 【进度循环只能启动一次】真机踩坑：`onPlaying` 在播放状态抖动时
                // （`BUFFERING ↔ READY` 振荡导致 `isPlaying` 反复变化）会被反复调用，
                // 而初版每次都 `launch { while(true) {...} }` ——
                // **每触发一次就多一个永不退出的循环**，形成正反馈：
                // 更多循环 → 更多 `currentPosition` 读取 → 更多状态更新 → 更多抖动。
                //
                // 用 `progressJob` 保证只有一个循环在跑：已存在就不重复启动。
                if (progressJob?.isActive == true) return
                progressJob = viewModelScope.launch {
                    while (true) {
                        kotlinx.coroutines.delay(1000)
                        val e = inlineEngine ?: break
                        if (!e.isPlaying) continue
                        inlineState = inlineState.copy(curTime = e.currentPosition)
                        inlineSaveTick++
                        if (inlineSaveTick % 5 == 0) saveInlineHistory()
                    }
                }
            }

            override fun onPaused() {
                inlineState = inlineState.copy(isPlaying = false)
            }

            override fun onBuffering(active: Boolean) {
                inlineState = inlineState.copy(buffering = active)
            }

            override fun onCompleted() {
                inlineState = inlineState.copy(isPlaying = false)
                viewModelScope.launch { saveInlineHistory() }
            }

            override fun onError(
                kind: com.dengying.player.player.PlaybackErrorKind,
                code: Int,
                message: String,
            ) {
                PlaybackProbe.note("inline-error", "kind=$kind code=$code msg=${message.take(80)}")
                inlineState = inlineState.copy(
                    buffering = false,
                    errMsg = "播放失败，可换线路或全屏播放",
                )
            }

            override fun onVideoSize(width: Int, height: Int) {
                // 内嵌位固定 16:9，尺寸变化不影响布局，无需处理
            }
        })
    }

    /** FR-405 同一口径：小于 3 秒视为从头。**每次媒体会话只执行一次**（守卫见字段说明） */
    private suspend fun restoreInlinePosition() {
        if (inlinePositionRestored) return
        inlinePositionRestored = true
        if (curVodId == 0L || curSource.isEmpty()) return
        val pos = store.getEpisodePosition(curVodId, curSource, inlineEpisodeIndex)
        PlaybackProbe.note("inline-restore-pos", "pos=$pos applied=${pos > 3000}")
        if (pos > 3000) inlineEngine?.seekTo(pos)
    }

    /** FR-507：内嵌播放同样落盘观看记录 */
    private suspend fun saveInlineHistory() {
        if (curVodId == 0L || curSource.isEmpty()) return
        val eps = currentEps()
        val epName = eps.getOrNull(inlineEpisodeIndex)?.name ?: ""
        val item = HistoryItem(
            vod_id = curVodId,
            vod_name = state.item?.vod_name ?: curName,
            vod_pic = state.item?.vod_pic ?: curPic,
            source = curSource,
            vod_uri = curVodUri,
            episodeIndex = inlineEpisodeIndex,
            episodeName = epName,
            position = inlineState.curTime,
            duration = inlineState.totalTime,
            updateTime = Clock.nowMs(),
        )
        store.saveHistory(item)
    }

    fun toggleInlinePlay() {
        val e = inlineEngine ?: return
        if (inlineState.isPlaying) e.pause() else e.play()
    }

    fun seekInline(positionMs: Long) {
        inlineEngine?.seekTo(positionMs)
        inlineState = inlineState.copy(curTime = positionMs)
    }

    /**
     * 内嵌态点全屏键（FR-711：**纯导航，零播放操作**）。
     *
     * 会话继续播——全屏页只是同一会话的**全屏视图宿主**：
     * 不重新解析、不 setMedia、不 pause、不交接引擎。
     *
     * 初版在这里做了三件事（surfaceInactive / 清会话 / 传参重新解析），
     * 每一件都在真机上产生过问题："又触发一次缓存"、"直接不播放"、"返回不恢复"。
     * 单会话架构下这些操作**全部不存在**。
     */
    fun toFullscreen() {
        val s = com.dengying.player.playback.PlaybackSession.currentState()
        if (!s.hasSession) return
        // 【带上 curTime 与 headers】切全屏后如果画面跑错位置，这里是唯一能判断
        // "路由到底传了什么" 的一行。之前只记 pos，无法区分"pos 记对了但没传进路由"。
        PlaybackProbe.note(
            "inline-to-fullscreen",
            "pos=${s.curTime} headers=${s.headers.isNotEmpty()} ep=${s.episodeIndex}",
        )
        pendingPlayerRoute = Routes.player(
            url = s.url,
            title = s.title,
            vodId = s.vodId,
            source = s.source,
            vodName = s.vodName,
            vodPic = state.item?.vod_pic ?: curPic,
            vodUri = s.vodUri,
            lineFrom = state.lines.getOrNull(state.lineIdx)?.name ?: "",
            episodeIndex = s.episodeIndex,
            episodeName = s.episodeName,
            altUrls = emptyList(),
            epUrls = emptyList(),
            epNames = emptyList(),
            // 【必须带起播位置与请求头】真机实测：漏传这两个参数的后果是
            //   [inline-to-fullscreen] pos=3271638   ← 内嵌位已经播到 54:31
            //   [engine-seek] target=3271638         ← 用户手动 seek
            //   [resume-restore] ep=0 pos=114992     ← 全屏页用历史值把它盖掉
            //   [engine-seek] target=114992          ← 画面跳回 1:55
            // 起播位置决定 `PlayerViewModel.restorePosition()` 的第一道守卫：
            // 只有 `startAtMs > 0` 时它才会放弃历史续播点。漏传 = 守卫失效，
            // 于是「切全屏」和「手动 seek」两条路径都会被历史记录拉回去。
            startAtMs = s.curTime,
            headersJson = s.headers,
        )
    }

    /**
     * 回到详情页时恢复内嵌位（修"返回后内嵌位全黑"）。
     *
     * ## 真机反馈"几乎每次恢复播放历史记录时都这样"的根因
     *
     * 从观看记录进入详情页时 `autoPlay = true` → `tryAutoPlay()` → `playEpisode()`
     * **直接跳播放页**（不走内嵌路径），所以 `inlineUrl` **始终是空的**。
     *
     * 之后详情页退到后台 → `ON_STOP` → `clearInlineUi()` 置 `hidden = true`；
     * 从播放页返回 → `ON_START` → 本方法见 `inlineUrl` 为空 → **直接 return**，
     * `hidden` 再也没被清掉 → **内嵌位全黑、没有占位图形、没有控制栏**。
     *
     * 而手动点剧集时走内嵌路径（`inlineUrl` 有值），能正常恢复——
     * 这就是"偶现"的确切来源：**只在这条路径上 hidden 无人复位**。
     *
     * ## 修法
     *
     * `hidden` 只是"转场中不画占位"的**瞬时**状态，回到前台必须**无条件清掉**。
     * 恢复会话是"锦上添花"，清 hidden 是"必须做"——两者不能绑在一个条件里。
     */
    fun resumeInlineIfPlaying() {
        // 第一件事：无条件复位 hidden（与是否能恢复会话无关）
        if (inlineState.hidden) {
            inlineState = inlineState.copy(hidden = false)
            PlaybackProbe.note("inline-hidden-cleared")
        }

        // 【FR-711 单会话】内嵌位的"会话"就是全局 PlaybackSession。
        // 从全屏返回时 session 还在播 → 直接把它画进内嵌位，继续播。
        // 初版读自己的 inlineUrl/inlineState——那套交接字段在单会话架构下已废弃，
        // 这也是"返回不恢复"多次修不好的根源。
        //
        // 【先落探针再判早退】真机（2026-10-31）出现过 hidden-cleared 之后
        // 既无 inline-resumed 也无 skip 证据的返回，两处早退分不清是哪条挡的。
        // 与 playInline 同一口径：必须能区分"没进来"与"进来了被挡"。
        val s = com.dengying.player.playback.PlaybackSession.currentState()
        if (!s.hasSession) {
            PlaybackProbe.note(
                "inline-resume-skip",
                "reason=no-session status=${s.status} urlEmpty=${s.url.isEmpty()}",
            )
            return
        }

        val e = engineHost.peek()
        if (e == null) {
            PlaybackProbe.note("inline-resume-skip", "reason=engine-null")
            return
        }
        inlineEngine = e
        if (!surfaceRegistered) {
            surfaceRegistered = true
            engineHost.surfaceActive()
        }
        val enginePlaying = e.isPlaying
        inlineState = inlineState.copy(
            active = true,
            hidden = false,
            isPlaying = enginePlaying,
            curTime = e.currentPosition,
            totalTime = if (e.duration > 0) e.duration else inlineState.totalTime,
            status = "",
            errMsg = "",
        )
        if (enginePlaying) {
            runCatching { e.play() }
        }
        PlaybackProbe.note(
            "inline-resumed",
            "pos=${e.currentPosition} enginePlaying=$enginePlaying sessionStatus=${s.status}",
        )
    }

    /**
     * **同步**清空内嵌播放的 UI 状态（不动引擎）。
     *
     * 【为什么与 [releaseInline] 分开】真机反馈"返回首页后占位符残留约 0.5 秒"：
     * 页面退出转场期间它仍在组合中、仍在渲染，而原来的 `releaseInline`
     * 把"清状态"和"释放引擎"都放在协程里串行执行，清状态被推迟到转场之后。
     *
     * 拆成两步：本方法**立即**把 UI 状态归零（下一帧就不再画占位），
     * 引擎释放交给 [releaseInline] 异步做。UI 与资源释放解耦，
     * 用户看不到残留，解码器也照样被回收。
     */
    fun clearInlineUi() {
        // 【只置 hidden，不清空状态】真机反馈"返回首页后占位符残留约 0.5 秒"：
        // `onDispose` 要等 NavHost 退出转场**结束**才触发，那段时间详情页仍在组合中。
        // 若把状态清成空态，`engine == null` 分支会画出占位图形（▶ + 文案）——
        // 那正是用户看到的"残留"。
        //
        // 置 hidden 后占位图形不画，黑底保留（视频位置本来就是黑的，视觉无跳变）。
        inlineState = inlineState.copy(hidden = true)
    }

    /**
     * 详情页真正销毁时：**结束会话**（FR-711）。
     *
     * 单会话架构下的规则：
     *  - 全屏页在播（会话由它接管）→ 不动会话，只注销本页表面
     *  - 没有全屏页（用户直接返回首页）→ 会话该停——**这就是"返回首页声音停"的实现点**
     *
     * 探针证据（修"返回首页还有声音"时的记录）：
     * `inline-release engine=false` —— 字段被提前清空导致判断失效。
     * 现在不再依赖任何"引擎引用是否为空"的推断，直接问会话："全屏页接管了吗？"
     */
    fun releaseInline() {
        inlineEngine = null
        preResolved = null
        preResolveIdx = -1

        // 注销本页表面（无条件——详情页确实不再显示了）
        if (surfaceRegistered) {
            surfaceRegistered = false
            engineHost.surfaceInactive()
            PlaybackProbe.note(
                "inline-surface-inactive",
                "count=${engineHost.surfaceCount()}",
            )
        }

        // 会话归属判断：播放页活着（它的 surfaceActive 未注销）→ 由它接管，不动会话；
        // 否则本页是最后的宿主 → 结束会话（内部 pause，"返回首页声音停"）。
        val s = com.dengying.player.playback.PlaybackSession.currentState()
        if (s.hasSession && engineHost.surfaceCount() == 0) {
            com.dengying.player.playback.PlaybackSession.stop()
        }
    }

    /**
     * 暂停内嵌播放（**保留会话**）。
     *
     * 用于"转场开始"这个时机：立刻静音，但不清会话——
     * 用户从全屏页返回时还能恢复原会话继续看。
     */
    fun pauseInline() {
        inlineEngine?.pause()
        inlineState = inlineState.copy(isPlaying = false)
        PlaybackProbe.note("inline-pause")
    }

    fun consumeInlineError() {
        if (inlineState.errMsg.isNotEmpty()) {
            inlineState = inlineState.copy(errMsg = "")
        }
    }

    /**
     * 播放指定集并**跳到全屏播放页**（FR-506：UI 点击与「从观看记录直达」共用同一入口）。
     *
     * @param realIdx 真实集序号（非显示序号；倒序显示由调用方先换算）
     * @param startAtMs 起播位置。0 = 从头；内嵌态切全屏时传入当前进度（FR-705）
     * @param overrideUrl 已解析好的地址。内嵌态切全屏时传入——
     *   **必须传**，否则播放页拿到分享页地址要重新解析（真机反馈"点全屏又触发一次缓存"）
     * @param overrideHeaders 与 `overrideUrl` 配套的请求头
     */
    fun playEpisode(
        realIdx: Int,
        startAtMs: Long = 0L,
        overrideUrl: String = "",
        overrideHeaders: String = "",
    ) {
        val it = state.item ?: return
        val eps = currentEps()
        if (realIdx < 0 || realIdx >= eps.size) return
        val ep = eps[realIdx]
        // 有 overrideUrl 时用它（已解析），否则用原始地址
        val playUrl = overrideUrl.ifEmpty { ep.url }
        // 【写文件，不依赖日志】vivo 会全量过滤本应用日志，探针文件是唯一可读通道
        com.dengying.player.player.PlaybackProbe.note(
            "detail-playEpisode",
            "idx=$realIdx name=${ep.name} urlLen=${playUrl.length} vodId=$curVodId " +
                "source=$curSource startAtMs=$startAtMs overridden=${overrideUrl.isNotEmpty()}",
        )
        Log.w(
            "DY/Detail",
            "playEpisode idx=$realIdx name=${ep.name} url=${ep.url.take(60)} " +
                "vodId=$curVodId source=$curSource",
        )

        // 同源其他线路的完整 URL：给播放页做自动换线候选
        val altUrls = ArrayList<String>()
        state.lines.forEachIndexed { li, line ->
            if (li != state.lineIdx && line.episodes.size > realIdx) {
                altUrls += line.episodes[realIdx].url
            }
        }

        pendingPlayerRoute = Routes.player(
            // 【用已解析地址】内嵌态切全屏时传的是 `overrideUrl`（已解析的 68 字节地址）。
            // 若这里仍传 `ep.url`（原始分享页地址），播放页会重新跑解析链——
            // 真机反馈"点全屏好像又触发了一次缓存"就是这个原因。
            url = playUrl,
            title = "${it.vod_name} ${ep.name}",
            vodId = curVodId,
            source = curSource,
            vodName = it.vod_name,
            vodPic = it.vod_pic,
            vodUri = curVodUri,
            lineFrom = state.lines.getOrNull(state.lineIdx)?.name ?: "",
            episodeIndex = realIdx,
            episodeName = ep.name,
            altUrls = altUrls,
            epUrls = eps.map { e -> e.url },
            epNames = eps.map { e -> e.name },
            startAtMs = startAtMs,
            headersJson = overrideHeaders,
        )
    }

    fun consumePlayerRoute() {
        pendingPlayerRoute = null
    }

    /**
     * FR-506：从观看记录直达该集续播。
     *
     * 判定交给纯函数 [ResumeDecide.decideResume]。三种结局：
     *  - 直达播放
     *  - 拿不到线路：停在详情页并提示换线路
     *  - 集序号越界（源改版）：停在详情页并提示换线路，**不静默播别的集**
     */
    private fun tryAutoPlay() {
        if (autoPlayed) return
        val d = ResumeDecide.decideResume(autoPlay, currentEps().size, resumeEp)
        // 详情整体失败时已有 errMsg 说明问题，不再叠一条同类提示
        if (d.notice.isNotEmpty() && state.item != null) {
            state = state.copy(resumeNotice = d.notice)
        }
        if (d.verdict == ResumeDecide.Verdict.Play) {
            autoPlayed = true
            playEpisode(d.episodeIndex)
            return
        }
        if (autoPlay) autoPlayed = true
    }

    fun consumeNotice() {
        if (state.resumeNotice.isNotEmpty()) state = state.copy(resumeNotice = "")
    }
}
