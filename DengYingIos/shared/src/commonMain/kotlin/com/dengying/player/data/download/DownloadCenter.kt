package com.dengying.player.data.download

import com.dengying.player.data.json.JsonX
import com.dengying.player.data.net.HlsUtil
import com.dengying.player.data.net.HttpClient
import com.dengying.player.domain.playback.HlsVariants
import com.dengying.player.platform.Clock
import com.dengying.player.platform.KeyValueStore
import com.dengying.player.platform.PlatformLock
import com.dengying.player.platform.ioDispatcher
import com.dengying.player.player.PlaybackProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.coroutineContext

/**
 * 离线下载中心 —— 独立于播放缓存 `MediaCache`。
 *
 * 任务元数据持久化在 [KeyValueStore]，文件落在 [DownloadStorage]。
 * 下载走统一 [HttpClient]，HLS 会解析主列表、逐片下载并生成本地 m3u8；
 * 直链文件按原字节保存。
 *
 * 这一步先提供应用进程内下载。进程被杀会中断，重启后任务标记为失败，
 * 不做断点续传；用户可重新下载。
 */
object DownloadCenter {

    const val KEY_ITEMS = "downloads_v1"

    enum class Status { QUEUED, RUNNING, DONE, FAILED }

    data class Request(
        val vodId: Long,
        val source: String,
        val vodName: String,
        val vodPic: String,
        val episodeIndex: Int,
        val episodeName: String,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    )

    data class Item(
        val id: String,
        val vodId: Long,
        val source: String,
        val vodName: String,
        val vodPic: String,
        val episodeIndex: Int,
        val episodeName: String,
        val status: Status,
        val progress: Float,
        val bytes: Long,
        val localPath: String,
        val error: String,
        val createdAt: Long,
    ) {
        val canPlay: Boolean get() = status == Status.DONE && localPath.isNotEmpty()
    }

    private val lock = PlatformLock()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val items = ArrayList<Item>()
    private val jobs = HashMap<String, Job>()
    private var store: KeyValueStore? = null
    private var loaded = false

    fun init(kv: KeyValueStore) {
        lock.withLock {
            store = kv
            if (loaded) return@withLock
            loaded = true
            items.clear()
            items.addAll(loadLocked(kv))
            // 上次进程被杀时留下的 RUNNING 状态没有真实任务在跑。
            var changed = false
            for (i in items.indices) {
                if (items[i].status == Status.RUNNING || items[i].status == Status.QUEUED) {
                    items[i] = items[i].copy(
                        status = Status.FAILED,
                        error = "下载中断，请重新下载",
                    )
                    changed = true
                }
            }
            if (changed) persistLocked()
        }
    }

    fun isAvailable(): Boolean = DownloadStorage.supported && DownloadStorage.isReady()

    fun snapshot(): List<Item> = lock.withLock { items.toList() }

    fun itemFor(source: String, vodId: Long, episodeIndex: Int): Item? = lock.withLock {
        items.firstOrNull {
            it.source == source && it.vodId == vodId && it.episodeIndex == episodeIndex
        }
    }

    fun totalBytes(): Long = lock.withLock { items.sumOf { it.bytes } }

    fun localPlayUrl(item: Item): String =
        if (item.canPlay) DownloadStorage.fileUrl(item.localPath) else ""

    /**
     * 入队。同一影片、同一源、同一集只保留一个任务；已完成或下载中返回 false。
     */
    fun enqueue(req: Request): Boolean {
        if (!isAvailable()) return false
        if (req.url.isEmpty() || !req.url.contains("://")) return false
        val id = idOf(req)
        val fresh = Item(
            id = id,
            vodId = req.vodId,
            source = req.source,
            vodName = req.vodName,
            vodPic = req.vodPic,
            episodeIndex = req.episodeIndex,
            episodeName = req.episodeName,
            status = Status.QUEUED,
            progress = 0f,
            bytes = 0L,
            localPath = "",
            error = "",
            createdAt = Clock.nowMs(),
        )
        var accepted = false
        lock.withLock {
            val old = items.firstOrNull { it.id == id }
            if (old != null) {
                if (old.status == Status.QUEUED || old.status == Status.RUNNING ||
                    old.status == Status.DONE
                ) {
                    return@withLock
                }
                DownloadStorage.deleteTree(old.id)
                items.removeAll { it.id == id }
            }
            items.add(fresh)
            persistLocked()
            val job = scope.launch { runJob(id, req) }
            jobs[id] = job
            accepted = true
        }
        if (!accepted) return false
        PlaybackProbe.note("download-enqueue", "id=$id ep=${req.episodeIndex}")
        return true
    }

