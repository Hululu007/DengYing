package com.dengying.player.ui.platform

/**
 * 鸿蒙：全屏 `XComponent(SURFACE)` 铺在 Compose 之下（ComposePage.ets），
 * 只有播放页根背景透明时画面才可见。
 */
actual val videoSurfaceIsBelowCompose: Boolean = true
