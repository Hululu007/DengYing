package com.dengying.player.data.store

import com.dengying.player.core.CmsClass
import com.dengying.player.core.FavoriteItem
import com.dengying.player.core.HistoryItem
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.json.StoreEnvelope
import com.dengying.player.data.source.SourceOverrideCodec
import com.dengying.player.domain.source.SourceOverrideRules
import com.dengying.player.domain.store.FavoritePrune
import com.dengying.player.domain.store.LedgerPrune
import com.dengying.player.domain.store.StoreGate
import com.dengying.player.platform.Clock
import com.dengying.player.platform.KeyValueStore
import com.dengying.player.platform.Log
import com.dengying.player.platform.PlatformLock
import com.dengying.player.platform.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 本地存储 —— 收藏 / 历史 / 设置 / 源覆盖层
 *
 * FR-507（P0）：本地存储未就绪时，写操作**不得静默丢弃**。
 *
 * 就绪判定与状态迁移委托 [StoreGate]（纯状态机，可脱设备单测）；本类负责
 * 键值存储的实际读写。
 *
 * 契约：所有写函数返回 `Boolean`，`true` = 已落盘，`false` = 未落盘（原因已记录日志）。
 * 写路径**不抛异常**——异常在此归类为 `false`，避免调用方协程中断。
 * 读路径保持返回空集合的既有语义（首帧渲染允许空）。
 *
 * 【为什么用键值存储而不是 Room】
 * 数据量小（收藏 ≤100、历史 ≤200、搜索历史 ≤20），且产品契约要求**整体重写**
 * （用户资产以列表为单位，不需要按行查询/事务）。键值存储的同步写
 * （`sync = true`，Android 侧即 `SharedPreferences.commit()`）同步返回布尔值，
 * 正好对应 FR-507 的「是否已落盘」契约；引入 Room 会为一个不需要数据库的问题
 * 加一个数据库。
 *
 * ## KMP P3 批 E：平台细节全部换到门面后面
 *
 * | 迁移前 | 迁移后 | 语义是否变 |
 * |---|---|---|
 * | `SharedPreferences` 构造参数 | [KeyValueStore] 接口 | 否：`SharedPrefsStore` 逐条直通 |
 * | `Dispatchers.IO` | [ioDispatcher] | 否：Android/JVM 上就是同一个对象 |
 * | `ConcurrentHashMap.newKeySet()` | [PlatformLock] + `HashSet` | 否：原本只做 add/remove/判空 |
 * | `System.currentTimeMillis()` | [Clock.nowMs] | 否：同一个值 |
 * | `org.json.JSONObject/JSONArray` | [JsonX]（kotlinx 语义门面） | 否：见 docs/04 §13.12 |
 */
