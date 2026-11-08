package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.data.net.HttpClient
import com.dengying.player.platform.UrlCodec

/**
 * 山楂4k —— RSA 加密体源
 *
 * 抓包版（AH 抓包清单 ·1.4）：
 *   POST {api}v1/app/search/searchMovie   明文 JSON：{"condition":{"value":"<kw>"},"pageNum":1,"pageSize":40}
 *   POST {api}v1/app/play/movieDetails    RSA 加密体（`{"key": Base64(RSA_PKCS1(body, 请求公钥))}`，>117B 分块）
 *   GET  {api}v1/app/play/analysisMovieUrl?playerUrl=&playerId=
 *
 * 注意：`api` **结尾带斜杠**，路径直接拼接；`condition.typeId` 必须缺省留空
 * （传空字符串会返回 code 1003）。
 *
 * 实测（2026-10-04）：`POST /api/v1/app/search/searchMovie` 全程 502
 * （9 个用户代理全 502，超时窗口 ~5s、响应体 0 字节）→ 上游不可达，
 * 故该源置 `enabled = false` 并从搜索聚合中排除。
 *
 * 响应端的解密未实现：上游从未返回过可分析的成功体，解密逻辑无从确认，
 * 因此这里只做请求发送与明文解析。
 *
 * 解析与请求体拼装见 [ShanzhaParser]；本文件只负责 HTTP 控制流。
 */
object ShanzhaAdapter {

    private const val CONNECT_MS = 6000
    private const val READ_MS = 8000

    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        val url = "${source.api}v1/app/search/searchMovie"
        val body = ShanzhaParser.searchBody(keyword)

        val r = HttpClient.post(
            url, body, mapOf("Content-Type" to "application/json"), CONNECT_MS, READ_MS,
        )
        if (r.status != 200) {
            return ShanzhaParser.failed(source, "HTTP ${r.status}（上游 2026-10-04 实测 502）")
        }

        return ShanzhaParser.parseSearch(source, r.body)
    }

    /** 换取播放地址：analysisMovieUrl（该接口给 url 与 playerId） */
    suspend fun resolvePlay(source: CmsSource, playerUrl: String, playerId: String): String {
        val url = "${source.api}v1/app/play/analysisMovieUrl" +
            "?playerUrl=${UrlCodec.encodeForm(playerUrl)}" +
            "&playerId=${UrlCodec.encodeForm(playerId)}"
        val r = HttpClient.get(url, null, CONNECT_MS, READ_MS)
        if (r.status != 200) return ""
        return ShanzhaParser.playUrl(r.body)
    }
}
