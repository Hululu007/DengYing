package com.dengying.player.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dengying.player.platform.OhosScreenLevels
import com.dengying.player.platform.OhosShellControl
import com.dengying.player.platform.OhosWindowControl
import com.dengying.player.player.OhosPlayerEngineHost
import com.dengying.player.player.OhosVideoSurfaceBridge
import com.dengying.player.player.PlayerEngine

/**
 * ohos 端平台能力 actual（CMP spike，docs/04 §16.40）。
 *
 * 首批一律给「语义正确的降级实现」——口径同 `Platform.jvm.kt`（桌面端）：
 * 先让 commonMain 整层编过、页面能渲染，真窗口/亮度/音量/画面能力随后按
 * parity 清单逐项补（§16.40 未验证清单）。
 */

/**
 * 画面承载：ArkTS 壳在 Compose 下方铺了一层全屏 XComponent（ComposePage.ets），
 * surfaceId 经 NAPI → [OhosVideoSurfaceBridge] → `OH_AVPlayer_SetVideoSurface`。
 *
 * 本 actual 不再自己建视图，只负责：
 * - 组合进入/退出时驱动 `OhosPlayerEngineHost.surfaceActive/Inactive`
 *   （计数归零只暂停，不释放）；
 * - 订阅 surface 就绪状态，未就绪时画黑底、就绪后透明，让下层的 XComponent
 *   画面透出来；
 * - 通过 `onBind` 上报绑定状态。
 *
 * 【打洞分工（2026-11-14）】CMP 的 Compose 渲染面（CanvasNode/RenderNode 或
 * TEXTURE XComponent）与视频层同树合成，透明区域可以透出下层视频——所以
 * 本 actual 在 bound 后透明即可打洞；根 Box 的黑底让位逻辑在 PlayerScreen.kt
 * （`shouldRevealVideoSurface(..., surfaceBound, prepared, buffering, hasError)`
 * 为真才透明），两处配合视频才可见。该让位由 `videoSurfaceIsBelowCompose`
 * 限定在 ohos：Android/JVM 的根 Box 恒为不透明黑底。
 * 内嵌小窗（InlinePlayer 的 16:9 Box）仍是黑底：全屏 XComponent 与小窗的
 * 坐标对齐需要 Compose↔ArkUI interop，尚未做。
 *
 * 已知限制（WIP）：XComponent 是全屏的，视频会铺满窗口而不是 Compose 布局里
 * 的播放器框。要精确对齐需要在 Compose 里插入 interop 视图（CMP ui-ohos 的
 * insertInteropView 路径），当前播放页/内嵌位复用时位置会不一致。
 */
@Composable
actual fun VideoSurface(
    engine: PlayerEngine?,
    modifier: Modifier,
    zoomMode: VideoZoomMode,
    onBind: (bound: Boolean) -> Unit,
) {
    var surfaceReady by remember { mutableStateOf(OhosVideoSurfaceBridge.isReady()) }

    DisposableEffect(engine) {
        if (engine != null) OhosPlayerEngineHost.surfaceActive()
        val listener: (Boolean) -> Unit = { surfaceReady = it }
        OhosVideoSurfaceBridge.addReadyListener(listener)
        onDispose {
            OhosVideoSurfaceBridge.removeReadyListener(listener)
            if (engine != null) OhosPlayerEngineHost.surfaceInactive()
        }
    }

    val bound = engine != null && surfaceReady
    LaunchedEffect(bound) { onBind(bound) }

    Box(
        modifier
            .fillMaxSize()
            .background(if (bound) Color.Transparent else Color.Black)
    )
}

/**
 * 窗口方向/系统栏控制。
 *
 * 鸿蒙的方向与系统栏只能由 ArkUI 的 `window` 接口改，Kotlin 调不到 ArkTS，
 * 因此这里只把请求推给 ArkTS 注册的 sink（见 [OhosWindowControl]）：
 * `EntryAbility` 收到后调 `setPreferredOrientation` + `setWindowSystemBarEnable`。
 *
 * 与 Android（`requestedOrientation` + `WindowInsetsController`）、iOS
 * （`IosOrientationLock` + Swift 的 `prefersStatusBarHidden`）同一语义：
 * 只请求、不保证生效，且不抛异常（sink 未注册时静默降级）。
 */
actual class FullscreenState internal constructor() {
    actual fun enter(landscape: Boolean) {
        OhosWindowControl.requestLandscape(landscape)
    }

    actual fun exit() {
        OhosWindowControl.requestLandscape(false)
    }
}

@Composable
actual fun rememberFullscreenState(): FullscreenState = FullscreenState()

@Composable
actual fun ConsumePortraitOrientation() {
    // 鸿蒙没有 Activity.requestedOrientation，方向只能经 NAPI 让 ArkTS 改
    // （与 FullscreenState.exit 同一条路）。调用方 DetailScreen 只在「非全屏形态」
    // 组合它（`if (!vm.screenOnlyFullscreen)`），所以这里无条件收敛回竖屏不会
    // 和全屏请求打架；请求本身幂等，重复 SideEffect 无副作用。
    SideEffect { OhosWindowControl.requestLandscape(false) }
}

