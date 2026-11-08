package com.dengying.player.data.net

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 出网优先级 —— 协程上下文元素，由 `HttpClient` 读出后交给闸门。
 *
 * 【为什么需要】出网闸门只有 6 个空位，而「打开详情页」这个动作会同时扇出
 * 十几个候选源的搜索。真机实测（见 docs/04 §16.34）候选搜索在闸门前的排队
 * 长达 412~3161ms；详情请求排在它们后面，于是源站本身只要 400~1100ms 的请求
 * 在 App 里被记成 1200~3500ms。
 *
 * 于是分成两档：
 *  - [Foreground]：用户正盯着结果等（进详情、搜索、翻页）
 *  - [Background]：结果晚到几十毫秒无感（候选源预搜）
 *
 * 后台档只能占用闸门的一部分空位，剩下的留给前台，
 * 这样前台请求永远不必等一整批后台搜索跑完。
 *
 * 【为什么用协程上下文而不是加函数参数】搜索链路要穿过
 * 适配器 → 解析器好几层，逐层传参会污染所有签名，且新增调用点容易漏传。
 * 上下文元素一次 `withContext` / `async(context)` 覆盖整棵子树。
 */
class NetPriority private constructor(val background: Boolean) :
    AbstractCoroutineContextElement(NetPriority) {

    companion object Key : CoroutineContext.Key<NetPriority> {

        /** 用户正在等结果的请求（默认档） */
        val Foreground: NetPriority = NetPriority(false)

        /** 后台预取类请求：可让位给前台 */
        val Background: NetPriority = NetPriority(true)
    }
}
