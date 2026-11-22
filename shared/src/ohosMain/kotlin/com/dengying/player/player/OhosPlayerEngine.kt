@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package com.dengying.player.player

import com.dengying.player.platform.Log
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.toKString
import cnames.structs.OH_AVFormat
import cnames.structs.OH_AVPlayer
import platform.ArkGraphics2D.NativeWindow.OHNativeWindow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.AVCodecKit.Core.OH_AVFormat_GetIntValue
import platform.AVCodecKit.Core.OH_AVFormat_GetLongValue
import platform.MediaKit.AVPlayer.*
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs

/**
 * 鸿蒙播放引擎 —— 直接用 MediaKit 的 native AVPlayer（libavplayer.so）。
 *
 * ## 为什么直接用 native AVPlayer，而不是 ArkTS 的 @ohos.multimedia.media
 *
 * CPF 发行版已随 ohos target 提供 platform klib
 * `org.jetbrains.kotlin.native.platform.AVPlayer`（package
 * `platform.MediaKit.AVPlayer`，manifest 自带 linkerOpts=-lavplayer），
 * 底层就是系统 `libavplayer.so`。命令与回调因此都留在 Kotlin 进程内：
 * 不需要在 ArkTS 再写一份播放器、不需要轮询命令槽、也不会多一条
 * Kotlin→C++→NAPI→ArkTS 的异步转发链。
 *
 * 注意：**不要自建 cinterop**。CPF 的 klib 布局会让 cinterop 任务报
 * "Could not find org.jetbrains.kotlin.native.platform.ohos"（实测），
 * 直接用上面的 platform klib 即可。
 *
 * ## 已实现 / 未实现（第一版，WIP）
 *
 * - 已实现：setMedia（URL）/prepare/play/pause/seekTo/setSpeed/release，
 *   状态→onReady/onPlaying/onPaused/onCompleted/onBuffering/onError，
 *   时长与视频尺寸。
 * - **未实现：自定义请求头**。本 SDK 版本的 avplayer.h 没有
 *   SetMediaSourceHeader 之类的入口（只有 SetURLSource/SetFDSource，
 *   platform klib 里也没有 OH_AVMediaSource_* 符号），headersJson 目前仅记录，
 *   不生效。可选方案见 `DengYingOhos/docs/ohos-player-notes.md`。
 * - 画面绑定：surfaceId 经 ArkTS XComponent → NAPI → [OhosVideoSurfaceBridge]
 *   传入；`SetURLSource` 成功后才 `OH_AVPlayer_SetVideoSurface`，再 `Prepare`。
 *   无 surface 时停在 INITIALIZED，surface 到达后补绑定与 Prepare。
 * - 回调线程：native 回调只做「归属校验 + 拷贝 body 值」，随后 `Dispatchers.Main`
 *   派发；状态字段与 listener 通知都在主线程完成（PlaybackSession 也在 Main）。
 * - 释放：`ReleaseSync` 移出主线程，在 Dispatchers.Default 执行；锚点先失效，
 *   后台返回后再 dispose StableRef。ReleaseSync 失败则保留锚点与 window 引用
 *   （可能仍会回调，提前释放会 UAF）。
 * - 同一 window 的「旧 release → 新 player SetVideoSurface」串行：setMedia
 *   在 SetURLSource 成功后才把绑定/Prepare 排到 Main 协程，遇到未完成的旧
 *   release 就挂起等待，不阻塞主线程；ReleaseSync 失败会封锁该 window，
 *   不同 window 互不影响。
 * - Prepare 之后 surface 重建（窗口变化/折叠屏）：重新 setMedia，保留
 *   url/headers/位置/起播意图，新 surface 在 Prepare 前绑好。
 */
class OhosPlayerEngine : PlayerEngine {

