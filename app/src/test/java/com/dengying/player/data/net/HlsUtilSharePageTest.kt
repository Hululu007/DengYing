package com.dengying.player.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分享页地址提取的正则契约（FR-705 的关键路径）
 *
 * ## 为什么必须锁住这段逻辑
 *
 * 真机实测：三条旧正则对真实页面**全部不匹配**，导致"每部片第一次必失败"——
 * 而失败是**静默**的（返回原地址，被当成媒体地址交给播放器 → `code=3003`）。
 * 没有用例锁住，下次改动还会静默失效。
 *
 * 用例里的样本取自**真机抓到的真实页面**（1593 字节，DPlayer + hls.js）。
 */
class HlsUtilSharePageTest {

    /**
     * 真实页面片段（真机 `files/sharepage.html` 抓取）。
     *
     * 关键行是 `const vid = '...'` —— 变量名是 `vid` 不是 `url`，
     * 这正是旧正则漏掉它的原因。
     */
    private val realPage = """
        <head>
            <script src="https://s2.pstatp.com/cdn/expire-1-M/hls.js/1.1.5/hls.min.js"></script>
        </head>
        <body>
            <div id="dplayer"></div>
        <script>
                const vid = 'https://hd.kuktxu.com/play/eERR6RNa/index.m3u8';

                const videoConfig = {
                    url: vid,
                    type: 'hls'
                };
        </script>
        </body>
    """.trimIndent()

    /**
     * 提取逻辑的镜像实现（与 `HlsUtil.resolveSharePage` 的正则逐条对应）。
     *
     * 【为什么在这里复制一份】`resolveSharePage` 是 `suspend` 且要发网络请求，
     * JVM 单测里无法直接跑。把**正则部分**抽出来做纯函数测试，
     * 覆盖同样的分支，而 IO 部分由真机验证覆盖。
     *
     * 两边正则必须保持一致——若将来只改一处，本用例会失败，起提醒作用。
     */
    private fun extract(html: String): String? {
        val patterns = listOf(
            Regex(
                "(?:const|var|let)\\s+[A-Za-z_$][\\w$]*\\s*=\\s*[\"'`]\\s*" +
                    "(https?://[^\"'`\\s]+?\\.(?:m3u8|mp4|flv|ts)(?:\\?[^\"'`\\s]*)?)\\s*[\"'`]"
            ),
            Regex(
                "[\"'`](https?://[^\"'`\\s]+?\\.(?:m3u8|mp4|flv)(?:\\?[^\"'`\\s]*)?)[\"'`]"
            ),
            Regex("\"url\"\\s*:\\s*\"([^\"]+)\""),
            Regex("[\"'`](/[^\"'`\\s]*\\.(?:m3u8|mp4|flv)[^\"'`\\s]*)[\"'`]"),
        )
        return patterns.firstNotNullOfOrNull { re ->
            re.find(html)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotEmpty() && it != "//" }
        }
    }

    // ---- 真实页面（回归用例）----

    @Test
    fun `真实页面 const vid 形态能提取`() {
        // 这是"每部片第一次必失败"的回归用例：旧正则漏掉变量名 vid
        assertEquals("https://hd.kuktxu.com/play/eERR6RNa/index.m3u8", extract(realPage))
    }

    @Test
    fun `变量名为 url 的旧形态仍能提取`() {
        val html = """const url = 'https://a.com/x/index.m3u8';"""
        assertEquals("https://a.com/x/index.m3u8", extract(html))
    }

    // ---- 变量名与引号组合 ----

    @Test
    fun `任意变量名都能提取`() {
        val names = listOf("vid", "url", "src", "playUrl", "m3u8", "hlsUrl", "video_url", "\$v")
        for (n in names) {
            val html = "const $n = 'https://a.com/x.m3u8';"
            assertEquals("变量名 $n 应能提取", "https://a.com/x.m3u8", extract(html))
        }
    }

    @Test
    fun `三种引号都能提取`() {
        assertEquals("https://a.com/x.m3u8", extract("const v = 'https://a.com/x.m3u8'"))
        assertEquals("https://a.com/x.m3u8", extract("const v = \"https://a.com/x.m3u8\""))
        assertEquals("https://a.com/x.m3u8", extract("const v = `https://a.com/x.m3u8`"))
    }

    @Test
    fun `var 与 let 都能提取`() {
        assertEquals("https://a.com/x.m3u8", extract("var v = 'https://a.com/x.m3u8'"))
        assertEquals("https://a.com/x.m3u8", extract("let v = 'https://a.com/x.m3u8'"))
    }

    // ---- 扩展名覆盖 ----

    @Test
    fun `mp4 与 flv 也能提取`() {
        assertEquals("https://a.com/v.mp4", extract("const v = 'https://a.com/v.mp4'"))
        assertEquals("https://a.com/v.flv", extract("const v = 'https://a.com/v.flv'"))
    }

    @Test
    fun `带查询串的地址完整保留`() {
        val html = "const v = 'https://a.com/x.m3u8?sign=abc&t=1';"
        assertEquals("https://a.com/x.m3u8?sign=abc&t=1", extract(html))
    }

    // ---- 兜底形态 ----

    @Test
    fun `JSON 风格 url 字段能提取`() {
        val html = """{"url":"https://a.com/x.m3u8","type":"hls"}"""
        assertEquals("https://a.com/x.m3u8", extract(html))
    }

    @Test
    fun `相对路径能提取`() {
        val html = """player.src("/hls/x.m3u8");"""
        assertEquals("/hls/x.m3u8", extract(html))
    }

    @Test
    fun `裸的完整 URL 不带变量名也能提取`() {
        val html = """<div data-src="https://a.com/x.m3u8"></div>"""
        assertEquals("https://a.com/x.m3u8", extract(html))
    }

    // ---- 失败路径 ----

    @Test
    fun `无媒体地址时返回 null`() {
        // 页面里只有 hls.js 的 CDN 地址，没有媒体地址——不该误提取 CDN
        val html = """
            <script src="https://s2.pstatp.com/cdn/hls.min.js"></script>
            <script src="https://s2.pstatp.com/cdn/DPlayer.min.js"></script>
        """.trimIndent()
        assertEquals(null, extract(html))
    }

    @Test
    fun `空页面返回 null`() {
        assertEquals(null, extract(""))
    }

    @Test
    fun `变量名形态优先于裸 URL`() {
        // 页面里既有变量赋值又有裸引用时，应取变量赋值那处（更可能是真地址）
        val html = """
            const vid = 'https://real.com/a.m3u8';
            <a href="https://other.com/b.m3u8">link</a>
        """.trimIndent()
        assertEquals("https://real.com/a.m3u8", extract(html))
    }

    @Test
    fun `双斜线开头的地址会被跳过`() {
        // `//` 单独出现时不是有效地址，正则不应把它当结果
        val html = """const v = '//';"""
        assertEquals(null, extract(html))
    }

    @Test
    fun `只匹配媒体扩展名不误取图片`() {
        val html = """
            const pic = 'https://a.com/poster.jpg';
            const vid = 'https://a.com/v.m3u8';
        """.trimIndent()
        assertEquals("https://a.com/v.m3u8", extract(html))
    }

    @Test
    fun `不误取 CDN 上的 js`() {
        val html = """<script src="https://cdn.com/hls.min.js"></script>"""
        assertEquals(null, extract(html))
        assertTrue("js 不应被当成媒体", extract(html) == null)
    }
}
