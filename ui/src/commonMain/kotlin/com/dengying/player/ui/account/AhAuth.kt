package com.dengying.player.ui.account

import com.dengying.player.data.json.JsonX
import com.dengying.player.data.net.HttpClient
import kotlinx.serialization.json.JsonObject
import com.dengying.player.platform.Clock
import com.dengying.player.platform.KeyValueStore
import kotlin.concurrent.Volatile


/**
 * AH 账号体系（`danmu.bmsp9.xyz`）的登录 / 注册 / 会话入口。
 *
 * 【接口契约】见 `D:\workspace\AHVideo\AH视频_账号体系登录注册接口文档.md`：
 * - `POST /auth/login`、`POST /auth/register` 的 JSON body 必须同时带 `ts`（秒级，
 *   字符串形态）与 `app_auth_token`（48 字节 base64）。二者放 query 或 header 会得到
 *   422；`ts` 偏差超出 −299…+300 秒得到 `Expired app_auth_token`；签名不匹配得到
 *   `Invalid app_auth_token`。校验顺序在账号字段之前，签名没通时账号根本不被判断。
 * - 服务端账号字段名是 `account`（不是 `username`）；只传 `username` 返回
 *   `422 Missing field: account`。
 * - `POST /auth/refresh` 只读 `refresh_token`，不校验 `ts` / `app_auth_token`，
 *   是当前唯一可用的登录态入口（文档 §8.3）。
 * - `GET /user` 需要 `Authorization: Bearer <access_token>`。
 *
 * 【为什么这里允许 HTTP 明文】账号服务只提供 `http://` 端点；应用已有的
 * `res/xml/network_security_config.xml` 为采集源保留了全局明文例外且仍校验 TLS
 * 证书链。这里沿用同一条出网通道 [HttpClient]，不新建 client、不引第三方 SDK；
 * 例外只限于这一个已文档化的账号域，不扩展到播放链路。
 *
 * 【签名未取得时的行为】静态分析只确认了算法输入，未提取到
 * `appAuthTokenSecret`；本实现**不猜密钥、不伪造成功**。未配置令牌时本地直接
 * 拦下并给出可操作提示，不向服务端发注定 422 的请求。真机抓到的令牌可用
 * [submitAppAuthToken] 落盘（300 秒内有效），密钥到手后只需替换
 * [appAuthToken] 的取用逻辑，页面与调用方不变。
 *
 * 【登录态】成功响应顶层与 `data` 嵌套两种形态都兼容（文档 §5.9 的实测是嵌在
 * `data` 里）。解不出 access_token 就不写登录态，不伪造成功。
 */
object AhAuth {

    private const val HOST = "http://danmu.bmsp9.xyz"
    private const val LOGIN_PATH = "$HOST/auth/login"
    private const val REGISTER_PATH = "$HOST/auth/register"
    private const val REFRESH_PATH = "$HOST/auth/refresh"
    private const val USER_PATH = "$HOST/user"

    private const val KEY_APP_AUTH_TOKEN = "ah_app_auth_token_v1"
    private const val KEY_ACCOUNT = "ah_account_v1"
    private const val KEY_SESSION_RAW = "ah_session_raw_v1"

    // 与原客户端 MMKV 键名对齐（auth_access_token_v1 等），方便对照抓包核对。
    private const val KEY_ACCESS_TOKEN = "auth_access_token_v1"
    private const val KEY_REFRESH_TOKEN = "auth_refresh_token_v1"
    private const val KEY_USER_JSON = "auth_user_v1"
    private const val KEY_ACCESS_EXPIRES = "auth_access_expires_v1"
    private const val KEY_REFRESH_EXPIRES = "auth_refresh_expires_v1"

    private const val CONNECT_MS = 8_000
    private const val READ_MS = 10_000

    /** 访问令牌提前量：距离到期不足 30 秒就主动刷新，避免临界请求 401。 */
    private const val REFRESH_SKEW_SEC = 30L

    private const val MSG_NO_TOKEN =
        "登录服务需要签名令牌（app_auth_token），当前未取得：可在登录页「令牌设置」粘贴真机抓包值（300 秒内有效），或用会话令牌直接恢复登录"

    sealed interface Outcome {
        data class Ok(val account: String, val message: String) : Outcome
        data class Error(val message: String) : Outcome
    }

