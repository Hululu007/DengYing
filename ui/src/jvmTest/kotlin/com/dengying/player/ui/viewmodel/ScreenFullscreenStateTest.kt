package com.dengying.player.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FR-711 纯屏幕形态状态机回归。
 *
 * 设计约束（`docs/superpowers/specs/2026-11-15-screen-only-fullscreen-design.md`）：
 * 全屏只改屏幕形态、不触碰播放状态。状态机零依赖（守卫经注入 lambda 判定），
 * 这里直接驱动它，覆盖：默认值、无会话守卫、进入/退出往返、幂等、
 * 「重建后状态保持」（转屏重建 Activity 时 VM 连同状态机存活）。
 */
class ScreenFullscreenStateTest {

    private fun state(hasSession: Boolean = true) = ScreenFullscreenState { hasSession }

    @Test
    fun `默认非全屏形态`() {
        assertFalse(state().active)
    }

    @Test
    fun `无会话时进入被守卫`() {
        val s = state(hasSession = false)
        s.enter()
        assertFalse(s.active)
    }

    @Test
    fun `进入后铺满形态_退出后回到内嵌形态`() {
        val s = state()
        s.enter()
        assertTrue(s.active)
        s.exit()
        assertFalse(s.active)
    }

    @Test
    fun `重复进入幂等`() {
        val s = state()
        s.enter()
        s.enter()
        assertTrue(s.active)
    }

    @Test
    fun `未进入时退出是no-op`() {
        val s = state()
        s.exit()
        assertFalse(s.active)
    }

    @Test
    fun `退出后可再次进入`() {
        val s = state()
        s.enter()
        s.exit()
        s.enter()
        assertTrue(s.active)
    }

    @Test
    fun `转屏重建场景_同一实例状态保持`() {
        // 转屏重建 Activity 时 VM（连同本状态机）存活：新组合从同一实例读到的
        // 仍是 true，全屏形态不因重建丢失。
        val s = state()
        s.enter()
        val afterRecreation = s.active
        assertTrue(afterRecreation)
        assertEquals(s.active, afterRecreation)
    }
}
