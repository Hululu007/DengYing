package com.dengying.player.data.net

import com.dengying.player.domain.playback.HlsVariants

/**
 * HLS 播放列表处理
 *
 * 处理两类问题：
 *  1. master playlist：内容是指向子列表的引用，本身无分片
 *       #EXTM3U
 *       #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1080x608
 *       2000k/hls/mixed.m3u8        ← 需要下钻到这里
 *  2. 分享页（HTML）：返回的是网页，需要提取真实地址
 *
 * 部分播放器对嵌套 m3u8 支持不佳（鸿蒙 AVPlayer 实测），Media3 虽原生支持，
 * 但**主动下钻仍保留**：下钻后播放器拿到的是纯媒体列表，可减少一次内部请求，
 * 也让「变体选择」在本应用里可控（FR-412）。
 */
object HlsUtil {

    /** 判断是否为 m3u8 直链 */
    fun isM3u8(url: String): Boolean =
        url.split("?")[0].lowercase().endsWith(".m3u8")

    /** 判断是否为分享页 */
    fun isSharePage(url: String): Boolean {
        if (url.isEmpty()) return false
        val bare = url.split("?")[0].lowercase()
        if (bare.endsWith(".m3u8") || bare.endsWith(".mp4") ||
            bare.endsWith(".flv") || bare.endsWith(".ts")
        ) {
            return false
        }
        return url.contains("/share/") || url.contains("/play/") || bare.endsWith(".html")
    }

    /** 取 base 地址（用于拼接相对路径） */
    private fun baseOf(url: String): String {
        val q = url.indexOf('?')
        val clean = if (q >= 0) url.substring(0, q) else url
        val idx = clean.lastIndexOf('/')
        return if (idx >= 0) clean.substring(0, idx + 1) else clean
    }

    /** 取 origin（协议+主机） */
    fun originOf(url: String): String =
        Regex("^(https?://[^/]+)").find(url)?.groupValues?.getOrNull(1) ?: ""

    /** 抓取文本（失败返回空串） */
    private suspend fun fetchText(url: String): String {
        val r = HttpClient.get(
            url,
            mapOf("User-Agent" to HttpClient.UA_SYSTEM),
            10000, 10000,
        )
        return if (r.status == 200) r.body else ""
    }

    /**
     * 解析分享页 → 真实地址。
     *
     * ## 正则修正记录（2026-10-17，真机抓到真实页面后修正）
     *
     * 初版三条正则对真实页面**全部不匹配**，导致"每部片第一次必失败"。
     * 抓到的真实页面（1593 字节，DPlayer + hls.js）关键内容是：
     *
     * ```javascript
     * const vid = 'https://hd.kuktxu.com/play/eERR6RNa/index.m3u8';
     * ```
     *
     * 三条旧正则为何都失败：
     *  1. `(?:const|var|let)\s+url\s*=` —— 变量名是 **`vid`** 不是 `url`
     *  2. `"url"\s*:\s*"..."` —— 这里是 `url: vid`，值不是字面量
     *  3. `["'`](/[^"'`\s]*\.m3u8...)` —— 要求以 `/` 开头，而这里是**完整 URL**
     *
     * 修正后的覆盖策略（按可靠度排序）：
     *  1. **任意变量名**赋值为含媒体扩展名的完整 URL 或路径
     *  2. 引号里的完整媒体 URL（含 `http`）
     *  3. JSON 风格的 `"url":"..."`
     *  4. 以 `/` 开头的相对媒体路径
     *
     * 关键是**不再假设变量名**，而是直接找"引号里以媒体扩展名结尾的 URL"——
     * 这个特征与变量名、引号种类、是否 JSON 都无关。
     *
     * @return 解析出的绝对地址；提取不到时返回**入参原值**
     */
    suspend fun resolveSharePage(url: String): String {
        val html = fetchText(url)
        if (html.isEmpty()) return url
        val origin = originOf(url)

        val patterns = listOf(
            // 1) 任意变量名 = 'http...m3u8|mp4|flv'（真机页面的形态）
            Regex(
                "(?:const|var|let)\\s+[A-Za-z_$][\\w$]*\\s*=\\s*[\"'`]\\s*" +
                    "(https?://[^\"'`\\s]+?\\.(?:m3u8|mp4|flv|ts)(?:\\?[^\"'`\\s]*)?)\\s*[\"'`]"
            ),
            // 2) 引号里的完整媒体 URL（不限变量名、不限是否赋值）
            Regex(
                "[\"'`](https?://[^\"'`\\s]+?\\.(?:m3u8|mp4|flv)(?:\\?[^\"'`\\s]*)?)[\"'`]"
            ),
            // 3) JSON 风格："url":"..."
            Regex("\"url\"\\s*:\\s*\"([^\"]+)\""),
            // 4) 相对路径："/xxx.m3u8"
            Regex("[\"'`](/[^\"'`\\s]*\\.(?:m3u8|mp4|flv)[^\"'`\\s]*)[\"'`]"),
        )
        val real = patterns.firstNotNullOfOrNull { re ->
            re.find(html)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotEmpty() && it != "//" }
        } ?: return url

