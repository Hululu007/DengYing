package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.HomeSection
import com.dengying.player.core.HomeTab
import com.dengying.player.core.VideoItem
import com.dengying.player.data.source.HomeFeedComposer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope

/**
 * AH 首页「现场合成」适配器（方案路线 B）。
 *
 * AH 的首页 JSON 由设备端运行时拼接主机名，PC 上抓不到（通路 A 未闭环）。
 * 但 AH 的 CMS 源 `ah_ap2233` 支持标准 `ac=videolist` / `ac=detail`：
 * 按 电影 / 电视剧 / 综艺 / 动漫 四个 typeId 各拉一页，再用批量 detail 补齐
 * 封面与年份/地区，交给 [HomeFeedComposer] 编排出与 AH 首页同形的版块。
 *
 * 这里的 key 是 `ah_ap2233` 自己的，播放能力受该源 `playGap` 限制；
 * 卡片进详情后可走「其他资源站」换源，与首页只负责发现的产品口径一致。
 */
internal object AhHomeAdapter {

    /** 推荐页路径（HomeTab.path）。只有一页，不渲染频道 chips。 */
    private const val PATH_RECOMMEND = "recommend"

    /** 每个分类先取这么多条，补全后再由 composer 截断/轮转 */
    private const val FETCH_PER_CATEGORY = 14

    /**
     * 分类名 → ffzy 叶子 typeId（每栏两个，合并后截 14 条）。
     *
     * 【为什么是叶子 id】ffzy 的 `t=1..4` 是顶层分类，顶层几乎不挂直属影片：
     * 实测 `ac=videolist&t=1/2/3` 返回 total=0，`t=4` 只有 44 条 2022 年前的旧动漫，
     * 首页会整屏变成 2011-2013 年的老片。叶子 id（动作片 6 / 国产剧 13 /
     * 大陆综艺 25 / 国产动漫 29 等）才按 vod_time 倒序返回最新内容。
     * api2233 时期用 `t=1..4` 是因为那套数据把影片直接挂在顶层，换源后必须改口径。
     * 刻意不取 34 伦理片、36 短剧：前者内容不合规，后者与现有短剧源重复。
     */
    private val CATEGORIES = listOf(
        "电影" to listOf(6, 7),
        "电视剧" to listOf(13, 14),
        "综艺" to listOf(25, 26),
        "动漫" to listOf(29, 30),
    )

    /** api2233 的 type_name 对这几个分类统一返回「分类N」，不能当展示名用。 */
    private val GENERIC_TYPE_NAME = Regex("^分类\\d+$")

    fun homeTabs(): List<HomeTab> = listOf(HomeTab(name = "推荐", path = PATH_RECOMMEND))

    /**
     * @param onPartial 第一个有足够卡片的分类到货时回调一次，供页面先出 Banner + 双列宫格；
     *        最终仍以四分类全量 compose 覆盖。回调失败不影响全量结果。
     */
    suspend fun homeSections(
        source: CmsSource,
        path: String,
        onPartial: (List<HomeSection>) -> Unit = {},
    ): List<HomeSection> {
        if (path != PATH_RECOMMEND) return emptyList()
        val byCategory = coroutineScope {
            val arrived = Channel<Pair<String, List<VideoItem>>>(CATEGORIES.size)
            CATEGORIES.forEach { (name, typeIds) ->
                async {
                    val items = runCatching { fetchCategory(source, name, typeIds) }
                        .getOrDefault(emptyList())
                    arrived.send(name to items)
                }
            }
            val collected = LinkedHashMap<String, List<VideoItem>>()
            var partialSent = false
            repeat(CATEGORIES.size) {
                val (name, items) = arrived.receive()
                if (items.isEmpty()) return@repeat
                collected[name] = items
                if (!partialSent) {
                    val early = HomeFeedComposer.compose(collected)
                    if (early.isNotEmpty()) {
                        partialSent = true
                        runCatching { onPartial(early) }
                    }
                }
            }
            arrived.close()
            collected
        }
        return HomeFeedComposer.compose(byCategory)
    }

    private suspend fun fetchCategory(
        source: CmsSource,
        category: String,
        typeIds: List<Int>,
    ): List<VideoItem> {
        val pages = coroutineScope {
            typeIds.map { typeId ->
                async {
                    runCatching {
                        CmsAdapter.list(source, typeId, 1).items
                            .filter { it.vod_id > 0L && it.vod_name.isNotBlank() }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        val listed = interleaveNewest(pages, FETCH_PER_CATEGORY)
        if (listed.isEmpty()) return emptyList()
        val detailed = runCatching {
            CmsAdapter.details(source, listed.map { it.vod_id })
        }.getOrDefault(emptyList()).associateBy { it.vod_id }
        return listed.map { item ->
            val merged = detailed[item.vod_id] ?: item
            if (merged.type_name.isNullOrBlank() || GENERIC_TYPE_NAME.matches(merged.type_name.orEmpty())) {
                merged.copy(type_name = category)
            } else {
                merged
            }
        }
    }
}

/**
 * 多个分类页轮流取位合并，按 vod_id 去重，最多取 [limit] 条。
 *
 * 每个分类页自身已按 vod_time 倒序，交替取值既保持「新片在前」，
 * 又不会让第一个子分类把整栏占满。纯函数，便于单测。
 */
internal fun interleaveNewest(groups: List<List<VideoItem>>, limit: Int): List<VideoItem> {
    if (limit <= 0) return emptyList()
    val out = ArrayList<VideoItem>(limit)
    val seen = HashSet<Long>()
    val maxSize = groups.maxOfOrNull { it.size } ?: 0
    var i = 0
    while (out.size < limit && i < maxSize) {
        for (g in groups) {
            val item = g.getOrNull(i) ?: continue
            if (seen.add(item.vod_id)) out += item
            if (out.size >= limit) break
        }
        i++
    }
    return out
}
