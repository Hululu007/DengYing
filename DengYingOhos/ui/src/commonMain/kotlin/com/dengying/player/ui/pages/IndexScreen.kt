package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.core.DiscoverItem
import com.dengying.player.core.VideoItem
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.ErrorView
import com.dengying.player.ui.components.LoadingView
import com.dengying.player.ui.components.Poster
import com.dengying.player.ui.navigation.Routes
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

@Composable
fun IndexScreen(
    /** 参数是「带到搜索页的初始关键词」，空串 = 普通进搜索页 */
    onOpenSearch: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenDetail: (String) -> Unit,
    vm: IndexViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        val deps = UiDepsHolder.get()
        IndexViewModel(repo = deps.sources, cache = deps.pageCache, detailCache = deps.detailCache)
    },
) {
    val s = vm.state

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

    // 每个分类各一个 `LazyGridState`，由 Pager 的每一页各自持有。
    // 用分类下标作为 key：不同分类的状态互不干扰。首次创建时用已保存的位置做初值。
    //
    // 位置的变化在各自那一页里用 snapshotFlow 记录（见下方的 Pager 页），
    // 不用 DisposableEffect 里保存：后者依赖「子组件的 onDispose 先于 NavHost
    // 保存状态」这一执行顺序，而 snapshotFlow 在每次滚动落地时就已写进 map。
    val gridStates = androidx.compose.runtime.remember(s.categories.size) {
        HashMap<Int, androidx.compose.foundation.lazy.grid.LazyGridState>()
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = s.currentTab,
    ) { s.categories.size }

    // 【Tab → Pager】点标签滚到对应页。
    // 只在两者不一致时滚，否则「滚停回调 → selectTab → 又触发滚动」会自激。
    LaunchedEffect(s.currentTab) {
        if (s.currentTab != pagerState.currentPage) {
            pagerState.animateScrollToPage(s.currentTab)
        }
    }

    // 【Pager → Tab】滑停之后才认账：拖动途中切分类会让下方的列表在手指下
    // 换内容，观感是「还没松手内容就变了」。settledPage 只在停稳时更新。
    LaunchedEffect(pagerState.settledPage) {
        vm.onShown(pagerState.settledPage)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.AppBg)
            // 【必须补状态栏避让】MainActivity 调了 enableEdgeToEdge()，内容会铺到
            // 状态栏底下。原工程的 EntryAbility 刻意不调 setWindowLayoutFullScreen，
            // 理由正是"开启后顶部按钮会被状态栏遮挡导致点击无效"——本移植踩了同一个坑。
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ---- 顶部栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 【品牌标记：矢量灯形】原来只有 "灯影" 两个字。
            // 灯形与自适应图标前景 `ic_launcher_foreground.xml` 同一造型（梯形灯罩 + 顶杆 + 光晕），
            // 使启动图标与首页标题视觉同源。
            DyIcon(
                icon = DyIcons.Lantern,
                tint = Palette.Brand,
                size = 22.dp,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = "灯影",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(end = 12.dp),
            )

            // FR-315：搜索条框（假输入框，整框点击进搜索页，不拦截真输入）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(Palette.SearchBg, CircleShape)
                    .clickable { onOpenSearch("") }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 【原为 "⌕" 字形】它是字体里的一个怪字符，各 ROM 字重/宽度不一致，
                // 14sp 下几乎看不清。改用矢量搜索图标。
                DyIcon(
                    icon = DyIcons.Search,
                    tint = Palette.TextSecondary,
                    size = 16.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text("搜索影视", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
            }

            // 我的
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .width(52.dp)
                    .height(36.dp)
                    .background(Palette.SearchBg, CircleShape)
                    .clickable(onClick = onOpenFavorites),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DyIcon(
                        icon = DyIcons.Person,
                        tint = Palette.TextSecondary,
                        size = 15.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("我的", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                }
            }
        }

        // ---- 分类 Tab ----
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(s.categories.size) { idx ->
                val cat = s.categories[idx]
                val selected = s.currentTab == idx
                Box(
                    // 点标签只改状态，翻页动作由上方 LaunchedEffect(s.currentTab) 统一驱动。
                    // 若这里也调 animateScrollToPage，两个 effect 会同时滚同一个 Pager。
                    modifier = Modifier
                        .clickable { vm.selectTab(idx) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Palette.Brand else Palette.TextSecondary,
                    )
                }
            }
        }

        // ---- 内容 ----
        //
        // 【左右滑动切分类】用 HorizontalPager 而不是「检测横滑就调 selectTab」。
        // 后者实测能用（左右各滑一次能切到相邻分类），但**没有跟手动画**——
        // 手指拖动时内容纹丝不动，松手才「啪」地换一屏。要跟手就得让相邻分类
        // 各自有自己的一份内容先在屏外备着，`HorizontalPager` 正是这件事；
        // VM 的状态也随之改成按分类分槽（见 IndexViewModel.CatState）。
        //
        // 【为什么刷新框在 Pager 里面】PullToRefreshBox 的 `isRefreshing`
        // 必须是「当前这一页」的状态。放在外面就只能取一个全局值，
        // A 页在刷新时 B 页也会显示加载条。
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 预组合相邻各一页：滑到一半就能看见下一页内容。
            // 取 1 而不是更大的值——每多预组合一页就多一整屏封面要解码。
            beyondViewportPageCount = 1,
        ) { page ->
            val cs = s.cat(page)
            val gridState = gridStates.getOrPut(page) {
                val p = savedScroll[page]
                androidx.compose.foundation.lazy.grid.LazyGridState(p?.first ?: 0, p?.second ?: 0)
            }

            LaunchedEffect(gridState, page) {
                androidx.compose.runtime.snapshotFlow {
                    gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                }.collect {
                    savedScroll[page] = it
                }
            }

            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = cs.refining,
                onRefresh = { vm.refresh(page) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    cs.loading && cs.items.isEmpty() -> LoadingView()
                    cs.errMsg.isNotEmpty() && cs.items.isEmpty() ->
                        // 同空态：错误态是"下拉重试"的第二现场，必须可拖动
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ErrorView(cs.errMsg, onRetry = { vm.loadData(page) })
                        }
                    cs.items.isEmpty() ->
                        // 【空态也要能下拉】`PullToRefreshBox` 的手势建立在子节点
                        // **能消费纵向拖动**之上：静态 `Box` 不滚动，手指下拉会被
                        // 判定为"没有可滚动内容"，刷新永远触发不了。
                        // 而"该分类暂无内容"正是用户最想下拉重试的时刻。
                        // 故用 fillMaxSize + verticalScroll 把内容变成可拖动的
                        // （内容不足一屏时不会真的滚，但拖动事件能传到刷新容器）。
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "该分类暂无内容",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Palette.TextSecondary,
                                )
                            }
                        }
                    else -> VideoGrid(
                        items = cs.items,
                        onOpenDetail = onOpenDetail,
                        onNearEnd = { vm.loadMore(page) },
                        loadingMore = cs.loadingMore,
                        // 当前分类**自己的**滚动状态（修"切分类串位置"）
                        state = gridState,
                        ranking = cs.ranking,
                        category = s.categories[page],
                        onRankingClick = { title ->
                            // 榜单条目没有可播源，点它 = 拿片名去搜（发现层的用途）
                            onOpenSearch(title)
                        },
                    )
                }
            }
        }
    }
}

