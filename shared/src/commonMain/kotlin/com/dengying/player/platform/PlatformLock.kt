package com.dengying.player.platform

/**
 * 跨平台互斥锁（KMP P3 批 E）
 *
 * ## 为什么需要它
 *
 * `PageCache` 的 `ensureLoaded()` 与 `persistAsync()` 原本用 `synchronized(this)`
 * 保护内存镜像：多个线程可能同时触发首次加载，两个都写 `mem` 会互相覆盖。
 * `synchronized` 是 JVM 专属 API，Kotlin/Native 上没有。
 *
 * ## 为什么不用 `Mutex` 或 atomicfu
 *
 * - `kotlinx.coroutines.sync.Mutex` 要求 `suspend` 上下文。`PageCache` 的
 *   `get/isStale/put/touch` 全是**非 suspend** 函数，调用点包括
 *   `DengYingApp.onCreate` 与 `IndexViewModel.loadData()` 里的非协程段落；
 *   改成 suspend 会波及这两处的结构，属于超出「无行为变化」的改动。
 * - atomicfu 是新增依赖，而 §5.1 限定「新增依赖仅 kotlinx-serialization-json」。
 *
 * 所以用一个最小的平台锁：JVM/Android 上就是 `synchronized`，语义完全一致，
 * 调用点结构一行不动。
 *
 * ## 用法
 *
 * 每处需要互斥的地方**各自持有一个实例**（等价于原来 `synchronized(this)` 里的 `this`）：
 *
 * ```
 * private val lock = PlatformLock()
 * ...
 * lock.withLock { mem[key] = rec }
 * ```
 *
 * 【不要嵌套不同实例的锁】两个实例之间没有全局顺序，交叉加锁会死锁。
 * 同实例重入是安全的（JVM 的 monitor 可重入）。
 */
expect class PlatformLock() {

    /** 在锁内执行 [block] 并返回其结果；同实例重入安全 */
    fun <T> withLock(block: () -> T): T
}
