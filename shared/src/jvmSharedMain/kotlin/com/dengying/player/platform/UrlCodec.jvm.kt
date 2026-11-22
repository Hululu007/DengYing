package com.dengying.player.platform

import java.net.URLEncoder

/**
 * [UrlCodec] 的 JVM 实现（Android 与 JVM 桌面共用）
 *
 * 直接转发到 `java.net.URLEncoder` —— 这是迁移前唯一在用的实现，
 * 所以 Android 侧行为逐字节不变。
 *
 * iOS 侧（P5）需要另写 actual：Foundation 的
 * `addingPercentEncoding(withAllowedCharacters:)` 与 `URLEncoder` 的保留字符集
 * **不完全一致**（`URLEncoder` 额外编码 `~`，且对 `*` 的处理不同），
 * 届时必须用金样本钉住字节，不能想当然地认为"都是百分号编码所以一样"。
 */
actual object UrlCodec {

    actual fun encodeForm(s: String): String = URLEncoder.encode(s, "UTF-8")

    actual fun encode(s: String): String = encodeForm(s).replace("+", "%20")
}
