package com.dengying.player.ui.platform

/** Android（PlayerView）与桌面/JVM 都在 Compose 树内出图，没有下层视频可透出。 */
actual val videoSurfaceIsBelowCompose: Boolean = false
