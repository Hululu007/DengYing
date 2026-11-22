package com.dengying.player.ui.components

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode

/** iOS 实现：Compose 通用 BlurEffect（折射/提饱和待 Metal shader，先退化纯模糊）。 */
actual fun glassRenderEffect(
    blurPx: Float,
    refractPx: Float,
    widthPx: Float,
    heightPx: Float,
): RenderEffect? = BlurEffect(blurPx, blurPx, TileMode.Decal)