    fun remove(id: String): Boolean {
        val removed = lock.withLock {
            jobs.remove(id)?.cancel()
            val ok = items.removeAll { it.id == id }
            if (!ok) return@withLock false
            persistLocked()
            true
        }
        if (!removed) return false
        DownloadStorage.deleteTree(safeDownloadDir(id))
        PlaybackProbe.note("download-remove", "id=$id")
        return true
    }

    fun clearAll(): Boolean {
        lock.withLock {
            for (job in jobs.values) job.cancel()
            jobs.clear()
            items.clear()
            persistLocked()
        }
        val ok = DownloadStorage.deleteAll()
        PlaybackProbe.note("download-clear", "ok=$ok")
        return ok
    }

    /**
     * 下载内容是否其实是网页。
     *
     * token 源换链失败时返回站点播放页：HTTP 200、体积不为空，落在 `.mp4`
     * 里就是 HTML。离线播放会报 ExoPlayer 3003，而下载列表仍显示已完成。
     * 只看前 64 字节的 HTML 特征，避免引入 MIME 嗅探依赖。
     */
    internal fun looksLikeHtmlPage(bytes: ByteArray): Boolean {
        // 站点播放页不一定以 <!doctype 开头：优酷转码页是 `<script>sessionStorage…`，
        // 只看 doctype/html 会把 1537 字节的页面当视频存下来（真机已抓到）。
        val head = bytes.decodeToString(0, minOf(bytes.size, 256)).lowercase()
        return head.contains("<!doctype") ||
            head.contains("<html") ||
            head.contains("<script") ||
            head.trimStart().startsWith("<?xml")
    }

    private suspend fun runJob(id: String, req: Request) {
        update(id) { it.copy(status = Status.RUNNING, progress = 0f, error = "") }
        try {
            val result = if (HlsUtil.isM3u8(req.url) || req.url.contains(".m3u8", ignoreCase = true)) {
                downloadHls(id, req)
            } else {
                downloadFile(id, req)
            }
            if (result.relPath.isEmpty()) error("下载结果为空")
            update(id) {
                it.copy(
                    status = Status.DONE,
                    progress = 1f,
                    bytes = result.bytes,
                    localPath = result.relPath,
                    error = "",
                )
            }
            PlaybackProbe.note("download-done", "id=$id bytes=${result.bytes}")
        } catch (e: CancellationException) {
            PlaybackProbe.note("download-cancel", "id=$id")
            coroutineContext.ensureActive()
            throw e
        } catch (e: Exception) {
            val message = friendlyError(e)
            update(id) {
                it.copy(status = Status.FAILED, progress = 0f, error = message)
            }
            PlaybackProbe.note("download-failed", "id=$id reason=$message")
        } finally {
            lock.withLock { jobs.remove(id) }
        }
    }

    private data class Result(val relPath: String, val bytes: Long)

    private suspend fun downloadFile(id: String, req: Request): Result {
        val bytes = HttpClient.getBinary(
            req.url,
            req.headers,
            connectMs = 12000,
            readMs = 120000,
        )
        if (bytes.status !in 200..299 || bytes.bytes.isEmpty()) {
            error("文件请求失败（${bytes.status}）")
        }
        // 【别把网页当视频存下来】token 源换链失败时会返回站点播放页，HTTP 是
        // 200、体积也不小，直接落盘就是 195KB 的 HTML；真机离线播放表现为
        // ExoPlayer 3003 Source error，且下载列表还显示「已下载」。这里按内容
        // 头判一次，宁可失败让用户换源，也不要产出播不了的假文件。
        if (looksLikeHtmlPage(bytes.bytes)) error("下载到的是网页而不是视频，请换源重试")
        val ext = extensionOf(req.url, "mp4")
        val rel = "${safeDownloadDir(id)}/video.$ext"
        if (!DownloadStorage.writeBytes(rel, bytes.bytes)) error("写入下载文件失败")
        update(id) { it.copy(progress = 0.9f, bytes = bytes.bytes.size.toLong()) }
        return Result(rel, bytes.bytes.size.toLong())
    }

