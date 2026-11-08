package com.dengying.player.data.source.discover

import com.dengying.player.core.CmsSources
import com.dengying.player.core.DiscoverKind
import com.dengying.player.core.DiscoverQuery
import com.dengying.player.core.DoubanRankingTag
import com.dengying.player.data.source.adapters.Golden
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 发现层解析守卫（`:shared:jvmTest`）
 *
 * 金样本两份，均为 2026-10-27 真实响应逐字节落盘：
 *  - `golden/iqiyi_suggest.txt`  6420 B  `key=流浪地球`，10 条（2 实体 + 8 联想词）
 *  - `golden/douban_anime.txt`   7718 B  `tags=动漫&start=0`，20 条
 *
 * 断言分三组：
 *  1. **金样本正向** —— 钉住真实响应能被正确解读（条数、实体判定、字段映射）
 *  2. **端点差异** —— 两个端点的形态差异（豆瓣无 year、爱奇艺 code 是字符串）
 *  3. **容错与 URL 构造** —— 破损体、风控响应、分页参数
 *
 * 依据：`docs/04-验证记录.md` §15.6.1 的设计定稿。
 */
class DiscoverParserTest {

    // ---------------------------------------------------------------- 爱奇艺

    @Test
    fun iqiyi_golden_item_count() {
        val f = DiscoverParser.parseIqiyiSuggest(Golden.text("iqiyi_suggest"))
        assertEquals(10, f.items.size, "金样本 data 应为 10 条")
        assertEquals(DiscoverKind.IqiyiSuggest, f.kind)
        assertEquals("", f.err, "金样本不该带错误")
        assertTrue(f.hasData)
    }

    @Test
    fun iqiyi_golden_entity_vs_suggest_split() {
        val f = DiscoverParser.parseIqiyiSuggest(Golden.text("iqiyi_suggest"))
        val entities = f.items.filter { it.isEntity }
        val suggests = f.items.filterNot { it.isEntity }
        assertEquals(2, entities.size, "只有前 2 条是实体作品（aid > 0）")
        assertEquals(8, suggests.size, "后 8 条是纯联想词（aid = 0）")
    }

    @Test
    fun iqiyi_golden_first_entity_fields() {
        val f = DiscoverParser.parseIqiyiSuggest(Golden.text("iqiyi_suggest"))
        val e = f.items.first { it.isEntity }
        assertEquals("流浪地球", e.title)
        assertEquals("电影", e.subtitle, "subtitle 取 cname（分类名）")
        assertEquals("2019", e.year)
        assertEquals("http://www.iqiyi.com/v_19rrfe142o.html", e.link)
        assertEquals("923452300", e.externalId, "externalId 取 aid 的十进制字符串")
        assertEquals(71, e.pic.length, "picture_url 长度 71")
        assertTrue(e.pic.startsWith("http://pic5.iqiyipic.com/"))
    }

    @Test
    fun iqiyi_golden_second_entity_is_sequel() {
        val f = DiscoverParser.parseIqiyiSuggest(Golden.text("iqiyi_suggest"))
        val e = f.items.filter { it.isEntity }[1]
        assertEquals("流浪地球2", e.title)
        assertEquals("2023", e.year)
        assertEquals("7292991076670500",
            e.externalId, "aid 超过 Int 范围，必须按 Long 取")
    }

    @Test
    fun iqiyi_suggest_word_has_no_link_and_no_pic() {
        val f = DiscoverParser.parseIqiyiSuggest(Golden.text("iqiyi_suggest"))
        val s = f.items.first { !it.isEntity }
        assertEquals("流浪地球2：再次冒险", s.title)
        assertEquals("", s.link, "联想词没有详情页链接")
        assertEquals("", s.pic, "联想词没有封面")
        assertEquals("", s.externalId, "联想词 aid=0 ⇒ externalId 空串而非 \"0\"")
    }

