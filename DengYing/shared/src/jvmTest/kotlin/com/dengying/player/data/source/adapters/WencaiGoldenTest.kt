package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.SourceCaps
import com.dengying.player.core.SourceProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 文才守卫测试（批 C-4）。
 *
 * 与山楂不同，这一批**有真实金样本**（2026-10-04 实测上游可用）：
 * - `wencai_search.txt`：搜「流浪地球」，`code:200` / `totalCount:108` / 8 条
 * - `wencai_detail.txt`：`vodId=125224`，1 集，`nid=1050147`
 * - `wencai_play.txt`：`data.list` 3 条，第 0 条是 1080P m3u8
 *
 * 录制脚本用的签名与 `WencaiAdapter.signHeaders` 完全一致
 * （`SHA1(MD5("<参数串>&key=<KEY>&t=<ms>"))`），所以样本与线上真实响应同形。
 *
 * 时间戳随时间变化不影响样本内容（服务端只校验签名，不把 `t` 回写进响应）。
 */
class WencaiGoldenTest {

    private val src = CmsSource(
        key = "ah_wencai",
        name = "文才",
        api = "https://y2s52n7.com",
        speed = 20,
        direct = false,
        enabled = true,
        tier = 2,
        protocol = SourceProtocol.Wencai,
        caps = SourceCaps(search = true, category = false, detail = true, token = true),
    )

    /** 详情/换链样本对应的 vodId（录制时第一个搜索结果） */
    private val vodId = 125224L

    /** 详情样本里唯一一集的 nid */
    private val nid = 1050147L

    /** `$` 在 Kotlin 字符串里是模板起始符，紧跟中文会被当标识符；用常量隔离。 */
    private val D = "$"

    // ---------------- 搜索：金样本 ----------------

    @Test
    fun golden_search_outcome_and_counts() {
        val r = WencaiParser.parseSearch(src, Golden.text("wencai_search"))
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(8, r.items.size)
        // totalCount 是「全网命中数」，与当页条数无关
        assertEquals(108, r.total)
        assertEquals(1, r.page)
        assertEquals(1, r.pageCount)
        assertEquals("文才", r.sourceName)
        assertNull(r.err)
    }

    @Test
    fun golden_search_first_item_fields() {
        val it = WencaiParser.parseSearch(src, Golden.text("wencai_search")).items[0]
        assertEquals(vodId, it.vod_id)
        assertEquals("流浪地球", it.vod_name)
        assertEquals(
            "https://obs.3688baihuo.com/upload/movie/20240821/vod35267208.webp",
            it.vod_pic,
        )
        // 该片的 vodRemarks 是空串，不是 null
        assertEquals("", it.vod_remarks)
        assertEquals("ah_wencai", it._source)
    }

    @Test
    fun golden_search_all_ids_positive_and_ordered() {
        val ids = WencaiParser.parseSearch(src, Golden.text("wencai_search")).items.map { it.vod_id }
        assertEquals(8, ids.size)
        assertTrue(ids.all { it > 0 }, "所有 id 都应为正：$ids")
        // 服务端返回顺序原样保留（第一条就是 125224）
        assertEquals(vodId, ids[0])
    }

    @Test
    fun golden_search_tolerates_extra_fields() {
        // 搜索行有 27 个字段，解析只取 5 个；多出的字段不能引起失败
        // （Json 配置的 ignoreUnknownKeys 在这里不起作用——JsonX 是遍历式取值，
        //  这条测试只是钉住「多字段不影响」这一行为本身）
        val r = WencaiParser.parseSearch(src, Golden.text("wencai_search"))
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
    }

    // ---------------- 详情：金样本 ----------------

