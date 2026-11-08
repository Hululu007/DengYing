package com.dengying.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dengying.player.domain.playback.SeekPreview

/**
 * 滑动 seek 的中心预览浮层（FR-708 / P-02）。
 *
 * ## 为什么单独成组件
 *
 * 全屏播放页与详情页内嵌位是**两条独立的 UI 路径**（两个不同的 Composable，
 * 各持一套手势状态）。初版只把浮层写在全屏页里，内嵌位滑动时只有底部进度条在动，
 * 用户反馈"内嵌滑动没有动画"——正是这个不对称造成的。
 *
 * 判据（方向、差值基准、时分格式）全在 `domain/SeekPreview`，本组件只负责呈现，
 * 两条路径因此不可能给出不一致的读数。
 *
 * ## 视觉口径
 *
 * - 96dp 左右宽度的半透明胶囊：黑底 0.68，圆角 12dp
 * - 方向图标 + 目标时间（headlineSmall）+ 差值（titleSmall，0.82 白）
 * - 入场 120ms 放大淡入（0.92 → 1），收尾 160ms 缩小淡出（1 → 0.96）
 *
 * 【为什么差值基准是 `fromMs` 而不是当前播放位置】见 `SeekPreview.deltaLabel` 的说明：
 * 用播放位置算，同一次按压停住不动时读数会自己缩水甚至反向。
 *
 * @param visible 手势进行中（含松手后的短暂收尾窗口，由调用方控制）
 * @param fromMs 本次手势的起点
 * @param targetMs 目标位置
 */
@Composable
fun SeekPreviewBubble(
    visible: Boolean,
    fromMs: Long,
    targetMs: Long,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(120)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(160)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(160)),
        modifier = modifier,
    ) {
        val forward = SeekPreview.isForward(fromMs, targetMs)
        val delta = SeekPreview.deltaLabel(fromMs, targetMs)
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 方向图标：原来是 ⏩/⏪ 字形（字体缺字时渲染成方框），现改为矢量双三角
            DyIcon(
                icon = if (forward) DyIcons.FastForward else DyIcons.FastRewind,
                tint = Color.White,
                size = 22.dp,
                contentDescription = if (forward) "快进" else "快退",
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = fmtSeekTime(targetMs),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            if (delta.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = delta,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

/** 毫秒 → `m:ss` 或 `h:mm:ss`（预览浮层专用，与各页面的时间格式保持同一口径） */
private fun fmtSeekTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    } else {
        "$m:${sec.toString().padStart(2, '0')}"
    }
}
