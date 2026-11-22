package com.dengying.player.ui.platform

import androidx.compose.runtime.Composable

/**
 * Android actual：直接委派给 `androidx.activity.compose.BackHandler`。
 *
 * 不做任何包装逻辑 —— 迁移前 `PlayerScreen` 用的就是它，语义逐条相同：
 * 内部走 `OnBackPressedDispatcher`，`enabled` 变化时会重新注册，
 * 离开组合时自动注销。
 *
 * `:ui` 的 androidMain 已有 `libs.androidx.activity.compose` 依赖
 * （`VideoSurface` 的 actual 也要它）。
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
