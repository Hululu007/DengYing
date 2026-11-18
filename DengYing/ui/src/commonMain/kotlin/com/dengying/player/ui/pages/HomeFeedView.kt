package com.dengying.player.ui.pages

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dengying.player.core.HOME_HOT_TITLE
import com.dengying.player.core.HomeSection
import com.dengying.player.core.VideoItem
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.Poster
import com.dengying.player.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * 首页版块 → 首屏结构。
 *
 * 热播版块拆成「Banner 轮播」+「双列宫格」两段；其余版块原样作为横滑 rail。
 * 纯映射，单测直接跑（见 ui/src/commonTest/.../HomeFeedLayoutTest.kt）。
 */
internal data class HomeFeedLayout(
    val hero: List<VideoItem>,
    val grid: List<VideoItem>,
    val rails: List<HomeSection>,
)

internal fun buildHomeFeedLayout(sections: List<HomeSection>): HomeFeedLayout {
    if (sections.isEmpty()) return HomeFeedLayout(emptyList(), emptyList(), emptyList())
    val hotIndex = sections.indexOfFirst { it.title == HOME_HOT_TITLE }.let { if (it >= 0) it else 0 }
    val hot = sections[hotIndex].items
    // Banner 只轮 3 张，其余进双列宫格：12 条热播正好铺满一屏半，且不和 Banner 重复。
    val hero = hot.take(3)
    val grid = hot.drop(hero.size)
    val rails = sections.filterIndexed { i, s -> i != hotIndex && s.items.isNotEmpty() }
    return HomeFeedLayout(hero = hero, grid = grid, rails = rails)
}

/**
 * 推荐页新布局（对齐 AH 首页的信息层级，保留灯影的深色玻璃质感）。
 *
 * AH 原版：顶部搜索、分类 tab、16:9 Banner、热播双列宫格、分类横滑 rail、底部 Dock。
 * 灯影 v5 已按用户要求去掉首页频道 chips，因此这里只迁移内容区：
 * Banner → 热播宫格 → 分类 rail；顶栏与 Dock 仍由 [IndexScreen] 的悬浮层负责。
 */
@Composable
internal fun HomeFeedView(
    sections: List<HomeSection>,
    topPadding: Dp,
    bottomPadding: Dp,
    state: LazyListState,
    onOpen: (VideoItem) -> Unit,
) {
    val layout = buildHomeFeedLayout(sections)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (layout.hero.isNotEmpty()) {
            item(key = "hero") {
                HeroCarousel(items = layout.hero, onOpen = onOpen)
            }
        }
        if (layout.grid.isNotEmpty()) {
            item(key = "hot-header") {
                SectionHeader(title = HOME_HOT_TITLE, count = layout.hero.size + layout.grid.size)
            }
            items(layout.grid.chunked(2), key = { row -> "hot-${row.first().vod_id}" }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (v in row) {
                        HotGridCard(
                            v = v,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpen(v) },
                        )
                    }
                    // 奇数条时补一个同宽空位，避免最后一张卡被拉成整行宽
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        for (rail in layout.rails) {
            item(key = "rail-header-${rail.title}") {
                SectionHeader(title = rail.title)
            }
            item(key = "rail-${rail.title}") {
                // 无限横滑：与旧版一致取模循环，用户不会滑到尽头
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(count = Int.MAX_VALUE, key = { i -> "loop-${rail.title}-$i" }) { i ->
                        val v = rail.items[i % rail.items.size]
                        RailCard(v = v, onClick = { onOpen(v) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCarousel(items: List<VideoItem>, onOpen: (VideoItem) -> Unit) {
    val pager = rememberPagerState { items.size }
    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5_000)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(0.dp),
            pageSpacing = 0.dp,
        ) { page ->
            HeroCard(
                v = items[page],
                modifier = Modifier.padding(horizontal = 18.dp),
                onClick = { onOpen(items[page]) },
            )
        }
        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (i in items.indices) {
                    Box(
                        modifier = Modifier
                            .size(width = if (i == pager.currentPage) 14.dp else 5.dp, height = 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (i == pager.currentPage) Palette.Brand
                                else Palette.TextSecondary.copy(alpha = 0.35f)
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(v: VideoItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Poster(
            url = v.vod_pic,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 22,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.10f),
                        1f to Color.Black.copy(alpha = 0.86f),
                    )
                ),
        )
        if (!v.vod_remarks.isNullOrBlank()) {
            Badge(
                text = v.vod_remarks.orEmpty(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = v.vod_name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = metaLine(v)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** 16:9 双列卡：封面在上，片名 + 一句数据侧副标题在下。 */
@Composable
private fun HotGridCard(v: VideoItem, modifier: Modifier, onClick: () -> Unit) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = 6.dp,
        liquid = false,
    ) {
        Column(modifier = Modifier.clickable(onClick = onClick)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Poster(
                    url = v.vod_pic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    cornerRadius = 16,
                )
                if (!v.vod_remarks.isNullOrBlank()) {
                    Badge(
                        text = v.vod_remarks.orEmpty(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        compact = true,
                    )
                }
            }
            Text(
                text = v.vod_name,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 9.dp, end = 9.dp, top = 7.dp),
            )
            Text(
                text = tagline(v),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 9.dp, end = 9.dp, top = 1.dp, bottom = 8.dp),
            )
        }
    }
}

/** 分类 rail 的竖版小卡。 */
@Composable
private fun RailCard(v: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
    ) {
        Poster(
            url = v.vod_pic,
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp),
            cornerRadius = 14,
        )
        Text(
            text = v.vod_name,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        val sub = railMeta(v)
        if (sub.isNotEmpty()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(15.dp)
                .background(Palette.Brand, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = "$count 部",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
            )
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(
                horizontal = if (compact) 5.dp else 7.dp,
                vertical = if (compact) 2.dp else 3.dp,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
        )
    }
}

/** Banner 用：类型 · 年份 · 首个地区（空字段跳过）。 */
private fun metaLine(v: VideoItem): String = listOf(v.type_name, v.vod_year, primaryArea(v))
    .mapNotNull { it?.takeIf(String::isNotBlank) }
    .joinToString(" · ")

/** 宫格副标题：类型 · 年份；更新状态已在封面徽章里，不重复。 */
private fun tagline(v: VideoItem): String =
    listOf(v.type_name, v.vod_year).mapNotNull { it?.takeIf(String::isNotBlank) }.joinToString(" · ")

/** Rail 副标题：年份 · 首个地区；分类名已由版块标题表达。 */
private fun railMeta(v: VideoItem): String =
    listOf(v.vod_year, primaryArea(v)).mapNotNull { it?.takeIf(String::isNotBlank) }.joinToString(" · ")

private fun primaryArea(v: VideoItem): String? =
    v.vod_area?.split('/')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

/**
 * 首拉骨架：Banner + 两行双列卡 + 一条 rail，尺寸与真实内容一致，避免加载完成时跳动。
 */
@Composable
internal fun HomeFeedSkeleton(topPadding: Dp, bottomPadding: Dp) {
    val transition = rememberInfiniteTransition(label = "home-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home-skeleton-alpha",
    )
    val block = Palette.TextSecondary.copy(alpha = alpha * 0.35f)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        item(key = "sk-hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(block),
            )
        }
        items(listOf("sk-h1", "sk-h2")) { key ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(2) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(128.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(block),
                    )
                }
            }
        }
        item(key = "sk-rail") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .width(112.dp)
                            .height(156.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(block),
                    )
                }
            }
        }
    }
}
