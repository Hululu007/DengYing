package com.dengying.player.ui.player

import com.dengying.player.player.PlaybackErrorKind
import com.dengying.player.player.PlaybackProbe
import com.dengying.player.player.PlayerEngine
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.player.PlayerListener
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.duration
import platform.AVFoundation.presentationSize
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setRate
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSEC_PER_SEC

/**
 * iOS 的 [PlayerEngine] 真实现（AVPlayer）。
 *
 * ## 与 Android 侧的对应关系
 *
 * | Android（Media3） | iOS（AVFoundation） |
 * |---|---|
 * | `ExoPlayer` | [AVPlayer] |
 * | `HlsMediaSource` / `ProgressiveMediaSource` 按内容选 | AVPlayer 自己按 URL 推断，**不需要**选 |
 * | `Player.Listener` | 周期时间观察者 + 状态比对（见下） |
 * | `MediaCodecVideoRenderer` 出画面 | [AVPlayerLayer] 出画面，由 `VideoSurface` 承载 |
 * | `setPlaybackSpeed` | `AVPlayer.rate`（与播放/暂停共用一个字段） |
 *
 * ## 为什么状态用「周期观察者 + 比对」而不是 KVO
 *
 * AVFoundation 的标准做法是 KVO `AVPlayerItem.status` / `playbackBufferEmpty`。
 * 在 Kotlin/Native 上做 KVO 需要在 `observeValueForKeyPath` 里区分 keyPath 字符串、
 * 自己维护 context 指针，且**观察者必须在 `release()` 里成对注销**，漏一个就是
 * 崩溃级缺陷。而本引擎本来就要装周期时间观察者（进度条要有数），间隔 250ms，
 * 与播放状态迁移的时间尺度相比足够细。故让同一个观察者顺带比对状态并派发回调 ——
 * 少一套生命周期，就少一类缺陷。
 *
 * 代价：`onReady` / `onError` 最迟有 250ms 延迟。两者都是「一次性、用户感知不到
 * 250ms」的事件，可以接受。
 *
 * ## 契约照守（commonMain `PlayerEngine`）
 *
 * 1. 所有方法都在**主线程**被调用，实现内部不切线程：本类全程单线程模型，无锁、
 *    无自己的队列。周期观察者的 `queue = null` 即「主队列」。
 * 2. `currentPosition` / `duration` / `isPlaying` 是属性读取，随时可读。
 * 3. `viewHandle()` 返回 [AVPlayerLayer]，调用方据此挂到 `UIView.layer`。
 *
 * ## 已知能力降级（`docs/09` §3 记录在案，非本文件决定）
 *
 * **不注入自定义 HTTP 头**：`setMedia` 收了 `headersJson`，但 AVPlayer 的公开 API
 * 没有请求头入口（能用的 `AVURLAssetHTTPHeaderFieldsKey` 是私有键）。
 * `docs/09-KMP多平台迁移方案.md` 已就「headers 相关源在 iOS 侧降级」记录在案，故这里
 * 不擅自改用私有键 —— 那是产品/架构决策，属 `docs/11-Mac待办清单.md` 的 M-15 项。
 */
@OptIn(ExperimentalForeignApi::class)
class AvPlayerEngine : PlayerEngine {

    private val player = AVPlayer()

    /** 画面承载层：由 `VideoSurface` 取走挂到 `UIView` 上 */
    private val layer: AVPlayerLayer = AVPlayerLayer.playerLayerWithPlayer(player)

    private val listeners = mutableListOf<PlayerListener>()

    private var timeObserver: Any? = null

    private var endObserver: Any? = null

    /** 一次 `setMedia` 的生命周期内，`onReady` 只发一次 */
    private var readyFired = false

    private var lastBuffering: Boolean? = null

    private var lastPlaying: Boolean? = null

    private var lastDurationMs: Long = 0L

    private var lastVideoW: Int = 0

    private var lastVideoH: Int = 0

    /** Surface 挂上/摘下由 host 通知，用于区分「主动暂停」与「意外停住」 */
    private var surfaceAttached = false

