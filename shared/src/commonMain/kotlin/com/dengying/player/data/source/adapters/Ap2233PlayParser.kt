package com.dengying.player.data.source.adapters

/**
 * 蓝光4k（Api2233）播放串识别层 —— **纯 Kotlin，无平台依赖**
 *
 * ## 这个类为什么只做「识别」而不做「解密」
 *
 * 该源的 `vod_play_url` 里没有明文媒体地址，每集是一个**加密串**。实测（2026-10-04，
 * 样本 `ap2233_videolist` / `ap2233_detail906`）两种形态：
 *
 * - `ETH-<hex>`（记为 [TokenKind.Bba]）：总长恒 420，`ETH-` 之后先 32 个 hex 字符是
 *   **全部样本共用的常量前缀**（[BBA_CONST_PREFIX_HEX]，实测 130 个 token 全同），
 *   其后是各集不同的 384 个 hex 字符（192 字节）。
 * - `NBY-XMYAES<hex>|<hex>`（记为 [TokenKind.Nby]）：总长恒 139，`NBY-XMYAES` 之后
 *   96 个 hex 字符，`|` 之后 32 个 hex 字符——**尾部 32 位在全部样本里恒同**。
 *
 * 二层解密端点（`api/nby.php?url=` / `api/bba.php?url=`）确实返回 200，但响应是纯
 * base64 密文：解出 368 / 96 字节，**不含 `#EXT` / `m3u8` / `http` 任何明文线索**；
 * 用串尾 32 位、串体首尾 32 位、md5、sha1、sha256、全零等候选密钥配 ECB/CBC
 * 均未解出明文（详见 `docs/04-验证记录.md` §15.2）。⇒ **播放链未闭环**。
 *
 * 因此本类的职责被限定为「把加密串识别出来，好让上层显式提示不可播」，
 * 而不是猜解密算法：猜错会把乱码当地址交给播放器，报 `code=3003` 白等一次失败。
 *
 * ## 为什么不能直接把加密串交给播放器
 *
 * 播放器只认 `http/https`。`ETH-…` / `NBY-XMYAES…` 都没有协议前缀，交给
 * `UrlGuard` 会被判 `RejectScheme`，用户看到的是「播放地址协议不受支持」——
 * 这句话对用户毫无意义（他并没有输入地址）。故必须先识别、再给可行动提示。
 *
 * 该类**没有平台依赖**，留在 `commonMain` 供 iOS/鸿蒙复用。
 */
object Ap2233PlayParser {

    /** BBA 系加密串前缀 */
    const val PREFIX_BBA = "ETH-"

    /** NBY 系加密串前缀 */
    const val PREFIX_NBY = "NBY-XMYAES"

    /** NBY 系串体与尾部之间的分隔符 */
    const val SEP_NBY_TAIL = '|'

    /**
     * BBA 系常量前缀（hex 字符，32 个）。
     *
     * 实测 130 个 BBA token **全部**以它开头，但它**不能**当作「同一地址」的判据：
     * 同一部片第 01 集与第 02 集前缀相同、其后分叉。
     */
    const val BBA_CONST_PREFIX_HEX = "33663936373136656464613737633435"

    /** 播放串种类 */
    enum class TokenKind {
        /** `ETH-<hex>`，BBA 线路加密串 */
        Bba,

        /** `NBY-XMYAES<hex>|<hex>`，NBY 线路加密串 */
        Nby,

        /** 已经是明文 `http/https` 地址（该源当前样本里没有，保留以便上游恢复直链） */
        Url,

        /** 认不出来——既不是已知加密串也不是 http(s) 地址 */
        Unknown,
    }

    /**
     * 单集播放串的分类统计。
     *
     * 用于把「上游给了什么」量化下来：样本里 `unknown` 恒为 0 是重要事实——
     * 一旦线上出现新形态，这里会立刻体现，而不是被静默当成 `Unknown` 交给播放器。
     */
    data class Scan(
        val total: Int,
        val bba: Int,
        val nby: Int,
        val url: Int,
        val unknown: Int,
    )

