package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSources
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.WebIdMode
import com.dengying.player.core.WebTokenPlayRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 金样本基准：两个BT（`ah_bttwo`）全链 —— 搜索 / 详情 / 播放
 *
 * 三份样本都是**真实上游响应**（`D:/workspace/dengY/.p3tmp/p154_record.py` 录制）：
 *  - `bttwo_search.txt` 40293 B：`GET /search?q=流浪地球` 原始 HTML，3 条
 *  - `bttwo_detail.txt` 105690 B：`GET /play/ch4q7lov1` 原始 HTML，14 集
 *  - `bttwo_play.txt` 291 B：`GET /video/play?...` 的真实 200 响应
 *
 * ## 为什么详情断言只看结构不看 userlink
 *
 * 播放页里的 `userlink` **每次请求都会变**（实测同一路径两次取到不同值），
 * 把它写进期望值等于把样本录制那一刻的会话固化成断言。故本文件只断言
 * 「能取到非空 userlink」，具体值由 `BttwoSignTest` 用固定输入钉住。
 *
 * ## dataid 的锚定方式
 *
 * 多集条目的播放页里有 14 个 `dataid`，只有 `href` 与当前页路径相同的那个是本集。
 * `bttwo_detail.txt` 是 `/play/ch4q7lov1` 的页面，故期望 `dataid = 40985`；
 * 同一份 HTML 用 `/play/ch4q7lovh` 去查必须得到 40986 —— 这条差分断言是本文件
 * 最重要的守卫：它证明锚定真的跟着页面路径走，而不是取第一个匹配。
 */
class BttwoGoldenTest {

    private val rule: WebTokenPlayRule by lazy {
        val src = CmsSources.byKey("ah_bttwo")
        assertNotNull(src, "源清单里应有 ah_bttwo")
        assertNotNull(src.rule, "ah_bttwo 应带 rule")
        assertNotNull(src.rule.tokenPlay, "ah_bttwo 应带 tokenPlay 规则")
        src.rule.tokenPlay
    }

    private val webRule = CmsSources.byKey("ah_bttwo")!!.rule!!

    // ---------------- 搜索层 ----------------

    @Test
    fun search_real_golden_returns_three_items() {
        val src = CmsSources.byKey("ah_bttwo")!!
        val r = WebParser.parseSearchHtml(src, Golden.text("bttwo_search"), webRule)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(3, r.items.size, "真实搜索样本应有 3 条")
        assertEquals(3, r.total)
        assertEquals(1, r.page)
        assertEquals(1, r.pageCount)
        assertEquals("两个BT", r.sourceName)
    }

    @Test
    fun search_items_have_slug_ids_not_zero() {
        // 关键回归：/play/ch2mo68f3 没有数字尾，用 trailingNumber 会全丢成 0 条
        val src = CmsSources.byKey("ah_bttwo")!!
        val r = WebParser.parseSearchHtml(src, Golden.text("bttwo_search"), webRule)
        for (it in r.items) {
            assertTrue(it.vod_id > 0, "slug 型 id 必须 > 0：${it.vod_name} -> ${it.vod_id}")
        }
    }

    @Test
    fun search_names_and_uris_are_real() {
        val src = CmsSources.byKey("ah_bttwo")!!
        val r = WebParser.parseSearchHtml(src, Golden.text("bttwo_search"), webRule)
        val names = r.items.map { it.vod_name }
        assertEquals(listOf("流浪地球", "流浪地球2", "流浪地球：飞跃2020特别版"), names)
        for (it in r.items) {
            assertTrue(
                it.vod_uri?.startsWith("https://www.bttwo.life/play/") == true,
                "详情链接应补成绝对地址：${it.vod_uri}",
            )
            assertEquals("ah_bttwo", it._source)
        }
    }

    @Test
    fun search_slug_id_is_stable_and_matches_manual_hash() {
        // 算法冻结守卫：改哈希算法会让老用户收藏全部失联
        val src = CmsSources.byKey("ah_bttwo")!!
        val r = WebParser.parseSearchHtml(src, Golden.text("bttwo_search"), webRule)
        val first = r.items.first { it.vod_uri!!.endsWith("/play/ch2mo68f3") }
        // id 是从**相对 href** 算的（`/play/ch2mo68f3`），不是绝对地址——两者哈希不同
        assertEquals(WebParser.slugId("/play/ch2mo68f3"), first.vod_id)
        assertEquals(first.vod_id, WebParser.slugId("/play/ch2mo68f3"))
    }

