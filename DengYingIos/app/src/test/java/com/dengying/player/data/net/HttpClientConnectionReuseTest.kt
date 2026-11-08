package com.dengying.player.data.net

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * 连接复用契约 —— 用**真实 TCP 连接数**锁住 `HttpClient` 的网络行为。
 *
 * ## 这些用例要挡住什么
 *
 * 一度以为 `HttpClient.send` 的 `finally` 里无条件 `conn.disconnect()` 会让
 * keep-alive 池失效、每个请求重付一次 TCP/TLS 握手，并据此加了 `drained`
 * 判定来"启用复用"。**实测推翻了该假设**：
 *
 *  - JDK 18：3 次连续 GET → 服务端只 `accept` **1** 次
 *  - Android 16（SDK 36）：同样只 `accept` **1** 次
 *
 * 机制：`stream.use { }` 关闭流那一刻，连接**已经还回 keep-alive 池**；
 * 之后再 `disconnect()` 拿不回池里的连接，是空操作。JDK 文档的措辞
 * "may close the underlying socket if a persistent connection is otherwise
 * **idle** at that time" 指的是另一种情况——流没读完、连接还挂在当前请求名下。
 *
 * 于是这些用例的定位从"证明改动有效"变成**回归防护**，各守一条：
 *
 *  1. 正常 2xx：连接复用（accept 1）——防止有人真的把池弄坏
 *  2. 对照：无条件 disconnect 读完后仍是 accept 1——**这是本条契约的核心事实**，
 *     防止后人重走"删掉 disconnect 是性能优化"的弯路
 *  3. 302 无 errorStream：响应体读不干净 → 必须丢弃 socket，不能污染后续请求
 *  4. 404 有正文：读干净 → 连接可以回池
 *
 * 用例 3 是唯一**真正依赖 disconnect()** 的场景，也是保留无条件 disconnect 的理由。
 *
 * ## 为什么手写 ServerSocket 而不是 com.sun.net.httpserver
 *
 * AGP 的单元测试跑在 `mockable-android.jar` 上，bootclasspath 里**没有**
 * `com.sun.net.httpserver`（实测 `Unresolved reference: sun`）。而这条契约
 * 恰恰必须验真实 socket，不能被 mock 掉。故这里用裸 `ServerSocket` 手写一个
 * 最小 HTTP/1.1 keep-alive 服务端：解析请求行、读掉头部、回定长响应。
 *
 * 同样的结构另有一份真机版：`androidTest/.../HttpClientConnectionReuseDeviceTest`。
 * 两处都要跑——JVM 的 `HttpURLConnection` 与 Android 的实现（OkHttp 内核）
 * 不是同一份代码，复用行为不能互相代言。
 */
class HttpClientConnectionReuseTest {

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

    // ---------------------------------------------------------------- 用例

    /**
     * 核心契约：连续 3 次 GET 同一主机，服务端只应看到 **1 条** TCP 连接。
     *
     * 若有人把 keep-alive 弄坏（真去掐 socket、或每次都新建 `HttpClient`
     * 实例绕过系统连接池），这里会变成 3。
     */
    @Test
    fun repeatedGetReusesSingleConnection() = runBlocking {
        repeat(3) {
            val r = HttpClient.get("${server.base}/json")
            assertEquals("请求应成功", 200, r.status)
            assertTrue("应拿到响应体", r.body.contains("\"ok\":true"))
        }

        assertEquals("3 次请求应真的到达服务端", 3, server.hits.get())
        assertEquals(
            "3 次 GET 应复用同一条 TCP 连接（服务端只 accept 一次）；" +
                "出现多个说明连接池失效",
            1,
            server.connectionCount(),
        )
    }

