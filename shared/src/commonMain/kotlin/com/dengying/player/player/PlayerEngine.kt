package com.dengying.player.player

/**
 * 播放失败的跨平台分类。
 *
 * 【为什么需要这一层】迁移前 `PlayerViewModel` 直接引用
 * `androidx.media3.common.PlaybackException.ERROR_CODE_IO_*` 做「致命 / 非致命」判定
 * （见 docs/04 §14.0.3 缺口 2）。那是比 `PlayerView` 更隐蔽的 Media3 泄漏：
 * 它藏在 ViewModel 的业务分支里，iOS 侧没有任何对应常量。
 *
 * 现在分类上移到引擎实现，`commonMain` 只认这个枚举；`code` 仍原样透传供日志排障。
 *
 * | kind | Media3 错误码 | 含义 | 应用该做什么 |
 * |---|---|---|---|
 * | [Network] | `ERROR_CODE_IO_UNSPECIFIED` / `..._NETWORK_CONNECTION_TIMEOUT` / `..._NETWORK_CONNECTION_FAILED` | 网络抖动 | **不介入**：Media3 自己退避重试 3 次，界面只提示「正在重试」 |
 * | [SourceGone] | `ERROR_CODE_IO_BAD_HTTP_STATUS` / `..._FILE_NOT_FOUND` / `..._INVALID_HTTP_CONTENT_TYPE` | 源侧永久失效 | 换线路（FR-411，只做一轮） |
 * | [Decode] | 其余（解码/容器/DRM 等） | 解不了 | 换线路可能解决 |
 * | [Unknown] | 无法归类 | — | 按可致命处理 |
 */
enum class PlaybackErrorKind {
    /** 网络抖动：交给播放器自己重试，应用层不介入 */
    Network,

    /** 源侧永久问题（404/403/内容类型错）：换线路 */
    SourceGone,

    /** 解码或容器问题：换线路可能解决 */
    Decode,

    /** 无法归类：按可致命处理 */
    Unknown,
}

/**
 * 播放引擎窄接口 —— **页面只依赖这些能力**。
 *
 * 存在的意义：把播放器实现（Android = Media3 ExoPlayer，iOS = AVPlayer）的类型挡在
 * 平台实现内。页面与 ViewModel 不认识 `Player`、`ExoPlayer`、`AVPlayer` 任何类型。
 *
 * ## 与 P1~P3 前身的关系
 *
 * 本接口是 `app/.../playback/PlayerFactory.kt` 里原 `interface Engine` 的跨平台版本。
 * 两处**刻意的形状变化**（其余逐条保留）：
 *
 * 1. **`setMedia` 去掉 `context: Context` 参数**。原签名带 `context` 是因为
 *    `PlayerFactory.mediaSource(context, ...)` 需要它建数据源；但 `Media3Engine`
 *    自己已有 `private val context: Context` 字段，参数与字段重名、纯属冗余。
 *    删掉后行为不变（同一个 `applicationContext`）。
 *
 * 2. **`onError` 从 `(code, message)` 改为 `(kind, code, message)`**。原因见
 *    [PlaybackErrorKind]：分类逻辑必须离开 ViewModel。`code` 保留原值供日志。
 *
 * ## 线程约束（实现方必须遵守）
 *
 * Android 侧 ExoPlayer **所有 API 必须主线程调用**（真机踩过
 * `IllegalStateException: Player is accessed on the wrong thread`）。
 * 本接口的方法因此**约定在主线程调用**，实现不得自行切线程。
 */
interface PlayerEngine {

    /**
     * 装载媒体并准备播放。
     *
     * @param url 媒体地址
     * @param headersJson 请求头 JSON（空串表示无自定义头）。解析失败应**不影响播放**——
     *   原实现 `parseHeaders` 坏 JSON 时返回空表，本接口沿用该契约。
     * @param startPositionMs 起播位置，>0 时 seek 后再 prepare
     */
    fun setMedia(url: String, headersJson: String, startPositionMs: Long)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float)

    /** 释放引擎。释放后再调任何方法的行为由实现决定（Android 侧为忽略）。 */
    fun release()

    /** 当前进度（ms） */
    val currentPosition: Long

    /** 总时长（ms）；未知为 0 */
    val duration: Long

    val isPlaying: Boolean

    /**
     * 当前装载的媒体地址；`null` = 未装载或已释放。
     *
     * 供「无缝续播」判定：调用方（发现页内嵌位）回来时若 [currentUrl] 仍是
     * 自己的媒体，`play()` 直接续即可（不重载不缓冲）；被别的页面借走换了
     * 媒体才需要 `setMedia` 重设。
     */
    val currentUrl: String?

    /** 注册状态回调；`release` 会一并清空 */
    fun addListener(listener: PlayerListener)

    /**
     * 注销状态回调。
     *
     * 【为什么接口必须有它】引擎是进程级单例（[PlayerEngineHost]），而播放页
     * ViewModel 每次进入都会注册一个新的监听器。只增不减时，单例永久持有旧
     * ViewModel：内存泄漏，且旧 VM 在用户离开后仍会收到 `onReady/onPlaying/
     * onCompleted`，可能触发换集或历史落盘。VM 在 `onCleared` 与切换引擎时
     * 必须按**同一实例**注销。
     *
     * 注销未注册过的实例应为无操作（幂等），不得抛异常。
     */
    fun removeListener(listener: PlayerListener)

    /**
     * 供视图层绑定原生播放表面。
     *
     * **这是刻意的窄口子**：视图层需要一个具体播放器实例（Android = `Player`，
     * iOS = `AVPlayer`），而本接口刻意不暴露这些类型。折中做法是只给出这一个
     * 绑定用的方法，视图层拿到的仍是 `Any`，只有平台实现知道它其实是什么。
     *
     * 返回 `null` 表示引擎尚未创建——视图应显示黑底等待，**不应抛异常**。
     */
    fun viewHandle(): Any?
}

