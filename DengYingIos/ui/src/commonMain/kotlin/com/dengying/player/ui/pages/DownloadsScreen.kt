package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dengying.player.data.download.DownloadCenter
import com.dengying.player.ui.components.EmptyView
import com.dengying.player.ui.components.GlassBackButton
import com.dengying.player.ui.components.GlassChip
import com.dengying.player.ui.components.GlassClearButton
import com.dengying.player.ui.components.GlassPage
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.Poster
import com.dengying.player.ui.components.formatBytesText
import com.dengying.player.ui.theme.Palette
import kotlinx.coroutines.delay

/** 我的下载：离线任务列表。完成项可直接用本地文件起播。 */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenPlayer: (DownloadCenter.Item) -> Unit,
) {
    var items by remember { mutableStateOf<List<DownloadCenter.Item>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            val next = DownloadCenter.snapshot()
            if (next != items) items = next
            delay(1000)
        }
    }

    val used = items.sumOf { it.bytes }

    GlassPage(
        modifier = Modifier.fillMaxSize(),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Spacer(Modifier.height(56.dp))
                if (items.isEmpty()) {
                    EmptyView("还没有下载。进入影片详情，点「下载」后选集即可离线保存")
                } else {
                    Text(
                        text = "${items.size} 个任务 · ${formatBytesText(used)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 6.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items, key = { it.id }) { item ->
                            DownloadRow(
                                item = item,
                                onPlay = { onOpenPlayer(item) },
                                onRemove = { DownloadCenter.remove(item.id) },
                            )
                        }
                    }
                }
            }
        },
        overlay = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassBackButton(onClick = onBack, modifier = Modifier.padding(end = 10.dp))
                Text(
                    text = "我的下载",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                GlassClearButton(
                    text = "清空",
                    enabled = items.isNotEmpty(),
                    onClick = { DownloadCenter.clearAll() },
                )
            }
        },
    )
}

@Composable
private fun DownloadRow(
    item: DownloadCenter.Item,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = item.canPlay, onClick = onPlay),
        shape = RoundedCornerShape(14.dp),
        elevation = 6.dp,
        liquid = false,
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            Poster(
                url = item.vodPic,
                modifier = Modifier
                    .width(70.dp)
                    .height(96.dp),
                cornerRadius = 6,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = item.vodName.ifEmpty { "未命名影片" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.episodeName.ifEmpty { "第 ${item.episodeIndex + 1} 集" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.weight(1f))
                when (item.status) {
                    DownloadCenter.Status.RUNNING -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Palette.SearchBg, RoundedCornerShape(2.dp)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(Palette.Brand, RoundedCornerShape(2.dp)),
                            )
                        }
                        Text(
                            text = "下载中 ${(item.progress * 100).toInt()}% · ${formatBytesText(item.bytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Brand,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }

                    DownloadCenter.Status.QUEUED -> {
                        Text(
                            text = "排队中",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextSecondary,
                        )
                    }

                    DownloadCenter.Status.DONE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "已下载 · ${formatBytesText(item.bytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.Brand,
                                modifier = Modifier.weight(1f),
                            )
                            GlassChip(
                                text = "播放",
                                selected = true,
                                compact = true,
                                onClick = onPlay,
                            )
                        }
                    }

                    DownloadCenter.Status.FAILED -> {
                        Text(
                            text = item.error.ifEmpty { "下载中断，请重新下载" },
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Danger,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 删除按钮：原来只有 labelSmall 文字 + 默认点击波纹，热区不到 30dp。
                // 现在给足 40dp 热区、去掉默认 indication 与灰色底（用户明确不要
                // 灰色点击反馈），视觉上只保留红色文字。
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .height(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRemove,
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Danger,
                    )
                }
            }
        }
    }
}
