package com.dengying.player.playback

import com.dengying.player.ui.platform.shouldRevealVideoSurface
import com.dengying.player.ui.platform.videoSurfaceIsBelowCompose
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放页根背景「让位给下层视频」的判定契约。
 *
 * ## 为什么必须锁住
 *
 * 鸿蒙的视频画在 Compose 之下的全屏 XComponent 上，根 Box 不透明就会整块
 * 盖住画面（PR #11 的 BLOCKER）。但 `PlayerScreen` 是 commonMain 共享 UI：
 * 若把这个透明分支也套到 Android 上，`PlayerView` 的 FIT 黑边之外会露出
 * Activity 的暖白 `windowBackground`（`Theme.DengYing` 的 `#F4F4F1`），
 * 起播/缓冲/宽屏片源下闪白。故判定必须同时受平台层序开关约束。
 *
 * 真机层序无法在本机验证，但「何时让位」是纯逻辑，用单测写死。
 */
class VideoSurfaceRevealTest {

    private fun reveal(
        belowCompose: Boolean,
        bound: Boolean = true,
        prepared: Boolean = true,
        buffering: Boolean = false,
        hasError: Boolean = false,
    ) = shouldRevealVideoSurface(belowCompose, bound, prepared, buffering, hasError)

    @Test
    fun ohosRevealsOnlyWhenSurfaceIsBoundAndPictureIsReady() {
        assertTrue(reveal(belowCompose = true))
        assertFalse("未绑 surface 时不能透明，否则露出窗口底色", reveal(belowCompose = true, bound = false))
        assertFalse("未 prepared 时还没有画面", reveal(belowCompose = true, prepared = false))
    }

    @Test
    fun bufferingAndErrorKeepTheOpaqueBlackBackdrop() {
        assertFalse(reveal(belowCompose = true, buffering = true))
        assertFalse(reveal(belowCompose = true, hasError = true))
    }

    @Test
    fun inTreeVideoPlatformsNeverReveal() {
        // Android / JVM 都满足「已绑 + 已就绪 + 非缓冲 + 无错」，仍必须保持黑底。
        assertFalse(reveal(belowCompose = false))
        assertFalse(reveal(belowCompose = false, buffering = true))
        assertFalse(reveal(belowCompose = false, hasError = true))
    }

    @Test
    fun androidUnitTestTargetUsesTheInTreeLayering() {
        // :app 是 Android 壳；若有人把 commonMain 的 actual 改成 true，这里先失败。
        assertFalse("Android 宿主不应启用下层视频打洞", videoSurfaceIsBelowCompose)
    }
}
