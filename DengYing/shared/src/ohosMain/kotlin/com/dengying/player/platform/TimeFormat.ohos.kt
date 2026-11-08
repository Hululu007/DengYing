package com.dengying.player.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.localtime_r
import platform.posix.strftime
import platform.posix.tm
import platform.posix.time_tVar

/**
 * [TimeFormat] 的 OHOS 实现
 *
 * 【选型：POSIX localtime_r + strftime，已实测编通】
 * - `localtime_r` 是线程安全版本（**不能用 `localtime`**——它返回静态缓冲区，
 *   多线程下会串味；这正是 JVM actual 用 `SimpleDateFormat` 每次新建实例的同一顾虑）。
 * - `strftime` 用 `%Y%m%d%H%M%S`，与 JVM actual 的 `SimpleDateFormat("yyyyMMddHHmmss")`
 *   逐位等价。
 * - **时区取进程默认**（`localtime_r` 读 `TZ`）：这与 JVM actual 用
 *   `SimpleDateFormat` 默认时区一致。iOS actual 钉了 `en_US_POSIX` locale，
 *   那是为了防止佛历/和历下 `yyyy` 输出错误年号；`strftime` 的 `%Y` 不受
 *   locale 日历影响，故这里不需要额外钉 locale。
 */
@OptIn(ExperimentalForeignApi::class)
actual object TimeFormat {

    actual fun compactLocalTimestamp(epochMs: Long): String = memScoped {
        val tt = alloc<time_tVar>()
        tt.value = epochMs / 1000L
        val tm = alloc<tm>()
        localtime_r(tt.ptr, tm.ptr)
        val buf = allocArray<ByteVar>(32)
        strftime(buf, 32u, "%Y%m%d%H%M%S", tm.ptr)
        buf.toKString()
    }
}