    @Test
    fun golden_detail_basic_fields() {
        val it = assertNotNull(
            WencaiParser.parseDetail(src, Golden.text("wencai_detail"), vodId),
        )
        assertEquals(vodId, it.vod_id)
        assertEquals("流浪地球", it.vod_name)
        assertEquals(
            "https://obs.3688baihuo.com/upload/movie/20240821/vod35267208.webp",
            it.vod_pic,
        )
        assertEquals("吴京,刘德华,李雪健,沙溢,宁", it.vod_actor)
        assertEquals("", it.vod_remarks)
        assertEquals("ah_wencai", it._source)
    }

    @Test
    fun golden_detail_content_is_html_stripped() {
        val it = assertNotNull(
            WencaiParser.parseDetail(src, Golden.text("wencai_detail"), vodId),
        )
        val c = it.vod_content ?: ""
        assertTrue(c.isNotEmpty(), "简介不应为空")
        assertTrue(!c.contains("<p>"), "不应残留 <p>：${c.take(60)}")
        assertTrue(!c.contains("</p>"), "不应残留 </p>：${c.take(60)}")
        // 原文以「在并不遥远的未来」开头
        assertTrue(c.startsWith("在并不遥远的未来"), "开头不符：${c.take(40)}")
    }

    @Test
    fun golden_detail_single_episode_shape() {
        val it = assertNotNull(
            WencaiParser.parseDetail(src, Golden.text("wencai_detail"), vodId),
        )
        assertEquals("文采蓝光", it.vod_play_from)
        // 该片只有 1 集，集名是「蓝光」，token 是纯数字 nid
        assertEquals("蓝光${D}${nid}", it.vod_play_url)
    }

    // ---------------- 换链：金样本 ----------------

    @Test
    fun golden_play_picks_first_url() {
        val r = WencaiParser.parsePlay(Golden.text("wencai_play"))
        assertTrue(r.url.startsWith("https://ppvod01.kqgfbs.com/splitOut/"), "实际：${r.url}")
        assertTrue(r.url.contains("/index.m3u8?"), "实际：${r.url}")
        // 文才的 m3u8 是直连，不需要请求头
        assertEquals("", r.headers)
    }

    @Test
    fun golden_play_url_has_no_surrounding_space() {
        val r = WencaiParser.parsePlay(Golden.text("wencai_play"))
        assertEquals(r.url.trim(), r.url)
    }

    @Test
    fun golden_play_uses_first_of_three_resolutions() {
        // 样本里 list 有 3 条（1080/720/…），原实现固定取 [0]
        val r = WencaiParser.parsePlay(Golden.text("wencai_play"))
        assertTrue(r.url.contains("1043397"), "应取第 0 条（1080P），实际：${r.url}")
    }

    // ---------------- nid 双态 ----------------

    @Test
    fun nid_accepts_number_and_string() {
        assertEquals("1050147", nidIn("""{"nid":1050147}"""))
        assertEquals("1050147", nidIn("""{"nid":"1050147"}"""))
    }

    @Test
    fun nid_truncates_float_like_org_json_number_branch() {
        // 原实现 `is Number -> toLong().toString()`：1234.9 → "1234"，
        // 若误用 textOf 会得到 "1234.9"。这是本批唯一需要显式处理的语义差。
        assertEquals("1234", nidIn("""{"nid":1234.9}"""))
    }

    @Test
    fun nid_null_and_missing_are_empty() {
        assertEquals("", nidIn("""{"nid":null}"""))
        assertEquals("", nidIn("""{}"""))
    }

    @Test
    fun nid_token_strips_absolute_url_prefix() {
        assertEquals("", WencaiParser.nidOfToken(""))
        assertEquals("1050147", WencaiParser.nidOfToken("1050147"))
        assertEquals(
            "abc123",
            WencaiParser.nidOfToken("https://example.com/play/abc123"),
        )
        // 以 http 开头但只有域名时，最后一个 / 之后是域名本身（非空），不回落到原串
        assertEquals(
            "example.com",
            WencaiParser.nidOfToken("https://example.com"),
        )
    }

    // ---------------- 详情：多集与集名回退 ----------------

