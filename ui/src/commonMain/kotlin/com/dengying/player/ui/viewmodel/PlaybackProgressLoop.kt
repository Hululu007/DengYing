package com.dengying.player.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 播放进度推进循环：**同一时刻只允许一个 Job**。
 *
 * 【为什么要有这个类】`PlayerListener.onPlaying` 不是"每次播放只调一次"——
 * ExoPlayer 在暂停→播放、seek 后恢复、缓冲结束后都会重新触发；`PlaybackSession`
 * 里也曾因此累积过多个 `while (true)`（见其注释）。旧实现每收到一次 `onPlaying`
 * 就 `launch` 一个新循环，协程与状态写入随播放次数线性增长，旧 ViewModel 被单例
 * 引擎监听器持有后循环还永不停止（审计 B-6）。
 *
 * 把"只启动一次 + 可取消"的语义下沉到这里，让 `:ui:jvmTest` 能用虚拟时钟直接
 * 断言（View 层的 `viewModelScope` 依赖 `Dispatchers.Main`，不适合单独驱动）。
 *
 * @param scope 循环所属作用域（生产传 `viewModelScope`）
 * @param intervalMs 推进间隔
 * @param onTick 每次推进执行；可调用 [stop] 让循环在本次回调后退出
 */
internal class PlaybackProgressLoop(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 1_000L,
    private val onTick: suspend () -> Unit,
) {

    private var job: Job? = null

    /** 当前是否有循环在跑（排障/测试用） */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * 启动循环。已在运行时**直接返回**，不会新建 Job——这正是重复 `onPlaying`
     * 不再累积协程的保证。
     */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                while (isActive) {
                    delay(intervalMs)
                    onTick()
                }
            } finally {
                // 只清理自己这一代：stop() 之后又 start() 的新 Job 不能被旧循环清掉。
                if (job === coroutineContext[Job]) job = null
            }
        }
    }

    /** 取消循环；未启动或已结束时为无操作。 */
    fun stop() {
        val running = job ?: return
        job = null
        running.cancel()
    }
}
