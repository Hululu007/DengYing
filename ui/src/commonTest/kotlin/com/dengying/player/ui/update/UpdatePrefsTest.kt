package com.dengying.player.ui.update

import com.dengying.player.platform.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatePrefsTest {

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

        override fun remove(key: String, sync: Boolean): Boolean {
            values.remove(key)
            return true
        }
    }

    @Test
    fun `auto check is due every 6 hours`() {
        val prefs = UpdatePrefs(FakeKeyValueStore())
        val now = 1_700_000_000_000L

        assertTrue(prefs.shouldAutoCheck(now))
        prefs.markChecked(now)
        assertFalse(prefs.shouldAutoCheck(now + 5 * 60 * 60 * 1000L))
        assertTrue(prefs.shouldAutoCheck(now + 6 * 60 * 60 * 1000L))
    }

    @Test
    fun `clock moving backwards does not trigger repeated checks`() {
        val prefs = UpdatePrefs(FakeKeyValueStore())
        val now = 1_700_000_000_000L
        prefs.markChecked(now)

        assertFalse(prefs.shouldAutoCheck(now - 60_000L))
    }

    @Test
    fun `ignored tag persists and can be cleared`() {
        val store = FakeKeyValueStore()
        val prefs = UpdatePrefs(store)

        prefs.ignore("v1.3.14")
        assertTrue(prefs.isIgnored("v1.3.14"))
        assertEquals("v1.3.14", UpdatePrefs(store).ignoredTag())

        prefs.clearIgnored()
        assertFalse(prefs.isIgnored("v1.3.14"))
        assertEquals("", UpdatePrefs(store).ignoredTag())
    }

    @Test
    fun `null store degrades without crashing`() {
        val prefs = UpdatePrefs(null)
        prefs.markChecked(123L)
        prefs.ignore("v1.3.14")

        assertTrue(prefs.shouldAutoCheck(1_700_000_000_000L))
        assertFalse(prefs.isIgnored("v1.3.14"))
    }

    @Test
    fun `status text describes elapsed time`() {
        val prefs = UpdatePrefs(FakeKeyValueStore())
        val now = 1_700_000_000_000L

        assertEquals("检查 Gitee / GitHub 上的最新版本", prefs.statusText(now))
        prefs.markChecked(now - 2 * 60 * 60 * 1000L)
        assertEquals("2 小时前检查", prefs.statusText(now))
    }
}
