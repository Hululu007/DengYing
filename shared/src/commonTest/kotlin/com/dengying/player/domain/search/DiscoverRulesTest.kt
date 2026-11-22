package com.dengying.player.domain.search

import com.dengying.player.core.DiscoverFeed
import com.dengying.player.core.DiscoverItem
import com.dengying.player.core.DiscoverKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 发现层展示规则的契约用例。
 *
 * 这两条规则决定「什么能上屏」，而错误的后果是用户可见的：
 * 联想区多出一排空壳 chips、榜位出现一块没有海图的灰底。
 * 规则本身是纯数据变换，抽到 domain 后即可完整覆盖。
 */
class DiscoverRulesTest {

    private fun item(
        title: String,
        pic: String = "",
        entity: Boolean = true,
        kind: DiscoverKind = DiscoverKind.IqiyiSuggest,
    ) = DiscoverItem(title = title, pic = pic, kind = kind, isEntity = entity)

    private fun feed(vararg items: DiscoverItem, kind: DiscoverKind = DiscoverKind.IqiyiSuggest) =
        DiscoverFeed(kind = kind, items = items.toList())

    // ---- suggestItems ----

    @Test
    fun `联想区丢弃纯联想词`() {
        val f = feed(
            item("实体片", entity = true),
            item("联想词", entity = false),
        )
        val out = DiscoverRules.suggestItems(f)
        assertEquals(listOf("实体片"), out.map { it.title })
    }

    @Test
    fun `联想区保留全部实体条目且保序`() {
        val f = feed(item("A"), item("B"), item("C"))
        assertEquals(listOf("A", "B", "C"), DiscoverRules.suggestItems(f).map { it.title })
    }

    @Test
    fun `联想区不因缺封面而丢弃`() {
        // chips 是文字标签，没有封面无所谓
        val f = feed(item("无图", pic = ""))
        assertEquals(1, DiscoverRules.suggestItems(f).size)
    }

    @Test
    fun `联想区对 null 与无条目返回空`() {
        assertTrue(DiscoverRules.suggestItems(null).isEmpty())
        assertTrue(DiscoverRules.suggestItems(feed()).isEmpty())
    }

    @Test
    fun `豆瓣条目恒为实体故全部保留`() {
        val f = feed(
            item("豆瓣片1", pic = "http://x/1.jpg", kind = DiscoverKind.DoubanAnime),
            item("豆瓣片2", pic = "http://x/2.jpg", kind = DiscoverKind.DoubanAnime),
        )
        assertEquals(2, DiscoverRules.suggestItems(f).size)
    }

    // ---- rankingItems ----

    @Test
    fun `榜位丢弃无封面条目`() {
        val f = feed(
            item("有图", pic = "http://x/a.jpg"),
            item("无图", pic = ""),
        )
        assertEquals(listOf("有图"), DiscoverRules.rankingItems(f).map { it.title })
    }

    @Test
    fun `榜位同时丢弃非实体条目`() {
        val f = feed(
            item("有图实体", pic = "http://x/a.jpg", entity = true),
            item("有图联想词", pic = "http://x/b.jpg", entity = false),
            item("无图实体", pic = "", entity = true),
        )
        assertEquals(listOf("有图实体"), DiscoverRules.rankingItems(f).map { it.title })
    }

    @Test
    fun `榜位对 null 返回空`() {
        assertTrue(DiscoverRules.rankingItems(null).isEmpty())
    }

    @Test
    fun `两条规则对封面要求不同这不合并成一个函数__suggest 留图 ranking 要图`() {
        // 同一条目：联想区收、榜位弃。钉住这个刻意差异，防止后人「顺手合并」
        val f = feed(item("无图", pic = ""))
        assertEquals(1, DiscoverRules.suggestItems(f).size)
        assertEquals(0, DiscoverRules.rankingItems(f).size)
    }
}
