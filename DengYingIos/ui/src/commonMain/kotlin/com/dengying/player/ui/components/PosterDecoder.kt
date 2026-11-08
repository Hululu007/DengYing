package com.dengying.player.ui.components

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 海报字节解码（KMP P4-3c）。
 *
 * ## 为什么解码必须按 target 各写一份
 *
 * `ImageBitmap` 是 Compose 的**跨平台**类型（`androidx.compose.ui.graphics`），
 * 但「把字节变成它」这件事没有任何跨平台 API：
 *
 * - Android：`android.graphics.BitmapFactory.decodeByteArray(...).asImageBitmap()`
 * - JVM（CMP desktop）：`org.jetbrains.skia.Image.makeFromEncoded(...).toComposeImageBitmap()`
 *
 * 两者连解码器都不同（Skia 在 Android 上走系统 `BitmapFactory`，在桌面上走自带的
 * Skia 解码），故 `:ui` 的 `jvmSharedMain` 无法容纳它 —— 这条是 `:shared` 与 `:ui`
 * 中间源集的**关键差别**：`:shared` 的 `javax.crypto` / `java.security` 两端逐字相同，
 * 而图片解码不是。
 *
 * ## 契约（actual 必须满足）
 *
 * 1. 不抛异常：坏字节、不支持的格式、OOM 一律返回 `null`，由调用方显示占位。
 * 2. 不做 IO：只解码内存里的字节（下载由 `PosterFetch` 负责）。
 * 3. 返回值可直接交给 Compose `Image(bitmap = ...)`。
 */
expect object PosterDecoder {
    /**
     * 解码一张海报。
     *
     * @param bytes 图片原始字节（JPEG / PNG / WebP）
     * @return 解码结果；失败返回 `null`
     */
    fun decode(bytes: ByteArray): ImageBitmap?
}
