@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
package com.dengying.player.ui.ohos
import androidx.compose.ui.window.ComposeArkUIViewController
import com.dengying.player.playback.PlaybackSession
import com.dengying.player.ui.navigation.DengYingNavHost
import com.dengying.player.ui.theme.DengYingTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.initMainHandler
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_env
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value
import kotlin.experimental.ExperimentalNativeApi
/**
 * ArkTS 宿主进入 Compose 的入口（docs/04 §16.40）。
 *
 * 与社区样例 xiaqingpeng/HarmonyKMP 逐字对齐：
 *  - 入口符号名就叫 MainArkUIViewController（har 的 Compose 组件按此契约取 controller）
 *  - 创建 controller 前必须先 initMainHandler(env) 建主线程 TSFN
 *
 * 必须是顶层函数：@CName 挂在 object 成员上会多带一个 receiver 参数。
 *
 * 【内容与 Android 完全同源】`DengYingTheme { DengYingNavHost() }` 就是
 * `D:/workspace/dengY/DengYingOhos/app/src/main/java/com/dengying/player/MainActivity.kt`
 * 的 `setContent { DengYingTheme { DengYingNavHost(...) } }`——7 条路由、返回栈、
 * 页面实现全部来自 `:ui` 的 commonMain，鸿蒙侧没有第二份 UI。
 *
 * 【为什么先 installOhosUiDeps()】各 Screen 的首个组合就调 `UiDepsHolder.get()`，
 * 注入晚一步即启动期崩溃。
 */

@CName("MainArkUIViewController")
fun mainArkUIViewController(env: napi_env): napi_value {
    initMainHandler(env)
    installOhosUiDeps()
    // 对齐 Android DengYingApp.onCreate：播放会话必须在任何页面发起播放前拿到引擎宿主。
    // 幂等，重复进入 controller 不会重置会话。
    PlaybackSession.init(OhosUiDeps.engineHost)
    return ComposeArkUIViewController(env) {
        DengYingTheme { DengYingNavHost() }
    }
}
