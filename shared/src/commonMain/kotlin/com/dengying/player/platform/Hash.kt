package com.dengying.player.platform

/** 摘要算法枚举 */
enum class HashAlg { Md5, Sha1, Sha256 }

/**
 * 消息摘要（KMP P2）
 * 为什么是 expect/actual 而不是引库：三种算法都是平台自带能力
 * （JVM 的 `java.security.MessageDigest`、iOS 的 `CommonCrypto.CC_MD5/CC_SHA1/CC_SHA256`），
 * 各写几行桥接即可。引入第三方密码学库要换掉整条依赖口径（SEC-001），收益为零。
 *
 * 【算法选择不可协商】这三个算法不是「选个安全的」问题——它们是源站的**协议约定**
 *文才源签名是 `SHA1(MD5(...))`，独播库用 MD5）。换成更安全的算法会让请求被拒。
 */
expect object Hash {

    /**
     * 取摘要。
     *
     * @return 原始摘要字节（长度：MD5 16、SHA-1 20、SHA-256 32）
     */
    fun digest(alg: HashAlg, bytes: ByteArray): ByteArray
}
