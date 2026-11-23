package com.dengying.player.domain.search

import com.dengying.player.core.VideoItem

/**
 * 搜索结果相关度排序（FR-201 补充：聚合结果按匹配度重排）。
 *
 * ## 为什么需要它
 *
 * `SourceRepository.searchAll` 按各源**到达顺序**累积结果，这是流式秒搜
 * （NFR-002）的刻意取舍：先返回的源先上屏。但到达顺序等价于「谁快谁靠前」，
 * 与用户搜的词有多匹配无关。本对象在**不改动上屏时机**的前提下，对结果快照
 * 按相关度重排，让最匹配的片名出现在列表头部。
 *
 * ## 评分口径
 *
 * 标题归一化后与关键词比对，取命中档位的最高分作基础分：
 *
 * | 档位 | 基础分 |
 * |---|---|
 * | 精确相等 | 100 |
 * | 前缀命中 | 80 |
 * | 子串命中 | 60 |
 * | 关键词分词覆盖率 | 40 × 命中词数 / 总词数 |
 *
 * 弱加成（避免年份/演员压过标题）：关键词年份命中 +3，演员/导演字段命中 +2。
 * 演员命中要求关键词归一化后至少 2 个字符，避免单字在演员表里到处误命中。
 *
 * ## 稳定性
 *
 * 同分项保持输入顺序（显式带 index 作次级键，不依赖 `sortedWith` 的实现稳定性）。
 * 调用方传入的 `merged` 正是到达顺序，故同分等价于保留 tier / 速度次序。
 *
 * 纯逻辑、无平台依赖，放在 `domain`，正确性由 `commonTest` 守护。
 */
object SearchRelevance {

    /**
     * 按关键词相关度对结果重排，返回新列表；输入列表与元素均不修改。
     * 关键词为空（归一化后）时原样返回，避免空词把所有分数打成 0 后仍做一次无意义排序。
     */
    fun rank(items: List<VideoItem>, keyword: String): List<VideoItem> {
        val kw = TitleKey.normalizeTitle(keyword)
        if (kw.isEmpty() || items.isEmpty()) return items

        data class Scored(val item: VideoItem, val score: Int, val index: Int)

        return items
            .mapIndexed { index, item -> Scored(item, score(item, kw), index) }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.index })
            .map { it.item }
    }

    private fun score(item: VideoItem, kw: String): Int {
        val title = TitleKey.normalizeTitle(item.vod_name)
        val base = when {
            title == kw -> 100
            title.startsWith(kw) -> 80
            title.contains(kw) -> 60
            else -> {
                val tokens = kw.split(' ', '\t', '\n', '\r').filter { it.isNotEmpty() }
                if (tokens.isEmpty()) 0
                else {
                    val hit = tokens.count { title.contains(it) }
                    val tokenScore = (40.0 * hit / tokens.size).toInt()
                    // 无空格的中文关键词不能只靠分词：「仙逆」必须高于只命中「逆」的结果。
                    // 逐字覆盖只作弱分，封顶低于完整子串命中（60），避免单字宽匹配插到精确结果前面。
                    val charScore = if (kw.length >= 2 && !kw.contains(' ')) {
                        val matched = kw.count { title.contains(it) }
                        (30.0 * matched / kw.length).toInt()
                    } else 0
                    maxOf(tokenScore, charScore)
                }
            }
        }

        var bonus = 0
        val kwYear = TitleKey.parseYear(kw)
        if (kwYear > 0 && TitleKey.parseYear(item.vod_year) == kwYear) bonus += 3
        if (kw.length >= 2) {
            if (item.vod_actor?.let { TitleKey.normalizeTitle(it).contains(kw) } == true) bonus += 2
            if (item.vod_director?.let { TitleKey.normalizeTitle(it).contains(kw) } == true) bonus += 2
        }
        return base + bonus
    }
}
