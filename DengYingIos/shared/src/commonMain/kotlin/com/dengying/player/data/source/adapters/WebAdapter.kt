package com.dengying.player.data.source.adapters

import com.dengying.player.data.net.HttpClient
import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.VideoItem
import com.dengying.player.core.WebRule
import com.dengying.player.core.WebTokenPlayAlg
import com.dengying.player.core.WebTokenPlayRule
import com.dengying.player.platform.Clock
import com.dengying.player.platform.UrlCodec

/**
 * 适配器：网站型源（HTML 正则抓取）—— 奇优 / 农民影视 / 两个BT / 素白白
 *
 * 形态：搜索页 → 条目列表（名称/详情链接/海报/状态）→ 详情页（线路 + 集数）→ 播放页
 * 播放地址三条路（按序尝试，都不成立才嗅探兜底）：
 *  1. 签名换链（[WebTokenPlayRule]）：播放页取令牌 → 站点算法签名 → 换链接口（两个BT）
 *  2. 站点播放器自解析链（[WebPlayRule]）：播放页 → 播放器 iframe → 播放器页常量
 *     → `{mediaUrlTemplate}` 换出最终媒体地址（奇优已实测闭环）
 *  3. macCMS 播放页 `player_aaaa`（`WebRule.playerAaaa`）：页内变量直接带媒体地址
 *     （农民影视 nmystv 新域形态，复用 [Ap2233PlayPageParser] 解码）
 * 嗅探兜底：在播放页/播放器页正文里按白名单关键字直接找媒体地址
 *
 * 解析规则已抽到 `:shared` 的 `WebParser`（KMP P3 批 C-2）：本文件只管发请求、
 * 拼 URL、判状态码、决定「要不要再抓播放器页」。这样正则与 JSON 拼接能编进 iOS 端，
 * 也能用金样本离线钉住。
 */
object WebAdapter {

    private const val CONNECT_MS = 6000
    private const val READ_MS = 8000

    private fun enc(s: String): String =
        UrlCodec.encode(s)

    /** 搜索 */
    suspend fun search(source: CmsSource, keyword: String, rule: WebRule): FetchResult {
        val encoded = enc(keyword)
        if (rule.search.method == "POST") {
            val body = "${rule.search.field}=$encoded"
            val r = HttpClient.post(
                rule.search.url, body,
                mapOf(
                    "User-Agent" to HttpClient.UA_PC,
                    "Content-Type" to "application/x-www-form-urlencoded",
                ),
                CONNECT_MS, READ_MS, "text/html",
            )
            if (r.status != 200) return WebParser.failed(source, 1, "HTTP ${r.status}")
            return WebParser.parseSearchHtml(source, r.body, rule)
        }
        val url = rule.search.url.replace("{kw}", encoded).replace("{field}", rule.search.field)
        val r = HttpClient.get(
            url, mapOf("User-Agent" to HttpClient.UA_PC, "Accept" to "text/html,*/*"),
            CONNECT_MS, READ_MS,
        )
        if (r.status != 200) return WebParser.failed(source, 1, "HTTP ${r.status}")
        return WebParser.parseSearchHtml(source, r.body, rule)
    }

    /** 详情：优先用 `vodUri`（原站详情链接），否则由数字 id 反推（站点模板见规则 base） */
    suspend fun detail(
        source: CmsSource,
        vodId: Long,
        rule: WebRule,
        vodUri: String,
    ): VideoItem? {
        if (vodUri.isEmpty()) return null
        val r = HttpClient.get(
            vodUri, mapOf("User-Agent" to HttpClient.UA_PC, "Accept" to "text/html,*/*"),
            CONNECT_MS, READ_MS,
        )
        if (r.status != 200) return null
        return WebParser.parseDetailHtml(source, r.body, rule, vodId)
    }