    @Test
    fun iqiyi_suggest_word_year_is_current_year_and_untrustworthy() {
        val f = DiscoverParser.parseIqiyiSuggest(Golden.text("iqiyi_suggest"))
        val years = f.items.filterNot { it.isEntity }.map { it.year }.toSet()
        assertEquals(setOf("2026"), years,
            "联想词 year 一律是当前年（2026），不可信——这条断言钉住该事实，防止有人拿它做筛选")
    }

    @Test
    fun iqiyi_code_is_string_not_number() {
        // code 是字符串 "A00000"，不是数字 200。把数字当成功码会全盘失败。
        val ok = DiscoverParser.parseIqiyiSuggest("""{"code":"A00000","data":[]}""")
        assertEquals("", ok.err)
        val bad = DiscoverParser.parseIqiyiSuggest("""{"code":"A00001","data":[]}""")
        assertEquals("code A00001", bad.err)
        assertTrue(bad.items.isEmpty())
    }

    @Test
    fun iqiyi_non_json_body_reports_error() {
        val f = DiscoverParser.parseIqiyiSuggest("<html>502</html>")
        assertEquals("响应非 JSON", f.err)
        assertTrue(f.items.isEmpty())
    }

    @Test
    fun iqiyi_empty_data_array_is_not_an_error() {
        val f = DiscoverParser.parseIqiyiSuggest("""{"code":"A00000","data":[]}""")
        assertEquals("", f.err, "空数组是正常结果，不是错误")
        assertTrue(f.items.isEmpty())
    }

    @Test
    fun iqiyi_row_without_name_is_skipped() {
        val body = """{"code":"A00000","data":[
            {"aid":1,"name":"","link":"x"},
            {"aid":2,"name":"有名字"}
        ]}"""
        val f = DiscoverParser.parseIqiyiSuggest(body)
        assertEquals(1, f.items.size)
        assertEquals("有名字", f.items[0].title)
    }

    // ------------------------------------------------------------------ 豆瓣

    @Test
    fun douban_golden_item_count() {
        val f = DiscoverParser.parseDoubanAnime(Golden.text("douban_anime"))
        assertEquals(20, f.items.size, "金样本 data 应为 20 条")
        assertEquals(DiscoverKind.DoubanAnime, f.kind)
        assertEquals("", f.err)
        assertTrue(f.items.all { it.isEntity }, "豆瓣榜单条目全部是实体作品")
    }

    @Test
    fun douban_golden_first_item_fields() {
        val f = DiscoverParser.parseDoubanAnime(Golden.text("douban_anime"))
        val e = f.items[0]
        assertEquals("赛博朋克：边缘行者 第二季", e.title)
        assertEquals("5.5", e.subtitle, "subtitle 取 rate（字符串评分）")
        assertEquals("37421775", e.externalId, "豆瓣 id 天生是字符串")
        assertEquals("https://movie.douban.com/subject/37421775/", e.link)
        assertTrue(e.pic.startsWith("https://img3.doubanio.com/view/photo/s_ratio_poster/"))
    }

    @Test
    fun douban_golden_second_item_rate_is_high() {
        val f = DiscoverParser.parseDoubanAnime(Golden.text("douban_anime"))
        assertEquals("赛博朋克：边缘行者 第一季", f.items[1].title)
        assertEquals("9.0", f.items[1].subtitle)
        assertEquals("35118256", f.items[1].externalId)
    }

    @Test
    fun douban_year_is_always_empty() {
        // 豆瓣该端点不返回 year 字段（行键固定 10 个），故 year 必须恒空。
        // 这条断言防止有人「顺手」从 url 或 title 里猜年份。
        val f = DiscoverParser.parseDoubanAnime(Golden.text("douban_anime"))
        assertTrue(f.items.all { it.year.isEmpty() }, "豆瓣没有年份字段，year 必须恒空")
    }