    private companion object {
        const val TAG = "DY/OhosPlayer"

        /** 等待期间 surface 连续重建的上限，避免绑定协程长时间打转。 */
        const val MAX_BIND_ATTEMPTS = 8

        // AVPlayerState（avplayer_base.h）。CPF 的 platform klib 把 C enum 暴露为
        // 无 entries 的整型（AVPlayerState.entries 编不过），故按 C 枚举值使用。
        const val STATE_IDLE = 0
        const val STATE_INITIALIZED = 1
        const val STATE_PREPARED = 2
        const val STATE_PLAYING = 3
        const val STATE_PAUSED = 4
        const val STATE_STOPPED = 5
        const val STATE_COMPLETED = 6
        const val STATE_RELEASED = 7
        const val STATE_ERROR = 8

        // AVPlayerOnInfoType
        const val INFO_EOS = 3
        const val INFO_STATE_CHANGE = 4
        const val INFO_POSITION_UPDATE = 5
        const val INFO_RESOLUTION_CHANGE = 8
        const val INFO_BUFFERING_UPDATE = 9
        const val INFO_DURATION_UPDATE = 12

        // AVPlayerSeekMode.AV_SEEK_CLOSEST（since 12）
        val SEEK_CLOSEST: AVPlayerSeekMode = 2u

        // AVPlaybackSpeed 在 klib 里是真实 enum（CEnum），只能按档位设置。
        // 表中按 speed 值升序排列，用于把任意 float 映射到最近档。
        val SPEED_TABLE: List<Pair<Float, AVPlaybackSpeed>> = listOf(
            0.125f to AVPlaybackSpeed.AV_SPEED_FORWARD_0_125_X,
            0.25f to AVPlaybackSpeed.AV_SPEED_FORWARD_0_25_X,
            0.5f to AVPlaybackSpeed.AV_SPEED_FORWARD_0_50_X,
            0.75f to AVPlaybackSpeed.AV_SPEED_FORWARD_0_75_X,
            1f to AVPlaybackSpeed.AV_SPEED_FORWARD_1_00_X,
            1.25f to AVPlaybackSpeed.AV_SPEED_FORWARD_1_25_X,
            1.5f to AVPlaybackSpeed.AV_SPEED_FORWARD_1_50_X,
            1.75f to AVPlaybackSpeed.AV_SPEED_FORWARD_1_75_X,
            2f to AVPlaybackSpeed.AV_SPEED_FORWARD_2_00_X,
            3f to AVPlaybackSpeed.AV_SPEED_FORWARD_3_00_X,
        )
    }

    /**
     * 回调归属锚点：userData 指向这里，而不是裸引擎引用。
     *
     * native 回调只给一个 userData 指针 + 触发它的 player 指针。换集/换源时旧
     * player 的 STATE_CHANGE/错误仍可能迟到，因此回调里必须同时校验
     * 「代次 + player 指针 + 是否已失效」，不匹配就整条丢弃，绝不允许旧
     * player 的事件改写新 player 的 state/playing。
     */
    private class CallbackAnchor(
        val engine: OhosPlayerEngine,
        val generation: Long,
        val player: CPointer<OH_AVPlayer>,
    ) {
        /** 释放前置 false；在途回调看到后不再触碰引擎状态。 */
        @Volatile
        var active: Boolean = true

        /** player 指针匹配（SDK 未回传时退化为只看代次）。 */
        fun matches(cbPlayer: CPointer<OH_AVPlayer>?): Boolean =
            cbPlayer == null || cbPlayer == player
    }

    /** 监听器快照，回调线程与主线程并发读写；K/N 新内存模型下 @Volatile 引用即可。 */
    @Volatile
    private var listeners: List<PlayerListener> = emptyList()

    /**
     * 主线程派发：native 回调线程只做最小记录与归属校验，真正的状态改写和
     * listener 通知都投到这里（PlayerEngine 契约要求主线程回调）。
     * PlaybackSession 本身也跑在 Dispatchers.Main，故 ohos 上的 Main 调度器可用。
     */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** ReleaseSync 会阻塞（非 idle 时较长），单独放后台，绝不占主线程/UI。 */
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 同一 native window 上的「旧 player ReleaseSync 完成 → 新 player SetVideoSurface」
     * 串行化。有两个 AVPlayer 同时消费同一 surface 时，旧 release 还会在该 window
     * 上做清理，必须等它返回。
     *
     * 等待全部用挂起（[ReleaseTask.done] / [Mutex]），不阻塞主线程；表只在 Main 读写。
     * 不同 window 各有一把锁，互不等待。
     */
    /** 一次 ReleaseSync 的完成信号与结果；[failed] 在 complete 之前由后台线程写入。 */
    private class ReleaseTask(val done: CompletableDeferred<Unit>) {
        @Volatile
        var failed: Boolean = false
    }

    private val pendingReleases =
        mutableMapOf<CPointer<OHNativeWindow>, MutableList<ReleaseTask>>()
    private val windowGates = mutableMapOf<CPointer<OHNativeWindow>, Mutex>()

    /**
     * 该 window 上曾经有 player 的 `ReleaseSync` 失败。native 可能仍把它当作
     * consumer，绑定新 player 会破坏「同一 surface 只有一个 consumer」的前提，
     * 因此该 window 不再接受 SetVideoSurface，直到 surface 被重建。
     * 失败路径保留了 window 引用（未 Unreference），地址不会被新对象复用。
     */
    private val blockedWindows = mutableSetOf<CPointer<OHNativeWindow>>()