    @Test
    fun detail_episode_name_fallbacks() {
        val body = """
            {"code":200,"data":{"vodName":"X","episodeList":[
              {"nid":11,"name":"第一集"},
              {"nid":22,"title":"甲"},
              {"nid":33}
            ]}}
        """.trimIndent()
        val it = assertNotNull(WencaiParser.parseDetail(src, body, 1L))
        assertEquals(
            "第一集${D}11#甲${D}22#第3集${D}33",
            it.vod_play_url,
        )
    }

    @Test
    fun detail_skips_episodes_without_nid() {
        val body = """
            {"code":200,"data":{"vodName":"X","episodeList":[
              {"name":"无 nid"},
              {"nid":null,"name":"null nid"},
              {"nid":0,"name":"零"},
              {"nid":7,"name":"有"}
            ]}}
        """.trimIndent()
        val it = assertNotNull(WencaiParser.parseDetail(src, body, 1L))
        // nid=0 会经 longOf 变成 "0"（非空）→ 保留；只有缺失与 null 被跳过
        assertEquals("零${D}0#有${D}7", it.vod_play_url)
    }

    @Test
    fun detail_without_episodes_returns_empty_play_fields() {
        val body = """{"code":200,"data":{"vodName":"X","episodeList":[]}}"""
        val it = assertNotNull(WencaiParser.parseDetail(src, body, 1L))
        assertEquals("", it.vod_play_from)
        assertEquals("", it.vod_play_url)
    }

    @Test
    fun detail_returns_null_on_bad_code_or_missing_data() {
        assertNull(WencaiParser.parseDetail(src, """{"code":900404}""", 1L))
        assertNull(WencaiParser.parseDetail(src, """{"code":200}""", 1L))
        assertNull(WencaiParser.parseDetail(src, "not json", 1L))
        assertNull(WencaiParser.parseDetail(src, null, 1L))
    }

    // ---------------- 搜索：破损与四态 ----------------

    @Test
    fun search_fails_on_bad_code_with_message() {
        val r = WencaiParser.parseSearch(src, """{"code":500}""")
        assertEquals(RequestOutcome.RequestFailed, r.outcome)
        assertEquals("code 500", r.err)
        assertTrue(r.items.isEmpty())
    }

    @Test
    fun search_fails_when_body_is_not_json() {
        for (body in listOf(null, "", "not json", "<html>")) {
            val r = WencaiParser.parseSearch(src, body)
            assertEquals(RequestOutcome.RequestFailed, r.outcome, "body=$body")
            assertEquals("响应非 JSON", r.err, "body=$body")
        }
    }

    @Test
    fun search_missing_code_defaults_to_200() {
        // optInt("code", 200)：缺字段按成功处理
        val body = """{"data":{"result":{"list":[{"vodId":1,"vodName":"A"}]}}}"""
        val r = WencaiParser.parseSearch(src, body)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(1, r.items.size)
    }

    @Test
    fun search_empty_list_is_success_no_data() {
        val body = """{"code":200,"data":{"result":{"totalCount":0,"list":[]}}}"""
        val r = WencaiParser.parseSearch(src, body)
        assertEquals(RequestOutcome.SuccessNoData, r.outcome)
        assertEquals(0, r.total)
    }

    @Test
    fun search_missing_result_block_degrades_to_empty() {
        // optJSONObject 类型不符返回 null → 容器链落空 → 空列表，不抛
        for (body in listOf(
            """{"code":200}""",
            """{"code":200,"data":{}}""",
            """{"code":200,"data":{"result":{}}}""",
            """{"code":200,"data":{"result":"str"}}""",
        )) {
            val r = WencaiParser.parseSearch(src, body)
            assertEquals(RequestOutcome.SuccessNoData, r.outcome, "body=$body")
            assertEquals(0, r.total, "body=$body")
        }
    }

