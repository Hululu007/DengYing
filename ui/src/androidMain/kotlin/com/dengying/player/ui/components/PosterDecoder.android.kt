package com.dengying.player.ui.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 海报解码的 Android 实现（KMP P4-3c）。
 *
 * 与迁移前的 `Poster.kt` 逐行等价：那时直接在 `BitmapFactory.decodeByteArray(bytes, 0, bytes.size)`
 * 上取 `.asImageBitmap()`。这里唯一的变化是把 `try/catch` 收拢在本文件内，
 * 让 commonMain 的调用方不必再关心 `OutOfMemoryError`。
 *
 * 为什么不做 `inSampleSize` 降采样：海报在列表里显示宽度约 100dp，理论上可以
 * `inJustDecodeBounds` 先读尺寸再降采样省内存。迁移前实测没必要（120 张 LRU 缓存
 * 上限下 OOM 未复现），且降采样会改变缓存里的位图尺寸，属于行为差异 —— 不做。
 */
actual object PosterDecoder {
    actual fun decode(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty()) return null
        return try {
            // BitmapFactory 对坏字节返回 null（不抛），但遇到极端参数可能抛
            // IllegalArgumentException / OutOfMemoryError，两者都要兜住：
            // 海报是装饰性资源，失败显示占位比崩掉整屏好。
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Throwable) {
            // 刻意 catch Throwable 而不是 Exception：OOM 是 Error 不是 Exception，
            // 而这里正是最可能出现 OOM 的地方（一张 4K 海报解码后约 30MB）。
            null
        }
    }
}
