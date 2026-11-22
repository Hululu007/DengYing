package com.dengying.player.data.source.adapters

import com.dengying.player.data.json.JsonX
import com.dengying.player.data.net.HttpClient
import com.dengying.player.platform.Clock
import com.dengying.player.platform.Log

/**
 * 桔子根域动态下发（AH 接口清单「二、动态下发层」）
 *
 * 桔子的业务根域**不是固定的**：服务端把当前可用域名写在一个对象存储文件里，
 * App 每次启动先读它再拼业务 URL。迁移前 `CmsSources.ah_juzhi.api` 写死
 * `http://103.36.167.27:18004`，该主机已 502 —— 这是本模块存在的原因。
 *
 * 下发文件（实测 2026-10，HTTP 200 / 98B）：
 * ```
 * {"domain": "http://fuckducai.biosyne.cn", "enabled": true,
 *  "offlineUrl": "https://juzhi7777.app"}
 * ```
 * 同目录的 `oss.txt` 是 `0903-1030` 形态的**生效日期区间**，不是接口，不读。
 *
 * 契约：
 *  1. 返回的根域**不带尾斜杠**（调用方直接拼 `$base/api/...`）；
 *  2. 任何失败（网络失败 / 非 200 / 非 JSON / `domain` 为空 / `enabled == false`）
 *     一律回退到调用方传入的 `defaultBase`，**绝不抛异常**——根域下发失败不该
 *     让整个源不可用，写死的地址仍是有效兜底；
 *  3. 成功后缓存 [TTL_MS]，期内不再发请求（首页/搜索页会反复调，不能每次都出网）；
 *  4. `enabled == false` 视为「服务端主动下线」，**不缓存**，下次仍会重新读。
 *
 * 为什么放 commonMain 而不是各平台 actual：只依赖 [HttpClient] 与 [JsonX]，
 * 两者都已是跨平台门面，无任何平台 API。
 */
object JuzhiRoot {

    /** 下发文件地址（实测可用；改址只改这一处） */
    private const val ROSS_URL =
        "https://oss-1372779881.cos.ap-guangzhou.myqcloud.com/ross.txt"

    private const val CONNECT_MS = 5000
    private const val READ_MS = 5000

    /** 缓存时长：15 分钟。根域轮换不频繁，但也不能缓存太久——换域后旧域会开始 502 */
    private const val TTL_MS = 15 * 60 * 1000L

    private const val TAG = "DY/JuzhiRoot"

    private var cachedBase: String = ""
    private var cachedAt: Long = 0

    /**
     * 取当前可用根域。
     *
     * @param defaultBase 下发失败时的兜底根域（通常是 `source.api`）
     * @return 可用根域，**保证非空且无尾斜杠**
     */
    suspend fun resolve(defaultBase: String): String {
        val now = Clock.nowMs()
        if (cachedBase.isNotEmpty() && (now - cachedAt) < TTL_MS) return cachedBase

        val resolved = fetch()
        if (resolved.isEmpty()) return defaultBase

        cachedBase = resolved
        cachedAt = now
        return resolved
    }

    /** 强制下一次 [resolve] 重新读下发文件（供自检与换域后重试用） */
    fun invalidate() {
        cachedBase = ""
        cachedAt = 0
    }

    /** 读下发文件并解析；任何一步不成立返回空串 */
    private suspend fun fetch(): String {
        val r = HttpClient.get(
            ROSS_URL,
            mapOf("User-Agent" to HttpClient.UA_SYSTEM, "Accept" to "application/json, */*"),
            CONNECT_MS, READ_MS,
        )
        if (r.status != 200) {
            Log.w(TAG, "ross.txt HTTP ${r.status}，回退默认根域")
            return ""
        }

        val domain = parseRoss(r.body)
        if (domain.isEmpty()) {
            Log.w(TAG, "ross.txt 不可用，回退默认根域")
        }
        return domain
    }

    /**
     * 解析下发文件正文 → 根域（空串 = 不可用）。
     *
     * 抽成独立函数是为了**能离线测**：网络分支（超时/非 200）不可复现，
     * 但「JSON 长什么样才算可用」是纯字符串判定，用金样本 `juzhi_ross.txt`
     * 与若干负向样本就能钉死。`internal` 让同模块的 `jvmTest` 可调用。
     *
     * 判定顺序（任一步不成立即返回空串）：
     *  1. 顶层必须是 JSON **对象**（数组/纯文本都不行）；
     *  2. `enabled` 缺省视为 true —— 下发文件的核心字段是 domain，
     *     缺 enabled 时按「可用」处理比按「下线」处理更不容易误伤；
     *     `enabled == false` 表示服务端主动下线；
     *  3. `domain` 必须非空、以 `http` 开头（协议相对写法与相对路径都不接受），
     *     并去掉尾斜杠（调用方直接拼 `$base/api/...`，多一个斜杠会变双斜杠）。
     */
    internal fun parseRoss(body: String): String {
        val o = JsonX.parseObject(body) ?: return ""
        if (!JsonX.bool(o, "enabled", true)) return ""
        val domain = JsonX.str(o, "domain").trim().trimEnd('/')
        if (domain.isEmpty() || !domain.startsWith("http")) return ""
        return domain
    }
}
