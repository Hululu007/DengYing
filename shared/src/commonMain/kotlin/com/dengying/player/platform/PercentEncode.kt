package com.dengying.player.platform

/**
 * 百分号编码（KMP P5-1）
 *
 * ## 为什么放在 commonMain 用纯 Kotlin 实现
 *
 * 与 [percentDecode] 同一条理由：这是**纯计算**，不依赖任何平台服务。
 * 但这里还多一条更硬的理由 —— iOS 的 `addingPercentEncoding(withAllowedCharacters:)`
 * 与 `URLEncoder` 的保留字符集**不一致**，[UrlCodec] 的 Kdoc 已预警过：
 *
 *  | 字符 | `URLEncoder.encode(s,"UTF-8")` | `addingPercentEncoding(.alphanumerics)` |
 *  |---|---|---|
 *  | `*` | 原样保留 | 编成 `%2A` |
 *  | `~` | 编成 `%7E` | 原样保留 |
 *  | `+` | 编成 `%2B` | 编成 `%2B`（一致） |
 *
 * 直接用 Foundation 的 API 会让两端在 `*` 与 `~` 上产出不同字节，而服务端签名
 * 依赖这些字节。故 iOS actual 不调 Foundation，改为调用这里的纯 Kotlin 实现 ——
 * 一份代码、两端同源，且能在 `:shared:jvmTest` 里与 JVM 的 `URLEncoder` 逐字节比对。
 *
 * ## 语义基准
 *
 * 严格按 JDK `java.net.URLEncoder.encode(s, "UTF-8")` 的规则：
 *  - 字母 `A-Z a-z`、数字 `0-9`、以及 `.` `-` `*` `_` **原样保留**
 *  - 空格：表单模式编成 `+`，URL 模式编成 `%20`
 *  - 其余字符先取其 UTF-8 字节，每个字节编成 `%XX`（**大写**十六进制）
 *
 * @param spaceAsPlus `true` = 表单编码（空格→`+`，对应 [UrlCodec.encodeForm]）；
 *   `false` = URL 编码（空格→`%20`，对应 [UrlCodec.encode]）
 */
fun percentEncode(s: String, spaceAsPlus: Boolean): String {
    if (s.isEmpty()) return s
    val sb = StringBuilder(s.length + 8)
    for (raw in s.encodeToByteArray()) {
        val c = raw.toInt() and 0xFF
        when {
            c == 0x20 -> sb.append(if (spaceAsPlus) '+' else "%20")
            c in 'a'.code..'z'.code -> sb.append(c.toChar())
            c in 'A'.code..'Z'.code -> sb.append(c.toChar())
            c in '0'.code..'9'.code -> sb.append(c.toChar())
            c == '.'.code || c == '-'.code || c == '*'.code || c == '_'.code -> sb.append(c.toChar())
            else -> {
                sb.append('%')
                sb.append(HEX_UPPER[c ushr 4])
                sb.append(HEX_UPPER[c and 0x0F])
            }
        }
    }
    return sb.toString()
}

/** 大写十六进制表；JDK `URLEncoder` 输出大写，故不能复用 `Digest.toHex`（那个是小写） */
private val HEX_UPPER = "0123456789ABCDEF".toCharArray()
