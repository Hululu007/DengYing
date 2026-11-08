package com.dengying.player.ui.platform

import androidx.compose.runtime.Composable

/**
 * iOS actual：空实现（KMP P5-1）。
 *
 * iOS 没有系统返回键：返回动作由导航栏按钮或侧滑手势触发，且都在
 * `UINavigationController` / `NavHost` 的层级里，不在 Compose 组合内。
 * 故与桌面 actual 同形 —— **空实现，不报错**（报错会让 iOS 侧任何一次
 * 组合预览直接崩溃，掩盖真正要验证的编译问题）。
 *
 * TODO(Mac)：若将来在 iOS 壳里接管侧滑返回，需在 Swift 侧把
 * `interactivePopGestureRecognizer` 的状态回传给 Compose，
 * 再在此处注册（需真机验证手势与全屏共存时的优先级）。
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // 空实现：不注册任何回调。
}
