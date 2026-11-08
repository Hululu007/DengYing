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
import com.dengying.player.data.source.crypto.Rsa
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 瓜皮影视（WexGuaZi）纯逻辑层 —— 密码学编排 + 响应解析（批 C-6）。
 *
 * ## 为什么这一层特别值钱
 *
 * 瓜皮的鉴链是全网关里唯一一条「AES + RSA + MD5 三级串联」的：
 *
 * ```
 * 业务 JSON 明文
 *   -> AES-128-CBC(key=GUAPI_AES_KEY, iv=GUAPI_AES_IV) -> 大写 HEX = request_key
 * keys = RSA(PKCS1, 请求公钥)("{"iv":..,"key":..}") -> Base64
 * signature = uppercase(MD5("token_id=..,token=..,phone_type=1,request_key=..,
 *                            app_id=1,time=..,keys=.." + secret))
 * 响应: data.keys -> RSA 私钥解出 {key,iv} -> data.response_key(HEX) -> AES-CBC -> 业务 JSON
 * ```
 *
 * 迁移前这些步骤全在 `:app` 里，依赖 `javax.crypto` / `java.security`，**iOS 上根本跑不了**。
 * 而它们其实没有一处真的需要平台 API：`Aes` / `Rsa` / `Digest` 在批 P2 已经搬进
 * `commonMain` 并落成了 `AesCipher` / `RsaCipher` / `Hash` 的 expect/actual。所以这一层
 * 只做**纯编排**，一行平台代码都没有。
 *
 * ## 与 `GuapiAdapter` 的分界
 *
 * 留在 `:app` 的只有：HTTP 往返、`URLEncoder`（批 F 才跨平台化）、`Session` 内存态、
 * 随机设备号（`kotlin.random.Random`，本就是跨平台的，但调用点在 HTTP 层）、
 * 以及把上面这些串起来的控制流。
 *
 * ## 实测状态（2026-10-31，对照 AH 清单第四轮复核）
 *
 * 签名公式已用判别实验锁定（`MD5(core+secret)` 进入业务分支即 code 400/423；
 * `secret+core` 与 HMAC-MD5 均返回 code=300 签名错误）。2026-10-04/10-27 的
 * 「鉴权链不通」是**误测**：请求缺全套协议头（Ver/Version/PackageName/code/lang/
 * Referer/UA），服务端走进异常分支；新设备 signIn 恒 403 本来就是预期行为
 * （signUp 自动注册即可）。对齐 tools/guazi_run.py 信封后全链 T3 实证
 * （signUp → findMoreVod 99 条 → Vurl/show 36 集 → showOne → wmvbo m3u8 200）。
 * 本层真信封金样本（guapi_*2.json）由 tools/dy_capture_1031.py 采集落盘。
 */
object GuapiParser {

    /** 响应里的 `code` 判定成功值（`resp_code` 缺失时也当成功，与 org.json 旧实现一致） */
    const val OK_CODE = 200

    // ---------------- 请求侧：加密编排 ----------------

    /**
     * 业务 JSON → AES-128-CBC/PKCS7 → **大写 HEX**（字段名 `request_key`）。
     *
     * 注意是 HEX 不是 Base64，且**必须大写** —— 签名串里参与 MD5 的就是这串大写文本，
     * 大小写错一位整个请求就 300/400。
     */
    fun requestKey(bizJson: String): String {
        val encBytes = Aes.cbcEncrypt(
            "AES128",
            Digest.utf8Bytes(SourceSecrets.GUAPI_AES_KEY),
            Digest.utf8Bytes(SourceSecrets.GUAPI_AES_IV),
            Digest.utf8Bytes(bizJson),
        )
        return Digest.toHex(encBytes).uppercase()
    }

    /**
     * `keys` 字段：RSA(请求公钥, PKCS1) 加密 `{"iv":..,"key":..}` → Base64。
     *
     * 把 AES 的 key/iv 再用 RSA 包一层，是这类接口防止中间人直接拿到对称密钥的常见做法。
     */
    fun keysField(): String {
        val pem = Rsa.wrapPem(SourceSecrets.GUAPI_REQUEST_PUBLIC_KEY, "PUBLIC KEY")
        val inner = "{\"iv\":\"${SourceSecrets.GUAPI_AES_IV}\",\"key\":\"${SourceSecrets.GUAPI_AES_KEY}\"}"
        return Digest.base64Encode(Rsa.encrypt(pem, Digest.utf8Bytes(inner)))
    }

