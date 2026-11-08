package com.dengying.player.platform

/**
 * 非对称加密（RSA PKCS#1 v.5，KMP P2）
 *
 * 用途：桔子源二级公钥发放与请求签名、瓜皮源表单 keys 加密与响应解密、山楂源详情体加密。
 * 密钥以 base64 体形式登记在 `core/SourceSecrets.kt`，本门面只做「DER 字节进出」。
 *
 * 【密钥格式是固定的】源里给的密钥体是 **DER 的 base64**（不带 PEM 头）：
 *  - `-----BEGIN PUBLIC KEY-----` 对应 X.509 SubjectPublicKeyInfo
 *  - `-----BEGIN PRIVATE KEY-----` 对应 PKCS#8
 *
 * 所以接口只接收 DER 字节，PEM 头尾的剥离/拼接留在 [PemUtil]（纯字符串处理，跨平台）。
 * 两端实现各自按自己的规范解析 DER：JVM 用 `X509EncodedKeySpec`/`PKCS8EncodedKeySpec`，
 * iOS 用 `SecKeyCreateWithData`（它认的正是这两种 DER 结构）。
 *
 * 【填充必须是 PKCS#1 v1.5，不能用 OAEP】源站按 v1.5 验签，换 OAEP 直接全部验签失败。
 */
expect object RsaCipher {

    /** 公钥加密（PKCS#1 v1.5）；[publicKeyDer] 是 DER 字节，不是 PEM 文本 */
    fun encrypt(publicKeyDer: ByteArray, plain: ByteArray): ByteArray

    /** 私钥解密（PKCS#1 v1.5）；[privateKeyDer] 是 DER 字节，不是 PEM 文本 */
    fun decrypt(privateKeyDer: ByteArray, cipherBytes: ByteArray): ByteArray

    /**
     * 一次加密能容纳的最大明文字节数（= 密钥字节数 − 11）。
     *
     * 供上层做分块加密的步长计算（山楂源用 117 字节分块 = 1024 位密钥）。
     * 由实现方按实际密钥长度返回，避免在 `commonMain` 里写死密钥位数。
     */
    fun maxPlainChunk(publicKeyDer: ByteArray): Int
}
