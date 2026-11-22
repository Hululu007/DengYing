package com.dengying.player.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime
import platform.posix.gettimeofday
import platform.posix.timeval
import platform.posix.timespec

/**
 * [Clock] 的 OHOS 实现（HarmonyOS / OpenHarmony，ABI 2.2.0 工具链）
 *
 * 【选型：POSIX，已实测编通】
 * - `nowMs()` 用 `gettimeofday`：OHOS 的 libc（musl）提供完整 POSIX 时间接口，
 *   与 JVM 的 `System.currentTimeMillis()` 同语义（墙钟，毫秒）。
 * - `elapsedMs()` 用 `clock_gettime(CLOCK_MONOTONIC)`：单调时钟。**不能用墙钟做差值**——
 *   NTP 校时或用户改表会让墙钟倒流，算出的耗时变负数，这与 JVM actual 用
 *   `System.nanoTime()` 的理由完全一致。
 * - 绝对值无意义，只能做差（接口约定，见 commonMain 的 Kdoc）。
 */
@OptIn(ExperimentalForeignApi::class)
actual object Clock {

    actual fun nowMs(): Long = memScoped {
        val tv = alloc<timeval>()
        gettimeofday(tv.ptr, null)
        tv.tv_sec * 1000L + tv.tv_usec / 1000L
    }

    actual fun elapsedMs(): Long = memScoped {
        val ts = alloc<timespec>()
        clock_gettime(CLOCK_MONOTONIC, ts.ptr)
        ts.tv_sec * 1000L + ts.tv_nsec / 1_000_000L
    }
}
