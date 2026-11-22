package com.dengying.player.ui.viewmodel

import com.dengying.player.data.net.HlsUtil
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.domain.playback.CastUtil
import com.dengying.player.platform.KeyValueStore
import com.dengying.player.player.PlayerEngine
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.player.PlayerListener
import com.dengying.player.playback.cast.CastController
import com.dengying.player.playback.cast.CastDevice
import com.dengying.player.playback.cast.CastEvent
import com.dengying.player.playback.cast.CastMedia
import com.dengying.player.playback.cast.DisconnectCause
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `PlayerViewModel` 的投屏接线回归（FR-418..423）。
 *
 * 用可记录、可主动发事件的假 [CastController] 驱动 ViewModel，覆盖：
 * 入口禁用/放行、设备发现与选择、连接后下发媒体、失败分流、
 * 断开后回落本机播放、清理时注销监听。真机/模拟器的 GMS Cast 会话
 * 本环境跑不了，这里只验 VM 侧的接线与状态机。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelCastTest {

    private companion object {
        const val URL = "https://cdn.example.com/master.m3u8"
        const val DEVICE_ID = "cast-route-1"
    }

    @BeforeTest
    fun setUp() {
        // 只需要给 `PlayerViewModel.viewModelScope` 一个可用的 Main：
        // `onCleared()` 会读 `viewModelScope`，而 JVM 单测没有平台 Main 实现。
        // 这里**刻意不初始化 `PlaybackSession`**：它是单例，object init 会在
        // `Dispatchers.Main` 上 launch 一个 while(true)+delay 的进度循环；
        // 本测试类的投屏路径全是同步状态机（startOnInit=false），不需要它。
        // 少初始化一个常驻循环，就不会在 `resetMain()` 后留下悬挂的取消恢复任务
        // 去污染后续测试类（实测会让 `SuggestDebouncerTest` 偶发失败）。
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        // 【刻意不 resetMain】`:ui:jvmTest` 的所有测试类共用一个 JVM，而多个类里的
        // `PlayerViewModel.viewModelScope` / `PlaybackSession` 进度循环都绑在
        // `Dispatchers.Main` 上。任何一类在 @AfterTest 里 resetMain() 都会把后续类的
        // Main 置为 unset，导致「Dispatchers.Main was accessed ... unset」偶发失败
        // （实测换类顺序后 SuggestDebouncerTest / PlayerViewModelEngineTest 都中过招）。
        // 这里只把本类设的 Main 留着，交给后续类的 @BeforeTest 覆盖；不在本类制造 unset 窗口。
        // 无参构造的 UnconfinedTestDispatcher 不持有可泄漏的常驻 Job。
    }

    // ------------------------------------------------------------------ 入口

    @Test
    fun `通道不可用时入口禁用且点击不发起发现`() {
        val cast = FakeCastController(available = false)
        val vm = newVm(cast)
        vm.refreshCastabilityForTest()

        assertFalse(vm.state.castEnabled)
        assertTrue(vm.state.castBlockReason.isNotEmpty())
        vm.onCastClick()
        assertEquals(0, cast.discoveryStarts)
        assertFalse(vm.state.showCastSheet)
    }

    @Test
    fun `可投时点击展开面板并开始发现设备`() {
        val cast = FakeCastController(available = true)
        val vm = newVm(cast)
        vm.refreshCastabilityForTest()

        assertTrue(vm.state.castEnabled)
        assertEquals("", vm.state.castBlockReason)
        vm.onCastClick()
        assertEquals(1, cast.discoveryStarts)
        assertTrue(vm.state.showCastSheet)
        assertTrue(vm.state.castDevices.isEmpty())
    }

    // ------------------------------------------------------------------ 发现与选择

    @Test
    fun `发现回调把设备写进状态，选中设备转给控制器`() {
        val cast = FakeCastController(available = true)
        val vm = newVm(cast)
        vm.refreshCastabilityForTest()
        vm.onCastClick()

        cast.emitDevices(listOf(CastDevice(DEVICE_ID, "客厅电视")))
        assertEquals(1, vm.state.castDevices.size)
        assertEquals("客厅电视", vm.state.castDevices[0].name)

        vm.onCastDeviceSelected(DEVICE_ID)
        assertEquals(DEVICE_ID, cast.selectedDeviceId)
        assertTrue(vm.state.castMsg.contains("正在连接"))
    }

    @Test
    fun `选路失败分流为设备不可达`() {
        val cast = FakeCastController(available = true).apply { selectReturns = false }
        val vm = newVm(cast)
        vm.refreshCastabilityForTest()
        vm.onCastClick()
        cast.emitDevices(listOf(CastDevice(DEVICE_ID, "客厅电视")))

        vm.onCastDeviceSelected(DEVICE_ID)
        assertEquals(
            CastUtil.castFailureText(CastUtil.FailureKind.DeviceUnreachable),
            vm.state.castMsg,
        )
        assertFalse(vm.state.showCastSheet)
        assertEquals(0, cast.loads)
    }

    // ------------------------------------------------------------------ 下发与成功

    @Test
    fun `会话建立后下发媒体，交接成功进入已投屏态`() {
        val cast = FakeCastController(available = true)
        val engine = RecordingEngine()
        val vm = newVm(cast, engine)
        vm.refreshCastabilityForTest()
        vm.onCastClick()

        cast.emit(CastEvent.Connected)
        assertEquals(1, cast.loads)
        assertEquals(URL, cast.lastMedia?.url)
        assertEquals("测试影片", cast.lastMedia?.title)
        assertTrue(engine.pauseCalls > 0, "投屏开始后本机引擎必须暂停，避免两端同时出声")

        cast.emit(CastEvent.HandoffStarted)
        assertTrue(vm.state.castConnected)
        assertFalse(vm.state.showCastSheet)
        assertEquals("已投屏到电视", vm.state.castMsg)
    }

    @Test
    fun `无活动会话时下发被拒并分流设备不可达`() {
        val cast = FakeCastController(available = true).apply { loadReturns = false }
        val vm = newVm(cast)
        vm.refreshCastabilityForTest()
        vm.onCastClick()

        cast.emit(CastEvent.Connected)
        assertEquals(
            CastUtil.castFailureText(CastUtil.FailureKind.DeviceUnreachable),
            vm.state.castMsg,
        )
        assertFalse(vm.state.castConnected)
    }

    // ------------------------------------------------------------------ 失败分流

    @Test
    fun `远端拉流失败给出可行动文案`() {
        val cast = FakeCastController(available = true)
        val vm = newVm(cast)
        vm.refreshCastabilityForTest()
        vm.onCastClick()

        cast.emit(CastEvent.Failed(CastUtil.Stage.Remote, hasAddress = true))
        assertEquals(
            CastUtil.castFailureText(CastUtil.FailureKind.RemotePullFail),
            vm.state.castMsg,
        )
    }

    @Test
    fun `地址失效优先于链路阶段`() {
        val cast = FakeCastController(available = true)
        val vm = newVm(cast)
        vm.refreshCastabilityForTest()
        vm.onCastClick()

        cast.emit(CastEvent.Failed(CastUtil.Stage.Link, hasAddress = false))
        assertEquals(
            CastUtil.castFailureText(CastUtil.FailureKind.AddressGone),
            vm.state.castMsg,
        )
    }

    // ------------------------------------------------------------------ 断开回落

    @Test
    fun `链路断开后回落本机播放并提示`() {
        val cast = FakeCastController(available = true)
        val engine = RecordingEngine()
        val vm = newVm(cast, engine)
        vm.refreshCastabilityForTest()
        vm.onCastClick()
        cast.emit(CastEvent.Connected)
        cast.emit(CastEvent.HandoffStarted)

        cast.emit(CastEvent.Disconnected(DisconnectCause.LinkLost))
        assertFalse(vm.state.castConnected)
        assertEquals("投屏已断开，已回到本机播放", vm.state.castMsg)
        assertTrue(engine.seekCalls.isNotEmpty(), "断开后必须 seek 回投屏时的进度")
        assertTrue(engine.playCalls > 0, "断开后必须恢复本机 play()")
        assertEquals(1, cast.discoveryStops)
    }

    @Test
    fun `用户主动断开静默回落本机`() {
        val cast = FakeCastController(available = true)
        val engine = RecordingEngine()
        val vm = newVm(cast, engine)
        vm.refreshCastabilityForTest()
        vm.onCastClick()
        cast.emit(CastEvent.Connected)
        cast.emit(CastEvent.HandoffStarted)

        vm.onCastClick() // 已连接 → 停止投屏
        assertEquals(1, cast.endSessionCalls)
        cast.emit(CastEvent.Disconnected(DisconnectCause.UserInitiated))
        assertEquals("已回到本机播放", vm.state.castMsg)
        assertTrue(engine.playCalls > 0)
    }

    // ------------------------------------------------------------------ 清理

    @Test
    fun `页面清理时结束会话并释放控制器`() {
        val cast = FakeCastController(available = true)
        val vm = newVm(cast)
        vm.refreshCastabilityForTest()
        vm.onCastClick()
        cast.emit(CastEvent.Connected)
        cast.emit(CastEvent.HandoffStarted)

        vm.clearForTest()
        assertEquals(1, cast.endSessionCalls)
        assertEquals(1, cast.releases)
    }

    // ------------------------------------------------------------------ 脚手架

    private fun newVm(cast: FakeCastController, engine: RecordingEngine = RecordingEngine()): PlayerViewModel {
        val vm = PlayerViewModel(
            params = PlayerViewModel.PlayerParams(
                url = URL,
                title = "测试影片",
                vodId = 0L,
                source = "",
                vodName = "",
                vodPic = "",
                vodUri = "",
                lineFrom = "",
                episodeIndex = 0,
                episodeName = "",
                altUrls = emptyList(),
                epUrls = emptyList(),
                epNames = emptyList(),
            ),
            repo = SourceRepository(),
            store = Store(FakeKeyValueStore()),
            engineHost = FakeEngineHost(engine),
            isDebug = false,
            castController = cast,
            probeReachability = { HlsUtil.ReachResult(true, 200, true, 200, "cdn.example") },
            startOnInit = false,
        )
        // 让 resolvedUrl 为已解析的直出地址（startOnInit=false 时解析链不会跑）
        vm.primeEngineForTest(URL)
        return vm
    }

    /** 记录调用并可主动发事件的假投屏通道。 */
    private class FakeCastController(private val available: Boolean) : CastController {
        var discoveryStarts = 0
            private set
        var discoveryStops = 0
            private set
        var selectedDeviceId: String? = null
            private set
        var loads = 0
            private set
        var lastMedia: CastMedia? = null
            private set
        var endSessionCalls = 0
            private set
        var releases = 0
            private set
        var selectReturns = true
        var loadReturns = true

        private var onDevices: ((List<CastDevice>) -> Unit)? = null
        private var onState: ((CastEvent) -> Unit)? = null

        override fun isAvailable(): Boolean = available
        override fun startDiscovery(onDevices: (List<CastDevice>) -> Unit, onState: (CastEvent) -> Unit) {
            discoveryStarts++
            this.onDevices = onDevices
            this.onState = onState
        }
        override fun stopDiscovery() { discoveryStops++ }
        override fun selectDevice(deviceId: String): Boolean {
            selectedDeviceId = deviceId
            return selectReturns
        }
        override fun load(media: CastMedia): Boolean {
            loads++
            lastMedia = media
            return loadReturns
        }
        override fun endSession() { endSessionCalls++ }
        override fun release() { releases++ }

        fun emitDevices(devices: List<CastDevice>) = onDevices?.invoke(devices)
        fun emit(event: CastEvent) = onState?.invoke(event)
    }

    /** 只记录 pause/seek/play 的假引擎，其余用空实现。 */
    private class RecordingEngine : PlayerEngine {
        val seekCalls = mutableListOf<Long>()
        var playCalls = 0
            private set
        var pauseCalls = 0
            private set
        override fun setMedia(url: String, headersJson: String, startPositionMs: Long) {}
        override fun play() { playCalls++ }
        override fun pause() { pauseCalls++ }
        override fun seekTo(positionMs: Long) { seekCalls += positionMs }
        override fun setSpeed(speed: Float) {}
        override fun release() {}
        override val currentPosition: Long get() = 0L
        override val duration: Long get() = 0L
        override val isPlaying: Boolean get() = true
        override val currentUrl: String? get() = URL
        override fun addListener(listener: PlayerListener) {}
        override fun removeListener(listener: PlayerListener) {}
        override fun viewHandle(): Any? = null
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
        private val map = mutableMapOf<String, String>()
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
