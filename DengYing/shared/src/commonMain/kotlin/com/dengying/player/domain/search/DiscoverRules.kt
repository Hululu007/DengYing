package com.dengying.player.domain.search

import com.dengying.player.core.DiscoverFeed
import com.dengying.player.core.DiscoverItem

/**
 * 发现层条目的**展示规则** —— 纯模块，不依赖 Android
 *
 * 把「哪些条目值得上屏」从 ViewModel 里抽出来，理由与 [PosterLoader] 相同：
 * ViewModel 依赖 `viewModelScope`，在 `:ui:jvmTest` 里构造不出来，
 * 而这里的判断是纯数据变换，可以完整覆盖。
 *
 * 设计依据见 `core/DiscoverTypes.kt` 里 `DiscoverItem.isEntity` 的 Kdoc。
 */
object DiscoverRules {

    /**
     * 搜索结果页的联想词：**只保留实体作品**。
     *
     * 纯联想词（爱奇艺 `aid = 0`）的 `link` / `pic` 都是空串，渲染成卡片点了也
     * 无处可去；它们的唯一用途是「当搜索关键词」，而在本应用里点 chips 本来
     * 就是把词填进搜索框，所以留不留它们对交互没有差别——**留了只会多出一排
     * 没有封面、没有评分的空壳**。
     *
     * 豆瓣条目恒 `isEntity = true`，不会被过滤掉。
     */
    fun suggestItems(feed: DiscoverFeed?): List<DiscoverItem> =
        feed?.items?.filter { it.isEntity } ?: emptyList()

    /**
     * 首页榜位：**优先取有封面的条目**。
     *
     * 榜位是横滑卡片（84×116 的海报位），没有封面就是一块空灰底——比不显示更糟，
     * 用户会以为图挂了。故这里会把 `pic` 为空的条目过滤掉。
     *
     * 与 [suggestItems] 的差异是刻意的：chips 是文字标签，没有封面无所谓；
     * 卡片必须有图。两处需求不同，不合并成一个函数。
     */
    fun rankingItems(feed: DiscoverFeed?): List<DiscoverItem> =
        feed?.items?.filter { it.isEntity && it.pic.isNotEmpty() } ?: emptyList()
}