/**
 * 亮度：经壳通道（OP_SET_BRIGHTNESS）调到 ArkUI 的 `window.setWindowBrightness`。
 *
 * 契约「只影响当前窗口、不改系统设置」由 ArkTS 侧的窗口接口保证；起点优先用壳
 * 推上来的真值（[OhosScreenLevels]），读不到退化为 0.5。`restore` 发 -1 让窗口
 * 交还系统亮度，避免用户顶着改过的亮度去别的页面。
 */
actual class BrightnessController internal constructor() {
    private var last = Float.NaN

    actual fun current(): Float {
        if (!last.isNaN()) return last
        val known = OhosScreenLevels.brightness
        last = if (known.isNaN()) 0.5f else known
        return last
    }

    actual fun adjust(base: Float, fraction: Float): Float {
        val v = (base + fraction).coerceIn(0.01f, 1f)
        last = v
        OhosShellControl.call(OhosShellControl.OP_SET_BRIGHTNESS, v.toString())
        return v
    }

    actual fun restore() {
        // 幂等：没调过亮度就不必发通道。调用方的 DisposableEffect 在窗口重组时
        // 可能反复 dispose，这里必须挡住，否则会变成每秒十几次的 NAPI 调用。
        if (last.isNaN()) return
        last = Float.NaN
        OhosShellControl.call(OhosShellControl.OP_RESTORE_BRIGHTNESS, "")
    }
}

@Composable
actual fun rememberBrightnessController(): BrightnessController? =
    remember { BrightnessController() }

/**
 * 媒体音量：**鸿蒙侧不提供**，保留类型只为对齐 expect。
 *
 * 原因（实测 + SDK 声明）：普通应用改系统媒体音量需要
 * `ohos.permission.ACCESS_NOTIFICATION_POLICY`（受限能力），
 * `AudioVolumeGroupManager` 只有读没有写；唯一能改的是播放器自身的音量
 * （AVPlayer.setVolume），而 `PlayerEngine` 契约里没有音量方法。
 * 与其给一个「滑了没反应」的手势，不如按契约返回 null 让 UI 关掉它 ——
 * [rememberVolumeController] 就是这个口径。
 */
actual class VolumeController internal constructor() {
    actual fun currentFraction(): Float = 0.5f

    actual fun adjust(base: Float, fraction: Float): Float =
        (base + fraction).coerceIn(0f, 1f)
}

/** 拿不到可写音量通道 → 返回 null，全屏音量手势按「不可用」处理。 */
@Composable
actual fun rememberVolumeController(): VolumeController? = null

/** 系统返回：注册进 [OhosBackStack]，由 ArkTS 的 `onBackPressed` 触发。 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { /* 未启用：不占位 */ }
        val handler: () -> Unit = { current.value() }
        OhosBackStack.register(handler)
        onDispose { OhosBackStack.unregister(handler) }
    }
}

/** 鸿蒙没有 `OnBackPressedDispatcher`，导航层必须自己注册路由级返回处理。 */
actual val systemBackNeedsHost: Boolean = true

/**
 * 鸿蒙系统返回键的处理器栈（LIFO：后注册者优先，与 Android 的 dispatcher 同序）。
 *
 * 事件从 ArkTS 的 `UIAbility.onBackPressed()` 进来：`libentry.so` 的
 * `dispatchBack()` 用 `dlsym` 找到本对象的 [dengyUiBack]，同步返回
 * 「是否被消费」。返回 false 时 ArkTS 返回 false，系统按默认行为退出应用。
 *
 * 【为什么同步】返回键必须当场给出「消费与否」的结论，不能丢给另一个线程再回调；
 * CPF 的 Compose 组合与 ArkUI 主线程是同一个，所以这里能直接在 UI 线程上调导航。
 */
object OhosBackStack {
    private val handlers = mutableListOf<() -> Unit>()

    fun register(handler: () -> Unit) {
        handlers.add(handler)
    }

    fun unregister(handler: () -> Unit) {
        handlers.remove(handler)
    }

    /** @return true 表示已有处理器消费了这次返回。 */
    fun dispatch(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}

/** libentry.so 的 `dispatchBack()` 经 dlsym 调用本函数。 */
@kotlin.native.CName("dengy_ui_back")
fun dengyUiBack(): Int = if (OhosBackStack.dispatch()) 1 else 0


/** 方向：ArkTS 壳通过 NAPI 推送窗口方向，组合订阅后重组全屏分支。 */
@Composable
actual fun rememberIsLandscape(): Boolean {
    val bridge = remember { OhosVideoSurfaceBridge }
    var landscape by remember { mutableStateOf(bridge.isLandscape()) }
    DisposableEffect(bridge) {
        val listener: (Boolean) -> Unit = { landscape = it }
        bridge.addLandscapeListener(listener)
        onDispose { bridge.removeLandscapeListener(listener) }
    }
    return landscape
}
