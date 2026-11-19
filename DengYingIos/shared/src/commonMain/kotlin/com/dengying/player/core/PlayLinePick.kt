package com.dengying.player.core

/**
 * 默认线路选择 —— 进入详情页时优先能播「完整视频」的线路。
 *
 * 背景：`vod_play_url` 里的第一条线路不保证最全。部分源把「预告 / 花絮 /
 * 片段」排在正片前面，或先给一条只有几集的试看线；详情页原先固定
 * `lineIdx = 0`，用户点播放可能只看到预告或残缺的一两集。
 *
 * 规则（稳定、可单测）：
 *  1. 名称含 [PREVIEW_MARKERS] 的线路视为非正片，整体排在正片之后；
 *  2. 同组内按集数降序，集数最多者优先；
 *  3. 完全并列时保留协议里的原始顺序（靠前的优先）。
 *
 * 纯函数，不依赖平台。调用方只在用户尚未手动选线时使用本结果，
 * 手动选线后不得再被网络刷新覆盖。
 */

/** 非正片线路的名称标记 */
private val PREVIEW_MARKERS = listOf("预告", "花絮", "片段", "特辑", "抢先")

/** 线路名是否表示预告 / 花絮等非正片内容 */
fun isPreviewLine(name: String): Boolean =
    name.isNotEmpty() && PREVIEW_MARKERS.any { name.contains(it) }

/**
 * 返回默认线路下标。
 *
 * 空列表返回 0，与现有 `currentEps()` 的空线路判定保持一致；
 * 调用方按 `lines.isEmpty()` 单独处理无线路场景。
 */
fun chooseDefaultLineIndex(lines: List<PlayLine>): Int {
    if (lines.isEmpty()) return 0
    var best = 0
    for (i in 1 until lines.size) {
        if (isBetterLine(lines[i], lines[best])) best = i
    }
    return best
}

private fun isBetterLine(candidate: PlayLine, current: PlayLine): Boolean {
    val candPreview = isPreviewLine(candidate.name)
    val curPreview = isPreviewLine(current.name)
    if (candPreview != curPreview) return !candPreview
    return candidate.episodes.size > current.episodes.size
}

/**
 * 跨源自动换线（FR-317）：当前源分片/试看，候选源有更多正片集时改走候选源。
 *
 * * 只在当前线路命中非正片标记，或当前集数 < 2 时考虑换源；
 * * 候选必须至少 2 集且比当前多 2 集以上，避免为了零头集数来回跳转；
 * * 更好 = 正片优先，再比集数；候选不优于 current 时返回 null。
 */
fun chooseFullerSourceEpisodeCount(currentEpisodes: Int, currentLine: String, candidateEpisodes: Int): Int? {
    val currentPreview = isPreviewLine(currentLine)
    if (!currentPreview && currentEpisodes >= 2) return null
    if (candidateEpisodes < 2) return null
    return candidateEpisodes.takeIf { it >= currentEpisodes + 2 }
}
