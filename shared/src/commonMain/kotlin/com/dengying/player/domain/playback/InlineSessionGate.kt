package com.dengying.player.domain.playback

/**
 * 「详情页内嵌位还能不能替当前会话落盘 / 恢复续播点」—— 纯函数，不依赖 Android。
 *
 * ## 为什么需要这道门禁
 *
 * 单会话架构下引擎与监听器都是**进程级单例**：详情页在播、用户点全屏进播放页再换集，
 * 详情页 ViewModel 仍然活着、仍然挂在同一个引擎上，`onPlaying/onPaused` 照样回调。
 * 若它照旧写历史，条目就会变成「详情页记的集号 + 播放页正在播的进度」——
 * 下次续播会跳到错的地方。续播恢复同理：会话已被播放页推进到中途时再 seek 回
 * 详情页记录的旧位置，用户看到的就是「播放中画面突然跳回去」
 * （同一现象见 [ResumeRestore] 的探针记录）。
 *
 * ## 一条必须记住的语义
 *
 * 历史写入**不要求会话仍在**：用户直接返回首页时，`releaseInline()` 先 `stop()` 会话，
 * `onCleared()` 的兜底补写发生在之后——那时 `hasSession` 已经是 false，但这次写入
 * 恰恰是最需要落盘的一段进度。所以 [canWriteHistory] 对「无会话」放行，
 * 只拦「会话明确指向别的 vod/源/集号」。
 */
object InlineSessionGate {

    /**
     * 会话归属是否仍是本页这一集（vod + 采集源 + 集号三者同时相等）。
     *
     * 三者必须同时比：同一部片换集要拦（进度会串集），同号 vod 的另一源也要拦
     * （跨源同号片不是同一份内容，见 FR-305）。
     */
    fun ownsEpisode(
        vodId: Long,
        source: String,
        episodeIndex: Int,
        sessionVodId: Long,
        sessionSource: String,
        sessionEpisodeIndex: Int,
    ): Boolean = vodId == sessionVodId &&
        source == sessionSource &&
        episodeIndex == sessionEpisodeIndex

    /** 历史是否可写：无会话放行（离页补写），会话已指向别处则拦下 */
    fun canWriteHistory(hasSession: Boolean, ownsEpisode: Boolean): Boolean =
        !hasSession || ownsEpisode

    /**
     * 续播恢复是否可执行。
     *
     * 「引擎已经播到 3 秒之后」一律不恢复：本次内嵌起播的 `setMedia` 会让位置从 0 开始，
     * 一上来就已经走起来，说明驱动这次播放的不是本页（例如播放页接管了会话），
     * 此时 seek 回去只会把用户拉走。
     */
    fun canRestoreInline(
        hasSession: Boolean,
        ownsEpisode: Boolean,
        enginePositionMs: Long,
    ): Boolean = hasSession && ownsEpisode && enginePositionMs < ResumeRestore.HEAD_GUARD_MS
}
