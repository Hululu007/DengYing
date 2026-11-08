@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.dengying.player.app

import com.dengying.player.core.CmsSources
import com.dengying.player.core.VideoItem
import com.dengying.player.core.capsOf
import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.source.discover.DiscoverAdapter
import com.dengying.player.data.store.Store
import com.dengying.player.domain.page.ConcurrencyGate
import com.dengying.player.domain.page.PageBudget
import com.dengying.player.domain.search.DiscoverRules
import com.dengying.player.domain.search.SearchNotice
import com.dengying.player.domain.search.SearchSession
import com.dengying.player.domain.search.TitleKey
import com.dengying.player.platform.Clock
import com.dengying.player.platform.FileKeyValueStore
import com.dengying.player.platform.Log
import com.dengying.player.platform.ioDispatcher
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.set
import com.dengying.player.platform.PlatformLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.runtime.NativeRuntimeApi

/**
 * 鸿蒙壳的应用装配 + 数据门面（P6-6）
 *
 * 结果走轮询槽（同 HttpTestProbe 模式）：Kotlin 写固定缓冲，ArkTS 每 200ms 取。
 * 装配顺序对齐 Android DengYingApp.onCreate（去掉播放链路——播放器阶段另有任务）。
 */
@OptIn(ExperimentalAtomicApi::class, NativeRuntimeApi::class)
object OhosApp {

    internal val appScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    internal val started = AtomicInt(0)

    lateinit var kv: FileKeyValueStore
        private set
    lateinit var store: Store
        private set
    lateinit var sources: SourceRepository
        private set
    lateinit var pageCache: PageCache
        private set

    /** ArkTS 侧以 filesDir 传入；返回 1=成功/已启动，0=失败 */
    fun init(dataDir: String): Int {
        if (started.load() != 0) return 1
        return try {
            kv = FileKeyValueStore(dataDir)
            store = Store(kv)
            sources = SourceRepository()
            pageCache = PageCache(kv)
            pageCache.pruneExpired()
            sources.classCacheReader = {
                store.readClassCache().mapValues { (_, e) ->
                    SourceRepository.ClassCacheEntry(e.first, e.second)
                }
            }
            sources.classCacheWriter = { map ->
                store.writeClassCache(map.mapValues { (_, e) -> e.classes to e.time })
            }
            appScope.launch {
                store.init()
                sources.override = store.getSourceOverride()
                Log.w("DY/App", "store ${store.storeState()} reason=${store.storeFailReason()}")
            }
            started.store(1)
            1
        } catch (e: Exception) {
            Log.w("DY/App", "init failed: ${e.message}")
            0
        }
    }
}

// ---------------- 任务槽（分类页 / 搜索共用：UI 串行，同一时刻至多一个任务） ----------------

private const val SLOT_CAPACITY = 4 * 1024 * 1024
private val slotBuffer: CPointer<ByteVar> = nativeHeap.allocArray<ByteVar>(SLOT_CAPACITY.toLong())

@OptIn(ExperimentalAtomicApi::class)
private val slotRunning = AtomicInt(0)

@OptIn(ExperimentalAtomicApi::class)
private val slotSeq = AtomicLong(0)

@OptIn(ExperimentalAtomicApi::class)
private fun publishSlot(json: String) {
    val bytes = json.encodeToByteArray()
    if (bytes.size >= SLOT_CAPACITY - 8) {
        // 极端超长：收缩为「仅头 50 条」重发（保头部信息，UI 可显示截断）
        Log.w("DY/Facade", "slot overflow ${bytes.size}, truncating skipped — drop result")
        return
    }
    for (i in bytes.indices) slotBuffer[i] = bytes[i]
    slotBuffer[bytes.size] = 0
}

@OptIn(ExperimentalAtomicApi::class)
private fun writeSlotEmpty() {
    slotBuffer[0] = 0
}

