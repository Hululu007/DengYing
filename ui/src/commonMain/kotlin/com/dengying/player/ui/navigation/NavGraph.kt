package com.dengying.player.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.SavedState
import androidx.savedstate.read
import com.dengying.player.data.download.DownloadCenter
import com.dengying.player.ui.AppStartupSignals
import com.dengying.player.ui.components.GlassBackdropState
import com.dengying.player.ui.components.LocalGlassBackdrop
import com.dengying.player.ui.components.glassBackdrop
import com.dengying.player.ui.pages.DetailScreen
import com.dengying.player.ui.pages.DownloadsScreen
import com.dengying.player.ui.pages.FavoritesScreen
import com.dengying.player.ui.pages.HistoryScreen
import com.dengying.player.ui.pages.IndexScreen
import com.dengying.player.ui.pages.LiveChannelsScreen
import com.dengying.player.ui.pages.LoginScreen
import com.dengying.player.ui.pages.PlayerScreen
import com.dengying.player.ui.pages.RegisterScreen
import com.dengying.player.ui.pages.SearchScreen
import com.dengying.player.ui.pages.SettingsScreen
import com.dengying.player.ui.pages.SourceStatusScreen
import com.dengying.player.ui.platform.PlatformBackHandler
import com.dengying.player.ui.platform.rememberFullscreenState
import com.dengying.player.ui.platform.systemBackNeedsHost
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.ui.update.UpdateChecker
import com.dengying.player.ui.update.UpdateNoticeController
import com.dengying.player.ui.update.UpdateNoticeDialog
import com.dengying.player.ui.viewmodel.PlayerViewModel

/**
 * 全应用的路由表与返回栈。
 *
 * 【为什么在 `:ui` 而不是 `:app`】
 * 路由的目标页面（含登录 / 注册共 9 个 Screen）与参数编解码（[Routes]）全部住在 `:ui`，
 * 把 NavHost 留在 `:app` 会让壳层反向依赖每一个页面。搬进来之后 `:app`
 * 只剩「Activity + 系统栏 + 调试入口」三件事，iOS 侧将来用同一份
 * NavHost 起 ComposeUIViewController 即可。
 *
 * 【为什么单 NavHost、单 Activity】
 * 播放页进入全屏要横屏，多 Activity 会在转屏时重建整条返回栈，
 * 用户按返回会回到"上一个 Activity"而不是详情页。单 Activity + Compose 导航
 * 让返回栈稳定，转屏只重组播放页。
 *
 * @param debugRoute 仅 debug 构建会用：一条待跳转的播放路由。由平台壳
 *   （`MainActivity`）从 Intent 里取出后传入，本函数消费后立即回调
 *   [onDebugRouteConsumed] 让壳清空，避免重组时重复跳。
 * @param onDebugRouteConsumed 消费完成通知；默认空实现，便于预览与测试。
 */
