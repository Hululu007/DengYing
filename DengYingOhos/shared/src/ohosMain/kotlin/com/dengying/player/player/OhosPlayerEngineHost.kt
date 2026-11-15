package com.dengying.player.player

import kotlin.concurrent.Volatile

/**
 * 鸿蒙侧全局播放引擎宿主（对齐 Android `PlaybackEngineHolder`）。
 *
 * ## 语义（逐条沿用 PlayerEngineHost 契约）
 *
 * - **单例**：全应用只有一个 [OhosPlayerEngine]；inline / 全屏共用，避免解码器抢占。
 * - `surfaceCount` 只表示「几个表面在显示」，与谁持有无关；归零时**只暂停不释放**。
 * - [peek] 不创建实例：详情页内嵌位据此决定挂载还是空态。
 * - [forceRelease] 用于进程级清理/排障。
 *
 * 线程：K/N 新内存模型下用 `@Volatile` 引用 + 简单计数。所有方法按契约由主线程调用。
 */
object OhosPlayerEngineHost : PlayerEngineHost {

    @Volatile
    private var instance: OhosPlayerEngine? = null

    private var surfaceRefs: Int = 0

    override fun engine(): PlayerEngine {
        instance?.let { return it }
        val created = OhosPlayerEngine()
        instance = created
        return created
    }

    override fun peek(): PlayerEngine? = instance

    override fun surfaceActive() {
        surfaceRefs += 1
    }

    override fun surfaceInactive() {
        if (surfaceRefs > 0) surfaceRefs -= 1
        if (surfaceRefs == 0) instance?.pause()
    }

    override fun forceRelease() {
        surfaceRefs = 0
        instance?.release()
        instance = null
    }

    override fun surfaceCount(): Int = surfaceRefs
}
