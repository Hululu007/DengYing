package com.dengying.player.domain.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 联调用`SearchSession` 在**联想**这条路径上的用法规格。
 *
 * 单测 `SearchSessionTest` 已经钉住了 `shouldAdopt` 的纯判定；这里补上
 * `SearchViewModel.loadSuggest` 里那两步**编排**的语义——
 * 它们本身无法在 `:ui:jvmTest` 里驱动（`viewModelScope` 依赖 Main dispatcher，
 * 见 16.22 的说明），所以把「编排应当成立的性质」提炼到这里。
 */
class SearchSessionSuggestFlowTest {

    @Test
    fun `连续输入时只有最后一个词的会话被采用`() {
        val s = SearchSession()
        // 用户逐字敲入「甄嬛传」，每个字符都开一次会话
        val tokens = "甄嬛传".map { s.begin("甄嬛传".takeWhileInclusive(it)) }

        val adopted = tokens.map { s.shouldAdopt(it, "甄嬛传".substring(0, tokens.indexOf(it) + 1)) }

        assertTrue(adopted.last(), "最后一个词的结果应当被采用")
        assertTrue(adopted.count { it } <= 1, "防抖/序号必须挡住中间字符的迟到结果")
    }

    @Test
    fun `用户清空输入时先开的会话不再被采用`() {
        val s = SearchSession()
        val token = s.begin("甄嬛")
        // 用户删光输入：loadSuggest 走空词分支，不开新会话，只清 suggest
        assertTrue(s.shouldAdopt(token, "甄嬛"), "此时仍是当前会话")

        // 用户重新输入另一个词
        val next = s.begin("如懿")
        assertFalse(s.shouldAdopt(token, "甄嬛"), "清空前那次请求迟到时必须丢弃")
        assertTrue(s.shouldAdopt(next, "如懿"))
    }

    @Test
    fun `两个独立会话互不干扰`() {
        // SearchViewModel 里主搜索与联想各持一个 session，可同时在飞。
        // 两者序号都从 1 起，所以不能靠"值不等"来证明独立——要证明的是
        // 一方的 begin 不会让另一方的结果被判过期。
        val search = SearchSession()
        val suggest = SearchSession()

        val searchToken = search.begin("甄嬛传")
        suggest.begin("甄")
        suggest.begin("甄嬛")

        assertTrue(search.shouldAdopt(searchToken, "甄嬛传"), "联想连开两次会话不得让主搜索的结果被判过期")
        assertTrue(suggest.shouldAdopt(suggest.current(), "甄嬛"))
    }
}

private fun String.takeWhileInclusive(c: Char): String =
    substring(0, indexOf(c) + 1)
