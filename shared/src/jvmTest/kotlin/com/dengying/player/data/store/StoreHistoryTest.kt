package com.dengying.player.data.store

import com.dengying.player.core.HistoryItem
import com.dengying.player.data.json.StoreEnvelope
import com.dengying.player.platform.FileKeyValueStore
import com.dengying.player.platform.KeyValueStore
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 历史落盘的成败语义（计划 14 目标 1）。
 *
 * 验收点不是「能不能写」，而是**写失败必须能被上层看见**：
 * `saveHistory` / `saveHistoryBlocking` 都返回 Boolean，`putString` 返回 false、
 * 键里躺着更高版本信封、写盘抛异常，一律不能假装成功。
 *
 * 这里用真实 [FileKeyValueStore] 走通链路；只有「让 `putString` 失败」这一条
 * 需要一个能拒绝写入的包装实现——真实文件存储不会主动失败。
 */
class StoreHistoryTest {

    private fun tempStore(): Pair<FileKeyValueStore, File> {
        val f = File.createTempFile("dengying-history", ".properties")
        f.delete()
        return FileKeyValueStore(f) to f
    }

    private fun item(pos: Long, ep: Int = 0) = HistoryItem(
        vod_id = 7L,
        vod_name = "测试影片",
        source = "test_src",
        episodeIndex = ep,
        episodeName = "第${ep + 1}集",
        position = pos,
        duration = 3_600_000L,
        updateTime = 1_700_000_000_000L + pos,
    )

    @Test
    fun save_history_persists_and_reads_back() = runBlocking {
        val (kv, f) = tempStore()
        try {
            val store = Store(kv)
            store.init()

            assertTrue(store.saveHistory(item(12_000L)), "正常写入必须返回 true")

            val list = store.getHistory()
            assertEquals(1, list.size)
            assertEquals(12_000L, list[0].position)
            assertEquals(7L, list[0].vod_id)
            assertEquals("test_src", list[0].source)
        } finally {
            f.delete()
        }
    }

    @Test
    fun save_history_reports_false_when_put_string_fails() = runBlocking {
        val (base, f) = tempStore()
        try {
            // 历史键写入失败（模拟磁盘满 / commit 失败），其它键正常
            val kv = object : KeyValueStore by base {
                override fun putString(key: String, value: String, sync: Boolean): Boolean =
                    if (key == "history") false else base.putString(key, value, sync)
            }
            val store = Store(kv)
            store.init()

            assertFalse(store.saveHistory(item(9_000L)), "putString 返回 false 时不能报成功")
            assertTrue(store.getHistory().isEmpty(), "写入失败不能留下半条记录")
        } finally {
            f.delete()
        }
    }

    @Test
    fun save_history_blocking_reports_false_when_put_string_fails() {
        val (base, f) = tempStore()
        try {
            val kv = object : KeyValueStore by base {
                override fun putString(key: String, value: String, sync: Boolean): Boolean =
                    if (key == "history") false else base.putString(key, value, sync)
            }
            val store = Store(kv)
            runBlocking { store.init() }

            assertFalse(store.saveHistoryBlocking(item(4_000L)))
        } finally {
            f.delete()
        }
    }

    @Test
    fun newer_version_key_is_never_overwritten() = runBlocking {
        val (kv, f) = tempStore()
        try {
            val newer = """{"version":${StoreEnvelope.CURRENT_VERSION + 1},"payload":"[]"}"""
            kv.putString("history", newer, sync = true)
            val store = Store(kv)
            store.init()

            assertFalse(store.saveHistory(item(5_000L)), "更高版本信封存在时异步写必须拒绝")
            assertFalse(store.saveHistoryBlocking(item(5_000L)), "更高版本信封存在时同步写必须拒绝")
            assertEquals(newer, kv.getString("history", ""), "拒绝写入时原始数据必须原样保留")
            assertTrue(store.getHistory().isEmpty(), "读不出更高版本数据时返回空表，不抛异常")
        } finally {
            f.delete()
        }
    }

    @Test
    fun blocking_write_is_visible_to_next_read() = runBlocking {
        val (kv, f) = tempStore()
        try {
            val store = Store(kv)
            store.init()

            // onCleared 走同步路径写，下一次进页面（异步读）必须能看见
            assertTrue(store.saveHistoryBlocking(item(30_000L, ep = 2)))

            val list = store.getHistory()
            assertEquals(1, list.size)
            assertEquals(30_000L, list[0].position)
            assertEquals(2, list[0].episodeIndex)
        } finally {
            f.delete()
        }
    }
}
