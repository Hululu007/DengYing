package com.dengying.player.data.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * 连接复用契约 —— **真机实测**，用来锁住 `HttpClient` 的网络行为。
 *
 * ## 这条契约怎么来的（一次被推翻的假设）
 *
 * 曾认为 `HttpClient.send` 的 `finally` 里无条件 `conn.disconnect()` 会让
 * keep-alive 池失效、每请求重付 TCP/TLS 握手——海报与 JSON 几乎全来自少数几个
 * 主机，这看起来正是收益最大的优化点。据此加了 `drained` 判定来"启用复用"。
 *
 * **真机实测推翻了它**：Android 16（SDK 36，V2419A）连续 3 次 GET，
 * 服务端只 `accept` **1** 次，与 JDK 18 完全一致。机制是 `stream.use { }`
 * 关闭流那一刻连接**已经还回 keep-alive 池**，之后再 `disconnect()`
 * 拿不回池里的连接，是空操作。JDK 文档 "may close the underlying socket if a
 * persistent connection is otherwise **idle** at that time" 说的是另一种情况：
 * 流没读完、连接还挂在当前请求名下。
 *
 * 结论：**「删掉 disconnect 以启用连接复用」是伪优化**，代码变复杂、收益为零，
 * 已回退。保留无条件 `disconnect()` 的唯一理由在用例 3：响应体没读干净时
 * （3xx 无 errorStream），它正好丢弃这条带残渣的 socket。
 *
 * ## 判据
 *
 * 本地起一个最小 HTTP/1.1 keep-alive 服务端，统计 `accept()` 次数。
 * 同一个服务端被请求 N 次，`accept()` 次数就是**真实 TCP 连接数**。
 *
 * 为什么不用"客户端源端口去重"：Android 的临时端口在回环上会复用，
 * 多连接可能算出同一个端口，判据会失真。
 *
 * ## 用例构成
 *
 * 1. `newImplementationReusesConnection` —— 生产代码 3 次 GET，断言只 `accept` 1 次
 * 2. `unconditionalDisconnectAfterDrainedBodyIsNoOp` —— **对照事实**：旧写法
 *    （无条件 disconnect）同样是 `accept` 1 次。这是本契约的核心知识
 * 3. `unreadableRedirectBodyDiscardsSocket` —— 302 读不干净必须丢弃 socket
 *
 * ## 为什么 JVM 侧还有一份
 *
 * 两端的 `HttpURLConnection` 不是同一份实现（JVM 是 `sun.net.www.protocol.http`，
 * Android 是 `com.android.okhttp.internal.huc`），复用行为不能互相代言，
 * 两端都要各测一次。JVM 版：`test/.../HttpClientConnectionReuseTest`。
 */
@RunWith(AndroidJUnit4::class)
class HttpClientConnectionReuseDeviceTest {

    private lateinit var server: KeepAliveServer

    @Before
    fun setUp() {
        server = KeepAliveServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    /**
     * 生产代码：3 次 GET 同一主机，应只建立 1 条 TCP 连接。
     *
     * 若有人把 keep-alive 弄坏（真去掐 socket、或每次新建连接绕过系统池），
     * 这里会变成 3。
     */
    @Test
    fun newImplementationReusesConnection() = runBlocking {
        repeat(3) {
            val r = HttpClient.get("${server.base}/json")
            assertEquals("请求应成功", 200, r.status)
            assertTrue("应拿到响应体", r.body.contains("\"ok\":true"))
        }

        assertEquals("3 次请求应真的到达服务端", 3, server.hits.get())
        assertEquals(
            "3 次 GET 应复用同一条 TCP 连接（服务端只 accept 一次）；" +
                "accept 多次说明连接池失效",
            1,
            server.connectionCount(),
        )
    }

    /**
     * **对照事实**（真机）：旧写法（无条件 `disconnect()`）跑 3 次，服务端
     * **同样**只 accept 1 次。
     *
     * 这条原先断言"必须 accept 3"，用来给上一个用例提供可信度；Android 16 真机
     * 实测推翻了它——与 JDK 18 行为一致。这个"推翻"本身就是结论：
     * **响应体读干净后 `disconnect()` 是空操作，删掉它不产生任何性能收益**。
     *
     * 留在真机侧的价值：JVM 与 Android 的 `HttpURLConnection` 实现不同
     * （Android 走 OkHttp 内核），复用行为不能互相代言，两端都要各测一次。
     */
    @Test
    fun unconditionalDisconnectAfterDrainedBodyIsNoOp() {
        repeat(3) {
            val conn = URL("${server.base}/json").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                assertEquals(200, conn.responseCode)
                conn.inputStream.use { it.readBytes() }
            } finally {
                // 这就是生产代码的写法：读干净后无条件 disconnect
                conn.disconnect()
            }
        }

        assertEquals("对照：3 次请求到达服务端", 3, server.hits.get())
        assertEquals(
            "对照：真机上响应体读干净后 disconnect() 同样是空操作，" +
                "连接已回池，故仍只 accept 1 次。若这里变成 3，说明真机连接池" +
                "行为与 JVM 不同，「删掉 disconnect 以启用复用」需重新评估",
            1,
            server.connectionCount(),
        )
    }

