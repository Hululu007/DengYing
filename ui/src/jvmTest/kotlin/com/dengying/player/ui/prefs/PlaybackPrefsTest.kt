package com.dengying.player.ui.prefs

import com.dengying.player.data.cache.MediaCache
import com.dengying.player.platform.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackPrefsTest {

    @Test
    fun `默认长按两倍速且缓存关闭`() {
        PlaybackPrefs.init(FakeKeyValueStore())

        assertEquals(2f, PlaybackPrefs.boostSpeed)
        assertFalse(PlaybackPrefs.cacheEnabled)
        assertFalse(MediaCache.isEnabled)
    }

    @Test
    fun `初始化读取已持久化档位与缓存开关`() {
        PlaybackPrefs.init(
            FakeKeyValueStore(
                mutableMapOf(
                    "ui_boost_speed" to "3.0",
                    "ui_media_cache" to "1",
                ),
            ),
        )

        assertEquals(3f, PlaybackPrefs.boostSpeed)
        assertTrue(PlaybackPrefs.cacheEnabled)
        // JVM 的 MediaCacheHost 是桩：用户意图保留，但实际不启用。
        assertFalse(MediaCache.isEnabled)
    }

    @Test
    fun `非档位值归一化到最近档并落盘`() {
        val kv = FakeKeyValueStore()
        PlaybackPrefs.init(kv)

        PlaybackPrefs.selectBoostSpeed(2.9f)

        assertEquals(3f, PlaybackPrefs.boostSpeed)
        assertEquals("3.0", kv.getString("ui_boost_speed", ""))

        PlaybackPrefs.init(kv)
        assertEquals(3f, PlaybackPrefs.boostSpeed)
    }

    @Test
    fun `缓存开关持久化并同步到播放缓存门面`() {
        val kv = FakeKeyValueStore()
        PlaybackPrefs.init(kv)

        PlaybackPrefs.setMediaCache(true)

        assertTrue(PlaybackPrefs.cacheEnabled)
        assertEquals("1", kv.getString("ui_media_cache", "0"))

        PlaybackPrefs.setMediaCache(false)
        assertEquals("0", kv.getString("ui_media_cache", "1"))
    }

    @Test
    fun `倍速标签保留一位小数`() {
        assertEquals("1.5x", PlaybackPrefs.labelOf(1.5f))
        assertEquals("2x", PlaybackPrefs.labelOf(2f))
        assertEquals("3x", PlaybackPrefs.labelOf(3f))
    }

    private class FakeKeyValueStore(
        private val values: MutableMap<String, String> = mutableMapOf(),
    ) : KeyValueStore {
        override fun getString(key: String, defValue: String): String =
            values[key] ?: defValue

        override fun allKeys(): Set<String> = values.keys.toSet()

        override fun putString(key: String, value: String, sync: Boolean): Boolean {
            values[key] = value
            return true
        }

        override fun remove(key: String, sync: Boolean): Boolean =
            values.remove(key) != null
    }
}