    init {
        layer.videoGravity = AVLayerVideoGravityResizeAspect
        installTimeObserver()
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _: NSNotification? ->
            PlaybackProbe.note("engine-completed", "")
            listeners.toList().forEach { it.onCompleted() }
        }
    }

    // ------------------------------------------------------------------ 媒体

    override fun setMedia(url: String, headersJson: String, startPositionMs: Long) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            PlaybackProbe.note("engine-bad-url", "len=${url.length}")
            listeners.toList().forEach {
                it.onError(PlaybackErrorKind.SourceGone, -1, "URL 不可解析")
            }
            return
        }
        // headersJson 有意不消费，见类 Kdoc「已知能力降级」
        if (headersJson.isNotEmpty()) {
            PlaybackProbe.note("engine-headers-dropped", "len=${headersJson.length}")
        }
        readyFired = false
        lastBuffering = null
        lastPlaying = null
        lastDurationMs = 0L
        lastVideoW = 0
        lastVideoH = 0
        currentUrl = url

        player.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = nsUrl))
        if (startPositionMs > 0) {
            player.seekToTime(secondsToTime(startPositionMs / 1000.0))
        }
        PlaybackProbe.note("engine-set-media", "startMs=$startPositionMs")
    }

    override fun play() {
        player.play()
        PlaybackProbe.note("engine-play", "")
    }

    override fun pause() {
        player.pause()
        PlaybackProbe.note("engine-pause", "")
    }

    override fun seekTo(positionMs: Long) {
        player.seekToTime(secondsToTime(positionMs / 1000.0))
    }

    override fun setSpeed(speed: Float) {
        // AVPlayer 没有独立的「速度」字段：rate 同时表达「是否在播」。
        // 故只在正在播放时改 rate，暂停态保持 0 —— 否则调速度会自动起播。
        if (player.rate != 0f) player.setRate(speed)
    }

    override fun release() {
        player.pause()
        currentUrl = null
        timeObserver?.let { player.removeTimeObserver(it) }
        timeObserver = null
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        player.replaceCurrentItemWithPlayerItem(null)
        listeners.clear()
        PlaybackProbe.note("engine-release", "")
    }

    // ------------------------------------------------------------------ 状态

    override val currentPosition: Long
        get() = (CMTimeGetSeconds(player.currentTime()) * 1000).toLong().coerceAtLeast(0L)

    override val duration: Long
        get() = lastDurationMs

    override val isPlaying: Boolean
        get() = player.rate != 0f

    override var currentUrl: String? = null
        private set

    override fun addListener(listener: PlayerListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    override fun removeListener(listener: PlayerListener) {
        // 与 add 的 contains 守卫对称；未注册过时 remove 返回 false，不抛异常。
        listeners.remove(listener)
    }

    override fun viewHandle(): Any? = layer

    // ------------------------------------------------------------------ 内部

    private fun secondsToTime(seconds: Double) =
        CMTimeMakeWithSeconds(seconds, NSEC_PER_SEC.toInt())

    private fun installTimeObserver() {
        timeObserver = player.addPeriodicTimeObserverForInterval(
            interval = secondsToTime(0.25),
            // 主队列：契约要求回调在主线程，故不传自定义队列
            queue = null,
        ) { _ -> onTick() }
    }

    /** 每 250ms：刷新时长，并派发 ready / error / buffering / playing 状态迁移 */
    private fun onTick() {
        val item = player.currentItem ?: return

        when (item.status) {
            AVPlayerItemStatusFailed -> {
                val err = item.error
                val code = (err?.code ?: 0L).toLong()
                val kind = classify(code, err?.domain)
                PlaybackProbe.note("engine-error", "kind=$kind code=$code")
                listeners.toList().forEach {
                    it.onError(kind, code.toInt(), err?.localizedDescription ?: "未知错误")
                }
                return
            }

            AVPlayerItemStatusReadyToPlay -> {
                val ms = (CMTimeGetSeconds(item.duration) * 1000).toLong()
                if (ms > 0 && ms != lastDurationMs) lastDurationMs = ms
                if (!readyFired && lastDurationMs > 0) {
                    readyFired = true
 PlaybackProbe.note("engine-ready", "durationMs=$lastDurationMs")
                    listeners.toList().forEach { it.onReady(lastDurationMs) }
                }
                val size = item.presentationSize
                // CGSize 以 CValue<CGSize> 返回：借 useContents 读出结构字段
                val w = size.useContents { width }.toInt()
                val h = size.useContents { height }.toInt()
                if (readyFired && w > 0 && h > 0 && (w != lastVideoW || h != lastVideoH)) {
                    lastVideoW = w
                    lastVideoH = h
                    listeners.toList().forEach { it.onVideoSize(w, h) }
                }
            }

            else -> Unit
        }

        val buffering =
            player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
        if (buffering != lastBuffering) {
            lastBuffering = buffering
            listeners.toList().forEach { it.onBuffering(buffering) }
        }

        val playing = player.timeControlStatus == AVPlayerTimeControlStatusPlaying
        if (playing != lastPlaying) {
            lastPlaying = playing
            if (playing) {
                listeners.toList().forEach { it.onPlaying() }
            } else if (surfaceAttached) {
                // 只有「本该在播却停住」才算暂停；无画面时的停是 host 主动 pause
                listeners.toList().forEach { it.onPaused() }
            }
        }
    }

    /**
     * AVFoundation 错误码 → [PlaybackErrorKind]。
     *
     * `NSURLErrorDomain` 的负数码是 `NSURLError.h` 里稳定的公开常量。其中 **-1022
     * 是 ATS 拒绝**，在 iOS 上极常见，归到 [PlaybackErrorKind.Network] 但探针单独
     * 记录，便于与 `docs/11` 的 M-7 ATS 清单对账。
     *
     * [PlaybackErrorKind.Network] 的语义是「应用不介入、由底层重试」，故这里不把
     * `-1022` 归入 `SourceGone`：ATS 拒绝是配置问题，不是源失效，切线路没有意义。
     */
    private fun classify(code: Long, domain: String?): PlaybackErrorKind = when {
        domain == null -> PlaybackErrorKind.Unknown
        domain.contains("NSURLErrorDomain") -> when (code) {
            -1001L, -1003L, -1004L, -1005L, -1009L, -1022L -> PlaybackErrorKind.Network
            -1100L, -1101L -> PlaybackErrorKind.SourceGone
            else -> PlaybackErrorKind.Unknown
        }
        domain.contains("AVFoundationErrorDomain") -> when (code) {
            -11800L, -11828L -> PlaybackErrorKind.Decode
            -1102L, -11850L -> PlaybackErrorKind.SourceGone
            else -> PlaybackErrorKind.Unknown
        }
        else -> PlaybackErrorKind.Unknown
    }

    /** 由 [IosPlayerEngineHost] 在画面挂上时调用 */
    internal fun onSurfaceAttached() {
        surfaceAttached = true
    }

    /** 由 [IosPlayerEngineHost] 在画面摘下时调用 */
    internal fun onSurfaceDetached() {
        surfaceAttached = false
    }
}

