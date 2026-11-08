package com.dengying.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 玻璃层 token —— 悬浮玻璃控件（v3「透射玻璃」）的取值来源。
 *
 * 【与 [Palette] 的关系】Palette 管页面底色/文字/分隔线；[Glass] 只服务
 * 「浮在滚动内容之上的控件」：顶栏、Dock、分类标签、播放器控制条、卡片面。
 * 两套语义并存不合并。
 *
 * 【v3 口径（对齐 Kyant0/AndroidLiquidGlass 与 iOS 26 原型的实测参数）】
 * - 玻璃要「透」：液态 tint 降到白 18%（v2 是 36%）——颜色透出来靠
 *   模糊半径减半（22→14dp）+ 采样层饱和度提升（1.6×，vibrancy）+
 *   边缘折射（AGSL，API 33+），不再靠白 tint 撑体感。
 * - **选中态一律不用墨色实底**（v2 的黑胶囊被否）：选中 = 更亮更实的
 *   玻璃面 + 半粗文字；未选中 = 更透的玻璃面 + 次要文字。深色同理，
 *   用白/黑透明度反向表达，见 Light/Dark 两份 [GlassScheme]。
 * - 深色跟随系统：v3 起 [Glass] 全部 token 主题感知（getter 模式，
 *   调用点零改动），与 [Palette] 同机制。
 */
data class GlassScheme(
    /** 玻璃填充——静态卡片/退化路径的常态填充 */
    val fill: Color,
    /** 玻璃填充加强——播放器等深色媒体上方保证可读 */
    val fillStrong: Color,
    /** 玻璃填充·深色——播放器控制层暗玻璃（深浅色同值：视频上永远是暗玻璃） */
    val darkFill: Color,
    /** 液态 tint——liquid 采样路径的补色，v3 大幅调低让底色透进来 */
    val liquidTint: Color,
    /** 玻璃边线 */
    val border: Color,
    /** 悬浮投影颜色——配合 GlassSurface 的 elevation */
    val shadow: Color,
    /** chip/按钮 未选中填充 */
    val chipFill: Color,
    /** chip/按钮 选中填充（更亮更实，非墨色） */
    val chipSelectedFill: Color,
    /** Dock 选中项背后的滑动高光面板 */
    val dockHighlight: Color,
)

val LightGlass = GlassScheme(
    fill = Color(0xD9FFFFFF),
    fillStrong = Color(0xF0FFFFFF),
    darkFill = Color(0x73000000),
    liquidTint = Color(0x2EFFFFFF),
    border = Color(0x140C0C09),
    shadow = Color(0x1A0C0C09),
    chipFill = Color(0x99FFFFFF),
    chipSelectedFill = Color(0xF2FFFFFF),
    dockHighlight = Color(0xE6FFFFFF),
)

val DarkGlass = GlassScheme(
    fill = Color(0x1FFFFFFF),
    fillStrong = Color(0xF0FFFFFF),
    darkFill = Color(0x73000000),
    liquidTint = Color(0x1AFFFFFF),
    border = Color(0x26FFFFFF),
    shadow = Color(0x66000000),
    chipFill = Color(0x14FFFFFF),
    chipSelectedFill = Color(0x30FFFFFF),
    dockHighlight = Color(0x26FFFFFF),
)

/** 当前主题的玻璃方案，由 [com.dengying.player.ui.theme.DengYingTheme] 提供。 */
val LocalGlassScheme = staticCompositionLocalOf { LightGlass }

/** 兼容层：属性名与 v2 完全一致，调用点零改动。 */
object Glass {
    val Fill: Color @Composable get() = LocalGlassScheme.current.fill
    val FillStrong: Color @Composable get() = LocalGlassScheme.current.fillStrong
    val DarkFill: Color @Composable get() = LocalGlassScheme.current.darkFill
    val LiquidTint: Color @Composable get() = LocalGlassScheme.current.liquidTint
    val Border: Color @Composable get() = LocalGlassScheme.current.border
    val Shadow: Color @Composable get() = LocalGlassScheme.current.shadow
    val ChipFill: Color @Composable get() = LocalGlassScheme.current.chipFill
    val ChipSelectedFill: Color @Composable get() = LocalGlassScheme.current.chipSelectedFill
    val DockHighlight: Color @Composable get() = LocalGlassScheme.current.dockHighlight
}
