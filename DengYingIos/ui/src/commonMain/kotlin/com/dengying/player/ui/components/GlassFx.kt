package com.dengying.player.ui.components

import androidx.compose.ui.graphics.RenderEffect

/**
 * 玻璃渲染效果（v3 透射玻璃的取样层后处理）。
 *
 * 链路 = 模糊（半透明感的"体"）→ 折射+提饱和（"透射"：底色以更鲜艳的
 * 面目透过玻璃，边缘产生透镜式弯曲）。各端实现：
 * - Android API 33+：AGSL `RuntimeShader`（边缘折射 + 饱和度 1.6×）链在
 *   模糊之后——真·透射。
 * - Android API 31/32、iOS、JVM：仅模糊（degrade 路径，观感向 v2 靠拢）。
 *
 * 返回 null 表示调用方直接画原始采样层（不应发生，防御）。
 */
expect fun glassRenderEffect(
    blurPx: Float,
    refractPx: Float,
    widthPx: Float,
    heightPx: Float,
): RenderEffect?

/** 饱和度提升倍数：透射感的核心参数（iOS vibrancy ≈ 1.5-1.8） */
const val GLASS_SATURATION = 1.6f
