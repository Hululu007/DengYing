package com.dengying.player.platform

/**
 * 日志门面（KMP P2）
 *
 * 全项目只保留这一个日志出口。`commonMain` 里不允许出现 `android.util.Log`——
 * Kotlin/Native 上根本没有这个类，编译器会直接拒绝。
 *
 * 【为什么不引日志库】需要的能力只有「打一行带级别的字符串」。
 * 引入第三方日志库要换掉整条依赖口径（SEC-001），而收益为零。
 *
 * 【只有 w 一个方法】本项目现有日志**全部**是失败路径的 warning
 * （见 `data/store/Store.kt` 的写入保护提示、`data/net/HttpClient.kt` 的
 * `logNetFail`、`data/cache` 下各文件的缓存降级提示）。没有 info/debug 级别的实际使用，
 * 加进来就是无人调用的死 API。需要时按同样的桥接方式增补即可。
 */
expect object Log {

    /**
     * 输出一条 warning。
     *
     * @param tag 日志标签。**Android 侧有意截断到 23 字符**——`android.util.Log`
     *   对该长度有硬限制（旧版本会抛 `IllegalArgumentException`，现在截断）。
     *   这个约束只在 Android actual 里处理，调用方按业务含义命名即可，不必数长度。
     */
    fun w(tag: String, message: String)

    /**
     * 输出一条 warning，并附上异常。
     *
     * 【为什么是重载而不是给上面那个加默认参数】
     * `expect` 声明与它的 `actual` 都不能带默认参数值（Kotlin 多平台限制），
     * 所以只能写成两个独立方法。这样也顺手把「有异常 / 无异常」在调用点区分开，
     * 读代码时不必去猜第三个参数有没有被传。
     *
     * 【为什么不在 commonMain 里拼 `message + stackTrace`】
     * 各平台打印堆栈的方式不同（Android 走 `Log.w(tag, msg, tr)` 由系统拼，
     * JVM 走 `Logger.log(Level.WARNING, msg, tr)`）。在 commonMain 手工拼字符串
     * 会丢掉 Android 侧的行号与进程信息，也会让 JVM 侧的多行堆栈挤成一行。
     *
     * @param error 要随日志一起输出的异常。
     */
    fun w(tag: String, message: String, error: Throwable)
}
