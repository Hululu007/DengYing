package com.dengying.player.data.source.adapters

import com.dengying.player.data.json.JsonX
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.JsonObject

/**
 * 蓝光4k（Api2233）**播放页**解析层 —— 纯 Kotlin，无平台依赖
 *
 * ## 与 [Ap2233PlayParser] 的分工
 *
 * [Ap2233PlayParser] 只看 API 下发的 `vod_play_url` 字符串，负责**认出加密串种类**
 * （`ETH-…` / `NBY-XMYAES…|…`）。本类看的是站点的 **macCMS 播放页 HTML**，负责把页面里
 * `var player_aaaa={…}` 这一块解出结构化字段（线路名、加密级别、地址、上下集链接、影片名、
 * 线路/集序号）。两者互补：前者回答「这一集能不能播」，后者回答「这个播放页说的是哪一集、
 * 走的哪条线路、地址要不要再做一层 macCMS 解码」。
 *
 * ## 为什么用花括号配平而不是正则
 *
 * `player_aaaa` 是嵌了 `vod_data` 子对象的 JSON，且字符串值里可能含 `{` `}`。用
 * `Regex("var player_aaaa=(\\{.*?\\})")` 会在第一个 `}`（`vod_data` 的收尾）就截断。
 * 故 [extractObject] 逐字符扫描并跳过字符串字面量（含 `\"` 转义），只在括号深度归零处收尾。
 *
 * ## macCMS 的 `encrypt` 三级语义（对齐站点前端 player.js）
 *
 * 站点 `player.js` 的 `MacPlayer.Init` 里：
 * ```
 * a.encrypt=='1' ? a.url = unescape(a.url)
 * : a.encrypt=='2' ? a.url = unescape(base64decode(a.url)) : …
 * ```
 * 即 `1` = 只做 `unescape`，`2` = 先 base64 再 `unescape`，`0` = 原样交给播放器脚本。
 * 蓝光4k 当前实测样本是 `encrypt: 0`，`1`/`2` 两条分支保留是为了该源日后切换加密级别时
 * 不用再改解析器（[decodeUrl] 已覆盖三种）。
 *
 * ## 第二层密文为什么仍然解不开（2026-10-27 补测结论）
 *
 * `player_aaaa.url` 的值形如 `NBY-XMYAES<96 hex>|<32 hex>`，`encrypt=0` 意味着站点前端
 * **本来就要交给 `/static/player/NBY.js` 去解**。但实测该脚本与 `BBA.js` 都是
 * `HTTP 200 + Content-Length: 0` 的**空文件**（同目录 `dplayer.js` 297 B、`iva.js` 1859 B
 * 等全部正常）⇒ 站点自己没发布解密器。配合 §15.2/§15.5 已排除的六条路径（二层端点非确定性、
 * `bba.php` 恒为同一段常量密文、48 字节密文穷举 AES 全败、`/api.php` 各 action 全 0 字节、
 * `player.js` 反混淆后只有 macCMS 通用框架、站点无任何 CryptoJS 引用），结论是
 * **NBY/BBA 二层密文在客户端不可解**，故 [decodeUrl] 只做 macCMS 明文级解码，**不猜 AES**。
 * 详见 `docs/04-验证记录.md` §15.5。
 *
 * ## 平台依赖
 *
 * 只用 `JsonX`（跨平台 JSON 门面）与 stdlib Base64，**无 android/java 引用**，留 `commonMain`。
 */
object Ap2233PlayPageParser {

    /** 播放页里承载播放参数的脚本变量名 */
    const val PLAYER_VAR = "var player_aaaa"

    /** `encrypt = 0`：地址原样交给播放器脚本 */
    const val ENCRYPT_NONE = 0

    /** `encrypt = 1`：地址做过 `unescape`，需还原 */
    const val ENCRYPT_UNESCAPE = 1

    /** `encrypt = 2`：地址是 base64 后再 `unescape` */
    const val ENCRYPT_BASE64 = 2

    /**
     * 一个 macCMS 播放页的播放参数。
     *
     * 字段名与站点 `player_aaaa` 的键一一对应（`url_next` 用驼峰 [urlNext]）。
     * 缺失的键一律用零值，**不抛异常**：站点模板改版时宁可拿到半份数据也不该崩。
     */
    data class PlayPage(
        /** 播放标志，实测 `play` */
        val flag: String = "",
        /** 加密级别，0/1/2，见 [decodeUrl] */
        val encrypt: Int = ENCRYPT_NONE,
        /** 原始地址（未解码），加密源里是加密串 */
        val url: String = "",
        /** 下一集地址（原始形态） */
        val urlNext: String = "",
        /** 线路名，如 `NBY` / `BBA` / `dplayer` */
        val from: String = "",
        /** 影片 id（字符串，站点原样） */
        val vodId: String = "",
        /** 影片名 */
        val vodName: String = "",
        /** 线路序号 */
        val sid: Int = 0,
        /** 集序号 */
        val nid: Int = 0,
    ) {
        /** [url] 按 [encrypt] 解码后的地址；`encrypt = 0` 时与 [url] 相同 */
        val decodedUrl: String get() = decodeUrl(url, encrypt)

        /** [urlNext] 按 [encrypt] 解码后的地址 */
        val decodedUrlNext: String get() = decodeUrl(urlNext, encrypt)
    }

