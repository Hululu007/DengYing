package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSources
import com.dengying.player.core.CAPS_XPTV
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.SourceProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XptvParserTest {

    private val source = CmsSources.byKey("hulu_xptv_cg")!!

    @Test
    fun sourceCatalogDeclaresXptvWithTokenCaps() {
        assertEquals(SourceProtocol.Xptv, source.protocol)
        assertEquals(CAPS_XPTV, source.caps)
        // 2026-11-21 停用：站点内容不符合接入规范，标题黑名单覆盖不到整站，见 docs/04 §16.88.6。
        // 解析层单测仍按 key 拿配置跑，不受 enabled 影响；改回 true 必须先有整站级判定。
        assertFalse(source.enabled)
        assertTrue(source.api.startsWith("https://"))
    }

    @Test
    fun nuxtFlatArrayResolvesIndirectTitleCoverAndRoute() {
        val html = """
            <html><script id="__NUXT_DATA__" type="application/json">
            [{"title":1,"slug":2,"detail_route":3,"cover":4,"total_episode_count":6},"曹操穿越10国","caocao","/drama/caocao",{"url":5},"https://img.example/cover.jpg",9]
            </script></html>
        """.trimIndent()

        val result = XptvParser.list(html, source, 1)

        assertEquals(RequestOutcome.SuccessWithData, result.outcome)
        assertEquals(1, result.items.size)
        val item = result.items.single()
        assertEquals("曹操穿越10国", item.vod_name)
        assertEquals("https://img.example/cover.jpg", item.vod_pic)
        assertEquals("/drama/caocao", item.vod_uri)
        assertEquals("全9集", item.vod_remarks)
        assertEquals("/play/caocao/1", item.vod_play_url)
        assertEquals("hulu_xptv_cg", item._source)
    }

    @Test
    fun adultTitlesAreFilteredBeforeAggregation() {
        val html = """
            <html><script id="__NUXT_DATA__">
            [{"title":1,"slug":2,"detail_route":3},"发情女上司调教指南","adult","/drama/adult"]
            </script></html>
        """.trimIndent()

        val result = XptvParser.list(html, source, 1)

        assertEquals(RequestOutcome.SuccessNoData, result.outcome)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun playPageExtractsM3u8FromNuxtStringArray() {
        val html = """
            <html><script id="__NUXT_DATA__">
            [0,{"playUrl":2},"https://hls.example/v/1.m3u8?auth_key=x"]
            </script></html>
        """.trimIndent()

        assertEquals("https://hls.example/v/1.m3u8?auth_key=x", XptvParser.play(html))
    }

    @Test
    fun brokenPayloadIsParseFailureInsteadOfCrash() {
        val result = XptvParser.list("<html>no data</html>", source, 1)
        assertEquals(RequestOutcome.ParseFailed, result.outcome)
        assertEquals("", XptvParser.play("<html>no data</html>"))
    }
}
