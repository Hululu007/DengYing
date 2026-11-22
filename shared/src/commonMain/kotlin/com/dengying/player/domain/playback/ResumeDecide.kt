package com.dengying.player.domain.playback

/**
 * 续播直达判定（FR-506）—— 纯函数，不依赖 Android
 *
 * 需求：从观看记录点击后**直达该集续播**；若该集地址已失效则**回到详情并提示换线路**。
 *
 * 为什么判定要独立成纯函数：历史记录只存 `(vod_id, source, episodeIndex)`，
 * 不含媒体地址（地址会过期，存了也未必还能播）。所以「能否直达」取决于
 * 「详情页这次拉回来的线路里，那个集序号还在不在」。三种结局各有不同处置，
 * 且都不该静默：
 *
 * | 情形 | 结局 |
 * |---|---|
 * | 线路就绪且该集存在 | 直达播放 |
 * | 线路为空（详情拉不到 / 源已失效 / 播放链缺口） | 停在详情并提示换线路 |
 * | 集序号越界（源改版、集数变了） | 停在详情并提示换线路，**不静默播别的集** |
 */
object ResumeDecide {

    /** 直达判定的结局 */
    enum class Verdict {
        /** 线路就绪且该集存在 → 直达播放 */
        Play,

        /** 拿不到线路（或非直达入口）→ 停在详情 */
        StayNoLines,

        /** 集序号越界 → 停在详情，**不静默播别的集** */
        StayEpMissing,
    }

    data class Decision(
        val verdict: Verdict,
        /** 直达播放时的真实集序号；其余情形为 -1 */
        val episodeIndex: Int,
        /** 停在详情页时要展示给用户的提示；直达时为空串 */
        val notice: String,
    )

    /** 停在详情页的提示文案（要能行动：换线路） */
    const val NOTICE_NEED_OTHER_LINE = "该集地址已失效，请换一条线路再试"
    const val NOTICE_NO_LINE = "暂时拿不到播放线路，请换一条线路或稍后重试"

    /**
     * 判定从观看记录进入后该如何处理。
     *
     * @param autoPlay 本次进入是否由观看记录发起（其他入口不自动起播）
     * @param episodeCount 详情页当前线路的集数
     * @param resumeEpisode 上次看到的一集（-1 表示无记录）
     */
    fun decideResume(autoPlay: Boolean, episodeCount: Int, resumeEpisode: Int): Decision {
        fun stay(v: Verdict, n: String) = Decision(v, -1, n)

        // 非「直达」入口：保持原行为，不做任何自动跳转也不提示
        if (!autoPlay) return stay(Verdict.StayNoLines, "")
        if (resumeEpisode < 0) return stay(Verdict.StayNoLines, "")
        if (episodeCount <= 0) return stay(Verdict.StayNoLines, NOTICE_NO_LINE)
        if (resumeEpisode >= episodeCount) {
            // 源改版导致集数变化：不得静默播别的集，否则用户会看到错的内容
            return stay(Verdict.StayEpMissing, NOTICE_NEED_OTHER_LINE)
        }
        return Decision(Verdict.Play, resumeEpisode, "")
    }
}
