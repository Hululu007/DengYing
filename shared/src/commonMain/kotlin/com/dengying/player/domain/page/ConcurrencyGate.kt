package com.dengying.player.domain.page

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 全局出站并发闸门（FR-105、NFR-004）—— 纯模块，不依赖 Android
 *
 * 需求：并发受**全局**预算约束，而非每处调用各自为限；同时进行的出站 HTTP 请求数
 * 有上限并可断言；页面快速切换不产生请求叠加。
 *
 * ## 为什么要"全局"
 *
 * 改动前 `MAX_CONCURRENT = 3` 只在分批列表里生效，
 * 而搜索、详情、分类表三条路径都是无上限全并发。
 * 结果是：翻一次分类页可能同时有 3 批 × N 源在飞，用户再快速切页就叠加——
 * 「每个聚合函数各自为限」在数学上等于**没有上限**。
 *
 * 闸门放在 `HttpClient` 这一层：它是适配层唯一的出网入口，因此任何新增源、
 * 任何新调用路径都自动受约束，不需要每个调用点记得限流。
 *
 * ## 前后台分流
 *
 * 只有上限还不够：上限 6 时，「打开详情页」这一个动作会同时扇出十几个候选源的
 * 搜索，把 6 个空位全占满，详情请求只能排到队尾。真机实测（docs/04 §16.34）
 * 候选搜索在闸门前排队 412~3161ms，而详情请求源站本身只要 400~1100ms。
 *
 * 于是分两档（见 [com.dengying.player.data.net.NetPriority]）：
 *  - 前台：用户正盯着结果等（进详情、搜索、翻页）
 *  - 后台：结果晚到几十毫秒无感（候选源预搜）
 *
 * 后台档只能用 `limit - reservedForForeground` 个空位，剩余空位前台随到随用；
 * 归还空位时**前台队列优先**。
 *
 * ## 可断言性
 *
 * `peak()` 记录历史最大并发数。NFR-004 要求「有单测断言并发峰值」，
 * 单测可以让 N 个任务同时冲闸门，然后断言 `peak() <= limit`。
 *
 * 【与 ArkTS 版的差异】用挂起函数 + Mutex 实现，而不是手写 Promise 队列。
 * 语义保持一致：FIFO 唤醒、重复释放不放大上限、`cancelQueued` 只丢排队的不动在跑的。
 */
class ConcurrencyGate(limit: Int, reservedForForeground: Int = 0) {

    /** 上限至少为 1：为 0 会让所有请求永久挂起，是配置错误 */
    private val limit: Int = if (limit >= 1) limit else 1

    /**
     * 保留给前台的空位数。
     *
     * 后台档最多只能用到 `limit - reservedForForeground` 个，剩下的空位前台随到随用。
     * 取 0 等于关闭分流（两档一样），取满会把后台彻底饿死，故夹在 `[0, limit-1]`。
     */
    private val reserved: Int =
        if (reservedForForeground < 0) 0
        else if (reservedForForeground > limit - 1) limit - 1
        else reservedForForeground

    /** 后台档可用上限 */
    private val backgroundLimit: Int = limit - reserved

    private val mutex = Mutex()
    private var active = 0

    /** `active` 里属于后台档的那部分：判后台是否到配额只能靠它，`active` 分不出档 */
    private var activeBg = 0

    private var peakCount = 0

    /** 前台等待者队列 */
    private val fgWaiters = ArrayDeque<CompletableDeferred<Boolean>>()

    /** 后台等待者队列。单独一条是为了让 `release` 能按档分配空位 */
    private val bgWaiters = ArrayDeque<CompletableDeferred<Boolean>>()

    /** 当前上限 */
    fun getLimit(): Int = limit

    /** 后台档可用上限 */
    fun getBackgroundLimit(): Int = backgroundLimit

    /** 当前正在执行的任务数 */
    suspend fun activeCount(): Int = mutex.withLock { active }

    /** 当前正在执行的后台任务数 */
    suspend fun activeBackgroundCount(): Int = mutex.withLock { activeBg }

    /** 正在等待空位的任务数（前台 + 后台） */
    suspend fun pendingCount(): Int = mutex.withLock { fgWaiters.size + bgWaiters.size }

    /** 正在等待空位的前台任务数 */
    suspend fun pendingForegroundCount(): Int = mutex.withLock { fgWaiters.size }

    /** 历史最大并发数（NFR-004 的断言对象） */
    suspend fun peak(): Int = mutex.withLock { peakCount }

    /** 清空峰值记录（单测在每例开始处调用，避免用例间互相污染） */
    suspend fun resetPeak() {
        mutex.withLock { peakCount = active }
    }

