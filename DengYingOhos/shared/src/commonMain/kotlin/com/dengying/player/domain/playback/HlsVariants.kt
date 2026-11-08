package com.dengying.player.domain.playback

/**
 * HLS 主列表变体解析（FR-412）—— 纯函数，不依赖 Android
 *
 * 需求：主列表存在多个变体时让用户选择清晰度；默认取**最高带宽**或用户上次偏好；
 * 选择结果写入会话；**不支持多码率时不显示入口**。
 *
 * 现状（改动前）：解析链固定取**第一个**变体——而 HLS 规范没有规定变体顺序，
 * 站点常把最低码率放前面，于是用户默认看到的是最糊的那档。
 *
 * 本对象只做文本解析与选择，不发请求，故可用夹具在本地单测（NFR-009）。
 */
object HlsVariants {

    /** 一个变体（码率档位） */
    data class Variant(
        /** 声明带宽（bps）；缺失为 0 */
        val bandwidth: Int,
        /** 分辨率「宽x高」；缺失为空串 */
        val resolution: String,
        /** 垂直像素（用于显示与排序兜底）；缺失为 0 */
        val height: Int,
        /** 解析并补全后的绝对地址 */
        val url: String,
    )

    /** 带宽为 0（未声明）时用于兜底的排序权重 */
    private const val NO_BANDWIDTH = -1

    /** 从 `RESOLUTION=1920x1080` 里取高度 */
    private fun heightOf(resolution: String): Int {
        val i = resolution.lowercase().indexOf('x')
        if (i < 0) return 0
        val h = resolution.substring(i + 1)
        if (h.isEmpty() || !h.all { it in '0'..'9' }) return 0
        return h.toIntOrNull() ?: 0
    }

