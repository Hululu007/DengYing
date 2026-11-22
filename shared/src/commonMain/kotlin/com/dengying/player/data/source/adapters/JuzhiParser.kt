package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.SourceSecrets
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.source.crypto.Aes
import com.dengying.player.data.source.crypto.Digest
import com.dengying.player.data.source.crypto.Protobuf
import com.dengying.player.data.source.crypto.Rsa
import kotlin.random.Random

/**
 * 桔子（橘汁）协议层 —— protobuf + AES + RSA 的全部纯逻辑。
 *
 * 与 [JuzhiAdapter] 的分界：本对象**不发起任何网络请求**，只负责
 *
 *  - 设备参数 JSON 与 `publicParams` 请求头的构造
 *  - 一级公钥请求体（`RSARequestProto`）与二级公钥响应的解析
 *  - 业务请求体（`SecureRequestProto`）与 `ApiResult.data` 的解析
 *  - 搜索 / 详情 / 换链三个响应的字段提取
 *
 * 全部依赖（[Aes]、[Rsa]、[Digest]、[Protobuf]、[JsonX]）已在 KMP P2/P3 前批迁入
 * `commonMain`，因此这一层在 iOS 上可直接复用 —— 这正是本轮迁移价值最高的部分：
 * 原实现依赖 `javax.crypto.Cipher` 与 `java.security.KeyFactory`，在 Kotlin/Native 上
 * 完全不存在。
 *
 * 协议要点见 [JuzhiAdapter] 的类注释（键序、f9 机器标识、wire type 5 跳过等），
 * 此处不重复。
 */
object JuzhiParser {

    private const val ALNUM =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** 设备参数里的 `appName`（`URLEncoder.encode("橘汁","UTF-8")` 的固定结果）。 */
    private const val APP_NAME_ENCODED = "%E6%A9%98%E6%B1%81"

    /** `vApp` 常量，参与 RSA 签名。 */
    const val V_APP = "3023"

    /** 随机字母数字串（长度 n）。原实现用 `kotlin.random.Random`，跨平台可用。 */
    fun rnd(n: Int): String {
        val sb = StringBuilder(n)
        repeat(n) { sb.append(ALNUM[Random.nextInt(ALNUM.length)]) }
        return sb.toString()
    }

    /** 32 位小写十六进制 uuid（原实现同样用 Random，未用 `java.util.UUID`）。 */
    fun newUuid(): String {
        val hex = "0123456789abcdef"
        val sb = StringBuilder(32)
        repeat(32) { sb.append(hex[Random.nextInt(16)]) }
        return sb.toString()
    }

    /**
     * 设备参数 JSON —— **键序必须与规则一致**，服务端按序签验。
     *
     * 手写拼串而非 JSON 构造器：JSON 对象内部若是 HashMap，键序不保证，一旦顺序变了
     * 服务端验签就失败，且错误信息只有一句「签名错误」。原实现即为此写法，此处原样保留。
     *
     * @param uuid 设备 uuid（`uuid` / `udid` / `androidID` 三个字段共用同一个值）
     */
    fun deviceParamsJson(
        ts: Long,
        randomStr: String,
        aesSign: String,
        sig: String,
        vApp: String,
        uuid: String,
    ): String = buildString(1400) {
        append("{\"country\":\"CN\"")
        append(",\"vName\":\"3.0.2.3\"")
        append(",\"cpuId\":\"MT6893Z%2FCZA\"")
        append(",\"young\":0")
        append(",\"facturer\":\"Xiaomi\"")
        append(",\"pkg\":\"com.mxj.wylcjbxyx\"")
        append(",\"uuid\":\"").append(uuid).append('"')
        append(",\"resolution\":\"1080x2272\"")
        append(",\"mac\":\"02%3A00%3A00%3A00%3A00\"")
        append(",\"abid\":\"397\"")
        append(",\"model\":\"M2102J20SG\"")
        append(",\"plat\":\"android\"")
        append(",\"udid\":\"").append(uuid).append('"')
        append(",\"dpi\":\"440\"")
        append(",\"net\":\"1\"")
        append(",\"lang\":\"zh\"")
        append(",\"brand\":\"Xiaomi\"")
        append(",\"density\":\"2.75\"")
        append(",\"appName\":\"").append(APP_NAME_ENCODED).append('"')
        append(",\"cpu\":\"arm64-v8a\"")
        append(",\"chid\":\"10000\"")
        append(",\"carrier\":\"%E8%81%94%E9%80%9A\"")
        append(",\"_vOsCode\":35")
        append(",\"vOs\":\"14\"")
        append(",\"v\":1")
        append(",\"tenantId\":\"\"")
        append(",\"vApp\":\"").append(vApp).append('"')
        append(",\"device\":0")
        append(",\"androidID\":\"").append(uuid).append('"')
        append(",\"timestamp\":").append(ts)
        append(",\"random_str\":\"").append(randomStr).append('"')
        append(",\"sig\":\"").append(sig).append('"')
        append(",\"sig2\":\"").append(aesSign.substring(0, 8)).append('"')
        append(",\"sig3\":\"").append(aesSign.substring(8)).append('"')
        append('}')
    }