/** 引擎事件 */
interface PlayerListener {

    /** 就绪（可播）。`durationMs` 未知时为 0 */
    fun onReady(durationMs: Long)

    /** 首帧可渲染 / 开始播放 */
    fun onPlaying()

    fun onPaused()

    /** 缓冲开始/结束 */
    fun onBuffering(active: Boolean)

    /** 播放完成（到达末尾） */
    fun onCompleted()

    /**
     * 失败。
     *
     * @param kind 跨平台分类（决定是否换线路，见 [PlaybackErrorKind]）
     * @param code 实现方的原始错误码，仅供日志与排障，**业务逻辑不要依赖它**
     * @param message 原始错误文本，仅供日志
     */
    fun onError(kind: PlaybackErrorKind, code: Int, message: String)

    /** 视频尺寸（0x0 表示尚无视频轨，供「有声音没画面」的判定） */
    fun onVideoSize(width: Int, height: Int)
}

/**
 * 全局唯一播放引擎的宿主。
 *
 * ## 为什么是 interface 而不是 expect/actual
 *
 * 与 P3 的 `KeyValueStore` 同一理由：它由平台壳（`:app` / `iosApp`）装配后注入，
 * 不是全局服务，用 `expect` 会让平台类型漏进 `commonMain` 的声明里。
 *
 * ## 实现必须满足的语义（Android 侧逐条沿用 `PlaybackEngineHolder`）
 *
 * - **单例**：全应用只有一个引擎实例。初版让 inline / 全屏各建一个，导致解码器抢占
 *   （google/ExoPlayer Issue #9556 维护者原话）。
 * - **计数只表示「有几个表面在显示」**，与谁持有无关。归零时**只暂停不释放**：
 *   频繁释放重建会让 codec 反复初始化，官方反对。
 * - 第二版曾用 acquire/release 配对计数，因 `toFullscreen()` 先置 `inlineEngine = null`
 *   导致归还取到 null 而泄漏（`engine=false refCount=2`），故不采用配对语义。
 */
interface PlayerEngineHost {

    /** 取（必要时创建）全局引擎 */
    fun engine(): PlayerEngine

    /**
     * 偷看当前引擎，**不创建**。没有引擎时返回 null。
     *
     * 与 [engine] 的区别是副作用：详情页的内嵌位要在「已有引擎」时挂载、
     * 「尚无引擎」时保持空态，若调 [engine] 会凭空造一个从未播放过的实例。
     * 迁移前这是 `PlaybackEngineHolder.peek()` 的语义，原样保留。
     */
    fun peek(): PlayerEngine?

    /** 有表面开始显示 */
    fun surfaceActive()

    /** 有表面停止显示；计数归零时暂停（**不释放**） */
    fun surfaceInactive()

    /** 强制释放（进程级清理 / 排障用） */
    fun forceRelease()

    /** 当前表面计数（排障用） */
    fun surfaceCount(): Int
}

/**
 * 空引擎：`engine()` 拿不到真引擎时的安全替代（2026-11-09，ohos 桩崩溃修复）。
 *
 * 发现页在**组合期** `remember { engineHost.engine() }`（IndexScreen 发现窗格），
 * 而 ohos 侧 `engine()` 是显式抛错桩（OH_AVPlayer 未接入）——组合期抛出即未捕获
 * 异常，进程 SIGABRT（faultlog cppcrash 实录：点开发现页闪退回桌面）。Android 侧
 * `engine()` 恒成功，行为不变；ohos 桩的抛错语义保留（全屏播放器路径有错误弹层
 * 接住）。发现页改用本对象兜底：**只浏览不自动播**，`viewHandle()` 返回 null →
 * `VideoSurface` 渲染空画面（expect 契约明文允许：null = 暂无画面，不得抛异常）。
 */
object NullPlayerEngine : PlayerEngine {
    override fun setMedia(url: String, headersJson: String, startPositionMs: Long) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(positionMs: Long) {}
    override fun setSpeed(speed: Float) {}
    override fun release() {}
    override val currentPosition: Long get() = 0L
    override val duration: Long get() = 0L
    override val isPlaying: Boolean get() = false
    override val currentUrl: String? get() = null
    override fun addListener(listener: PlayerListener) {}
    override fun removeListener(listener: PlayerListener) {}
    override fun viewHandle(): Any? = null
}
