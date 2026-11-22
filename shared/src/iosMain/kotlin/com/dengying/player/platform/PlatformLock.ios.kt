package com.dengying.player.platform

import platform.Foundation.NSRecursiveLock

/**
 * [PlatformLock] 的 iOS 实现（KMP P5-1）
 *
 * 用 `NSRecursiveLock` 而非普通 `NSLock`：JVM 侧 actual 是 `synchronized`，
 * **同一线程可重入**。若这里用非递归锁，任何「持锁调用回自身」的路径
 * （`Store` 的 `markNewer` 嵌套调用、`PageCache` 的读取路径）都会自死锁。
 *
 * 【为什么不用 `kotlin.native.concurrent` 的原子操作】[PlatformLock] 的契约是
 * 「保护一段可能很长的临界区」，不是无锁计数；自旋重试会把 IO 线程烧满。
 *
 * 【为什么不用协程 `Mutex`】`withLock` 的调用点全在非 suspend 上下文
 * （`DengYingApp.onCreate` 装配、`IndexViewModel.loadData()` 的同步段），
 * 无法 await。这与 JVM 侧选 `synchronized` 的理由完全相同。
 */
actual class PlatformLock {

    private val lock = NSRecursiveLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
