package com.dengying.player.ui.viewmodel

import com.dengying.player.data.net.HlsUtil
import com.dengying.player.domain.playback.HlsVariants
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.playback.PlaybackSession
import com.dengying.player.platform.KeyValueStore
import com.dengying.player.player.PlayerEngine
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.player.PlayerListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.seconds
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `PlayerViewModel` 的引擎生命周期回归（审计 B-2 / B-6 / B-7）。
 *
 * 这里用记录调用的假 [PlayerEngine] 直接驱动 ViewModel：
 *  - B-6：重复 `onPlaying` 只能有一个进度 Job；
 *  - B-7：切换/解绑引擎必须注销监听器，解绑窗口内的回调不得再进入 VM；
 *  - B-2：换档成功后对新地址 `setMedia` 并从保留位置起播；探测失败必须回退
 *    原地址继续播，不能把 `engine` 留在 null（永久黑屏）。
 *
 * ViewModel 的 `init` 会走真实解析链（网络），故测试用 `startOnInit = false`
 * 构造，并注入 `probeReachability` 假结果；`PlaybackSession` 在 `@BeforeTest`
 * 用**独立调度器**提前初始化，避免它的常驻进度循环干扰 `runTest` 的虚拟时钟。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelEngineTest {

    private companion object {
        const val OLD_URL = "http://old.example/master.m3u8"
        const val NEW_URL = "http://old.example/1080p.m3u8"
        const val EP1_URL = "http://old.example/ep1.m3u8"
        /** token 源的换集入口地址：未经源适配器换链，绝不能交给引擎。 */
        const val RAW_TOKEN = "C0ffeeToken123"
    }

    /**
     * `PlaybackSession` 是 object，首次访问会 `launch` 一个**永不结束**的进度循环。
     * 若它落在当前测试的调度器上，`runTest` 的虚拟时钟里就永远有挂起任务。
     * 因此先用一个不会被推进的独立调度器把它初始化掉。
     */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(TestCoroutineScheduler()))
        PlaybackSession.init(object : PlayerEngineHost {
            override fun engine(): PlayerEngine = com.dengying.player.player.NullPlayerEngine
            override fun peek(): PlayerEngine? = null
            override fun surfaceActive() {}
            override fun surfaceInactive() {}
            override fun forceRelease() {}
            override fun surfaceCount(): Int = 0
        })
        // 会话级进度循环是 while(true)+delay，跑在测试调度器上会让虚拟时钟永不空闲。
        PlaybackSession.cancelProgressTickerForTest()
    }

    @AfterTest
    fun tearDown() {
        PlaybackSession.cancelProgressTickerForTest()
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------ B-6

    @Test
    fun `重复 onPlaying 只保留一个进度循环`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine().apply { position = 42_000L }
        val vm = newVm(engine) { okProbe() }
        vm.attachEngine()

        try {
            // ExoPlayer 的 onIsPlayingChanged(true) 会在暂停恢复、seek、缓冲恢复时反复触发
            engine.emitPlaying()
            engine.emitPlaying()
            engine.emitPlaying()

            advanceTimeBy(5_500)
            assertEquals(
                5,
                engine.positionReads,
                "三个 onPlaying 只能有一个每秒推进的循环（5.5s 应读 5 次，而非 15 次）",
            )

            engine.emitPaused()
            val readsAtPause = engine.positionReads
            advanceTimeBy(3_000)
            assertEquals(readsAtPause, engine.positionReads, "暂停后进度循环必须取消")
        } finally {
            // 常驻循环必须在本测试内停掉，否则 runTest 收尾会在虚拟时钟上永不空闲。
            vm.stopProgressLoopForTest()
        }
    }

    // ------------------------------------------------------------------ B-7

    @Test
    fun `切换引擎时注销监听器，解绑窗口内的回调不得进入 VM`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine()
        var listenersDuringProbe = -1
        var playingSeenDuringProbe = true
        var vmRef: PlayerViewModel? = null
        val probe: suspend (String) -> HlsUtil.ReachResult = {
            // 此刻 bindEngine(null) 已执行，探测尚未返回——正是"引擎解绑窗口"。
            listenersDuringProbe = engine.listeners.size
            engine.emitPlaying()
            playingSeenDuringProbe = vmRef?.state?.isPlaying ?: false
            failProbe()
        }
        val vm = newVm(engine, probe)
        vmRef = vm
        vm.attachEngine()

        try {
            assertEquals(1, engine.listeners.size, "挂载后 VM 注册了一个监听器")

            vm.applyQuality(NEW_URL)
            runCurrent()

            assertEquals(0, listenersDuringProbe, "换档解绑时必须 removeListener")
            assertFalse(playingSeenDuringProbe, "已注销的监听器不得再收到回调")
            assertEquals(1, engine.listeners.size, "回退起播应重新注册同一个监听器实例")
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    // ------------------------------------------------------------------ B-2

    @Test
    fun `换档成功后对新地址 setMedia 并从保留位置起播`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine().apply { position = 42_000L }
        val vm = newVm(engine) { okProbe() }
        vm.attachEngine()

        try {
            engine.emitPlaying()
            advanceTimeBy(1_100) // 让进度循环把 curTime 推到一个非零值
            assertEquals(42_000L, vm.state.curTime)

            vm.applyQuality(NEW_URL)
            runCurrent()

            val call = engine.setMediaCalls.last()
            assertEquals(NEW_URL, call.first, "换档后必须对新地址 setMedia")
            assertEquals(42_000L, call.third, "换档必须保留原进度")
            assertNotNull(vm.engine, "换档后引擎必须重新挂上，不能停在 null（黑屏）")
            assertEquals(NEW_URL, vm.state.selectedVariant)
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    @Test
    fun `换集解析窗口内换档失败不得把原始 token 交给引擎`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine()
        val vm = newVm(engine, { failProbe() }, epUrls = listOf(OLD_URL, RAW_TOKEN))
        vm.attachEngine()

        try {
            vm.primeEngineForTest(OLD_URL)
            runCurrent()
            val before = engine.setMediaCalls.size

            // 换到第二集：resolvedUrl 先被写成原始 token，解析尚未完成
            vm.switchEpisode(1)
            runCurrent()
            assertEquals(1, vm.state.curEpIndex)

            // 解析窗口内用户换档且探测失败：只能走 resolveUrl()，不得回退 token
            vm.applyQuality(NEW_URL)
            runCurrent()

            assertEquals(
                before,
                engine.setMediaCalls.size,
                "解析窗口内换档失败不得 setMedia 任何未解析地址",
            )
            assertTrue(
                engine.setMediaCalls.none { it.first == RAW_TOKEN },
                "原始 epUrl/token 绝不能交给引擎（会 2001 死循环）",
            )
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    @Test
    fun `换集清空旧变体且拒绝旧档位的 applyQuality`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine()
        val vm = newVm(engine, { okProbe() }, epUrls = listOf(OLD_URL, EP1_URL))
        vm.attachEngine()

        try {
            vm.primeEngineForTest(OLD_URL)
            runCurrent()
            val oldVariant = "http://old.example/ep0-720p.m3u8"
            vm.primeVariantsForTest(
                listOf(
                    HlsVariants.Variant(1_800_000, "1280x720", 720, oldVariant),
                    HlsVariants.Variant(800_000, "854x480", 480, "http://old.example/ep0-480p.m3u8"),
                ),
            )
            assertTrue(vm.showQualityEntry, "多码率清单应显示清晰度入口")

            vm.switchEpisode(1)
            runCurrent()

            assertTrue(vm.state.variants.isEmpty(), "换集必须清空上一集的档位清单")
            assertEquals("", vm.state.selectedVariant, "换集必须清空选中的旧档位")
            assertFalse(vm.showQualityEntry, "换集必须关闭清晰度入口")
            assertFalse(vm.state.showQualitySheet, "换集必须关闭清晰度面板")

            val before = engine.setMediaCalls.size
            vm.applyQuality(oldVariant)
            runCurrent()

            assertEquals(before, engine.setMediaCalls.size, "换集后旧档位不得再触发 setMedia")
            assertTrue(
                engine.setMediaCalls.none { it.first == oldVariant },
                "旧集的媒体地址绝不能写进新集会话",
            )
            assertEquals("", vm.state.selectedVariant, "被拒绝的旧档位不得改选中项")
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    @Test
    fun `换档探测失败回退原地址继续播放且不黑屏`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine().apply { position = 25_000L }
        val vm = newVm(engine) { failProbe() }
        vm.attachEngine()

        try {
            // 先走一次真实起播，制造生产回退路径的前提：startedUrl == previousUrl。
            // 否则回退会落到 setMedia 分支，测不到同址 seek/play 的真实形态。
            vm.primeEngineForTest(OLD_URL)
            runCurrent()
            assertEquals(OLD_URL, engine.currentUrl)
            val setMediaBefore = engine.setMediaCalls.size

            engine.emitPlaying()
            advanceTimeBy(1_100)
            assertEquals(25_000L, vm.state.curTime)

            vm.applyQuality(NEW_URL)
            runCurrent()

            assertEquals(
                setMediaBefore,
                engine.setMediaCalls.size,
                "同址回退应走 seek/play，不重复 setMedia",
            )
            assertEquals(OLD_URL, engine.currentUrl, "探测失败必须回退到原地址")
            assertNotNull(vm.engine, "回退后引擎必须仍在，不能留下 engine=null 的黑屏")
            assertEquals("", vm.state.errMsg, "回退成功后不得留下常驻错误浮层")
            assertTrue(vm.state.prepared, "回退成功后必须恢复 prepared，否则一直转圈")
            assertEquals(25_000L, vm.state.curTime, "回退后仍保留原进度")
            assertTrue(vm.state.noticeMsg.isNotEmpty(), "回退应给一次性轻提示")
            assertNotEquals(NEW_URL, vm.state.selectedVariant, "档位应还原为原档")
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    @Test
    fun `已起播后 retry 会对同一地址再次 setMedia`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine()
        val vm = newVm(engine) { okProbe() }
        vm.attachEngine()

        try {
            vm.primeEngineForTest(OLD_URL)
            runCurrent()
            assertEquals(1, engine.setMediaCalls.size, "前提：首次起播已 setMedia")
            assertEquals(OLD_URL, engine.currentUrl)

            vm.retry()
            runCurrent()

            assertEquals(
                2,
                engine.setMediaCalls.size,
                "retry 必须重新 setMedia：bindEngine(null) 后新引擎没有媒体，" +
                    "startedUrl 幂等判据不能把同址重试挡掉",
            )
            assertEquals(OLD_URL, engine.setMediaCalls.last().first, "重试应使用当前已解析地址")
            assertEquals(OLD_URL, engine.currentUrl)
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    // ------------------------------------------------------------------ 取消 / 并发

    @Test
    fun `换档探测中 onCleared 后不得再 setMedia 也不得重挂监听器`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine()
        val gate = CompletableDeferred<Unit>()
        val vm = newVm(engine) {
            gate.await()
            okProbe()
        }
        vm.attachEngine()

        try {
            assertEquals(1, engine.listeners.size, "挂载后 VM 注册了一个监听器")

            vm.applyQuality(NEW_URL)
            runCurrent()
            assertEquals(0, engine.listeners.size, "探测期间引擎已解绑")

            // 探测还挂在网络上时页面被销毁
            vm.clearForTest()
            gate.complete(Unit)
            runCurrent()

            assertTrue(engine.setMediaCalls.isEmpty(), "已清理的 VM 不得再对引擎 setMedia")
            assertEquals(0, engine.listeners.size, "已清理的 VM 不得重新注册监听器")
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    @Test
    fun `快速连续换档只对最后一次档位 setMedia`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine()
        val urlA = "http://old.example/720p.m3u8"
        val urlB = "http://old.example/480p.m3u8"
        val gateA = CompletableDeferred<Unit>()
        val probe: suspend (String) -> HlsUtil.ReachResult = { url ->
            if (url == urlA) {
                try {
                    gateA.await()
                } catch (e: CancellationException) {
                    // 模拟不响应取消的旧探测：即使恢复执行，结果也必须被代际检查丢弃
                }
                failProbe()
            } else {
                okProbe()
            }
        }
        val vm = newVm(engine, probe)
        vm.attachEngine()

        try {
            vm.applyQuality(urlA)
            runCurrent()
            // A 仍挂在探测上，用户立刻改选 B
            vm.applyQuality(urlB)
            runCurrent()

            // 旧探测此刻才返回（且失败）——不得回退、不得覆盖新选择
            gateA.complete(Unit)
            runCurrent()

            assertEquals(
                listOf(urlB),
                engine.setMediaCalls.map { it.first },
                "只允许对最后一次点击的档位 setMedia，旧探测结果必须丢弃",
            )
            assertEquals(urlB, vm.state.selectedVariant, "旧探测失败不得覆盖新选择")
            assertEquals("", vm.state.errMsg, "旧探测失败不得写错误态")
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    @Test
    fun `onCleared 后引擎监听器为 0 且不再响应回调`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = RecordingEngine()
        val vm = newVm(engine) { okProbe() }
        vm.attachEngine()

        try {
            assertEquals(1, engine.listeners.size)
            vm.clearForTest()
            assertEquals(0, engine.listeners.size, "onCleared 必须注销监听器（B-7）")

            engine.emitPlaying()
            assertFalse(vm.state.isPlaying, "已清理的 VM 不得再被引擎回调改状态")
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    // ------------------------------------------------------------------ 会话进度

    @Test
    fun `同一集换档不重置会话进度`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        PlaybackSession.play(
            repo = SourceRepository(),
            rawUrl = "http://old.example/ep0.m3u8",
            title = "测试影片",
            vodName = "测试影片",
            vodId = 7L,
            source = "test-source",
            vodUri = "",
            episodeIndex = 0,
            episodeName = "第1集",
            preResolved = "http://old.example/ep0.m3u8",
            startAtMs = 30_000L,
        )
        assertEquals(30_000L, PlaybackSession.currentState().curTime, "起播位置应写入会话")

        // 换清晰度：同一集（vodId+source+episodeIndex 相同），只换 url
        PlaybackSession.registerPlayback(
            url = "http://old.example/ep0-1080p.m3u8",
            headers = "",
            title = "测试影片",
            vodName = "测试影片",
            vodId = 7L,
            source = "test-source",
            vodUri = "",
            episodeIndex = 0,
            episodeName = "第1集",
        )

        assertEquals(
            30_000L,
            PlaybackSession.currentState().curTime,
            "换档只换 url，不得把同一集进度归零",
        )
        runCurrent()
    }

    // ------------------------------------------------------------------ 观看历史（计划 14）

    @Test
    fun `进度过 3 秒立刻写一次历史`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val kv = FakeKeyValueStore()
        val store = Store(kv).also { it.init() }
        val engine = RecordingEngine().apply { position = 1_000L }
        val vm = newVm(engine, { okProbe() }, emptyList(), vodId = 900L, source = "test_src", kv = kv, store = store)
        vm.attachEngine()

        try {
            engine.emitPlaying()
            advanceTimeBy(2_500)
            runCurrent()
            assertEquals("", kv.getString("history", ""), "3 秒门槛前不得写历史（误触/片头不算看过）")

            engine.position = 3_500L
            advanceTimeBy(1_500)
            runCurrent()
            awaitHistory(kv, "\"position\":3500")
            assertTrue(
                kv.getString("history", "").contains("3500"),
                "过门槛后应立刻落盘，不能等满 5 个 tick",
            )
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    @Test
    fun `暂停补写未落盘的进度`() = runTest(timeout = 20.seconds) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val kv = FakeKeyValueStore()
        val store = Store(kv).also { it.init() }
        val engine = RecordingEngine().apply { position = 6_000L }
        val vm = newVm(engine, { okProbe() }, emptyList(), vodId = 901L, source = "test_src", kv = kv, store = store)
        vm.attachEngine()

        try {
            engine.emitPlaying()
            advanceTimeBy(1_500) // 首个 tick 写 6000
            runCurrent()
            awaitHistory(kv, "\"position\":6000")

            // 第二个 tick 只更新 curTime（未到 5 tick 周期），随后用户暂停
            engine.position = 11_000L
            advanceTimeBy(1_500)
            runCurrent()
            engine.emitPaused()
            runCurrent()

            awaitHistory(kv, "\"position\":11000")
            assertTrue(
                kv.getString("history", "").contains("11000"),
                "暂停时必须把 curTime 补写一次（FR-408）",
            )
        } finally {
            vm.stopProgressLoopForTest()
        }
    }

    /** 落盘走真实 IO 调度器，等它真写完（虚拟时钟等不到别的线程） */
    private suspend fun awaitHistory(kv: KeyValueStore, needle: String) {
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            kotlinx.coroutines.withTimeoutOrNull(3_000) {
                while (!kv.getString("history", "").contains(needle)) kotlinx.coroutines.delay(10)
            }
        }
    }

    // ------------------------------------------------------------------ 脚手架

    private fun newVm(
        engine: PlayerEngine,
        probe: suspend (String) -> HlsUtil.ReachResult,
    ): PlayerViewModel = newVm(engine, probe, emptyList())

    private fun newVm(
        engine: PlayerEngine,
        probe: suspend (String) -> HlsUtil.ReachResult,
        epUrls: List<String>,
        vodId: Long = 0L,
        source: String = "",
        kv: KeyValueStore = FakeKeyValueStore(),
        store: Store = Store(kv),
    ): PlayerViewModel = PlayerViewModel(
        params = PlayerViewModel.PlayerParams(
            url = OLD_URL,
            title = "测试影片",
            vodId = vodId,
            source = source,
            vodName = if (vodId == 0L) "" else "测试影片",
            vodPic = "",
            vodUri = "",
            lineFrom = "",
            episodeIndex = 0,
            episodeName = "",
            altUrls = emptyList(),
            epUrls = epUrls,
            epNames = if (epUrls.isEmpty()) emptyList() else epUrls.indices.map { "第${it + 1}集" },
        ),
        repo = SourceRepository(),
        store = store,
        engineHost = FakeEngineHost(engine),
        isDebug = false,
        probeReachability = probe,
        startOnInit = false,
    )

    private fun okProbe() = HlsUtil.ReachResult(
        m3u8Ok = true,
        m3u8Status = 200,
        segOk = true,
        segStatus = 200,
        segHost = "cdn.example",
    )

    private fun failProbe() = HlsUtil.ReachResult(
        m3u8Ok = false,
        m3u8Status = 0,
        segOk = false,
        segStatus = 0,
        segHost = "",
    )

    /** 记录调用并按接口契约维护监听器列表的假引擎。 */
    private class RecordingEngine : PlayerEngine {

        val listeners = mutableListOf<PlayerListener>()
        val setMediaCalls = mutableListOf<Triple<String, String, Long>>()
        val seekCalls = mutableListOf<Long>()
        var positionReads = 0
        var playCalls = 0
        var position = 0L

        override var currentUrl: String? = null
            private set

        override fun setMedia(url: String, headersJson: String, startPositionMs: Long) {
            setMediaCalls += Triple(url, headersJson, startPositionMs)
            currentUrl = url
            if (startPositionMs > 0L) position = startPositionMs
        }

        override fun play() {
            playCalls++
        }

        override fun pause() {}

        override fun seekTo(positionMs: Long) {
            seekCalls += positionMs
            position = positionMs
        }

        override fun setSpeed(speed: Float) {}

        override fun release() {
            listeners.clear()
            currentUrl = null
        }

        override val currentPosition: Long
            get() {
                positionReads++
                return position
            }

        override val duration: Long get() = 0L

        override val isPlaying: Boolean get() = true

        override fun addListener(listener: PlayerListener) {
            listeners += listener
        }

        override fun removeListener(listener: PlayerListener) {
            listeners -= listener
        }

        override fun viewHandle(): Any? = null

        fun emitPlaying() {
            listeners.toList().forEach { it.onPlaying() }
        }

        fun emitPaused() {
            listeners.toList().forEach { it.onPaused() }
        }
    }

    private class FakeEngineHost(private val engine: PlayerEngine) : PlayerEngineHost {
        override fun engine(): PlayerEngine = engine
        override fun peek(): PlayerEngine = engine
        override fun surfaceActive() {}
        override fun surfaceInactive() {}
        override fun forceRelease() {}
        override fun surfaceCount(): Int = 0
    }

    private class FakeKeyValueStore : KeyValueStore {
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
