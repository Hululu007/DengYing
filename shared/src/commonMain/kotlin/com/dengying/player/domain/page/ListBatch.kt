package com.dengying.player.domain.page

/**
 * 「首批即渲染」的批次决策（NFR-701 加载速度）—— 纯模块，不依赖 Android
 *
 * ## 问题
 *
 * 分类列表的聚合是「按子分类逐个拉，凑够 30 条就收口」。这个 30 条的门槛
 * 是**为完整性设的**，但它同时决定了「用户多久能看到东西」：
 * 首屏三列网格只需要约 6–9 条就能填满一屏，而实现会一直等到 30 条。
 * 实测冷启动 `index-load elapsed=3786ms`，其中大部分时间在等第 3、4 个子分类。
 *
 * ## 做法（参考 Paging 3 的"首批即呈现"与 NowInAndroid 的 SWR）
 *
 * 把一次分类拉取拆成两个阶段：
 *
 * 1. **首批（first batch）**：第一个返回且非空的子分类结果 → 立即上屏。
 *    一屏 3 列 × 约 3 行 = 9 条，多数源单页就能给 20–60 条，所以首批通常就是
 *    "一个源的一页"，足以填满首屏。
 * 2. **补齐（fill）**：其余子分类继续拉，凑满目标条数后返回最终结果。
 *    这一步**不阻塞已上屏的内容**——用户已经在看了。
 *
 * ## 为什么不直接改成"每来一批就上屏"
 *
 * 那样会让列表在几秒内反复重排（不同源的排序交错），滚动位置和视觉焦点都在跳。
 * 只做「首批 + 最终」两次呈现是刻意的：第一次让用户看到内容，第二次让内容变完整，
 * 中间不抖。这与 Paging 的 `insertSeparators` + `withLoadStateHeader` 的取舍一致。
 *
 * ## 与预算的关系
 *
 * 补齐阶段仍受 [PageBudget] 约束：到点就用手上的结果收口，不再等慢源。
 */
object ListBatch {

    /**
     * 首批最小条数：够填满首屏即可。
     *
     * 3 列 × 3 行 = 9。取 8 是因为网格最后一行常常只露出一半，
     * 8 条已经能撑满可视区而不出现大片空白。
     */
    const val FIRST_BATCH_MIN = 8

    /**
     * 最终目标条数（原实现里写死的 30 条）。
     *
     * 保留 30：它是"一页内容看起来像一页"的经验值，也是缓存条目的目标量
     * （`PageCache` 单页上限 60）。
     */
    const val FULL_BATCH_TARGET = 30

    /**
     * 判断当前累积的结果是否已经够「首批」上屏。
     *
     * @param mergedCount 已合并的条数
     * @param alreadyEmitted 是否已经上屏过首批
     * @return true 表示应当**立刻**把当前结果交给 UI
     */
    fun shouldEmitFirstBatch(mergedCount: Int, alreadyEmitted: Boolean): Boolean =
        !alreadyEmitted && mergedCount >= FIRST_BATCH_MIN

    /**
     * 是否该停止继续拉取。
     *
     * 两个停止条件，任一成立即停：
     *  - 已凑满目标条数（内容够了）
     *  - 剩余任务为空（没有更多可拉）
     *
     * 【为什么单独抽出来】原实现把判断写在 `while` 循环里，且只判条数；
     * "任务拉完但不足 30 条"时靠循环条件自然退出，逻辑分散在两处。
     * 抽成纯函数后，"什么时候停"可以被穷举测试。
     */
    fun shouldStop(mergedCount: Int, remainingTasks: Int): Boolean =
        mergedCount >= FULL_BATCH_TARGET || remainingTasks <= 0

    /**
     * 下一次并发拉几个子分类。
     *
     * 【为什么动态而不是固定 3】首批阶段要**快**：并发越多，第一个返回越早。
     * 首批已上屏后要**稳**：继续用大并发去挤全局闸门（上限 6）会把
     * 用户下一步操作（点进详情）的请求排在后面。故首批用较大并发抢时间，
     * 补齐阶段回落到 2。
     *
     * @param firstEmitted 首批是否已上屏
     * @param gateLimit 全局出站闸门上限
     */
    fun concurrencyFor(firstEmitted: Boolean, gateLimit: Int): Int {
        val limit = if (gateLimit >= 1) gateLimit else 1
        return if (!firstEmitted) {
            // 首批：最多用闸门的一半，留一半给搜索/详情等交互路径
            (limit / 2).coerceAtLeast(2).coerceAtMost(4)
        } else {
            2
        }
    }

    /**
     * 滑动窗口大小：本次还能同时挂几个任务。
     *
     * 【为什么需要它】原实现是「取一批 → `awaitAll()` 等整批 → 判首批」。
     * 只要批里有一个慢源，整批都被它拖住，首批判据也就跟着推迟——
     * 真机探针抓到的形态是 `firstBatch ≈ elapsed`（首批与最终**几乎同一时刻**），
     * 说明「首批即上屏」在最慢的那个分类上等于没生效。
     *
     * 改成滑动窗口后：**一个结果回来就补一个任务**，慢源只拖住它自己。
     * 窗口大小仍受 [concurrencyFor] 的约束（首批大、上屏后回落），
     * 且不超过剩余任务数——否则会空转等一个永远不会来的位置。
     *
     * @param firstEmitted 首批是否已上屏
     * @param gateLimit 全局出站闸门上限
     * @param remaining 还没派发的任务数
     * @return 应当同时在飞的任务数上限（≥ 0）
     */
    fun windowFor(firstEmitted: Boolean, gateLimit: Int, remaining: Int): Int {
        if (remaining <= 0) return 0
        val want = concurrencyFor(firstEmitted, gateLimit)
        return if (want < remaining) want else remaining
    }
}
