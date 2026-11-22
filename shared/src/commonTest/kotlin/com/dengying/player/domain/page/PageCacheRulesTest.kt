package com.dengying.player.domain.page

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * 分页缓存策略的契约用例（FR-701、FR-702）
 *
 * 覆盖四类边界：
 *  1. 键构造与解析（含畸形输入）
 *  2. TTL 判定（含时钟回拨）
 *  3. LRU 淘汰顺序（含确定性）
 *  4. 源变更时的整体失效
 */
class PageCacheRulesTest {

    private data class E(
        override val key: String,
        override val savedAt: Long,
        override val accessedAt: Long,
    ) : PageCacheRules.Entry

    // ---- 键构造与解析 ----

    @Test
    fun `键由分类与页码构成`() {
        assertEquals(PageCacheRules.keyOf("电影", 1), "电影#1")
        assertEquals(PageCacheRules.keyOf("记录片", 3), "记录片#3")
    }

    @Test
    fun `键可反解出分类与页码`() {
        val k = PageCacheRules.keyOf("短剧", 2)
        assertEquals(PageCacheRules.categoryOf(k), "短剧")
        assertEquals(2, PageCacheRules.pageOf(k))
    }

    @Test
    fun `分类名里含井号时以最后一个井号切分`() {
        // 站点分类名理论上可能出现 # —— 用最后一个井号切分才能保证页码可解
        val k = PageCacheRules.keyOf("剧#集", 5)
        assertEquals(PageCacheRules.categoryOf(k), "剧#集")
        assertEquals(5, PageCacheRules.pageOf(k))
    }

    @Test
    fun `畸形键反解不抛异常`() {
        assertEquals(-1, PageCacheRules.pageOf("没有井号"))
        assertEquals(PageCacheRules.categoryOf("没有井号"), "")
        assertEquals(-1, PageCacheRules.pageOf("电影#abc"))
    }

    // ---- TTL ----

    @Test
    fun `TTL 内可命中`() {
        val now = 1_000_000L
        val e = E("电影#1", savedAt = now - 1000, accessedAt = now)
        assertTrue(PageCacheRules.shouldServe(e, now))
    }

    @Test
    fun `TTL 边界按半开区间`() {
        val now = 1_000_000L
        // 恰好等于 TTL → 不可用（>= TTL 即过期）
        val onEdge = E("电影#1", savedAt = now - PageCacheRules.RETAIN_MS, accessedAt = now)
        assertFalse(PageCacheRules.shouldServe(onEdge, now))
        // 差 1ms → 仍可用
        val justInside = E("电影#1", savedAt = now - PageCacheRules.RETAIN_MS + 1, accessedAt = now)
        assertTrue(PageCacheRules.shouldServe(justInside, now))
    }

    @Test
    fun `陈旧条目仍可命中`() {
        // stale-while-revalidate 的核心语义：陈旧 ≠ 不可用
        val now = 1_000_000_000L
        val stale = E("电影#1", savedAt = now - PageCacheRules.STALE_MS - 1, accessedAt = now)
        assertTrue(PageCacheRules.shouldServe(stale, now), "陈旧但保留期内应可命中")
        assertTrue(PageCacheRules.isStale(stale, now), "且应被判为需要后台刷新")
    }

    @Test
    fun `陈旧边界按半开区间`() {
        val now = 1_000_000_000L
        val justFresh = E("电影#1", savedAt = now - PageCacheRules.STALE_MS + 1, accessedAt = now)
        val onEdge = E("电影#2", savedAt = now - PageCacheRules.STALE_MS, accessedAt = now)
        assertFalse(PageCacheRules.isStale(justFresh, now))
        assertTrue(PageCacheRules.isStale(onEdge, now), "恰好到 STALE 即算陈旧")
    }

    @Test
    fun `保留期边界`() {
        val now = 1_000_000_000L
        // 恰好到 RETAIN → 不可命中（>= RETAIN 即删除）
        val onEdge = E("电影#1", savedAt = now - PageCacheRules.RETAIN_MS, accessedAt = now)
        assertFalse(PageCacheRules.shouldServe(onEdge, now))
        // 差 1ms → 仍可命中
        val inside = E("电影#1", savedAt = now - PageCacheRules.RETAIN_MS + 1, accessedAt = now)
        assertTrue(PageCacheRules.shouldServe(inside, now))
    }

    @Test
    fun `staleKeys 只含陈旧但可命中的`() {
        val now = 1_000_000_000L
        val entries = listOf(
            E("新#1", now - 1000, now),                                  // 新 → 不在 stale
            E("陈#1", now - PageCacheRules.STALE_MS - 1, now),           // 陈旧 → 在 stale
            E("删#1", now - PageCacheRules.RETAIN_MS - 1, now),          // 超保留 → 不在 stale（该删）
        )
        assertEquals(listOf("陈#1"), PageCacheRules.staleKeys(entries, now))
        assertEquals(listOf("删#1"), PageCacheRules.expiredKeys(entries, now))
    }

    @Test
    fun `时钟回拨时不可命中`() {
        // savedAt 在未来：不能因为"now - savedAt 是负数"就判成永不过期
        val now = 1_000_000L
        val fromFuture = E("电影#1", savedAt = now + 60_000, accessedAt = now)
        assertFalse(PageCacheRules.shouldServe(fromFuture, now))
    }

