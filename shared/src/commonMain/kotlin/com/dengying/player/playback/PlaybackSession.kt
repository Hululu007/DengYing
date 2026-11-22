package com.dengying.player.playback

import com.dengying.player.data.net.HlsUtil
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.platform.ioDispatcher
import com.dengying.player.player.PlaybackProbe
import com.dengying.player.player.PlayerEngine
import com.dengying.player.player.PlayerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * 播放会话（**Application 单例**，FR-711 的架构核心）。
 *
 * ## 为什么存在（docs/06 §4.3 D6）
 *
 * 用户对目标行为的描述："我点击全屏键是直接变成横屏，但不再一次触发视频加载……
 * 就相当于只是把视频窗口恢复到了那里而已。"
 *
 * 即**全屏只是窗口形态切换**。此前的架构让详情页与全屏页各自持 ViewModel、
 * 各自走解析链、靠路由与生命周期回调交接引擎——产生了全部残留问题：
 * 抖动、瞬时播放后暂停、退出全屏无声、返回不恢复。
 *
 * 现在播放的发起与迁移**只发生在这里**；页面只是视图宿主
 * （把引擎绑到自己的 PlayerView、把会话状态画出来）。
 *
 * ## 会话状态机
 *
 * ```
 * Idle ──play()──> Resolving ──成功──> Buffering ──READY──> Playing ⇄ Paused
 *                     │失败(换源后仍败)                      │
 *                     └────────────> Error <─────────────────┘（可 retry）
 * ```
 *
 * ## 线程约束
 *
 * ExoPlayer API 必须主线程（真机踩过 wrong-thread 异常）。
 * 本类对外方法**均应在主线程调用**；内部网络/IO 切 IO。
 */
object PlaybackSession {

    private const val TAG = "DY/Session"

    /** tv189 免登录试看链时长（`end=300`，见 [resolvePlayChain]） */
    private const val TRIAL_LENGTH_MS = 300_000L

    /** 试看升级的提前量：剩这么多时再去换全片，留够跨源搜索与缓冲 */
    private const val TRIAL_TAIL_MS = 40_000L

    /** 试看升级的观察步长（进度 1s 一跳，细于它没有意义） */
    private const val TRIAL_POLL_MS = 1_000L

    /** 试看尾等上限：用户长时间暂停时不把协程无限挂着 */
    private const val TRIAL_MAX_WAIT_MS = 6L * 60 * 60 * 1000

    enum class Status { Idle, Resolving, Buffering, Playing, Paused, Error }

