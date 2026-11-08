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
import com.dengying.player.domain.playback.GestureRules
import com.dengying.player.domain.playback.SeekPreview
import com.dengying.player.player.PlayerEngine
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
 * 2. **控制栏只有三件套**：进度条 + 暂停键 + 全屏键。**没有**倍速/上下集/连播/投屏。
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
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onEnterFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }

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
            .fillMaxWidth()
            // 16:9 是绝大多数影视内容的宽高比；竖向短剧在此留黑边（FIT 不裁切）
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .onSizeChanged { viewWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            // 画面手势：**一次手势只做一件事**（与全屏同一套口径）
            //   横滑 → seek 预览，松手提交（FR-708）
            //   未形成拖动 → 单击切控制栏 / 双击播放暂停（P-03）
            //
            // 【为什么用 detectDragGestures】内嵌位尺寸小，斜向滑动很常见；
            // 只有横向累计位移超过阈值时才认定为 seek，否则交回点击处理。
            .pointerInput(Unit) {
                var totalX = 0f
                var totalY = 0f
                var mode = 0   // 0=未判定 1=seek
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        mode = 0
                    },
                    onDrag = { change, dragAmount ->
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        if (mode == 0) {
                            if (
                                kotlin.math.abs(totalX) > GestureRules.THRESHOLD_PX &&
                                kotlin.math.abs(totalX) >= kotlin.math.abs(totalY)
                            ) {
                                mode = 1
                                change.consume()
                                seekStartMs = liveCurTime
                                seekTargetMs = liveCurTime
                                seeking = true
                            } else if (kotlin.math.abs(totalY) > GestureRules.THRESHOLD_PX) {
                                // 竖向意图明确但内嵌位不接管亮度/音量：
                                // 本次手势作废，既不当 seek 也不当点击（`docs/08` §2 约束 4）
                                mode = -1
                            }
                        }
                        if (mode == 1) {
                            change.consume()
                            seekTargetMs = SeekPreview.targetOf(
                                fromMs = seekStartMs,
                                deltaX = totalX,
                                viewWidth = viewWidthPx,
                                totalMs = liveTotalTime,
                            )
                        }
                    },
                    onDragEnd = {
                        if (mode == 1) {
                            // 松手才真正 seek：连续 seek 会重建解码管线（FR-708 ③）
                            pendingSeekMs = seekTargetMs
                            onSeek(seekTargetMs)
                            seeking = false
                        }
                    },
                    onDragCancel = {
                        // 取消不提交错误位置
                        seeking = false
                        seekTargetMs = seekStartMs
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
                InlineIconButton(
                    icon = if (isPlaying) DyIcons.Pause else DyIcons.Play,
                    label = if (isPlaying) "暂停" else "播放",
                    onClick = onTogglePlay,
                )
                Spacer(Modifier.width(4.dp))
                InlineIconButton(
                    icon = DyIcons.Fullscreen,
                    label = "全屏",
                    onClick = onEnterFullscreen,
                )
            }
        }
    }
}

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
private fun InlineIconButton(icon: DyIcon, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DyIcon(
            icon = icon,
            tint = Color.White,
            size = 19.dp,
            contentDescription = label,
        )
    }
}