    /** 服务端返回码归一化：HTTP 状态 + body 里的 `message`。 */
    private data class Reply(val status: Int, val raw: String, val body: String)

    @Volatile
    private var kv: KeyValueStore? = null

    /** 由平台壳在装配时注入（与 UiDepsHolder.init 同处，setContent 之前）。 */
    fun init(store: KeyValueStore) {
        kv = store
    }

    private fun kv(): KeyValueStore? = kv

    /** 是否已登录：以本地存有 access_token 为准。过期由 [accessTokenExpired] + 刷新链兜底。 */
    fun isLoggedIn(): Boolean = accessToken().isNotEmpty()

    fun accessToken(): String = kv()?.getString(KEY_ACCESS_TOKEN, "") ?: ""

    fun refreshToken(): String = kv()?.getString(KEY_REFRESH_TOKEN, "") ?: ""

    /** 访问令牌是否已（或即将）过期；没有到期时间时按「未过期」处理。 */
    fun accessTokenExpired(): Boolean {
        val expires = kv()?.getString(KEY_ACCESS_EXPIRES, "")?.toLongOrNull() ?: return false
        if (expires <= 0L) return false
        return nowSec() >= expires - REFRESH_SKEW_SEC
    }

    /** 展示用昵称：优先取用户资料里的 nickname，没有就退回登录时的账号。 */
    fun nickname(): String {
        val stored = kv()?.getString(KEY_USER_JSON, "") ?: ""
        val name = JsonX.str(JsonX.parseObject(stored), "nickname")
        if (name.isNotEmpty()) return name
        return kv()?.getString(KEY_ACCOUNT, "") ?: ""
    }

    /** 头像地址；没有资料或资料里没有该字段时为空串。 */
    fun avatarUrl(): String {
        val stored = kv()?.getString(KEY_USER_JSON, "") ?: ""
        return JsonX.str(JsonX.parseObject(stored), "avatar_url")
    }

    /** 本地还没有用户资料时返回 true，供「我的」页补拉一次 GET /user。 */
    fun needsProfile(): Boolean {
        if (!isLoggedIn()) return false
        val stored = kv()?.getString(KEY_USER_JSON, "") ?: ""
        return JsonX.str(JsonX.parseObject(stored), "nickname").isEmpty()
    }

    /**
     * 用已存的 access_token 拉一次 `GET /user` 并落盘。
     *
     * 令牌过期时先走 `POST /auth/refresh` 换新再重试一次；刷新失败才清登录态，
     * 避免一次 401 就把可用会话丢掉（文档 §9 的过期处理口径）。
     *
     * @return 是否拿到了资料。
     */
    suspend fun refreshProfile(): Boolean {
        if (accessToken().isEmpty()) return false

        if (accessTokenExpired()) {
            // 刷新失败不阻断：旧令牌可能仍然有效（服务端时钟差异等），由下面的 401 分支兜底。
            refreshTokens()
        }
        var status = fetchProfileInto(accessToken())
        if (status == 401) {
            if (!refreshTokens()) {
                logout()
                return false
            }
            status = fetchProfileInto(accessToken())
        }
        return status in 200..299
    }

    /** 退出登录：只清本地登录态，不调用服务端（文档没有登出端点）。 */
    fun logout() {
        val store = kv() ?: return
        store.remove(KEY_ACCESS_TOKEN, sync = true)
        store.remove(KEY_REFRESH_TOKEN, sync = true)
        store.remove(KEY_USER_JSON, sync = true)
        store.remove(KEY_ACCOUNT, sync = true)
        store.remove(KEY_SESSION_RAW, sync = true)
        store.remove(KEY_ACCESS_EXPIRES, sync = true)
        store.remove(KEY_REFRESH_EXPIRES, sync = true)
    }

    /**
     * 账号 / 邮箱 + 密码登录（文档 §6.2）。
     *
     * 本地只做「非空 + 密码至少 6 位」的校验（对应文档 UI 文案）；其余交给服务端。
     */
    suspend fun login(account: String, password: String): Outcome {
        val user = account.trim()
        if (user.isEmpty()) return Outcome.Error("请输入账号")
        if (password.isEmpty()) return Outcome.Error("请输入密码")
        if (password.length < 6) return Outcome.Error("密码长度不能少于6位")
        val token = appAuthToken()
        if (token.isEmpty()) return Outcome.Error(MSG_NO_TOKEN)
        val body = buildLoginBody(user, password, nowSec().toString(), token)
        return request(LOGIN_PATH, body, user, isLogin = true, success = "登录成功")
    }

