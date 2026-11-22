package com.dengying.player.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.CoreGraphics.CGRectMake
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISlider
import platform.UIKit.UIViewController
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS

/**
 * iOS 端的全屏 / 方向 / 亮度 / 音量实现（KMP P5-6 落真身）。
 *
 * ## 与 Android 侧的差异（这四项在 iOS 上能力天然不同，不是偷工）
 *
 * | 能力 | Android | iOS（本文件） |
 * |---|---|---|
 * | 方向 | `Activity.requestedOrientation` | `UIWindowScene.requestGeometryUpdateWithPreferences` + 外壳 VC 的 `supportedInterfaceOrient` |
 * | 亮度 | `Window.attributes.screenBrightness`（窗口级） | **无窗口级概念**，见下 |
 * | 音量 | `AudioManager` 改媒体流 | **无公开 API**，只能驱动 `MPVolumeView` 的滑块 |
 * | 横竖判断 | 配置变更 | `UIWindowScene.interfaceOrientation` |
 *
 * ## 亮度：本文件**不做手势**（返回 null）
 *
 * commonMain 契约第 1 条写明「调整窗口亮度，不写系统设置、不需要权限」。
 * iOS 只有 `UIScreen.mainScreen.brightness`，它改的是**系统**亮度（退出 App 也不复原），
 * 违反该契约。故这里 `rememberBrightnessController()` 返回 `null`，调用方据
 * `brightnessAvailable = controller != null` 自动关闭亮度手势 —— 这正是该分支
 * 存在的原因，也是三端形状一致的体现。
 *
 * 若产品决定「接受改系统亮度」，改动点只在本函数：返回一个调
 * `UIScreen.mainScreen.brightness` 的实现并在 `restore()` 里写回进入前的值。
 * 这是产品决策，已记入 `docs/11-Mac待办清单.md` 的 M-16 项，不在此擅改。
 *
 * ## 音量：从 `MPVolumeView` 里取系统音量滑块
 *
 * iOS 没有「设置媒体音量」的公开 API。标准且合规的做法是：把一个 [MPVolumeView]
 * 加进视图树（哪怕尺寸为 1×1），再取它的 `UISlider` 子视图，读写 `value` 即等于
 * 读写系统音量。注意：**不能 `hidden = true`**（隐藏会被审核判定为刻意规避系统 UI），
 * 故这里用 `alpha = 0.01` 的 1×1 容器承放它。
 *
 * ## 方向：需要外壳配合
 *
 * `requestGeometryUpdateWithPreferences` 只在「外壳 VC 的 `supportedInterfaceOrientations`
 * 允许」时才生效，故 Swift 侧 `MainViewController` 已改写为按 [IosOrientationLock.mask]
 * 返回掩码。
 */
@OptIn(ExperimentalForeignApi::class)
actual class FullscreenState internal constructor() {

    actual fun enter(landscape: Boolean) {
        IosOrientationLock.request(
            if (landscape) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait,
        )
    }

    actual fun exit() {
        // 语义是「显式回竖屏」，不是「恢复进入前的快照」（commonMain 契约）
        IosOrientationLock.request(UIInterfaceOrientationMaskPortrait)
    }
}

@Composable
actual fun rememberFullscreenState(): FullscreenState = remember { FullscreenState() }

/**
 * 请求竖屏并放开方向锁。
 *
 * iOS 上「消费一次竖屏意图」= 把方向锁重置为「允许竖屏」，否则从横屏播放页返回后
 * 列表页会停在横屏。
 *
 * ## 【RESUMED 才收敛】与 Android v1.3.4 同口径
 *
 * 导航过渡期（详情页 → 播放页的转场）本页仍在组合、仍会重组，`SideEffect` 因此
 * 会被再次执行。此时若照旧把 [IosOrientationLock] 顶回竖屏，就会把播放页刚
 * 请求的横屏覆盖掉，真机表现为「先横屏又弹回竖屏」。播放页从发起请求到转屏
 * 生效之间，没有任何机制能阻止旧页面的副作用重跑，故判据只能放在这里：
 * **只有本页仍是前台页（RESUMED）时才收敛竖屏**。
 *
 * 返回本页时生命周期回到 RESUMED，重组中的 `SideEffect` 自然重新收敛竖屏，
 * 与 Android 侧 `Fullscreen.android.kt` 的行为逐条一致。
 */
