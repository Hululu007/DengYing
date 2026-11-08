package com.dengying.player.platform

/**
 * 百分号编码（KMP P3 批 F）
 *
 * ## 为什么需要两个函数而不是一个
 *
 * `java.net.URLEncoder.encode(s, "UTF-8")` 是 **`application/x-www-form-urlencoded`**
 * 编码：空格编成 `+`。这对表单体是对的，但用在 URL 路径或查询值里就错了 ——
 * 那些位置的 `+` 不是空格，服务端会原样保留。
 *
 * 迁移前的实测分布（10 个调用点）：
 *
 *  | 形态 | 处数 | 位置 |
 *  |---|---|---|
 *  | `encode(s,"UTF-8").replace("+","%20")` | 7 | Cms/Duboku/Mino/Tv189/Web/Wencai 的 `enc`、`ui/navigation/Routes.kt:97` |
 *  | `encode(s,"UTF-8")`（不替换） | 2 | `ShanzhaAdapter.kt:50-51`、`GuapiAdapter.kt:48` |
 *
 * 两种形态都在用，所以门面必须保留两条路径。**不要**只留一条然后把另一条
 * 也改过来 —— 那是在没有需求的情况下改线上请求的字节。
 *
 * ## 为什么是 expect/actual 而不是 kotlinx 库
 *
 * 标准库 `kotlin.io.encoding.Base64` 有跨平台实现，但**没有** URL 百分号编码。
 * 而这里的语义必须与 `URLEncoder` 逐字节一致（服务端签名/校验依赖它），
 * 自己按 RFC 3986 写一份"更正确"的实现反而会引入差异 —— 例如 `URLEncoder`
 * 把 `~` 编成 `%7E`，而 RFC 3986 规定 `~` 是 unreserved 不该编码。
 *
 * 故：门面只声明语义，字节由各平台的实际实现保证。
 */
expect object UrlCodec {

    /**
     * 表单编码：空格 → `+`。等价于 `URLEncoder.encode(s, "UTF-8")`。
     *
     * 用于 `application/x-www-form-urlencoded` 请求体与历史遗留的查询值
     * （`ShanzhaAdapter` / `GuapiAdapter` 就是这么用的）。
     */
    fun encodeForm(s: String): String

    /**
     * URL 编码：空格 → `%20`。等价于
     * `URLEncoder.encode(s, "UTF-8").replace("+", "%20")`。
     *
     * 用于 URL 路径段与查询值。注意替换是安全的：字面 `+` 在编码结果里
     * 已经是 `%2B`，所以出现 `+` 只可能来自空格。
     */
    fun encode(s: String): String
}
