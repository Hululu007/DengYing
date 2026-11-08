package com.dengying.player.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SearchSession`（FR-207 会话序号）的判定契约。
 *
 * 这个类存在的唯一理由：await 回来后判定「我是否仍是当前会话」。
 * 判错的两种后果都很显眼——旧词的结果覆盖新词（shouldAdopt 放行过头），
 * 或新词的结果被误弃（shouldAdopt 拦过头）。两条都在这里钉死。
 */
class SearchSessionTest {

    @Test
    fun `同序号同关键词才采用`() {
        val s = SearchSession()
        val token = s.begin("甄嬛传")
        assertTrue(s.shouldAdopt(token, "甄嬛传"))
    }

    @Test
    fun `关键词前后空格不影响判定`() {
        val s = SearchSession()
        val token = s.begin("甄嬛传")
        assertTrue(s.shouldAdopt(token, "  甄嬛传  "), "begin 与 shouldAdopt 两侧都该 trim")
    }

    @Test
    fun `旧会话的迟到结果被丢弃`() {
        val s = SearchSession()
        val old = s.begin("甄嬛传")
        val new = s.begin("如懿传")
        assertFalse(s.shouldAdopt(old, "甄嬛传"), "旧词的结果不得再上屏")
        assertTrue(s.shouldAdopt(new, "如懿传"))
    }

    @Test
    fun `序号相等但关键词不符也丢弃`() {
        val s = SearchSession()
        val token = s.begin("甄嬛传")
        // 中间发生过会话错位：序号没变但词已经不是那个词
        assertFalse(s.shouldAdopt(token, "如懿传"), "宁可丢弃，也不要把不匹配的结果显示出来")
    }

    @Test
    fun `每次 begin 都递增，同词重搜也算新会话`() {
        val s = SearchSession()
        val a = s.begin("甄嬛传")
        val b = s.begin("甄嬛传")
        assertEquals(a + 1, b, "用户可能想重试，同词也必须开新会话")
        assertFalse(s.isCurrent(a))
        assertTrue(s.isCurrent(b))
    }

    @Test
    fun `只有当前会话能结束`() {
        val s = SearchSession()
        val old = s.begin("甄嬛传")
        val new = s.begin("如懿传")

        assertFalse(s.finish(old), "旧会话迟到的结束回调不得生效")
        assertTrue(s.isRunning(), "新搜索还在跑，「搜索中」提示不能被旧会话提前关掉")

        assertTrue(s.finish(new))
        assertFalse(s.isRunning())
    }

    @Test
    fun `空关键词与同词有结果时跳过`() {
        val s = SearchSession()
        assertTrue(s.shouldSkip("", false))
        assertTrue(s.shouldSkip("   ", false), "纯空白等同空")

        s.begin("甄嬛传")
        assertTrue(s.shouldSkip("甄嬛传", hasResults = true), "连点不应重复发请求")
        assertFalse(s.shouldSkip("甄嬛传", hasResults = false), "没有结果时必须真的去搜")
        assertFalse(s.shouldSkip("如懿传", hasResults = true), "换词是明确意图，不能被跳过")
    }

    @Test
    fun `搜索进行中不因 isRunning 而跳过`() {
        val s = SearchSession()
        s.begin("甄嬛传")
        assertTrue(s.isRunning())
        // 改动前的实现会在这里返回 true，导致换词点了搜索没反应
        assertFalse(s.shouldSkip("如懿传", hasResults = false), "换词必须打断旧搜索、开新会话")
    }
}
