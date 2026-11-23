package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.core.CmsSources
import com.dengying.player.core.Episode
import com.dengying.player.core.VideoItem
import com.dengying.player.player.PlaybackProbe
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.GlassBackButton
import com.dengying.player.ui.components.GlassChip
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.InlineNotice
import com.dengying.player.ui.components.InlinePlayer
import com.dengying.player.ui.components.Poster
import com.dengying.player.ui.components.rememberSkeletonTextColor
import com.dengying.player.ui.components.glassPageBackdrop
import com.dengying.player.ui.platform.ConsumePortraitOrientation
import com.dengying.player.ui.platform.FullscreenState
import com.dengying.player.ui.platform.PlatformBackHandler
import com.dengying.player.ui.platform.rememberFullscreenState
import com.dengying.player.ui.theme.Palette
import com.dengying.player.ui.viewmodel.AltItem
import com.dengying.player.ui.viewmodel.DetailViewModel
import com.dengying.player.data.download.DownloadCenter

/**
 * 固定宽度按钮里的单行标签样式：量出超过可用宽度就按 0.5sp 步进缩小。
 *
 * 【为什么需要这一步】选集区两枚按钮改成等宽 76dp 后，`maxLines = 1` +
 * `softWrap = false` 只保证不换行，不保证放得下。真机（MuMu，1080×1920，280dpi）
 * 把字体放大到 2.0x 时，「正序 ↑」实测宽 133px = 76dp，正好顶满按钮，箭头右半边
 * 被裁掉。用 `TextMeasurer` 先量后定字号，比「把按钮改宽」更能守住等宽这条需求。
 */
@Composable
private fun fittedLabelStyle(text: String, maxWidth: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    val maxPx = with(density) { maxWidth.toPx() }
    return remember(text, base, maxPx, density.fontScale) {
        var style = base
        var guard = 0
        while (guard++ < 24 && style.fontSize.value > 8f &&
            measurer.measure(text, style).size.width > maxPx
        ) {
            style = style.copy(fontSize = (style.fontSize.value - 0.5f).sp)
        }
        style
    }
}