    private fun gateFor(w: CPointer<OHNativeWindow>): Mutex =
        windowGates.getOrPut(w) { Mutex() }

    private fun trackRelease(window: CPointer<OHNativeWindow>?, task: ReleaseTask) {
        if (window == null) return
        pendingReleases.getOrPut(window) { mutableListOf() }.add(task)
        task.done.invokeOnCompletion {
            val failed = task.done.isCancelled || task.failed
            // 表只在 Main 读写；完成回调在后台线程，切回 Main 再清理。
            mainScope.launch {
                pendingReleases[window]?.let { list ->
                    list.remove(task)
                    if (list.isEmpty()) pendingReleases.remove(window)
                }
                if (failed) {
                    blockedWindows.add(window)
                    Log.w(TAG, "window marked unbindable: previous ReleaseSync failed")
                }
            }
        }
    }

    /**
     * 等该 window 上所有未完成的 ReleaseSync。返回 false 表示其中一次失败，
     * 该 window 已被标记为不可再绑定，调用方不得 SetVideoSurface。
     * 循环重查，避免等待期间又有新的 release 入表却被漏掉。
     */
    private suspend fun awaitWindowReleased(w: CPointer<OHNativeWindow>): Boolean {
        while (true) {
            if (blockedWindows.contains(w)) return false
            val list = pendingReleases[w] ?: return true
            // 先就地消化已完成的任务：[trackRelease] 的清理协程排在 mainScope 队列里，
            // 可能晚于本次 bind 执行；而 awaitAll 对已完成的 Deferred 立即返回，
            // 若只靠它等待，已完成条目无法出表，循环会空转占死主线程。
            val it = list.iterator()
            while (it.hasNext()) {
                val task = it.next()
                if (!task.done.isCompleted) continue
                if (task.done.isCancelled || task.failed) {
                    blockedWindows.add(w)
                    Log.w(TAG, "window marked unbindable: previous ReleaseSync failed")
                    return false
                }
                it.remove()
            }
            if (list.isEmpty()) {
                pendingReleases.remove(w)
                return !isWindowBlocked(w)
            }
            // 仍有未完成的任务：挂起等待（不是忙等），其中至少一个完成后重扫。
            list.map { it.done }.awaitAll()
        }
    }

    /** 直接看任务结果，不依赖「清理协程已先于绑定协程执行」的调度顺序。 */
    private fun isWindowBlocked(w: CPointer<OHNativeWindow>): Boolean =
        blockedWindows.contains(w) || pendingReleases[w]?.any { it.failed } == true

    // surface 回调来自 ArkTS UI 线程、状态改写来自 Dispatchers.Main，置 @Volatile
    // 保证 onVideoSurfaceChanged 看到的是已发布的 player/state。
    @Volatile
    private var player: CPointer<OH_AVPlayer>? = null

    /** 当前显示面。surface 可能在 player 之前/之后到达，统一由 [onVideoSurfaceChanged] 维护。 */
    @Volatile
    private var videoWindow: CPointer<OHNativeWindow>? = null

    /**
     * 当前 player 正在使用的 window，对应 bridge 通过
     * [OhosVideoSurfaceBridge.retainForPlayerRelease] 加的一次原生引用。
     * 只有该 player 的 `OH_AVPlayer_ReleaseSync` 返回后才减引用（见 [releasePlayer]）——
     * 否则 surface 并发销毁会回收仍在使用的 window（UAF）。
     */
    @Volatile
    private var boundWindow: CPointer<OHNativeWindow>? = null
    private var callbacksAnchor: StableRef<CallbackAnchor>? = null

    /** 每换一次 player 递增，回调按代次过滤旧 player 的异步事件。 */
    private var generation: Long = 0L

    @Volatile
    private var state: Int = STATE_IDLE
    private var durationMs: Long = 0L
    private var positionMs: Long = 0L
    private var playing = false
    private var completedNotified = false

    private var loadedUrl: String? = null
    private var headersJson: String = ""
    private var pendingSeekMs: Long = 0L

    /**
     * `SetURLSource` 已成功返回（native 处于 INITIALIZED）。Prepare 只有在它为
     * true 时才允许发出，避免在 IDLE 上 Prepare 失败或抢在 SetURLSource 之前。
     */
    @Volatile
    private var sourceReady = false

    /** 本轮 player 已发出过 Prepare，防止「setMedia 与 surface 回调都补 Prepare」重复调用。 */
    private var prepareIssued = false
    private var wantPlay = false
    private var speed: Float = 1f

