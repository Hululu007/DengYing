package com.dengying.player.platform

/**
 * [Clock] 的 JVM 实现（androidMain 与 jvmMain 技术上都能用，但两端 API 一致，
 * 所以这仍是一份共用实现，放 `jvmSharedMain` 不合适——见下面说明）
 *
 * 实际上 `System.currentTimeMillis()` 与 `System.nanoTime()` 在 Android 上同样存在，
 * 故这里直接放在 `jvmSharedMain`，Android 侧不再重复。
 */
actual object Clock {

    actual fun nowMs(): Long = System.currentTimeMillis()

    /**
     * 单调时钟。
     *
     * 用 `nanoTime()` 而不是 `currentTimeMillis()`：后者会被 NTP 校时与用户改表
     * 影响，可能倒流，用它算出的「耗时」会是负数。`nanoTime()` 单调且不受墙钟影响，
     * 但**绝对值无意义**（起点是任意的），只能做差——接口文档已写明这个约定。
     */
    actual fun elapsedMs(): Long = System.nanoTime() / 1_000_000L
}
