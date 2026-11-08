package com.dengying.player.domain.page

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * 「首批即渲染」判据的单测（NFR-701）。
 *
 * 这些函数决定两件用户可感知的事：
 *  1. 多久能看到内容（首批门槛）
 *  2. 并发抢得多凶（会不会占满闸门、拖慢用户下一步操作）
 *
 * 它们是纯函数，故可以穷举边界——这正是把它们抽到 domain 的理由。
 */
class ListBatchTest {

    // ---------------- 首批门槛 ----------------

    @Test
    fun `空结果不上屏`() {
        assertFalse(ListBatch.shouldEmitFirstBatch(mergedCount = 0, alreadyEmitted = false))
    }

    @Test
    fun `未达门槛不上屏`() {
        assertFalse(
            ListBatch.shouldEmitFirstBatch(
                mergedCount = ListBatch.FIRST_BATCH_MIN - 1,
                alreadyEmitted = false,
            )
        )
    }

    @Test
    fun `刚好达门槛就上屏`() {
        assertTrue(
            ListBatch.shouldEmitFirstBatch(
                mergedCount = ListBatch.FIRST_BATCH_MIN,
                alreadyEmitted = false,
            )
        )
    }

    @Test
    fun `已上屏过就不再重复上屏`() {
        // 关键约束：一次加载只允许两次呈现（首批 + 最终），
        // 否则列表会在几秒内反复重排，滚动位置一直在跳。
        assertFalse(
            ListBatch.shouldEmitFirstBatch(
                mergedCount = ListBatch.FULL_BATCH_TARGET,
                alreadyEmitted = true,
            )
        )
    }

    // ---------------- 停止条件 ----------------

    @Test
    fun `凑满目标条数就停`() {
        assertTrue(ListBatch.shouldStop(mergedCount = ListBatch.FULL_BATCH_TARGET, remainingTasks = 99))
    }

    @Test
    fun `任务拉完就停`() {
        // 该分类本身就少内容：不足 30 条也必须停，不能空转等
        assertTrue(ListBatch.shouldStop(mergedCount = 3, remainingTasks = 0))
    }

    @Test
    fun `还有任务且未满时继续`() {
        assertFalse(ListBatch.shouldStop(mergedCount = 3, remainingTasks = 5))
    }

    // ---------------- 动态并发 ----------------

    @Test
    fun `首批并发是闸门的一半`() {
        // 闸门 6 -> 首批 3，把另一半留给用户交互路径（搜索/详情）
        assertEquals(3, ListBatch.concurrencyFor(firstEmitted = false, gateLimit = 6))
    }

    @Test
    fun `闸门很小时首批仍有下限 2`() {
        assertEquals(2, ListBatch.concurrencyFor(firstEmitted = false, gateLimit = 2))
    }

    @Test
    fun `闸门很大时首批封顶 4`() {
        // 防止闸门调到 20 时首批一次发出 10 个请求、把源站打崩
        assertEquals(4, ListBatch.concurrencyFor(firstEmitted = false, gateLimit = 20))
    }

    @Test
    fun `首批上屏后回落到 2`() {
        assertEquals(2, ListBatch.concurrencyFor(firstEmitted = true, gateLimit = 6))
        assertEquals(2, ListBatch.concurrencyFor(firstEmitted = true, gateLimit = 20))
    }

    @Test
    fun `非法闸门值不会算出 0 并发`() {
        // 0 并发会让循环永久挂起；宁可退化也不能死循环
        assertEquals(2, ListBatch.concurrencyFor(firstEmitted = false, gateLimit = 0))
        assertEquals(2, ListBatch.concurrencyFor(firstEmitted = true, gateLimit = -1))
    }

    // ---------------- 滑动窗口 ----------------

    @Test
    fun `没有剩余任务时窗口为 0`() {
        // 0 是"别再发新任务"的信号。若返回 >0，外层会去取一个不存在的任务。
        assertEquals(0, ListBatch.windowFor(firstEmitted = false, gateLimit = 6, remaining = 0))
        assertEquals(0, ListBatch.windowFor(firstEmitted = false, gateLimit = 6, remaining = -3))
    }

    @Test
    fun `剩余任务少于窗口时按剩余数开`() {
        // 只剩 1 个任务却开 4 个窗口位：外层会空转等一个永远不会来的位置
        assertEquals(1, ListBatch.windowFor(firstEmitted = false, gateLimit = 6, remaining = 1))
        assertEquals(2, ListBatch.windowFor(firstEmitted = false, gateLimit = 6, remaining = 2))
    }

    @Test
    fun `剩余任务充足时窗口等于并发上限`() {
        assertEquals(
            ListBatch.concurrencyFor(firstEmitted = false, gateLimit = 6),
            ListBatch.windowFor(firstEmitted = false, gateLimit = 6, remaining = 100),
        )
    }

    @Test
    fun `首批上屏后窗口随之回落`() {
        // 这是"把闸门让给用户下一步操作"的落地点：上屏前 3，上屏后 2
        assertEquals(3, ListBatch.windowFor(firstEmitted = false, gateLimit = 6, remaining = 100))
        assertEquals(2, ListBatch.windowFor(firstEmitted = true, gateLimit = 6, remaining = 100))
    }

    @Test
    fun `窗口永不为负`() {
        for (gate in listOf(0, 1, 2, 6, 20)) {
            for (remaining in listOf(0, 1, 3, 50)) {
                for (emitted in listOf(false, true)) {
                    val w = ListBatch.windowFor(emitted, gate, remaining)
                    assertTrue(w >= 0, "gate=$gate remaining=$remaining emitted=$emitted → $w")
                }
            }
        }
    }

}
