package com.dengying.player.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext

/**
 * [ioDispatcher] 的 OHOS 实现
 *
 * 【选型：可增长线程池（newCachedThreadPool），与 iOS actual 的口径有意不同】
 *
 * - **不用 `Dispatchers.Default`**：它是按 CPU 核数开的**计算**线程池，
 *   跑阻塞 IO 会把计算任务饿死。这一条与 iOS actual 的取舍一致。
 * - **容量为什么是 16 而不是原来的 4**：OHOS 的 HTTP 经 ArkTS 桥异步回注，
 *   线程挂起等待期间仍占池。实测一次代理网络抖动就同时卡住 4 个分类请求，
 *   固定 4 线程池被全占后，连「写搜索历史」这种不碰网络的协程都排不上队
 *   （2026-11-02 模拟器排查 search_history 不落盘的根因）。K/N 没有可增长的
 *   cached 池，只能加大固定值；真实并发由 ConcurrencyGate 出网闸门兜底，
 *   16 个线程只是挂起等待的余量，不会全部跑满。
 * - `newFixedThreadPoolContext` 需 `@OptIn(DelicateCoroutinesApi)`：
 *   它是全局线程，不随 CoroutineScope 关闭。这与 [Clock] 一样的全局资源，
 *   应用生命周期内只存在一份，可接受。
 */
@OptIn(DelicateCoroutinesApi::class)
actual val ioDispatcher: CoroutineDispatcher = newFixedThreadPoolContext(16, "dy-io")