        return when {
            real.startsWith("//") -> "https:$real"
            real.startsWith("/") -> origin + real
            !real.startsWith("http") -> "$origin/$real"
            else -> real
        }
    }

    /**
     * 分享页的结构诊断：**只报"有没有这类线索"，不报内容**（SEC-004 口径）。
     *
     * 存在的理由：真机实测遇到过"分享页三级正则都提取不到"的情形，
     * 需要区分「正则覆盖不够」与「页面需要执行 JS 才能拿到地址」——
     * 后者靠静态抓取永远解不了，必须换方案（WebView 嗅探）。
     */
    data class SharePageFacts(
        val htmlLen: Int,
        val hasM3u8: Boolean,
        val hasMp4: Boolean,
        val hasIframe: Boolean,
        val hasPlayerKeyword: Boolean,
        val hasScript: Boolean,
        val matched: Boolean,
    )

    /** 抓一次分享页并给出结构特征（供调用方写诊断日志） */
    suspend fun inspectSharePage(url: String): SharePageFacts {
        val html = fetchText(url)
        return SharePageFacts(
            htmlLen = html.length,
            hasM3u8 = html.contains(".m3u8"),
            hasMp4 = html.contains(".mp4"),
            hasIframe = html.contains("<iframe"),
            hasPlayerKeyword = html.contains("player", ignoreCase = true),
            hasScript = html.contains("<script"),
            matched = false,
        )
    }

    /**
     * 把 master playlist 下钻到媒体列表。
     *
     * 若 url 指向主列表（含 `EXT-X-STREAM-INF`），则取**带宽最高**的变体；否则原样返回。
     *
     * FR-412：改动前固定取**第一个**变体。HLS 规范没有规定变体顺序，
     * 站点常把最低码率放前面，于是默认播的是最糊那档。
     */
    suspend fun resolveMasterPlaylist(url: String): String {
        if (!isM3u8(url)) return url
        val text = fetchText(url)
        if (text.isEmpty()) return url
        // 已是媒体列表（含分片）→ 直接用
        if (text.contains("#EXTINF")) return url
        // 是主列表 → 取最高带宽的变体
        val best = HlsVariants.pickDefault(HlsVariants.parseVariants(text, url))
            ?: return url
        return best.url
    }

    /** 解析结果：最终地址 + 该地址所属的主列表变体清单（FR-412） */
    data class ResolvedPlay(
        val url: String,
        /** `variants` 为空表示不支持多码率，调用方据此**不显示清晰度入口** */
        val variants: List<HlsVariants.Variant> = emptyList(),
        /** 当前选中的变体地址；单码率时为空串 */
        val selected: String = "",
    )

    /**
     * 解析链的详细版：与 [resolvePlayUrl] 同一套流程，另外把变体清单带出来。
     *
     * 清单只在**最终地址的上一层**有意义——若解析链中途下钻过主列表，
     * 记下那一层的变体；未下钻（直接就是媒体列表或非 m3u8）则为空。
     */
    suspend fun resolvePlayUrlDetailed(url: String): ResolvedPlay {
        var cur = url
        if (isSharePage(cur)) cur = resolveSharePage(cur)
        if (!isM3u8(cur)) return ResolvedPlay(cur)

        val text = fetchText(cur)
        if (text.isEmpty() || text.contains("#EXTINF")) return ResolvedPlay(cur)

        val variants = HlsVariants.parseVariants(text, cur)
        if (variants.isEmpty()) return ResolvedPlay(cur)
        val best = HlsVariants.pickDefault(variants)
            ?: return ResolvedPlay(cur)
        return ResolvedPlay(url = best.url, variants = variants, selected = best.url)
    }

    /**
     * 从**已确定的最终地址**加载变体清单（FR-412）。
     *
     * **为什么要与解析链分开**：地址的来源不止解析链一条。适配器换出的地址
     * 同样可能是 m3u8 主列表，但那条路径不走 [resolvePlayUrlDetailed]——
     * 若只在解析链里解析变体，适配器源就永远拿不到清晰度入口。
     *
     * **判据按内容而非扩展名**：适配器换出的地址常无 `.m3u8` 后缀
     * （token 型、`content.php?...` 之类）。扩展名判断会把这类主列表提前挡掉，
     * 于是出现「探测按内容抓到 HLS、变体却为 0」这种自相矛盾的结果。
     */
    suspend fun loadVariants(url: String): List<HlsVariants.Variant> {
        if (url.isEmpty()) return emptyList()
        val text = fetchText(url)
        if (text.isEmpty() || text.contains("#EXTINF")) return emptyList()
        return HlsVariants.parseVariants(text, url)
    }

    /** 完整解析链：分享页 → m3u8 → 下钻主列表 */
    suspend fun resolvePlayUrl(url: String): String {
        var cur = url
        if (isSharePage(cur)) cur = resolveSharePage(cur)
        return resolveMasterPlaylist(cur)
    }

    /**
     * 解析链的结果：地址 + **是否真的解析出来了**。
     *
     * @property resolved `false` 表示"地址是分享页但提取不出媒体地址"。
     *   调用方据此**直接换线**，而不是把这个 HTML 地址交给播放器——
     *   那必然报 `code=3003 Source error`，白等一次失败再换线。
     *   真机实测：这一步能省掉 2-3 秒的无效等待，也是"每部片第一次都失败"的成因之一。
     */
    data class ResolveOutcome(val url: String, val resolved: Boolean)

    /** 解析链（带成败标记）：分享页 → m3u8 → 下钻主列表 */
    suspend fun resolvePlayUrlOrFail(url: String): ResolveOutcome {
        var cur = url
        // 【无协议必败】token 型源换链失败时会把裸 ID 原样传进来（模拟器实测：
        // tv189 VIP 内容 917 无试看流 → resolvePlay 空手 → raw="C47346905"）。
        // 这种串连 URL 都不是，交给播放器必然 MalformedURLException
        // （code=3003 "播放失败（解码错误或地址失效）"）。判解析失败，
        // 让解析链走换源兜底、最终给出可读错误，而不是白等一次播放器报错。
        if (!cur.contains("://")) {
            return ResolveOutcome(cur, resolved = false)
        }
        if (isSharePage(cur)) {
            val real = resolveSharePage(cur)
            if (real.isEmpty() || real == cur) {
                return ResolveOutcome(cur, resolved = false)
            }
            cur = real
        }
        return ResolveOutcome(resolveMasterPlaylist(cur), resolved = true)
    }

    /**
     * 可达性诊断。
     *
     * 用 App 自身的网络栈探测 m3u8 与其首个分片是否可拉取。
     * 用于区分「源不可达」与「播放器问题」。
     *
     * **必须以设备/模拟器的网络环境为准，PC 侧的连通性不代表设备侧。**
     */
    data class ReachResult(
        val m3u8Ok: Boolean,
        val m3u8Status: Int,
        val segOk: Boolean,
        val segStatus: Int,
        val segHost: String,
    )

    suspend fun probeReachability(m3u8Url: String): ReachResult {
        val text = fetchText(m3u8Url)
        if (text.isEmpty()) {
            return ReachResult(false, 0, false, 0, "")
        }
        val m3u8Ok = true
        val m3u8Status = 200

        // 找首个分片
        for (raw in text.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (!line.contains(".ts") && !line.contains(".m3u8")) continue

            val seg = when {
                line.startsWith("//") -> "https:$line"
                line.startsWith("/") -> originOf(m3u8Url) + line
                !line.startsWith("http") -> baseOf(m3u8Url) + line
                else -> line
            }
            val segHost = originOf(seg)
            val ok = headOk(seg)
            return ReachResult(m3u8Ok, m3u8Status, ok, if (ok) 200 else -1, segHost)
        }
        // 没有分片行（可能是主列表）：只报清单可达
        return ReachResult(m3u8Ok, m3u8Status, false, 0, "")
    }

    /**
     * 探测分片可达性。
     *
     * 【真机实测的误判，必须记录】初版只认 200/206，而带 `Range: bytes=0-1023`
     * 的请求在部分 CDN 上会返回 **416（Range Not Satisfiable）** 或直接回 200 空体；
     * 更常见的是 CDN 忽略 Range 但返回 200 全量。结果是**可播的分片被判成不可达**，
     * 应用明明能播却反复走"自动换线"，用户看到的是长时间黑屏。
     *
     * 真机证据（vivo V2419A / jyzy 源）：
     *   after-probe elapsed=3042 m3u8=true seg=false host=https://hd.kuktxu.com
     * 而同一地址播放器实际能出画面 —— 即 `seg=false` 是误判。
     *
     * 故判定放宽为「**拿到 2xx 即视为可达**」，并且**不再带 Range 头**：
     * 只要 CDN 愿意回数据，就说明这条线路可用；真正不可达会走 status=0 或 4xx/5xx。
     * 代价是探测会多下一点数据——但 HLS 分片本身很小，且这一步本来就要发请求。
     */
    private suspend fun headOk(url: String): Boolean {
        val r = HttpClient.get(
            url,
            mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36"),
            8000, 8000,
        )
        return r.status in 200..299
    }
}
