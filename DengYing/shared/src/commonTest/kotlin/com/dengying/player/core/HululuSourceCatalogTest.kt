package com.dengying.player.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hululu007 MacCMS 编译期清单约束（2026-11-20 聚合筛选 + 2026-11-21 批量接入）。 */
class HululuSourceCatalogTest {

    private val huluSources = CmsSources.ALL.filter { it.key.startsWith("hulu_") && it.protocol == SourceProtocol.Cms }

    @Test
    fun `Hululu 源使用 CMS 协议且全部启用`() {
        // 5 条 2026-11-20 聚合 + 4 条 2026-11-21 批量接入
        assertEquals(9, huluSources.size)
        assertTrue(huluSources.all { it.enabled })
        assertTrue(huluSources.all { it.protocol == SourceProtocol.Cms })
        assertTrue(huluSources.all { it.caps == CAPS_CMS })
        assertTrue(huluSources.all { it.tier == 3 })
        // 11-20 那批是直链；11-21 新接入的 4 条首行是分享页，
        // 由 HlsUtil.isSharePage 识别后换 m3u8，故 direct=false。
        assertTrue(huluSources.filter { it.note?.contains("2026-11-20") == true }.all { it.direct })
        assertTrue(huluSources.filter { it.note?.contains("2026-11-21") == true }.all { !it.direct })
    }

    @Test
    fun `Hululu 源 key 与接口基址唯一`() {
        assertEquals(huluSources.size, huluSources.map { it.key }.toSet().size)
        assertEquals(huluSources.size, huluSources.map { it.api }.toSet().size)
        assertTrue(huluSources.all { it.api.contains("provide/vod") })
        assertTrue(huluSources.all {
            it.note?.contains("2026-11-20") == true || it.note?.contains("2026-11-21") == true
        })
    }
}

/** Hululu007 live.m3u 编译期频道清单约束（2026-11-21 抽样）。 */
class HululuLiveCatalogTest {
    @Test
    fun `直播频道清单非空且 key 唯一`() {
        val live = CmsSources.DIRECT
        assertTrue(live.size >= 5)
        assertEquals(live.size, live.map { it.key }.toSet().size)
        assertTrue(live.all { it.key.startsWith("live_hulu_") })
        assertTrue(live.all { it.url.startsWith("http") && ".m3u8" in it.url })
    }
}
