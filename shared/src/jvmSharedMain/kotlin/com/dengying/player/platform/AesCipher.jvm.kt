package com.dengying.player.platform

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * [AesCipher] 的 JVM 实现（androidMain 与 jvmMain 共用）
 *
 * 逐字保留了原 `data/source/crypto/Aes.kt` 的行为：
 *  - transformation 固定 `AES/{CBC,ECB}/PKCS5Padding`
 *  - 密钥按 [bits] 截断到 16/32 字节（协议要求，不是容错）
 *  - 密钥不足时抛 `IllegalArgumentException`（`require`），与原来一致
 */
actual object AesCipher {

    private fun keySpec(bits: Int, keyBytes: ByteArray): SecretKeySpec {
        val need = bits / 8
        require(keyBytes.size >= need) {
            "AES-$bits 需要至少 $need 字节密钥，实际 ${keyBytes.size}"
        }
        // 只取前 need 字节：部分源的密钥是 32 字节但协议只用前 16 字节
        val trimmed = if (keyBytes.size == need) keyBytes else keyBytes.copyOfRange(0, need)
        return SecretKeySpec(trimmed, "AES")
    }

    actual fun cbcEncrypt(
        keyBits: Int,
        keyBytes: ByteArray,
        ivBytes: ByteArray,
        plain: ByteArray,
    ): ByteArray {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, keySpec(keyBits, keyBytes), IvParameterSpec(ivBytes))
        return c.doFinal(plain)
    }

    actual fun cbcDecrypt(
        keyBits: Int,
        keyBytes: ByteArray,
        ivBytes: ByteArray,
        cipherBytes: ByteArray,
    ): ByteArray {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, keySpec(keyBits, keyBytes), IvParameterSpec(ivBytes))
        return c.doFinal(cipherBytes)
    }

    actual fun ecbEncrypt(keyBits: Int, keyBytes: ByteArray, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, keySpec(keyBits, keyBytes))
        return c.doFinal(plain)
    }

    actual fun ecbDecrypt(keyBits: Int, keyBytes: ByteArray, cipherBytes: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, keySpec(keyBits, keyBytes))
        return c.doFinal(cipherBytes)
    }
}
