package com.dengying.player.data.net

import com.dengying.player.data.json.JsonX
import com.dengying.player.domain.page.ConcurrencyGate
import com.dengying.player.platform.Clock
import com.dengying.player.player.PlaybackProbe
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 统一 HTTP 客户端 —— 源适配层唯一出网入口
 *
 * 设计要点：
 *  - 返回 `status`：失败不再折叠成空串，「无数据 / 请求失败 / 解析失败」可区分的前置条件
 *  - GET/POST 共用一份超时与头策略；**不做 UA 轮换、不加代理、不打散节奏**（SEC-006）
 *  - 不抛网络异常：任何异常一律 `status = 0`
 *
 * FR-105 / NFR-004：**全部出网都经全局并发闸门**（[ConcurrencyGate]）。
 * 闸门放在这一层而不是各个聚合函数里——这里是唯一出口，因此任何新增源、
 * 任何新调用路径都自动受约束，不需要每个调用点记得限流。
 * 「每个聚合函数各自为限」在数学上等于没有上限：分类映射与列表请求会叠加。
 *
 * 【为什么用 `HttpURLConnection` 而不是第三方 HTTP 库】
 * 产品契约 SEC-001 要求「运行时依赖清单为空 / 不接入第三方 SDK」。
 * 鸿蒙版用 `@ohos.net.http` 走系统栈；Android 侧对应的系统栈就是 HttpURLConnection。
 * 它满足本项目的全部需要：自定义头、逐请求超时、二进制收发、状态码、可控重定向。
 * 引入 OkHttp 会为「连接池复用」这类收益付出一个第三方依赖的代价，与本项目的取舍不符。
 *
 * ## KMP 分层（P3 批 D）
 *
 * 本文件在 `commonMain`，**不碰任何平台 API**。真正发请求的那一段被抽成
 * [HttpTransport]（expect），Android/JVM 共用的 actual 落在 `jvmSharedMain`。
 * 分层依据是「哪些语义必须与平台无关」：
 *  - 闸门：`ConcurrencyGate.outbound` 必须包住**整个**请求生命周期（含超时窗口），
 *    这条契约与传输实现无关，放在这里才能「换平台不用重新证明一次」
 *  - 请求头合并、两个 UA 常量、超时默认值：纯数据，各端必须完全一致
 *  - `elapsedMs` 的取点：`t0` 取在**进闸门之前**（排队时间也要算进耗时），
 *    transport 只看得见闸门内，所以差值必须在**这里**算
 *  - `status = 0` 的失败折叠、`safeJson` 判空规则
 *
 * 落到 actual 的只有「真发请求 + 读流 + 失败排障日志」。
 */
object HttpClient {

    /** 系统 UA（默认） */
    const val UA_SYSTEM: String = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36"

    /** PC UA（部分源实测要求桌面端 UA） */
    const val UA_PC: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    data class HttpResult(
        val status: Int,
        val body: String,
        val elapsedMs: Long,
    )

