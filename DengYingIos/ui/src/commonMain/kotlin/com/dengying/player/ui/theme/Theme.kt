package com.dengying.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 主题。
 *
 * 【深色模式】原工程的 dark 目录只覆写了 `start_window_background`，
 * 页面内仍大量硬编码浅色 hex——也就是说深色模式在原工程里是**不完整**的
 * （`docs/04 §7` 已把这一点记为已知缺口）。
 *
 * 本移植的处置：**不假装支持深色**。深色下沿用同一套浅色配色，
 * 并把这一点写在注释里——比给出一半的深色样式（白底卡片配深色文字）诚实。
 * 要真做深色，需要先把配色收敛成完整的语义 token 再逐页核对，
 * 那是一件独立的工作，不在移植范围内。
 */
private val LightColors = lightColorScheme(
    primary = Palette.Brand,
    onPrimary = Palette.CardBg,
    secondary = Palette.BrandSecondary,
    onSecondary = Palette.CardBg,
    background = Palette.AppBg,
    onBackground = Palette.TextPrimary,
    surface = Palette.CardBg,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SearchBg,
    onSurfaceVariant = Palette.TextSecondary,
    outline = Palette.Divider,
    error = Palette.Danger,
)

private val DarkColors = darkColorScheme(
    primary = Palette.Brand,
    onPrimary = Palette.CardBg,
    secondary = Palette.BrandSecondary,
    onSecondary = Palette.CardBg,
    background = Palette.AppBg,
    onBackground = Palette.TextPrimary,
    surface = Palette.CardBg,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SearchBg,
    onSurfaceVariant = Palette.TextSecondary,
    outline = Palette.Divider,
    error = Palette.Danger,
)

/**
 * 字号体系。
 *
 * 数值对齐原工程的用法（顶栏 20/17/18、正文 13-15、角标 9-12），
 * 但整理成语义级别而不是每个地方写死。行高按 1.35 倍给足——
 * 中文在 1.2 倍行高下会显得挤。
 */
private val AppTypography = Typography(
    // 品牌字（首页左上「灯影」）
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
    // 页面标题（我的收藏 / 播放历史 / 源状态）
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
    // 详情页片名
    titleSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp),
    // 卡片标题（列表条目名）
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    // 常规正文
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    // 次要说明（提示行、简介）
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    // 元信息（年份、来源、已看时长）
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    // 极小角标（更新状态、线路标签）
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
)

@Composable
fun DengYingTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 刻意按浅色渲染：见文件头关于深色模式的说明
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