    @Test
    fun search_empty_html_is_no_data_not_failure() {
        val src = CmsSources.byKey("ah_bttwo")!!
        val r = WebParser.parseSearchHtml(src, "", webRule)
        assertEquals(RequestOutcome.SuccessNoData, r.outcome)
        assertEquals(0, r.items.size)
    }

    // ---------------- 详情层 ----------------

    @Test
    fun detail_real_golden_yields_fourteen_episodes() {
        val src = CmsSources.byKey("ah_bttwo")!!
        val item = WebParser.parseDetailHtml(src, Golden.text("bttwo_detail"), webRule, 0L)
        assertNotNull(item, "播放页应能解析出剧集")
        val url = item.vod_play_url ?: ""
        assertEquals(14, url.split("#").size, "样本是 14 集条目")
        assertEquals("线路1", item.vod_play_from)
        assertEquals("ah_bttwo", item._source)
    }

    @Test
    fun detail_episode_names_are_sequential_numbers() {
        val src = CmsSources.byKey("ah_bttwo")!!
        val item = WebParser.parseDetailHtml(src, Golden.text("bttwo_detail"), webRule, 0L)!!
        val labels = item.vod_play_url!!.split("#").map { it.substringBefore('$') }
        assertEquals((1..14).map { "第${it}集" }, labels, "该站剧集名是「第N集」")
    }

    @Test
    fun detail_episode_links_are_absolute_play_urls() {
        val src = CmsSources.byKey("ah_bttwo")!!
        val item = WebParser.parseDetailHtml(src, Golden.text("bttwo_detail"), webRule, 0L)!!
        for (one in item.vod_play_url!!.split("#")) {
            val href = one.substringAfter('$')
            assertTrue(href.startsWith("https://www.bttwo.life/play/"), "剧集链接应绝对化：$href")
        }
    }

    @Test
    fun detail_empty_html_is_null() {
        val src = CmsSources.byKey("ah_bttwo")!!
        assertNull(WebParser.parseDetailHtml(src, "", webRule, 0L))
    }

    // ---------------- 播放层：userlink / dataid 提取 ----------------

    @Test
    fun userlink_is_present_in_real_page() {
        val link = WebParser.userlinkOf(rule, Golden.text("bttwo_detail"))
        assertTrue(link.isNotEmpty(), "真实播放页应下发 userlink")
        assertTrue(link.length >= 16, "userlink 长度异常：$link")
    }

    @Test
    fun dataid_of_real_page_matches_its_own_path() {
        // bttwo_detail.txt 是 /play/ch4q7lov1 的页面
        val pageUrl = "https://www.bttwo.life/play/ch4q7lov1"
        assertEquals("40985", WebParser.dataIdOf(rule, Golden.text("bttwo_detail"), pageUrl))
    }

    @Test
    fun dataid_anchors_on_path_not_first_match() {
        // 同一份 HTML，换成第 2 集的路径必须得到第 2 集的 dataid
        // —— 若实现退回「取第一个 dataid」，这条会失败
        val sameHtml = Golden.text("bttwo_detail")
        assertEquals("40985", WebParser.dataIdOf(rule, sameHtml, "https://www.bttwo.life/play/ch4q7lov1"))
        assertEquals("40986", WebParser.dataIdOf(rule, sameHtml, "https://www.bttwo.life/play/ch4q7lovh"))
        assertEquals("40987", WebParser.dataIdOf(rule, sameHtml, "https://www.bttwo.life/play/ch4q7lovx"))
    }

    @Test
    fun dataid_of_unknown_path_is_empty() {
        // 路径不在页面上时必须返回空串（不许猜），调用方据此判「播放链未闭环」
        assertTrue(
            WebParser.dataIdOf(rule, Golden.text("bttwo_detail"), "https://www.bttwo.life/play/nosuch").isEmpty(),
        )
    }

    @Test
    fun slug_of_play_url_is_path_tail() {
        assertEquals("ch4q7lov1", WebParser.slugOf("https://www.bttwo.life/play/ch4q7lov1"))
        assertEquals("ch4q7lov1", WebParser.slugOf("https://www.bttwo.life/play/ch4q7lov1?x=1"))
        assertEquals("ch4q7lov1", WebParser.slugOf("https://www.bttwo.life/play/ch4q7lov1/"))
    }

