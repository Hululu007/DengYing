package com.dengying.player.ui.components

import com.dengying.player.platform.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.setValue
import platform.Foundation.dataTaskWithRequest
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * 海报字节下载的 iOS 实现（KMP P5-1）。
 *
 * ## 与 `:shared` 的 `HttpTransport` 的区别（契约不同，不能复用）
 *
 * `HttpTransport` **不跟随重定向**（两个BT 的换链必须看到原始 302）；
 * 海报加载**必须跟随** —— 图床普遍用 302 把请求转到实际 CDN 节点，
 * 不跟随就取不到图。故这里独立实现，用 [FollowRedirects] 显式接管。
 *
 * ## 契约（与 commonMain 的 Kdoc 逐条一致）
 *
 * 1. 不抛异常：非 2xx、空体、超时、任何异常一律返回 `null`。
 * 2. 跟随重定向：`NSURLSession` 默认就跟随，这里用 delegate 显式表达意图，
 *    避免将来被误改成"不跟随"。
 * 3. 无条件收尾：`finishTasksAndInvalidate()` 释放 session 与其连接。
 * 4. 内部切后台线程：`NSURLSession` 自带工作线程。
 *
 * **运行时未验证（需 macOS）**：只通过 Windows 上的 klib 编译验证，
 * 真实请求行为需在 macOS/真机上验证（见 docs/04 §16.7）。
 */
@OptIn(ExperimentalForeignApi::class)
actual object PosterFetch {

    private const val CONNECT_MS = 6000
    private const val READ_MS = 8000

    actual suspend fun fetch(url: String): ByteArray? {
        val nsUrl = NSURL.URLWithString(url) ?: return null
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.setTimeoutInterval(READ_MS.toDouble() / 1000.0)
        posterReferer(url)?.let { request.setValue(it, forHTTPHeaderField = "Referer") }
        val config = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            timeoutIntervalForRequest = READ_MS.toDouble() / 1000.0
            timeoutIntervalForResource = (CONNECT_MS + READ_MS).toDouble() / 1000.0
        }
        val delegate = FollowRedirects()
        val session = NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate = delegate,
            delegateQueue = NSOperationQueue.mainQueue,
        )
        return suspendCancellableCoroutine { cont ->
            val handler: (NSData?, NSURLResponse?, NSError?) -> Unit = { data, response, error ->
                val result = if (error != null) {
                    null
                } else {
                    val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                    val bytes = data?.toByteArray()
                    if (status in 200..299 && bytes != null && bytes.isNotEmpty()) bytes else null
                }
                session.finishTasksAndInvalidate()
                if (cont.isActive) cont.resume(result)
            }
            val task: NSURLSessionTask = session.dataTaskWithRequest(request, handler)
            cont.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
        }
    }

    /**
     * 显式表达「跟随重定向」。
     *
     * `NSURLSession` 的默认行为本来就跟随，实现这个 delegate 只是为了把意图
     * 写进代码 —— 否则将来有人照 `HttpTransport` 的样子改成不跟随，
     * 图床的 302 就会静默返回空体。
     */
    @OptIn(ExperimentalForeignApi::class)
    private class FollowRedirects : NSObject(), NSURLSessionTaskDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit,
        ) {
            // 回传 newRequest = 跟随该重定向（回 null 才是不跟随）。
            completionHandler(newRequest)
        }
    }
}
