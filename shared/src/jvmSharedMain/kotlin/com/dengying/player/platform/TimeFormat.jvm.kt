package com.dengying.player.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [TimeFormat] 的 JVM actual（Android 与桌面共用）。
 *
 * 逐字节等价于迁移前 `Tv189Adapter` 里的
 * `SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())`：
 *
 *  - `Locale.US` 固定住数字形态，不受设备 locale 影响
 *  - 不显式传 TimeZone ⇒ 取 `TimeZone.getDefault()`，与原实现相同
 *  - 每次新建实例：`SimpleDateFormat` 不是线程安全的，缓存共享实例会在
 *    并发请求下产出错乱字符串（原实现同样每次新建）
 */
actual object TimeFormat {

    actual fun compactLocalTimestamp(epochMs: Long): String =
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(epochMs))
}
