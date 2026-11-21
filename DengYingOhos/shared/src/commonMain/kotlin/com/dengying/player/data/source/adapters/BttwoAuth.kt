package com.dengying.player.data.source.adapters

import com.dengying.player.data.json.JsonX
import com.dengying.player.data.net.HttpClient
import com.dengying.player.platform.Clock
import com.dengying.player.platform.KeyValueStore
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 两个BT（bttwo.life）登录态（AH 视频逆向接口，`AH视频_后端接口清单.md` §9.2）。
 *
 * ## 协议（站点 JS `bttwo_app_bundle.js`，已实测）
 *
 * 1. `GET /api/login/nonce` → `{code:200, data:{nonce}}`
 * 2. `POST /api/login`，body `{username, password, client_ts, client_nonce, client_sign}`，
 *    `client_sign = javaHashCode("nbmv_login_v1|{ts}|{user小写}|{pass}|{nonce}")` 8 位 hex
 * 3. 成功响应 `code == 200`，服务端 `Set-Cookie: nbmovie_session=...`
 * 4. 登录后播放页下发的 `userlink` 变成登录值 → 4K 档解锁、可用线路更多；
 *    播放请求 `k` 与 cookie **都要带**（清单 §9.2 实测：只带其一 → 401）
 *
 * ## 存储口径
 *
 * 会话 cookie 与用户名直接存 [KeyValueStore]（不走 [com.dengying.player.data.store.Store]
 * 的信封/剪枝——登录态不是列表资产，一个键一个值，信封那套是给列表用的）。
 * [init] 由宿主（DengYingApp）在装配时调用；未 init 时全部功能安全退化为未登录。
 *
 * ## 平台注意
 *
 * `set-cookie` 读取依赖 [HttpClient.HttpResult.headers]：JVM/Android、iOS 已透传；
 * 鸿蒙走 JS 桥（只回传 body）暂无响应头 → 鸿蒙侧登录拿不到会话，[login] 返回
 * 明确错误文案而不是静默失败。
 */
object BttwoAuth {

    private const val HOST = "https://www.bttwo.life"
    private const val KEY_SESSION = "bttwo_session"
    private const val KEY_USER = "bttwo_user"
    private const val COOKIE_NAME = "nbmovie_session"

    private const val CONNECT_MS = 8000
    private const val READ_MS = 10000

    @Volatile
    private var kv: KeyValueStore? = null

    /** 宿主装配时注入（DengYingApp.onCreate，与 Store 共用同一 preferences 文件）。 */
    fun init(store: KeyValueStore) {
        kv = store
    }

    /** 当前会话 cookie 值（`nbmovie_session=...` 的值部分）；未登录为空串。 */
    fun session(): String = kv?.getString(KEY_SESSION, "").orEmpty()

    /** 登录用户名（仅展示用）；未登录为空串。 */
    fun userName(): String = kv?.getString(KEY_USER, "").orEmpty()

    /** 是否已登录（以本地存有会话 cookie 为准；会话过期由播放 401 兜底，见下）。 */
    fun isLoggedIn(): Boolean = session().isNotEmpty()

    /**
     * 播放页/换链请求要带的 Cookie 头值；未登录返回 null（调用方不加该头）。
     */
    fun cookieHeader(): String? =
        session().takeIf { it.isNotEmpty() }?.let { "$COOKIE_NAME=$it" }

    /**
     * 登录。成功返回用户名；失败返回用户可读的错误文案（不抛异常）。
     */
    suspend fun login(username: String, password: String): LoginOutcome {
        val user = username.trim()
        if (user.isEmpty() || password.isEmpty()) {
            return LoginOutcome.Error("请输入账号和密码")
        }
        val nonce = fetchNonce()
        if (nonce.isEmpty()) {
            return LoginOutcome.Error("登录服务不可达，请稍后再试")
        }
        val ts = Clock.nowMs()
        val body = buildJsonObject {
            put("username", user)
            put("password", password)
            put("client_ts", ts)
            put("client_nonce", nonce)
            put("client_sign", BttwoSign.clientSign(user, password, ts, nonce))
        }
        val r = HttpClient.post(
            "$HOST/api/login",
            body.toString(),
            mapOf(
                "User-Agent" to HttpClient.UA_PC,
                "Content-Type" to "application/json",
                "Accept" to "application/json, text/plain, */*",
            ),
            CONNECT_MS, READ_MS,
        )
        if (r.elapsedMs == 0L || (r.status != 200 && r.status != 401 && r.status != 403)) {
            return LoginOutcome.Error("网络异常（HTTP ${r.status}），请稍后再试")
        }
        val json = JsonX.parseObject(r.body)
        val code = JsonX.int(json, "code", 0)
        if (code != 200) {
            // 站点错误文案在 message 字段（JS 弹窗直接展示它）
            val msg = JsonX.str(json, "message").ifEmpty { JsonX.str(json, "msg") }
            return LoginOutcome.Error(msg.ifEmpty { "账号或密码错误" })
        }
        val session = parseSession(r.headers["set-cookie"])
            ?: // 响应体兜底：部分部署把 token 放在 data.session/data.token
            JsonX.str(JsonX.optObject(json, "data"), "session")
                .ifEmpty { JsonX.str(JsonX.optObject(json, "data"), "token") }
        if (session.isEmpty()) {
            // code=200 但没有会话：按协议不该发生（鸿蒙无响应头时会落这里）
            return LoginOutcome.Error("登录服务未返回会话（当前平台不支持），请稍后再试")
        }
        kv?.putString(KEY_SESSION, session, sync = true)
        kv?.putString(KEY_USER, user, sync = true)
        return LoginOutcome.Ok(user)
    }

    /** 退出登录：尽力通知服务端，本地会话无论如何都清掉。 */
    suspend fun logout() {
        val cookie = cookieHeader()
        if (cookie != null) {
            try {
                HttpClient.post(
                    "$HOST/api/logout", "",
                    mapOf("User-Agent" to HttpClient.UA_PC, "Cookie" to cookie),
                    CONNECT_MS, READ_MS,
                )
            } catch (_: Exception) {
                // 本地清登录态是主目标；服务端通知失败不影响
            }
        }
        kv?.remove(KEY_SESSION, sync = true)
        kv?.remove(KEY_USER, sync = true)
    }

    /** 拉登录 nonce；任何失败都返回空串（调用方统一按「服务不可达」处理）。 */
    private suspend fun fetchNonce(): String {
        val r = HttpClient.get(
            "$HOST/api/login/nonce",
            mapOf("User-Agent" to HttpClient.UA_PC, "Accept" to "application/json"),
            CONNECT_MS, READ_MS,
        )
        if (r.status != 200) return ""
        val json = JsonX.parseObject(r.body)
        if (JsonX.int(json, "code", 0) != 200) return ""
        return JsonX.str(JsonX.optObject(json, "data"), "nonce")
    }

    /** 从 `Set-Cookie` 响应头值里抠 `nbmovie_session`（可能多条拼接、带属性段）。 */
    internal fun parseSession(setCookie: String?): String? {
        if (setCookie.isNullOrEmpty()) return null
        val re = Regex("$COOKIE_NAME=([^;\\s]+)")
        return re.find(setCookie)?.groupValues?.getOrNull(1)
    }

    /** 登录结果：成功带用户名，失败带可读文案。 */
    sealed class LoginOutcome {
        data class Ok(val username: String) : LoginOutcome()
        data class Error(val message: String) : LoginOutcome()
    }
}
