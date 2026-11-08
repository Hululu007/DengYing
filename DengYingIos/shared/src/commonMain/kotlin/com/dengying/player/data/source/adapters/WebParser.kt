package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.core.WebIdMode
import com.dengying.player.core.WebPlayRule
import com.dengying.player.core.WebRule
import com.dengying.player.core.WebTokenPlayRule
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.source.crypto.Digest

/**
 * 网站型源（HTML 正则抓取）的**纯解析层**。
 *
 * 分界线与 `CmsParser` / `MinoParser` 一致：本文件只做「页面文本 → 结果对象」，
 * 不发请求、不看状态码。控制流（先抓播放页，再决定要不要抓播放器页）留在
 * `:app` 的 `WebAdapter` 里，因为那需要 `HttpClient`。
 *
 * KMP P3：原实现在 `parseDetailHtml` / `resolvePlay` 里用 `org.json` 拼请求头 JSON
 * （`JSONObject().put(...)` → `toString()`）。这里改用 [JsonX.buildObject]，
 * 输出仍是「紧凑、无空格、保持插入顺序」的同一形状——`PlayerFactory.parseHeaders`
 * 只按 key 取值，不依赖顺序，但顺序稳定便于断言。
 *
 * 【为什么正则留在本文件】规则以正则承载，`Regex` 是 kotlin stdlib 的多平台 API，
 * 不需要 expect/actual；只有 lookbehind 在 JS 后端不可用，本文件与原实现一样
 * 不用 lookbehind（`trailingNumber` 用的是 lookahead `(?!.*\d)`，三端都支持）。
 */
object WebParser {

    /** 失败结果（保留 `err` 供页面提示） */
    fun failed(
        source: CmsSource,
        page: Int,
        err: String,
        outcome: RequestOutcome = RequestOutcome.RequestFailed,
    ) = FetchResult(outcome = outcome, page = page, sourceName = source.name, err = err)

    /** 相对路径 → 绝对地址 */
    fun absolutize(base: String, href: String): String {
        if (href.isEmpty()) return ""
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (href.startsWith("//")) return "https:$href"
        if (href.startsWith("/")) return "$base$href"
        return "$base/$href"
    }

    /**
     * 从详情链接尾段取数字 id（`/view/13691.html` → 13691；无数字则为 0）。
     *
     * `(?!.*\d)` 是**负向先行断言**：只取最后一段连续数字。不能用
     * `(\d+)$`，因为链接普遍以 `.html` 结尾。
     */
    fun trailingNumber(href: String): Long {
        val m = Regex("(\\d+)(?!.*\\d)").find(href) ?: return 0L
        return m.groupValues[1].toLongOrNull() ?: 0L
    }

    /**
     * 按规则抓搜索页并映射为条目。
     *
     * 【FR-108】空列表是 `SuccessNoData`，不是失败——网站型源抓不到条目常见于
     * 「该站没有这部片」，与「站点挂了」性质不同。
     */
    fun parseSearchHtml(source: CmsSource, html: String, rule: WebRule): FetchResult {
        val items = ArrayList<VideoItem>()
        val re = Regex(rule.search.itemPattern)
        for (m in re.findAll(html)) {
            val groups = m.groupValues
            val name = if (rule.item.nameGroup > 0) groups.getOrElse(rule.item.nameGroup) { "" } else ""
            val href = if (rule.item.hrefGroup > 0) groups.getOrElse(rule.item.hrefGroup) { "" } else ""
            val pic = if (rule.item.picGroup > 0) groups.getOrElse(rule.item.picGroup) { "" } else ""
            val status = if (rule.item.statusGroup > 0) groups.getOrElse(rule.item.statusGroup) { "" } else ""
            val vodId = idOf(href, rule.search.idMode)
            if (name.isNotEmpty() && vodId > 0) {
                items += VideoItem(
                    vod_id = vodId,
                    vod_name = name,
                    vod_pic = absolutize(rule.search.base, pic),
                    vod_remarks = status,
                    vod_uri = absolutize(rule.search.base, href),
                    _source = source.key,
                )
            }
        }
        val outcome =
            if (items.isNotEmpty()) RequestOutcome.SuccessWithData else RequestOutcome.SuccessNoData
        return FetchResult(
            outcome = outcome, items = items, page = 1, pageCount = 1,
            total = items.size, sourceName = source.name,
        )
    }

