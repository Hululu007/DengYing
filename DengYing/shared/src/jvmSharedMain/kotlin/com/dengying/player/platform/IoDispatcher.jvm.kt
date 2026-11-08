package com.dengying.player.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * [ioDispatcher] 的 JVM/Android 实现
 *
 * 直接用 `Dispatchers.IO`：它就是一个按需扩容的线程池（默认 64 线程，
 * 与 CPU 核数无关），正是为阻塞 IO 准备的。Android 与桌面 JVM 上行为一致，
 * 故放 `jvmSharedMain` 共用一份。
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
