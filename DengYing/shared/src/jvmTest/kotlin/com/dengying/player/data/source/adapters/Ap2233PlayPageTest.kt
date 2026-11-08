package com.dengying.player.data.source.adapters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 蓝光4k 播放页解析层守卫 —— 金样本驱动
 *
 * 样本：`shared/src/jvmTest/resources/golden/ap2233_playpage.txt`
 * （`_meta.json` 登记，2026-10-27 从 `index.php/vod/play/id/906/sid/1/nid/1.html`
 * 的完整响应里逐字节截取的 `var player_aaaa={…}` 块，715 B）。
 *
 * 覆盖四组：
 * 1. 金样本正向字段（线路名 / 加密级别 / 上下集 / 影片名 / 线路集序号）
 * 2. [Ap2233PlayPageParser.extractObject] 的花括号配平与字符串跳过
 * 3. [Ap2233PlayPageParser.unescape] 的 `uXXXX` / `%XX` / 非法序列
 * 4. [Ap2233PlayPageParser.decodeUrl] 的三级 encrypt 语义
 */
class Ap2233PlayPageTest {

    private fun golden(): String = Golden.text("ap2233_playpage")

    // ---------- 1. 金样本 ----------

    @Test
    fun golden_page_parses() {
        val p = Ap2233PlayPageParser.parse(golden())
        assertNotNull(p, "金样本必须能解析")
    }

    @Test
    fun golden_page_line_and_encrypt() {
        val p = Ap2233PlayPageParser.parse(golden())!!
        assertEquals("NBY", p.from, "线路名取 player_aaaa.from")
        assertEquals(Ap2233PlayPageParser.ENCRYPT_NONE, p.encrypt, "实测该页 encrypt 为 0")
        assertEquals("play", p.flag, "flag 取 play")
    }

    @Test
    fun golden_page_identity_fields() {
        val p = Ap2233PlayPageParser.parse(golden())!!
        assertEquals("906", p.vodId, "id 是字符串 906")
        assertEquals(1, p.sid)
        assertEquals(1, p.nid)
        assertEquals("名侦探柯南", p.vodName, "vod_data.vod_name")
    }

    @Test
    fun golden_page_url_is_nby_cipher() {
        val p = Ap2233PlayPageParser.parse(golden())!!
        assertTrue(p.url.startsWith(Ap2233PlayParser.PREFIX_NBY), "url 是 NBY-XMYAES 加密串")
        assertEquals(
            Ap2233PlayParser.TokenKind.Nby,
            Ap2233PlayParser.kindOf(p.url),
            "与识别层的判据必须一致",
        )
    }

    @Test
    fun golden_page_encrypt_none_passes_url_through() {
        val p = Ap2233PlayPageParser.parse(golden())!!
        assertEquals(p.url, p.decodedUrl, "encrypt=0 时 decodedUrl 必须与原始 url 完全相同")
        assertEquals(p.urlNext, p.decodedUrlNext)
    }

    @Test
    fun golden_page_next_episode_present() {
        val p = Ap2233PlayPageParser.parse(golden())!!
        assertTrue(p.urlNext.startsWith(Ap2233PlayParser.PREFIX_NBY), "url_next 同样是 NBY 加密串")
        assertTrue(p.url != p.urlNext, "本集与下一集不是同一串")
    }

    @Test
    fun golden_page_tail_hex_is_shared() {
        val p = Ap2233PlayPageParser.parse(golden())!!
        assertEquals(
            Ap2233PlayParser.tailHexOf(p.url),
            Ap2233PlayParser.tailHexOf(p.urlNext),
            "金样本里 url 与 url_next 的 | 尾部相同（这是 §15.2 记录过的站点特征）",
        )
    }

    @Test
    fun golden_page_extract_equals_direct_parse() {
        val raw = golden()
        val block = Ap2233PlayPageParser.extractObject(raw)!!
        assertEquals(
            Ap2233PlayPageParser.parse(raw),
            Ap2233PlayPageParser.parse(block),
            "整页与单独一块必须解析出同一结果",
        )
    }

    // ---------- 2. extractObject ----------

    @Test
    fun extract_null_and_blank() {
        assertNull(Ap2233PlayPageParser.extractObject(null))
        assertNull(Ap2233PlayPageParser.extractObject(""))
        assertNull(Ap2233PlayPageParser.extractObject("<html>no var here</html>"), "没有变量名返回 null")
    }

    @Test
    fun extract_missing_brace() {
        assertNull(
            Ap2233PlayPageParser.extractObject("var player_aaaa = ;"),
            "有变量名但后面没有 { 返回 null",
        )
    }

    @Test
    fun extract_unbalanced_returns_null() {
        assertNull(
            Ap2233PlayPageParser.extractObject("var player_aaaa={\"a\":1"),
            "括号未配平（页面被截断）返回 null",
        )
    }

    @Test
    fun extract_skips_nested_object() {
        val s = "var player_aaaa={\"a\":{\"b\":1},\"c\":2}"
        assertEquals("{\"a\":{\"b\":1},\"c\":2}", Ap2233PlayPageParser.extractObject(s))
    }

    @Test
    fun extract_skips_brace_inside_string() {
        val s = "var player_aaaa={\"a\":\"}\",\"b\":\"{\"}"
        assertEquals("{\"a\":\"}\",\"b\":\"{\"}", Ap2233PlayPageParser.extractObject(s), "字符串里的花括号不计深度")
    }

