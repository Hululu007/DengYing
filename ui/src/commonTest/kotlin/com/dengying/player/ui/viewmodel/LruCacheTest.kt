package com.dengying.player.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * `LruCache` 的访问序语义。
 *
 * 这组用例的存在理由：`DetailViewModel.altCache` 原先是手写 LinkedHashMap，
 * 命中不重排 = FIFO 而非 LRU（一款常看的片只因第一次看得早，反而先被淘汰）。
 * 修好之后那段逻辑依赖 `viewModelScope`，无法在 `:ui:jvmTest` 里断言，
 * 所以把语义下沉到这里钉死。
 */
class LruCacheTest {

    @Test
    fun `未超限时不淘汰`() {
        val c = LruCache<String, String>(3)
        c.put("a", "1")
        c.put("b", "2")
        assertEquals(listOf("a", "b"), c.keysInOrder())
        assertEquals(2, c.size)
    }

    @Test
    fun `超限时淘汰最久未用`() {
        val c = LruCache<String, String>(2)
        c.put("a", "1")
        c.put("b", "2")
        c.put("c", "3")

        assertEquals(listOf("b", "c"), c.keysInOrder(), "a 是最久未用，应被淘汰")
        assertNull(c.get("a"))
    }

    @Test
    fun `命中会刷新访问序——这是 LRU 与 FIFO 的分界`() {
        val c = LruCache<String, String>(2)
        c.put("a", "1")
        c.put("b", "2")

        // 命中 a：FIFO 下顺序不变，LRU 下 a 移到表尾
        assertEquals("1", c.get("a"))
        assertEquals(listOf("b", "a"), c.keysInOrder())

        // 再写入 c：LRU 应淘汰 b（最久未用），FIFO 会淘汰 a
        c.put("c", "3")
        assertEquals("1", c.get("a"), "刚用过的 a 不该被淘汰")
        assertNull(c.get("b"), "b 才是最久未用的")
    }

    @Test
    fun `反复命中常看的键不会被淘汰`() {
        val c = LruCache<String, String>(2)
        c.put("hot", "H")
        c.put("cold", "C")

        // 模拟「常看 hot，冷落 cold」
        repeat(5) { c.get("hot") }
        c.put("new", "N")

        assertEquals("H", c.get("hot"), "常看的片不该只因看得早而被淘汰")
        assertNull(c.get("cold"))
    }

    @Test
    fun `同键重写只改值并刷新到表尾`() {
        val c = LruCache<String, String>(2)
        c.put("a", "1")
        c.put("b", "2")
        c.put("a", "1-new")

        assertEquals(listOf("b", "a"), c.keysInOrder(), "重写按更新处理，不占新位置但刷新表尾")
        assertEquals("1-new", c.get("a"))
        assertEquals(2, c.size)
    }

    @Test
    fun `未命中不改变顺序`() {
        val c = LruCache<String, String>(3)
        c.put("a", "1")
        c.put("b", "2")

        assertNull(c.get("missing"))
        assertEquals(listOf("a", "b"), c.keysInOrder())
    }

    @Test
    fun `容量为正才允许构造`() {
        assertFailsWith<IllegalArgumentException> { LruCache<String, String>(0) }
        assertFailsWith<IllegalArgumentException> { LruCache<String, String>(-1) }
    }

    @Test
    fun `连续写入远超容量时始终守住上限`() {
        val c = LruCache<Int, Int>(4)
        repeat(100) { c.put(it, it) }

        assertEquals(4, c.size)
        assertEquals(listOf(96, 97, 98, 99), c.keysInOrder())
    }
}
