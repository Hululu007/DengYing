package com.dengying.player.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dengying.player.player.PlayerEngine

/**
 * ohos 端平台能力 actual（CMP spike，docs/04 §16.40）。
 *
 * 首批一律给「语义正确的降级实现」——口径同 `Platform.jvm.kt`（桌面端）：
 * 先让 commonMain 整层编过、页面能渲染，真窗口/亮度/音量/画面能力随后按
 * parity 清单逐项补（§16.40 未验证清单）。
 */

/** 画面承载待接 ArkUI XComponent 互操作（ui-ohos klib 的 insertInteropView 路径）：先黑底占位。 */
@Composable
actual fun VideoSurface(
    engine: PlayerEngine?,
    modifier: Modifier,
    onBind: (bound: Boolean) -> Unit,
) {
    Box(modifier.fillMaxSize().background(Color.Black))
}

/** 窗口方向/系统栏控制待接 ohos window API：先空实现（enter/exit 幂等、不抛，符合契约 3）。 */
actual class FullscreenState internal constructor() {
    actual fun enter(landscape: Boolean) {}
    actual fun exit() {}
}

@Composable
actual fun rememberFullscreenState(): FullscreenState = FullscreenState()

@Composable
actual fun ConsumePortraitOrientation() {}

/** 亮度：返回非 null 空实现实例（JVM 同口径），保证 UI 分支与桌面一致。 */
actual class BrightnessController internal constructor() {
    private var last = Float.NaN

    actual fun current(): Float {
        if (!last.isNaN()) return last
        last = 0.5f
        return last
    }

    actual fun adjust(base: Float, fraction: Float): Float {
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

actual class VolumeController internal constructor() {
    private companion object {
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

/** 系统返回：ArkTS 壳的页面级 Back 处理已经覆盖导航回退，组合内先空实现（iOS 同口径）。 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}

/** 方向：先恒竖屏版式；接 window properties 后改真值。 */
@Composable
actual fun rememberIsLandscape(): Boolean = false