@OptIn(ExperimentalAtomicApi::class)
private fun currentSlot(): String? {
    if (slotBuffer[0] == 0.toByte()) return null
    // UTF-8 解码（此前逐字节 toChar() 是 Latin-1 语义：中文 tag/tip 变双重编码乱码，
    // 与 ArkTS 侧 expectTag 比对必失败——2026-11-02 搜索结果「永不显示」的根因）
    var len = 0
    while (len < SLOT_CAPACITY && slotBuffer[len] != 0.toByte()) len++
    val bytes = ByteArray(len)
    for (i in 0 until len) bytes[i] = slotBuffer[i]
    return bytes.decodeToString()
}

private fun appendJsonStr(sb: StringBuilder, v: String) {
    sb.append('"')
    for (c in v) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') {
                sb.append("\\u00").append(c.code.toString(16).padStart(2, '0'))
            } else sb.append(c)
        }
    }
    sb.append('"')
}

private fun jsonQuote(v: String): String {
    val sb = StringBuilder(v.length + 2)
    appendJsonStr(sb, v)
    return sb.toString()
}

private fun videoItemJson(it: VideoItem): String = buildString {
    append('{')
    append("\"id\":").append(it.vod_id)
    append(",\"name\":"); appendJsonStr(this, it.vod_name)
    append(",\"pic\":"); appendJsonStr(this, it.vod_pic)
    append(",\"remarks\":"); appendJsonStr(this, it.vod_remarks ?: "")
    append(",\"year\":"); appendJsonStr(this, it.vod_year ?: "")
    append(",\"area\":"); appendJsonStr(this, it.vod_area ?: "")
    append(",\"type\":"); appendJsonStr(this, it.type_name ?: "")
    append(",\"src\":"); appendJsonStr(this, it._source ?: "")
    append(",\"uri\":"); appendJsonStr(this, it.vod_uri ?: "")
    append('}')
}

private fun mergedJson(
    items: List<VideoItem>,
    tag: String,
    partial: Boolean,
    elapsedMs: Long,
    tip: String = "",
): String {
    val sb = StringBuilder(items.size * 160 + 64)
    sb.append('{')
    sb.append("\"tag\":"); appendJsonStr(sb, tag)
    sb.append(",\"partial\":").append(if (partial) "true" else "false")
    sb.append(",\"elapsedMs\":").append(elapsedMs)
    sb.append(",\"n\":").append(items.size)
    sb.append(",\"items\":[")
    items.forEachIndexed { i, it ->
        if (i > 0) sb.append(',')
        sb.append(videoItemJson(it))
    }
    sb.append(']')
    if (tip.isNotEmpty()) {
        sb.append(",\"tip\":"); appendJsonStr(sb, tip)
    }
    sb.append('}')
    return sb.toString()
}

/** 1=启动成功；0=已有任务在跑（UI 串行约束）或装配未初始化 */
@OptIn(ExperimentalAtomicApi::class)
private fun startTask(tag: String, body: suspend (emitPartial: (String) -> Unit) -> String): Int {
    if (OhosApp.started.load() == 0) return 0
    if (slotRunning.load() == 1) return 0
    val seq = slotSeq.addAndFetch(1)
    Log.w("DY/Facade", "startTask queued tag=$tag seq=$seq")
    slotRunning.store(1)
    writeSlotEmpty()
    OhosApp.appScope.launch {
        val finalJson = try {
            body { js ->
                if (slotSeq.load() == seq) publishSlot(js)
            }
        } catch (e: Exception) {
            "{\"tag\":${jsonQuote(tag)},\"partial\":false,\"elapsedMs\":0,\"n\":0,\"items\":[]," +
                "\"err\":${jsonQuote(e.message ?: e::class.simpleName ?: "Error")}}"
        }
        if (slotSeq.load() == seq) {
            publishSlot(finalJson)
            slotRunning.store(0)
        }
    }
    return 1
}

// ---------------- 门面导出 ----------------

/** 六分类名 JSON 数组（与 Android UiState.categories 同源） */
@CName("dengy_home_categories")
fun homeCategories(): String {
    val sb = StringBuilder("[")
    CmsSources.HOME_CATEGORIES.forEachIndexed { i, c ->
        if (i > 0) sb.append(',')
        appendJsonStr(sb, c)
    }
    sb.append(']')
    return sb.toString()
}

