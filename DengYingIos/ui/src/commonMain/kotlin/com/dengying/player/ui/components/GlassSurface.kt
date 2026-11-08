package com.dengying.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.theme.Glass
import com.dengying.player.ui.theme.Palette

/**
 * 玻璃面容器——本项目"悬浮玻璃 UI"的基础件（v3：透射玻璃）。
 *
 * 【liquid=true：透射】背后内容实时透过玻璃，且不是灰蒙蒙的高斯白：
 * 模糊半径降到 14dp，采样层再过一遍渲染链——
 *   Android API 33+：AGSL 边缘折射 + 饱和度 1.6×（vibrancy），
 *   低版本/iOS/JVM：纯模糊退化。
 * 白 tint 只剩 18%（v2 是 36%）——底色（海报、封面）以更鲜艳的面目
 * 透进玻璃，这就是"映射底部颜色"的透射效果。原理见 [GlassBackdrop.kt]。
 * 只给导航件用（顶栏/Dock/播放器控制层）——成本 ∝ 面积，内容卡不采样。
 *
 * 【liquid=false：静态玻璃面】海报卡等小面积件用「半透明填充 + 细边」，
 * 深浅色取值来自 [Glass] token，随主题自动切换。
 *
 * 【无 backdrop 的退化】`LocalGlassBackdrop` 为 null（预览/测试/未包采样根）
 * 或坐标未挂载时自动走静态路径，不崩。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = Glass.Fill,
    elevation: Dp = 10.dp,
    liquid: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    // Glass getter 读 CompositionLocal（@Composable）——drawWithCache/Modifier
    // 链里不能读，先在组合期取好局部值
    val liquidTint = Glass.LiquidTint
    val borderC = Glass.Border
    val stdFill = Glass.Fill
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sampleLayer = rememberGraphicsLayer()
    Box(
        modifier = modifier
            .onGloballyPositioned { coords = it }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Glass.Shadow,
                spotColor = Glass.Shadow,
            )
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val src = backdrop?.layer
                val self = coords
                val origin = backdrop?.origin
                val canSample = liquid && src != null && self != null && origin != null &&
                    self.isAttached && origin.isAttached
                val edge = 1.dp.toPx()
                if (canSample) {
                    // 反向平移量：采样图层以根节点为原点，玻璃件要"透过自己看世界"
                    val dx = self.positionInRoot().x - origin.positionInRoot().x
                    val dy = self.positionInRoot().y - origin.positionInRoot().y
                    val blurPx = GLASS_BLUR_RADIUS_DP.dp.toPx()
                    val refractPx = GLASS_REFRACT_DP.dp.toPx()
                    // 液态 tint：透射感靠饱和度与低模糊，白 tint 只补可读性；
                    // 播放器等暗玻璃（fill != Glass.Fill）按调用方原样叠加
                    val tint = if (fill == stdFill) liquidTint else fill
                    val specular = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.06f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawWithContent {
                        // 1) 采样：全场景图层平移到本件坐标系，录进自身尺寸的图层
                        sampleLayer.record {
                            translate(-dx, -dy) { drawLayer(src) }
                        }
                        // 2) 模糊 → 折射+提饱和 链式后处理上屏
                        sampleLayer.renderEffect = glassRenderEffect(
                            blurPx, refractPx, size.width, size.height,
                        )
                        drawLayer(sampleLayer)
                        // 3) tint 补可读性（v3 大幅调低，透射为主）
                        drawOutline(outline, color = tint, style = Fill)
                        // 4) 高光描边：左上亮右下暗，玻璃"厚度"的镜面反光
                        drawOutline(outline, brush = specular, style = Stroke(edge))
                        // 5) 内容最后画（浮在玻璃面上）
                        drawContent()
                    }
                } else {
                    onDrawWithContent {
                        drawOutline(outline, color = fill, style = Fill)
                        drawOutline(outline, color = borderC, style = Stroke(edge))
                        drawContent()
                    }
                }
            },
        content = content,
    )
}

/** 磨砂半径：v3 从 22dp 降到 14dp——透射要求底色可辨，再大就"雾"了 */
private const val GLASS_BLUR_RADIUS_DP = 14

/** 边缘折射深度：透镜弯曲的采样外推距离（≈3dp，观感明显但不夸张） */
private const val GLASS_REFRACT_DP = 3

/**
 * 玻璃胶囊：分类标签 / 频道 tab / 联想词与历史 chips 共用。
 *
 * 【v3 选中态】废除墨色实底（用户明确否掉黑胶囊）：
 * 选中 = 更亮更实的玻璃面 + 半粗主文字；未选中 = 透玻璃面 + 主文字。
 * [quiet]=true 时未选中不带底（频道 tab 行用，更轻）。
 */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean = false,
    quiet: Boolean = false,
    /** 紧凑档（28dp）：悬浮玻璃控件内部用，比常规 32dp 小一号 */
    compact: Boolean = false,
    onClick: () -> Unit = {},
) {
    val selFill = Glass.ChipSelectedFill
    val idleFill = Glass.ChipFill
    Box(
        modifier = Modifier
            .height(if (compact) 28.dp else 32.dp)
            .then(
                when {
                    selected -> Modifier
                        .shadow(6.dp, ChipShape, ambientColor = Glass.Shadow, spotColor = Glass.Shadow)
                        .clip(ChipShape)
                        .background(selFill, ChipShape)
                    quiet -> Modifier
                    else -> Modifier
                        .shadow(4.dp, ChipShape, ambientColor = Glass.Shadow, spotColor = Glass.Shadow)
                        .clip(ChipShape)
                        .background(idleFill, ChipShape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Palette.TextPrimary else
                if (quiet) Palette.TextSecondary else Palette.TextPrimary,
        )
    }
}

private val ChipShape = RoundedCornerShape(16.dp)
