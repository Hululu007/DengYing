package com.dengying.player.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hululu007 MacCMS 编译期清单约束（2026-11-20 聚合筛选）。 */
class HululuSourceCatalogTest {

    private val huluSources = CmsSources.ALL.filter { it.key.startsWith("hulu_") }

    @Test
    fun `Hululu 源使用 CMS 协议且全部启用`() {
        assertEquals(5, huluSources.size)
        assertTrue(huluSources.all { it.enabled })
        assertTrue(huluSources.all { it.protocol == SourceProtocol.Cms })
        assertTrue(huluSources.all { it.caps == CAPS_CMS })
        assertTrue(huluSources.all { it.direct })
        assertTrue(huluSources.all { it.tier == 3 })
    }

    @Test
    fun `Hululu 源 key 与接口基址唯一`() {
        assertEquals(huluSources.size, huluSources.map { it.key }.toSet().size)
        assertEquals(huluSources.size, huluSources.map { it.api }.toSet().size)
        assertTrue(huluSources.all { it.api.contains("api.php/provide/vod") })
        assertTrue(huluSources.all { it.note?.contains("2026-11-20") == true })
    }
}
