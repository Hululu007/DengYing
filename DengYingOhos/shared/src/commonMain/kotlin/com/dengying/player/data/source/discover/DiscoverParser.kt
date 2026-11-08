package com.dengying.player.data.source.discover

import com.dengying.player.core.DiscoverFeed
import com.dengying.player.core.DiscoverItem
import com.dengying.player.core.DiscoverKind
import com.dengying.player.data.json.JsonX

/**
 * 发现层解析 —— **纯 Kotlin，无平台依赖**
 *
 * 只做「响应字节 → [DiscoverItem] 列表」，发请求在 DiscoverAdapter。
 * 两个端点的响应形态差异很大，全部差异都吸收在这里，上层只认 [DiscoverItem]。
 *
 * JSON 取值一律走 [JsonX]（逐条对齐 `org.json` 的 `optString/optLong/optObject` 语义），
 * 与 P3 起全仓其余解析层一致。
 *
 * 设计依据：`docs/04-验证记录.md` §15.6.1。
 */
object DiscoverParser {

    /** 爱奇艺联想成功码（**字符串**，不是数字 200） */
    const val IQIYI_OK_CODE: String = "A00000"

    /**
     * 爱奇艺搜索联想。
     *
     * 响应形态（2026-10-27 实测）：
     * ```
     * {"code":"A00000","data":[{...}],"attributes":{...},"show_query_count":20}
     * ```
     *
     * `data` 里混着两类条目，靠 `aid > 0` 区分：
     *  - 实体（`aid`/`vid` > 0、有 `link`/`picture_url`/`cid`/`region`/`year`/`duration`）
     *  - 纯联想词（`aid`/`vid`/`cid` = 0、`link`/`picture_url` 空串，只有 `name` 与
     *    `presentation_element`）。联想词的 `year` 一律是当前年，不可信。
     *
     * `Content-Type` 是 `text/html` 但 body 是合法 JSON，故只看 body 不看响应头。
     */
    fun parseIqiyiSuggest(body: String?): DiscoverFeed {
        val raw = JsonX.parseObject(body)
            ?: return DiscoverFeed(DiscoverKind.IqiyiSuggest, err = "响应非 JSON")
        val code = JsonX.str(raw, "code")
        if (code != IQIYI_OK_CODE) {
            return DiscoverFeed(DiscoverKind.IqiyiSuggest, err = "code $code")
        }

        val rows = JsonX.optArray(raw, "data")
        val items = ArrayList<DiscoverItem>(JsonX.len(rows))
        for (i in 0 until JsonX.len(rows)) {
            val row = JsonX.optObjectAt(rows, i) ?: continue
            val name = JsonX.str(row, "name")
            if (name.isEmpty()) continue
            // 判据只认 aid > 0：实测 aid 与 vid 同值，但 aid 是「作品 id」的语义名。
            // 不用 link.isEmpty() 判——联想词现在恰好没链接，将来若有链接该判据就失效。
            val aid = JsonX.long(row, "aid", 0L)
            items += DiscoverItem(
                title = name,
                subtitle = JsonX.str(row, "cname"),
                pic = JsonX.str(row, "picture_url"),
                year = JsonX.str(row, "year"),
                link = JsonX.str(row, "link"),
                externalId = if (aid > 0L) aid.toString() else "",
                kind = DiscoverKind.IqiyiSuggest,
                isEntity = aid > 0L,
            )
        }
        return DiscoverFeed(kind = DiscoverKind.IqiyiSuggest, items = items)
    }

    /**
     * 豆瓣动漫榜单。
     *
     * 正常响应：
     * ```
     * {"data":[{"directors":[...],"rate":"9.0","star":"45","title":"...","url":"...",
     *           "casts":[...],"cover":"...","id":"35118256","cover_x":1365,"cover_y":2048}]}
     * ```
     * 行键固定 10 个：`directors` / `rate` / `cover_x` / `star` / `title` / `url` /
     * `casts` / `cover` / `id` / `cover_y`。**没有年份字段**，故 [DiscoverItem.year] 恒空。
     *
     * 风控响应（同 IP 短时间第二次请求）：
     * ```
     {"msg":"检测有异常请求从您的IP发出，请登录再试!","r":1}
     * ```
     * 没有 `data` 字段。这种「本次拿不到、稍后再试」不算源故障，故不重试、不换 UA 绕，
     * 直接把站点的 msg 透给用户（见 §15.6.1 为何不并入 `FetchResult` 的四态）。
     */
    fun parseDoubanAnime(
        body: String?,
        page: Int = 0,
        /** 顶层数组键名。`new_search_subjects` 是 `data`，老端点 `search_subjects` 是 `subjects` */
        arrayKey: String = "data",
    ): DiscoverFeed {
        val raw = JsonX.parseObject(body)
            ?: return DiscoverFeed(DiscoverKind.DoubanAnime, page = page, err = "响应非 JSON")

        val rows = JsonX.optArray(raw, arrayKey)
        if (rows == null) {
            // 风控或其它拒绝形态：把站点原话透给用户，没有 msg 时给通用文案。
            val msg = JsonX.str(raw, "msg")
            return DiscoverFeed(
                kind = DiscoverKind.DoubanAnime,
                page = page,
                err = msg.ifEmpty { "榜单暂不可用，请稍后再试" },
            )
        }

        val items = ArrayList<DiscoverItem>(JsonX.len(rows))
        for (i in 0 until JsonX.len(rows)) {
            val row = JsonX.optObjectAt(rows, i) ?: continue
            val title = JsonX.str(row, "title")
            if (title.isEmpty()) continue
            items += DiscoverItem(
                title = title,
                // rate 是字符串（"9.0"），无评分时字段缺失或空串 ⇒ 副标题空
                subtitle = JsonX.str(row, "rate"),
                pic = JsonX.str(row, "cover"),
                // 豆瓣没有年份字段，恒空
                year = "",
                link = JsonX.str(row, "url"),
                externalId = JsonX.str(row, "id"),
                kind = DiscoverKind.DoubanAnime,
                // 豆瓣榜单条目全部是实体作品
                isEntity = true,
            )
        }
        return DiscoverFeed(kind = DiscoverKind.DoubanAnime, items = items, page = page)
    }
}