    private suspend fun downloadHls(id: String, req: Request): Result {
        var playlistUrl = req.url
        var text = fetchText(playlistUrl, req.headers)
        if (text.contains("#EXT-X-STREAM-INF")) {
            val variants = HlsVariants.parseVariants(text, playlistUrl)
            val best = HlsVariants.pickDefault(variants)
            if (best != null) {
                playlistUrl = best.url
                text = fetchText(playlistUrl, req.headers)
            }
        }
        if (!text.contains("#EXTM3U")) error("播放列表为空或格式不支持")

        val plan = HlsOffline.plan(text, playlistUrl)
        if (plan.resources.isEmpty()) error("播放列表里没有可下载的分片")
        val dir = safeDownloadDir(id)
        var doneBytes = 0L
        var doneCount = 0
        for (res in plan.resources) {
            coroutineContext.ensureActive()
            val bytes = HttpClient.getBinary(
                res.url,
                req.headers,
                connectMs = 12000,
                readMs = 60000,
            )
            if (bytes.status !in 200..299 || bytes.bytes.isEmpty()) {
                error("分片下载失败（${bytes.status}）")
            }
            if (!DownloadStorage.writeBytes("$dir/${res.localName}", bytes.bytes)) {
                error("写入分片失败")
            }
            doneCount++
            doneBytes += bytes.bytes.size
            val p = (doneCount.toFloat() / plan.resources.size.toFloat()) * 0.95f
            update(id) { it.copy(progress = p, bytes = doneBytes) }
        }
        val playlistRel = "$dir/index.m3u8"
        if (!DownloadStorage.writeText(playlistRel, plan.localPlaylist)) {
            error("写入本地播放列表失败")
        }
        return Result(playlistRel, doneBytes)
    }

    private suspend fun fetchText(url: String, headers: Map<String, String>): String {
        val r = HttpClient.get(url, headers, connectMs = 12000, readMs = 20000)
        if (r.status !in 200..299 || r.body.isEmpty()) error("播放列表请求失败（${r.status}）")
        return r.body
    }

    private fun update(id: String, block: (Item) -> Item) {
        lock.withLock {
            val idx = items.indexOfFirst { it.id == id }
            if (idx < 0) return@withLock
            items[idx] = block(items[idx])
            persistLocked()
        }
    }

    private fun friendlyError(e: Exception): String {
        val raw = e.message ?: ""
        return when {
            raw.contains("播放列表") -> raw
            raw.contains("分片") -> raw
            raw.contains("文件请求") -> raw
            raw.contains("写入") -> raw
            else -> "下载失败，请稍后重试"
        }
    }

    private fun idOf(req: Request): String =
        "v${req.vodId}_${safeDownloadDir(req.source)}_${req.episodeIndex}"

    private fun persistLocked() {
        val kv = store ?: return
        val arr = JsonX.buildArray(items.map { it.toJson() })
        kv.putString(KEY_ITEMS, arr.toString(), sync = true)
    }

    private fun loadLocked(kv: KeyValueStore): List<Item> {
        val raw = kv.getString(KEY_ITEMS, "")
        val arr = JsonX.parseArray(raw) ?: return emptyList()
        val out = ArrayList<Item>(arr.size)
        for (i in 0 until arr.size) {
            val o = JsonX.optObjectAt(arr, i) ?: continue
            val id = JsonX.str(o, "id", "")
            if (id.isEmpty()) continue
            out.add(
                Item(
                    id = id,
                    vodId = JsonX.long(o, "vodId", 0L),
                    source = JsonX.str(o, "source", ""),
                    vodName = JsonX.str(o, "vodName", ""),
                    vodPic = JsonX.str(o, "vodPic", ""),
                    episodeIndex = JsonX.int(o, "episodeIndex", 0),
                    episodeName = JsonX.str(o, "episodeName", ""),
                    status = parseStatus(JsonX.str(o, "status", "")),
                    progress = JsonX.double(o, "progress", 0.0).toFloat(),
                    bytes = JsonX.long(o, "bytes", 0L),
                    localPath = JsonX.str(o, "localPath", ""),
                    error = JsonX.str(o, "error", ""),
                    createdAt = JsonX.long(o, "createdAt", 0L),
                )
            )
        }
        return out
    }

