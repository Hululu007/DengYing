package com.dengying.player.ui.pages

import com.dengying.player.core.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 发现页跨导航状态（DVP §5）。
 *
 * [DiscoverUiState.feedFor] 是「从发现页点进详情、再返回」后还能原样接着看的关键：
 * 同一批次（key 不变）必须复用同一份列表与解析缓存，换批（key 变）必须把上一批的
 * 解析结果、断点、起播下标全部作废，否则新批会挂着上一批的地址和进度。
 */
class DiscoverUiStateTest {

    private fun pool(n: Int): List<VideoItem> =
        (0 until n).map { VideoItem(vod_id = it.toLong() + 1, vod_name = "片$it") }

    @Test
    fun `同一批次再次取用保持列表同一性与首批数量`() {
        val state = DiscoverUiState()
        val (source1, list1) = state.feedFor(key = 7L, pool = pool(30))
        val (source2, list2) = state.feedFor(key = 7L, pool = pool(30))

        // 同一份批次源、同一份列表（返回详情页后不会重洗、不回到顶部）
        assertSame(source1, source2)
        assertSame(list1, list2)
        assertEquals(DISCOVER_BATCH, list1.size)
        // 池子比一批还小也照样填满一批：跨轮复用（否则列表触底就长不出来），
        // 但每张卡的 key（round:vod_id）必须唯一，不然 LazyColumn 直接抛重复 key。
        val (_, small) = state.feedFor(key = 8L, pool = pool(4))
        assertEquals(DISCOVER_BATCH, small.size)
        assertEquals(small.size, small.distinctBy { it.key }.size, "同一批里不能出现重复 key")
    }

    @Test
    fun `换批清空解析结果与断点并重建列表`() {
        val state = DiscoverUiState()
        val (_, first) = state.feedFor(key = 1L, pool = pool(30))
        // 模拟一批播过：有解析结果、有断点、有起播与中心下标
        state.resume.resolved[0] = Triple("https://example.invalid/a.m3u8", "", false)
        state.resume.paused[0] = 42_000L
        state.resume.startedIdx.intValue = 0
        state.resume.playingIdx.intValue = 0

        val (_, second) = state.feedFor(key = 2L, pool = pool(30))

        assertNotSame(first, second)
        assertTrue(state.resume.resolved.isEmpty(), "换批后上一批的解析结果必须作废")
        assertTrue(state.resume.paused.isEmpty(), "换批后上一批的断点必须作废")
        assertEquals(-1, state.resume.startedIdx.intValue)
        assertEquals(-1, state.resume.playingIdx.intValue)
        assertEquals(DISCOVER_BATCH, second.size)
    }

    @Test
    fun `换批后新列表来自新池`() {
        val state = DiscoverUiState()
        state.feedFor(key = 1L, pool = (1L..30L).map { VideoItem(vod_id = it, vod_name = "旧$it") })
        val (_, fresh) = state.feedFor(
            key = 2L,
            pool = (100L..130L).map { VideoItem(vod_id = it, vod_name = "新$it") },
        )
        // 新批只从新池取片：老批的 vod_id 一个都不该再出现
        assertTrue(fresh.all { it.vod.vod_id >= 100L }, "换批后仍混入旧池的片")
    }
}
