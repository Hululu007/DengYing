package com.dengying.player.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.platform.VideoSurface
import com.dengying.player.ui.theme.Glass
import com.dengying.player.domain.playback.GestureRules
import com.dengying.player.domain.playback.SeekPreview
import com.dengying.player.player.PlayerEngine
import com.dengying.player.ui.platform.rememberBrightnessController
import com.dengying.player.ui.platform.rememberVolumeController
import com.dengying.player.ui.prefs.PlaybackPrefs
import com.dengying.player.ui.theme.Palette

/**
 * 内嵌播放器（FR-705、FR-706）
 *
 * ## 需求原文
 *
 * 「进入详情页能不能在内容上方放一个占位，就是点击剧集的时候视频就会在占位处播放，
 * 此时点击视频就只会出现进度条暂停键和全屏建（手机布局和平板布局在此场景下要保持一致）」
 *
 * ## 三条设计约束
 *
 * 1. **占位常驻**：未选剧集时显示空态（而不是不渲染），这样用户知道"这里能播"。
 * 2. **控制栏保持精简**：进度条 + 上/下一集 + 暂停键 + 全屏键。
 *    v1.3.2 按用户反馈补了上/下一集（"没有上下集按钮功能"）；仍**没有**倍速/连播/投屏。
 * 3. **不区分设备类型**：手机与平板用同一套版式（用户明确要求）。
 *    内嵌位尺寸由详情页版式决定（16:9 宽高比），与设备类型无关。
 *
 * ## 与全屏播放页的关系
 *
 * 【D6 单会话架构修订】内嵌位与全屏页共用**同一个全局引擎**
 * （`PlaybackEngineHolder` 的唯一实例，FR-711）：页面只是引擎的**视图宿主**，
 * 播放的发起与迁移都在 `PlaybackSession`。本组件不创建、不释放引擎。
 *
 * 初版注释声称"内嵌态用独立的引擎实例，不与全屏页共用"——那是 D6 改造前的
 * 旧设计，已被真机验证否定（解码器抢占），此处按现状改正，避免注释与代码矛盾。
 */