    @Test
    fun douban_unrated_items_have_empty_subtitle() {
        // 金样本 20 条里有 3 条 rate 是空串（凡人修仙传 年番4 / 时光代理人 第三季 / 谷雨街后巷）
        val f = DiscoverParser.parseDoubanAnime(Golden.text("douban_anime"))
        val unrated = f.items.filter { it.subtitle.isEmpty() }
        assertEquals(3, unrated.size, "3 条无评分")
        assertTrue(unrated.any { it.title == "凡人修仙传 年番4" })
    }

    @Test
    fun douban_all_ids_are_numeric_strings() {
        val f = DiscoverParser.parseDoubanAnime(Golden.text("douban_anime"))
        assertTrue(f.items.all { it.externalId.isNotEmpty() })
        assertTrue(f.items.all { it.externalId.all { c -> c in '0'..'9' } },
            "豆瓣 id 全是数字，但仍是字符串")
    }

    @Test
    fun douban_risk_control_message_is_passed_through() {
        // 同 IP 第二次请求的真实风控响应
        val body = """{"msg":"检测到有异常请求从您的IP发出，请登录再试!","r":1}"""
        val f = DiscoverParser.parseDoubanAnime(body)
        assertEquals("检测到有异常请求从您的IP发出，请登录再试!", f.err,
            "风控文案原样透给用户，不翻译不改写")
        assertTrue(f.items.isEmpty())
    }

    @Test
    fun douban_risk_without_msg_gets_generic_text() {
        val f = DiscoverParser.parseDoubanAnime("""{"r":1}""")
        assertEquals("榜单暂不可用，请稍后再试", f.err)
    }

    @Test
    fun douban_non_json_body_reports_error() {
        val f = DiscoverParser.parseDoubanAnime("not json at all")
        assertEquals("响应非 JSON", f.err)
        assertTrue(f.items.isEmpty())
    }

    @Test
    fun douban_row_without_title_is_skipped() {
        val body = """{"data":[{"id":"1","title":""},{"id":"2","title":"有标题"}]}"""
        val f = DiscoverParser.parseDoubanAnime(body)
        assertEquals(1, f.items.size)
        assertEquals("有标题", f.items[0].title)
    }

    @Test
    fun douban_page_is_carried_through() {
        val f = DiscoverParser.parseDoubanAnime("""{"data":[]}""", page = 3)
        assertEquals(3, f.page, "页号由调用方传入并原样带出，解析层不猜")
    }

    // -------------------------------------------------------------- URL 构造

    @Test
    fun iqiyi_url_shape() {
        val u = DiscoverQuery.iqiyiSuggestUrl("流浪地球")
        assertTrue(u.startsWith("https://suggest.video.iqiyi.com/?if=mobile&key="))
        assertTrue(u.endsWith("&p=1&t=0"))
        assertTrue(u.contains("%E6%B5%81%E6%B5%AA%E5%9C%B0%E7%90%83"),
            "关键词必须 URL 编码（UTF-8 百分号）")
    }

    @Test
    fun iqiyi_url_encodes_space_as_percent20() {
        // UrlCodec.encode 比 encodeForm 多做 + → %20 替换（P3 批 F §13.13）
        val u = DiscoverQuery.iqiyiSuggestUrl("a b")
        assertTrue(u.contains("key=a%20b"), "空格必须是 %20 而不是 +")
    }

    @Test
    fun douban_url_shape() {
        val u = DiscoverQuery.doubanAnimeUrl(0)
        assertTrue(u.startsWith("https://movie.douban.com/j/new_search_subjects?sort=U&range=0,20&tags="))
        assertTrue(u.endsWith("&start=0"))
        assertTrue(u.contains("%E5%8A%A8%E6%BC%AB"), "动漫 编码为 %E5%8A%A8%E6%BC%AB")
    }

    @Test
    fun douban_url_start_offset() {
        assertTrue(DiscoverQuery.doubanAnimeUrl(20).endsWith("&start=20"))
        assertTrue(DiscoverQuery.doubanAnimeUrl(40).endsWith("&start=40"))
    }

