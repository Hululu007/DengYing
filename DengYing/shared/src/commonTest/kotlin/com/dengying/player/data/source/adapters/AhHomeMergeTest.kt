package com.dengying.player.data.source.adapters

import com.dengying.player.core.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 首页多分类合并（FR-633）。
 *
 * 背景：ffzy 每个叶子分类只按 vod_time 倒序给一页，单取一个子分类会让
 * 「电影」栏全是动作片；[interleaveNewest] 负责把多个叶子交替拼接。
 */
class AhHomeMergeTest {

    private fun item(id: Long) = VideoItem(vod_id = id, vod_name = "v$id")

    @Test
    fun `交替合并保持各自新旧顺序`() {
        val merged = interleaveNewest(
            listOf(listOf(item(1), item(2), item(3)), listOf(item(11), item(12))),
            limit = 10,
        )
        assertEquals(listOf(1L, 11L, 2L, 12L, 3L), merged.map { it.vod_id })
    }

    @Test
    fun `按 vod_id 去重且先到的位置优先`() {
        val merged = interleaveNewest(
            listOf(listOf(item(1), item(2)), listOf(item(2), item(3))),
            limit = 10,
        )
        assertEquals(listOf(1L, 2L, 3L), merged.map { it.vod_id })
    }

    @Test
    fun `超过限额即截断`() {
        val merged = interleaveNewest(
            listOf(listOf(item(1), item(2), item(3)), listOf(item(11), item(12))),
            limit = 3,
        )
        assertEquals(listOf(1L, 11L, 2L), merged.map { it.vod_id })
    }

    @Test
    fun `空组与非正限额都不炸`() {
        assertTrue(interleaveNewest(emptyList(), 5).isEmpty())
        assertTrue(interleaveNewest(listOf(emptyList(), emptyList()), 5).isEmpty())
        assertTrue(interleaveNewest(listOf(listOf(item(1))), 0).isEmpty())
        assertEquals(listOf(1L), interleaveNewest(listOf(emptyList(), listOf(item(1))), 5).map { it.vod_id })
    }
}
