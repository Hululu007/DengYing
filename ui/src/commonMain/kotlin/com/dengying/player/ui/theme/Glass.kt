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
    /**
     * 主按钮（登录 / 搜索）的填充。
     *
     * 与 [fill] 分开是因为主按钮叠在同样是玻璃的输入框上：两边都用 60% 白时，
     * 实测只差 1~2 个色阶，按钮轮廓完全消失。主按钮要近实底（浅色近白、深色亮一档），
     * 靠描边与投影跟输入框拉开，而不是靠染色。
     */
    val actionFill: Color,
    /** 主按钮上的文字色：与 [actionFill] 成对，保证对比度 */
    val actionText: Color,
    /** 悬浮投影颜色——配合 GlassSurface 的 elevation */
    val shadow: Color,
    /** chip/按钮 未选中填充 */
    val chipFill: Color,
    /** chip/按钮 选中填充（更亮更实，非墨色） */
    val chipSelectedFill: Color,
    /** Dock 选中项背后的滑动高光面板 */
    val dockHighlight: Color,
    /**
     * 静态玻璃面左上高光的峰值 alpha（`GlassSurface` 退化/静态分支用）。
     *
     * 【为什么必须是 token】v1.3.0 之前这里硬编码 `if (fill.luminance() < 0.5f) 0.10f else 0.34f`，
     * 判据是「fill 自身亮不亮」——但深色主题下 fill 是**低 alpha 白**
     * （`0x1CFFFFFF`，luminance≈0.11 但实际叠加后是深色），被判成暗色给 0.10f 高光，
     * 叠加在深色柔光底上仍然把卡片整体抬亮一档，副文本压不住。
     * 高光强度取决于**主题**，不取决于 fill 的算术亮度，所以提升为 token。
     */
    val sheenAlpha: Float,
)

val LightGlass = GlassScheme(
    // v5 磨砂（对齐 vivo OriginOS 组件的观感）：v4 把静态面压到 42% 白追求"透"，
    // 结果用户看到的是"几乎透明的塑料片"——底色一冲就没了玻璃的体感。
    // 磨砂玻璃的正解是「白面够实 + 模糊够浅」：60% 白保证有面，模糊 12dp 保证
    // 背后内容还认得出形状。太透（<45%）像塑料膜，太实（>75%）又变回白卡。
    fill = Color(0x99FFFFFF),
    fillStrong = Color(0xC7FFFFFF),
    darkFill = Color(0x73000000),
    // 采样路径的补色 40% 白。
    // 【取值理由】24% 时海报细节在玻璃里还能读出来（用户：不要做透明）；
    // 55% 以上背后只剩一片白雾（不要很模糊）。40% 是「能看出这里有一张图、
    // 但读不清图上的字」的分界，正对 vivo 系统组件的磨砂厚度。
    liquidTint = Color(0x66FFFFFF),
    // 边线 14% 黑在近白底上只差 5 个色阶，真机上看不出边界。
    // 提到 28% 后，输入框与卡片在 rgb(246) 的底上有一条可辨的灰线。
    border = Color(0x6E0C0C09),
    // 主按钮近白实底：输入框是 60% 白玻璃，按钮必须再实一档才有轮廓
    actionFill = Color(0xF2FFFFFF),
    actionText = Color(0xFF0C0C09),
    shadow = Color(0x1A0C0C09),
    chipFill = Color(0x5CFFFFFF),
    chipSelectedFill = Color(0xCCFFFFFF),
    dockHighlight = Color(0xD9FFFFFF),
    sheenAlpha = 0.30f,
)

val DarkGlass = GlassScheme(
    // 深色同理：白面 18%（v4 是 10%）——纯靠模糊的暗玻璃在深色底上会糊成一团，
    // 需要一层白霜把边缘与层次"提"出来。
    // 【v1.3.0 实测回调】18% 在深色底上把卡片抬到 rgb(140,137,154)，
    // 而副文本 textSecondary 是 rgb(155,155,147)——两者几乎同亮度，
    // 「登录两个BT 解锁 4K 高清线路」直接糊在卡片里。压到 11% 后卡片落回
    // rgb(≈96,92,108)，与副文本拉开一档，同时仍比页面底亮，玻璃层次不丢。
    fill = Color(0x1CFFFFFF),
    fillStrong = Color(0xC7FFFFFF),
    darkFill = Color(0x73000000),
    liquidTint = Color(0x1CFFFFFF),
    border = Color(0x3DFFFFFF),
    // 主按钮亮一档：深色输入框只有 11% 白，按钮用 24% 白才看得见边界
    actionFill = Color(0x3DFFFFFF),
    actionText = Color(0xFFF2F2EE),
    shadow = Color(0x66000000),
    chipFill = Color(0x2EFFFFFF),
    chipSelectedFill = Color(0x52FFFFFF),
    dockHighlight = Color(0x3DFFFFFF),
    sheenAlpha = 0.06f,
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
    val ActionFill: Color @Composable get() = LocalGlassScheme.current.actionFill
    val ActionText: Color @Composable get() = LocalGlassScheme.current.actionText
    val Shadow: Color @Composable get() = LocalGlassScheme.current.shadow
    val ChipFill: Color @Composable get() = LocalGlassScheme.current.chipFill
    val ChipSelectedFill: Color @Composable get() = LocalGlassScheme.current.chipSelectedFill
    val DockHighlight: Color @Composable get() = LocalGlassScheme.current.dockHighlight
    val SheenAlpha: Float @Composable get() = LocalGlassScheme.current.sheenAlpha
}
