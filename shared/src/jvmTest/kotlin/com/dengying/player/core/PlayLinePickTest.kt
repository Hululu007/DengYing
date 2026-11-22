package com.dengying.player.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [chooseDefaultLineIndex] 守卫测试。
 *
 * 覆盖详情页「进页默认播哪条线路」的判定口径：
 *  - 集数最多者优先（能播完整视频）
 *  - 预告 / 花絮类线路不论集数多少都排后
 *  - 完全并列时保持协议顺序
 *  - 空线路不越界
 */
class PlayLinePickTest {

    private fun line(name: String, n: Int): PlayLine =
        PlayLine(name = name, episodes = (1..n).map { Episode("第${it}集", "http://x/$it.m3u8") })

    @Test
    fun empty_lines_return_zero() {
        assertEquals(0, chooseDefaultLineIndex(emptyList()))
    }

    @Test
    fun single_line_is_always_chosen() {
        assertEquals(0, chooseDefaultLineIndex(listOf(line("线路1", 1))))
    }

    @Test
    fun line_with_most_episodes_wins_even_when_not_first() {
        val lines = listOf(line("线路1", 2), line("线路2", 40), line("线路3", 12))
        assertEquals(1, chooseDefaultLineIndex(lines))
    }

    @Test
    fun preview_line_loses_even_with_more_episodes() {
        val lines = listOf(line("预告", 60), line("正片线路", 3))
        assertEquals(1, chooseDefaultLineIndex(lines))
    }

    @Test
    fun preview_marker_variants_are_recognized() {
        for (name in listOf("预告片", "花絮合集", "精彩片段", "特辑", "抢先看")) {
            assertTrue(isPreviewLine(name), "应识别为非正片: $name")
        }
        assertTrue(!isPreviewLine("线路1"), "普通线路名不应被当成预告")
        assertTrue(!isPreviewLine(""), "空名称不应被当成预告")
    }

    @Test
    fun tie_keeps_protocol_order() {
        val lines = listOf(line("线路1", 10), line("线路2", 10), line("线路3", 9))
        assertEquals(0, chooseDefaultLineIndex(lines))
    }

    @Test
    fun when_all_lines_are_previews_pick_the_fullest() {
        val lines = listOf(line("预告", 1), line("花絮", 30))
        assertEquals(1, chooseDefaultLineIndex(lines))
    }

    @Test
    fun full_line_picked_regardless_of_position() {
        val lines = listOf(line("预告", 1), line("线路2", 5), line("线路3", 36))
        assertEquals(2, chooseDefaultLineIndex(lines))
    }
}

class CrossSourcePlayLinePickTest {
    @Test
    fun current_full_series_stays() {
        assertEquals(null, chooseFullerSourceEpisodeCount(168, "正片", 200))
    }

    @Test
    fun current_single_episode_promotes_full_candidate() {
        assertEquals(24, chooseFullerSourceEpisodeCount(1, "正片", 24))
    }

    @Test
    fun preview_line_promotes_full_candidate() {
        assertEquals(32, chooseFullerSourceEpisodeCount(8, "抢先版", 32))
    }

    @Test
    fun one_episode_gain_is_not_worth_switching() {
        assertEquals(null, chooseFullerSourceEpisodeCount(20, "正片", 21))
    }

    @Test
    fun candidate_without_full_episodes_is_ignored() {
        assertEquals(null, chooseFullerSourceEpisodeCount(1, "正片", 1))
    }
}
