package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.data.net.HttpClient
import com.dengying.player.platform.UrlCodec

/**
 * XPTV csp 脚本型站点适配器（当前落地：橙果短剧）。
 *
 * 统一走 [HttpClient]，按 JS 脚本 `chengguo.js` 的契约请求 HTML 页面：
 * 分类 `/{tid}`、搜索 `/search?keyword=`、详情 `/drama/{slug}`、播放 `/play/{slug}/{n}`。
 * 数据解析在 [XptvParser]，本文件只负责拼 URL、带移动端 UA、判状态码。
 */
object XptvAdapter {

    private const val CONNECT_MS = 8000
    private const val READ_MS = 10000

    private val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "zh-CN,zh;q=0.9",
        "Referer" to "https://chengguodj.com/",
    )

    private fun base(source: CmsSource): String = source.api.trimEnd('/')

    private suspend fun fetch(url: String): HttpClient.HttpResult =
        HttpClient.get(url, HEADERS, CONNECT_MS, READ_MS)

    suspend fun classes(): List<com.dengying.player.core.CmsClass> = XptvParser.classes()

    suspend fun list(source: CmsSource, typeId: Int?, page: Int): FetchResult {
        val path = XptvParser.classPath(typeId)
        val url = if (page <= 1) "${base(source)}/$path" else "${base(source)}/$path/page-$page"
        val r = fetch(url)
        if (r.status != 200) {
            return FetchResult(
                outcome = RequestOutcome.RequestFailed,
                page = page,
                sourceName = source.name,
                err = "HTTP ${r.status}",
            )
        }
        return XptvParser.list(r.body, source, page)
    }

    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        val kw = UrlCodec.encode(keyword)
        val url = "${base(source)}/search?keyword=$kw"
        val r = fetch(url)
        if (r.status != 200) {
            return FetchResult(
                outcome = RequestOutcome.RequestFailed,
                sourceName = source.name,
                err = "HTTP ${r.status}",
            )
        }
        return XptvParser.list(r.body, source, 1)
    }

    suspend fun detail(source: CmsSource, vodId: Long, vodUri: String): VideoItem? {
        if (vodUri.isEmpty()) return null
        val r = fetch("${base(source)}$vodUri")
        if (r.status != 200) return null
        return XptvParser.detail(r.body, source, vodUri)
    }

    suspend fun resolvePlay(
        source: CmsSource,
        vodId: Long,
        token: String,
        from: String,
    ): PlayResolveResult {
        if (token.isEmpty()) return PlayResolveResult()
        val r = fetch("${base(source)}$token")
        if (r.status != 200) return PlayResolveResult()
        return PlayResolveResult(url = XptvParser.play(r.body), headers = "")
    }
}