/**
 * 豆瓣分类榜单横滑行（发现层）。
 *
 * 【点击语义】条目是**站外元数据**，本应用无法换链播放，故点击 = 拿片名去搜，
 * 不跳站外（跳出去看不了，还会把用户丢出应用）。
 *
 * 【为什么放在网格头部而不是单独一屏】榜单是"不知道看什么"时的入口，
 * 与分类浏览同一屏更顺，故只在 tab 下方占一整行。
 *
 * 【标题跟着分类走】初版这里写死「动漫热榜」，六个分类全都显示动漫榜；
 * 现在 [title] 由调用方传入当前分类名，如「电影热榜」「综艺热榜」。
 */
@Composable
private fun RankingRow(items: List<DiscoverItem>, title: String, onClick: (String) -> Unit) {
    Column {
        Text(
            text = "${title}热榜 · 点按搜索",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items.size) { i ->
                val it = items[i]
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .clickable { onClick(it.title) },
                ) {
                    Poster(
                        url = it.pic,
                        modifier = Modifier
                            .width(84.dp)
                            .height(116.dp),
                        cornerRadius = 6,
                    )
                    Text(
                        text = it.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (it.subtitle.isNotEmpty()) {
                        Text(
                            text = it.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Brand,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 视频网格。
 *
 * 用 `LazyVerticalGrid` 而非 `ForEach`：原工程的 `ForEach` 全量渲染是已知缺口
 * （NFR-006），长列表会整表重建。Lazy 版本只组合可见项。
 */
@Composable
private fun VideoGrid(
    items: List<VideoItem>,
    onOpenDetail: (String) -> Unit,
    onNearEnd: () -> Unit,
    /** 翻页请求进行中：底部显示加载指示（真机反馈"翻页没有任何反馈，像卡住"） */
    loadingMore: Boolean = false,
    /**
     * 本分类**自己的**滚动状态。
     *
     * 【真机反馈的 bug】初版不传 `state`，`LazyVerticalGrid` 用内部默认状态，
     * 而它在同一个组合位置上会被复用 —— 于是切分类时**滚动位置被继承**：
     * 电影滑到第 20 个，切到电视剧还停在第 20 个位置。
     *
     * 修法：按分类各持一个 `LazyGridState`（由调用方按 tab 索引缓存）。
     * 这样既解决"串位置"，又保留"切回该分类时记住上次位置"的合理行为。
     */
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    /** 本分类的豆瓣榜单（发现层）；空则不占位 */
    ranking: List<DiscoverItem> = emptyList(),
    /** 当前分类名，只用于榜单行标题（如「电影热榜」） */
    category: String = "",
    onRankingClick: (String) -> Unit = {},
) {
    // 触底检测：最后一项进入组合即触发翻页（等价于原工程的 onScrollEdge(Bottom)）
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (ranking.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                RankingRow(ranking, category, onRankingClick)
            }
        }
        items(items, key = { "${it._source}_${it.vod_id}_${it.vod_name}" }) { item ->
            VideoCard(item) {
                onOpenDetail(
                    Routes.detail(
                        vodId = item.vod_id,
                        source = item._source ?: "",
                        name = item.vod_name,
                        pic = item.vod_pic,
                        vodUri = item.vod_uri ?: "",
                    )
                )
            }
        }
        // 触底哨兵 + 翻页加载指示（主流应用形态：底部转圈 + 文案，占满整行）
        item(span = { GridItemSpan(3) }) {
            LaunchedEffect(items.size) { onNearEnd() }
            if (loadingMore) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = Palette.TextSecondary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.width(16.dp).height(16.dp),
                    )
                    Text(
                        text = "正在加载更多…",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCard(item: VideoItem, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box {
            Poster(
                url = item.vod_pic,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                cornerRadius = 8,
            )
            // 更新状态角标（FR-302：超长省略号）
            val remarks = item.vod_remarks
            if (!remarks.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 4.dp)
                        .background(Palette.BadgeScrim, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = remarks,
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = item.vod_name,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth(),
        )
    }
}
