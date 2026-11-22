package com.dengying.player.data.net

/**
 * HTTP 响应头的线格式编解码（ArkTS/C++ 桥 ↔ Kotlin）。
 *
 * 为什么是逐行 `name: value` 而不是 JSON：桥的 C++ 侧只做字符串透传，
 * 不需要引入 JSON 解析；而且 `set-cookie` 的值里带逗号（`Expires=Wed, 21 Oct...`），
 * JSON 数组/字符串同样要处理转义，纯文本更不容易在两层桥里被改坏。
 *
 * 线格式约定：
 *  - 每行一个头值：`name: value`
 *  - 同名多头（尤其 `set-cookie`）重复出现多行，**不合并、不按逗号切分**
 *  - 空行与不含冒号的行忽略
 *
 * 解析语义与 JVM/Android 实现对齐（见 `HttpTransport.jvm.kt`）：
 * 头名小写化，同名多值用 `"; "` 拼接。`HttpClient`/`BttwoAuth` 依赖这个口径
 * 从 `headers["set-cookie"]` 里抠 `nbmovie_session`。
 */
object HttpHeaderCodec {

    /** 把桥传回的响应头文本解析成头表；空/畸形输入返回空表，绝不抛异常。 */
    fun decode(text: String?): Map<String, String> {
        if (text.isNullOrEmpty()) return emptyMap()
        val collected = LinkedHashMap<String, StringBuilder>()
        for (rawLine in text.split('\n')) {
            val line = rawLine.trimEnd('\r')
            if (line.isEmpty()) continue
            val colon = line.indexOf(':')
            // 冒号在首位/缺失都不是合法头行（HTTP/2 的 :status 之类不会出现在这里）
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            if (name.isEmpty()) continue
            val value = line.substring(colon + 1).trim()
            val existing = collected[name]
            if (existing == null) {
                collected[name] = StringBuilder(value)
            } else {
                existing.append("; ").append(value)
            }
        }
        if (collected.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>(collected.size)
        for ((name, value) in collected) {
            result[name] = value.toString()
        }
        return result
    }
}
