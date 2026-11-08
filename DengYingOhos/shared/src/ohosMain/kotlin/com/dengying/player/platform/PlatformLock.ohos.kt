package com.dengying.player.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t

/**
 * [PlatformLock] 的 OHOS 实现
 *
 * 【选型：pthread mutex，且显式设为 RECURSIVE】
 *
 * - 用 `PTHREAD_MUTEX_RECURSIVE` 是为了**对齐 JVM actual 的 `synchronized` 语义**：
 *   `synchronized` 可重入，同一线程二次进入不会自锁。若这里用默认的
 *   `PTHREAD_MUTEX_DEFAULT`，一旦调用链里出现重入（例如 `PageCache.persistAsync`
 *   内部又调了另一个需要锁的方法），OHOS 上会死锁，而 Android 上不会——
 *   这是最隐蔽的一类跨平台行为差异，必须主动对齐。
 *   这一条也与 iOS actual 选 `NSRecursiveLock` 的理由一致。
 * - `withLock` 保证 `unlock` 一定执行：用 `try/finally`，异常路径也不会漏解锁
 *   （漏解锁会让后续所有调用永久阻塞，是比死锁更难查的故障）。
 */
@OptIn(ExperimentalForeignApi::class)
actual class PlatformLock actual constructor() {

    private val mutex = nativeHeap.alloc<pthread_mutex_t>()

    init {
        memScoped {
            val attr = alloc<pthread_mutexattr_t>()
            pthread_mutexattr_init(attr.ptr)
            pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_RECURSIVE)
            pthread_mutex_init(mutex.ptr, attr.ptr)
            pthread_mutexattr_destroy(attr.ptr)
        }
    }

    actual fun <T> withLock(block: () -> T): T {
        pthread_mutex_lock(mutex.ptr)
        return try {
            block()
        } finally {
            pthread_mutex_unlock(mutex.ptr)
        }
    }

    /**
     * 释放 mutex。
     *
     * OHOS 上没有可靠的「对象析构」钩子（Kotlin/Native 无 finalizer 保证），
     * 故需要显式调用。本应用里 [PlatformLock] 实例都是长生命周期对象
     * （`Store.newerLock`、`PageCache.lock`），随进程退出即可，无需调用点释放。
     */
    fun dispose() {
        pthread_mutex_destroy(mutex.ptr)
    }
}