    /**
     * 毒连接防护（两端行为一致，故 JVM 单测已覆盖，这里再在真机确认一次）。
     *
     * 302 带 `Content-Length: 100` 但只写 10 字节：302 不属于 4xx/5xx，
     * `getErrorStream()` 返回 null，客户端拿不到任何流，剩下 90 字节永远
     * 留在内核缓冲区。这条连接必须丢弃，否则下个请求先读到垃圾。
     */
    @Test
    fun unreadableRedirectBodyDiscardsSocket() = runBlocking {
        val r302 = HttpClient.get("${server.base}/redirect")
        assertEquals("3xx 按失败口径返回（不跟随重定向）", 302, r302.status)

        val r200 = HttpClient.get("${server.base}/json")
        assertEquals("毒连接不应污染后续请求", 200, r200.status)
        assertTrue(r200.body.contains("\"ok\":true"))

        assertEquals(
            "302 响应体读不干净，该 socket 必须丢弃；故 302 与 200 应各占一条连接",
            2,
            server.connectionCount(),
        )
    }

    // ---------------------------------------------------------------- 测试服务端

    /**
     * 最小 HTTP/1.1 keep-alive 服务端。
     *
     * 只做三件事：接受连接、累计 `accept()` 次数、回定长响应。
     * `accept()` 次数就是**真实 TCP 连接数**——本测试唯一的判据来源。
     */
    private class KeepAliveServer {

        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

        /** `accept()` 返回次数 = 真实 TCP 连接数 */
        private val accepted = AtomicInteger(0)

        /** 请求命中次数（确认请求真的到达服务端，而不是被缓存/短路） */
        val hits = AtomicInteger(0)

        @Volatile
        private var running = true

        private val workers = CopyOnWriteArrayList<Thread>()

        val base: String get() = "http://127.0.0.1:${server.localPort}"

        fun connectionCount(): Int = accepted.get()

        fun start() {
            val acceptor = Thread {
                while (running) {
                    val socket = try {
                        server.accept()
                    } catch (e: IOException) {
                        break // server 被 close()，正常退出
                    }
                    accepted.incrementAndGet()
                    val worker = Thread { serve(socket) }
                    worker.isDaemon = true
                    workers.add(worker)
                    worker.start()
                }
            }
            acceptor.isDaemon = true
            acceptor.start()
        }

        fun stop() {
            running = false
            try {
                server.close()
            } catch (e: Exception) {
                // 忽略
            }
        }

        /** 在**同一条 socket** 上循环处理请求——这就是 keep-alive */
        private fun serve(socket: Socket) {
            try {
                socket.use { sock ->
                    sock.soTimeout = 5000
                    val input = sock.getInputStream().buffered()
                    val output = sock.getOutputStream()
                    while (running) {
                        val requestLine = readLine(input) ?: break
                        if (requestLine.isEmpty()) continue
                        val path = requestLine.split(' ').getOrNull(1) ?: break
                        // 读掉请求头（测试全是 GET，无请求体）
                        while (true) {
                            val header = readLine(input) ?: return
                            if (header.isEmpty()) break
                        }
                        hits.incrementAndGet()
                        when (path) {
                            "/json" -> respond(output, 200, """{"ok":true,"list":[1,2,3]}""")
                            // 302 且 CL=100 只写 10 字节 —— 响应体读不干净的构造
                            "/redirect" -> {
                                output.write(
                                    (
                                        "HTTP/1.1 302 Found\r\n" +
                                            "Location: /json\r\n" +
                                            "Content-Length: 100\r\n" +
                                            "Connection: keep-alive\r\n\r\n" +
                                            "0123456789"
                                        ).toByteArray(Charsets.UTF_8),
                                )
                                output.flush()
                            }
                            else -> respond(output, 404, "{}")
                        }
                    }
                }
            } catch (e: Exception) {
                // 客户端断开 / 超时：该连接结束，不影响断言
            }
        }

        private fun respond(output: OutputStream, code: Int, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val reason = if (code == 200) "OK" else "Error"
            val head = "HTTP/1.1 $code $reason\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: keep-alive\r\n\r\n"
            output.write(head.toByteArray(Charsets.UTF_8))
            output.write(bytes)
            output.flush()
        }

        private fun readLine(input: InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val c = input.read()
                if (c == -1) return if (sb.isEmpty()) null else sb.toString()
                if (c == '\n'.code) return sb.toString().trimEnd('\r')
                sb.append(c.toChar())
            }
        }
    }
}