    /**
     * 构造 v5 请求头。
     *
     * `aesSign` = Base64(AES-256-ECB/PKCS7(`"$ts$randomStr"`, key=[SourceSecrets.JUZHI_KEY_2]))
     * `sig`     = Base64(RSA(`"$ts$randomStr$vApp"`, 二级公钥))
     * `paramsData` = hex(AES-128-CBC(`deviceParamsJson`, key=iv=[SourceSecrets.JUZHI_PUBLIC_PARAMS_KEY]))
     *
     * 原实现用 `JSONObject().put("paramsData", ...).toString()` 拼头；这里改用 [JsonX.buildObject]，
     * 语义等价（下游按 key 取，不依赖顺序）。
     */
    fun v5Headers(
        secondLevelPublic: String,
        ts: Long,
        randomStr: String,
        uuid: String,
    ): Map<String, String> {
        val vApp = V_APP

        val aesSign = Digest.base64Encode(
            Aes.ecbEncrypt(
                "AES256",
                Digest.utf8Bytes(SourceSecrets.JUZHI_KEY_2),
                Digest.utf8Bytes("$ts$randomStr"),
            )
        )
        val pem = if (secondLevelPublic.startsWith("-----BEGIN")) {
            secondLevelPublic
        } else {
            Rsa.wrapPem(secondLevelPublic, "PUBLIC KEY")
        }
        val sig = Digest.base64Encode(
            Rsa.encrypt(pem, Digest.utf8Bytes("$ts$randomStr$vApp"))
        )
        val paramsJson = deviceParamsJson(ts, randomStr, aesSign, sig, vApp, uuid)
        val cbc = Aes.cbcEncrypt(
            "AES128",
            Digest.utf8Bytes(SourceSecrets.JUZHI_PUBLIC_PARAMS_KEY),
            Digest.utf8Bytes(SourceSecrets.JUZHI_PUBLIC_PARAMS_KEY),
            Digest.utf8Bytes(paramsJson),
        )
        return mapOf(
            "User-Agent" to SourceSecrets.JUZHI_UA,
            "Accept" to "application/x-protobuf",
            "Content-Type" to "application/x-protobuf",
            "publicParams" to JsonX.buildObject("paramsData" to Digest.toHex(cbc)).toString(),
        )
    }

