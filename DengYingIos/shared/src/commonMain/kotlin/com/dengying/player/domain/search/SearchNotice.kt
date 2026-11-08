package com.dengying.player.domain.search

/**
 * 搜索与详情的提示文案（FR-208、FR-309）—— 纯函数，不依赖 Android
 *
 * 两件事共用一个对象，因为它们都属于「把系统状态如实告诉用户」：
 *
 * - **FR-208**：搜索中的文案要反映**实际参与聚合的源数量**，不写死数字。
 * - **FR-309**：解析不出剧集时给出明确提示，且「空线路」与「加载失败」可区分。
 */
object SearchNotice {

    // ---------------- FR-208 搜索提示 ----------------

    /** 参与聚合的源的最小形状（只需判断能否搜索） */
    data class SearchableSource(val enabled: Boolean, val capsSearch: Boolean)

    /**
     * 计算实际参与搜索的源数量。
     *
     * 口径必须与聚合层的筛选一致（`enabled && caps.search`），
     * 否则文案里的数字会比实际参与数多，等于对用户撒谎。
     *
     * 【FR-601 注意】`enabled` 必须是**应用覆盖层之后**的启用状态，
     * 不能是内置清单的基线值——否则用户停用源后这里显示的数字不会变。
     */
    fun countSearchable(sources: List<SearchableSource>): Int =
        sources.count { it.enabled && it.capsSearch }

    /** 搜索进行中的文案；源数为 0 时如实说明无法搜索 */
    fun searchingText(sourceCount: Int): String =
        if (sourceCount <= 0) "没有启用的资源站，无法搜索"
        else "正在搜索 $sourceCount 个资源站…"

    /** 流式阶段文案：已找到 N 个，仍在继续 */
    fun partialText(found: Int, sourceCount: Int): String =
        if (found <= 0) searchingText(sourceCount)
        else "已找到 $found 个，继续搜索中…"

    /** 搜索结束的文案；无结果时区分「搜到了但没有」与「源都没参与」 */
    fun doneText(found: Int, keyword: String, sourceCount: Int): String {
        if (sourceCount <= 0) return "没有启用的资源站，无法搜索"
        if (found <= 0) return "在 $sourceCount 个资源站都没有找到「$keyword」，换个词试试"
        return ""
    }

    // ---------------- FR-309 空剧集提示 ----------------

    /** 详情页的剧集状态 */
    enum class EpisodeKind { Ok, NoLines }

    data class EpisodeState(
        val kind: EpisodeKind,
        /** 需展示的提示；`Ok` 时为空串 */
        val notice: String,
        /** 是否应当渲染可选剧集网格（`NoLines` 时不得渲染，否则是死胡同） */
        val showGrid: Boolean,
    )

    /** 空线路提示 */
    const val NOTICE_NO_LINES = "本片未解析到可播剧集，可换源或稍后再试"

    /**
     * 判定详情页是否应渲染剧集网格。
     *
     * **「加载失败」不在此判定内**：详情请求本身失败时（`item == null`），
     * 页面外层已走错误分支（不渲染剧集块），本函数根本不会被调用。
     * 两类提示的区分因此**在页面级成立**：加载失败 = 页面错误态，空线路 = 本函数的 notice。
     * 把 `LoadFailed` 塞进本函数会造出一个不可达分支——那是假的覆盖。
     *
     * 本函数负责的是另一半：详情**拿到了**，但解析不出线路或剧集。
     * 此时**不渲染空网格**，否则用户面对一个点不动的「选集（共 0 集）」，是死胡同。
     */
    fun decideEpisodeState(lineCount: Int, episodeCount: Int): EpisodeState =
        if (lineCount <= 0 || episodeCount <= 0) {
            EpisodeState(EpisodeKind.NoLines, NOTICE_NO_LINES, showGrid = false)
        } else {
            EpisodeState(EpisodeKind.Ok, "", showGrid = true)
        }
}