    @Test
    fun `过期键清单只含真过期的`() {
        val now = 1_000_000L
        val entries = listOf(
            E("电影#1", now - 1000, now),
            E("电影#2", now - PageCacheRules.RETAIN_MS - 1, now),
            E("电影#3", now - 1000, now),
        )
        assertEquals(listOf("电影#2"), PageCacheRules.expiredKeys(entries, now))
    }

    // ---- LRU 淘汰 ----

    @Test
    fun `未达上限时不淘汰`() {
        val now = 1_000_000L
        // MAX-1 条 + 1 新 = MAX 条，正好等于上限 → 不淘汰
        val entries = (1 until PageCacheRules.MAX_ENTRIES).map {
            E("电影#$it", savedAt = now, accessedAt = now)
        }
        assertTrue(PageCacheRules.evictOrder(entries, "电影#new", now).isEmpty())
    }

    @Test
    fun `恰好达上限时不淘汰`() {
        val now = 1_000_000L
        val entries = (1..PageCacheRules.MAX_ENTRIES).map {
            E("电影#$it", savedAt = now, accessedAt = now)
        }
        // 已满但更新同键 → 总数不变
        assertTrue(PageCacheRules.evictOrder(entries, "电影#5", now).isEmpty())
    }

    @Test
    fun `超上限淘汰最久未访问的`() {
        val now = 1_000_000L
        // 已有 MAX 条，accessedAt 依次递增：最旧的是 #1
        val entries = (1..PageCacheRules.MAX_ENTRIES).map {
            E("电影#$it", savedAt = now, accessedAt = now + it)
        }
        // 插入新键 → 超 1 条 → 淘汰 accessedAt 最小的那条
        val evicted = PageCacheRules.evictOrder(entries, "电影#new", now)
        assertEquals(listOf("电影#1"), evicted)
    }

    @Test
    fun `同键更新不占新位置`() {
        val now = 1_000_000L
        val entries = (1..PageCacheRules.MAX_ENTRIES).map {
            E("电影#$it", savedAt = now, accessedAt = now + it)
        }
        // 更新已存在的键 → 总数不变 → 不淘汰
        assertTrue(PageCacheRules.evictOrder(entries, "电影#5", now).isEmpty())
    }

    @Test
    fun `淘汰顺序确定且可预测`() {
        val now = 1_000_000L
        // MAX+1 条（含即将写入的那条占位）→ 实际 others = MAX 条，+1 新 = MAX+1，超 1 条
        // 构造"访问时间相同"的条目：应按 savedAt 再按键名定序，保证两次调用结果一致
        val entries = (1..PageCacheRules.MAX_ENTRIES).map {
            E("电影#$it", savedAt = now, accessedAt = now)
        }
        val first = PageCacheRules.evictOrder(entries, "电影#new", now)
        val second = PageCacheRules.evictOrder(entries, "电影#new", now)
        assertEquals(first, second)
        // 键名升序（字符串比较）："电影#1" < "电影#10" < "电影#2"
        assertEquals(listOf("电影#1"), first)
    }

    @Test
    fun `超上限多条时一次淘汰到上限`() {
        val now = 1_000_000L
        // others = MAX+5 条，+1 新 = MAX+6，超 6 条
        val entries = (1..PageCacheRules.MAX_ENTRIES + 5).map {
            E("电影#$it", savedAt = now, accessedAt = now + it)
        }
        val evicted = PageCacheRules.evictOrder(entries, "电影#new", now)
        // 一次淘汰到上限，避免"每次只淘汰一条"导致反复触发
        assertEquals(6, evicted.size)
    }

    @Test
    fun `容量上限为 0 时全部淘汰`() {
        // 退化输入：容量为 0 表示关闭缓存，应淘汰所有而非崩溃。
        // 用参数化的 max 而非常量——常量无法测这条边界。
        val now = 1_000_000L
        val entries = listOf(
            E("电影#1", now, now),
            E("电影#2", now, now),
        )
        val evicted = PageCacheRules.evictOrder(entries, "电影#3", now, max = 0)
        assertEquals(listOf("电影#1", "电影#2"), evicted)
    }

    @Test
    fun `容量为 1 时只留最新`() {
        val now = 1_000_000L
        val entries = listOf(E("电影#1", now, now))
        val evicted = PageCacheRules.evictOrder(entries, "电影#2", now, max = 1)
        assertEquals(listOf("电影#1"), evicted)
    }

    // ---- 源变更失效 ----

    @Test
    fun `源变更时全部键失效`() {
        val now = 1_000_000L
        val entries = listOf(
            E("电影#1", now, now),
            E("电视剧#1", now, now),
        )
        // 理由：列表是多源聚合结果，任一源变化都会改变聚合内容，无法按源拆分
        assertEquals(listOf("电影#1", "电视剧#1"), PageCacheRules.invalidateOnSourceChange(entries))
    }

    @Test
    fun `空缓存集不产生淘汰与失效`() {
        val now = 1_000_000L
        assertTrue(PageCacheRules.evictOrder(emptyList(), "电影#1", now).isEmpty())
        assertTrue(PageCacheRules.invalidateOnSourceChange(emptyList()).isEmpty())
        assertTrue(PageCacheRules.expiredKeys(emptyList(), now).isEmpty())
    }
}
