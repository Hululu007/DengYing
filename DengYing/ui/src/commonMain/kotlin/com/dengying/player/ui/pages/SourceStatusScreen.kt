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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.domain.source.SourceHealthRules
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.GlassBackButton
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.GlassPage
import com.dengying.player.ui.theme.Palette
import com.dengying.player.ui.viewmodel.SourceStatusViewModel

@Composable
fun SourceStatusScreen(
    onBack: () -> Unit,
    vm: SourceStatusViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        val deps = UiDepsHolder.get()
        SourceStatusViewModel(repo = deps.sources, store = deps.store)
    },
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.reload() }
    val s = vm.state

    // 【透射源】顶栏是 overlay、源卡片列表是 content（兄弟层）：
    // 列表上滑时从顶栏背后穿过，玻璃返回环与「刷新」透出真实卡片。
    GlassPage(
        modifier = Modifier.fillMaxSize(),
        content = {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // 让出顶栏高度（顶栏在 overlay 层，不参与内容排版）
        Spacer(Modifier.height(56.dp))

        // ---- FR-601：改动计数 + 恢复默认 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (s.overridden > 0) "已修改 ${s.overridden} 项源" else "源清单为默认状态",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (s.overridden > 0) {
                Text(
                    text = "恢复默认",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Brand,
                    modifier = Modifier
                        .height(44.dp)
                        .clickable { vm.restoreDefaults() }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        // ---- 兜底提示：全部可疑时说明"仍会全试" ----
        if (s.fallbackAll) {
            Text(
                text = "所有源本次都出现过失败，仍会全部尝试；若持续如此，请检查网络",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Palette.SearchBg, RoundedCornerShape(8.dp))
                    .padding(14.dp),
            )
        }

        // ---- 口径说明：健康分不持久化，避免误导 ----
        Text(
            text = "健康分只在本会话内累计，重启后重置；来源分级（tier）为人工实测快照",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )

        if (s.tip.isNotEmpty()) {
            Text(
                text = s.tip,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Danger,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
                items(s.rows, key = { it.key }) { row ->
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(14.dp),
                        elevation = 6.dp,
                        liquid = false,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = row.key,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Palette.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            // 健康态
                            Text(
                                text = if (row.health.state == SourceHealthRules.State.Suspicious) "可疑" else "正常",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (row.health.state == SourceHealthRules.State.Suspicious) {
                                    Palette.Danger
                                } else {
                                    Palette.Brand
                                },
                                modifier = Modifier
                                    .background(Palette.SearchBg, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                            // FR-601：启用开关。停用的源用次要色标出，与"健康可疑"是两个维度
                            if (row.changed) {
                                Text(
                                    text = "已改",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.Danger,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                            Switch(
                                checked = row.enabled,
                                onCheckedChange = { vm.toggle(row.key) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Palette.CardBg,
                                    checkedTrackColor = Palette.Brand,
                                    uncheckedThumbColor = Palette.CardBg,
                                    uncheckedTrackColor = Palette.TextSecondary,
                                ),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }

                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                text = "分数 ${row.health.score}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.TextSecondary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Text(
                                text = "样本 ${row.health.samples}/${SourceHealthRules.HEALTH_WINDOW}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.TextSecondary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Text(
                                text = "最近 ${outcomeText(row.health)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.TextSecondary,
                            )
                        }

                        Text(
                            text = row.health.note,
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            text = row.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        }
                    }
                }
            }
    }
        },
        overlay = {
            // ---- 顶栏（玻璃 overlay：采样下方列表）----
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
                    text = "源状态",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                // 原为「刷新」纯文字：加一个矢量刷新图标
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(44.dp)
                        .clickable { vm.reload() }
                        .padding(horizontal = 12.dp),
                ) {
                    DyIcon(
                        icon = DyIcons.Refresh,
                        tint = Palette.Brand,
                        size = 15.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "刷新",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Brand,
                    )
                }
            }
        },
    )
}

/** 最近一次结果的可读说法 */
private fun outcomeText(r: SourceHealthRules.Row): String = when (r.lastOutcome) {
    SourceHealthRules.Outcome.Ok -> "成功"
    SourceHealthRules.Outcome.SuccessNoData -> "无数据"
    SourceHealthRules.Outcome.Failed -> "失败"
}
