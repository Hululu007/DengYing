package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.core.Episode
import com.dengying.player.core.VideoItem
import com.dengying.player.player.PlaybackProbe
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.GlassBackButton
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.InlineNotice
import com.dengying.player.ui.components.InlinePlayer
import com.dengying.player.ui.components.Poster
import com.dengying.player.ui.components.glassPageBackdrop
import com.dengying.player.ui.platform.ConsumePortraitOrientation
import com.dengying.player.ui.theme.Palette
import com.dengying.player.ui.viewmodel.AltItem
import com.dengying.player.ui.viewmodel.DetailViewModel

@Composable
fun DetailScreen(
    vodId: Long,
    source: String,
    name: String,
    pic: String,
    vodUri: String,
    resumeEpisode: Int,
    autoPlay: Boolean,
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    /**
     * 本页是否仍是导航栈顶（P-06）。
     *
     * 【为什么需要它】"返回后内嵌位残留"此前靠 `ON_STOP` → `hidden=true` 处理，
     * 但退出转场期间本页仍在组合、仍在渲染，而 `ON_STOP` 的触发时机由
     * Navigation 的生命周期推进决定，**不保证早于转场的第一帧**。
     * 一旦它晚到，转场期间内嵌位就会画出占位图形或控制栏——用户看到"残留"。
     *
     * 现在由导航状态直接回答"本页还是不是栈顶"：不是栈顶 → 内嵌位立刻只留黑底。
     * 这个判据在 `popBackStack()` 调用的那一刻就成立，与转场动画、与生命周期
     * 时序都无关，不需要任何固定延时（docs/08 §2 约束 6）。
     *
     * 默认 true：非导航宿主（预览、测试）调用时不改变既有行为。
     */
    pageActive: Boolean = true,
) {
    val vm: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "detail_$source$vodId",
    ) {
        val deps = UiDepsHolder.get()
        DetailViewModel(
            vodId = vodId,
            source = source,
            name = name,
            pic = pic,
            vodUri = vodUri,
            resumeEpisode = resumeEpisode,
            autoPlayRequested = autoPlay,
            repo = deps.sources,
            store = deps.store,
            engineHost = deps.engineHost,
            deps = deps,
        )
    }
    val s = vm.state

    // 【FR-711 方向收敛】详情页是竖屏形态的宿主：组合期间显式保证竖屏方向。
    // 全屏页把方向设为横屏，返回详情页后 `requestedOrientation` 残留横屏值，
    // 无人改回就会一直横屏——由本页收敛（页面自己知道自己是竖屏形态）。
    ConsumePortraitOrientation()

    // 导航副作用：VM 里只置一个「待跳转路由」，跳转由 UI 层执行（VM 不持有导航器）
    val route = vm.pendingPlayerRoute
    androidx.compose.runtime.LaunchedEffect(route) {
        if (route != null) {
            vm.consumePlayerRoute()
            onOpenPlayer(route)
        }
    }

    // FR-705：离开详情页的清理由**三个时机**分担，不能混在一起：
    //
    // | 时机 | 该做什么 | 不该做什么 |
    // |---|---|---|
    // | `ON_STOP`（转场开始） | 置 hidden（占位立刻消失） | **不暂停会话**（可能是切全屏） |
    // | `ON_START`（回到本页） | 会话仍在则恢复内嵌绑定 | 不重新解析 |
    // | `onDispose`（真正销毁） | 若无全屏页在播则结束会话 | — |
    //
    // 【为什么 ON_STOP 不再 pause】单会话架构下"切全屏"也是离开本页，
    // 此时暂停会话 = 用户反馈的"点全屏直接不播放"。
    // 会话是否该停，由 `onDispose` 依据"是否还有全屏页在播"决定。
    val backStackEntry = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current
        as? androidx.navigation.NavBackStackEntry
    androidx.compose.runtime.DisposableEffect(backStackEntry) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    // 转场开始：占位立刻消失。**会话不动**——可能只是切全屏。
                    vm.clearInlineUi()
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    // 回到本页：会话仍在则恢复内嵌绑定（从全屏返回的主路径）
                    vm.resumeInlineIfPlaying()
                }
                else -> Unit
            }
        }
        backStackEntry?.lifecycle?.addObserver(observer)
        onDispose {
            backStackEntry?.lifecycle?.removeObserver(observer)
            // 真正销毁：若会话仍归属本页（没有全屏页接管），结束它
            vm.releaseInline()
        }
    }

    // 【这里不用 GlassPage】详情页内容区含内嵌播放位（SurfaceView/TextureView）。
    // 把视频面录进 GraphicsLayer 会采不到画面（SurfaceView 不参与图层合成），
    // 实测风险大于收益，故本页只保留柔光底：顶栏玻璃透出的是页面柔光，
    // 不采样内容。首页/列表页无视频面，才用 GlassPage 做真实内容透射。
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 【透射源】纯色底换成柔光渐变层——玻璃件才有真实内容可透（见 GlassPageBackdrop）
            .glassPageBackdrop(),
    ) {
        // ---- 返回栏 ----
        // 【玻璃化 2026-11-08】原为品牌色实底延伸进状态栏；改版后顶栏融入
        // 背景（Material You 的 blend top bar 形态），内容块自己以悬浮玻璃卡
        // 承担视觉层次，不再靠顶栏压色。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 【原为全角 "‹" 字形】它属于字体里的符号字符，粗细随字重漂移、
            // 左右留白也占宽度（"全角字符 + padding" 容易把标题挤偏）。改矢量箭头。
            GlassBackButton(onClick = onBack, modifier = Modifier.padding(end = 10.dp))
            Text(
                text = s.item?.vod_name ?: name,
                style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 【收藏键：星形改为矢量】原为 “★ / ☆” 字形对——笔画粗细不一、
            // 两个字符的视觉重心也不同，切换时会有细微跳动。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { vm.onToggleFav() }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                DyIcon(
                    icon = if (s.faved) DyIcons.Star else DyIcons.StarOutline,
                    tint = Palette.TextPrimary,
                    size = 17.dp,
                    contentDescription = if (s.faved) "取消收藏" else "收藏",
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (s.faved) "已收藏" else "收藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextPrimary,
                )
            }
        }

            when {
            // 【加载速度：有内容就先画内容】`s.loading` 曾经是第一分支，
            // 于是即便详情已经到手，只要那次本地 IO（收藏态/历史态）没回来，
            // 整页就还是一个转圈——数据在手却不让看。
            s.item != null -> Column(modifier = Modifier.fillMaxSize()) {
                // ---- 内嵌播放位（FR-705）----
                //
                // 位置：**内容上方**（顶栏之下、影片信息之上）——用户明确要求
                // 「在内容上方放一个占位」。放在这里的好处是点下方剧集后，
                // 播放位就在同一屏内，不需要用户再往上找。
                //
                // 【吸顶：不参与滚动】（2026-10-25 真机反馈"上滑的时候内嵌位不变"）
                // 初版把播放位放进整页 verticalScroll 的第一个子项，上滑看剧集时
                // 画面跟着滚出屏幕。现拆成两段：播放位钉在顶部固定不动，
                // 下方内容区（简介/候选/剧集）自己滚——边看边挑集不丢画面。
                //
                // 未选剧集时也**渲染**（显示空态），而不是不渲染：
                // 用户需要知道"这里能播"。
                //
                // 【视图宿主绑定】每次组合都尝试把会话引擎接到这里——
                // 会话在播（含从全屏返回）时，画面就出现在这个占位里。
                SideEffect { vm.attachInlineSurface() }
                InlinePlayer(
                    engine = vm.inlineEngine,
                    title = vm.inlineState.title,
                    isPlaying = vm.inlineState.isPlaying,
                    curTime = vm.inlineState.curTime,
                    totalTime = vm.inlineState.totalTime,
                    buffering = vm.inlineState.buffering,
                    errMsg = vm.inlineState.errMsg,
                    status = vm.inlineState.status,
                    active = vm.inlineState.active,
                    // 【P-06 残留修法】两个来源取或：
                    //  - `hidden`：ON_STOP 置的瞬时态（应用退到后台等）
                    //  - `!pageActive`：本页已不是栈顶（返回、切全屏、跳其它页）
                    // 后者在导航动作发生时立即成立，不依赖转场与生命周期时序。
                    hidden = vm.inlineState.hidden || !pageActive,
                    onTogglePlay = { vm.toggleInlinePlay() },
                    onSeek = { pos -> vm.seekInline(pos) },
                    onEnterFullscreen = { vm.toFullscreen() },
                    modifier = Modifier.background(Color.Black),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    InfoBlock(s.item!!)
                    if (s.altLoading || s.altSources.isNotEmpty()) {
                        AltSourceBlock(s.altSources, s.altLoading) { vm.switchToSource(it) }
                    }
                    EpisodeBlock(
                        s = s,
                        onSelectLine = { vm.selectLine(it) },
                        onToggleOrder = { vm.toggleOrder() },
                        displayEps = vm.displayEps(),
                        realIndexOf = { vm.epRealIndex(it) },
                        isResumeEp = { vm.isResumeEp(it) },
                        // FR-705：点剧集**就地播放**（内嵌），不再跳页。
                        // 跳全屏播放页的入口改为内嵌位的全屏键（见上）。
                        onPlayEpisode = { vm.playInline(it) },
                    )

                    // 底部留白：让最底部的剧集按钮脱离屏幕边缘与系统手势区。
                    //
                    // 【为什么用固定高度而不是 navigationBarsPadding】
                    // 真机实测：加 `navigationBarsPadding()` 后，剧集按钮的可点区域
                    // 出现异常（点击被上方的线路 chip 抢走）——insets 计算改变了
                    // 滚动容器的测量结果，而滚动中的触摸目标随之偏移。
                    // 固定高度的 Spacer 不参与 insets 协商，效果确定、可预期。
                    Spacer(Modifier.height(96.dp))
                }
            }

            // 冷启首帧：路由里已经带了片名和封面（Routes.detail 的 name/pic 参数），
            // 先按真实版式把信息块画出来，比空白转圈更接近"已经开了"的观感。
            // 封面走进程级 PosterCache，从首页点进来的这一张基本已在内存里。
            s.loading -> Column(modifier = Modifier.fillMaxSize()) {
                SkeletonInfoBlock(name = name, pic = pic)
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Palette.Brand, strokeWidth = 3.dp)
                }
            }

            else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = s.errMsg.ifEmpty { "无数据" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

/**
 * 详情未到手时的信息块占位：结构与 [InfoBlock] 一致（110x150 封面 + 右侧文字列），
 * 用路由参数先把片名占上，避免冷启整屏空白。
 */
@Composable
private fun SkeletonInfoBlock(name: String, pic: String) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = 6.dp,
        liquid = false,
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
    ) {
        Row {
            Poster(
                url = pic,
                modifier = Modifier
                    .width(110.dp)
                    .height(150.dp),
                cornerRadius = 8,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                if (name.isNotEmpty()) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "正在获取详情…",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun InfoBlock(item: VideoItem) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = 6.dp,
        liquid = false,
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
    ) {
        Row {
            Poster(
                url = item.vod_pic,
                modifier = Modifier
                    .width(110.dp)
                    .height(150.dp),
                cornerRadius = 8,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = item.vod_name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val remarks = item.vod_remarks
                if (!remarks.isNullOrEmpty()) {
                    Text(
                        text = remarks,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Brand,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                val yearArea = "${item.vod_year ?: ""} ${item.vod_area ?: ""}".trim()
                if (yearArea.isNotEmpty()) {
                    Text(
                        text = yearArea,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val type = item.type_name
                if (!type.isNullOrEmpty()) {
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                val actor = item.vod_actor
                if (!actor.isNullOrEmpty()) {
                    Text(
                        text = "主演：$actor",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val content = item.vod_content
        if (!content.isNullOrEmpty()) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            )
        }
    }
    }
}

/** FR-417：跨源候选块 */
@Composable
private fun AltSourceBlock(
    alts: List<AltItem>,
    loading: Boolean,
    onSwitch: (AltItem) -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = 6.dp,
        liquid = false,
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "其他资源站",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary,
            )
            Text(
                text = if (loading) "查找中…" else "${alts.size} 个",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (a in alts) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(Palette.AppBg, RoundedCornerShape(16.dp))
                        .clickable { onSwitch(a) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = a.sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextPrimary,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun EpisodeBlock(
    s: DetailViewModel.UiState,
    onSelectLine: (Int) -> Unit,
    onToggleOrder: () -> Unit,
    displayEps: List<Episode>,
    realIndexOf: (Int) -> Int,
    isResumeEp: (Int) -> Boolean,
    onPlayEpisode: (Int) -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = 6.dp,
        liquid = false,
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
    ) {
        // FR-309：解析不出剧集时给明确提示，且**不渲染空网格**
        if (!s.epShowGrid) {
            InlineNotice("⚠ ${s.epNotice}", Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
        // FR-506：从观看记录直达失败——停在本页并说明下一步
        if (s.resumeNotice.isNotEmpty()) {
            InlineNotice("⚠ ${s.resumeNotice}", Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
        // 源播放链缺口：显式提示不可播，避免点进去才静默失败
        if (s.playGap.isNotEmpty()) {
            InlineNotice("⚠ ${s.playGap}", Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }

        // 线路选择（单条时不显示）
        if (s.lines.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(s.lines) { idx, line ->
                    val selected = s.lineIdx == idx
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) Palette.Brand else Palette.SearchBg,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { onSelectLine(idx) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = line.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) Color.White else Palette.TextSecondary,
                        )
                    }
                }
            }
        }

        if (s.epShowGrid) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选集（共 ${displayEps.size} 集）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (displayEps.size > 1) {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .background(
                                if (s.descOrder) Palette.Brand else Palette.SearchBg,
                                RoundedCornerShape(18.dp),
                            )
                            .clickable(onClick = onToggleOrder)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (s.descOrder) "倒序 ↓" else "正序 ↑",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (s.descOrder) Color.White else Palette.TextSecondary,
                        )
                    }
                }
            }

            // 剧集网格：**不用 LazyVerticalGrid**
            //
            // 【实测踩坑，必须记录】初版用 `LazyVerticalGrid(userScrollEnabled = false)`
            // 嵌在 `verticalScroll` 里，结果每一项都**点不动**——外层滚动容器拦截了手势，
            // Lazy 网格自己又不滚动，点击事件到不了项上。
            // 同一屏里的线路 chip（LazyRow）与收藏按钮都能点中，只有网格项失灵，
            // 这就是判据。
            //
            // 改法：手动按 4 列分行，用普通 Row/Box。选集数量有限（实测该源 1 集，
            // 长剧最多几百集），且外层已经是 Scroll，嵌套 Lazy 本来就没有收益。
            val perRow = 4
            val rows = (displayEps.size + perRow - 1) / perRow
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (c in 0 until perRow) {
                            val displayIdx = r * perRow + c
                            if (displayIdx >= displayEps.size) {
                                // 占位，保证最后一行左对齐
                                Spacer(Modifier.weight(1f))
                                continue
                            }
                            val ep = displayEps[displayIdx]
                            val real = realIndexOf(displayIdx)
                            val resume = isResumeEp(real)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    // 【FR-709 + 点击失效诊断】高度从 38dp 提到 48dp：
                                    // 更大的热区，同时也是对"点击没反应"的一次结构性改善
                                    .height(48.dp)
                                    .background(
                                        if (resume) Palette.Brand else Palette.SearchBg,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .clickable {
                                        // 【UI 层探针】区分两类失败：
                                        //  a) 本探针出现、无 inline-click-enter → VM 调用链断
                                        //  b) 本探针也不出现 → Compose 手势层没触发
                                        PlaybackProbe.note("ui-episode-click", "displayIdx=$displayIdx real=$real")
                                        onPlayEpisode(real)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ep.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (resume) Color.White else Palette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // 「续」角标：上次看到的一集
                                if (resume) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .background(
                                                Palette.Danger,
                                                RoundedCornerShape(topEnd = 6.dp),
                                            ),
                                    ) {
                                        Text(
                                            text = "续",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