@Composable
fun DetailScreen(
    vodId: Long,
    source: String,
    name: String,
    pic: String,
    vodUri: String,
    resumeEpisode: Int,
    autoPlay: Boolean,
    /** 发现页平滑交接：已播位置（ms）。-1 = 无交接。 */
    startAtMs: Long = -1L,
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
    /** 直播直达：跳过详情加载，进入后即全屏，返回直接回频道列表。 */
    liveMode: Boolean = false,
    liveUrl: String = "",
    liveHeaders: String = "",
) {
    val vm: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        // 带上交接位置：同一部片从发现页带断点再进，也必须是一个全新的 VM，
        // 否则复用旧 VM 会保留 handoffUsed/autoPlayed/adoptedFromDiscovery，
        // 新一次交接被当成"已经消费过"，回页既不按新位置起播也不恢复原位续播。
        key = "detail_${source}_${vodId}_$startAtMs",
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
            handoffStartAtMs = startAtMs,
            repo = deps.sources,
            store = deps.store,
            engineHost = deps.engineHost,
            deps = deps,
            live = liveMode,
            liveUrl = liveUrl,
            liveHeaders = liveHeaders,
        )
    }
    val s = vm.state

    // 【FR-711 方向收敛】详情页是竖屏形态的宿主：组合期间显式保证竖屏方向。
    // 全屏页把方向设为横屏，返回详情页后 `requestedOrientation` 残留横屏值，
    // 无人改回就会一直横屏——由本页收敛（页面自己知道自己是竖屏形态）。
    //
    // 【纯屏幕形态例外（FR-711）】内嵌全屏形态下本页自己请求横屏（见全屏分支），
    // 若这里仍无条件收敛，进入形态的下一帧就被顶回竖屏——方向横竖抖动。
    // 故全屏形态时不收敛；退出形态后本调用重新生效，兜底收敛竖屏。
    if (!vm.screenOnlyFullscreen) ConsumePortraitOrientation()

    // FR-711：平台方向/系统栏状态。进入/退出全屏形态时与 VM 状态成对调用：
    // 进入 = vm.enterScreenFullscreen() + fullscreen.enter(landscape=true)，
    // 退出 = vm.exitScreenFullscreen() + fullscreen.exit()。
    // 【注册顺序即返回键优先级】OnBackPressedDispatcher 后注册者优先：
    // 本实例先注册，全屏分支里的 PlatformBackHandler 后注册——全屏形态下
    // 返回键先被「退形态」消费，不会直接退出详情页。
    val fullscreen = rememberFullscreenState()

    // FR-711：形态状态驱动的平台成对请求。手动点全屏键的路径在回调里显式
    // 成对调用；**历史直达**（tryAutoPlay → playInline + enterScreenFullscreen）
    // 没有点击事件，方向/系统栏必须由状态变化驱动——这里统一兜底。
    // enter 是幂等请求（对同方向重复设置无害），与回调里的显式调用不冲突。
    androidx.compose.runtime.LaunchedEffect(vm.screenOnlyFullscreen) {
        if (vm.screenOnlyFullscreen) fullscreen.enter(landscape = true)
    }

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

    // 【这里不用 GlassPage】详情页内容区含内嵌播放位，渲染面已是 TextureView
    // （参与正常图层合成，可被 GraphicsLayer 采样）；但视频层透射采样在真机上的
    // 效果与开销尚未验证，故本页维持现状：只保留柔光底，顶栏玻璃透出的是页面柔光，
    // 不采样内容。首页/列表页无视频面，才用 GlassPage 做真实内容透射。
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 【透射源】纯色底换成柔光渐变层——玻璃件才有真实内容可透（见 GlassPageBackdrop）
            .glassPageBackdrop(),
    ) {
        // ---- FR-711 纯屏幕形态：同一会话的全屏视图 ----
        //
        // 【与路由式全屏的区别】不跳 PLAYER 页、不新建 PlayerViewModel、
        // 不做任何播放操作（设计：docs/superpowers/specs/
        // 2026-11-15-screen-only-fullscreen-design.md）。同一个 InlinePlayer
        // 实例铺满全屏，控制栏与手势全部保留；退出方式：返回键或「退出全屏」键。
        //
        // 【为什么仍然 attachInlineSurface】本页仍是引擎的视图宿主，
        // 全屏形态只是布局变化，表面登记语义不变。
        if (vm.screenOnlyFullscreen) {
            DetailFullscreenView(vm = vm, fullscreen = fullscreen, pageActive = pageActive, onBack = onBack, liveMode = liveMode)
        }

        // ---- 返回栏 ----
        // 【玻璃化 2026-11-08】原为品牌色实底延伸进状态栏；改版后顶栏融入
        // 背景（Material You 的 blend top bar 形态），内容块自己以悬浮玻璃卡
        // 承担视觉层次，不再靠顶栏压色。
        // 【全屏形态下不画返回栏】整屏交给播放器（FR-711）
        if (!vm.screenOnlyFullscreen) Row(
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

            // 【全屏形态下不画内容区】（FR-711）整屏只有播放器
            if (!vm.screenOnlyFullscreen) when {
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
                // 【从发现页平移到详情的画面】
                // 发现页点击时把「正在播放的那块画面」的屏幕矩形交给本页；
                // 这里把它从卡片位置动画到自己的播放位（320ms），中途不重载媒体，
                // 所以看起来就是同一块画面在移动。两个窗口都是 16:9，
                // 等比缩放 + 平移即可精确对齐，不需要改布局。
                var heroFrom by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                var slotRect by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                val heroAnim = remember { androidx.compose.animation.core.Animatable(1f) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val r = DiscoverHero.take()
                    if (r != null) {
                        heroFrom = androidx.compose.ui.geometry.Rect(
                            left = r[0].toFloat(),
                            top = r[1].toFloat(),
                            right = (r[0] + r[2]).toFloat(),
                            bottom = (r[1] + r[3]).toFloat(),
                        )
                    }
                }
                androidx.compose.runtime.LaunchedEffect(heroFrom, slotRect) {
                    val from = heroFrom
                    val to = slotRect
                    if (from != null && to != null && to.width > 0f) {
                        heroAnim.snapTo(0f)
                        heroAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 320,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing,
                            ),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { slotRect = it.boundsInWindow() }
                        .graphicsLayer {
                            val from = heroFrom
                            val to = slotRect
                            if (from != null && to != null && to.width > 0f) {
                                val s0 = from.width / to.width
                                val v = heroAnim.value
                                val scale = s0 + (1f - s0) * v
                                scaleX = scale
                                scaleY = scale
                                translationX = (from.center.x - to.center.x) * (1f - v)
                                translationY = (from.center.y - to.center.y) * (1f - v)
                            }
                        },
                ) {
                    InlinePlayer(
                    engine = vm.inlineEngine,
                    title = vm.inlineState.title,
                    episodeName = vm.inlineState.episodeName,
                    episodeIndex = vm.inlineState.episodeIndex,
                    episodeCount = vm.inlineEpisodeCount(),
                    live = vm.inlineState.live,
                    zoomMode = vm.inlineState.zoomMode,
                    onZoomModeChange = { vm.setInlineZoomMode(it) },
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
                    canPrev = vm.inlineHasEp(-1),
                    canNext = vm.inlineHasEp(1),
                    onTogglePlay = { vm.toggleInlinePlay() },
                    onSeek = { pos -> vm.seekInline(pos) },
                    onPrev = { vm.inlineSwitchEpisode(-1) },
                    onNext = { vm.inlineSwitchEpisode(1) },
                    onBoostStart = { vm.beginInlineBoost() },
                    onBoostEnd = { vm.endInlineBoost() },
                    // FR-711：进入纯屏幕形态。VM 只记形态状态；平台方向/系统栏
                    // 由本层请求——两步必须成对（见 rememberFullscreenState 处注释）。
                    onEnterFullscreen = {
                        vm.enterScreenFullscreen()
                        fullscreen.enter(landscape = true)
                    },
                    modifier = Modifier.background(Color.Black),
                )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    InfoBlock(s.item!!)
                    if (s.altLoading || s.altSources.isNotEmpty()) {
                        AltSourceBlock(
                            alts = s.altSources,
                            loading = s.altLoading,
                            currentSourceKey = s.sourceKey,
                            switchingTo = s.switchingTo,
                            onSwitch = { vm.switchToSource(it) },
                        )
                    }
                    EpisodeBlock(
                        s = s,
                        onSelectLine = { vm.selectLine(it) },
                        onToggleOrder = { vm.toggleOrder() },
                        displayEps = vm.displayEps(),
                        realIndexOf = { vm.epRealIndex(it) },
                        isResumeEp = { vm.isResumeEp(it) },
                        playingEp = s.playingEp,
                        // FR-705：点剧集**就地播放**（内嵌），不再跳页。
                        // 跳全屏播放页的入口改为内嵌位的全屏键（见上）。
                        onPlayEpisode = { vm.playInline(it) },
                        downloadOf = { s.downloads[it] },
                        onDownloadEpisode = { vm.downloadEpisode(it) },
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
                title = name,
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
                    color = rememberSkeletonTextColor(),
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
                title = item.vod_name,
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
    currentSourceKey: String,
    switchingTo: String,
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
                text = "播放源",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary,
            )
            Text(
                text = when {
                    switchingTo.isNotEmpty() -> "切换到 $switchingTo…"
                    loading -> "查找中…"
                    else -> "另有 ${alts.size} 个"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        // 源清单可到上百条：一次只铺 20 个 chip（含「当前」），其余收进弹窗，
        // 否则 FlowRow 会把详情页推得极长、每次换源都要滚半天。
        var showAll by remember { mutableStateOf(false) }
        val (visibleCount, hidden) = splitSourceChips(alts.size)
        val visible = alts.take(visibleCount)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val currentName = CmsSources.byKey(currentSourceKey)?.name ?: currentSourceKey
            if (currentName.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$currentName · 当前",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            for (a in visible) {
                GlassChip(
                    text = altLabel(a),
                    onClick = { onSwitch(a) },
                )
            }
            if (hidden > 0) {
                GlassChip(
                    text = "更多 $hidden 个 ›",
                    onClick = { showAll = true },
                )
            }
        }
        if (showAll) {
            AllSourcesDialog(
                alts = alts,
                onSwitch = { showAll = false; onSwitch(it) },
                onDismiss = { showAll = false },
            )
        }
    }
    }
}

/** 详情页一次最多展示的播放源条数（含「当前」chip） */
private const val MAX_VISIBLE_SOURCES = 20

/**
 * 候选 chip 文案：集数已后台探测到就带上「全 N 集」。
 *
 * 换源前用户最想知道的就是"哪条更全"——以前要点进去才发现是 1 集试看线。
 * [AltItem.epCount] 为 -1 表示还没探测到，只显示源名，不写猜的数字。
 */
private fun altLabel(a: AltItem): String =
    if (a.epCount > 0) "${a.sourceName} · 全${a.epCount}集" else a.sourceName

/**
 * 播放源 chip 切分：返回 (直接铺出的候选数, 折叠进「更多」的候选数)。
 *
 * 上限含「当前」那枚黑底 chip，所以候选只能占 `max - 1` 个位置；
 * 候选不多于上限时全部铺出，隐藏数为 0（不出现「更多」）。
 */
internal fun splitSourceChips(
    totalAlternatives: Int,
    max: Int = MAX_VISIBLE_SOURCES,
): Pair<Int, Int> {
    val room = (max - 1).coerceAtLeast(0)
    val visible = totalAlternatives.coerceIn(0, room)
    return visible to (totalAlternatives - visible)
}

/** 「更多」弹窗：列出全部跨源候选，点条目直接换源。 */
@Composable
private fun AllSourcesDialog(
    alts: List<AltItem>,
    onSwitch: (AltItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全部播放源 · ${alts.size}") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            ) {
                items(alts) { a ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSwitch(a) }
                            .padding(horizontal = 4.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = altLabel(a),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "切换",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Brand,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeBlock(
    s: DetailViewModel.UiState,
    onSelectLine: (Int) -> Unit,
    onToggleOrder: () -> Unit,
    displayEps: List<Episode>,
    realIndexOf: (Int) -> Int,
    isResumeEp: (Int) -> Boolean,
    /** 会话正在播的真实集序号（-1 = 无）。优先于历史续播点显示选中态。 */
    playingEp: Int,
    onPlayEpisode: (Int) -> Unit,
    downloadOf: (Int) -> DownloadCenter.Item?,
    onDownloadEpisode: (Int) -> Unit,
) {
    // 下载模式：先点「下载」进入，再点任一剧集即离线保存。
    // 原先只有长按隐藏手势，用户不知道能下载；长按仍保留作为快捷方式。
    var downloadMode by remember { mutableStateOf(false) }
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
        // 源播放链缺口：显式提示不可播，避免点进去才静默失败。
        // 文案走统一用语——playGap 原文是维护者视角的内部描述，不给用户看。
        if (s.playGap.isNotEmpty()) {
            InlineNotice("⚠ ${com.dengying.player.ui.viewmodel.PLAYBACK_UNAVAILABLE_TEXT}", Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
        if (s.downloadMsg.isNotEmpty()) {
            Text(
                text = s.downloadMsg,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Brand,
                modifier = Modifier.padding(bottom = 10.dp),
            )
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "选集（共 ${displayEps.size} 集）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        text = if (downloadMode) "点击剧集下载到本机" else "点「下载」后选集即可离线保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (downloadMode) Palette.Brand else Palette.TextSecondary,
                    )
                }
                // 下载模式开关：比长按手势可见，也让「点了就下载」有明确的进入/退出
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .height(36.dp)
                        .background(
                            if (downloadMode) Palette.Brand else Palette.SearchBg,
                            RoundedCornerShape(18.dp),
                        )
                        .clickable {
                            downloadMode = !downloadMode
                            PlaybackProbe.note(
                                "ui-download-mode", "on=$downloadMode",
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val downloadLabel = if (downloadMode) "完成" else "下载"
                    Text(
                        text = downloadLabel,
                        style = fittedLabelStyle(downloadLabel, 74.dp),
                        color = if (downloadMode) Color.White else Palette.TextSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (displayEps.size > 1) {
                    Box(
                        modifier = Modifier
                            .width(76.dp)
                            .height(36.dp)
                            .background(
                                if (s.descOrder) Palette.Brand else Palette.SearchBg,
                                RoundedCornerShape(18.dp),
                            )
                            .clickable(onClick = onToggleOrder),
                        contentAlignment = Alignment.Center,
                    ) {
                        val orderLabel = if (s.descOrder) "倒序 ↓" else "正序 ↑"
                        Text(
                            text = orderLabel,
                            style = fittedLabelStyle(orderLabel, 74.dp),
                            color = if (s.descOrder) Color.White else Palette.TextSecondary,
                            maxLines = 1,
                            softWrap = false,
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
                            // 【选中态】当前在播 > 历史续播点。此前只看续播点，
                            // 点集后网格毫无反馈（用户反馈"选中剧集没有选中UI显示"）。
                            val current = playingEp == real
                            val resume = !current && isResumeEp(real)
                            val selected = current || resume
                            val download = downloadOf(real)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    // 【FR-709 + 点击失效诊断】高度从 38dp 提到 48dp：
                                    // 更大的热区，同时也是对"点击没反应"的一次结构性改善
                                    .height(48.dp)
                                    .background(
                                        if (selected) Palette.Brand else Palette.SearchBg,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (downloadMode) {
                                                // 下载模式：单击即下载，不再起播
                                                PlaybackProbe.note(
                                                    "ui-episode-download",
                                                    "displayIdx=$displayIdx real=$real mode=tap",
                                                )
                                                onDownloadEpisode(real)
                                            } else {
                                                // 【UI 层探针】区分两类失败：
                                                //  a) 本探针出现、无 inline-click-enter → VM 调用链断
                                                //  b) 本探针也不出现 → Compose 手势层没触发
                                                PlaybackProbe.note("ui-episode-click", "displayIdx=$displayIdx real=$real")
                                                onPlayEpisode(real)
                                            }
                                        },
                                        onLongClick = {
                                            PlaybackProbe.note("ui-episode-download", "displayIdx=$displayIdx real=$real mode=long")
                                            onDownloadEpisode(real)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ep.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected) Color.White else Palette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // 角标：当前在播「播」；否则历史续播点「续」
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .background(
                                                Palette.Danger,
                                                RoundedCornerShape(topEnd = 6.dp),
                                            ),
                                    ) {
                                        Text(
                                            text = if (current) "播" else "续",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 3.dp),
                                        )
                                    }
                                }
                                if (download != null) {
                                    val mark = when (download.status) {
                                        DownloadCenter.Status.DONE -> "已下"
                                        DownloadCenter.Status.RUNNING ->
                                            "${(download.progress * 100).toInt()}%"
                                        DownloadCenter.Status.QUEUED -> "等待"
                                        DownloadCenter.Status.FAILED -> "重试"
                                    }
                                    Text(
                                        text = mark,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Color.White else Palette.Brand,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(horizontal = 3.dp),
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

/**
 * FR-711 纯屏幕形态的全屏视图：同一会话、同一 [InlinePlayer] 实例铺满宿主。
 *
 * 【为什么抽成独立 Composable】主 [DetailScreen] 已是超大 Compose 函数，
 * 叠加全屏分支后 Kotlin/Native（CPF LLVM 19）侧 clang++ -cc1 在 X64 目标
 * 稳定 0xC0000005 崩溃（Arm64 抽出 InlinePlayer 顶栏/倍速面板后已通过，
 * X64 仍崩）。拆出本函数后主函数体缩小；改这段时不要合回主函数。
 *
 * 【语义】不跳 PLAYER 页、不新建 PlayerViewModel、零播放操作；返回键只退
 * 形态不退页面。iOS/OHOS/JVM 的 PlatformBackHandler 为空实现，退出依赖
 * 「退出全屏」键（平台差异见设计文档）。
 */
@Composable
private fun DetailFullscreenView(
    vm: DetailViewModel,
    fullscreen: FullscreenState,
    pageActive: Boolean,
    onBack: () -> Unit,
    liveMode: Boolean = false,
) {
    SideEffect { vm.attachInlineSurface() }
    InlinePlayer(
        engine = vm.inlineEngine,
        title = vm.inlineState.title,
        episodeName = vm.inlineState.episodeName,
        episodeIndex = vm.inlineState.episodeIndex,
        episodeCount = vm.inlineEpisodeCount(),
        live = vm.inlineState.live,
        zoomMode = vm.inlineState.zoomMode,
        onZoomModeChange = { vm.setInlineZoomMode(it) },
        isPlaying = vm.inlineState.isPlaying,
        curTime = vm.inlineState.curTime,
        totalTime = vm.inlineState.totalTime,
        buffering = vm.inlineState.buffering,
        errMsg = vm.inlineState.errMsg,
        status = vm.inlineState.status,
        active = vm.inlineState.active,
        hidden = vm.inlineState.hidden || !pageActive,
        canPrev = vm.inlineHasEp(-1),
        canNext = vm.inlineHasEp(1),
        onTogglePlay = { vm.toggleInlinePlay() },
        onSeek = { pos -> vm.seekInline(pos) },
        onPrev = { vm.inlineSwitchEpisode(-1) },
        onNext = { vm.inlineSwitchEpisode(1) },
        onBoostStart = { vm.beginInlineBoost() },
        onBoostEnd = { vm.endInlineBoost() },
        fullscreen = true,
        // 全屏形态下控制栏的进全屏键已切换为「退出全屏」，本回调不会触发
        onEnterFullscreen = {},
        onExitFullscreen = {
            vm.exitScreenFullscreen()
            fullscreen.exit()
            if (liveMode) onBack()
        },
        // FR-711 全屏形态扩展（2026-11-15 用户反馈）：倍速 + 投屏键
        speed = vm.inlineSpeed,
        onSpeedChange = { vm.applyInlineSpeed(it) },
        // 「投屏」= 升级到播放页：投屏状态机只在 PlayerViewModel，
        // 详情页不复制它；交接走 FR-705 既有会话 adopt（不重解析）。
        onCast = { vm.openPlayerForCast() },
        modifier = Modifier.fillMaxSize(),
    )
    PlatformBackHandler(enabled = true) {
        vm.exitScreenFullscreen()
        fullscreen.exit()
        if (liveMode) onBack()
    }
}
