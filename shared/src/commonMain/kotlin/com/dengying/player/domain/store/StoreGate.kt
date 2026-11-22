package com.dengying.player.domain.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 存储就绪闸门（FR-507）—— 纯状态机，不依赖 Android
 *
 * 需求：本地存储未就绪时，**写操作不得静默丢弃**。
 *
 * 三种状态与对应处置：
 *
 * | 状态 | 读操作 | 写操作 |
 * |---|---|---|
 * | `Pending` 初始化中 | 返回空集合（首帧允许空） | **等待**初始化收敛后再判定，只延迟不丢弃 |
 * | `Ready`   就绪 | 正常读 | 正常写 |
 * | `Failed`  初始化失败 | 返回空集合 | **拒绝**并返回 `false`，调用方给出可见失败；下次写入前自动重试一次 |
 *
 * 本类只承载「该等、该写、还是该拒绝」的判定，不接触 SharedPreferences，
 * 因此可在本地单测覆盖失败路径——真机上存储初始化几乎不失败，
 * 失败路径若无可注入的缝就只能长期未验证。
 *
 * 【并发说明】状态由 [Mutex] 串行化。初始化动作 `open()` 可能是挂起的 IO，
 * 故**不能**把它放在临界区内执行——临界区只做状态迁移，初始化在锁外跑。
 * 这也是初版写错的地方：`synchronized` 块里调用了挂起函数。
 */
class StoreGate {

    enum class State { Pending, Ready, Failed }

    /** 写入前应做的动作 */
    enum class Verdict { Proceed, Wait, Reject }

    /**
     * 初始化动作：成功即返回，失败即抛出（原因取 `message`）。
     *
     * 用 `suspend () -> Unit` 而不是具名接口：`fun interface` 的方法名是自定义的，
     * 以类型引用后无法直接调用；函数类型可以直接 invoke。
     * 类型别名放在文件级（Kotlin 不支持嵌套 typealias 且不启用实验开关）。
     */
    // 见文件末尾 `typealias GateOpener`



    private val lock = Mutex()

    @kotlin.concurrent.Volatile
    private var state: State = State.Pending

    @kotlin.concurrent.Volatile
    private var failureReason: String = ""

    @kotlin.concurrent.Volatile
    private var opener: GateOpener? = null

    /** 在途初始化；用 CompletableDeferred 而不是 Deferred，便于在锁内创建、锁外等待 */
    @kotlin.concurrent.Volatile
    private var inflight: CompletableDeferred<Boolean>? = null

    /** 协程作用域，由持有者在构造后注入；负责承载初始化任务 */
    @kotlin.concurrent.Volatile
    var scope: CoroutineScope? = null

    /** 绑定初始化动作（可重复绑定，用于重试） */
    fun bind(opener: GateOpener) {
        this.opener = opener
    }

    /** 当前状态 */
    fun state(): State = state

    /** 最近一次失败原因；无失败返回空串 */
    fun reason(): String = failureReason

    /** 是否已有初始化在途 */
    fun isInflight(): Boolean = inflight != null

    /**
     * 由初始化兑现结果驱动状态迁移（同步，便于直接断言）。
     *
     * @param ok 初始化是否成功
     * @param reason 失败原因（成功时忽略）
     */
    fun settleWith(ok: Boolean, reason: String) {
        if (ok) {
            state = State.Ready
            failureReason = ""
        } else {
            state = State.Failed
            failureReason = reason
        }
    }

    /** 写操作前的判定（同步） */
    fun verdict(): Verdict = when (state) {
        State.Ready -> Verdict.Proceed
        State.Pending -> Verdict.Wait
        State.Failed -> Verdict.Reject
    }

    /** 失败态才需要重试 */
    fun shouldRetry(): Boolean = state == State.Failed && opener != null

    /** 是否可写（供挂起之后判定，避免状态在等待期间被改写而读到陈旧值） */
    fun isReady(): Boolean = state == State.Ready

    /**
     * 启动一次初始化；已完成或进行中则复用现有结果。
     *
     * 状态迁移与任务创建在锁内完成，**初始化动作本身在锁外执行**。
     *
     * @return 是否为就绪态
     */
    suspend fun start(): Boolean {
        // 已完成：直接返回，不重复开启
        if (isReady()) return true

        // 锁内：拿到在途任务，或创建一条新的登记（创建只登记，**不执行**）
        var created: GateOpener? = null
        val existing: CompletableDeferred<Boolean>? = lock.withLock {
            if (state == State.Ready) return true
            inflight?.let { return@withLock it }

            val fn = opener
            if (fn == null) {
                settleWith(false, "no opener bound")
                return@withLock CompletableDeferred<Boolean>().also { it.complete(false) }
            }
            val gate = CompletableDeferred<Boolean>()
            inflight = gate
            state = State.Pending
            created = fn
            gate
        }

        // 已有在途初始化：等它，不重复发起
        if (created == null) return existing!!.await()

        val fn: GateOpener = created
        val gate: CompletableDeferred<Boolean> = existing!!

        // 锁外执行挂起的初始化动作
        val sc = scope
        val job: Deferred<Unit>? = sc?.async {
            try {
                fn()
                settleWith(true, "")
            } catch (t: Throwable) {
                settleWith(false, t.message ?: "unknown")
            } finally {
                lock.withLock { inflight = null }
                gate.complete(isReady())
            }
        }

        // 没有注入作用域时退化为同步执行（单测里没有协程作用域也能跑）
        if (job == null) {
            try {
                fn()
                settleWith(true, "")
            } catch (t: Throwable) {
                settleWith(false, t.message ?: "unknown")
            } finally {
                lock.withLock { inflight = null }
                gate.complete(isReady())
            }
        } else {
            job.await()
        }

        return isReady()
    }

    /**
     * 显式重试（FR-507「可重试」的落点）：清掉在途状态并重新初始化。
     *
     * @return 重试后是否就绪
     */
    suspend fun retry(): Boolean {
        if (isReady()) return true
        lock.withLock { inflight = null }
        return start()
    }

    /**
     * 写入前取用：等待收敛；失败态下重试一次。
     *
     * 「等待」即 FR-507 的「就绪前的写操作不丢弃」——写入方挂起本方法，
     * 初始化收敛后照常落盘，无需单独的写队列。
     *
     * 重试语义：一次 acquire 至多触发一次初始化。
     * - `Pending`：等待在途的初始化收敛，据其结果返回（这是首次尝试）
     * - `Failed` ：发起一次新尝试（这是重试）
     *
     * 两条路径互斥，故一次 acquire 不会把「首次失败」与「立即重试」叠在一起——
     * 否则失败态在同一调用内被自愈，调用方与用户都观测不到失败。
     *
     * @return 是否可写
     */
    suspend fun acquire(): Boolean {
        return when (state()) {
            State.Pending -> {
                start()
                isReady()
            }
            State.Failed -> {
                if (opener != null) retry()
                isReady()
            }
            State.Ready -> true
        }
    }

    /** 仅用于单测复位：清空所有状态 */
    suspend fun resetForTest() {
        lock.withLock {
            state = State.Pending
            failureReason = ""
            inflight = null
        }
    }
}


/** 初始化动作：成功即返回，失败即抛出（原因取 message）。 */
typealias GateOpener = suspend () -> Unit
