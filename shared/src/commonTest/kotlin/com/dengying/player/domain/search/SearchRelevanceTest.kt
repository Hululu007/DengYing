package com.dengying.player.domain.search

import com.dengying.player.core.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `SearchRelevance` 的契约用例（FR-201 补充）
 *
 * 核心不变式：
 *  1. 相关度分档：精确 > 前缀 > 包含 > 分词覆盖
 *  2. 年份/演员弱加成不推翻标题优先级
 *  3. 同分保持到达顺序（稳定排序）
 *  4. 全半角/书名号归一后仍正确匹配
 *  5. 空关键词原样返回
 */
class SearchRelevanceTest {

    private fun item(name: String, year: String? = null, actor: String? = null, director: String? = null) =
        VideoItem(vod_name = name, vod_year = year, vod_actor = actor, vod_director = director)

    private fun names(list: List<VideoItem>) = list.map { it.vod_name }

    @Test
    fun `精确匹配排在前缀匹配之前`() {
        val result = SearchRelevance.rank(
            listOf(item("庆余年第二季"), item("庆余年")),
            "庆余年",
        )
        assertEquals(listOf("庆余年", "庆余年第二季"), names(result))
    }

    @Test
    fun `前缀匹配排在子串匹配之前`() {
        val result = SearchRelevance.rank(
            listOf(item("我在庆余年的日子"), item("庆余年")),
            "庆余年",
        )
        assertEquals(listOf("庆余年", "我在庆余年的日子"), names(result))
    }

    @Test
    fun `子串匹配排在分词覆盖之前`() {
        val result = SearchRelevance.rank(
            listOf(item("余年庆"), item("庆余年第二季")),
            "庆余年",
        )
        // "余年庆" 不含完整 "庆余年"，只能靠分词覆盖；"庆余年第二季" 是前缀命中
        assertEquals(listOf("庆余年第二季", "余年庆"), names(result))
    }

    @Test
    fun `年份命中弱加成`() {
        val result = SearchRelevance.rank(
            listOf(item("庆余年", "2019"), item("庆余年", "2024")),
            "庆余年 2019",
        )
        // 两者标题都前缀命中"庆余年"，年份 2019 命中者靠前
        assertEquals(listOf("庆余年"), names(result).distinct())
        assertEquals("2019", result.first().vod_year)
    }

    @Test
    fun `演员命中弱加成不越过标题优先级档`() {
        val result = SearchRelevance.rank(
            listOf(item("余年庆", actor = "庆余年"), item("庆余年")),
            "庆余年",
        )
        // 标题精确命中（100）必须压过演员命中（40+2），即使演员项先到达
        assertEquals("庆余年", result.first().vod_name)
    }

    @Test
    fun `演员命中在同档内作弱加成`() {
        val result = SearchRelevance.rank(
            listOf(item("余年庆", actor = "庆余年"), item("余年庆")),
            "庆余年",
        )
        // 两者都在分词覆盖档（40），演员命中 +2 排前面，但不越过标题精确/前缀档
        assertEquals("庆余年", result.first().vod_actor)
    }

    @Test
    fun `全半角与书名号归一后仍匹配`() {
        val result = SearchRelevance.rank(
            listOf(item("《庆余年》"), item("庆余年")),
            "庆余年",
        )
        // 两者归一后都是"庆余年"，精确匹配同分，保持原顺序
        assertEquals(listOf("《庆余年》", "庆余年"), names(result))
    }

    @Test
    fun `同分保持输入顺序`() {
        val input = listOf(item("庆余年"), item("庆余年"), item("庆余年"))
        val result = SearchRelevance.rank(input, "庆余年")
        assertEquals(3, result.size)
        assertEquals("庆余年", result.first().vod_name)
    }

    @Test
    fun `空关键词原样返回`() {
        val input = listOf(item("庆余年"), item("庆余年第二季"))
        assertEquals(input, SearchRelevance.rank(input, "   "))
    }

    @Test
    fun `无标题匹配时按分词覆盖率排序`() {
        val result = SearchRelevance.rank(
            listOf(item("余年庆"), item("余年")),
            "庆余年",
        )
        // "余年庆" 命中 2/3 词，"余年" 命中 1/3 词
        assertEquals(listOf("余年庆", "余年"), names(result))
    }

    @Test
    fun `完整关键词命中排在单字宽匹配前面`() {
        val result = SearchRelevance.rank(
            listOf(
                item("逆世界"),
                item("逆修仙途"),
                item("仙逆"),
                item("仙逆第二季"),
            ),
            "仙逆",
        )
        assertEquals(listOf("仙逆", "仙逆第二季"), names(result).take(2))
    }

    @Test
    fun `单字关键词不在演员字段误加成`() {
        val result = SearchRelevance.rank(
            listOf(item("庆余年"), item("庆余年", actor = "张若昀")),
            "年",
        )
        // 关键词长度 1，演员字段不加成；两者标题都包含"年"，同分保持原序。
        // 演员项放在第二位，若误加成会翻到前面。
        assertEquals(listOf("庆余年", "庆余年"), names(result))
        assertEquals(null, result.first().vod_actor)
    }
}
