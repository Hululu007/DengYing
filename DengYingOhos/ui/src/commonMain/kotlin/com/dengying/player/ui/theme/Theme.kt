package com.dengying.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dengying.player.platform.KeyValueStore

/**
 * 主题风格偏好（v5，「我的」页可切，主流 app 的设置口径）。
 *
 * 【为什么是 object 而不是 VM】主题作用域是整棵组合树（根上取一次），
 * 放任何 VM 都会引入跨页依赖；全局单例 + `mutableStateOf` 让根主题
 * 组合期读它、设置页写它，改动即时重组生效。
 *
 * 【持久化】经平台壳注入的 [KeyValueStore]（与 [com.dengying.player.data.source.adapters.BttwoAuth]
 * 同一通道），键 `ui_theme_mode`。未 init（预览/测试）时仅内存生效。
 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

object ThemePrefs {
    private const val KEY = "ui_theme_mode"
    private var kv: KeyValueStore? = null

    /** 当前风格；默认跟随系统。写它即触发根主题重组 */
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** 平台壳启动时调用（DengYingApp.onCreate，与 BttwoAuth.init 同处） */
    fun init(store: KeyValueStore?) {
        kv = store
        mode = when (store?.getString(KEY, "system")) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    /** 「我的」页切换入口 */
    fun set(m: ThemeMode) {
        mode = m
        kv?.putString(KEY, when (m) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }, sync = false)
    }
}

/**
 * 主题。
 *
 * 【深色模式 · v3 起真正支持】原先刻意单浅色（原工程深色不完整，
 * `docs/04 §7` 记为已知缺口）。液态玻璃 v3 需求里深浅色切换是硬要求：
 * [Palette]/[Glass] 已 token 化（数据类 + Local 注入，调用点零改动），
 * 这里按 [darkTheme] 提供两份方案并接通 MaterialTheme 深色 colorScheme。
 * 页面里残留的个别硬编码色（如有）后续按语义收敛。
 *
 * 【字号体系】数值对齐原工程用法（顶栏 20/17/18、正文 13-15、角标 9-12），
 * 语义级 token；行高 1.35 倍——中文在 1.2 倍行高下会显得挤。
 */
private fun lightColors(p: PaletteScheme) = lightColorScheme(
    primary = p.brand,
    onPrimary = p.cardBg,
    secondary = p.brandSecondary,
    onSecondary = p.cardBg,
    background = p.appBg,
    onBackground = p.textPrimary,
    surface = p.cardBg,
    onSurface = p.textPrimary,
    surfaceVariant = p.searchBg,
    onSurfaceVariant = p.textSecondary,
    outline = p.divider,
    error = p.danger,
)

private fun darkColors(p: PaletteScheme) = darkColorScheme(
    primary = p.brand,
    onPrimary = p.cardBg,
    secondary = p.brandSecondary,
    onSecondary = p.cardBg,
    background = p.appBg,
    onBackground = p.textPrimary,
    surface = p.cardBg,
    onSurface = p.textPrimary,
    surfaceVariant = p.searchBg,
    onSurfaceVariant = p.textSecondary,
    outline = p.divider,
    error = p.danger,
)

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
    darkTheme: Boolean = when (ThemePrefs.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val glass = if (darkTheme) DarkGlass else LightGlass
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalGlassScheme provides glass,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColors(palette) else lightColors(palette),
            typography = AppTypography,
            content = content,
        )
    }
}
