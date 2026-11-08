package com.dengying.player.domain.page

/**
 * 页面级聚合预算（NFR-003）—— 纯模块，不依赖 Android
 *
 * 需求：单请求超时 8s/10s，**页面级总请求预算 ≤ 15s**；预算耗尽即返回已有结果
 * **并标注不完整**。
 *
 * ## 为什么需要"页面级"
 *
 * 单请求超时管的是「一个请求最多等多久」，管不住「一次聚合最多等多久」：
 * 多源聚合时，逐个请求都在自己的 8s 内，但**串起来**可以远超 15s——
 * 用户看到的是长时间的空白或转圈。页面预算是给整次聚合设的总闸。
 *
 * ## 原本只有搜索有兜底，且无法区分"跑完"与"到点"
 *
 * 改动前搜索用 `race(全部完成, 预算超时)` 兜底，但返回值与"全部源都跑完"**完全一样**——
 * 调用方无从判断结果是否完整，也就无法向用户说明。而按分类取列表连兜底都没有。
 *
 * 故本对象提供两件东西：统一的预算时刻计算，以及**可传递的完成度**。
 */
object PageBudget {

    /** 单请求连接超时（ms），与 [HttpClient] 默认值一致 */
    const val CONNECT_TIMEOUT_MS = 8_000L

    /** 单请求读取超时（ms） */
    const val READ_TIMEOUT_MS = 10_000L

    /**
     * 页面级总预算（ms）。需求原文「≤ 15s」。
     *
     * 取 12000 而非贴着 15000：给「到点后用已有结果渲染 + 告诉用户」留出时间。
     * 若贴着上限，用户仍要等到 15s 才看到东西，体验上等于没有预算。
     */
    const val PAGE_BUDGET_MS = 12_000L

    /**
     * 跨源候选（[com.dengying.player.data.source.SourceRepository.findAltSources]）预算。
     *
     * 【为什么比页面预算短】它只做一件事：搜同名资源拿到若干候选。
     * 原实现用 `awaitAll()` 等**全部**源跑完，真机探针抓到
     * `alt-sources-loaded elapsed=14824 / 14961 / 14310 / 21741 / 19363`——
     * 14~22 秒，而这条路径要么是详情页的旁路信息、要么是换线兜底，
     * 让用户为它等 20 秒毫无道理。
     *
     * 取 6s：够闸门（6 路并发）跑完一到两轮，拿到的候选数已远超实际需要的
     * （真机抓到 n=4~15），再等下去只是在等慢源。
     */
    const val ALT_SOURCES_BUDGET_MS = 6_000L

    /**
     * 分类表解析预算（ms）。
     *
     * 【为什么单独给一条】`resolveTypeIds` 要逐个源拉 `ac=list` 才能把分类名换成
     * type_id，这是首页的**前置依赖**。真机探针抓到 tier1 三个源里两个是死源
     * （分类表 3s 超时），整层被拖到 4.4s，而唯一活着的源 1.4s 就返回了。
     *
     * 取 3000：等于给「一个源正常返回」留足时间，又不允许整层被死源拖满两轮超时。
     * 到点就用已解析到的映射继续拉列表，没解析完的源由后台补齐（下次进入即命中缓存）。
     */
    const val TYPE_RESOLVE_BUDGET_MS = 3_000L

    /** 一次聚合的完成度 */
    enum class Outcome {
        /** 全部源跑完 */
        Complete,

        /** 预算耗尽，结果可能不完整 */
        Expired,

        /** 用户主动取消 */
        Cancelled,
    }

    /** 聚合结束时的不完整提示；完整时为空串 */
    const val NOTICE_INCOMPLETE = "部分结果：已显示先返回的内容，网络较慢的来源未等到"

    /** 取消时的提示；与"不完整"区分——取消是用户主动的，不是网络慢 */
    const val NOTICE_CANCELLED = ""

    /** 由完成度给出提示文案；完整时为空串 */
    fun outcomeNotice(kind: Outcome): String = when (kind) {
        Outcome.Expired -> NOTICE_INCOMPLETE
        Outcome.Cancelled -> NOTICE_CANCELLED
        Outcome.Complete -> ""
    }

    /** 已收口的结果与完成度 */
    data class Budgeted<T>(
        val items: List<T>,
        val outcome: Outcome,
        /** 已用毫秒，供日志与诊断 */
        val elapsedMs: Long,
    )

    /**
     * 判断"是否已达到足够的收口条件"。
     *
     * 与预算配合使用：预算未到但已收够条目时，也可以提前收口——
     * 用户不必为剩下的慢源一直等。
     */
    fun shouldSettle(have: Int, need: Int): Boolean = have >= need

    /**
     * 由给定的候选截止时刻算出"下一个该醒来的时刻"，用于把多个等待合并成一次。
     *
     * @return 剩余毫秒中最小的那个；全部已过则返回 0
     */
    fun nextWakeMs(nowMs: Long, deadlines: List<Long>): Long {
        var best = -1L
        for (d in deadlines) {
            val left = d - nowMs
            if (left <= 0) return 0L
            if (best < 0 || left < best) best = left
        }
        return if (best < 0) 0L else best
    }
}

/**
 * 预算计时钟。
 *
 * 语义：`expired()` 表示总预算是否已耗尽；`remaining()` 给出剩余毫秒。
 * 时钟在构造时就起算——因为"用户已经开始等"是从页面发起聚合那一刻算，
 * 而不是从某个内部函数被调用算。
 *
 * 【为什么是 class 而不是 data class】它有可变状态（outcome/endMs），
 * 且「只有第一次标记有效」是行为而非数据。
 */
class PageClock(
    private val startMs: Long,
    budgetMs: Long = PageBudget.PAGE_BUDGET_MS,
) {
    private val budgetMs: Long = if (budgetMs >= 1) budgetMs else 1
    private var outcome: PageBudget.Outcome = PageBudget.Outcome.Complete
    private var endMs: Long = 0

    /** 从聚合开始到 `nowMs` 已过的毫秒；不返回负数 */
    fun elapsed(nowMs: Long): Long {
        val d = nowMs - startMs
        return if (d > 0) d else 0
    }

    /** 距预算耗尽还剩多少毫秒；已耗尽返回 0（不返回负数） */
    fun remaining(nowMs: Long): Long {
        val left = budgetMs - elapsed(nowMs)
        return if (left > 0) left else 0
    }

    /** 总预算是否已耗尽 */
    fun expired(nowMs: Long): Boolean = elapsed(nowMs) >= budgetMs

    /** 预算上限 */
    fun getBudgetMs(): Long = budgetMs

    /**
     * 标记本次聚合提前结束。
     *
     * 只有**第一次**标记有效——若已因超时标记为 `Expired`，随后到达的取消不该
     * 把它改写成 `Cancelled`（那会让用户看到"已取消"却是超时导致的）。
     */
    fun markOutcome(kind: PageBudget.Outcome, nowMs: Long) {
        if (outcome != PageBudget.Outcome.Complete) return
        outcome = kind
        endMs = nowMs
    }

    /** 本次聚合的完成度 */
    fun getOutcome(): PageBudget.Outcome = outcome

    /** 是否完整（未超时、未取消） */
    fun isComplete(): Boolean = outcome == PageBudget.Outcome.Complete

    /** 实际耗时的毫秒数（未标记结束时按 `nowMs` 计） */
    fun durationMs(nowMs: Long): Long = if (endMs > 0) endMs - startMs else elapsed(nowMs)

    /** 截止时刻（供 `nextWakeMs` 与 `delay` 使用） */
    fun deadlineMs(): Long = startMs + budgetMs
}
