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
    ) {
        /** 是否存在可恢复的会话（详情页内嵌位据此决定恢复还是空态） */
        val hasSession: Boolean get() = url.isNotEmpty() && status != Status.Idle
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var engine: PlayerEngine? = null

    init {
        // 【会话级进度循环】唯一的进度推进器（替代初版分散在各页、
        // 且会在 onPlaying 抖动时无限累积的多个 while(true)——那是个真 bug）。
        scope.launch {
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

    /** 引擎监听是否已挂（引擎全局唯一，监听只挂一次） */
    private var listenerBound = false

    /** 幂等：同一地址已 setMedia 过则跳过（防重复打断），但 play 照常恢复 */
    private var lastSetUrl: String = ""

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
    ) {
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
        _state.value = _state.value.copy(
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
        )

        scope.launch {
            try {
                // 1) 地址解析：优先用预解析结果；否则走解析链（含换源兜底）
                var url = preResolved ?: ""
                var headers = preHeaders
                if (url.isEmpty()) {
                    val resolved = resolveChain(repo, rawUrl, source, vodId, altUrls)
                    if (resolved == null) {
                        _state.value = _state.value.copy(
                            status = Status.Error,
                            errMsg = "该线路地址解析失败，候选线路均不可用",
                        )
                        return@launch
                    }
                    url = resolved.first
                    headers = resolved.second
                }

                // 2) SEC-005：进入网络层前校验
                //    （Host 白名单用 CMS 源的 api hosts；媒体 CDN 不在名单里，
                //     这里放行已由解析链产出的地址——它来自受信源的数据）

                // 3) 交给引擎（幂等：同地址不重复 setMedia，但 play 必调）
                val eng = ensureEngine()
                val sameMedia = lastSetUrl == url && engine != null
                val preserveSettledState = sameMedia &&
                    previousStatus in setOf(Status.Playing, Status.Paused) &&
                    !previousBuffering
                if (!preserveSettledState) {
                    _state.value = _state.value.copy(status = Status.Buffering, buffering = true)
                }
                if (!sameMedia) {
                    PlaybackProbe.note("session-setMedia", "len=${url.length} start=$startAtMs")
                    eng.setMedia(url, headers, startAtMs)
                    lastSetUrl = url
                } else {
                    PlaybackProbe.note("session-skip-setMedia", "same url")
                    if (startAtMs > 0) eng.seekTo(startAtMs)
                }
                _state.value = _state.value.copy(
                    url = url,
                    headers = headers,
                    status = if (preserveSettledState) previousStatus else Status.Buffering,
                    buffering = !preserveSettledState,
                )
                eng.play()
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
    ): Pair<String, String>? = resolvePlayChain(
        repo = repo,
        raw = raw,
        source = source,
        vodId = vodId,
        vodName = _state.value.vodName,
        episodeIndex = _state.value.episodeIndex,
        ioDispatcher = ioDispatcher,
    )

    fun pause() {
        PlaybackProbe.note("session-pause")
        engine?.pause()
    }

    fun resume() {
        PlaybackProbe.note("session-resume")
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

    private fun ensureEngine(): PlayerEngine {
        engine?.let { return it }
        val h = host
            ?: error("PlaybackSession.init(host) 未被调用：平台壳必须在播放前注入引擎宿主")
        val e = h.engine()
        engine = e
        if (!listenerBound) {
            listenerBound = true
            bindListener(e)
        }
        return e
    }

    private fun bindListener(e: PlayerEngine) {
        e.addListener(object : PlayerListener {
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
        })
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
): Pair<String, String>? {
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

    // 跨源兜底：**并发**试候选，谁先成功用谁（真机：串行 5 候选要十几秒）
    suspend fun tryAltSources(): Pair<String, String>? {
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

    // 试看链：全片优先——跨源找到同名全片就播全片；所有源都没有，回落试看链
    // （300 秒也好过黑屏）。cdn 链本身就是 m3u8，无需再过分享页解析。
    //
    // 【总预算 6 秒】tv189 免登录片几乎全是试看链（首页版块尤甚），这条路径
    // 是它们的常规起播路——跨源全链（搜候选+逐个试播）最坏要十几秒，用户
    // 感知"详情页加载变慢"。预算内拿到全片最好，拿不到先给试看链（秒开），
    // 用户仍可通过详情页"其他资源站"手动换全片线路。
    if (previewUrl.isNotEmpty()) {
        val alt = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(6000) { tryAltSources() }
        }.getOrNull()
        if (alt != null) return alt
        PlaybackProbe.note("session-preview-fallback")
        return previewUrl to ""
    }

    // 分享页解析
    if (headers.isEmpty()) {
        val outcome = HlsUtil.resolvePlayUrlOrFail(url)
        if (outcome.resolved) {
            return outcome.url to ""
        }
    } else {
        return url to headers
    }

    return tryAltSources()
}