    /**
     * 创建账号（文档 §6.1）。
     *
     * 请求体按文档只发 `account` / `password` / `ts` / `app_auth_token`；
     * 邀请码是客户端侧的进度功能（文档 §7.2），服务端未文档化该字段，仅在填写时附带。
     */
    suspend fun register(
        account: String,
        password: String,
        confirmPassword: String,
        inviteCode: String,
    ): Outcome {
        val user = account.trim()
        if (user.isEmpty()) return Outcome.Error("请输入账号")
        if (!isNumericOrEmail(user)) return Outcome.Error("账号只能是数字或邮箱")
        if (password.isEmpty()) return Outcome.Error("请输入密码")
        if (password.length < 6) return Outcome.Error("密码长度不能少于6位")
        if (password != confirmPassword) return Outcome.Error("新密码和重复新密码不一致")
        val token = appAuthToken()
        if (token.isEmpty()) return Outcome.Error(MSG_NO_TOKEN)
        val body = buildRegisterBody(user, password, nowSec().toString(), token, inviteCode)
        return request(REGISTER_PATH, body, user, isLogin = false, success = "注册成功")
    }

    /**
     * 第三方（QQ）登录（文档 §5.9 实测契约）。
     *
     * QQ 登录没有专用换取端点：客户端把 QQ 侧授权结果直接提交到 `POST /auth/login`，
     * `account` 为 32 位十六进制标识，`password` 与 `account` 同值（服务端不比对），
     * 并带 `is_third_party=true`、`nickname`、`avatar_url`。
     *
     * 本应用不引入 QQ SDK，所以「取 openid」这一步不在本函数内；调用方需要先拿到
     * 那 32 位标识（例如从已登录设备的资料里读，或由用户粘贴）。
     */
    suspend fun loginThirdParty(account: String, nickname: String, avatarUrl: String): Outcome {
        val id = account.trim().uppercase()
        if (!isThirdPartyAccount(id)) return Outcome.Error("QQ 登录需要 32 位十六进制账号标识")
        val token = appAuthToken()
        if (token.isEmpty()) return Outcome.Error(MSG_NO_TOKEN)
        val body = buildThirdPartyBody(id, nickname.trim(), avatarUrl.trim(), nowSec().toString(), token)
        return request(LOGIN_PATH, body, id, isLogin = true, success = "登录成功")
    }

    /**
     * QQ 登录的直接入口：授权取 openid 需要 QQ SDK，本应用不引入第三方 SDK。
     *
     * 后端契约已实测（[loginThirdParty]），缺的只有「拿到 32 位标识」这一步；
     * 这里如实说明，不假装可用。
     *
     * 注意：登录页目前直接显示「QQ 登录暂未开放」，不渲染本方法的说明文字；
     * 保留它是因为 [loginThirdParty] 的契约以后接线时还要用。
     */
    fun qqLoginNotReady(): Outcome = Outcome.Error(
        "QQ 登录需要 QQ SDK 授权取得 32 位账号标识；本应用不引入第三方 SDK。可先用账号 / 邮箱登录，或粘贴会话令牌恢复登录",
    )

    /**
     * 用 `refresh_token` 恢复登录（文档 §6.3 / §8.3）。该端点不校验签名，
     * 是当前唯一不依赖 `app_auth_token` 的登录态入口。
     */
    suspend fun submitRefreshToken(token: String): Outcome {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return Outcome.Error("请先粘贴会话令牌（refresh_token）")
        kv()?.putString(KEY_REFRESH_TOKEN, trimmed, sync = true)
        if (!refreshTokens()) {
            kv()?.remove(KEY_REFRESH_TOKEN, sync = true)
            return Outcome.Error("会话令牌无效或已过期，请重新获取")
        }
        val account = kv()?.getString(KEY_ACCOUNT, "") ?: ""
        refreshProfile()
        return Outcome.Ok(account, "登录成功")
    }

