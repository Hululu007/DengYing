package com.dengying.player.ui.pages
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.core.CmsSources
import com.dengying.player.data.net.HlsUtil
import com.dengying.player.player.PlayerEngine
import com.dengying.player.ui.platform.VideoSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random
import com.dengying.player.core.DiscoverItem
import com.dengying.player.core.VideoItem
import com.dengying.player.ui.components.BottomDock
import com.dengying.player.ui.components.DockSpec
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.ErrorView
import com.dengying.player.ui.components.GlassChip
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.LoadingView
import com.dengying.player.ui.components.Poster
import com.dengying.player.ui.navigation.Routes
import com.dengying.player.ui.theme.Glass
import com.dengying.player.ui.theme.Palette
import com.dengying.player.ui.viewmodel.IndexViewModel

/**
 * `HashMap<分类下标, (firstVisibleItemIndex, firstVisibleItemScrollOffset)>` 的保存器。
 *
 * 只用 `Int`：`IntArray` 是 Bundle 原生支持的类型，不必再序列化。
 * 扁平化成一维（每 3 个一组：下标、index、offset），比 `listSaver` 少一层装箱。
 */
private val ScrollPositionsSaver = androidx.compose.runtime.saveable.Saver<
    HashMap<Int, Pair<Int, Int>>, IntArray>(
    save = { map ->
        val out = IntArray(map.size * 3)
        var i = 0
        for ((k, v) in map) {
            out[i++] = k
            out[i++] = v.first
            out[i++] = v.second
        }
        out
    },
    restore = { arr ->
        val map = HashMap<Int, Pair<Int, Int>>(arr.size / 3)
        var i = 0
        while (i + 2 < arr.size) {
            map[arr[i]] = arr[i + 1] to arr[i + 2]
            i += 3
        }
        map
    },
)

/**
 * 首页（玻璃化改版，2026-11-08）。
 *
 * 【结构：内容垫底、控件悬浮】旧版是 Column（顶栏→标签→内容），内容永远在
 * 顶栏下方起排；玻璃化后改为 Box：Pager 铺满全屏，顶栏/标签以玻璃胶囊
 * **悬浮**在内容之上，列表从状态栏下就开始滚动、从玻璃件背后穿过——
 * 即主流安卓（Android 15-16 观感）的悬浮控件布局。
 *
 * 【为什么不用真背景模糊】见 [GlassSurface] 的注释：不引第三方 SDK 的前提下
 * 用「半透明填充 + 细边 + 柔影」表达玻璃，滚动帧率不受模糊拖累。
 *
 * 【第一 tab 是「推荐」】天翼版块流（FR-612），见 [RecommendPage]；
 * 其余六个聚合分类照旧走 [VideoGrid]。
 */
