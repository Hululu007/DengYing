package com.dengying.player.playback.cast

import com.dengying.player.domain.playback.CastUtil

/**
 * 投屏通道的跨平台契约（FR-418..423）。
 *
 * ## 为什么是 interface 而不是 expect/actual
 *
 * 与 [com.dengying.player.player.PlayerEngineHost] 同理由：这是**装配关系**，
 * 不是「同一件事在各平台有不同实现」的平台服务。Android 用 GMS Cast 框架，
 * 其余平台目前没有投屏通道，注入空实现即可；差异只在「谁来注入」，
 * 由 `:app` 的 `UiDeps` 满足，方向保持 `:app → :ui → :shared`。
 *
 * ## 为什么放进 commonMain
 *
 * `PlayerViewModel`（`:ui/commonMain`）需要驱动发现设备、下发媒体、断开回落，
 * 但不允许认识 `android.content.Context` / GMS 类型。故这里只放**窄口子**：
 * 设备列表用字符串 id + 名称，媒体只传 [CastMedia]，状态只出 [CastEvent]。
 * 平台实现细节（CastContext / SessionManager / RemoteMediaClient）一律留在 `:app`。
 *
 * 契约里所有方法都允许在 JVM 单测里被 fake 替换，不依赖 Android 运行时。
 */
interface CastController {

    /**
     * 当前是否具备投屏能力（框架可用、登记完备）。
     *
     * 只表达「有没有通道」，不表达「此刻有没有发现设备」——设备列表由
     * [startDiscovery] 的 `onDevices` 单独给。没有设备时 UI 显示「正在搜索设备…」，
     * 而不是把入口整个抹掉。
     */
    fun isAvailable(): Boolean

    /**
     * 开始发现设备。
     *
     * @param onDevices 设备列表回调（可能在任意线程触发，由 ViewModel 切回状态写入）
     * @param onState 连接/断开/失败事件回调
     */
    fun startDiscovery(onDevices: (List<CastDevice>) -> Unit, onState: (CastEvent) -> Unit)

    /** 停止发现，注销监听。页面离开或 ViewModel 清理时调用。 */
    fun stopDiscovery()

    /**
     * 选中设备并建立会话。
     *
     * @return false 表示当前无该设备或无法发起连接（调用方据此分流「设备不可达」）。
     */
    fun selectDevice(deviceId: String): Boolean

    /**
     * 把当前媒体下发给接收端。
     *
     * @return 命令是否已进入下发阶段；false 表示没有活动会话，应分流「设备不可达」。
     */
    fun load(media: CastMedia): Boolean

    /**
     * 断开当前会话并回落到本机播放。
     *
     * 断开后本机播放器必须继续从当前进度起播——这条语义由
     * `PlayerViewModel.onCastDisconnected()` 完成，本方法只负责结束远端会话。
     */
    fun endSession()

    /** 释放资源（注销 SessionManagerListener 等）。与 [stopDiscovery] 成对。 */
    fun release()
}

/** 一台可投设备。只暴露选择与显示所需的最小信息。 */
data class CastDevice(val id: String, val name: String)

/**
 * 待下发的媒体。
 *
 * 接收端自己拉流，不经过本应用的请求头注入链路；因此 [CastUtil] 判定为
 * 「需要专属请求头」的源会在 UI 层被禁用，根本不会走到这里。
 */
data class CastMedia(
    val url: String,
    val title: String,
    val subtitle: String = "",
    /** 接收端起播位置（毫秒），用于断开后本机继续播的对齐口径。 */
    val startPositionMs: Long = 0L,
    /** HLS 用 `application/x-mpegURL`，其它按扩展名推断，空串交接收端嗅探。 */
    val contentType: String = "",
)

/** 投屏通道事件。ViewModel 据此更新提示文案与本机回落。 */
sealed interface CastEvent {
    /** 会话建立成功，但未下发媒体。 */
    data object Connected : CastEvent

    /** 媒体已被接收端接受（进入远端播放阶段）。 */
    data object HandoffStarted : CastEvent

    /**
     * 会话断开（用户断开 / 接收端离线 / 会话被系统回收）。
     *
     * @param cause 分类依据，见 [CastUtil.classifyCastFailure]
     */
    data class Disconnected(val cause: DisconnectCause) : CastEvent

    /** 下发或远端拉流失败。 */
    data class Failed(val stage: CastUtil.Stage, val hasAddress: Boolean) : CastEvent
}

/** 断开原因：区分「用户主动」与「链路断了」，决定要不要提示。 */
enum class DisconnectCause {
    /** 用户点「停止投屏」或退出页面，静默回落到本机。 */
    UserInitiated,

    /** 设备离线 / GMS 会话被回收，要给用户可行动提示。 */
    LinkLost,
}

/** 引用纯判定，避免契约层重复定义分类口径。 */
typealias CastFailureKind = CastUtil.FailureKind

/**
 * 无投屏通道时的空实现（JVM 单测、iOS / 鸿蒙壳）。
 *
 * 「空实现」在这里是合法的**装配缺省**，不是伪造的投屏功能：
 * `isAvailable()` 恒 false，UI 层据此把入口置为不可用并给出理由；
 * Android 壳永远注入真的 [CastController] 实现，不会落到这个对象。
 */
object NoopCastController : CastController {
    override fun isAvailable(): Boolean = false
    override fun startDiscovery(onDevices: (List<CastDevice>) -> Unit, onState: (CastEvent) -> Unit) {}
    override fun stopDiscovery() {}
    override fun selectDevice(deviceId: String): Boolean = false
    override fun load(media: CastMedia): Boolean = false
    override fun endSession() {}
    override fun release() {}
}
