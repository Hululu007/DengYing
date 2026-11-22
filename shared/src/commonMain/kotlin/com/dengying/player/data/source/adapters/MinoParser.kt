package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX

/**
 * 米米4k（MINO）解析层 —— **纯 Kotlin，无平台依赖**
 *
 * 原本解析逻辑长在 [MinoAdapter] 里，与「发请求」挤在同一个函数。P3 换 JSON 实现时
 * 把两者切开：适配器只管网络，本文件只管字节 → 数据模型，于是同一批真实响应样本
 * 可以脱离网络反复跑。
 *
 * **JSON 取值一律走 [JsonX]**，不直接用 `kotlinx.serialization.json` 的 API——
 * `JsonX` 逐条对齐了 `org.json` 的 `optString/optInt/optLong/optObject` 语义
 * （缺失给默认值、`null` 字面量给 `"null"`、数字自动转文本等）。绕过它直接取字段，
 * 会把这些细节重新引进来。迁移依据：`docs/09-KMP多平台迁移方案.md` §5.1。
 *
 * 该类**没有平台依赖**，因此可以留在 `commonMain` 被 iOS/鸿蒙复用。
 */
object MinoParser {

    /** `/parse` 配额用尽业务码（40311「今日免费次数已用完」，换 X-Device-ID 即重置） */
    const val QUOTA_CODE = 40311

    /**
     * 搜索响应 → 结果。
     *
     * 字段名多候选（`vod_id`/`id`、`vod_pic`/`pic`）是真实现状：该源早期返回
     * 苹果 CMS 风格字段，后期改成简写，两种都还在线上出现过。
     */
    fun search(body: String, source: CmsSource): FetchResult {
        val raw = JsonX.parseObject(body)
            ?: return failed(source, "响应非 JSON", RequestOutcome.ParseFailed)
        val code = JsonX.int(raw, "code", 0)
        if (code != 0) return failed(source, "code $code")

        val data = JsonX.optObject(raw, "data")
        val rows = JsonX.optArray(data, "list")
        val items = ArrayList<VideoItem>(JsonX.len(rows))
        for (i in 0 until JsonX.len(rows)) {
            val row = JsonX.optObjectAt(rows, i) ?: continue
            // 逐字对应原写法 `row.opt("vod_id") ?: row.opt("id")`：
            // 只有「字段不存在」才转到 id；字段存在但值为 0 时**不**回退
            // （org.json 的 opt 只对缺失返回 null，firstLong 会在 0 时继续找下一个，
            //   语义不同，故这里不用 firstLong）。
            val id = if (JsonX.has(row, "vod_id")) {
                JsonX.long(row, "vod_id", 0L)
            } else {
                JsonX.long(row, "id", 0L)
            }
            if (id <= 0) continue
            items += VideoItem(
                vod_id = id,
                vod_name = JsonX.str(row, "vod_name").ifEmpty { JsonX.str(row, "title") },
                vod_pic = JsonX.str(row, "vod_pic").ifEmpty { JsonX.str(row, "pic") },
                vod_remarks = JsonX.str(row, "vod_remarks")
                    .ifEmpty { JsonX.str(row, "vod_year").ifEmpty { "更新中" } },
                _source = source.key,
            )
        }
        val outcome =
            if (items.isNotEmpty()) RequestOutcome.SuccessWithData else RequestOutcome.SuccessNoData
        return FetchResult(
            outcome = outcome,
            items = items,
            page = 1,
            pageCount = 1,
            total = JsonX.int(data, "total", items.size),
            sourceName = source.name,
        )
    }

    /** 详情响应 → 条目；`code != 0` 或结构不合规返回 `null` */
    fun detail(body: String, vodId: Long, source: CmsSource): VideoItem? {
        val raw = JsonX.parseObject(body) ?: return null
        if (JsonX.int(raw, "code", 0) != 0) return null
        val v = JsonX.optObject(raw, "data") ?: return null

        return VideoItem(
            vod_id = vodId,
            vod_name = JsonX.str(v, "vod_name"),
            vod_pic = JsonX.str(v, "vod_pic"),
            vod_remarks = JsonX.str(v, "vod_remarks").ifEmpty { JsonX.str(v, "vod_year") },
            vod_actor = JsonX.str(v, "vod_actor").ifEmpty { JsonX.str(v, "vod_director") },
            vod_content = stripHtml(
                JsonX.str(v, "vod_content").ifEmpty { JsonX.str(v, "vod_blurb") }
            ),
            vod_play_from = JsonX.str(v, "vod_play_from"),
            vod_play_url = JsonX.str(v, "vod_play_url"),
            _source = source.key,
        )
    }

    /**
     * 构造 `/parse` 请求体。
     *
     * **`vod_id` 必须是整数**——传字符串会被服务端拒绝（接口清单 §1.1 的实测结论）。
     * 序列化实现换代最容易在这里漂移（把 Long 写成 `"7194"`），故由金样本单测钉住。
     */
    fun buildParseBody(token: String, from: String, vodId: Long): String =
        JsonX.buildObject(
            "url" to token,
            "from" to from,
            "vod_id" to vodId,
        ).toString()

    /**
     * `/parse` 游客每日配额用尽（code:40311「今日免费次数已用完」）。
     *
     * 配额绑在 `X-Device-ID` 头上——缺失时是 40110「请登录后观看」（曾因此把该源
     * 误判为需登录），用尽时是 40311；换新设备号即重置。适配器据此换号重试一次。
     */
    fun quotaExceeded(body: String): Boolean {
        val raw = JsonX.parseObject(body) ?: return false
        return JsonX.int(raw, "code", 0) == QUOTA_CODE
    }

    /** `/parse` 响应 → 播放地址 */
    fun parsePlay(body: String): PlayResolveResult {
        val raw = JsonX.parseObject(body) ?: return PlayResolveResult()
        if (JsonX.int(raw, "code", 0) != 0) return PlayResolveResult()
        val d = JsonX.optObject(raw, "data") ?: return PlayResolveResult()
        val url = JsonX.str(d, "url")
            .ifEmpty { JsonX.str(d, "play_url") }
            .ifEmpty { JsonX.str(d, "playUrl") }
        return PlayResolveResult(url = url.trim(), headers = "")
    }

    /** 简介去 HTML（该源返回 `<p>`/`<br>` 标签） */
    fun stripHtml(value: String): String = value
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
        .trim()

    private fun failed(
        source: CmsSource,
        err: String,
        outcome: RequestOutcome = RequestOutcome.RequestFailed,
    ) = FetchResult(outcome = outcome, page = 1, sourceName = source.name, err = err)
}