class Store(private val store: KeyValueStore) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val gate = StoreGate().apply {
        this.scope = this@Store.scope
        bind { openStore() }
    }

    // ---------------- 键名（原工程一致，换端后数据密钥不变便于对照） ----------------

    private companion object {
        const val KEY_FAV = "favorites"
        const val KEY_HIS = "history"
        const val KEY_SEARCH = "search_history"
        const val KEY_AUTO_NEXT = "auto_next_episode"
        const val KEY_SRC_OVERRIDE = "source_override"
        const val KEY_CLASS_CACHE = "class_type_cache"

        const val MAX_HISTORY = 200
        const val MAX_SEARCH_HISTORY = 20
    }

    // ---------------- 初始化（FR-507） ----------------

    /** 初始化动作：把键值存储读一遍，失败即抛出 */
    private suspend fun openStore() {
        // 自检必须真的读一次：`getSharedPreferences` 本身不触盘，
        // 文件损坏要到读时才发现。`allKeys()` 在 Android 侧就是 `prefs.all`。
        store.allKeys()
        loadOverrideCache()
    }

    /** 初始化本地存储；重复调用安全（已就绪直接返回，初始化中共享在途动作） */
    suspend fun init() {
        gate.start()
    }

    /** FR-507 重试入口：失败态下重新初始化 */
    suspend fun retryInit(): Boolean {
        if (gate.state() == StoreGate.State.Ready) return true
        return gate.retry()
    }

    fun storeState(): StoreGate.State = gate.state()

    fun storeFailReason(): String = gate.reason()

    /**
     * 等待初始化收敛后取「可写」判定。
     *
     * FR-507「可重试」的落点：失败态下写入前重试一次（由闸门判定，同一调用内不叠加）。
     */
    private suspend fun ensure(): Boolean = gate.acquire()

    /** 写失败留痕（FR-507：写操作不得静默丢弃） */
    private fun warnWrite(op: String, e: Throwable? = null) {
        val why = e?.message ?: "store ${gate.state()}${if (gate.reason().isEmpty()) "" else ":" + gate.reason()}"
        Log.w("DY/Store", "write $op not persisted: $why")
    }

    // ---------------- FR-508 版本信封读写 ----------------

    /**
     * 由更高版本应用写入的键（读返回空集合，写被拒绝，需向用户提示）
     *
     * 迁移前是 `ConcurrentHashMap.newKeySet()`；这里换成普通 `HashSet` + [PlatformLock]，
     * 因为三处访问都只是 add / remove / 判空，不需要并发容器。
     */
    private val newerLock = PlatformLock()
    private val newerKeys = HashSet<String>()

    private fun markNewer(key: String) {
        newerLock.withLock { newerKeys.add(key) }
    }

    private fun unmarkNewer(key: String) {
        newerLock.withLock { newerKeys.remove(key) }
    }

    /** FR-509：最近一次 addFavorite 淘汰掉的收藏条数（供页面提示，读后清零） */
    @kotlin.concurrent.Volatile
    private var lastFavoriteDrop: Int = 0

    /** 读一个列表键（FR-508）：损坏 → 原始串留到诊断键；更高版本 → 记待提示且不清空 */
    private fun readListRaw(key: String): String = store.getString(key, "")

    private inline fun <T> readList(key: String, parse: (JsonArray) -> List<T>): List<T> {
        val raw = readListRaw(key)
        val d = StoreEnvelope.decodePayload(raw)
        when (d.kind) {
            StoreEnvelope.Kind.Corrupt -> {
                // 单键损坏不连带他键：保留原始串供排查，本轮按空集合处理
                runCatching { store.putString(StoreEnvelope.KEY_CORRUPT, raw, sync = true) }
                Log.w("DY/Store", "key $key corrupt; raw preserved")
                return emptyList()
            }
            StoreEnvelope.Kind.NeedsUpgrade -> {
                markNewer(key)
                Log.w("DY/Store", "key $key written by newer version v=${d.version}")
                return emptyList()
            }
            StoreEnvelope.Kind.Ok -> {
                val arr = JsonX.parseArray(d.payload)
                if (arr == null) return emptyList()
                return runCatching { parse(arr) }.getOrDefault(emptyList())
            }
        }
    }

    /**
     * 写一个列表键（FR-508）。
     *
     * 更高版本写入的数据**拒绝**被旧结构覆盖——否则会抹掉用户数据。
     * @return 是否已落盘（FR-507）
     */
    private fun writeList(key: String, jsonArray: JsonArray, op: String): Boolean {
        val raw = readListRaw(key)
        if (!StoreEnvelope.canOverwrite(raw)) {
            markNewer(key)
            Log.w("DY/Store", "write $op blocked: key holds newer-version data")
            return false
        }
        // sync = true 而非 false：异步写拿不到「是否真的写成功」，
        // 而 FR-507 的契约就是返回这个布尔值
        val ok = store.putString(key, StoreEnvelope.encodePayload(jsonArray.toString()), sync = true)
        if (ok) unmarkNewer(key)
        return ok
    }

    /** FR-508：是否有键由更高版本应用写入（页面在列表为空时可据此显示提示） */
    fun hasNewerVersionData(): Boolean = newerLock.withLock { newerKeys.isNotEmpty() }

    /** FR-508 提示文案（无待提示时为空串） */
    fun newerVersionNotice(): String =
        if (newerLock.withLock { newerKeys.isEmpty() }) "" else "本地数据由更高版本的应用写入，请升级后再使用"

    /** FR-509 淘汰提示文案（无淘汰时为空串）；读后清零 */
    fun takeFavoriteDropNotice(): String = FavoritePrune.prunedNotice(takeFavoriteDropCount())

    fun takeFavoriteDropCount(): Int {
        val n = lastFavoriteDrop
        lastFavoriteDrop = 0
        return n
    }

    // ---------------- 收藏 ----------------

    suspend fun getFavorites(): List<FavoriteItem> {
        if (!ensure()) return emptyList()
        return withContext(ioDispatcher) {
            readList(KEY_FAV) { arr ->
                (0 until JsonX.len(arr)).mapNotNull { i ->
                    JsonX.optObjectAt(arr, i)?.let { o ->
                        FavoriteItem(
                            vod_id = JsonX.long(o, "vod_id", 0L),
                            vod_name = JsonX.str(o, "vod_name", ""),
                            vod_pic = JsonX.str(o, "vod_pic", ""),
                            source = JsonX.str(o, "source", ""),
                            addTime = JsonX.long(o, "addTime", 0L),
                            vod_uri = JsonX.str(o, "vod_uri", ""),
                        )
                    }
                }
            }
        }
    }

    suspend fun isFavorite(vodId: Long, source: String): Boolean =
        getFavorites().any { it.vod_id == vodId && it.source == source }

    /** @return 是否已落盘（FR-507）；更高版本数据存在时拒写（FR-508） */
    suspend fun addFavorite(item: FavoriteItem): Boolean {
        if (!ensure()) {
            warnWrite("addFavorite")
            return false
        }
        return withContext(ioDispatcher) {
            try {
                val list = getFavorites().toMutableList()
                if (list.any { it.vod_id == item.vod_id && it.source == item.source }) return@withContext true
                list.add(0, item)
                // FR-509：超容量按 addTime 最旧整条淘汰，并记录条数供页面提示
                val pruned = FavoritePrune.pruneFavorites(
                    list.map { FavoritePruneEntry(it.vod_id, it.source, it.addTime, it) }
                )
                lastFavoriteDrop = pruned.dropped
                writeList(KEY_FAV, favoritesJson(pruned.kept.map { it.item }), "addFavorite")
            } catch (e: Exception) {
                warnWrite("addFavorite", e)
                false
            }
        }
    }

    /** @return 是否已落盘（FR-507）；更高版本数据存在时拒写（FR-508） */
    suspend fun removeFavorite(vodId: Long, source: String): Boolean {
        if (!ensure()) {
            warnWrite("removeFavorite")
            return false
        }
        return withContext(ioDispatcher) {
            try {
                val next = getFavorites().filterNot { it.vod_id == vodId && it.source == source }
                writeList(KEY_FAV, favoritesJson(next), "removeFavorite")
            } catch (e: Exception) {
                warnWrite("removeFavorite", e)
                false
            }
        }
    }

    /**
     * 收藏翻转（FR-507）。
     *
     * 返回值是「是否已落盘」，不是翻转后的收藏态：
     * 调用方必须在 `true` 时才翻转界面，`false` 时保持原状并给可见失败，
     * 否则会出现「界面显示已收藏、重启后消失」。
     */
    suspend fun toggleFavorite(item: FavoriteItem): Boolean {
        return if (isFavorite(item.vod_id, item.source)) {
            removeFavorite(item.vod_id, item.source)
        } else {
            addFavorite(item)
        }
    }

    private fun favoritesJson(list: List<FavoriteItem>): JsonArray =
        JsonX.buildArray(
            list.map { f ->
                JsonX.buildObject(
                    "vod_id" to f.vod_id,
                    "vod_name" to f.vod_name,
                    "vod_pic" to f.vod_pic,
                    "source" to f.source,
                    "addTime" to f.addTime,
                    "vod_uri" to (f.vod_uri ?: ""),
                )
            }
        )

    /** FavoritePrune 要求的形状适配（domain 层不依赖 model 层，故用薄包装） */
    private data class FavoritePruneEntry(
        override val vodId: Long,
        override val source: String,
        override val addTime: Long,
        val item: FavoriteItem,
    ) : FavoritePrune.Entry

    // ---------------- 历史 ----------------

    suspend fun getHistory(): List<HistoryItem> {
        if (!ensure()) return emptyList()
        return withContext(ioDispatcher) {
            readList(KEY_HIS) { arr -> historyOf(arr) }
        }
    }

    private fun historyOf(arr: JsonArray): List<HistoryItem> =
        (0 until JsonX.len(arr)).mapNotNull { i ->
            JsonX.optObjectAt(arr, i)?.let { o ->
                HistoryItem(
                    vod_id = JsonX.long(o, "vod_id", 0L),
                    vod_name = JsonX.str(o, "vod_name", ""),
                    vod_pic = JsonX.str(o, "vod_pic", ""),
                    source = JsonX.str(o, "source", ""),
                    episodeIndex = JsonX.int(o, "episodeIndex", 0),
                    episodeName = JsonX.str(o, "episodeName", ""),
                    position = JsonX.long(o, "position", 0L),
                    duration = JsonX.long(o, "duration", 0L),
                    updateTime = JsonX.long(o, "updateTime", 0L),
                    vod_uri = JsonX.str(o, "vod_uri", ""),
                )
            }
        }

    /**
     * 保存播放历史。
     *
     * 去重键 = 影片 + 线路 + 集数（同一部剧的每一集单独记进度）。
     * @return 是否已落盘（FR-507）
     */
    suspend fun saveHistory(item: HistoryItem): Boolean {
        if (!ensure()) {
            warnWrite("saveHistory")
            return false
        }
        return withContext(ioDispatcher) {
            try {
                val next = getHistory()
                    .filterNot {
                        it.vod_id == item.vod_id && it.source == item.source &&
                            it.episodeIndex == item.episodeIndex
                    }
                    .toMutableList()
                next.add(0, item)
                // FR-504：按片分组淘汰，保证每部在看的剧至少留最新一集
                val pruned = LedgerPrune.pruneLedger(
                    next.map { LedgerEntry(it) }, MAX_HISTORY
                ).map { it.item }
                writeList(KEY_HIS, historyJson(pruned), "saveHistory")
            } catch (e: Exception) {
                warnWrite("saveHistory", e)
                false
            }
        }
    }

    private data class LedgerEntry(val item: HistoryItem) : LedgerPrune.Entry {
        override val vodId: Long get() = item.vod_id
        override val source: String get() = item.source
        override val episodeIndex: Int get() = item.episodeIndex
        override val updateTime: Long get() = item.updateTime
    }

    /**
     * 同步保存历史 —— **仅供 `ViewModel.onCleared` 使用**。
     *
     * 那里协程作用域已被取消，而 FR-408 要求"离开页面立即落盘"。
     * 键值存储的 `sync = true` 写本身是同步的（Android 侧即 `commit()`），
     * 故这里直接调用；读现有列表也是同步读（Android 侧 `getString` 是内存读，不触盘）。
     *
     * 不把这套同步路径开放给页面：它会在主线程做 IO，只适合"最后一次写入"
     * 这种不可避免的场景。
     */
    fun saveHistoryBlocking(item: HistoryItem) {
        try {
            val list = runCatching { getHistoryBlocking() }.getOrDefault(emptyList())
            val next = list.filterNot {
                it.vod_id == item.vod_id && it.source == item.source &&
                    it.episodeIndex == item.episodeIndex
            }.toMutableList()
            next.add(0, item)
            val pruned = LedgerPrune.pruneLedger(next.map { LedgerEntry(it) }, MAX_HISTORY)
                .map { it.item }
            val raw = readListRaw(KEY_HIS)
            if (!StoreEnvelope.canOverwrite(raw)) return
            store.putString(
                KEY_HIS,
                StoreEnvelope.encodePayload(historyJson(pruned).toString()),
                sync = true,
            )
        } catch (e: Exception) {
            warnWrite("saveHistoryBlocking", e)
        }
    }

    private fun getHistoryBlocking(): List<HistoryItem> =
        readList(KEY_HIS) { arr -> historyOf(arr) }

    /** 取某集的上次播放进度（按 影片 + 线路 + 集数 精确查找） */
    suspend fun getEpisodePosition(vodId: Long, source: String, episodeIndex: Int): Long =
        getHistory().firstOrNull {
            it.vod_id == vodId && it.source == source && it.episodeIndex == episodeIndex
        }?.position ?: 0L

    /** 取整部影片最近观看的一集（用于详情页高亮「继续观看」） */
    suspend fun getLastEpisode(vodId: Long, source: String): HistoryItem? =
        getHistory().filter { it.vod_id == vodId && it.source == source }
            .maxByOrNull { it.updateTime }

    /** @return 是否已落盘（FR-507）；更高版本数据存在时拒写（FR-508） */
    suspend fun clearHistory(): Boolean {
        if (!ensure()) {
            warnWrite("clearHistory")
            return false
        }
        return withContext(ioDispatcher) {
            try {
                writeList(KEY_HIS, JsonX.buildArray(emptyList()), "clearHistory")
            } catch (e: Exception) {
                warnWrite("clearHistory", e)
                false
            }
        }
    }

    private fun historyJson(list: List<HistoryItem>): JsonArray =
        JsonX.buildArray(
            list.map { h ->
                JsonX.buildObject(
                    "vod_id" to h.vod_id,
                    "vod_name" to h.vod_name,
                    "vod_pic" to h.vod_pic,
                    "source" to h.source,
                    "episodeIndex" to h.episodeIndex,
                    "episodeName" to h.episodeName,
                    "position" to h.position,
                    "duration" to h.duration,
                    "updateTime" to h.updateTime,
                    "vod_uri" to (h.vod_uri ?: ""),
                )
            }
        )

    // ---------------- 搜索历史 ----------------

    suspend fun getSearchHistory(): List<String> {
        if (!ensure()) return emptyList()
        return withContext(ioDispatcher) {
            readList(KEY_SEARCH) { arr ->
                // 只保留字符串项：混入非字符串的历史载荷不应污染调用方
                (0 until JsonX.len(arr)).mapNotNull { i ->
                    val e = arr[i]
                    if (JsonX.isString(e)) JsonX.textOf(e) else null
                }
            }
        }
    }

    /**
     * 记录一次搜索关键词（自动写入，非用户显式动作）。
     * 去重（最新在前）、上限 20 条。
     * @return 是否已落盘（FR-507）；失败不阻断搜索本身
     */
    suspend fun addSearchHistory(keyword: String): Boolean {
        val kw = keyword.trim()
        if (kw.isEmpty()) return true
        if (!ensure()) {
            warnWrite("addSearchHistory")
            return false
        }
        return withContext(ioDispatcher) {
            try {
                val next = getSearchHistory().filter { it != kw }.toMutableList()
                next.add(0, kw)
                while (next.size > MAX_SEARCH_HISTORY) next.removeAt(next.size - 1)
                writeList(KEY_SEARCH, JsonX.buildArray(next), "addSearchHistory")
            } catch (e: Exception) {
                warnWrite("addSearchHistory", e)
                false
            }
        }
    }

    /** @return 是否已落盘（FR-507）；更高版本数据存在时拒写（FR-508） */
    suspend fun removeSearchHistory(keyword: String): Boolean {
        if (!ensure()) {
            warnWrite("removeSearchHistory")
            return false
        }
        return withContext(ioDispatcher) {
            try {
                val next = getSearchHistory().filter { it != keyword }
                writeList(KEY_SEARCH, JsonX.buildArray(next), "removeSearchHistory")
            } catch (e: Exception) {
                warnWrite("removeSearchHistory", e)
                false
            }
        }
    }

    /** @return 是否已落盘（FR-507） */
    suspend fun clearSearchHistory(): Boolean {
        if (!ensure()) {
            warnWrite("clearSearchHistory")
            return false
        }
        return withContext(ioDispatcher) {
            try {
                writeList(KEY_SEARCH, JsonX.buildArray(emptyList()), "clearSearchHistory")
            } catch (e: Exception) {
                warnWrite("clearSearchHistory", e)
                false
            }
        }
    }

    // ---------------- 播放设置 ----------------

    /**
     * FR-410：自动连播开关（跨会话持久，默认开）。读失败回落默认值。
     *
     * FR-508：本键是布尔而非列表，故不经列表信封；载荷用 `v1:` 前缀标版本，
     * 无前缀视为旧值——这样同一存储文件里不存在「有版本 / 无版本」两套规则。
     */
    suspend fun getAutoNext(): Boolean {
        if (!ensure()) return true
        return withContext(ioDispatcher) {
            val raw = store.getString(KEY_AUTO_NEXT, "")
            val v = if (raw.startsWith("v1:")) raw.substring(3) else raw
            v != "false"
        }
    }

    /** @return 是否已落盘（FR-507） */
    suspend fun setAutoNext(on: Boolean): Boolean {
        if (!ensure()) {
            warnWrite("setAutoNext")
            return false
        }
        return withContext(ioDispatcher) {
            try {
                store.putString(KEY_AUTO_NEXT, "v1:${if (on) "true" else "false"}", sync = true)
            } catch (e: Exception) {
                warnWrite("setAutoNext", e)
                false
            }
        }
    }

    // ---------------- FR-601 源覆盖层 ----------------

    @kotlin.concurrent.Volatile
    private var overrideCache: SourceOverrideRules.Payload = SourceOverrideRules.emptyPayload()

    /** 从存储载入覆盖层缓存（仅在初始化时调用一次） */
    private fun loadOverrideCache() {
        try {
            val raw = store.getString(KEY_SRC_OVERRIDE, "")
            // 存储内容是版本信封（FR-508），故先用同一套解码
            val dec = StoreEnvelope.decodePayload(raw)
            overrideCache = if (dec.needsUpgrade) {
                markNewer(KEY_SRC_OVERRIDE)
                SourceOverrideRules.emptyPayload()
            } else {
                SourceOverrideCodec.normalizePayload(dec.payload)
            }
        } catch (e: Exception) {
            // 读失败按"无覆盖"处理——不因为读不到偏好就阻断聚合
            overrideCache = SourceOverrideRules.emptyPayload()
            warnWrite("loadSourceOverride", e)
        }
    }

    /** FR-601：同步取当前覆盖层。供聚合层的同步源过滤使用。 */
    fun getSourceOverride(): SourceOverrideRules.Payload = overrideCache

    /**
     * FR-601：设定某源的启用状态并持久化。
     *
     * 缓存**立即**更新（用户的下一次搜索就该看到效果），落盘同步进行；
     * 落盘失败返回 false，调用方据此提示。
     */
    suspend fun setSourceEnabled(key: String, enabled: Boolean, baselineEnabled: Boolean): Boolean {
        val next = SourceOverrideRules.setEnabled(
            overrideCache, key, enabled, baselineEnabled, Clock.nowMs(),
        )
        overrideCache = next
        if (!ensure()) {
            warnWrite("setSourceEnabled")
            return false
        }
        return withContext(ioDispatcher) {
            writeList(
                KEY_SRC_OVERRIDE,
                JsonX.buildArray(
                    next.list.map {
                        JsonX.buildObject(
                            "key" to it.key,
                            "enabled" to it.enabled,
                            "updatedAt" to it.updatedAt,
                        )
                    }
                ),
                "setSourceEnabled",
            )
        }
    }

    /** FR-601：清空全部覆盖（「恢复默认」） */
    suspend fun clearSourceOverride(): Boolean {
        overrideCache = SourceOverrideRules.clearAll(overrideCache)
        if (!ensure()) {
            warnWrite("clearSourceOverride")
            return false
        }
        return withContext(ioDispatcher) {
            writeList(KEY_SRC_OVERRIDE, JsonX.buildArray(emptyList()), "clearSourceOverride")
        }
    }

    // ---------------- 分类表磁盘缓存（FR-103） ----------------

    /** 供分类缓存读取（同一个键值存储） */
    suspend fun readClassCache(): Map<String, Pair<List<CmsClass>, Long>> {
        if (!ensure()) return emptyMap()
        return withContext(ioDispatcher) {
            try {
                val raw = store.getString(KEY_CLASS_CACHE, "")
                val obj = JsonX.parseObject(raw) ?: return@withContext emptyMap()
                val out = HashMap<String, Pair<List<CmsClass>, Long>>()
                for (k in JsonX.keys(obj)) {
                    val v = JsonX.optObject(obj, k) ?: continue
                    val arr = JsonX.optArray(v, "classes") ?: continue
                    val list = ArrayList<CmsClass>(JsonX.len(arr))
                    for (i in 0 until JsonX.len(arr)) {
                        val c = JsonX.optObjectAt(arr, i) ?: continue
                        list += CmsClass(
                            type_id = JsonX.int(c, "type_id", 0),
                            type_name = JsonX.str(c, "type_name", ""),
                            type_pid = if (JsonX.has(c, "type_pid") && !JsonX.isNull(c["type_pid"])) {
                                JsonX.int(c, "type_pid", 0)
                            } else {
                                null
                            },
                        )
                    }
                    out[k] = list to JsonX.long(v, "time", 0L)
                }
                out
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    suspend fun writeClassCache(map: Map<String, Pair<List<CmsClass>, Long>>): Boolean {
        if (!ensure()) return false
        return withContext(ioDispatcher) {
            try {
                val pages = ArrayList<Pair<String, Any?>>(map.size)
                for ((k, v) in map) {
                    val arr = JsonX.buildArray(
                        v.first.map { c ->
                            val pairs = ArrayList<Pair<String, Any?>>(3)
                            pairs += "type_id" to c.type_id
                            pairs += "type_name" to c.type_name
                            c.type_pid?.let { pairs += "type_pid" to it }
                            JsonX.buildObject(*pairs.toTypedArray())
                        }
                    )
                    pages += k to JsonX.buildObject("classes" to arr, "time" to v.second)
                }
                val obj: JsonObject = JsonX.buildObject(*pages.toTypedArray())
                store.putString(KEY_CLASS_CACHE, obj.toString(), sync = true)
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun deleteClassCache() {
        if (!ensure()) return
        withContext(ioDispatcher) {
            store.remove(KEY_CLASS_CACHE, sync = true)
        }
    }
}
