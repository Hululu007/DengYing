package com.dengying.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dengying.player.domain.ui.ConfirmSpec
import com.dengying.player.ui.theme.Palette

/**
 * 通用 UI 小块。
 *
 * 这里只放**被两个以上页面用到**的东西。只在一个页面出现的版式留在那个页面里——
 * 抽到公共层会让"这个组件的归属"变得模糊，改动时来回跳文件。
 */

/** 整页加载态。原工程是 LoadingProgress + 「加载中…」，高度 300vp 居中。 */
@Composable
fun LoadingView(text: String = "加载中…", height: Int = 300) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .height(height.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = Palette.Brand,
            strokeWidth = 3.dp,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * 整页错误态：图标 + 说明 + 「点击重试」。
 *
 * 「可点击重试」是 FR-109 的验收条件之一（提示要能行动），
 * 故重试入口不是可选的装饰。
 */
@Composable
fun ErrorView(message: String, onRetry: (() -> Unit)? = null, height: Int = 300) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .height(height.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 【原为 😕 emoji】emoji 的字形由系统字体提供：部分 ROM 缺字渲染成方框，
        // 各版本的阴脸造型也不一致。改用矢量图标，尺寸与颜色可控。
        DyIcon(
            icon = DyIcons.ErrorFace,
            tint = Palette.TextSecondary,
            size = 34.dp,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(
                    text = "点击重试",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Brand,
                )
            }
        }
    }
}

/** 空态文案（居中，可能很长——FR-508 的「更高版本写入」提示要能完整显示） */
@Composable
fun EmptyView(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp),
        )
    }
}

/**
 * 破坏性操作确认框（FR-505）。
 *
 * 焦点策略：`focusCancel` 为真时**取消键是默认焦点**。
 * Compose 的 AlertDialog 没有 `defaultFocus` 参数，对应做法是把取消键
 * 放在 dismiss 位置（点遮罩/返回键等同取消），并让它的视觉权重高于确认键。
 *
 * 更关键的是**结构保证**：`onConfirm` 只在确认时执行，
 * 取消路径（含点外部关闭）一律不执行——「确认前不写入」不是靠调用方自觉。
 */
@Composable
fun ConfirmDialog(
    spec: ConfirmSpec.Spec,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title, style = MaterialTheme.typography.titleSmall) },
        text = {
            Text(
                spec.message,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                // 破坏性动作的确认键用警示色 + 动作动词（不是「确定」）
                Text(spec.confirmText, color = Palette.Danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(spec.cancelText, color = Palette.TextSecondary)
            }
        },
        containerColor = Palette.CardBg,
    )
}

/** 轻提示（替代系统的 Toast —— 系统 Toast 在部分 ROM 上被限流/降级为无） */
@Composable
fun InlineNotice(text: String, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Box(
        modifier = modifier
            .background(Palette.SearchBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = text },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary,
        )
    }
}
