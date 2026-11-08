package com.dengying.player.data.net

import com.dengying.player.platform.toByteArray
import com.dengying.player.platform.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSError
import platform.Foundation.NSData
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.HTTPMethod
import platform.Foundation.HTTPBody
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * [HttpTransport] 的 iOS 实现（KMP P5-1）
 *
 * 走 `NSURLSession`，与 JVM 侧 `HttpURLConnection` 逐条对齐 [HttpTransport] 的 6 条契约：
 *
 * | 契约 | JVM（HttpURLConnection） | iOS（NSURLSession） |
 * |---|---|---|
 * | 不抛异常，网络错误折叠 `status = 0` | catch 全部 IOException | `error != null` → 0 |
 * | 不跟随重定向，3xx 原样返回 | `setInstanceFollowRedirects(false)` | [RedirectBlocker] 回调 `nil` |
 * | 2xx 读 inputStream、其余读 errorStream | 分流 | NSURLSession 只有一个 data 回调，无需分流 |
 * | 无条件 disconnect | `finally { disconnect() }` | `finishTasksAndInvalidate()` |
 * | 不设 Accept-Encoding | 不设 | 不设（CFNetwork 自动 gzip） |
 * | actual 内部切后台线程 | 自带工作线程 | 自带工作线程 |
 *
 * 【为什么必须挡重定向】两个BT 的播放页会 302 到 `/play/<slug>`，
 * 若自动跟随，`resolvePlay` 拿到的 body 是**跳转后**的页面，
 * `dataIdPattern` 里 `{path}` 锚定的就不是原始 href，会静默取错集。
 *
 * 【哪些需要 import、哪些不需要】
 * cinterop 把 ObjC 的属性 setter 与类方法（`+` 方法）生成为**成员/伴生函数**，
 * 不是包级顶层符号，逐个 import 必然 `Unresolved reference`。
 * 属性一律用赋值语法（`HTTPMethod = ...`），类方法一律用
 * `类名.方法名(...)`（`NSURLSessionConfiguration.ephemeralSessionConfiguration()`）。
 * `NSObject` 的包是 `platform.darwin` 而非 `platform.Foundation`。
 *
 * **运行时未验证（需 macOS）**：只通过 Windows 上的 klib 编译验证，
 * 真实请求行为需在 macOS/真机上验证（见 docs/04 §16.7）。
 */
@OptIn(ExperimentalForeignApi::class)
actual object HttpTransport {

    actual suspend fun send(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        connectMs: Int,
        readMs: Int,
        binary: Boolean,
    ): RawResponse {
        val nsUrl = NSURL.URLWithString(url) ?: return emptyResponse()
        // 用单参 requestWithURL：三参版在 K/N 上返回类型是 NSURLRequest（instancetype 的基类映射），
        // 拿到手就不是 NSMutableURLRequest，改不了 method/body。超时改用属性赋值。
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.HTTPMethod = method
        request.setTimeoutInterval(readMs.toDouble() / 1000.0)
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        if (body != null) request.HTTPBody = body.toNSData()
        val config = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            timeoutIntervalForRequest = readMs.toDouble() / 1000.0
            // resource 超时给 connect + read 之和：连接与读体各占一段
            timeoutIntervalForResource = (connectMs + readMs).toDouble() / 1000.0
        }
        val delegate = RedirectBlocker()
        val session = NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate = delegate,
            delegateQueue = NSOperationQueue.mainQueue,
        )
        return suspendCancellableCoroutine { cont ->
            // 显式声明函数类型：两个 dataTaskWithRequest 重载靠它消歧，尾随 lambda 会撞类型推断
            val handler: (NSData?, NSURLResponse?, NSError?) -> Unit = { data, response, error ->
                val result = if (error != null) {
                    emptyResponse()
                } else {
                    val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                    val bytes = data?.toByteArray() ?: ByteArray(0)
                    val text = if (binary) "" else decodeUtf8(bytes)
                    RawResponse(status = status, text = text, bytes = bytes)
                }
                session.finishTasksAndInvalidate()
                if (cont.isActive) cont.resume(result)
            }
            val task = session.dataTaskWithRequest(request, handler)
            cont.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
        }
    }

    private fun emptyResponse() = RawResponse(status = 0, text = "", bytes = ByteArray(0))

    @OptIn(BetaInteropApi::class)
    private fun decodeUtf8(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return NSString.create(data = bytes.toNSData(), encoding = NSUTF8StringEncoding)?.toString() ?: ""
    }

    /**
     * 只做一件事：所有重定向都回 `null`（= 不跟随）。
     *
     * 必须继承 [NSObject] 并实现 [NSURLSessionTaskDelegateProtocol]，
     * 否则 `NSURLSession` 用默认行为（自动跟随 301/302/307/308）。
     */
    @OptIn(ExperimentalForeignApi::class)
    private class RedirectBlocker : NSObject(), NSURLSessionTaskDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit,
        ) {
            completionHandler(null)
        }
    }
}
