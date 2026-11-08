package com.dengying.player.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyGetBlockSize
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1

/**
 * [RsaCipher] 的 iOS 实现（KMP P5-1）
 *
 * 走 `Security` 框架的 C API（`SecKeyCreateWithData` / `SecKeyCreateEncryptedData` /
 * `SecKeyCreateDecryptedData`），与 JVM 侧 `Cipher.getInstance("RSA/ECB/PKCS1Padding")`
 * 语义一一对应：填充固定 PKCS#1 v1.5（`kSecPaddingPKCS1`）。
 *
 * ## 为什么必须先做 DER 拆包
 *
 * [RsaCipher] 的入参是 **X.509 SubjectPublicKeyInfo（SPKI）** 与 **PKCS#8 PrivateKeyInfo**，
 * 而 `SecKeyCreateWithData` 对 RSA 只认 **PKCS#1**（`RSAPublicKey` / `RSAPrivateKey`）。
 * 直接把 SPKI 丢进去会得到 `errSecDecode`。故先用 [extractPkcs1] 把外层容器剥掉：
 *
 * ```
 * SPKI   = SEQ { SEQ { OID rsaEncryption, NULL }, BIT STRING { RSAPublicKey } }
 * PKCS#8 = SEQ { INTEGER 0, SEQ { OID rsaEncryption, NULL }, OCTET STRING { RSAPrivateKey } }
 * ```
 *
 * 只做「按 TLV 逐层跳过」，不解码模数/指数——模数解析交给 Security 框架，
 * 少一层自己算大数的机会。
 *
 * ## 属性字典为什么用 `null` callbacks
 *
 * `CFDictionaryCreateMutable(null, n, null, null)` 不 retain/release 键值。
 * 这里所有键值都是 `kSecAttr*` 全局常量（进程生命周期），不存在悬垂风险，
 * 也就避开了 `kCFTypeDictionaryKeyCallBacks.ptr` 在 Kotlin/Native 上取址不便的问题。
 *
 * ## 失败语义与 JVM 的差异
 *
 * JVM actual 在建 key 失败时抛 `GeneralSecurityException`；这里返回 `ByteArray(0)` / `0`。
 * 三条业务调用（Juzhi / Guapi / 其它签名源）都把空结果当失败处理，故行为等价；
 * 差异记录在 docs/04 §16.7 的裁决表里。
 *
 * **运行时未验证（需 macOS）**：本文件只通过 Windows 上的 klib 编译验证，
 * 真实加解密需在 macOS 上与 JVM 侧对拍（见 docs/04 §16.7）。
 */
