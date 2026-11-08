package com.dengying.player.platform

import cnames.structs.OH_CryptoSymCipher
import cnames.structs.OH_CryptoSymCipherParams
import cnames.structs.OH_CryptoSymKey
import cnames.structs.OH_CryptoSymKeyGenerator
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CryptoArchitectureKit.CryptoCommonApi.CRYPTO_DECRYPT_MODE
import platform.CryptoArchitectureKit.CryptoCommonApi.CRYPTO_ENCRYPT_MODE
import platform.CryptoArchitectureKit.CryptoCommonApi.Crypto_DataBlob
import platform.CryptoArchitectureKit.CryptoSymCipherApi.CRYPTO_IV_DATABLOB
import platform.CryptoArchitectureKit.CryptoSymCipherApi.OH_CryptoSymCipher_Create
import platform.CryptoArchitectureKit.CryptoSymCipherApi.OH_CryptoSymCipher_Destroy
import platform.CryptoArchitectureKit.CryptoSymCipherApi.OH_CryptoSymCipher_Final
import platform.CryptoArchitectureKit.CryptoSymCipherApi.OH_CryptoSymCipher_Init
import platform.CryptoArchitectureKit.CryptoSymCipherApi.OH_CryptoSymCipherParams_Create
import platform.CryptoArchitectureKit.CryptoSymCipherApi.OH_CryptoSymCipherParams_Destroy
import platform.CryptoArchitectureKit.CryptoSymCipherApi.OH_CryptoSymCipherParams_SetParam
import platform.CryptoArchitectureKit.CryptoSymKeyApi.OH_CryptoSymKeyGenerator_Convert
import platform.CryptoArchitectureKit.CryptoSymKeyApi.OH_CryptoSymKeyGenerator_Create
import platform.CryptoArchitectureKit.CryptoSymKeyApi.OH_CryptoSymKeyGenerator_Destroy
import platform.CryptoArchitectureKit.CryptoSymKeyApi.OH_CryptoSymKey_Destroy

/**
 * [AesCipher] 的 OHOS 实现
 *
 * 【选型：CryptoArchitectureKit 的 OH_CryptoSymCipher（cinterop 真实现，已实测编通）】
 *
 * ## 与 JVM actual 的逐条对齐
 * - **密钥截断**：JVM 用 `SecretKeySpec` 取前 `bits/8` 字节（协议要求，非容错）。
 *   这里在 [prepKey] 里同样 `copyOf(need)`，行为一致。
 * - **密钥不足**：JVM `require` 抛 `IllegalArgumentException`。OHOS 侧同样抛
 *   `IllegalArgumentException`（见 [prepKey]），不用平台返回码静默降级——
 *   静默降级会让「协议配错密钥长度」变成「拿到错误密文」，排查成本高得多。
 * - **填充**：JVM 写 `PKCS5Padding`；OHOS 的算法串写 `PKCS7`。**这不是差异**——
 *   AES 块长 16 字节下 PKCS5 与 PKCS7 等价（JVM 的 `PKCS5Padding` 在 16 字节块上
 *   实为 PKCS7）。commonMain 的 Kdoc 已写明这个等价关系。
 * - **算法名**：OHOS 用 `"AES128|CBC|PKCS7"` 这种组合串，keyBits 决定选 128/256。
 *
 * ## 失败口径
 * 任一步失败返回空数组（不抛）。这与 JVM 侧「初始化失败抛异常」不同，但调用点
 * （`JuzhiAdapter` / `GuapiAdapter`）拿到空数组后同样会走到「源不可用」分支。
 */
