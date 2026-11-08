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
import com.dengying.player.data.net.HlsUtil
import com.dengying.player.core.CmsSources
import com.dengying.player.core.capsOf
import com.dengying.player.domain.playback.CastUtil
import com.dengying.player.domain.playback.HlsVariants
import com.dengying.player.domain.playback.ResumeRestore
import com.dengying.player.domain.playback.SeekPreview
import com.dengying.player.domain.ui.UrlGuard
import com.dengying.player.core.HistoryItem
import com.dengying.player.player.PlaybackErrorKind
import com.dengying.player.player.PlayerEngine
import com.dengying.player.player.PlayerListener
import com.dengying.player.player.PlaybackProbe
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.platform.Clock
import com.dengying.player.platform.Log
import com.dengying.player.platform.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放页（FR-401..406、FR-409、FR-410、FR-412、FR-413、FR-415、FR-416、FR-419、FR-424）
 *
 * ## 相对鸿蒙版的结构变化（有意为之，不是遗漏）
 *
 * | 原工程机制 | 本移植 | 原因 |
 * |---|---|---|
 * | surfaceId 与 url 的三条绑定时序 | **不复刻** | Media3 的 PlayerView/PlayerSurface 内部持有 surface，不存在"先 surface 后 url"的约束。复刻一个不存在的约束只会制造假依赖 |
 * | `media.createMediaSourceWithUrl` 传头 | 数据源工厂注入 | 同一语义，位置更底层（HLS 与 progressive 都生效） |
 * | `PlaybackSpeed` 枚举映射 | `PlaybackParameters(float)` | Media3 接受任意速率，五档直接可用 |
 * | `XComponent` 无条件渲染 | `AndroidView(PlayerView)` 常驻 | 等价保证：视图树里始终有渲染目标 |
 *
 * ## 保留的核心契约（全部来自原工程，逐条对应）
 *
 * 1. **SEC-005 两道闸**：路由地址与适配器换出的地址都要过 [UrlGuard]
 * 2. **探测后起播 / 失败自动换线**：同源其它线路 → 跨源同名候选，逐个"解析+探测"，分片可达即用
 * 3. **续播**：按 `(内容标识, 剧集序号)` 精确恢复；**小于 3 秒视为从头**
 * 4. **进度落盘**：每 5 个时间回调写一次；离开页面再写一次
 * 5. **失败分类**：清单不可达 / 分片不可达（含主机名）/ 解码失效，三类提示不同（FR-406）
 * 6. **自动连播**：开关持久化；末集不越界
 * 7. **清晰度**：`shouldShowPicker` 决定是否显示入口；换档保留进度
 * 8. **失败重试**：解析阶段失败重走全链路，播放器阶段失败只重建引擎
 */
