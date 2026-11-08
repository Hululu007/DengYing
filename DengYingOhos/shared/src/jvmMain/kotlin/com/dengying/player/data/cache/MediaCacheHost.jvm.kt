package com.dengying.player.data.cache

/**
 * [MediaCacheHost] 的 JVM 实现 —— **恒为空实现**
 *
 * `jvm` target 的存在意义只是让跨平台逻辑能在不启 Android 运行时的纯 JVM 上跑单测
 * （见 `shared/build.gradle.kts` 的 target 说明），它不产出任何用户可见的播放能力。
 * 媒体缓存依赖 Media3 的 `SimpleCache`，而 Media3 是 Android 库，JVM 上没有——
 * 故这里如实返回「没有缓存」：`getOrCreate()` 恒 null、`hasFailed()` 恒 false。
 *
 * ## 为什么不是 `hasFailed() = true`
 *
 * `hasFailed` 的语义是「初始化尝试过并失败了」，调用方据此**停止重试**。
 * 而 JVM 侧从未尝试过，也没有重试的必要（永远不会成功）。返回 false 让
 * `MediaCache.warmUp()` 每次都走一遍空调用——那只是两次字段读取，代价可忽略，
 * 却避免了把「本平台不支持」伪装成「曾经失败过」。
 */
actual object MediaCacheHost {

    actual fun getOrCreate(): Any? = null

    actual fun isReady(): Boolean = false

    actual fun hasFailed(): Boolean = false

    actual fun sizeBytes(): Long = 0L

    actual fun clear() = Unit
}