    /** 解析 attribute list（`A=1,B=2`） */
    private fun attrOf(attrs: String, key: String): String {
        val prefix = "$key="
        for (p in attrs.split(',')) {
            val t = p.trim()
            if (t.startsWith(prefix)) {
                var v = t.substring(prefix.length).trim()
                // 去引号（CODECS 之类的值可能带引号）
                if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length - 1)
                }
                return v
            }
        }
        return ""
    }

    /**
     * 把变体地址补成绝对地址。
     *
     * 三种形态都要处理：协议相对（`//host/path`）、根相对（`/path`）、
     * 路径相对（`720p.m3u8`）。HLS 里三种都常见。
     *
     * 【踩坑注释，必须保留】路径相对分支只能在 **origin 之后** 的部分找斜杠——
     * 直接 `lastIndexOf('/')` 会落在协议分隔符 `://` 里（`https://h` 的最后一条
     * 斜杠在索引 6），于是把 `https://` 当成目录、丢掉主机名，拼出 `https://a.m3u8`。
     */
    fun resolveVariantUrl(base: String, raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return ""
        if (v.startsWith("http://") || v.startsWith("https://")) return v

        // 取 base 的 origin（scheme://host[:port]）
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return v
        val afterScheme = base.substring(schemeEnd + 3)
        val slash = afterScheme.indexOf('/')
        val origin =
            if (slash < 0) base
            else base.substring(0, schemeEnd + 3 + slash)

        if (v.startsWith("//")) {
            return base.substring(0, schemeEnd + 1) + v
        }
        if (v.startsWith("/")) {
            return origin + v
        }

        // 路径相对：去掉 base 的最后一段。见上方踩坑注释。
        val q = base.indexOf('?')
        val clean = if (q < 0) base else base.substring(0, q)
        val pathStart = clean.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) {
            // base 只有 origin、没有路径段 → 相对地址挂在根下
            return "$clean/$v"
        }
        val lastSlash = clean.lastIndexOf('/')
        val dir =
            if (lastSlash < pathStart) clean.substring(0, pathStart + 1)
            else clean.substring(0, lastSlash + 1)
        return dir + v
    }

    /**
     * 解析主列表文本，返回全部变体（按带宽降序，最高在前）。
     *
     * 非主列表（无 `#EXT-X-STREAM-INF`）返回空数组——调用方据此判断
     * 「不支持多码率」，从而**不显示清晰度入口**。
     *
     * @param text master playlist 文本
     * @param baseUrl 该 m3u8 的地址（用于补全相对路径）
     */
    fun parseVariants(text: String, baseUrl: String): List<Variant> {
        if (text.isEmpty() || !text.contains("#EXT-X-STREAM-INF")) return emptyList()

        val out = ArrayList<Variant>()
        var pending = ""   // 最近一条 STREAM-INF 的属性串
        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                pending = line.substring("#EXT-X-STREAM-INF:".length)
                continue
            }
            if (line.startsWith("#")) {
                // 其他标签不打断变体配对，但也不消费 pending
                continue
            }
            // 非注释行：若前面有 STREAM-INF，则这条是它的地址
            if (pending.isNotEmpty()) {
                val bw = attrOf(pending, "BANDWIDTH")
                val res = attrOf(pending, "RESOLUTION")
                val url = resolveVariantUrl(baseUrl, line)
                if (url.isNotEmpty()) {
                    out += Variant(
                        bandwidth = if (bw.isNotEmpty() && bw.all { it in '0'..'9' }) (bw.toIntOrNull() ?: 0) else 0,
                        resolution = res,
                        height = heightOf(res),
                        url = url,
                    )
                }
                pending = ""
            }
        }
        return sortVariants(out)
    }

    /**
     * 按清晰度降序排序：先比带宽（缺失视为最低），带宽相同比垂直像素。
     * 顺序确定，故可用例断言。
     */
    fun sortVariants(list: List<Variant>): List<Variant> =
        list.sortedWith(
            compareByDescending<Variant> { if (it.bandwidth > 0) it.bandwidth else NO_BANDWIDTH }
                .thenByDescending { it.height }
                // 最后按地址定序，保证同码率同分辨率的多个变体顺序稳定
                .thenBy { it.url }
        )

    /**
     * 默认变体：**最高带宽**（需求原文）。列表为空返回 null——
     * 调用方据此走原路径（直接播放输入的地址）。
     */
    fun pickDefault(variants: List<Variant>): Variant? = sortVariants(variants).firstOrNull()

    /**
     * 是否应显示清晰度入口。
     *
     * 需求：「不支持多码率时不显示入口」。故 **2 个及以上变体**才显示——
     * 只有一个变体时给用户一个"选择"是无意义的。
     * 另外要求各变体**实际可区分**（码率或分辨率不同），
     * 否则会出现「三个选项点哪个都一样」的假选择。
     */
    fun shouldShowPicker(variants: List<Variant>): Boolean {
        if (variants.size < 2) return false
        val first = variants[0]
        return variants.any { it.bandwidth != first.bandwidth || it.height != first.height }
    }

    /**
     * 值不值得去抓变体清单（FR-412）。
     *
     * 判据与调度分离：[PlayerViewModel.startBackgroundVariantsIfWanted] 原本把这个
     * 判断和 `viewModelScope.launch` 写在一起，于是「非 m3u8 就不抓」这条语义
     * 只能靠读代码确认。抽出来之后可以断言。
         * @param url 已解析出的播放地址
     * @param fromSource 是否经换链。换链来源的地址常常没有 `.m3u8` 后缀
     *   （`content.php?id=...` 之类），但内容就是主列表，靠后缀会把它们全挡掉。
     */
    fun wantsVariants(url: String, fromSource: Boolean): Boolean =
        fromSource || url.substringBefore('?').lowercase().endsWith(".m3u8")

    /**
     * 后台抓回来的清单是否已经作废。
     *
     * 抓取异步进行，回来时用户可能已经换集/换线/换档。把上一集的档位盖到
     * 当前集上会出现「选了 1080P 却在播上一集」的错乱，故地址一变就丢。
     *
     * 抽出来的理由同 [wantsVariants]：原本是 `startBackgroundVariants` 协程里的
     * 一行 `if (url != resolvedUrl) return@launch`。真机上要复现必须让两次抓取
     * 在时间上重叠（第一次慢、第二次快），实测两次都在 400–500ms 内失败、从未重叠，
     * 该分支此前**既无单测也无真机证据**（docs/04 §16.18.6.4）。
     */
    fun isStale(requestUrl: String, currentUrl: String): Boolean = requestUrl != currentUrl

    /** 单个变体的显示标签，如「1080P」「1080P · 5.0M」 */
    fun variantLabel(v: Variant): String {
        val parts = ArrayList<String>(2)
        if (v.height > 0) parts += "${v.height}P"
        if (v.bandwidth > 0) {
            // 以 M 为单位、保留一位小数：5.0M 比 5000000 好读
            //
            // 【P4 变更】原来用 `String.format("%.1fM", mbps)` —— 那是 `java.lang.String`
            // 的静态方法，`commonMain` 里只有 JVM 编译才会碰巧通过（P5 加 iOS target 后
            // 立即编译失败）。改成整数十分位手工拼装：`round(bw/100_000.0)` 本身就是
            // 十分位计数（例如 5_000_000 → 50 → "5.0M"），不需要任何格式化 API。
            // 取值与旧实现逐字节一致（非负输入下 `%.1f` 就是「整数部分.十分位」）。
            val tenths = kotlin.math.round(v.bandwidth / 100_000.0).toLong()
            parts += "${tenths / 10}.${tenths % 10}M"
        }
        if (parts.isEmpty()) {
            // 两者都没声明：只能给个序号性描述，不留空标签
            return "未知档位"
        }
        return parts.joinToString(" · ")
    }

    /**
     * 按用户选择取变体。
     *
     * @param wantUrl 会话里记住的选择（空串表示未选择过）
     * @return 选中的变体；`wantUrl` 已不在列表中时回落默认档
     */
    fun pickByUrl(variants: List<Variant>, wantUrl: String): Variant? {
        if (variants.isEmpty()) return null
        if (wantUrl.isNotEmpty()) {
            variants.firstOrNull { it.url == wantUrl }?.let { return it }
        }
        return pickDefault(variants)
    }
}