@Composable
actual fun ConsumePortraitOrientation() {
    val owner = LocalLifecycleOwner.current
    SideEffect {
        if (owner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            IosOrientationLock.request(UIInterfaceOrientationMaskPortrait)
        }
    }
}

/**
 * 亮度控制器：**有意返回 null**。
 *
 * 理由见文件头「亮度」一节：iOS 没有窗口级亮度，改 `UIScreen.brightness` 会写系统设置，
 * 违反 commonMain 契约。返回 null 让调用方关闭亮度手势，这是契约允许的表达方式
 * （`rememberBrightnessController` 的 Kdoc：null ⇒ `brightnessAvailable false`）。
 */
actual class BrightnessController internal constructor() {

    /** 进入播放页时的系统亮度，用于 [restore] */
    private var saved: Double? = null

    /**
     * 当前窗口亮度。
     *
     * 读 `UIScreen.mainScreen.brightness`（iOS 唯一可读来源），但**只有**在
     * 我们改过它时才需要 `restore()`，其余情况不写。
     */
    actual fun current(): Float = platform.UIKit.UIScreen.mainScreen.brightness.toFloat()

    /**
     * 调整亮度。
     *
     * 唯一可用 API 改的是**系统**亮度，这是 commonMain 契约第 1 条禁止的；
     * 但因为 [rememberBrightnessController] 已返回 null，本方法在正常路径上
     * 永远不会被调用。实现保留完整是为了「产品改主意」时只改一行 return，
     * 而不是回来重写整个类。
     */
    actual fun adjust(base: Float, fraction: Float): Float {
        if (saved == null) saved = platform.UIKit.UIScreen.mainScreen.brightness
        val target = (base + fraction).coerceIn(0.01f, 1f)
        platform.UIKit.UIScreen.mainScreen.brightness = target.toDouble()
        return target
    }

    /** 写回进入前的系统亮度；未改过则什么都不做 */
    actual fun restore() {
        saved?.let { platform.UIKit.UIScreen.mainScreen.brightness = it }
        saved = null
    }
}

@Composable
actual fun rememberBrightnessController(): BrightnessController? = null

/**
 * 音量控制器：驱动系统音量滑块。
 *
 * 返回的是**读回值**而不是目标值：系统音量是整数量化后再读回，直接返回目标值会让
 * 浮层显示与真实音量不一致。
 */
@OptIn(ExperimentalForeignApi::class)
actual class VolumeController internal constructor() {

    private val slider: UISlider? = findSystemVolumeSlider()

    actual fun currentFraction(): Float = slider?.value ?: 0.5f

    actual fun adjust(base: Float, fraction: Float): Float {
        val s = slider ?: return (base + fraction).coerceIn(0f, 1f)
        s.setValue((base + fraction).coerceIn(0f, 1f), animated = false)
        return s.value
    }
}

@Composable
actual fun rememberVolumeController(): VolumeController? = remember { VolumeController() }

/**
 * 方向锁：外壳（Swift `OrientationHostingController`）与 `:ui` 之间的唯一共享状态。
 *
 * 放在 `object` 里而不是回调注入：外壳与 `:ui` 分属两个编译单元，用回调注入需要
 * 额外的跨语言接口（`Any?` 转型 + 生命周期管理），而这只是一个方向掩码，收益不抵成本。
 *
 * `mask` 初值 `UIInterfaceOrientationMaskPortrait` 与 `Info.plist` 的
 * `UISupportedInterfaceOrientations`（竖 + 左右横）同域：首帧必定竖屏。
 */
@OptIn(ExperimentalForeignApi::class)
object IosOrientationLock {

    /** 当前允许的方向码，外壳的 `supportedInterfaceOrientations` 读它 */
    var mask: UIInterfaceOrientationMask = UIInterfaceOrientationMaskPortrait
        private set

    fun request(newMask: UIInterfaceOrientationMask) {
        mask = newMask
        applyToActiveScene(newMask)
    }

