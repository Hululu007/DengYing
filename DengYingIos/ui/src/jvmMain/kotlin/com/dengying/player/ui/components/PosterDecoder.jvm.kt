package com.dengying.player.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * 海报解码的 JVM（CMP desktop）实现（KMP P4-3c）。
 *
 * 走 skiko 的 Skia 解码器，与 Android 侧的 `BitmapFactory` 是两套实现 ——
 * 这正是它不能放进 `jvmSharedMain` 的原因，详见 `PosterDecoder.kt` 的 Kdoc。
 *
 * `org.jetbrains.skia.Image` 由 `libs.compose.ui` 在 jvm target 上传递引入的
 * skiko 提供，故 `ui/build.gradle.kts` 不需要额外的 skiko 依赖。
 */
actual object PosterDecoder {
    actual fun decode(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty()) return null
        return try {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (e: Throwable) {
            // 同 Android 侧：坏字节、不支持的格式、OOM 一律返回 null。
            null
        }
    }
}
