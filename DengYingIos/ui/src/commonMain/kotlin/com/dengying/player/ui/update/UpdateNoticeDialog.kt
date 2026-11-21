package com.dengying.player.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.GlassActionButton
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.platform.PlatformBackHandler
import com.dengying.player.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * 非阻断的更新提示。
 *
 * 挂在导航根容器上而不是新增路由：更新检查不该改变用户当前页面与返回栈，
 * 弹窗关闭后仍停在原来的页面。视觉沿用项目的暗色玻璃面与 `DyIcons`，不引入
 * 新的图标库或弹窗框架。
 */
@Composable
fun UpdateNoticeDialog(
    info: UpdateInfo,
    ignored: Boolean,
    onIgnoreChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Boolean,
    onOpen: () -> Boolean,
    onOpenApk: () -> Boolean,
) {
    val downloadProvider = if (info.url.startsWith("https://gitee.com/")) "Gitee" else "GitHub"
    var copied by remember(info.tag) { mutableStateOf(false) }
    var copyFailed by remember(info.tag) { mutableStateOf(false) }
    var openFailed by remember(info.tag) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2200)
            copied = false
        }
    }

    val feedback = when {
        copied -> "下载链接已复制"
        copyFailed -> "复制失败，请长按链接手动复制"
        openFailed -> "无法打开浏览器，请复制链接后在浏览器访问"
        else -> ""
    }

    PlatformBackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            )
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 520.dp)
                .pointerInput(Unit) {
                    detectTapGestures { }
                },
            shape = RoundedCornerShape(24.dp),
            // 【可读性优先】更新说明是要被读的文字块，不能做液态玻璃：
            // 采样会把底下的海报/标题透上来，深浅色下正文都发花。改成不透明卡面。
            fill = Palette.CardBg,
            elevation = 18.dp,
            liquid = false,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.SearchBg.copy(alpha = 0.9f))
                            .padding(9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        DyIcon(
                            icon = DyIcons.Refresh,
                            tint = Palette.Brand,
                            size = 22.dp,
                            contentDescription = "应用更新",
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "发现新版本",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.TextPrimary,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = buildString {
                                append("最新版本 v")
                                append(info.version)
                                if (info.publishedDate.isNotEmpty()) {
                                    append(" · ")
                                    append(info.publishedDate)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                            color = Palette.TextSecondary,
                        )
                    }
                }

                Text(
                    text = info.notes.ifEmpty { "暂无更新说明" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    color = Palette.TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.SearchBg.copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = ignored,
                            role = Role.Checkbox,
                            onValueChange = onIgnoreChange,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = ignored,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Palette.Brand,
                            uncheckedColor = Palette.TextSecondary,
                            checkmarkColor = Palette.AppBg,
                        ),
                    )
                    Text(
                        text = "忽略此版本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpdateQuietAction(
                        text = "稍后",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    UpdateQuietAction(
                        text = "复制下载链接",
                        onClick = {
                            val ok = onCopy()
                            copied = ok
                            copyFailed = !ok
                            if (ok) openFailed = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (feedback.isNotEmpty()) {
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextSecondary,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }

                GlassActionButton(
                    text = if (downloadProvider == "Gitee") "下载 APK（GitHub）" else "去 $downloadProvider 下载",
                    onClick = {
                        val ok = if (downloadProvider == "Gitee") onOpenApk() else onOpen()
                        openFailed = !ok
                        if (ok) copyFailed = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (downloadProvider == "Gitee") {
                    // Gitee 服务端把 .apk 标成 application/zip，手机浏览器会存成
                    // app-release.apk.zip；这里把能直接得到 .apk 的入口放主按钮，
                    // Gitee 页面留作镜像，并明确告诉用户改后缀的办法。
                    Text(
                        text = "Gitee 附件会被浏览器存成 .zip，下载后把后缀改回 .apk 即可安装。",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        ),
                        color = Palette.TextSecondary,
                    )
                    Text(
                        text = "改用 Gitee 页面下载",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Palette.TextSecondary,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                role = Role.Button,
                                onClick = {
                                    val ok = onOpen()
                                    openFailed = !ok
                                    if (ok) copyFailed = false
                                },
                            )
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateQuietAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier
            .height(48.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(14.dp),
        fill = Palette.SearchBg.copy(alpha = 0.9f),
        elevation = 0.dp,
        liquid = false,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Palette.TextPrimary,
            )
        }
    }
}
