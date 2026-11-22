package com.dengying.player.data.cache

import com.dengying.player.data.json.JsonX
import com.dengying.player.domain.page.PageCacheRules
import com.dengying.player.core.VideoItem
import com.dengying.player.platform.Clock
import com.dengying.player.platform.KeyValueStore
import com.dengying.player.platform.Log
import com.dengying.player.platform.PlatformLock
import kotlinx.serialization.json.JsonObject

/**
 * 首页分页结果缓存（FR-701、FR-702）
 *
 * ## 职责边界
 *
 * - **策略**（键构造、TTL、LRU 淘汰顺序）在 [PageCacheRules]，纯函数、可单测
 * - **本类只做 IO**：读写键值存储、按策略给的清单增删
 *
 * 这样"该不该命中"的判定不依赖设备，用例能穷举边界；而本类不做任何判断，
 * 拿到策略结论照做即可。
 *
 * ## 为什么存键值存储而不是文件
 *
 * 数据量小（30 条 × 每页 ≤60 个条目，序列化后约几十 KB），且与个人资产
 * 同属"轻量键值"性质。用文件要自己处理原子写与并发，收益不抵复杂度。
 *
 * ## 为什么不与 `favorites` 等用户资产同键
 *
 * 分页缓存是**派生数据**（丢了大不了重拉），与用户资产（丢了不可恢复）性质不同。
 * 用独立的 `page_cache` 键，这样将来"清空用户资产"不会误删缓存，
 * 反之"清缓存"也不会碰用户数据。
 *
 * ## KMP P3 批 E：从 `SharedPreferences` 换成 [KeyValueStore]
 *
 * 平台细节由接口挡在 commonMain 之外（Android = `SharedPreferences`，
 * JVM 测试 = `Properties` 文件）。`synchronized(this)` 换成 [PlatformLock]
 * （JVM/Android 上就是 `synchronized`，语义不变），`System.currentTimeMillis()`
 * 换成 [Clock.nowMs]。其余逻辑逐行保持原样，包括「`mem` 的读写仍在锁外」
 * 这一既有形态——本轮目标是**行为不变**，并发粒度调整不属于本轮。
 */
class PageCache(private val store: KeyValueStore) {

    private companion object {
        const val KEY = "page_cache"
        /** 单页最多存多少条——防止某页异常返回上千条把存储撑爆 */
        const val MAX_ITEMS_PER_PAGE = 60
        const val TAG = "DY/PageCache"
    }

    /** 内存镜像：避免每次读都做 JSON 解析 */
    @kotlin.concurrent.Volatile
    private var loaded = false

    /** 替代原来的 `synchronized(this)`，保护首次加载与落盘遍历 */
    private val lock = PlatformLock()

    private val mem = HashMap<String, Record>()

    private data class Record(
        val items: List<VideoItem>,
        val savedAt: Long,
        val accessedAt: Long,
    )

