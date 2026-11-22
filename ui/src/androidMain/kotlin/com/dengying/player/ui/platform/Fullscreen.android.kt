package com.dengying.player.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt

/**
 * Android actual：`requestedOrientation` + `WindowInsetsController`。
 *
 * ## 这一版是照成熟做法重写的，此前的实现有根本性错误
 *
 * 参考实现：[fengdai/compose-media 的 FullscreenToggle.kt]
 * (https://github.com/fengdai/compose-media/blob/master/sample/src/main/java/com/github/fengdai/compose/media/sample/FullscreenToggle.kt)
 *
 * ### 错误一：自己维护"是否全屏"这个状态
 *
 * 初版用 `var isFullscreen: Boolean` 加一个"进入前方向快照"。问题：这个布尔与
 * 系统实际方向会**脱节** —— 旋转、Activity 重建、系统栏变化都会改变真实方向，
 * 而布尔不会跟着变，于是 UI 认为"还在全屏"而实际已经不在（或反之），
 * 恢复逻辑就失效。
 *
 * 成熟做法：**`requestedOrientation` 是唯一真相，UI 从
 * `LocalConfiguration.orientation` 反推当前是不是横屏**。这样任何异常都能自愈。
 *
 * ### 错误二：退全屏"恢复快照"而不是显式设竖屏
 *
 * 初版记下"进入前的方向"并在退出时恢复。但若用户进入前本来就是横屏
 * （平板/支架），"恢复"等于保持横屏 —— 用户看到的就是**锁死横屏**。
 *
 * 成熟做法：退出时显式设 `SCREEN_ORIENTATION_USER_PORTRAIT`。
 *
 * ### 错误三：`BackHandler(enabled = 普通字段)`
 *
 * `BackHandler` 的 `enabled` 需要是**可观察状态**才会在变化时重新注册。
 * 传普通 Boolean 字段，它永远停在初值 `false`，**返回键从未被接管**。
 *
 * 成熟做法：`OnBackPressedCallback` + `SideEffect { callback.isEnabled = isLandscape }`
 * —— `SideEffect` 每次成功重组都跑，把 enabled 同步到最新方向。
 *
 * ## 方向取值为什么用 `USER_LANDSCAPE` / `USER_PORTRAIT`
 *
 * 而非 `SENSOR_LANDSCAPE`：`SENSOR_*` 会跟随重力感应翻转（左右横屏来回切），
 * 用户躺着看时画面会跟着转。`USER_*` 尊重用户的系统自动旋转开关。
 */
@Composable
actual fun rememberFullscreenState(): FullscreenState {
    val context = LocalContext.current
    val activity = context.findActivity()
    val componentActivity = context.findComponentActivity()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 状态同步：每次成功重组都把方向与系统栏对齐到当前形态。
    // 这一步是"自愈"的关键 —— 不依赖任何一次点击是否成功。
    SideEffect {
        val act = activity ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (isLandscape) {
            // 全屏态：隐藏系统栏。方向由播放页的 enter() 设定。
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // 非全屏态：只显示系统栏。
            //
            // 【方向不在这里设】isLandscape 读自 LocalConfiguration，而配置的更新
            // 滞后于 requestedOrientation 的生效 —— 若这里在"已请求横屏但配置未变"
            // 的窗口期把方向顶回竖屏，全屏请求就被自己取消了（死锁）。
            // 竖屏收敛由各竖屏页面（详情页等）在自己组合时显式做。
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 返回键：横屏时先退全屏、不退出页面。
    val callback = remember {
        object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            }
        }
    }
    val dispatcher = componentActivity?.onBackPressedDispatcher
    DisposableEffect(dispatcher) {
        dispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
    SideEffect { callback.isEnabled = isLandscape }

    return remember(activity) { FullscreenState(activity) }
}

/** 竖屏页面收敛方向。见 expect 声明的理由。 */
@Composable
actual fun ConsumePortraitOrientation() {
    val activity = LocalContext.current.findActivity()
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    SideEffect {
        // 【RESUMED 才收敛】导航过渡期（如详情页 → 播放页的 220ms 退场动画）
        // 本页仍在组合、但已非 RESUMED（新页面持有焦点）——此时若照旧把方向
        // 顶回竖屏，会把全屏页刚设好的横屏覆盖掉，真机表现为"先横屏又变回
        // 竖屏"。只在自己是前台页（RESUMED）时收敛方向；返回本页时恢复
        // RESUMED，SideEffect 自然重新收敛竖屏。
        if (owner.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
    }
}

/**
 * Android actual：窗口方向。
 *
 * 对外只暴露两个动作，不暴露内部状态 —— **因为"是否全屏"不该由本类回答**，
 * 那是系统方向的事。
 */
actual class FullscreenState internal constructor(
    private val activity: Activity?,
) {
    actual fun enter(landscape: Boolean) {
        val act = activity ?: return
        act.requestedOrientation = if (landscape) {
            // 【SENSOR_ 而非 USER_】USER_LANDSCAPE 尊重系统自动旋转开关——
            // 国产机默认关旋转锁（vivo 实测）：请求先生效又被系统纠正回竖屏，
            // 观感"闪一下横屏变回竖屏"。播放器全屏业界通行是强制传感器横屏
            // （B 站/爱奇艺同款），不受用户旋转锁影响。
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            // 竖向视频：保持竖屏方向，只由 SideEffect 隐藏系统栏
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
    }

    actual fun exit() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    }
}

/** 从 Context 往上找 Activity（Compose 里拿到的常是 ContextWrapper） */
internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 同上，但要求拿到 `ComponentActivity`。
 *
 * `onBackPressedDispatcher` 只在这个子类上 —— `Activity` 基类没有。
 * 分开两个函数而不是让调用方强转：强转失败会抛 ClassCastException，
 * 而这里只需要"拿不到就退化为不接管返回键"。
 */
internal fun Context.findComponentActivity(): androidx.activity.ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is androidx.activity.ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Android actual：读 `LocalConfiguration.orientation`。
 *
 * 这是"自愈"的关键 —— 不自己维护布尔，任何异常（旋转、重建、系统栏变化）
 * 都能从系统配置反推回正确形态，不存在"锁死"。
 */
@Composable
actual fun rememberIsLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
