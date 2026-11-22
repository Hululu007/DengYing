package com.dengying.player.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.UByteVar
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * `ByteArray` / `NSData` / `CFData` 的桥接（KMP P5-1，iOS 专用）
 *
 * 三者在 iOS 侧频繁互转：`NSURLSession` 收 `NSData`、`Security` 收 `CFData`、
 * 而跨平台层一律用 `ByteArray`。集中在这里写一份，避免每个 actual 各写一遍
 * 边界处理（空数组时 `addressOf(0)` 会抛，是最容易漏的一处）。
 */

/** `ByteArray` → `NSData`。空数组单独处理：`addressOf(0)` 对空数组非法。 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

/** `NSData` → `ByteArray` */
@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val src = bytes ?: return ByteArray(0)
    return ByteArray(len).also { out ->
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), src, length) }
    }
}

/**
 * `ByteArray` → `CFData`（**调用方负责 `CFRelease`**）。
 *
 * 不用 `CFDataCreateWithBytesNoCopy`：那个要求字节缓冲的生命周期长于 CFData，
 * 而这里的 `ByteArray` 是临时对象，交出所有权会立刻悬垂。
 */
@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toCFData(): CFDataRef? {
    // memScoped 的 lambda 接收者是 MemScope，`this` 在里面指的是它而不是本 ByteArray，
    // 故先取出源数组。逐字节拷进 UByteVar 缓冲：ByteArray 的 addressOf 给的是
    // CPointer<ByteVar>，与 CFDataCreate 要的 CPointer<UByteVar> 不兼容，而 reinterpret
    // 的 reified 推断在此处不收敛。DER/密文都很小（最大几百字节），代价可忽略。
    val src = this
    return memScoped {
        val len = src.size
        val buf = allocArray<UByteVar>(if (len == 0) 1 else len)
        for (i in 0 until len) buf[i] = src[i].toUByte()
        CFDataCreate(null, buf, len.toLong())
    }
}

/**
 * `CFData` → `ByteArray`（**不释放** [ref]，所有权仍归调用方）。
 */
@OptIn(ExperimentalForeignApi::class)
fun cfDataToByteArray(ref: CFDataRef?): ByteArray {
    if (ref == null) return ByteArray(0)
    val len = CFDataGetLength(ref).toInt()
    if (len <= 0) return ByteArray(0)
    val ptr = CFDataGetBytePtr(ref) ?: return ByteArray(0)
    return ByteArray(len) { ptr[it].toByte() }
}

/** 释放一个可能为 null 的 CF 对象（`CFRelease(null)` 会崩，必须判空） */
@OptIn(ExperimentalForeignApi::class)
fun cfRelease(ref: kotlinx.cinterop.COpaquePointer?) {
    if (ref != null) CFRelease(ref)
}
