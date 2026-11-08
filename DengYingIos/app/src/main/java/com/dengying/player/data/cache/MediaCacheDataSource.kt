package com.dengying.player.data.cache

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache

/**
 * 把跨平台缓存实例包成 Media3 `DataSource.Factory` —— **`:app` 侧唯一的 Media3 解释点**
 *
 * ## 为什么需要这个文件
 *
 * KMP P4-1c 把缓存决策层（开关、上限、预热时机、占用查询）下沉到
 * `:shared/commonMain` 的 `MediaCache`，把真正的 `SimpleCache` 收进
 * `MediaCacheHost` 的 Android actual。两侧交接靠
 * `MediaCacheHost.getOrCreate(): Any?` 这个**窄口子**——`commonMain` 的签名里
 * 绝不能出现 Media3 类型（Kotlin/Native 上没这个库）。
 *
 * 于是「把 `Any?` 变回 `SimpleCache`」这件事必须发生在认识 Media3 的地方。
 * 本文件就是那个地方，且**只有这一处**——受控的一次强制转换，比让 Media3
 * 类型顺着依赖图往上渗要安全得多。
 *
 * 与 `PlayerEngine.viewHandle(): Any?` 是同一手法：跨平台层交出实例，
 * 平台层负责解释它。
 */
@OptIn(UnstableApi::class)
object MediaCacheDataSource {

    /**
     * 用缓存包一层数据源工厂。
     *
     * @param upstream 原来的网络数据源工厂
     * @return 带缓存的数据源工厂；**缓存关闭、未就绪或类型不符时原样返回 `upstream`**
     *   （缓存是加速手段，不能成为播放的前提）
     *
     * ## flags 按官方推荐
     *
     * - `FLAG_BLOCK_ON_CACHE`：VOD 的分片可完整缓存，官方推荐完整缓存后再读，
     *   避免半缓存状态下反复回源
     * - `FLAG_IGNORE_CACHE_ON_ERROR`：缓存出故障时直连上游，
     *   保证"缓存坏了播放也照常"
     */
    fun wrap(upstream: DataSource.Factory): DataSource.Factory {
        if (!MediaCache.MEDIA_CACHE_ENABLED) return upstream
        val cache = MediaCacheHost.getOrCreate() as? SimpleCache ?: return upstream
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(
                CacheDataSource.FLAG_BLOCK_ON_CACHE or
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
            )
    }
}