@Composable
fun DengYingNavHost(
    debugRoute: String? = null,
    onDebugRouteConsumed: () -> Unit = {},
    /** 进程级冷启动（非重建/非转屏）才自动查更新；壳按 savedInstanceState 判定 */
    coldStartAutoCheck: Boolean = false,
) {
    val nav = rememberNavController()

    // 调试路由：一旦被写入就跳转并清空，避免重组时重复跳
    LaunchedEffect(debugRoute) {
        if (!debugRoute.isNullOrEmpty()) {
            onDebugRouteConsumed()
            nav.navigate(debugRoute)
        }
    }

    // 当前路由：离开播放页时兜底恢复方向
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 全屏状态由**系统方向**驱动（`LocalConfiguration.orientation`），
    // 不在应用侧另存一个布尔——这是照成熟做法重写的关键，见 ui/platform/Fullscreen.kt 的说明
    val fullscreen = rememberFullscreenState()

    // 离开播放页时兜底：只要不在播放页就回竖屏。
    // 不依赖任何一次点击是否成功，异常状态能自愈。
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && !currentRoute.startsWith(Routes.PLAYER)) {
            fullscreen.exit()
        }
    }

    // 【采样状态是全局单例，采样图层是每页私有的】
    // 曾经把 `Modifier.glassBackdrop` 挂在下面这个 Box 上（祖先），它把整棵
    // NavHost 录进图层——玻璃件自己也在图层里，玻璃再 drawLayer(同一图层)
    // 就是自引用，RenderThread 无限递归到栈溢出（SIGSEGV / 512 frames）。
    // 现在采样层由每个页面的 `Modifier.glassPageBackdrop()` 提供，且**只录
    // 柔光渐变、不录内容树**，所以图层里永远没有玻璃件，结构上不可能递归。
    // 这里只负责把 state 挂进 CompositionLocal 供 GlassSurface 读取。
    val glassBackdrop = remember { GlassBackdropState() }
    CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
    val deps = remember { UiDepsHolder.get() }
    // 自动检查不阻塞首帧：请求在 IO 上发出，失败只更新「我的」页状态。
    // 只认冷启动：转屏/重建会让 NavHost 重组，但不应周期性再弹更新提示。
    LaunchedEffect(coldStartAutoCheck) {
        if (!coldStartAutoCheck) return@LaunchedEffect
        // 【让位给首屏】冷启动时更新检查和首页首批、发现页首条解析抢同一个
        // 出网闸（6 席）。等首屏内容出现（最多 8s）再以 Background 档启动，
        // 用户先看到内容；更新提示晚几秒没有感知（计划 18 §12.2）。
        AppStartupSignals.awaitFirstContent(8_000)
        kotlinx.coroutines.withContext(com.dengying.player.data.net.NetPriority.Background) {
            UpdateNoticeController.autoCheckOnColdStart(deps)
        }
    }
    // 【鸿蒙没有系统返回分发器】Android 的 NavHost 自己接了 OnBackPressedDispatcher，
    // 这里只给 `systemBackNeedsHost` 的平台补一条路由级返回：有上一页时 popBackStack；
    // 根页不注册 → dispatch 返回 false → 系统按默认行为退出应用（与 Android 根页一致）。
    if (systemBackNeedsHost) {
        PlatformBackHandler(enabled = nav.previousBackStackEntry != null) {
            nav.popBackStack()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = Routes.INDEX) {

        composable(Routes.INDEX) {
            IndexScreen(
                onOpenSearch = { kw -> nav.navigate(Routes.search(kw)) },
                onOpenFavorites = { nav.navigate(Routes.FAVORITES) },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenSourceStatus = { nav.navigate(Routes.SOURCE_STATUS) },
                onOpenLiveChannels = { nav.navigate(Routes.LIVE_CHANNELS) },
                onOpenLogin = { nav.navigate(Routes.LOGIN) },
                onOpenDetail = { route -> nav.navigate(route) },
            )
        }

        composable(
            route = "${Routes.SEARCH}?kw={kw}",
            arguments = listOf(
                navArgument("kw") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            SearchScreen(
                onBack = { nav.popBackStack() },
                onOpenDetail = { route -> nav.navigate(route) },
                // 从首页榜单点进来的关键词；空串 = 普通进页
                initialKeyword = entry.arguments?.stringArg("kw") ?: "",
            )
        }

        composable(
            route = "${Routes.LIVE_PLAYER}?url={url}&name={name}&source={source}&headers={headers}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("source") { type = NavType.StringType; defaultValue = "" },
                navArgument("headers") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val a = entry.arguments
            DetailScreen(
                vodId = 0L,
                source = dec(a?.stringArg("source")),
                name = dec(a?.stringArg("name")),
                pic = "",
                vodUri = "",
                resumeEpisode = -1,
                autoPlay = true,
                onBack = { nav.popBackStack() },
                onOpenPlayer = {},
                pageActive = backStackEntry?.id == entry.id,
                liveMode = true,
                liveUrl = dec(a?.stringArg("url")),
                liveHeaders = dec(a?.stringArg("headers")),
            )
        }

        composable(
            route = "${Routes.DETAIL}?vodId={vodId}&source={source}&name={name}&pic={pic}" +
                "&vodUri={vodUri}&resumeEpisode={resumeEpisode}&autoPlay={autoPlay}" +
                "&startAtMs={startAtMs}",
            arguments = listOf(
                navArgument("vodId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("source") { type = NavType.StringType; defaultValue = "" },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("pic") { type = NavType.StringType; defaultValue = "" },
                navArgument("vodUri") { type = NavType.StringType; defaultValue = "" },
                navArgument("resumeEpisode") { type = NavType.IntType; defaultValue = -1 },
                navArgument("autoPlay") { type = NavType.BoolType; defaultValue = false },
                navArgument("startAtMs") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val a = entry.arguments
            // 【P-06】"本页是否仍是栈顶"由导航状态直接回答。
            // 用 `entry.id` 比对而不是只比路由：返回栈里可能有多个详情页，
            // 只有最上面那个该显示内嵌画面，下面被遮住的那个只留黑底。
            // 该判据在 `popBackStack()` 调用时立即成立，与转场动画无关。
            val isTopEntry = backStackEntry?.id == entry.id
            DetailScreen(
                vodId = a?.longArg("vodId") ?: 0L,
                source = dec(a?.stringArg("source")),
                name = dec(a?.stringArg("name")),
                pic = dec(a?.stringArg("pic")),
                vodUri = dec(a?.stringArg("vodUri")),
                resumeEpisode = a?.intArg("resumeEpisode") ?: -1,
                autoPlay = a?.boolArg("autoPlay") ?: false,
                startAtMs = a?.longArg("startAtMs") ?: -1L,
                onBack = { nav.popBackStack() },
                onOpenPlayer = { route -> nav.navigate(route) },
                pageActive = isTopEntry,
            )
        }

        composable(
            route = "${Routes.PLAYER}?url={url}&title={title}&vodId={vodId}&source={source}" +
                "&vodName={vodName}&vodPic={vodPic}&vodUri={vodUri}&lineFrom={lineFrom}" +
                "&episodeIndex={episodeIndex}&episodeName={episodeName}" +
                "&altUrls={altUrls}&epUrls={epUrls}&epNames={epNames}&startAtMs={startAtMs}" +
                "&headersJson={headersJson}&localFile={localFile}&live={live}",
            // 【退场仍用滑动】播放画面现在是 TextureView（参与正常 View 图层合成），
            // alpha 淡出理论上不会再有 SurfaceView 打洞渲染的帧滞留问题；
            // 这里保留 220ms 滑动是为了不额外改动转场行为，二者都可用。
            popExitTransition = {
                androidx.compose.animation.slideOutVertically(
                    animationSpec = androidx.compose.animation.core.tween(220),
                ) { it }
            },
            exitTransition = {
                androidx.compose.animation.slideOutVertically(
                    animationSpec = androidx.compose.animation.core.tween(220),
                ) { -it }
            },
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("vodId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("source") { type = NavType.StringType; defaultValue = "" },
                navArgument("vodName") { type = NavType.StringType; defaultValue = "" },
                navArgument("vodPic") { type = NavType.StringType; defaultValue = "" },
                navArgument("vodUri") { type = NavType.StringType; defaultValue = "" },
                navArgument("lineFrom") { type = NavType.StringType; defaultValue = "" },
                navArgument("episodeIndex") { type = NavType.IntType; defaultValue = 0 },
                navArgument("episodeName") { type = NavType.StringType; defaultValue = "" },
                navArgument("altUrls") { type = NavType.StringType; defaultValue = "" },
                navArgument("epUrls") { type = NavType.StringType; defaultValue = "" },
                navArgument("epNames") { type = NavType.StringType; defaultValue = "" },
                // FR-705：内嵌态切全屏时带过来的起播位置
                navArgument("startAtMs") { type = NavType.LongType; defaultValue = 0L },
                // FR-705：适配器换链产出的请求头（避免播放页重新换链）
                navArgument("headersJson") { type = NavType.StringType; defaultValue = "" },
                navArgument("localFile") { type = NavType.BoolType; defaultValue = false },
                navArgument("live") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val a = entry.arguments
            val params = PlayerViewModel.PlayerParams(
                url = dec(a?.stringArg("url")),
                title = dec(a?.stringArg("title")),
                vodId = a?.longArg("vodId") ?: 0L,
                source = dec(a?.stringArg("source")),
                vodName = dec(a?.stringArg("vodName")),
                vodPic = dec(a?.stringArg("vodPic")),
                vodUri = dec(a?.stringArg("vodUri")),
                lineFrom = dec(a?.stringArg("lineFrom")),
                episodeIndex = a?.intArg("episodeIndex") ?: 0,
                episodeName = dec(a?.stringArg("episodeName")),
                altUrls = splitList(dec(a?.stringArg("altUrls"))),
                epUrls = splitList(dec(a?.stringArg("epUrls"))),
                epNames = splitList(dec(a?.stringArg("epNames"))),
                // FR-705：内嵌态切全屏时带过来的起播位置
                startAtMs = a?.longArg("startAtMs") ?: 0L,
                // FR-705：适配器换链产出的请求头
                headersJson = dec(a?.stringArg("headersJson")),
                localFile = a?.boolArg("localFile") ?: false,
                live = a?.boolArg("live") ?: false,
            )
            PlayerScreen(
                params = params,
                onBack = {
                    // 【返回语义】换源/解析失败后用户按返回，期望是「回到这部片的详情页」，
                    // 而不是退出应用。分两种情况：
                    //   a) 栈里下面就是详情页（绝大多数路径）→ 正常回退
                    //   b) 播放页是直接进来的（调试入口、将来的「继续观看」直达）
                    //      → 用 params 里的 source/vodId 构造详情页路由，并**替换**本页
                    val prev = nav.previousBackStackEntry?.destination?.route
                    if (prev != null && (prev.startsWith(Routes.DETAIL) || prev.startsWith(Routes.DOWNLOADS))) {
                        nav.popBackStack()
                    } else if (params.source.isNotEmpty() && params.vodId != 0L) {
                        nav.navigate(
                            Routes.detail(
                                vodId = params.vodId,
                                source = params.source,
                                name = params.vodName.ifEmpty { params.title },
                                pic = params.vodPic,
                                vodUri = params.vodUri,
                            ),
                        ) {
                            // 用替换而不是叠加：否则再按一次返回会回到刚失败的播放页
                            popUpTo(Routes.PLAYER) { inclusive = true }
                        }
                    } else {
                        nav.popBackStack()
                    }
                },
                fullscreen = fullscreen,
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onBack = { nav.popBackStack() },
                onOpenDetail = { route -> nav.navigate(route) },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenSourceStatus = { nav.navigate(Routes.SOURCE_STATUS) },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { nav.popBackStack() },
                onOpenDetail = { route -> nav.navigate(route) },
            )
        }

        composable(Routes.SOURCE_STATUS) {
            SourceStatusScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.LIVE_CHANNELS) {
            LiveChannelsScreen(
                onBack = { nav.popBackStack() },
                onOpenLive = { route -> nav.navigate(route) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onBack = { nav.popBackStack() },
                onOpenPlayer = { item ->
                    // 【断网也要能播】下载项直接进播放页并带 localFile=true。
                    // 之前走详情页路由，离线时详情请求失败（「详情加载失败：桔子
                    // 无数据或请求失败」），本地文件根本没机会起播。iOS/鸿蒙
                    // 早就走播放页直连，这里对齐。
                    val localUrl = DownloadCenter.localPlayUrl(item)
                    if (localUrl.isNotEmpty()) {
                        nav.navigate(
                            Routes.player(
                                url = localUrl,
                                title = item.vodName,
                                vodId = item.vodId,
                                source = item.source,
                                vodName = item.vodName,
                                vodPic = item.vodPic,
                                vodUri = "",
                                lineFrom = "离线下载",
                                episodeIndex = item.episodeIndex,
                                episodeName = item.episodeName,
                                altUrls = emptyList(),
                                epUrls = emptyList(),
                                epNames = emptyList(),
                                localFile = true,
                            ),
                        )
                    }
                },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onBack = { nav.popBackStack() },
                onOpenRegister = { nav.navigate(Routes.REGISTER) },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(onBack = { nav.popBackStack() })
        }
        }

        UpdateNoticeController.dialogInfo?.let { info ->
            UpdateNoticeDialog(
                info = info,
                ignored = UpdateNoticeController.isIgnored(info),
                onIgnoreChange = { ignored ->
                    UpdateNoticeController.setIgnored(deps, info, ignored)
                },
                onDismiss = { UpdateNoticeController.dismissDialog() },
                onCopy = {
                    deps.copyText(info.url)
                },
                onOpen = {
                    val opened = deps.openExternalUrl(info.url)
                    if (opened) UpdateNoticeController.dismissDialog()
                    opened
                },
                onOpenApk = {
                    // Gitee 的附件 MIME 是 application/zip，浏览器会把 APK 存成 .zip；
                    // 主按钮改走 GitHub 的 APK 直链，拿到的文件才是 .apk。
                    val apkUrl = UpdateChecker.githubApkUrl(info.version)
                    val opened = apkUrl != null && deps.openExternalUrl(apkUrl)
                    if (opened) UpdateNoticeController.dismissDialog()
                    opened
                },
            )
        }
    }
    }
}

/**
 * 参数读取扩展。
 *
 * 【为什么需要这一层】JB 的 KMP navigation 里 `NavBackStackEntry.arguments` 是
 * `androidx.savedstate.SavedState`，不再是 Android 的 `Bundle`，因此没有
 * `getString`/`getLong` 这套方法。跨平台读取入口是 `SavedState.read { }`，
 * 块内 `SavedStateReader` 提供 `getXxxOrNull`。这里只包一层，避免 22 个调用点
 * 各写一次 `read { }`。
 *
 * 语义与迁移前的 `Bundle.getXxx` 一致：键不存在或类型不符都返回 null，
 * 由调用点用 `?: 默认值` 兜底（迁移前也是这么写的，默认值与 `navArgument`
 * 的 `defaultValue` 是两层保险）。
 */
private fun SavedState.stringArg(key: String): String? = read { getStringOrNull(key) }
private fun SavedState.longArg(key: String): Long? = read { getLongOrNull(key) }
private fun SavedState.intArg(key: String): Int? = read { getIntOrNull(key) }
private fun SavedState.boolArg(key: String): Boolean? = read { getBooleanOrNull(key) }