    /**
     * 用已存的 refresh_token 换一对新令牌（文档 §6.3）。成功返回 true。
     * 响应字段名文档标为待实测，这里同时认顶层与 `data` 两种形态。
     */
    suspend fun refreshTokens(): Boolean {
        val stored = refreshToken()
        if (stored.isEmpty()) return false
        val body = JsonX.buildObject("refresh_token" to stored).toString()
        val reply = try {
            post(REFRESH_PATH, body)
        } catch (_: Exception) {
            return false
        }
        if (reply.status !in 200..299) return false
        val json = JsonX.parseObject(reply.body) ?: return false
        val access = pickToken(json, "access_token")
        if (access.isEmpty()) return false
        val store = kv() ?: return false
        store.putString(KEY_ACCESS_TOKEN, access, sync = true)
        val nextRefresh = pickToken(json, "refresh_token")
        if (nextRefresh.isNotEmpty()) store.putString(KEY_REFRESH_TOKEN, nextRefresh, sync = true)
        storeExpires(json)
        return true
    }

    /**
     * 保存真机抓到的 `app_auth_token`（文档 §8.4）。
     *
     * 注意：令牌与 `ts` 绑定且只有 300 秒有效，单独保存旧值不会让后续请求自动
     * 通过；这是给真机复核用的落盘入口，不改写 [appAuthToken] 的取用时机。
     */
    fun submitAppAuthToken(token: String) {
        if (token.isBlank()) return
        kv()?.putString(KEY_APP_AUTH_TOKEN, token.trim(), sync = true)
    }

    fun savedAppAuthToken(): String = kv()?.getString(KEY_APP_AUTH_TOKEN, "") ?: ""

    // ---- 请求体（纯函数，见 AhAuthRequestBodyTest）----

    internal fun buildLoginBody(account: String, password: String, ts: String, token: String): String =
        JsonX.buildObject(
            "account" to account,
            "password" to password,
            "ts" to ts,
            "app_auth_token" to token,
        ).toString()

    internal fun buildRegisterBody(
        account: String,
        password: String,
        ts: String,
        token: String,
        inviteCode: String,
    ): String {
        val code = inviteCode.trim()
        return if (code.isEmpty()) {
            buildLoginBody(account, password, ts, token)
        } else {
            // 服务端未文档化 invite_code；仅在用户填写时附带，不改变必需字段。
            JsonX.buildObject(
                "account" to account,
                "password" to password,
                "invite_code" to code,
                "ts" to ts,
                "app_auth_token" to token,
            ).toString()
        }
    }

    internal fun buildThirdPartyBody(
        account: String,
        nickname: String,
        avatarUrl: String,
        ts: String,
        token: String,
    ): String = JsonX.buildObject(
        "account" to account,
        "password" to account,
        "is_third_party" to true,
        "nickname" to nickname,
        "avatar_url" to avatarUrl,
        "ts" to ts,
        "app_auth_token" to token,
    ).toString()

    /** 注册账号格式：纯数字或邮箱（文档 §5.4 第 5 步）。 */
    internal fun isNumericOrEmail(value: String): Boolean {
        if (value.isEmpty()) return false
        if (value.all { it.isDigit() }) return true
        val at = value.indexOf('@')
        if (at <= 0 || at != value.lastIndexOf('@')) return false
        val domain = value.substring(at + 1)
        return domain.length >= 3 && domain.contains('.') &&
            !domain.startsWith('.') && !domain.endsWith('.')
    }

    /** QQ 登录的账号标识：32 位十六进制（文档 §5.9 实测为 32 位大写十六进制）。 */
    internal fun isThirdPartyAccount(value: String): Boolean =
        value.length == 32 && value.all { it.isDigit() || it in 'A'..'F' }

