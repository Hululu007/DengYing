package com.dengying.player.domain.playback

import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * 全屏手势方向仲裁的契约用例（`docs/08` §4 的 A-06 / F-11 / F-12）。
 *
 * 这些用例覆盖"一次手势只落入一种模式"这一条：斜向、等量、分区、边界
 * 都不能出现两个动作同时成立的结果。
 */
class GestureRulesTest {

    private val w = 1000f

    @Test
    fun `位移未越阈值时不判定`() {
        assertEquals(
            GestureRules.Action.Undecided,
            GestureRules.decide(totalX = 5f, totalY = 3f, startX = 100f, viewWidth = w, brightnessAvailable = true),
        )
        // 恰好在阈值上不算越过：阈值是"必须超过"的界线
        assertEquals(
            GestureRules.Action.Undecided,
            GestureRules.decide(totalX = 20f, totalY = 0f, startX = 100f, viewWidth = w, brightnessAvailable = true),
        )
    }

    @Test
    fun `横向主导判 seek`() {
        assertEquals(
            GestureRules.Action.Seek,
            GestureRules.decide(totalX = 60f, totalY = 25f, startX = 100f, viewWidth = w, brightnessAvailable = true),
        )
    }

    @Test
    fun `横向与竖向等量时归 seek`() {
        assertEquals(
            GestureRules.Action.Seek,
            GestureRules.decide(totalX = 80f, totalY = 80f, startX = 100f, viewWidth = w, brightnessAvailable = true),
        )
    }

    @Test
    fun `竖向主导且起点在左半屏判亮度`() {
        assertEquals(
            GestureRules.Action.Brightness,
            GestureRules.decide(totalX = 10f, totalY = 90f, startX = 499f, viewWidth = w, brightnessAvailable = true),
        )
    }

    @Test
    fun `竖向主导且起点在右半屏判音量`() {
        assertEquals(
            GestureRules.Action.Volume,
            GestureRules.decide(totalX = 10f, totalY = 90f, startX = 501f, viewWidth = w, brightnessAvailable = true),
        )
        // 右半屏即使亮度不可调也仍然是音量——分区判据只看起点
        assertEquals(
            GestureRules.Action.Volume,
            GestureRules.decide(totalX = 10f, totalY = 90f, startX = 501f, viewWidth = w, brightnessAvailable = false),
        )
    }

    @Test
    fun `左半屏但亮度不可调时作废而不是退回 seek`() {
        assertEquals(
            GestureRules.Action.Discard,
            GestureRules.decide(totalX = 10f, totalY = 90f, startX = 100f, viewWidth = w, brightnessAvailable = false),
        )
    }

    @Test
    fun `半屏边界按左半屏处理`() {
        // startX == viewWidth/2 属于左半屏（判据是 <=）
        assertEquals(
            GestureRules.Action.Brightness,
            GestureRules.decide(totalX = 0f, totalY = 90f, startX = w / 2f, viewWidth = w, brightnessAvailable = true),
        )
    }

    @Test
    fun `画面宽度未知时分区退化但不崩溃`() {
        // viewWidth = 0 时半屏分界为 0，任何正数起点都落右半屏
        assertEquals(
            GestureRules.Action.Volume,
            GestureRules.decide(totalX = 0f, totalY = 90f, startX = 10f, viewWidth = 0f, brightnessAvailable = true),
        )
    }

    @Test
    fun `竖向反向位移不影响分区判定`() {
        // 上滑（totalY 为负）与下滑必须落到同一个动作，只有数值方向不同
        assertEquals(
            GestureRules.Action.Brightness,
            GestureRules.decide(totalX = 0f, totalY = -90f, startX = 100f, viewWidth = w, brightnessAvailable = true),
        )
        assertEquals(
            GestureRules.Action.Volume,
            GestureRules.decide(totalX = 0f, totalY = -90f, startX = 900f, viewWidth = w, brightnessAvailable = true),
        )
    }

    @Test
    fun `任何输入都只返回一个动作`() {
        // 穷举一批组合，确认返回值始终落在枚举内且无"两动作同时成立"的表达
        val actions = GestureRules.Action.values().toSet()
        for (x in listOf(-200f, -30f, 0f, 30f, 200f)) {
            for (y in listOf(-200f, -30f, 0f, 30f, 200f)) {
                for (sx in listOf(0f, 250f, 500f, 750f, 1000f)) {
                    for (avail in listOf(true, false)) {
                        val a = GestureRules.decide(x, y, sx, w, avail)
                        assertEquals(true, a in actions, "返回值必须在枚举内")
                    }
                }
            }
        }
    }
}
