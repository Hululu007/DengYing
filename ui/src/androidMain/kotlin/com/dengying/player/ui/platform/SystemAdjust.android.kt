package com.dengying.player.ui.platform

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/**
 * Android actual：窗口亮度与媒体音量（docs/08 P-04 / P-05）。
 *
 * ## 职责边界（docs/08 §2 设计约束 1）
 *
 * 只有本文件的两个类允许触碰窗口与音频 API。`domain` 与 `playback` 里
 * 不出现 `WindowManager`、`AudioManager`（同 S-2 的依赖方向约束）。
 * 手势判定留在 `PlayerScreen`；本文件只做"给一个数值，设置到系统"。
 *
 * 具体语义（亮度对象是当前窗口、起点取值顺序、0.01 下限、谁设置谁恢复、
 * 音量只动媒体流且不弹系统音量条）见 expect 声明处的 Kdoc —— 那些是
 * 迁移前已定稿的行为，actual 必须逐条照做。
 */
actual class BrightnessController(private val activity: Activity) {

    /** 本次播放会话内由本类写入的值；NaN 表示尚未写入（[restore] 无事可做） */
    private var lastSet = Float.NaN

    actual fun current(): Float {
        if (!lastSet.isNaN()) return lastSet
        val w = activity.window.attributes.screenBrightness
        if (w in 0f..1f) {
            lastSet = w
            return w
        }
        return try {
            Settings.System.getInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            ) / 255f
        } catch (e: Exception) {
            0.5f
        }
    }

    actual fun adjust(base: Float, fraction: Float): Float {
        val v = (base + fraction).coerceIn(0.01f, 1f)
        lastSet = v
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = v
        }
        return v
    }

    actual fun restore() {
        if (lastSet.isNaN()) return
        lastSet = Float.NaN
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
}

/**
 * 媒体音量控制（P-05）。
 *
 * 用 `Application` 上下文构造即可：`AudioManager` 不需要 Activity。
 */
actual class VolumeController(context: Context) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val maxVolume: Int = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    actual fun currentFraction(): Float =
        if (maxVolume <= 0) 0f
        else am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume

    actual fun adjust(base: Float, fraction: Float): Float {
        if (maxVolume <= 0) return 0f
        // 音量是整数档：先换算成档位再夹到系统上下限，不越界
        val target = ((base + fraction) * maxVolume).roundToInt().coerceIn(0, maxVolume)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return target.toFloat() / maxVolume
    }
}

@Composable
actual fun rememberBrightnessController(): BrightnessController? {
    val activity = LocalContext.current.findActivity()
    return remember(activity) { activity?.let { BrightnessController(it) } }
}

@Composable
actual fun rememberVolumeController(): VolumeController? {
    val ctx = LocalContext.current
    return remember(ctx) { VolumeController(ctx.applicationContext) }
}