    /**
     * 解析详情页 → 线路与剧集。
     *
     * 面板型（`anchorGroup > 0`）：按锚点切段（本锚点到下一锚点），段内剧集归属对应线路——
     * **不切段会把导航区的 `<li><a>` 当成剧集、把多条线路混成一条**
     * （奇优实测：裸跑 45 条 vs 分段 13 条/面板）。
     *
     * 无面板型：全页抽取，按线路号分组（正则无线索时统一 1）。
     *
     * 剧集链接一律补成绝对地址（相对路径会被 `parseEpisodes` 的 http 校验丢弃）。
     */
    fun parseDetailHtml(
        source: CmsSource,
        html: String,
        rule: WebRule,
        vodId: Long,
    ): VideoItem? {
        val base = rule.search.base
        val lineNames = ArrayList<String>()
        val anchors = ArrayList<String>()

        if (rule.line.pattern.isNotEmpty()) {
            val lre = Regex(rule.line.pattern)
            for (m in lre.findAll(html)) {
                val g = m.groupValues
                lineNames += g.getOrElse(rule.line.nameGroup) { "" }.trim()
                anchors += if (rule.line.anchorGroup > 0) g.getOrElse(rule.line.anchorGroup) { "" } else ""
            }
        }

        val groups = LinkedHashMap<Int, MutableList<String>>()

        if (rule.seasonListPattern.isNotEmpty()) {
            // 容器型：每个匹配项是一「季 / 列表」容器，各自成为一条线路。
            // 捕获组 1 取容器正文；无捕获组时退化为整个匹配。
            val sre = Regex(rule.seasonListPattern)
            for ((a, m) in sre.findAll(html).withIndex()) {
                val g = m.groupValues
                val seg = if (g.size > 1) g[1] else m.value
                val eps = extractEpisodes(seg, rule, base)
                if (eps.isNotEmpty()) groups[a + 1] = eps.toMutableList()
            }
        } else if (rule.line.anchorGroup > 0 && anchors.isNotEmpty()) {
            for (a in anchors.indices) {
                val start = html.indexOf("id=\"${anchors[a]}\"")
                if (start < 0) continue
                var end = html.length
                for (b in (a + 1) until anchors.size) {
                    val next = html.indexOf("id=\"${anchors[b]}\"")
                    if (next > start) {
                        end = next
                        break
                    }
                }
                val seg = html.substring(start, end)
                val eps = extractEpisodes(seg, rule, base)
                if (eps.isNotEmpty()) groups[a + 1] = eps.toMutableList()
            }
        } else {
            for (one in extractEpisodes(html, rule, base)) {
                val seg = lineNoOf(rule)
                groups.getOrPut(seg) { ArrayList() }.add(one)
            }
        }

        if (groups.isEmpty()) return null

        val keys = groups.keys.sorted()
        val fromParts = ArrayList<String>(keys.size)
        val urlParts = ArrayList<String>(keys.size)
        for (k in keys) {
            val lnName = if (k in 1..lineNames.size) lineNames[k - 1] else "线路$k"
            fromParts += lnName.ifEmpty { "线路$k" }
            urlParts += (groups[k] ?: emptyList()).joinToString("#")
        }

        return VideoItem(
            vod_id = vodId,
            vod_name = "",
            vod_pic = "",
            vod_play_from = fromParts.joinToString("$$$"),
            vod_play_url = urlParts.joinToString("$$$"),
            _source = source.key,
        )
    }

    /** 从一段 HTML 抽剧集（返回 `集名$绝对链接`） */
    fun extractEpisodes(seg: String, rule: WebRule, base: String): List<String> {
        val out = ArrayList<String>()
        val ere = Regex(rule.episode.pattern)
        var n = 0
        for (m in ere.findAll(seg)) {
            val g = m.groupValues
            val href = if (rule.episode.hrefGroup > 0) g.getOrElse(rule.episode.hrefGroup) { "" } else ""
            if (href.isNotEmpty()) {
                n++
                val name = if (rule.episode.nameGroup > 0) {
                    g.getOrElse(rule.episode.nameGroup) { "" }.trim()
                } else ""
                val label = name.ifEmpty { "第${n}集" }
                out += "$label\$${absolutize(base, href)}"
            }
        }
        // 「逆转选集 = true」的源把第 1 集放在页面末尾：倒的是**顺序**，
        // 集名仍跟着自己的链接走（标签与地址不能错配）。
        if (rule.reverseEpisodes) out.reverse()
        return out
    }

    /**
     * 无面板型：剧集所属线路号。
     *
     * 规则里没有可用的线路号捕获组时统一归到 1——这是刻意的降级：
     * 猜错线路号会把剧集分到不存在的线路下，比「只有一条线」更糟。
     */
    fun lineNoOf(rule: WebRule): Int = 1

    /** 取 URL 的 scheme://host:port */
    fun originOf(url: String): String =
        Regex("^(https?://[^/]+)").find(url)?.groupValues?.getOrNull(1) ?: ""

