package com.dengying.player.domain.page

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrencyGatePriorityTest {

    private suspend fun awaitTrue(ms: Long = 2000, cond: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(ms) {
            while (!cond()) yield()
            true
        } ?: false

    @Test
    fun backgroundCannotTakeReservedSlots() = runBlocking {
        val gate = ConcurrencyGate(limit = 6, reservedForForeground = 2)
        assertEquals(4, gate.getBackgroundLimit())
        repeat(4) { assertTrue(gate.acquire(background = true)) }
        assertEquals(4, gate.activeBackgroundCount())

        val blocked = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.acquire(background = true)
        }
        assertTrue(awaitTrue { gate.pendingCount() == 1 })
        assertEquals(4, gate.activeCount())

        val fgOk = withTimeoutOrNull(500) { gate.acquire() }
        assertEquals(true, fgOk)

        blocked.cancel()
        gate.resetForTest()
    }

    @Test
    fun backgroundStaysQueuedWhileQuotaFull() = runBlocking {
        val gate = ConcurrencyGate(limit = 2, reservedForForeground = 1)
        assertTrue(gate.acquire(background = true))

        val bg = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.acquire(background = true)
        }
        assertTrue(awaitTrue { gate.pendingCount() == 1 })

        assertTrue(gate.acquire())
        gate.release()
        assertEquals(1, gate.pendingCount())

        gate.release(background = true)
        assertTrue(awaitTrue { gate.pendingCount() == 0 })

        bg.cancel()
        gate.resetForTest()
    }

    @Test
    fun foregroundSlotAvailableDuringBackgroundFlood() = runBlocking {
        val gate = ConcurrencyGate(limit = 3, reservedForForeground = 1)
        repeat(2) { assertTrue(gate.acquire(background = true)) }

        val jobs = (1..4).map {
            launch(start = CoroutineStart.UNDISPATCHED) {
                gate.acquire(background = true)
            }
        }
        assertTrue(awaitTrue { gate.pendingCount() == 4 })

        val fgOk = withTimeoutOrNull(500) { gate.acquire() }
        assertEquals(true, fgOk)

        jobs.forEach { it.cancel() }
        gate.resetForTest()
    }

    @Test
    fun zeroReservationKeepsLegacyBehavior() = runBlocking {
        val gate = ConcurrencyGate(limit = 2)
        assertEquals(2, gate.getBackgroundLimit())
        assertTrue(gate.acquire(background = true))
        assertTrue(gate.acquire(background = true))

        val third = withTimeoutOrNull(300) { gate.acquire(background = true) }
        assertEquals(null, third)

        gate.release(background = true)
        gate.resetForTest()
    }

    @Test
    fun mixedTiersNeverExceedLimit() = runBlocking {
        val gate = ConcurrencyGate(limit = 4, reservedForForeground = 2)
        gate.resetPeak()
        repeat(2) { assertTrue(gate.acquire(background = true)) }
        assertEquals(2, gate.activeBackgroundCount())
        repeat(2) { assertTrue(gate.acquire()) }
        assertEquals(4, gate.activeCount())
        assertTrue(gate.peak() <= 4)

        val overflow = withTimeoutOrNull(300) { gate.acquire() }
        assertEquals(null, overflow)

        gate.resetForTest()
    }

    @Test
    fun cancelledBackgroundWaiterDoesNotLeakSlot() = runBlocking {
        val gate = ConcurrencyGate(limit = 2, reservedForForeground = 1)
        assertTrue(gate.acquire(background = true))

        val w = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.acquire(background = true)
        }
        assertTrue(awaitTrue { gate.pendingCount() == 1 })

        w.cancel()
        w.join()
        assertEquals(0, gate.pendingCount())

        gate.release(background = true)
        assertEquals(0, gate.activeCount())

        val again = withTimeoutOrNull(500) { gate.acquire(background = true) }
        assertEquals(true, again)
        gate.resetForTest()
    }
}
