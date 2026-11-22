package com.dengying.player.data.source.crypto

import com.dengying.player.platform.AesCipher

/**
 * 对称加密原语（AES-128/256 · CBC/ECB · PKCS7）
 *
 * 用途：桔子源请求体 AES-256-ECB、publicParams 头 AES-128-CBC；瓜皮源表单 AES-128-CBC。
 *
 * 【P2 迁移】真正的加解密下沉到 `platform.AesCipher`（expect/actual）：
 * Android/JVM 侧的 actual 就是原来的 `javax.crypto` 代码，逐字未改。
 * 本对象只保留**协议字面量到密钥位数的换算**与 hex 便捷方法，属于与平台无关的部分。
 *
 * 说明：`alg` 参数的取值保持原工程的 'AES128' / 'AES256' 字面量，
 * 以便调用点与协议文档逐条对得上，减少移植时的对照成本。
 */
object Aes {

    /** 'AES128' → 128 位，'AES256' → 256 位（取 '256' 子串判定） */
    private fun bitsOf(alg: String): Int = if (alg.contains("256")) 256 else 128

    /** AES-CBC 加密 → 字节 */
    fun cbcEncrypt(alg: String, keyBytes: ByteArray, ivBytes: ByteArray, plain: ByteArray): ByteArray =
        AesCipher.cbcEncrypt(bitsOf(alg), keyBytes, ivBytes, plain)

    /** AES-CBC 解密 → 字节 */
    fun cbcDecrypt(alg: String, keyBytes: ByteArray, ivBytes: ByteArray, cipherBytes: ByteArray): ByteArray =
        AesCipher.cbcDecrypt(bitsOf(alg), keyBytes, ivBytes, cipherBytes)

    /** AES-ECB 加密 → 字节 */
    fun ecbEncrypt(alg: String, keyBytes: ByteArray, plain: ByteArray): ByteArray =
        AesCipher.ecbEncrypt(bitsOf(alg), keyBytes, plain)

    /** AES-ECB 解密 → 字节 */
    fun ecbDecrypt(alg: String, keyBytes: ByteArray, cipherBytes: ByteArray): ByteArray =
        AesCipher.ecbDecrypt(bitsOf(alg), keyBytes, cipherBytes)

    /** AES-CBC 加密 → 小写 hex（桔子 publicParams 头用的形态） */
    fun cbcHex(alg: String, keyText: String, ivText: String, plainText: String): String =
        Digest.toHex(
            cbcEncrypt(
                alg,
                Digest.utf8Bytes(keyText),
                Digest.utf8Bytes(ivText),
                Digest.utf8Bytes(plainText),
            )
        )

    /** 小写 hex → 字节（失败返回空数组） */
    fun hexToBytesSafe(hex: String): ByteArray = Digest.fromHex(hex)
}
