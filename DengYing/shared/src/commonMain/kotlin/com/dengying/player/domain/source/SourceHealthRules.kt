package com.dengying.player.domain.source


/**
 * 源健康分（FR-107、OPS-005）—— 纯模块，不依赖 Android
 *
 * ## 设计约定，不自行改造
 *
 * | 项 | 规定 |
 * |---|---|
 * | 滚动窗口 | **8 样本** |
 * | `SuccessNoData` | **不扣分**——源响了但没这片，不是源的错 |
 * | `Suspicious` | 排末尾、**不删除** |
 * | 全 `Suspicious` | **仍兜底全试**——否则"全部不可用"时会误报无结果 |
 * | 持久化 | **不持久化**——避免把一次弱网故障固化为长期惩罚 |
 *
 * ## 为什么这些细节重要
 *
 * - **`SuccessNoData` 不扣分**：把"没搜到"当失败会让内容少的正常源被误判为坏源。
 * - **不删除**：源会恢复。删掉就意味着用户永久失去一个源，且要改清单发版才能加回。
 * - **全可疑仍兜底**：把"全部可疑"当"全部不可用"会让应用在弱网下直接报无结果，
 *   而实际上可能只是刚才几次运气差。
 *
 * ## 与人工 `tier` 的关系
 *
 * `tier` 是**排序主键**（人工先验、实测快照），健康分只做**同级纠偏**——
 * 它不推翻 tier，只在同 tier 内把连续失败的源往后排。
 */
object SourceHealthRules {

    /** 滚动窗口上限（固定 8 样本） */
    const val HEALTH_WINDOW = 8

    /** 一次源交互的结果分类 */
    enum class Outcome {
        /** 拿到可用数据 */
        Ok,

        /** 请求成功但无数据（该源没有这片）——**不扣分** */
        SuccessNoData,

        /** 请求失败（超时、4xx/5xx、解析失败） */
        Failed,
    }

    /** 源的可用性判定 */
    enum class State {
        /** 健康：可正常参与聚合 */
        Active,

        /** 可疑：排末尾但不删除 */
        Suspicious,
    }

    /**
     * 分数下限。取 -8 使"窗口内全失败"恰好落到最低分，
     * 且不设更低——分数再低也不会改变排序结果，只会让显示数字无意义地大。
     */
    private const val SCORE_MIN = -8

    /** 分数上限：连续成功累加到 8 即封顶，避免老源靠历史分数压倒新实测 */
    private const val SCORE_MAX = 8

    /**
     * 判定为可疑的分数阈值。
     *
     * 取 **-2**：即"窗口内至少连续两次失败"才转可疑。
     * 单次失败可能是偶发抖动，一次就降级会让源在正常网络波动下反复横跳。
     */
    private const val SUSPICIOUS_THRESHOLD = -2

    /** 单源的健康记录 */
    data class Health(
        /** 源标识（`CmsSource.key`） */
        val key: String,
        /** 滚动窗口内的分数 */
        val score: Int,
        /** 已记录的样本数（不超过窗口上限） */
        val samples: Int,
        /** 最近一次结果 */
        val lastOutcome: Outcome,
    )

    /** 新建一条健康记录（初始分数 0：无历史即中性，不预判好坏） */
    fun newHealth(key: String) = Health(key, 0, 0, Outcome.Ok)

    /**
     * 把一次结果并入健康记录，返回**新的**记录（不修改入参）。
     *
     * 计分规则：`Ok` → **+1**；`SuccessNoData` → **0**（不扣分）；`Failed` → **-1**。
     * 钳制范围 `[-8, +8]`。
     *
     * **不做时间衰减**。窗口是"最近 N 次"而非"最近 N 分钟"——
     * 加时间衰减会引入时钟依赖，使判定不可单测，而收益有限（会话本就是短时的，
     * 且健康分不持久化，每次启动都从零开始）。
     */
    fun record(h: Health, outcome: Outcome): Health {
        val delta = when (outcome) {
            Outcome.Ok -> 1
            Outcome.Failed -> -1
            Outcome.SuccessNoData -> 0
        }
        val next = (h.score + delta).coerceIn(SCORE_MIN, SCORE_MAX)
        val n = h.samples + 1
        return Health(
            key = h.key,
            score = next,
            samples = if (n > HEALTH_WINDOW) HEALTH_WINDOW else n,
            lastOutcome = outcome,
        )
    }

    /** 由记录判定可用性 */
    fun stateOf(h: Health): State =
        if (h.score <= SUSPICIOUS_THRESHOLD) State.Suspicious else State.Active

    /**
     * 对一组项按健康分排序（稳定：同分保持输入顺序）。
     *
     * **不删除任何源**——明确"排末尾不删除"。
     * 返回的是排序后的完整列表，调用方据此决定尝试顺序。
     */
    fun <T> sortByHealth(
        items: List<T>,
        keyOf: (T) -> String,
        healthOf: (String) -> Health?,
    ): List<T> {
        // 用 withIndex 保留原序，避免依赖 sort 的稳定性（Kotlin 的 sort 是稳定的，
        // 但显式带 idx 让"同分保持输入顺序"这条契约不依赖语言实现细节）
        return items.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<T>> { healthOf(keyOf(it.value))?.score ?: 0 }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    /**
     * 是否需要"兜底全试"。
     *
     * **全部源都可疑时仍兜底全试**。若不兜底，弱网下会直接报"无结果"，
     * 而实际上源可能只是刚才几次运气差。
     *
     * @return 全部源都可疑（且至少有一个源）时返回 true
     */
    fun needFallbackAll(healths: List<Health>): Boolean {
        if (healths.isEmpty()) return false
        return healths.all { stateOf(it) == State.Suspicious }
    }

    /** 供调试视图显示的一行摘要 */
    data class Row(
        val key: String,
        val state: State,
        val score: Int,
        val samples: Int,
        val lastOutcome: Outcome,
        /** 一句话说明，供人工排查 */
        val note: String,
    )

    /**
     * 生成调试视图的行（OPS-005：源失效的人工发现途径）。
     *
     * 视图的目的是**让人能判断"这个源是不是坏了"**，故每行必须给出可行动的信息：
     * 当前状态、分数、样本数、最近一次结果。只说"可疑"不够——用户需要知道凭什么。
     */
    fun toRow(h: Health): Row {
        val st = stateOf(h)
        val note = when {
            h.samples == 0 -> "本次启动尚无记录"
            st == State.Suspicious -> "窗口内连续失败，已排在末尾（仍会尝试）"
            h.lastOutcome == Outcome.Failed -> "最近一次失败，继续观察"
            h.lastOutcome == Outcome.SuccessNoData -> "请求正常但无该片，不计为故障"
            else -> "正常"
        }
        // 一条重要的显示约定：可疑源必须标注「仍会尝试」。
        // 否则用户会以为源被禁用了，而规则明确"排末尾不删除"——它会继续参与聚合，只是排在后面。
        return Row(h.key, st, h.score, h.samples, h.lastOutcome, note)
    }

    /** 批量生成调试视图行，可疑的排前面（人工排查先看有问题的） */
    fun toRows(healths: List<Health>): List<Row> =
        healths.map { toRow(it) }.sortedWith(
            compareBy<Row> { if (it.state == State.Suspicious) 0 else 1 }
                .thenBy { it.score }
                .thenBy { it.key }
        )
}
