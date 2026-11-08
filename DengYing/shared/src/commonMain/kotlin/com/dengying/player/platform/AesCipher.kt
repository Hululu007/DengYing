package com.dengying.player.platform

/**
 * 对称加密（AES，KMP P2）
 *
 * 用途：桔子源请求体 AES-256-ECB、publicParams 头 AES-128-CBC；瓜皮源表单 AES-128-CBC。
 *
 * 【为什么不做成通用 `Cipher` 抽象】本项目只用 AES 的两种分组模式（CBC/ECB）与
 * PKCS5 填充。抽象成「算法名 → 变换字符串」的通用门面会把平台相关的
 * transformation 字符串泄到 `commonMain`，而 iOS 那边（`CommonCrypto`）根本
 * 不用这种字符串寻址。按用例定接口，两端实现都直白。
 *
 * 【填充是 PKCS5 还是 PKCS7】AES 的块长固定 16 字节，PKCS5 与 PKCS7 在这个块长上
 * 完全等价（PKCS5 定义在 8 字节块上，16 字节时 JDK 的 `PKCS5Padding` 实为 PKCS7）。
 * 沿用 JDK 的 `PKCS5Padding` 字样，是为了让调用点与既有协议文档对得上。
 */
expect object AesCipher {

    /**
     * AES-CBC 加密。
     *
     * @param keyBytes 密钥字节；[keyBits] 位对应的长度（128→16 字节，256→32 字节）。
     *   密钥更长时**只取前 N 字节**——部分源的密钥是 32 字节但协议只用前 16 字节，
     *   这个截断是协议要求，不是容错。
     * @param ivBytes 初始向量，长度必须等于块长 16
     */
    fun cbcEncrypt(keyBits: Int, keyBytes: ByteArray, ivBytes: ByteArray, plain: ByteArray): ByteArray

    /** AES-CBC 解密，参数含义同 [cbcEncrypt] */
    fun cbcDecrypt(keyBits: Int, keyBytes: ByteArray, ivBytes: ByteArray, cipherBytes: ByteArray): ByteArray

    /** AES-ECB 加密（无 IV） */
    fun ecbEncrypt(keyBits: Int, keyBytes: ByteArray, plain: ByteArray): ByteArray

    /** AES-ECB 解密（无 IV） */
    fun ecbDecrypt(keyBits: Int, keyBytes: ByteArray, cipherBytes: ByteArray): ByteArray
}
