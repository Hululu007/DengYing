package com.dengying.player.data

import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.json.StoreEnvelope
import com.dengying.player.data.source.adapters.Golden
import com.dengying.player.data.store.Store
import com.dengying.player.domain.page.PageCacheRules
import com.dengying.player.platform.FileKeyValueStore
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 「旧字节回放」端到端测试（Store + PageCache）—— KMP P3 批 E 的验收项
 *
 * `OldBytesReplayTest` 覆盖的是**编解码层**（信封与覆盖层），本文件覆盖**真正的读写路径**：
 * 把 org.json 写出的旧字节塞进键值存储，再走 `Store.getFavorites()` / `getHistory()` /
 * `getSearchHistory()` / `getSourceOverride()` / `readClassCache()` 与 `PageCache.get()`。
 *
 * ## 为什么用 `FileKeyValueStore` 而不是 mock
 *
 * `KeyValueStore` 是接口，本可以写个内存假实现。这里偏不用：批 E 的验收要证明的是
 * **整条链路**（接口 → actual 实现 → JSON 解析 → 数据模型）在真实字节上成立。
 * 用假实现会把 actual 的语义差异（`sync` 是否真的同步、`getString` 的默认值处理）
 * 挡在测试之外。`FileKeyValueStore` 是 jvmMain 的真 actual，正好补上这一环。
 *
 * ## 为什么 `nowMs` 必须显式传
 *
 * 旧样本的时间戳是 2023 年（`1700000003333`）。`PageCache` 的默认 `nowMs` 取
 * `Clock.nowMs()`（真实当前时间），算下来早已超过 24 小时保留期，缓存会被判为
 * 不可命中——那样测到的就不是「能否读旧字节」，而是「样本过期了」。
 */
class StoreReplayTest {

    /** 样本时间戳 +2 秒：落在保留期内，又晚于 `savedAt`，可命中且不陈旧 */
    private val nowMs = 1700000005000L

    private fun tempStore(): Pair<FileKeyValueStore, File> {
        val f = File.createTempFile("dengying-replay", ".properties")
        f.delete()
        return FileKeyValueStore(f) to f
    }

    // ---------------- Store：收藏 ----------------

    @Test
    fun store_reads_old_favorites_envelope() = runBlocking {
        val (kv, f) = tempStore()
        try {
            kv.putString("favorites", Golden.text("old_favorites_envelope"), sync = true)
            val store = Store(kv)
            store.init()

            val list = store.getFavorites()
            assertEquals(2, list.size, "旧信封里两条收藏必须都读出来")

            assertEquals(1001L, list[0].vod_id)
            assertEquals("流浪地球2", list[0].vod_name)
            assertEquals("https://img.example.com/a.jpg", list[0].vod_pic)
            assertEquals("ah_wencai", list[0].source)
            assertEquals(1700000000123L, list[0].addTime)
            assertEquals(
                "https://api.example.com/detail?id=1001",
                list[0].vod_uri,
                "vod_uri 是 slug 型源直达详情的寻址串，丢了就点不进详情",
            )

            assertEquals(1002L, list[1].vod_id)
            assertEquals("满江红 / 满江红·2023", list[1].vod_name)
            assertEquals("", list[1].vod_pic)
            assertEquals("", list[1].vod_uri)
        } finally {
            f.delete()
        }
    }

    // ---------------- Store：历史 ----------------

    @Test
    fun store_reads_old_history_envelope() = runBlocking {
        val (kv, f) = tempStore()
        try {
            kv.putString("history", Golden.text("old_history_envelope"), sync = true)
            val store = Store(kv)
            store.init()

            val list = store.getHistory()
            assertEquals(1, list.size)
            val h = list[0]
            assertEquals(2001L, h.vod_id)
            assertEquals("庆余年 第二季", h.vod_name)
            assertEquals("ah_wencai", h.source)
            assertEquals(3, h.episodeIndex, "续播位置必须逐字段还原")
            assertEquals("第4集", h.episodeName)
            assertEquals(754321L, h.position, "毫秒级进度不能丢精度")
            assertEquals(2700000L, h.duration)
            assertEquals(1700000000789L, h.updateTime)
            assertEquals("https://api.example.com/detail?id=2001", h.vod_uri)
        } finally {
            f.delete()
        }
    }

    // ---------------- Store：搜索历史 ----------------

