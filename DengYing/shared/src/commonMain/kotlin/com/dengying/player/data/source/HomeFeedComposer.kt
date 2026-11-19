package com.dengying.player.data.source

import com.dengying.player.core.HomeSection
import com.dengying.player.core.HOME_HOT_TITLE
import com.dengying.player.core.VideoItem

/**
 * 首页版块编排（纯函数，不发请求）。
 *
 * 输入是「分类名 → 该分类的条目」，输出是 AH 首页形的版块序列：
 *  1. 第一条固定 [HOME_HOT_TITLE]——多分类轮转取样，供首屏 Banner + 双列宫格；
 *  2. 其后每个分类一条横滑 rail，条目里剔除已进入热播池的片，避免首屏重复。
 *
 * 同片可能出现在不同源：按「源 key + vod_id + 片名」去重，保留先到的条目。
 */
internal object HomeFeedComposer {

    /** 热播池上限：3 张 Banner + 9 张双列宫格 */
    private const val HOT_LIMIT = 12

    /** 每条 rail 的条目上限 */
    private const val RAIL_LIMIT = 14

    /** 少于这个数不出版块（两三张卡的横滑条观感很差） */
    private const val MIN_ITEMS = 4

    fun compose(byCategory: Map<String, List<VideoItem>>): List<HomeSection> {
        val pools = byCategory.mapValues { (_, items) ->
            items.asSequence()
                .filter { it.vod_pic.isNotBlank() && it.vod_name.isNotBlank() }
                .distinctBy { keyOf(it) }
                .toList()
        }

        val hot = ArrayList<VideoItem>(HOT_LIMIT)
        val hotKeys = HashSet<String>(HOT_LIMIT * 2)
        val maxSize = pools.values.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxSize) {
            for (pool in pools.values) {
                val v = pool.getOrNull(i) ?: continue
                if (!hotKeys.add(keyOf(v))) continue
                hot += v
                if (hot.size >= HOT_LIMIT) break
            }
            if (hot.size >= HOT_LIMIT) break
        }

        val out = ArrayList<HomeSection>(pools.size + 1)
        if (hot.size >= MIN_ITEMS) {
            out += HomeSection(title = HOME_HOT_TITLE, items = hot)
        }
        for ((category, pool) in pools) {
            val rest = pool.asSequence()
                .filter { keyOf(it) !in hotKeys }
                .take(RAIL_LIMIT)
                .toList()
            if (rest.size < MIN_ITEMS) continue
            out += HomeSection(title = "${category}推荐", items = rest)
        }
        return out
    }

    private fun keyOf(v: VideoItem): String =
        "${v._source ?: ""}|${v.vod_id}|${v.vod_name}"
}