@CName("dengy_fetch_category")
fun fetchCategory(category: String?, page: Int): Int {
    val cat = category ?: return 0
    val t0 = Clock.nowMs()
    return startTask("cat:$cat#$page") { emitPartial ->
        val res = OhosApp.sources.fetchByCategory(
            OhosApp.sources.allSources(), cat, page.coerceAtLeast(1),
            onFirstBatch = { partial ->
                if (partial.size >= 8) {
                    emitPartial(mergedJson(partial, "cat:$cat#$page", true, Clock.nowMs() - t0))
                }
            },
        )
        if (res.hasData) OhosApp.pageCache.put(cat, page.coerceAtLeast(1), res.items)
        mergedJson(res.items.take(120), "cat:$cat#$page", false, Clock.nowMs() - t0)
    }
}

/** 参与聚合的源数（FR-208 口径：override 之后 enabled && caps.search，与 SearchViewModel.searchSourceCount 一致） */
private val searchSession = SearchSession()

@Volatile
private var historyCache: String? = null

private fun searchSourceCount(): Int =
    SearchNotice.countSearchable(
        CmsSources.ALL.map { SearchNotice.SearchableSource(OhosApp.sources.isSourceEnabled(it), capsOf(it).search) },
    )

@CName("dengy_search_all")
@OptIn(ExperimentalAtomicApi::class)
fun searchAll(keyword: String?): Int {
    val kw = keyword?.trim()
    if (kw.isNullOrEmpty()) return 0
    val token = searchSession.begin(kw)
    OhosApp.appScope.launch {
        val ok = OhosApp.store.addSearchHistory(kw)
        Log.w("DY/Facade", "addSearchHistory($kw) ok=$ok")
    }
    val t0 = Clock.nowMs()
    val count = searchSourceCount()
    val r = startTask("search:$kw") { emitPartial ->
        val res = OhosApp.sources.searchAllBudgeted(
            OhosApp.sources.allSources(), kw, t0,
            onPartial = { snapshot ->
                if (searchSession.shouldAdopt(token, kw)) {
                    emitPartial(
                        mergedJson(
                            snapshot.take(120), "search:$kw", true, Clock.nowMs() - t0,
                            tip = SearchNotice.partialText(snapshot.size, count),
                        ),
                    )
                }
            },
        )
        val tip = when {
            res.items.isEmpty() -> SearchNotice.doneText(0, kw, count)
            res.outcome == PageBudget.Outcome.Expired -> PageBudget.outcomeNotice(res.outcome)
            else -> ""
        }
        mergedJson(res.items.take(120), "search:$kw", false, Clock.nowMs() - t0, tip = tip)
    }
    return r
}

/** 1=已取消并落槽；0=无在跑任务 */
@CName("dengy_cancel_search")
@OptIn(ExperimentalAtomicApi::class)
fun cancelSearch(): Int {
    Log.w("DY/Facade", "cancelSearch called running=${slotRunning.load()}")
    if (slotRunning.load() != 1) return 0
    // 推进序号：在途任务的 partial/final 一律不再落槽（同 SearchSession 挡过期结果的语义）
    slotSeq.addAndFetch(1)
    searchSession.finish(searchSession.current())
    OhosApp.appScope.launch { ConcurrencyGate.outbound.cancelQueued() }
    val kw = searchSession.keyword()
    val tip = SearchNotice.doneText(0, kw, searchSourceCount())
    publishSlot("{\"tag\":${jsonQuote("search:$kw")},\"partial\":false,\"elapsedMs\":0,\"n\":0,\"items\":[],\"cancelled\":true,\"tip\":${jsonQuote(tip)}}")
    slotRunning.store(0)
    return 1
}

// ---------------- 搜索联想（对齐 SearchViewModel.loadSuggest：防抖 300ms + 会话守卫 + 失败静默） ----------------

