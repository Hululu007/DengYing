package com.dengying.player.domain.playback

/**
 * 观看历史的写入规则（FR-408 / DVP-PORT-PLAN-014 §0）。
 *
 * 3 秒是「算看过」的门槛：低于它写历史只会让列表塞满误触记录，
 * 也会让下一次续播从片头附近无意义地做一次 seek。
 * 续播阈值 [ResumeRestore.HEAD_GUARD_MS] 与本值同源，但语义不同，勿合并。
 */
object HistoryWritePolicy {
    const val MIN_POSITION_MS: Long = 3_000L

    /** 该位置是否算一次有效观看（达到门槛才允许落盘） */
    fun shouldWrite(positionMs: Long): Boolean = positionMs >= MIN_POSITION_MS

    /**
     * 是否需要写：位置达到门槛，且与上次已落盘的位置不同（避免重复写同一进度）。
     * @param lastSavedPositionMs 上次成功落盘的位置；`-1` 表示本集还没写过
     */
    fun shouldWrite(positionMs: Long, lastSavedPositionMs: Long): Boolean =
        shouldWrite(positionMs) && positionMs != lastSavedPositionMs
}
