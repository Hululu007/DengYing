package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsClass
import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX
import kotlinx.serialization.json.JsonObject

/**
 * 苹果CMS 协议（含 api2233 变体）的**纯解析层**。
 *
 * 分界线与 `MinoParser` 一致：本文件只做「响应文本 → 结果对象」，不发请求、不看状态码。
 * 这样解析规则可以用金样本离线钉住，而网络部分留在 `:app` 的 `CmsAdapter`。
 *
 * KMP P3：原实现用 `org.json`，行为细节（`optString` 对 null 字面量返回 `"null"`、
 * 数字自动转文本、`optInt` 对 `"12"` 也能取到 12）由 [JsonX] 逐条复刻。
 * 迁移前该文件有 85 处 org.json 调用（`optString` 17、`optJSONObject` 8、`optInt` 6 …），
 * 是 9 个适配器里最大的一处。
 *
 * 【协议字段名保持 `vod_*` 原样】不做驼峰转换——转换一次就多一处「哪一层负责映射」的歧义。
 */
object CmsParser {

    /**
     * 失败结果（保留 `err` 供页面提示）。
     *
     * FR-108：**区分两种失败**——`RequestFailed`（未拿到可用响应）与
     * `ParseFailed`（拿到 2xx 但结构不合规）。两者都触发故障转移，
     * 但对用户的含义不同（网络问题 vs 站点改版）。
     */
    fun failed(
        source: CmsSource,
        page: Int,
        err: String,
        outcome: RequestOutcome = RequestOutcome.RequestFailed,
    ): FetchResult = FetchResult(
        outcome = outcome,
        page = page,
        sourceName = source.name,
        err = err,
    )

    /** 非 JSON 响应：与「HTTP 失败」区分开，故单独给一个入口 */
    private fun parseFailed(source: CmsSource, page: Int): FetchResult =
        failed(source, page, "响应非 JSON", RequestOutcome.ParseFailed)

    /**
     * 标准响应 → 内部结果（`list` 逐项打 `_source`）。
     *
     * FR-108：**空列表是 `SuccessNoData`，不是失败**。
     *
     * 入参为响应文本而非已解析对象：`:app` 那边原本先 `safeJson` 再传对象，
     * 现在把「解析失败」也收进来，少一处 `?: return` 的机会。
     */
    fun fill(body: String, source: CmsSource, page: Int): FetchResult {
        val raw = JsonX.parseObject(body) ?: return parseFailed(source, page)
        return fillParsed(raw, source, page)
    }

    /** 已经解析成对象的分支（搜索路径要先判 `hasData` 再决定用哪个端点） */
    private fun fillParsed(raw: JsonObject, source: CmsSource, page: Int): FetchResult {
        val list = ArrayList<VideoItem>()
        val arr = JsonX.optArray(raw, "list")
        if (arr != null) {
            for (i in 0 until JsonX.len(arr)) {
                val o = JsonX.optObjectAt(arr, i) ?: continue
                list += parseItem(o, source.key)
            }
        }
        val outcome =
            if (list.isNotEmpty()) RequestOutcome.SuccessWithData else RequestOutcome.SuccessNoData
        return FetchResult(
            outcome = outcome,
            items = list,
            page = JsonX.int(raw, "page", page),
            pageCount = JsonX.int(raw, "pagecount", 1),
            total = JsonX.int(raw, "total", list.size),
            sourceName = source.name,
        )
    }

    /** 单条 JSON → VideoItem（字段缺失一律给空串，不用 null 传染到 UI） */
    fun parseItem(o: JsonObject, sourceKey: String): VideoItem = VideoItem(
        vod_id = JsonX.long(o, "vod_id", 0L),
        vod_name = JsonX.str(o, "vod_name", ""),
        vod_pic = JsonX.str(o, "vod_pic", ""),
        vod_remarks = JsonX.str(o, "vod_remarks", ""),
        vod_year = JsonX.str(o, "vod_year", ""),
        vod_area = JsonX.str(o, "vod_area", ""),
        vod_lang = JsonX.str(o, "vod_lang", ""),
        vod_actor = JsonX.str(o, "vod_actor", ""),
        vod_director = JsonX.str(o, "vod_director", ""),
        vod_content = JsonX.str(o, "vod_content", ""),
        vod_play_from = JsonX.str(o, "vod_play_from", ""),
        vod_play_url = JsonX.str(o, "vod_play_url", ""),
        type_name = JsonX.str(o, "type_name", ""),
        vod_uri = JsonX.str(o, "vod_uri", ""),
        _source = sourceKey,
    )

    /** 详情响应解析：`list` 空或首项缺失都返回 null */
    fun detail(body: String, source: CmsSource, vodId: Long): VideoItem? {
        val raw = JsonX.parseObject(body) ?: return null
        val arr = JsonX.optArray(raw, "list") ?: return null
        if (JsonX.len(arr) == 0) return null
        val o = JsonX.optObjectAt(arr, 0) ?: return null
        return parseItem(o, source.key)
    }

    /**
     * 分类表响应（ac=list）
     *
     * `type_pid` 必须是「字段存在且不是 null」才给非 null 值：顶级分类不下发该字段，
     * 若直接 `optInt(..., 0)` 会把顶级和「父 id 真是 0」混成一样。
     */
    fun classes(body: String): List<CmsClass> {
        val raw = JsonX.parseObject(body) ?: return emptyList()
        val arr = JsonX.optArray(raw, "class") ?: return emptyList()
        val out = ArrayList<CmsClass>(JsonX.len(arr))
        for (i in 0 until JsonX.len(arr)) {
            val o = JsonX.optObjectAt(arr, i) ?: continue
            out += CmsClass(
                type_id = JsonX.int(o, "type_id", 0),
                type_name = JsonX.str(o, "type_name", ""),
                type_pid = if (JsonX.has(o, "type_pid") && !JsonX.isNull(o["type_pid"])) {
                    JsonX.int(o, "type_pid", 0)
                } else {
                    null
                },
            )
        }
        return out
    }

    // ---------------- api2233 变体 ----------------

    /**
     * api2233 搜索响应：非标端点 + `info.vod.list` 嵌套。
     *
     * 该源是短剧站，长剧/老片 0 命中属内容池特征，不是故障——故空列表判 `SuccessNoData`。
     */
    fun api2233Search(body: String, source: CmsSource): FetchResult {
        val raw = JsonX.parseObject(body) ?: return parseFailed(source, 1)

        val info = JsonX.optObject(raw, "info")
        val vod = if (info == null) null else JsonX.optObject(info, "vod")
        val rows = if (vod == null) null else JsonX.optArray(vod, "list")

        val items = ArrayList<VideoItem>(if (rows == null) 0 else JsonX.len(rows))
        if (rows != null) {
            for (i in 0 until JsonX.len(rows)) {
                val row = JsonX.optObjectAt(rows, i) ?: continue
                val id = JsonX.long(row, "id", 0L)
                if (id <= 0) continue
                items += VideoItem(
                    vod_id = id,
                    vod_name = JsonX.str(row, "name", ""),
                    vod_pic = JsonX.str(row, "pic", ""),
                    vod_remarks = JsonX.str(row, "remarks", ""),
                    _source = source.key,
                )
            }
        }
        val total = if (vod == null) items.size else JsonX.int(vod, "total", items.size)
        val outcome =
            if (items.isNotEmpty()) RequestOutcome.SuccessWithData else RequestOutcome.SuccessNoData
        return FetchResult(
            outcome = outcome, items = items, page = 1, pageCount = 1,
            total = total, sourceName = source.name,
        )
    }
}
