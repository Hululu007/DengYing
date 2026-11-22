package com.dengying.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 * 【v4 透射的前提】玻璃能不能"透"，取决于背后有没有东西可透。v3 之前页面
 * 底层是纯不透明 `Palette.AppBg`，于是玻璃永远只能透出一片死色（用户实测
 * 否掉）。v4 起页面根统一改用 [glassPageBackdrop]（底色 + 四团柔光），
 * 玻璃采样到的才是真实色相与明暗。
 *
 * 【liquid=true：透射】背后内容实时透过玻璃：
 * 模糊半径 14dp，采样层再过一遍渲染链——
 *   Android API 33+：AGSL 边缘折射 + 饱和度 1.6×（vibrancy），
 *   低版本/iOS/JVM：纯模糊退化。
 * 白 tint 只剩 9%（v3 是 18%）——底色以更鲜艳的面目透进玻璃。原理见 [GlassBackdrop.kt]。
 * 给导航件与交互件用（顶栏/Dock/返回环/输入框/主按钮）。
 *
 * 【liquid=false：静态玻璃面】海报卡等成批出现的小面积件用「半透明填充 +
 * 左上高光 + 细边」，不采样（成本 ∝ 面积 × 数量，几十张卡会拖穿滚动帧率）；
 * 因为填充已降到 42% 白，页面柔光底照样透上来，观感仍是玻璃。
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
    // 【自引用保险】处在被采样内容层里时禁止采样——采样图层包含本件自身，
    // drawLayer 会层层嵌套导致 RenderThread 栈溢出。详见 LocalGlassInSampledContent。
    val inSampledContent = LocalGlassInSampledContent.current
    // Glass getter 读 CompositionLocal（@Composable）——drawWithCache/Modifier
    // 链里不能读，先在组合期取好局部值
    val liquidTint = Glass.LiquidTint
    val borderC = Glass.Border
    val stdFill = Glass.Fill
    val sheenAlpha = Glass.SheenAlpha
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
                // 【只在绘制期读采样源，不在缓存期读】drawWithCache 的块只在
                // size/state 变化时重跑，若在这里抓 `backdrop?.layer`，页面重进
                // 时图层对象已换新，缓存里仍是旧图层 → 玻璃要么全黑要么停在上一页。
                // 采样源由页面的 `glassPageBackdrop()` 在**每帧绘制期**写入，
                // 所以这里每次绘制都重新取。
                val outline = shape.createOutline(size, layoutDirection, this)
                val edge = 1.dp.toPx()
                val blurPx = GLASS_BLUR_RADIUS_DP.dp.toPx()
                // 【折射深度按控件尺寸归一】v4 用固定 3dp：44dp 的顶栏/搜索框半高才
                // 22dp，3dp 占了 13.6%，整个面都在弯 → 观感是「一块会扭的塑料」；
                // 56dp 的 Dock 占 10.7%，同样的参数看着却是正常玻璃。同一套 shader
                // 在不同高度的控件上表现不一致，根因就是这个绝对值。
                // 改成「短边 5%」并钳在 1.5~3dp：矮控件弯得浅、高控件也不会弯过头。
                val refractPx = (minOf(size.width, size.height) * GLASS_REFRACT_RATIO)
                    .coerceIn(1.5f * density, GLASS_REFRACT_MAX_DP * density)
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
                // 静态玻璃面的左上高光（内容卡/小件用）。
                // 【为什么不用 fill.luminance() 判断】深色主题的 fill 是低 alpha 白，
                // 算术亮度≈0.11 被判成「暗色」→ 走 0.10f 分支，但叠在深色柔光底上
                // 仍把卡片抬亮一档，副文本压不住。高光强度是**主题属性**，取 token。
                val sheen = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = sheenAlpha),
                        Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height * 1.4f),
                )
                onDrawWithContent {
                    val src = backdrop?.layer
                    val self = coords
                    val origin = backdrop?.origin
                    val canSample = liquid && !inSampledContent && src != null && self != null &&
                        origin != null && self.isAttached && origin.isAttached
                    if (canSample) {
                        // 反向平移量：采样图层以根节点为原点，玻璃件要"透过自己看世界"
                        val dx = self.positionInRoot().x - origin.positionInRoot().x
                        val dy = self.positionInRoot().y - origin.positionInRoot().y
                        // 1) 采样：把页面图层平移到本件坐标系，录进自身尺寸的图层
                        sampleLayer.record {
                            translate(-dx, -dy) { drawLayer(src) }
                        }
                        // 2) 模糊 → 折射 + 提饱和 链式后处理上屏
                        sampleLayer.renderEffect = glassRenderEffect(
                            blurPx, refractPx, size.width, size.height,
                        )
                        drawLayer(sampleLayer)
                        // 3) tint 补可读性（v3 大幅调低，透射为主）
                        drawOutline(outline, color = tint, style = Fill)
                        // 4) 实色描边：高光描边是 55% 白，压在浅色键盘/浅底上完全消失。
                        //    先画一圈主题边线，保证任何底色下都能看出轮廓。
                        drawOutline(outline, color = borderC, style = Stroke(edge))
                        // 5) 高光描边：左上亮右下暗，玻璃"厚度"的镜面反光
                        drawOutline(outline, brush = specular, style = Stroke(edge))
                        // 5) 内容最后画（浮在玻璃面上）
                        drawContent()
                    } else {
                        // 静态玻璃面（内容卡/小件）：填充已是半透明，页面柔光底会透上来；
                        // 再补一道左上高光，避免"半透明色块"缺少玻璃厚度。
                        drawOutline(outline, color = fill, style = Fill)
                        drawOutline(outline, brush = sheen, style = Fill)
                        drawOutline(outline, color = borderC, style = Stroke(edge))
                        drawContent()
                    }
                }
            },
        content = content,
    )
}

/**
 * 磨砂半径：22dp → 14dp → 9dp → 12dp → 16dp。
 *
 * 9dp 是 v4「透射」口径——能看清海报边缘和文字块，但配上 42% 白的面，
 * 整体像一层塑料膜（用户实测否决）。磨砂玻璃要的是「白面够实 + 模糊适中」。
 * 12dp 时海报上的文字仍可辨认，偏薄；16dp 下背后只剩色块与明暗走向，
 * 细节被吃掉但不至于糊成一片均匀色雾（>20dp 才开始糊）。
 */
private const val GLASS_BLUR_RADIUS_DP = 16

/**
 * 边缘折射深度：取控件短边的比例，而不是固定 dp。
 *
 * 固定值会让同一套 shader 在 44dp 顶栏与 56dp Dock 上表现不一致（顶栏整个面
 * 都在弯，像塑料；Dock 只在边缘轻微弯，像玻璃）。
 */
private const val GLASS_REFRACT_RATIO = 0.05f

/** 折射深度上限：高控件（播放器控制条等）不要把边缘推得太夸张。 */
private const val GLASS_REFRACT_MAX_DP = 3

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
                        .border(1.dp, Glass.Border, ChipShape)
                    quiet -> Modifier
                    else -> Modifier
                        .shadow(4.dp, ChipShape, ambientColor = Glass.Shadow, spotColor = Glass.Shadow)
                        .clip(ChipShape)
                        .background(idleFill, ChipShape)
                        .border(1.dp, Glass.Border, ChipShape)
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
