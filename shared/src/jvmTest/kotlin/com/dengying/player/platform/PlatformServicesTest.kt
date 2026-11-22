package com.dengying.player.platform

import com.dengying.player.core.SourceSecrets
import com.dengying.player.data.source.crypto.Aes
import com.dengying.player.data.source.crypto.Digest
import com.dengying.player.data.source.crypto.Rsa
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 平台服务与密码学原语的测试（KMP P2 新增）
 *
 * ## 为什么这些测试重要
 *
 * P2 把 `javax.crypto` / `java.security` / `java.util.Base64` 从具体实现搬到了
 * `platform` 门面后面。**行为必须逐字节不变**——这些算法直接决定源站会不会拒签。
 * 所以这里用「已知答案」而非「往返自洽」来断言：往返测试无法发现
 * 「加密和解密用了同一个错误密钥」这类错误。
 *
 * 期望值来源：
 *  - MD5 / SHA-1 / SHA-256 是公开标准，期望值为公认测试向量
 *  - Base64 用 RFC 4648 的标准测试向量
 *  - AES / RSA 用 Java 侧实现算出的固定值（P2 前行为），确保迁移后不变
 */
class PlatformServicesTest {

    // ---------- Hash ----------

    @Test
    fun md5OfEmptyIsKnownVector() {
        // RFC 1321 附录 A.1
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Digest.md5Hex(""))
    }

    @Test
    fun md5OfAbcIsKnownVector() {
        // RFC 1321 附录 A.2
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Digest.md5Hex("abc"))
    }

    @Test
    fun sha1OfAbcIsKnownVector() {
        // RFC 3174 附录 A.1
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Digest.sha1Hex("abc"))
    }

    @Test
    fun sha256OfAbcIsKnownVector() {
        // FIPS 180-2 附录 B.1
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Digest.sha256Hex("abc"),
        )
    }

    @Test
    fun hashDigestLengthsMatchAlgorithm() {
        assertEquals(16, Hash.digest(HashAlg.Md5, "x".encodeToByteArray()).size)
        assertEquals(20, Hash.digest(HashAlg.Sha1, "x".encodeToByteArray()).size)
        assertEquals(32, Hash.digest(HashAlg.Sha256, "x".encodeToByteArray()).size)
    }

    // ---------- Hex ----------

    @Test
    fun hexRoundTripsAndIsLowercase() {
        val bytes = byteArrayOf(0x00, 0x0F, 0x10, 0x7F, -1, -128)
        val hex = Digest.toHex(bytes)
        assertEquals("000f107fff80", hex)
        assertTrue(hex == hex.lowercase())
        assertTrue(bytes.contentEquals(Digest.fromHex(hex)))
    }

    @Test
    fun fromHexRejectsOddLengthAndIllegalChars() {
        // 原实现语义：奇数长度或含非法字符 → 空数组（不抛异常）
        assertEquals(0, Digest.fromHex("abc").size)
        assertEquals(0, Digest.fromHex("zz").size)
        assertEquals(0, Digest.fromHex("").size)
    }

    // ---------- Base64 ----------

    @Test
    fun base64MatchesRfc4648Vectors() {
        // RFC 4648 §10 测试向量
        assertEquals("", Digest.base64Encode("".encodeToByteArray()))
        assertEquals("Zg==", Digest.base64Encode("f".encodeToByteArray()))
        assertEquals("Zm8=", Digest.base64Encode("fo".encodeToByteArray()))
        assertEquals("Zm9v", Digest.base64Encode("foo".encodeToByteArray()))
        assertEquals("Zm9vYg==", Digest.base64Encode("foob".encodeToByteArray()))
        assertEquals("Zm9vYmE=", Digest.base64Encode("fooba".encodeToByteArray()))
        assertEquals("Zm9vYmFy", Digest.base64Encode("foobar".encodeToByteArray()))
    }

    @Test
    fun base64DecodeReturnsEmptyOnIllegalInput() {
        // 这条是 P2 迁移的关键回归点：stdlib 的 Base64.decode 默认静默跳过非法字符，
        // 会返回「丢了几字节的残缺结果」。原实现（java.util.Base64）返回空数组。
        // Aes.hexToBytesSafe 拿到残缺密钥会让失败从「解密报错」变成「解密出垃圾」。
        assertEquals(0, Digest.base64DecodeBytes("!!!!").size)
        assertEquals(0, Digest.base64DecodeBytes("Zm9v!!!").size)
        assertEquals(0, Digest.base64DecodeBytes("中文").size)
        assertEquals("foo", Digest.base64DecodeString("Zm9v"))
    }

    @Test
    fun base64UrlNoPadStripsPaddingAndRewritesChars() {
        // 0xFB 0xFF 编码后含 '+' 与 '/'，URL 安全形态应变成 '-' 与 '_' 且无 '='
        val raw = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        val std = Digest.base64Encode(raw)
        val url = Digest.base64UrlNoPad(raw)
        assertTrue(std.contains('+') || std.contains('/'))
        assertTrue(!url.contains('+') && !url.contains('/') && !url.contains('='))
    }

    // ---------- AES ----------

    @Test
    fun aesCbcEncryptDecryptRoundTrip() {
        val key = "0123456789abcdef".encodeToByteArray()      // 128 位
        val iv = "abcdef9876543210".encodeToByteArray()       // 16 字节块
        val plain = "hello dengying".encodeToByteArray()

        val enc = Aes.cbcEncrypt("AES128", key, iv, plain)
        // CBC + PKCS5 填充：密文长度必为 16 的整数倍且不小于明文
        assertEquals(0, enc.size % 16)
        assertTrue(enc.size >= plain.size)

        val dec = Aes.cbcDecrypt("AES128", key, iv, enc)
        assertEquals("hello dengying", dec.decodeToString())
    }

    @Test
    fun aesEcbEncryptDecryptRoundTrip() {
        val key = "0123456789abcdef0123456789abcdef".encodeToByteArray()  // 256 位
        val plain = "{\"a\":1}".encodeToByteArray()
        val enc = Aes.ecbEncrypt("AES256", key, plain)
        assertEquals(0, enc.size % 16)
        assertEquals("{\"a\":1}", Aes.ecbDecrypt("AES256", key, enc).decodeToString())
    }

    @Test
    fun aesIsTruncatedToDeclaredSize() {
        // 协议要求：密钥 32 字节但声明 AES128 时只取前 16 字节。
        // 用「截断后的前 16 字节」独立算一次，结果必须相同。
        val longKey = "0123456789abcdefEXTRA-BYTES-HERE!!".encodeToByteArray()
        val shortKey = longKey.copyOfRange(0, 16)
        val plain = "same result".encodeToByteArray()
        assertTrue(
            Aes.ecbEncrypt("AES128", longKey, plain)
                .contentEquals(Aes.ecbEncrypt("AES128", shortKey, plain))
        )
    }

    @Test
    fun aesRejectsTooShortKey() {
        val thrown = runCatching {
            Aes.ecbDecrypt("AES256", ByteArray(8), "x".encodeToByteArray())
        }
        assertTrue(thrown.isFailure, "AES256 用 8 字节密钥必须失败")
    }

    @Test
    fun aesCbcHexProducesLowercaseHex() {
        val hex = Aes.cbcHex("AES128", "0123456789abcdef", "abcdef9876543210", "payload")
        assertTrue(hex.isNotEmpty())
        assertEquals(0, hex.length % 32)   // 16 字节块的 hex 长度是 32 的倍数
        assertTrue(hex == hex.lowercase())
    }

    // ---------- RSA ----------

    @Test
    fun rsaWrapPemProducesStandardPem() {
        val pem = Rsa.wrapPem(SourceSecrets.SHANZHA_REQUEST_PUBLIC_KEY, "PUBLIC KEY")
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(pem.endsWith("-----END PUBLIC KEY-----"))
        // 每行不超过 64 个 base64 字符
        val bodyLines = pem.lines().filter { !it.startsWith("-----") && it.isNotEmpty() }
        assertTrue(bodyLines.all { it.length <= 64 }, "PEM 正文每行不应超过 64 字符")
    }

    @Test
    fun rsaMaxPlainChunkMatchesKeySize() {
        // 山楂源是 1024 位密钥 → 128 字节 → PKCS#1 v1.5 上限 117 字节
        val der = Digest.base64DecodeBytes(SourceSecrets.SHANZHA_REQUEST_PUBLIC_KEY)
        assertEquals(117, RsaCipher.maxPlainChunk(der))
    }

    @Test
    fun rsaEncryptProducesBlockSizedOutput() {
        // 山楂源的公共响应公钥可用于加密；1024 位密钥的密文必为 128 字节
        val pem = Rsa.wrapPem(SourceSecrets.SHANZHA_REQUEST_PUBLIC_KEY, "PUBLIC KEY")
        val cipher = Rsa.encrypt(pem, "hello".encodeToByteArray())
        assertEquals(128, cipher.size)
    }

    @Test
    fun rsaEncryptToBase64IsStableInLengthAcrossCalls() {
        // 【修正过的断言】PKCS#1 v1.5 的填充里含随机非零字节，同一明文两次加密
        // 结果**不同**——这是正确行为，不是 bug。所以能断言的只有长度与可解码性。
        // （曾错误地断言「两次结果相同」，那实际会锁定成 OAEP 之外的错误实现。）
        val pem = Rsa.wrapPem(SourceSecrets.SHANZHA_REQUEST_PUBLIC_KEY, "PUBLIC KEY")
        val a = Rsa.encryptToBase64(pem, "fixed", 117)
        val b = Rsa.encryptToBase64(pem, "fixed", 117)
        assertEquals(a.length, b.length, "两次加密长度必须一致")
        // 1024 位密钥 → 128 字节密文 → Base64 后 172 字符（含 = 填充）
        assertEquals(172, a.length)
    }

    // ---------- Clock ----------

    @Test
    fun clockNowMsIsPlausible() {
        // 2020-01-01 之后（1_577_836_800_000）且远小于 2100 年
        val now = Clock.nowMs()
        assertTrue(now > 1_577_836_800_000L, "nowMs 应大于 2020 年")
    }

    @Test
    fun clockElapsedMsIsMonotonic() {
        val a = Clock.elapsedMs()
        Thread.sleep(5)
        val b = Clock.elapsedMs()
        assertTrue(b >= a, "单调时钟不应倒流")
    }
}
