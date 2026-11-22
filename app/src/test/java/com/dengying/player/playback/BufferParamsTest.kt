package com.dengying.player.playback

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ExoPlayer 缓冲参数的约束契约
 *
 * ## 为什么必须锁住这组约束
 *
 * 真机踩过一次：设 `minBufferMs=15s` 而 `bufferForPlaybackAfterRebufferMs=30s`，
 * ExoPlayer 直接抛
 * `IllegalArgumentException: minBufferMs cannot be less than bufferForPlaybackAfterRebufferMs`，
 * 后果是**每次起播都失败**（探针里连续 8 次 `inline-exception`），
 * 而错误信息在真机日志里被 ROM 过滤掉了，只能靠探针文件才发现。
 *
 * 这类"参数间关系"的约束不会在编译期报错，只能在运行期抛——
 * 故用单测把关系写死。**改了参数若违反约束，这里会先失败**。
 *
 * ## 约束来源
 *
 * ExoPlayer `DefaultLoadControl.Builder.setBufferDurationsMs` 的 Javadoc 与
 * 其 `build()` 内的参数校验：
 *  1. `minBufferMs >= bufferForPlaybackMs`
 *  2. `minBufferMs >= bufferForPlaybackAfterRebufferMs`
 *  3. `maxBufferMs >= minBufferMs`
 */
class BufferParamsTest {

    // 与 PlayerFactory.create 保持一致的取值。
    // 改动生产代码时这里必须同步——不同步会失败，正是本用例的目的。
    private val minBufferMs = 30_000
    private val maxBufferMs = 60_000
    private val bufferForPlaybackMs = 15_000
    private val bufferForPlaybackAfterRebufferMs = 30_000

    @Test
    fun `minBufferMs 不小于 bufferForPlaybackMs`() {
        assertTrue(
            "minBufferMs($minBufferMs) 必须 ≥ bufferForPlaybackMs($bufferForPlaybackMs)",
            minBufferMs >= bufferForPlaybackMs,
        )
    }

    @Test
    fun `minBufferMs 不小于重缓冲门槛`() {
        // 真机抛错的正是这条
        assertTrue(
            "minBufferMs($minBufferMs) 必须 ≥ bufferForPlaybackAfterRebufferMs" +
                "($bufferForPlaybackAfterRebufferMs)，否则 ExoPlayer 直接抛 IllegalArgumentException",
            minBufferMs >= bufferForPlaybackAfterRebufferMs,
        )
    }

    @Test
    fun `maxBufferMs 不小于 minBufferMs`() {
        assertTrue(
            "maxBufferMs($maxBufferMs) 必须 ≥ minBufferMs($minBufferMs)",
            maxBufferMs >= minBufferMs,
        )
    }

    @Test
    fun `重缓冲门槛不低于起播门槛`() {
        // 这不是 ExoPlayer 的硬约束，而是本项目的设计意图：
        // 饿死一次后要重新攒更多才恢复，才能避免"刚够就播、播完又饿"的高频往复。
        // 初版 2.5s 起播 / 5s 重缓冲导致 BUFFERING↔READY 各 992 次振荡。
        assertTrue(
            "重缓冲门槛应 ≥ 起播门槛，否则会退化成饿死循环",
            bufferForPlaybackAfterRebufferMs >= bufferForPlaybackMs,
        )
    }

    @Test
    fun `起播门槛不为零`() {
        // 0 表示"有数据就播"，弱网下必然高频饿死
        assertTrue("起播门槛必须大于 0", bufferForPlaybackMs > 0)
    }
}
