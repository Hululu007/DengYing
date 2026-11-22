package com.dengying.player.ui.prefs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dengying.player.data.cache.MediaCache
import com.dengying.player.platform.KeyValueStore
import kotlin.math.abs

/**
 * 播放偏好（长按临时倍速 / 媒体缓存）。
 *
 * 持久化形状与 [com.dengying.player.ui.theme.ThemePrefs] 一致：
 * `KeyValueStore` 只收字符串，布尔与浮点在这里做窄转换。
 */
object PlaybackPrefs {

    const val DEFAULT_BOOST_SPEED = 2f

    /**
     * 长按画面任意位置的临时倍速档位。
     *
     * 上限 3x：OHOS 播放内核的倍速表最高到 3x，三端使用同一份设置，
     * 不能提供只有 Android 能兑现的 4x。
     */
    val boostSpeeds = listOf(1.5f, 2f, 3f)

    private const val KEY_BOOST_SPEED = "ui_boost_speed"
    private const val KEY_MEDIA_CACHE = "ui_media_cache"

    private var kv: KeyValueStore? = null

    var boostSpeed by mutableStateOf(DEFAULT_BOOST_SPEED)
        private set

    /** 用户是否开启媒体缓存。是否真正生效还取决于平台实现。 */
    var cacheEnabled by mutableStateOf(false)
        private set

    fun init(store: KeyValueStore?) {
        kv = store
        boostSpeed = normalizeBoost(store?.getString(KEY_BOOST_SPEED, "")?.toFloatOrNull())
        cacheEnabled = store?.getString(KEY_MEDIA_CACHE, "0") == "1"
        MediaCache.setEnabled(cacheEnabled)
    }

    fun selectBoostSpeed(value: Float) {
        boostSpeed = normalizeBoost(value)
        kv?.putString(KEY_BOOST_SPEED, boostSpeed.toString(), sync = false)
    }

    fun setMediaCache(on: Boolean) {
        cacheEnabled = on
        kv?.putString(KEY_MEDIA_CACHE, if (on) "1" else "0", sync = false)
        MediaCache.setEnabled(on)
    }

    fun labelOf(value: Float): String =
        if (value == value.toInt().toFloat()) "${value.toInt()}x" else "${value}x"

    private fun normalizeBoost(value: Float?): Float {
        val target = value?.takeIf { it.isFinite() } ?: return DEFAULT_BOOST_SPEED
        return boostSpeeds.minByOrNull { abs(it - target) } ?: DEFAULT_BOOST_SPEED
    }
}
