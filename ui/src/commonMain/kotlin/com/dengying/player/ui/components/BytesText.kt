package com.dengying.player.ui.components

/** 下载体积的人类可读文本；小于 1 MB 时按 KB 显示。 */
fun formatBytesText(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val mb = safe / (1024L * 1024L)
    if (mb > 0L) return "$mb MB"
    val kb = safe / 1024L
    return "$kb KB"
}
