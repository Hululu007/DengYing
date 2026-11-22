package com.dengying.player.platform

/**
 * [RsaCipher] 的 OHOS 实现 —— **blocked（未实现），如实标注**
 *
 * ## 为什么是 blocked 而不是实现
 *
 * OHOS 的 `OH_CryptoAsymCipher_Init` 签名是：
 *
 * ```
 * OH_Crypto_ErrCode OH_CryptoAsymCipher_Init(
 *     OH_CryptoAsymCipher *ctx, Crypto_CipherMode mode, OH_CryptoKeyPair *key)
 * ```
 *
 * 关键限制有两条，都不是配置能绕过的：
 *
 * 1. **它要的是 `OH_CryptoKeyPair`，不是单个 `OH_CryptoKey`。**
 *    而 `OH_CryptoKeyPair` 只能由 `OH_CryptoAsymKeyGenerator_Generate`（现场生成密钥对）
 *    或 `OH_CryptoAsymKeyGeneratorWithSpec_GenKeyPair` 产出。
 *    本应用的 RSA 用途是**拿站点下发的 DER 公钥加密**（山楂源登录参数），
 *    即「导入一个已有的公钥」，不是「生成密钥对」。
 *    `OH_CryptoAsymKeyGenerator_Convert` 产出的是单个 `OH_CryptoKey`，
 *    无法装进 `OH_CryptoAsymCipher_Init` 的 `OH_CryptoKeyPair` 形参。
 *
 * 2. **该 API 是 `introduced=20.0.0`**，而本仓 harmony/ 壳工程的
 *    `compatibleSdkVersion` 是 `5.0.0(12)`（API 12），低于 20。
 *    即便解决了第 1 条，在当前壳工程的目标 API 上链接也会失败。
 *
 * ## 为什么不做纯 Kotlin 实现
 *
 * RSA 不是「几百行就能写对」的算法：需要大数运算（模幂、Miller-Rabin 素性测试）、
 * DER 解析、PKCS#1 v1.5 填充。自己实现等于引入一份**需要长期金样本守护的密码学代码**，
 * 而它的正确性无法靠编译验证。按决策阶梯第 2 条：**纯 Kotlin 不可行就如实标 blocked，
 * 不许造假实现**。
 *
 * ## 影响面
 *
 * 只有山楂源（`ah_shanzha`）的登录参数用 RSA。该源当前 `enabled = false`
 * （2026-10 探活 502），故 OHOS 上这条路径暂时走不到。
 * 若将来该源复活且需要 OHOS 支持，正确做法是等待 OHOS 提供
 * 「导入公钥 → 加密」的 API（或把壳工程提到 API 20+）。
 */
actual object RsaCipher {

    actual fun encrypt(publicKeyDer: ByteArray, plain: ByteArray): ByteArray {
        // blocked：见本文件 Kdoc。抛异常而不是返回空数组——静默返回空数组会让
        // 调用方以为「加密成功但结果是空的」，比直接失败更难排查。
        // 用 IllegalStateException 而非 NotImplementedError：后者继承 Error，
        // SourceRepository.searchAll 的 per-source catch(Exception) 接不住，
        // 2026-11-02 实测聚合到需要 RSA 的源直接把进程打死（Konan_main Uncaught）。
        throw IllegalStateException(
            "RsaCipher.encrypt 在 OHOS 上未实现：OH_CryptoAsymCipher_Init 需要 " +
                "OH_CryptoKeyPair（无法从外部 DER 公钥导入），且该 API 要求 API 20+ " +
                "（壳工程当前 compatibleSdkVersion = 5.0.0(12)）。详见本文件 Kdoc。"
        )
    }

    actual fun decrypt(privateKeyDer: ByteArray, cipherBytes: ByteArray): ByteArray {
        throw IllegalStateException(
            "RsaCipher.decrypt 在 OHOS 上未实现：同 encrypt，见本文件 Kdoc。"
        )
    }

    /**
     * PKCS#1 v1.5 明文上限 = 模长字节数 − 11。
     *
     * 这一条**可以**实现，因为它只需要从 DER 里量出模长，不需要做加密运算。
     * X.509 SubjectPublicKeyInfo 的结构是固定 TLV：
     *   SEQUENCE { SEQUENCE { OID rsaEncryption, NULL }, BIT STRING { SEQUENCE { n, e } } }
     * 模长就在最内层 SEQUENCE 的第一个 INTEGER 里。
     */
    actual fun maxPlainChunk(publicKeyDer: ByteArray): Int {
        val modulusBytes = modulusByteLength(publicKeyDer)
        return if (modulusBytes > 0) modulusBytes - 11 else 0
    }

    /**
     * 从 X.509 SubjectPublicKeyInfo DER 里量出 RSA 模长（字节）。
     *
     * 只做 TLV 长度读取，不做密钥解析，因此不需要密码学库。
     * 读不出来（非预期结构）返回 0，由 [maxPlainChunk] 转成 0 让调用方按「未知」处理。
     */
    private fun modulusByteLength(der: ByteArray): Int {
        var i = 0
        fun readLen(): Int {
            if (i >= der.size) return -1
            val first = der[i++].toInt() and 0xFF
            if (first < 0x80) return first
            val n = first and 0x7F
            if (n == 0 || n > 4 || i + n > der.size) return -1
            var v = 0
            repeat(n) { v = (v shl 8) or (der[i++].toInt() and 0xFF) }
            return v
        }
        // 外层 SEQUENCE
        if (i >= der.size || der[i++].toInt() and 0xFF != 0x30) return 0
        readLen()
        // 内层 AlgorithmIdentifier SEQUENCE
        if (i >= der.size || der[i++].toInt() and 0xFF != 0x30) return 0
        val algLen = readLen()
        if (algLen < 0) return 0
        i += algLen
        // BIT STRING
        if (i >= der.size || der[i++].toInt() and 0xFF != 0x03) return 0
        val bitLen = readLen()
        if (bitLen < 0) return 0
        i += 1 // 跳过 unused-bits 字节
        // 内层 RSAPublicKey SEQUENCE
        if (i >= der.size || der[i++].toInt() and 0xFF != 0x30) return 0
        readLen()
        // 第一个 INTEGER = modulus
        if (i >= der.size || der[i++].toInt() and 0xFF != 0x02) return 0
        val modLen = readLen()
        if (modLen <= 0) return 0
        return modLen
    }
}
