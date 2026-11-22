package com.dengying.player.platform

import android.util.Log as AndroidLog

/**
 * [Log] 的 Android 实现
 *
 * 【23 字符截断】`android.util.Log` 对 tag 长度有硬限制：旧版本直接抛
 * `IllegalArgumentException("Log tag \"...\" exceeds limit of 23 characters")`，
 * 新版本静默截断。这里显式截断，行为与新旧版本都一致，也不会因为
 * 某个调用方改长了 tag 而在旧设备上崩。
 */
actual object Log {

    private const val MAX_TAG = 23

    actual fun w(tag: String, message: String) {
        AndroidLog.w(truncate(tag), message)
    }

    actual fun w(tag: String, message: String, error: Throwable) {
        AndroidLog.w(truncate(tag), message, error)
    }

    /** 截断到 [MAX_TAG]，两个重载共用，避免两处各写一遍边界判断 */
    private fun truncate(tag: String): String =
        if (tag.length <= MAX_TAG) tag else tag.substring(0, MAX_TAG)
}
