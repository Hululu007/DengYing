package com.dengying.player.data.source.adapters

import com.dengying.player.data.source.crypto.Digest
import com.dengying.player.platform.Hash
import com.dengying.player.platform.HashAlg
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 两个BT（`ah_bttwo`）播放链签名 —— wasm 算法复刻
 *
 * 【为什么需要本文件】
 * 站点的播放地址不是直接下发，而是由浏览器端加载一个 Rust→wasm 模块
 * （`/static/wasm/nbmovie_wasm.js`，导出 `build_play_url`）算出请求 URL。
 * 该 wasm 在 Kotlin/Native 上无法运行，故**逆向出算法后用纯 Kotlin 复刻**，
 * 保证 iOS 端与 Android 端走同一份纯函数、不依赖 WebView 或 wasm 运行时。
 *
 * 【算法（2026-10 逆向，已与真实端点交叉验证）】
 * ```
 *   k = Base64( userlink XOR "nbmovie2024secretkey" )   // 逐字节循环密钥异或
 *   t = Clock.nowMs()                                   // 毫秒时间戳
 *   s = HMAC-SHA256(key = v, message = "$p:$t:$v")      // 取小写 hex 前 32 位
 *   url = "/video/play?p=$p&v=$v&q=$q&s=$s&t=$t&k=$k"
 * ```
 * 其中 `p` = 剧集 `dataid`（如 `24089`）、`v` = 播放页路径末段（如 `ch2mo68f3`）、
 * `q` = 清晰度（`4k` 会被 wasm 归一成 `1`，其余原样；服务端不校验 q 参与签名）。
 *
 * 【逆向依据（可复现）】
 *  - 反汇编 `nbmovie.wasm` 得 `nbmovie.wat`，`build_play_url` 体 3080 行，
 *    其中调用 `f17`（标准 SHA-256 压缩核心，含全部 64 个 K 常数）**8 次**；
 *  - 在 `f17` 入口注入 trace 抓输入块，发现两次固定常量 `0x36`(ipad) / `0x5c`(opad)
 *    填充的 64 字节块 ⇒ HMAC 结构；消息块末尾长度字段 `0x2e8`(=744 位)
 *    = 64 字节密钥块 + 29 字节消息，与 `"24089:1793030533833:ch2mo68f3"` 精确吻合；
 *  - 用 48 组 (p, v, t) 组合对比 wasm 输出与纯 Python HMAC-SHA256，**全部一致**；
 *  - 用纯函数算出的 `s`/`k` 直接请求真实端点 → `200 OK` 并拿到可播 m3u8。
 *
 * 【为什么 `k` 用 `userlink` 而不是别的】
 * `userlink` 是服务端下发给**匿名访客**的标识，硬编码在播放页 HTML 的 `x-data`
 * 里（`{isLoggedIn:false,...,userlink:'X1VUXEZe...'}`），随后
 * `window.myuserlink = userlink`。WASM 侧 `const p = window.myuserlink || '0'`，
 * 未注入时退化用 `'0'`。故解析页时**必须先从 HTML 取 userlink**，取不到才用 `'0'`。
 *
 * 【注意 q 的归一化】wasm 里 `'4k' === q ? '1' : q` 是**唯一**对 q 的加工，
 * 不是「清晰度越高 s 越不同」——签名完全不含 q（已实测 q 改 720/1 时 s 不变）。
 */
object BttwoSign {

    /** 异或密钥（wasm 数据段偏移 1048576 处的常量串，实测逐字节一致） */
    private const val XOR_KEY = "nbmovie2024secretkey"

    /** 播放接口路径模板（wasm 数据段偏移 1048804 处的常量） */
    private const val PLAY_PATH = "/video/play"

    /**
     * 计算访问令牌 `k`。
     *
     * @param userlink 播放页下发的访客标识；为空时用 `"0"`（与 wasm 的 `|| '0'` 一致）
     * @return 标准 Base64（带 `=` 填充）文本
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun token(userlink: String): String {
        val link = userlink.ifEmpty { "0" }
        val raw = link.encodeToByteArray()
        val out = ByteArray(raw.size)
        for (i in raw.indices) {
            // 密钥循环使用：wasm 侧按字节下标对 key 取模
            val k = XOR_KEY[i % XOR_KEY.length].code.toByte()
            out[i] = (raw[i].toInt() xor k.toInt()).toByte()
        }
        return Base64.encode(out)
    }

    /**
     * 计算签名 `s`。
     *
     * `s = HMAC-SHA256(key = v, msg = "$dataId:$timestamp:$v")` 的小写 hex 前 32 位。
     *
     * 注意密钥就是 `v` 本身（不是某个固定 secret）：wasm 里 HMAC 的 key
     * 直接取播放页标识串。这条是逆向时最容易看错的一处——`v` 同时是
     * **密钥**与**消息的最后一段**。
     *
     * @param dataId 剧集 `dataid`（数字串）
     * @param timestamp 毫秒时间戳（`t` 参数，原样参与消息）
     * @param v 播放页路径末段（如 `ch2mo68f3`）
     */
    fun sign(dataId: String, timestamp: String, v: String): String =
        hmacSha256Hex(v.encodeToByteArray(), "$dataId:$timestamp:$v".encodeToByteArray()).take(32)

    /**
     * 拼出完整播放请求 URL。
     *
     * @param host 站点根域（如 `https://www.bttwo.life`），可为空则返回纯路径
     * @param dataId 剧集 `dataid`
     * @param v 播放页路径末段
     * @param quality 清晰度（`4k` 归一为 `1`）
     * @param timestamp 毫秒时间戳
     * @param userlink 播放页下发的访客标识
     */
    fun playUrl(
        host: String,
        dataId: String,
        v: String,
        quality: String,
        timestamp: Long,
        userlink: String,
    ): String {
        val q = if (quality == "4k") "1" else quality
        val t = timestamp.toString()
        val s = sign(dataId, t, v)
        val k = token(userlink)
        return "$host$PLAY_PATH?p=$dataId&v=$v&q=$q&s=$s&t=$t&k=$k"
    }

    /**
     * HMAC-SHA256 → 小写 hex。
     *
     * 【为什么不用 `Digest.sha256Hex` 直接拼】HMAC 不是 `hash(key + msg)`：
     * 它有两轮带常量填充的哈希（ipad `0x36` / opad `0x5c`），直接拼串结果完全不同。
     * 这里用平台 [Hash] 门面做「单次 SHA-256」，HMAC 的分组逻辑在纯 Kotlin 里实现，
     * 保证与 `javax.crypto.Mac` 逐字节一致。
     *
     * 实现按 RFC 2104：块长取 64 字节（SHA-256 的 B），密钥长于块长时先哈希。
     */
    private fun hmacSha256Hex(key: ByteArray, message: ByteArray): String {
        val blockSize = 64
        val k = if (key.size > blockSize) Hash.digest(HashAlg.Sha256, key) else key
        val ipad = ByteArray(blockSize)
        val opad = ByteArray(blockSize)
        for (i in 0 until blockSize) {
            val kb = if (i < k.size) k[i].toInt() else 0
            ipad[i] = (kb xor 0x36).toByte()
            opad[i] = (kb xor 0x5c).toByte()
        }
        val inner = Hash.digest(HashAlg.Sha256, ipad + message)
        val outer = Hash.digest(HashAlg.Sha256, opad + inner)
        return Digest.toHex(outer)
    }
}