    /**
     * 一级公钥请求体（`RSARequestProto`）。
     *
     * f1 = timestamp、f2 = Base64(RSA(`"$ts$randomStr"`, 一级公钥)) 的**文本** UTF-8 字节、
     * f3/f5 = 随机 16、f4 = randomStr。
     */
    fun zoneRequestBytes(ts: Long, randomStr: String): ByteArray {
        val w = Protobuf.Writer()
        w.varintField(1, ts)
        val pem = Rsa.wrapPem(SourceSecrets.JUZHI_PRIMARY_PUBLIC_KEY, "PUBLIC KEY")
        val sign = Rsa.encrypt(pem, Digest.utf8Bytes("$ts$randomStr"))
        // Rsa.encrypt 返回原始签名字节；这里放的是其 Base64 **文本**的 UTF-8 字节，
        // 不是原始字节 —— 服务端按字符串解
        w.bytesField(2, Digest.utf8Bytes(Digest.base64Encode(sign)))
        w.bytesField(3, Digest.utf8Bytes(rnd(16)))
        w.bytesField(4, Digest.utf8Bytes(randomStr))
        w.bytesField(5, Digest.utf8Bytes(rnd(16)))
        return w.toBytes()
    }

    /**
     * 解析 zone 响应，取出二级公钥。
     *
     * `ApiResult` 的 f1 = code（必须 200）、f3 = data；data 内 f2..f5 文本顺序拼接
     * 即二级公钥 PEM body（**跳过 f1**）。失败返回空串（不抛）。
     */
    fun zoneKeyOf(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val result = Protobuf.readFields(bytes)
        if (Protobuf.varintOf(result, 1) != 200L) return ""
        val parts = Protobuf.readFields(Protobuf.bytesOf(result, 3))
        val sb = StringBuilder()
        for (i in 2..5) {
            sb.append(Digest.utf8String(Protobuf.bytesOf(parts, i)))
        }
        return sb.toString()
    }

    /**
     * 业务请求体（`SecureRequestProto`）。
     *
     * `pairs` = 非空字段的 `k=v` 以 `&` 连接；明文 = `pairs + ts`；
     * 密文 = Base64(AES-256-ECB(明文, key=[SourceSecrets.JUZHI_KEY_1]))；
     * `mixed` = random8 + 密文，f1 = 前 20 字符、f2 = 剩余。
     */
    fun requestBodyBytes(fields: Map<String, String>, ts: Long, random8: String): ByteArray {
        val pairs = fields.entries
            .filter { it.value.isNotEmpty() }
            .joinToString("&") { "${it.key}=${it.value}" }

        val encRaw = Aes.ecbEncrypt(
            "AES256",
            Digest.utf8Bytes(SourceSecrets.JUZHI_KEY_1),
            Digest.utf8Bytes(pairs + ts),
        )
        val encB64 = Digest.base64Encode(encRaw)
        val mixed = random8 + encB64

        val w = Protobuf.Writer()
        w.bytesField(1, Digest.utf8Bytes(mixed.substring(0, minOf(20, mixed.length))))
        w.bytesField(2, Digest.utf8Bytes(if (mixed.length > 20) mixed.substring(20) else ""))
        w.bytesField(3, Digest.utf8Bytes(rnd(20)))
        w.varintField(4, ts)
        w.bytesField(5, Digest.utf8Bytes(random8))
        return w.toBytes()
    }

    /**
     * 解析业务响应 `ApiResult`：f1 = code，f3 = data。
     *
     * code != 200 返回 null（调用方据此判断「二级公钥需轮换重试」）。
     */
    fun apiDataOf(bytes: ByteArray): ByteArray? {
        if (bytes.isEmpty()) return null
        val parsed = Protobuf.readFields(bytes)
        if (Protobuf.varintOf(parsed, 1) != 200L) return null
        return Protobuf.bytesOf(parsed, 3)
    }

    fun failed(
        source: CmsSource,
        err: String,
        outcome: RequestOutcome = RequestOutcome.RequestFailed,
    ): FetchResult = FetchResult(outcome = outcome, page = 1, sourceName = source.name, err = err)

    /** 剧集 id：真实响应里 DramaItem.f3 是 varint（规则误记为字符串），f3 取不到再按文本兜底。 */
    fun idOf(fields: List<Protobuf.Field>): Long {
        val n = Protobuf.varintOf(fields, 3)
        if (n > 0) return n
        val t = Protobuf.textOf(fields, 3)
        return t.trim().toLongOrNull() ?: 0L
    }

