package com.dengying.player.data.cache

/**
 * [MediaCacheHost] 的 OHOS 实现 —— **桩**，`TODO(OHOS-device)`
 *
 * 【为什么是桩】JVM/Android actual 依赖 Media3 的 `SimpleCache`
 * （`StandaloneDatabaseProvider` + `LeastRecentlyUsedCacheEvictor`）。
 * OHOS 上没有 Media3，等价能力需要用 `@ohos.multimedia.media` 的
 * `AVPlayer` 配合本地文件缓存自行实现，属于**播放链路**范畴，而不是
 * 「平台门面填充」范畴。
 *
 * 【桩的取值为什么是 false 而不是 true】
 * `hasFailed()` 返回 **false** 表示「从未尝试过」，与 iOS actual 同一口径：
 *   - 返回 true 会让上层认为「缓存坏了」从而走降级路径；
 *   - 返回 true 还会让排障时误判「OHOS 上缓存初始化失败」。
 * 实际语义是「还没实现」，故用 false + `isReady() = false` 表达
 * 「不可用但未失败」。
 */
actual object MediaCacheHost {

    actual fun getOrCreate(): Any? = null

    actual fun isReady(): Boolean = false

    actual fun hasFailed(): Boolean = false

    actual fun sizeBytes(): Long = 0L

    actual fun clear() {
        // TODO(OHOS-device): 见 Kdoc
    }
}
