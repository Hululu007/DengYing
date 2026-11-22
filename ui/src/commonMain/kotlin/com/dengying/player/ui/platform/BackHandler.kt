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