    /**
     * 播放器页 → 媒体地址。
     *
     * 从播放器页正文里取 `const Url` / `const Sign` / `const From` 三个常量，
     * 按 `mediaUrlTemplate` 拼出最终地址，并把 `headers` 里的 `{page}` 占位
     * 换成**播放器页地址**（不是播放页地址——防盗链校验的是 iframe 那一层）。
     *
     * 缺 `Url` 或 `Sign` 时返回 `null`：原实现在这里是 `if (u != null && s != null)`，
     * 条件不成立就落到嗅探兜底，不能返回一个「url 为空」的结果。
     */
    fun playFromPlayerPage(
        rule: WebPlayRule,
        iframeUrl: String,
        playerBody: String,
    ): PlayResolveResult? {
        val u = Regex(rule.urlConstPattern).find(playerBody)?.groupValues?.getOrNull(1) ?: return null
        val s = Regex(rule.signConstPattern).find(playerBody)?.groupValues?.getOrNull(1) ?: return null
        val f = Regex(rule.fromConstPattern).find(playerBody)?.groupValues?.getOrNull(1)
        val media = rule.mediaUrlTemplate
            .replace("{origin}", originOf(iframeUrl))
            .replace("{url}", u)
            .replace("{sign}", s)
            .replace("{from}", f ?: "")
        return PlayResolveResult(url = media, headers = playHeaders(rule, iframeUrl))
    }

    /**
     * 两步换链的第二步：换链接口的 JSON 响应 → 最终媒体地址。
     *
     * 奇优改版后的 `/player/api.php` 返回
     * `{"code":200,"type":"m3m8","url":"https:\/\/…m3u8"}`（`\/` 是标准 JSON 转义，
     * 解析后即正常地址）。取 `jsonUrlKey` 指定字段，非 http 开头视为失败返回 `null`
     * （调用方回落嗅探兜底）。
     */
    fun playFromJsonResponse(rule: WebPlayRule, body: String): PlayResolveResult? {
        val key = rule.jsonUrlKey ?: return null
        val o = JsonX.parseObject(body) ?: return null
        val url = JsonX.str(o, key, "").trim()
        if (!url.startsWith("http")) return null
        return PlayResolveResult(url = url, headers = "")
    }

    /**
     * 播放器链的请求头 JSON。
     *
     * `{page}` 占位替换成 [iframeUrl]。输出形状与原来的
     * `JSONObject().put(k, v).toString()` 一致（紧凑、插入序）。
     */
    fun playHeaders(rule: WebPlayRule, iframeUrl: String): String {
        val pairs = ArrayList<Pair<String, Any?>>(rule.headers.size)
        for ((k, v) in rule.headers) {
            pairs += k to v.replace("{page}", iframeUrl)
        }
        return JsonX.buildObject(*pairs.toTypedArray()).toString()
    }

    /**
     * 嗅探兜底的头 JSON：只带 UA 与 Referer（Referer 为**播放页**地址）。
     *
     * [ua] 由调用方传入而不是在本文件写死常量：`HttpClient.UA_PC` 属于 `:app` 的
     * HTTP 层，批 D 才会挪进 `:shared`。在此之前传参比提前搬常量更小改。
     */
    fun sniffHeaders(ua: String, pageUrl: String): String =
        JsonX.buildObject(
            "User-Agent" to ua,
            "Referer" to pageUrl,
        ).toString()

    /**
     * 按白名单关键字嗅探媒体地址（mp4/m3u8）。
     *
     * 【为什么不按白名单过滤掉不匹配的】
     * `sniff` 字段的语义是「这个站点的媒体地址通常长这样」，用于**优先**而不是排除。
     * 拿不到白名单命中也应该把找到的第一个媒体地址交给播放器试试——
     * 排除掉只会让本来能播的线路变成「未找到地址」。
     *
     * 注意本函数实际**只取第一个 m3u8/mp4**，`sniff` 参数当前未参与筛选，
     * 这是原实现就有的行为（保留签名以免调用方改动），不要顺手「修好」它。
     */
    fun sniffMedia(html: String, sniff: List<String>): String {
        val m = Regex("(https?://[^\"'\\s<>]+?\\.(?:m3u8|mp4)[^\"'\\s<>]*)", RegexOption.IGNORE_CASE)
            .find(html) ?: return ""
        return m.groupValues.getOrElse(1) { "" }
    }

    // ---------------- slug 型 id（两个BT 这类无数字尾的站点） ----------------

