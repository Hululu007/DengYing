package com.dengying.player.data.download

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 下载内容守卫：真机抓到 token 源换链失败时把站点播放页（HTML）当 mp4 存下来，
 * 离线播放报 3003。这里锁住识别口径。
 */
class DownloadContentGuardTest {

    @Test
    fun htmlDocumentIsRejected() {
        val html = "<!DOCTYPE html><html lang=\"zh-CN\"><head>".encodeToByteArray()
        assertTrue(DownloadCenter.looksLikeHtmlPage(html))
    }

    @Test
    fun htmlWithoutDoctypeIsRejected() {
        val html = "<html><body>播放页</body></html>".encodeToByteArray()
        assertTrue(DownloadCenter.looksLikeHtmlPage(html))
    }

    @Test
    fun scriptOnlyLandingPageIsRejected() {
        // 真机抓到的优酷转码页：1537 字节，开头就是 <script>，没有 doctype/html
        val page = (
            "<script>sessionStorage.x5referer = window.location.href;var url = " +
                "window.location.protocol + \"//v.youku.com//v_show/id_X"
            ).encodeToByteArray()
        assertTrue(DownloadCenter.looksLikeHtmlPage(page))
    }

    @Test
    fun xmlDocumentIsRejected() {
        assertTrue(DownloadCenter.looksLikeHtmlPage("<?xml version=\"1.0\"?><rss>".encodeToByteArray()))
    }

    @Test
    fun mp4HeaderIsAccepted() {
        // ISO BMFF: 00 00 00 18 'ftyp' ...
        val mp4 = byteArrayOf(0, 0, 0, 0x18) + "ftypisom".encodeToByteArray() + ByteArray(32)
        assertFalse(DownloadCenter.looksLikeHtmlPage(mp4))
    }

    @Test
    fun emptyBytesAreAcceptedByThisGuard() {
        // 空内容由 downloadFile 的字节数检查负责，这里不抢职责
        assertFalse(DownloadCenter.looksLikeHtmlPage(ByteArray(0)))
    }
}
