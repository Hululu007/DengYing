package com.dengying.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dengying.player.ui.navigation.DengYingNavHost
import com.dengying.player.ui.theme.DengYingTheme
import com.dengying.player.ui.theme.ThemeMode
import com.dengying.player.ui.theme.ThemePrefs

/**
 * 单 Activity 入口。只做三件事：edge-to-edge、调试 Intent 消费、起 Compose 内容。
 *
 * 【P4-6 之后这里只剩壳】路由表与返回栈已搬到 `:ui` 的
 * `navigation/NavGraph.kt`（`DengYingNavHost`）。原工程的结构对应：
 *  - 鸿蒙的 `pages` 目录 + `main_pages.json` → Compose Navigation 的路由表
 *  - 鸿蒙的 `EntryAbility` 窗口设置 → 这里的 edge-to-edge 与系统栏策略
 *
 * 【为什么单 Activity】
 * 播放页进入全屏要横屏，多 Activity 会在转屏时重建整条返回栈，
 * 用户按返回会回到"上一个 Activity"而不是详情页。单 Activity + Compose 导航
 * 让返回栈稳定，转屏只重组播放页。
 *
 * 【为什么壳里还留 Intent 解析】
 * `Intent` 是 Android 独有概念，跨平台层不该知道它。壳把 `Intent` 里的
 * 路由字符串取出来交给 `DengYingNavHost`，iOS 侧将来用同一个 NavHost
 * 起 `ComposeUIViewController`，调试入口各自实现。
 */
class MainActivity : ComponentActivity() {

    /**
     * 调试入口（仅 debug 构建会用）：把一条播放路由经 Intent 带进来，
     * 由 [DengYingNavHost] 消费。存在的理由是**让真机验证不依赖坐标点击**——
     * 详见 `DebugPlayReceiver` 的说明。
     */
    companion object {
        const val ACTION_DEBUG_ROUTE = "com.dengying.player.DEBUG_ROUTE"
        const val EXTRA_DEBUG_ROUTE = "debug_route"
    }

    /** 待消费的调试路由；由 `onCreate`/`onNewIntent` 写入，Composable 读后清空 */
    private val pendingDebugRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeDebugIntent(intent)
        setContent {
            DengYingTheme {
                // 【系统栏图标配色必须跟着 App 内主题走，不能跟系统走】
                // enableEdgeToEdge() 的默认实现按 **系统** 深浅色决定状态栏图标是黑还是白。
                // 但本 App 允许在「我的」里把主题钉成浅色/深色，与系统设置无关：
                // 系统是浅色、App 选深色时，状态栏会画黑字压在深色玻璃上，几乎不可读。
                // 所以这里每次重组都把 isAppearanceLightStatusBars 同步到**实际生效的主题**。
                val view = LocalView.current
                val dark = when (ThemePrefs.mode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                SideEffect {
                    val window = window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    // 三键导航栏同理：浅色主题要深色图标，深色主题要浅色图标。
                    controller.isAppearanceLightNavigationBars = !dark
                }
                DengYingNavHost(
                    debugRoute = pendingDebugRoute.value,
                    onDebugRouteConsumed = { pendingDebugRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeDebugIntent(intent)
    }

    private fun consumeDebugIntent(intent: Intent?) {
        if (intent?.action == ACTION_DEBUG_ROUTE) {
            pendingDebugRoute.value = intent.getStringExtra(EXTRA_DEBUG_ROUTE)
        }
    }
}
