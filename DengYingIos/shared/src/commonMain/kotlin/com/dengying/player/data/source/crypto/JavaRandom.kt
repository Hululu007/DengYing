package com.dengying.player.data.source.crypto

/**
 * Java `java.util.Random` 的 48 位线性同余发生器复刻 —— 独播库源签名依赖
 *
 * 递推：`seed = (seed * 25214903917 + 11) mod 2^48`，`next(bits) = floor(seed / 2^(48-bits))`。
 *
 * 【为什么用 Long 而不是 Double】ArkTS 版用「倍增取模」避免 float64 精度损失。
 * Kotlin 的 `Long` 是 64 位整数，`25214903917 * (2^48-1) ≈ 7.1e24` 会溢出 Long
 * （上限 9.2e18），故仍需避免直接相乘。这里用 `Math.multiplyHigh` 的等价做法：
 * 分拆乘数，每步取模，结果始终 < 2^48。
 *
 * 验证：本机独立实现按同一算法生成 sign/ssid/token，
 * 服务端 `https://api.dbokutv.com/vodsearch` 返回真实数据（如「庆余年 第二季」）。
 */
class JavaRandom(seed: Long) {

    private var seed: Long = ((seed % MOD_48) + MOD_48) % MOD_48

    /** `next(bits)`：取种子的高 bits 位 */
    fun next(bits: Int): Long {
        seed = (mulMod(seed, MULTIPLIER) + 11) % MOD_48
        return seed / (1L shl (48 - bits))
    }

    /** `nextInt(bound)`：与 `java.util.Random` 同分布 */
    fun nextInt(bound: Int): Int {
        if (bound <= 0) return next(32).toInt()
        var r: Long = next(31)
        val m = bound - 1
        if (bound and m == 0) {
            return ((bound * r) / 2147483648L).toInt()
        }
        var u = r
        var rr = u % bound
        while (u - rr + m < 0) {
            u = next(31)
            rr = u % bound
        }
        return rr.toInt()
    }

    companion object {
        /** 2^48 */
        private const val MOD_48 = 281474976710656L
        private const val MULTIPLIER = 25214903917L

        /**
         * `(a * b) mod 2^48`，避免 64 位溢出。
         *
         * 用「倍增 + 加法取模」：每步把 x 翻倍并取模，b 逐位右移。
         * 加法结果 < 2^49，落在 Long 安全区内。
         */
        private fun mulMod(a: Long, b: Long): Long {
            var result = 0L
            var x = a % MOD_48
            var y = b
            while (y > 0) {
                if (y and 1L == 1L) {
                    result = (result + x) % MOD_48
                }
                x = (x * 2) % MOD_48
                y = y shr 1
            }
            return result
        }

        /** 独播库 `a(i, j, i2)` 的两张字符表 */
        private const val ALPHABET_33 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val ALPHABET_88 = "XYZ0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVW"

        /**
         * 生成随机串（对应规则里的 `a`）
         *
         * @param len 目标长度
         * @param j 种子偏移（调用点传 `60 + t` 或 `t + 38`）
         * @param kind 33 → ALPHABET_33；88 → ALPHABET_88
         * @param nowSec 当前秒级时间戳（外部传入，保证与同一次签名的其它部分一致）
         */
        fun dubokuRandomString(len: Int, j: Long, kind: Int, nowSec: Long): String {
            val random = JavaRandom(nowSec + j)
            val alphabet = if (kind == 33) ALPHABET_33 else ALPHABET_88
            val sb = StringBuilder(len)
            for (i in 0 until len) {
                sb.append(alphabet[random.nextInt(alphabet.length)])
            }
            return sb.toString()
        }

        /**
         * 独播库签名参数（对应规则里的 `b()`）
         *
         * 形态：`?sign={a1}&ssid={base64url}&token={a2}`，
         * `ssid` 为「两个 9-10 位随机数与毫秒时间戳逐字符交错」后 Base64（`=` 替换为 `.`）。
         */
        fun dubokuSignParam(nowSec: Long, nowMs: Long): String {
            val random = JavaRandom(nowSec)
            val nextInt: Long = random.nextInt(0x2faf0800) % 0x2faf0801L
            val head = "${100000000 + nextInt}"
            val tail = "${900000000 - nextInt}"
            val mixed = head + tail
            val ms = "$nowMs"

            // 逐字符交错：直到较短的一方用完，剩余部分原样追加
            val sb = StringBuilder(mixed.length + ms.length)
            val min = minOf(mixed.length, ms.length)
            for (i in 0 until min) {
                sb.append(mixed[i])
                sb.append(ms[i])
            }
            if (mixed.length > min) sb.append(mixed.substring(min))
            if (ms.length > min) sb.append(ms.substring(min))

            val ssid = Digest.base64Encode(Digest.utf8Bytes(sb.toString())).replace('=', '.')
            val a1 = dubokuRandomString(60, 60 + nowSec, 33, nowSec)
            val a2 = dubokuRandomString(38, nowSec + 38, 88, nowSec)
            return "?sign=$a1&ssid=$ssid&token=$a2"
        }
    }
}
