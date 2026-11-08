package com.dengying.player.data.source.crypto

import com.dengying.player.platform.Hash
import com.dengying.player.platform.HashAlg
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 摘要与编码原语（D / SHA1 / Hex / Base64）
 *
 * 来源需求：文才源的签名算法 `SHA1((...))`、独播库源的 10 字符组反转 + Base64 编码。
 * 这两个算法已在本机用独立实现复算并与服务端对齐。
 *
 * 【迁移史（P2）】原实现直接用 `java.security.MessageDigest` 与 `java.util.Base64`。
 * 这两个类在 Kotlin/Native 上不存在，故 P2 把**算法**下沉到 `platform.Hash`
 * （expect/actual，各端用系统能力），本文件只留与平台无关的编码/十六进制转换。
 * 对外行为逐字不变：`md5Hex` / `sha1Hex` / `sha256Hex` / `base64*` 的输入输出与原来一致。
 *
 * 【依赖说明】不引第三方密码学库——与 SEC-001「运行时依赖清单为空」一致。
 */
object Digest {

    /** UTF-8 编码 */
    fun utf8Bytes(text: String): ByteArray = text.encodeToByteArray()

    /** UTF-8 解码 */
    fun utf8String(bytes: ByteArray): String = bytes.decodeToString()

    /**
     * 字节 → 小写 hex
     *
     * 手写而不用 `ByteArray.toHexString()`：后者是 Kotlin 1.9 才进 stdlib 的实验 API，
     * 且这里是热路径（每次签名都过），保留原实现的 `StringBuilder` 版本行为更可控。
     */
    fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }

    /**
     * 小写 hex → 字节（长度为奇数或含非法字符返回空数组）
     *
     * `Character.digit` 是 Java API，换成纯 Kotlin 的取值映射，
     * 行为与原实现一致：非 hex 字符返回 -1，整体返回空数组。
     */
    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim()
        if (clean.isEmpty() || clean.length % 2 != 0) return ByteArray(0)
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = hexDigit(clean[i * 2])
            val lo = hexDigit(clean[i * 2 + 1])
            if (hi < 0 || lo < 0) return ByteArray(0)
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** 单个 hex 字符 → 数值；非法返回 -1（等价于 `Character.digit(c, 16)`） */
    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    /** MD5 摘要（小写 hex） */
    fun md5Hex(text: String): String = toHex(Hash.digest(HashAlg.Md5, utf8Bytes(text)))

    /** SHA1 摘要（小写 hex） */
    fun sha1Hex(text: String): String = toHex(Hash.digest(HashAlg.Sha1, utf8Bytes(text)))

    /** SHA256 摘要（小写 hex） */
    fun sha256Hex(text: String): String = toHex(Hash.digest(HashAlg.Sha256, utf8Bytes(text)))

    /**
     * Base64 编码（标准表，带 = 填充）
     *
     * 用 stdlib 的 [Base64] 而非 `java.util.Base64`——后者在 Kotlin/Native 上不存在。
     * 输出与 `java.util.Base64.getEncoder().encodeToString(bytes)` 逐字节一致。
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun base64Encode(bytes: ByteArray): String = Base64.encode(bytes)

    /**
     * Base64 解码为字节（非法输入返回空数组）
     *
     * 【必须自己校验，不能直接用 stdlib】`java.util.Base64.getDecoder()` 遇非法字符
     * 抛 `IllegalArgumentException`，原实现靠捕获它返回空数组；而 stdlib 的
     * `Base64.decode` 默认**静默跳过**非法字符，返回「丢了几字节的残缺结果」。
     * 两者在处理坏数据时结果不同，而调用方（如 `Aes.hexToBytesSafe`）拿到残缺密钥
     * 会让失败从「解密报错」变成「解密出垃圾」——静默且难查。故先做字符集与长度校验。
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun base64DecodeBytes(text: String): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        // 只允许标准 Base64 字符集与 = 填充；出现其他字符即视为非法
        for (c in text) {
            val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='
            if (!ok) return ByteArray(0)
        }
        return try {
            Base64.decode(text)
        } catch (e: IllegalArgumentException) {
            ByteArray(0)
        }
    }

    /** Base64 解码为 UTF-8 字符串（非法输入返回空串） */
    fun base64DecodeString(text: String): String {
        val b = base64DecodeBytes(text)
        return if (b.isEmpty()) "" else utf8String(b)
    }

    /** Base64 编码（URL 安全：+ → -、/ → _、去掉 =） */
    fun base64UrlNoPad(bytes: ByteArray): String =
        base64Encode(bytes).replace('+', '-').replace('/', '_').trimEnd('=')

    /**
     * 独播库专用：每 10 个字符一组反转，再把 `.` 还原为 `=` 后 Base64 解码。
     *
     * 该形态是服务端自定义混淆，非标准 Base64（已验证：DId → /voddetail/4570）。
     *
     * 【必须照搬的细节】反转是**按 10 字符切块后逐块反转**，不是整串反转。
     * 整串反转在长度是 10 的整数倍时碰巧一致，其余情况全错。
     */
    fun dubokuUnscramble(encoded: String): String {
        if (encoded.isEmpty()) return ""
        val sb = StringBuilder(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val end = minOf(i + 10, encoded.length)
            sb.append(encoded.substring(i, end).reversed())
            i += 10
        }
        var b64 = sb.toString().replace('.', '=')
        while (b64.length % 4 != 0) {
            b64 += "="
        }
        return base64DecodeString(b64)
    }
}
