package com.dengying.player.ui.pages

import com.dengying.player.core.CmsSources
import com.dengying.player.core.VideoItem
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.domain.source.SourceOverrideRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 发现页源优选（计划 14 目标 8）。
 *
 * 发现页的承诺是「刷到即看」：关掉的源、有已知播放缺口的源一律不进池。
 * 这条规则以前散在页面里、没有测试；现在收敛到 [playableInDiscover] 便于回归。
 */
class DiscoverSourcePickTest {

    private fun video(source: String?) = VideoItem(vod_id = 1L, vod_name = "测试影片", _source = source)

    private val goodSource = CmsSources.ALL.first { it.playGap.isNullOrEmpty() && it.enabled }

    @Test
    fun 启用且无缺口的源可以进池() {
        assertTrue(playableInDiscover(video(goodSource.key), SourceRepository()))
    }

    @Test
    fun 有播放缺口的源不进池() {
        val gapped = CmsSources.ALL.firstOrNull { !it.playGap.isNullOrEmpty() }
        // 清单里若一个缺口源都没有，这条断言无意义，直接跳过（当前清单里有 ah_ap2233）
        if (gapped == null) return
        assertFalse(playableInDiscover(video(gapped.key), SourceRepository()))
    }

    @Test
    fun 用户关掉的源不进池() {
        val repo = SourceRepository()
        repo.override = SourceOverrideRules.Payload(
            version = SourceOverrideRules.OVERRIDE_VERSION,
            list = listOf(SourceOverrideRules.Override(goodSource.key, false, 0L)),
        )
        assertFalse(playableInDiscover(video(goodSource.key), repo))
    }

    @Test
    fun 未知来源与无来源的片不进池() {
        assertFalse(playableInDiscover(video("no_such_source"), SourceRepository()))
        assertFalse(playableInDiscover(video(null), SourceRepository()))
    }
}
