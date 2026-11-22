package com.dengying.player.data.source.adapters

import com.dengying.player.data.net.HttpClient
import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.VideoItem
import com.dengying.player.data.source.crypto.JavaRandom
import com.dengying.player.platform.Clock
import com.dengying.player.platform.UrlCodec

/**
 * 适配器：独播库（dbokutv）—— Java LCG 签名 JSON + 自定义解码
 *
 * 接口形态（AH 接口清单 §1.11）：
 *   GET {api}/vodsearch{sign}&wd=<关键词>     搜索 → 数组 [{Name, Rating, Tag, DId, TnId}]
 *   GET {api}{detailPath}{sign}               详情 → {Name, Playlist:[{EpisodeName, VId}], ...}
 *   GET {api}{playPath}{sign}                 播放 → {HId} → 解码得最终地址
 *
 * 签名 `{sign} = ?sign={a1}&ssid={b64url}&token={a2}`，由 Java 48 位 LCG 派生（见 crypto/JavaRandom）。
 * 详情/播放路径由服务端返回的 `DId` / `VId` 经「每 10 字符一组反转 + Base64」解码得到。
 *
 * 解析与解码已抽到 `DubokuParser`（KMP P3 批 C-5，commonMain）；本对象只留 HTTP 编排：
 * 连接超时、参数编码（[com.dengying.player.platform.UrlCodec]）、签名时间戳（[Clock.nowMs]）、`HttpClient.UA_PC`。
 *
 * 实测（2026-10-04）：搜索「庆余年」返回真实数据；`/vodsearch?wd=` 无签名被拒（400）。
 */
object DubokuAdapter {

    private const val CONNECT_MS = 6000
    private const val READ_MS = 8000

    private fun enc(s: String): String =
        UrlCodec.encode(s)

    private fun headers(): Map<String, String> = mapOf(
        "User-Agent" to HttpClient.UA_PC,
        "Connection" to "Keep-Alive",
        "Referer" to "https://www.duboku.tv/",
    )

    /** 生成一次签名参数（同一请求内复用同一时间戳） */
    private fun signParam(): String {
        val nowMs = Clock.nowMs()
        val nowSec = nowMs / 1000
        return JavaRandom.dubokuSignParam(nowSec, nowMs)
    }

    /** 搜索：响应是**裸数组**，`DId`/`TnId` 均为编码路径 */
    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        val url = "${source.api}/vodsearch${signParam()}&wd=${enc(keyword)}"
        val r = HttpClient.get(url, headers(), CONNECT_MS, READ_MS)
        if (r.status != 200) return DubokuParser.failed(source, 1, "HTTP ${r.status}")
        return DubokuParser.parseSearch(source, r.body)
    }

    /** 详情：按数字 id 直接取 `/voddetail/{id}` */
    suspend fun detail(source: CmsSource, vodId: Long): VideoItem? {
        if (vodId <= 0) return null
        val r = HttpClient.get(
            "${source.api}/voddetail/$vodId${signParam()}", headers(), CONNECT_MS, READ_MS,
        )
        if (r.status != 200) return null
        return DubokuParser.parseDetail(source, r.body, vodId)
    }

    /** 播放换链：token 即播放路径（绝对地址或 `/vodplay/...`），响应 `HId` 再解码 */
    suspend fun resolvePlay(
        source: CmsSource,
        vodId: Long,
        token: String,
        from: String,
    ): PlayResolveResult {
        val target = DubokuParser.resolveTarget(source, token)
        if (target.isEmpty()) return PlayResolveResult()
        val r = HttpClient.get("$target${signParam()}", headers(), CONNECT_MS, READ_MS)
        if (r.status != 200) return PlayResolveResult()
        return DubokuParser.parsePlay(r.body)
    }
}