    /**
     * **对照事实**：旧写法（无条件 `disconnect()`）跑 3 次，服务端**同样**只
     * accept 1 次。
     *
     * 这条曾经断言"必须 accept 3"，用来给上一个用例提供可信度；实测后
     * 断言被推翻——而推翻本身才是要记录的知识：**读完后 `disconnect()`
     * 是空操作，删掉它不产生任何性能收益**。留这条用例，下次谁再想"优化"
     * 连接复用，先看这里。
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
            "对照：响应体读干净后 disconnect() 是空操作，连接已回池，" +
                "故仍只 accept 1 次。若这里变成 3，说明连接池行为变了，" +
                "「删掉 disconnect 以启用复用」的伪优化就该重新评估",
            1,
            server.connectionCount(),
        )
    }

    /**
     * 毒连接防护：302 无响应体 → `errorStream` 为 null → 响应体读不干净
     * → 必须丢弃该 socket，不能留在池里。
     *
     * 这是**唯一真正依赖 `disconnect()`** 的场景。断言两层：
     *  1. 下一条 200 请求仍然成功（毒连接没有污染后续请求）
     *  2. 302 与 200 各占一条连接（accept 2 次）——证明确实 discard 了
     *
     * 若有人把 `disconnect()` 整个删掉，这里会先撞 stale 再重试，或直接读到残渣。
     */
    @Test
    fun unreadableRedirectBodyDiscardsSocketInsteadOfPoisoningPool() = runBlocking {
        val r302 = HttpClient.get("${server.base}/redirect")
        assertEquals("3xx 按失败口径返回（不跟随重定向）", 302, r302.status)

        val r200 = HttpClient.get("${server.base}/json")
        assertEquals("毒连接不应污染后续请求", 200, r200.status)
        assertTrue(r200.body.contains("\"ok\":true"))

        assertEquals(
            "302 的响应体读不干净，该 socket 必须丢弃；" +
                "故 302 与 200 应各占一条连接（accept 2 次）",
            2,
            server.connectionCount(),
        )
    }

    /**
     * 404 带响应体：`errorStream` 可读 → 读干净 → 连接**可以**回池复用。
     *
     * 这条把"非 2xx 一律丢弃"的错误简化挡住：只要正文读完了，socket 就是干净的。
     * 同时验证错误页正文确实读到了——这是区分「网关劫持页」与「站点改版」的依据。
     */
    @Test
    fun errorResponseWithReadableBodyStillReusesConnection() = runBlocking {
        val r1 = HttpClient.get("${server.base}/missing")
        assertEquals(404, r1.status)
        assertTrue("应读到错误页正文（用于区分网关劫持页与站点改版）", r1.body.contains("nope"))

        val r2 = HttpClient.get("${server.base}/json")
        assertEquals(200, r2.status)

        assertEquals(
            "404 正文已读干净，连接应回池复用（accept 1 次）",
            1,
            server.connectionCount(),
        )
    }

    // ---------------------------------------------------------------- 测试服务端

    /**
     * 最小 HTTP/1.1 keep-alive 服务端。
     *
     * 只做三件事：接受连接、累计 `accept()` 次数、回定长响应。
     * `accept()` 次数就是**真实 TCP 连接数**——这是本测试唯一的判据来源。
     */
    private class KeepAliveServer {

        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

        /**
         * `accept()` 返回次数 —— **真实 TCP 连接数**。
         *
         * 一开始用「客户端源端口去重」当判据，实测不可靠：Windows 的临时端口
         * 分配会复用同一端口（循环回环 + 连接快速关闭时尤其明显），于是
         * 3 条连接被算成 1 条，判据直接失效。`accept()` 次数没有这个歧义。
         */
        private val accepted = AtomicInteger(0)

        /** 服务端见过的客户端源端口集合——仅作诊断用，不作断言判据 */
        private val clientPorts = ConcurrentHashMap.newKeySet<Int>()

        /** 请求命中次数（确认请求真的到达了服务端，而不是被缓存/短路） */
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
                    clientPorts.add(socket.port)
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
                        // 读掉请求头（本项目测试全是 GET，无请求体）
                        while (true) {
                            val header = readLine(input) ?: return
                            if (header.isEmpty()) break
                        }
                        hits.incrementAndGet()
                        when (path) {
                            "/json" -> respond(output, 200, """{"ok":true,"list":[1,2,3]}""")
                            "/missing" -> respond(output, 404, """{"err":"nope"}""")
                            // 302 且 Content-Length: 100，但只写 10 字节 —— 这才是
                            // 真正"响应体读不干净"的构造。302 不属于 4xx/5xx，JDK 的
                            // getErrorStream() 返回 null，客户端**拿不到任何流**，
                            // 于是那 100 字节里的 90 字节永远留在内核缓冲区。
                            // 这条连接若不丢弃，下一个请求会先读到这 90 字节垃圾。
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
