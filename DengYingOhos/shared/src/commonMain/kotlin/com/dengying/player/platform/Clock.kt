package com.dengying.player.platform

/**
 * 单调与墙钟时间（KMP P2）
 *
 * ## 为什么不用 kotlinx-datetime
 *
 * 设计稿 §4 把「时间/Base64/UUID」都记在 kotlinx-datetime 名下。实际用下来，
 * 本模块需要的只有两种时间：
 *
 *  - [nowMs]：**墙钟**毫秒，用于「上次更新时间」这类展示与比较，必须能随用户改表跳变
 *  - [elapsedMs]：**单调**计时，用于网络耗时、缓存年龄，绝不能因为用户改系统时间而倒流
 *
 * 这两者用 expect/actual 各写一行就够，而 kotlinx-datetime 的 `Clock` 只给墙钟、
 * 单调时钟仍要平台 API。为了两个函数引入一个含时区数据库的库，与 SEC-001
 * 「运行时依赖清单为空」的取舍不符。
 *
 * 另外 `data/source/adapters/Tv189Adapter.kt` 用 `SimpleDateFormat` 做「yyyyMMdd」格式化，
 * 那是**格式化**需求，不是时钟需求，P3 单独处理（它属于 Java 时间 API，不属于本门面）。
 */
expect object Clock {

    /** 墙钟当前毫秒（Unix epoch）。等价于 JVM 的 `System.currentTimeMillis()`。 */
    fun nowMs(): Long

    /** 单调递增的纳秒/毫秒计数，只用于计算**时间差**，绝对值无意义。 */
    fun elapsedMs(): Long
}
