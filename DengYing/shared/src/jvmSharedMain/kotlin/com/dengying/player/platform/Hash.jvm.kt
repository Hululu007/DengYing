package com.dengying.player.platform

import java.security.MessageDigest

/**
 * [Hash] 的 JVM 实现（androidMain 与 jvmMain 共用）
 *
 * Android 运行时与标准 JVM 都提供 `java.security.MessageDigest`，实现逐字相同，
 * 故放在 `jvmSharedMain` 而不是各写一份。
 */
actual object Hash {

    actual fun digest(alg: HashAlg, bytes: ByteArray): ByteArray {
        val name = when (alg) {
            HashAlg.Md5 -> "MD5"
            HashAlg.Sha1 -> "SHA-1"
            HashAlg.Sha256 -> "SHA-256"
        }
        return MessageDigest.getInstance(name).digest(bytes)
    }
}