    /**
     * 签名原文（**字段顺序即协议，不可调换**）。
     *
     * `MD5(core + secret).uppercase()` 才是最终 `signature`；这里单独暴露 core 是
     * 为了能让金样本测试直接钉住串的拼接顺序 —— 顺序错一位，MD5 完全变样，
     * 而失败表现只是「签名错误」一条，极难反查。
     */
    fun signatureCore(
        tokenId: String,
        token: String,
        ts: String,
        requestKey: String,
        keys: String,
    ): String = "token_id=$tokenId,token=$token,phone_type=1," +
        "request_key=$requestKey,app_id=1,time=$ts,keys=$keys"

    /** `signature` = 大写 MD5(签名原文 + 密钥) */
    fun signature(core: String): String =
        Digest.md5Hex(core + SourceSecrets.GUAPI_SIGNATURE_SECRET).uppercase()

    // ---------------- 响应侧：解密 ----------------

    /**
     * 响应解密：`data.keys` → RSA 私钥解出 `{key,iv}`；`data.response_key`(HEX) → AES-CBC → 业务 JSON 文本。
     *
     * 任一环节解不出都返回 `""`（**不抛异常**）—— 与迁移前 `try/catch → ""` 的降级语义一致：
     * 调用方拿到空串就当「无响应或解密失败」处理。
     *
     * @param body 原始响应文本（未解密）
     * @return 解密后的 JSON 文本；失败为 `""`
     */
    fun decryptResponse(body: String): String {
        val resp = JsonX.parseObject(body) ?: return ""
        val d = JsonX.optObject(resp, "data") ?: return ""
        return decryptData(d)
    }

    /**
     * 剥响应信封 → **业务明文 JSON 文本**（2026-10-31 真信封实测，金样本 guapi_*2.json）。
     *
     * 四种形态（与实测脚本 tools/guazi_run.py 的 decode_resp 同口径）：
     *  1. 业务错误（`code` 存在且 != 200，如 signIn 403「用户不存在」）→ `""`——
     *     错误信封即使带密钥段也不值得解，调用方换下一路径；
     *  2. `data` 带 `keys`+`response_key`（加密信封）→ RSA→AES 解出明文；
     *  3. `data` 存在但**无密钥字段**（未加密响应）→ 把 `data` 本身序列化返回；
     *  4. 无 `data` 且顶层直接带业务键（list/vodInfo/url/token）→ 整包原样返回。
     *
     * 任一环节解不出返回 `""`，不抛异常。
     */
    fun unwrapEnvelope(body: String): String {
        val env = JsonX.parseObject(body) ?: return ""
        if (JsonX.has(env, "code") && JsonX.int(env, "code", -1) != OK_CODE) return ""
        val d = JsonX.optObject(env, "data")
        if (d == null) {
            val plain = JsonX.has(env, "list") || JsonX.has(env, "vodInfo") ||
                JsonX.has(env, "url") || JsonX.has(env, "token")
            return if (plain) body else ""
        }
        val keys = JsonX.str(d, "keys", "")
        val responseKey = JsonX.str(d, "response_key", "")
        if (keys.isEmpty() || responseKey.isEmpty()) return d.toString()
        return decryptData(d)
    }

    /** 信封 `data` 对象 → 业务明文；无密钥段或解密失败为 `""` */
    private fun decryptData(d: kotlinx.serialization.json.JsonObject): String {
        val keys = JsonX.str(d, "keys", "")
        val responseKey = JsonX.str(d, "response_key", "")
        if (keys.isEmpty() || responseKey.isEmpty()) return ""

        val keyIvText = tryDecryptKeys(keys)
        if (keyIvText.isEmpty()) return ""
        val parsed = JsonX.parseObject(keyIvText) ?: return ""
        val key = JsonX.str(parsed, "key", "")
        val iv = JsonX.str(parsed, "iv", "")
        if (key.isEmpty() || iv.isEmpty()) return ""

        val cipherBytes = Aes.hexToBytesSafe(responseKey)
        if (cipherBytes.isEmpty()) return ""
        return try {
            Digest.utf8String(
                Aes.cbcDecrypt(
                    "AES128",
                    Digest.utf8Bytes(key),
                    Digest.utf8Bytes(iv),
                    cipherBytes,
                )
            )
        } catch (e: Exception) {
            ""
        }
    }

