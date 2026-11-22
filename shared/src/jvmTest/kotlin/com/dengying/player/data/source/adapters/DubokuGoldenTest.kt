package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.SourceCaps
import com.dengying.player.core.SourceProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 独播库解析守卫（批 C-5）。
 *
 * 上游 `api.dbokutv.com` 2026-10-04 实测可用，所以这里钉的是真实金样本
 * （`duboku_search` / `duboku_detail` / `duboku_play`）加一批破损体。
 *
 * 期望值来源：先用 org.json 原实现（`git show 3067bcf`）与 DubokuParser
 * 做 16 组 parity 比对全绿，再把同一批输入固化成断言。
 *
 * 本批 parity 抓到过一条真实语义缺陷：JsonX.textOf 曾把 null 字面量映射成
 * 字符串 "null"，而 org.json 的 optString 对 null 返回 fallback。修正后才使
 * Actor 数组的 null 元素、以及 Director/ReleaseYear 为 null 的用例对齐。
 */
class DubokuGoldenTest {

    private val src = CmsSource(
        key = "ah_duboku",
        name = "独播库",
        api = "https://api.dbokutv.com",
        speed = 21,
        direct = false,
        enabled = true,
        protocol = SourceProtocol.Duboku,
        caps = SourceCaps(search = true, category = false, detail = true, token = true),
    )

    /** `dubokuUnscramble` 的逆运算：明文按 10 字符分组反转。 */
    private fun scramble(plain: String): String {
        val b64 = com.dengying.player.data.source.crypto.Digest.base64Encode(plain.encodeToByteArray())
        val noPad = b64.trimEnd('=')
        val sb = StringBuilder()
        var i = 0
        while (i < noPad.length) {
            val end = minOf(i + 10, noPad.length)
            sb.append(noPad.substring(i, end).reversed())
            i = end
        }
        return sb.toString()
    }

    private fun searchRow(extra: String): String {
        val did = scramble(extra)
        return "[{\"Name\":\"N\",\"DId\":\"" + did + "\"}]"
    }

    // ---------------- 真实金样本：搜索 ----------------

    @Test
    fun golden_search_shape() {
        val r = DubokuParser.parseSearch(src, Golden.text("duboku_search"))
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(2, r.items.size)
        assertEquals(2, r.total)
        assertEquals("独播库", r.sourceName)
    }

    @Test
    fun golden_search_items() {
        val items = DubokuParser.parseSearch(src, Golden.text("duboku_search")).items
        assertEquals(4570L, items[0].vod_id)
        assertEquals("庆余年 第二季", items[0].vod_name)
        assertEquals("全36集", items[0].vod_remarks)
        assertTrue(items[0].vod_pic.startsWith("https://static.dbokutv.com/image/"))
        assertEquals(300L, items[1].vod_id)
        assertEquals("庆余年", items[1].vod_name)
        assertTrue(items.all { it.vod_id > 0L })
    }

    // ---------------- 真实金样本：详情 ----------------

    @Test
    fun golden_detail_basics() {
        val it = DubokuParser.parseDetail(src, Golden.text("duboku_detail"), 4570L)!!
        assertEquals(4570L, it.vod_id)
        assertEquals("庆余年 第二季", it.vod_name)
        assertEquals("2024 导演:孙皓", it.vod_remarks)
        assertEquals("张若昀,李沁,陈道明,吴刚,郭麒麟,田雨,李小冉", it.vod_actor)
        assertTrue(it.vod_content!!.startsWith("《庆余年 第二季》"))
    }

    @Test
    fun golden_detail_playlist() {
        val it = DubokuParser.parseDetail(src, Golden.text("duboku_detail"), 4570L)!!
        assertEquals("独播库", it.vod_play_from)
        val eps = it.vod_play_url!!.split("#")
        assertEquals(36, eps.size)
        assertEquals("第1集" + "$" + "https://api.dbokutv.com/vodplay/4570-1-1", eps[0])
    }

    @Test
    fun golden_detail_rejects_non_positive_id() {
        assertNull(DubokuParser.parseDetail(src, Golden.text("duboku_detail"), 0L))
        assertNull(DubokuParser.parseDetail(src, Golden.text("duboku_detail"), -1L))
    }

