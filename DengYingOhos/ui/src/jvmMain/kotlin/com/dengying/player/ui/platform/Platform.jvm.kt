package com.dengying.player.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dengying.player.player.PlayerEngine

/**
 * 桌面（JVM）端的平台能力实现。
 *
 * ## 这个文件为什么全是空实现
 *
 * 桌面目标的存在理由（docs/09 D-3）是**验证 commonMain 能编译、能跑测试**，
 * 不是做一个可用的桌面播放器。故凡属"平台窗口/音频/视图互操作"的能力，
 * 这里一律给出**语义正确的空实现**，而不是报错：
 *
 *  - 报错会让任何一次桌面侧的组合预览直接崩溃，掩盖真正要验证的编译问题；
 *  - 空实现让 UI 能完整跑起来（黑画面、无全屏、无亮度音量手势），
 *    正好把注意力集中在跨平台代码本身。
 *
 * 各实现刻意保持与 Android 侧**相同的公开形状与返回值域**，以便调用方的
 * 分支逻辑（例如 `brightnessAvailable = controller != null`）在两端一致。
 */

/** 桌面无画面承载能力：只画一块黑底，占住调用方给定的尺寸。 */
@Composable
actual fun VideoSurface(
    engine: PlayerEngine?,
    modifier: Modifier,
    onBind: (bound: Boolean) -> Unit,
) {
    // 不回调 onBind(true)：桌面侧从未真正绑上播放器，虚报会让排障信息失真。
    Box(modifier.fillMaxSize().background(Color.Black))
}

/** 桌面无窗口方向概念：所有动作都是空操作。 */
actual class FullscreenState internal constructor() {
    actual fun enter(landscape: Boolean) {
        // 桌面窗口没有"请求横屏"这回事，空操作即为正确语义。
    }

    actual fun exit() {
    }
}

/** 桌面恒返回空实现（不返回 null：调用方不该因"没有全屏能力"而改变 UI 分支）。 */
@Composable
actual fun rememberFullscreenState(): FullscreenState = FullscreenState()

/** 桌面无方向收敛需求，空操作。 */
@Composable
actual fun ConsumePortraitOrientation() {
}

/**
 * 桌面亮度控制器。
 *
 * 返回**非 null 实例**而不是 null：调用方用 `!= null` 决定是否启用亮度手势，
 * 若这里返回 null 会让桌面侧的 UI 分支与 Android 不同，从而掩盖真正的
 * 跨平台编译问题。实例内部是空操作，`adjust` 原样返回计算值。
 */
actual class BrightnessController internal constructor() {
    private var last = Float.NaN

    actual fun current(): Float {
        if (!last.isNaN()) return last
        // 与 Android 侧"读不到系统亮度时退化为 0.5"同口径。
        last = 0.5f
        return last
    }

    actual fun adjust(base: Float, fraction: Float): Float {
        // 取值范围与 Android 侧一致（下限不取 0），保证浮层显示的数值同域。
        val v = (base + fraction).coerceIn(0.01f, 1f)
        last = v
        return v
    }

    actual fun restore() {
        last = Float.NaN
    }
}

@Composable
actual fun rememberBrightnessController(): BrightnessController? = BrightnessController()

/**
 * 桌面音量控制器。
 *
 * 与亮度同理返回非 null 实例，内部空操作。整数档换算保留 —— 让浮层显示的
 * 数值在两端都是"档位化"的结果，不至于桌面侧显示无限细分的浮点值。
 */
actual class VolumeController internal constructor() {
    private companion object {
        /** 与 Android 侧典型 STREAM_MUSIC 档位一致，仅用于数值同域 */
        const val MAX_STEPS = 15
    }

    actual fun currentFraction(): Float = 0.5f

    actual fun adjust(base: Float, fraction: Float): Float {
        val target = ((base + fraction) * MAX_STEPS).toInt().coerceIn(0, MAX_STEPS)
        return target.toFloat() / MAX_STEPS
    }
}

@Composable
actual fun rememberVolumeController(): VolumeController? = VolumeController()

/**
 * JVM actual：桌面端没有"设备方向"概念，恒为 false。
 *
 * 不用 `LocalWindowInfo` 按宽高比推断 —— 那会让播放器在宽窗口里自动变成
 * "横屏版式"，与桌面窗口的自由拉伸语义冲突；桌面端固定用竖屏版式。
 */
@Composable
actual fun rememberIsLandscape(): Boolean = false
