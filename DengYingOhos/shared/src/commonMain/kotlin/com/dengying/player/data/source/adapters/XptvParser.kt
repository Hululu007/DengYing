package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsClass
import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * XPTV csp 脚本型站点的纯解析层（当前落地：橙果短剧）。
 *
 * 这类站点把数据以 Nuxt 扁平数组内嵌在 HTML 的 `<script id="__NUXT_DATA__">` 里：
 * 数组元素既可能是值，也可能是**整数下标**，取字段时必须按 JS 脚本的 `nuxtVal`
 * 规则解引用一次。解析逻辑单独放这里，便于用真实页面样本离线钉住，不发请求。
 */
internal object XptvParser {

    /** 页面内嵌数据脚本的匹配边界；大小写与单双引号都按站点实际形态兼容。 */
    private val NUXT_SCRIPT = Regex(
        """<script[^>]+id=["']__NUXT_DATA__["'][^>]*>([\s\S]*?)</script>""",
        RegexOption.IGNORE_CASE,
    )

    /** 站点分类 tab：id 同时是路径名。用静态表避免为首页分类再发一次页面请求。 */
    private val CLASSES: List<CmsClass> = listOf(
        CmsClass(1, "原创短剧"),
        CmsClass(2, "魔改短剧"),
        CmsClass(3, "漫剧"),
        CmsClass(4, "真人短剧"),
        CmsClass(5, "AI短剧"),
        CmsClass(6, "片库索引"),
        CmsClass(7, "排行榜"),
        CmsClass(8, "首页推荐"),
    )

    private val CLASS_PATHS = mapOf(
        1 to "yuanchuang",
        2 to "mogai",
        3 to "manju",
        4 to "zhenren",
        5 to "aiduanju",
        6 to "browse",
        7 to "rank",
        8 to "home",
    )

    /** 供 fetchClasses 返回静态类目，缓存与负缓存语义沿用 SourceRepository。 */
    fun classes(): List<CmsClass> = CLASSES

    /** 分类 id → 站点路径；未知 id 回落原创短剧。 */
    fun classPath(typeId: Int?): String = classPathOf(typeId)

    /** 搜索/分类列表：HTML → FetchResult。同一解析规则复用两种入口。 */
    fun list(body: String, source: CmsSource, page: Int): FetchResult {
        val arr = payload(body) ?: return failed(source, page, "响应缺少 __NUXT_DATA__", RequestOutcome.ParseFailed)
        val items = parseItems(arr, source)
        return FetchResult(
            outcome = if (items.isEmpty()) RequestOutcome.SuccessNoData else RequestOutcome.SuccessWithData,
            items = items,
            page = page,
            sourceName = source.name,
        )
    }

    /** 详情：以 detail_route 定位，复用列表项的剧集数信息。 */
    fun detail(body: String, source: CmsSource, vodUri: String): VideoItem? {
        val arr = payload(body) ?: return null
        return parseItems(arr, source).firstOrNull { it.vod_uri == vodUri }
            ?: parseItems(arr, source).firstOrNull()
    }

    /**
     * 播放链换取：播放页 Nuxt 数组中的第一个字符串 m3u8 即当前集直链。
     * `token` 是本适配器在详情里写入的 `/play/<slug>/<n>` 路径。
     */
    fun play(body: String): String = playFromPayload(body)

    private fun failed(
        source: CmsSource,
        page: Int,
        err: String,
        outcome: RequestOutcome,
    ): FetchResult = FetchResult(
        outcome = outcome,
        page = page,
        sourceName = source.name,
        err = err,
    )

    private fun payload(html: String): JsonArray? {
        val raw = NUXT_SCRIPT.find(html)?.groupValues?.getOrNull(1) ?: return null
        return JsonX.parseArray(raw)
    }

    private fun playFromPayload(html: String): String {
        val arr = payload(html) ?: return ""
        for (e in arr) {
            val s = JsonX.textOf(e, "")
            if (s.startsWith("http://") || s.startsWith("https://")) {
                val unescaped = s.replace("\\u002F", "/").replace("\\/", "/")
                if (unescaped.contains(".m3u8")) return unescaped
            }
        }
        return ""
    }

    private fun parseItems(arr: JsonArray, source: CmsSource): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val seen = HashSet<String>()
        for (e in arr) {
            val item = e as? JsonObject ?: continue
            if (!item.containsKey("title") || (!item.containsKey("slug") && !item.containsKey("detail_route"))) continue
            val title = deref(arr, item["title"]) as? JsonPrimitive ?: continue
            val name = JsonX.textOf(title, "").trim()
            if (name.isEmpty() || !isAllowedTitle(name)) continue

            val slug = deref(arr, item["slug"])?.let { JsonX.textOf(it, "") }.orEmpty()
            var route = deref(arr, item["detail_route"])?.let { JsonX.textOf(it, "") }.orEmpty()
            if (route.isEmpty() && slug.isNotEmpty()) route = "/drama/$slug"
            if (!route.startsWith("/drama/")) continue
            if (!seen.add(route)) continue

            val cover = deref(arr, item["cover"]) as? JsonObject
            val pic = cover?.get("url")?.let { deref(arr, it) }?.let { JsonX.textOf(it, "") }.orEmpty()
            val eps = JsonX.intOf(deref(arr, item["total_episode_count"]), 0)
                .takeIf { it > 0 }
                ?: JsonX.intOf(deref(arr, item["published_episode_count"]), 0)
            val playPath = if (eps > 0 && slug.isNotEmpty()) "/play/$slug/1" else ""
            out += VideoItem(
                vod_id = 0L,
                vod_name = name,
                vod_pic = pic,
                vod_remarks = if (eps > 0) "全${eps}集" else "",
                vod_play_from = if (playPath.isNotEmpty()) "橙果" else "",
                vod_play_url = playPath,
                vod_uri = route,
                _source = source.key,
            )
        }
        return out
    }

    /**
     * 过滤明显成人标题。XPTV 聚合清单含成人站点，本接入只保留普通短剧；
     * 这是编译期常量过滤器，不引入在线审核或推荐。
     */
    private fun isAllowedTitle(title: String): Boolean {
        val denied = listOf("淫", "性", "调教", "逼", "情趣", "色", "裸", "性爱", "成人", "老婆", "少妇")
        return denied.none { title.contains(it, ignoreCase = true) }
    }

    /** Nuxt 扁平数组：整数值视为下标，解引用一次；其余原样返回。 */
    private fun deref(arr: JsonArray, e: JsonElement?): JsonElement? {
        val p = e as? JsonPrimitive ?: return e
        if (!p.isString && p.content.matches(Regex("\\d+"))) {
            return p.content.toIntOrNull()?.let { arr.getOrNull(it) } ?: e
        }
        return e
    }

    private fun classPathOf(typeId: Int?): String = CLASS_PATHS[typeId] ?: "yuanchuang"
}