    override fun setMedia(url: String, headersJson: String, startPositionMs: Long) {
        releasePlayer()
        // 引擎可能在上一次 release 时注销过，这里重新挂到全局 surface 通道。
        OhosVideoSurfaceBridge.register(this)
        videoWindow = OhosVideoSurfaceBridge.currentWindow()
        loadedUrl = url
        this.headersJson = headersJson
        pendingSeekMs = startPositionMs
        wantPlay = false
        completedNotified = false
        durationMs = 0L
        positionMs = startPositionMs
        playing = false
        state = STATE_IDLE

        if (headersJson.isNotEmpty()) {
            // 本版本 native API 无请求头入口，明确留痕，避免误判成「源坏了」。
            // TODO(请求头)：OH_AVPlayer_SetURLSource 不接受 header；本 SDK 的
            // avplayer.h / platform klib 也没有 OH_AVMediaSource_CreateWithUrl。
            // 可选方案：① 本地 HTTP 代理注入头；② ArkTS media.createAVPlayer
            // 的 header 参数。详见 DengYingOhos/docs/ohos-player-notes.md。
            Log.w(TAG, "headers ignored: native OH_AVPlayer has no header API ($headersJson)")
        }

        val p = OH_AVPlayer_Create()
        if (p == null) {
            notifyError(PlaybackErrorKind.Unknown, -1, "OH_AVPlayer_Create returned null")
            return
        }
        player = p
        sourceReady = false
        prepareIssued = false

        // userData 里放「引擎 + 代次 + player」锚点，StableRef 保活到 ReleaseSync 返回之后。
        val anchor = CallbackAnchor(this, generation, p)
        val anchorRef = StableRef.create(anchor)
        callbacksAnchor = anchorRef
        OH_AVPlayer_SetOnInfoCallback(
            p,
            staticCFunction { cbPlayer: CPointer<OH_AVPlayer>?, type: AVPlayerOnInfoType,
                body: CPointer<OH_AVFormat>?, userData: COpaquePointer? ->
                try {
                    val a = userData?.asStableRef<CallbackAnchor>()?.get() ?: return@staticCFunction
                    if (!a.active || !a.matches(cbPlayer)) return@staticCFunction
                    // body 只在回调期间有效：在此线程把需要的值全部拷出来，
                    // 再投主线程处理；不在回调线程改引擎状态。
                    a.engine.dispatchInfoFromCallback(a, type, body)
                } catch (t: Throwable) {
                    Log.w(TAG, "onInfo failed: ${t.message}")
                }
            },
            anchorRef.asCPointer(),
        )
        OH_AVPlayer_SetOnErrorCallback(
            p,
            staticCFunction { cbPlayer: CPointer<OH_AVPlayer>?, code: Int, msg: CPointer<ByteVar>?,
                userData: COpaquePointer? ->
                try {
                    val a = userData?.asStableRef<CallbackAnchor>()?.get() ?: return@staticCFunction
                    if (!a.active || !a.matches(cbPlayer)) return@staticCFunction
                    a.engine.dispatchErrorFromCallback(a, code, msg?.toKString() ?: "")
                } catch (t: Throwable) {
                    Log.w(TAG, "onError failed: ${t.message}")
                }
            },
            anchorRef.asCPointer(),
        )

        // 顺序硬约束：先 SetURLSource（IDLE → INITIALIZED），成功后才允许
        // 「SetVideoSurface → Prepare」。绝不能在 SetURLSource 之前 Prepare。
        val ret = OH_AVPlayer_SetURLSource(p, url)
        if (ret != 0u) {
            // 失败也必须收尾：释放刚建的 player 与锚点，置错误态并通知，
            // 否则 PlaybackSession 会停在 Buffering 且 player 泄漏。
            player = null
            callbacksAnchor = null
            val failedWindow = boundWindow
            boundWindow = null
            generation++
            state = STATE_ERROR
            loadedUrl = null
            playing = false
            sourceReady = false
            prepareIssued = false
            anchorRef.get().active = false
            releaseAsync(p, failedWindow, anchorRef)
            notifyError(PlaybackErrorKind.SourceGone, ret.toInt(), "SetURLSource failed: $ret")
            return
        }

        sourceReady = true
        val w = videoWindow
        if (w != null) {
            // 有 surface：等该 window 上旧 ReleaseSync 成功 → SetVideoSurface → Prepare。
            scheduleBind(p)
        } else {
            // 无 surface：停在 INITIALIZED，等 surface 到达时由
            // [onVideoSurfaceChanged] 绑定并补 Prepare。
            Log.w(TAG, "no video surface yet: source ready, Prepare deferred until surface arrives")
        }
    }

    override fun play() {
        wantPlay = true
        val p = player ?: return
        if (state == STATE_PREPARED ||
            state == STATE_PAUSED ||
            state == STATE_COMPLETED ||
            state == STATE_STOPPED
        ) {
            applyPendingSeek(p)
            OH_AVPlayer_Play(p)
        }
        // INITIALIZED 状态等 PREPARED 回调再起播（见 handleInfo）。
    }

