package com.dengying.player.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dengying.player.player.PlayerEngine

/**
 * 播放画面承载（KMP P4-5）。
 *
 * ## 为什么必须是 expect/actual 而不是 interface
 *
 * 这是**平台视图互操作**，不是装配关系：Android 侧要往组合树里塞一个
 * `AndroidView(PlayerView)`（media3 的 View 体系），而桌面/JVM 侧根本没有
 * 这个视图类。两端不是"同一件事的不同实现"，而是"一端有画面、另一端没有"，
 * 故只能用 expect/actual 让编译器保证每个 target 都给出一个答案。
 *
 * ## 契约（actual 必须满足）
 *
 *  1. **引擎变化必须重建整个视图**。真机实测黑屏的根因是绑定时序：视图
 *     `factory` 先执行、`update` 只在重组时执行，而引擎常在
 *     `DisposableEffect` 里创建 —— factory 早于引擎，首次 update 必然拿到 null。
 *     即便引擎变成可观察状态触发重组，`update` 也只是"再跑一次"，而旧视图
 *     实例内部的 player 状态是上一次的，会出现"绑上了但 surface 未就绪"的中间态。
 *     故 Android 侧用 `key(engine)` 包住视图，引擎一变就整体重建。
 *  2. **不接管触摸**：控制层全部由调用方自绘，视图的 `useController = false`。
 *  3. **不画缓冲指示**：缓冲态由调用方的浮层表达，视图的
 *     `setShowBuffering(SHOW_BUFFERING_NEVER)`。
 *  4. **保持 FIT 缩放**：`RESIZE_MODE_FIT` 保证不裁切（黑边好过切掉字幕）。
 *  5. 视图句柄经 `PlayerEngine.viewHandle(): Any?` 取得，actual 侧负责转型；
 *     转型失败按 null 处理（等价"暂无画面"），**不得抛异常**。
 *
 * @param engine 当前引擎。null 表示尚无引擎，此时 actual 应渲染空画面。
 * @param modifier 由调用方给定尺寸约束（通常 `fillMaxSize()`）。
 * @param onBind 绑定观测回调，参数为"本次是否绑上了播放器"。
 *        生产环境传空实现即可，排障时用来确认绑定时机。
 */
@Composable
expect fun VideoSurface(
    engine: PlayerEngine?,
    modifier: Modifier = Modifier,
    onBind: (bound: Boolean) -> Unit = {},
)
