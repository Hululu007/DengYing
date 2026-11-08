package com.dengying.player.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.dengying.player.data.net.HttpTransport
import com.dengying.player.platform.Log
import org.jetbrains.skia.Image

/**
 * 海报解码 ohos actual（CMP spike，docs/04 §16.40）。
 *
 * 与 iOS/JVM 同一条 skiko 路径：ohos 线的 Compose 渲染器就是 skiko
 * （ComposePlugin 已把 skiko-ohosx64-api/overlay 接进编译图），
 * `Image.makeFromEncoded` 直接可用。契约：不抛异常、失败返回 null。
 */
actual object PosterDecoder {
    actual fun decode(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty()) return null
        return try {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Throwable) {
            null
        }
    }
}

/**
 * 海报下载 ohos actual。
 *
 * 复用 `:shared` 的 [HttpTransport]（P6-5 打通的 ArkTS http 桥，模拟器实测 200）。
 * 契约差异处理见 commonMain Kdoc：海报**必须跟随重定向**——ArkTS
 * `@ohos.net.http` 的 `request()` 默认自动跟随 302，正合语义，与 iOS 侧
 * 「默认行为本来就跟随，实现 delegate 只是把意图写进代码」同理。
 * Referer 走 headers 参数（豆瓣 418 防盗链）。
 */
actual object PosterFetch {
    actual suspend fun fetch(url: String): ByteArray? {
        return try {
            val referer = posterReferer(url)
            val headers = if (referer != null) mapOf("Referer" to referer) else emptyMap()
            val resp = HttpTransport.send(
                method = "GET",
                url = url,
                body = null,
                headers = headers,
                connectMs = 8_000,
                readMs = 10_000,
                binary = true,
            )
            if (resp.status in 200..299 && resp.bytes.isNotEmpty()) resp.bytes else null
        } catch (e: Exception) {
            Log.w("DY/Poster", "fetch failed $url: ${e.message}")
            null
        }
    }
}
