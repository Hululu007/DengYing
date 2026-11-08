package com.dengying.player.ui.components

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode

/** JVM（桌面预览）实现：Compose 通用 BlurEffect，纯模糊退化路径。 */
actual fun glassRenderEffect(
    blurPx: Float,
    refractPx: Float,
    widthPx: Float,
    heightPx: Float,
): RenderEffect? = BlurEffect(blurPx, blurPx, TileMode.Decal)