    @Test
    fun store_reads_old_search_history_with_quotes() = runBlocking {
        val (kv, f) = tempStore()
        try {
            kv.putString("search_history", Golden.text("old_search_envelope"), sync = true)
            val store = Store(kv)
            store.init()

            val list = store.getSearchHistory()
            assertEquals(3, list.size)
            assertEquals("流浪地球", list[0])
            assertEquals("庆余年", list[1])
            // 双层编码里的 `\"` 转义：解出来必须是真实引号
            assertEquals("三体 \"黑暗森林\"", list[2])
        } finally {
            f.delete()
        }
    }

    // ---------------- Store：源覆盖层 ----------------

    @Test
    fun store_loads_old_override_envelope_on_init() = runBlocking {
        val (kv, f) = tempStore()
        try {
            kv.putString("source_override", Golden.text("old_override_envelope"), sync = true)
            val store = Store(kv)
            store.init()

            // loadOverrideCache 在 openStore() 里跑，init() 之后必须已就绪
            val p = store.getSourceOverride()
            assertEquals(2, p.list.size)
            assertEquals("ah_wencai", p.list[0].key)
            assertFalse(p.list[0].enabled, "旧覆盖值：ah_wencai 被关闭")
            assertEquals(1700000001111L, p.list[0].updatedAt)
            assertEquals("ah_duboku", p.list[1].key)
            assertTrue(p.list[1].enabled, "旧覆盖值：ah_duboku 被开启")
        } finally {
            f.delete()
        }
    }

    // ---------------- Store：分类缓存（裸 JSON，不经信封） ----------------

    @Test
    fun store_reads_old_class_cache_raw_json() = runBlocking {
        val (kv, f) = tempStore()
        try {
            // 分类缓存是**唯一**不走信封的用户数据键：Store.writeClassCache 直接写裸 JSON。
            // 样本 old_class_envelope 是探针造的信封形态，实际不存在，故这里用裸 payload。
            kv.putString("class_type_cache", Golden.text("old_class_payload"), sync = true)
            val store = Store(kv)
            store.init()

            val cache = store.readClassCache()
            assertEquals(1, cache.size)
            val (classes, time) = cache.getValue("ah_wencai")
            assertEquals(1700000000999L, time)
            assertEquals(2, classes.size)
            assertEquals(1, classes[0].type_id)
            assertEquals("电影", classes[0].type_name)
            assertNull(classes[0].type_pid, "旧字节里没有 type_pid 字段 → 必须为 null，不能默认 0")
            assertEquals(2, classes[1].type_id)
            assertEquals(0, classes[1].type_pid, "显式写了 0 的必须保留 0")
        } finally {
            f.delete()
        }
    }

    // ---------------- Store：损坏与更高版本（负向语义） ----------------

    @Test
    fun store_preserves_corrupt_bytes_and_returns_empty() = runBlocking {
        val (kv, f) = tempStore()
        try {
            val raw = Golden.text("old_corrupt_envelope")
            kv.putString("favorites", raw, sync = true)
            val store = Store(kv)
            store.init()

            assertTrue(store.getFavorites().isEmpty(), "损坏键按空集合处理，不崩")

            // 原始字节被拷到诊断键：用户修不好数据前，排障线索不能先丢
            assertEquals(raw, kv.getString(StoreEnvelope.KEY_CORRUPT, ""), "损坏原始字节必须留档")
        } finally {
            f.delete()
        }
    }

    @Test
    fun store_rejects_read_and_write_on_newer_version_data() = runBlocking {
        val (kv, f) = tempStore()
        try {
            kv.putString("favorites", Golden.text("old_newer_envelope"), sync = true)
            val store = Store(kv)
            store.init()

            assertTrue(store.getFavorites().isEmpty(), "更高版本数据不读（读出来可能是降级不认识的形状）")
            assertTrue(store.hasNewerVersionData(), "必须记住该键来自更高版本，供 UI 提示升级")

            // 写也必须拒：降级写会覆盖掉高版本数据，用户升级回去就没了
            val ok = store.addFavorite(
                com.dengying.player.core.FavoriteItem(
                    vod_id = 9L,
                    vod_name = "x",
                    source = "ah_wencai",
                    addTime = nowMs,
                )
            )
            assertFalse(ok, "更高版本数据存在时写操作必须返回 false（FR-508）")
        } finally {
            f.delete()
        }
    }

    // ---------------- Store：写出去的新字节能被自己读回 ----------------