    override fun pause() {
        wantPlay = false
        val p = player ?: return
        OH_AVPlayer_Pause(p)
    }

    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs
        val p = player ?: return
        if (state == STATE_PREPARED || state == STATE_PLAYING ||
            state == STATE_PAUSED || state == STATE_COMPLETED
        ) {
            val seconds = (positionMs / 1000L).toInt()
            OH_AVPlayer_Seek(p, seconds, SEEK_CLOSEST)
        } else {
            pendingSeekMs = positionMs
        }
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        val p = player ?: return
        if (state == STATE_PREPARED || state == STATE_PLAYING ||
            state == STATE_PAUSED
        ) {
            // SetPlaybackRate 是 API 20；compatibleSdkVersion 是 17，必须用 API 11 的枚举接口。
            OH_AVPlayer_SetPlaybackSpeed(p, avSpeedOf(speed))
        }
    }

    override fun release() {
        wantPlay = false
        loadedUrl = null
        releasePlayer()
        OhosVideoSurfaceBridge.unregister(this)
        listeners = emptyList()
    }

    override val currentPosition: Long
        get() {
            val p = player ?: return positionMs
            return memScoped {
                val t = alloc<IntVar>()
                if (OH_AVPlayer_GetCurrentTime(p, t.ptr) == 0u) t.value.toLong() else positionMs
            }
        }

    override val duration: Long
        get() {
            val p = player ?: return durationMs
            return memScoped {
                val d = alloc<IntVar>()
                if (OH_AVPlayer_GetDuration(p, d.ptr) == 0u) d.value.toLong() else durationMs
            }
        }

    override val isPlaying: Boolean get() = playing

    override val currentUrl: String? get() = loadedUrl

    override fun addListener(listener: PlayerListener) {
        val current = listeners
        if (current.any { it === listener }) return
        listeners = current + listener
    }

    override fun removeListener(listener: PlayerListener) {
        listeners = listeners.filterNot { it === listener }
    }

    /**
     * 视图绑定口子。画面由 native AVPlayer 直接渲染到 ArkUI XComponent 的
     * surface（见 [OhosVideoSurfaceBridge]），Compose 侧拿不到、也不需要原生视图
     * 句柄，因此恒为 null（合法契约，由 `VideoSurface` 画占位/透明）。
     */
    override fun viewHandle(): Any? = null

    // ---------------- 内部 ----------------

    private fun releasePlayer() {
        val p = player
        // 该 window 的引用必须活到 ReleaseSync 返回之后：surface 可能在此期间被销毁。
        val releasedWindow = boundWindow
        boundWindow = null
        player = null
        playing = false
        state = STATE_RELEASED
        sourceReady = false
        prepareIssued = false
        generation++

        val anchorRef = callbacksAnchor
        callbacksAnchor = null
        // 顺序是硬约束：① 锚点先失效（release 期间到达的旧回调立刻丢弃）
        // ② 后台 ReleaseSync ③ 返回后 dispose StableRef（否则回调读已释放内存）。
        anchorRef?.get()?.active = false

        if (p != null) {
            // ReleaseSync 会阻塞调用线程（非 idle 时耗时更长），而 setMedia 在
            // Dispatchers.Main 上调进来 —— 必须移出主线程。这里不阻塞等待：
            // 新 player 若要用同一 window，[scheduleBind] 会 join 这个 Job。
            releaseAsync(p, releasedWindow, anchorRef)
        } else {
            OhosVideoSurfaceBridge.releasePlayerWindow(releasedWindow)
            anchorRef?.dispose()
        }
    }

    /**
     * 后台释放 player，并把 Job 记到 window 上，供后续绑定串行等待。
     *
     * ReleaseSync 失败时**不 dispose 锚点、不减 window 引用**：native 可能仍持有
     * player 并回调 userData，提前 dispose 会造成 UAF。锚点 active 早已为 false，
     * 泄漏的回调会被直接丢弃。先重试一次，仍失败就保持现状。
     */
    private fun releaseAsync(
        p: CPointer<OH_AVPlayer>,
        window: CPointer<OHNativeWindow>?,
        anchorRef: StableRef<CallbackAnchor>?,
    ) {
        // 结果必须进入串行状态：true = 该 window 可以安全交给下一个 player，
        // false = native 可能仍在使用它，绑定路径不得再 SetVideoSurface。
        val task = ReleaseTask(CompletableDeferred())
        releaseScope.launch {
            var ret = OH_AVPlayer_ReleaseSync(p)
            if (ret != 0u) {
                Log.w(TAG, "ReleaseSync failed: $ret; retrying once")
                ret = OH_AVPlayer_ReleaseSync(p)
            }
            if (ret != 0u) {
                // 只重试一次，避免 ReleaseSync 持续失败时无限阻塞换集。
                Log.w(TAG, "ReleaseSync still failed: $ret; keep anchor + window reference")
                task.failed = true
                task.done.complete(Unit)
                return@launch
            }
            // player 已不再持有该 window，这时才允许 bridge 减掉那份引用。
            OhosVideoSurfaceBridge.releasePlayerWindow(window)
            anchorRef?.dispose()
            task.done.complete(Unit)
        }
        trackRelease(window, task)
    }

    /**
     * surface 就绪/销毁通知（来自 [OhosVideoSurfaceBridge]）。
     *
     * AVPlayer 只保证 Prepare 之前设置显示面可靠：
     * - IDLE/INITIALIZED：直接补设（仍在 Prepare 之前）。
     * - Prepare 之后（窗口重建/尺寸变化/折叠屏切换）：in-place 重设不可靠，
     *   走 [rebuildForSurface] 安全重建，保证新 surface 立即生效，而不是黑到
     *   下一次 setMedia。
     */
    fun onVideoSurfaceChanged(window: CPointer<OHNativeWindow>?) {
        videoWindow = window
        // window == null 表示 surface 即将销毁：只清缓存，不动 [boundWindow]。
        // 该 player 仍在使用旧 window，它的引用由 [releasePlayer] 在 ReleaseSync
        // 返回后才减，bridge 的 DestroyNativeWindow 因此不会提前回收。
        if (window == null) return
        val p = player ?: return
        // 统一切到 Main 处理状态重建（surface 回调来自 ArkUI UI 线程）。
        mainScope.launch {
            if (videoWindow != window || player !== p) return@launch
            if (state == STATE_IDLE || state == STATE_INITIALIZED) {
                // 无 surface 启动时这里才是补 Prepare 的地方：绑定后再 Prepare。
                bindCurrentWindow(p)
            } else {
                rebuildForSurface()
            }
        }
    }

    /**
     * Prepare 之后 surface 被重建：保留 url / headers / 播放位置 / 起播意图，
     * 重新走 setMedia（新 player 在新 surface 上、Prepare 之前完成绑定）。
     *
     * 不做 in-place `SetVideoSurface`：头文件只声明「失败返回 INVALID_VAL」，
     * 官方文档要求 Prepare 之前调用，之后重设存在「返回成功但不切画面」的
     * 不确定性，无法在不改工具链的前提下验证，因此选可证明正确的路径。
     * 旧 player 在后台释放，window 由引用计数保活到 ReleaseSync 返回。
     */
    private fun rebuildForSurface() {
        val url = loadedUrl ?: return
        if (player == null) return
        val resume = wantPlay || playing
        // INITIALIZED 期间用户已经 seek 过：pendingSeekMs 比 GetCurrentTime
        // 更能代表用户想要的位置（后者此时可能还是 0）。
        val position = if (pendingSeekMs > 0L) pendingSeekMs else currentPosition
        Log.w(TAG, "surface rebuilt after Prepare; re-preparing from ${position}ms")
        setMedia(url, headersJson, position)
        // setMedia 会重置起播意图；Prepare 回调在此之前不会到达（绑定是挂起的）。
        wantPlay = resume
    }

    /**
     * 串行地完成「等旧 release → 绑当前 surface → Prepare」。
     *
     * 若同一 window 上还有旧 player 的 `ReleaseSync` 未返回，先挂起等它结束：
     * 两个 AVPlayer 不能同时作为同一 surface 的 consumer。等待是挂起式的，
     * 主线程不被阻塞。
     *
     * 等待期间 surface 可能已被销毁/重建：恢复后必须重新读取 [videoWindow]，
     * 只对**当前** window 绑定；旧指针不再 retain / SetVideoSurface（否则 UAF
     * 且会把新 surface 覆盖回旧窗口）。
     */
    private fun scheduleBind(p: CPointer<OH_AVPlayer>) {
        mainScope.launch { bindCurrentWindow(p) }
    }

    private suspend fun bindCurrentWindow(p: CPointer<OH_AVPlayer>) {
        repeat(MAX_BIND_ATTEMPTS) {
            // 排队期间可能已换集/换源/release。
            if (player !== p) return
            val target = videoWindow ?: return
            if (!awaitWindowReleased(target)) {
                Log.w(TAG, "window blocked by failed ReleaseSync; SetVideoSurface skipped")
                return
            }
            var done = false
            gateFor(target).withLock {
                if (player !== p) return@withLock
                // 等锁期间窗口又变了：不碰旧指针，回到外层按当前窗口重试。
                if (videoWindow !== target) return@withLock
                if (boundWindow != target) bindSurface(p, target)
                maybePrepare(p)
                done = true
            }
            if (done) return
        }
        Log.w(TAG, "surface changed repeatedly; binding left to next surface event")
    }

    /** 只有 SetURLSource 成功后、且 player 仍停在 IDLE/INITIALIZED 时才 Prepare。 */
    private fun maybePrepare(p: CPointer<OH_AVPlayer>) {
        if (player !== p || !sourceReady || prepareIssued) return
        if (state != STATE_IDLE && state != STATE_INITIALIZED) return
        prepareIssued = true
        val ret = OH_AVPlayer_Prepare(p)
        if (ret != 0u) {
            prepareIssued = false
            Log.w(TAG, "Prepare failed: $ret")
        }
    }

    /**
     * 把 [w] 绑给 [p]（必须在 Prepare 之前）。先由 bridge 加一次原生引用，
     * 保证这个 window 在本 player 存活期间不会被 surface 销毁回收。
     */
    private fun bindSurface(p: CPointer<OH_AVPlayer>, w: CPointer<OHNativeWindow>) {
        val prev = boundWindow
        if (prev == w) return
        OhosVideoSurfaceBridge.retainForPlayerRelease(w)
        boundWindow = w
        val ret = OH_AVPlayer_SetVideoSurface(p, w)
        if (ret != 0u) Log.w(TAG, "SetVideoSurface failed: $ret")
        if (prev != null) {
            // 同一 player 换显示面：旧 window 已不再被它使用，可立即减引用。
            OhosVideoSurfaceBridge.releasePlayerWindow(prev)
        }
    }

    /** 最近档映射：AVPlaybackSpeed 只有离散档位，取绝对差最小的一档。 */
    private fun avSpeedOf(speed: Float): AVPlaybackSpeed =
        SPEED_TABLE.minByOrNull { abs(it.first - speed) }?.second
            ?: AVPlaybackSpeed.AV_SPEED_FORWARD_1_00_X

    /** 回调是否仍属于当前 player（代次 + 指针 + 未失效）。 */
    private fun isCurrent(a: CallbackAnchor): Boolean =
        a.active && a.generation == generation && player == a.player

    private fun applyPendingSeek(p: CPointer<OH_AVPlayer>) {
        if (pendingSeekMs > 0L) {
            OH_AVPlayer_Seek(p, (pendingSeekMs / 1000L).toInt(),
                SEEK_CLOSEST)
            pendingSeekMs = 0L
        }
        if (speed != 1f) {
            OH_AVPlayer_SetPlaybackSpeed(p, avSpeedOf(speed))
        }
    }

    /** 回调线程拷出的不可变事件；body 指针不得越过 native 回调作用域。 */
    private class NativeInfo(
        val anchor: CallbackAnchor,
        val type: Int,
        val state: Int,
        val duration: Long,
        val position: Int,
        val width: Int,
        val height: Int,
        val bufferingType: Int,
        val bufferingValue: Int,
    )

    /** 在 AVPlayer 回调线程执行：只读 body + 投递主线程，不改任何引擎状态。 */
    private fun dispatchInfoFromCallback(
        a: CallbackAnchor,
        type: AVPlayerOnInfoType,
        body: CPointer<OH_AVFormat>?,
    ) {
        val t = type.toInt()
        val event = NativeInfo(
            anchor = a,
            type = t,
            state = if (t == INFO_STATE_CHANGE) intOf(body, OH_PLAYER_STATE) else -1,
            duration = if (t == INFO_STATE_CHANGE || t == INFO_DURATION_UPDATE)
                longOf(body, OH_PLAYER_DURATION) else -1L,
            // OH_PLAYER_CURRENT_POSITION 是 int32_t，review 已确认不能用 GetLongValue。
            position = if (t == INFO_POSITION_UPDATE)
                intOf(body, OH_PLAYER_CURRENT_POSITION) else -1,
            width = if (t == INFO_RESOLUTION_CHANGE) intOf(body, OH_PLAYER_VIDEO_WIDTH) else 0,
            height = if (t == INFO_RESOLUTION_CHANGE) intOf(body, OH_PLAYER_VIDEO_HEIGHT) else 0,
            bufferingType = if (t == INFO_BUFFERING_UPDATE)
                intOf(body, OH_PLAYER_BUFFERING_TYPE) else -1,
            bufferingValue = if (t == INFO_BUFFERING_UPDATE)
                intOf(body, OH_PLAYER_BUFFERING_VALUE) else -1,
        )
        mainScope.launch {
            // 派发后二次归属校验：排队期间可能已经换集/换源/release。
            if (!isCurrent(a)) return@launch
            handleInfo(event)
        }
    }

    private fun dispatchErrorFromCallback(a: CallbackAnchor, code: Int, message: String) {
        mainScope.launch {
            if (!isCurrent(a)) return@launch
            onNativeError(code, message)
        }
    }

    /** 主线程：状态改写与 listener 通知都在这里发生。 */
    private fun handleInfo(ev: NativeInfo) {
        when (ev.type) {
            INFO_STATE_CHANGE -> onStateChanged(ev.state, ev.duration)
            INFO_DURATION_UPDATE -> {
                if (ev.duration > 0L) durationMs = ev.duration
            }
            INFO_POSITION_UPDATE -> {
                if (ev.position >= 0) positionMs = ev.position.toLong()
            }
            INFO_RESOLUTION_CHANGE -> {
                notify { it.onVideoSize(ev.width, ev.height) }
            }
            INFO_BUFFERING_UPDATE -> {
                // AVPLAYER_BUFFERING_START = 1 / AVPLAYER_BUFFERING_END = 2。
                if (ev.bufferingType == 1 || ev.bufferingType == 2) {
                    notify { it.onBuffering(ev.bufferingType == 1) }
                } else {
                    // PERCENT / CACHED_DURATION：按百分比兜底（1..99 = 缓冲中）。
                    if (ev.bufferingValue in 1..99) notify { it.onBuffering(true) }
                }
            }
            // EOS 与 STATE_CHANGE(COMPLETED) 都可能到，交给 completedNotified 去重。
            INFO_EOS -> notifyCompleted()
            else -> Unit
        }
    }

    private fun intOf(body: CPointer<OH_AVFormat>?, key: CPointer<ByteVar>?): Int {
        if (body == null || key == null) return 0
        val name = key.toKString()
        return memScoped {
            val v = alloc<IntVar>()
            if (OH_AVFormat_GetIntValue(body, name, v.ptr)) v.value else 0
        }
    }

    private fun longOf(body: CPointer<OH_AVFormat>?, key: CPointer<ByteVar>?): Long {
        if (body == null || key == null) return 0L
        val name = key.toKString()
        return memScoped {
            val v = alloc<LongVar>()
            if (OH_AVFormat_GetLongValue(body, name, v.ptr)) v.value else 0L
        }
    }

    private fun onStateChanged(rawState: Int, bodyDuration: Long) {
        state = rawState
        when (rawState) {
            STATE_PREPARED -> {
                // 优先用 STATE_CHANGE body 里的时长，避免在回调路径上重入
                // OH_AVPlayer_GetDuration（review 建议）。
                durationMs = if (bodyDuration > 0L) bodyDuration else duration
                notify { it.onReady(durationMs) }
                player?.let { applyPendingSeek(it) }
                if (wantPlay) player?.let { OH_AVPlayer_Play(it) }
            }
            STATE_PLAYING -> {
                playing = true
                notify { it.onPlaying() }
            }
            STATE_PAUSED, STATE_STOPPED -> {
                playing = false
                notify { it.onPaused() }
            }
            STATE_COMPLETED -> {
                playing = false
                notifyCompleted()
            }
            STATE_ERROR -> notifyError(PlaybackErrorKind.Unknown, -1, "player entered AV_ERROR")
            else -> Unit
        }
    }

    private fun onNativeError(code: Int, message: String) {
        playing = false
        state = STATE_ERROR
        notifyError(classify(code, message), code, message)
    }

    private fun classify(code: Int, message: String): PlaybackErrorKind {
        val text = message.lowercase()
        return when {
            "timeout" in text || "network" in text || "connect" in text -> PlaybackErrorKind.Network
            "404" in text || "403" in text || "not found" in text -> PlaybackErrorKind.SourceGone
            "codec" in text || "decode" in text || "demux" in text -> PlaybackErrorKind.Decode
            else -> PlaybackErrorKind.Unknown
        }
    }

    private fun notifyCompleted() {
        if (completedNotified) return
        completedNotified = true
        playing = false
        notify { it.onCompleted() }
    }

    private fun notifyError(kind: PlaybackErrorKind, code: Int, message: String) {
        Log.w(TAG, "error kind=$kind code=$code msg=$message")
        notify { it.onError(kind, code, message) }
    }

    private inline fun notify(block: (PlayerListener) -> Unit) {
        for (l in listeners) {
            try {
                block(l)
            } catch (t: Throwable) {
                Log.w(TAG, "listener failed: ${t.message}")
            }
        }
    }
}