@Composable
fun IndexScreen(
    /** 参数是「带到搜索页的初始关键词」，空串 = 普通进搜索页 */
    onOpenSearch: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSourceStatus: () -> Unit,
    /** 「我的」页点登录：进独立登录页，而不是就地展开表单 */
    onOpenLogin: () -> Unit,
    onOpenDetail: (String) -> Unit,
    vm: IndexViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        val deps = UiDepsHolder.get()
        IndexViewModel(repo = deps.sources, cache = deps.pageCache, detailCache = deps.detailCache)
    },
) {
    val s = vm.state
    // ---- v5 四窗格导航（首页/分类/发现/我的）----
    // 导航事实源是 pagerState 本身：Dock 选中项直接读 pagerState.targetPage
    // （拖动中高光即跟随），点 Dock 只 animateScrollToPage——不再有独立 dockTab。
    // VM 的 currentTab 只服务数据加载（onShown 按需拉取）。
    // 分类窗格里选中的聚合分类（对应 s.categories 下标，1..6，0 是推荐位）
    var catIdx by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(1)
    }
    // 发现窗格：随机片池的种子（下拉刷新 reshuffle）
    var discoSeed by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(0)
    }
    // 发现窗格的跨页重建状态（滚动位置/解析缓存/断点）。
    // 必须 remember 在**本层**：DiscoverPane 切到首页时会被销毁（pager 只预组合
    // 相邻一页），状态 remember 在页内的结果就是"去首页再回来从头播"（用户实测）。
    val discoverResume = remember { DiscoverResume() }
    // 每个分类**各持一个滚动状态**（FR-701 的配套修复）。
    //
    // 【真机反馈的 bug-1】初版不给 `LazyVerticalGrid` 传 state，它用内部默认状态，
    // 而该状态在**同一个组合位置**上会被复用 —— 于是切分类时滚动位置被继承：
    // 电影滑到第 20 个，切到电视剧仍停在第 20 个位置。
    //
    // 【真机反馈的 bug-2】进详情页再返回，首页回到顶部。
    // 根因：`IndexScreen` 挂在 `composable(Routes.INDEX)` 上，跳详情时整棵子树
    // 离开组合，`remember` 出来的 `LazyGridState` 一起销毁；返回时重建为 index=0。
    // 修法：位置本身存进 `rememberSaveable`——NavHost 会为每个返回栈条目保存
    // saveable 状态，返回时原样恢复，再用它作为 `LazyGridState` 的初始位置。
    //
    // 【为什么记「位置」而不是记 `LazyGridState` 本身】
    // `LazyGridState` 是活对象，只能活在组合里；跨组合保存它得靠 Saver，
    // 而 Saver 存进去的其实也只是一对 (index, offset)。直接存这对数字更直白，
    // 且能顺带把「切回该分类记住上次位置」的原有行为一并保住。
    val savedScroll = androidx.compose.runtime.saveable.rememberSaveable(
        saver = ScrollPositionsSaver,
    ) { HashMap<Int, Pair<Int, Int>>() }
    // 每个分类各一个滚动状态（网格页用 LazyGridState、推荐页用 LazyListState），
    // 由 Pager 的每一页各自持有。用分类下标作为 key：不同分类的状态互不干扰。
    // 首次创建时用已保存的位置做初值。位置的变化在各自那一页里用 snapshotFlow 记录。
    val gridStates = androidx.compose.runtime.remember(s.categories.size) {
        HashMap<Int, androidx.compose.foundation.lazy.grid.LazyGridState>()
    }
    val listStates = androidx.compose.runtime.remember(s.categories.size) {
        HashMap<Int, androidx.compose.foundation.lazy.LazyListState>()
    }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { 4 }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 【Pager → 数据】滑停之后按窗格触发加载：
    // 首页吃推荐槽位(0)，分类/发现各自吃所选分类槽位，我的不拉数据。
    LaunchedEffect(pagerState.settledPage) {
        when (pagerState.settledPage) {
            0 -> vm.onShown(0)
            1 -> vm.onShown(catIdx)
            // 发现页：预热六个内容分类给混合片池当主体（已启动的分类自动跳过）
            2 -> vm.warmAllCategories()
        // 我的页不拉数据
        }
    }
    // 底部悬浮 Dock 的内容避让：Dock 本体高 60 + 悬浮边距 16 + 安全区。
    // 分类网格/版块流的 contentPadding.bottom 用它——最后一张卡要能滚出 Dock 顶。
    val bottomReserve = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 76.dp

    // 【发现页混合片池 v5.6】主体 = 六个分类的聚合条目（采集站多源混合，直链几乎
    // 必播，失败率低）；辅料 = tv189 版块运营精选（封面/画质好，但 VIP 独播多、
    // 跨源救不回比例高 → 只限量掺入约三分之一）。辅料内部保留免费率加权
    // （少儿/动画版块排前），主体验证自动播放尽量一击即中。
    //
    // 【v5.6 狂刷修复】旧版 poolStamp 逐分类计 items.size：warmAllCategories
    // 预热时 6 个分类**陆续到货**，每到一个整池重洗一次（真机实测 7+ 次
    // "内容连变直到稳定"）。改成**一次成型**：六分类全部到货（或 8s 超时兜底，
    // 防个别分类始终无货卡死）才洗一次；期间发现页显示"备片"加载态。
    // 换批（下拉刷新 discoSeed++）仍整池重洗。
    val allWarm = (1..6).all { s.cat(it).items.isNotEmpty() }
    var warmTimeout by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    androidx.compose.runtime.LaunchedEffect(allWarm) {
        if (!allWarm) {
            kotlinx.coroutines.delay(8000)
            warmTimeout = true
        }
    }
    val poolReady = allWarm || warmTimeout
    val picks = remember(poolReady, discoSeed) {
        if (!poolReady) {
            emptyList()
        } else {
            val cms = (1..6)
                .flatMap { s.cat(it).items }
                .distinctBy { it.vod_name ?: it.vod_id.toString() }
            fun freeish(t: String) = t.contains("动画") || t.contains("动漫") || t.contains("少儿") ||
                t.contains("儿童") || t.contains("亲子")
            val tv = run {
                val head = s.cat(0).sections.filter { freeish(it.title) }.flatMap { it.items }
                val tail = s.cat(0).sections.filterNot { freeish(it.title) }.flatMap { it.items }
                // head/tail 各自洗牌后拼接（不能合一起洗——加权会被打散）
                (head.shuffled(Random(discoSeed)) + tail.shuffled(Random(discoSeed + 1)))
                    .distinctBy { it.vod_id }
            }
            val tvLimit = (cms.size / 3).coerceAtLeast(20)
            (cms.shuffled(Random(discoSeed + 7)) + tv.take(tvLimit))
                .distinctBy { it.vod_name ?: it.vod_id.toString() }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.AppBg),
    ) {
        // ---- 内容层（铺满全屏，从玻璃件背后穿过） ----
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 预组合相邻各一页：滑到一半就能看见下一页内容。
            // 取 1 而不是更大的值——每多预组合一页就多一整屏封面要解码。
            beyondViewportPageCount = 1,
        ) { page ->
            // 悬浮层总高：顶栏一行 44 + 边距 ≈ 56dp，加状态栏
            // 悬浮层总高：顶栏一行 44 + 边距 ≈ 56dp，加状态栏。
            // 我的页（page 3）共享顶栏隐藏（v5），只留标题行高 ≈ 8dp 起排。
            val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                (if (page == 3) 8.dp else 56.dp)
            when (page) {
                // ---- 首页：天翼版块流（FR-612） ----
                0 -> {
                    val cs = s.cat(0)
                    val listState = listStates.getOrPut(0) {
                        val p = savedScroll[0]
                        androidx.compose.foundation.lazy.LazyListState(p?.first ?: 0, p?.second ?: 0)
                    }
                    LaunchedEffect(listState) {
                        androidx.compose.runtime.snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }.collect {
                            savedScroll[0] = it
                        }
                    }
                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        isRefreshing = cs.refining,
                        onRefresh = { vm.refresh(0) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        RecommendPage(
                            cs = cs,
                            topPadding = topPad,
                            bottomPadding = bottomReserve,
                            onOpenDetail = onOpenDetail,
                            onRetry = { vm.loadData(0) },
                            state = listState,
                        )
                    }
                }
                // ---- 分类：聚合分类网格（chips 行在窗格内，不再挤顶栏） ----
                1 -> CategoryPane(
                    categories = s.categories,
                    selected = catIdx,
                    onSelect = { idx ->
                        catIdx = idx
                        vm.onShown(idx)
                    },
                    cs = s.cat(catIdx),
                    category = s.categories[catIdx],
                    topPadding = topPad,
                    bottomPadding = bottomReserve,
                    gridState = gridStates.getOrPut(catIdx) {
                        val p = savedScroll[catIdx]
                        androidx.compose.foundation.lazy.grid.LazyGridState(p?.first ?: 0, p?.second ?: 0)
                    },
                    onScroll = { idx, off -> savedScroll[catIdx] = idx to off },
                    onOpenDetail = onOpenDetail,
                    onRetry = { vm.loadData(catIdx) },
                    onNearEnd = { vm.loadMore(catIdx) },
                    onRefresh = { vm.refresh(catIdx) },
                )
                // ---- 发现：随机推荐 + 内嵌自动播放（v5，抖音式单播） ----
                2 -> DiscoverPane(
                    pool = picks,
                    resume = discoverResume,
                    topPadding = topPad,
                    bottomPadding = bottomReserve,
                    onOpenDetail = onOpenDetail,
                    onShuffle = { discoSeed++ },
                    // targetPage 在滑动开始即变 → 开始滑就停声（主流口径）
                    active = pagerState.targetPage == 2,
                    preparing = !poolReady,
                )
                // ---- 我的：个人信息聚合（v5；共享顶栏在本页隐藏，自带页头） ----
                3 -> MineScreen(
                    onOpenFavorites = onOpenFavorites,
                    onOpenHistory = onOpenHistory,
                    onOpenSettings = onOpenSourceStatus,
                    onOpenLogin = onOpenLogin,
                    topPadding = topPad,
                    bottomPadding = bottomReserve,
                )
            }
        }
        // ---- 悬浮层（玻璃顶栏：品牌 + 长条搜索框）----
        // v5：「我的」页整条隐藏（该页自带页头 + 右上角设置钮）；搜索改长条。
        androidx.compose.animation.AnimatedVisibility(
            visible = pagerState.targetPage != 3,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { -it / 2 },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { -it / 2 },
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 【必须补状态栏避让】MainActivity 调了 enableEdgeToEdge()，内容会铺到
                // 状态栏底下。原工程的 EntryAbility 刻意不调 setWindowLayoutFullScreen，
                // 理由正是"开启后顶部按钮会被状态栏遮挡导致点击无效"——本移植踩了同一个坑。
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 玻璃品牌条（44dp 胶囊）。
            // 【v5 修复】内层 Row 撑满胶囊高度再垂直居中——之前 Box 默认
            // TopStart 对齐，文字在胶囊里不居中（用户实测截图）。
            GlassSurface(
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 【品牌标记：矢量灯形】灯形与自适应图标前景 `ic_launcher_foreground.xml`
                    // 同一造型（梯形灯罩 + 顶杆 + 光晕），启动图标与首页标题视觉同源。
                    DyIcon(
                        icon = DyIcons.Lantern,
                        tint = Palette.Brand,
                        size = 20.dp,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = "灯影",
                        style = MaterialTheme.typography.titleLarge,
                        color = Palette.TextPrimary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // 长条搜索框（v5）：占满剩余宽度，玻璃材质，整条可点
            GlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenSearch("") }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DyIcon(
                        icon = DyIcons.Search,
                        tint = Palette.TextSecondary,
                        size = 18.dp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = "搜索片名、演员",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.TextSecondary,
                    )
                }
            }
        }
        }
        // ---- 底部悬浮 Dock（v4）：首页/分类/发现/我的 四窗格 ----
        // 条下的内容从玻璃背后穿过时被 [GlassBackdropState] 采样实时磨砂。
        // v5：选中项绑定 pagerState.targetPage —— 拖动翻页时高光跟着走
        //（spring 动画在 BottomDock 内部平滑追踪），点 Dock 只负责滚动。
        BottomDock(
            items = listOf(
                DockSpec(key = "home", label = "首页", icon = DyIcons.Lantern),
                DockSpec(key = "category", label = "分类", icon = DyIcons.Grid),
                DockSpec(key = "discover", label = "发现", icon = DyIcons.Compass),
                DockSpec(key = "mine", label = "我的", icon = DyIcons.Person),
            ),
            selectedKey = DOCK_KEYS[pagerState.targetPage],
            onSelect = { item ->
                scope.launch {
                    pagerState.animateScrollToPage(DOCK_KEYS.indexOf(item.key).coerceAtLeast(0))
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Dock 项 key，下标与 Pager 页一一对应。 */
private val DOCK_KEYS = listOf("home", "category", "discover", "mine")

/**
 * 「推荐」页：天翼 tv189 首页版块流（FR-612）。
 *
 * 【形态】纵向一条条「版块」，每条 = 版块标题 + 横滑海报条——即主流
 * 影视首页的版块流形态。顶部一条玻璃 tab（天翼侧 8 个频道 tab）。
 *
 * 【与聚合分类页互不干扰】此页吃 [IndexViewModel.CatState.sections]（结构化
 * 版块），聚合页吃 `items`（平铺网格），同一个 VM 槽位里两个子状态。
 */
@Composable
private fun RecommendPage(
    cs: IndexViewModel.CatState,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenDetail: (String) -> Unit,
    onRetry: () -> Unit,
    state: androidx.compose.foundation.lazy.LazyListState,
) {
    when {
        // 首拉中：占位避免闪空
        cs.loading && cs.sections.isEmpty() -> LoadingView()
        // 真失败（网络/源异常）：可重试
        cs.errMsg != null && cs.sections.isEmpty() -> ErrorView(message = cs.errMsg ?: "", onRetry = onRetry)
        // 源未启用或版块拉空：给引导文案，不算失败态
        cs.sections.isEmpty() -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("首页版块暂时没有内容", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
            Text(
                "可在「我的-源状态」里启用天翼源后下拉重试",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        else -> androidx.compose.foundation.lazy.LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            // 顶部垫出悬浮层的空间，底部垫出 Dock 的空间：内容从这里开始露出，
            // 滚动时穿过玻璃背后（Liquid Glass 采样源）
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 【v5】首页不再放频道 tab 行（用户反馈：首页里不要 chips）——
            // cs.homeTabs 数据仍在（VM 缓存），只是不再渲染。
            items(cs.sections.size, key = { "sec-$it" }) { i ->
                val sec = cs.sections[i]
                SectionRail(
                    title = sec.title,
                    items = sec.items,
                    onOpenDetail = onOpenDetail,
                )
            }
        }
    }
}

/** 一条版块：标题 + 横滑海报条（玻璃卡片）。 */
@Composable
private fun SectionRail(
    title: String,
    items: List<VideoItem>,
    onOpenDetail: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 版块名左侧一根品牌色短竖条，材料 3 的小节头形态
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .background(Palette.Brand, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 【无限横滑 v5.6】版块条数是运营给的固定值（首播影院 151、少儿 26…），
            // 用户反馈"向右滑是有限的，我要无限"——取模循环展示（同发现页观感），
            // key 用绝对下标保证唯一且稳定，取数 items[i % size] 环形取。
            items(count = Int.MAX_VALUE, key = { i -> "loop-$i" }) { i ->
                val v = items[i % items.size]
                RailCard(v = v, onClick = {
                    onOpenDetail(
                        Routes.detail(
                            vodId = v.vod_id,
                            source = v._source ?: "",
                            name = v.vod_name ?: "",
                            pic = v.vod_pic ?: "",
                            vodUri = v.vod_uri ?: "",
                        )
                    )
                })
            }
        }
    }
}

/** 版块里的横滑小卡：玻璃底 + 竖版海报 + 单行标题。 */
@Composable
private fun RailCard(v: VideoItem, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .width(116.dp)
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        elevation = 6.dp,
        // 内容卡不做液态采样：成本∝面积，全屏几十张卡采样会拖帧（见 GlassBackdrop 注释）
        liquid = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Poster(
                url = v.vod_pic,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                cornerRadius = 14,
            )
            Text(
                text = v.vod_name,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 分类玻璃胶囊已提升为公共组件 [com.dengying.player.ui.components.GlassChip]，
 * 顶部分类条与天翼频道 tab 共用。
 */

/**
 * 聚合分类网格（原 `VideoGrid`，玻璃化后更名）：3 列海报网格 + 无限加载。
 * v4：豆瓣榜单头移入独立的「发现」窗格，此页只管分类内容。
 */
@Composable
private fun PageContent(
    cs: IndexViewModel.CatState,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenDetail: (String) -> Unit,
    onRetry: () -> Unit,
    onNearEnd: () -> Unit,
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
) {
    when {
        cs.loading && cs.items.isEmpty() -> LoadingView()
        cs.errMsg != null && cs.items.isEmpty() -> ErrorView(message = cs.errMsg ?: "", onRetry = onRetry)
        cs.items.isEmpty() -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("暂无内容，下拉刷新试试", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
        }
        else -> {
            // 【触底预加载】不设"加载更多"按钮：滚到离底 6 个格位就静默续页，
            // 手感与主流 App 一致。放进 `snapshotFlow` 是因为布局信息是快照态，
            // collect 会在每次可见集合变化时重新给值，天然覆盖"加载完又滚"的场景。
            LaunchedEffect(state, cs.items.size, cs.hasMore) {
                if (!cs.hasMore) return@LaunchedEffect
                androidx.compose.runtime.snapshotFlow {
                    state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                }.collect { last ->
                    if (last >= cs.items.size - 6 && !cs.loading) onNearEnd()
                }
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                state = state,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding, top = topPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = cs.items.size,
                    key = { cs.items[it].vod_id },
                ) { i ->
                    VideoCard(v = cs.items[i], onClick = {
                        onOpenDetail(
                            Routes.detail(
                                vodId = cs.items[i].vod_id,
                                source = cs.items[i]._source ?: "",
                                name = cs.items[i].vod_name ?: "",
                                pic = cs.items[i].vod_pic ?: "",
                                vodUri = cs.items[i].vod_uri ?: "",
                            )
                        )
                    })
                }
                if (cs.hasMore) {
                    item(key = "load-more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cs.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 首页海报卡：玻璃底、圆角海报、集数/评分角标、单行标题。 */
@Composable
private fun VideoCard(v: VideoItem, onClick: () -> Unit) {
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
                .clickable(onClick = onClick),
        ) {
            Box {
                Poster(
                    url = v.vod_pic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    cornerRadius = 14,
                )
                // 角标（"更新至12集"/"高清"之类），压在海报左下
                if (!v.vod_remarks.isNullOrBlank()) {
                    Text(
                        text = v.vod_remarks ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(
                                androidx.compose.ui.graphics.Color(0x99000000),
                                RoundedCornerShape(5.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = v.vod_name,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 分类窗格（v4）：窗格内自己的玻璃 chips 行 + 聚合分类网格。
 *
 * 【为什么 chips 下沉】旧版把 7 个分类 chips 挤在顶栏下，与品牌条/推荐频道
 * tab 叠成三层。主流 app（爱奇艺/腾讯视频）的分类页都是「页内自己的分类
 * 头 + 内容区」结构，顶栏只留全局件。chips 选中态走 [GlassChip] 玻璃样式。
 */
@Composable
private fun CategoryPane(
    categories: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    cs: IndexViewModel.CatState,
    category: String,
    topPadding: Dp,
    bottomPadding: Dp,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onScroll: (Int, Int) -> Unit,
    onOpenDetail: (String) -> Unit,
    onRetry: () -> Unit,
    onNearEnd: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = cs.refining,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                // 分类分段控件（v5.2）：与底部 Dock 同款交互——玻璃胶囊容器 +
                // 滑动高光（animateDpAsState 弹簧）+ 内容均分（spaceBetween 语义，
                // 固定 6 分类每项 weight(1f)），不再是滚动 chips 行。
                GlassSurface(
                    modifier = Modifier
                        .padding(top = topPadding, start = 12.dp, end = 12.dp)
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    BoxWithConstraints(Modifier.fillMaxHeight()) {
                        val count = categories.size - 1 // 下标 0 是「推荐」位，不在本页
                        val cell = maxWidth / count
                        val hlX by androidx.compose.animation.core.animateDpAsState(
                            targetValue = cell * (selected - 1).coerceIn(0, count - 1),
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.9f,
                                stiffness = 380f,
                            ),
                            label = "catHl",
                        )
                        // 高光在下层：先画高光胶囊，再叠文字行
                        Box(
                            Modifier
                                .offset(x = hlX)
                                .width(cell)
                                .fillMaxHeight()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Glass.DockHighlight)
                                .border(1.dp, Glass.Border, RoundedCornerShape(18.dp)),
                        )
                        Row(Modifier.fillMaxSize()) {
                            for (i in 1 until categories.size) {
                                val idx = i
                                val sel = selected == idx
                                val tag = remember(idx) { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = tag,
                                            indication = null,
                                        ) { onSelect(idx) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = categories[idx],
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (sel) Palette.TextPrimary else Palette.TextSecondary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                PageContent(
                    cs = cs,
                    // 网格已在 chips 之下（Column 布局），只需垫过 chips 底边距
                    topPadding = 8.dp,
                    bottomPadding = bottomPadding,
                    onOpenDetail = onOpenDetail,
                    onRetry = onRetry,
                    onNearEnd = onNearEnd,
                    state = gridState,
                )
            }
        }
    }
    // 滚动位置记录：PageContent 内部不便回传，在这里用状态对象观察
    LaunchedEffect(gridState) {
        androidx.compose.runtime.snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (idx, off) -> onScroll(idx, off) }
    }
}

/** 发现页无限流：每批追加的条数（滚近尾部 +1 批） */
private const val DISCOVER_BATCH = 9

/** 池子前 [n] 个不重复项的数量（首批游标初值：首批同步填充消耗掉的池子长度） */
private fun countFirstUnique(pool: List<VideoItem>, n: Int): Int {
    if (pool.isEmpty()) return 0
    val seen = HashSet<Long>()
    var i = 0
    while (i < pool.size && seen.size < n) {
        seen.add(pool[i].vod_id)
        i++
    }
    return i
}

/**
 * 发现窗格（v5.3）：随机推荐 + 内嵌自动播放的**无限流**列表。
 *
 * 【片池】[IndexScreen] 把首页 tv189 版块条目全量洗牌成 [pool]（下拉刷新重洗，
 * [onShuffle]）；本窗格按批（[DISCOVER_BATCH]）从池里追加显示——滚近尾部自动续批，
 * 池子取尽就取模循环（洗过一轮的顺序重来），观感上无限。
 *
 * 【自动播放判定（主流口径）】滚动停止后，取视口中心最近、可见面积过半的
 * 项解析起播；拖动中不播。单引擎天然保证同屏只有一个声音。
 */
/**
 * 发现页的「跨页重建」状态持有者。
 *
 * 【为什么需要】pager 只预组合相邻一页（`beyondViewportPageCount = 1`）：
 * 从发现页（第 2 页）切到首页（第 0 页）时，发现页**整棵子树被销毁**，
 * 页内所有 `remember` 状态（滚动位置、解析缓存、正在播的项、断点位置）全丢。
 * 原先这些都 remember 在 [DiscoverPane] 内部 → 切回发现页从第一张重新开始、
 * 播放也从 0 开始（用户实测反馈「去首页再回来续播状态就没了」）。
 *
 * 持有者 remember 在 [IndexScreen]（常驻组合），状态于是跨页销毁存活：
 * 切回来仍是同一张卡、同一滚动位置、同一断点续播。
 *
 * 只在内存里，不落盘：进程重启回到首屏是合理行为（主流短视频页同款）。
 */
private class DiscoverResume {
    /** 列表滚动位置（项下标 + 项内偏移）。 */
    val listIndex = androidx.compose.runtime.mutableIntStateOf(0)
    val listOffset = androidx.compose.runtime.mutableIntStateOf(0)
    /** 当前自动播放的项（-1 = 无）。 */
    val playingIdx = androidx.compose.runtime.mutableIntStateOf(-1)
    /** 引擎里装的是不是本窗格这一项（-1 = 没起播过）。 */
    val startedIdx = androidx.compose.runtime.mutableIntStateOf(-1)
    /** 各项断点（下标 → ms）。 */
    val paused = androidx.compose.runtime.mutableStateMapOf<Int, Long>()
    /** 解析结果缓存：下标 → (直链, headers, 是否试看链)。跨重建复用，免二次解析。 */
    val resolved = androidx.compose.runtime.mutableStateMapOf<Int, Triple<String, String, Boolean>>()
}

@Composable
private fun DiscoverPane(
    pool: List<VideoItem>,
    resume: DiscoverResume,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpenDetail: (String) -> Unit,
    onShuffle: () -> Unit,
    // 【v5.6 声音泄漏修复】本窗格是否为 pager 前台页。Dock 滑走时页面仍在
    // 组合（beyondViewportPageCount 预组合），原实现只在 playingIdx 变化时
    // pause——滑到分类/首页后自动播的片子继续出声（真机实测）。前台门：
    // 失焦即 pause，回焦自动续播。
    active: Boolean,
    // 【v5.6】六分类预热中（池子未成型）——空池时显示"备片"加载态而非"没内容"。
    preparing: Boolean,
) {
    // 滚动位置跨页重建：从持有者恢复初值，滚动时回写（切到首页再回来仍在原处）
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = resume.listIndex.intValue,
        initialFirstVisibleItemScrollOffset = resume.listOffset.intValue,
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (i, o) ->
                resume.listIndex.intValue = i
                resume.listOffset.intValue = o
            }
    }
    val deps = UiDepsHolder.get()
    // 【ohos 桩防崩】engine() 在 ohos 上是显式抛错桩（OH_AVPlayer 未接入），
    // 组合期抛出 = 未捕获异常杀进程（faultlog cppcrash 实录：点发现页闪退）。
    // 拿不到就退化 NullPlayerEngine：只浏览不自动播，VideoSurface 渲染空画面。
    // Android 侧 engine() 恒成功，runCatching 不改变任何行为。
    val engine = remember {
        runCatching { deps.engineHost.engine() }
            .getOrElse { com.dengying.player.player.NullPlayerEngine }
    }
    // 【无限流】显示列表 = 池子按批追加；cursor 记住池子消费到哪（取模循环）。
    // 首批在 remember 初始化里同步填好（之后追加都在协程里做，不在组合期改状态）。
    val items = remember(pool) {
        mutableStateListOf<VideoItem>().also { list ->
            var tries = 0
            while (list.size < DISCOVER_BATCH && tries < pool.size * 2) {
                val v = pool[tries % pool.size]
                if (list.none { it.vod_id == v.vod_id }) list.add(v)
                tries++
            }
        }
    }
    // 池子消费游标：首批占了前 DISCOVER_BATCH 个不重复项，从池头继续数
    val cursor = remember(pool) { androidx.compose.runtime.mutableIntStateOf(countFirstUnique(pool, DISCOVER_BATCH)) }
    // 追加一批：从游标处取不重复的项，池子取尽取模循环（观感无限）
    fun addBatch() {
        if (pool.isEmpty()) return
        var added = 0
        var tries = 0
        while (added < DISCOVER_BATCH && tries < pool.size * 2) {
            val v = pool[cursor.intValue % pool.size]
            cursor.intValue++
            tries++
            if (items.none { it.vod_id == v.vod_id }) {
                items.add(v)
                added++
            }
        }
    }
    // 解析结果缓存：下标 -> (直链, headers json, 是否试看链)；url 为空串 = 解析失败
    // （换源也救不回）。只缓存成功项会永久转圈——失败也要落袋。试看标记给角标
    // （v5.6：tv189 免登录片跨源救不回时不再默默只给 5 分钟）。
    // 跨页重建复用（持有者里，池子换了由 IndexScreen 的 pick 变化自然作废——
    // 池子变化时下面 inFlight/slots 重建，resolved 的下标语义与 items 一致，
    // 而 items 只取决于 pool，故同 pool 重建后下标仍然对齐）
    val resolved = resume.resolved
    // 解析在飞标记：防同一项被「可见即解析」重复发起（SnapshotStateList 主线程读写）
    val inFlight = remember(pool) { mutableStateListOf<Int>() }
    // 解析并发闸：同时只放 2 条全链解析。可见 2.5 项全并发时每项要发 6-10 个
    // 请求（详情+换链+跨源搜索/候选），20+ 请求挤 6 个出网位 → 排队 13s（实测
    // probe net-queue），中心项反而最后就绪。限流 + 中心近的先入闸。
    val resolveSlots = remember(pool) { kotlinx.coroutines.sync.Semaphore(2) }
    // 池子换了（下拉换一批）：旧下标语义全部作废，清掉缓存与断点，
    // 否则会把上一批的直链/断点套到新一批的卡上（同下标不同片）。
    var seenPool by remember { mutableStateOf(pool) }
    LaunchedEffect(pool) {
        if (seenPool !== pool) {
            seenPool = pool
            resume.resolved.clear()
            resume.paused.clear()
            resume.startedIdx.intValue = -1
            resume.playingIdx.intValue = -1
        }
    }

    // 当前自动播放的列表项：视口中心最近、可见面积过半的那条（滚动停止后判定）
    var playingIdx by resume.playingIdx
    // 视频画面是否在屏上（控制引擎的 surface 引用计数，防止后台偷解码）
    var surfaceOn by remember { mutableStateOf(false) }
    fun setSurface(on: Boolean) {
        if (on != surfaceOn) {
            surfaceOn = on
            if (on) deps.engineHost.surfaceActive() else deps.engineHost.surfaceInactive()
        }
    }
    suspend fun resolveChainOf(idx: Int) = runCatching {
        com.dengying.player.playback.resolvePlayChain(
            repo = deps.sources,
            raw = discoverRawUrl(deps, items[idx]),
            source = items[idx]._source ?: "",
            vodId = items[idx].vod_id,
            vodName = items[idx].vod_name ?: "",
            episodeIndex = 0,
            ioDispatcher = com.dengying.player.platform.ioDispatcher,
            altLimit = 3, // 发现页批量预解析：候选只试 3 个，防出网闸拥塞
            // 批量预解析不走"先播试看再热升级"：这里在后台等全片更划算——
            // 缓存里直接是全片，中心项落定时起播零等待（用户看不见）。
            waitForFullSource = true,
        )?.let { Triple(it.url, it.headers, it.previewOnly) }
    }.getOrNull()

    // 【可见即解析】v5.4 核心：别等中心项落定才开始解析——跨源兜底一条就要
    // 1-3s，落定再解析用户要干等 10s（实测 probe：net-queue 排队 5.5s）。
    // 可见项（一屏 ~2.5 部）提前全链解析，中心项落定时结果多半已在袋里 → 即刻起播。
    LaunchedEffect(pool) {
        if (pool.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.map { it.index }.sorted()
        }.collect { visible ->
            val li = listState.layoutInfo
            val center = li.viewportStartOffset + li.viewportSize.height / 2
            // 中心近的先解析（信号量 FIFO，先 launch 先入闸 → 中心项最先就绪）
            visible
                .map { idx -> idx to (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }?.let { kotlin.math.abs(it.offset + it.size / 2 - center) } ?: Int.MAX_VALUE) }
                .sortedBy { it.second }
                .forEach { (idx, _) ->
                    if (idx < items.size && resolved[idx] == null && idx !in inFlight) {
                        inFlight.add(idx)
                        launch {
                            resolveSlots.withPermit {
                                val r = resolveChainOf(idx)
                                resolved[idx] = r ?: Triple("", "", false)
                            }
                            inFlight.remove(idx)
                        }
                    }
                }
        }
    }

    // 【surface 引用跟随前台】active=false（预组合/滑走）即释放引用计数，
    // 防后台窗格偷解码；回前台再领。原先挂在 pool effect 里首启时领一次，
    // 但预组合窗格也会首启 → 释放时机错乱，统一收口到 active。
    LaunchedEffect(active) {
        setSurface(active)
    }

    // 【续播断点】各片暂停时的播放位置（下标 → ms）。切走/换片时记下当前片的
    // 位置，回来带位置续播，而不是从头播。跨页重建存活（见 [DiscoverResume]）。
    val pausedPositions = resume.paused
    var startedIdx by resume.startedIdx

    // 【中心项起播】playingIdx / active 变化：先停旧声（切中心/滑走都不许
    // 旧片继续响），再等该项解析落袋（可见即解析已在跑）→ 起播。
    // 等待期间滚走：LaunchedEffect(key) 结构化取消，旧播放意图作废；滚回
    // （或从别的窗格滑回来）→ 协程重启命中缓存立即续播。
    LaunchedEffect(playingIdx, active) {
        val idx = playingIdx
        // 被切走的片记断点：滑到新中心、或整窗格失焦，都要留位置
        if (startedIdx >= 0 && (startedIdx != idx || !active)) {
            pausedPositions[startedIdx] = engine.currentPosition
        }
        engine.pause()
        if (!active || idx < 0 || idx >= items.size) return@LaunchedEffect
        val hit = snapshotFlow { resolved[idx] }.filterNotNull().first()
        if (hit.first.isEmpty()) return@LaunchedEffect // 救不回：卡片给「看不了」提示
        // 【无缝续播】媒体还是本片的（只是 Dock 切页暂停过）→ play() 直接续：
        // 不重载、不缓冲、不闪黑，如同全屏播放器里暂停再继续。只有媒体被
        // 借走（进过详情/播放页，engine.currentUrl 已变）或换到别的片，
        // 才 setMedia(断点) 重载。
        if (startedIdx == idx && engine.currentUrl == hit.first) {
            engine.play()
            return@LaunchedEffect
        }
        engine.setMedia(hit.first, hit.second, pausedPositions[idx] ?: 0L)
        engine.play()
        startedIdx = idx
    }

    // 【中心判定（主流口径：B 站/微博内嵌视频流）】滚动**停止**后才动作：
    // 取视口中心最近的项，且可见面积 >= 55%（防边缘半张卡误播）。
    LaunchedEffect(pool) {
        if (pool.isEmpty()) return@LaunchedEffect
        fun visibleFraction(info: androidx.compose.foundation.lazy.LazyListItemInfo): Float {
            val li = listState.layoutInfo
            val top = maxOf(info.offset, li.viewportStartOffset)
            val bottom = minOf(info.offset + info.size, li.viewportStartOffset + li.viewportSize.height)
            return ((bottom - top).coerceAtLeast(0)).toFloat() / info.size
        }
        snapshotFlow {
            listState.isScrollInProgress
        }.collect { scrolling ->
            if (scrolling) return@collect // 拖动中不换片
            val info = listState.layoutInfo
            val center = info.viewportStartOffset + info.viewportSize.height / 2
            val best = info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
            playingIdx = if (best != null && visibleFraction(best) >= 0.55f) best.index else -1
        }
    }
    // 【无限流追加】滚近尾部（倒数第 4 项进入视口）就再追加一批
    LaunchedEffect(pool) {
        if (pool.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }.collect { last ->
            if (last >= items.size - 4) addBatch()
        }
    }
    // 离开发现页（切 Dock/退出）：记断点 + 释放 surface 引用。
    // 页面被销毁（切到首页/分类 → 超出 pager 预组合范围）时，断点写进
    // [DiscoverResume]，切回来才能从原位续播（原先状态随页面一起丢，从头播）。
    DisposableEffect(pool) {
        onDispose {
            if (startedIdx >= 0) pausedPositions[startedIdx] = engine.currentPosition
            setSurface(false)
        }
    }
    if (pool.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (preparing) {
                // 六分类预热中（v5.6 一次成型）：转圈等待，成型后一次到位不再狂刷
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Palette.TextSecondary,
                )
                Text(
                    "正在为你备片…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                Text("先去首页逛一圈，再来发现好片", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
                Text(
                    "发现页从首页内容里随机抽选",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        return
    }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onShuffle,
        modifier = Modifier.fillMaxSize(),
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, top = topPadding, end = 12.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items.size) { idx ->
                val item = items[idx]
                val hit = resolved[idx]
                DiscoverVideoCard(
                    item = item,
                    resolving = playingIdx == idx && hit == null,
                    playing = playingIdx == idx && hit != null && hit.first.isNotEmpty(),
                    failed = playingIdx == idx && hit != null && hit.first.isEmpty(),
                    preview = playingIdx == idx && hit != null && hit.third,
                    engine = engine,
                    onOpenDetail = onOpenDetail,
                )
            }
        }
    }
}

/**
 * 发现页单片卡（v5.1）：**16:9 内嵌播放窗**——不是满页海报。
 * 视频画面/海报都约束在 16:9 窗口里，信息条在窗下（不压画面），
 * 卡片整体即「一个内嵌播放位」的大小，贴顶居中排。
 * 点卡片 = 进详情（完整选集/简介在详情页，发现页只管「刷到即看」）。
 */
@Composable
private fun DiscoverVideoCard(
    item: VideoItem,
    resolving: Boolean,
    playing: Boolean,
    failed: Boolean,
    preview: Boolean,
    engine: PlayerEngine,
    onOpenDetail: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            elevation = 10.dp,
        ) {
            Column {
                // 16:9 播放窗：视频与海报同一几何，起播瞬间无跳变。
                // 【onOpenDetail 收的是完整路由串】（NavGraph 直接 nav.navigate）——
                // 传裸 ID 会崩（Navigation destination cannot be found）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clickable {
                            onOpenDetail(
                                com.dengying.player.ui.navigation.Routes.detail(
                                    vodId = item.vod_id,
                                    source = item._source ?: "",
                                    name = item.vod_name ?: "",
                                    pic = item.vod_pic ?: "",
                                    vodUri = item.vod_uri ?: "",
                                )
                            )
                        },
                ) {
                    if (playing) {
                        VideoSurface(engine = engine, modifier = Modifier.fillMaxSize())
                    } else {
                        com.dengying.player.ui.components.Poster(
                            url = item.vod_pic ?: "",
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 0,
                        )
                    }
                    if (resolving) {
                        // 【必须 size 而非 height】只约束高度会让宽高不等，
                        // 圆环几何中心偏移，视觉上「整个组件在绕圈」而非笔刷转
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                    if (failed) {
                        Text(
                            text = "这部暂时看不了 · 下拉换一批",
                            style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    // 试看链：点进详情可换全片源（交叉信息，不吓退）
                    if (preview) {
                        Text(
                            text = "试看 · 详情页可看全片",
                            style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                                .background(
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                // 信息条：窗下，玻璃卡同底色
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = item.vod_name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            listOfNotNull(item.vod_year, item.type_name).joinToString(" · ").ifBlank { null }?.let {
                                append(it)
                                append("  ")
                            }
                            append("轻触观看完整影片 ›")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * 发现页取原始集地址：详情 → vod_play_url 第一条线路的第一集（"线路A$$$线路B"、
 * "集名$url#集名$url"）。后续解析统一走 [resolvePlayChain]（全链含跨源换线
 * 兜底）——「解析只有一个入口」，发现页不再自写解析：§16.45 实录，自写解析
 * （只换链不兜底）导致「详情能播、发现页却说看不了」。
 */
private suspend fun discoverRawUrl(
    deps: com.dengying.player.ui.UiDeps,
    item: VideoItem,
): String {
    val key = item._source ?: return ""
    val src = CmsSources.byKey(key) ?: return ""
    val detail = runCatching {
        deps.sources.fetchDetail(src, item.vod_id)
    }.getOrNull() ?: return ""
    return detail.vod_play_url
        ?.split("$$$")
        ?.firstOrNull()
        ?.split("#")
        ?.firstOrNull()
        ?.substringAfter('$')
        ?.takeIf { it.isNotBlank() && it.contains("://") }
        ?: ""
}
