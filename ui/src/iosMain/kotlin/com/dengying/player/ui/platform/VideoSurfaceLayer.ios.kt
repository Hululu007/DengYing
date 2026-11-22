package com.dengying.player.ui.platform

/**
 * iOS 侧视频层序：[VideoSurface] 走 CMP 的 `UIKitView`，原生视图被塞进
 * Compose 布局树内部（见 `VideoSurface.ios.kt`），画面本身就在 Compose 树里，
 * 与 Android 的 `AndroidView(PlayerView)` 同构，不存在「Compose 下方有独立原生层」。
 *
 * 因此不需要鸿蒙那种「根 Box 透明让位」：让位反而会露出
 * `ComposeUIViewController` 的宿主底色，在起播/缓冲/宽屏片源下闪白。
 * 调用点只负责组合条件，见 [shouldRevealVideoSurface]。
 */
actual val videoSurfaceIsBelowCompose: Boolean = false
