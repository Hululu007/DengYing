package com.dengying.player.data.source.adapters

import com.dengying.player.data.net.HttpClient
import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.SourceSecrets
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.data.source.crypto.Digest
import com.dengying.player.platform.Clock
import com.dengying.player.platform.UrlCodec
import kotlin.random.Random

/**
 * 适配器：米米4k（MINO / 星影）—— 普通 JSON + `/parse` 换链
 *
 * 接口形态（AH 接口清单 §1.1）：
 *   GET  {base}/api/search?wd=&page=1&limit=20   搜索
 *   GET  {base}/api/videos/{vod_id}              详情
 *   POST {base}/api/parse                        {url, from, vod_id} → 最终 m3u8/mp4/iframe
 *
 * 详情里的播放地址是**线路 token**，本地无法解密，必须经 `/parse` 换取（`direct=false`）。
 * **注意 `vod_id` 必须是整数，传字符串会被拒**。
 *
 * 实测（2026-10-31，对照 AH 清单第四轮）：全链免登录可播——此前 40110「请登录后观看」
 * 的根因是请求缺 `X-Device-ID` 头；游客每日约 2 次解析配额也绑在这个头上
 * （code:40311 用尽，换新设备号即重置，见 [rotateDeviceId]）。
 */
object MinoAdapter {

    private const val CONNECT_MS = 6000
    private const val READ_MS = 8000

    private fun enc(s: String): String =
        UrlCodec.encode(s)

    /**
     * 游客设备号（32 位 hex，任意 md5 形态即可）。
     *
     * 服务端把每日免费解析配额绑在 `X-Device-ID` 上：缺失时 `/parse` 一律
     * code:40110「请登录后观看」，用尽时 code:40311。按进程生成一次，
     * 配额用尽由 [resolvePlay] 换号重试（换号即重置配额）。
     */
    private var deviceId: String = newDeviceId()

    private fun newDeviceId(): String =
        Digest.md5Hex("mino-" + Clock.nowMs() + "-" + Random.nextInt(Int.MAX_VALUE))

    private fun headers(): Map<String, String> = mapOf(
        "User-Agent" to SourceSecrets.MINO_UA,
        "Accept" to "application/json",
        "X-Device-ID" to deviceId,
    )

    private fun failed(
        source: CmsSource,
        page: Int,
        err: String,
        outcome: RequestOutcome = RequestOutcome.RequestFailed,
    ) = FetchResult(outcome = outcome, page = page, sourceName = source.name, err = err)

    /** 搜索 */
    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        val url = "${source.api}/search?wd=${enc(keyword)}&page=1&limit=20"
        val r = HttpClient.get(url, headers(), CONNECT_MS, READ_MS)
        if (r.status != 200) return failed(source, 1, "HTTP ${r.status}")

        // 解析层看不到 HTTP 状态码，页号只能由解析结果决定；
        // 该源的搜索接口没有分页参数，故恒为 1，与拆开前一致。
        return MinoParser.search(r.body, source)
    }

    /** 详情（播放地址为线路 token，需经 [resolvePlay] 换取） */
    suspend fun detail(source: CmsSource, vodId: Long): VideoItem? {
        val r = HttpClient.get("${source.api}/videos/$vodId", headers(), CONNECT_MS, READ_MS)
        if (r.status != 200) return null
        return MinoParser.detail(r.body, vodId, source)
    }

    /** 播放换链：POST /parse（`vod_id` 必须是整数；配额用尽自动换设备号重试一次） */
    suspend fun resolvePlay(
        source: CmsSource,
        vodId: Long,
        token: String,
        from: String,
    ): PlayResolveResult {
        if (token.isEmpty()) return PlayResolveResult()

        val payload = MinoParser.buildParseBody(token, from, vodId)
        fun parseHeaders(): Map<String, String> = mapOf(
            "User-Agent" to SourceSecrets.MINO_UA,
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "X-Device-ID" to deviceId,
        )
        var r = HttpClient.post("${source.api}/parse", payload, parseHeaders(), CONNECT_MS, READ_MS)
        // 40311「今日免费次数已用完」：配额绑在设备号上，换号即重置，重试一次
        if (MinoParser.quotaExceeded(r.body)) {
            deviceId = newDeviceId()
            r = HttpClient.post("${source.api}/parse", payload, parseHeaders(), CONNECT_MS, READ_MS)
        }
        if (r.status != 200) return PlayResolveResult()
        return MinoParser.parsePlay(r.body)
    }
}