    /** 是否直接是媒体地址（决定详情里存原地址还是存 Base64 token）。 */
    fun isMediaUrl(v: String): Boolean =
        Regex("\\.(?:mp4|m3u8|flv|mkv|avi|ts|mov|mpd|m4a|wmv)(?:[?#].*)?$", RegexOption.IGNORE_CASE)
            .containsMatchIn(v)

    /** 按图片对象取封面：f1 优先，f2 兜底。 */
    fun picOf(coverBytes: ByteArray): String {
        if (coverBytes.isEmpty()) return ""
        val cover = Protobuf.readFields(coverBytes)
        return Protobuf.textOf(cover, 1).ifEmpty { Protobuf.textOf(cover, 2) }
    }

    /**
     * 解析搜索响应（`DramaSearchPage`）。
     *
     * f1 = repeated Drama；Drama 的 f5 = 名称、f3 = id（varint）、f2 = 图片对象、
     * f13 = 备注文本、f14 = varint（-1 表示无）。
     */
    fun parseSearch(source: CmsSource, data: ByteArray): FetchResult {
        if (data.isEmpty()) return failed(source, "响应为空")
        val page = Protobuf.readFields(data)
        val items = ArrayList<VideoItem>()
        for (f in page) {
            if (f.fieldNo != 1 || f.wireType != 2) continue
            val it = Protobuf.readFields(f.bytes)
            val name = Protobuf.textOf(it, 5)
            val id = idOf(it)
            if (id <= 0 || name.isEmpty()) continue
            val pic = picOf(Protobuf.bytesOf(it, 2))
            items += VideoItem(
                vod_id = id,
                vod_name = name,
                vod_pic = pic,
                vod_remarks = "${Protobuf.textOf(it, 13)} ${Protobuf.varintOf(it, 14)}"
                    .replace("-1", "").trim(),
                _source = source.key,
            )
        }
        val outcome =
            if (items.isNotEmpty()) RequestOutcome.SuccessWithData else RequestOutcome.SuccessNoData
        return FetchResult(
            outcome = outcome, items = items, page = 1, pageCount = 1,
            total = items.size, sourceName = source.name,
        )
    }

    /**
     * 解析详情响应（`Drama`）。
     *
     * f9 = 名称、f2 = 图片、f25 = 演员、f26 = 备注、f18 = 年份（varint）、
     * f7/f6 = 简介（前者优先）、f29 = repeated Video 分集。
     *
     * 线路分组键用 `Video.f9`（机器标识如 Ksvideo / bytedance）而非 f10 的中文名 ——
     * **换链接口只认 f9**。展示名用 f10，缺失时回退 f9。
     *
     * 每集 token：直接是媒体地址就存原地址，否则存
     * Base64(`{"vodPlayFrom":f9,"playUrl":f4}`)，播放时走 videoUsableUrl。
     * token 只在本地解码（不参与服务端验签），故 JSON 键序无语义。
     */
    fun parseDetail(source: CmsSource, data: ByteArray, vodId: Long): VideoItem? {
        if (data.isEmpty()) return null
        val d = Protobuf.readFields(data)
        val name = Protobuf.textOf(d, 9)
        if (name.isEmpty()) return null
        val pic = picOf(Protobuf.bytesOf(d, 2))

        val lineNames = ArrayList<String>()
        val lineEps = ArrayList<MutableList<String>>()
        val lineIndex = HashMap<String, Int>()

        for (f in d) {
            if (f.fieldNo != 29 || f.wireType != 2) continue
            val v = Protobuf.readFields(f.bytes)
            val path = Protobuf.textOf(v, 4)
            if (path.isEmpty()) continue
            val srcId = Protobuf.textOf(v, 9)
            val srcCn = Protobuf.textOf(v, 10)
            val key = srcId.ifEmpty { srcCn }
            if (key.isEmpty()) continue

            var idx = lineIndex[key] ?: -1
            if (idx < 0) {
                idx = lineNames.size
                lineIndex[key] = idx
                lineNames += srcCn.ifEmpty { srcId }
                lineEps.add(ArrayList())
            }
            val title = Protobuf.textOf(v, 2).ifEmpty { Protobuf.textOf(v, 3) }
            val label = title.ifEmpty { "第${lineEps[idx].size + 1}集" }
            val token = if (isMediaUrl(path)) {
                path
            } else {
                Digest.base64Encode(
                    Digest.utf8Bytes(
                        JsonX.buildObject("vodPlayFrom" to key, "playUrl" to path).toString()
                    )
                )
            }
            lineEps[idx].add("$label\$$token")
        }

        val year = Protobuf.varintOf(d, 18)
        return VideoItem(
            vod_id = vodId,
            vod_name = name,
            vod_pic = pic,
            vod_actor = Protobuf.textOf(d, 25),
            vod_remarks = Protobuf.textOf(d, 26),
            vod_year = if (year > 0) "$year" else "",
            vod_content = Protobuf.textOf(d, 7).ifEmpty { Protobuf.textOf(d, 6) },
            vod_play_from = if (lineNames.isNotEmpty()) lineNames.joinToString("$$$") else "",
            vod_play_url = lineEps.joinToString("$$$") { it.joinToString("#") },
            _source = source.key,
        )
    }

