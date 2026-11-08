package com.dengying.player.platform

import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * [RsaCipher] 的 JVM 实现（Main 与 jvmMain 共用）
 *
 * 逐字保留原 `data/source/crypto/Rsa.kt` 的行为：
 *  - `KeyFactory.getInstance("RSA")` + `X509EncodedKeySpec`（公钥）/ `PKCS8EncodedKeySpec`（私钥）
 *  - 填充固定 `RSA/ECB/PKCS1Padding`（v1.5；源站按此验签，换 OAEP 全部失败）
 *
 * 【为什么不需要 BouncyCastle】源里给的密钥是 DER 的 base64，
 * X.509 SubjectPublicKeyInfo 与 PKCS#8 正是 JDK 的两种 EncodedKeySpec 直接对应的结构。
 */
actual object RsaCipher {

    private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"

    actual fun encrypt(publicKeyDer: ByteArray, plain: ByteArray): ByteArray {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key)
        return c.doFinal(plain)
    }

    actual fun decrypt(privateKeyDer: ByteArray, cipherBytes: ByteArray): ByteArray {
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKeyDer))
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key)
        return c.doFinal(cipherBytes)
    }

    /**
     * PKCS#1 v1.5 的明文上限 = 密钥字节数 − 11（11 字节是填充开销）。
     *
     * 从公钥本身量出模长，而不是写死 117——山楂源是 1024 位（128 字节 → 117），
     * 但别的源可能是 2048 位，写死会在换源时静默算错分块。
     */
    actual fun maxPlainChunk(publicKeyDer: ByteArray): Int {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
        // 用安全转换而非 as：非 RSA 密钥（不该出现）应给出明确错误，而不是 ClassCastException
        val rsa = key as? RSAPublicKey
            ?: error("maxPlainChunk 需要 RSA 公钥，实际是 ${key.algorithm}")
        val keyBytes = (rsa.modulus.bitLength() + 7) / 8
        return keyBytes - 11
    }
}
