package com.dengying.player.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import com.dengying.player.data.cache.MediaCache
import com.dengying.player.data.cache.MediaCacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.dengying.player.data.json.JsonX
import com.dengying.player.player.PlaybackErrorKind
import com.dengying.player.player.PlaybackProbe
import com.dengying.player.player.PlayerEngine
import com.dengying.player.player.PlayerListener

/**
 * 播放引擎封装 —— **Media3 ExoPlayer 的唯一接触点**。
 *
 * 职责边界（对应原工程 `Player.ets` 里的 AVPlayer 使用段）：
 *  - 建实例、装数据源、绑定媒体、控制播放、释放
 *  - 向上只暴露窄接口（[PlayerEngine]），页面不认识 ExoPlayer 类型
 *
 * 【与鸿蒙 AVPlayer 的关键差异，逐条对应原工程的踩坑注释】
 *
 * | 鸿蒙 AVPlayer | Media3 | 移植处置 |
 * |---|---|---|
 * | surface 必须由 XComponent 提供，且 url 要在 surface 之后设 | ExoPlayer 不需要调用方给 surface；Compose 用 PlayerSurface 组件渲染 | surface 与媒体源的先后依赖**消失**。原工程的三条绑定时序约束在 Media3 上不存在，故不复刻——复刻一个已不存在的约束只会制造假问题 |
 * | `media.createMediaSourceWithUrl(url, headers)` 才能带请求头 | `DefaultHttpDataSource.Factory().setDefaultRequestProperties()` | 请求头在**数据源工厂**层注入（FR-402），HLS 与 progressive 共用同一工厂 |
 * | `setSpeed(PlaybackSpeed)` 只认枚举 | `setPlaybackParameters(PlaybackParameters(speed))` 接受任意 float | 0.75/1.0/1.25/1.5/2.0 全部直接可用，不再有枚举映射表 |
 * | 换集必须 release 重建（媒体源 prepare 后不可替换） | `setMediaItem()` 可在播放中替换 | 但仍**保留 release→重建**：换集后要重走解析与探测（自动换线），重建比就地替换更符合那条链路 |
 * | 无内建 HLS 变体切换 | `TrackSelectionParameters` 可切轨道 | 本应用按**地址**换档（每个变体是独立 URL），故仍走"重建 + seek 回原进度" |
 */
@UnstableApi
object PlayerFactory {

    /**
     * 建一个 ExoPlayer。
     *
     * 参数取向：
     *  - `handleAudioFocus = true`：系统来电/其他 App 播放时自动让出
     *  - `setWakeMode` 不设：本应用不后台播放（NFR-007），不需要持锁
     *  - 不挂 MediaSession：投屏走独立端口，媒体通知不是本版范围
     */
    fun create(context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    // ## 缓冲参数（真机实测修正）
                    //
                    // 初版取 `min=15s / max=30s / 起播门槛=2.5s / 重缓冲门槛=5s`，
                    // 结果真机探针记录到**各 992 次**的 `BUFFERING ↔ READY` 交替：
                    // ```
                    // 992 × STATE_READY
                    // 992 × STATE_BUFFERING
                    // ```
                    // 用户反馈"非常快速地暂停又恢复、一直往复，而且缓冲圆环一直闪"。
                    //
                    // **根因**：起播门槛（2.5s）**远低于**最小缓冲量（15s）。
                    // 播放器攒到 2.5 秒就开播，播完这点缓冲立刻饿死 → 再缓冲 →
                    // 再攒到 2.5 秒又开播……形成"饿死循环"。
                    //
                    // **正确关系**（ExoPlayer 的参数语义，真机报错后确认）：
                    //   minBufferMs ≥ bufferForPlaybackMs
                    //   minBufferMs ≥ bufferForPlaybackAfterRebufferMs   ← 第一版漏了这条
                    //   maxBufferMs ≥ minBufferMs
                    //
                    // 第一版设 `min=15s / 重缓冲门槛=30s`，违反第二条，真机直接抛：
                    // ```
                    // IllegalArgumentException: minBufferMs cannot be less than
                    //   bufferForPlaybackAfterRebufferMs
                    // ```
                    // 后果是**每次起播都失败**（探针里连续 8 次 `inline-exception`）。
                    //
                    // 现在的取值（满足全部约束，且保留"重缓冲门槛更高"的意图）：
                    //  - `minBufferMs = 30s`：最小缓冲量抬到 30 秒
                    //  - `bufferForPlaybackMs = 15s`：起播门槛 15 秒（低于下限，
                    //    起播不必等满 30 秒，兼顾首帧速度）
                    //  - `bufferForPlaybackAfterRebufferMs = 30s`：**重缓冲门槛 30 秒**，
                    //    与 minBuffer 同值——饿死一次后要重新攒够才恢复，
                    //    这是治"高频往复"的关键（初版 2.5s/5s 太激进导致 992 次振荡）
                    //  - `maxBufferMs = 60s`：给 CDN 抖动留余量（免费源实测 45-357KB/s）
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 30_000,
                        /* maxBufferMs = */ 60_000,
                        /* bufferForPlaybackMs = */ 15_000,
                        /* bufferForPlaybackAfterRebufferMs = */ 30_000,
                    )
                    .build()
            )
            .build()

    /**
     * 由地址与请求头造媒体源。
     *
     * **按内容形态选 MediaSource 类型**，而不是按扩展名：
     * 适配器换出的地址常无 `.m3u8` 后缀（`content.php/api.php?...` 之类）。
     * 这里用稳妥策略：
     *  - 含 `.m3u8` → HlsMediaSource
     *  - 否则 → ProgressiveMediaSource（mp4/flv 等）
     *
     * 【FR-704 媒体缓存】数据源工厂被 [MediaCache] 包一层，实现"二次播放免重复下载"。
     * 缓存不可用时 `wrap` 会原样返回上游工厂——**降级为无缓存直连**，
     * 不让缓存成为播放的前置条件。
     */
    fun mediaSource(
        context: Context,
        url: String,
        headersJson: String,
        headers: Map<String, String> = emptyMap(),
    ): MediaSource {
        val networkFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                headers["User-Agent"]
                    ?: "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36"
            )
            .setAllowCrossProtocolRedirects(true)

        // 请求头：先放适配器产出的（覆盖 UA），再补调用方显式给的
        val props = HashMap<String, String>()
        props.putAll(parseHeaders(headersJson))
        props.putAll(headers)
        if (props.isNotEmpty()) {
            networkFactory.setDefaultRequestProperties(props)
        }

        // KMP P4-1c：缓存的 Media3 解释点已收进 MediaCacheDataSource（:app 唯一一处），
        // 决策层（开关/上限/预热时机）在 :shared/commonMain 的 MediaCache。
        val factory = MediaCacheDataSource.wrap(networkFactory)
        val item = MediaItem.fromUri(url)
        return if (url.substringBefore('?').lowercase().endsWith(".m3u8")) {
            HlsMediaSource.Factory(factory).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(factory).createMediaSource(item)
        }
    }

    /**
     * 解析适配器产出的请求头 JSON。
     *
     * 解析失败返回空表——**不因为请求头坏了就不播**：
     * 大多数源不需要请求头，坏掉的 JSON 只是少带了几个头，由服务端判定。
     */
    fun parseHeaders(json: String): Map<String, String> {
        if (json.isEmpty()) return emptyMap()
        val obj = JsonX.parseObject(json) ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (k in JsonX.keys(obj)) out[k] = JsonX.str(obj, k, "")
        return out
    }
}