    /** `data.keys`（Base64 的 RSA 密文）→ 明文 `{"key":..,"iv":..}`；失败为 `""` */
    private fun tryDecryptKeys(keys: String): String {
        val privPem = Rsa.wrapPem(SourceSecrets.GUAPI_RESPONSE_PRIVATE_KEY, "PRIVATE KEY")
        return try {
            Digest.utf8String(Rsa.decrypt(privPem, Digest.base64DecodeBytes(keys)))
        } catch (e: Exception) {
            ""
        }
    }

    // ---------------- 换链：param 展开 ----------------

    /**
     * 线路 `param` 串（`vod_d_id=..&vurl_id=..&…`）→ 平铺 JSON 对象文本。
     *
     * showOne 的业务体就是把 param 的 k=v 对**原样展开**成 JSON（值全为字符串），
     * 不是 `{"param": …}` 包一层——2026-10-31 真样本实证（金样本 guapi_param2/showone_env2）。
     * 没有 `=` 的段丢弃（与实测脚本一致）。
     */
    fun expandParam(param: String): String {
        val pairs = ArrayList<Pair<String, String>>()
        for (kv in param.split('&')) {
            if (kv.isEmpty()) continue
            val i = kv.indexOf('=')
            if (i <= 0) continue
            pairs += kv.substring(0, i) to kv.substring(i + 1)
        }
        return JsonX.buildObject(*pairs.toTypedArray()).toString()
    }

    // ---------------- 响应侧：解析 ----------------

    fun failed(
        source: CmsSource,
        err: String,
        outcome: RequestOutcome = RequestOutcome.RequestFailed,
    ) = FetchResult(outcome = outcome, page = 1, sourceName = source.name, err = err)

    /**
     * 解析搜索响应（**已解密**的业务 JSON）。
     *
     * `vod_id` 是全网关里唯一的三态字段：可能是数字、可能是数字字符串、可能是 `null`。
     * 旧实现用 `when (v) { null, JSONObject.NULL -> 0; is Number -> toLong(); else -> toString().toLongOrNull() ?: 0 }`，
     * 这里用 [idOf] 逐字复刻 —— 尤其「非数字字符串 → 0 → 该行被跳过」这层降级不能丢。
     */
    fun parseSearch(source: CmsSource, text: String): FetchResult {
        if (text.isEmpty()) return failed(source, "无响应或解密失败")
        val data = JsonX.parseObject(text)
            ?: return failed(source, "响应结构非预期", RequestOutcome.ParseFailed)

        val rows = JsonX.optArray(data, "list")
        val items = ArrayList<VideoItem>()
        if (rows != null) {
            for (i in 0 until JsonX.len(rows)) {
                val row = JsonX.optObjectAt(rows, i) ?: continue
                val id = idOf(row["vod_id"])
                if (id <= 0) continue
                items += VideoItem(
                    vod_id = id,
                    vod_name = JsonX.str(row, "vod_name", ""),
                    vod_pic = JsonX.str(row, "vod_pic", ""),
                    vod_remarks = JsonX.str(row, "vod_remarks", ""),
                    _source = source.key,
                )
            }
        }
        val outcome =
            if (items.isNotEmpty()) RequestOutcome.SuccessWithData else RequestOutcome.SuccessNoData
        return FetchResult(
            outcome = outcome,
            items = items,
            page = 1,
            pageCount = 1,
            total = JsonX.int(data, "total", items.size),
            sourceName = source.name,
        )
    }

    /** `vod_id` 三态取值：`null`/缺失 → 0；数字 → `toLong()`；其它 → `toString().toLongOrNull() ?: 0` */
    fun idOf(e: JsonElement?): Long {
        if (e == null || JsonX.isNull(e)) return 0L
        val p = e as? JsonPrimitive
        if (p != null) {
            if (p.isString) return p.content.toLongOrNull() ?: 0L
            return p.content.toDoubleOrNull()?.toLong() ?: 0L
        }
        return JsonX.textOf(e, "").toLongOrNull() ?: 0L
    }

