package com.dengying.player.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 亮屏续播判定（计划 14 目标 3）。
 *
 * 回归的是这条 bug：`MainActivity.onStart` 无条件 `peek()?.play()`，
 * 于是熄屏/切后台回来的第一帧就开始播——哪怕用户是自己按暂停才离开的，
 * 哪怕播放器已经被销毁。判定收敛成纯函数，只为让这两条边界可测。
 */
class ForegroundResumePolicyTest {

    @Test
    fun 熄屏前在播且有画面时恢复() {
        assertTrue(shouldResumeAfterStop(wasPlayingBeforeStop = true, surfaceCount = 1))
    }

    @Test
    fun 用户主动暂停后回前台不恢复() {
        assertFalse(shouldResumeAfterStop(wasPlayingBeforeStop = false, surfaceCount = 1))
    }

    @Test
    fun 没有存活画面时回前台不恢复() {
        assertFalse(shouldResumeAfterStop(wasPlayingBeforeStop = true, surfaceCount = 0))
        assertFalse(shouldResumeAfterStop(wasPlayingBeforeStop = false, surfaceCount = 0))
    }
}