    // ---------------- 真实金样本：换链 ----------------

    @Test
    fun golden_play_resolves_m3u8() {
        val r = DubokuParser.parsePlay(Golden.text("duboku_play"))
        assertEquals("https://vid.dbokutv.com/20240516/onRUDs6b/index.m3u8", r.url)
        assertEquals("", r.headers)
    }

    // ---------------- 搜索：破损体 ----------------

    @Test
    fun search_broken_bodies() {
        assertEquals(RequestOutcome.ParseFailed, DubokuParser.parseSearch(src, "{\"Name\":\"x\"}").outcome)
        assertEquals(RequestOutcome.ParseFailed, DubokuParser.parseSearch(src, "").outcome)
        assertEquals(RequestOutcome.ParseFailed, DubokuParser.parseSearch(src, null).outcome)
    }

    @Test
    fun search_empty_array_is_no_data() {
        val r = DubokuParser.parseSearch(src, "[]")
        assertEquals(RequestOutcome.SuccessNoData, r.outcome)
        assertEquals(0, r.items.size)
    }

    @Test
    fun search_skips_rows_without_id() {
        val r = DubokuParser.parseSearch(src, "[{\"Name\":\"A\"},{\"Name\":\"B\"}]")
        assertEquals(RequestOutcome.SuccessNoData, r.outcome)
        assertEquals(0, r.items.size)
    }

    @Test
    fun search_preserves_order() {
        val rows = (1..4).joinToString(",") { i ->
            "{\"Name\":\"N" + i + "\",\"DId\":\"" + scramble("/voddetail/" + i) + "\"}"
        }
        val r = DubokuParser.parseSearch(src, "[" + rows + "]")
        assertEquals(listOf(1L, 2L, 3L, 4L), r.items.map { it.vod_id })
    }

    // ---------------- 搜索：备注回退 ----------------

    @Test
    fun search_remark_shapes() {
        assertEquals("全12集", DubokuParser.parseSearch(src, searchRow("/voddetail/1")
            .replace("\"Name\":\"N\"", "\"Name\":\"N\",\"Tag\":\"全12集\",\"Rating\":7.5")).items[0].vod_remarks)
        assertEquals("8.5 分", DubokuParser.parseSearch(src, searchRow("/voddetail/1")
            .replace("\"Name\":\"N\"", "\"Name\":\"N\",\"Rating\":8.5")).items[0].vod_remarks)
        assertEquals("", DubokuParser.parseSearch(src, searchRow("/voddetail/1")).items[0].vod_remarks)
        assertEquals("", DubokuParser.parseSearch(src, searchRow("/voddetail/1")
            .replace("\"Name\":\"N\"", "\"Name\":\"N\",\"Tag\":\"\",\"Rating\":null")).items[0].vod_remarks)
    }

    // ---------------- 详情：破损体与字段形态 ----------------

    @Test
    fun detail_broken_bodies() {
        assertNull(DubokuParser.parseDetail(src, "[]", 1L))
        assertNull(DubokuParser.parseDetail(src, "", 1L))
        assertNull(DubokuParser.parseDetail(src, null, 1L))
    }

    @Test
    fun detail_null_actor_element_becomes_empty() {
        val vid = scramble("/vodplay/1-1-1")
        val body = "{\"Name\":\"N\",\"Actor\":[\"A\",null,\"C\"],\"Playlist\":[{\"EpisodeName\":\"E1\",\"VId\":\"" + vid + "\"}]}"
        assertEquals("A,,C", DubokuParser.parseDetail(src, body, 1L)!!.vod_actor)
    }

    @Test
    fun detail_actor_must_be_array() {
        val vid = scramble("/vodplay/1-1-1")
        val body = "{\"Name\":\"N\",\"Actor\":\"甲,乙\",\"Playlist\":[{\"EpisodeName\":\"E1\",\"VId\":\"" + vid + "\"}]}"
        assertEquals("", DubokuParser.parseDetail(src, body, 1L)!!.vod_actor)
    }