    /**
     * 服务端错误 → 可读中文（文档 §5.5 的错误表）。
     * @param isLogin 只用于兜底前缀（登录失败 / 注册失败）。
     */
    internal fun friendlyError(status: Int, rawMessage: String, isLogin: Boolean): String {
        val msg = rawMessage.trim()
        val prefix = if (isLogin) "登录失败：" else "注册失败："
        return when {
            msg.contains("Expired app_auth_token", ignoreCase = true) ->
                "设备时间与服务器相差过大（Expired app_auth_token），请校准系统时间后重试"
            msg.contains("Invalid app_auth_token", ignoreCase = true) ->
                "登录服务的签名令牌无效或已过期（403 Invalid app_auth_token），请在「令牌设置」更新"
            msg.contains("Missing field: ts or app_auth_token", ignoreCase = true) ->
                "登录服务需要签名令牌（app_auth_token），请在「令牌设置」粘贴真机抓包值"
            msg.contains("Missing field: account", ignoreCase = true) -> "请填写账号"
            msg.contains("Missing field: password", ignoreCase = true) -> "请填写密码"
            msg.contains("帐号只能是数字或邮箱格式") -> "账号只能是数字或邮箱"
            msg.contains("密码长度") -> "密码长度不能少于6位"
            msg.contains("Missing field: refresh_token", ignoreCase = true) -> "会话令牌缺失"
            msg.contains("无效的刷新令牌") -> "会话令牌无效或已过期，请重新获取"
            msg.contains("同一 IP 注册次数已达上限", ignoreCase = true) || status == 429 ->
                "同一网络注册次数已达上限，请稍后再试"
            status == 401 -> "登录已过期，请重新登录"
            status in 500..599 -> "登录服务暂时不可用（HTTP $status），请稍后再试"
            status == 404 -> "$prefix 服务端未找到该接口（HTTP 404）"
            msg.isNotEmpty() -> prefix + msg
            else -> prefix + "HTTP $status"
        }
    }

    // ---- 内部实现 ----

    private suspend fun request(
        path: String,
        body: String,
        account: String,
        isLogin: Boolean,
        success: String,
    ): Outcome {
        val reply = try {
            post(path, body)
        } catch (_: Exception) {
            // 传输层异常（DNS / 超时 / 明文策略）由平台 actual 抛到这里；
            // 不把异常原文塞进 UI，也不让登录按钮卡在 busy。
            return Outcome.Error("网络异常，请检查网络后重试")
        }
        if (reply.status == 0) return Outcome.Error("网络异常，请检查网络后重试")

        val json = JsonX.parseObject(reply.body)
        // 响应带 code 且不是 0/200，按业务失败处理；没有 code 字段则只认 HTTP 2xx。
        val hasCode = json?.containsKey("code") == true
        val code = JsonX.int(json, "code")
        val logicalOk = reply.status in 200..299 && (!hasCode || code == 0 || code == 200)
        if (!logicalOk) {
            return Outcome.Error(friendlyError(reply.status, reply.raw, isLogin))
        }

        val token = pickToken(json, "access_token")
        if (token.isEmpty()) {
            return Outcome.Error(if (isLogin) "登录失败：服务端未返回登录令牌" else "注册失败：服务端未返回登录令牌")
        }
        val store = kv()
        store?.putString(KEY_ACCESS_TOKEN, token, sync = true)
        val refresh = pickToken(json, "refresh_token")
        if (refresh.isNotEmpty()) store?.putString(KEY_REFRESH_TOKEN, refresh, sync = true)
        store?.putString(KEY_ACCOUNT, account, sync = true)
        store?.putString(KEY_SESSION_RAW, reply.body.take(4096), sync = true)
        storeExpires(json)
        // 用户资料能从响应里直接拿到就用，拿不到再拉一次 GET /user（文档 §5.6）。
        val inlineUser = pickUser(json)
        val profile = inlineUser ?: fetchProfileObject(token)
        if (profile != null) store?.putString(KEY_USER_JSON, slimUser(profile), sync = true)
        return Outcome.Ok(account, success)
    }

    private suspend fun post(path: String, body: String): Reply {
        val result = HttpClient.post(
            url = path,
            body = body,
            headers = mapOf(
                "User-Agent" to HttpClient.UA_SYSTEM,
                "Content-Type" to "application/json;charset=UTF-8",
                "Accept" to "application/json, text/plain, */*",
            ),
            connectMs = CONNECT_MS,
            readMs = READ_MS,
        )
        val json = JsonX.parseObject(result.body)
        val message = JsonX.str(json, "message").ifEmpty { JsonX.str(json, "msg") }
        return Reply(result.status, message, result.body)
    }