    /**
     * 把详情链接换算成本地条目 id。
     *
     * `TrailingNumber` 是历史默认行为；`SlugHash` 用于 `/play/ch2mo68f3` 这类
     * **链接里没有数字**的站点——不换方式的话 `trailingNumber` 恒为 0，
     * 搜索结果会被整体丢弃（[parseSearchHtml] 要求 `vodId > 0`）。
     *
     * `SlugHash` 取 `SHA-256(链接)` 的前 15 个十六进制位，是 60 位正整数：
     * 对「几千条收藏」这个量级冲突概率可忽略，且**只用作本地键**
     * （收藏 / 历史 / 进度），不与线上接口交互。
     *
     * 【这条算法一旦上线就不能改】改了等于把老用户的收藏全部指向不存在的条目。
     */
    fun idOf(href: String, mode: WebIdMode): Long = when (mode) {
        WebIdMode.TrailingNumber -> trailingNumber(href)
        WebIdMode.SlugHash -> slugId(href)
    }

    /** 链接 → 稳定正整数 id（算法冻结说明见 [idOf]） */
    fun slugId(href: String): Long {
        if (href.isEmpty()) return 0L
        val hex = Digest.sha256Hex(href).take(15)
        return hex.toLongOrNull(16) ?: 0L
    }

    /** 取 URL 的路径部分（去掉 scheme://host 与查询串） */
    fun pathOf(url: String): String {
        val noQuery = url.substringBefore('?').substringBefore('#')
        val origin = originOf(noQuery)
        if (origin.isEmpty()) return noQuery
        return noQuery.removePrefix(origin)
    }

    /** 取 URL 路径末段（`/play/ch2mo68f3` → `ch2mo68f3`） */
    fun slugOf(url: String): String =
        pathOf(url).trimEnd('/').substringAfterLast('/')

    /**
     * 从播放页取 `userlink`（签名换链的令牌来源）。
     *
     * 该值**每次请求都会变**，不能缓存到规则或进程内复用；取不到时返回空串，
     * 由调用方退化用 `"0"`（与站点 JS 的 `window.myuserlink || '0'` 一致）。
     */
    fun userlinkOf(rule: WebTokenPlayRule, html: String): String {
        if (rule.userlinkPattern.isEmpty()) return ""
        val m = Regex(rule.userlinkPattern).find(html) ?: return ""
        return m.groupValues.getOrElse(1) { "" }.trim()
    }

    /**
     * 从播放页取**本集**的 `dataid`。
     *
     * 多集条目的播放页里会出现十几个 `dataid`（每集一个），只有 `href` 与当前页
     * 路径完全相同的那个 `<a>` 才是本集。规则里的 `{path}` 占位就是为此：
     * 调用方把播放页路径填进去，正则才能精确锚定到当前集。
     *
     * 取不到返回空串，调用方必须把它当成「播放链未闭环」，不许猜一个 dataid。
     */
    fun dataIdOf(rule: WebTokenPlayRule, html: String, pageUrl: String): String {
        if (rule.dataIdPattern.isEmpty()) return ""
        val path = pathOf(pageUrl)
        if (path.isEmpty()) return ""
        val pattern = rule.dataIdPattern.replace("{path}", escapeLiteral(path))
        val m = Regex(pattern).find(html) ?: return ""
        return m.groupValues.getOrElse(1) { "" }.trim()
    }

    /**
     * 把字符串转成正则字面量。
     *
     * 【为何不用 [Regex.escape]】它产出 `\Q...\E` 形式，三端正则引擎对 `\Q` 的支持
     * 不一致（JVM 支持，Kotlin/Native 与 JS 后端不保证）；逐字符转义是安全的写法。
     */
    private fun escapeLiteral(s: String): String {
        val metas = "\\.^$|?*+()[]{}"
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            if (metas.indexOf(c) >= 0) sb.append('\\')
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * 换链接口响应 → 媒体地址。
     *
     * 【必须看业务码，不能只看 HTTP 状态】实测该接口在 `code=401/403/404` 时
     * **HTTP 仍是 200**（`请提供访问令牌` / `没有权限` / `record not found`），
     * 只看状态码会把一条错误 JSON 当成功，最终表现为「拿到空地址但不知道原因」。
     */
    fun playFromTokenResponse(rule: WebTokenPlayRule, body: String): PlayResolveResult? {
        val o = JsonX.parseObject(body) ?: return null
        val code = JsonX.int(o, rule.codeKey, -1)
        if (code != rule.okCode) return null
        val list = JsonX.optArray(JsonX.optObject(o, "data"), rule.listKey) ?: return null
        val first = JsonX.optObjectAt(list, 0) ?: return null
        val url = JsonX.str(first, rule.urlKey, "").trim()
        if (url.isEmpty()) return null
        // 媒体地址直连，不带自定义请求头（实测带不带 Referer 都返回同一份 m3u8）
        return PlayResolveResult(url = url, headers = "")
    }
}