    data class State(
        val status: Status = Status.Idle,
        val url: String = "",
        val headers: String = "",
        val title: String = "",
        /** 片名（换源搜索用；不含集名） */
        val vodName: String = "",
        val vodId: Long = 0L,
        val source: String = "",
        val vodUri: String = "",
        val episodeIndex: Int = 0,
        val episodeName: String = "",
        val curTime: Long = 0,
        val totalTime: Long = 0,
        val buffering: Boolean = false,
        val errMsg: String = "",
        /** 【v5.6 试看标记】tv189 免登录试看链（end=300）且跨源未救回全片——UI 打"试看"角标 */
        val isPreview: Boolean = false,
        /** 直播会话：不可暂停/seek，播完/出错不换源。 */
        val live: Boolean = false,
    ) {
        /** 是否存在可恢复的会话（详情页内嵌位据此决定恢复还是空态） */
        val hasSession: Boolean get() = url.isNotEmpty() && status != Status.Idle
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var engine: PlayerEngine? = null

    /** 会话级进度循环的句柄（测试可取消，见 [cancelProgressTickerForTest]） */
    private var progressTicker: kotlinx.coroutines.Job? = null

    init {
        // 【会话级进度循环】唯一的进度推进器（替代初版分散在各页、
        // 且会在 onPlaying 抖动时无限累积的多个 while(true)——那是个真 bug）。
        progressTicker = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val e = engine ?: continue
                val s = _state.value
                if (!s.hasSession) continue
                if (s.status != Status.Playing) continue
                _state.value = s.copy(curTime = e.currentPosition)
            }
        }
    }

    /**
     * 仅供测试：停掉会话级进度循环。
     *
     * 该循环是 `while(true) + delay(1000)`，测试若把它跑在 `runTest` 的虚拟时钟上，
     * 时间推进永远不会空闲（`advanceUntilIdle` 100% CPU 不返回）。生产代码不调用；
     * 应用退出时随进程结束。
     */
    fun cancelProgressTickerForTest() {
        progressTicker?.cancel()
        progressTicker = null
    }

    /** 会话挂在引擎上的监听实例（换引擎时要把它搬过去，见 [bindListener]） */
    private var engineListener: PlayerListener? = null

    /** 当前挂着的引擎（同一实例不重复 add） */
    private var listenerEngine: PlayerEngine? = null

    /** 幂等：同一地址已 setMedia 过则跳过（防重复打断），但 play 照常恢复 */
    private var lastSetUrl: String = ""

    /**
     * 用户播放意图（唯一真源）。
     *
     * 【为什么要有它】`play()` 内部有多条后台链路会自己收尾：试看热升级、
     * 解析失败换源、预解析回填。旧实现在这些收尾里直接 `engine.play()`，
     * 于是用户刚在内嵌位按下暂停，几秒后后台换源完成又把画面拉起来
     * （真机反馈「按了暂停不管用 / 视频自己又播了」，docs/04 §16.99）。
     *
     * 现在：只有 [play]（用户点了播放/点卡片起播）、[resume] 置 true，
     * [pause] 置 false；所有后台收尾统一走 [applyIntent]，暂停态就保持暂停。
     */
    private var playIntent = true

    /** 按用户意图起播或保持暂停：后台链路收尾必须走这里，不能直连 `play()` */
    private fun applyIntent(e: PlayerEngine) {
        if (playIntent) e.play() else e.pause()
    }

    fun currentState(): State = _state.value

    /**
     * 发起/切换播放。
     *
     * @param rawUrl 剧集地址（可能是分享页；解析在本方法内完成）
     * @param preResolved 已预解析的地址（详情页后台解析的结果），非空则跳过解析链
     * @param preHeaders 与 [preResolved] 配套的请求头
     */
    fun play(
        repo: com.dengying.player.data.source.SourceRepository,
        rawUrl: String,
        title: String,
        vodName: String,
        vodId: Long,
        source: String,
        vodUri: String,
        episodeIndex: Int,
        episodeName: String,
        preResolved: String? = null,
        preHeaders: String = "",
        startAtMs: Long = 0L,
        altUrls: List<String> = emptyList(),
        localFile: Boolean = false,
        live: Boolean = false,
    ) {
        val isLocal = localFile || rawUrl.startsWith("file://", ignoreCase = true)
        // 走到这里 = 用户要播放（点卡片/点集/恢复），意图重置为「播」。
        playIntent = true
        // Keep the last settled state before marking this request as Resolving.
        // Replaying the same URL does not call setMedia, so Media3 may not emit a
        // new READY event to clear a speculative Buffering state.
        val previousStatus = _state.value.status
        val previousBuffering = _state.value.buffering
        PlaybackProbe.note(
            "session-play",
            "rawLen=${rawUrl.length} pre=${preResolved?.length ?: 0} vodId=$vodId ep=$episodeIndex start=$startAtMs",
        )
        // 【playGap 门禁】源声明播放链缺口（§15.2：非空 = 该源当前不可播）→
        // 任何入口都不得进入解析/引擎流程，直接以缺口文案失败。放在会话入口
        // 而不是各页面：inline（playInline）与未来一切入口共用本方法，一处拦全拦
        // （docs/04 §16.16.4：ap2233 的加密 token 经 caps.token=false 的直通路径
        // 落进引擎报 2001，用户只看到通用「播放失败」）。
        if (!isLocal) {
            val gapSrc = com.dengying.player.core.CmsSources.byKey(source)
            if (gapSrc != null && !gapSrc.playGap.isNullOrEmpty()) {
                PlaybackProbe.note("playGap-hit", "source=${gapSrc.key} entry=session")
                _state.value = _state.value.copy(
                    status = Status.Error,
                    errMsg = "该源暂不可播：${gapSrc.playGap}",
                    buffering = false,
                )
                return
            }
        }
        // 【换集必须清进度】旧实现只改身份字段，curTime/totalTime 原样留下：
        // 详情页内嵌位把会话状态直接映射成进度条，换集后的解析/缓冲期间
        // 进度条仍停在上一集（用户反馈"旧视频加载条没重置"）。
        // 同一集重播不清：引擎不 setMedia、位置本该延续，清了会被下一秒的
        // 进度循环拉回去，只剩一次无意义的归零闪烁。
        // totalTime 必须归 0：InlinePlayer 以 totalTime<=0 判定"还没拿到时长"
        // 并显示缓冲态——这正是换集后应该看到的形态。
        val prev = _state.value
        val directMedia = live || isLocal
        val switchingEpisode = !prev.hasSession ||
            prev.vodId != vodId || prev.source != source || prev.episodeIndex != episodeIndex
        // 【发现页交接过门（docs/17 §11.5 原因 1）】即将播放的地址若已经装在
        // 预览引擎里，这次 play 只是「换个宿主视图」，不是重新加载：
        // 状态沿用引擎的真实状态（Playing/Paused），进度沿用引擎的位置。
        //
        // 判定必须写在这里而不是下面接管成功之后——会话先广播一次
        // Resolving + totalTime=0，详情页 InlinePlayer 以 `totalTime <= 0`
        // 判定「还没拿到时长」，会立刻画出一个加载圈：画面明明正播着，
        // 用户却先看到一次「重新加载」。
        val upfrontUrl = if (directMedia) rawUrl else preResolved ?: ""
        val handoffPreview = previewHolding(upfrontUrl)
        _state.value = if (handoffPreview != null) {
            prev.copy(
                live = live,
                status = if (handoffPreview.isPlaying) Status.Playing else Status.Paused,
                url = rawUrl,
                headers = "",
                title = title,
                vodName = vodName,
                vodId = vodId,
                source = source,
                vodUri = vodUri,
                episodeIndex = episodeIndex,
                episodeName = episodeName,
                errMsg = "",
                buffering = false,
                curTime = handoffPreview.currentPosition,
                totalTime = handoffPreview.duration.takeIf { it > 0 } ?: prev.totalTime,
            )
        } else {
            prev.copy(
                live = live,
                status = Status.Resolving,
                url = rawUrl,
                headers = "",
                title = title,
                vodName = vodName,
                vodId = vodId,
                source = source,
                vodUri = vodUri,
                episodeIndex = episodeIndex,
                episodeName = episodeName,
                errMsg = "",
                buffering = false,
                curTime = if (switchingEpisode) startAtMs else prev.curTime,
                totalTime = if (switchingEpisode) 0L else prev.totalTime,
            )
        }

        scope.launch {
            try {
                // 1) 地址解析：优先用预解析结果；否则走解析链（含换源兜底）
                var url = if (directMedia) rawUrl else preResolved ?: ""
                var headers = if (directMedia) preHeaders else ""
                var previewOnly = false
                /** 本次是否接管了发现页的预览引擎（见下方交接段） */
                var adoptedPreview: PlayerEngine? = null
                if (!directMedia && url.isEmpty()) {
                    val resolved = resolveChain(repo, rawUrl, source, vodId, altUrls)
                    if (resolved == null) {
                        _state.value = _state.value.copy(
                            status = Status.Error,
                            errMsg = "该线路地址解析失败，候选线路均不可用",
                        )
                        return@launch
                    }
                    url = resolved.url
                    headers = resolved.headers
                    previewOnly = resolved.previewOnly
                } else if (!isLocal && url.contains("end=300")) {
                    // 【预解析路径的试看兜底】预解析（详情页后台）拿到的若仍是 tv189
                    // end=300 试看授权链，解析链没参与、previewOnly 信号丢失——
                    // 用 URL 特征补判（全片链 end=总时长秒数，恰好 300s 的片极少）。
                    previewOnly = true
                }

                // 2) SEC-005：进入网络层前校验
                //    （Host 白名单用 CMS 源的 api hosts；媒体 CDN 不在名单里，
                //     这里放行已由解析链产出的地址——它来自受信源的数据）

                // 【发现页→详情平滑交接】会话还没接管引擎、宿主手里有已播该 URL
                // 的预览引擎时，直接接管它而不是 ensureEngine() 造/换主引擎。
                // 后续 sameMedia 命中（lastSetUrl 已在发现页 setMedia 时写入）
                // 会跳过 setMedia，画面不断、不重新加载。
                // 【换引擎也允许接管】旧实现只在 `engine == null` 时考虑接管。
                // 真机上会话常常还握着主引擎（上一个片/上一次详情留下的），
                // 而发现页预览引擎正播着这一条 → 接管被跳过 → 主引擎 setMedia
                // 从零起播，屏幕上那块画面却还挂在预览引擎上：声音/画面/控件
                // 三套状态各说各话。现在只要「预览引擎里就是这条流」，就把会话
                // 搬到预览引擎上，旧引擎先暂停（同一时刻只允许一个引擎出声）。
                val preview = host?.previewEngineIfAny()?.takeIf { it.currentUrl == url }
                if (preview != null && engine !== preview) {
                    engine?.takeIf { it !== preview }?.pause()
                    engine = preview
                    adoptedPreview = preview
                    bindListener(preview)
                    lastSetUrl = url
                    PlaybackProbe.note(
                        "session-adopt-preview",
                        "len=${url.length} eng=${preview.hashCode()} sameUrl=true playing=${preview.isPlaying}",
                    )
                }
                val eng = ensureEngine()
                val sameMedia = lastSetUrl == url && engine != null
                // 【接管预览引擎时不要装成"正在缓冲"】
                // 引擎里这一条已经在播了，会话只是换了个宿主视图。
                // 沿用旧逻辑（previousStatus 还是 Idle）会先广播 Buffering、
                // 把详情页 UI 打成"正在缓冲…"，用户看到的就是"切页重新加载"。
                val adoptedStatus = adoptedPreview?.let {
                    if (it.isPlaying) Status.Playing else Status.Paused
                }
                val preserveSettledState = (adoptedStatus != null) || (
                    sameMedia &&
                        previousStatus in setOf(Status.Playing, Status.Paused) &&
                        !previousBuffering
                    )
                if (!preserveSettledState) {
                    _state.value = _state.value.copy(status = Status.Buffering, buffering = true)
                }
                // 【没接管就必须让预览引擎闭嘴】发现页的预览引擎和我们用的不是同一个
                // 实例时，它会继续解码、继续出声——真机「进详情后视频还在播、控制不住」
                // 的一半来源就是这个隐形引擎（docs/04 §16.99）。接管成功时不动它。
                if (adoptedPreview == null) {
                    host?.previewEngineIfAny()?.let { preview ->
                        if (preview !== eng) {
                            preview.pause()
                            PlaybackProbe.note(
                                "session-pause-preview",
                                "previewEng=${preview.hashCode()} mainEng=${eng.hashCode()}",
                            )
                        }
                    }
                }
                if (!sameMedia) {
                    PlaybackProbe.note("session-setMedia", "len=${url.length} start=$startAtMs eng=${eng.hashCode()}")
                    eng.setMedia(url, headers, startAtMs)
                    lastSetUrl = url
                } else {
                    PlaybackProbe.note("session-skip-setMedia", "same url eng=${eng.hashCode()}")
                    // 【接管预览引擎时不要 seek】引擎里这一条正播着，位置就是用户
                    // 刚才在发现页看到的那一帧；再 seek 到详情传的进度会黑一帧。
                    if (startAtMs > 0 && adoptedPreview == null) eng.seekTo(startAtMs)
                }
                _state.value = _state.value.copy(
                    url = url,
                    headers = headers,
                    status = adoptedStatus
                        ?: if (preserveSettledState) previousStatus else Status.Buffering,
                    buffering = !preserveSettledState,
                    isPreview = previewOnly,
                )
                // 【接管预览引擎时不补 play()】它本来就在播；用户自己暂停过就保持暂停。
                // 旧实现无条件 play() 会把"暂停"顶成"播放"，并让引擎多走一次起播。
                if (adoptedPreview == null) {
                    applyIntent(eng)
                } else {
                    // 【接管后按意图对齐播放状态】旧实现只记探针、什么都不做：
                    // 预览引擎此刻若是暂停（发现页切卡/表面归零的瞬时暂停），
                    // 会话状态就停在 Paused，而画面冻结、按钮与真实状态对不上。
                    // 用户点卡片进来就是要看，这里按 intent 起播（状态由监听回调刷新）。
                    PlaybackProbe.note(
                        "session-adopt-keep",
                        "playing=${adoptedPreview.isPlaying}",
                    )
                    applyIntent(adoptedPreview)
                }

                // 【试看热升级】试看链已让用户看得到（毫秒级起播），全片搜索
                // 继续在后台跑；找到就**原位换源**（保留当前播放位置），用户只会
                // 看到一次极短的缓冲，而不是在半途撞上 5 分钟上限。
                //
                // 为什么不在上面阻塞等（旧实现 6s→12s）：冷启动首次点片时详情页
                // 预取刚发起、跨源搜索还在途，等满预算仍可能空手回落试看，且用户
                // 全程对着黑屏转圈。先播再换，起播延迟与搜索耗时彻底解耦。
                //
                // 预算 20s：这是"后台升级"，慢一点无妨，覆盖降级轮（最多 3 轮）。
                //
                // 【接管发现页预览时先不换全片（docs/17 §11.5 原因 2）】用户刚
                // 看着画面从发现页卡片滑到详情位，紧接着 setMedia 换全片会把它
                // 打断成一次缓冲，恰好抵消掉刚做好的平滑进场。改成等这条试看链
                // 播到尾声再升级（用户中途换集/手动换源会重新走 play()）。
                if (previewOnly) {
                    val previewUrl = url
                    val waitTrialTail = adoptedPreview != null
                    scope.launch {
                        if (waitTrialTail && !awaitTrialTail(previewUrl)) return@launch
                        val alt = runCatching {
                            kotlinx.coroutines.withTimeoutOrNull(20_000) {
                                findFullSource(
                                    repo = repo,
                                    source = source,
                                    vodName = vodName,
                                    episodeIndex = episodeIndex,
                                    ioDispatcher = ioDispatcher,
                                    // 后台升级不必试全部候选：先成功的那个立刻换上去，
                                    // 少试几个就能少占出网位（详情页可能正在并发预取）
                                    altLimit = 4,
                                )
                            }
                        }.getOrNull() ?: return@launch
                        // 期间用户可能换片/换集/离开/自己换了线路——只在仍播着
                        // 这条试看链时才升级（否则会把用户刚选的片顶掉）。
                        if (_state.value.url != previewUrl) return@launch
                        val pos = engine?.currentPosition ?: 0L
                        PlaybackProbe.note(
                            "session-preview-upgrade",
                            "pos=$pos len=${alt.first.length}",
                        )
                        val e2 = ensureEngine()
                        e2.setMedia(alt.first, alt.second, pos)
                        lastSetUrl = alt.first
                        _state.value = _state.value.copy(
                            url = alt.first,
                            headers = alt.second,
                            isPreview = false,
                            status = Status.Buffering,
                            buffering = true,
                        )
                        // 用户暂停过就一直暂停：后台升级只换媒体，不夺回播放权。
                        applyIntent(e2)
                    }
                }
            } catch (e: Exception) {
                PlaybackProbe.note("session-play-fail", "${e::class.simpleName}: ${e.message?.take(100)}")
                _state.value = _state.value.copy(
                    status = Status.Error,
                    errMsg = "播放启动失败：${e::class.simpleName}",
                    buffering = false,
                )
            }
        }
    }

    /**
     * 登记一次**不经过 [play]** 的引擎播放（FR-711 会话不变量补口）。
     *
     * 历史直达播放页由 PlayerViewModel 自主起播（startEngine → setMedia 直连引擎），
     * 此前会话单例一直保持 Idle/urlEmpty——返回详情页时 `resumeInlineIfPlaying` 与
     * `toFullscreen` 的 `hasSession` 检查全部静默失效（docs/05 §9.8：返回占位、
     * 全屏键"点了没反应"同源）。
     *
     * 引擎事件监听与进度循环本就全局生效，这里只补身份字段，状态此后自洽；
     * status 为 Idle 时落 Buffering，避免登记与 onReady 之间的窗口期 hasSession 仍为假。
     * 不动引擎、不碰 [lastSetUrl]——重复 setMedia 的幂等语义仍归 [play] 管。
     */
    fun registerPlayback(
        url: String,
        headers: String,
        title: String,
        vodName: String,
        vodId: Long,
        source: String,
        vodUri: String,
        episodeIndex: Int,
        episodeName: String,
    ) {
        PlaybackProbe.note(
            "session-register", "len=${url.length} vodId=$vodId ep=$episodeIndex",
        )
        // 【必须挂监听】会话的引擎监听只在 ensureEngine 里挂，而直达播放路径从未
        // 走过 play() —— 不挂的话 status 停在本方法置的 Buffering，无人推进到
        // Playing，内嵌位永远显示「正在缓冲…」（2026-10-31 真机复验踩到）。
        // 引擎此刻必已存在（调用方刚 setMedia 过），ensureEngine 幂等只补监听。
        ensureEngine()
        val cur = _state.value
        // 与 play() 同一口径：换集/换片/换源要把进度归零。全屏页换集后回到
        // 详情页，内嵌位读的是会话状态；不清的话会先画出上一集的进度。
        //
        // 【不能用 url 不同判定换集】换清晰度也会换 url，但集身份没变：
        // 按 url 判会把 curTime/totalTime 归零，切换瞬间（尤其暂停态或立即
        // 返回详情页）把保留进度读成 0。集身份 = (vodId, source, episodeIndex)。
        val sameEpisode = cur.hasSession &&
            cur.vodId == vodId && cur.source == source && cur.episodeIndex == episodeIndex
        val switchingEpisode = !sameEpisode
        _state.value = cur.copy(
            url = url,
            headers = headers,
            title = title,
            vodName = vodName,
            vodId = vodId,
            source = source,
            vodUri = vodUri,
            episodeIndex = episodeIndex,
            episodeName = episodeName,
            status = if (cur.status == Status.Idle) Status.Buffering else cur.status,
            errMsg = "",
            curTime = if (switchingEpisode) 0L else cur.curTime,
            totalTime = if (switchingEpisode) 0L else cur.totalTime,
        )
    }

    /**
     * 解析链：token 换链 → 分享页解析 → （失败）跨源换线。
     *
     * 从 DetailViewModel.playInline / PlayerViewModel.resolveUrl 收敛而来——
     * **解析只有一个入口**，页面不再各自解析。
     */
    private suspend fun resolveChain(
        repo: com.dengying.player.data.source.SourceRepository,
        raw: String,
        source: String,
        vodId: Long,
        altUrls: List<String>,
    ): PlayChainResult? = resolvePlayChain(
        repo = repo,
        raw = raw,
        source = source,
        vodId = vodId,
        vodName = _state.value.vodName,
        episodeIndex = _state.value.episodeIndex,
        ioDispatcher = ioDispatcher,
    )

    fun pause() {
        PlaybackProbe.note("session-pause", "eng=${engine?.hashCode()}")
        // 用户暂停 = 意图翻转，后台收尾（换源/升级）不得再把画面拉起来。
        playIntent = false
        engine?.pause()
    }

    fun resume() {
        PlaybackProbe.note("session-resume", "eng=${engine?.hashCode()}")
        playIntent = true
        engine?.play()
    }

    fun togglePlay() {
        val e = engine ?: return
        if (e.isPlaying) pause() else resume()
    }

    fun seekTo(positionMs: Long) {
        PlaybackProbe.note("session-seek", "target=$positionMs")
        engine?.seekTo(positionMs)
        _state.value = _state.value.copy(curTime = positionMs)
    }

    fun setSpeed(speed: Float) {
        engine?.setSpeed(speed)
    }

    fun currentPosition(): Long = engine?.currentPosition ?: 0L
    fun duration(): Long = engine?.duration ?: 0L
    fun isPlaying(): Boolean = engine?.isPlaying == true

    /**
     * 会话当前使用的引擎（可能不是宿主主实例）。
     *
     * 【为什么需要它】发现页→详情的平滑交接会把**预览引擎**接管成会话引擎
     * （见 [play] 里的 adopt 分支）。此时详情页若仍按 `engineHost.peek()`
     * 取宿主主实例，就会拿到一个没有装载媒体的播放器——有时间轴、有声音，
     * 画面全黑。视图必须显示**会话实际在用的那个引擎**。
     */
    fun engineOrNull(): PlayerEngine? = engine

    /**
     * 结束会话：暂停并清状态（**不释放引擎**——频繁重建 codec 是官方反对的）。
     * 详情页销毁且无全屏页时调用。
     */
    fun stop() {
        PlaybackProbe.note("session-stop", "pos=${currentPosition()}")
        engine?.pause()
        lastSetUrl = ""
        _state.value = State()
    }

    /**
     * 平台壳注入的引擎宿主（P4-1d）。
     *
     * 跨平台契约里没有 `Context`——引擎的构造上下文由平台壳在
     * `PlaybackEngineHolder.init(context)` 时补上，这里只取装配好的实例。
     * 未注入就调用说明平台壳漏了装配，直接报错而不是静默降级。
     */
    private var host: PlayerEngineHost? = null

    /** 由平台壳在 Application 启动时调用一次（见 DengYingApp.onCreate）。 */
    fun init(host: PlayerEngineHost) {
        this.host = host
    }

    /**
     * 宿主手里的预览引擎是否**正装着** [url]（发现页→详情交接的判据）。
     *
     * 只看事实、不看 UI 侧的一次性交接标记：会话还没有引擎、地址非空、
     * 预览引擎的 `currentUrl` 完全一致。命中即意味着这次 play 不需要解析、
     * 不需要 setMedia——引擎里播的和用户刚才看到的是同一段流，
     * 状态不该回到 Resolving（docs/17 §11.5）。
     */
    private fun previewHolding(url: String): PlayerEngine? {
        if (url.isEmpty() || engine != null) return null
        val preview = host?.previewEngineIfAny() ?: return null
        return preview.takeIf { it.currentUrl == url }
    }

    /**
     * 等试看链播到尾声（剩 [TRIAL_TAIL_MS]）再让调用方换全片。
     *
     * 用于接管发现页预览引擎的场景：用户刚经历「画面从卡片滑到详情位」，
     * 这时 setMedia 换全片会立刻黑一下（docs/17 §11.5 原因 2）。
     *
     * @return false = 期间会话换了地址 / 引擎不在了 / 等太久（用户一直暂停），
     *         调用方必须放弃这次升级；之后换集或换源会重新走 [play]。
     */
    private suspend fun awaitTrialTail(url: String): Boolean {
        var waited = 0L
        while (true) {
            if (_state.value.url != url) return false
            val e = engine ?: return false
            val total = e.duration.takeIf { it > 0 } ?: TRIAL_LENGTH_MS
            if (total - e.currentPosition <= TRIAL_TAIL_MS) return true
            if (waited >= TRIAL_MAX_WAIT_MS) return false
            kotlinx.coroutines.delay(TRIAL_POLL_MS)
            waited += TRIAL_POLL_MS
        }
    }

    private fun ensureEngine(): PlayerEngine {
        engine?.let { return it }
        val h = host
            ?: error("PlaybackSession.init(host) 未被调用：平台壳必须在播放前注入引擎宿主")
        val e = h.engine()
        engine = e
        // bindListener 自身幂等（同一引擎重复调用会直接返回），换引擎时会搬监听。
        bindListener(e)
        return e
    }

    private fun bindListener(e: PlayerEngine) {
        val existing = engineListener
        if (existing != null) {
            // 【换引擎要搬监听】会话可能从主引擎切到发现页预览引擎（交接），
            // 旧实现用 `listenerBound` 布尔只挂一次，切过去后新引擎的事件全丢：
            // status 卡在 Buffering、totalTime 不更新，用户看到「一直在加载」。
            val from = listenerEngine
            if (from === e) return
            if (from != null) from.removeListener(existing)
            e.addListener(existing)
            listenerEngine = e
            return
        }
        val l = object : PlayerListener {
            override fun onReady(durationMs: Long) {
                PlaybackProbe.note("session-ready", "dur=$durationMs")
                _state.value = _state.value.copy(
                    status = Status.Playing,
                    totalTime = if (durationMs > 0) durationMs else _state.value.totalTime,
                    buffering = false,
                    errMsg = "",
                )
            }

            override fun onPlaying() {
                _state.value = _state.value.copy(status = Status.Playing, buffering = false)
            }

            override fun onPaused() {
                if (_state.value.status == Status.Playing) {
                    _state.value = _state.value.copy(status = Status.Paused)
                }
            }

            override fun onBuffering(active: Boolean) {
                _state.value = _state.value.copy(buffering = active)
            }

            override fun onCompleted() {
                PlaybackProbe.note("session-completed")
                _state.value = _state.value.copy(status = Status.Paused)
            }

            override fun onError(
                kind: com.dengying.player.player.PlaybackErrorKind,
                code: Int,
                message: String,
            ) {
                PlaybackProbe.note("session-error", "kind=$kind code=$code")
                _state.value = _state.value.copy(
                    status = Status.Error,
                    errMsg = "播放失败（解码错误或地址失效）",
                    buffering = false,
                )
            }

            override fun onVideoSize(width: Int, height: Int) {}
        }
        engineListener = l
        listenerEngine = e
        e.addListener(l)
    }
}

