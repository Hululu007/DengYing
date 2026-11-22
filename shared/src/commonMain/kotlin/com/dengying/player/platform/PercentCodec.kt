package com.dengying.player.platform

/**
 * 百分号解码（KMP P4 批 pages）
 *
 * ## 为什么在 commonMain 用纯 Kotlin 实现，而不是 expect/actual
 *
 * 百分号解码是**纯计算**，没有任何平台服务依赖 —— 与 [Digest] 的 `toHex`
 * 同类，与 [Hash] 的 `md5` 不同（后者要 `MessageDigest`）。放进 commonMain
 * 可以一次写完、三个 target 共用，也便于在 `:shared:jvmTest` 里用字面量断言钉住。
 *
 * ## 语义基准是 `android.net.Uri.decode`，不是 `java.net.URLDecoder`
 *
 * 两者在**三处**上不一致，而迁移前的 `Routes.dec()` 用的是前者：
 *
 *  | 行为 | `android.net.Uri.decode` | `java.net.URLDecoder.decode` |
 *  |---|---|---|
 *  | 字面 `+` | 原样保留（`UriCodec.decode(s, false)`） | 转成空格 |
 *  | 非法转义（`%ZZ`、末尾裸 `%`） | 保留字面 `%`，不抛 | 抛 `IllegalArgumentException` |
 *  | 非法 UTF-8 字节序列 | U+FFFD 替换符 | U+FFFD 替换符（一致） |
 *
 * 把 actual 写成 `URLDecoder.decode` 是最省事的做法，但会**同时**踩前两条差异。
 * 路由值虽然由 [UrlCodec.encode] 生成、当前不会出现字面 `+` 或非法转义，
 * 但「因为输入不会触发所以不修」正是 P3 批 E 记录过的坑：等哪天真出现一个
 * 手拼的路由，差异会以最难查的形态暴露（`+` 变空格 → 签名/参数错位）。
 *
 * 故这里按 `Uri.decode` 的语义逐条实现，不抛异常、不转 `+`。
 *
 * ## 实现方式
 *
 * 先扫出一串字节（字面字符取其 UTF-8 字节，`%XX` 取该字节），
 * 最后整体 `decodeToString()`。分两步的好处是**跨段**的多字节序列也能正确解码：
 * 例如 `%E6%B5%81` 与紧跟的字面中文混排时，按字节流一次解码才不会各自崩成替换符。
 */
fun percentDecode(s: String): String {
    if (s.isEmpty()) return s
    // 上界估计：每个 %XX 至少缩成 1 字节，字面字符最多 4 字节（非 BMP）。
    // 不预分配精确大小，避免为极端输入算两遍。
    val bytes = ArrayList<Byte>(s.length)

    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val hi = hexValue(s[i + 1])
            val lo = hexValue(s[i + 2])
            if (hi >= 0 && lo >= 0) {
                bytes.add(((hi shl 4) or lo).toByte())
                i += 3
                continue
            }
            // 非法转义：保留字面 '%'，与 Uri.decode 一致（不抛、不吞后续字符）
            bytes.add('%'.code.toByte())
            i++
            continue
        }
        // 字面字符：取其 UTF-8 字节。代理对必须成对取，否则两半各自编成替换符。
        if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
            for (b in s.substring(i, i + 2).encodeToByteArray()) bytes.add(b)
            i += 2
            continue
        }
        for (b in c.toString().encodeToByteArray()) bytes.add(b)
        i++
    }

    return bytes.toByteArray().decodeToString()
}

/**
 * 十六进制字符转数值；非十六进制返回 -1。
 *
 * 只认 ASCII `0-9a-fA-F` —— 不用 `Character.digit`，那个对全角/其它文字的数字
 * 也返回数值，会让 `%ＦＦ` 这种明显非法的输入被"宽容"解码。
 */
private fun hexValue(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}
