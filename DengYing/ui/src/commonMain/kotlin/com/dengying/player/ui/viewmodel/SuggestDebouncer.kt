package com.dengying.player.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 输入联想防抖：连续输入只在停顿后发一次请求。
 *
 * ## 为什么值得单独一个类
 *
 * 联想是「每敲一个字符就触发」的路径。没有防抖等于把用户的打字速度直接
 * 变成上游 QPS——打「甄嬛传」7 个字符 = 7 次请求。而这条路径与其他搜索一样
 * 走 `ConcurrencyGate.outbound`（上限 6），会把真正的搜索挤在后面。
 *
 * 这段逻辑原先写在 `SearchViewModel.loadSuggest` 里，调度靠 `viewModelScope`
 * （`Dispatchers.Main.immediate`），在 `:ui:jvmTest` 里无从驱动，只能靠读代码。
 * 而「取消上一次等待」恰恰是最容易在重构中丢掉的一行——丢了照样能跑，
 * 只是请求数悄悄翻倍。所以把 scope 提成参数（与 `PosterLoader` 同做法），
 * 让 `runTest` 能接管虚拟时钟。
 *
 * ## 用法
 *
 * ```
 * debouncer.submit(keyword) { kw -> ... 真正的请求 ... }
 * ```
 * 每次 [submit] 取消上一次尚未结束的等待；等待结束后才执行 [block]。
 * [block] 内部仍应自己校验会话序号（用户可能在等待期间又改了词，
 * 但那时 [submit] 会取消这次，所以仍需校验的是**请求已发出之后**的竞态）。
 */
class SuggestDebouncer(
    private val scope: CoroutineScope,
    private val delayMs: Long,
) {

    private var job: Job? = null

    /** 当前是否有等待中的任务（仅供测试与调试） */
    val pending: Boolean get() = job?.isActive == true

    /**
     * 提交一次输入。
     *
     * 取消上一次未完成的等待，然后在 [delayMs] 后执行 [block]。
     * 返回本次的 [Job]，便于调用方（或测试）等待其结束。
     */
    fun submit(block: suspend () -> Unit): Job {
        job?.cancel()
        val j = scope.launch {
            delay(delayMs)
            block()
        }
        job = j
        return j
    }

    /** 主动取消（例如输入被清空、页面离开） */
    fun cancel() {
        job?.cancel()
        job = null
    }
}