    private fun ensureLoaded() {
        if (loaded) return
        lock.withLock {
            // 双检：`withLock` 不是 inline，故用 return@withLock 而非 return
            if (loaded) return@withLock
            try {
                val raw = store.getString(KEY, "")
                if (raw.isNotEmpty()) {
                    val obj = JsonX.parseObject(raw)
                    if (obj != null) {
                        for (k in JsonX.keys(obj)) {
                            val rec = JsonX.optObject(obj, k) ?: continue
                            val arr = JsonX.optArray(rec, "items") ?: continue
                            val items = ArrayList<VideoItem>(JsonX.len(arr))
                            for (i in 0 until JsonX.len(arr)) {
                                JsonX.optObjectAt(arr, i)?.let { items += parseItem(it) }
                            }
                            mem[k] = Record(
                                items = items,
                                savedAt = JsonX.long(rec, "savedAt", 0L),
                                accessedAt = JsonX.long(rec, "accessedAt", 0L),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // 损坏时**不阻断**：清空内存镜像，下次写入会覆盖
                Log.w(TAG, "read cache failed: ${e.message}")
                mem.clear()
            }
            loaded = true
        }
    }

    /**
     * 取一页缓存。
     *
     * @return 命中返回条目列表；未命中或已过期返回 null
     */
    fun get(category: String, page: Int, nowMs: Long = Clock.nowMs()): List<VideoItem>? {
        ensureLoaded()
        val key = PageCacheRules.keyOf(category, page)
        val rec = mem[key] ?: return null
        if (!PageCacheRules.shouldServe(
                object : PageCacheRules.Entry {
                    override val key = key
                    override val savedAt = rec.savedAt
                    override val accessedAt = rec.accessedAt
                },
                nowMs,
            )
        ) {
            // 过期即删，不留"读了但没用"的负担
            mem.remove(key)
            persistAsync()
            return null
        }
        // 命中即刷新访问时间（LRU 需要）。
        // 【只改内存，不落盘】`accessedAt` 只服务于 LRU 淘汰排序，是易失信息：
        // 进程重启后按上次落盘的时间重算淘汰顺序，结果一样正确。而原实现在这里调
        // `persistAsync()`——每次命中都把**全部条目**重新序列化（30 页 × 60 条）
        // 并整份写回 `SharedPreferences`。这条路径跑在主线程（`IndexViewModel.loadData`
        // 直接调），于是"缓存命中"本该是零等待，实际成了一次全量 JSON 构造 + 写盘，
        // 冷启动进入首页时尤其明显。
        //
        // 现在 `accessedAt` 的变化只记在内存里，随下一次真正的 `put()` / `touch()`
        // 一并落盘——那些调用本来就要重写整份数据，不增加任何额外成本。
        mem[key] = rec.copy(accessedAt = nowMs)
        return rec.items
    }

    /**
     * 只刷新时间戳（内容未变时用）。
     *
     * 【为什么需要】后台刷新拿回来的内容与缓存一致时，如果不更新时间戳，
     * 下次进入仍判为"陈旧"，会**重复拉一次网络**——用户感知为"每次进来都转一下"。
     * 刷新时间戳后，30 分钟内不再重复拉取。
     */
    fun touch(category: String, page: Int, nowMs: Long = Clock.nowMs()) {
        ensureLoaded()
        val key = PageCacheRules.keyOf(category, page)
        val rec = mem[key] ?: return
        mem[key] = rec.copy(savedAt = nowMs, accessedAt = nowMs)
        persistAsync()
    }

    /** 该页是否已陈旧（该后台刷新）——用于决定要不要拉新内容 */
    fun isStale(category: String, page: Int, nowMs: Long = Clock.nowMs()): Boolean {
        ensureLoaded()
        val rec = mem[PageCacheRules.keyOf(category, page)] ?: return true
        return PageCacheRules.isStale(
            object : PageCacheRules.Entry {
                override val key = PageCacheRules.keyOf(category, page)
                override val savedAt = rec.savedAt
                override val accessedAt = rec.accessedAt
            },
            nowMs,
        )
    }

    /** 写入一页（含淘汰与过期清理） */
    fun put(category: String, page: Int, items: List<VideoItem>, nowMs: Long = Clock.nowMs()) {
        ensureLoaded()
        val key = PageCacheRules.keyOf(category, page)
        val trimmed = if (items.size > MAX_ITEMS_PER_PAGE) items.take(MAX_ITEMS_PER_PAGE) else items
        mem[key] = Record(trimmed, savedAt = nowMs, accessedAt = nowMs)

        val snapshot = mem.entries.map { (k, v) ->
            object : PageCacheRules.Entry {
                override val key = k
                override val savedAt = v.savedAt
                override val accessedAt = v.accessedAt
            }
        }
        // 先清过期，再按容量淘汰
        for (k in PageCacheRules.expiredKeys(snapshot, nowMs)) mem.remove(k)
        for (k in PageCacheRules.evictOrder(snapshot, key, nowMs)) mem.remove(k)

        persistAsync()
    }

    /** 整体失效（源启用状态变化、下拉刷新） */
    fun invalidateAll() {
        ensureLoaded()
        mem.clear()
        store.remove(KEY, sync = false)
    }

    /** 仅清理过期条目（进程启动时调一次即可，不必每次读） */
    fun pruneExpired(nowMs: Long = Clock.nowMs()) {
        ensureLoaded()
        val snapshot = mem.entries.map { (k, v) ->
            object : PageCacheRules.Entry {
                override val key = k
                override val savedAt = v.savedAt
                override val accessedAt = v.accessedAt
            }
        }
        val expired = PageCacheRules.expiredKeys(snapshot, nowMs)
        if (expired.isEmpty()) return
        for (k in expired) mem.remove(k)
        persistAsync()
    }

    fun entryCount(): Int {
        ensureLoaded()
        return mem.size
    }

    /**
     * 落盘。
     *
     * 【为什么用 `sync = false` 而不是 `sync = true`】缓存是派生数据，
     * 丢失可重建，不需要 FR-507 的"是否已落盘"语义。异步写不阻塞调用线程——
     * 这里的调用点在主线程（页面切分类后写缓存），同步写会引入可见卡顿。
     */
    private fun persistAsync() {
        val obj: JsonObject = lock.withLock {
            val pages = ArrayList<Pair<String, Any?>>(mem.size)
            for ((k, v) in mem) {
                val arr = ArrayList<Any?>(v.items.size)
                for (it in v.items) {
                    arr += JsonX.buildObject(
                        "id" to it.vod_id,
                        "name" to it.vod_name,
                        "pic" to it.vod_pic,
                        "remarks" to (it.vod_remarks ?: ""),
                        "year" to (it.vod_year ?: ""),
                        "actor" to (it.vod_actor ?: ""),
                        "type" to (it.type_name ?: ""),
                        "uri" to (it.vod_uri ?: ""),
                        "src" to (it._source ?: ""),
                    )
                }
                pages += k to JsonX.buildObject(
                    "items" to JsonX.buildArray(arr),
                    "savedAt" to v.savedAt,
                    "accessedAt" to v.accessedAt,
                )
            }
            JsonX.buildObject(*pages.toTypedArray())
        }
        store.putString(KEY, obj.toString(), sync = false)
    }

    private fun parseItem(o: JsonObject): VideoItem = VideoItem(
        vod_id = JsonX.long(o, "id", 0L),
        vod_name = JsonX.str(o, "name", ""),
        vod_pic = JsonX.str(o, "pic", ""),
        vod_remarks = JsonX.str(o, "remarks", ""),
        vod_year = JsonX.str(o, "year", ""),
        vod_actor = JsonX.str(o, "actor", ""),
        type_name = JsonX.str(o, "type", ""),
        vod_uri = JsonX.str(o, "uri", ""),
        _source = JsonX.str(o, "src", ""),
    )
}
