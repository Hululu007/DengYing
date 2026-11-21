package com.dengying.player.domain.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 内嵌位的历史写入 / 续播恢复门禁（[InlineSessionGate]）。
 *
 * 回归的问题形态：单会话架构下引擎与监听器是进程级单例，详情页在全屏播放页之下
 * 仍然收 `onPlaying/onPaused`。没有这道门禁时会写出「详情页集号 + 播放页进度」的历史，
 * 并把播放页已经推进的位置 seek 回详情页记的旧位置。
 */
class InlineSessionGateTest {

    private fun owns(
        vodId: Long = 901L,
        source: String = "baofeng",
        episodeIndex: Int = 3,
        sVodId: Long = 901L,
        sSource: String = "baofeng",
        sEpisode: Int = 3,
    ) = InlineSessionGate.ownsEpisode(
        vodId = vodId,
        source = source,
        episodeIndex = episodeIndex,
        sessionVodId = sVodId,
        sessionSource = sSource,
        sessionEpisodeIndex = sEpisode,
    )

    @Test
    fun `同片同源同集算拥有`() {
        assertTrue(owns())
    }

    @Test
    fun `换集不算拥有`() {
        assertFalse(owns(sEpisode = 4), "播放页换集后详情页不得再按旧集号落盘")
    }

    @Test
    fun `跨源同号片不算拥有`() {
        assertFalse(owns(sSource = "tv189"), "跨源同号 vod 不是同一份内容，必须拦下")
    }

    @Test
    fun `换片不算拥有`() {
        assertFalse(owns(sVodId = 902L))
    }

    @Test
    fun `无会话时历史仍可写`() {
        assertTrue(
            InlineSessionGate.canWriteHistory(hasSession = false, ownsEpisode = false),
            "releaseInline 先 stop 会话、onCleared 再兜底补写，这一段不能被拦掉",
        )
    }

    @Test
    fun `会话指向别处时不写历史`() {
        assertFalse(InlineSessionGate.canWriteHistory(hasSession = true, ownsEpisode = false))
        assertTrue(InlineSessionGate.canWriteHistory(hasSession = true, ownsEpisode = true))
    }

    @Test
    fun `引擎已走起来时不恢复续播点`() {
        assertTrue(
            InlineSessionGate.canRestoreInline(
                hasSession = true,
                ownsEpisode = true,
                enginePositionMs = 0L,
            ),
        )
        assertFalse(
            InlineSessionGate.canRestoreInline(
                hasSession = true,
                ownsEpisode = true,
                enginePositionMs = ResumeRestore.HEAD_GUARD_MS,
            ),
            "本次不起播却已经在 3 秒之后 = 会话被别的页面驱动，seek 回去会把用户拉走",
        )
    }

    @Test
    fun `无会话或会话指向别处时不恢复续播点`() {
        assertFalse(
            InlineSessionGate.canRestoreInline(
                hasSession = false,
                ownsEpisode = true,
                enginePositionMs = 0L,
            ),
        )
        assertFalse(
            InlineSessionGate.canRestoreInline(
                hasSession = true,
                ownsEpisode = false,
                enginePositionMs = 0L,
            ),
        )
    }
}
