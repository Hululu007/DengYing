package com.dengying.player.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * 海报解码的 iOS 实现（KMP P5-1）。
 *
 * 与 JVM 桌面侧同一条路径：Compose Multiplatform 在 iOS 上也走 skiko，
 * 故 `org.jetbrains.skia.Image.makeFromEncoded` 两端共用（Android 侧才需要
 * `BitmapFactory`，那是平台自带解码器）。
 *
 * 契约与 commonMain 的 Kdoc 逐条一致：不抛异常、不做 IO、失败返回 null。
 *
 * **运行时未验证（需 macOS）**：只通过 Windows 上的 klib 编译验证，
 * 真实解码行为需在 macOS/真机上验证（见 docs/04 §16.7）。
 */
actual object PosterDecoder {
    actual fun decode(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty()) return null
        return try {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Throwable) {
            // 同 Android / JVM 侧：坏字节、不支持的格式、OOM 一律返回 null。
            null
        }
    }
}
