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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import com.dengying.player.core.DiscoverItem
import com.dengying.player.core.VideoItem
import com.dengying.player.ui.components.BottomDock
import com.dengying.player.ui.components.DiscoverSkeleton
import com.dengying.player.ui.components.DockSpec
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.ErrorView
import com.dengying.player.ui.components.GlassChip
import com.dengying.player.ui.components.HomeFeedSkeleton
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.Poster
import com.dengying.player.ui.components.SkeletonGrid
import com.dengying.player.ui.components.rememberSkeletonColor
import com.dengying.player.ui.components.GlassPage
import com.dengying.player.ui.components.GlassPullIndicator
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
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSourceStatus: () -> Unit,
    onOpenLiveChannels: () -> Unit,
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
    // 发现窗格：随机片池的种子（下拉刷新 reshuffle）。
    // 首值必须真随机：原先恒 0 → Random(0) 每次冷启同序（用户反馈「不够随机」）。
    // rememberSaveable 仍跨重建保留，只有首次进入 / 进程重启才换一批。
    var discoSeed by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(Random.Default.nextLong())
    }
    // 发现窗格的跨页重建状态（滚动位置/解析缓存/断点）。
    // 必须 remember 在**本层**：DiscoverPane 切到首页时会被销毁（pager 只预组合
    // 相邻一页），状态 remember 在页内的结果就是"去首页再回来从头播"（用户实测）。
    val discoverUi = vm.discoverUi
    val discoverResume = discoverUi.resume
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
    // 首页下拉刷新的转圈要画在玻璃顶栏之上，而顶栏在 GlassPage 的 overlay 层，
    // 所以 state 提到外层，由首页那一页的下拉手势写入、overlay 读取。
    // 必须在这里 remember：手势只会更新它拿到的那一个 state，换一个实例 overlay 就永远读到 0。
    val homePullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    var homePullRefreshing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // 【v5.7 chrome 自动隐藏】用户需求：下滑看内容时收起顶栏（logo/搜索）、
    // 底部 Dock 和分类页的分类分段控件，上滑呼出。方向判定由各页滚动状态
    // 经 [ScrollDirWatcher] 汇报；切窗格时强制复位为显示（不能带着隐藏态进新页）。
    var chromeVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    // 【Pager → 数据】滑停之后按窗格触发加载：
    // 首页吃推荐槽位(0)，分类/发现各自吃所选分类槽位，我的不拉数据。
    LaunchedEffect(pagerState.settledPage) {
        // 切窗格复位 chrome：隐藏是"这页正在往下看"的临时态，不跨页携带
        chromeVisible = true
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
    // 【v5.6 狂刷修复 + 提速】首张卡取第一个到货的分类，不整批等待；
    // picks 的 key 含 catItemCount，后续分类到货只触发 DiscoverPane 补后续轮次，
    // 不重建已显示的卡（v5.6「内容连变 7 次」的根因就是整池 wait 后一次性换池）。
    val poolReady = (1..6).any { s.cat(it).items.isNotEmpty() }
    // 【池子扩容只追加】分类分页会让 items 变多；这里把条目总数并进 remember key，
    // 池子变大时重新生成 picks（DiscoverPane 只用来补后续轮次，不重建已显示卡片）。
    val catItemCount = (0..6).sumOf { s.cat(it).items.size }
    // 【冷启动内容信号】首页推荐或发现页片池一有内容就放行后台工作：更新检查、
    // 发现页详情预取都等它之后再启动（计划 18 §12.2），不抢 t=0 的前台席位。
    val homeHasContent = s.cat(0).items.isNotEmpty()
    LaunchedEffect(homeHasContent, poolReady) {
        if (homeHasContent || poolReady) {
            com.dengying.player.ui.AppStartupSignals.markFirstContent()
        }
    }
    val picks = remember(poolReady, discoSeed, catItemCount) {
        if (!poolReady) {
            emptyList()
        } else {
            val repo = UiDepsHolder.get().sources
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
                // 【源优选】发现页是「刷到即看」，必须只留能直接播的源：
                // 用户关掉的源 + playGap 非空（要登录/要会员）的源都不进池。
                // 池子因此变小也不放宽——进了池却播不了比少一张卡更伤。
                .filter { playableInDiscover(it, repo) }
        }
    }
    // 【透射源】拆成「内容层（被采样）+ 玻璃浮层（去采样）」两个兄弟层：
    // 海报滚到顶栏/Dock 背后时，玻璃里透出的是真实封面而不是一片色雾。
    // 玻璃件绝不能留在 content 里，否则会录到自己 → 自引用栈溢出。
    GlassPage(
        modifier = Modifier.fillMaxSize(),
        content = {
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
                    ScrollDirWatcher(
                        pos = { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset },
                        onHide = { down -> chromeVisible = !down },
                    )
                    // 【下拉刷新】转圈不画在这里。内容层在 GlassPage 的采样层里，
                    // 玻璃顶栏是浮在它之上的 overlay，转圈画在内容层会被顶栏整个盖住
                    // （用户实测：下拉时看不见转圈）。手势写入外层那一个 state，转圈画进 overlay。
                    homePullRefreshing = cs.refining
                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        isRefreshing = cs.refining,
                        onRefresh = { vm.refresh(0) },
                        state = homePullState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {},
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
                    // v5.7：下滑收起分类分段控件（与顶栏/Dock 同步），上滑呼出
                    chipsVisible = chromeVisible,
                    // v5.7.2：网格滚动方向也驱动整组 chrome 显隐
                    onScrollDir = { down -> chromeVisible = !down },
                )
                // ---- 发现：随机推荐 + 内嵌自动播放（v5，抖音式单播） ----
                2 -> DiscoverPane(
                    pool = picks,
                    resume = discoverResume,
                    uiState = discoverUi,
                    topPadding = topPad,
                    bottomPadding = bottomReserve,
                    onOpenDetail = onOpenDetail,
                    onShuffle = { discoSeed++ },
                    // targetPage 在滑动开始即变 → 开始滑就停声（主流口径）
                    active = pagerState.targetPage == 2,
                    preparing = !poolReady,
                    // v5.7：发现页下滑同样收起 chrome（监听在窗格自己的 listState 上）
                    onScrollDir = { down -> chromeVisible = !down },
                    // 换批代号：只有下拉刷新才重建列表（池子扩容不重建）
                    feedKey = discoSeed,
                    onNeedMore = {
                        // 滑到这批尾部：顺便给还有分页的分类续拉（最多两个，防同时
                        // 六路出网把中心项挤在队列里）。新条目进后续轮次，不动当前列表。
                        val more = (1..6)
                            .filter { idx ->
                                val c = s.cat(idx)
                                c.hasMore && !c.loadingMore && !c.loading
                            }
                            .shuffled()
                            .take(2)
                        more.forEach { vm.loadMore(it) }
                    },
                )
                // ---- 我的：个人信息聚合（v5；共享顶栏在本页隐藏，自带页头） ----
                3 -> MineScreen(
                    onOpenFavorites = onOpenFavorites,
                    onOpenHistory = onOpenHistory,
                    onOpenDownloads = onOpenDownloads,
                    onOpenSettings = onOpenSettings,
                    onOpenSourceStatus = onOpenSourceStatus,
                    onOpenLiveChannels = onOpenLiveChannels,
                    onOpenLogin = onOpenLogin,
                    topPadding = topPad,
                    bottomPadding = bottomReserve,
                )
            }
        }
        },
        overlay = {
        // ---- 悬浮层（玻璃顶栏：品牌 + 长条搜索框）----
        // v5：「我的」页整条隐藏（该页自带页头 + 右上角设置钮）；搜索改长条。
        // v5.7：再叠加滚动隐藏——任一窗格下滑时收起，上滑或切页复位。
        androidx.compose.animation.AnimatedVisibility(
            visible = chromeVisible && pagerState.targetPage != 3,
            // 【只淡入淡出，不做纵向滑动】(DVP §8) 玻璃的折射是用"表面在根坐标里的
            // 位置"对齐采样层的；纵向滑动动画期间这个对齐每帧在变，底边会画出错位
            // 的双线。去掉滑动后玻璃只在固定位置淡入，底边干净。
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 【必须补状态栏避让】三端内容都铺到状态栏底下：Android 用
                // enableEdgeToEdge()，iOS 用 prefersStatusBarHidden/安全区外扩，
                // 鸿蒙在 EntryAbility 调了 setWindowLayoutFullScreen(true)。
                // 首页品牌条是普通页面（非全屏播放），仍要避让状态栏；
                // 全屏播放的贴边口径见 InlinePlayer 的 6dp 规则。
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
        // v5.7：Dock 跟随滚动隐藏/呼出（与顶栏同一 chromeVisible）。
        androidx.compose.animation.AnimatedVisibility(
            visible = chromeVisible,
            // 【只淡入淡出，不做纵向滑动】(DVP §8) Dock 是最明显的受害者：玻璃折射
            // 按表面在根坐标里的位置对齐采样层，纵向滑动期间对齐每帧在变，收起再
            // 呼出后底边会留一道错位的亮线。淡入淡出即可，Dock 自身有 spring 追踪。
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
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
        )
        }
        // 首页下拉刷新的转圈：必须在 overlay 层，否则被玻璃顶栏盖住看不见。
        // 只在首页显示，顶栏正下方居中。
        if (pagerState.targetPage == 0) {
            GlassPullIndicator(
                state = homePullState,
                isRefreshing = homePullRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 56.dp + 8.dp),
            )
        }
        },
    )
}