    /** 解析详情响应（**已解密**的业务 JSON）；失败为 `null` */
    fun parseDetail(source: CmsSource, text: String, vodId: Long): VideoItem? {
        if (text.isEmpty()) return null
        val parsed = JsonX.parseObject(text) ?: return null
        // 2026-10-31 真样本：playInfo 的字段在 `vodInfo` 子对象里（顶层是 comment_auth
        // 等壳字段）；旧合成样本是顶层平铺——vodInfo 缺失时回落顶层，两种都兼容。
        val vi = JsonX.optObject(parsed, "vodInfo") ?: parsed
        return VideoItem(
            vod_id = vodId,
            vod_name = JsonX.str(vi, "vod_name", "").ifEmpty { JsonX.str(vi, "vod_title", "") },
            vod_pic = JsonX.str(vi, "vod_pic", ""),
            vod_content = stripHtml(
                JsonX.str(vi, "vod_content", "").ifEmpty { JsonX.str(vi, "vod_use_content", "") }
            ),
            _source = source.key,
        )
    }

    /** `show_type == "2"` 是不可播条目（试看/锁定，param 恒空）——2026-10-31 真样本实证 */
    const val SHOW_TYPE_LOCKED = "2"

    /**
     * Vurl/show 明文响应 → 详情条目（线路 = 清晰度 src 键，剧集地址 = param 令牌）。
     *
     * 实测形状（金样本 guapi_vurl_plain2）：`{"listCount":36,"list":[{"title":"01",
     * "play":{"480":{"param":"","show_type":2},"720":{"param":"vod_d_id=..&.."},"1080":{…}}}]}`。
     * `show_type=="2"` 与 param 为空的 src 跳过；线路名按**首次出现序**，每条线只收自己的集，
     * 集序保持接口原序（01、02、…，无「逆转选集」语义）。
     *
     * 一条可用线路都没有时返回 `null`（调用方回落到 playInfo 的裸条目）。
     */
    fun parseVurl(
        source: CmsSource,
        text: String,
        vodId: Long,
        name: String,
        pic: String,
    ): VideoItem? {
        if (text.isEmpty()) return null
        val parsed = JsonX.parseObject(text) ?: return null
        val rows = JsonX.optArray(parsed, "list") ?: return null

        val lineNames = ArrayList<String>()
        val lineEps = LinkedHashMap<String, MutableList<String>>()
        for (i in 0 until JsonX.len(rows)) {
            val row = JsonX.optObjectAt(rows, i) ?: continue
            val title = JsonX.str(row, "title", "").ifEmpty { "第${i + 1}集" }
            val play = JsonX.optObject(row, "play") ?: continue
            for (k in play.keys) {
                val sd = JsonX.optObject(play, k) ?: continue
                if (JsonX.str(sd, "show_type", "") == SHOW_TYPE_LOCKED) continue
                val param = JsonX.str(sd, "param", "")
                if (param.isEmpty()) continue
                if (!lineEps.containsKey(k)) {
                    lineNames += k
                    lineEps[k] = ArrayList()
                }
                lineEps[k]!!.add("$title\$$param")
            }
        }
        if (lineNames.isEmpty()) return null
        return VideoItem(
            vod_id = vodId,
            vod_name = name,
            vod_pic = pic,
            vod_play_from = lineNames.joinToString("$$$"),
            vod_play_url = lineNames.map { (lineEps[it] ?: emptyList()).joinToString("#") }
                .joinToString("$$$"),
            _source = source.key,
        )
    }

    /** 解析换链响应（**已解密**的业务 JSON）：`{"url":..}`；失败为空结果 */
    fun parsePlay(text: String): PlayResolveResult {
        if (text.isEmpty()) return PlayResolveResult()
        val parsed = JsonX.parseObject(text) ?: return PlayResolveResult()
        return PlayResolveResult(url = JsonX.str(parsed, "url", ""), headers = "")
    }

    /** 简介去 HTML（与文才同形态：接口返回带标签的正文） */
    fun stripHtml(value: String): String = value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
