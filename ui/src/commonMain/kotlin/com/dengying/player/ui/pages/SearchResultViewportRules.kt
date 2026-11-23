package com.dengying.player.ui.pages

internal object SearchResultViewportRules {

    /**
     * 后到批次是否要把视口拉回新首位。
     *
     * 只看"首位有没有变"与"用户有没有主动下滑"：不能用 `firstVisibleItemIndex`
     * 判断，因为列表按 key 锚定后，旧首项可能已经被顶到索引 5，看起来像用户
     * 滑过很远，实际用户一直停在顶部。
     */
    fun shouldFollowTopChange(topChanged: Boolean, userScrolledAway: Boolean): Boolean =
        topChanged && !userScrolledAway
}
