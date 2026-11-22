package com.dengying.player.ui.pages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 发现页可播地址缓存的行为契约（计划 18 §3.2.2/3）。
 *
 * 它替用户省的是「滑回同一张卡、或换批后又抽到同一部片」时的整条解析链，
 * 所以这里只钉三件事：键要区分源/片/集、空地址不入缓存、试看链能被全片覆盖，
 * 外加容量淘汰确实按「最久未用」而不是按写入顺序。
 */
class DiscoverUrlCacheTest {

    @Test
    fun keySeparatesSourceVodAndEpisode() {
        assertTrue(UrlCacheStore.key("a", 1L, 0) != UrlCacheStore.key("b", 1L, 0))
        assertTrue(UrlCacheStore.key("a", 1L, 0) != UrlCacheStore.key("a", 2L, 0))
        assertTrue(UrlCacheStore.key("a", 1L, 0) != UrlCacheStore.key("a", 1L, 1))
    }

    @Test
    fun putThenGetReturnsSameEntry() {
        val store = UrlCacheStore()
        val key = UrlCacheStore.key("src", 42L, 3)
        store.put(key, "https://cdn.example/a.m3u8", "Referer: x", previewOnly = true)

        val hit = store.get(key)
        assertNotNull(hit)
        assertEquals("https://cdn.example/a.m3u8", hit.url)
        assertEquals("Referer: x", hit.headers)
        assertTrue(hit.previewOnly)
    }

    @Test
    fun emptyUrlIsNotCached() {
        val store = UrlCacheStore()
        val key = UrlCacheStore.key("src", 7L, 0)
        store.put(key, "", "", previewOnly = true)
        assertNull(store.get(key))
    }

    @Test
    fun fullSourceOverwritesPreviewEntry() {
        val store = UrlCacheStore()
        val key = UrlCacheStore.key("src", 7L, 0)
        store.put(key, "https://cdn.example/preview.m3u8", "", previewOnly = true)
        store.put(key, "https://cdn.example/full.m3u8", "", previewOnly = false)

        val hit = store.get(key)
        assertNotNull(hit)
        assertEquals("https://cdn.example/full.m3u8", hit.url)
        assertFalse(hit.previewOnly)
    }

    @Test
    fun evictsOldestBeyondCapacity() {
        val store = UrlCacheStore(maxEntries = 2)
        val first = UrlCacheStore.key("src", 0L, 0)
        store.put(first, "u0", "", false)
        store.put(UrlCacheStore.key("src", 1L, 0), "u1", "", false)
        store.put(UrlCacheStore.key("src", 2L, 0), "u2", "", false)

        assertNull(store.get(first), "超出容量后最旧的一条应被淘汰")
        assertNotNull(store.get(UrlCacheStore.key("src", 2L, 0)))
    }

    @Test
    fun recentHitSurvivesEviction() {
        val store = UrlCacheStore(maxEntries = 2)
        val first = UrlCacheStore.key("src", 0L, 0)
        store.put(first, "u0", "", false)
        store.put(UrlCacheStore.key("src", 1L, 0), "u1", "", false)

        // 命中会把条目移到队尾（近似 LRU）。
        assertNotNull(store.get(first))
        store.put(UrlCacheStore.key("src", 2L, 0), "u2", "", false)

        assertNotNull(store.get(first), "刚命中过的不应被当成最旧淘汰")
        assertNull(store.get(UrlCacheStore.key("src", 1L, 0)))
    }
}
