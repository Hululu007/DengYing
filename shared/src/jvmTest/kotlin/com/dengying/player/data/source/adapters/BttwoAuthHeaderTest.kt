package com.dengying.player.data.source.adapters

import com.dengying.player.data.net.HttpHeaderCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 登录链路回归：鸿蒙桥回传的响应头文本经 [HttpHeaderCodec] 解析后，
 * [BttwoAuth.parseSession] 必须能取到 `nbmovie_session`。
 *
 * 这是审计 B-11 的验收点——此前鸿蒙侧 headers 恒为空，登录必然失败。
 */
class BttwoAuthHeaderTest {

    @Test
    fun `从多值 set-cookie 中提取 nbmovie_session`() {
        val wire = "Set-Cookie: nbmovie_session=sess_abc123; Path=/; HttpOnly; Secure\n" +
            "Set-Cookie: theme=dark; Path=/\n"

        val headers = HttpHeaderCodec.decode(wire)

        assertEquals("sess_abc123", BttwoAuth.parseSession(headers["set-cookie"]))
    }

    @Test
    fun `多条 set-cookie 且会话不在第一条也能命中`() {
        val wire = "set-cookie: theme=dark; Expires=Wed, 21 Oct 2026 07:28:00 GMT\n" +
            "set-cookie: nbmovie_session=sess_second\n"

        val headers = HttpHeaderCodec.decode(wire)

        assertEquals("sess_second", BttwoAuth.parseSession(headers["set-cookie"]))
    }

    @Test
    fun `没有 set-cookie 时返回 null`() {
        val headers = HttpHeaderCodec.decode("Content-Type: application/json\n")

        assertNull(BttwoAuth.parseSession(headers["set-cookie"]))
    }
}
