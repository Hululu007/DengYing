package com.dengying.player.core

/**
 * 源请求结果的四态判定（FR-108、FR-109）—— 纯模块，不依赖 Android
 *
 * ## 解决的错误
 *
 * 改动前 7 个适配器一律写 `ok: items.length > 0`，把两种**性质完全不同**的情况
 * 压成同一个 `ok: false`：
 *
 * | 实际情况 | 含义 | 正确处置 |
 * |---|---|---|
 * | 请求成功，该源确实没有这部片 | 源是好的 | **不触发故障转移**、不扣健康分 |
 * | 请求失败（超时 / 5xx / 解析失败） | 源可能坏了 | 触发故障转移、扣健康分 |
 *
 * 后果有三层：
 * 1. **冷门片的搜索会白跑一遍全部源**——每个源都"失败"，实际它们都正常
 * 2. **健康分被污染**（FR-107）——把正常源记成失败
 * 3. **提示无法区分**（NFR-008）——用户看到"搜索失败"其实是"这片没人收录"
 *
 * ## 四态
 *
 * | 态 | 触发条件 | 故障转移 | 健康分 |
 * |---|---|---|---|
 * | `SuccessWithData` | 请求成功且有条目 | 否（已成功） | +1 |
 * | `SuccessNoData` | **请求成功但零条目** | **否** | **0** |
 * | `ParseFailed` | 拿到响应但解析不出结构 | 是 | −1 |
 * | `RequestFailed` | 未拿到响应（网络层失败、非 2xx） | 是 | −1 |
 *
 * 判定为纯函数，故可脱设备单测（NFR-009）。
 */
enum class RequestOutcome {
    /** 请求成功且有数据 */
    SuccessWithData,

    /** 请求成功但零条目：该源没有这部片，**不是源的错** */
    SuccessNoData,

    /** 拿到响应但解析不出结构（站点改版、网关劫持页） */
    ParseFailed,

    /** 未拿到可用响应（网络层失败、非 2xx） */
    RequestFailed,
}

/** 判定输入：适配器能提供的最小事实集 */
data class OutcomeFacts(
    /** HTTP 状态码；0 表示未拿到响应（网络层异常） */
    val status: Int,
    /** 响应体是否可解析为预期结构（JSON 能解出 list / HTML 匹配到条目容器） */
    val parsed: Boolean,
    /** 解析出的条目数 */
    val itemCount: Int,
)

/**
 * 由最小事实集判定四态。
 *
 * 判定顺序**有约束**：先看有没有响应（`status`），再看能否解析，
 * 最后看有没有条目。反序会把"网络失败"误判为"无数据"——
 * 因为网络失败时条目数同样是 0。
 *
 * `status == 0` 表示**未拿到响应**（HttpClient 的约定：任何异常一律 `status = 0`），
 * 故它优先于其它判据。
 */
fun classifyOutcome(facts: OutcomeFacts): RequestOutcome {
    // 1) 没有响应 → 请求失败。必须先判这一条：网络失败时 itemCount 也是 0
    if (facts.status == 0) return RequestOutcome.RequestFailed
    // 2) 非 2xx → 请求失败（源站点错误、限流、鉴权失败）
    if (facts.status < 200 || facts.status >= 300) return RequestOutcome.RequestFailed
    // 3) 拿到 2xx 但解不出结构 → 解析失败（站点活着但协议不合规）
    if (!facts.parsed) return RequestOutcome.ParseFailed
    // 4) 解出来了但没条目 → **源正常，只是没这部片**
    if (facts.itemCount <= 0) return RequestOutcome.SuccessNoData
    return RequestOutcome.SuccessWithData
}

/** 是否算"源本身是好的"（用于是否扣健康分、是否触发故障转移） */
fun RequestOutcome.isSourceHealthy(): Boolean =
    this == RequestOutcome.SuccessWithData || this == RequestOutcome.SuccessNoData

/**
 * 是否应触发对后续源的**故障转移**。
 *
 * 关键点：`SuccessNoData` **不触发**——该源正常响应了，
 * 换别的源只是因为别处可能收录了这部片，与"这个源坏了"无关。
 * 这个区分正是 FR-108 的验收条件之一。
 */
fun RequestOutcome.shouldFailover(): Boolean =
    this == RequestOutcome.RequestFailed || this == RequestOutcome.ParseFailed

/** 该结果对应到健康分的信号（FR-107 的三分集合） */
enum class HealthSignal { Ok, SuccessNoData, Failed }

/**
 * 四态 → 健康分信号。
 *
 * 这是 FR-107 与 FR-108 的接缝：若适配器从不产出 `SuccessNoData` 这个态，
 * 健康分里的 `SuccessNoData` 分支就永远拿不到值。
 */
fun RequestOutcome.toHealthSignal(): HealthSignal = when (this) {
    RequestOutcome.SuccessWithData -> HealthSignal.Ok
    RequestOutcome.SuccessNoData -> HealthSignal.SuccessNoData
    RequestOutcome.RequestFailed, RequestOutcome.ParseFailed -> HealthSignal.Failed
}

// ---------------- 用户可见提示（NFR-008：区分"无网络"与"源无数据"） ----------------

/** 只给结论 + 下一步，不暴露内部概念 */
object OutcomeMessages {
    const val NO_NETWORK = "网络不可用，请检查连接后重试"
    const val ALL_SOURCES_FAILED = "所有来源都请求失败，请检查网络或稍后重试"
    const val NO_SOURCE_HAS_IT = "已搜索全部来源，都没有这部片"
}

/**
 * 聚合层用的汇总判定：全部源跑完后该给用户什么提示。
 *
 * @param outcomes 本次参与的源的结果列表
 * @return 提示文案；空串表示无需提示（有源成功且拿到数据）
 */
fun aggregateNotice(outcomes: List<RequestOutcome>): String {
    if (outcomes.isEmpty()) return ""

    var withData = 0
    var noData = 0
    var failed = 0
    for (o in outcomes) {
        when (o) {
            RequestOutcome.SuccessWithData -> withData++
            RequestOutcome.SuccessNoData -> noData++
            else -> failed++
        }
    }
    // 有源拿到数据 → 不提示
    if (withData > 0) return ""
    // 全部失败 → 网络类提示（含重试建议）
    if (failed == outcomes.size) return OutcomeMessages.ALL_SOURCES_FAILED
    // 有源正常响应但都没这部片 → 说清是"没有"而不是"坏了"
    if (noData > 0) return OutcomeMessages.NO_SOURCE_HAS_IT
    return OutcomeMessages.ALL_SOURCES_FAILED
}