    @Test
    fun extract_handles_escaped_quote() {
        val s = "var player_aaaa={\"a\":\"x\\\"}y\",\"b\":1}"
        assertEquals(
            "{\"a\":\"x\\\"}y\",\"b\":1}",
            Ap2233PlayPageParser.extractObject(s),
            "转义引号不结束字符串",
        )
    }

    @Test
    fun extract_stops_at_first_balanced_end() {
        val s = "var player_aaaa={\"a\":1};var other={\"b\":2}"
        assertEquals("{\"a\":1}", Ap2233PlayPageParser.extractObject(s), "只取第一个配平块")
    }

    @Test
    fun extract_tolerates_spaces_around_equals() {
        val s = "var player_aaaa   =   {\"a\":1}"
        assertEquals("{\"a\":1}", Ap2233PlayPageParser.extractObject(s))
    }

    // ---------- 3. unescape ----------

    @Test
    fun unescape_no_percent_returns_same_instance_value() {
        assertEquals("abc", Ap2233PlayPageParser.unescape("abc"))
        assertEquals("", Ap2233PlayPageParser.unescape(""))
    }

    @Test
    fun unescape_utf8_bytes_to_chinese() {
        assertEquals("名侦探柯南", Ap2233PlayPageParser.unescape("%E5%90%8D%E4%BE%A6%E6%8E%A2%E6%9F%AF%E5%8D%97"))
    }

    @Test
    fun unescape_utf16_escape() {
        assertEquals("名", Ap2233PlayPageParser.unescape("%u540D"))
    }

    @Test
    fun unescape_mixed_forms() {
        assertEquals("a名b", Ap2233PlayPageParser.unescape("a%u540Db"))
        assertEquals("a名b", Ap2233PlayPageParser.unescape("a%E5%90%8Db"))
    }

 @Test
 fun unescape_plain_ascii_percent() {
  assertEquals("a b", Ap2233PlayPageParser.unescape("a%20b"))
  assertEquals("100%", Ap2233PlayPageParser.unescape("100%"), "孤立 % 原样保留")
 }

    @Test
    fun unescape_truncated_sequence_kept() {
        assertEquals("a%2", Ap2233PlayPageParser.unescape("a%2"), "截断的 %2 原样保留")
        assertEquals("a%u54", Ap2233PlayPageParser.unescape("a%u54"), "截断的 %u54 原样保留")
    }

    @Test
    fun unescape_invalid_hex_kept() {
        assertEquals("%zz", Ap2233PlayPageParser.unescape("%zz"), "非 hex 原样保留")
    }

    // ---------- 4. decodeUrl 三级语义 ----------

    @Test
    fun decode_none_is_identity() {
        assertEquals("NBY-XMYAESabc|def", Ap2233PlayPageParser.decodeUrl("NBY-XMYAESabc|def", 0))
        assertEquals(
            "NBY-XMYAESabc|def",
            Ap2233PlayPageParser.decodeUrl("NBY-XMYAESabc|def", 99),
            "未知 encrypt 值按原样处理",
        )
    }

    @Test
    fun decode_unescape_level() {
        assertEquals(
            "http://a/名.m3u8",
            Ap2233PlayPageParser.decodeUrl("http://a/%E5%90%8D.m3u8", 1),
        )
    }

    @Test
    fun decode_base64_level() {
        // "http://a/1.m3u8" 的 base64
        val b64 = "aHR0cDovL2EvMS5tM3U4"
        assertEquals("http://a/1.m3u8", Ap2233PlayPageParser.decodeUrl(b64, 2))
    }

    @Test
    fun decode_base64_then_unescape_order() {
        // "%E5%90%8D" (不补 padding) 的 base64
        val b64 = "JUU1JTkwJThE"
        assertEquals("名", Ap2233PlayPageParser.decodeUrl(b64, 2), "先 base64 再 unescape")
    }

    @Test
    fun decode_base64_invalid_returns_empty() {
        assertEquals("", Ap2233PlayPageParser.decodeUrl("!!!!", 2), "非法 base64 解出空串而非抛异常")
    }

    // ---------- 5. 容错 ----------

    @Test
    fun parse_returns_null_on_invalid_json() {
        assertNull(Ap2233PlayPageParser.parse("var player_aaaa={broken"), "非法 JSON 返回 null")
        assertNull(Ap2233PlayPageParser.parse("var player_aaaa=[1,2]"), "数组不是对象，返回 null")
    }

    @Test
    fun parse_missing_fields_use_zero_values() {
        val p = Ap2233PlayPageParser.parse("var player_aaaa={}")
        assertNotNull(p, "空对象也能解析")
        assertEquals("", p.from)
        assertEquals(Ap2233PlayPageParser.ENCRYPT_NONE, p.encrypt)
        assertEquals("", p.url)
        assertEquals(0, p.sid)
        assertEquals(0, p.nid)
    }

    @Test
    fun parse_missing_vod_data_is_empty_name() {
        val p = Ap2233PlayPageParser.parse("var player_aaaa={\"url\":\"x\",\"from\":\"NBY\"}")!!
        assertEquals("", p.vodName, "缺 vod_data 时影片名为空")
        assertEquals("NBY", p.from)
    }

    @Test
    fun parse_sid_nid_from_string_tolerated() {
        val p = Ap2233PlayPageParser.parse("var player_aaaa={\"sid\":\"3\",\"nid\":\"7\"}")!!
        assertEquals(3, p.sid, "站点偶尔下发字符串数字，按 JSON 宽松语义取到 3")
        assertEquals(7, p.nid)
    }
}
