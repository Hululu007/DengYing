package com.dengying.player.data.source.adapters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 两个BT 播放链签名复刻（[BttwoSign]）金样本校验
 *
 * 金样本 `golden/bttwo_sign.txt` 由 wasm 模块本体（`nbmovie.wasm`，
 * 导出 `build_play_url`）在 Node 中以固定 `Date.now` 跑出，是**外部权威输出**，
 * 不是本实现自证。本测试逐行复算，一旦 [Bttwo] 的算法、密钥、
 * 消息顺序、时间戳表示或 HMAC 分组有任何偏差都会失败。
 */
class BttwoSignTest {

    private val lines: List<String> by lazy {
        Golden.text("bttwo_sign")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun signRows(): List<List<String>> =
        lines.map { it.split(" ") }.filter { it.size == 6 }

    private fun tokenRows(): List<List<String>> =
        lines.map { it.split(" ") }.filter { it.size == 2 }

    @Test
    fun golden_file_well_formed() {
        assertTrue(signRows().size >= 48, "签名样本应 >=48 行，实际 ${signRows().size}")
        assertTrue(tokenRows().size >= 4, "token 样本应 >=4 行，实际 ${tokenRows().size}")
        for (r in signRows()) {
            assertEquals(32, r[5].length, "s 应为 32：$r")
            assertTrue(r[5].all { it in "0123456789abcdef" }, "s 应为小写 hex：$r")
        }
    }

    @Test
    fun sign_matches_wasm_for_every_row() {
        val rows = signRows()
        assertTrue(rows.isNotEmpty())
        for (r in rows) {
            val dataId = r[0]
            val v = r[1]
            val t = r[2]
            val expected = r[5]
            assertEquals(expected, BttwoSign.sign(dataId, t, v), "p=$dataId v=$v t=$t")
        }
    }

    @Test
    fun token_matches_wasm_for_every_row() {
        val rows = tokenRows()
        assertTrue(rows.isNotEmpty())
        for (r in rows) {
            assertEquals(r[1], BttwoSign.token(r[0]), "userlink=${r[0]}")
        }
    }

    @Test
    fun token_of_real_userlink() {
        assertEquals(
            "NlM7Oi4sP1dmRW05JDJGIzcMBk4nNwE2IFlQUQ==",
            BttwoSign.token("X1VUXEZeVwYJAQ4FCgc7IUlYV05c"),
        )
    }

    @Test
    fun token_of_empty_userlink_uses_zero() {
        assertEquals(BttwoSign.token("0"), BttwoSign.token(""))
    }

    @Test
    fun sign_is_independent_of_quality() {
        val p = "24089"
        val v = "ch2mo68f3"
        val t = "1793030533833"
        val base = BttwoSign.sign(p, t, v)
        assertEquals("ac95f9e713eca8d48af834799d967002", base)
        val u1 = BttwoSign.playUrl("", p, v, "1080", t.toLong(), "X1VUXEZeVwYJAQ4FCgc7IUlYV05c")
        val u2 = BttwoSign.playUrl("", p, v, "720", t.toLong(), "X1VUXEZeVwYJAQ4FCgc7IUlYV05c")
        assertTrue(u1.contains("s=$base"), u1)
        assertTrue(u2.contains("s=$base"), u2)
        assertTrue(u1.contains("q=1080"), u1)
        assertTrue(u2.contains("q=720"), u2)
    }

    @Test
    fun quality_4k_normalised_to_1() {
        val u = BttwoSign.playUrl("", "1", "a", "4k", 1L, "0")
        assertTrue(u.contains("q=1"), u)
        assertTrue(!u.contains("4k"), u)
    }

    @Test
    fun play_url_shape() {
        val u = BttwoSign.playUrl(
            "https://www.bttwo.life", "24089", "ch2mo68f3", "1080",
            1793030533833L, "X1VUXEZeVwYJAQ4FCgc7IUlYV05c",
        )
        assertTrue(u.startsWith("https://www.bttwo.life/video/play?"), u)
        assertTrue(u.contains("p=24089"), u)
        assertTrue(u.contains("v=ch2mo68f3"), u)
        assertTrue(u.contains("q=1080"), u)
        assertTrue(u.contains("s=ac95f9e713eca8d48af834799d967002"), u)
        assertTrue(u.contains("t=1793030533833"), u)
        assertTrue(u.endsWith("k=NlM7Oi4sP1dmRW05JDJGIzcMBk4nNwE2IFlQUQ=="), u)
    }

    @Test
    fun message_order_is_sensitive() {
        val a = BttwoSign.sign("1", "2", "3")
        assertEquals(32, a.length)
        assertTrue(a != BttwoSign.sign("3", "2", "1"), "消息顺序应影响结果")
    }

    @Test
    fun leading_zero_changes_result() {
        val a = BttwoSign.sign("1", "179303053383", "a")
        val b = BttwoSign.sign("1", "01793030533833", "a")
        assertTrue(a != b, "前导零应改变签名")
    }
}
