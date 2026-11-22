package com.dengying.player.platform

import platform.Foundation.NSLog

/**
 * [Log] 的 iOS 实现（KMP P5-1）
 *
 * 走 `NSLog`：输出进系统统一日志（Console.app / Xcode 控制台 / `idevicesyslog` 均可见）。
 *
 * 【格式串必须是字面量】`NSLog("%@", s)` 而不是 `NSLog(s)`：
 * 后者会把消息里的 `%` 当格式符解释（`"100%"` 会读到不存在的参数而崩溃）。
 * 用 `%@` 收一个已拼好的字符串，与 JVM 侧「tag 与 message 直接拼接」等价。
 *
 * 【为什么不做 23 字符截断】那是 Android `android.util.Log.isLoggable` 的硬限制
 * （超长 tag 直接抛），iOS 无此约束，故 [MAX_TAG] 只在 androidMain 生效。
 */
actual object Log {

    actual fun w(tag: String, message: String) {
        NSLog("%@", "[$tag] $message")
    }

    actual fun w(tag: String, message: String, error: Throwable) {
        NSLog("%@", "[$tag] $message :: ${error::class.simpleName}: ${error.message ?: ""}")
    }
}
