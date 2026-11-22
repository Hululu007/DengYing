package com.dengying.player.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [percentDecode] 守卫测试（KMP P4 批 pages）
 *
 * ## 为什么这组测试的期望值不能写成「和 Uri.decode 一致」
 *
 * 迁移前 `Routes.dec()` 用的是 `android.net.Uri.decode`，而 P4 把 `Routes.kt`
 * 搬进 `:ui/commonMain` 后不能再依赖 `android.net.Uri`。当前 JVM actual 是纯 Kotlin
 * 实现（[percentDecode]），**今天**它与 `Uri.decode` 的差异不可能暴露 ——
 * 除非测试把差异点逐条钉成字面量期望值。
 *
 * 它防的是**未来**：P5 若为了省事把实现改成 `URLDecoder.decode`，下面
 * 前三条会立刻失败（`+` 变空格、非法转义抛异常、末尾裸 `%` 抛异常）。
 *
 * ## 期望值怎么来的
 *
 * 前两组按 `android.net.Uri.decode` 的实测行为写成字面量；第三组是往返性质，
 * 用 [UrlCodec.encode] 生成再解回，覆盖真实路由参数的字符集。
 */
class PercentCodecTest {

    /** Kotlin 字符串里 $ 是模板起始符，紧跟的字母会被当标识符，故用常量拼接 */
    private val DOLLAR = "$"


    // ---------- 基本解码 ----------

    @Test
    fun decodes_ascii_percent_escapes() {
        assertEquals("a b", percentDecode("a%20b"))
        assertEquals("a&b=c", percentDecode("a%26b%3Dc"))
        assertEquals("100%", percentDecode("100%25"))
    }

    @Test
    fun decodes_utf8_multibyte_sequences() {
        // 「流浪地球」= E6 B5 81 E6 B5 AA E5 9C B0 E7 90 83
        assertEquals("流浪地球", percentDecode("%E6%B5%81%E6%B5%AA%E5%9C%B0%E7%90%83"))
        // 非 BMP：U+1F600 的 UTF-8 是 F0 9F 98 80
        assertEquals("\uD83D\uDE00", percentDecode("%F0%9F%98%80"))
    }

    @Test
    fun mixed_literal_and_escaped_multibyte_decode_in_one_pass() {
        // 关键用例：%E6%B5%81 与紧跟的字面中文必须按同一字节流解码。
        // 若实现逐段解码再拼接，字面「球」会被单独 encode/decode 一次，
        // 虽然结果碰巧也对，但一旦跨段出现半个多字节序列就会各自崩成替换符。
        assertEquals("流球", percentDecode("%E6%B5%81%E7%90%83"))
        assertEquals("a流b球c", percentDecode("a%E6%B5%81b%E7%90%83c"))
        // 字面中文与转义中文混排：两侧都在同一字节流里，不能各自成段解
        assertEquals("甲流乙球丙", percentDecode("甲%E6%B5%81乙%E7%90%83丙"))
        assertEquals("甲流", percentDecode("甲%E6%B5%81"))
    }

    // ---------- 与 URLDecoder 的三处差异（防未来改错实现）----------

    @Test
    fun literal_plus_is_preserved_not_turned_into_space() {
        // URLDecoder.decode("a+b") = "a b"；Uri.decode 与这里都是 "a+b"。
        assertEquals("a+b", percentDecode("a+b"))
        assertEquals("a+b", percentDecode("a%2Bb"))
        assertEquals("a+b c", percentDecode("a+b%20c"))
    }

    @Test
    fun malformed_escape_keeps_literal_percent_and_does_not_throw() {
        // URLDecoder 对这三种输入都会抛 IllegalArgumentException。
        assertEquals("%ZZ", percentDecode("%ZZ"))
        assertEquals("100%", percentDecode("100%"))
        assertEquals("%4", percentDecode("%4"))
        assertEquals("%A", percentDecode("%%41"))
    }

    @Test
    fun lowercase_hex_is_accepted() {
        // 0x41='A' 0x4A='J' 0x61='a' 0x6A='j'
        assertEquals("A", percentDecode("%41"))
        assertEquals("J", percentDecode("%4A"))
        assertEquals("J", percentDecode("%4a"))
        assertEquals("j", percentDecode("%6a"))
    }

    @Test
    fun non_ascii_digits_are_not_treated_as_hex() {
        // 全角 Ｆ 不是十六进制字符 —— 不宽容解码，保留字面。
        assertEquals("%ＦＦ", percentDecode("%ＦＦ"))
    }

    // ---------- 边界 ----------

    @Test
    fun empty_input_returns_empty() {
        assertEquals("", percentDecode(""))
    }

    @Test
    fun text_without_escapes_is_unchanged() {
        assertEquals("hello 世界", percentDecode("hello 世界"))
        assertEquals("a/b?c=d&e=f#g", percentDecode("a/b?c=d&e=f#g"))
    }

    @Test
    fun lone_surrogate_becomes_replacement_char_not_crash() {
        // 孤立代理无法编成合法 UTF-8，encodeToByteArray 会产出替换符（EF BF BD）。
        // 不断言具体内容，只要求不抛且非空 —— 这是「不崩」契约。
        val out = percentDecode("\uD83D")
        assertTrue(out.isNotEmpty())
    }

    // ---------- 往返：真实路由参数 ----------

    @Test
    fun round_trip_with_encode_for_route_parameters() {
        val cases = listOf(
            "流浪地球2",
            "https://example.com/a/b?x=1&y=2#frag",
            "第1集" + DOLLAR + "https://cdn.example.com/v.m3u8",
            "a b+c",
            "100%",
            "",
            "粤语/国语 双音轨",
        )
        for (s in cases) {
            assertEquals(s, percentDecode(UrlCodec.encode(s)), "往返失败：$s")
        }
    }

    @Test
    fun round_trip_of_separator_joined_lists() {
        // Routes 用 \u0001 分隔数组；整串先 encode 再 decode，分隔符必须原样回来。
        val joined = listOf("https://a/1.m3u8", "https://b/2.m3u8").joinToString("\u0001")
        val back = percentDecode(UrlCodec.encode(joined))
        assertEquals(joined, back)
        assertEquals(2, back.split("\u0001").size)
    }
}
