package com.dengying.player.data.download

import com.dengying.player.data.net.HttpClient
import com.dengying.player.data.net.NetPriority
import com.dengying.player.platform.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MemoryKv : com.dengying.player.platform.KeyValueStore {
    private val data = HashMap<String, String>()
    override fun getString(key: String, defValue: String): String = data[key] ?: defValue
    override fun allKeys(): Set<String> = data.keys.toSet()
    override fun putString(key: String, value: String, sync: Boolean): Boolean {
        data[key] = value
        return true
    }
    override fun remove(key: String, sync: Boolean): Boolean {
        data.remove(key)
        return true
    }
}

/** 下载提速的行为测试：用 fake 传输/存储替身，不依赖真实网络。 */
class DownloadCenterSpeedupTest {
    private lateinit var kv: MemoryKv
    private val originalScope = DownloadCenter.scope
    private val originalStreaming = HttpClient.streamingForTest
    private val originalBinary = HttpClient.binaryForTest
    private val originalText = HttpClient.textForTest
    private val originalProgress = DownloadCenter.onProgress
    private val originalDelay = DownloadCenter.segmentDelayMs
    private val originalFail = DownloadCenter.failSegmentAt

    @BeforeTest
    fun setUp() {
        DownloadStorage.init(System.getProperty("java.io.tmpdir") + "/dengy-download-test")
        DownloadStorage.deleteAll()
        kv = MemoryKv()
        DownloadCenter.init(kv)
        DownloadCenter.scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        DownloadCenter.segmentDelayMs = 0
        DownloadCenter.failSegmentAt = 0
    }

    @AfterTest
    fun tearDown() {
        DownloadCenter.clearAll()
        DownloadStorage.deleteAll()
        DownloadCenter.scope = originalScope
        HttpClient.streamingForTest = originalStreaming
        HttpClient.binaryForTest = originalBinary
        HttpClient.textForTest = originalText
        DownloadCenter.onProgress = originalProgress
        DownloadCenter.segmentDelayMs = originalDelay
        DownloadCenter.failSegmentAt = originalFail
    }

    private fun idOfReq(id: Int): String =
        "v${id.toLong()}_test_$id"

    private fun req(id: Int, url: String) = DownloadCenter.Request(
        vodId = id.toLong(), source = "test", vodName = "n", vodPic = "",
        episodeIndex = id, episodeName = "e", url = url,
    )

    private fun fakeStreaming(mp4: ByteArray = MP4) {
        HttpClient.streamingForTest = { _, _, _, _, onChunk ->
            assertTrue(currentCoroutineContext()[NetPriority]?.background == true,
                "download requests must be background")
            onChunk(mp4)
            HttpClient.HttpBinaryResult(200, ByteArray(0), 0, mp4.size.toLong())
        }
    }

    private fun awaitStatus(id: String, want: DownloadCenter.Status) {
        val deadline = System.currentTimeMillis() + 3000
        var last: DownloadCenter.Status? = null
        while (System.currentTimeMillis() < deadline) {
            val item = DownloadCenter.snapshot().firstOrNull { it.id == id }
            last = item?.status
            if (item?.status == want) return
            Thread.sleep(10)
        }
        val all = DownloadCenter.snapshot().map { "${it.id}:${it.status}:${it.error}" }
        assertEquals(want, last, "items=$all")
    }

    @Test
    fun hlsSegmentsNeverExceedThreeConcurrent() {
        var inFlight = 0
        var peak = 0
        HttpClient.textForTest = { _, _, _, _ -> HttpClient.HttpResult(200, M3U8, 0) }
        DownloadCenter.segmentDelayMs = 80
        HttpClient.streamingForTest = { _, _, _, _, onChunk ->
            val now = ++inFlight
            synchronized(this) { peak = maxOf(peak, now) }
            delay(80)
            synchronized(this) { inFlight-- }
            onChunk(MP4)
            HttpClient.HttpBinaryResult(200, ByteArray(0), 0, MP4.size.toLong())
        }
        DownloadCenter.enqueue(req(1, "https://e.test/a.m3u8"))
        awaitStatus(idOfReq(1), DownloadCenter.Status.DONE)
        assertTrue(peak <= 3, "peak=$peak must be <=3")
        assertTrue(peak >= 2, "expected parallel segments, peak=$peak")
    }