/** Dock 项 key，下标与 Pager 页一一对应。 */
private val DOCK_KEYS = listOf("home", "category", "discover", "mine")

/**
 * 「推荐」页：首页版块流（FR-612）。
 *
 * 【形态】对齐 AH 首页：Banner 轮播 → 热播双列宫格 → 分类横滑 rail。
 * 数据由 AhHomeAdapter 从 ah_ap2233 现场合成，失败时回落到 tv189 jsondata。
 * 结构见 [HomeFeedView]；v5 起首页不放频道 chips（用户明确要求）。
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
        // 首拉中：骨架与真实版块同尺寸，避免加载完成时跳动
        cs.loading && cs.sections.isEmpty() -> HomeFeedSkeleton(topPadding, bottomPadding)
        // 真失败（网络/源异常）：可重试。
        // 【v5 修复】原判断是 `cs.errMsg != null`——errMsg 是**非空 String**，
        // 该条件恒为 true，于是「版块为空但没有错误」时也走 ErrorView，且 message
        // 是空串 → 屏幕一片空白，看着就像「下拉刷新没反应」。改成按空串判断。
        cs.sections.isEmpty() && cs.errMsg.isNotEmpty() -> ErrorView(message = cs.errMsg, onRetry = onRetry)
        // 源未启用或版块拉空：给引导文案，不算失败态
        cs.sections.isEmpty() -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("首页暂时没有内容", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
            Text(
                "下拉刷新再试一次。还是没有，去“我的”里打开更多片源。",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        else -> HomeFeedView(
            sections = cs.sections,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            state = state,
            onOpen = { v ->
                onOpenDetail(
                    Routes.detail(
                        vodId = v.vod_id,
                        source = v._source ?: "",
                        name = v.vod_name,
                        pic = v.vod_pic,
                        vodUri = v.vod_uri ?: "",
                    )
                )
            },
        )
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
        cs.loading && cs.items.isEmpty() -> SkeletonGrid(rememberSkeletonColor(), topPadding, bottomPadding)
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
                    title = v.vod_name,
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
    // 【v5.7 chrome 自动隐藏】分类分段控件显隐：下滑收起（IndexScreen 的
    // chromeVisible 驱动），上滑呼出。收起时网格顶边自然上移填补。
    chipsVisible: Boolean = true,
    // 【v5.7.2】分类网格滚动方向回调：下滑=收起顶栏/Dock/chips（同一
    // chromeVisible），上滑呼出。与首页/发现页同口径，缺省不联动。
    onScrollDir: (Boolean) -> Unit = {},
) {
    ScrollDirWatcher(
        pos = { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset },
        onHide = onScrollDir,
    )
    Column(Modifier.fillMaxSize()) {
        // 同首页：玻璃 indicator 落在分段控件**下方**，不压在胶囊上。
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = cs.refining,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                GlassPullIndicator(
                    state = pullState,
                    isRefreshing = cs.refining,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding + 52.dp),
                )
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                // 分类分段控件（v5.2）：与底部 Dock 同款交互——玻璃胶囊容器 +
                // 滑动高光（animateDpAsState 弹簧）+ 内容均分（spaceBetween 语义，
                // 固定 6 分类每项 weight(1f)），不再是滚动 chips 行。
                // v5.7：包 AnimatedVisibility 参与滚动隐藏；shrinkVertically 让
                // 网格贴上来，不留空带。
                androidx.compose.animation.AnimatedVisibility(
                    visible = chipsVisible,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                ) {
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
                }
                PageContent(
                    cs = cs,
                    // 网格已在 chips 之下（Column 布局），只需垫过 chips 底边距；
                    // chips 收起（v5.7）后网格自己垫回完整顶栏高度，避免顶进状态栏
                    topPadding = if (chipsVisible) 8.dp else topPadding,
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

/** 发现页无限流：每批追加的条数（滚近尾部 +1 批）——常量与取片器见 [DiscoverFeed.kt] */

