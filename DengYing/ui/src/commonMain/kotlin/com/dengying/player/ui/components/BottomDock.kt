package com.dengying.player.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.theme.Glass
import com.dengying.player.ui.theme.Palette

/**
 * 底部悬浮 Dock（v3 透射玻璃改版，2026-11-08）。
 *
 * 【选中态 · 参照 Kyant0/AndroidLiquidGlass 与 iOS 26 原型】**不用墨色实底**：
 * 一块滑动的高光玻璃面板（[Glass.DockHighlight]，浅色=亮白 90%、深色=白 15%）
 * 以弹簧动画滑到选中项背后；选中项图标+文字用主文字色，未选中只留图标、
 * 次要文字色。深浅色下都是"玻璃上浮起内容"，没有黑块。
 *
 * 【结构】GlassSurface 液态采样（真·透射，底色透过 dock）；面板与图标层
 * 分离——面板只动位置，图标列表不动，切换只重排内容不重建玻璃。
 */
data class DockSpec(
    val key: String,
    val label: String,
    val icon: DyIcon,
)

@Composable
fun BottomDock(
    items: List<DockSpec>,
    selectedKey: String,
    onSelect: (DockSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier
            // 底部全局悬浮 + 导航条避让：dock"浮"在内容层上（内容满铺）
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 10.dp, top = 4.dp)
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = 16.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cell = maxWidth / items.size
            val selIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
            val highlightX by animateDpAsState(
                targetValue = cell * selIndex,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                label = "dockHighlightX",
            )
            // 滑动高光面板：玻璃件，不是墨色胶囊
            Box(
                Modifier
                    .offset(x = highlightX)
                    .width(cell)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Glass.DockHighlight, RoundedCornerShape(24.dp))
                    .border(1.dp, Glass.Border, RoundedCornerShape(24.dp)),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val selected = item.key == selectedKey
                    Box(
                        Modifier
                            .width(cell)
                            .fillMaxHeight()
                            // 整格可点（标准触达），无 ripple——玻璃上不出水波
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DyIcon(
                                icon = item.icon,
                                tint = if (selected) Palette.TextPrimary else Palette.TextSecondary,
                                size = 21.dp,
                            )
                            if (selected) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Palette.TextPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
