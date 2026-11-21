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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dengying.player.playback.PlaybackEngineHolder
import com.dengying.player.playback.shouldResumeAfterStop
import com.dengying.player.player.PlaybackProbe
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

    /**
     * 退后台那一刻引擎是否在播（且仍有播放表面）。
     * `onStop` 写、`onStart` 读后清零——这是「亮屏不自动续播」的判定依据。
     */
    private var resumeAfterStop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeDebugIntent(intent)
        // 【退后台必须停声】发现页预览/详情内嵌这类「组合仍活着」的播放位，
        // Home/切走时不会触发组合销毁，引擎没人 pause（真机实测：退到桌面
        // 声音继续）。组合层管不到生命周期，唯一的收口点是 Activity 的
        // ON_STOP——在此统一暂停全局引擎，回前台由各播放位自己的续播逻辑
        // 恢复（发现页回焦自动续播是既有行为）。peek() 引擎不存在时（还没
        // 播过任何东西）不动它，避免无谓创建。
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // 注意转型坑：Media3Engine 实现的是 PlayerEngine，不是
                // androidx.media3.common.Player，`as? Player` 恒 null（真机
                // 实测退后台声不停）——直接走 PlayerEngine.pause()。
                val engine = PlaybackEngineHolder.peek()
                // 【回前台只恢复「本来在播」的】旧实现 onStart 无条件 play()，
                // 用户手动暂停后锁屏/亮屏会被强行续播。这里先记录意图再暂停。
                resumeAfterStop = engine?.isPlaying == true &&
                    PlaybackEngineHolder.surfaceCount() > 0
                engine?.pause()
                PlaybackProbe.note(
                    "foreground-stop",
                    "wasPlaying=$resumeAfterStop surfaces=${PlaybackEngineHolder.surfaceCount()}",
                )
            }

            override fun onStart(owner: LifecycleOwner) {
                // 只有退后台时确实在播、且播放表面仍在，才恢复；
                // 无会话/已暂停/离开播放位一律不动引擎。
                val resume = shouldResumeAfterStop(
                    resumeAfterStop,
                    PlaybackEngineHolder.surfaceCount(),
                )
                resumeAfterStop = false
                if (!resume) return
                PlaybackEngineHolder.peek()?.play()
                PlaybackProbe.note("foreground-resume")
            }
        })
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
                    // 只有进程级冷启动（onCreate 没有恢复状态）才自动查更新；
                    // 转屏/重建/从后台回来的组合重建都不该再弹一次
                    coldStartAutoCheck = savedInstanceState == null,
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