@Composable
fun InlinePlayer(
    engine: PlayerEngine?,
    title: String,
    isPlaying: Boolean,
    curTime: Long,
    totalTime: Long,
    buffering: Boolean,
    errMsg: String,
    /** 进行中的状态文案（如"正在换源…"）。有它就必须让用户看见，不能静默等待。 */
    status: String = "",
    /**
     * 播放会话是否已激活（用户点过正片 / 会话在播）。
     *
     * 【真机反馈"点正片后内嵌位一直是占位，等缓存好了才出现视频栏"的修法】
     * 会话解析地址期间（token 换链最长可到 5s+）引擎尚未创建，`engine == null`
     * 成立——空态占位（▶ + "选择下方剧集"）把这段等待整个占了，用户以为没点上。
     * 现在：**空态只在会话未激活时显示**；激活但引擎未就绪时直接落入缓冲态
     * （转圈 + "正在解析播放地址…"），视频栏形态立刻出现。
     *
     * 从全屏返回的瞬间同理：会话在播（active=true）而引擎引用尚未重新挂上，
     * 不再闪一帧空态占位——那正是"退出全屏后的残留"观感来源之一。
     */
    active: Boolean = false,
    /**
     * 页面正在退出时置 true：**只保留黑底，不画任何占位图形**。
     *
     * 【真机反馈"返回后占位符残留约 0.5 秒"的修法】
     * `onDispose` 要等 NavHost 退出转场**结束**才触发，那段时间详情页仍在组合中。
     * 初版把状态清空后，`engine == null` 分支会渲染空态占位（▶ + 文案），
     * 于是转场期间就出现了"残留"。
     *
     * 现在：退出前置 `hidden`，占位图形不画；黑底保留（视频位置本来就是黑的，
     * 视觉上无跳变，也不会让上方内容突然上移）。
     */
    hidden: Boolean = false,
    /** 上/下一集是否可切；false 时按钮置灰（首集/末集）。 */
    canPrev: Boolean = false,
    canNext: Boolean = false,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onEnterFullscreen: () -> Unit,
    /**
     * 全屏形态（FR-711 纯屏幕形态）：true = 铺满宿主，全屏键变为「退出全屏」。
     *
     * 只影响**布局与按钮语义**，不影响渲染面——`VideoSurface` 两种形态都是
     * 同一个实例 `fillMaxSize()`，切形态不重建画面、不触碰引擎。
     */
    fullscreen: Boolean = false,
    /** 全屏形态下「退出全屏」键的回调；由调用方与平台方向/系统栏成对处理。 */
    onExitFullscreen: () -> Unit = {},
    /**
     * 当前倍速（FR-711 全屏形态扩展）。只影响全屏形态——内嵌态控制栏保持
     * 精简口径（设计约束 2：进度条 + 上/下一集 + 暂停 + 全屏，见本文件头）。
     */
    speed: Float = 1.0f,
    /** 倍速选择回调；调用方负责写会话速度并回填状态（`DetailViewModel.setInlineSpeed`）。 */
    onSpeedChange: (Float) -> Unit = {},
    /** 长按画面右半屏开始临时加速；由调用方保存原倍速。 */
    onBoostStart: () -> Unit = {},
    /** 松手 / 手势取消 / 离开页面时恢复原倍速。 */
    onBoostEnd: () -> Unit = {},
    /**
     * 全屏形态右上角「投屏」键回调；null = 不显示。
     *
     * 【为什么是回调而不是内嵌投屏面板】投屏的设备发现/连接/回落状态机
     * 都在 `PlayerViewModel`（castController 经 UiDeps 注入），详情页不复制它。
     * 当前实现是**升级到播放页**：`DetailViewModel.openPlayerForCast()` 走
     * FR-705 既有交接（已解析地址 + 当前进度，播放页 adopt 会话），投屏 UI
     * 全量复用。返回详情页时落回竖屏内嵌形态。
     */
    onCast: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    // 全屏形态的倍速面板开关（FR-711 扩展）；内嵌形态无倍速键，恒为 false。
    var speedSheet by remember { mutableStateOf(false) }
    var boosting by remember { mutableStateOf(false) }

    // ---- 画面横向滑动 seek（FR-708 / P-02）----
    //
    // 【为什么内嵌位也要有】真机复现：点剧集后视频在**内嵌位**播放，
    // 而全屏要再点一次 ⤢ 才进得去。用户"直接滑屏幕"的第一现场就是这里，
    // 此前这里只有一个 detectTapGestures——横滑被当成点击，什么都不发生。
    //
    // 手感与全屏一致：只预览不连续 seek，松手提交一次（`docs/06` D4）。
    var viewWidthPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var seeking by remember { mutableStateOf(false) }
    var seekStartMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }

    // ---- FR-711 全屏形态手势扩展：左半屏亮度 / 右半屏音量（P-04/P-05 同口径）----
    // 内嵌态不接管：竖向意图明确时仍按 docs/08 §2 约束 4 作废（见下方手势块）。
    // 控制器与全屏播放页同一套（`ui/platform`）；拿不到窗口时为 null，
    // 亮度手势经 `GestureRules.decide` 的 brightnessAvailable 分支自动退化为作废。
    val brightnessCtl = rememberBrightnessController()
    val volumeCtl = rememberVolumeController()
    var viewHeightPx by androidx.compose.runtime.mutableFloatStateOf(1f)
    // 【必须 rememberUpdatedState】pointerInput(Unit) 不随参数重启，手势块里
    // 直接读 `fullscreen` 会永远拿到首次组合的值（与 :171 的 seek 同坑）。
    val liveFullscreen by androidx.compose.runtime.rememberUpdatedState(fullscreen)

    // 调节反馈浮层（口径同全屏播放页：数值随拖动更新，手势结束 800ms 后收起；
    // 手势进行中即使跨过 800ms 也不收——adjustActive 挡住）。
    var adjustInfo by remember { mutableStateOf<GestureAdjust?>(null) }
    var adjustVisible by remember { mutableStateOf(false) }
    var adjustActive by remember { mutableStateOf(false) }
    var adjustHideTick by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(adjustHideTick) {
        if (adjustHideTick > 0) {
            kotlinx.coroutines.delay(800)
            if (!adjustActive) adjustVisible = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { if (boosting) onBoostEnd() }
    }

    // 离开全屏形态（或整个内嵌位销毁）时撤掉窗口亮度覆写——谁设置谁恢复，
    // 不恢复会让其它页面顶着这个亮度（与 PlayerScreen 的恢复规则一致）。
    // DisposableEffect(fullscreen) 在形态切换时重启：true→false 的收尾闭包
    // 捕获的是旧值 true，恰好只在"曾经覆写过"时执行恢复。
    androidx.compose.runtime.DisposableEffect(fullscreen, brightnessCtl) {
        onDispose { if (fullscreen) brightnessCtl?.restore() }
    }

    // 已提交、尚未被会话位置确认的目标。0 = 无待生效 seek。
    // 作用同全屏态：提交后播放器要重新缓冲，`curTime` 仍停在旧位置，
    // 直接显示会让进度条**先弹回旧位置再跳到新位置**。
    var pendingSeekMs by remember { mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(pendingSeekMs) {
        if (pendingSeekMs > 0L) {
            kotlinx.coroutines.delay(4000)
            pendingSeekMs = 0L   // 兜底：缓冲再久也不让进度条卡在预览值
        }
    }

    // 【必须用 rememberUpdatedState】`pointerInput(Unit)` 的块**不会**因为参数变化
    // 而重启，lambda 里直接读 `curTime` / `totalTime` 会永远拿到首次组合时的值。
    // 真机踩到：滑动时 `totalTime` 仍是 0，`SeekPreview.targetOf` 原样返回起点 0
    // → 探针出现 `engine-seek target=0`，松手直接回到片头。
    val liveCurTime by androidx.compose.runtime.rememberUpdatedState(curTime)
    val liveTotalTime by androidx.compose.runtime.rememberUpdatedState(totalTime)

    // 【自动隐藏控制栏】真机反馈"小屏的进度条一直不隐藏，应该和全屏一样"。
    // 全屏态是播放中 3 秒后淡出、点击画面重新显示——内嵌态初版**常驻**。
    // 现在用同一口径：播放中 3 秒后隐藏，暂停/缓冲/拖动时保持显示。
    var controlsVisible by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(isPlaying, buffering, dragging, seeking, errMsg) {
        if (!isPlaying || buffering || dragging || seeking || errMsg.isNotEmpty()) {
            // 非播放态（暂停/缓冲/出错/拖动/滑动 seek）保持可见，否则用户找不到按钮
            controlsVisible = true
            return@LaunchedEffect
        }
        controlsVisible = true
        kotlinx.coroutines.delay(3000)
        controlsVisible = false
    }

    // 滑动期间把控制栏唤出来：用户要看的"进度条动画"就是底部这条。
    // 与全屏态同口径（见 `PlayerViewModel.onSeekStart` 的说明）。
    androidx.compose.runtime.LaunchedEffect(seeking) {
        if (seeking) controlsVisible = true
    }

    Box(
        modifier = modifier
            // 【FR-711 纯屏幕形态】全屏形态铺满宿主；内嵌形态 16:9
            // （16:9 是绝大多数影视内容的宽高比；竖向短剧在此留黑边，FIT 不裁切）。
            .then(
                if (fullscreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            .background(Color.Black)
            .onSizeChanged {
                viewWidthPx = it.width.toFloat().coerceAtLeast(1f)
                viewHeightPx = it.height.toFloat().coerceAtLeast(1f)
            }
            // 画面手势：**一次手势只做一件事**（与全屏同一套口径）
            //   横滑 → seek 预览，松手提交（FR-708）
            //   未形成拖动 → 单击切控制栏 / 双击播放暂停（P-03）
            //
            // 【为什么用 detectDragGestures】内嵌位尺寸小，斜向滑动很常见；
            // 只有横向累计位移超过阈值时才认定为 seek，否则交回点击处理。
            .pointerInput(Unit) {
                var totalX = 0f
                var totalY = 0f
                var startX = 0f
                // 0=未判定 1=seek 2=亮度 3=音量 -1=竖向但该侧不可调/内嵌态（作废）
                var mode = 0
                var brightBase = 0f
                var volBase = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        totalX = 0f
                        totalY = 0f
                        startX = offset.x
                        mode = 0
                    },
                    onDrag = { change, dragAmount ->
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        if (mode == 0) {
                            // 方向仲裁与全屏播放页同一纯函数（`domain/GestureRules`）：
                            // 横向主导→seek、竖向主导+起点左半→亮度、右半→音量、
                            // 该侧不可调→Discard。内嵌态不接管亮度/音量（FR-711 只给
                            // 全屏形态），竖向仍按 docs/08 §2 约束 4 作废。
                            when (GestureRules.decide(
                                totalX = totalX,
                                totalY = totalY,
                                startX = startX,
                                viewWidth = viewWidthPx,
                                brightnessAvailable = brightnessCtl != null,
                            )) {
                                GestureRules.Action.Undecided -> Unit
                                GestureRules.Action.Seek -> {
                                    change.consume()
                                    mode = 1
                                    seekStartMs = liveCurTime
                                    seekTargetMs = liveCurTime
                                    seeking = true
                                }
                                GestureRules.Action.Brightness -> {
                                    if (liveFullscreen && brightnessCtl != null) {
                                        change.consume()
                                        mode = 2
                                        brightBase = brightnessCtl.current()
                                        adjustInfo = GestureAdjust(2, brightBase)
                                        adjustVisible = true
                                        adjustActive = true
                                    } else {
                                        mode = -1
                                    }
                                }
                                GestureRules.Action.Volume -> {
                                    if (liveFullscreen && volumeCtl != null) {
                                        change.consume()
                                        mode = 3
                                        volBase = volumeCtl.currentFraction()
                                        adjustInfo = GestureAdjust(3, volBase)
                                        adjustVisible = true
                                        adjustActive = true
                                    } else {
                                        mode = -1
                                    }
                                }
                                GestureRules.Action.Discard -> {
                                    // 竖向意图已明确但该侧不可调：本次手势作废。
                                    // 不退回 seek（方向已定），也不当成点击。
                                    mode = -1
                                }
                            }
                        }
                        when (mode) {
                            1 -> {
                                change.consume()
                                seekTargetMs = SeekPreview.targetOf(
                                    fromMs = seekStartMs,
                                    deltaX = totalX,
                                    viewWidth = viewWidthPx,
                                    totalMs = liveTotalTime,
                                )
                            }
                            // 上滑为正：竖向位移取负号（Y 轴向下为正），比例按画面高度
                            2 -> adjustInfo = GestureAdjust(
                                2,
                                brightnessCtl!!.adjust(brightBase, -totalY / viewHeightPx),
                            )
                            3 -> adjustInfo = GestureAdjust(
                                3,
                                volumeCtl?.adjust(volBase, -totalY / viewHeightPx) ?: volBase,
                            )
                        }
                    },
                    onDragEnd = {
                        when (mode) {
                            1 -> {
                                // 松手才真正 seek：连续 seek 会重建解码管线（FR-708 ③）
                                pendingSeekMs = seekTargetMs
                                onSeek(seekTargetMs)
                                seeking = false
                            }
                            // 浮层在手势结束后延时收起（adjustHideTick），松手瞬间不消失
                            2, 3 -> {
                                adjustActive = false
                                adjustHideTick++
                            }
                            // 0：位移不足阈值 → 交回下方 detectTapGestures 按点击处理
                            // -1：作废手势，不切控制层（竖向意图已明确）
                        }
                    },
                    onDragCancel = {
                        if (mode == 1) {
                            // 取消不提交错误位置
                            seeking = false
                            seekTargetMs = seekStartMs
                        } else if (mode == 2 || mode == 3) {
                            adjustActive = false
                            adjustHideTick++
                        }
                    },                )
            }
            // 单击 / 双击：只在"未形成拖动"时触发，故不会与 seek 冲突
            //
            // 【P-03 双击】内嵌位与全屏一致：双击切换播放/暂停。
            // 单击与双击的区分由 `detectTapGestures` 的判定窗口完成，
            // 代价是单击回调会等约 300ms（与全屏同口径，见 PlayerScreen 的说明）。
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { onTogglePlay() },
                    onLongPress = { offset ->
                        if (!boosting && offset.x >= size.width / 2f) {
                            boosting = true
                            onBoostStart()
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (boosting) {
                            boosting = false
                            onBoostEnd()
                        }
                    },
                )
            },
    ) {
        // ---- 渲染面 ----
        //
        // 【P-06 残留的真正根因】`hidden` 之前只跳过了**浮层**的绘制，
        // 而这块 `AndroidView(PlayerView)` 仍留在组合里、**继续显示最后一帧**。
        // 返回转场期间详情页还在画，于是用户在滑动的内容里看到一块
        // “卡住的视频画面”——那才是他说的“残留”。
        //
        // 修法：`hidden` 时**整个渲染面都不进组合**（不是只不画浮层）。
        // AndroidView 从组合树移除会同时把 PlayerView 从窗口上摘下，
        // 故不会留下任何一帧。会话不动——回详情页/回全屏时重新挂载即可
        //（ENGINE 是全局单例，画面可重建，播放不会中断）。
        if (!hidden) {
            // 【P-06】hidden 时整个渲染面都不进组合（不是只不画浮层）——
            // 视图从组合树移除会同时把播放面从窗口上摘下，不留下任何一帧。
            // 会话不动：回详情页/回全屏时重新挂载即可（引擎是全局单例）。
            VideoSurface(engine = engine, modifier = Modifier.fillMaxSize())
        }

        // ---- 空态 / 错误态 / 缓冲态 ----
        //
        // 【真机反馈的 bug：点击剧集"没反应"】初版的顺序是
        //   `if (engine == null) { 空态; return@Box }`
        // 于是**解析失败时错误提示被吞掉**——用户看到的一直是
        // "选择下方剧集开始播放"，看起来就是点了没反应。
        // 正确顺序：**先判错误，再判空态**。
        if (hidden) {
            // 页面退出中：不画任何图形，只留黑底
            return@Box
        }

        if (errMsg.isNotEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = errMsg,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            return@Box
        }

        if (engine == null && !active) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 空态占位：原来是一个裸字形 "▶"，现改为矢量图标（同尺寸下更挺、不自带字形空白）
                DyIcon(
                    icon = DyIcons.Play,
                    tint = Color.White.copy(alpha = 0.5f),
                    size = 32.dp,
                )
                Text(
                    text = "选择下方剧集开始播放",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Box
        }

        // 会话已激活但引擎未就绪（解析地址中 / 全屏返回瞬间重挂中）：
        // 不 return——落到下方缓冲态，视频栏形态立即出现。
        // 解析期 totalTime 还是 0 → 转圈 + 状态文案；恢复期 totalTime > 0 →
        // 黑底等第一帧，控制栏照常可用。

        // ---- 缓冲态（错误已在上方判过，故不会与错误同屏）----
        if (buffering || totalTime <= 0) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                    // 【必须有状态文案】换源兜底可能耗时数秒，静默转圈等于"没反应"
                    if (status.isNotEmpty()) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        // ---- 滑动 seek 中心浮层（FR-708 / P-02）----
        //
        // 【为什么必须独立于控制栏】用户反馈"内嵌滑动没有动画"：初版只有底部
        // 进度条跟着走，而进度条属于控制栏——控制栏隐藏时它不在，滑动就完全静默。
        // 浮层自己管显隐（`seeking`），与 `controlsVisible` 无关。
        //
        // 【为什么放在缓冲态之后】error / 空态分支已经 `return@Box`，
        // 到这里说明画面在播；浮层只可能叠在画面上，不会盖住错误提示。
        SeekPreviewBubble(
            visible = seeking,
            fromMs = seekStartMs,
            targetMs = seekTargetMs,
            modifier = Modifier.align(Alignment.Center),
        )

        if (boosting) {
            Text(
                text = "${PlaybackPrefs.labelOf(PlaybackPrefs.boostSpeed)} 加速中",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }

        // ---- 亮度/音量调节浮层（FR-711 全屏形态；P-04/P-05 同口径）----
        // 居中玻璃 pill，随拖动实时刷新数值；显隐由 adjustVisible 管，
        // 与 controlsVisible 无关（控制栏隐藏时手势浮层仍要可见）。
        if (adjustVisible && adjustInfo != null) {
            val info = adjustInfo!!
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                GlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    fill = Glass.DarkFill,
                    elevation = 0.dp,
                ) {
                    Text(
                        text = "${if (info.kind == 2) "亮度" else "音量"} " +
                            "${(info.value * 100).toInt().coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // ---- 控制栏：**进度条与按钮在同一行**（FR-706 + 真机反馈）----
        //
        // 【真机反馈的三点，逐条对应】
        // 1. "进度条和播放等不在同一条线" → 初版进度条在上、按钮在下（两行）。
        //    现在改成**一行**：时间 · 进度条 · 暂停 · 全屏。
        // 2. "进度条 UI 很丑" → 初版用 Material3 `Slider`（默认粗轨道 + 大圆点 +
        //    左右留白），在内嵌的小尺寸里显得笨重。改用**自绘细线进度条**：
        //    轨道 3dp、滑块小圆点、无内边距。
        // 3. "小屏的进度条一直不隐藏，应该和全屏一样" → 全屏态有自动隐藏（3 秒），
        //    内嵌态初版**常驻显示**。现在加同样的自动隐藏：
        //    播放中 3 秒后淡出，点击画面重新显示。
        val duration = if (totalTime > 0) totalTime else 1L
        // 拖动进度条 / 画面横滑 都走预览值，松手后才回到真实播放位置。
        // 提交后的等待期用 `pendingSeekMs`：会话位置还没追上来时不回退，
        // 避免进度条"弹回旧位置再跳过去"。
        val adopt = pendingSeekMs <= 0L ||
            SeekPreview.shouldAdoptEnginePosition(curTime, pendingSeekMs)
        val shown = when {
            dragging -> dragValue
            seeking -> seekTargetMs
            adopt -> curTime
            else -> pendingSeekMs
        }
        // 位置追平后清掉 pending。必须放在副作用里：组合期写状态会触发重组循环。
        androidx.compose.runtime.LaunchedEffect(adopt) {
            if (adopt && pendingSeekMs > 0L) pendingSeekMs = 0L
        }
        val fraction = (shown.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fmtTime(shown),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.width(44.dp),
                )
                // 自绘细线进度条（比 Material Slider 更紧凑、更贴合播放器观感）
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                // 点进度条直接跳转（按点击位置比例）
                                val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                onSeek((duration * ratio).toLong())
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragValue = curTime
                                },
                                onDragEnd = {
                                    dragging = false
                                    onSeek(dragValue)
                                },
                                onHorizontalDrag = { change, delta ->
                                    change.consume()
                                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                                    dragValue = (duration * ratio).toLong()
                                },
                            )
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val w = maxWidth
                    // 轨道
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                    )
                    // 已播部分
                    Box(
                        modifier = Modifier
                            .width(w * fraction)
                            .height(3.dp)
                            .background(Palette.Danger, RoundedCornerShape(2.dp)),
                    )
                    // 滑块（小圆点，只在拖动时放大）
                    Box(
                        modifier = Modifier
                            .offset(x = w * fraction - 5.dp)
                            .size(if (dragging) 12.dp else 9.dp)
                            .background(Palette.Danger, CircleShape),
                    )
                }

                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (totalTime > 0) fmtTime(totalTime) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.width(44.dp),
                )

                Spacer(Modifier.width(4.dp))
                // FR-711 扩展：全屏形态补倍速键（纯文字，与播放页 :791 同口径——
                // 「它不是图形，给图标反而看不懂」）。内嵌形态不出现。
                if (fullscreen) {
                    Text(
                        text = if (speed == 1.0f) "倍速" else if (speed == 2.0f) "2x" else "${speed}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (speed == 1.0f) Color.White else Palette.Danger,
                        modifier = Modifier
                            .clickable { speedSheet = !speedSheet }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                InlineIconButton(
                    icon = DyIcons.SkipPrevious,
                    label = "上一集",
                    enabled = canPrev,
                    onClick = onPrev,
                )
                Spacer(Modifier.width(4.dp))
                InlineIconButton(
                    icon = if (isPlaying) DyIcons.Pause else DyIcons.Play,
                    label = if (isPlaying) "暂停" else "播放",
                    onClick = onTogglePlay,
                )
                Spacer(Modifier.width(4.dp))
                InlineIconButton(
                    icon = DyIcons.SkipNext,
                    label = "下一集",
                    enabled = canNext,
                    onClick = onNext,
                )
                Spacer(Modifier.width(4.dp))
                InlineIconButton(
                    // FR-711：全屏形态下同一颗键变为「退出全屏」
                    icon = if (fullscreen) DyIcons.FullscreenExit else DyIcons.Fullscreen,
                    label = if (fullscreen) "退出全屏" else "全屏",
                    onClick = if (fullscreen) onExitFullscreen else onEnterFullscreen,
                )
            }
        }

        // ---- 全屏形态顶栏（FR-711 扩展：返回 / 投屏）----
        //
        // 【为什么内嵌态没有】内嵌态时详情页自己的返回栏就在播放位上方，
        // 再画一条是重复；全屏态铺满宿主、详情页返回栏不参与布局，必须自带。
        //   左上「返回」= 退全屏，与系统返回键同语义（只退形态，不退页面）；
        //   右上「投屏」= 升级到播放页（投屏状态机只在 PlayerViewModel，见 onCast 文档）。
        if (fullscreen) {
            FullscreenTopBar(
                controlsVisible = controlsVisible,
                onExitFullscreen = onExitFullscreen,
                onCast = onCast,
            )

            // ---- 倍速面板（FR-711 扩展）----
            // 玻璃面板 + 档位列表，与播放页 :669 同一设计语言；档位一致
            // （0.75/1.0/1.25/1.5/2.0），当前档用 Danger 高亮。
            // 随控制栏一起隐藏：点画面收起控制栏时面板不能悬空残留。
            SpeedSheetPanel(
                visible = speedSheet && controlsVisible,
                speed = speed,
                onSpeedChange = onSpeedChange,
                onDismiss = { speedSheet = false },
                // BoxScope 对齐留在调用点（抽出后的 Composable 不在 BoxScope 里）。
                // 【bottom 贴控制行】此前 120dp 把 5 档竖排面板（≈180dp 高）整体顶到
                // 上半区，用户实测"在右上角很突兀"；收到 64dp 让面板底边贴着
                // 控制行上沿，从倍速键正上方展开。
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 64.dp),
            )
        }
    }
}