/**
 * 滚动方向监视（v5.7 chrome 自动隐藏）：把 Lazy 列表/网格的 (首可见项下标, 偏移)
 * 流转成方向事件——累计下滑超过阈值报 `onHide(true)`，累计上滑报 `onHide(false)`。
 *
 * 【为什么是累计而不是立即】手指抖动/回弹会产生反向小位移，立即切换会让
 * chrome 闪烁；累计量过方向抵消后需要走满阈值才切换。阈值 80px（约 30dp）：
 * 小于一次典型 fling，响应不至于迟钝。
 *
 * 【下标跨项的换算】不同项高度不一，跨项时用 `下标差 * 100000` 当位移量级
 * （单项偏移远小于该值），保证跨项滑动方向不被项内偏移噪声淹没。
 *
 * 【pos 用 lambda 而不是状态参数】列表状态由调用方各自持有（网格/列表两种
 * 类型），lambda 里读快照态，[androidx.compose.runtime.snapshotFlow] 订阅
 * 其变化——与 savedScroll 的既有口径一致。
 */
@Composable
private fun ScrollDirWatcher(
    pos: () -> Pair<Int, Int>,
    onHide: (Boolean) -> Unit,
) {
    val cur by androidx.compose.runtime.rememberUpdatedState(pos)
    val report by androidx.compose.runtime.rememberUpdatedState(onHide)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var prev = cur()
        var accum = 0
        androidx.compose.runtime.snapshotFlow { cur() }.collect { now ->
            val d = if (now.first == prev.first) now.second - prev.second
            else (now.first - prev.first) * 100000
            prev = now
            if (d == 0) return@collect
            accum += d
            if (accum > 80) {
                accum = 0
                report(true)
            } else if (accum < -80) {
                accum = 0
                report(false)
            }
        }
    }
}

/**
 * 发现窗格（v5.3）：随机推荐 + 内嵌自动播放的**无限流**列表。
 *
 * 【片池】[IndexScreen] 把首页 tv189 版块条目全量洗牌成 [pool]（下拉刷新重洗，
 * [onShuffle]）；本窗格按批（[DISCOVER_BATCH]）从池里追加显示——滚近尾部自动续批，
 * 一轮取尽就换轮重洗（[DiscoverFeedSource]），观感上无限。
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
internal class DiscoverResume {
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

/**
 * 发现页跨导航持有者：由 `IndexViewModel` 持有，随 nav back stack entry 存活。
 *
 * 【为什么不在 Compose 里 remember】`remember` 只活到组合销毁；从发现页点进详情，
 * IndexScreen 离开组合，remember 全丢 → 换批种子重洗、滚动回顶、从 0 重播
 * （用户实测「返回每次都会刷新一下」）。ViewModel 则跟着 nav entry 存活，返回后原样复用。
 *
 * 只存「可重建的输入与已生成的结果」，不存 Composable 对象。
 */
internal class DiscoverUiState {
    val resume = DiscoverResume()

    private var feedKey: Long = Long.MIN_VALUE
    private var feedSource: DiscoverFeedSource? = null
    private var feed: MutableList<DiscoverEntry>? = null

    /** 取（必要时创建）本批的批次源与显示列表。换批 [key] 变才重置。 */
    fun feedFor(
        key: Long,
        pool: List<VideoItem>,
    ): Pair<DiscoverFeedSource, MutableList<DiscoverEntry>> {
        if (key != feedKey) {
            feedKey = key
            feedSource = null
            feed = null
            resume.resolved.clear()
            resume.paused.clear()
            resume.startedIdx.intValue = -1
            resume.playingIdx.intValue = -1
        }
        val src = feedSource ?: DiscoverFeedSource().also { feedSource = it }
        val list = feed ?: androidx.compose.runtime.mutableStateListOf<DiscoverEntry>().also {
            if (pool.isNotEmpty()) it.addAll(src.nextBatch(pool))
            feed = it
        }
        return src to list
    }
}

