package com.dengying.player.ui.pages

import com.dengying.player.core.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 发现页无限流取片器（计划 14 目标 7）。
 *
 * 三条不变式：
 *  - 同一轮里不重复（`DiscoverEntry.key` 要有区分度，否则 LazyColumn key 冲突）；
 *  - 池子取尽后开新一轮重洗，而不是停下（这就是「无限流」的全部含义）；
 *  - 池子在浏览中扩容（分类分页陆续到货）后，新片进入后续轮次，不重建列表。
 */
class DiscoverFeedTest {

    private fun pool(n: Int) = (1..n).map { VideoItem(vod_id = it.toLong(), vod_name = "片$it") }

    @Test
    fun 同一轮内不重复_取尽后开新一轮() {
        val src = DiscoverFeedSource(batch = 5)
        val p = pool(10)

        val first = src.nextBatch(p) + src.nextBatch(p) + src.nextBatch(p)
        assertEquals(15, first.size, "池子 10 条、每批 5 条，取 15 条必须继续给（不能触底停住）")
        assertEquals(10, first.take(10).map { it.vod.vod_id }.toSet().size, "第一轮内不得重复")
        assertEquals(0, first[0].round)

        // 前 10 条属于第 0 轮，第 11 条起进入第 1 轮
        assertTrue(first.drop(10).all { it.round == 1 })
    }

    @Test
    fun 跨轮允许同一部片再次出现但_key_不同() {
        val src = DiscoverFeedSource(batch = 2)
        val p = pool(2)

        val a = src.nextBatch(p)
        val b = src.nextBatch(p)
        val c = src.nextBatch(p)

        val keys = (a + b + c).map { it.key }
        assertEquals(keys.size, keys.toSet().size, "key 必须全局唯一，否则 LazyColumn 崩溃")
        assertTrue((a + b + c).map { it.vod.vod_id }.toSet().size == 2, "池子里只有 2 部片")
    }

    @Test
    fun 池子扩容后新片进入后续轮次() {
        val src = DiscoverFeedSource(batch = 2)
        val small = pool(2)

        src.nextBatch(small)
        // 分类分页到货：池子从 2 条变 4 条
        val grown = pool(4)
        val seen = mutableSetOf<Long>()
        repeat(6) { src.nextBatch(grown).forEach { seen += it.vod.vod_id } }

        assertTrue(seen.containsAll(listOf(3L, 4L)), "扩容后的新片必须在后续轮次里出现")
    }

    @Test
    fun 空池返回空表() {
        assertTrue(DiscoverFeedSource().nextBatch(emptyList()).isEmpty())
        assertEquals(-1, DiscoverFeedSource().currentRound)
    }
}
