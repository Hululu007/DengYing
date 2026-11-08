package com.dengying.player.data.source.crypto

import com.dengying.player.platform.RsaCipher

/**
 * 非对称加密原语（RSA · PKCS#1 v1.5）
 *
 * 用途：桔子源二级公钥发放与请求签名、瓜皮源表单 keys 加密与响应解密、山楂源详情体加密。
 * 密钥以 base64 体形式登记在 `core/SourceSecrets.kt`。
 *
 * 【P2 迁移】真正的加解密下沉到 `platform.RsaCipher`（expect/actual）：
 * Android/JVM 侧的 actual 就是原来的 `KeyFactory` + `javax.crypto` 代码。
 * 本对象只保留**与平台无关的部分**：PEM 头包装/剥离、base64→DER 转换、分块拼接。
 *
 * 【密钥格式】源里给的是 **DER 的 base64**（不带 PEM 头）。
 * `-----BEGIN PUBLIC KEY-----` 对应 X.509 SubjectPublicKeyInfo，
 * `-----BEGIN PRIVATE KEY-----` 对应 PKCS#8 —— 两者正好是 JDK 的两种 EncodedKeySpec，
 * 也正好是 iOS `SecKeyCreateWithData` 认的结构，故不需要 BouncyCastle。
 */
object Rsa {

    /** 把 base64 密钥体包成标准 PEM（每 64 字符换行） */
    fun wrapPem(base64Body: String, kind: String): String {
        val body = base64Body.replace(Regex("[\\r\\n\\s]"), "")
        val sb = StringBuilder(body.length + 64)
        var i = 0
        while (i < body.length) {
            sb.append(body, i, minOf(i + 64, body.length))
            if (i + 64 < body.length) sb.append('\n')
            i += 64
        }
        return "-----BEGIN $kind-----\n$sb\n-----END $kind-----"
    }

    /** 去掉 PEM 头尾与所有空白，得到裸 base64 体 */
    private fun stripPem(text: String): String =
        text.replace(Regex("-----[^-]+-----"), "")
            .replace(Regex("[\\r\\n\\s]"), "")
            .trim()

    /** PEM（或裸 base64）→ DER 字节 */
    private fun derOf(pemOrBase64: String): ByteArray =
        Digest.base64DecodeBytes(stripPem(pemOrBase64))

    /** RSA 公钥加密（PKCS#1 v1.5） */
    fun encrypt(pemOrBase64: String, plain: ByteArray): ByteArray =
        RsaCipher.encrypt(derOf(pemOrBase64), plain)

    /** RSA 私钥解密（PKCS#1 v1.5） */
    fun decrypt(pemOrBase64: String, cipherBytes: ByteArray): ByteArray =
        RsaCipher.decrypt(derOf(pemOrBase64), cipherBytes)

    /**
     * 明文超过单块上限时按块加密后拼接。
     *
     * 块大小口径：RSA-1024 单块 117 字节、RSA-2048 单块 245 字节
     * （= 模长 - 11 字节的 PKCS#1 v1.5 开销）。调用方按协议给定；
     * 需要现算时用 [RsaCipher.maxPlainChunk]。
     */
    fun encryptChunked(pemOrBase64: String, plain: ByteArray, chunk: Int): ByteArray {
        if (plain.size <= chunk) return encrypt(pemOrBase64, plain)
        val parts = ArrayList<ByteArray>()
        var total = 0
        var i = 0
        while (i < plain.size) {
            val end = minOf(i + chunk, plain.size)
            val part = encrypt(pemOrBase64, plain.copyOfRange(i, end))
            parts += part
            total += part.size
            i += chunk
        }
        return concat(parts, total)
    }

    /** 密文按块独立解密后拼接（响应体形态） */
    fun decryptChunked(pemOrBase64: String, cipherBytes: ByteArray, blockSize: Int): ByteArray {
        if (cipherBytes.size <= blockSize) return decrypt(pemOrBase64, cipherBytes)
        val parts = ArrayList<ByteArray>()
        var total = 0
        var i = 0
        while (i < cipherBytes.size) {
            val end = minOf(i + blockSize, cipherBytes.size)
            val part = decrypt(pemOrBase64, cipherBytes.copyOfRange(i, end))
            parts += part
            total += part.size
            i += blockSize
        }
        return concat(parts, total)
    }

    /** RSA 加密 → Base64（表单/字段形态） */
    fun encryptToBase64(pemOrBase64: String, plainText: String, chunk: Int): String =
        Digest.base64Encode(encryptChunked(pemOrBase64, Digest.utf8Bytes(plainText), chunk))

    /**
     * 拼接多段字节。
     *
     * 原来用 `java.io.ByteArrayOutputStream`，它在 Kotlin/Native 上没有，
     * 但这里只是「顺序拼字节」，用一次预分配 + `copyInto` 更直白且少一层包装。
     * 语义与 `ByteArrayOutputStream.toByteArray()` 完全一致。
     */
    private fun concat(parts: List<ByteArray>, total: Int): ByteArray {
        val out = ByteArray(total)
        var pos = 0
        for (p in parts) {
            p.copyInto(out, pos)
            pos += p.size
        }
        return out
    }
}
