package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.SourceSecrets
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.net.HttpClient
import com.dengying.player.platform.Clock
import kotlinx.serialization.json.JsonPrimitive
import com.dengying.player.platform.UrlCodec
import kotlin.random.Random

/**
 * 适配器：瓜皮影视（瓜子 WexGuaZi）—— HTTP 外壳（批 C-6；2026-10-31 对齐实测信封）
 *
 * 密码学编排与响应解析在 `GuapiParser`（`commonMain`，跨平台可用）；
 * 这里只剩「发请求 / 管 session / 生成设备号」这些必须碰平台的部分。
 *
 * 接口形态（AH 接口清单 §1.3）：
 *   POST /App/Authentication/Device/signIn        设备登录（新设备恒 403「用户不存在」）
 *   POST /App/Authentication/Device/signUp        设备注册（new_key=随机 40 位大写 hex，无限可注册）
 *   POST /App/Index/findMoreVod                   搜索 {keywords, ns:"", nt, order_val:"1"}
 *   POST /App/IndexPlay/playInfo                  影片信息（vodInfo 子对象）
 *   POST /App/Resource/Vurl/show                  分集列表 {vurl_cloud_id:"2", vod_d_id}
 *   POST /App/Resource/VurlDetail/showOne         播放换链（业务体 = param 串平铺展开）
 *
 * ## 2026-10-31 与实测脚本（tools/guazi_run.py，T3-PLAYABLE）逐项对齐后的关键结论
 *
 *  1. **请求头全集**：Ver/api-ver/Version/PackageName/code/lang/Referer/UA(Lavf) 一个都不能少——
 *     之前只发 Content-Type+deviceId，服务端走进异常分支，是「signIn 恒 400」的真正根因之一；
 *  2. **form/sign 里的 `token_id` 恒为空串**；app_user_id 只进业务 JSON（playInfo 的 token_id）；
 *  3. 响应信封四态（业务错误 / 加密 / 未加密 data / 顶层裸负载）由 [GuapiParser.unwrapEnvelope] 统一剥；
 *  4. 分集在 `Vurl/show`（此前整段缺失），换链体是 param 展开（此前错包成 `{"param":…}`）。
 */
object GuapiAdapter {

    private const val CONNECT_MS = 6000
    private const val READ_MS = 8000

    /** 设备身份（内存态；登录成功后可由上层落盘复用） */
    data class Session(
        val deviceId: String,
        val deviceKey: String,
        val token: String,
        /** app_user_id：只进业务 JSON（playInfo），**不进 form/sign**（实测 form 里恒空串） */
        val tokenId: String,
    )

    private var session: Session? = null

    /** 已验证可用的根域（5 个 base 历史上 DNS 反复横跳，首个成功者钉住，失败按序回退） */
    private var pinnedBase: String = ""

    private fun enc(s: String): String = UrlCodec.encodeForm(s)

    private fun randomHex(len: Int): String {
        val sb = StringBuilder(len)
        val chars = "0123456789ABCDEF"
        repeat(len) { sb.append(chars[Random.nextInt(16)]) }
        return sb.toString()
    }

    /** 生成设备号（固定前缀 + 4 位补零随机） */
    private fun newDeviceId(): String {
        val tail = Random.nextInt(10000)
        return SourceSecrets.GUAPI_DEVICE_PREFIX + tail.toString().padStart(4, '0')
    }

    /** 协议头全集（2026-10-31 真信封实测；缺任一项服务端都走异常分支） */
    private fun headers(base: String, deviceId: String): Map<String, String> = mapOf(
        "Content-Type" to "application/x-www-form-urlencoded",
        "User-Agent" to SourceSecrets.GUAPI_UA,
        "Ver" to SourceSecrets.GUAPI_VERSION_NAME,
        "api-ver" to SourceSecrets.GUAPI_VERSION_NAME,
        "Version" to SourceSecrets.GUAPI_VERSION_CODE,
        "PackageName" to SourceSecrets.GUAPI_PACKAGE,
        "code" to SourceSecrets.GUAPI_CHANNEL,
        "deviceId" to deviceId,
        "lang" to "zh_cn",
        "Cache-Control" to "no-cache",
        "Referer" to base,
    )

    /**组装签名表单（**字段顺序即协议，不可调换**；`token_id` 恒空串） */
    private fun buildForm(bizJson: String, sess: Session): String {
        val ts = (Clock.nowMs() / 1000).toString()
        val requestKey = GuapiParser.requestKey(bizJson)
        val keys = GuapiParser.keysField()
        val core = GuapiParser.signatureCore("", sess.token, ts, requestKey, keys)
        val signature = GuapiParser.signature(core)

        return listOf(
            "token_id=",
            "token=${enc(sess.token)}",
            "phone_type=1",
            "request_key=${enc(requestKey)}",
            "app_id=1",
            "time=$ts",
            "keys=${enc(keys)}",
            "signature=$signature",
            "phone_model=${enc(SourceSecrets.GUAPI_PHONE_MODEL)}",
            "ad_version=1",
        ).joinToString("&")
    }

    /**
     * 发一次 POST（签名表单 + 协议头全集），按钉住根域优先、失败按序回退；
     * 返回**原始信封**文本（信封可解析即视为该根域可用并钉住），全败返回空串。
     */
    private suspend fun post(source: CmsSource, path: String, bizJson: String, sess: Session): String {
        val bases = buildList {
            if (pinnedBase.isNotEmpty()) add(pinnedBase)
            add(source.api)
            addAll(source.mirrors)
        }.distinct()
        for (b in bases) {
            val r = HttpClient.post(
                "$b$path", buildForm(bizJson, sess), headers(b, sess.deviceId), CONNECT_MS, READ_MS,
            )
            if (r.status != 200 || r.body.isEmpty()) continue
            if (JsonX.parseObject(r.body) != null) {
                pinnedBase = b
                return r.body
            }
        }
        return ""
    }