    @Test
    fun detail_remarks_shapes() {
        val vid = scramble("/vodplay/1-1-1")
        val pl = "\"Playlist\":[{\"EpisodeName\":\"E1\",\"VId\":\"" + vid + "\"}]"
        assertEquals("", DubokuParser.parseDetail(src, "{\"Name\":\"N\",\"ReleaseYear\":null,\"Director\":null," + pl + "}", 1L)!!.vod_remarks)
        assertEquals("2020", DubokuParser.parseDetail(src, "{\"Name\":\"N\",\"ReleaseYear\":2020," + pl + "}", 1L)!!.vod_remarks)
        assertEquals(" 导演:张三", DubokuParser.parseDetail(src, "{\"Name\":\"N\",\"Director\":\"张三\"," + pl + "}", 1L)!!.vod_remarks)
    }

    @Test
    fun detail_episode_name_falls_back_to_index() {
        val v1 = scramble("/vodplay/1-1-1")
        val v2 = scramble("/vodplay/1-1-2")
        val body = "{\"Name\":\"N\",\"Playlist\":[{\"VId\":\"" + v1 + "\"},{\"VId\":\"" + v2 + "\"}]}"
        val url = DubokuParser.parseDetail(src, body, 1L)!!.vod_play_url
        val d = "$"
        assertEquals("第1集" + d + "https://api.dbokutv.com/vodplay/1-1-1#第2集" + d + "https://api.dbokutv.com/vodplay/1-1-2", url)
    }

    @Test
    fun detail_playlist_skips_empty_vid() {
        val v2 = scramble("/vodplay/1-1-2")
        val body = "{\"Name\":\"N\",\"Playlist\":[{\"EpisodeName\":\"E1\",\"VId\":\"\"},{\"EpisodeName\":\"E2\",\"VId\":\"" + v2 + "\"}]}"
        val d = "$"
        assertEquals("E2" + d + "https://api.dbokutv.com/vodplay/1-1-2", DubokuParser.parseDetail(src, body, 1L)!!.vod_play_url)
    }

    @Test
    fun detail_empty_playlist_has_no_play_from() {
        val it = DubokuParser.parseDetail(src, "{\"Name\":\"N\",\"Playlist\":[]}", 1L)!!
        assertEquals("", it.vod_play_from)
        assertEquals("", it.vod_play_url)
    }

    // ---------------- 换链：破损体 ----------------

    @Test
    fun play_broken_bodies() {
        assertEquals("", DubokuParser.parsePlay("").url)
        assertEquals("", DubokuParser.parsePlay(null).url)
        assertEquals("", DubokuParser.parsePlay("[]").url)
        assertEquals("", DubokuParser.parsePlay("{\"HId\":null}").url)
        assertEquals("", DubokuParser.parsePlay("{\"HId\":\"\"}").url)
    }

    // ---------------- 工具函数 ----------------

    @Test
    fun trailing_number_paths() {
        assertEquals(4570L, DubokuParser.trailingNumber("/voddetail/4570"))
        assertEquals(4570L, DubokuParser.trailingNumber("4570"))
        assertEquals(0L, DubokuParser.trailingNumber(""))
        assertEquals(0L, DubokuParser.trailingNumber("/"))
        assertEquals(0L, DubokuParser.trailingNumber("/voddetail/abc"))
        assertEquals(42L, DubokuParser.trailingNumber("/voddetail/ 42 "))
        assertEquals(0L, DubokuParser.trailingNumber("trailing/"))
    }

    @Test
    fun resolve_target_shapes() {
        assertEquals("", DubokuParser.resolveTarget(src, ""))
        assertEquals("https://x/y", DubokuParser.resolveTarget(src, "https://x/y"))
        assertEquals("http://x/y", DubokuParser.resolveTarget(src, "http://x/y"))
        assertEquals("https://api.dbokutv.com/vodplay/1-1-1", DubokuParser.resolveTarget(src, "/vodplay/1-1-1"))
        assertEquals("https://api.dbokutv.com" + "vodplay/x", DubokuParser.resolveTarget(src, "vodplay/x"))
    }
}
