package com.dengying.player.platform

import cnames.structs.OH_CryptoDigest
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.value
import platform.CryptoArchitectureKit.CryptoCommonApi.Crypto_DataBlob
import platform.CryptoArchitectureKit.CryptoDigestApi.OH_CryptoDigest_Create
import platform.CryptoArchitectureKit.CryptoDigestApi.OH_CryptoDigest_Final
import platform.CryptoArchitectureKit.CryptoDigestApi.OH_CryptoDigest_Update
import platform.CryptoArchitectureKit.CryptoDigestApi.OH_DigestCrypto_Destroy

/**
 * [Hash] 的 OHOS 实现
 *
 * 【选型：CryptoArchitectureKit 的 OH_CryptoDigest（cinterop 真实现，已实测编通）】
 *
 * 为什么不用纯 Kotlin 自己写 MD5/SHA-1/SHA-256：
 *  - 三者都是标准算法，自己写等于新增一份需要金样本长期守护的密码学代码，
 *    而平台已经提供经过认证的硬件/系统实现。
 *  - 但**必须验证**：算法名字符串是平台约定（`"MD5"` / `"SHA1"` / `"SHA256"`），
 *    与 JDK 的 `MessageDigest` 命名（`"MD5"` / `"SHA-1"` / `"SHA-256"`）**不同名**。
 *    这里按 OHOS 文档写，并在 RESULTS 里标记为「需真机对拍」。
 *
 * 调用链：`OH_CryptoDigest_Create` → `Update` → `Final` → 读 DataBlob → `Destroy`。
 * 任何一步失败都返回空数组，与 JVM actual 抛异常外的失败口径保持一致。
 */
@OptIn(ExperimentalForeignApi::class)
actual object Hash {

    actual fun digest(alg: HashAlg, bytes: ByteArray): ByteArray {
        val algoName = when (alg) {
            HashAlg.Md5 -> "MD5"
            HashAlg.Sha1 -> "SHA1"
            HashAlg.Sha256 -> "SHA256"
        }
        return memScoped {
            val ctxVar = alloc<CPointerVar<OH_CryptoDigest>>()
            if (OH_CryptoDigest_Create(algoName, ctxVar.ptr) != 0u) return@memScoped ByteArray(0)
            val ctx = ctxVar.value ?: return@memScoped ByteArray(0)
            try {
                if (bytes.isNotEmpty()) {
                    val inBlob = alloc<Crypto_DataBlob>()
                    inBlob.data = bytes.pin().addressOf(0).reinterpret()
                    inBlob.len = bytes.size.convert()
                    if (OH_CryptoDigest_Update(ctx, inBlob.ptr) != 0u) return@memScoped ByteArray(0)
                }
                val outBlob = alloc<Crypto_DataBlob>()
                if (OH_CryptoDigest_Final(ctx, outBlob.ptr) != 0u) return@memScoped ByteArray(0)
                val n = outBlob.len.toInt()
                val src = outBlob.data
                if (src == null || n == 0) ByteArray(0)
                else ByteArray(n) { i -> src.reinterpret<ByteVar>().get(i) }
            } finally {
                OH_DigestCrypto_Destroy(ctx)
            }
        }
    }
}
