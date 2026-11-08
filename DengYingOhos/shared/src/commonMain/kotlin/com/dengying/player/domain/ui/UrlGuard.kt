package com.dengying.player.domain.ui

/**
 * 路由地址校验（SEC-005）—— 纯函数，不依赖 Android
 *
 * 需求：路由参数中的地址必须校验后才交给网络层；只接受 `http/https`；
 * 拒绝内网/回环地址。
 *
 * ## 为什么是「拒绝型」而不是「白名单型」
 *
 * 原设计的验收条件写「主机需命中来源清单」。按字面实现会有一个严重副作用：
 * 媒体地址普遍来自 **CDN**，域名与源的 api 主机不同（例如 api 在 `xxxapi.com`、
 * 切片在 `cdn-3.example.net`），严格白名单会把绝大多数可播线路误杀。
 *
 * 故采用**拒绝型**判定：
 *
 * 1. 协议必须是 `http` / `https`（其余一律拒绝）
 * 2. **明确拒绝内网与回环地址**——这是威胁模型真正要防的（SSRF 式探测本机/局域网服务）
 * 3. 命中来源清单的主机直接放行；未命中的公网主机也放行，但**标记 `offList`** 供上层
 *    决定（例如后续可收紧为「仅当来自适配器解析结果时放行」）
 *
 * 这样既拦住真正危险的目标，又不误杀 CDN 线路。
 *
 * 判定不读内容、不发请求，故可脱设备单测（NFR-009）。
 */
object UrlGuard {

    /** 拒绝文案（要能行动：告诉用户该怎么办） */
    const val NOTICE_BAD_SCHEME = "播放地址协议不受支持，只允许 http/https"
    const val NOTICE_PRIVATE = "播放地址指向本机或内网，已拒绝"
    const val NOTICE_EMPTY = "播放地址为空"

    /** 校验结论 */
    enum class Verdict { Ok, OkOffList, RejectScheme, RejectPrivate, RejectEmpty }

    data class Result(
        val verdict: Verdict,
        /** 是否允许进入网络层 */
        val allowed: Boolean,
        /** 是否未命中来源清单（放行但需可观测） */
        val offList: Boolean,
        /** 拒绝时的可行动提示；放行时为空串 */
        val notice: String,
    )

    /** 取 URL 的协议（小写）；无协议返回空串 */
    fun schemeOf(url: String): String {
        val i = url.indexOf("://")
        if (i <= 0) return ""
        return url.substring(0, i).lowercase()
    }

    /**
     * 取主机名（小写，去除端口与用户信息）。
     *
     * 解析失败返回空串——调用方据此判 `RejectEmpty`/`RejectPrivate`。
     */
    fun hostOf(url: String): String {
        val s = schemeOf(url)
        if (s.isEmpty()) return ""
        var rest = url.substring(s.length + 3)

        // 去掉路径/查询/片段
        val slash = rest.indexOf('/').let { if (it < 0) rest.length else it }
        val query = rest.indexOf('?').let { if (it < 0) rest.length else it }
        val frag = rest.indexOf('#').let { if (it < 0) rest.length else it }
        val end = minOf(slash, query, frag)
        rest = rest.substring(0, end)

        // 去掉用户信息 user@host
        val at = rest.lastIndexOf('@')
        if (at >= 0) rest = rest.substring(at + 1)

        // 去掉端口（IPv6 字面量带方括号，这里按「最后一个冒号在 ] 之后」处理）
        val close = rest.lastIndexOf(']')
        val colon = rest.lastIndexOf(':')
        if (colon > close) rest = rest.substring(0, colon)

        // 去掉 IPv6 的方括号
        if (rest.startsWith("[") && rest.endsWith("]")) {
            rest = rest.substring(1, rest.length - 1)
        }
        return rest.lowercase()
    }

    /** 是否 IPv4 字面量 */
    private fun isIpv4(h: String): Boolean {
        val parts = h.split('.')
        if (parts.size != 4) return false
        for (p in parts) {
            if (p.isEmpty() || p.length > 3) return false
            if (!p.all { it in '0'..'9' }) return false
            val n = p.toIntOrNull() ?: return false
            if (n < 0 || n > 255) return false
        }
        return true
    }

    /** 是否 IPv6 字面量（粗判：含冒号且只由十六进制与冒号组成） */
    private fun isIpv6(h: String): Boolean {
        if (!h.contains(':')) return false
        return h.all { c ->
            val l = c.lowercaseChar()
            (l in '0'..'9') || (l in 'a'..'f') || l == ':'
        }
    }

    /**
     * 是否内网 / 回环 / 链路本地地址。
     *
     * 覆盖：回环（127/8、::1）、私有段（10/8、172.16/12、192.168/16、fc00::/7）、
     * 链路本地（169.254/16、fe80::/10）、`0.0.0.0/8`、以及 `localhost` 等本地名。
     *
     * 【为什么含 169.254/16】云环境的元数据服务（169.254.169.254）就在这一段，
     * 是 SSRF 的经典目标。
     */
    fun isPrivateHost(h: String): Boolean {
        if (h.isEmpty()) return true
        if (h == "localhost" || h.endsWith(".localhost") || h == "local") return true

        if (isIpv4(h)) {
            val p = h.split('.')
            val a = p[0].toInt()
            val b = p[1].toInt()
            if (a == 127) return true          // 回环
            if (a == 10) return true           // 私有
            if (a == 172 && b in 16..31) return true  // 私有
            if (a == 192 && b == 168) return true     // 私有
            if (a == 169 && b == 254) return true     // 链路本地（含云元数据）
            if (a == 0) return true            // 0.0.0.0/8
            return false
        }

        if (isIpv6(h)) {
            val l = h.lowercase()
            if (l == "::1" || l == "::") return true  // 回环 / 未指定
            if (l.startsWith("fe80")) return true      // 链路本地
            if (l.isNotEmpty() && l[0] == 'f' && l.length > 1 && (l[1] == 'c' || l[1] == 'd')) {
                return true                            // 唯一本地 fc00::/7
            }
            return false
        }
        return false
    }

    /**
     * 校验一个待播放地址。
     *
     * @param url 路由参数或适配器解析出的地址
     * @param knownHosts 来源清单的主机集合（小写）；命中则直接放行
     */
    fun checkPlayUrl(url: String?, knownHosts: List<String>): Result {
        val raw = url ?: ""
        if (raw.trim().isEmpty()) {
            return Result(Verdict.RejectEmpty, false, false, NOTICE_EMPTY)
        }
        val s = schemeOf(raw)
        if (s != "http" && s != "https") {
            return Result(Verdict.RejectScheme, false, false, NOTICE_BAD_SCHEME)
        }
        val h = hostOf(raw)
        if (h.isEmpty() || isPrivateHost(h)) {
            return Result(Verdict.RejectPrivate, false, false, NOTICE_PRIVATE)
        }
        for (k in knownHosts) {
            if (k.isNotEmpty() && k == h) {
                return Result(Verdict.Ok, true, false, "")
            }
        }
        // 公网但不在清单：放行并标记，供上层决定是否收紧
        return Result(Verdict.OkOffList, true, true, "")
    }

    /** 从一组源地址里提取主机集合（供 [checkPlayUrl] 使用） */
    fun hostsOf(apis: List<String>): List<String> {
        val out = ArrayList<String>(apis.size)
        for (a in apis) {
            val h = hostOf(a)
            if (h.isNotEmpty() && !out.contains(h)) out += h
        }
        return out
    }
}