    private fun Item.toJson(): JsonObject = JsonX.buildObject(
        "id" to id,
        "vodId" to vodId,
        "source" to source,
        "vodName" to vodName,
        "vodPic" to vodPic,
        "episodeIndex" to episodeIndex,
        "episodeName" to episodeName,
        "status" to status.name,
        "progress" to progress.toDouble(),
        "bytes" to bytes,
        "localPath" to localPath,
        "error" to error,
        "createdAt" to createdAt,
    )

    private fun parseStatus(raw: String): Status =
        Status.values().firstOrNull { it.name == raw } ?: Status.FAILED

    private fun extensionOf(url: String, fallback: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val last = clean.substringAfterLast('/', "")
        val ext = last.substringAfterLast('.', "").lowercase()
        return if (ext in setOf("mp4", "m4v", "webm", "mkv", "flv", "ts", "m4s", "aac")) {
            ext
        } else {
            fallback
        }
    }
}

/**
 * HLS 本地化：把媒体播放列表改写成同目录下的本地文件名，并给出需要下载的资源。
 *
 * 保留 `EXT-X-BYTERANGE` 等标签；同一 URL 只下载一次，分片名稳定可测。
 */
internal object HlsOffline {

    data class Resource(val url: String, val localName: String)

    data class Plan(val localPlaylist: String, val resources: List<Resource>)

    fun plan(text: String, baseUrl: String): Plan {
        val resources = ArrayList<Resource>()
        val seen = LinkedHashMap<String, String>()
        val out = StringBuilder(text.length)
        var seq = 0
        var keySeq = 0
        var mapSeq = 0

        fun localFor(url: String, fallbackExt: String, key: String = ""): String {
            seen[url]?.let { return it }
            val prefix = when (key) {
                "key" -> "key${keySeq++}"
                "map" -> "map${mapSeq++}"
                else -> "seg${seq++}"
            }
            val ext = extensionOf(url, fallbackExt)
            val name = "$prefix.$ext"
            seen[url] = name
            resources.add(Resource(url, name))
            return name
        }

        for (raw in text.split('\n')) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-KEY:") -> {
                    out.append(rewriteUriAttribute(line, baseUrl) { url -> localFor(url, "key", "key") })
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    out.append(rewriteUriAttribute(line, baseUrl) { url -> localFor(url, "mp4", "map") })
                }
                line.isEmpty() || line.startsWith("#") -> out.append(line)
                else -> {
                    val full = HlsVariants.resolveVariantUrl(baseUrl, line)
                    out.append(localFor(full, "ts"))
                }
            }
            out.append('\n')
        }
        return Plan(out.toString(), resources)
    }

    private fun rewriteUriAttribute(
        line: String,
        baseUrl: String,
        localFor: (String) -> String,
    ): String {
        val marker = "URI=\""
        val start = line.indexOf(marker)
        if (start < 0) return line
        val valueStart = start + marker.length
        val end = line.indexOf('"', valueStart)
        if (end < 0) return line
        val rawUrl = line.substring(valueStart, end)
        val full = HlsVariants.resolveVariantUrl(baseUrl, rawUrl)
        if (full.isEmpty()) return line
        return line.substring(0, valueStart) + localFor(full) + line.substring(end)
    }

    private fun extensionOf(url: String, fallback: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val last = clean.substringAfterLast('/', "")
        val ext = last.substringAfterLast('.', "").lowercase()
        return if (ext in setOf("ts", "m4s", "mp4", "m4v", "aac", "key", "webm")) ext else fallback
    }
}