    /** GET /user 并把资料落盘；返回 HTTP 状态码，异常按 0 处理。 */
    private suspend fun fetchProfileInto(token: String): Int {
        if (token.isEmpty()) return 401
        val result = try {
            HttpClient.get(
                url = USER_PATH,
                headers = mapOf(
                    "User-Agent" to HttpClient.UA_SYSTEM,
                    "Authorization" to "Bearer $token",
                    "Accept" to "application/json, text/plain, */*",
                ),
                connectMs = CONNECT_MS,
                readMs = READ_MS,
            )
        } catch (_: Exception) {
            return 0
        }
        if (result.status !in 200..299) return result.status
        val json = JsonX.parseObject(result.body) ?: return result.status
        val data = JsonX.optObject(json, "data")
        val profile = when {
            JsonX.str(data, "nickname").isNotEmpty() -> data
            JsonX.str(json, "nickname").isNotEmpty() -> json
            else -> null
        } ?: return result.status
        kv()?.putString(KEY_USER_JSON, slimUser(profile), sync = true)
        return result.status
    }

    /** 登录响应里直接带的用户对象；用于省掉一次 GET /user。 */
    private suspend fun fetchProfileObject(token: String): JsonObject? {
        if (fetchProfileInto(token) !in 200..299) return null
        val stored = kv()?.getString(KEY_USER_JSON, "") ?: ""
        return JsonX.parseObject(stored)
    }

    /** 顶层没有该字段时退到 `data` 里找；两处都没有返回空串。 */
    private fun pickToken(json: JsonObject?, key: String): String {
        val top = JsonX.str(json, key)
        if (top.isNotEmpty()) return top
        return JsonX.str(JsonX.optObject(json, "data"), key)
    }

    /** 到期时间同时认顶层与 `data`，用于主动刷新判定。 */
    private fun storeExpires(json: JsonObject?) {
        val data = JsonX.optObject(json, "data")
        val store = kv() ?: return
        val accessExpires = JsonX.long(json, "access_expires_at")
            .takeIf { it > 0L } ?: JsonX.long(data, "access_expires_at")
        if (accessExpires > 0L) {
            store.putString(KEY_ACCESS_EXPIRES, accessExpires.toString(), sync = true)
        }
        val refreshExpires = JsonX.long(json, "refresh_expires_at")
            .takeIf { it > 0L } ?: JsonX.long(data, "refresh_expires_at")
        if (refreshExpires > 0L) {
            store.putString(KEY_REFRESH_EXPIRES, refreshExpires.toString(), sync = true)
        }
    }

    /** 用户资料对象：顶层 `user`，其次 `data.user`，再其次 `data` 本身（带 nickname 时），都没有返回 null。 */
    private fun pickUser(json: JsonObject?): JsonObject? {
        val top = JsonX.optObject(json, "user")
        if (top != null && JsonX.str(top, "nickname").isNotEmpty()) return top
        val data = JsonX.optObject(json, "data") ?: return null
        val nested = JsonX.optObject(data, "user")
        if (nested != null && JsonX.str(nested, "nickname").isNotEmpty()) return nested
        return if (JsonX.str(data, "nickname").isNotEmpty()) data else null
    }

    /** 只保留展示用字段，避免把整个响应原样落盘。 */
    private fun slimUser(user: JsonObject): String = JsonX.buildObject(
        "id" to JsonX.long(user, "id"),
        "user_no" to JsonX.long(user, "user_no"),
        "account" to JsonX.str(user, "account"),
        "nickname" to JsonX.str(user, "nickname"),
        "avatar_url" to JsonX.str(user, "avatar_url"),
        "vip" to JsonX.bool(user, "vip"),
        "vip_expires_at" to JsonX.long(user, "vip_expires_at"),
    ).toString()

    private fun nowSec(): Long = Clock.nowMs() / 1000L

    /**
     * 签名令牌来源：真机抓到的值由 [submitAppAuthToken] 落盘；未配置时返回空串。
     * 空串不是「假令牌」，它让调用方在本地就给出可操作提示，不发注定 422 的请求。
     * 抓到密钥后按文档补 `signAppAuthToken(ts)`，只需替换这里的取用逻辑。
     */
    private fun appAuthToken(): String = kv()?.getString(KEY_APP_AUTH_TOKEN, "") ?: ""
}