    data class HttpBinaryResult(
        val status: Int,
        val bytes: ByteArray,
        val elapsedMs: Long,
    ) {
        // data class 里含 ByteArray 必须手写 equals/hashCode，否则是引用比较。
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HttpBinaryResult) return false
            return status == other.status &&
                elapsedMs == other.elapsedMs &&
                bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int =
            (status * 31 + elapsedMs.hashCode()) * 31 + bytes.contentHashCode()
    }

    private fun mergeHeaders(
        headers: Map<String, String>?,
        accept: String = "application/json, text/plain, */*",
    ): MutableMap<String, String> {
        val h = LinkedHashMap<String, String>()
        h["User-Agent"] = UA_SYSTEM
        h["Accept"] = accept
        if (headers != null) h.putAll(headers)
        return h
    }

    /**
     * 发送一次请求。
     *
     * 【闸门包住整个请求生命周期】含超时窗口——只包"发起"是不够的：
     * 连接建立后仍占用系统连接数，出闸门等于放开了上限。
     *
     * ## 关于连接复用（实测结论，勿再"优化"）
     *
     * actual 里**无条件** `conn.disconnect()`，看起来像"每次请求都掐掉 socket"，
     * 但实测（见 `app/src/test/.../HttpClientConnectionReuseTest.kt`）**并非如此**：
     *
     *  - JDK 18：3 次连续 GET → 服务端只 `accept` **1** 次
     *  - Android 16（SDK 36，OkHttp 内核）：同样只 `accept` **1** 次
     *
     * 原因是：`stream.use { }` 关闭流的那一刻，连接**已经还回 keep-alive 池**；
     * 之后再 `disconnect()` 拿不回池里的连接，是空操作。JDK 文档的措辞
     * "may close the underlying socket if a persistent connection is **otherwise
     * idle** at that time" 指的是另一种情况——流没读完、连接还挂在当前请求名下。
     *
     * 所以「删掉 disconnect 以启用连接复用」是**伪优化**：代码变复杂，
     * 收益为零。反过来，「响应体没读完时不 disconnect」才是真 bug——那条连接
     * 带着未读字节回到池里，下一个请求会先读到残渣。故保持无条件 disconnect：
     * 读干净的场景它是空操作，没读干净的场景它正好兜底。
     *
     * 【gzip 不动】**刻意不设** `Accept-Encoding`：系统栈（Android 的
     * `HttpURLConnectionImpl` 走 OkHttp 内核）在调用方未设置该头时会自动加
     * `Accept-Encoding: gzip` 并**透明解压**。一旦自己设了这个头，透明解压就
     * 关掉了，得自己 gunzip——那是有害改动，不是优化。
     *
     * @return 被闸门丢弃时返回 `null`，调用方据此给出 `elapsedMs = 0`
     */
    private suspend fun send(
        method: String,
        url: String,
        body: ByteArray?,
        headers: MutableMap<String, String>,
        connectMs: Int,
        readMs: Int,
        binary: Boolean,
    ): Any? {
        // 用单调时钟（Clock.elapsedMs），不用墙钟：elapsedMs 是「耗时」，
        // 墙钟会被 NTP 校时与用户改表影响，可能算出负数。
        // t0 取在**进闸门之前**：排队等空位的时间也要算进耗时里。
        // 调用方靠 elapsedMs == 0 区分「没发出去」（被闸门丢弃）与「发出去了但失败」。
        val t0 = Clock.elapsedMs()
        // 差值必须在闸门**内**算完：elapsedMs 的语义是「这次出网花了多久」，
        // 不含归还空位与唤醒下一个等待者的开销。放到闸门外算会把这部分算进去，
        // 高并发时逐次累积成可观测的漂移。
        // 优先级从协程上下文读：调用方用 NetPriority.Background 标记后台预取，
        // 后台档只能用部分空位，请求因此不必排在整批后台搜索之后。
        val isBg = currentCoroutineContext()[NetPriority]?.background == true
        var waitedMs = 0L
        val pair = ConcurrencyGate.outbound.run(background = isBg) {
            waitedMs = Clock.elapsedMs() - t0
            val raw = HttpTransport.send(method, url, body, headers, connectMs, readMs, binary)
            raw to (Clock.elapsedMs() - t0)
        } ?: return null
        // 【排队开销必须可见】elapsedMs 含排队时间，若闸门被别的请求占满，
        // 一个本该 400ms 的详情会被记成 3000ms，看起来像「源站慢」。
        // 只有超过 50ms 才打，正常直通不打，避免刷屏。
        if (waitedMs > 50) {
            PlaybackProbe.note("net-queue", "${waitedMs}ms url=${url.take(60)}")
        }
        val raw = pair.first
        val elapsed = pair.second
        return if (binary) {
            HttpBinaryResult(raw.status, raw.bytes, elapsed)
        } else {
            HttpResult(raw.status, raw.text, elapsed)
        }
    }

    /**
     * GET。
     *
     * 被闸门丢弃（FR-207 的取消）时返回 `status = 0, elapsedMs = 0`——
     * 调用方靠 `elapsedMs == 0` 可区分「没发出去」与「发出去了但失败」。
     */
    suspend fun get(
        url: String,
        headers: Map<String, String>? = null,
        connectMs: Int = 8000,
        readMs: Int = 10000,
    ): HttpResult {
        val r = send("GET", url, null, mergeHeaders(headers), connectMs, readMs, false)
        return (r as? HttpResult) ?: HttpResult(0, "", 0)
    }

    /** POST（body 为字符串；调用方自行序列化） */
    suspend fun post(
        url: String,
        body: String,
        headers: Map<String, String>? = null,
        connectMs: Int = 8000,
        readMs: Int = 10000,
        accept: String = "application/json, text/plain, */*",
    ): HttpResult {
        val r = send(
            "POST", url, body.encodeToByteArray(),
            mergeHeaders(headers, accept), connectMs, readMs, false,
        )
        return (r as? HttpResult) ?: HttpResult(0, "", 0)
    }

    /**
     * POST 二进制：请求体与响应体都按原始字节收发。
     *
     * 用途：桔子的 v5 接口 —— 请求体是 protobuf 字节（发 Base64 文本会得 400），
     * 响应也是 protobuf 字节（按字符串读会被解成乱码）。
     */
    suspend fun postBinary(
        url: String,
        body: ByteArray,
        headers: Map<String, String>? = null,
        connectMs: Int = 8000,
        readMs: Int = 10000,
    ): HttpBinaryResult {
        val r = send("POST", url, body, mergeHeaders(headers), connectMs, readMs, true)
        return (r as? HttpBinaryResult) ?: HttpBinaryResult(0, ByteArray(0), 0)
    }

    /** GET 便捷返回（失败返回空串；仅历史 CMS 路径保持原语义用） */
    suspend fun getText(url: String, connectMs: Int = 8000, readMs: Int = 10000): String {
        val r = get(url, null, connectMs, readMs)
        return if (r.status == 200) r.body else ""
    }

    /**
     * 安全 JSON 解析：非 JSON（HTML 错误页等）返回 null。
     *
     * 判据是**以 `<` 起始**——部分源的错误页会带 200 状态码，
     * 只看状态码会把错误页当数据。
     *
     * 返回值类型随批 D 从 `org.json.JSONObject` 换成 kotlinx 的 [JsonObject]
     * （原类型在 Kotlin/Native 上不存在）。判空规则逐条不变：空串 → null、
     * 去空白后以 `<` 起始 → null、解析失败 → null。当前**零调用者**
     * （10 个 adapter 已改为直接吃响应文本），保留是为了不删掉既有契约。
     */
    fun safeJson(text: String): JsonObject? {
        if (text.isEmpty()) return null
        val t = text.trim()
        if (t.startsWith("<")) return null
        return JsonX.parseObject(t)
    }

    /** 安全 JSON 数组解析：非数组返回 null（判空规则同上） */
    fun safeJsonArray(text: String): JsonArray? {
        if (text.isEmpty()) return null
        val t = text.trim()
        if (!t.startsWith("[")) return null
        return JsonX.parseArray(t)
    }
}