/**
 * 播放地址解析链（**全应用唯一入口**）：token 换链 → （试看链先跨源找全片）→
 * 分享页解析 → （失败）跨源换线。
 *
 * 【为什么是 top-level】内嵌自动播放（发现页）与全屏会话需要同一条解析语义——
 *  tv189 VIP 片换链拿不到试看流时，跨源兜底（同名资源换源）往往能救回来；
 *  任何页面自己另写解析都会出现「详情能播、发现页说看不了」的分裂（§16.45 实录）。
 *  [PlaybackSession.resolveChain] 是本函数的会话侧薄壳（取会话状态做参数）。
 *
 * 【试看链语义】tv189 免登录返回 `end=300` 的试看授权链（previewOnly=true）时，
 *  先跨源找同名全片——找到播全片，所有源都没有才回落试看链（300 秒好过黑屏）。
 *
 * @param vodName 跨源换线需要片名查同名资源；为空则跳过换源兜底
 * @param episodeIndex 换源时取候选源的同一集
 */
/**
 * 播放链解析结果。[previewOnly] = tv189 免登录试看链（end=300）回落——跨源没
 * 救回全片时 UI 据此打「试看」角标，不再默默只给 5 分钟（用户反馈 #1）。
 */
class PlayChainResult(val url: String, val headers: String, val previewOnly: Boolean)