    /**
     * 请求系统按新掩码转屏。
     *
     * 两条路都用上，因为单靠任一条都不够：
     * 1. `setNeedsUpdateOfSupportedInterfaceOrientations()` 让 UIKit 重新问外壳 VC
     *    要掩码（外壳读 [IosOrientationLock.mask]）；
     * 2. `UIWindowScene.requestGeometryUpdateWithPreferences` 才是真正发起转屏动效的调用。
     *
     * 方法必须在**正在显示的** view controller 上调用，`:ui` 拿不到它，故沿
     * `keyWindow.rootViewController` 向下找 `presentedViewController` 的最顶层。
     * 拿不到窗口/VC 时静默返回：转屏失败不应当崩掉播放（契约：actual 永不抛）。
     */
    private fun applyToActiveScene(newMask: UIInterfaceOrientationMask) {
        val scene = activeScene() ?: return
        // 注意：iOS 16 的 `setNeedsUpdateOfSupportedInterfaceOrientations()` 在
        //HONEOS_DEPLOYMENT_TARGET = 15.0 下不可用，K/N 平台 klib 也未导出；
        // `attemptRotationToDeviceOrientation` 同样未导出。故**只**走下面这条
        // geometry update。若真机出现「掩码已改但不转屏」，先查这里（docs/11 M-19）。
        // 2) 发起真正的转屏动效。
        scene.requestGeometryUpdateWithPreferences(
            UIWindowSceneGeometryPreferencesIOS(newMask),
        ) { _ -> Unit }
    }

    /** 正在显示的最顶层 VC。当前未用于掩码刷新（见上），保留供真机排障时挂断点。 */
    internal fun activeTopViewController(): UIViewController? {
        var vc = activeWindow()?.rootViewController ?: return null
        while (true) {
            vc = vc.presentedViewController ?: return vc
        }
    }

    private fun activeScene(): UIWindowScene? =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }

    private fun activeWindow(): UIWindow? {
        val sceneWindow = activeScene()?.windows
            ?.filterIsInstance<UIWindow>()
            ?.firstOrNull { it.isKeyWindow() }
        if (sceneWindow != null) return sceneWindow
        return UIApplication.sharedApplication.windows
            .filterIsInstance<UIWindow>()
            .firstOrNull { it.isKeyWindow() }
    }
}

/**
 * 从 `MPVolumeView` 里掏出系统音量 `UISlider`。
 *
 * 这是一个「广为人知但有代价」的做法：`MPVolumeView` 的子视图结构不是公开契约，
 * 但自 iOS 6 起稳定（其第一个 `UISlider` 即系统音量滑块）。找不到就返回 null，
 * 调用方退化为「读 0.5、不真正改音量」，**不抛异常**。
 *
 * 为什么必须挂进视图树：不在视图层级里的 `MPVolumeView` 不会实例化内部滑块。
 */
@OptIn(ExperimentalForeignApi::class)
private fun findSystemVolumeSlider(): UISlider? {
    val window = UIApplication.sharedApplication.windows
        .filterIsInstance<UIWindow>()
        .firstOrNull { it.isKeyWindow() } ?: return null
    val container = UIView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)).apply {
        alpha = 0.01
    }
    val volumeView = MPVolumeView(frame = container.bounds)
    container.addSubview(volumeView)
    window.addSubview(container)
    return volumeView.subviews.filterIsInstance<UISlider>().firstOrNull()
}

/**
 * 是否横屏。
 *
 * 读 `UIWindowScene.interfaceOrientation` 而不是比较 bounds 宽高：iPad 分屏时
 * 窗口可能是横的而设备是竖的，比较宽高会给出与用户感知相反的答案。
 */
@Composable
actual fun rememberIsLandscape(): Boolean {
    val scene = remember {
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull()
    }
    val orientation: UIInterfaceOrientation? = scene?.interfaceOrientation
    return when (orientation) {
        UIInterfaceOrientationLandscapeLeft, UIInterfaceOrientationLandscapeRight -> true
        else -> false
    }
}

/**
 * 配置音频会话，让静音键不打断播放。
 *
 * 与 Android 侧 `handleAudioFocus = true` 对应：iOS 上等价的语义是
 * `AVAudioSessionCategoryPlayback`（不受静音开关影响）。在 Composable 内容设置前
 * 调用一次，见 `MainViewController.kt` 的装配顺序。
 */
@OptIn(ExperimentalForeignApi::class)
internal fun configureAudioSession() {
    val session = AVAudioSession.sharedInstance()
    session.setCategory(AVAudioSessionCategoryPlayback, error = null)
    session.setActive(true, error = null)
}