@OptIn(ExperimentalForeignApi::class)
actual object AesCipher {

    private fun prepKey(bits: Int, keyBytes: ByteArray): ByteArray {
        val need = bits / 8
        require(keyBytes.size >= need) {
            "AES-$bits 需要至少 $need 字节密钥，实际 ${keyBytes.size}"
        }
        return if (keyBytes.size == need) keyBytes else keyBytes.copyOf(need)
    }

    private fun algoName(bits: Int) = if (bits >= 256) "AES256" else "AES128"

    actual fun cbcEncrypt(
        keyBits: Int,
        keyBytes: ByteArray,
        ivBytes: ByteArray,
        plain: ByteArray,
    ): ByteArray = run(CRYPTO_ENCRYPT_MODE, algoName(keyBits) + "|CBC|PKCS7",
        prepKey(keyBits, keyBytes), ivBytes, plain)

    actual fun cbcDecrypt(
        keyBits: Int,
        keyBytes: ByteArray,
        ivBytes: ByteArray,
        cipherBytes: ByteArray,
    ): ByteArray = run(CRYPTO_DECRYPT_MODE, algoName(keyBits) + "|CBC|PKCS7",
        prepKey(keyBits, keyBytes), ivBytes, cipherBytes)

    actual fun ecbEncrypt(keyBits: Int, keyBytes: ByteArray, plain: ByteArray): ByteArray =
        run(CRYPTO_ENCRYPT_MODE, algoName(keyBits) + "|ECB|PKCS7",
            prepKey(keyBits, keyBytes), null, plain)

    actual fun ecbDecrypt(keyBits: Int, keyBytes: ByteArray, cipherBytes: ByteArray): ByteArray =
        run(CRYPTO_DECRYPT_MODE, algoName(keyBits) + "|ECB|PKCS7",
            prepKey(keyBits, keyBytes), null, cipherBytes)

    private fun run(
        mode: platform.CryptoArchitectureKit.CryptoCommonApi.Crypto_CipherMode,
        algo: String,
        key: ByteArray,
        iv: ByteArray?,
        data: ByteArray,
    ): ByteArray = memScoped {
        val genVar = alloc<CPointerVar<OH_CryptoSymKeyGenerator>>()
        if (OH_CryptoSymKeyGenerator_Create(algo.substringBefore("|"), genVar.ptr) != 0u) {
            return@memScoped ByteArray(0)
        }
        val generator = genVar.value ?: return@memScoped ByteArray(0)
        val symKey: kotlinx.cinterop.CPointer<OH_CryptoSymKey>?
        try {
            val keyBlob = alloc<Crypto_DataBlob>()
            keyBlob.data = key.pin().addressOf(0).reinterpret()
            keyBlob.len = key.size.convert()
            val keyVar = alloc<CPointerVar<OH_CryptoSymKey>>()
            if (OH_CryptoSymKeyGenerator_Convert(generator, keyBlob.ptr, keyVar.ptr) != 0u) {
                return@memScoped ByteArray(0)
            }
            symKey = keyVar.value ?: return@memScoped ByteArray(0)
        } finally {
            OH_CryptoSymKeyGenerator_Destroy(generator)
        }
        try {
            val paramsVar = alloc<CPointerVar<OH_CryptoSymCipherParams>>()
            if (OH_CryptoSymCipherParams_Create(paramsVar.ptr) != 0u) return@memScoped ByteArray(0)
            val params = paramsVar.value ?: return@memScoped ByteArray(0)
            try {
                if (iv != null) {
                    val ivBlob = alloc<Crypto_DataBlob>()
                    ivBlob.data = iv.pin().addressOf(0).reinterpret()
                    ivBlob.len = iv.size.convert()
                    OH_CryptoSymCipherParams_SetParam(params, CRYPTO_IV_DATABLOB, ivBlob.ptr)
                }
                val cipherVar = alloc<CPointerVar<OH_CryptoSymCipher>>()
                if (OH_CryptoSymCipher_Create(algo, cipherVar.ptr) != 0u) return@memScoped ByteArray(0)
                val cipher = cipherVar.value ?: return@memScoped ByteArray(0)
                try {
                    OH_CryptoSymCipher_Init(cipher, mode, symKey, params)
                    val inBlob = alloc<Crypto_DataBlob>()
                    inBlob.data = data.pin().addressOf(0).reinterpret()
                    inBlob.len = data.size.convert()
                    val outBlob = alloc<Crypto_DataBlob>()
                    OH_CryptoSymCipher_Final(cipher, inBlob.ptr, outBlob.ptr)
                    val n = outBlob.len.toInt()
                    val src = outBlob.data
                    if (src == null || n == 0) ByteArray(0)
                    else ByteArray(n) { i -> src.reinterpret<ByteVar>().get(i) }
                } finally {
                    OH_CryptoSymCipher_Destroy(cipher)
                }
            } finally {
                OH_CryptoSymCipherParams_Destroy(params)
            }
        } finally {
            OH_CryptoSymKey_Destroy(symKey)
        }
    }
}
