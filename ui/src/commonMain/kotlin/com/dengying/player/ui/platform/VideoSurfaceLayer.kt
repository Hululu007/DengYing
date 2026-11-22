package com.dengying.player.ui.platform

/**
 * 播放页是否允许「打洞」让 Compose 下方的原生视频层透出。
 *
 * ## 为什么必须按平台区分
 *
 * `PlayerScreen` 是 commonMain 的共享 UI。鸿蒙侧的视频画在
 * `ComposePage.ets` 的全屏 `XComponent(SURFACE)` 上，位于整个 Compose 渲染树
 * **之下**：只有播放页根 Box 背景透明，画面才不会被那层黑底盖住。
 *
 * Android 侧不同：`VideoSurface` actual 是 `AndroidView(PlayerView)`，画面本身
 * 就在 Compose 树里。PlayerView 的 `RESIZE_MODE_FIT` 会在视频之外留下黑边，
 * 而 media3 的 `exo_shutter` 只铺满 `exo_content_frame`；根 Box 一旦透明，
 * 这些区域会露出 Activity 的 `windowBackground`（`Theme.DengYing` 的暖白
 * `#F4F4F1`），起播/缓冲/宽屏片源下都会闪白。桌面/JVM 侧则根本没有
 * 下层视频，透明只会露出窗口底色。
 *
 * 所以「让位」是鸿蒙独有的层序需求，不能写进 commonMain 的通用分支。
 * 各 target 用 expect/actual 给出答案，共用调用点只负责组合条件。
 */
expect val videoSurfaceIsBelowCompose: Boolean

/**
 * 播放页根背景是否应当让位给下层视频。
 *
 * 抽成纯函数是为了让"何时打洞"可单测，而 Compose 组合内只保留一次调用。
 * 条件全部满足才透明，任一不满足都落回不透明黑底（缓冲转圈、错误文案、
 * 未就绪空态都画在黑底上）。
 *
 * @param videoBelowCompose 平台层序，见 [videoSurfaceIsBelowCompose]。
 * @param surfaceBound `VideoSurface.onBind(true)`，即引擎已绑上播放面。
 * @param prepared 引擎已 `onReady`，此时才有画面可透出。
 * @param buffering 缓冲中；缓冲态保持黑底，避免半透明/未刷新的画面闪动。
 * @param hasError 错误态；错误文案需要黑底衬托。
 */
fun shouldRevealVideoSurface(
    videoBelowCompose: Boolean,
    surfaceBound: Boolean,
    prepared: Boolean,
    buffering: Boolean,
    hasError: Boolean,
): Boolean = videoBelowCompose && surfaceBound && prepared && !buffering && !hasError
