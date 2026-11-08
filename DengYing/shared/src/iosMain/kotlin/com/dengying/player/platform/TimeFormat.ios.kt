package com.dengying.player.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

/**
 * [TimeFormat] 的 iOS 实现（KMP P5-1）
 *
 * 输出设备**本地时区**的 `yyyyMMddHHmmss`，与 JVM 侧
 * `SimpleDateFormat("yyyyMMddHHmmss", Locale.US)` 逐位对齐。
 *
 * 【为什么 locale 必须钉 `en_US_POSIX`】`NSDateFormatter` 默认跟随用户区域设置，
 * 在佛历（th_TH）、和历（ja_JP@calendar=japanese）等区域下 `yyyy` 会输出佛历/和历年号，
 * 与 JVM 侧（显式 `Locale.US`）不一致。`en_US_POSIX` 强制公历 + ASCII 数字。
 *
 * 【为什么每次新建 formatter】`NSDateFormatter` 不是线程安全的，且转换开销远小于
 * 一处日志时间戳；JVM 侧同样每次新建 `SimpleDateFormat`，两侧保持一致。
 */
actual object TimeFormat {

    private const val PATTERN = "yyyyMMddHHmmss"

    /**
     * 1970-01-01 到 2001-01-01（`NSDate` 的参考纪元）的秒数。
     *
     * Kotlin/Native 的 `NSDate` 绑定只暴露 `timeIntervalSinceReferenceDate` 构造，
     * 没有 `timeIntervalSince1970`，故用这个常量换算，避免依赖未生成的绑定。
     */
    private const val EPOCH_TO_REFERENCE_SECONDS = 978307200.0

    actual fun compactLocalTimestamp(epochMs: Long): String {
        val formatter = NSDateFormatter()
        formatter.dateFormat = PATTERN
        formatter.locale = NSLocale(localeIdentifier = "en_US_POSIX")
        formatter.timeZone = NSTimeZone.localTimeZone
        val date = NSDate(
            timeIntervalSinceReferenceDate = epochMs / 1000.0 - EPOCH_TO_REFERENCE_SECONDS,
        )
        return formatter.stringFromDate(date)
    }
}
