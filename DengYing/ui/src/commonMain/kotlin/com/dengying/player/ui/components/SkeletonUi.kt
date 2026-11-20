package com.dengying.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.theme.Palette

/** 首页与其他列表页共用的骨架节奏：900ms 往返呼吸，色值沿用首页原参数。 */
@Composable
fun rememberSkeletonColor(): androidx.compose.ui.graphics.Color {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    return Palette.TextSecondary.copy(alpha = alpha * 0.35f)
}

/** 详情页短提示文字用：沿用同一呼吸节奏，但保持可读性（不按大色块透明度压暗）。 */
@Composable
fun rememberSkeletonTextColor(): androidx.compose.ui.graphics.Color {
    val transition = rememberInfiniteTransition(label = "skeleton-text")
    val alpha by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-text-alpha",
    )
    return Palette.TextSecondary.copy(alpha = alpha)
}

/** 矩形骨架块，圆角与底色跟随主题。 */
@Composable
fun SkeletonBlock(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    radius: Dp = 8.dp,
) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(color))
}

/** 分类页 3 列海报骨架：140dp 海报 + 单行标题，与真实 VideoCard 对齐。 */
@Composable
fun SkeletonGrid(
    color: androidx.compose.ui.graphics.Color,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding, top = topPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(9) {
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                SkeletonBlock(color, Modifier.fillMaxWidth().height(140.dp), radius = 14.dp)
                SkeletonBlock(color, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).height(12.dp), radius = 3.dp)
            }
        }
    }
}

/** 搜索结果骨架：80x110 海报 + 右侧两行文字，与 ResultRow 对齐。 */
@Composable
fun SkeletonResultRows(color: androidx.compose.ui.graphics.Color) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 60.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(6) {
            Row(
                modifier = Modifier.fillMaxWidth().background(
                    Palette.CardBg.copy(alpha = 0.5f),
                    RoundedCornerShape(14.dp),
                ).padding(10.dp),
            ) {
                SkeletonBlock(color, Modifier.width(80.dp).height(110.dp), radius = 6.dp)
                Column(
                    modifier = Modifier.weight(1f).height(110.dp).padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBlock(color, Modifier.fillMaxWidth().height(16.dp), radius = 3.dp)
                    SkeletonBlock(color, Modifier.fillMaxWidth(0.65f).height(12.dp), radius = 3.dp)
                    Spacer(Modifier.weight(1f))
                    SkeletonBlock(color, Modifier.width(60.dp).height(12.dp), radius = 3.dp)
                }
            }
        }
    }
}

/** 首页版块骨架：Banner + 两行双列卡 + 一条 rail，尺寸与真实内容一致。 */
@Composable
fun HomeFeedSkeleton(topPadding: Dp, bottomPadding: Dp) {
    val block = rememberSkeletonColor()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        item(key = "sk-hero") {
            SkeletonBlock(
                block,
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).aspectRatio(16f / 9f),
                radius = 22.dp,
            )
        }
        items(2) { _ ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(2) {
                    SkeletonBlock(block, Modifier.weight(1f).height(128.dp), radius = 16.dp)
                }
            }
        }
        item(key = "sk-rail") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(3) {
                    SkeletonBlock(block, Modifier.width(112.dp).height(156.dp), radius = 14.dp)
                }
            }
        }
    }
}