    @Test
    fun douban_negative_start_clamped_to_zero() {
        assertTrue(DiscoverQuery.doubanAnimeUrl(-5).endsWith("&start=0"),
            "负偏移量夹到 0，别把负数发给站点")
    }

    @Test
    fun douban_page_of_start() {
        assertEquals(0, DiscoverQuery.doubanPageOf(0))
        assertEquals(1, DiscoverQuery.doubanPageOf(20))
        assertEquals(2, DiscoverQuery.doubanPageOf(40))
        assertEquals(0, DiscoverQuery.doubanPageOf(-3), "负值当第 0 页")
    }

    @Test
    fun douban_custom_tag() {
        val u = DiscoverQuery.doubanAnimeUrl(0, tag = "电影")
        assertTrue(u.contains("tags=%E7%94%B5%E5%BD%B1"))
    }

    // ------------------------------------------------- 分类榜单（老端点 subjects）

    @Test
    fun douban_ranking_url_shape() {
        val u = DiscoverQuery.doubanRankingUrl("movie", "热门", 0)
        // 老端点：j/search_subjects，不是 j/new_search_subjects
        assertTrue(u.startsWith("https://movie.douban.com/j/search_subjects?type=movie"),
            "必须走老端点 search_subjects（新端点已被 IP 风控）")
        assertTrue(u.contains("&sort=recommend"))
        assertTrue(u.contains("&page_limit=20"), "页大小与 DOUBAN_PAGE_SIZE 一致")
        assertTrue(u.endsWith("&page_start=0"))
        assertTrue(u.contains("tag=%E7%83%AD%E9%97%A8"), "热门 编码为 %E7%83%AD%E9%97%A8")
    }

    @Test
    fun douban_ranking_url_start_offset() {
        assertTrue(DiscoverQuery.doubanRankingUrl("tv", "综艺", 20).endsWith("&page_start=20"))
        assertTrue(DiscoverQuery.doubanRankingUrl("tv", "综艺", 40).endsWith("&page_start=40"))
    }

    @Test
    fun douban_ranking_negative_start_clamped_to_zero() {
        assertTrue(DiscoverQuery.doubanRankingUrl("tv", "综艺", -7).endsWith("&page_start=0"),
            "负偏移量夹到 0")
    }

    @Test
    fun ranking_tag_covers_all_home_categories() {
        // 首页六个分类各要有对应榜单，否则那一栏只能空着
        val expected = mapOf(
            "电影" to ("movie" to "热门"),
            "电视剧" to ("tv" to "热门"),
            "综艺" to ("tv" to "综艺"),
            "动漫" to ("tv" to "日本动画"),
            // 「短剧」用 mv 而非 tv：tv/短剧 实测返回 0 条，mv/短剧 有 20 条
            "短剧" to ("mv" to "短剧"),
            "记录片" to ("movie" to "纪录片"),
        )
        assertEquals(expected.keys, CmsSources.HOME_CATEGORIES.toSet(),
            "映射表必须与首页分类清单一一对应")
        expected.forEach { (cat, pair) ->
            assertEquals(pair, DoubanRankingTag.of(cat), "分类「$cat」的榜单映射错了")
        }
    }

    @Test
    fun ranking_tag_anime_avoids_blocked_endpoint() {
        // 动漫不能回到 new_search_subjects（2026-11-01 起被豆瓣按 IP 软风控）
        assertEquals("tv" to "日本动画", DoubanRankingTag.of("动漫"))
    }

    @Test
    fun ranking_tag_unknown_category_returns_null() {
        // 猜一个错的榜单比不显示更糟，故未知分类必须显式返回 null
        assertEquals(null, DoubanRankingTag.of("未知"))
        assertEquals(null, DoubanRankingTag.of(""))
    }
}
