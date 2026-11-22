package com.dengying.player.ui.platform

import androidx.compose.runtime.Composable

/**
 * 拦截系统返回（KMP P4-5）。
 *
 * ## 为什么需要这个门面
 *
 * `androidx.activity.compose.BackHandler` 是 Android 独占的：它把返回事件
 * 接到 `OnBackPressedDispatcher` 上，那是 `ComponentActivity` 的机制。
 * iOS 与桌面没有这个 dispatcher，故 `:ui/commonMain` 不能直接用它 ——
 * 否则 `:ui:compileKotlinJvm` 立刻报 `Unresolved reference`。
 *
 * ## 契约
 *
 *  1. **只在 [enabled] 为 true 时拦截**；false 时返回事件继续往下传（交给
 *     NavHost 的默认 `popBackStack()`）。
 *  2. [enabled] **必须是可观察状态**才会在变化时重新注册。传普通字段时
 *     Android 侧的 `BackHandler` 不会重新注册回调 —— 这个坑在
 *     `FullscreenController.kt` 的注释里已记录过一次。
 *  3. actual 必须与平台生命周期对齐：离开组合时自动注销，不留悬挂回调。
 *  4. 桌面端可以是空实现（没有系统返回键），但**不要报错** —— 报错会让
 *     桌面侧的组合预览直接崩掉，掩盖真正要验证的编译问题。
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * 系统返回键是否需要**应用自己**接（KMP P4-5）。
 *
 * Android 的 `NavHost` 把返回接到 `OnBackPressedDispatcher` 上，根页返回交给
 * Activity 收尾，应用不需要额外注册；鸿蒙没有这个 dispatcher，系统返回键默认
 * 直接让 UIAbility 退到后台，所以导航层必须自己注册一个 `popBackStack()`。
 *
 * 只在为 true 的平台上让 `NavGraph` 注册那条路由级处理器，Android 保持原样
 * （否则会和 `NavHost` 自己的处理器抢，根页返回也不再退出应用）。
 */
expect val systemBackNeedsHost: Boolean
