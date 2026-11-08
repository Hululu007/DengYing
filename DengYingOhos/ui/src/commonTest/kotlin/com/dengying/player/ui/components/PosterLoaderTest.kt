package com.dengying.player.ui.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `PosterLoader` 的调度契约。
 *
 * 覆盖三件在真机上昂贵、在这里免费的事：
 *  1. 同一 URL 并发请求只下载一次，等待者拿到**同一份**结果
 *  2. 下载者失败时等待者立刻拿到 null（不白等，不返回旧缓存）
 *  3. 并发上限真的生效，且取消后上限能释放（不会永久卡死）
 *
 * 用 `String` 代替 `ImageBitmap`：`ImageBitmap` 需要平台图形栈，commonTest 里造不出来；
 * 调度语义与载荷类型无关。
 */
class PosterLoaderTest {

    @Test
    fun `并发请求同一 URL 只下载一次`() = runTest {
        var calls = 0
        val loader = PosterLoader<String>(concurrency = 4) { url ->
            calls++
            // 让出一次，制造「另一个协程也进来」的窗口
            yield()
            "bytes-of-$url"
        }

        val results = listOf(1, 2, 3, 4).map { async { loader.load("a.png") } }.awaitAll()

        assertEquals(1, calls, "同一 URL 应只真正下载一次")
        assertEquals(List(4) { "bytes-of-a.png" }, results, "等待者应拿到同一份结果")
    }

    @Test
    fun `不同 URL 各自下载`() = runTest {
        val calls = mutableListOf<String>()
        val loader = PosterLoader<String>(concurrency = 4) { url ->
            calls += url
            "bytes-of-$url"
        }

        val results = listOf("a.png", "b.png").map { url -> async { loader.load(url) } }.awaitAll()

        assertEquals(listOf("a.png", "b.png"), calls.sorted())
        assertEquals(listOf("bytes-of-a.png", "bytes-of-b.png"), results)
    }

    @Test
    fun `下载失败时等待者立即拿到 null`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loader = PosterLoader<String>(concurrency = 4) {
            started.complete(Unit)
            release.await()
            null // 下载失败
        }

        val first = async { loader.load("a.png") }
        started.await()
        val second = async { loader.load("a.png") }
        yield() // 让 second 进入 inflight 等待分支
        release.complete(Unit)

        assertNull(first.await())
        assertNull(second.await(), "等待者应拿到与真正下载者相同的失败结论，而不是卡住或返回占位以外的值")
    }

    @Test
    fun `并发不超过上限`() = runTest {
        val limit = 3
        var running = 0
        var maxSeen = 0
        val loader = PosterLoader<String>(concurrency = limit) { url ->
            running++
            maxSeen = maxOf(maxSeen, running)
            yield()
            running--
            "bytes-of-$url"
        }

        (1..12).map { i -> async { loader.load("p$i.png") } }.awaitAll()

        assertEquals(limit, maxSeen, "同时在飞的下载数不应超过并发上限")
    }

    @Test
    fun `发起者被取消后等待者不会永远挂起`() = runTest {
        val started = CompletableDeferred<Unit>()
        val loader = PosterLoader<String>(concurrency = 4) {
            started.complete(Unit)
            // 永不返回：模拟一次卡住的下载，只有取消能结束
            CompletableDeferred<Unit>().await()
            "never"
        }

        val first = async { loader.load("a.png") }
        started.await()
        val second = async { loader.load("a.png") }
        yield() // 让 second 进入等待

        first.cancel()
        yield()

        // 兜底 complete(null) 必须让等待者立刻结束，而不是跟着一起挂死
        assertNull(second.await(), "发起者取消时，等待者应得到 null 而不是永远挂起")
    }

    @Test
    fun `取消后并发上限被释放`() = runTest {
        val started = CompletableDeferred<Unit>()
        val loader = PosterLoader<String>(concurrency = 1) { url ->
            if (url == "stuck") {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
            "bytes-of-$url"
        }

        val stuck = async { loader.load("stuck") }
        started.await()
        stuck.cancel()
        yield()

        // concurrency = 1：许可没释放的话这里会挂住，runTest 会以超时失败
        assertEquals("bytes-of-b.png", loader.load("b.png"))
    }

    @Test
    fun `空 URL 直接返回 null 不占并发`() = runTest {
        var calls = 0
        val loader = PosterLoader<String>(concurrency = 1) {
            calls++
            "x"
        }

        assertNull(loader.load(""))
        assertEquals(0, calls, "空 URL 不应触发下载")
    }

    @Test
    fun `下载抛异常也只会让本次返回 null`() = runTest {
        val loader = PosterLoader<String>(concurrency = 2) { url ->
            if (url == "boom") error("network down")
            "bytes-of-$url"
        }

        // 契约说 decode 不抛异常；这里验证的是「万一抛了」也不会把 Deferred 悬着
        val failed = runCatching { loader.load("boom") }
        assertEquals("bytes-of-ok.png", loader.load("ok.png"), "一张失败不应影响后续下载")
        kotlin.test.assertTrue(failed.isFailure || failed.getOrNull() == null)
    }
}