/**
 * 跨源找同名片的**全片**直链：**并发**试候选，谁先成功用谁
 * （真机：串行 5 候选要十几秒）。
 *
 * 抽成顶层函数是因为两个入口都要用：
 * 1. 解析链里的试看分支（[resolvePlayChain]，`waitForFullSource = true` 时）；
 * 2. 播放会话的**后台热升级**（试看已起播，随后换全片）。
 */
suspend fun findFullSource(
    repo: com.dengying.player.data.source.SourceRepository,
    source: String,
    vodName: String,
    episodeIndex: Int,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    altLimit: Int = Int.MAX_VALUE,
): Pair<String, String>? {
    PlaybackProbe.note("session-switch-source")
    if (vodName.isEmpty()) return null
    val alts = repo.findAltSources(repo.allSources(), source, vodName).take(altLimit)
    return kotlinx.coroutines.coroutineScope {
        val deferred = alts.map { alt ->
            // coroutineScope 接收者上的 async：候选随作用域自动取消
            async(ioDispatcher) {
                val altSrc = com.dengying.player.core.CmsSources.byKey(alt.sourceKey)
                    ?: return@async null
                val u = repo.fetchAltPlayUrl(altSrc, alt.vodId, episodeIndex)
                if (u.isEmpty()) return@async null
                val oc = HlsUtil.resolvePlayUrlOrFail(u)
                if (!oc.resolved) null else oc.url to ""
            }
        }
        for (d in deferred) {
            val r = runCatching { d.await() }.getOrNull()
            if (r != null) {
                deferred.forEach { if (it.isActive) it.cancel() }
                return@coroutineScope r
            }
        }
        null
    }
}

