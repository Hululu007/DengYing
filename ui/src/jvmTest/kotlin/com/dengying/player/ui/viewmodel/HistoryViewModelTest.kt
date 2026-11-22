package com.dengying.player.ui.viewmodel

import com.dengying.player.core.HistoryItem
import com.dengying.player.data.store.Store
import com.dengying.player.platform.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 历史页读取态（计划 14 目标 1 的页面侧）。
 *
 * 回归的 bug：未登录用户打开历史页，读盘还没回来就先显示「暂无播放记录」——
 * 记录其实就在本地。空列表必须能区分「读取中」「读不出来」「确实没有」。
 *
 * 读盘走真实 IO 调度器（`Store` 内部 `withContext(ioDispatcher)`），虚拟时钟
 * 等不到它，故这里用真实调度器轮询 `loading` 收敛。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun 读取成功后带回本地记录且结束读取中状态() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = Store(MemoryKv()).also { it.init() }
        store.saveHistory(
            HistoryItem(vod_id = 5L, vod_name = "本地片", source = "s", episodeIndex = 0, position = 8_000L)
        )

        val vm = HistoryViewModel(store)
        assertFalse(vm.state.loading, "构造函数不应假设正在读取")

        vm.reload()
        runCurrent()
        assertTrue(vm.state.loading, "reload 一开始必须进入读取中，空表不能立刻说「暂无」")

        awaitLoaded(vm)
        assertFalse(vm.state.loading)
        assertEquals("", vm.state.error)
        assertEquals(1, vm.state.list.size, "未登录也要能读到本机记录")
        assertEquals(8_000L, vm.state.list[0].position)
    }

    @Test
    fun 空存储读取结束后仍为空且无错误() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = Store(MemoryKv()).also { it.init() }
        val vm = HistoryViewModel(store)

        vm.reload()
        runCurrent()
        awaitLoaded(vm)

        assertFalse(vm.state.loading)
        assertEquals("", vm.state.error, "确无记录不是错误")
        assertTrue(vm.state.list.isEmpty())
    }

    /** 等 `loading` 回到 false（读盘在真实 IO 线程上） */
    private suspend fun awaitLoaded(vm: HistoryViewModel) {
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(3_000) {
                while (vm.state.loading) delay(5)
            }
        }
    }

    private class MemoryKv : KeyValueStore {
        private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
        override fun getString(key: String, defValue: String): String = map[key] ?: defValue
        override fun allKeys(): Set<String> = map.keys.toSet()
        override fun putString(key: String, value: String, sync: Boolean): Boolean {
            map[key] = value
            return true
        }

        override fun remove(key: String, sync: Boolean): Boolean {
            map.remove(key)
            return true
        }
    }
}
