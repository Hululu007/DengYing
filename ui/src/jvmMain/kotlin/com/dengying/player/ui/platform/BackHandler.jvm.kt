package com.dengying.player.ui.platform

import androidx.compose.runtime.Composable

/**
 * 桌面 actual：空实现。
 *
 * 桌面窗口没有「系统返回键」这个概念，故不需要拦截。刻意**不报错**：
 * 报错会让桌面侧的任何一次组合预览直接崩溃，而桌面目标的存在理由
 * （docs/09 D-3）是验证 commonMain 能编译、能跑测试，不是做可用的桌面播放器。
 *
 * 返回值语义与 Android 侧一致 —— 都是「什么都不发生」之外的同一件事：
 * 调用方（`PlayerScreen`）依赖的是「返回时能回到详情页」这条业务语义，
 * 而桌面端根本没有触发它的入口，故没有语义缺口。
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // 空实现：不注册任何回调。
}
