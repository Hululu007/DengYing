package com.dengying.player.platform

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 阻塞 IO 调度器（KMP P3 批 E）
 *
 * ## 为什么需要门面
 *
 * `Dispatchers.IO` **不在 commonMain**：它在 kotlinx-coroutines 里由各平台源集
 * 分别声明（JVM/Android 一份、Native 从 1.7 起另有一份），common 代码引用不到。
 * `Store` 的每个读写函数都用 `withContext(Dispatchers.IO)` 把 SharedPreferences 的
 * 磁盘 IO 挪出调用线程，这部分逻辑要搬进 commonMain 就必须有个跨平台的入口。
 *
 * ## 与批 D `HttpTransport` 的分工
 *
 * 网络请求的线程切换在 `HttpTransport` 的 actual 内部完成（调用方拿不到也无需知道）。
 * 存储这边不同：`Store` 需要**在 IO 上执行一段普通函数**（读 JSON、算淘汰清单），
 * 那段代码是 commonMain 的，所以切换点必须暴露到 commonMain。
 *
 * ## 为什么不直接用 `Dispatchers.Default`
 *
 * `Default` 是 CPU 线程池，阻塞式磁盘读写会占满它并拖慢所有计算任务。
 * `Store` 在 `withContext` 里做的正是 `getString`/`commit` 这类阻塞调用。
 */
expect val ioDispatcher: CoroutineDispatcher
