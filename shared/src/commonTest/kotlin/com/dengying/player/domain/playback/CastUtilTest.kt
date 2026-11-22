package com.dengying.player.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `CastUtil` 的纯函数契约（FR-419 / FR-423）。
 *
 * PR #6 之前文档明确写「纯函数也没有单测」——这些用例把判定顺序、
 * 入口禁用、文案分流三条口径固化，供投屏通道接线后回归。
 */
class CastUtilTest {

    private val url = "https://cdn.example.com/master.m3u8"

    // ---------------------------------------------------------------- 可投性

    @Test
    fun `isCastableUrl 只认 http 与 https`() {
        assertTrue(CastUtil.isCastableUrl("http://a/b.m3u8"))
        assertTrue(CastUtil.isCastableUrl("HTTPS://a/b.m3u8"))
        assertFalse(CastUtil.isCastableUrl(""))
        assertFalse(CastUtil.isCastableUrl("file:///sdcard/a.mp4"))
        assertFalse(CastUtil.isCastableUrl("C0ffeeToken123"))
        assertFalse(CastUtil.isCastableUrl("rtmp://a/live"))
    }

    @Test
    fun `地址不可用优先于请求头`() {
        val c = CastUtil.decideCastability("", requiresHeaders = true, fromSourceChain = true)
        assertEquals(CastUtil.CastabilityKind.NotCastable, c.kind)
        assertEquals(CastUtil.BlockReason.AddressNotReady, c.reason)
    }

    @Test
    fun `需要专属请求头时不可投`() {
        val c = CastUtil.decideCastability(url, requiresHeaders = true, fromSourceChain = false)
        assertEquals(CastUtil.CastabilityKind.NotCastable, c.kind)
        assertEquals(CastUtil.BlockReason.NeedsHeaders, c.reason)
    }

    @Test
    fun `换链来源判为可试而非可投`() {
        val c = CastUtil.decideCastability(url, requiresHeaders = false, fromSourceChain = true)
        assertEquals(CastUtil.CastabilityKind.Trial, c.kind)
        assertEquals(CastUtil.BlockReason.None, c.reason)
    }

    @Test
    fun `直出地址判为可投`() {
        val c = CastUtil.decideCastability(url, requiresHeaders = false, fromSourceChain = false)
        assertEquals(CastUtil.CastabilityKind.Castable, c.kind)
        assertEquals(CastUtil.BlockReason.None, c.reason)
    }

    @Test
    fun `入口可点性：可投与可试放行，不可投禁用`() {
        assertTrue(CastUtil.isCastEntryEnabled(CastUtil.Castability(CastUtil.CastabilityKind.Castable, CastUtil.BlockReason.None)))
        assertTrue(CastUtil.isCastEntryEnabled(CastUtil.Castability(CastUtil.CastabilityKind.Trial, CastUtil.BlockReason.None)))
        assertFalse(CastUtil.isCastEntryEnabled(CastUtil.Castability(CastUtil.CastabilityKind.NotCastable, CastUtil.BlockReason.NeedsHeaders)))
    }

    @Test
    fun `不可投文案互不相同且可投时为空`() {
        assertEquals("", CastUtil.castBlockText(CastUtil.BlockReason.None))
        val needs = CastUtil.castBlockText(CastUtil.BlockReason.NeedsHeaders)
        val notReady = CastUtil.castBlockText(CastUtil.BlockReason.AddressNotReady)
        assertTrue(needs.isNotEmpty())
        assertTrue(notReady.isNotEmpty())
        assertTrue(needs != notReady)
    }

    // ---------------------------------------------------------------- 失败分流

    @Test
    fun `地址失效优先于链路阶段`() {
        // 即使发生在连接阶段，没有地址也要归到「地址失效」，让用户去重解析而不是反复重连
        for (stage in CastUtil.Stage.entries) {
            assertEquals(
                CastUtil.FailureKind.AddressGone,
                CastUtil.classifyCastFailure(stage, hasAddress = false),
                "stage=$stage 且无地址时必须归 AddressGone",
            )
        }
    }

    @Test
    fun `连接前与连接阶段归设备不可达`() {
        assertEquals(
            CastUtil.FailureKind.DeviceUnreachable,
            CastUtil.classifyCastFailure(CastUtil.Stage.Precheck, hasAddress = true),
        )
        assertEquals(
            CastUtil.FailureKind.DeviceUnreachable,
            CastUtil.classifyCastFailure(CastUtil.Stage.Link, hasAddress = true),
        )
    }

    @Test
    fun `下发与远端阶段归远端拉流失败`() {
        assertEquals(
            CastUtil.FailureKind.RemotePullFail,
            CastUtil.classifyCastFailure(CastUtil.Stage.Handoff, hasAddress = true),
        )
        assertEquals(
            CastUtil.FailureKind.RemotePullFail,
            CastUtil.classifyCastFailure(CastUtil.Stage.Remote, hasAddress = true),
        )
    }

    @Test
    fun `三类失败文案互不相同且非空`() {
        val texts = CastUtil.FailureKind.entries.map { CastUtil.castFailureText(it) }
        assertEquals(3, texts.size)
        assertTrue(texts.all { it.isNotEmpty() })
        assertEquals(texts.size, texts.toSet().size, "三类失败必须给出不同下一步动作")
    }
}