    @Test
    fun store_write_then_read_roundtrips() = runBlocking {
        val (kv, f) = tempStore()
        try {
            val store = Store(kv)
            store.init()

            val item = com.dengying.player.core.FavoriteItem(
                vod_id = 4242L,
                vod_name = "回环测试",
                vod_pic = "https://img.example.com/x.jpg",
                source = "ah_wencai",
                addTime = 1700000009999L,
                vod_uri = "https://api.example.com/detail?id=4242",
            )
            assertTrue(store.addFavorite(item), "空存储上首次写入必须成功")

            val list = store.getFavorites()
            assertEquals(1, list.size)
            assertEquals(4242L, list[0].vod_id)
            assertEquals("回环测试", list[0].vod_name)

            // 落盘形态必须是 v1 信封（下次启动走的是同一条解析路径）
            val raw = kv.getString("favorites", "")
            assertEquals(StoreEnvelope.Kind.Ok, StoreEnvelope.decodePayload(raw).kind)
            assertEquals(1, StoreEnvelope.decodePayload(raw).version)
        } finally {
            f.delete()
        }
    }

    // ---------------- PageCache ----------------

    @Test
    fun page_cache_reads_old_page_payload() {
        val (kv, f) = tempStore()
        try {
            kv.putString("page_cache", Golden.text("old_page_payload"), sync = true)
            val cache = PageCache(kv)

            assertEquals(1, cache.entryCount(), "旧字节里一页缓存")

            val items = cache.get("电影", 1, nowMs)
            assertNotNull(items, "保留期内必须命中（savedAt=1700000003333，now=+2s）")
            assertEquals(1, items.size)
            val it = items[0]
            assertEquals(3001L, it.vod_id)
            assertEquals("测试影片", it.vod_name)
            assertEquals("https://img.example.com/c.jpg", it.vod_pic)
            assertEquals("完结", it.vod_remarks)
            assertEquals("2024", it.vod_year)
            assertEquals("张三,李四", it.vod_actor)
            assertEquals("电影", it.type_name)
            assertEquals("https://api.example.com/detail?id=3001", it.vod_uri)
            assertEquals("ah_wencai", it._source)
        } finally {
            f.delete()
        }
    }

    @Test
    fun page_cache_key_format_matches_rules() {
        // 旧样本的键是「电影#1」，这是 PageCacheRules.keyOf 的产物。
        // 若键格式变了（比如改成分隔符 |），旧缓存会整体读不出来 —— 这条用例把它钉住。
        assertEquals("电影#1", PageCacheRules.keyOf("电影", 1))
        assertEquals("电影", PageCacheRules.categoryOf("电影#1"))
        assertEquals(1, PageCacheRules.pageOf("电影#1"))
    }

    @Test
    fun page_cache_reports_stale_state_from_old_bytes() {
        val (kv, f) = tempStore()
        try {
            kv.putString("page_cache", Golden.text("old_page_payload"), sync = true)
            val cache = PageCache(kv)

            // savedAt=1700000003333，now=+2s：远未到 30 分钟陈旧阈值
            assertFalse(cache.isStale("电影", 1, nowMs), "刚存 2 秒的缓存不该判陈旧")

            // 推进到 31 分钟后：仍可命中（stale-while-revalidate），但该后台刷新
            val later = nowMs + 31 * 60 * 1000L
            assertTrue(cache.isStale("电影", 1, later), "超过 30 分钟必须判陈旧")
            assertNotNull(cache.get("电影", 1, later), "陈旧不等于失效：仍要能立刻出内容")
        } finally {
            f.delete()
        }
    }

    @Test
    fun page_cache_tolerates_garbage_bytes() {
        val (kv, f) = tempStore()
        try {
            kv.putString("page_cache", "{not json at all", sync = true)
            val cache = PageCache(kv)
            assertEquals(0, cache.entryCount(), "损坏缓存清空内存镜像，不阻断启动")
            assertNull(cache.get("电影", 1, nowMs), "损坏缓存视为未命中")
        } finally {
            f.delete()
        }
    }

    @Test
    fun page_cache_write_then_read_roundtrips() {
        val (kv, f) = tempStore()
        try {
            val cache = PageCache(kv)
            val item = com.dengying.player.core.VideoItem(
                vod_id = 777L,
                vod_name = "缓存回环",
                vod_pic = "https://img.example.com/z.jpg",
                vod_remarks = "更新至8集",
                _source = "ah_duboku",
            )
            cache.put("电视剧", 2, listOf(item), nowMs)

            // 用新实例读：证明数据真的落到键值存储，而不是只在内存镜像里
            val fresh = PageCache(kv)
            val back = fresh.get("电视剧", 2, nowMs)
            assertNotNull(back, "写出去的缓存必须能被新实例读回")
            assertEquals(1, back.size)
            assertEquals(777L, back[0].vod_id)
            assertEquals("缓存回环", back[0].vod_name)
            assertEquals("更新至8集", back[0].vod_remarks)
            assertEquals("ah_duboku", back[0]._source)
        } finally {
            f.delete()
        }
    }
}