/** [PlayerEngine] 的 Media3 实现（Android = Media3 ExoPlayer） */
@UnstableApi
class Media3Engine(private val context: Context) : PlayerEngine {

    /**
     * ExoPlayer 实例 —— **构造时即创建，不懒创建**。
     *
     * 【为什么必须即时创建】真机实测黑屏的根因之一：
     * 初版把它写成"首次 `setMedia` 才 `ensure()`"，于是
     * `viewHandle()` 在引擎刚建好时返回 `null`，`PlayerView.player` 被绑成 null；
     * 而 `setMedia` 不改变任何可观察状态，`AndroidView` 的 `update` 不会因此重跑，
     * `PlayerView` 就永久停在无 player 状态 —— 表现为**一直黑屏**。
     *
     * 即时创建后，`viewHandle()` 自引擎存在起恒非空，绑定一次到位。
     */
    private val player: ExoPlayer = PlayerFactory.create(context).also { p ->
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        PlaybackProbe.note("STATE_READY")
                        // duration 可能在 READY 时仍是 C.TIME_UNSET（HLS 没解析完），
                        // 此时给 0，由页面按"未知时长"处理（进度条不显示总长）
                        val d = p.duration
                        val dur = if (d == androidx.media3.common.C.TIME_UNSET || d < 0) 0L else d
                        listeners.forEach { it.onReady(dur) }
                        // 【P-01 修复】READY 就是"缓冲结束"。
                        // 结束信号必须与开始信号**同源**（都由 playbackState 表达），
                        // 否则一旦结束事件丢失，去重状态会停在 true，
                        // 下一次 seek 触发的 STATE_BUFFERING 会被吞掉——
                        // 真机表现正是"seek 时没有缓冲环"（docs/08 P-01）。
                        reportBuffering(false)
                    }
                    Player.STATE_BUFFERING -> {
                        PlaybackProbe.note("STATE_BUFFERING")
                        reportBuffering(true)
                    }
                    Player.STATE_ENDED -> {
                        PlaybackProbe.note("STATE_ENDED")
                        reportBuffering(false)
                        listeners.forEach { it.onCompleted() }
                    }
                    Player.STATE_IDLE -> reportBuffering(false)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    listeners.forEach { it.onPlaying() }
                } else {
                    listeners.forEach { it.onPaused() }
                }
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                // 【P-01 修复：本回调不再参与缓冲判定】
                //
                // 初版在这里补发 `onBuffering(false)`，条件是
                // `!isLoading && playbackState == STATE_READY`。这个条件与
                // `STATE_BUFFERING` 的上报**不同源**，两者之间的去重状态
                // 一旦不同步，就会永久吞掉后续的缓冲开始事件：
                //
                //   1. STATE_BUFFERING → lastBuffering = true，UI 显示缓冲环
                //   2. 播放开始（onPlaying 里 UI 自行清掉缓冲环），但此刻
                //      `onIsLoadingChanged(false)` 尚未到达，或到达时
                //      `playbackState` 还不是 READY → 结束信号丢失
                //   3. 用户 seek → STATE_BUFFERING → `if (!lastBuffering)` 不成立
                //      → **事件被吞** → 用户看到"seek 时没有缓冲环"
                //
                // 现在缓冲上报只由 `playbackState` 驱动（见 [reportBuffering]），
                // 开始与结束同源，不存在丢一半的可能。
                // 本回调留空是**有意为之**：它不再决定缓冲态，留着只为避免
                // 后来者误以为缓冲逻辑还在这里。
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                PlaybackProbe.note("VIDEO_SIZE", "${videoSize.width}x${videoSize.height}")
                listeners.forEach { it.onVideoSize(videoSize.width, videoSize.height) }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                PlaybackProbe.note("PLAYER_ERROR", "code=${error.errorCode}")
                listeners.forEach {
                    it.onError(errorKindOf(error.errorCode), error.errorCode, error.message ?: "")
                }
            }
        })
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<PlayerListener>()

    /**
     * 上一次上报的缓冲态（去重用的唯一状态）。
     *
     * 【P-01 修复后】开始与结束都由 [reportBuffering] 写入，
     * 与 `playbackState` 一一对应，不再有"UI 自己清过、而这里不知道"的脱节。
     */
    private var lastBuffering = false

    /**
     * 缓冲态上报（P-01）。
     *
     * 只在**真变化**时上报，避免同一状态重复回调让 UI 反复切换。
     * 调用点只有 `playbackState` 的四个分支，因此上报值与播放器状态恒等：
     * `STATE_BUFFERING` → true，`READY` / `ENDED` / `IDLE` → false。
     */
    private fun reportBuffering(active: Boolean) {
        if (lastBuffering == active) return
        lastBuffering = active
        listeners.forEach { it.onBuffering(active) }
    }

    override fun setMedia(url: String, headersJson: String, startPositionMs: Long) {
        PlaybackProbe.note("setMedia", "len=${url.length} headers=${headersJson.isNotEmpty()} start=$startPositionMs")
        currentUrl = url
        player.setMediaSource(PlayerFactory.mediaSource(context, url, headersJson))
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.prepare()
    }

    override fun play() {
        // 探针：`BUFFERING ↔ READY` 高频空转时，先确认是不是有人反复调 play/pause。
        // 若这两条探针与 STATE_* 交替出现 → 调用方在振荡；若它们只出现一两次 → 振荡在播放器内部。
        PlaybackProbe.note("engine-play-call")
        player.playWhenReady = true
        player.play()
    }

    override fun pause() {
        PlaybackProbe.note("engine-pause-call")
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        // 【探针】seek 与随后的 `STATE_BUFFERING` / `STATE_READY` 在探针文件里
        // 相邻出现，就是"seek 后显示缓冲环"的直接证据（docs/08 A-01 的验证口径）。
        PlaybackProbe.note("engine-seek", "target=$positionMs")
        player.seekTo(positionMs)
    }

    override fun setSpeed(speed: Float) {
        player.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
    }

    override fun release() {
        listeners.clear()
        currentUrl = null
        try {
            player.release()
        } catch (e: Exception) {
            // 释放失败不影响后续（进程内引用已断）
        }
    }

    override val currentPosition: Long
        get() = player.currentPosition

    override val duration: Long
        get() {
            val d = player.duration
            return if (d == androidx.media3.common.C.TIME_UNSET || d < 0) 0L else d
        }

    override val isPlaying: Boolean
        get() = player.isPlaying

    override var currentUrl: String? = null
        private set

    override fun addListener(listener: PlayerListener) {
        listeners.add(listener)
    }

    override fun viewHandle(): Any? = player

    /**
     * Media3 错误码 → 跨平台分类（见 [PlaybackErrorKind]）。
     *
     * 这张表是**从 `PlayerViewModel` 上移来的**：迁移前它藏在 VM 的业务分支里，
     * iOS 侧没有任何对应常量。逐条保留原判定，仅把"致命/非致命"换成四分类。
     */
    private fun errorKindOf(code: Int): PlaybackErrorKind = when (code) {
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        -> PlaybackErrorKind.Network   // 交给 Media3 自己退避重试，应用层不介入

        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        -> PlaybackErrorKind.SourceGone   // 源侧永久问题：换线路

        else -> PlaybackErrorKind.Decode   // 解码/解析类：换线路可能解决
    }
}
