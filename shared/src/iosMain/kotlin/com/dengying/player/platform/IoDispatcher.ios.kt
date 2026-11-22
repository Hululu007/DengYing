package com.dengying.player.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext

/**
 * [ioDispatcher] 的 iOS 实现（KMP P5-1）
 *
 * Kotlin/Native 没有 JVM 的 `Dispatchers.IO`（那个池是 JVM 专属的阻塞任务池）。
 * 这里显式建一个 4 线程的固定池，语义对齐：**专供阻塞 IO，不占用 CPU 池**。
 *
 * 【为什么不用 `Dispatchers.Default`】Native 的 Default 池线程数 ≈ CPU 核数，
 * 而 [ioDispatcher] 的调用点（`Store` 落盘、`PageCache`、缓存预热）全是阻塞调用。
 * 把它们丢进 Default 会在 IO 抖动时饿死计算任务（真机上表现为界面掉帧）。
 *
 * 【为什么是 4】与 JVM 侧 `Dispatchers.IO` 的默认上限（64）不同，移动端进程
 * 没必要开那么多线程；4 条足够覆盖「落盘 + 网络回包 + 缓存预热」的并发度，
 * 且不会让线程切换成本上升。这是设计选择，不是硬约束。
 */
@OptIn(DelicateCoroutinesApi::class)
actual val ioDispatcher: CoroutineDispatcher = newFixedThreadPoolContext(4, "dy-io")
