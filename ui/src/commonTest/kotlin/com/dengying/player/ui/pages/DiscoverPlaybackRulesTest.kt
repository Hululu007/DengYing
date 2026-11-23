package com.dengying.player.ui.pages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 发现页播放门控的纯规则回归。
 *
 * 对应 2026-11-22 用户反馈的六个问题里的四个：误报看不了、海报残影、
 * 进详情断点、缓冲不转圈。这些规则在 [DiscoverPlaybackRules] 里，页面只消费。
 */
class DiscoverPlaybackRulesTest {

    @Test
    fun 非中心卡与未就绪中心卡仍画海报() {
        assertTrue(DiscoverPlaybackRules.showPoster(isCenter = false, mediaReady = true))
        assertTrue(DiscoverPlaybackRules.showPoster(isCenter = true, mediaReady = false))
    }

    @Test
    fun 中心卡媒体就绪后不画海报_防FIT黑边透出旧图() {
        assertFalse(DiscoverPlaybackRules.showPoster(isCenter = true, mediaReady = true))
    }

    @Test
    fun 中心失败项按次数重试_用尽才报看不了() {
        assertTrue(DiscoverPlaybackRules.shouldRetry(isCenter = true, failed = true, attempts = 0))
        assertTrue(
            DiscoverPlaybackRules.shouldRetry(
                isCenter = true, failed = true, attempts = DiscoverPlaybackRules.MAX_CENTER_RETRY - 1,
            ),
        )
        assertFalse(
            DiscoverPlaybackRules.shouldRetry(
                isCenter = true, failed = true, attempts = DiscoverPlaybackRules.MAX_CENTER_RETRY,
            ),
        )
        assertFalse(DiscoverPlaybackRules.shouldRetry(isCenter = true, failed = false, attempts = 0))
        assertFalse(DiscoverPlaybackRules.shouldRetry(isCenter = false, failed = true, attempts = 0))

        // 重试期间不报错，用尽才报错——真机上「发现页报看不了、进详情又能播」
        // 就是解析链临时失败被当成了终局。
        assertFalse(DiscoverPlaybackRules.showFailure(failed = true, attempts = 0))
        assertFalse(
            DiscoverPlaybackRules.showFailure(
                failed = true, attempts = DiscoverPlaybackRules.MAX_CENTER_RETRY - 1,
            ),
        )
        assertTrue(
            DiscoverPlaybackRules.showFailure(
                failed = true, attempts = DiscoverPlaybackRules.MAX_CENTER_RETRY,
            ),
        )
        assertFalse(
            DiscoverPlaybackRules.showFailure(
                failed = false, attempts = DiscoverPlaybackRules.MAX_CENTER_RETRY,
            ),
        )
    }

    @Test
    fun 快照为空但全部源未跑完时不能报看不了() {
        // DVP §10：重试用尽 + 解析为空，但跨源还只是 6s 快照 → 继续转圈
        assertFalse(
            DiscoverPlaybackRules.finalFailure(
                emptyUrl = true,
                attempts = DiscoverPlaybackRules.MAX_CENTER_RETRY,
                allSourcesDone = false,
            ),
        )
        // 全部源跑完仍为空才是真结论
        assertTrue(
            DiscoverPlaybackRules.finalFailure(
                emptyUrl = true,
                attempts = DiscoverPlaybackRules.MAX_CENTER_RETRY,
                allSourcesDone = true,
            ),
        )
        // 有地址就不报失败；没重试完也不报
        assertFalse(
            DiscoverPlaybackRules.finalFailure(
                emptyUrl = false,
                attempts = DiscoverPlaybackRules.MAX_CENTER_RETRY,
                allSourcesDone = true,
            ),
        )
        assertFalse(
            DiscoverPlaybackRules.finalFailure(emptyUrl = true, attempts = 0, allSourcesDone = true),
        )
    }

    @Test
    fun 转圈在解析中与缓冲期可见_失败态不转() {
        assertTrue(DiscoverPlaybackRules.showSpinner(resolving = true, buffering = false, failed = false))
        assertTrue(DiscoverPlaybackRules.showSpinner(resolving = false, buffering = true, failed = false))
        assertFalse(DiscoverPlaybackRules.showSpinner(resolving = true, buffering = true, failed = true))
        assertFalse(DiscoverPlaybackRules.showSpinner(resolving = false, buffering = false, failed = false))
    }

    @Test
    fun 进详情断点只在媒体就绪时带过去() {
        assertEquals(12_345L, DiscoverPlaybackRules.handoffStartAtMs(true, 12_345L))
        assertEquals(-1L, DiscoverPlaybackRules.handoffStartAtMs(false, 12_345L))
        assertEquals(-1L, DiscoverPlaybackRules.handoffStartAtMs(true, -1L))
    }

    @Test
    fun 返回发现页只在接过本卡媒体时恢复原位播放() {
        assertTrue(
            DiscoverPlaybackRules.shouldResumeDiscoveryAfterDetail(
                adoptedFromDiscovery = true,
                previewMediaAvailable = true,
            ),
        )
        assertFalse(
            DiscoverPlaybackRules.shouldResumeDiscoveryAfterDetail(
                adoptedFromDiscovery = false,
                previewMediaAvailable = true,
            ),
        )
        assertFalse(
            DiscoverPlaybackRules.shouldResumeDiscoveryAfterDetail(
                adoptedFromDiscovery = true,
                previewMediaAvailable = false,
            ),
        )
    }

    @Test
    fun 详情路由交接位置识别() {
        assertTrue(DiscoverPlaybackRules.isDiscoveryHandoffRoute("detail?vodId=1&startAtMs=0"))
        assertTrue(
            DiscoverPlaybackRules.isDiscoveryHandoffRoute(
                "detail?vodId=1&startAtMs=12345&autoPlay=true",
            ),
        )
        assertFalse(DiscoverPlaybackRules.isDiscoveryHandoffRoute("detail?vodId=1&startAtMs=-1"))
        assertFalse(DiscoverPlaybackRules.isDiscoveryHandoffRoute("detail?vodId=1"))
        assertEquals(12345L, DiscoverPlaybackRules.routeStartAtMs("detail?startAtMs=12345&x=1"))
        assertEquals(-1L, DiscoverPlaybackRules.routeStartAtMs("detail?startAtMs=bad"))
    }

    @Test
    fun 普通详情入口不被别的片会话激活() {
        assertFalse(
            DiscoverPlaybackRules.shouldActivateInlineSession(
                sessionBelongsToPage = false,
                adoptedFromDiscovery = false,
                displayEngineActive = false,
            ),
        )
        assertTrue(
            DiscoverPlaybackRules.shouldActivateInlineSession(
                sessionBelongsToPage = true,
                adoptedFromDiscovery = false,
                displayEngineActive = false,
            ),
        )
        assertTrue(
            DiscoverPlaybackRules.shouldActivateInlineSession(
                sessionBelongsToPage = false,
                adoptedFromDiscovery = true,
                displayEngineActive = false,
            ),
        )
        assertTrue(
            DiscoverPlaybackRules.shouldActivateInlineSession(
                sessionBelongsToPage = false,
                adoptedFromDiscovery = false,
                displayEngineActive = true,
            ),
        )
    }
}