    /** 换链 token 解码结果（[playUrl] / [vodPlayFrom] 均已套用调用方给的兜底值）。 */
    data class PlayParams(val playUrl: String, val vodPlayFrom: String)

    /**
     * 解码换链 token 里的 `playUrl` / `vodPlayFrom`。
     *
     * 兜底值与原实现一致：`optString("playUrl", token)`、`optString("vodPlayFrom", from)` ——
     * 即键缺失时分别回落到入参 token 与调用方传入的线路标识。
     *
     * token 不是 Base64 JSON 时返回 `null`，调用方按原文处理。
     */
    fun playParamsOf(token: String, from: String): PlayParams? {
        val raw = runCatching { Digest.base64DecodeString(token) }.getOrNull() ?: return null
        val o = JsonX.parseObject(raw) ?: return null
        return PlayParams(
            playUrl = JsonX.str(o, "playUrl", token),
            vodPlayFrom = JsonX.str(o, "vodPlayFrom", from),
        )
    }

    /**
     * 解析换链响应（`ParsePlayUrlBean`）。
     *
     * f1 = 播放地址、f6 = repeated 自定义头（每项 f1 = key、f2 = value）、
     * **f9 = 失败原因**。f9 非空表示换链失败，此时服务端把 f1 原样回显成入参 token ——
     * 绝不能把它当播放地址交出去，否则播放器会去拉一个 `Ksvideo-…` 之类的无效串。
     */
    fun parsePlay(data: ByteArray): PlayResolveResult {
        if (data.isEmpty()) return PlayResolveResult()
        val bean = Protobuf.readFields(data)
        val reason = Protobuf.textOf(bean, 9)
        if (reason.isNotEmpty()) return PlayResolveResult()

        val url = Protobuf.textOf(bean, 1)
        if (!url.startsWith("http")) return PlayResolveResult()

        val pairs = ArrayList<Pair<String, Any?>>()
        for (f in bean) {
            if (f.fieldNo != 6 || f.wireType != 2) continue
            val entry = Protobuf.readFields(f.bytes)
            val k = Protobuf.textOf(entry, 1)
            val v = Protobuf.textOf(entry, 2)
            if (k.isNotEmpty()) pairs += k to v
        }
        // 原实现用 `JSONObject().toString()`，无自定义头时是 "{}" 而不是 "" —— 保持等价。
        // 下游 `PlayerFactory.parseHeaders` 对两者都返回空表，但字节形态不该无谓漂移。
        val headers = JsonX.buildObject(*pairs.toTypedArray()).toString()
        return PlayResolveResult(url = url, headers = headers)
    }
}