@OptIn(ExperimentalForeignApi::class)
actual object RsaCipher {

    /** PKCS#1 v1.5 加密填充占用的固定开销：`0x00 || 0x02 || PS(>=8) || 0x00` = 11 字节 */
    private const val PKCS1_OVERHEAD = 11

    actual fun encrypt(publicKeyDer: ByteArray, plain: ByteArray): ByteArray {
        val key = createKey(publicKeyDer, public = true) ?: return ByteArray(0)
        try {
            val data = plain.toCFData() ?: return ByteArray(0)
            val out = SecKeyCreateEncryptedData(key, kSecKeyAlgorithmRSAEncryptionPKCS1, data, null)
            cfRelease(data)
            val result = cfDataToByteArray(out)
            cfRelease(out)
            return result
        } finally {
            cfRelease(key)
        }
    }

    actual fun decrypt(privateKeyDer: ByteArray, cipherBytes: ByteArray): ByteArray {
        val key = createKey(privateKeyDer, public = false) ?: return ByteArray(0)
        try {
            val data = cipherBytes.toCFData() ?: return ByteArray(0)
            val out = SecKeyCreateDecryptedData(key, kSecKeyAlgorithmRSAEncryptionPKCS1, data, null)
            cfRelease(data)
            val result = cfDataToByteArray(out)
            cfRelease(out)
            return result
        } finally {
            cfRelease(key)
        }
    }

    actual fun maxPlainChunk(publicKeyDer: ByteArray): Int {
        val key = createKey(publicKeyDer, public = true) ?: return 0
        try {
            return (SecKeyGetBlockSize(key).toInt() - PKCS1_OVERHEAD).coerceAtLeast(0)
        } finally {
            cfRelease(key)
        }
    }

    private fun createKey(der: ByteArray, public: Boolean): SecKeyRef? {
        val pkcs1 = extractPkcs1(der, public) ?: return null
        val cfData = pkcs1.toCFData() ?: return null
        val attrs = CFDictionaryCreateMutable(null, 2, null, null)
        if (attrs == null) {
            cfRelease(cfData)
            return null
        }
        CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionaryAddValue(
            attrs,
            kSecAttrKeyClass,
            if (public) kSecAttrKeyClassPublic else kSecAttrKeyClassPrivate,
        )
        val key = SecKeyCreateWithData(cfData, attrs as CFDictionaryRef, null)
        cfRelease(cfData)
        cfRelease(attrs)
        return key
    }

    /**
     * 剥掉 SPKI / PKCS#8 外层容器，返回 PKCS#1 的完整 DER（含自身的 `SEQ` 头）。
     *
     * 只用「读 tag + 读长度 + 跳过」三步，不校验 OID 具体值——
     * 非 RSA 的 key 会在 `SecKeyCreateWithData` 处失败，不需要在这里分辨。
     */
    internal fun extractPkcs1(der: ByteArray, public: Boolean): ByteArray? {
        val outer = tlvAt(der, 0) ?: return null
        if (outer.tag != TAG_SEQUENCE) return null
        var pos = outer.contentStart
        val end = outer.contentStart + outer.length
        if (!public) {
            // PKCS#8: 跳过 version INTEGER 与 AlgorithmIdentifier SEQUENCE
            val version = tlvAt(der, pos) ?: return null
            pos = version.contentStart + version.length
            val alg = tlvAt(der, pos) ?: return null
            pos = alg.contentStart + alg.length
            val octet = tlvAt(der, pos) ?: return null
            if (octet.tag != TAG_OCTET_STRING) return null
            return der.copyOfRange(octet.contentStart, octet.contentStart + octet.length)
        }
        // SPKI: 跳过 AlgorithmIdentifier SEQUENCE，取 BIT STRING 去掉首字节（unused bits）
        val alg = tlvAt(der, pos) ?: return null
        pos = alg.contentStart + alg.length
        val bits = tlvAt(der, pos) ?: return null
        if (bits.tag != TAG_BIT_STRING) return null
        val start = bits.contentStart + 1
        val len = bits.length - 1
        if (len <= 0 || start + len > end || start + len > der.size) return null
        return der.copyOfRange(start, start + len)
    }

    private class Tlv(val tag: Int, val contentStart: Int, val length: Int)

    /** 读一个 DER TLV 头。长度支持短形式（<128）与长形式（1~3 字节）。 */
    private fun tlvAt(b: ByteArray, at: Int): Tlv? {
        if (at + 2 > b.size) return null
        val tag = b[at].toInt() and 0xFF
        var p = at + 1
        val first = b[p].toInt() and 0xFF
        p++
        val len: Int
        if (first < 0x80) {
            len = first
        } else {
            val n = first and 0x7F
            if (n == 0 || n > 3 || p + n > b.size) return null
            var v = 0
            for (i in 0 until n) {
                v = (v shl 8) or (b[p + i].toInt() and 0xFF)
            }
            p += n
            len = v
        }
        if (p + len > b.size) return null
        return Tlv(tag, p, len)
    }

    private const val TAG_SEQUENCE = 0x30
    private const val TAG_OCTET_STRING = 0x04
    private const val TAG_BIT_STRING = 0x03
}
