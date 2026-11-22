package com.dengying.player.platform

/**
 * 时间格式化（KMP P4-1b）
 *
 * ## 为什么单独开一个门面，不并进 [Clock]
 *
 * [Clock] 只回答「现在几点」；本门面回答「把这个时刻排成一段字符串」。
 * 前者是时钟能力，后者是**格式化**能力——iOS 侧要换成 `NSDateFormatter`，
 * 两者没有共同实现可复用，混在一个 expect 里只会让每个 actual 都要写两套无关逻辑。
 *
 * ## 当前唯一用途
 *
 * `Tv189Adapter` 的 `cltts` 参数：`yyyyMMddHHmmss` 形态的**本地时间**时间戳，
 * 服务端用它做请求签名的时间分量，必须与设备本地时区一致（迁移前用
 * `SimpleDateFormat("yyyyMMddHHmmss", Locale.US)`，隐式取默认时区）。
 *
 * 只暴露这一种形态，不提供通用 pattern 参数：跨平台层没有统一的时间格式化
 * 语法（JVM 用 `SimpleDateFormat` 的 pattern，iOS 用 `NSDateFormatter` 的 pattern，
 * 两者不兼容），开放 pattern 会诱使调用方写出「只在 Android 上对」的代码。
 */
expect object TimeFormat {

    /**
     * 把 Unix epoch 毫秒格式化为**设备本地时区**的 `yyyyMMddHHmmss`。
     *
     * 例：`2026-10-26 14:03:07 +0800` → `"20261026140307"`。
     *
     * 时区取自设备当前设置，与迁移前 `SimpleDateFormat` 的隐式默认时区一致；
     * 数字一律用 ASCII 0-9（不随系统 locale 变成全角或阿拉伯-印度数字）。
     */
    fun compactLocalTimestamp(epochMs: Long): String
}
