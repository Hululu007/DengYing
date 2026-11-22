package com.dengying.player.domain.ui

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * 播放地址校验的契约用例（SEC-005）
 *
 * 两条验收：只接受 http/https；拒绝内网与回环。
 * 另加一条实测修正：「命中清单」是放行加速而非唯一准入——
 * 否则会误杀 CDN 线路（媒体在 cdn-3.example.net，而源 api 在 xxxapi.com）。
 */
class UrlGuardTest {

    private val knownHosts = listOf("jyzyapi.com", "huyazy.net", "www.wdbay.com")

    // ---- 协议白名单 ----

    @Test
    fun `只接受 http 与 https`() {
        assertTrue(UrlGuard.checkPlayUrl("http://jyzyapi.com/a.m3u8", knownHosts).allowed)
        assertTrue(UrlGuard.checkPlayUrl("https://jyzyapi.com/a.m3u8", knownHosts).allowed)
    }

    @Test
    fun `拒绝非 http 协议`() {
        for (u in listOf(
            "file:///etc/passwd",
            "ftp://jyzyapi.com/a.m3u8",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "content://media/external/video/1",
        )) {
            val r = UrlGuard.checkPlayUrl(u, knownHosts)
            assertEquals(UrlGuard.Verdict.RejectScheme, r.verdict, "$u 应被拒绝")
            assertFalse(r.allowed, "$u 不得进入网络层")
        }
    }

    @Test
    fun `空地址判空而不是判协议`() {
        assertEquals(UrlGuard.Verdict.RejectEmpty, UrlGuard.checkPlayUrl("", knownHosts).verdict)
        assertEquals(UrlGuard.Verdict.RejectEmpty, UrlGuard.checkPlayUrl("   ", knownHosts).verdict)
        assertEquals(UrlGuard.Verdict.RejectEmpty, UrlGuard.checkPlayUrl(null, knownHosts).verdict)
    }

    // ---- 内网与回环（威胁模型真正要防的）----

    @Test
    fun `拒绝回环地址`() {
        for (u in listOf("http://127.0.0.1:8080/a", "http://127.1.2.3/a", "http://[::1]/a")) {
            val r = UrlGuard.checkPlayUrl(u, knownHosts)
            assertEquals(UrlGuard.Verdict.RejectPrivate, r.verdict, "$u 应被拒绝")
            assertFalse(r.allowed)
        }
    }

    @Test
    fun `拒绝私有网段`() {
        for (u in listOf(
            "http://10.0.0.5/a",
            "http://172.16.0.1/a",
            "http://172.31.255.254/a",
            "http://192.168.1.1/a",
            "http://[fd00::1]/a",
        )) {
            assertFalse(UrlGuard.checkPlayUrl(u, knownHosts).allowed, "$u 应被拒绝")
        }
    }

    @Test
    fun `拒绝链路本地地址含云元数据服务`() {
        // 169.254.169.254 是 SSRF 的经典目标
        assertEquals(
            UrlGuard.Verdict.RejectPrivate,
            UrlGuard.checkPlayUrl("http://169.254.169.254/latest/meta-data/", knownHosts).verdict,
        )
    }

    @Test
    fun `拒绝 localhost 别名`() {
        for (u in listOf("http://localhost/a", "http://LOCALHOST/a", "http://foo.localhost/a")) {
            assertFalse(UrlGuard.checkPlayUrl(u, knownHosts).allowed, "$u 应被拒绝")
        }
    }

    @Test
    fun `172 网段只拒绝 16 到 31`() {
        // 172.15 与 172.32 都是公网——按 172 整段拒绝会误杀
        assertTrue(UrlGuard.checkPlayUrl("http://172.15.0.1/a", knownHosts).allowed)
        assertTrue(UrlGuard.checkPlayUrl("http://172.32.0.1/a", knownHosts).allowed)
        assertFalse(UrlGuard.checkPlayUrl("http://172.16.0.1/a", knownHosts).allowed)
    }

    // ---- 清单命中是加速而非准入 ----

    @Test
    fun `命中清单的地址判 ok`() {
        val r = UrlGuard.checkPlayUrl("https://jyzyapi.com/provide/vod/", knownHosts)
        assertEquals(UrlGuard.Verdict.Ok, r.verdict)
        assertTrue(r.allowed)
        assertFalse(r.offList, "命中清单不算 offList")
    }

    @Test
    fun `未命中清单的公网地址放行但标记 offList`() {
        // 媒体地址普遍来自 CDN，域名与源 api 主机不同。
        // 严格白名单会把绝大多数可播线路误杀。
        val r = UrlGuard.checkPlayUrl("https://cdn-3.example.net/seg/1.ts", knownHosts)
        assertEquals(UrlGuard.Verdict.OkOffList, r.verdict)
        assertTrue(r.allowed, "公网 CDN 必须放行")
        assertTrue(r.offList, "但要标记供上层收紧")
    }

    // ---- 主机提取 ----

    @Test
    fun `主机提取去除端口与用户信息`() {
        assertEquals(UrlGuard.hostOf("https://jyzyapi.com/a/b?c=1#d"), "jyzyapi.com")
        assertEquals(UrlGuard.hostOf("https://jyzyapi.com:8443/a"), "jyzyapi.com")
        assertEquals(UrlGuard.hostOf("https://user:pw@jyzyapi.com/a"), "jyzyapi.com")
        assertEquals(UrlGuard.hostOf("http://[::1]:8080/a"), "::1")
    }

    @Test
    fun `主机提取对畸形输入返回空串`() {
        assertEquals(UrlGuard.hostOf("not a url"), "")
        assertEquals(UrlGuard.hostOf(""), "")
    }

    @Test
    fun `来源清单主机集合去重`() {
        val hosts = UrlGuard.hostsOf(
            listOf(
                "https://jyzyapi.com/provide/vod/",
                "https://jyzyapi.com/api.php",
                "https://huyazy.net/api.php/provide/vod/",
            )
        )
        assertEquals(2, hosts.size)
        assertTrue(hosts.contains("jyzyapi.com"))
        assertTrue(hosts.contains("huyazy.net"))
    }
}
