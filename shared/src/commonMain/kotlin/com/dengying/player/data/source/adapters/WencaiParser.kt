package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 文才（mw-movie 匿名接口）的纯解析层。
 *
 * 与 [com.dengying.player.data.source.adapters.WencaiAdapter] 的分界：
 * - 这里只做「响应体 → 领域对象」，不碰 HTTP、不碰签名时间戳
 * - 签名（`t` / `sign`）与 URL 拼装留在 `:app` 的 adapter，因为
 *   `SourceSecrets` 的密钥常量与 `HttpClient` 都还在 `:app`（批 D 才搬）
 *
 * ## 与迁移前的差异
 * 迁移前的 `WencaiAdapter` 用 `org.json`。本文件的 org.json 语义全部由
 * [JsonX] 提供（`optInt` / `optString` / `optJSONObject` / `optJSONArray`
 * 的宽松规则，含数字字符串、`null` 字面量、类型不符降级为默认值）。
 *
 * 只有一处必须显式处理：**`nid` 是数字或字符串双态**。
 * 原代码写的是 `when (nidRaw) { is Number -> toLong().toString(); else -> toString() }`，
 * `JsonX` 里对应的写法必须是 `JsonX.isString` 判断 + `JsonX.textOf`，
 * 否则 `JsonPrimitive(1050147)` 会被 `str` 当成 `"1050147"`（其实结果一样，
 * 但整数在 kotlinx 里不带小数点，浮点会带 `.0` —— 见下方单测钉住的边界）。
 */
object WencaiParser {

    /** 文才接口把 `code` 放在响应体外层，缺省按成功处理（与 `optInt("code", 200)` 一致） */
    const val OK_CODE = 200

    private const val PLAY_FROM = "文采蓝光"

    fun failed(
        source: CmsSource,
        page: Int,
        err: String,
        outcome: RequestOutcome = RequestOutcome.RequestFailed,
    ): FetchResult = FetchResult(
        outcome = outcome, page = page, sourceName = source.name, err = err,
    )

    // ---------------- 搜索 ----------------

    /**
     * 搜索响应 → [FetchResult]。
     *
     * 容器路径固定为 `data.result.list`（不像山楂有多级回退），
     * `total` 取 `data.result.totalCount`，缺省回落条目数。
     */
    fun parseSearch(source: CmsSource, body: String?): FetchResult {
        val raw = JsonX.parseObject(body) ?: return failed(source, 1, "响应非 JSON")
        val code = JsonX.int(raw, "code", OK_CODE)
        if (code != OK_CODE) return failed(source, 1, "code $code")

        val block = JsonX.optObject(JsonX.optObject(raw, "data"), "result")
        val rows = JsonX.optArray(block, "list")

        val items = ArrayList<VideoItem>(JsonX.len(rows))
        for (i in 0 until JsonX.len(rows)) {
            val row = JsonX.optObjectAt(rows, i) ?: continue
            val id = JsonX.long(row, "vodId", 0L)
            if (id <= 0) continue
            items += VideoItem(
                vod_id = id,
                vod_name = JsonX.str(row, "vodName", ""),
                vod_pic = JsonX.str(row, "vodPic", ""),
                vod_remarks = JsonX.str(row, "vodRemarks", ""),
                _source = source.key,
            )
        }

        val outcome = if (items.isNotEmpty()) {
            RequestOutcome.SuccessWithData
        } else {
            RequestOutcome.SuccessNoData
        }
        return FetchResult(
            outcome = outcome,
            items = items,
            page = 1,
            pageCount = 1,
            total = JsonX.int(block, "totalCount", items.size),
            sourceName = source.name,
        )
    }

    // ---------------- 详情 ----------------

