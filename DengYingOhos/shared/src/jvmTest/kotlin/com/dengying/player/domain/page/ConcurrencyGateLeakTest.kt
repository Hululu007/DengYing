package com.dengying.player.domain.page

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 闸门在「排队中被取消」时不能漏空位。
 *
 * 真机症状：详情页开一次就再也回不来，之后**整个 App 的网络全停**——
 * 累计六次泄漏即可把 `OUTBOUND_LIMIT = 6` 全占满，且再也无法归还。
 */
class ConcurrencyGateLeakTest {

    /** 等到某个条件成立，最多等 `limitMs`；返回是否等到（不用 `while` 空转，避免条件永不成立时卡死用例） */
    private suspend fun awaitTrue(limitMs: Long = 2000, cond: suspend () -> Boolean): Boolean {
        return withTimeoutOrNull(limitMs) {
            while (!cond()) yield()
            true
        } ?: false
    }

    @Test
    fun cancelledWaiterDoesNotStealSlot() = runBlocking {
        val gate = ConcurrencyGate(1)
        assertTrue(gate.acquire(), "唯一空位应当先给到占位者")

        val waiter = launch(start = CoroutineStart.UNDISPATCHED) { gate.acquire() }
        assertTrue(awaitTrue { gate.pendingCount() == 1 }, "排队者应当已入队")
        waiter.cancel()
        waiter.join()

        gate.release()

        val got = withTimeoutOrNull(1000) { gate.acquire() }
        assertEquals(true, got, "排队者被取消后，归还的空位必须能被下一个请求拿到")
    }

    /**
     * 反复「排队 → 取消 → 归还」之后，闸门仍要能发空位。
     *
     * 关键在于每轮的排队者都必须**真的排进队列**（用 pendingCount 断言，
     * 而不是假定它一定进去了）；修复前 `active` 只增不减，八轮之后彻底发不出空位。
     */
    @Test
    fun manyCancellationsLeaveGateUsable() = runBlocking {
        val gate = ConcurrencyGate(2)
        assertTrue(gate.acquire())
        assertTrue(gate.acquire())

        repeat(8) { round ->
            val w = launch(start = CoroutineStart.UNDISPATCHED) { gate.acquire() }
            assertTrue(awaitTrue { gate.pendingCount() == 1 }, "第 $round 轮排队者应当已入队")
            w.cancel()
            w.join()
            assertEquals(0, gate.pendingCount(), "第 $round 轮取消后队列应当为空")
            gate.release()
            assertEquals(1, gate.activeCount(), "第 $round 轮归还后应只剩一个空位")
            assertTrue(gate.acquire(), "第 $round 轮应当能重新拿到空位")
        }

        gate.release()
        gate.release()
        val a = withTimeoutOrNull(1000) { gate.acquire() }
        assertEquals(true, a, "八次排队取消之后，闸门仍应能发空位")
    }
}
