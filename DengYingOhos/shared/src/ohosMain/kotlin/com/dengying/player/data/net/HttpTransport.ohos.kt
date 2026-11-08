package com.dengying.player.data.net

import com.dengying.player.platform.PlatformLock
import kotlin.experimental.ExperimentalNativeApi
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.posix.RTLD_NOLOAD
import platform.posix.dlopen
import platform.posix.dlsym
import kotlin.coroutines.resume

/**
 * OHOS 实现：把请求经 C 桥交给 ArkTS 的 `@kit.NetworkKit` http 执行，结果再回注本文件。
 *
 * 为什么绕这一圈：毕昇 CPF 的 `libnet_http` 没有任何请求体 API
 * （`OH_Http_Request` 的 options 只有 method/headers/超时），POST 类源（wdbay、
 * 桔子、剧知、Mino、山楂）直接判死；`libnet_ssl` 只有证书校验、不是 TLS 传输层，
 * sysroot 与设备侧都没有 curl/openssl。ArkTS http 是设备侧唯一同时具备
 * HTTPS + POST + 超时控制的路径，所以网络能力从 ArkTS 借，执行体留在 :shared。
 *
 * 流转（push 式）：
 *   send() 生成 id → 挂起前把 continuation 存 pending
 *   → dlopen("libentry.so", RTLD_NOLOAD) + dlsym("dengy_http_submit")
 *   → 桥把 JSON 请求经 threadsafe function 投到 JS 线程
 *   → ArkTS 注册的执行器发 http.request → 完成后调 submitResult(id, status, binary, b64)
 *   → 桥经 dlsym(RTLD_DEFAULT, "dengy_http_complete") 回调本文件的 httpComplete()
 *   → base64 解码 → RawResponse → resume 对应 continuation
 *
 * 小 body 走 base64 字符串而不是裸指针：搜索类请求体是 KB 级，代价可接受，
 * 换来的是彻底没有跨 .so 的缓冲区所有权问题。
 *
 * 契约对照（commonMain 定的 6 条）：
 *  1. 不抛异常——桥符号缺失、投递失败、payload 非法一律折叠 status=0。
 *  2. 不跟随重定向——由 ArkTS 执行器侧决定（maxRedirects），本文件原样回传状态码。
 *  3. 2xx 读 body、其余读错误体——执行器已把 body 填进 payload，这里不区分。
 *  4. 无条件释放——无平台句柄需释放，pending 槽位 remove 即回收。
 *  5. 不设 Accept-Encoding——透传调用方 headers，不追加。
 *  6. 后台线程执行——挂起在调用方协程上，回调在 JS 线程恢复，均不占 commonMain 计算池。
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
actual object HttpTransport {

    /** 等待回注的续体：id → (resume 函数, 是否二进制响应)。锁在 [pendingLock] 下访问。 */
    private class Slot(
        val resumeWith: (RawResponse) -> Unit,
        val binary: Boolean,
    )

    private val pending = HashMap<Long, Slot>()
    private val pendingLock = PlatformLock()
    private var nextId = 1L

    // P6-5 端到端探针槽（见 HttpTestProbe.ohos.kt）：串行、单测用，不进业务路径。
    private var runningFlag = false
    private var startedAtMs = 0L
    private val testLock = PlatformLock()

    fun testRunning(): Boolean = testLock.withLock { runningFlag }
    fun testStartedAt(): Long = testLock.withLock { startedAtMs }
    fun beginTest(): Boolean = testLock.withLock {
        if (runningFlag) false else {
            runningFlag = true
            startedAtMs = com.dengying.player.platform.Clock.elapsedMs()
            true
        }
    }
    fun endTest() {
        testLock.withLock { runningFlag = false }
    }

    /** dlopen 结果缓存：桥的 .so 进程内常驻，只解析一次符号。 */
    private var submitSymbol: CPointer<CFunction<(CPointer<ByteVar>) -> Int>>? = null
    private var submitResolved = false

    actual suspend fun send(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        connectMs: Int,
        readMs: Int,
        binary: Boolean,
    ): RawResponse {
        val bridge = resolveSubmitSymbol()
        if (bridge == null) {
            // 桥未加载或符号解析失败：按契约折叠为 status=0，调用方与 JVM 行为一致。
            return RawResponse(0, "", ByteArray(0))
        }

        val requestId: Long = pendingLock.withLock { nextId++ }
        val requestJson = buildRequestJson(
            requestId, method, url, body, headers, connectMs, readMs, binary,
        )

        // 硬超时兜底：ArkTS http 对半开 TCP 连接（fake-ip 代理场景实测会 CLOSE_WAIT
        // 挂死）不触发 readTimeout，回注永不到达→续体永久挂起、闸门 permit 泄漏。
        val timeoutMs = connectMs.toLong() + readMs.toLong() + 3000L
        val response = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val slot = Slot({ r -> cont.resume(r) }, binary)
                pendingLock.withLock { pending[requestId] = slot }
                cont.invokeOnCancellation {
                    pendingLock.withLock { pending.remove(requestId) }
                }

                val accepted = memScoped {
                    val cstr = requestJson.cstr
                    bridge.invoke(cstr.ptr)
                }
                if (accepted != 0) {
                    // 投递失败（JS 线程未就绪等）：当场失败，不等回注。
                    val orphan = pendingLock.withLock { pending.remove(requestId) }
                    orphan?.resumeWith(RawResponse(0, "", ByteArray(0)))
                }
            }
        }
        if (response == null) {
            pendingLock.withLock { pending.remove(requestId) }
            com.dengying.player.platform.Log.w("DY/Http", "timeout ${timeoutMs}ms id=$requestId url=$url")
            return RawResponse(0, "", ByteArray(0))
        }
        return response
    }

    /** 解析桥导出符号；失败返回 null。 */
    private fun resolveSubmitSymbol(): CPointer<CFunction<(CPointer<ByteVar>) -> Int>>? {
        if (submitResolved) return submitSymbol
        // RTLD_NOLOAD：桥 .so 已被 HAP 加载（libdengying.so 的 DT_NEEDED 含它），
        // 只取已加载句柄；取不到说明依赖断了，不强行再 dlopen。
        val handle = dlopen("libentry.so", RTLD_NOLOAD)
        val symbol = handle?.let { dlsym(it, "dengy_http_submit") }
        @Suppress("UNCHECKED_CAST")
        submitSymbol = symbol as? CPointer<CFunction<(CPointer<ByteVar>) -> Int>>
        submitResolved = true
        return submitSymbol
    }

    /**
     * 回注实现。入口在文件末尾的顶层 [httpComplete]——@CName 不能标在 object 成员上，
     * 那样生成的 C 签名带 receiver，桥按无 receiver 调用会整体错位一位（实测崩溃）。
     */
    internal fun complete(
        id: Long,
        status: Int,
        binary: Int,
        payloadBase64: CPointer<ByteVar>?,
        payloadLen: Int,
    ) {
        val isBinary = binary != 0
        val slot = pendingLock.withLock { pending.remove(id) }
            ?: Slot({}, isBinary) // 已取消/超时：仍解码以对齐语义，但 resume 空操作
        val response = decodePayload(status, payloadBase64, payloadLen, slot.binary)
        slot.resumeWith(response)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodePayload(
        status: Int,
        payloadBase64: CPointer<ByteVar>?,
        payloadLen: Int,
        binary: Boolean,
    ): RawResponse {
        if (payloadBase64 == null || payloadLen <= 0) {
            return RawResponse(status, "", ByteArray(0))
        }
        val bytes = try {
            Base64.decode(payloadBase64.toKString())
        } catch (error: IllegalArgumentException) {
            return RawResponse(0, "", ByteArray(0))
        }
        return if (binary) {
            RawResponse(status, "", bytes)
        } else {
            RawResponse(status, bytes.decodeToString(), ByteArray(0))
        }
    }

    private fun buildRequestJson(
        id: Long,
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        connectMs: Int,
        readMs: Int,
        binary: Boolean,
    ): String = buildString {
        append("{\"id\":").append(id)
        append(",\"method\":\"").append(jsonEscape(method)).append('"')
        append(",\"url\":\"").append(jsonEscape(url)).append('"')
        if (body != null && body.isNotEmpty()) {
            append(",\"body\":\"").append(jsonEscape(b64Encode(body))).append('"')
        }
        append(",\"headers\":{")
        headers.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            append('"').append(jsonEscape(key)).append("\":\"").append(jsonEscape(value)).append('"')
        }
        append('}')
        append(",\"connectMs\":").append(connectMs)
        append(",\"readMs\":").append(readMs)
        append(",\"binary\":").append(if (binary) "true" else "false")
        append('}')
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun b64Encode(bytes: ByteArray): String = Base64.encode(bytes)

    private fun jsonEscape(text: String): String = buildString {
        for (ch in text) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') {
                    append("\\u00").append(ch.code.toString(16).padStart(2, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
}

/**
 * 桥回调入口（C 侧 `dlsym(handle, "dengy_http_complete")` 取址调用）。
 * 必须是顶层函数：@CName 标在 object 成员上会多出 receiver 参数，
 * 桥按 5 参签名调用时整体错位——首个实参（id=1）被当 receiver 解引用，SIGSEGV。
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("dengy_http_complete")
fun httpComplete(id: Long, status: Int, binary: Int, payloadBase64: CPointer<ByteVar>?, payloadLen: Int) {
    HttpTransport.complete(id, status, binary, payloadBase64, payloadLen)
}
