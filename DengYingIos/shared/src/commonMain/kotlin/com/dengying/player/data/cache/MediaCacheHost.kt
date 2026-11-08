package com.dengying.player.data.cache

/**
 * 平台缓存实例的持有者（KMP P4-1c）
 *
 * ## 为什么是 expect/actual 而不是 interface
 *
 * 与 `PlayerEngineHost`（interface，由平台壳注入装配关系）不同，本门面**没有装配
 * 自由度**：缓存实例是进程内单例，生命周期由缓存策略本身决定，任何调用方拿到的
 * 必须是同一份。故用 `expect object` 让编译器保证「每个 target 恰好一份实现」。
 *
 * ## 为什么 `cacheHandle()` 返回 `Any?` 而不是具体类型
 *
 * 具体类型是 `androidx.media3.datasource.cache.SimpleCache`，那是 Media3 的类，
 * 绝不能出现在 `commonMain` 的签名里。需要一个窄口子把实例交给 `:app` 侧的
 * `MediaCacheDataSource.kt`——那里才认识 Media3，做一次受控的强制转换。
 *
 * 这与 `PlayerEngine.viewHandle(): Any?` 是同一手法：**跨平台层交出实例，
 * 平台层负责解释它**。口子越窄越好，故只此一处。
 *
 * ## 六个实现必须满足的契约
 *
 * 1. [getOrCreate] 可以阻塞（构造要等索引加载），调用方必须已在 IO 线程。
 * 2. [getOrCreate] **不抛异常**：初始化失败返回 null 并置 [hasFailed]，之后恒返 null。
 * 3. [getOrCreate] 幂等：同一进程内重复调用返回同一实例。
 * 4. [clear] 释放实例并删除磁盘数据，**重置** [hasFailed]（用户主动清理后允许重试）。
 * 5. [sizeBytes] 在实例未就绪时返回 0，不触发初始化。
 * 6. 实例的构造参数（目录、上限、索引实现）由 actual 决定，commonMain 不感知。
 */
expect object MediaCacheHost {

    /**
     * 取（必要时创建）平台缓存实例。
     *
     * 必须在 IO 线程调用。失败返回 null 并记下失败标记，后续调用不再重试。
     */
    fun getOrCreate(): Any?

    /** 实例是否已就绪（不触发初始化）。 */
    fun isReady(): Boolean

    /** 是否已初始化失败过（不触发初始化）。 */
    fun hasFailed(): Boolean

    /** 当前占用字节数；未就绪返回 0。 */
    fun sizeBytes(): Long

    /** 释放实例并删除磁盘数据，同时重置失败标记。 */
    fun clear()
}
