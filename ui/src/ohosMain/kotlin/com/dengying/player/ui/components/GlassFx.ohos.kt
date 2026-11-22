package com.dengying.player.ui.components

import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode

/** OHOS 实现：与 JVM 同一条退化路径——Compose 通用 BlurEffect，纯模糊，无 AGSL 折射。 */
actual fun glassRenderEffect(
    blurPx: Float,
    refractPx: Float,
    widthPx: Float,
    heightPx: Float,
): RenderEffect? = BlurEffect(blurPx, blurPx, TileMode.Decal)
