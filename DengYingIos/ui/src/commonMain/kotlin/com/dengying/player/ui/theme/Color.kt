package com.dengying.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 配色 —— **值的唯一来源**是鸿蒙工程的 `entry/src/main/resources/base/element/color.json`，
 * 逐条搬运、不自行调色。
 *
 * 只有 7 个语义色，加上警示红（原工程在确认框与「取消收藏」处直接写 `#DC2626`）。
 * 刻意不做渐变、不做层级阴影：这套配色的取向是「哑光纸面」，加装饰就破了。
 */
object Palette {
    /** 品牌主色（哑光黑） */
    val Brand = Color(0xFF0C0C09)

    /** 品牌副色（原工程 color.json 有，页面未实际使用，保留以对齐基线） */
    val BrandSecondary = Color(0xFF312C85)

    /** 页面底色（暖白） */
    val AppBg = Color(0xFFF4F4F1)

    /** 卡片底色（纯白，比页面底色亮一点，形成无阴影的层次） */
    val CardBg = Color(0xFFFFFFFF)

    /** 搜索框/胶囊底色（暖灰） */
    val SearchBg = Color(0xFFEAEAE5)

    /** 主文字 */
    val TextPrimary = Color(0xFF0C0C09)

    /** 次要文字 */
    val TextSecondary = Color(0xFF6E6E68)

    /** 分隔线 */
    val Divider = Color(0xFFE4E4DE)

    /** 警示红（破坏性操作：清空、取消收藏） */
    val Danger = Color(0xFFDC2626)

    /** 图片角标底（原工程 `#B3000000`） */
    val BadgeScrim = Color(0xB3000000)
}