    /**
     * 解析播放页 HTML，失败返回 `null`。
     *
     * 输入可以是**整页 HTML**，也可以只是 `{…}` 或 `var player_aaaa={…}` 这一块
     * （金样本就是后者的形态，测试与排障时直接喂一块很方便）。实现上先试 [extractObject]，
     * 找不到变量名时退一步把整段当 JSON 对象试解析。
     *
     * 「失败」只有两种：既没有 [PLAYER_VAR] 也整段不是合法 JSON 对象，或截出的块不是
     * 合法 JSON 对象。找到但字段缺失时返回带零值的 [PlayPage]。
     */
    fun parse(html: String?): PlayPage? {
        val text = html ?: return null
        val block = extractObject(text) ?: text.trim()
        val obj = JsonX.parseObject(block) ?: return null
        return playPageOf(obj)
    }

    /**
     * 从 HTML 里截出 `var player_aaaa={…}` 的对象文本（含首尾花括号）。
     *
     * 逐字符扫描并**跳过字符串字面量**（含 `\"` 转义），故值里出现 `{` `}` 也不会提前收尾。
     * 找不到变量名、或括号未配平（页面被截断）时返回 `null`。
     */
    fun extractObject(html: String?): String? {
        val text = html ?: return null
        val at = text.indexOf(PLAYER_VAR)
        if (at < 0) return null
        val start = text.indexOf('{', at + PLAYER_VAR.length)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
            i += 1
        }
        return null
    }

    /**
     * 按 macCMS 的 `encrypt` 语义解码地址。
     *
     * - [ENCRYPT_NONE]（及任何未知值）：原样返回
     * - [ENCRYPT_UNESCAPE]：做一次 JS `unescape` 等价解码
     * - [ENCRYPT_BASE64]：先 Base64 解出字节再按 UTF-8 转文本，最后做一次 `unescape`
     *
     * **只做这两级**（站点前端的明文级处理），不做 AES —— 理由见类注释。
     */
    fun decodeUrl(url: String, encrypt: Int): String = when (encrypt) {
        ENCRYPT_UNESCAPE -> unescape(url)
        ENCRYPT_BASE64 -> unescape(base64ToText(url))
        else -> url
    }

    /**
     * JS `unescape` 等价实现：还原 `%uXXXX`（UTF-16 码元）与 `%XX`（UTF-8 字节序列）。
     *
     * 与浏览器 `unescape` 的差异只有一处、且是刻意的：`unescape` 把每个 `%XX` 当成
     * Latin-1 单字符，而 macCMS 写入的是 **UTF-8 百分号编码**（中文地址按 UTF-8 三字节
     * 展开）。本实现把连续的 `%XX` 归组后按 UTF-8 解码，才能还原出正确中文。
     * 非法 / 截断的 `%` 序列原样保留，不抛异常。
     */
    fun unescape(value: String): String {
        if (value.indexOf('%') < 0) return value
        val sb = StringBuilder(value.length)
        val pending = ArrayList<Byte>(4)
        fun flush() {
            if (pending.isEmpty()) return
            val bytes = ByteArray(pending.size) { pending[it] }
            sb.append(bytes.decodeToString())
            pending.clear()
        }
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '%') {
                flush()
                sb.append(c)
                i += 1
                continue
            }
            // %uXXXX
            if (i + 5 < value.length && (value[i + 1] == 'u' || value[i + 1] == 'U')) {
                val hex = value.substring(i + 2, i + 6)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    flush()
                    sb.append(code.toChar())
                    i += 6
                    continue
                }
            }
            // %XX
            if (i + 2 < value.length) {
                val code = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) {
                    pending.add(code.toByte())
                    i += 3
                    continue
                }
            }
            // 非法序列：原样保留一个 %
            flush()
            sb.append(c)
            i += 1
        }
        flush()
        return sb.toString()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun base64ToText(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        return runCatching {
            Base64.decode(padBase64(trimmed)).decodeToString()
        }.getOrDefault("")
    }

    /** 补回被去掉的 `=` 填充（macCMS 前端 base64 表里含 `=`，但前缀常被裁掉） */
    private fun padBase64(value: String): String {
        val rest = value.length % 4
        return if (rest == 0) value else value + "=".repeat(4 - rest)
    }

    private fun playPageOf(obj: JsonObject): PlayPage = PlayPage(
        flag = JsonX.str(obj, "flag", ""),
        encrypt = JsonX.int(obj, "encrypt", ENCRYPT_NONE),
        url = JsonX.str(obj, "url", ""),
        urlNext = JsonX.str(obj, "url_next", ""),
        from = JsonX.str(obj, "from", ""),
        vodId = JsonX.str(obj, "id", ""),
        vodName = JsonX.str(JsonX.optObject(obj, "vod_data"), "vod_name", ""),
        sid = JsonX.int(obj, "sid", 0),
        nid = JsonX.int(obj, "nid", 0),
    )
}
