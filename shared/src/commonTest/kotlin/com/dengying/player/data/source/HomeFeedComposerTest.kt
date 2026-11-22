package com.dengying.player.data.source

import com.dengying.player.core.HOME_HOT_TITLE
import com.dengying.player.core.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeFeedComposerTest {

    @Test
    fun hotSectionComesFirstAndRailsExcludeHotItems() {
        val sections = HomeFeedComposer.compose(
            linkedMapOf(
                "电影" to videos(1, 12),
                "电视剧" to videos(2, 12),
                "综艺" to videos(3, 12),
                "动漫" to videos(4, 12),
            )
        )

        assertEquals(5, sections.size)
        assertEquals(HOME_HOT_TITLE, sections.first().title)
        assertEquals(12, sections.first().items.size)
        // 轮转取样：每个分类各贡献 3 条
        assertEquals(
            mapOf("电影" to 3, "电视剧" to 3, "综艺" to 3, "动漫" to 3),
            sections.first().items.groupingBy { it.type_name.orEmpty() }.eachCount(),
        )

        val hotKeys = sections.first().items.map { "${it._source}|${it.vod_id}" }.toSet()
        for (rail in sections.drop(1)) {
            assertEquals(9, rail.items.size)
            assertTrue(rail.items.none { "${it._source}|${it.vod_id}" in hotKeys })
        }
    }

    @Test
    fun thinCategoryIsDroppedInsteadOfRenderingAStubRail() {
        val sections = HomeFeedComposer.compose(
            linkedMapOf(
                "电影" to videos(1, 12),
                "电视剧" to videos(2, 12),
                "综艺" to videos(3, 5),
                "动漫" to videos(4, 12),
            )
        )

        assertTrue(sections.none { it.title == "综艺推荐" })
        assertEquals(4, sections.size)
    }

    @Test
    fun oneCategoryAlreadyProducesRenderableHotSection() {
        // 首个分类到货时先上屏：只要一个分类就能凑出 Banner + 双列宫格。
        val sections = HomeFeedComposer.compose(linkedMapOf("电影" to videos(1, 12)))

        assertEquals(1, sections.size)
        assertEquals(HOME_HOT_TITLE, sections.first().title)
        assertEquals(12, sections.first().items.size)
    }

    @Test
    fun blankCoversAndDuplicateKeysAreRemovedBeforeSampling() {
        val duplicated = videos(1, 2) + videos(1, 2)
        val blank = VideoItem(vod_id = 99, vod_name = "无封面", vod_pic = "", _source = "s")
        val sections = HomeFeedComposer.compose(
            linkedMapOf(
                "电影" to videos(1, 10),
                "电视剧" to videos(2, 10),
                "综艺" to videos(3, 10),
                "动漫" to duplicated + blank,
            )
        )

        val all = sections.flatMap { it.items }
        assertEquals(all.size, all.distinctBy { "${it._source}|${it.vod_id}" }.size)
        assertTrue(all.none { it.vod_id == 99L })
    }

    private fun videos(source: Int, count: Int): List<VideoItem> = (1..count).map { i ->
        VideoItem(
            vod_id = source * 100L + i,
            vod_name = "片$source-$i",
            vod_pic = "https://img/$source/$i.jpg",
            type_name = listOf("电影", "电视剧", "综艺", "动漫")[source - 1],
            _source = "src$source",
        )
    }
}