suspend fun resolvePlayChain(
    repo: com.dengying.player.data.source.SourceRepository,
    raw: String,
    source: String,
    vodId: Long,
    vodName: String,
    episodeIndex: Int,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    /**
     * 换源兜底最多试几个候选。详情页保持全量（默认）；发现页传小值——
     * 它是**批量预解析**场景，跨源搜索一条就要 1-3s，全量候选会把
     * 出网闸（OUTBOUND_LIMIT=6）堵死，中心项的起播延迟被拉到 10s 级。
     */
    altLimit: Int = Int.MAX_VALUE,
    /**
     * 试看链时是否**在这里等**跨源全片（阻塞语义）。
     *
     * - `false`（默认，用户等着起播的场景）：立刻返回试看链，由
     *   [PlaybackSession.play] 后台热升级——起播延迟与跨源搜索彻底解耦。
     * - `true`（批量预解析场景，如发现页）：在这里等到全片（有预算），
     *   缓存里直接是全片，后续起播零等待。
     */
    waitForFullSource: Boolean = false,
): PlayChainResult? {
    val src = com.dengying.player.core.CmsSources.byKey(source)
    var url = raw
    var headers = ""
    var previewUrl = ""

    // token 型源先换链
    if (src != null && src.caps.token) {
        val r = repo.resolveSourcePlay(src, vodId, raw, "")
        if (r.url.isNotEmpty()) {
            if (r.previewOnly) {
                // 【试看链】tv189 免登录只给 end=300 的 CDN 授权（全片 143 分钟只放
                // 300 秒）。先不急着用它——跨源找全片，找不到才回落（见下）。
                previewUrl = r.url
            } else {
                url = r.url
                headers = r.headers
            }
        }
    }

    suspend fun tryAltSources(): Pair<String, String>? =
        findFullSource(repo, source, vodName, episodeIndex, ioDispatcher, altLimit)

    // 试看链：全片优先——跨源找到同名全片就播全片；所有源都没有，回落试看链
    // （300 秒也好过黑屏）。cdn 链本身就是 m3u8，无需再过分享页解析。
    //
    // 【总预算 6 秒】tv189 免登录片几乎全是试看链（首页版块尤甚），这条路径
    // 是它们的常规起播路——跨源全链（搜候选+逐个试播）最坏要十几秒，用户
    // 感知"详情页加载变慢"。预算内拿到全片最好，拿不到先给试看链（秒开），
    // 用户仍可通过详情页"其他资源站"手动换全片线路。
    if (previewUrl.isNotEmpty()) {
        // 【先播试看，后台热升级】旧实现在这里**阻塞等**跨源搜索（6s→12s），
        // 而冷启动首点时详情页预取刚发起、两次搜索还在途，等满预算仍可能空手
        // → 回落试看，这就是"首次进 app 点海报只有 5 分钟、第二次进就恢复"：
        // 首次点片撞上在途搜索，播放链等不到；等播放链放弃后那一轮搜索才跑完
        // 写进缓存，所以第二次点同一片直接全片。
        //
        // 现在改成**毫秒级起播**：试看链立刻返回给引擎播，全片搜索在
        // play() 里作为后台任务继续跑，找到就原位换源（保留播放位置）。
        // 用户永远看不到"干等转圈"，也基本碰不到 5 分钟上限（搜索通常
        // 6-12 秒内出结果，远早于 300 秒）。
        if (waitForFullSource) {
            // 批量预解析：在这里等到全片（预算内），缓存里直接是全片。
            val alt = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(12_000) { tryAltSources() }
            }.getOrNull()
            if (alt != null) return PlayChainResult(alt.first, alt.second, previewOnly = false)
            PlaybackProbe.note("session-preview-fallback")
            return PlayChainResult(previewUrl, "", previewOnly = true)
        }
        PlaybackProbe.note("session-preview-faststart")
        return PlayChainResult(previewUrl, "", previewOnly = true)
    }

    // 分享页解析
    if (headers.isEmpty()) {
        val outcome = HlsUtil.resolvePlayUrlOrFail(url)
        if (outcome.resolved) {
            return PlayChainResult(outcome.url, "", previewOnly = false)
        }
    } else {
        return PlayChainResult(url, headers, previewOnly = false)
    }

    return tryAltSources()?.let { PlayChainResult(it.first, it.second, previewOnly = false) }
}