/**
 * iOS 的 [PlayerEngineHost] 真实现。
 *
 * 语义与 Android 的
 * `app/src/main/java/com/dengying/player/playback/PlaybackEngineHolder.kt` 逐条对齐：
 *
 *  - 全应用单例引擎，只构造一次；
 *  - `surfaceCount` 是**正在显示画面的视图数量**，归零时**只暂停不释放**
 *    （释放会丢掉缓冲与播放位置，回列表再进来就要重头缓冲）；
 *  - `peek()` 不创建引擎（详情页组合时就会调它，创建引擎会白起一个 AVPlayer）。
 */
object IosPlayerEngineHost : PlayerEngineHost {

    private var instance: AvPlayerEngine? = null

    private var surfaces = 0

    override fun engine(): PlayerEngine {
        instance?.let { return it }
        val created = AvPlayerEngine()
        instance = created
        PlaybackProbe.note("host-engine-created", "")
        return created
    }

    override fun peek(): PlayerEngine? = instance

    override fun surfaceActive() {
        surfaces += 1
        instance?.onSurfaceAttached()
        PlaybackProbe.note("host-surface-active", "count=$surfaces")
    }

    override fun surfaceInactive() {
        if (surfaces > 0) surfaces -= 1
        val engine = instance
        if (surfaces == 0 && engine != null) {
            engine.onSurfaceDetached()
            engine.pause()
            PlaybackProbe.note("host-no-surface-paused", "")
        }
    }

    override fun forceRelease() {
        surfaces = 0
        instance?.release()
        instance = null
        PlaybackProbe.note("host-force-release", "")
    }

    override fun surfaceCount(): Int = surfaces
}
