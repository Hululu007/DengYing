package com.dengying.player.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 冷启动阶段的进程级「第一帧内容」信号。
 *
 * 【解决什么】冷启动时三件事同时发生在 t=0：首页/分类首批、发现页第一条可播
 * 地址、更新检查请求。它们都吃同一个 6 席出网闸（OUTBOUND_LIMIT=6），更新检查
 * 抢到席位就会把用户等的那张卡往后推 10-20s（计划 18 §12.2）。
 *
 * 【怎么用】界面线程在真正出现内容/首个可播地址时 [markFirstContent] /
 * [markFirstPlayableUrl]；后台工作（更新检查、发现页详情预取）用
 * [awaitFirstContent] / [awaitFirstPlayableUrl] 等一个带上限的信号。超时也放行，
 * 不能因为首页回包慢就把更新检查永久卡住。
 *
 * 信号只标记不重置：冷启动只有一次，第二次进首页时这些 Deferred 早已完成，
 * 等待立即返回，不会引入额外延迟。
 */
internal object AppStartupSignals {
    private val firstContent = CompletableDeferred<Unit>()
    private val firstPlayableUrl = CompletableDeferred<Unit>()

    fun markFirstContent() {
        firstContent.complete(Unit)
    }

    fun markFirstPlayableUrl() {
        firstPlayableUrl.complete(Unit)
    }

    suspend fun awaitFirstContent(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { firstContent.await() } != null

    suspend fun awaitFirstPlayableUrl(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { firstPlayableUrl.await() } != null
}
