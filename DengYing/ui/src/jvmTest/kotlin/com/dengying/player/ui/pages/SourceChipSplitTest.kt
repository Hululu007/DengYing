package com.dengying.player.ui.pages

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 详情页播放源折叠规则（FR-634）。
 *
 * 上限含「当前」黑底 chip：20 个上限 → 最多铺 19 个候选 + 1 个「更多」。
 * 候选不足时全部铺出，不出现「更多」。
 */
class SourceChipSplitTest {

    @Test
    fun `候选不足上限时全部铺出`() {
        assertEquals(0 to 0, splitSourceChips(0))
        assertEquals(6 to 0, splitSourceChips(6))
        assertEquals(19 to 0, splitSourceChips(19))
    }

    @Test
    fun `超过上限时第 20 个位置留给更多`() {
        assertEquals(19 to 1, splitSourceChips(20))
        assertEquals(19 to 2, splitSourceChips(21))
        assertEquals(19 to 81, splitSourceChips(100))
    }

    @Test
    fun `上限可注入且退化时不出负数`() {
        assertEquals(2 to 1, splitSourceChips(3, max = 3))
        assertEquals(0 to 3, splitSourceChips(3, max = 1))
        assertEquals(0 to 3, splitSourceChips(3, max = 0))
    }
}
