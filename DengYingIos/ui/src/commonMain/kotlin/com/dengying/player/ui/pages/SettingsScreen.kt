package com.dengying.player.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.dengying.player.data.download.DownloadCenter
import com.dengying.player.ui.components.GlassBackButton
import com.dengying.player.ui.components.GlassChip
import com.dengying.player.ui.components.GlassClearButton
import com.dengying.player.ui.components.GlassPage
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.formatBytesText
import com.dengying.player.ui.prefs.PlaybackPrefs
import com.dengying.player.ui.theme.Palette

/** 应用设置：播放手势默认档位与下载存储占用。 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var downloadCount by remember { mutableStateOf(0) }
    var downloadBytes by remember { mutableStateOf(0L) }

    fun refreshStorage() {
        val list = DownloadCenter.snapshot()
        downloadCount = list.size
        downloadBytes = list.sumOf { it.bytes }
    }

    LaunchedEffect(Unit) { refreshStorage() }

    GlassPage(
        modifier = Modifier.fillMaxSize(),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(56.dp))

                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    liquid = false,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                        Text(
                            text = "播放设置",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.TextPrimary,
                        )
                        Text(
                            text = "长按画面右侧临时加速，松手恢复原速",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            text = "加速档位",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PlaybackPrefs.boostSpeeds.forEach { speed ->
                                GlassChip(
                                    text = PlaybackPrefs.labelOf(speed),
                                    selected = PlaybackPrefs.boostSpeed == speed,
                                    compact = true,
                                    onClick = { PlaybackPrefs.selectBoostSpeed(speed) },
                                )
                            }
                        }
                    }
                }

                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    liquid = false,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "下载与存储",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Palette.TextPrimary,
                            )
                            Text(
                                text = if (downloadCount == 0) {
                                    "还没有离线下载"
                                } else {
                                    "$downloadCount 个任务 · 已用 ${formatBytesText(downloadBytes)}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        GlassClearButton(
                            text = "清空",
                            enabled = downloadCount > 0,
                            onClick = {
                                DownloadCenter.clearAll()
                                refreshStorage()
                            },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
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
                    text = "设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                )
            }
        },
    )
}
