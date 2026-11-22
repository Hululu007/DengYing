package com.dengying.player.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

/**
 * [Hash] 的 iOS 实现（KMP P5-1）
 *
 * 走 `CommonCrypto` 的 C API（`CC_MD5` / `CC_SHA1` / `CC_SHA256`）——
 * 与 JVM 侧 `java.security.MessageDigest` 一一对应，且是系统自带，不引第三方库
 * （与 SEC-001「运行时依赖清单为空」的取舍一致）。
 *
 * 【为什么不用 `CryptoKit`】那是 Swift 专用框架，Kotlin/Native 的 cinterop 拿不到；
 * `CommonCrypto` 是纯 C 头文件，可直接绑定。
 *
 * 【空输入的边界】`usePinned { addressOf(0) }` 对空数组非法，故空输入单独走
 * `null` 指针 + 长度 0 的分支——`CC_*` 系列允许 `data = NULL` 当且仅当 `len = 0`。
 */
@OptIn(ExperimentalForeignApi::class)
actual object Hash {

    actual fun digest(alg: HashAlg, bytes: ByteArray): ByteArray {
        val outLen = when (alg) {
            HashAlg.Md5 -> CC_MD5_DIGEST_LENGTH
            HashAlg.Sha1 -> CC_SHA1_DIGEST_LENGTH
            HashAlg.Sha256 -> CC_SHA256_DIGEST_LENGTH
        }
        return memScoped {
            val out = allocArray<UByteVar>(outLen)
            if (bytes.isEmpty()) {
                when (alg) {
                    HashAlg.Md5 -> CC_MD5(null, 0u, out)
                    HashAlg.Sha1 -> CC_SHA1(null, 0u, out)
                    HashAlg.Sha256 -> CC_SHA256(null, 0u, out)
                }
            } else {
                bytes.usePinned { pinned ->
                    val p = pinned.addressOf(0)
                    val n = bytes.size.toUInt()
                    when (alg) {
                        HashAlg.Md5 -> CC_MD5(p, n, out)
                        HashAlg.Sha1 -> CC_SHA1(p, n, out)
                        HashAlg.Sha256 -> CC_SHA256(p, n, out)
                    }
                }
            }
            ByteArray(outLen) { out[it].toByte() }
        }
    }
}