    /**
     * 申请一个空位。
     *
     * 有空位立即返回 `true`；无空位则挂起，直到有空位（返回 `true`）
     * 或该等待被 [cancelQueued] 丢弃（返回 `false`）。
     *
     * 返回 `false` 表示**任务不应执行**——调用方必须据此跳过。
     * 故通常直接用 [run]，不要手写 acquire/release 配对。
     *
     * @param background 以后台档申请：只能用部分空位，且归还时前台优先
     */
    suspend fun acquire(background: Boolean = false): Boolean {
        val waiter: CompletableDeferred<Boolean>? = mutex.withLock {
            if (canTake(background)) {
                take(background)
                null
            } else {
                // 无位：入队等待。空位的分配推迟到 release 时进行，
                // 这样"唤醒即占用"是原子的，不会出现两个等待者抢同一个空位。
                CompletableDeferred<Boolean>().also {
                    if (background) bgWaiters.addLast(it) else fgWaiters.addLast(it)
                }
            }
        }
        if (waiter == null) return true

        // 【取消必须把自己从队列里摘掉】
        //
        // 曾被漏掉，后果是全局死锁：等待者被取消后仍留在 `waiters` 里，
        // 之后每次 `release()` 都把空位交给这个**没人接**的 deferred——
        // `active` 不减（代码把它算作已转交），队列却少一个真等待者。
        // 漏满 `limit` 个名额后，所有出网永久挂起，表现为「详情页一直加载不出来，
        // 整个 App 再也发不出请求」。
        //
        // 触发条件很常见：详情页一开就对十几个候选源并发搜索，`withTimeoutOrNull`
        // 到点把没抢到空位的那些一并取消。
        //
        // 两种时序都要兜住：
        //  - 还没到点：本协程仍排在队列里 → 自己摘掉即可
        //  - 已被 `release()` 唤醒并转交了空位（`await()` 在已完成后仍可能因
        //    协程被取消而抛）→ 空位已经算在本协程头上，必须还回去
        try {
            return waiter.await()
        } catch (e: CancellationException) {
            val stillQueued = withContext(NonCancellable) {
                mutex.withLock { fgWaiters.remove(waiter) || bgWaiters.remove(waiter) }
            }
            if (!stillQueued) {
                withContext(NonCancellable) { release(background) }
            }
            throw e
        }
    }

    /** 在 `mutex` 内调用 */
    private fun canTake(background: Boolean): Boolean =
        active < limit && (!background || activeBg < backgroundLimit)

    /** 在 `mutex` 内调用 */
    private fun take(background: Boolean) {
        active++
        if (background) activeBg++
        if (active > peakCount) peakCount = active
    }

    /**
     * 归还一个空位，并让等待最久、且配额允许的任务接走。
     *
     * 重复释放（`active` 已为 0 时再调）不改变状态——宁可静默忽略，
     * 也不要让计数变成负数而放大上限。
     */
    suspend fun release(background: Boolean = false) {
        mutex.withLock {
            if (active <= 0) return
            active--
            if (background && activeBg > 0) activeBg--

            // 前台永远优先：只要有人在等前台空位就先给它，
            // 后台搜索再多也压不住一次进详情。
            val fg = fgWaiters.removeFirstOrNull()
            if (fg != null) {
                take(background = false)
                fg.complete(true)
                return
            }
            // 前台队列空了才轮到后台，且仍受后台配额约束
            val bg = bgWaiters.firstOrNull()
            if (bg != null && canTake(background = true)) {
                bgWaiters.removeFirst()
                take(background = true)
                bg.complete(true)
            }
        }
    }

    /**
     * 丢弃所有**排队中**的任务，返回丢弃数量。
     *
     * 语义边界：只丢「还没拿到空位」的，**已在跑的不动**——它们会跑完并归还空位。
     * 这与「不再发新一批、已发出收尾」一致，也是唯一不破坏调用方契约的做法
     * （半途掐断一个正在读的响应没有安全的收尾方式）。
     *
     * 用途：搜索换词时旧批次的排队请求不该再执行——否则它们会占着空位跑完，
     * 把新词的请求挤到后面，用户看到的是"换了词却还是慢"。
     */
    suspend fun cancelQueued(): Int {
        val pending: List<CompletableDeferred<Boolean>> = mutex.withLock {
            val snapshot = fgWaiters.toList() + bgWaiters.toList()
            fgWaiters.clear()
            bgWaiters.clear()
            snapshot
        }
        for (w in pending) w.complete(false)   // false = 被丢弃，不执行
        return pending.size
    }

    /**
     * 在闸门内执行一个任务：申请 → 执行 → 归还（无论成败）。
     *
     * @param background 是否以后台档申请。后台档只能用部分空位，前台优先。
     * @return 任务结果；若在等待期间被 [cancelQueued] 丢弃，返回 `null`
     */
    suspend fun <T> run(background: Boolean = false, fn: suspend () -> T): T? {
        if (!acquire(background)) return null
        try {
            return fn()
        } finally {
            // 取消也要还：`release()` 里要拿 mutex，而已取消的协程再进起点会
            // 立刻抛 Cancellation——空位就漏掉了。
            withContext(NonCancellable) { release(background) }
        }
    }

    /** 仅用于单测复位：清空所有状态，等待中的任务不会被唤醒（测试应确保无在途任务） */
    suspend fun resetForTest() {
        mutex.withLock {
            active = 0
            activeBg = 0
            peakCount = 0
            fgWaiters.clear()
            bgWaiters.clear()
        }
    }

    companion object {
        /**
         * 全局闸门上限。
         *
         * 上限 6 的来历：原分批并发为 3，而分批是"一层叠一层"——分类映射 3 + 列表 3
         * 就可能到 6。取 6 既覆盖原有正常峰值，又给快速切页留出可观测的封顶。
         * 若后续实测发现系统连接数吃紧，改这一个常量即可全局生效。
         */
        const val OUTBOUND_LIMIT = 6

        /**
         * 保留给前台的并发空位数。
         *
         * 取 2：真机实测（docs/04 §16.34）候选源搜索能在闸门前排 412~3161ms，
         * 而详情请求本身的源站耗时只要 400~1100ms。留 2 个前台位后，
         * 后台搜索最多同时跑 4 个，详情/搜索/翻页这类用户在等的请求随到随走。
         *
         * 不再往上加的原因：后台只剩 4 个位会让候选源搜索本身变慢，
         * 而候选是「多给几个换线选项」，慢一点无感；前台慢一点用户就在盯转圈。
         */
        const val FOREGROUND_RESERVED = 2

        /** 全局单例：`HttpClient` 的全部出网路径都经它 */
        val outbound: ConcurrencyGate by lazy {
            ConcurrencyGate(OUTBOUND_LIMIT, FOREGROUND_RESERVED)
        }
    }
}