@Composable
private fun DiscoverPane(
    pool: List<VideoItem>,
    resume: DiscoverResume,
    uiState: DiscoverUiState,
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
    // 【v5.7 chrome 自动隐藏】滚动方向回调：true=下滑（收起顶栏/Dock），false=上滑。
    onScrollDir: (Boolean) -> Unit = {},
    // 换批代号（discoSeed）：变了才重建列表并清缓存；池子扩容不重建。
    feedKey: Long = 0L,
    // 滚近尾部时向调用方要更多片（分类分页），让池子继续变大。
    onNeedMore: () -> Unit = {},
) {
    // 滚动位置跨页重建：从持有者恢复初值，滚动时回写（切到首页再回来仍在原处）
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = resume.listIndex.intValue,
        initialFirstVisibleItemScrollOffset = resume.listOffset.intValue,
    )
    ScrollDirWatcher(
        pos = { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset },
        onHide = onScrollDir,
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
    // Android 壳提供独立预览引擎（2.5s 起播缓冲）；其它平台默认等于 engine()。
    val engine = remember {
        runCatching { deps.engineHost.previewEngine() }
            .getOrElse { runCatching { deps.engineHost.engine() }.getOrElse { com.dengying.player.player.NullPlayerEngine } }
    }
    // 【无限流】显示列表 = 按轮追加：一轮内洗牌遍历当前池，取尽后轮次 +1 重洗。
    // 只在换批（[feedKey] = discoSeed）时重建；池子扩容只影响后续轮次，
    // 已有卡片、解析缓存和滚动位置都不动。
    // 首批在 remember 初始化里同步填好（之后追加都在协程里做，不在组合期改状态）。
    // 【跨导航保留】DiscoverPane 在横向 Pager 切到首页时会被销毁，但 NavGraph
    // 里 discoverResume 持有者（IndexScreen）只随 IndexScreen 退出才销毁。
    // 回收后再次进入发现页仍保留滚动位置/解析缓存/断点；只有 feedKey 变才重置。
    val (feedSource, feed) = remember(feedKey, uiState) { uiState.feedFor(feedKey, pool) }
    // 池子是每次取批时传入的「最新值」：分类分页可在浏览中陆续到货
    val currentPool by androidx.compose.runtime.rememberUpdatedState(pool)
    fun appendBatch() {
        val more = feedSource.nextBatch(currentPool)
        if (more.isNotEmpty()) feed.addAll(more)
    }
    // 首次进入/预热中池子还是空的：成型后补首批（不重置已有卡片）
    LaunchedEffect(pool) {
        if (feed.isEmpty() && pool.isNotEmpty()) feed.addAll(feedSource.nextBatch(pool))
    }
    // 解析结果缓存：下标 -> (直链, headers json, 是否试看链)；url 为空串 = 解析失败
    // （换源也救不回）。只缓存成功项会永久转圈——失败也要落袋。试看标记给角标
    // （v5.6：tv189 免登录片跨源救不回时不再默默只给 5 分钟）。
    // 跨页重建复用（持有者里）；下标语义只随 [feedKey] 变化作废——列表只追加，
    // 池子扩容/换批都不改已有下标（换批会重建 feed，见下面的清理）。
    val resolved = resume.resolved
    // 解析在飞标记：防同一项被「可见即解析」重复发起（SnapshotStateList 主线程读写）
    val inFlight = remember(feedKey) { mutableStateListOf<Int>() }
    // 解析并发闸：同时只放 2 条全链解析。可见 2.5 项全并发时每项要发 6-10 个
    // 请求（详情+换链+跨源搜索/候选），20+ 请求挤 6 个出网位 → 排队 13s（实测
    // probe net-queue），中心项反而最后就绪。限流 + 中心近的先入闸。
    val resolveSlots = remember(feedKey) { kotlinx.coroutines.sync.Semaphore(2) }
    // 下拉换一批（[feedKey] 变）：旧下标语义全部作废，清掉缓存与断点并回到首批，
    // 否则会把上一批的直链/断点套到新一批的卡上（同下标不同片）。
    // 用「记住上一次 key」而不是直接清：DiscoverPane 跨页重建时会重跑效果，
    // 无条件清会把跨页保活的滚动位置与断点一起抹掉。
    var seenFeedKey by remember { mutableStateOf(feedKey) }
    LaunchedEffect(feedKey) {
        if (seenFeedKey != feedKey) {
            seenFeedKey = feedKey
            resume.resolved.clear()
            resume.paused.clear()
            resume.startedIdx.intValue = -1
            resume.playingIdx.intValue = -1
            listState.scrollToItem(0)
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
    suspend fun resolveChainOf(
        idx: Int,
        altLimit: Int = 3,
        forceNet: Boolean = false,
        waitForFullSource: Boolean = false,
    ): Triple<String, String, Boolean>? {
        val vod = feed[idx].vod
        val cacheKey = DiscoverUrlCache.key(vod._source ?: "", vod.vod_id, 0)
        // 进程级缓存优先：滑回刚播过的卡、换批后又抽到同一部片，都不再打解析链。
        // forceNet（中心卡全量重试）跳过缓存，否则刚存的空线路会把重试按回失败。
        if (!forceNet) {
            DiscoverUrlCache.get(cacheKey)?.let {
                return Triple(it.url, it.headers, it.previewOnly)
            }
        }
        val result = runCatching {
            com.dengying.player.playback.resolvePlayChain(
                repo = deps.sources,
                raw = discoverRawUrl(deps, vod, forceNet = forceNet),
                source = vod._source ?: "",
                vodId = vod.vod_id,
                vodName = vod.vod_name ?: "",
                episodeIndex = 0,
                ioDispatcher = com.dengying.player.platform.ioDispatcher,
                altLimit = altLimit, // 发现页批量预解析默认只试 3 个，防出网闸拥塞；中心卡全量重试传 MAX
                // 【先出画面，再换全片】这里**不再**阻塞等全片：真机上 12s 预算里
                // 跨源兜底常排到队尾，用户看到的是长时间黑卡。改成先返回首个可播
                // 地址（直链或试看）立刻起播，全片搜索交给起播后的后台升级（见起播
                // 效果里的 previewOnly 分支），拿到全片再原位换链、保留进度。
                waitForFullSource = waitForFullSource,
            )
        }.getOrNull()?.let { Triple(it.url, it.headers, it.previewOnly) }
        if (result != null && result.first.isNotEmpty()) {
            DiscoverUrlCache.put(cacheKey, result.first, result.second, result.third)
            // 冷启动第一条可播地址到了：发现页的详情预取与更新检查可以放行了。
            com.dengying.player.ui.AppStartupSignals.markFirstPlayableUrl()
        }
        return result
    }

    // 【预热组跟随中心卡】中心卡落定、加入 Semaphore 之后，再按「距中心最近」
    // 预热 3 张。旧实现固定预热下标 0..2：首屏中心卡排在其后时，预热组先抢走
    // Semaphore(2) 的两个许可，中心项反而最后就绪（真机 probe net-queue）。
    // 仍限 2 并发、不新增出网配额。
    LaunchedEffect(feedKey, feed.size >= 3) {
        if (feed.size < 3) return@LaunchedEffect
        val center = snapshotFlow { playingIdx }.first { it >= 0 }
        (0 until feed.size)
            .filter { it != center }
            .sortedBy { kotlin.math.abs(it - center) }
            .take(3)
            .forEach { idx ->
                if (resolved[idx] == null && idx !in inFlight) {
                    inFlight.add(idx)
                    launch {
                        resolveSlots.withPermit {
                            resolved[idx] = resolveChainOf(idx) ?: Triple("", "", false)
                        }
                        inFlight.remove(idx)
                    }
                }
            }
    }

    // 【可见即解析】v5.4 核心：别等中心项落定才开始解析——跨源兜底一条就要
    // 1-3s，落定再解析用户要干等 10s（实测 probe：net-queue 排队 5.5s）。
    // 可见项（一屏 ~2.5 部）提前全链解析，中心项落定时结果多半已在袋里 → 即刻起播。
    LaunchedEffect(feedKey, feed.isNotEmpty()) {
        if (feed.isEmpty()) return@LaunchedEffect
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
                    if (idx < feed.size && resolved[idx] == null && idx !in inFlight) {
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

    // 【中心项起播】playingIdx / active / 该项解析结果变化：先停旧声，再等该项解析落袋 → 起播。
    // 不等 surface 绑定：换了片先 setMedia（mediaReady 据此挂回 PlayerView），
    // play() 紧跟着调；等绑定会让换卡首帧既无画也无音，只能靠用户再滑一下触发。
    // 等待期间滚走：LaunchedEffect(key) 结构化取消，旧播放意图作废。
    //
    // key 里带 `resolved[playingIdx]?.first`（DVP §2）：中心卡首轮解析失败 → 重试
    // 写回非空地址后本效果自动重跑一次，把「救回来了」的卡片直接起播，而不是
    // 继续停在「看不了」。首轮成功时 key 不变，不会重复起播。
    LaunchedEffect(playingIdx, active, resolved[playingIdx]?.first) {
        val idx = playingIdx
        // 被切走的片记断点：滑到新中心、或整窗格失焦，都要留位置
        if (startedIdx >= 0 && (startedIdx != idx || !active)) {
            pausedPositions[startedIdx] = engine.currentPosition
        }
        // 【只在该停的时候停】旧实现在这里无条件 pause()：Dock 切回、重试成功、
        // 交接进详情都会把「已经在前台播着的那一条」掐一下再拉起来，听感断一拍。
        // 交接期间更不能停：详情页马上要接管这条流（DVP §11）。
        val willSwap = !active || idx < 0 || startedIdx != idx
        if (willSwap && !deps.engineHost.isHandoffInProgress()) engine.pause()
        if (!active || idx < 0 || idx >= feed.size) return@LaunchedEffect
        val hit = snapshotFlow { resolved[idx] }.filterNotNull().first()
        if (hit.first.isEmpty()) return@LaunchedEffect // 救不回：卡片给「看不了」提示
        // 【无缝续播】媒体还是本片的（只是 Dock 切页暂停过）→ play() 直接续。
        // 只有媒体被借走（进过详情/播放页，engine.currentUrl 已变）或换到别的片，
        // 才 setMedia(断点) 重载。
        val resumeSame = startedIdx == idx && engine.currentUrl == hit.first
        if (!resumeSame) {
            // 先落媒体再起播：mediaReady 据此把 PlayerView 绑回真引擎出画。
            engine.setMedia(hit.first, hit.second, pausedPositions[idx] ?: 0L)
            startedIdx = idx
        }
        engine.play()
        // 【先出试看，再换全片】解析只拿到 300s 试看链时不再让用户对着黑卡等：
        // 画面立刻给出去，全片搜索在后台继续，找到就**保留当前位置**原位换链
        // （DVP §14）。换链前确认中心卡还是这一张、引擎里还是那条试看，避免
        // 把用户已经滑走后的新卡片顶掉。
        if (hit.third && idx < feed.size) {
            val vod = feed[idx].vod
            launch {
                val full = withTimeoutOrNull(20_000) {
                    com.dengying.player.playback.findFullSource(
                        repo = deps.sources,
                        source = vod._source ?: "",
                        vodName = vod.vod_name ?: "",
                        episodeIndex = 0,
                        ioDispatcher = com.dengying.player.platform.ioDispatcher,
                    )
                } ?: return@launch
                if (playingIdx != idx || engine.currentUrl != hit.first) return@launch
                val pos = engine.currentPosition
                // 后台换到全片后覆盖进程级缓存里的试看链，滑回来直接播全片。
                DiscoverUrlCache.put(
                    DiscoverUrlCache.key(vod._source ?: "", vod.vod_id, 0),
                    full.first,
                    full.second,
                    false,
                )
                resolved[idx] = Triple(full.first, full.second, false)
                engine.setMedia(full.first, full.second, pos)
                engine.play()
            }
        }
    }

    // 【中心卡多次重试】批量预解析只试 3 个候选（见 resolveChainOf），中心卡
    // 落定后仍判失败时再走详情页同款全量解析。真机上出现过「发现页报看不了、
    // 进详情等一下又能播」——解析链在那一刻临时失败是常态（token 未就绪、
    // 源站抖动），所以允许最多 [DiscoverPlaybackRules.MAX_CENTER_RETRY] 次、
    // 逐次退避，且重试时**跳过详情缓存重新拉**，否则缓存里的空线路会一直
    // 把重试按回失败。
    val retryCount = remember(feedKey) { mutableStateMapOf<Int, Int>() }
    // 【最终结论】只有「全部源跑完仍无可播首集」才许卡片报失败（DVP §10）。
    // 6s 快照里没有可播候选，往往只是慢源还没回来——按快照报「看不了」正是
    // 用户反馈的「发现页说看不了、进详情等一下又能播」。
    val finalVerdict = remember(feedKey) { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(playingIdx, active, resolved[playingIdx]?.first) {
        val idx = playingIdx
        if (!active || idx < 0 || idx >= feed.size) return@LaunchedEffect
        val failed = resolved[idx]?.first?.isEmpty() == true
        val attempts = retryCount[idx] ?: 0
        if (!DiscoverPlaybackRules.shouldRetry(
                isCenter = true,
                failed = failed,
                attempts = attempts,
            )
        ) return@LaunchedEffect
        retryCount[idx] = attempts + 1
        // 退避：第 1 次立刻重试（多数是解析时序问题），之后 1.2s、2.4s。
        if (attempts > 0) kotlinx.coroutines.delay(1200L * attempts)
        val lastChance = attempts + 1 >= DiscoverPlaybackRules.MAX_CENTER_RETRY
        if (lastChance) {
            // 最后一次：先把「全部源跑完」的候选等回来再解析，否则只是把同一份
            // 残缺快照再试一遍。等不到（超时/本轮没在搜）就按原逻辑收尾。
            withTimeoutOrNull(20_000L) {
                deps.sources.awaitCompleteAltSources(feed[idx].vod.vod_name ?: "")
            }
        }
        val r = resolveSlots.withPermit {
            resolveChainOf(idx, altLimit = Int.MAX_VALUE, forceNet = true)
        }
        if (r != null && r.first.isNotEmpty()) {
            finalVerdict[idx] = false
            resolved[idx] = r
        } else if (lastChance) {
            // 最终列表里也没可播首集：这才是真结论，卡片可以给失败提示了
            finalVerdict[idx] = true
            com.dengying.player.player.PlaybackProbe.note("discover-give-up", "idx=$idx")
        }
    }

    // 【缓冲转圈】发现页只认「URL 解析中」，解析完成到首帧之间引擎在缓冲却
    // 没有任何提示（用户实测「转圈消失后还要卡一下」）。这里接引擎的
    // onBuffering，三端引擎都发同一事件；只记下标与布尔，不记地址。
    var engineBuffering by remember { mutableStateOf(false) }
    DisposableEffect(engine) {
        val listener = object : com.dengying.player.player.PlayerListener {
            override fun onReady(durationMs: Long) { engineBuffering = false }
            override fun onPlaying() { engineBuffering = false }
            override fun onPaused() { engineBuffering = false }
            override fun onBuffering(active: Boolean) {
                engineBuffering = active
                com.dengying.player.player.PlaybackProbe.note(
                    "discover-buffering", "idx=$playingIdx on=$active",
                )
            }
            override fun onCompleted() { engineBuffering = false }
            override fun onError(
                kind: com.dengying.player.player.PlaybackErrorKind,
                code: Int,
                message: String,
            ) { engineBuffering = false }
            override fun onVideoSize(width: Int, height: Int) {}
        }
        engine.addListener(listener)
        onDispose { engine.removeListener(listener) }
    }
    LaunchedEffect(playingIdx) { engineBuffering = false }

    // 【中心判定（主流口径：B 站/微博内嵌视频流）】滚动**停止**后才起播：
    // 取视口中心最近的项，且可见面积 >= 55%（防边缘半张卡误播）。
    //
    // 【滚动中立刻停】旧实现只监听 isScrollInProgress 的翻转，滚动期间什么都不做
    // ——手指一动卡片已经滑走，旧视频还在原地播（声音、画面都要等抬手才切）。
    // 现在滚动中也观察滚动位置：当前中心卡可见面积掉到 55% 以下就立刻置 -1 停播；
    // 滚动中**绝不**启播下一张，否则一路滑过去会连续起播好几张。
    LaunchedEffect(feedKey, feed.isNotEmpty()) {
        if (feed.isEmpty()) return@LaunchedEffect
        fun visibleFraction(info: androidx.compose.foundation.lazy.LazyListItemInfo): Float {
            val li = listState.layoutInfo
            val top = maxOf(info.offset, li.viewportStartOffset)
            val bottom = minOf(info.offset + info.size, li.viewportStartOffset + li.viewportSize.height)
            return ((bottom - top).coerceAtLeast(0)).toFloat() / info.size
        }
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }.collect { (scrolling, _, _) ->
            val info = listState.layoutInfo
            if (scrolling) {
                if (playingIdx >= 0) {
                    val cur = info.visibleItemsInfo.firstOrNull { it.index == playingIdx }
                    if (cur == null || visibleFraction(cur) < 0.55f) playingIdx = -1
                }
                return@collect
            }
            val center = info.viewportStartOffset + info.viewportSize.height / 2
            val best = info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
            playingIdx = if (best != null && visibleFraction(best) >= 0.55f) best.index else -1
        }
    }
    // 【无限流追加】滚近尾部（倒数第 4 项进入视口）就再追加一批，
    // 顺便向调用方要更多分类分页，让池子继续变大（换轮时才会用上）。
    // 一轮取尽由 [DiscoverFeedSource] 自动换轮重洗，列表不会触底停住。
    LaunchedEffect(feedKey, feed.isNotEmpty()) {
        if (feed.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }.collect { last ->
            if (last >= feed.size - 4) {
                appendBatch()
                onNeedMore()
            }
        }
    }
    // 离开发现页（切 Dock/退出）：记断点 + 释放 surface 引用。
    // 页面被销毁（切到首页/分类 → 超出 pager 预组合范围）时，断点写进
    // [DiscoverResume]，切回来才能从原位续播（原先状态随页面一起丢，从头播）。
    DisposableEffect(feedKey) {
        onDispose {
            if (startedIdx >= 0) pausedPositions[startedIdx] = engine.currentPosition
            setSurface(false)
        }
    }
    if (pool.isEmpty()) {
        // 六分类预热中：与首页/分类/搜索同节奏的骨架，几何对齐 16:9 内嵌播放卡；
        // 成型后一次到位，不再用居中转圈打断浏览预期。
        if (preparing) {
            DiscoverSkeleton(topPadding, bottomPadding)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
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
            items(count = feed.size, key = { feed[it].key }) { idx ->
                val item = feed[idx].vod
                val hit = resolved[idx]
                // 残帧门：本片还没装进引擎（解析中/失败/卡住）时不挂 PlayerView，
                // 旧视图销毁 → 上一集最后一帧不残留，只显示海报。
                val mediaReady = playingIdx == idx && startedIdx == idx &&
                    hit != null && hit.first.isNotEmpty()
                // 失败态要等重试用尽才成立：重试期间按「还在加载」处理，
                // 避免真机能播的片先被贴一张「这部暂时看不了」。
                // 重试用尽也只是必要条件——还得等「全部源跑完」的最终结论
                // （finalVerdict，DVP §10），否则慢源还没回来就先判了死刑。
                val failedNow = DiscoverPlaybackRules.finalFailure(
                    emptyUrl = playingIdx == idx && hit != null && hit.first.isEmpty(),
                    attempts = retryCount[idx] ?: 0,
                    allSourcesDone = finalVerdict[idx] == true,
                )
                DiscoverVideoCard(
                    item = item,
                    isCenter = playingIdx == idx,
                    resolving = playingIdx == idx && hit == null,
                    failed = failedNow,
                    preview = playingIdx == idx && hit != null && hit.third,
                    engine = engine,
                    onOpenDetail = onOpenDetail,
                    mediaReady = mediaReady,
                    // 重试等待期间也算「加载中」，转圈别断，用户才不会以为卡死。
                    buffering = playingIdx == idx && mediaReady && engineBuffering ||
                        (playingIdx == idx && hit != null && hit.first.isEmpty() && !failedNow),
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
    /** 是否视口中心卡：只有中心卡会挂 VideoSurface。 */
    isCenter: Boolean,
    resolving: Boolean,
    failed: Boolean,
    preview: Boolean,
    engine: PlayerEngine,
    onOpenDetail: (String) -> Unit,
    /** 本卡媒体已装进引擎。false = 还在解析/卡住，此时不挂视图，避免上一集残帧。 */
    mediaReady: Boolean = false,
    /** 引擎正在缓冲（发现页自己订阅 onBuffering）。 */
    buffering: Boolean = false,
) {
    // 播放窗在窗口里的位置：点击时交给详情页做「画面平移」的起点。
    var playerBounds by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            elevation = 10.dp,
            liquid = false,
        ) {
            Column {
                // 16:9 播放窗：视频与海报同一几何，起播瞬间无跳变。
                // 【onOpenDetail 收的是完整路由串】（NavGraph 直接 nav.navigate）——
                // 传裸 ID 会崩（Navigation destination cannot be found）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .onGloballyPositioned { playerBounds = it.boundsInWindow() }
                        .clickable {
                            // 详情页会先读进程级 detailCache。发现页已拿到完整 VideoItem，
                            // 这里同步注入，避免「视频已经看到了还要再转一圈正在获取详情」。
                            //
                            // 【只补不用覆盖】卡片上的 item 是**发现页的简化条目**：
                            // 它通常只带一条线路，有时连 vod_uri 都没有。若在这里无条件
                            // put，会把 discoverRawUrl 已经缓存的完整详情（全部线路）冲掉，
                            // 详情页上线后就会出现「播放源只有一个」。
                            val source = item._source ?: ""
                            val deps = com.dengying.player.ui.UiDepsHolder.get()
                            val cacheKey = com.dengying.player.ui.UiDeps.detailCacheKey(source, item.vod_id)
                            val cachedDetail = deps.detailCache.get(cacheKey)
                            if (cachedDetail == null) deps.detailCache.put(cacheKey, item)
                            // 【平滑交接】已装进预览引擎的片才带当前位置进详情，
                            // 解析中/失败的卡不带，详情页按自身逻辑处理。
                            // 【引擎为准，不信状态位】`mediaReady` 是 UI 侧复合条件
                            // （playingIdx+startedIdx+resolved 三者同时成立）；真机上
                            // 出现过引擎明明装着这条流、`mediaReady` 仍为 false 的情况
                            // （探针 [handoff-gate] ready=false urlLen=46 pos=0），
                            // 于是详情页判定「没有交接」→ 冷启 setMedia 把**正在播的
                            // 同一个引擎**重装一遍：画面黑一下、进度归零、控件失效。
                            // 引擎里装着这张卡（中心卡 + currentUrl 非空）就必须交接。
                            val engineHoldsCard = isCenter && !engine.currentUrl.isNullOrEmpty()
                            val startAtMs = DiscoverPlaybackRules.handoffStartAtMs(
                                mediaReady || engineHoldsCard, engine.currentPosition,
                            )
                            com.dengying.player.player.PlaybackProbe.note(
                                "handoff-gate",
                                "ready=$mediaReady urlLen=${engine.currentUrl?.length ?: -1} pos=${engine.currentPosition} " +
                                    "start=$startAtMs sess=${com.dengying.player.playback.PlaybackSession.engineOrNull()?.hashCode()}",
                            )
                            // 【画面平移】只有「这张卡正在播」才交接几何：
                            // 详情页会把画面从卡片矩形动画到自己的播放位，
                            // 中途不重载媒体，视觉上是同一块画面在移动。
                            playerBounds?.let { r ->
                                if (startAtMs >= 0L) {
                                    DiscoverHero.record(
                                        left = r.left.toInt(),
                                        top = r.top.toInt(),
                                        width = r.width.toInt(),
                                        height = r.height.toInt(),
                                    )
                                }
                            }
                            // 【地址交接】把预览引擎正在播的这条地址一起交过去：
                            // 详情页直接当首集预解析结果，起播时接管同一引擎，
                            // 不解链、不重建 MediaSource —— 这是画面能连续平移的前提。
                            if (startAtMs >= 0L) {
                                DiscoverHandoff.record(
                                    DiscoverHandoff.Payload(
                                        url = engine.currentUrl.orEmpty(),
                                        // 引擎不对外暴露请求头；此处交接的是
                                        // 「已经装进引擎的同一条流」，接管后不会
                                        // 再发请求，头部留空不影响播放。
                                        headers = "",
                                        positionMs = engine.currentPosition,
                                        vodId = item.vod_id,
                                        source = source,
                                    ),
                                )
                            }
                            // 【交接窗口】点卡的同一帧起，发现页这一侧的 surface 很快就会
                            // 撤掉（离开页面 / Dock 切换），而详情页的内嵌表面要几帧之后
                            // 才挂上。旧实现在这段空窗里按「表面归零就暂停」把预览引擎
                            // 掐了，详情页只能重新 setMedia → 用户看到黑屏、重新缓冲。
                            // 置位后壳层只停主引擎、保住预览引擎；详情页挂上表面或判定
                            // 交接失败时清除（见 DetailViewModel.attachInlineSurface）。
                            if (startAtMs >= 0L && !engine.currentUrl.isNullOrEmpty()) {
                                deps.engineHost.setHandoffInProgress(true)
                            }
                            onOpenDetail(
                                com.dengying.player.ui.navigation.Routes.detail(
                                    vodId = item.vod_id,
                                    source = source,
                                    name = item.vod_name ?: "",
                                    pic = item.vod_pic ?: "",
                                    // vodUri 优先取卡片条目的；卡片没有就退到缓存里
                                    // 那份完整详情的——它是解析线路的必需参数，
                                    // 传空会让详情页一条线路都解析不出来。
                                    vodUri = item.vod_uri ?: cachedDetail?.vod_uri ?: "",
                                    // resumeEpisode = 0：发现页看到的就是第 1 集，
                                    // -1 会让 ResumeDecide 判「不自动播」（详情停在空态）。
                                    resumeEpisode = 0,
                                    autoPlay = true,
                                    startAtMs = startAtMs,
                                )
                            )
                        },
                ) {
                    if (DiscoverPlaybackRules.showPoster(isCenter, mediaReady)) {
                        com.dengying.player.ui.components.Poster(
                            url = item.vod_pic ?: "",
                            title = item.vod_name ?: "",
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 0,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        // 媒体就绪：FIT 的 letterbox 必须落在黑底上，否则透出
                        // 下层海报，画面下半截还是上一张图（用户实测）。
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    }
                    if (mediaReady) {
                        VideoSurface(
                            engine = engine,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (DiscoverPlaybackRules.showSpinner(resolving, buffering, failed)) {
                        // 【必须 size 而非 height】只约束高度会让宽高不等，
                        // 圆环几何中心偏移，视觉上「整个组件在绕圈」而非笔刷转。
                        // 暗底保证浅色海报上也看得见。
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .padding(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        }
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
 * 发现页取原始集地址：详情 → 默认线路的第一集。
 */
private suspend fun discoverRawUrl(
    deps: com.dengying.player.ui.UiDeps,
    item: VideoItem,
    forceNet: Boolean = false,
): String {
    val key = item._source ?: return ""
    val src = CmsSources.byKey(key) ?: return ""
    val detail = runCatching {
        // detailCache 由 IndexViewModel.prefetchDetails 预热；命中即省掉 1.2–2.6s
        // 建连（真机实测）。未命中才打详情请求并写回，与预取同一键。
        // forceNet：重试路径必须绕过缓存——缓存里那份可能就是「线路为空」的
        // 那次失败结果，照着它重试只会得到同样结论。
        val key2 = com.dengying.player.ui.UiDeps.detailCacheKey(key, item.vod_id)
        (if (forceNet) null else deps.detailCache.get(key2)) ?: runCatching {
            deps.sources.fetchDetail(src, item.vod_id, item.vod_uri ?: "")
        }.getOrNull()?.also { deps.detailCache.put(key2, it) }
    }.getOrNull() ?: return ""
    // 【默认线路】第一条线路不保证是正片（部分源把预告/花絮排前面，或先给一条
    // 只有几集的试看线）。与详情页同一口径：优先非预告、集数最多的线路，
    // 再取该线路第一集；手动选线与此无关（发现页没有选线 UI）。
    val lines = runCatching {
        com.dengying.player.core.parsePlayLines(detail.vod_play_from, detail.vod_play_url)
    }.getOrElse { emptyList() }
    val picked = lines
        .getOrNull(com.dengying.player.core.chooseDefaultLineIndex(lines))
        ?.episodes?.firstOrNull()?.url
        ?.takeIf { it.isNotBlank() && it.contains("://") }
    if (picked != null) return picked
    // 解析不出线路（协议变体）：退回旧口径——第一条线路的第一集，并留探针，
    // 免得这种源静默走老路没人发现。
    val fallback = detail.vod_play_url
        ?.split("$$$")
        ?.firstOrNull()
        ?.split("#")
        ?.firstOrNull()
        ?.substringAfter('$')
        ?.takeIf { it.isNotBlank() && it.contains("://") }
        ?: ""
    if (fallback.isNotEmpty()) {
        com.dengying.player.player.PlaybackProbe.note(
            "line-fallback",
            "vod=${item.vod_id} source=$key",
        )
    }
    return fallback
}
