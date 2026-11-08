package com.dengying.player.ui.viewmodel

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SuggestDebouncer` 的防抖语义。
 *
 * 这组用例的存在理由：`loadSuggest` 原先靠 `viewModelScope`
 * （`Dispatchers.Main.immediate`）调度，在 `:ui:jvmTest` 里驱动不了，
 * 「取消上一次等待」那行只能靠读代码保证——而它恰恰是最容易在重构中
 * 悄悄丢掉的：丢了照样能跑，只是每个字符各发一次请求，请求数翻倍。
 *
 * `runTest` 提供虚拟时钟，`delay` 不真的等。
 */
class SuggestDebouncerTest {

    @Test
    fun `单次提交在延迟到点后才执行`() = runTest {
        val d = SuggestDebouncer(this, delayMs = 300)
        var ran = 0

        d.submit { ran++ }
        assertEquals(0, ran, "延迟未到不该执行")

        advanceTimeBy(299)
        assertEquals(0, ran, "差 1ms 仍未执行")

        advanceTimeBy(2)
        assertEquals(1, ran)
    }

    @Test
    fun `连续提交只有最后一次执行——这是防抖的全部意义`() = runTest {
        val d = SuggestDebouncer(this, delayMs = 300)
        val seen = mutableListOf<String>()

        // 模拟逐字输入「甄嬛传」：5 个字符，每个间隔 100ms（快于防抖窗口）
        for (kw in listOf("甄", "甄嬛", "甄嬛传")) {
            d.submit { seen += kw }
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(listOf("甄嬛传"), seen, "中间字符的请求必须被取消，只发最后一次")
    }

    @Test
    fun `停顿超过窗口则各自执行`() = runTest {
        val d = SuggestDebouncer(this, delayMs =300)
        val seen = mutableListOf<String>()

        d.submit { seen += "a" }
        advanceTimeBy(400)
        d.submit { seen += "b" }
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), seen, "两次输入间隔超过窗口，都该发")
    }

    @Test
    fun `恰好到点也执行——边界不偏一`() = runTest {
        val d = SuggestDebouncer(this, delayMs = 300)
        var ran = 0

        d.submit { ran++ }
        // advanceTimeBy 推进到该时刻但**不跑**该时刻排上的任务，
        // 所以还要 advanceUntilIdle 才会执行——不是它多睡了一毫秒
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(1, ran)
    }

    @Test
    fun `cancel 之后先前提交的不再执行`() = runTest {
        val d = SuggestDebouncer(this, delayMs = 300)
        var ran = 0

        d.submit { ran++ }
        advanceTimeBy(100)
        d.cancel()
        advanceUntilIdle()

        assertEquals(0, ran)
        assertFalse(d.pending, "取消后不应再挂着一个等待")
    }

    @Test
    fun `提交后 pending 为真，执行完转假`() = runTest {
        val d = SuggestDebouncer(this, delayMs = 300)

        d.submit { }
        assertTrue(d.pending)

        advanceUntilIdle()
        assertFalse(d.pending)
    }

    @Test
    fun `空的 block 也算执行过——不会卡在 pending`() = runTest {
        val d = SuggestDebouncer(this, delayMs = 300)
        var ran = false

        d.submit { ran = true }
        advanceUntilIdle()

        assertTrue(ran)
        assertFalse(d.pending)
    }

    @Test
    fun `耗时 block 期间新提交会取消旧的一次`() = runTest {
        val d = SuggestDebouncer(this, delayMs = 300)
        val order = mutableListOf<String>()

        val first = async {
            d.submit {
                kotlinx.coroutines.delay(1000)
                order += "first"
            }
        }
        first.await()
        advanceTimeBy(400) // 让第一个的 delay(300) 过去，block 内的 1000 还没跑完
        d.submit { order += "second" }
        advanceUntilIdle()

        assertEquals(listOf("second"), order, "在飞的那次即使已开始，也不该把旧结果写进去")
    }
}
