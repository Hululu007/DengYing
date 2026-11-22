package com.dengying.player.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FR-635 自动换源口径：进详情页就直接选中播放最完整的源。
 *
 * 这些断言钉的是「什么时候打扰用户」的边界——多换一次源就是多一次整页
 * 重新加载，少换一次就是用户点开只有 1 集的试看线。
 */
class BestSourcePolicyTest {

    @Test
    fun `候选集数更多时换成集数最多的`() {
        val targets = rankedSwitchTargets(
            currentKey = "ah_ap2233",
            currentEpCount = 12,
            currentHasGap = false,
            candidates = listOf(
                SourceFacts("hulu_ffzy", 40),
                SourceFacts("hulu_hongniu", 24),
                SourceFacts("hulu_uku", 40),
            ),
        )
        assertEquals(listOf("hulu_ffzy", "hulu_uku", "hulu_hongniu"), targets)
    }

    @Test
    fun `集数持平时不换源`() {
        val targets = rankedSwitchTargets(
            currentKey = "hulu_ffzy",
            currentEpCount = 40,
            currentHasGap = false,
            candidates = listOf(SourceFacts("hulu_hongniu", 40), SourceFacts("hulu_uku", 12)),
        )
        assertTrue(targets.isEmpty(), "集数不多于当前源就不该打断用户，实际=$targets")
    }

    @Test
    fun `当前源不可播时集数更少也换`() {
        val targets = rankedSwitchTargets(
            currentKey = "ah_ap2233",
            currentEpCount = 0,
            currentHasGap = true,
            candidates = listOf(SourceFacts("hulu_hongniu", 3), SourceFacts("hulu_uku", 0)),
        )
        assertEquals(listOf("hulu_hongniu"), targets)
    }

    @Test
    fun `未探测到集数的候选被忽略`() {
        val targets = rankedSwitchTargets(
            currentKey = "ah_ap2233",
            currentEpCount = 0,
            currentHasGap = true,
            candidates = listOf(SourceFacts("hulu_hongniu", -1), SourceFacts("hulu_uku", 0)),
        )
        assertTrue(targets.isEmpty(), "集数未知的候选不能自动换过去，实际=$targets")
    }

    @Test
    fun `当前源自身永远不是目标`() {
        val targets = rankedSwitchTargets(
            currentKey = "hulu_ffzy",
            currentEpCount = 0,
            currentHasGap = true,
            candidates = listOf(SourceFacts("hulu_ffzy", 99)),
        )
        assertTrue(targets.isEmpty(), "不能换到自己，实际=$targets")
    }

    /**
     * DVP §10：6s 快照里没出现更好的源，不代表「没有更好的源」——慢源还没回来。
     * 快照只带未探测的候选，此时口径必须是「不换、继续等」，而不是「保持现状即结论」。
     */
    @Test
    fun `快照缺集数时不换源继续等完整候选`() {
        val snapshot = rankedSwitchTargets(
            currentKey = "ah_ap2233",
            currentEpCount = 12,
            currentHasGap = false,
            // 快照里的候选都还没探测到集数（epCount = -1）
            candidates = listOf(SourceFacts("hulu_hongniu", -1), SourceFacts("hulu_uku", -1)),
        )
        assertTrue(snapshot.isEmpty(), "未探测的快照不能作为换源依据，实际=$snapshot")

        // 完整候选到位后，更完整的那条（40 集，晚到）应当胜出；集数持平的（12）不动
        val complete = rankedSwitchTargets(
            currentKey = "ah_ap2233",
            currentEpCount = 12,
            currentHasGap = false,
            candidates = listOf(
                SourceFacts("hulu_uku", 12),
                SourceFacts("hulu_ffzy", 40),
            ),
        )
        assertEquals(listOf("hulu_ffzy"), complete)
    }
}
