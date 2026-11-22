package com.dengying.player.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [UrlCodec] 守卫测试（KMP P3 批 F）
 *
 * ## 为什么需要这么多字节级断言
 *
 * 批 F 把 `java.net.URLEncoder` 换成了 expect/actual 门面。Android 侧的 actual
 * 现在就是转发到 `URLEncoder`，所以**今天**不可能有差异 —— 这组测试对当前代码
 * 是恒真的。
 *
 * 它防的是**未来**：P5 给 iOS 写 actual 时，Foundation 的
 * `addingPercentEncoding(withAllowedCharacters:)` 与 `URLEncoder` 的保留字符集
 * 并不一致。差异点至少有：
 *
 *  - `~`：`URLEncoder` 编成 `%7E`，RFC 3986 认为它是 unreserved 不该编码
 *  - `*`：`URLEncoder` **不编码**（与 `-` `_` `.` 同属保留原样的集合），
 *    而 Foundation 的 `urlQueryAllowed` 会保留、`urlPathAllowed` 不保留 —— 两端都可能不一致
 *  - `!` `'` `(` `)`：`URLEncoder` 全部编码，`CharacterSet.urlQueryAllowed` 保留一部分
 *  - 空格：`encodeForm` → `+`，`encode` → `%20`，两个函数不能混用
 *
 * 这些字节直接进服务端的签名计算与查询串，错一个字符就是「签名不匹配」。
 * 所以断言写成**逐字节的字面量期望值**，而不是「和 URLEncoder 一致」——
 * 后者在 iOS 上没法跑，等于没测。
 *
 * ## 期望值怎么来的
 *
 * 全部用 JVM 的 `java.net.URLEncoder.encode(s, "UTF-8")` 实测得出（与迁移前的
 * 线上行为逐字节相同），不是凭 RFC 手推。
 */
class UrlCodecTest {

    // ---------- encode：空格 → %20 ----------

    @Test
    fun encode_space_becomes_percent20_not_plus() {
        assertEquals("a%20b", UrlCodec.encode("a b"))
        assertEquals("a+b", UrlCodec.encodeForm("a b"))
    }

    @Test
    fun encode_chinese_is_utf8_percent() {
        // 「流浪地球」= E6 B5 81 E6 B5 AA E5 9C B0 E7 90 83
        assertEquals(
            "%E6%B5%81%E6%B5%AA%E5%9C%B0%E7%90%83",
            UrlCodec.encode("流浪地球"),
        )
        assertEquals("%E6%B5%81%E6%B5%AA%E5%9C%B0%E7%90%83", UrlCodec.encodeForm("流浪地球"))
    }

    @Test
    fun encode_keeps_unreserved_characters() {
        // 字母数字与 - _ . * 不编码（注意 * 在 URLEncoder 里确实不编码）
        assertEquals("abcXYZ012", UrlCodec.encode("abcXYZ012"))
        assertEquals("a-b_c.d*e", UrlCodec.encode("a-b_c.d*e"))
    }

    @Test
    fun encode_escapes_tilde_and_reserved() {
        // URLEncoder 把 ~ 编成 %7E（RFC 3986 认为不该编）—— 这是最容易被
        // 「更正确的实现」改坏的一处，所以单独钉住
        assertEquals("%7E", UrlCodec.encode("~"))
        assertEquals("%26", UrlCodec.encode("&"))
        assertEquals("%3D", UrlCodec.encode("="))
        assertEquals("%3F", UrlCodec.encode("?"))
        assertEquals("%23", UrlCodec.encode("#"))
        assertEquals("%2F", UrlCodec.encode("/"))
        assertEquals("%3A", UrlCodec.encode(":"))
        assertEquals("%2B", UrlCodec.encode("+"))
    }

