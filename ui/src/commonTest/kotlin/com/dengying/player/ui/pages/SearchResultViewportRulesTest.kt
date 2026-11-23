package com.dengying.player.ui.pages

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchResultViewportRulesTest {

    @Test
    fun `首位因重排变化且用户没下滑时跟随新首位`() {
        assertTrue(
            SearchResultViewportRules.shouldFollowTopChange(
                topChanged = true,
                userScrolledAway = false,
            ),
        )
    }

    @Test
    fun `用户已下滑时不被后到结果拽回顶部`() {
        assertFalse(
            SearchResultViewportRules.shouldFollowTopChange(
                topChanged = true,
                userScrolledAway = true,
            ),
        )
    }

    @Test
    fun `首位未变化时不动视口`() {
        assertFalse(
            SearchResultViewportRules.shouldFollowTopChange(
                topChanged = false,
                userScrolledAway = false,
            ),
        )
    }
}
