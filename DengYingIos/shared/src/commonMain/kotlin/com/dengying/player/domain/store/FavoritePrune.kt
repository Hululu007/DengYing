package com.dengying.player.domain.store

/**
 * 收藏集合容量与淘汰（FR-509）—— 纯函数，不依赖 Android
 *
 * 需求：收藏集合有容量上限与淘汰策略；超限淘汰顺序确定**并提示用户**。
 *
 * 与观看记录（FR-504，[LedgerPrune]）的区别：收藏是**用户显式逐条挑选**的结果，
 * 没有「分组」概念——一条收藏就是一个独立条目，不存在「某部片的最新一集」这种
 * 需要保住的定位。故淘汰规则比 FR-504 简单：超限时按 `addTime` 最旧**整条淘汰**。
 *
 * 两个都必须做到：
 *
 * 1. **顺序确定**：同一份输入两次裁剪结果逐条一致，否则用户看到的收藏列表不稳定。
 * 2. **淘汰可见**：淘汰发生了就必须能被告知。用户加了收藏却"消失了一条"而无任何
 *    说明，与 FR-507 要消灭的「点了没反应」是同一类问题。
 */
object FavoritePrune {

    /** 参与淘汰的最小记录形状 */
    interface Entry {
        val vodId: Long
        val source: String
        val addTime: Long
    }

    /** 收藏容量上限 */
    const val MAX_FAVORITES = 100

    /** 唯一键：内容标识（`source + vod_id`） */
    fun favoriteKeyOf(e: Entry): String = "${e.vodId}@${e.source}"

    /** 裁剪结果 */
    data class Result<T>(
        /** 保留的收藏，按 `addTime` 降序（最近添加在前） */
        val kept: List<T>,
        /** 被淘汰的条数；为 0 表示未发生淘汰 */
        val dropped: Int,
    )

    /** 排序：addTime 降序；同值按唯一键升序，保证顺序确定 */
    private val byNewestFirst = Comparator<Entry> { a, b ->
        if (b.addTime != a.addTime) {
            b.addTime.compareTo(a.addTime)
        } else {
            favoriteKeyOf(a).compareTo(favoriteKeyOf(b))
        }
    }

    /**
     * 按 FR-509 裁剪到容量上限。
     *
     * @param list 收藏列表
     * @param max 容量上限
     * @return 保留的列表与被淘汰条数；不修改入参
     */
    fun <T : Entry> pruneFavorites(list: List<T>, max: Int = MAX_FAVORITES): Result<T> {
        if (max <= 0) return Result(emptyList(), list.size)

        val sorted = list.sortedWith(byNewestFirst)
        if (sorted.size <= max) return Result(sorted, 0)

        return Result(sorted.subList(0, max), sorted.size - max)
    }

    /**
     * 淘汰提示文案（FR-509「并提示用户」）。
     *
     * 说清三件事：淘汰了几条、为什么、以及后续怎么办。只说「已达上限」不够——
     * 用户需要知道**东西是不是已经没了**。
     */
    fun prunedNotice(dropped: Int): String {
        if (dropped <= 0) return ""
        return "收藏已达 $MAX_FAVORITES 条上限，已移除最旧的 $dropped 条"
    }
}