    /** 请求并剥信封；业务码错误 / 解密失败 / 全根域失败都返回空串 */
    private suspend fun callRaw(
        source: CmsSource,
        path: String,
        bizJson: String,
        sess: Session,
    ): String {
        val body = post(source, path, bizJson, sess)
        if (body.isEmpty()) return ""
        return GuapiParser.unwrapEnvelope(body)
    }

    /**
     * 登录（或注册）取 token；失败返回 null。
     *
     * 先 signIn 后 signUp：**新设备 signIn 恒 403「用户不存在，请以设备号注册」**
     *（真信封金样本 guapi_signin_env2），signUp 自动注册即可拿 token——
     * `new_key` 用随机 40 位大写 hex，无限可注册（2026-10-31 实测）。
     */
    suspend fun login(source: CmsSource): Session? {
        session?.let { if (it.token.isNotEmpty()) return it }

        val sess = Session(
            deviceId = newDeviceId(),
            deviceKey = randomHex(40),
            token = "",
            tokenId = "",
        )
        val signInBody = JsonX.buildObject(
            "new_key" to sess.deviceKey,
            "old_key" to SourceSecrets.GUAPI_OLD_KEY,
        ).toString()
        var text = callRaw(source, "/App/Authentication/Device/signIn", signInBody, sess)
        if (text.isEmpty()) {
            val signUpBody = JsonX.buildObject(
                "new_key" to sess.deviceKey,
                "old_key" to SourceSecrets.GUAPI_OLD_KEY,
                "phone_type" to 1,
                "code" to "",
            ).toString()
            text = callRaw(source, "/App/Authentication/Device/signUp", signUpBody, sess)
        }
        if (text.isEmpty()) return null

        val parsed = JsonX.parseObject(text) ?: return null
        val token = JsonX.str(parsed, "token", "")
        if (token.isEmpty()) return null
        val out = Session(sess.deviceId, sess.deviceKey, token, appUserIdOf(parsed["app_user_id"]))
        session = out
        return out
    }

    /**
     * `app_user_id` 三态：`JSONObject.NULL`/缺失 → `""`；数字 → `toLong().toString()`；其它 → `toString()`。
     *
     * 旧实现走 `opt()` + `is Number` 分支，故这里用 `JsonPrimitive.isString` 区分
     * 「JSON 字符串」与「数字」—— 直接把 `"123"` 当字符串原样返回，与旧实现一致。
     */
    private fun appUserIdOf(e: kotlinx.serialization.json.JsonElement?): String {
        if (e == null || JsonX.isNull(e)) return ""
        val p = e as? JsonPrimitive ?: return JsonX.textOf(e, "")
        if (p.isString) return p.content
        return p.content.toDoubleOrNull()?.toLong()?.toString() ?: p.content
    }

    /** 搜索 */
    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        val sess = login(source)
            ?: return GuapiParser.failed(source, "设备登录未通过（signIn/signUp 均失败，见注释）")

        val biz = JsonX.buildObject(
            "keywords" to keyword,
            "ns" to "",
            "nt" to (Clock.nowMs() / 1000).toString(),
            "order_val" to "1",
        ).toString()

        val text = callRaw(source, "/App/Index/findMoreVod", biz, sess)
        return GuapiParser.parseSearch(source, text)
    }

    /**
     * 详情 = playInfo（vodInfo 子对象：名称/海报/简介）+ Vurl/show（分集与线路令牌）。
     *
     * **分集链此前整段缺失**。Vurl/show 失败时回落裸 playInfo 条目（详情面板仍可显示，
     * 只是无线路可播），不整体失败。
     */
    suspend fun detail(source: CmsSource, vodId: Long): VideoItem? {
        val sess = login(source) ?: return null
        val infoBiz = JsonX.buildObject(
            "token_id" to sess.tokenId,
            "vod_id" to vodId.toString(),
            "mobile_time" to (Clock.nowMs() / 1000).toString(),
            "token" to sess.token,
        ).toString()

        val info = GuapiParser.parseDetail(
            source, callRaw(source, "/App/IndexPlay/playInfo", infoBiz, sess), vodId,
        ) ?: return null

        val vurlBiz = JsonX.buildObject(
            "vurl_cloud_id" to "2",
            "vod_d_id" to vodId.toString(),
        ).toString()
        val vurlText = callRaw(source, "/App/Resource/Vurl/show", vurlBiz, sess)
        if (vurlText.isEmpty()) return info
        return GuapiParser.parseVurl(source, vurlText, vodId, info.vod_name, info.vod_pic) ?: info
    }

    /** 播放换链：线路 param 串 → showOne（业务体 = param 平铺展开）→ data.url */
    suspend fun resolvePlay(
        source: CmsSource,
        vodId: Long,
        token: String,
        from: String,
    ): PlayResolveResult {
        val sess = login(source) ?: return PlayResolveResult()
        if (token.isEmpty()) return PlayResolveResult()

        val text = callRaw(source, "/App/Resource/VurlDetail/showOne", GuapiParser.expandParam(token), sess)
        val r = GuapiParser.parsePlay(text)
        if (r.url.isEmpty()) return r
        // 流媒体域要求站方伪装 UA 与 Referer（实测不带会被拒）
        return PlayResolveResult(
            url = r.url,
            headers = JsonX.buildObject(
                "User-Agent" to SourceSecrets.GUAPI_UA,
                "Referer" to SourceSecrets.GUAPI_STREAM_REFERER,
            ).toString(),
        )
    }
}
