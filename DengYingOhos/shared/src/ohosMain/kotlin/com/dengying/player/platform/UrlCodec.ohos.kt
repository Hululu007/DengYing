package com.dengying.player.platform

import com.dengying.player.platform.percentEncode

/**
 * [UrlCodec] 的 OHOS 实现
 *
 * 【选型：复用 commonMain 的 [percentEncode]，纯 Kotlin】
 *
 * 理由与 iOS 一致：**两端必须共用同一份编码表**。各平台原生 API 的保留字符集
 * 并不统一（`java.net.URLEncoder` 放行 `*`、把空格编成 `+`；iOS
 * `addingPercentEncoding` 相反），而这些字节会进签名——差一个字符整条链就 401。
 * 用 commonMain 的纯 Kotlin 实现可以从根上消除这个漂移。
 *
 * 语义基准 = JDK `URLEncoder`：放行 `A-Z a-z 0-9 . - * _`。
 */
actual object UrlCodec {

    actual fun encodeForm(s: String): String = percentEncode(s, spaceAsPlus = true)

    actual fun encode(s: String): String = percentEncode(s, spaceAsPlus = false)
}
