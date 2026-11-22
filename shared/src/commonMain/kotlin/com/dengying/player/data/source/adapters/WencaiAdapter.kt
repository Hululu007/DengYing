package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.SourceSecrets
import com.dengying.player.core.VideoItem
import com.dengying.player.data.net.HttpClient
import com.dengying.player.data.source.crypto.Digest
import com.dengying.player.platform.Clock
import com.dengying.player.platform.UrlCodec

/**
 * 适配器：文才（mw-movie 匿名接口）—— MD5 + SHA1 签名 JSON
 *
 * 接口形态（AH 接口清单 §1.7）：
 *   GET {api}/mw-movie/anonymous/video/searchByWord?keyword=&pageNum=1&pageSize=8   header: t / sign
 *   GET {api}/mw-movie/anonymous/video/detail?id=                                   header: t / sign
 *   GET {api}/api/mw-movie/anonymous/v2/video/episode/url?clientType=3&id=&nid=      header: t / sign
 *
 * 签名：`sign = SHA1( MD5( "<参数串>&key=<共享密钥>&t=<毫秒时间戳>" ) )`。
 * 参数串按接口分别为 `keyword=&pageNum=1&pageSize=8``id=`、`clientType=3&id=&nid=`。
 *
 * **注意签名里的 `keyword` 用原文（未 URL 编码），URL 里才编码**——
 * 这一条搞错会得到「签名错误」，但 URL 看起来完全正常，极难排查。
 *
 * 实测（2026-10-04）：搜索「流浪地球」返回 `code:200`、107 条；详情签名被接受
 * （`code:900404` 表示 id 不存在，不是签名错误）。
 *
 * ## 本文件只留什么（KMP P3 批 C-4）
 *
 * 响应解析全部移到 commonMain 的 [WencaiParser]；这里只剩签名、时间戳与 HTTP 控制流。
 * 签名留在这里，因为 `SourceSecrets` 的密钥常量仍在 `:app`。`HttpClient` 与参数编码
 * 已分别随批 D、批 F 跨平台化（[com.dengying.player.platform.UrlCodec]）。
 */
object WencaiAdapter {

    private const val CONNECT_MS = 6000
    private const val READ_MS = 8000

    private fun enc(s: String): String =
        UrlCodec.encode(s)

    /**
     * 生成签名头：`t` = 毫秒时间戳，`sign` = SHA1(MD5(参数串))。
     *
     * 参数串里的 `{t}` 占位**哈希之前**替换成同一时间戳——
     * 先哈希再替换会让服务端算出的摘要与提交的不一致。
     *
     * 时间源用 [Clock.nowMs] 而不是 `System.currentTimeMillis()`：后者在
     * iOS/native 上不存在，且这里要的正是墙钟（签名要与服务端对齐）。
     */
    private fun signHeaders(raw: String): Map<String, String> {
        val ts = Clock.nowMs().toString()
        val body = raw.replace("{t}", ts)
        val md5 = Digest.md5Hex(body)
        val sign = Digest.sha1Hex(md5)
        return mapOf("t" to ts, "sign" to sign)
    }

    /** 搜索 */
    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        val headers = signHeaders(SourceSecrets.wencaiSearchSignRaw(keyword, "{t}"))
        val url = "${source.api}/mw-movie/anonymous/video/searchByWord" +
            "?keyword=${enc(keyword)}&pageNum=1&pageSize=8"
        val r = HttpClient.get(url, headers, CONNECT_MS, READ_MS)
        if (r.status != 200) return WencaiParser.failed(source, 1, "HTTP ${r.status}")
        return WencaiParser.parseSearch(source, r.body)
    }

    /** 详情（播放地址是 `nid`，需经 [resolvePlay] 换取） */
    suspend fun detail(source: CmsSource, vodId: Long): VideoItem? {
        val headers = signHeaders(SourceSecrets.wencaiDetailSignRaw(vodId.toString(), "{t}"))
        val r = HttpClient.get(
            "${source.api}/mw-movie/anonymous/video/detail?id=$vodId", headers, CONNECT_MS, READ_MS,
        )
        if (r.status != 200) return null
        return WencaiParser.parseDetail(source, r.body, vodId)
    }

    /** 播放换链：episode/url → `data.list[0].url`（token 可能是纯 nid 或带前缀的绝对地址） */
    suspend fun resolvePlay(
        source: CmsSource,
        vodId: Long,
        token: String,
        from: String,
    ): PlayResolveResult {
        val nid = WencaiParser.nidOfToken(token)
        if (nid.isEmpty()) return PlayResolveResult()
        val headers = signHeaders(SourceSecrets.wencaiPlaySignRaw(vodId.toString(), nid, "{t}"))
        val url = "${source.api}/api/mw-movie/anonymous/v2/video/episode/url" +
            "?clientType=3&id=$vodId&nid=${enc(nid)}"
        val r = HttpClient.get(url, headers, CONNECT_MS, READ_MS)
        if (r.status != 200) return PlayResolveResult()
        return WencaiParser.parsePlay(r.body)
    }
}
