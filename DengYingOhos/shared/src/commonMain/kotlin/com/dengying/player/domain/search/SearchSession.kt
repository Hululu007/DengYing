package com.dengying.player.domain.search

/**
 * 搜索会话序号（FR-207）—— 纯模块，不依赖 Android
 *
 * 需求：搜索进行中允许取消或换词，且丢弃过期结果。
 * 验收三条：① 取消后界面**无结果闪烁**；② 过期响应被**序号判定丢弃**；
 * ③ 无遗留请求**占用并发预算**。
 *
 * ## 为什么光有"请求层的序号"不够
 *
 * 聚合层里已有会话序号，但它只挡**中途回调**——挡不住 `await` 之后的那句
 * `results = r.items`：旧搜索最终返回时照样会把新搜索的结果**冲掉**，
 * 用户看到的是"搜了新词，结果又变回旧词"。
 *
 * 页面侧需要自己的序号，判定「这次 await 回来后，我是否仍是当前会话」。
 * 本类把这套判定抽成纯逻辑，使"过期即丢弃"可被单测固定。
 *
 * ## 闪烁的来历
 *
 * 换词时若立刻把结果清空，用户会看到结果先消失、再逐条出现——这就是"闪烁"。
 * 故换词时**保留旧结果**，只把提示切换为搜索中；新结果到达时整体替换。
 * 序号判定保证替换来自当前会话。
 */
class SearchSession {

    private var seq: Int = 0
    private var running: Boolean = false
    private var currentKeyword: String = ""

    /**
     * 开始新会话，返回其序号。
     *
     * 每次都递增——即使关键词相同也算新会话（用户可能想重试）。
     */
    fun begin(keyword: String): Int {
        seq++
        running = true
        currentKeyword = keyword
        return seq
    }

    /** 当前序号 */
    fun current(): Int = seq

    /** 某个序号是否仍是当前会话 */
    fun isCurrent(token: Int): Boolean = token == seq

    /**
     * 结束指定会话。
     *
     * 只有**当前**会话能结束——旧会话迟到的结束回调不得把新会话的
     * `running` 置回 false（那会让"搜索中"提示提前消失，而新搜索还在跑）。
     */
    fun finish(token: Int): Boolean {
        if (token != seq) return false
        running = false
        return true
    }

    /** 是否有搜索在跑 */
    fun isRunning(): Boolean = running

    /** 当前会话的关键词 */
    fun keyword(): String = currentKeyword

    /**
     * 是否应**跳过**本次搜索。
     *
     * 两种情形：
     * - 关键词为空（含纯空白）
     * - 同词重搜且已有结果（避免用户连点重复发请求）
     *
     * **不再因为"正在搜索"而跳过**——那是改动前的行为，会让换词被拒。
     * 换词是用户明确意图，应当打断旧搜索、开新会话。
     */
    fun shouldSkip(keyword: String, hasResults: Boolean): Boolean {
        val kw = keyword.trim()
        if (kw.isEmpty()) return true
        if (kw == currentKeyword && hasResults) return true
        return false
    }

    /**
     * 判定一个到达的结果是否应被采用。
     *
     * 三条件全部满足才采用——这比"序号相等"更严：序号相等但关键词不同，
     * 说明中间发生过会话错位，宁可丢弃也不要把不匹配的结果显示出来。
     */
    fun shouldAdopt(token: Int, keyword: String): Boolean =
        isCurrent(token) && currentKeyword == keyword.trim()
}