    @Test
    fun search_drops_rows_with_non_positive_id() {
        val body = """
            {"code":200,"data":{"result":{"list":[
              {"vodId":0,"vodName":"zero"},
              {"vodId":-1,"vodName":"neg"},
              {"vodName":"missing"},
              {"vodId":9,"vodName":"ok"}
            ]}}}
        """.trimIndent()
        val r = WencaiParser.parseSearch(src, body)
        assertEquals(listOf("ok"), r.items.map { it.vod_name })
    }

    @Test
    fun search_accepts_numeric_string_id() {
        val body = """{"code":200,"data":{"result":{"list":[{"vodId":"12","vodName":"A"}]}}}"""
        val r = WencaiParser.parseSearch(src, body)
        assertEquals(12L, r.items.single().vod_id)
    }

    @Test
    fun search_total_falls_back_to_item_count() {
        val body = """
            {"code":200,"data":{"result":{"list":[
              {"vodId":1,"vodName":"A"},{"vodId":2,"vodName":"B"}
            ]}}}
        """.trimIndent()
        assertEquals(2, WencaiParser.parseSearch(src, body).total)
    }

    // ---------------- 换链：破损 ----------------

    @Test
    fun play_returns_empty_on_malformed_bodies() {
        for (body in listOf(
            null,
            "",
            "not json",
            """{"code":200}""",
            """{"code":200,"data":{}}""",
            """{"code":200,"data":{"list":[]}}""",
            """{"code":200,"data":{"list":"str"}}""",
        )) {
            assertEquals("", WencaiParser.parsePlay(body).url, "body=$body")
        }
    }

    // ---------------- 去 HTML ----------------

    @Test
    fun strip_html_handles_br_and_p() {
        assertEquals("a\nb", WencaiParser.stripHtml("a<br>b"))
        assertEquals("a\nb", WencaiParser.stripHtml("a<br/>b"))
        assertEquals("a\nb", WencaiParser.stripHtml("a<br />b"))
        assertEquals("a\nb", WencaiParser.stripHtml("a</p><p>b".replace("<p>", "")))
    }

    @Test
    fun strip_html_removes_other_tags_and_decodes_entities() {
        assertEquals("粗体", WencaiParser.stripHtml("<b>粗体</b>"))
        assertEquals("a b", WencaiParser.stripHtml("a&nbsp;b"))
        assertEquals("a&b", WencaiParser.stripHtml("a&amp;b"))
        assertEquals("<x>", WencaiParser.stripHtml("&lt;x&gt;"))
        assertEquals("\"q\"", WencaiParser.stripHtml("&quot;q&quot;"))
    }

    @Test
    fun strip_html_is_case_insensitive() {
        assertEquals("a\nb", WencaiParser.stripHtml("a<BR>b"))
        // 单独的 </P> 只有一个换行且落在末尾，被 trim() 吃掉
        assertEquals("a", WencaiParser.stripHtml("a</P>"))
        assertEquals("a\nb", WencaiParser.stripHtml("a<BR/>b"))
        // 注意：`</p>`→"\n" 与 `<br/>`→"\n" 是两次独立替换，相邻时会得到两个换行
        assertEquals("a\n\nb", WencaiParser.stripHtml("a</P><BR/>b"))
        assertEquals("a b", WencaiParser.stripHtml("a&NBSP;b"))
    }

    @Test
    fun strip_html_collapses_blank_lines_and_trims() {
        assertEquals("a\n\nb", WencaiParser.stripHtml("  a\n\n\n\nb  "))
        assertEquals("", WencaiParser.stripHtml("   "))
    }

    // ---------------- 小工具 ----------------

    private fun nidIn(row: String): String {
        val body = """{"code":200,"data":{"vodName":"X","episodeList":[$row]}}"""
        val it = WencaiParser.parseDetail(src, body, 1L) ?: return "<null>"
        // play_url 形如 "集名$nid"，取最后一个 $ 之后
        return (it.vod_play_url ?: "").substringAfterLast(D)
    }
}
