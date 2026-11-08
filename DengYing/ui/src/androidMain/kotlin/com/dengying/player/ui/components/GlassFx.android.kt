package com.dengying.player.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Android 实现：API 33+ 用 AGSL 折射+提饱和链在模糊后；31/32 退化纯模糊。
 *
 * AGSL 细节：
 * - 采样坐标按"边缘法线外推"做透镜弯曲——离边缘越近弯得越多（smoothstep
 *   0.4→1.0 起斜），中心区不动，观感是玻璃边缘把背后内容"折射"了一道。
 * - 亮度→灰度→按饱和度回拉，即 vibrancy：底色透过玻璃时更鲜艳而不是发灰。
 */
private const val REFRACT_SATURATE_SHADER = """
uniform shader content;
uniform float2 uSize;
uniform float uRefract;
uniform float uSat;
half4 main(float2 xy) {
    float2 c = uSize * 0.5;
    float2 d = xy / c - 1.0;
    float r = length(d);
    float2 dir = d / max(r, 0.0001);
    float fall = smoothstep(0.4, 1.0, r) * uRefract;
    float2 uv = clamp(xy + dir * fall, float2(0.0), uSize - float2(1.0));
    half4 col = content.sample(uv);
    float g = dot(col.rgb, half3(0.299, 0.587, 0.114));
    col.rgb = half3(g) + (col.rgb - half3(g)) * half3(uSat);
    return col;
}
"""

actual fun glassRenderEffect(
    blurPx: Float,
    refractPx: Float,
    widthPx: Float,
    heightPx: Float,
): RenderEffect? {
    // 33 = TIRAMISI（AGSL/RenderShader 起始版本；用字面量避免 compileSdk 解析差异）
    if (Build.VERSION.SDK_INT < 33) {
        return androidx.compose.ui.graphics.BlurEffect(blurPx, blurPx, androidx.compose.ui.graphics.TileMode.Decal)
    }
    return runCatching {
        val shader = RuntimeShader(REFRACT_SATURATE_SHADER)
        shader.setFloatUniform("uSize", widthPx, heightPx)
        shader.setFloatUniform("uRefract", refractPx)
        shader.setFloatUniform("uSat", GLASS_SATURATION)
        val shaderFx = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        val blurFx = android.graphics.RenderEffect.createBlurEffect(
            blurPx, blurPx, android.graphics.Shader.TileMode.DECAL,
        )
        android.graphics.RenderEffect.createChainEffect(shaderFx, blurFx).asComposeRenderEffect()
    }.getOrNull()
}
