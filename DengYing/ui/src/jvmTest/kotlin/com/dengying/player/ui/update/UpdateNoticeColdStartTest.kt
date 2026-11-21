package com.dengying.player.ui.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 冷启动自动更新的「一次」语义（计划 14 目标 9）。
 *
 * 回归的 bug：导航根层的 `LaunchedEffect(Unit)` 在每次进入根图时都会触发，
 * 切页/重建都算一次，于是每回根页面都可能弹更新框。闸门只放行进程内第一次冷启动；
 * 手动检查走 `manualCheck`，不受闸门约束。
 */
class UpdateNoticeColdStartTest {

    @Test
    fun 闸门只放行一次() {
        val gate = ColdStartGate()
        assertTrue(gate.tryConsume(), "首次冷启动应放行")
        assertFalse(gate.tryConsume(), "同一进程内第二次进入根图不得再自动检查")
        assertFalse(gate.tryConsume())
    }

    @Test
    fun 两个闸门互不影响() {
        val a = ColdStartGate()
        val b = ColdStartGate()
        assertTrue(a.tryConsume())
        assertTrue(b.tryConsume(), "闸门是实例状态，不是进程单例以外的共享标记")
    }
}
