package com.dengying.player.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 续播恢复判定的契约用例（回归 FR-506）。
 *
 * 真机现象：用户手动 seek 到54:31，STATE_READY之后画面被拉回上次的位置。
 * 两个各自独立的原因，各自都足以造成覆盖：
 *
 *  1. 内嵌切全屏时路由漏传 startAtMs，第一道守卫失效
 *     （见 DetailViewModel.toFullscreen）
 *  2. 进度条拖动只置位了 onDragStart，而 Slider 自己消费了拖动手势，
 *     外层 detectTapGestures(onPress=...) 收不到按下信号，manualSeekOccurred 仍是 false
 *
 * 这里锁住的是判定，不是调用点。调用点靠真机探针
 * resume-restore-check load=... why=... 验证。
 */
class ResumeRestoreTest {

    /** 真机日志里那条历史位置（54:31） */
    private val restored = 3_271_638L

    // ---------- 第一阶段：要不要读历史 ----------

    @Test
    fun explicitStartWins() {
        val load = ResumeRestore.shouldLoad(
            startAtMs = 400_000L,
            vodId = 116807L,
            source = "hhzy",
            alreadyRequested = false,
        )
        assertFalse(load.shouldLoad)
        assertEquals("explicit-start", load.reason)
    }

    @Test
    fun noIdentitySkips() {
        // 调试直达（DebugPlayReceiver）不带 vodId，也不该被历史记录劫持
        assertFalse(ResumeRestore.shouldLoad(0L, 0L, "hhzy", false).shouldLoad)
        assertFalse(ResumeRestore.shouldLoad(0L, 116807L, "", false).shouldLoad)
        assertEquals("no-identity", ResumeRestore.shouldLoad(0L, 0L, "", false).reason)
    }

    @Test
    fun alreadyRequestedSkips() {
        // 同一会话里 onReady 可能来多次（缓冲反复），重复 seek 会把用户又弹一次
        val load = ResumeRestore.shouldLoad(0L, 116807L, "hhzy", alreadyRequested = true)
        assertFalse(load.shouldLoad)
        assertEquals("already-requested", load.reason)
    }

    @Test
    fun normalResumeLoads() {
        val load = ResumeRestore.shouldLoad(0L, 116807L, "hhzy", alreadyRequested = false)
        assertTrue(load.shouldLoad)
        assertEquals("load", load.reason)
    }

    // ---------- 第二阶段：读到了要不要真的 seek ----------

    @Test
    fun normalResumeApplies() {
        val apply = ResumeRestore.apply(storedMs = restored, manualSeekOccurred = false)
        assertTrue(apply.shouldSeek)
        assertEquals(restored, apply.targetMs)
        assertEquals("restore", apply.reason)
    }

    @Test
    fun manualSeekNotOverridden() {
        // 这就是缺陷 A 的核心：用户的手动意图比历史记录新
        val apply = ResumeRestore.apply(storedMs = restored, manualSeekOccurred = true)
        assertFalse(apply.shouldSeek)
        assertEquals("manual-seek", apply.reason)
    }

    @Test
    fun nearStartSkips() {
        // 历史位置就在 3 秒内：用户本来就要从头看，多一次 seek 只会闪一下
        val apply = ResumeRestore.apply(storedMs = 2_000L, manualSeekOccurred = false)
        assertFalse(apply.shouldSeek)
 assertEquals("near-start", apply.reason)

        // 边界：恰好等于阈值也算片头附近，避免 3000/3001 的抖动歧义
 assertFalse(ResumeRestore.apply(ResumeRestore.HEAD_GUARD_MS, false).shouldSeek)
        assertTrue(ResumeRestore.apply(ResumeRestore.HEAD_GUARD_MS + 1, false).shouldSeek)
    }

    @Test
    fun noStoredSkips() {
        assertFalse(ResumeRestore.apply(0L, manualSeekOccurred = false).shouldSeek)
    }

    @Test
    fun manualSeekIndependentOfStored() {
        // 两条路径必须独立：历史位置多大都不该让 manual-seek 失效
        listOf(0L, 5_000L, restored, 6_000_000L).forEach { stored ->
            assertFalse(
                ResumeRestore.apply(stored, manualSeekOccurred = true).shouldSeek,
                "stored=$stored 时手动 seek 被历史覆盖了",
            )
        }
    }
}
