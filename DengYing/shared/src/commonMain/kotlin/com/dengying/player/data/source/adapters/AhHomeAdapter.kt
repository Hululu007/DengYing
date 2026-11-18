package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.HomeSection
import com.dengying.player.core.HomeTab
import com.dengying.player.core.VideoItem
import com.dengying.player.data.source.HomeFeedComposer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    /** 分类名 → api2233 的 typeId（实测 t=1 电影 2523 / t=2 电视剧 3439 / t=3 综艺 57 / t=4 动漫 1241） */
    private val CATEGORIES = listOf(
        "电影" to 1,
        "电视剧" to 2,
        "综艺" to 3,
        "动漫" to 4,
    )

    /** api2233 的 type_name 对这几个分类统一返回「分类N」，不能当展示名用。 */
    private val GENERIC_TYPE_NAME = Regex("^分类\\d+$")

    fun homeTabs(): List<HomeTab> = listOf(HomeTab(name = "推荐", path = PATH_RECOMMEND))

    suspend fun homeSections(source: CmsSource, path: String): List<HomeSection> {
        if (path != PATH_RECOMMEND) return emptyList()
        val byCategory = coroutineScope {
            CATEGORIES.map { (name, typeId) ->
                async { name to fetchCategory(source, name, typeId) }
            }.awaitAll()
        }.filter { it.second.isNotEmpty() }.toMap()
        return HomeFeedComposer.compose(byCategory)
    }

    private suspend fun fetchCategory(
        source: CmsSource,
        category: String,
        typeId: Int,
    ): List<VideoItem> {
        val listed = CmsAdapter.list(source, typeId, 1).items
            .filter { it.vod_id > 0L && it.vod_name.isNotBlank() }
            .take(FETCH_PER_CATEGORY)
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