    /** 识别单集播放串的种类（按前缀判定，不做解码） */
    fun kindOf(token: String): TokenKind {
        val t = token.trim()
        return when {
            t.startsWith("http://", ignoreCase = true) ||
                t.startsWith("https://", ignoreCase = true) -> TokenKind.Url
            t.startsWith(PREFIX_BBA) -> TokenKind.Bba
            t.startsWith(PREFIX_NBY) -> TokenKind.Nby
            else -> TokenKind.Unknown
        }
    }

    /**
     * 取加密串里参与解密的「串体」hex（去掉已知前缀与 NBY 尾部）。
     *
     * 认不出来返回空串。BBA 额外剥掉 [BBA_CONST_PREFIX_HEX]；NBY 剥掉 `|` 及其后的尾部。
     */
    fun bodyHexOf(token: String): String {
        val t = token.trim()
        return when (kindOf(t)) {
            TokenKind.Bba -> {
                val rest = t.removePrefix(PREFIX_BBA)
                if (rest.startsWith(BBA_CONST_PREFIX_HEX)) {
                    rest.removePrefix(BBA_CONST_PREFIX_HEX)
                } else {
                    rest
                }
            }
            TokenKind.Nby -> {
                val rest = t.removePrefix(PREFIX_NBY)
                val bar = rest.indexOf(SEP_NBY_TAIL)
                if (bar < 0) rest else rest.substring(0, bar)
            }
            TokenKind.Url, TokenKind.Unknown -> ""
        }
    }

    /**
     * 取 NBY 系串体之后、`|` 分隔的尾部 hex。
     *
     * 该尾部在全部样本里恒同，**不是**每集独立的密钥；BBA 系与明文地址没有这一段，
     * 返回空串。
     */
    fun tailHexOf(token: String): String {
        val t = token.trim()
        if (kindOf(t) != TokenKind.Nby) return ""
        val rest = t.removePrefix(PREFIX_NBY)
        val bar = rest.indexOf(SEP_NBY_TAIL)
        return if (bar < 0) "" else rest.substring(bar + 1)
    }

    /**
     * 该播放串**是否值得交给播放器**。
     *
     * 只有 [TokenKind.Url] 值得。加密串与未知形态一律不交——见类注释的 `code=3003` 说明。
     */
    fun isPlayable(token: String): Boolean = kindOf(token) == TokenKind.Url

    /**
     * 不可播时的可行动提示；可播返回 `null`。
     *
     * 文案按「用户能做什么」写：加密串无法在 App 内解出，只能换源或换线路。
     */
    fun gapNoticeOf(token: String): String? = when (kindOf(token)) {
        TokenKind.Url -> null
        TokenKind.Bba, TokenKind.Nby ->
            "该线路地址为加密串，App 内无法解出媒体地址。请换一条线路或换源播放"
        TokenKind.Unknown ->
            "该线路地址无法识别，请换一条线路或换源播放"
    }

    /**
     * 统计 `vod_play_url` 里的全部播放串。
     *
     * `vod_play_url` 的既有格式：线路之间 `$$$` 分隔，线路内各集 `#` 分隔，每集
     * `集名$播放串`。本函数只关心 `$` 之后的播放串——集名里理论上也可能含 `$`，
     * 故按**最后一个** `$` 切分（与 `SourceRepository.fetchAltPlayUrl` 的
     * `indexOf('$')` 不同，那里用的是第一个；两种写法在本源样本上等价，因为集名
     * 都不含 `$`，此处取更稳的一种）。
     */
    fun scan(playUrl: String): Scan {
        var total = 0
        var bba = 0
        var nby = 0
        var url = 0
        var unknown = 0
        for (line in playUrl.split("$$$")) {
            for (ep in line.split("#")) {
                if (ep.isEmpty()) continue
                val cut = ep.lastIndexOf('$')
                val token = if (cut < 0) ep else ep.substring(cut + 1)
                if (token.isEmpty()) continue
                total++
                when (kindOf(token)) {
                    TokenKind.Bba -> bba++
                    TokenKind.Nby -> nby++
                    TokenKind.Url -> url++
                    TokenKind.Unknown -> unknown++
                }
            }
        }
        return Scan(total = total, bba = bba, nby = nby, url = url, unknown = unknown)
    }
}
