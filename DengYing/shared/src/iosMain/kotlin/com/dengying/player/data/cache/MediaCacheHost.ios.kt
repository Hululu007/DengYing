package com.dengying.player.data.cache

/**
 * [MediaCacheHost] 的 iOS 实现（KMP P5-1）—— **桩**
 *
 * 与 `jvmMain` 的 `MediaCacheHost.jvm.kt` 同形：全部空实现。
 *
 * 【为什么是桩而不是真实现】Android 侧的缓存是 Media3 的 `SimpleCache`（`androidx.media3.datasource.cache`），
 * iOS 没有对应物。可选的落地方案（AVPlayer 的 `AVURLAsset` 缓存 / 自建 LRU 磁盘缓存 + 自定义
 * `AVAssetResourceLoaderDelegate`）都还没定，且 [MediaCache.MEDIA_CACHE_ENABLED] 目前是 `false`
 * ——先在 Android 上验证「缓存开启会让 `STATE_BUFFERING` 从 21 次涨到 1743 次」的结论（见
 * [MediaCache] Kdoc 的真机实测表），iOS 侧的实现等该结论在 iOS 上复现后再做。
 *
 * 【`hasFailed` 为什么恒 `false`】语义是「尝试过并失败」。这里从未尝试、也永不会成功，
 * 返 `true` 会让 [MediaCache.warmUp] 永久短路，反而掩盖将来真实现接入时的行为变化。
 *
 * TODO(Mac)：iOS 缓存方案定型后替换本桩，并在 docs/04 §16.7 更新桩清单。
 */
actual object MediaCacheHost {

    actual fun getOrCreate(): Any? = null

    actual fun isReady(): Boolean = false

    actual fun hasFailed(): Boolean = false

    actual fun sizeBytes(): Long = 0L

    actual fun clear() = Unit
}
