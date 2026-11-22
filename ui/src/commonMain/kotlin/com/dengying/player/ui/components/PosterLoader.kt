package com.dengying.player.ui.components

import com.dengying.player.platform.PlatformLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 海报加载的**调度**部分：单飞去重 + 并发上限 + 内存缓存回填。
 *
 * ## 为什么把它从 `Poster.kt` 搬出来并做成泛型
 *
 * 这里的逻辑（同一 URL 只下载一次、等待者挂起而不是轮询、并发不超过 N）是
 * `Poster.kt` 里唯一**值得测也测得了**的部分，其余是 Compose 渲染与平台 IO。
 * 搬出来之后 `decoder` 由调用方注入，测试可以塞一个可控的假解码器，
 * 不必碰 `PosterFetch` / `PosterDecoder` 两个 expect object。
 *
 * 泛型 [T] 而不是写死 `ImageBitmap`：`ImageBitmap` 在 commonTest 里造不出来
 * （它需要平台图形栈），用 `String` 之类的值类型即可完整覆盖调度语义。
 *
 * ## 契约
 *
 * - `decode` 不抛异常：失败返回 null。等待者拿到的是与真正下载者**同一个结论**。
 * - 下载者协程被取消时，等待者不会永远挂起（见 `finally` 里的兜底 `complete`）。
 */
internal class PosterLoader<T : Any>(
    /** 并发上限：同时在飞的下载数 */
    concurrency: Int,
    /** 真正取数据；失败返回 null，不得抛异常 */
    private val decode: suspend (String) -> T?,
) {
    private val semaphore = Semaphore(concurrency)

    /** 正在下载的 URL（避免同一张图被多个 Composable 同时拉） */
    private val inflight = HashSet<String>()

    /** 单飞等待表：URL → 那次下载的完成信号 */
    private var results = HashMap<String, CompletableDeferred<T?>>()

    /**
     * 单独一把锁：`results` 与 `inflight` 必须同锁读写，
     * 但**不能**和 `semaphore` 的等待混在一起（否则一次慢下载会卡住别的 URL 的登记）。
     */
    private val lock = PlatformLock()

    /**
     * 取一张图。
     *
     * @return 数据；失败返回 null
     */
    suspend fun load(url: String): T? {
        if (url.isEmpty()) return null

        // 同一 URL 只让一个请求真的下载，其余挂起等同一份结果
        val mine = CompletableDeferred<T?>()
        val claimed = lock.withLock {
            if (inflight.add(url)) {
                results[url] = mine
                true
            } else {
                false
            }
        }
        if (!claimed) {
            // 对方可能刚在「加入 inflight」与「登记 results」之间被取消掉结果表——
            // 取不到信号时退化为自己下载，而不是返回 null 让调用方显示占位
            val other = lock.withLock { results[url] }
            return if (other != null) other.await() else null
        }

        try {
            val value = semaphore.withPermit { decode(url) }
            mine.complete(value)
            return value
        } finally {
            // 【必须兜底完成】调用方协程被取消（滚出屏幕、换分类）时 `withPermit`
            // 会抛 CancellationException，上面那行 `complete` 来不及执行——
            // 不在这里补上，等待同一张图的其他 Composable 会永远挂起。
            // `complete` 对已完成的 Deferred 是空操作，不会覆盖真实结果。
            mine.complete(null)
            lock.withLock {
                inflight.remove(url)
                results.remove(url)
            }
        }
    }
}
