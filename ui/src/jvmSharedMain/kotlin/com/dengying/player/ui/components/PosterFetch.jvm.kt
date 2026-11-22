package com.dengying.player.ui.components

import com.dengying.player.platform.Log
import com.dengying.player.platform.ioDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 读响应体的缓冲区大小。与 `HttpClient.IO_BUFFER` 同一口径：
 * `InputStream.copyTo` 默认 8192，海报 50-200KB 要读几十次。
 */
private const val IO_BUFFER = 32 * 1024

/**
 * 预分配上限。与 `HttpClient.MAX_PRESIZE` 同一口径：
 * `Content-Length` 是 CDN 自己报的，坏值可能很大，超过就退回默认增长策略。
 */
private const val MAX_PRESIZE = 4L * 1024 * 1024

/**
 * 海报下载的 JVM 实现（`jvmSharedMain`：Android 与 JVM 共用同一份）。
 *
 * 与 `:shared` 的 `HttpTransport.jvm.kt` 是**两份独立的实现**，不是遗漏：
 * 契约不同（这里要跟随重定向、要 image 系的 Accept、要 8 秒超时），
 * 且 `HttpTransport` 明确「不跟随重定向」，无法在其之上表达。
 */
actual object PosterFetch {
    actual suspend fun fetch(url: String): ByteArray? = withContext(ioDispatcher) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36",
                )
                setRequestProperty("Accept", "image/*")
                posterReferer(url)?.let { setRequestProperty("Referer", it) }
            }
            if (conn.responseCode !in 200..299) return@withContext null
            // 预分配 + 大缓冲：海报 50-200KB，readBytes() 内部 8KB 缓冲要读几十次；
            // 且 ByteArrayOutputStream 默认 32 字节起步倍增扩容会多次复制。
            // Content-Length 由 CDN 报，超过上限就退回默认策略防坏头撑爆内存。
            val hint = try {
                conn.contentLengthLong
            } catch (e: Exception) {
                -1L
            }
            conn.inputStream.use { input ->
                val out = if (hint in 1L..MAX_PRESIZE) ByteArrayOutputStream(hint.toInt())
                else ByteArrayOutputStream()
                input.copyTo(out, IO_BUFFER)
                out.toByteArray()
            }
        } catch (e: Exception) {
            // 平台日志门面：Android actual 会把 tag 截断到 23 字符。
            Log.w("DY/Net", "poster ${url.take(100)} ${e.message ?: ""}")
            null
        } finally {
            // 无条件 disconnect：`use { }` 关闭流时连接已还回 keep-alive 池，
            // 此时 disconnect() 是空操作（实测：连续 3 张海报只建 1 条 TCP 连接）；
            // 而响应体没读干净时它正好丢弃这条带残渣的 socket。
            try {
                conn?.disconnect()
            } catch (e: Exception) {
                // 忽略
            }
        }
    }
}
