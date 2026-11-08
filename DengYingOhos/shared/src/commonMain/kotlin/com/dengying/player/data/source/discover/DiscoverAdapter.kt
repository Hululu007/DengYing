package com.dengying.player.data.source.discover

import com.dengying.player.core.DiscoverFeed
import com.dengying.player.core.DiscoverKind
import com.dengying.player.core.DiscoverQuery
import com.dengying.player.data.net.HttpClient

/**
 * 发现层适配器 —— 只管发请求，解析委派 [DiscoverParser]
 *
 * 与既有 10 个源适配器（`data/source/adapters/XxAdapter`）的区别：发现层不发「搜索/详情/
 * 换链」三段，只有「拉一张列表」，且失败语义不进四态判定（见 `core/DiscoverTypes.kt`
 * 里 `DiscoverFeed` 的 Kdoc、`docs/04` §15.6.1）。
 *
 * **不新增平台门面**（docs/04 §15.0.8）：两个端点都是普通 HTTP + JSON，
 * `HttpTransport`/`JsonX` 已在 `commonMain`。
 *
 * 设计依据：`docs/04-验证记录.md` §15.6.1。
 */
object DiscoverAdapter {

    private const val CONNECT_MS = 6000
    private const val READ_MS = 8000

    /**
     * 爱奇艺搜索联想。
     *
     * 移动端接口：`Referer` 给爱奇艺首页，`Accept` 给 JSON（虽然实测响应头是
     * `text/html`，但 body 是合法 JSON）。
     */
    suspend fun iqiyiSuggest(keyword: String): DiscoverFeed {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            return DiscoverFeed(DiscoverKind.IqiyiSuggest, err = "请输入关键词")
        }
        val r = HttpClient.get(
            DiscoverQuery.iqiyiSuggestUrl(kw),
            mapOf(
                "User-Agent" to HttpClient.UA_SYSTEM,
                "Referer" to "https://www.iqiyi.com/",
                "Accept" to "application/json, text/plain, */*",
            ),
            CONNECT_MS, READ_MS,
        )
        if (r.status != 200) {
            return DiscoverFeed(DiscoverKind.IqiyiSuggest, err = "HTTP ${r.status}")
        }
        return DiscoverParser.parseIqiyiSuggest(r.body)
    }

    /**
     * 豆瓣分类榜单（`search_subjects` 老端点）。
     *
     * 【为什么不用 [doubanAnime]】2026-11-01 实测 `new_search_subjects` 已被豆瓣按 IP
     * 软风控——返回 `{"data":[]}` 空数组而非报错，连原本可用的「动漫」也一起失效。
     * `search_subjects` 风控更松：同 IP 连续 6 次请求仍返回 20 条（间隔 15–20s）。
     *
     * 两端的条目字段完全兼容（`title`/`rate`/`cover`/`url`/`id`），只有顶层数组名
     * 不同（`subjects` vs `data`），故解析仍走 [DiscoverParser.parseDoubanAnime]。
     *
     * @param type 豆瓣粗分类：`movie` 或 `tv`
     * @param tag 细分标签（中文，如「热门」「综艺」「日本动画」）
     */
    suspend fun doubanRanking(type: String, tag: String, start: Int = 0): DiscoverFeed {
        val page = DiscoverQuery.doubanPageOf(start)
        val r = HttpClient.get(
            DiscoverQuery.doubanRankingUrl(type, tag, start),
            mapOf(
                "User-Agent" to HttpClient.UA_PC,
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "https://movie.douban.com/",
            ),
            CONNECT_MS, READ_MS,
        )
        if (r.status != 200) {
            return DiscoverFeed(DiscoverKind.DoubanAnime, page = page, err = "HTTP ${r.status}")
        }
        return DiscoverParser.parseDoubanAnime(r.body, page, arrayKey = "subjects")
    }

    /**
     * 豆瓣动漫榜单（`new_search_subjects` 端点）。
     *
     * 该端点**一次性可用**（2026-10-27 实测）：同 IP 第二次请求即返回风控文案。
     * 故这里**不做重试、不换 UA 绕风控**——那只会把临时限制变成长期封禁。
     * 2026-11-01 起该端点对常用 IP 持续返回空数组，首页榜单已改用 [doubanRanking]。
     */
    suspend fun doubanAnime(start: Int = 0): DiscoverFeed {
        val page = DiscoverQuery.doubanPageOf(start)
        val r = HttpClient.get(
            DiscoverQuery.doubanAnimeUrl(start),
            mapOf(
                "User-Agent" to HttpClient.UA_PC,
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "https://movie.douban.com/",
            ),
            CONNECT_MS, READ_MS,
        )
        if (r.status != 200) {
            return DiscoverFeed(DiscoverKind.DoubanAnime, page = page, err = "HTTP ${r.status}")
        }
        return DiscoverParser.parseDoubanAnime(r.body, page)
    }
}
