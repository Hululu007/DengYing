package com.dengying.player.ui.pages

import com.dengying.player.core.HOME_HOT_TITLE
import com.dengying.player.core.HomeSection
import com.dengying.player.core.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeFeedLayoutTest {

    @Test
    fun hotSectionBecomesHeroAndGridWhileRailsStaySeparate() {
        val hot = videos(1, 12)
        val rails = listOf(
            HomeSection("电影推荐", videos(2, 6)),
            HomeSection("电视剧推荐", videos(3, 6)),
        )

        val layout = buildHomeFeedLayout(listOf(HomeSection(HOME_HOT_TITLE, hot)) + rails)

        assertEquals(hot.take(3), layout.hero)
        assertEquals(hot.drop(3), layout.grid)
        assertEquals(rails, layout.rails)
    }

    @Test
    fun sectionWithoutHotTitleFallsBackToFirstSection() {
        val first = HomeSection("电影推荐", videos(1, 5))
        val layout = buildHomeFeedLayout(listOf(first, HomeSection("电视剧推荐", videos(2, 5))))

        assertEquals(3, layout.hero.size)
        assertEquals(2, layout.grid.size)
        assertEquals(listOf(HomeSection("电视剧推荐", videos(2, 5))), layout.rails)
    }

    @Test
    fun emptySectionsProduceEmptyLayout() {
        val layout = buildHomeFeedLayout(emptyList())
        assertTrue(layout.hero.isEmpty())
        assertTrue(layout.grid.isEmpty())
        assertTrue(layout.rails.isEmpty())
    }

    private fun videos(source: Int, count: Int): List<VideoItem> = (1..count).map { i ->
        VideoItem(
            vod_id = source * 100L + i,
            vod_name = "片$source-$i",
            vod_pic = "https://img/$source/$i.jpg",
            _source = "src$source",
        )
    }
}