    // ---------------- 播放层：换链响应解析 ----------------

    @Test
    fun play_response_yields_m3u8_url() {
        val r = WebParser.playFromTokenResponse(rule, Golden.text("bttwo_play"))
        assertNotNull(r, "真实 200 响应应解析出媒体地址")
        assertTrue(r.url.startsWith("https://"), "媒体地址应为绝对地址：${r.url}")
        assertTrue(r.url.contains(".m3u8"), "媒体地址应为 m3u8：${r.url}")
        assertEquals("", r.headers, "实测该 m3u8 不需要自定义请求头")
    }

    @Test
    fun play_response_with_non_ok_code_is_rejected() {
        // 站点在 401/403/404 时 HTTP 仍是 200，只靠状态码会把错误当成功
        val body = """{"code":401,"message":"请提供访问令牌","data":{"play_id":0,"quality_urls":[]}}"""
        assertNull(WebParser.playFromTokenResponse(rule, body))
    }

    @Test
    fun play_response_404_record_not_found_is_rejected() {
        val body = """{"code":404,"message":"record not found","data":{"play_id":0,"quality_urls":[]}}"""
        assertNull(WebParser.playFromTokenResponse(rule, body))
    }

    @Test
    fun play_response_missing_list_is_null() {
        val body = """{"code":200,"message":"OK","data":{"play_id":1}}"""
        assertNull(WebParser.playFromTokenResponse(rule, body))
    }

    @Test
    fun play_response_empty_list_is_null() {
        val body = """{"code":200,"message":"OK","data":{"play_id":1,"quality_urls":[]}}"""
        assertNull(WebParser.playFromTokenResponse(rule, body))
    }

    @Test
    fun play_response_blank_url_is_null() {
        val body = """{"code":200,"message":"OK","data":{"quality_urls":[{"url":"   "}]}}"""
        assertNull(WebParser.playFromTokenResponse(rule, body))
    }

    @Test
    fun play_response_non_json_is_null() {
        assertNull(WebParser.playFromTokenResponse(rule, "<html>not json</html>"))
    }

    @Test
    fun play_response_picks_first_quality() {
        val body = """{"code":200,"data":{"quality_urls":[
            {"bitrate":1080,"url":"https://cdn/a.m3u8"},
            {"bitrate":720,"url":"https://cdn/b.m3u8"}]}}"""
        assertEquals("https://cdn/a.m3u8", WebParser.playFromTokenResponse(rule, body)?.url)
    }

    // ---------------- id 提取方式 ----------------

    @Test
    fun id_mode_trailing_number_unchanged() {
        assertEquals(13691L, WebParser.idOf("/view/13691.html", WebIdMode.TrailingNumber))
        // 【陷阱】slug 链接在 TrailingNumber 下**不会得 0**，而是得到末位那个数字：
        // `/play/ch2mo68f3` → 3、`/play/ch2o8cy6q` → 6。既是错的又是正数，
        // 会被 `vodId > 0` 的过滤放行，两个不同条目还可能撞成同一个 id。
        // 这就是必须用 SlugHash 的原因，这条断言把陷阱写进测试。
        assertEquals(3L, WebParser.idOf("/play/ch2mo68f3", WebIdMode.TrailingNumber))
        assertEquals(6L, WebParser.idOf("/play/ch2o8cy6q", WebIdMode.TrailingNumber))
        assertEquals(0L, WebParser.idOf("/play/abcdefgh", WebIdMode.TrailingNumber))
    }

    @Test
    fun id_mode_slug_hash_handles_real_links() {
        for (u in listOf("/play/ch2mo68f3", "/play/ch0nvstnc", "/play/ch2o8cy6q")) {
            assertTrue(WebParser.idOf(u, WebIdMode.SlugHash) > 0, "slug 链接应有正 id：$u")
        }
    }

    @Test
    fun slug_id_of_empty_is_zero() {
        assertEquals(0L, WebParser.slugId(""))
    }

    @Test
    fun slug_hash_algorithm_is_frozen() {
        // 冻结值：改动哈希会让老用户收藏失联，这条断言就是那条警戒线
        assertEquals(461647865884208940L, WebParser.slugId("/play/ch2mo68f3"))
    }
}
