package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.VideoItem
import com.dengying.player.data.net.HttpClient
import com.dengying.player.platform.Clock

/**
 * 适配器：桔子（橘汁 JuZhi）—— protobuf + AES-256-ECB + AES-128-CBC + RSA-PKCS1
 *
 * 接口形态（AH 接口清单 §1.2）：
 *   POST /api/v5/find/app/zone          一级域名/二级公钥下发（RSARequestProto）
 *   POST /api/proto/v5/drama/search     搜索   {searchKeys, page, pagesize}
 *   POST /api/proto/v5/drama/getDetail  详情   {id}
 *   POST /api/proto/v5/videoUsableUrl   换链   {vodPlayFrom, playUrl}
 *
 * 协议要点（逐条对照逆向规则）：
 *  - 请求头 `publicParams` = `{"paramsData":"<hex>"}`，
 *    `<hex>` = AES-128-CBC/PKCS7(设备参数 JSON, key=iv=`JUZHI_PUBLIC_PARAMS_KEY`) 的密文 hex。
 *    设备参数**键序固定**（服务端可能校验），末尾追加 timestamp / random_str /
 *    sig(RSA(timestamp+random+vApp)) / sig2=aesSign[0:8] / sig3=aesSign[8:]，其中
 *    aesSign = Base64(AES-256-ECB/PKCS7(timestamp+random, key=requestKey2))。
 *  - 二级公钥：`RSARequestProto`（f1 timestamp, f2 RSA(timestamp+random16),
 *    f3 fake1, f4 random16, f5 fake2），响应 `ApiResult`（f1=code，f3=data），
 *    data 内 f2..f5 文本顺序拼接 = 二级公钥 PEM（**跳过 f1**）。
 *  - 业务请求：`SecureRequestProto`（f1/f2 = 8 位随机前缀 + AES-256-ECB Base64 密文
 *    从第 20 字节切开，f3 fake, f4 timestamp, f5 random8）。
 *  - 换链：`ParsePlayUrlBean` f1=播放地址、f6=自定义请求头数组（每项 f1=key, f2=value）、
 *    **f9=失败原因**（换链不成功时 f1 原样回显 token）。
 *
 * 实测修正（2026-10-04，逆向规则与真实响应不符的两处，**均以实测为准**）：
 *  1. 搜索结果行的 `id` 是 **varint**（DramaItem.f3），不是字符串。规则里
 *     `juPbFirst(item, 3)` 当文本读会取到空串 → 详情请求 `id=` 为空 → 服务端
 *     回 `code=100010 参数解析异常`。
 *  2. 换链参数 `vodPlayFrom` 必须传 `Video.f9`（线路机器标识，如 `Ksvideo`、
 *     `bytedance`、`zijianm3u8`），**不能传 f10 的中文线路名**（"蓝光1"）。
 *     传中文名服务端查不到解析配置，回 f9=找不到视频源对应的解析配置 并把
 *     token 原样回显到 f1。
 *
 * 另注：Drama 里 f10 是 wire type 5（fixed32），解析必须跳过而不是中断，
 * 否则 f10 之后的字段（含 repeated Video 分集）全部丢失。[Protobuf.readFields] 已正确跳过。
 *
 * 二级公钥服务端轮换，缓存 6 小时（与规则一致）。
 *
 * 【根域动态下发（AH 接口清单「二、动态下发层」）】业务根域不再写死：每次取根域都
 * 经 [JuzhiRoot.resolve]，由它读服务端下发文件并缓存 15 分钟；下发失败回退
 * `source.api`（写死兜底）。迁移前 `source.api` = `http://103.36.167.27:18004` 已 502，
 * 这就是本改造的原因。
 *
 * 【KMP P3 批 C-7 切分】协议层（设备参数拼串、protobuf 请求/响应、三处解析）已全部
 * 迁至 `shared` 的 [JuzhiParser]，本文件只保留 HTTP 收发与二级公钥缓存这两个
 * 依赖网络/时序的部分。org.json 与 `java.net.URLEncoder` 均已从此文件移除。
 */
object JuzhiAdapter {

    private const val CONNECT_MS = 8000
    private const val READ_MS = 10000
    private const val SECOND_KEY_TTL = 6 * 60 * 60 * 1000L

    private var cachedSecondKey: String = ""
    private var cachedSecondAt: Long = 0
    private var cachedUuid: String = ""

    private fun deviceUuid(): String {
        if (cachedUuid.isNotEmpty()) return cachedUuid
        cachedUuid = JuzhiParser.newUuid()
        return cachedUuid
    }

    /** 构造 v5 请求头 */
    private fun v5Headers(secondLevelPublic: String): Map<String, String> =
        JuzhiParser.v5Headers(
            secondLevelPublic = secondLevelPublic,
            ts = Clock.nowMs(),
            randomStr = JuzhiParser.rnd(16),
            uuid = deviceUuid(),
        )

