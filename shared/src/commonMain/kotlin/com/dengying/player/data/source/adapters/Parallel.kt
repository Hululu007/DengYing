package com.dengying.player.data.source.adapters

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 小型并发工具：把「并行发两个请求」写成一行。
 *
 * 为什么单独成文件而不是每个适配器各写一次 `async/await`：
 * 搜索的「双端点并行」策略在 CMS 与几个变体里共用，两处各写一遍会让
 * 「并行还是串行」这件事出现两套实现，而它正是性能契约（FR-202）的一部分。
 */
object Parallel {

    /** 两路并行，按入参顺序返回结果 */
    suspend fun get(
        a: Pair<String, suspend () -> String>,
        b: Pair<String, suspend () -> String>,
    ): List<String> = coroutineScope {
        val da = async { a.second() }
        val db = async { b.second() }
        listOf(da.await(), db.await())
    }

    /** 三路并行，按入参顺序返回结果 */
    suspend fun <T> all3(
        a: suspend () -> T,
        b: suspend () -> T,
        c: suspend () -> T,
    ): List<T> = coroutineScope {
        val da = async { a() }
        val db = async { b() }
        val dc = async { c() }
        listOf(da.await(), db.await(), dc.await())
    }
}
