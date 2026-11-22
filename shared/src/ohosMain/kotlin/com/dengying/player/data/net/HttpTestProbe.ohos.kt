@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.dengying.player.data.net

import com.dengying.player.platform.Clock
import com.dengying.player.platform.ioDispatcher
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * P6-5 端到端验证面：仅供 ArkTS 调试页驱动，不属于业务代码。
 *
 * 为什么用固定缓冲 + 轮询而不是回调指针：结果字符串由 Kotlin 拥有、
 * [startHttpTest] 返回后保持稳定直到下一次调用，ArkTS 侧不需要处理
 * C 回调的生命周期与异常语义；测试串行执行，单槽位足够。
 */

private const val RESULT_CAPACITY = 256 * 1024

private val testResultBuffer: CPointer<ByteVar> =
    nativeHeap.allocArray<ByteVar>(RESULT_CAPACITY.toLong())

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("dengy_start_http_test")
// 返回 Int 而非 Boolean：K/N 的 Boolean 映射为 unsigned char，桥侧统一按 int32 读取。
fun startHttpTest(url: String?): Int {
    if (url.isNullOrEmpty()) return 0
    if (!HttpTransport.beginTest()) return 0
    val startedAt = HttpTransport.testStartedAt()
    CoroutineScope(ioDispatcher).launch {
        val response = HttpTransport.send(
            method = "GET",
            url = url,
            body = null,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36",
            ),
            connectMs = 8000,
            readMs = 10000,
            binary = false,
        )
        val head = response.text.take(200)
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\"", "'")
        val json = "{\"status\":${response.status}," +
            "\"bytes\":${response.text.length}," +
            "\"elapsedMs\":${Clock.elapsedMs() - startedAt}," +
            "\"head\":\"$head\"}"
        writeResult(json)
        HttpTransport.endTest()
    }
    return 1
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("dengy_http_test_running")
fun httpTestRunning(): Int = if (HttpTransport.testRunning()) 1 else 0

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("dengy_http_test_result")
fun httpTestResult(): String? {
    if (testResultBuffer[0] == 0.toByte()) return null
    return testResultBuffer.toKString()
}

@OptIn(ExperimentalForeignApi::class)
private fun writeResult(text: String) {
    val bytes = text.encodeToByteArray()
    val size = if (bytes.size >= RESULT_CAPACITY) RESULT_CAPACITY - 1 else bytes.size
    for (index in 0 until size) {
        testResultBuffer[index] = bytes[index]
    }
    testResultBuffer[size] = 0
}
