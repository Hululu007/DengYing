package com.dengying.player.data.net

import com.dengying.player.platform.Log
import com.dengying.player.platform.Clock
import com.dengying.player.player.PlaybackProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * [HttpTransport] 的 JVM / Android 共用实现（KMP P3 批 D）
 *
 * Android 的 `HttpURLConnectionImpl` 走 OkHttp 内核，JDK 走自带的实现，
 * 两者对本文件用到的 API 语义一致（连接复用、透明 gzip、`errorStream`），
 * 故同一份代码放在 `jvmSharedMain`，不为两端各写一份。
 *
 * 逐条对应 [HttpTransport.send] 的契约，其中三条最容易在改写时丢掉：
 *  - **无条件 `disconnect()`**：读干净的场景是空操作，没读干净的场景正好
 *    丢弃带残渣的 socket。删掉它并不会启用连接复用（实测见
 *    `app/src/test/.../HttpClientConnectionReuseTest.kt`），只是留下隐患。
 *  - **不设 `Accept-Encoding`**：系统栈在调用方未设置时自动加 gzip 并透明解压。
 *  - **3xx 不跟随**：`instanceFollowRedirects = false`。
 */
actual object HttpTransport {

    /**
     * 读响应体的缓冲区大小。
     *
     * 默认 `InputStream.copyTo` 用 8192；源站 JSON 常见 50-500KB，
     * 8KB 意味着几十次 read 系统调用。32KB 把次数压到原来的 1/4，
     * 又不会在小响应上浪费内存（这个 buffer 是短生命周期的）。
     */
    private const val IO_BUFFER = 32 * 1024

    /**
     * 预分配上限。
     *
     * `Content-Length` 是源站自己报的，恶意/错误的值可能很大（甚至几 GB）。
     * 超过这个值就退回默认增长策略，不让一个坏头把内存撑爆。
     * 4MB 足以覆盖本项目所有 JSON 响应（列表页实测最大约 300KB）。
     */
    private const val MAX_PRESIZE = 4L * 1024 * 1024

    actual suspend fun send(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        connectMs: Int,
        readMs: Int,
        binary: Boolean,
    ): RawResponse = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        var status = 0
        var text = ""
        var bytes = ByteArray(0)
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectMs
                readTimeout = readMs
                // 不跟随重定向：3xx 一律按失败处理（FR-113 的同一口径：
                // 源站重定向常指向验证页/广告页，跟过去只会拿到 200 的垃圾）
                instanceFollowRedirects = false
                useCaches = false
                for ((k, v) in headers) setRequestProperty(k, v)
            }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }
            }
            val tConn = Clock.nowMs()
            status = conn.responseCode
            val connectCost = Clock.nowMs() - tConn
            // 2xx 读 inputStream；其余读 errorStream——否则拿不到错误页正文，
            // 也就无法判断"网关劫持页"与"站点改版"的区别
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            var readCost = 0L
            var bodyLen = 0
            if (stream != null) {
                // 预分配：源站 JSON 常 100KB+，ByteArrayOutputStream 默认 32 字节
                // 起步、倍增扩容，等于把同一份数据复制好几遍。
                // gzip 透明解压时 contentLength=-1（长度未知），此时退回默认值。
                val hint = try {
                    conn.contentLengthLong
                } catch (e: Exception) {
                    -1L
                }
                val tRead = Clock.nowMs()
                stream.use { input ->
                    val out = if (hint in 1L..MAX_PRESIZE) ByteArrayOutputStream(hint.toInt())
                    else ByteArrayOutputStream()
                    input.copyTo(out, IO_BUFFER)
                    bodyLen = out.size()
                    if (binary) {
                        bytes = out.toByteArray()
                    } else {
                        text = out.toString(Charsets.UTF_8.name())
                    }
                }
                readCost = Clock.nowMs() - tRead
            }
            // 分段耗时探针：详情请求在 App 里比 curl 慢一倍，需要分清是
            // 「连接+首字节」慢还是「读正文」慢——两者的修法完全不同。
            if (connectCost + readCost > 300) {
                PlaybackProbe.note(
                    "net-split",
                    "conn=${connectCost}ms read=${readCost}ms len=$bodyLen url=${url.take(40)}",
                )
            }
        } catch (e: IOException) {
            status = 0
            logNetFail(method, url, e)
        } catch (e: Exception) {
            status = 0
            logNetFail(method, url, e)
        } finally {
            // 无条件 disconnect：读干净的场景是空操作（流 close 时连接已回池），
            // 没读干净的场景正好丢弃这条带残渣的 socket。见函数头说明。
            try {
                conn?.disconnect()
            } catch (e: Exception) {
                // 忽略：断开失败不影响已读到的结果
            }
        }
        RawResponse(status, text, bytes)
    }

    /**
     * 失败路径的排障日志（DY/Net）。
     *
     * 【为什么必须有】网络失败此前在 `status = 0` 处被静默吞掉，DNS / 握手 /
     * 超时不可区分——真机排障时全程无信号（2026-10-24 排「首页分类全空」时
     * logcat 一无所获，被迫盲猜）。只在**失败路径**打一行，成功路径零开销；
     * 顺带把主机当前解析到的地址一并打出：落在 VPN 的 Fake-IP 段 = 流量进了
     * 隧道，是真实 IP = 被 VPN 分流排除走了直连——一眼定位分流问题。
     */
    private fun logNetFail(method: String, url: String, e: Exception) {
        val host = try {
            URL(url).host
        } catch (e2: Exception) {
            "?"
        }
        val addrs = try {
            InetAddress.getAllByName(host).joinToString(",") { it.hostAddress ?: "?" }
        } catch (e2: Exception) {
            "dns-fail"
        }
        Log.w(
            "DY/Net",
            "$method $host ${e.javaClass.simpleName}: ${e.message ?: ""} ip=[$addrs]",
        )
    }
}
