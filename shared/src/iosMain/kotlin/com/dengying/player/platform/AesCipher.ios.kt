package com.dengying.player.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess

/**
 * [AesCipher] 的 iOS 实现（KMP P5-1）
 *
 * 走 `CommonCrypto` 的 `CCCrypt`：一个函数覆盖 CBC/ECB 两种模式，
 * 与 JVM 侧 `Cipher.getInstance("AES/{CBC,ECB}/PKCS5Padding")` 的语义一一对应。
 *
 * ## 三个必须逐条对齐的点
 *
 * 1. **填充是 PKCS7**：`kCCOptionPKCS7Padding`。AES 块长固定 16 字节，
 *    JDK 的 `PKCS5Padding` 在 16 字节块上就是 PKCS7，两端产出完全一致。
 * 2. **密钥截断**：`keyBits / 8` 字节，超出部分只取前 N —— 协议要求（见 [AesCipher] Kdoc）。
 *    不足时抛 `IllegalArgumentException`，与 JVM 的 `require` 一致。
 * 3. **ECB 无 IV**：`kCCOptionECBMode` 时传 `null`，CBC 必须传 16 字节。
 *
 * 【输出缓冲为什么是 `输入 + 16`】PKCS7 在最坏情况下（输入已是块整数倍）
 * 要补满一整块，故上限是 `size + BLOCK`。`CCCrypt` 不做预分配，缓冲区由调用方给。
 *
 * **运行时未验证（需 macOS）**：本文件只通过 Windows 上的 klib 编译验证，
 * 实际加解密结果需在 macOS/真机上与 JVM 侧对拍（见 docs/04 §16.7）。
 */
@OptIn(ExperimentalForeignApi::class)
actual object AesCipher {

    private const val BLOCK = 16

    actual fun cbcEncrypt(
        keyBits: Int,
        keyBytes: ByteArray,
        ivBytes: ByteArray,
        plain: ByteArray,
    ): ByteArray = crypt(encrypt = true, ecb = false, keyBits, keyBytes, ivBytes, plain)

    actual fun cbcDecrypt(
        keyBits: Int,
        keyBytes: ByteArray,
        ivBytes: ByteArray,
        cipherBytes: ByteArray,
    ): ByteArray = crypt(encrypt = false, ecb = false, keyBits, keyBytes, ivBytes, cipherBytes)

    actual fun ecbEncrypt(keyBits: Int, keyBytes: ByteArray, plain: ByteArray): ByteArray =
        crypt(encrypt = true, ecb = true, keyBits, keyBytes, ByteArray(0), plain)

    actual fun ecbDecrypt(keyBits: Int, keyBytes: ByteArray, cipherBytes: ByteArray): ByteArray =
        crypt(encrypt = false, ecb = true, keyBits, keyBytes, ByteArray(0), cipherBytes)

    private fun crypt(
        encrypt: Boolean,
        ecb: Boolean,
        keyBits: Int,
        keyBytes: ByteArray,
        ivBytes: ByteArray,
        input: ByteArray,
    ): ByteArray {
        val need = keyBits / 8
        require(keyBytes.size >= need) {
            "AES-$keyBits 需要至少 $need 字节密钥，实际 ${keyBytes.size}"
        }
        val key = if (keyBytes.size == need) keyBytes else keyBytes.copyOfRange(0, need)
        if (!ecb) {
            require(ivBytes.size == BLOCK) { "AES-CBC 的 IV 必须 $BLOCK 字节，实际 ${ivBytes.size}" }
        }
        val outCap = input.size + BLOCK

        return memScoped {
            val out = allocArray<UByteVar>(outCap)
            val moved = alloc<ULongVar>()
            moved.value = 0uL
            // IV 先拷进本作用域的 C 数组：直接 usePinned 嵌套会与 key/input 的 pin 冲突
            val iv = if (ecb) {
                null
            } else {
                allocArray<UByteVar>(BLOCK).also { arr ->
                    for (i in 0 until BLOCK) arr[i] = ivBytes[i].toUByte()
                }
            }
            val op = if (encrypt) kCCEncrypt else kCCDecrypt
            val options = if (ecb) {
                kCCOptionPKCS7Padding or kCCOptionECBMode
            } else {
                kCCOptionPKCS7Padding
            }

            val status: Int = key.usePinned { keyPin ->
                val keyPtr = keyPin.addressOf(0)
                val keyLen = key.size.toULong()
                if (input.isEmpty()) {
                    CCCrypt(
                        op.toUInt(), kCCAlgorithmAES.toUInt(), options.toUInt(),
                        keyPtr, keyLen, iv,
                        null, 0u,
                        out, outCap.toULong(), moved.ptr,
                    )
                } else {
                    input.usePinned { inPin ->
                        CCCrypt(
                            op.toUInt(), kCCAlgorithmAES.toUInt(), options.toUInt(),
                            keyPtr, keyLen, iv,
                            inPin.addressOf(0), input.size.toULong(),
                            out, outCap.toULong(), moved.ptr,
                        )
                    }
                }
            }

            require(status == kCCSuccess.toInt()) { "AES 运算失败，CCCrypt status=$status" }
            ByteArray(moved.value.toInt()) { out[it].toByte() }
        }
    }
}
