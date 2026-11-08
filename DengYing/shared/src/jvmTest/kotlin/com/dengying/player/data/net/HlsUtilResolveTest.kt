package com.dengying.player.data.net

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 解析链入口的「无协议必败」闸（2026-11-08）。
 *
 * 背景：token 型源换链失败时（tv189 VIP 内容 917 无试看流 → resolvePlay
 * 空手而归），resolveChain 会把裸 CID 原样送进解析链；旧逻辑在
 * `resolveMasterPlaylist` 失败后仍以 `resolved=true` 放行，CID 交给
 * ExoPlayer 抛 `MalformedURLException: no protocol`，用户只看到
 * 「播放失败（解码错误或地址失效）」。修复后无协议串直接判解析失败，
 * 让解析链走换源兜底、最终给出可读错误。
 *
 * 只测**不触网**的短路径：闸门在分享页判断之前短路。
 */
class HlsUtilResolveTest {

    @Test
    fun `bare cid is rejected as unresolved`() = runBlocking {
        val oc = HlsUtil.resolvePlayUrlOrFail("C47346905")
        assertFalse(oc.resolved, "裸 CID 不能判 resolved=true（会原样进播放器）")
        assertEquals("C47346905", oc.url)
    }

    @Test
    fun `plain text without protocol is rejected as unresolved`() = runBlocking {
        val oc = HlsUtil.resolvePlayUrlOrFail("not-a-url")
        assertFalse(oc.resolved)
        assertEquals("not-a-url", oc.url)
    }

    @Test
    fun `empty input is rejected as unresolved`() = runBlocking {
        val oc = HlsUtil.resolvePlayUrlOrFail("")
        assertFalse(oc.resolved)
        assertEquals("", oc.url)
    }
}