class PlayerViewModel(
    private val params: PlayerParams,
    private val repo: SourceRepository,
    private val store: Store,
    private val engineHost: PlayerEngineHost,
    /**
     * 是否 debug 构建（KMP P4-4b）。
     *
     * 只用于「分享页原始 HTML 落盘」这一个排查手段（见调用点注释）。
     * 迁移前直接读 `com.dengying.player.BuildConfig.DEBUG`，升舱后
     * `:ui` 没有自己的 BuildConfig，改由 [com.dengying.player.ui.UiDeps.isDebug]
     * 经平台壳注入。
     */
    private val isDebug: Boolean,
) : ViewModel() {

    data class PlayerParams(
        val url: String,
        val title: String,
        val vodId: Long,
        val source: String,
        val vodName: String,
        val vodPic: String,
        val vodUri: String,
        val lineFrom: String,
        val episodeIndex: Int,
        val episodeName: String,
        val altUrls: List<String>,
        val epUrls: List<String>,
        val epNames: List<String>,
        /**
         * 起播位置（FR-705）。
         *
         * 详情页内嵌播放态点全屏键时传入当前进度，避免全屏后从头播。
         * 0 表示"不指定"，此时由历史续播点决定（见 [restorePosition]）。
         */
        val startAtMs: Long = 0L,
        /**
         * 适配器换链产出的请求头（JSON）。
         *
         * FR-705：内嵌态切全屏时带过来。有它就意味着地址是**已换链的直出地址**，
         * 解析链整段跳过——这正是"点全屏不再触发一次解析"的关键。
         */
        val headersJson: String = "",
    )

    data class UiState(
        val title: String = "",
        val isPlaying: Boolean = false,
        val curTime: Long = 0,
        val totalTime: Long = 0,
        val prepared: Boolean = false,
        val errMsg: String = "",
        val status: String = "正在准备…",
        val buffering: Boolean = false,
        val hasVideoTrack: Boolean = false,
        val videoW: Int = 0,
        val videoH: Int = 0,
        val speed: Float = 1.0f,
        val showSpeedSheet: Boolean = false,
        val variants: List<HlsVariants.Variant> = emptyList(),
        val selectedVariant: String = "",
        val showQualitySheet: Boolean = false,
        val curEpIndex: Int = 0,
        val controlsVisible: Boolean = true,
        val autoNext: Boolean = true,
        val dragging: Boolean = false,
        val dragValue: Long = 0,
        /** FR-708：手势滑动 seek 进行中（用于显示目标时间浮层） */
        val seeking: Boolean = false,
        /** FR-708：滑动 seek 的目标位置 */
        val seekTarget: Long = 0,
        /**
         * FR-708：本次手势的**起点**（按下瞬间的播放位置）。
         *
         * 【为什么必须进状态】浮层的差值原先拿 `curTime` 算，而 `curTime`
         * 每秒被播放进度覆盖一次——同一次按压停住不动时，`+1:20` 会自己缩到
         * `+0:50`，方向甚至可能翻。起点是本次手势的常量，进状态才可断言。
         */
        val seekStartMs: Long = 0,
        val castEnabled: Boolean = false,
        val castBlockReason: String = "",
        val castMsg: String = "",
        val castLink: CastUtil.CastabilityKind = CastUtil.CastabilityKind.NotCastable,
    )

    var state by mutableStateOf(UiState())
        private set

    /**
     * 播放器窄接口。UI 层只用它，不认识 ExoPlayer。
     *
     * 【必须是可观察状态，不能是普通字段】真机实测踩坑：
     * `AndroidView` 的 `factory` 先执行、`update` 只在重组时执行，而引擎是在
     * `DisposableEffect` 里创建的——**factory 早于引擎创建**，首次 `update`
     * 拿到的必然是 `null`，`PlayerView.player = null` 就是黑屏。
     * 若引擎是普通字段，赋值不会触发重组，`update` 不再执行，黑屏就永久停留。
     *
     * 故用 `mutableStateOf`：引擎一就绪就触发重组，`update` 重跑并把 player 绑上去。
     */
    var engine: PlayerEngine? by mutableStateOf(null)
        private set

    /**
     * 内部改引擎。
     *
     * 保留 `private set` 的对外语义（外部只能读），同时把写入集中到一处：
     * 换集/换档会重建引擎，散落各处的赋值容易漏掉状态通知，
     * 而"漏掉通知 = 黑屏"正是真机踩过的坑。
     */
    private fun bindEngine(e: PlayerEngine?) {
        engine = e
    }

    private var resolvedUrl = params.url
    private var resolvedHeaders = ""
    private var engineReady = false
    private var urlReady = false
    private var starting = false
    private var lastSaveTick = 0
    private var hideTimerJob: kotlinx.coroutines.Job? = null

    /** 缓存的变体清单判据（`shouldShowPicker` 每次重组都算一遍没必要） */
    var showQualityEntry: Boolean = false
        private set

    init {
        state = state.copy(
            title = params.title,
            curEpIndex = params.episodeIndex,
        )
        // 【引擎必须在进入组合之前就建好】真机黑屏的第三个根因：
        // `key(eng)` 的 AndroidView 在**组合阶段**就读 `vm.engine`，而引擎原本是在
        // `DisposableEffect`（组合之后）里创建的 —— 首帧读到的必然是 null，
        // 且此后 `engine` 未变化就不会再重建视图。
        //
        // 用 Application 上下文：Media3 不需要 Activity/Window 上下文，
        // 用 Application 还能避免 ViewModel 持有 Activity 造成的泄漏。
        attachEngine()
        // 【登记活跃播放表面】与 `PlayerScreen` 的 `onDispose` 配对（那里 -1）。
        // 归零时 `PlaybackEngineHolder` 内部暂停播放——这是"离开后没有声音"的保证。
        engineHost.surfaceActive()
        viewModelScope.launch {
            // FR-410：连播开关先读盘再起播，避免首集播完时开关尚未就绪
            state = state.copy(autoNext = store.getAutoNext())

            // 【FR-711 单会话】若会话已在播（内嵌点剧集 → 点全屏的路径），
            // **什么都不做**：会话继续、引擎继续，本页只是全屏视图宿主。
            // 初版这里无条件 `resolveUrl()`——即使会话已在播也重跑一遍，
            // 是"点全屏又触发一次加载"的最后一个来源。
            val s = com.dengying.player.playback.PlaybackSession.currentState()
            if (s.hasSession && s.url == params.url) {
                PlaybackProbe.note(
                    "player-adopt-session",
                    "pos=${s.curTime} status=${s.status}",
                )
                urlReady = true
                startedUrl = s.url
                resolvedUrl = s.url
                resolvedHeaders = s.headers
                state = state.copy(
                    title = s.title,
                    curEpIndex = s.episodeIndex,
                    curTime = s.curTime,
                    totalTime = s.totalTime,
                    prepared = true,
                    buffering = s.buffering,
                    isPlaying = s.status == com.dengying.player.playback.PlaybackSession.Status.Playing,
                )
                return@launch
            }

            // 会话空闲（历史记录直达播放的路径）→ 由本页发起起播
            resolveUrl()
        }
    }

    // ---------------- SEC-005 ----------------

    /** 已知主机集合从源清单现算（清单是编译期常量且规模小，缓存反而要处理失效） */
    private fun guardUrl(url: String): UrlGuard.Result =
        UrlGuard.checkPlayUrl(url, UrlGuard.hostsOf(CmsSources.apiHosts()))

    // ---------------- 解析链 ----------------

    /**
     * 第一步：解析地址（分享页 → m3u8 → 主列表下钻）+ 可达性探测 + 自动换线。
     */
    fun resolveUrl() {
        viewModelScope.launch {
            val raw = params.url
            // 【FR-705 已解析地址直接起播】内嵌态切全屏时带来 `headersJson`，
            // 或地址本身已不是分享页 → **整段解析链跳过**。
            //
            // 真机探针抓到过不跳过的后果：
            //   [detail-playEpisode] urlLen=35   ← 又拿原始分享页地址
            //   [resolve-enter] len=35
            //   [after-resolveSharePage] resolved=false   ← 又解析一次，又失败
            // 用户感知为"点全屏好像又触发了一次缓存"，且等待期间无画面。
            //
            // 【token 型载荷不是 URL】FR-506 历史直达：playEpisode 传入的 ep.url
            // 对 token 源（桔子等）是 Base64 换链令牌（数百字节，docs/04 §16.16.3），
            // 既不是分享页也不是媒体地址——必须先走 resolveSourcePlay 换链。
            // 此前 `!isSharePage` 就判「已解析」，令牌被当真实地址直接送 guardUrl
            // （RejectScheme），真机表现=「播放地址协议不支持，只允许 http/https」。
            // headersJson 非空才是真「已解析」（内嵌切全屏带回了换链产物）。
            val src = CmsSources.byKey(params.source)
            // 【playGap 前置（§15.2.4）】播放链缺口在任何网络层/引擎动作之前告知。
            // 必须放在 alreadyResolved 早退**之前**：ap2233 的 ep.url 是站点播放页
            // （http 形态），会命中早退——检查放在后面就被绕过，引擎吃下播放页
            // 报 2001，用户只看到通用「播放失败」而不是可行动的缺口说明
            // （docs/04 §16.16.4 真机踩到）。
            // 注意判据含 `!capsOf(src).token`：token 型源的换链本身可能成功，
            // 提前拦会掐掉本可播的线路（两个BT 就是这种形态）。
            if (src != null && !capsOf(src).token && !src.playGap.isNullOrEmpty()) {
                PlaybackProbe.note("playGap-hit", "source=${src.key}")
                state = state.copy(errMsg = "该源暂不可播：${src.playGap}", status = "")
                return@launch
            }
            val tokenNeedsSwap = src != null && capsOf(src).token && params.headersJson.isEmpty()
            val alreadyResolved = !tokenNeedsSwap &&
                (params.headersJson.isNotEmpty() || !HlsUtil.isSharePage(raw))
            if (alreadyResolved) {
                PlaybackProbe.note(
                    "resolve-skip-already-resolved",
                    "headers=${params.headersJson.isNotEmpty()} isShare=${HlsUtil.isSharePage(raw)} len=${raw.length}",
                )
                val gate0 = guardUrl(raw)
                if (!gate0.allowed) {
                    state = state.copy(errMsg = gate0.notice, status = "")
                    return@launch
                }
                resolvedUrl = raw
                resolvedHeaders = params.headersJson
                startedUrl = ""
                urlReady = true
                state = state.copy(status = "正在准备…")
                refreshCastability()
                startEngine()
                // 早退路径同样要抓变体：分享页直达 / 真实 m3u8 直链都走这里，
                // 恰恰是最常见的多码率场景。漏了这行会让清晰度入口在该路径上永不出现
                // （2026-10-31 真机验收踩到）
                startBackgroundVariantsIfWanted(raw, fromSource = false)
                return@launch
            }

            com.dengying.player.player.PlaybackProbe.note(
                "resolve-enter", "len=${raw.length} source=${params.source} vodId=${params.vodId}"
            )
            // SEC-005 第一道闸：路由参数里的**真实 URL**先校验，未过的**不进入网络层**。
            // token 载荷（Base64 令牌）在此处必然 RejectScheme，不适用本闸——
            // 换链产出的真实地址在下方 g2 处校验（与 inline 会话链口径一致）。
            if (!tokenNeedsSwap) {
                val gate = guardUrl(raw)
                if (!gate.allowed) {
                    com.dengying.player.player.PlaybackProbe.note("SEC005-reject", "verdict=${gate.verdict}")
                    state = state.copy(errMsg = gate.notice, status = "")
                    return@launch
                }
            }
            resolvedUrl = raw
            // 【新一轮解析 = 要重新起播】重置幂等判据。
            // 初版漏了这一步：`resolveUrl` 会把 `resolvedUrl` 重置成原始地址，
            // 而 `startedUrl` 保留上次的值，两者不再相等 → 幂等判据放行 →
            // **重复 setMedia 同一地址**，表现为"进度条和播放失败一起来"。
            startedUrl = ""
            state = state.copy(status = "正在解析播放地址…")

            // AH 多协议源：token 型地址先经源适配器换出真实媒体地址，并带回播放所需请求头
            // （`src` 已在方法头部的 tokenNeedsSwap 判定处取出）
            // 【15.2 播放链缺口前置】playGap 已在方法头部提前拦截（含 alreadyResolved
            // 路径），此处不再重复——见上方「playGap 前置」注释。
            if (src != null && capsOf(src).token) {
                try {
                    val r = repo.resolveSourcePlay(src, params.vodId, raw, params.lineFrom)
                    if (r.url.isNotEmpty()) {
                        // SEC-005 第二道闸：适配器换出的地址同样要过校验
                        val g2 = guardUrl(r.url)
                        if (!g2.allowed) {
                            state = state.copy(errMsg = g2.notice, status = "")
                            return@launch
                        }
                        resolvedUrl = r.url
                        resolvedHeaders = r.headers
                    } else {
                        // 换链为空：若是已知播放链缺口，显式说明而不是静默失败
                        if (!src.playGap.isNullOrEmpty()) {
                            state = state.copy(errMsg = "该源暂不可播：${src.playGap}", status = "")
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.w("DY/Player", "source resolve ${src.key} failed")
                }
            }

            try {
                // 源适配器已换出地址时跳过分享页解析：换链结果是站点播放器直出的媒体流
                // （形态可能无扩展名，再当分享页抓会把流抓成乱码）
                val fromSource = resolvedHeaders.isNotEmpty()

                // 【判据只有"地址怎么来的"，与源的 token 能力位无关】
                //
                // 真机实测踩坑（探针日志）：初版按 `caps.token == false` 做"直链源短路"，
                // 认为 CMS 家族返回的一定是媒体地址。**这是错的**——CMS 源的
                // `vod_play_url` 里也可能是**分享页**地址，此时必须走解析链。
                // 短路后把分享页 HTML 直接交给播放器，必然报
                // `code=3003 Source error`，然后再换线成功——表现为"每部片第一次都失败"。
                //
                // 正确判据：只有**适配器换链产出**的地址（`fromSource`）才跳过解析；
                // 其余一律按形态判断是否需要解析（分享页 → 解析；已是媒体地址 → 原样）。
                val isSharePage = HlsUtil.isSharePage(resolvedUrl)

                if (!fromSource && isSharePage) {
                    PlaybackProbe.note("before-resolveSharePage", "urlLen=${resolvedUrl.length}")
                    val outcome = HlsUtil.resolvePlayUrlOrFail(resolvedUrl)
                    PlaybackProbe.note(
                        "after-resolveSharePage",
                        "resolved=${outcome.resolved} len=${outcome.url.length}",
                    )
                    if (!outcome.resolved) {
                        // 分享页提取不到媒体地址 → **直接换线**，不把这个 HTML 地址
                        // 交给播放器（那必然报 code=3003，白等一次失败再换线）。
                        // 真机实测：这一步能省掉 2-3 秒的无效等待。
                        //
                        // 同时记录页面结构特征，用于区分两类原因：
                        //  - 有 m3u8/iframe/player 线索但正则没提到 → 正则覆盖不够，可修
                        //  - 全都没有 → 页面靠 JS 渲染，静态抓取解不了，需换方案
                        val facts = HlsUtil.inspectSharePage(resolvedUrl)
                        PlaybackProbe.note(
                            "sharePage-unresolved",
                            "htmlLen=${facts.htmlLen} m3u8=${facts.hasM3u8} mp4=${facts.hasMp4} " +
                                "iframe=${facts.hasIframe} playerKw=${facts.hasPlayerKeyword} " +
                                "script=${facts.hasScript}",
                        )
                        // 把原始 HTML 落到 files/sharepage.html，供开发期看清地址书写形态。
                        // **只在 debug 构建做**——这是排查用的临时手段，
                        // 且页面内容可能含站点自有信息，不该进 release。
                        if (isDebug) {
                            PlaybackProbe.dumpSharePage(resolvedUrl)
                        }
                        state = state.copy(status = "正在寻找可用线路…")
                        if (autoSwitchLine()) return@launch
                        state = state.copy(
                            status = "",
                            errMsg = "该线路地址解析失败，候选线路均不可用",
                        )
                        return@launch
                    }
                    resolvedUrl = outcome.url
                } else {
                    PlaybackProbe.note(
                        "skip-resolveSharePage",
                        "fromSource=$fromSource isSharePage=$isSharePage",
                    )
                }

                // FR-412：变体清单与该地址怎么来的无关。
                // 只在地址**可能是主列表**（m3u8 形态）时才抓——非 m3u8 抓回来也是空，
                // 白等一次超时。
                //
                // 【不再阻塞起播】这次抓取是一次完整网络往返，而它只服务于「清晰度入口」，
                // 与"能不能播"无关。原先放在 startEngine() 之前，等于每次起播都白等一个
                // RTT（token 型源 fromSource 恒真，每次都走这条路）。改为起播后再抓：
                // 用户在等首帧时抓取与解码同时进行，入口就绪时自然出现。
                if (fromSource) {
                    // 换链地址交给播放器，由解码器判定——用 m3u8 探测链判它会误判为死线路
                    urlReady = true
                    state = state.copy(status = "正在准备…")
                    refreshCastability()
                    startEngine()
                    startBackgroundVariantsIfWanted(resolvedUrl, fromSource = true)
                    return@launch
                }

                // 【FR-703 去阻塞探测】真机探针实测：这里的可达性探测耗时 **3042ms**，
                // 而它的结果**当前已不作为起播准入条件**（下面 `probe-suspect-but-try-play`
                // 分支就是证据）。既然不阻塞决策，就不该阻塞用户。
                //
                // 改为：**立即起播**，探测放后台跑；结果只用于"播放器报错后是否值得换线"。
                //
                // 代价：弱网下"探测本可提前发现死线路"的能力丧失，用户多等一次播放器报错
                // （约 1-2s）。权衡：好源占多数（实测 tier1 三源均可播），多数用户省 3s。
                PlaybackProbe.note("probe-deferred-start", "urlLen=${resolvedUrl.length}")
                state = state.copy(status = "正在准备…")
                urlReady = true
                refreshCastability()
                startEngine()
                startBackgroundProbe()
                startBackgroundVariantsIfWanted(resolvedUrl, fromSource = false)
                return@launch
            } catch (e: Exception) {
                // 解析失败则用原始地址兜底
                PlaybackProbe.note(
                    "resolve-exception",
                    "${e::class.simpleName ?: "Exception"}: ${e.message?.take(120)}",
                )
                urlReady = true
                state = state.copy(status = "正在准备…")
                refreshCastability()
                startEngine()
                startBackgroundVariantsIfWanted(raw, fromSource = false)
            }
        }
    }

    /**
     * 后台可达性探测（FR-703）。
     *
     * 结果**不阻塞起播**，只写探针并缓存在 [lastProbe] 里，
     * 供 [switchLineOnPlaybackError] 判断"是线路问题还是播放器问题"。
     *
     * 失败（异常）静默忽略——它只是诊断信息，不是功能路径。
     */
    private fun startBackgroundProbe() {
        viewModelScope.launch {
            val url = resolvedUrl
            try {
                val t0 = Clock.nowMs()
                val probe = kotlinx.coroutines.withContext(ioDispatcher) {
                    HlsUtil.probeReachability(url)
                }
                lastProbe = probe
                PlaybackProbe.note(
                    "bg-probe",
                    "elapsed=${Clock.nowMs() - t0} m3u8=${probe.m3u8Ok} " +
                        "seg=${probe.segOk} host=${probe.segHost}",
                )
            } catch (e: Exception) {
                PlaybackProbe.note("bg-probe-failed", e::class.simpleName ?: "Exception")
            }
        }
    }

    /** 最近一次后台探测结果（诊断用，不参与起播准入） */
    private var lastProbe: HlsUtil.ReachResult? = null

    /** 判据与调度的分离：非 m3u8 且非换链来源时不抓，省一次必然超时的往返 */
    private fun startBackgroundVariantsIfWanted(url: String, fromSource: Boolean) {
        val wants = HlsVariants.wantsVariants(url, fromSource)
        if (!wants) {
            PlaybackProbe.note("skip-loadVariants", "not-m3u8")
            return
        }
        startBackgroundVariants(url)
    }

    /**
     * 后台抓取变体清单（FR-412）。
     *
     * 【为什么放到起播之后】这次抓取是一次完整的网络往返，但它的产物只决定
     * 「清晰度入口显不显示」。放在 `startEngine()` 之前等于让用户为一个与
     * 能否播放无关的请求白等一个 RTT——token 型源（`fromSource` 恒真）每次都走
     * 这条路径。移到起播之后，抓取与解码并行，入口就绪时自然出现。
     *
     * 抓回来时用户可能已经换了集：地址不再匹配就丢弃，避免把上一集的档位
     * 盖到当前集上。
     */
    private fun startBackgroundVariants(url: String) {
        viewModelScope.launch {
            try {
                val t0 = Clock.nowMs()
                val variants = kotlinx.coroutines.withContext(ioDispatcher) {
                    HlsUtil.loadVariants(url)
                }
                PlaybackProbe.note(
                    "bg-variants",
                    "elapsed=${Clock.nowMs() - t0} count=${variants.size}",
                )
                // 地址已变（换集/换线/换档）→ 本次结果作废
                if (HlsVariants.isStale(url, resolvedUrl)) {
                    PlaybackProbe.note("bg-variants-stale", "urlChanged=true")
                    return@launch
                }
                // 先写判据再写状态：`showQualityEntry` 是普通字段，靠随后的
                // `state` 更新触发重组才能被读到
                showQualityEntry = HlsVariants.shouldShowPicker(variants)
                state = state.copy(
                    variants = variants,
                    selectedVariant = variants.firstOrNull()?.url ?: "",
                )
            } catch (e: Exception) {
                // 清晰度入口是锦上添花，抓不到就当不支持多码率，不影响播放
                PlaybackProbe.note("bg-variants-failed", e::class.simpleName ?: "Exception")
            }
        }
    }

    /**
     * 播放器报错时的自动换线（FR-411）。
     *
     * ## 为什么只做一轮（真机实测的教训）
     *
     * 初版在换线成功后会 `switchTried = false` 允许再换，配合"任何 error 都换线"
     * 就构成了死循环：同一地址反复报 `code=3003` → 反复换线 → 换到的新地址也报错
     * → 无限循环。探针日志里能看到 `[engine-error] code=3003` 与
     * `[startEngine]` 交替出现 6 次以上。
     *
     * 现在的策略：**本次播放会话只自动换一轮**。换过之后仍失败就停在那里报错，
     * 由用户决定换线路或换片——这比"无限自动换线"对用户更有意义：
     * 后者表现为界面一直闪、进度条来回跳，且永远看不到明确结论。
     *
     * 用户手动换集/换档/重试时会重置本标志，给他们一条明确的恢复路径。
     */
    private fun switchLineOnPlaybackError() {
        if (switchTried) {
            PlaybackProbe.note("switch-already-tried")
            return
        }
        switchTried = true
        viewModelScope.launch {
            PlaybackProbe.note("auto-switch-on-error")
            val ok = autoSwitchLine()
            if (!ok) {
                state = state.copy(
                    status = "",
                    errMsg = "该线路不可播，候选线路均不可用",
                )
            }
            // 注意：**成功也不重置 switchTried** —— 见上面的死循环说明
        }
    }

    /**
     * 本次播放会话是否已自动换过线。
     *
     * 只在用户显式动作（换集/换档/重试）时重置，给出一条明确的恢复路径。
     */
    private var switchTried = false

    /**
     * 自动换线：同源其他线路 → 跨源同名资源。
     *
     * 每个候选都要过「解析 + 探测」全链路，谁健康用谁。
     * @return 成功返回 true（`resolvedUrl` 已更新为可用地址）
     */
    private suspend fun autoSwitchLine(): Boolean {
        val candidates = ArrayList<String>()

        // 1) 同源其他线路：token 型源的候选同样是 token，必须经 resolveSourcePlay 换出真实地址，
        //    否则拿到的是站点页面地址（29-41 字符），必死
        val cur = CmsSources.byKey(params.source)
        val sameSrcToken = cur != null && capsOf(cur).token
        for (u in params.altUrls) {
            if (u.isEmpty() || u == params.url) continue
            if (sameSrcToken && cur != null) {
                val r = repo.resolveSourcePlay(cur, params.vodId, u, params.lineFrom)
                if (r.url.isNotEmpty()) candidates += r.url
            } else {
                candidates += u
            }
        }

        // 2) 跨源同名资源（需要 vodName；搜索 → 详情 → 首集地址）
        val vodName = params.vodName
        if (vodName.isNotEmpty() && params.source.isNotEmpty()) {
            state = state.copy(status = "正在寻找备用线路…")
            val alts = repo.findAltSources(repo.allSources(), params.source, vodName)
            for (alt in alts) {
                val src = CmsSources.byKey(alt.sourceKey) ?: continue
                val u = repo.fetchAltPlayUrl(src, alt.vodId, params.episodeIndex)
                if (u.isNotEmpty()) candidates += u
            }
        }

        // 逐个候选：解析 + 探测。
        //
        // 【判定放宽的理由与主线路一致】探测不可达不等于不可播——真机实测过
        // `seg=false` 但播放器能出画面的情形。故这里改为"**清单可达即用**"：
        // 候选的清单能拉到就交给播放器，由解码器给出最终判定；
        // 若播放器报错，`switchLineOnPlaybackError` 会继续试下一个候选。
        //
        // 这样做的代价是可能选中一条实际不可播的候选（多试一次），
        // 收益是不会误杀可播的候选——后者的代价是"明明能播却一直黑屏"，更大。
        var i = 0
        for (cand in candidates) {
            i++
            state = state.copy(status = "自动切换线路 $i/${candidates.size}…")
            try {
                val curUrl = HlsUtil.resolvePlayUrl(cand)
                val p2 = HlsUtil.probeReachability(curUrl)
                PlaybackProbe.note(
                    "switch-probe",
                    "i=$i/${candidates.size} m3u8=${p2.m3u8Ok} seg=${p2.segOk}",
                )
                if (p2.m3u8Ok) {
                    resolvedUrl = curUrl
                    urlReady = true
                    // 【必须清 errMsg】换线成功后如果不清空失败提示，
                    // 界面会**同时**显示进度条（播放器已就绪）与「播放失败」（旧错误态）——
                    // 真机反馈"进度条和播放失败一起来"的直接原因之一。
                    // 同理清掉换线前留下的失败态，让新线路从干净状态开始。
                    state = state.copy(
                        status = "正在准备…",
                        errMsg = "",
                        prepared = false,
                        buffering = false,
                    )
                    refreshCastability()
                    startEngine()
                    return true
                }
            } catch (e: Exception) {
                // 候选解析失败，继续下一个
            }
        }
        return false
    }

    // ---------------- 引擎起停 ----------------

    fun attachEngine() {
        if (engine != null) return
        // 【用全局唯一引擎】官方推荐做法，见 PlaybackEngineHolder 的说明。
        // 初版在这里新建引擎，与详情页内嵌位各持一个，导致解码器抢占。
        val e = engineHost.engine()
        bindEngine(e)
        e.addListener(object : PlayerListener {
            override fun onReady(durationMs: Long) {
                Log.w("DY/Player", "engine ready duration=$durationMs")
                PlaybackProbe.note("engine-ready", "duration=$durationMs")
                // 【清错误态】播放器就绪说明地址是好的——任何残留的失败提示都必须清掉，
                // 否则会出现"进度条 + 播放失败"同屏。这是最后一道保证：
                // 无论前面哪条路径漏了清理，就绪事件都会把状态收敛回正常。
                state = state.copy(
                    prepared = true,
                    totalTime = durationMs,
                    status = "",
                    errMsg = "",
                    buffering = false,
                )
                // prepared 后按续播点恢复位置（FR-405：小于 3 秒视为从头）
                restorePosition()
                pendingSeekTargetMs = 0L
                state = state.copy(controlsVisible = true)
                scheduleHide()
            }

            override fun onPlaying() {
                Log.w("DY/Player", "engine playing")
                state = state.copy(isPlaying = true, status = "", buffering = false)
                // FR-410：每 5 个时间回调落盘一次
                lastSaveTick = 0
                viewModelScope.launch {
                    while (true) {
                        delay(1000)
                        val eng = engine ?: break
                        if (!eng.isPlaying) continue
                        // 【拖动中不覆盖】进度条此刻显示的是 `dragValue`，
                        // 而整块状态是整体替换的；顺手改写 `curTime` 会让松手后的
                        // 差值基准漂移，浮层的差值与方向都会跟着变（P-02 的抖动来源之一）。
                        if (state.dragging) continue
                        val pos = eng.currentPosition
                        if (!SeekPreview.shouldAdoptEnginePosition(pos, pendingSeekTargetMs)) {
                            // seek 尚未落地：保持目标位置，避免"先跳回旧位置再跳到新位置"
                            continue
                        }
                        state = state.copy(curTime = pos)
                        if (pendingSeekTargetMs > 0L) pendingSeekTargetMs = 0L
                        lastSaveTick++
                        if (lastSaveTick % 5 == 0) persistHistory()
                    }
                }
            }

            override fun onPaused() {
                state = state.copy(isPlaying = false)
            }

            override fun onBuffering(active: Boolean) {
                state = state.copy(buffering = active)
            }

            override fun onCompleted() {
                state = state.copy(isPlaying = false)
                viewModelScope.launch { onCompleted() }
            }

            override fun onError(kind: PlaybackErrorKind, code: Int, message: String) {
                Log.w("DY/Player", "engine error kind=$kind code=$code msg=$message")
                PlaybackProbe.note("engine-error", "code=$code msg=${message.take(100)}")

                // 【错误分类，而不是一律当致命】真机实测踩坑：初版把任何 error 都
                // 当成"线路不可播"并触发换线，结果 `code=3003`（Source error，
                // 即 `ERROR_CODE_IO_UNSPECIFIED`）反复出现时陷入死循环——
                // 同一地址被反复 startEngine，界面同时显示进度条与"播放失败"。
                //
                // Media3 对 IO 类错误**自己会重试**（默认 3 次退避重试），
                // 应用层不该抢它的活。只有以下两类才是应用该介入的：
                //  - 解码/格式不支持 → 换线路确实可能解决
                //  - 源永久失效（如 404/403 语义）→ 换线路
                //
                // 【P4 变更】原来这里是一张 `when (code)` 的 Media3 错误码表，现在
                // 分类已上移到引擎实现（`Media3Engine.errorKindOf`，见 `PlayerEngine.kt`
                // 的 `PlaybackErrorKind`）。本 VM 只认枚举，不再认识任何 Media3 常量——
                // 否则 iOS 侧没有对应常量可判。判定结果与旧表逐条等价：
                // `Network` 是唯一非致命分支，其余（`SourceGone`/`Decode`/`Unknown`）都换线。
                val fatal = kind != PlaybackErrorKind.Network

                if (!fatal) {
                    // 可恢复：不显示失败，只提示正在重试；播放器会自己退避重试
                    state = state.copy(status = "网络抖动，正在重试…")
                    PlaybackProbe.note("error-recoverable", "code=$code")
                    return
                }

                state = state.copy(
                    status = "",
                    // FR-406 第三类：解码或地址失效。错误码进日志，原文不显示给用户
                    errMsg = "播放失败（解码错误或地址失效）",
                )
                // FR-411：只对致命错误换线，且**只做一轮**
                switchLineOnPlaybackError()
            }

            override fun onVideoSize(width: Int, height: Int) {
                Log.w("DY/Player", "videoSize ${width}x$height")
                state = state.copy(
                    videoW = width,
                    videoH = height,
                    // 0x0 说明还没有视频轨：这就是"有声音没画面"的判定依据
                    hasVideoTrack = width > 0 && height > 0,
                )
            }
        })
    }

    /**
     * 起播（幂等）。
     *
     * ## 偶现"播放失败"的根因（真机反馈定位）
     *
     * 初版把 `starting` 当"正在起播"的锁，置 true 后**只在换集/换档/重试路径复位**。
     * 于是正常首次起播成功后 `starting` 永远停在 true，之后任何
     * `startEngine` 调用都被首行的 `if (starting) return` 挡掉——
     * 包括**换线成功后的重新起播**，表现为"偶现播放失败"（只在需要二次起播时暴露）。
     *
     * 另外两个隐患：
     *  - `engine?.setMedia(...)` 用安全调用，若引擎为 null 会**静默跳过**，
     *    而 `starting` 已置 true、无人复位 → 死锁
     *  - `starting = true` 之后的语句若抛异常，标志不回滚
     *
     * 修法：`starting` 只覆盖**实际的起播动作**（短临界区），并用 try/finally 保证复位；
     * 另加 `startedUrl` 记录"当前已交给播放器的地址"，用它做真正的幂等判据。
     */
    private fun startEngine() {
        if (!urlReady) return
        // 引擎在 init 里已建；若因换集被释放而尚未重建，先重建再起播
        if (engine == null) {
            attachEngine()
        }
        val eng = engine ?: run {
            // 引擎建不出来：留痕并返回，**不置 starting**，避免死锁
            PlaybackProbe.note("startEngine-no-engine")
            return
        }

        // 幂等：同一地址已交给播放器就不再重复 setMedia（会打断正在进行的播放）。
        //
        // 【但 play() 必须调】真机踩坑：切全屏时
        // `PlaybackEngineHolder.surfaceInactive()` 计数瞬间归零 → 引擎被 pause；
        // 随后全屏页走到这里，幂等命中直接 return——**连 play 也跳过了**，
        // 全屏页就一直停在暂停帧。用户反馈"直接播放启动失败了"的根因。
        //
        // 幂等的目的是防重复 `setMedia`（那会重建解码管线），
        // 不是防"恢复播放"。play() 是幂等操作，重复调无副作用。
        if (startedUrl == resolvedUrl) {
            PlaybackProbe.note("startEngine-skip-setMedia-resume-play")
            eng.play()
            return
        }
        if (starting) {
            PlaybackProbe.note("startEngine-skip-inflight")
            return
        }

        val resume = resumePositionOrZero()
        starting = true
        viewModelScope.launch {
            try {
                state = state.copy(status = "正在初始化播放器…")
                PlaybackProbe.note(
                    "startEngine",
                    "urlLen=${resolvedUrl.length} headers=${resolvedHeaders.isNotEmpty()} resume=$resume",
                )
                // 【分两步，不能整个丢进 IO 线程】真机探针抓到过
                // `IllegalStateException: Player is accessed on the wrong thread`：
                //  - `SimpleCache` 构造是同步阻塞的 → 必须 IO（否则首次起播卡几百毫秒）
                //  - ExoPlayer 的 API（setMediaSource/prepare/play）**必须在主线程** → 不能 IO
                // 故：先 IO 预热缓存，再回主线程调 ExoPlayer。
                // 预热后 `MediaCache.wrap` 命中单例立即返回，主线程不再被阻塞。
                com.dengying.player.data.cache.MediaCache.warmUp()
                eng.setMedia(resolvedUrl, resolvedHeaders, resume)
                eng.setSpeed(state.speed)
                eng.play()
                startedUrl = resolvedUrl
                // 【会话登记（FR-711 不变量）】本页自主起播不经过 PlaybackSession.play，
                // 会话单例此前一直 Idle/urlEmpty——返回详情页时 resumeInlineIfPlaying 与
                // 详情页全屏键的 hasSession 检查全部静默失效（docs/05 §9.8）。
                // 引擎监听/进度循环全局生效，这里只补身份字段；失败路径不登记（无 playback 可见）。
                com.dengying.player.playback.PlaybackSession.registerPlayback(
                    url = resolvedUrl,
                    headers = resolvedHeaders,
                    title = params.title,
                    vodName = params.vodName,
                    vodId = params.vodId,
                    source = params.source,
                    vodUri = params.vodUri,
                    episodeIndex = params.episodeIndex,
                    episodeName = params.episodeName,
                )
            } catch (e: Exception) {
                PlaybackProbe.note("startEngine-exception", "${e::class.simpleName ?: "Exception"}: ${e.message?.take(120)}")
                state = state.copy(status = "", errMsg = "播放器初始化失败，请重试")
            } finally {
                // 无论成败都复位——这是"偶现"不再复发的关键
                starting = false
            }
        }
    }

    /**
     * 当前已交给播放器的地址。
     *
     * 换集/换档时会被重置（那时地址变了，需要重新 setMedia）。
     */
    private var startedUrl: String = ""

    /** 供视图挂载后调用：引擎就绪即尝试起播 */
    fun onEngineAttached() {
        if (urlReady) startEngine()
    }

    /** FR-405：按（影片 + 线路 + 集数）精确恢复，避免跳到别的集 */
    /**
     * `setMedia` 时要用的起播位置。
     *
     * 【FR-705】优先用路由显式传入的 [PlayerParams.startAtMs]（内嵌态切全屏的场景）——
     * 这个值来自"用户刚刚看到的位置"，比历史记录更准、也更符合预期。
     *
     * 未指定时返回 0，真实续播点由 [restorePosition] 在 `onReady` 后异步 seek。
     * 分两步的理由：历史续播点要读盘（IO），不该阻塞 `setMedia`；
     * 而 `startAtMs` 是现成的值，直接交给 `setMedia` 可以让首帧就落在正确位置。
     */
    private fun resumePositionOrZero(): Long = params.startAtMs

    private fun restorePosition() {
        // 【先落探针再判早退】真机“手动 seek 后画面又被拉回去”就是从这里发生的。
        // 早期只记“实际 seek 了”的分支，所以看不出到底是“守卫没生效”
        // 还是“根本走不到这里”。把早退原因写进去。
        val load = ResumeRestore.shouldLoad(
            startAtMs = params.startAtMs,
            vodId = params.vodId,
            source = params.source,
            alreadyRequested = resumeRestoreRequested,
        )
        PlaybackProbe.note(
            "resume-restore-check",
            "startAtMs=${params.startAtMs} vodId=${params.vodId} source=${params.source} " +
                "requested=$resumeRestoreRequested manualSeek=$manualSeekOccurred " +
                "load=${load.shouldLoad} why=${load.reason}",
        )
        // 显式指定了起播位置时不再用历史值覆盖——那会让用户“跳回上次看到的地方”，
        // 而不是“接着刚才的位置播”
        if (!load.shouldLoad) return
        resumeRestoreRequested = true
        viewModelScope.launch {
            val pos = store.getEpisodePosition(params.vodId, params.source, state.curEpIndex)
            // 小于 3 秒视为从头播放——避免用户从片头位置收到一次无意义的 seek
            val apply = ResumeRestore.apply(pos, manualSeekOccurred)
            if (apply.shouldSeek) {
                PlaybackProbe.note("resume-restore", "ep=${state.curEpIndex} pos=${apply.targetMs}")
                engine?.seekTo(apply.targetMs)
            }
        }
    }

    /** FR-507：续播点未落盘必须可见（否则用户以为"接着看"生效，实际回到片头） */
    private suspend fun persistHistory() {
        if (params.vodId == 0L || params.source.isEmpty()) return
        val item = HistoryItem(
            vod_id = params.vodId,
            vod_name = params.vodName.ifEmpty { state.title },
            vod_pic = params.vodPic,
            source = params.source,
            vod_uri = params.vodUri,
            episodeIndex = state.curEpIndex,
            episodeName = params.episodeName,
            position = state.curTime,
            duration = state.totalTime,
            updateTime = Clock.nowMs(),
        )
        if (!store.saveHistory(item)) {
            state = state.copy(errMsg = state.errMsg.ifEmpty { "本地存储不可用，观看记录未保存" })
        }
    }

    // ---------------- 控制 ----------------

    fun togglePlay() {
        val e = engine ?: return
        if (state.isPlaying) e.pause() else e.play()
    }

    fun onDragStart() {
        manualSeekOccurred = true
        state = state.copy(dragging = true, dragValue = state.curTime)
    }

    fun onDrag(value: Long) {
        manualSeekOccurred = true
        state = state.copy(dragValue = value)
    }

    /** FR-409：拖动结束只 seek 一次，之后回到真实值 */
    fun onDragEnd() {
        manualSeekOccurred = true
        engine?.seekTo(state.dragValue)
        state = state.copy(dragging = false, curTime = state.dragValue)
    }

    // ---------------- 滑动 seek（FR-708） ----------------

    /**
     * 手势滑动 seek 的起始值。
     *
     * 与进度条拖动**共用** `dragging`/`dragValue` 状态——
     * 两套独立的拖动态会互相覆盖（用户可能先拖进度条、再滑画面），
     * 共用一套就不存在"谁覆盖谁"的问题。
     */
    private var seekStartMs = 0L

    /**
     * 已提交但尚未由引擎确认的 seek 目标；0 表示没有待生效的 seek。
     *
     * 用途见 `SeekPreview.shouldAdoptEnginePosition`：提交后播放器要重新缓冲，
     * 这段时间 `currentPosition` 仍停在旧位置。若照抄，进度条会先跳回旧位置再跳到
     * 新位置——用户看到的就是"进度条来回跳"。
     */
    private var pendingSeekTargetMs = 0L

    /** 滑动 seek 开始：记下起点，进入拖动态 */
    fun onSeekStart() {
        manualSeekOccurred = true
        seekStartMs = state.curTime
        // 手势进行中不让自动隐藏计时器把控制层关掉（否则松手时找不到按钮）
        cancelHide()
        state = state.copy(
            dragging = true,
            seeking = true,
            seekStartMs = seekStartMs,
            dragValue = state.curTime,
            seekTarget = state.curTime,
            // 【P-02 的关键一半】滑动 seek 时**临时唤出控制层**。
            // 用户说的"直接滑屏幕没有进度条动画"，指的正是底部那条进度条：
            // 控制层 4 秒后自动隐藏，此时滑动只有播放位置在跳，屏幕上没有任何
            // 随时间推进的东西。主流播放器（B 站 / 爱奇艺 / YouTube）都会在手势
            // 开始时把控制层唤出来，让进度条跟着手指走。
            controlsVisible = true,
        )
        PlaybackProbe.note("seek-start", "from=${state.curTime} controls=shown")
    }

    /**
     * 滑动中更新预览位置。
     *
     * 【为什么滑动中不直接 seek】连续 seek 会让播放器频繁重建解码管线，
     * 表现为画面抖动甚至卡死。只更新预览值，松手才真正 seek（FR-708 验收条件③）。
     *
     * @param deltaX 累计横向位移（像素）
     * @param viewWidth 画面宽度（像素）
     */
    fun onSeekDrag(deltaX: Float, viewWidth: Float) {
        if (viewWidth <= 0f || state.totalTime <= 0) return
        // 口径与单位换算在 `domain/SeekPreview`：滑过一个屏幕宽度 = 走完整片
        val target = SeekPreview.targetOf(
            fromMs = seekStartMs,
            deltaX = deltaX,
            viewWidth = viewWidth,
            totalMs = state.totalTime,
        )
        state = state.copy(dragValue = target, seekTarget = target)
    }

    /** 松手：真正 seek 并复位拖动/滑动态 */
    fun onSeekCommit() {
        val target = state.dragValue
        engine?.seekTo(target)
        PlaybackProbe.note("seek-commit", "target=$target delta=${target - seekStartMs}")
        pendingSeekTargetMs = target
        // 【为什么要 pendingSeekTargetMs】松手后播放器要重新缓冲，这期间
        // `currentPosition` 仍停在旧位置。旧实现松手即把 `curTime` 设为目标，
        // 下一秒又被引擎的旧位置覆盖回去——进度条先跳过去、再跳回来，
        // 用户看到的就是"进度条乱跳"。见 [SeekPreview.shouldAdoptEnginePosition]。
        //
        // 预览浮层在松手这一刻收起（160ms 淡出），随后的等待由既有缓冲环 + 状态文案
        // 承担——两层反馈不重叠，也不会让浮层在缓冲期间挡着画面。
        state = state.copy(
            dragging = false,
            seeking = false,
            curTime = target,
            seekTarget = target,
        )
        // 手势唤出的控制层重新开始 4 秒计时（不在这里取消的话会一直挂在画面上）
        scheduleHide()
    }

    /** 手势取消（被父容器拦截等）：放弃本次 seek，回到原位置 */
    fun onSeekCancel() {
        PlaybackProbe.note("seek-cancel")
        pendingSeekTargetMs = 0L
        state = state.copy(
            dragging = false,
            seeking = false,
            dragValue = seekStartMs,
            seekTarget = seekStartMs,
        )
    }

    /** FR-416：会话级倍速（不跨集持久） */
    fun setSpeed(v: Float) {
        state = state.copy(speed = v, showSpeedSheet = false)
        engine?.setSpeed(v)
        scheduleHide()
    }

    fun toggleSpeedSheet() {
        state = state.copy(showSpeedSheet = !state.showSpeedSheet, showQualitySheet = false)
        if (state.showSpeedSheet) cancelHide() else scheduleHide()
    }

    fun toggleQualitySheet() {
        state = state.copy(showQualitySheet = !state.showQualitySheet, showSpeedSheet = false)
        if (state.showQualitySheet) cancelHide() else scheduleHide()
    }

    /** FR-412：当前档位标签 */
    fun qualityLabel(): String {
        val v = state.variants.firstOrNull { it.url == state.selectedVariant }
            ?: state.variants.firstOrNull()
        return v?.let { HlsVariants.variantLabel(it) } ?: ""
    }

    /**
     * FR-412：切换清晰度档位。
     *
     * 换档**重建引擎**并保留进度——用户换清晰度不是为了从头看。
     * 新档位先探测；不可达则保留原档并给出可见提示，不静默回退。
     */
    fun applyQuality(url: String) {
        if (url.isEmpty() || url == state.selectedVariant) return
        switchTried = false
        val keep = state.curTime
        state = state.copy(showQualitySheet = false, selectedVariant = url, status = "正在切换清晰度…")
        resolvedUrl = url

        viewModelScope.launch {
            // 显式保证主线程：ExoPlayer 的 API 全部要求主线程（真机踩过 wrong-thread 异常）
            engineHost.surfaceInactive()
            bindEngine(null)
            starting = false
            urlReady = false
            state = state.copy(prepared = false, buffering = false)

            val probe = HlsUtil.probeReachability(resolvedUrl)
            if (!(probe.m3u8Ok && (probe.segOk || probe.segHost.isEmpty()))) {
                state = state.copy(
                    errMsg = "该清晰度暂时不可用，请换回原档位或重试",
                    status = "",
                )
                return@launch
            }
            state = state.copy(curTime = keep, totalTime = 0, prepared = false, status = "")
            urlReady = true
            // 引擎已释放，由 UI 侧重新挂载时懒建再起播
            pendingResumeMs = keep
        }
    }

    /** 换档/换集后待恢复的进度（引擎重建时塞给 setMedia） */
    private var pendingResumeMs: Long = 0
    /** 历史续播点每集只恢复一次；重缓冲后的 READY 不得覆盖用户的新 seek。 */
    private var resumeRestoreRequested: Boolean = false
    private var manualSeekOccurred: Boolean = false

    fun consumePendingResume(): Long {
        val v = pendingResumeMs
        pendingResumeMs = 0
        return v
    }

    // ---------------- 换集（FR-410 / FR-415） ----------------

    fun hasEp(offset: Int): Boolean {
        if (params.epUrls.isEmpty()) return false
        val t = state.curEpIndex + offset
        return t >= 0 && t < params.epUrls.size
    }

    /**
     * FR-410：本集播放完成。
     *
     * 顺序有约束：
     *  1. 先落盘「看完」（position = duration），必须在切集之前——否则会用新集序号写错条目
     *  2. 再判定连播：开关关闭 / 末集 / 集清单缺失 → 停在本集结束态
     */
    private suspend fun onCompleted() {
        state = state.copy(curTime = if (state.totalTime > 0) state.totalTime else state.curTime)
        persistHistory()
        if (!state.autoNext) return
        if (!hasEp(1)) return
        switchEpisode(1)
    }

    /** FR-415：切到相邻集——落盘旧集位置、释放旧引擎、再启动新会话 */
    fun switchEpisode(offset: Int) {
        if (!hasEp(offset)) return
        // 用户显式换集：重置换线标志，给他一条明确的恢复路径
        switchTried = false
        val target = state.curEpIndex + offset
        viewModelScope.launch {
            persistHistory()
            // 显式保证主线程：ExoPlayer 的 API 全部要求主线程（真机踩过 wrong-thread 异常）
            engineHost.surfaceInactive()
            bindEngine(null)
            starting = false
            urlReady = false

            val epName = params.epNames.getOrElse(target) { "" }
            state = state.copy(
                errMsg = "",
                prepared = false,
                curTime = 0,
                totalTime = 0,
                curEpIndex = target,
                title = if (params.vodName.isNotEmpty()) "${params.vodName} $epName" else epName,
                buffering = false,
            )
            resolvedUrl = params.epUrls.getOrElse(target) { "" }
            resolvedHeaders = ""
            pendingResumeMs = 0
            resumeRestoreRequested = false
            manualSeekOccurred = false
            resolveUrl()
        }
    }

    fun setAutoNext(on: Boolean) {
        state = state.copy(autoNext = on)
        viewModelScope.launch { store.setAutoNext(on) }
    }

    // ---------------- 全屏（FR-413） ----------------


    // ---------------- 控制层显隐 ----------------

    fun toggleControls() {
        state = state.copy(controlsVisible = !state.controlsVisible)
        if (!state.controlsVisible) {
            state = state.copy(showSpeedSheet = false, showQualitySheet = false)
            cancelHide()
        } else {
            scheduleHide()
        }
    }

    fun scheduleHide() {
        cancelHide()
        hideTimerJob = viewModelScope.launch {
            delay(4000)
            // 【拖动/滑动进行中不许隐藏】手势唤出的控制层若在手指还按着的时候
            // 被计时器关掉，进度条会在滑动途中消失——正是本次要修的那个现象。
            if (state.dragging || state.seeking) {
                PlaybackProbe.note("hide-skip", "dragging=${state.dragging} seeking=${state.seeking}")
                return@launch
            }
            state = state.copy(controlsVisible = false, showSpeedSheet = false, showQualitySheet = false)
        }
    }

    private fun cancelHide() {
        hideTimerJob?.cancel()
        hideTimerJob = null
    }

    // ---------------- 重试（FR-407） ----------------

    /** 重试：区分失败阶段——解析/探测失败重走全链路；播放器错误只重建引擎 */
    fun retry() {
        switchTried = false
        state = state.copy(errMsg = "", status = "正在重试…", prepared = false)
        viewModelScope.launch {
            // 显式保证主线程：ExoPlayer 的 API 全部要求主线程（真机踩过 wrong-thread 异常）
            engineHost.surfaceInactive()
            bindEngine(null)
            starting = false
            if (urlReady) startEngine() else resolveUrl()
        }
    }

    // ---------------- 投屏可投性（FR-419） ----------------

    /**
     * 重算源可投性。判定为纯函数，见 [CastUtil]。
     *
     * `castEnabled` 只表达「这个源能不能投」，不掺连接中状态——
     * 一旦掺入，投播组件会因为自身把状态置为 Connecting 而把自己从界面上抹掉。
     */
    private fun refreshCastability() {
        // 判定输入只有两项——解析后的地址、是否依赖自定义请求头。
        // 换链来源作为第三项只影响「可投/可试」的区分，不参与禁用判定。
        val fromChain = resolvedHeaders.isNotEmpty() || params.source.isNotEmpty()
        val c = CastUtil.decideCastability(resolvedUrl, resolvedHeaders.isNotEmpty(), fromChain)
        state = state.copy(
            castEnabled = CastUtil.isCastEntryEnabled(c),
            castBlockReason = CastUtil.castBlockText(c.reason),
        )
    }

    // ---------------- 生命周期 ----------------

    override fun onCleared() {
        super.onCleared()
        // FR-408：离开页面立即落盘位置并释放引擎
        val pos = state.curTime
        val snap = state
        val vmScope = viewModelScope
        // onCleared 里 viewModelScope 已取消，用 runBlocking 的廉价替代：
        // 直接同步写（SharedPreferences.commit 是同步的），不依赖协程
        if (params.vodId != 0L && params.source.isNotEmpty()) {
            val item = HistoryItem(
                vod_id = params.vodId,
                vod_name = params.vodName.ifEmpty { snap.title },
                vod_pic = params.vodPic,
                source = params.source,
                vod_uri = params.vodUri,
                episodeIndex = snap.curEpIndex,
                episodeName = params.episodeName,
                position = pos,
                duration = snap.totalTime,
                updateTime = Clock.nowMs(),
            )
            // 用独立协程避免阻塞主线程；释放引擎必须立即做。
            // 不用 Thread（Kotlin/Native 上没有 java.lang.Thread），改用 ioDispatcher 上的
            // 一次性 CoroutineScope：语义与「起一个后台线程写盘」等价，且跨平台。
            kotlinx.coroutines.CoroutineScope(ioDispatcher).launch {
                store.saveHistoryBlocking(item)
            }
        }
        // `onCleared` 由 ViewModel 框架在**主线程**调用，故直接释放即可，
        // 不需要 withContext（这里也不是协程上下文）。
        engineHost.surfaceInactive()
        bindEngine(null)
    }
}