    /**
     * 取当前业务根域（动态下发 + 写死兜底）。
     *
     * 所有业务请求都从这里出发，不直接用 `source.api`——后者只是兜底值。
     */
    private suspend fun base(source: CmsSource): String = JuzhiRoot.resolve(source.api)

    /** 取二级公钥（缓存 6 小时） */
    private suspend fun secondLevelKey(base: String, force: Boolean): String {
        if (force) {
            cachedSecondKey = ""
            cachedSecondAt = 0
        }
        val now = Clock.nowMs()
        if (cachedSecondKey.isNotEmpty() && (now - cachedSecondAt) < SECOND_KEY_TTL) {
            return cachedSecondKey
        }

        val randomStr = JuzhiParser.rnd(16)
        val body = JuzhiParser.zoneRequestBytes(now, randomStr)

        val headers = v5Headers(com.dengying.player.core.SourceSecrets.JUZHI_PRIMARY_PUBLIC_KEY)
        // 原始 protobuf 字节收发：Base64 文本会被服务端判 400（实测）
        val r = HttpClient.postBinary(
            "$base/api/v5/find/app/zone", body, headers, CONNECT_MS, READ_MS,
        )
        if (r.status != 200) return ""

        val second = JuzhiParser.zoneKeyOf(r.bytes)
        if (second.isNotEmpty()) {
            cachedSecondKey = second
            cachedSecondAt = now
        }
        return second
    }

    /** 业务请求：构造 SecureRequestProto 并解析 ApiResult.data */
    private suspend fun v5Request(
        base: String,
        path: String,
        fields: Map<String, String>,
    ): ByteArray? {
        val second = secondLevelKey(base, false)
        if (second.isEmpty()) return null

        val ts = Clock.nowMs()
        val body = JuzhiParser.requestBodyBytes(fields, ts, JuzhiParser.rnd(8))
        val headers = v5Headers(second)
        val r = HttpClient.postBinary("$base$path", body, headers, CONNECT_MS, READ_MS)

        if (r.status == 200) {
            val data = JuzhiParser.apiDataOf(r.bytes)
            if (data != null) return data
            return null
        }

        // 服务端轮换二级公钥 → 强制刷新后重试一次（与规则一致）
        val refreshed = secondLevelKey(base, true)
        if (refreshed.isEmpty() || refreshed == second) return null
        val r2 = HttpClient.postBinary(
            "$base$path", body, v5Headers(refreshed), CONNECT_MS, READ_MS,
        )
        if (r2.status != 200) return null
        return JuzhiParser.apiDataOf(r2.bytes)
    }

    /** 搜索 */
    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        val data = v5Request(
            base(source), "/api/proto/v5/drama/search",
            mapOf("searchKeys" to keyword, "page" to "1", "pagesize" to "21"),
        )
            ?: return JuzhiParser.failed(source, "桔子请求失败（二级公钥或签名未通过）")

        return JuzhiParser.parseSearch(source, data)
    }

    /** 详情：按线路分组，每集 token = Base64({vodPlayFrom: 机器标识, playUrl: path}) */
    suspend fun detail(source: CmsSource, vodId: Long): VideoItem? {
        val data = v5Request(
            base(source), "/api/proto/v5/drama/getDetail", mapOf("id" to "$vodId"),
        ) ?: return null

        return JuzhiParser.parseDetail(source, data, vodId)
    }

    /** 换链：videoUsableUrl → ParsePlayUrlBean（f1=地址，f6=自定义头） */
    suspend fun resolvePlay(
        source: CmsSource,
        vodId: Long,
        token: String,
        from: String,
    ): PlayResolveResult {
        if (token.isEmpty()) return PlayResolveResult()
        // 已经是媒体地址：直接播
        if (JuzhiParser.isMediaUrl(token)) return PlayResolveResult(url = token, headers = "")

        var playUrl = token
        var from2 = from
        JuzhiParser.playParamsOf(token, from)?.let { p ->
            playUrl = p.playUrl
            from2 = p.vodPlayFrom
        }
        if (JuzhiParser.isMediaUrl(playUrl) && playUrl.startsWith("http")) {
            return PlayResolveResult(url = playUrl, headers = "")
        }

        val data = v5Request(
            base(source), "/api/proto/v5/videoUsableUrl",
            mapOf("vodPlayFrom" to from2, "playUrl" to playUrl),
        ) ?: return PlayResolveResult()

        return JuzhiParser.parsePlay(data)
    }

    /** 自检入口：仅用于本地探测二级公钥是否可下发（不参与聚合链路） */
    suspend fun probeZone(source: CmsSource): Boolean =
        secondLevelKey(base(source), true).isNotEmpty()
}
