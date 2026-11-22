package com.dengying.player.platform

/**
 * [UrlCodec] 的 iOS 实现（KMP P5-1）
 *
 * **刻意不调 Foundation，改为委托 [percentEncode]。**
 *
 * JVM 侧 actual 是 `java.net.URLEncoder`，它的保留字符集是一个**具体而封闭**的集合：
 * 只放过 `A-Z a-z 0-9 . - * _`，其余一律 `%XX`（大写十六进制），空格按模式变 `+` 或 `%20`。
 *
 * 而 Foundation 的 `addingPercentEncoding(withAllowedCharacters:)` 与它并不相同：
 *
 *  | 字符 | `URLEncoder.encode(s,"UTF-8")` | `addingPercentEncoding(.alphanumerics)` |
 *  |---|---|---|
 *  | `*` | 原样保留 | 编成 `%2A` |
 *  | `~` | 编成 `%7E` | 原样保留 |
 *
 * 跨平台层要求**逐字节等价**，且服务端签名依赖这些字节，所以 iOS 不自己实现一套，
 * 而是调用 commonMain 的纯 Kotlin [percentEncode] —— 一份代码、两端同源，
 * 且能在 `:shared:jvmTest` 里与 JVM 的 `URLEncoder` 逐字节比对。
 *
 * 【验证方式】`UrlCodecTest` 的 12 例在 `:shared:jvmTest` 跑的是 JVM actual；
 * iOS actual 目前只保证 klib 编译通过，字节等价需在 macOS 上对拍（见 docs/04 §16.7）。
 */
actual object UrlCodec {

    actual fun encodeForm(s: String): String = percentEncode(s, spaceAsPlus = true)

    actual fun encode(s: String): String = percentEncode(s, spaceAsPlus = false)
}
