package com.dengying.player.platform

/**
 * [PlatformLock] 的 JVM/Android 实现
 *
 * 就是 `synchronized`：语义与迁移前 `synchronized(this)` 完全一致
 * （互斥、可重入、内存可见性由 monitor 提供），所以调用点的行为不会有任何变化。
 *
 * 【为什么不用 `ReentrantLock`】`synchronized` 的锁对象不需要显式释放，
 * 也不会因为漏写 `unlock()` 而永久卡死；这里是纯粹的短临界区（改一个 HashMap
 * 条目、拼一个 JSON），没有需要 `tryLock`/超时的场景。
 */
actual class PlatformLock actual constructor() {

    private val monitor = Any()

    actual fun <T> withLock(block: () -> T): T = synchronized(monitor) { block() }
}