/**
 * 全屏形态顶栏：左上「返回」（退形态）+ 右上「投屏」（升级播放页）。
 *
 * 【为什么抽成独立 Composable】主 [InlinePlayer] 已是超大 Compose 函数，
 * 再叠顶栏+面板后 Kotlin/Native 侧（CPF LLVM 19）`clang++ -cc1 -emit-obj`
 * 稳定 0xC0000005 崩溃（同代码旧版可链，新增后 3/3 复现）。拆出后函数体
 * 缩小，崩溃消失；顺带让主函数少一层嵌套。改这段时不要合回主函数。
 */
@Composable
private fun FullscreenTopBar(
    controlsVisible: Boolean,
    onExitFullscreen: () -> Unit,
    onCast: (() -> Unit)?,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = controlsVisible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineIconButton(
                icon = DyIcons.ArrowBack,
                label = "返回",
                onClick = onExitFullscreen,
            )
            Spacer(Modifier.weight(1f))
            if (onCast != null) {
                Text(
                    text = "投屏",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .clickable(onClick = onCast)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 全屏形态倍速面板（玻璃 + 档位列表，当前档 Danger 高亮）。抽出动机见 [FullscreenTopBar]。 */
@Composable
private fun SpeedSheetPanel(
    visible: Boolean,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        fill = Glass.DarkFill,
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            for (v in SPEED_OPTIONS) {
                Text(
                    text = "${if (v == 1.0f) "1.0" else if (v == 2.0f) "2.0" else v.toString()}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (speed == v) Palette.Danger else Color.White,
                    modifier = Modifier
                        .clickable {
                            onSpeedChange(v)
                            onDismiss()
                        }
                        .padding(horizontal = 22.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** 倍速档位（FR-711 扩展）：与全屏播放页 `:669` 面板同一组值，两处保持一致。 */
private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 全屏形态手势反馈浮层数据（kind 2=亮度、3=音量），口径同全屏播放页的同名类。 */
private data class GestureAdjust(val kind: Int, val value: Float)

/** 毫秒 → `m:ss` 或 `h:mm:ss` */
private fun fmtTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}

/**
 * 内嵌态图标按钮（FR-709）。
 *
 * 尺寸口径：**热区 34dp 见方**（2026-10-25 从 40dp 压缩——用户反馈内嵌位
 * 播放态下方控制栏占比过大），图标视觉 **19dp**。圆形半透明底保留，
 * 让图标有明确的可点边界。
 *
 * 图标由 `⏸ ▶` 字形改为矢量（[DyIcon]）：字形尺寸受字体行高约束，
 * 24dp 的热区里实际笔画只有十几 dp，且不同设备字重不一致。
 */
@Composable
private fun InlineIconButton(
    icon: DyIcon,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(
                Color.White.copy(alpha = if (enabled) 0.12f else 0.06f),
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DyIcon(
            icon = icon,
            // 禁用态压暗但不隐藏：用户要能看出"有上一集/下一集这个能力"
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            size = 19.dp,
            contentDescription = label,
        )
    }
}
