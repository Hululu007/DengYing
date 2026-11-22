package com.dengying.player.ui.components

import com.dengying.player.ui.platform.VideoZoomMode

/**
 * 新全屏 UI 的纯展示规则。抽出来是为了在 JVM 单测里覆盖，
 * Compose 侧只调用这些函数，不在这里依赖任何平台 API。
 */

/** 返回键右边显示的文字：多集才带集名；直播只显示频道名。 */
internal fun fullscreenTitleText(
    title: String,
    episodeName: String,
    episodeCount: Int,
    live: Boolean,
): String {
    val name = title.trim()
    if (live) return name
    val episode = episodeName.trim()
    if (episode.isEmpty() || episodeCount <= 1) return name
    return if (name.isEmpty()) episode else "$name $episode"
}

/** 三档缩放的固定文案，与 PlayerViewModel.zoomLabel 保持一致。 */
internal fun zoomLabel(mode: VideoZoomMode): String = when (mode) {
    VideoZoomMode.FIT -> "保留比例"
    VideoZoomMode.COVER -> "裁剪铺满"
    VideoZoomMode.FILL -> "拉伸铺满"
}

/** 点「画面」下拉：打开时关闭倍速；已打开则关闭。 */
internal fun toggleZoomSheet(open: Boolean, speedOpen: Boolean): Pair<Boolean, Boolean> =
    !open to false

/** 点「倍速」下拉：打开时关闭画面；已打开则关闭。返回 (画面, 倍速)。 */
internal fun toggleSpeedSheet(open: Boolean, zoomOpen: Boolean): Pair<Boolean, Boolean> =
    false to !open

/** 播放中、无下拉、未拖动、未 seek、无错误时，3 秒后自动隐藏上下栏。 */
internal fun shouldAutoHideControls(
    playing: Boolean,
    buffering: Boolean,
    dragging: Boolean,
    seeking: Boolean,
    hasError: Boolean,
    dropdownOpen: Boolean,
): Boolean = playing && !buffering && !dragging && !seeking && !hasError && !dropdownOpen

/** 直播左下角的 LIVE 标记，点播不显示。 */
internal fun liveBadgeVisible(live: Boolean): Boolean = live

/** 海报失败兜底首字：空白名返回 null（UI 画播放图标）。 */
internal fun posterFallbackChar(title: String): Char? = title.trim().firstOrNull()

/** 稳定渐变色对：同名总是同一对颜色，纯函数便于测试。 */
internal fun posterFallbackColors(title: String): Pair<Long, Long> {
    var hash = 5381L
    for (ch in title) hash = (hash * 33 + ch.code) and 0x7FFFFFFF
    val hue = (hash % 360).toFloat()
    val color1 = hsvToArgb(hue, 0.38f, 0.34f)
    val color2 = hsvToArgb((hue + 28f) % 360f, 0.42f, 0.20f)
    return color1 to color2
}

private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Long {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val c = value * saturation
    val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
    val m = value - c
    val (r, g, b) = when (h.toInt()) {
        0 -> triple(c, x, 0f)
        1 -> triple(x, c, 0f)
        2 -> triple(0f, c, x)
        3 -> triple(0f, x, c)
        4 -> triple(x, 0f, c)
        else -> triple(c, 0f, x)
    }
    fun channel(v: Float) = ((v + m) * 255).toLong().coerceIn(0L, 255L)
    // 【必须用 0xFF000000L 直接按位或】Compose 的 Color(Long) 只取低 32 位再左移：
    // 写成 `0xFF000000L shl 24` 会把 alpha 挤出 32 位，低 32 位只剩 RGB，alpha=0，
    // 兜底渐变就成了全透明——卡片看着是「白板」，正是 2026-11-21 真机复现的问题。
    return 0xFF000000L or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

private fun triple(a: Float, b: Float, c: Float) = Triple(a, b, c)