    /**
     * 播放页 → 媒体地址（站点播放器自解析链 + 嗅探兜底）。
     *
     * 控制流留在本文件：先抓播放页，命中 iframe 才去抓播放器页，播放器页 200 且
     * 三个常量齐备才算成功；任一步不成立都落到嗅探兜底。
     */
    suspend fun resolvePlay(
        source: CmsSource,
        pageUrl: String,
        rule: WebRule,
    ): PlayResolveResult {
        if (pageUrl.isEmpty()) return PlayResolveResult()
        val page = HttpClient.get(
            pageUrl, mapOf("User-Agent" to HttpClient.UA_PC, "Accept" to "text/html,*/*"),
            CONNECT_MS, READ_MS,
        )
        if (page.status != 200) return PlayResolveResult()

        // 签名换链型（两个BT）：播放页里取 userlink 与本集 dataid，签名后请求换链接口
        val tokenRule = rule.tokenPlay
        if (tokenRule != null) {
            signedPlay(source, pageUrl, page.body, tokenRule)?.let { return it }
            return PlayResolveResult()
        }

        val playRule = rule.play
        if (playRule != null) {
            val im = Regex(playRule.iframePattern).find(page.body)
            val iframeUrl = im?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
            if (iframeUrl != null) {
                val player = HttpClient.get(
                    iframeUrl,
                    mapOf(
                        "User-Agent" to HttpClient.UA_PC,
                        "Referer" to pageUrl,
                        "Accept" to "text/html,*/*",
                    ),
                    CONNECT_MS, READ_MS,
                )
                if (player.status == 200) {
                    val built = WebParser.playFromPlayerPage(playRule, iframeUrl, player.body)
                    if (built != null) {
                        if (playRule.jsonUrlKey == null) return built
                        // 两步换链：构造地址返回 JSON，url 字段才是媒体地址（奇优 /player/api.php）
                        val api = HttpClient.get(
                            built.url,
                            mapOf(
                                "User-Agent" to HttpClient.UA_PC,
                                "Referer" to iframeUrl,
                                "Accept" to "application/json, text/plain, */*",
                            ),
                            CONNECT_MS, READ_MS,
                        )
                        if (api.status == 200) {
                            WebParser.playFromJsonResponse(playRule, api.body)?.let { return it }
                        }
                    }
                }
            }
        }

        // macCMS 播放页型（农民影视 nmystv）：player_aaaa 带明文（或 macCMS 级加密）地址。
        // 解出非 http 开头的地址（相对令牌，如 nmdvd 线）视为该线不可播，回落嗅探。
        if (rule.playerAaaa) {
            val media = Ap2233PlayPageParser.parse(page.body)?.decodedUrl ?: ""
            if (media.startsWith("http")) {
                return PlayResolveResult(url = media, headers = "")
            }
        }

        // 嗅探兜底：正文里直接找白名单关键字的媒体地址
        val sniffed = WebParser.sniffMedia(page.body, rule.sniff)
        val hdrs = if (sniffed.isNotEmpty()) {
            WebParser.sniffHeaders(HttpClient.UA_PC, pageUrl)
        } else {
            ""
        }
        return PlayResolveResult(url = sniffed, headers = hdrs)
    }

    /**
     * 签名换链：播放页正文 → 换链接口 → 媒体地址。
     *
     * 任一步拿不到必需参数就返回 `null`（由调用方转成空结果）。**不猜**：
     * dataid 猜错会得到别的剧集（站点对 `p` 与 `v` 的配对是校验的），
     * userlink 取不到可以退化为 `"0"`（与站点 JS 一致），但 dataid 不能退化。
     */
    private suspend fun signedPlay(
        source: CmsSource,
        pageUrl: String,
        pageBody: String,
        rule: WebTokenPlayRule,
    ): PlayResolveResult? {
        val dataId = WebParser.dataIdOf(rule, pageBody, pageUrl)
        if (dataId.isEmpty()) return null
        val v = WebParser.slugOf(pageUrl)
        if (v.isEmpty()) return null
        val userlink = WebParser.userlinkOf(rule, pageBody)
        val url = when (rule.alg) {
            WebTokenPlayAlg.BttwoV1 -> BttwoSign.playUrl(
                host = WebParser.originOf(pageUrl),
                dataId = dataId,
                v = v,
                quality = rule.quality,
                timestamp = Clock.nowMs(),
                userlink = userlink,
            )
        }
        val r = HttpClient.get(
            url,
            mapOf(
                "User-Agent" to HttpClient.UA_PC,
                "Referer" to pageUrl,
                "Accept" to "application/json, text/plain, */*",
            ),
            CONNECT_MS, READ_MS,
        )
        if (r.status != 200) return null
        return WebParser.playFromTokenResponse(rule, r.body)
    }
}