@OptIn(ExperimentalAtomicApi::class)
private val suggestLock = PlatformLock()
private val suggestSession = SearchSession()

@Volatile
private var suggestJob: Job? = null

@Volatile
private var suggestResult: String? = "[]"

/** 1=已启动联想；0=关键词为空 */
@CName("dengy_start_suggest")
fun startSuggest(keyword: String?): Int {
    val kw = keyword?.trim()
    if (kw.isNullOrEmpty()) {
        suggestResult = "[]"
        return 0
    }
    val token = suggestSession.begin(kw)
    suggestJob?.cancel()
    suggestJob = OhosApp.appScope.launch {
        delay(300)
        val feed = try {
            DiscoverAdapter.iqiyiSuggest(kw)
        } catch (e: Exception) {
            null
        }
        if (!suggestSession.shouldAdopt(token, kw)) return@launch
        val items = DiscoverRules.suggestItems(feed)
        val sb = StringBuilder("[")
        items.forEachIndexed { i, d ->
            if (i > 0) sb.append(',')
            sb.append("{\"title\":").append(jsonQuote(d.title)).append('}')
        }
        sb.append(']')
        suggestResult = sb.toString()
    }
    return 1
}

@CName("dengy_suggest_result")
fun suggestResultJson(): String? = suggestResult

// ---------------- 搜索历史（FR-507：落盘失败如实报错，不假装成功） ----------------

private fun listJson(items: List<String>): String {
    val sb = StringBuilder("[")
    items.forEachIndexed { i, s ->
        if (i > 0) sb.append(',')
        appendJsonStr(sb, s)
    }
    sb.append(']')
    return sb.toString()
}

@CName("dengy_get_history")
@OptIn(ExperimentalAtomicApi::class)
fun getHistory(): String? {
    if (OhosApp.started.load() == 0) return "[]"
    // 直读 Store（内存 map + 必要时整文件读入，毫秒级）——与 remove/clear 同走 runBlocking，
    // 免掉缓存刷新的竞态（2026-11-02：搜索结果出来后 chips 读到旧缓存恒为空）
    return try {
        runBlocking { listJson(OhosApp.store.getSearchHistory()) }
    } catch (e: Exception) {
        "[]"
    }
}

@CName("dengy_refresh_history")
@OptIn(ExperimentalAtomicApi::class)
fun refreshHistory(): Int {
    if (OhosApp.started.load() == 0) return 0
    OhosApp.appScope.launch {
        val list = try {
            OhosApp.store.getSearchHistory()
        } catch (e: Exception) {
            emptyList()
        }
        suggestLock.withLock { historyCache = listJson(list) }
    }
    return 1
}

/** 1=删除成功；0=落盘失败（FR-507） */
@CName("dengy_remove_history")
@OptIn(ExperimentalAtomicApi::class)
fun removeHistory(keyword: String?): Int {
    val kw = keyword?.trim()
    if (kw.isNullOrEmpty()) return 0
    if (OhosApp.started.load() == 0) return 0
    var ok = false
    runBlocking { ok = OhosApp.store.removeSearchHistory(kw) }
    if (ok) refreshHistory()
    return if (ok) 1 else 0
}

@CName("dengy_clear_history")
@OptIn(ExperimentalAtomicApi::class)
fun clearHistory(): Int {
    if (OhosApp.started.load() == 0) return 0
    var ok = false
    runBlocking { ok = OhosApp.store.clearSearchHistory() }
    if (ok) refreshHistory()
    return if (ok) 1 else 0
}

@CName("dengy_task_running")
@OptIn(ExperimentalAtomicApi::class)
fun taskRunning(): Int = slotRunning.load()

@CName("dengy_task_result")
@OptIn(ExperimentalAtomicApi::class)
fun taskResult(): String? = currentSlot()

/** 独立于任务槽的装配入口（ArkTS 启动时调用） */
@CName("dengy_app_init")
fun appInit(dataDir: String?): Int = OhosApp.init(dataDir ?: "")