    /**
     * 详情响应 → [VideoItem]。
     *
     * `episodeList[i].nid` 是换链 token（不是 URL），拼成 `"$name$$$nid"` 的形状
     * 交给 `resolvePlay`。集名为空时依次回落 `name` → `title` → `"第 N 集"`。
     *
     * 非 200 code、缺 `data`、`episodeList` 为空都返回 `null`（原实现的既有语义）。
     */
    fun parseDetail(source: CmsSource, body: String?, vodId: Long): VideoItem? {
        val raw = JsonX.parseObject(body) ?: return null
        val code = JsonX.int(raw, "code", OK_CODE)
        if (code != OK_CODE) return null
        val d = JsonX.optObject(raw, "data") ?: return null

        val eps = JsonX.optArray(d, "episodeList")
        val parts = ArrayList<String>(JsonX.len(eps))
        for (i in 0 until JsonX.len(eps)) {
            val e = JsonX.optObjectAt(eps, i) ?: continue
            val nid = nidOf(e)
            if (nid.isEmpty()) continue
            val name = JsonX.str(e, "name", "").ifEmpty {
                JsonX.str(e, "title", "").ifEmpty { "第${i + 1}集" }
            }
            parts += "$name\$$nid"
        }

        return VideoItem(
            vod_id = vodId,
            vod_name = JsonX.str(d, "vodName", ""),
            vod_pic = JsonX.str(d, "vodPic", ""),
            vod_actor = JsonX.str(d, "vodActor", ""),
            vod_remarks = JsonX.str(d, "vodRemarks", ""),
            vod_content = stripHtml(JsonX.str(d, "vodContent", "")),
            vod_play_from = if (parts.isNotEmpty()) PLAY_FROM else "",
            vod_play_url = parts.joinToString("#"),
            _source = source.key,
        )
    }

    /**
     * `nid` 是数字或字符串双态。
     *
     * 原实现：`when (nidRaw) { null, JSONObject.NULL -> ""; is Number ->
     * nidRaw.toLong().toString(); else -> nidRaw.toString() }`。
     *
     * 差别在**浮点**：`1234.9` 在 org.json 版落到 `is Number` 分支 → `1234`；
     * 若直接用 `JsonX.textOf` 会得到字符串 `"1234.9"`。所以这里先判`isNumber`
     * 走 `JsonX.longOf`，其余才走 `textOf`。
     */
    fun nidOf(row: JsonObject?): String {
        val e = row?.get("nid") ?: return ""
        if (JsonX.isNull(e)) return ""
        val p = e as? JsonPrimitive
        if (p != null && p.isString) return p.content
        return JsonX.longOf(e, 0L).toString()
    }

    // ---------------- 换链 ----------------

    /**
     * 播放换链响应 → [PlayResolveResult]。
     *
     * 取 `data.list[0].url` 并 `trim()`。`token` 可能是纯 `nid`，也可能是带
     * 前缀的绝对地址（那种情况下 `nid` = 最后一个 `/` 之后的部分）。
     * 请求头为空（文才的 m3u8 是直连，不需要 Referer/UA）。
     */
    fun parsePlay(body: String?): PlayResolveResult {
        val raw = JsonX.parseObject(body) ?: return PlayResolveResult()
        val list = JsonX.optArray(JsonX.optObject(raw, "data"), "list")
        if (JsonX.len(list) == 0) return PlayResolveResult()
        val u = JsonX.str(JsonX.optObjectAt(list, 0), "url", "").trim()
        return PlayResolveResult(url = u, headers = "")
    }

    /**
     * 从换链 token 里取出 `nid`：带 `http` 前缀时取最后一段路径，否则原样返回。
     *
     * 原实现写在内联 `if (token.startsWith("http"))`，这里提成函数便于单测。
     */
    fun nidOfToken(token: String): String {
        if (token.isEmpty()) return ""
        return if (token.startsWith("http")) {
            token.substringAfterLast('/').ifEmpty { token }
        } else {
            token
        }
    }

    // ---------------- 简介去 HTML ----------------

    /**
     * 文才的 `vodContent` 带 `<p>` 标签，直接渲染会在详情页露出标签文本。
     *
     * 顺序不能乱：先换行标签 → 再删其余标签 → 再解实体 → 最后压空行。
     * 全部分支大小写不敏感（原实现用 `RegexOption.IGNORE_CASE`）。
     */
    fun stripHtml(value: String): String = value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
        .replace(Regex("&lt;", RegexOption.IGNORE_CASE), "<")
        .replace(Regex("&gt;", RegexOption.IGNORE_CASE), ">")
        .replace(Regex("&quot;", RegexOption.IGNORE_CASE), "\"")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
