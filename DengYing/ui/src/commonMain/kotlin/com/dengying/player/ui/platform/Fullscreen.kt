package com.dengying.player.ui.platform

import androidx.compose.runtime.Composable

/**
 * 全屏与方向控制（FR-413）的跨平台契约（KMP P4-5）。
 *
 * ## 为什么是 expect/actual
 *
 * "全屏"在本项目里的定义是**改变窗口方向 + 隐藏系统栏**，这完全是平台窗口
 * 体系的概念：Android 用 `requestedOrientation` + `WindowInsetsController`，
 * iOS 要用 `UIViewController` 的 `supportedInterfaceOrientations` 与
 * `prefersStatusBarHidden`，桌面则根本没有这个语义。两端不是同一件事的
 * 不同实现，故用 expect/actual。
 *
 * ## 契约
 *
 *  1. **"是否全屏"不由本类回答**。唯一真相是平台的方向状态；调用方需要判断时
 *     读自己平台的配置（Android 侧是 `LocalConfiguration.orientation`）。
 *     自己维护布尔会与系统实际状态脱节 —— 旋转、Activity 重建、系统栏变化都会
 *     改变真实方向而布尔不会跟着变，恢复逻辑随之失效。
 *  2. **[enter] 只请求，不保证生效**；[exit] 是"显式回竖屏"，不是"恢复进入前的
 *     快照"。用户进入前本就是横屏时（平板/支架），恢复快照等于把用户锁在横屏。
 *  3. actual 必须幂等且**不得抛异常**：拿不到窗口时静默降级。
 */
expect class FullscreenState {
    /**
     * 进入全屏：请求横屏并隐藏系统栏。
     *
     * @param landscape 竖向视频传 false —— 此时只隐藏系统栏、不转横屏，
     *                  避免把竖向画面拧成横向两侧大黑边。
     */
    fun enter(landscape: Boolean)

    /** 退出全屏：**显式回竖屏**（不是"恢复进入前的方向快照"） */
    fun exit()
}

/**
 * 取得全屏控制句柄。
 *
 * 内部自己找 Activity/窗口，故签名里不出现任何平台类型。桌面端返回一个
 * 什么都不做的实现。
 */
@Composable
expect fun rememberFullscreenState(): FullscreenState

/**
 * 竖屏页面在自己组合时调用：把窗口方向显式收敛回竖屏。
 *
 * 为什么放在页面而不是导航宿主：导航层无法区分"刚请求横屏的过渡期"与
 * "真的在竖屏页"；页面自己知道自己的语义。全屏页退出后方向可能残留横屏值，
 * 由本函数在竖屏页组合期间收敛。
 */
@Composable
expect fun ConsumePortraitOrientation()

/**
 * 屏幕亮度调节（docs/08 P-04）的跨平台契约。
 *
 * 语义定稿（迁移前已定，不得改动）：
 *  - 调节对象是**当前窗口**，不写系统设置 ⇒ 不需要权限，也不改动用户在
 *    系统里的亮度偏好。
 *  - 起点：窗口已有覆写值就用它；否则读系统亮度；读不到退化为 0.5。
 *  - 取值范围 0.01..1。下限不取 0：全黑会让用户以为屏幕故障。
 *  - **离开播放页必须恢复**（[restore]）：窗口覆写会一直作用到页面结束，
 *    不复原会让用户在其它页面顶着这个亮度且找不到原因。谁设置谁恢复。
 */
expect class BrightnessController {
    /** 当前值（0..1）。作为一次手势的调节起点；连续手势必须从上次结果继续 */
    fun current(): Float

    /**
     * 调到 `base + fraction`。
     *
     * @param base 本次手势的起点（调用 [current] 取得）
     * @param fraction 手势累计位移 / 画面高度。**上滑为正**（与主流播放器一致）
     * @return 实际生效值，供反馈浮层显示
     */
    fun adjust(base: Float, fraction: Float): Float

    /** 离开播放页：撤掉窗口覆写，把亮度交还系统 */
    fun restore()
}

/**
 * 取得亮度控制器。
 *
 * @return 拿不到窗口时返回 null —— 调用方据此关闭亮度手势（`brightnessAvailable = false`），
 *         而不是让手势静默无效。
 */
@Composable
expect fun rememberBrightnessController(): BrightnessController?

/**
 * 媒体音量调节（docs/08 P-05）的跨平台契约。
 *
 * 语义定稿：调节**媒体音量流**（与播放行为对应，不动铃声/闹钟流）；
 * 音量是整数档，先换算成档位再夹到平台上下限，不越界；不弹系统音量条
 * （反馈浮层由本应用自绘，避免两套 UI 叠加）。
 */
expect class VolumeController {
    /** 当前媒体音量（0..1）。作为一次手势的调节起点 */
    fun currentFraction(): Float

    /**
     * 调到 `base + fraction`。
     *
     * @param base 本次手势的起点（调用 [currentFraction] 取得）
     * @param fraction 手势累计位移 / 画面高度。**上滑为正**
     * @return 实际生效值（0..1），供反馈浮层显示
     */
    fun adjust(base: Float, fraction: Float): Float
}

/** 取得音量控制器。拿不到音频服务时返回 null。 */
@Composable
expect fun rememberVolumeController(): VolumeController?

/**
 * 当前窗口是否横屏。
 *
 * 为什么是 expect 而不是让调用方读 `LocalConfiguration`：`LocalConfiguration`
 * 是 Android 侧的配置对象，iOS/桌面没有同形的东西。而"是否横屏"在本项目里
 * 只有一个用途 —— 决定全屏键的图标与文案、以及控制层版式，故收一个布尔出去
 * 即可，不必把整个配置对象漏进 commonMain。
 *
 * **唯一真相是平台方向状态**，不要在应用侧另存"是否全屏"的布尔：旋转、
 * Activity 重建、系统栏变化都会改变真实方向，而布尔不会跟着变，恢复逻辑随之失效。
 */
@Composable
expect fun rememberIsLandscape(): Boolean
