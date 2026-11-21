package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.dengying.player.core.CmsSources
import com.dengying.player.core.DirectSource
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.navigation.Routes
import com.dengying.player.ui.components.GlassBackButton
import com.dengying.player.ui.components.GlassPage
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.theme.Palette

/** 编译期直播频道列表：点击直接进播放页，不走详情页/搜索聚合。 */
@Composable
fun LiveChannelsScreen(
    onBack: () -> Unit,
    onOpenLive: (String) -> Unit,
) {
    val channels = CmsSources.DIRECT
    GlassPage(
        modifier = Modifier.fillMaxSize(),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Spacer(Modifier.height(56.dp))
                Text(
                    text = "Hululu007 live.m3u 抽样频道，点击直接播放；频道可用性以当前线路为准。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(channels, key = { it.key }) { channel ->
                        LiveChannelCard(channel = channel, onClick = {
                            onOpenLive(
                                Routes.livePlayer(
                                    url = channel.url,
                                    name = channel.name,
                                    source = channel.key,
                                    headers = channel.headers.entries.joinToString(",") { (k, v) -> "$k=$v" },
                                ),
                            )
                        })
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
                    text = "直播频道",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

@Composable
private fun LiveChannelCard(channel: DirectSource, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = 6.dp,
        liquid = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
                Text(
                    "${channel.group} · 直播",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            DyIcon(icon = DyIcons.Play, tint = Palette.Brand, size = 18.dp)
        }
    }
}
