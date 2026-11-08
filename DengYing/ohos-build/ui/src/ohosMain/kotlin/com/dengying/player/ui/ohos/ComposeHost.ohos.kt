@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.dengying.player.ui.ohos

import androidx.compose.ui.window.ComposeArkUIViewController
import com.dengying.player.ui.spike.SpikeScreen
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
 */
@CName("MainArkUIViewController")
fun mainArkUIViewController(env: napi_env): napi_value {
    initMainHandler(env)
    return ComposeArkUIViewController(env) {
        SpikeScreen()
    }
}
