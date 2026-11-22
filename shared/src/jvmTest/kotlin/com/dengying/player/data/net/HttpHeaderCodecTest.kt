package com.dengying.player.data.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 桥响应头线格式 → 头表（[HttpHeaderCodec]）。
 *
 * 重点覆盖登录链路真正依赖的三件事：多值 `set-cookie` 不丢、
 * 头名大小写归一、畸形输入不抛异常。
 */
class HttpHeaderCodecTest {

    @Test
    fun `多值 set-cookie 逐行保留并用分号拼接`() {
        val text = "Set-Cookie: nbmovie_session=abc123; Path=/; HttpOnly\n" +
            "Set-Cookie: other=xyz; Expires=Wed, 21 Oct 2026 07:28:00 GMT\n"

        val headers = HttpHeaderCodec.decode(text)

        assertEquals(1, headers.size)
        val cookie = headers["set-cookie"]
        assertTrue(cookie != null)
        // 两条都保留；第二条里的逗号（Expires）不能被当成分隔符切掉
        assertTrue(cookie.contains("nbmovie_session=abc123"))
        assertTrue(cookie.contains("other=xyz; Expires=Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals("nbmovie_session=abc123; Path=/; HttpOnly; other=xyz; Expires=Wed, 21 Oct 2026 07:28:00 GMT", cookie)
    }

    @Test
    fun `头名统一小写`() {
        val headers = HttpHeaderCodec.decode("Content-Type: application/json\nX-Trace-ID: 42\n")

        assertEquals("application/json", headers["content-type"])
        assertEquals("42", headers["x-trace-id"])
        assertTrue(headers.keys.all { it == it.lowercase() })
    }

    @Test
    fun `空输入与 null 返回空表`() {
        assertEquals(emptyMap(), HttpHeaderCodec.decode(null))
        assertEquals(emptyMap(), HttpHeaderCodec.decode(""))
        assertEquals(emptyMap(), HttpHeaderCodec.decode("\n\r\n\n"))
    }

    @Test
    fun `畸形行忽略且不影响其它头`() {
        val text = "not-a-header\n" +
            ": empty-name\n" +
            "\n" +
            "Good: value\n" +
            "  X-Spaced  :   padded value   \n"

        val headers = HttpHeaderCodec.decode(text)

        assertEquals(2, headers.size)
        assertEquals("value", headers["good"])
        assertEquals("padded value", headers["x-spaced"])
    }

    @Test
    fun `CRLF 行尾可解析`() {
        val headers = HttpHeaderCodec.decode("Set-Cookie: a=1\r\nSet-Cookie: b=2\r\n")

        assertEquals("a=1; b=2", headers["set-cookie"])
    }
}
