package com.dengying.player.platform

import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970

/**
 * [Clock] 的 iOS 实现（KMP P5-1）
 *
 * - `nowMs` 走 `NSDate`（墙钟，与 JVM 的 `System.currentTimeMillis()` 同义）。
 * - `elapsedMs` 走 `NSProcessInfo.systemUptime`（**单调递增**，与 JVM 的
 *   `System.nanoTime()` 同义）。两者都只用于算差值，原点不同无影响。
 *
 * 【为什么不都用一个源】墙钟会被系统时间调整与 NTP 校正推动，
 * 拿它算耗时可能得到负数；反之 uptime 不能用于「记录某事件发生的时刻」。
 * 两个方法各有语义，调用方按需选（见 [Clock] Kdoc）。
 */
actual object Clock {

    actual fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    actual fun elapsedMs(): Long =
        (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()
}
