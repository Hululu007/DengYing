package com.dengying.player.domain.playback

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * 滑动 seek 预览的契约用例（FR-708 / `docs/08` P-02）。
 *
 * 覆盖真机反馈的三件事：
 *  1. 滑过多少距离对应多少时长（口径不许变）
 *  2. 差值必须以**手势起点**为基准——用当前播放位置算会自己缩水
 *  3. seek 落地前不采信引擎位置，否则界面先跳回旧位置
 */
class SeekPreviewTest {

    private val total = 6_000_000L   // 100 分钟

    @Test
    fun `滑过一个屏幕宽度走完整片`() {
        assertEquals(total, SeekPreview.targetOf(fromMs = 0, deltaX = 1000f, viewWidth = 1000f, totalMs = total))
    }

    @Test
    fun `半个屏幕宽度走一半`() {
        assertEquals(
            total / 2,
            SeekPreview.targetOf(fromMs = 0, deltaX = 500f, viewWidth = 1000f, totalMs = total),
        )
    }

    @Test
    fun `向左滑回退但不越过片头`() {
        // 起点 30 秒，向左滑一屏 → 想退到 -70 分钟，必须夹到 0
        assertEquals(
            0L,
            SeekPreview.targetOf(fromMs = 30_000, deltaX = -1000f, viewWidth = 1000f, totalMs = total),
        )
        assertEquals(
            0L,
            SeekPreview.targetOf(fromMs = 3_000_000, deltaX = -500f, viewWidth = 1000f, totalMs = total),
        )
    }

    @Test
    fun `向右滑不越过片尾`() {
        assertEquals(
            total,
            SeekPreview.targetOf(fromMs = total - 10_000, deltaX = 800f, viewWidth = 1000f, totalMs = total),
        )
    }

    @Test
    fun `时长或宽度未知时保持起点不崩`() {
        assertEquals(12_345L, SeekPreview.targetOf(12_345L, 400f, 1000f, totalMs = 0))
        assertEquals(12_345L, SeekPreview.targetOf(12_345L, 400f, 1000f, totalMs = -1))
        assertEquals(9_000L, SeekPreview.targetOf(9_000L, 400f, viewWidth = 0f, totalMs = total))
    }

    @Test
    fun `差额文案带符号且省略不足一秒`() {
        assertEquals(SeekPreview.deltaLabel(60_000, 140_000), "+1:20")
        assertEquals(SeekPreview.deltaLabel(140_000, 105_000), "-0:35")
        assertEquals(SeekPreview.deltaLabel(60_000, 60_400), "")
        assertEquals(SeekPreview.deltaLabel(60_000, 70_500), "+0:10")
    }

    @Test
    fun `零位移时方向归快进，与空差值不矛盾`() {
        // 空差值 = 没动；此时方向图标不能是"倒退"
        assertTrue(SeekPreview.isForward(fromMs = 60_000, targetMs = 60_000))
        assertEquals(SeekPreview.deltaLabel(60_000, 60_000), "")
    }

    /**
     * 回归用例：差值基准必须是**手势起点**。
     *
     * 旧实现浮层用 `state.curTime` 算差值，而 `curTime` 每秒被播放进度覆盖一次，
     * 于是同一次按压停住不动时，`+1:20` 会一路缩到 `+0:50`。
     * 现在起点是显式输入：只要手指不动，差值就不变。
     */
    @Test
    fun `手指不动时差值不随播放进度缩水`() {
        val from = 60_000L
        val target = 140_000L
        // 手指停在原地，播放位置自己往前走了 30 秒
        val curTime = 90_000L
        assertEquals(SeekPreview.deltaLabel(from, target), "+1:20")
        // 用当时的播放位置算，就会得到缩水后的差值——这正是要避免的那条路径
        assertEquals(SeekPreview.deltaLabel(curTime, target), "+0:50")
    }

    @Test
    fun `seek 落地前不采信引擎位置`() {
        val target = 3_000_000L
        // 引擎还在旧位置（落后 1 分钟）→ 不采信，界面保持目标值
        assertFalse(SeekPreview.shouldAdoptEnginePosition(2_940_000, target))
        // 已经到目标附近（容差 1.5 秒内）→ 采信
        assertTrue(SeekPreview.shouldAdoptEnginePosition(3_000_400, target))
        assertTrue(SeekPreview.shouldAdoptEnginePosition(2_999_000, target))
        // 没有待生效的 seek → 一律采信
        assertTrue(SeekPreview.shouldAdoptEnginePosition(123_456, pendingTargetMs = 0))
    }
}