    @Test
    fun secondJobStaysQueued() {
        var releaseFirst = false
        HttpClient.textForTest = { _, _, _, _ -> HttpClient.HttpResult(200, M3U8, 0) }
        HttpClient.streamingForTest = { _, _, _, _, onChunk ->
            while (!releaseFirst) delay(10)
            onChunk(MP4)
            HttpClient.HttpBinaryResult(200, ByteArray(0), 0, MP4.size.toLong())
        }
        DownloadCenter.enqueue(req(1, "https://e.test/a.m3u8"))
        kotlinx.coroutines.runBlocking { delay(100) }
        DownloadCenter.enqueue(req(2, "https://e.test/b.m3u8"))
        kotlinx.coroutines.runBlocking { delay(100) }
        val states = DownloadCenter.snapshot().map { it.status }
        assertTrue(states.contains(DownloadCenter.Status.QUEUED), "second job must stay QUEUED: $states")
        releaseFirst = true
        awaitStatus(idOfReq(2), DownloadCenter.Status.DONE)
    }

    @Test
    fun singleSegmentFailureCleansTaskDirectory() {
        HttpClient.textForTest = { _, _, _, _ -> HttpClient.HttpResult(200, M3U8, 0) }
        DownloadCenter.failSegmentAt = 1
        fakeStreaming()
        DownloadCenter.enqueue(req(1, "https://e.test/a.m3u8"))
        awaitStatus(idOfReq(1), DownloadCenter.Status.FAILED)
        val item = DownloadCenter.snapshot().first()
        assertEquals(DownloadCenter.Status.FAILED, item.status)
        assertFalse(DownloadStorage.exists("1/index.m3u8"))
    }

    @Test
    fun htmlFirstChunkIsRejected() {
        val html = "<html><body>x</body></html>".encodeToByteArray()
        fakeStreaming(html)
        DownloadCenter.enqueue(req(1, "https://e.test/movie.mp4"))
        awaitStatus(idOfReq(1), DownloadCenter.Status.FAILED)
        assertTrue(DownloadCenter.snapshot().first().error.contains("网页") || DownloadCenter.snapshot().first().error.contains("下载失败"), DownloadCenter.snapshot().first().error)
    }

    @Test
    fun unknownLengthStillReportsByteProgress() {
        val progresses = mutableListOf<Float>()
        var lastBytes = 0L
        DownloadCenter.onProgress = { _, p -> synchronized(progresses) { progresses.add(p) } }
        HttpClient.streamingForTest = { _, _, _, _, onChunk ->
            repeat(20) {
                onChunk(MP4)
                lastBytes += MP4.size
                delay(5)
            }
            HttpClient.HttpBinaryResult(200, ByteArray(0), 0, -1)
        }
        DownloadCenter.enqueue(req(1, "https://e.test/movie.mp4"))
        awaitStatus(idOfReq(1), DownloadCenter.Status.DONE)
        assertEquals(DownloadCenter.Status.DONE, DownloadCenter.snapshot().first().status)
        assertTrue(progresses.all { it <= 0.99f }, "progress must <=0.99 before done: $progresses")
        assertEquals(1f, DownloadCenter.snapshot().first().progress)
        assertTrue(lastBytes > MP4.size)
    }

    @Test
    fun storageRejectsTraversal() {
        assertFalse(DownloadStorage.writeBytes("../evil", MP4))
        assertFalse(DownloadStorage.appendBytes("/abs", MP4))
        assertFalse(DownloadStorage.rename("../a", "b"))
    }

    companion object {
        val MP4 = byteArrayOf(0, 0, 0, 0x18) + "ftypisom".encodeToByteArray() + ByteArray(32)
        const val M3U8 = "#EXTM3U\n#EXT-X-TARGETDURATION:10\nseg0.ts\nseg1.ts\nseg2.ts\nseg3.ts\nseg4.ts\n"
    }
}
