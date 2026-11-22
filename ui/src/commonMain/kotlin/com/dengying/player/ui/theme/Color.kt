package com.dengying.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 配色 —— 基础值来自鸿蒙工程的 `entry/src/main/resources/base/element/color.json`。
 *
 * 【v3 主题化】原先是单浅色 `object Palette`（硬编码 val）。液态玻璃 v3 起支持
 * 深浅色跟随系统：改为 `PaletteScheme` 数据类 + 浅/深两份实例，通过
 * `LocalPalette` 注入；`object Palette` 的**属性名原样保留**、改为 getter
 * 读取当前方案——全部调用点（`Palette.X`）零改动即获得主题感知。
 *
 * 深色值不是逐条"翻译"浅色，而是按同一套语义重新取值：
 * 底色近黑带暖、卡片比底色亮一档、文字反白、分隔线微亮。
 */
data class PaletteScheme(
    /** 品牌主色（浅色=哑光黑；深色=暖白，反转用法自动成立） */
    val brand: Color,
    /** 品牌副色 */
    val brandSecondary: Color,
    /** 页面底色 */
    val appBg: Color,
    /** 卡片底色 */
    val cardBg: Color,
    /** 搜索框/胶囊底色 */
    val searchBg: Color,
    /** 主文字 */
    val textPrimary: Color,
    /** 次要文字 */
    val textSecondary: Color,
    /** 分隔线 */
    val divider: Color,
    /** 警示红 */
    val danger: Color,
    /** 图片角标底 */
    val badgeScrim: Color,
)

val LightPalette = PaletteScheme(
    brand = Color(0xFF0C0C09),
    brandSecondary = Color(0xFF312C85),
    appBg = Color(0xFFF4F4F1),
    cardBg = Color(0xFFFFFFFF),
    searchBg = Color(0xFFEAEAE5),
    textPrimary = Color(0xFF0C0C09),
    textSecondary = Color(0xFF6E6E68),
    divider = Color(0xFFE4E4DE),
    danger = Color(0xFFDC2626),
    badgeScrim = Color(0xB3000000),
)

val DarkPalette = PaletteScheme(
    brand = Color(0xFFEDEDE8),
    brandSecondary = Color(0xFF8F8AD6),
    appBg = Color(0xFF111114),
    cardBg = Color(0xFF1C1C20),
    searchBg = Color(0xFF2A2A2F),
    textPrimary = Color(0xFFF2F2EE),
    // 【v1.3.0】0xFF9B9B93 在玻璃卡上（卡片实测 rgb(95,91,114)）对比度约 2.5:1，
    // 达不到可读线（4.5:1）。磨砂玻璃卡比纯色卡片亮一档，副文本必须跟着提亮：
    // 0xFFB6B6AD 在同样卡片上约 4.4:1，且与 textPrimary 仍拉开层次。
    textSecondary = Color(0xFFB6B6AD),
    divider = Color(0xFF2E2E33),
    danger = Color(0xFFE15555),
    badgeScrim = Color(0xB3000000),
)

/** 当前主题的配色方案，由 [com.dengying.player.ui.theme.DengYingTheme] 提供。 */
val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * 兼容层：属性名与旧 `object Palette` 完全一致，调用点零改动。
 * getter 读取 CompositionLocal → 在 composable 内随主题自动切换。
 */
object Palette {
    val Brand: Color @Composable get() = LocalPalette.current.brand
    val BrandSecondary: Color @Composable get() = LocalPalette.current.brandSecondary
    val AppBg: Color @Composable get() = LocalPalette.current.appBg
    val CardBg: Color @Composable get() = LocalPalette.current.cardBg
    val SearchBg: Color @Composable get() = LocalPalette.current.searchBg
    val TextPrimary: Color @Composable get() = LocalPalette.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalPalette.current.textSecondary
    val Divider: Color @Composable get() = LocalPalette.current.divider
    val Danger: Color @Composable get() = LocalPalette.current.danger
    val BadgeScrim: Color @Composable get() = LocalPalette.current.badgeScrim
}