    @Test
    fun encode_escapes_quotes_and_brackets() {
        assertEquals("%22", UrlCodec.encode("\""))
        assertEquals("%27", UrlCodec.encode("'"))
        assertEquals("%5B", UrlCodec.encode("["))
        assertEquals("%5D", UrlCodec.encode("]"))
        assertEquals("%7B", UrlCodec.encode("{"))
        assertEquals("%7D", UrlCodec.encode("}"))
        assertEquals("%7C", UrlCodec.encode("|"))
        assertEquals("%5C", UrlCodec.encode("\\"))
        assertEquals("%5E", UrlCodec.encode("^"))
        assertEquals("%60", UrlCodec.encode("`"))
    }

    @Test
    fun encode_escapes_control_and_non_ascii_punctuation() {
        assertEquals("%09", UrlCodec.encode("\t"))
        assertEquals("%0A", UrlCodec.encode("\n"))
        assertEquals("%0D", UrlCodec.encode("\r"))
        assertEquals("%00", UrlCodec.encode("\u0000"))
        // 全角标点（U+FF0C）也走 UTF-8 百分号
        assertEquals("%EF%BC%8C", UrlCodec.encode("，"))
        assertEquals("%E2%80%94", UrlCodec.encode("—"))
    }

    @Test
    fun encode_empty_string_is_empty() {
        assertEquals("", UrlCodec.encode(""))
        assertEquals("", UrlCodec.encodeForm(""))
    }

    // ---------- encodeForm：空格 → + ----------

    @Test
    fun encodeForm_uses_plus_for_space_and_percent2B_for_literal_plus() {
        // 这是 ShanzhaAdapter 的历史形态：空格进查询串是 +
        assertEquals("a+b", UrlCodec.encodeForm("a b"))
        assertEquals("a%2Bb", UrlCodec.encodeForm("a+b"))
    }

    @Test
    fun encodeForm_only_differs_from_encode_on_space() {
        // 除了空格，其余字符两函数必须完全一致 —— 这条能抓出「只改了一个分支」
        // 的实现错误
        val samples = listOf(
            "", " ", "a", "a b", "流浪地球", "~!*()'", "&=?#/:+", "中文 English 混合",
            "https://example.com/a/b?x=1&y=2", "\t\n\r", "100%",
        )
        for (s in samples) {
            val form = UrlCodec.encodeForm(s)
            val plain = UrlCodec.encode(s)
            assertEquals(
                form.replace("+", "%20"),
                plain,
                "form/plain 只在空格处应有差别，输入=<$s>",
            )
        }
    }

    // ---------- 组合场景 ----------

    @Test
    fun encode_round_trips_through_percent_decode() {
        // 用 java.net.URLDecoder 反解，证明 encode 的产物是合法百分号编码
        val samples = listOf("流浪地球", "a b", "a+b", "x&y=z", "https://e.com/p?q=1")
        for (s in samples) {
            val enc = UrlCodec.encode(s)
            val back = java.net.URLDecoder.decode(enc.replace("+", "%2B"), "UTF-8")
            assertEquals(s, back, "输入=<$s> 编码=<$enc>")
        }
    }

    @Test
    fun encode_matches_java_urlencoder_byte_for_byte() {
        // 兜底：对一批随机-ish 的字符逐一比对当前 JVM actual 与 URLEncoder。
        // iOS actual 写好后这条会失效（它只验 JVM），但那时上面的字面量断言
        // 仍然守着语义。
        val chars = " !\"#$%&'()*+,-./0123456789:;<=>?@AZ[\\]^_`az{|}~" +
            "流浪地球三体黑暗森林\u0000\t\n\r，—"
        for (c in chars) {
            val s = c.toString()
            val expected = java.net.URLEncoder.encode(s, "UTF-8")
            assertEquals(expected, UrlCodec.encodeForm(s), "encodeForm(<$s>)")
            assertEquals(expected.replace("+", "%20"), UrlCodec.encode(s), "encode(<$s>)")
        }
    }

    @Test
    fun encode_never_emits_plus_for_space_in_path_use() {
        // Routes.kt 用它拼 nav 路由，空格必须是 %20 不能是 +
        val s = "第 1 集"
        val enc = UrlCodec.encode(s)
        assertTrue('+' !in enc, "路径编码不该出现 +，实际=<$enc>")
        assertEquals("%E7%AC%AC%201%20%E9%9B%86", enc)
    }
}
