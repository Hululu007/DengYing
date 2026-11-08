package com.dengying.player.data.cache

import com.dengying.player.platform.ioDispatcher
import kotlinx.coroutines.withContext

/**
 * 媒体数据磁盘缓存（FR-704、NFR-704）—— 跨平台决策层
 *
 * ## 当前状态：**默认关闭**（`MEDIA_CACHE_ENABLED = false`）
 *
 * 这不是"还没做完"，而是一次**实测后的暂停启用决定**，证据如下。
 *
 * ## 真机实测：开启缓存时播放异常（2026-10-17）
 *
 * 用探针对比同一部片、同一地址：
 *
 * | | 缓存开启 | 缓存关闭 |
 * |---|---|---|
 * | `STATE_BUFFERING` | **1743 次** | 21 次 |
 * | 日志总行数 | 4710（几乎全是 BUFFERING↔READY 交替） | 82 |
 * | `VIDEO_SIZE` / `engine-ready` | **始终不出现** | 正常出现 |
 * | 用户感知 | "点了播放但一直不播" | 正常播放 |
 *
 * 振荡频率约 **每秒 17 次**——远超网络能造成的频率，是数据源层的同步空转。
 * 二分定位（唯一变量 = 是否包 `CacheDataSource`）确认原因在缓存数据源。
 *
 * ## 重新启用前必须完成的验证（按顺序）
 *
 * 1. 构造已改用 `StandaloneDatabaseProvider`（见 `MediaCacheHost.android.kt`）
 * 2. flags 已按官方推荐：`FLAG_BLOCK_ON_CACHE`（VOD 分片完整缓存后再读）
 *    叠加 `FLAG_IGNORE_CACHE_ON_ERROR`（缓存故障时直连，不让缓存成为播放前提）
 * 3. 依次实测：单集连续播放 10 分钟无振荡 → seek 后恢复 → 二次播放命中缓存
 * 4. 真机探针 `STATE_BUFFERING` 次数与关闭时同量级（< 30 次/分钟）
 *
 * 在此之前，起播与播放速度由以下已验证的手段保证（FR-703/705）：
 * 预解析（点击时地址已就绪）、缓冲参数修正（消除饿死循环）、去阻塞探测。
 *
 * ## KMP P4-1c 分层
 *
 * 本文件只保留**跨平台可判定的部分**：开关、上限、预热时机、占用查询的对外语义。
 * 真正的缓存实例（`SimpleCache` / `DatabaseProvider` / `DataSource.Factory`）全部
 * 是 Media3 类型，收在 [MediaCacheHost] 的平台 actual 里；`:app` 侧只有
 * `MediaCacheDataSource.kt` 一个薄包装把 `DataSource.Factory` 包上缓存。
 *
 * 这样切分的理由：开关关闭时整条缓存链路在 iOS 上根本不会被执行，
 * 但**"关闭"这个决策本身**必须是跨平台共享的——否则 iOS 侧会各自实现一套
 * 缓存策略，重新踩一遍 1743 次振荡的坑。
 */
object MediaCache {

    /** 缓存上限（MB） */
    const val LIMIT_MB = 500L

    /**
     * 媒体缓存总开关（FR-704）。
     *
     * **当前 = false（默认关闭）**，理由与重启用条件见类注释。
     * 关闭时 `wrap()` 原样返回上游工厂，播放走纯网络直连——
     * 已实测稳定（21 次缓冲属于 HLS 正常范围）。
     */
    const val MEDIA_CACHE_ENABLED = false

    /**
     * 预热缓存：在 IO 线程提前建好平台缓存实例。
     *
     * ## 为什么需要单独一步（真机踩坑记录）
     *
     * `SimpleCache` 的构造是**同步阻塞**的，所以不能放主线程。但**不能因此
     * 把整个 `setMedia` 丢进 IO 线程**——ExoPlayer 的 API
     * （`setMediaSource`/`prepare`/`play`/`seekTo`）**必须在主线程**调用，
     * 否则抛 `IllegalStateException: Player is accessed on the wrong thread`。
     *
     * 正确做法：**先在 IO 线程预热缓存**（这里），再回主线程调 ExoPlayer。
     *
     * 开关关闭时直接返回：不做任何平台调用，iOS 侧同样安全。
     */
    suspend fun warmUp() {
        if (!MEDIA_CACHE_ENABLED) return
        if (MediaCacheHost.isReady() || MediaCacheHost.hasFailed()) return
        withContext(ioDispatcher) { MediaCacheHost.getOrCreate() }
    }

    /** 清空缓存（设置项用）。开关关闭时无操作。 */
    fun clear() {
        if (!MEDIA_CACHE_ENABLED) return
        MediaCacheHost.clear()
    }

    /** 当前缓存占用字节数（供设置页显示）。开关关闭时恒为 0。 */
    fun sizeBytes(): Long = if (!MEDIA_CACHE_ENABLED) 0L else MediaCacheHost.sizeBytes()
}
