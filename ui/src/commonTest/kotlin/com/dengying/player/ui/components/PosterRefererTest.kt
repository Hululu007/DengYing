package com.dengying.player.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 海报 Referer 规则的回归测试。
 *
 * 【为什么值得单测】豆瓣图床缺 Referer 返回 **418**（2026-11-01 实测），
 * 而且失败态是「灰块」——没有报错、没有日志，肉眼只能看出图没出来。
 * 规则若被改成「全站统一加 Referer」或「一律不加」，都会重新踩回这个坑。
 */
class PosterRefererTest {

    @Test
    fun `豆瓣图床带 movie 域 Referer`() {
        assertEquals(
            "https://movie.douban.com/",
            posterReferer("https://img9.doubanio.com/view/photo/s_ratio_poster/p1.jpg"),
        )
        assertEquals(
            "https://movie.douban.com/",
            posterReferer("https://img1.doubanio.com/f/movie/logo.png"),
        )
    }

    @Test
    fun `子域和裸域都命中`() {
        assertEquals("https://movie.douban.com/", posterReferer("https://doubanio.com/x.jpg"))
        assertEquals("https://movie.douban.com/", posterReferer("https://s3.doubanio.com/x.jpg"))
    }

    @Test
    fun `非豆瓣图床不加 Referer`() {
        assertNull(posterReferer("https://hhmage.com/a.jpg"))
        assertNull(posterReferer("https://img.example.com/p.jpg"))
        // 域名字面里含 doubanio.com 但不是子域：不能被 endsWith("\\.doubanio\\.com") 误伤
        assertNull(posterReferer("https://evil-doubanio.com/a.jpg"))
        assertNull(posterReferer("https://doubanio.com.cn/a.jpg"))
    }

    @Test
    fun `无协议或无路径的 URL 不崩`() {
        assertNull(posterReferer(""))
        assertNull(posterReferer("not a url"))
        assertEquals("https://movie.douban.com/", posterReferer("https://img9.doubanio.com"))
    }
}
