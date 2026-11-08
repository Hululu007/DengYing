package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.domain.ui.ConfirmSpec
import com.dengying.player.ui.components.ConfirmDialog
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.EmptyView
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.Poster
import com.dengying.player.ui.navigation.Routes
import com.dengying.player.ui.theme.Palette
import com.dengying.player.ui.viewmodel.HistoryViewModel

/** 毫秒 → `m:ss`（与原工程同格式） */
private fun fmtTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    vm: HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        val deps = UiDepsHolder.get()
        HistoryViewModel(store = deps.store)
    },
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.reload() }
    val s = vm.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.AppBg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DyIcon(
                icon = DyIcons.ArrowBack,
                tint = Palette.TextPrimary,
                contentDescription = "返回",
                size = 26.dp,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = 8.dp),
            )
            Text(
                text = "播放历史",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            // 【原为「清空」纯文字】加一个垃圾桶图标，让“破坏性操作”一眼可辨
            // （FR-505 要求破坏性操作有明确视觉区别，图标比只靠红色单通道更稳）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(44.dp)
                    .clickable { vm.requestClear() }
                    .padding(horizontal = 8.dp),
            ) {
                DyIcon(
                    icon = DyIcons.ClearAll,
                    tint = Palette.Danger,
                    size = 16.dp,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Danger,
                )
            }
        }

        if (s.tip.isNotEmpty()) {
            Text(
                text = s.tip,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Danger,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (s.list.isEmpty()) {
            // FR-508：区分「确无记录」与「数据在，但由更高版本写入」
            EmptyView(s.newerNotice.ifEmpty { "暂无播放记录" })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(s.list, key = { "${it.vod_id}_${it.source}_${it.episodeIndex}_${it.updateTime}" }) { item ->
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(14.dp),
                        elevation = 6.dp,
                        liquid = false,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // FR-506：直达该集续播。历史不含媒体地址，
                                    // 故由详情页拉最新线路后自动进入该集。
                                    onOpenDetail(
                                        Routes.detail(
                                            vodId = item.vod_id,
                                            source = item.source,
                                            name = item.vod_name,
                                            pic = item.vod_pic,
                                            vodUri = item.vod_uri ?: "",
                                            resumeEpisode = item.episodeIndex,
                                            autoPlay = true,
                                        )
                                    )
                                }
                                .padding(10.dp),
                        ) {
                            Poster(
                                url = item.vod_pic,
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
                                    text = item.vod_name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Palette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.episodeName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Palette.TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "已看 ${fmtTime(item.position)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Palette.Brand,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (s.showClearConfirm) {
        ConfirmDialog(
            spec = ConfirmSpec.historyClearSpec(),
            onConfirm = { vm.doClear() },
            onDismiss = { vm.dismissClear() },
        )
    }
}
