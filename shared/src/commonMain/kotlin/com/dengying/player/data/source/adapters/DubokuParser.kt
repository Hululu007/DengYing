package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.source.crypto.Digest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 独播库（dbokutv）响应解析 —— 纯逻辑，无 HTTP（KMP P3 批 C-5）
 *
 * 从 `:app` 的 `DubokuAdapter` 里切出来，逐行等价迁移自 org.json 实现。
 *
 * ## 这个源的三个特殊之处
 *
 * 1. **搜索响应是裸数组**，不是 `{code,data}` 信封。所以解析入口用
 *    [JsonX.parseArray]，顶层是对象时直接判失败。
 * 2. **`DId` / `TnId` / `VId` / `HId` 都是编码过的字符串**，需经
 *    [Digest.dubokuUnscramble]（每 10 字符一组反转 + Base64，`=` 用 `.` 代替）。
 * 3. **`vod_remarks` 有回退链**：`Tag` 非空就用 `Tag`，否则用 `Rating` 拼成
 *    「N 分」。`Rating` 是数字，所以要按 org.json 的 `opt` 语义取文本。
 *
 * ## HTTP 侧留在 `:app`
 *
 * 连接超时、`URLEncoder`、`HttpClient.UA_PC`、签名时间戳（`JavaRandom.dubokuSignParam`
 * 需要 `Clock.nowMs()`）都还在 `DubokuAdapter`。本对象只处理「已经拿到的 HTTP 响应体」。
 */
object DubokuParser {

    /** 详情线路名（源展示名，不是接口字段） */
    private const val PLAY_FROM = "独播库"

    fun failed(
        source: CmsSource,
        page: Int,
        err: String,
        outcome: RequestOutcome = RequestOutcome.RequestFailed,
    ) = FetchResult(outcome = outcome, page = page, sourceName = source.name, err = err)

    /**
     * 从路径末尾取数字 id：`/voddetail/4570` → `4570`。
     *
     * 与原实现一致：取最后一个 `/` 之后的部分，`trim()` 后 `toLongOrNull()`，
     * 失败返回 0。注意这与 `WebParser.trailingNumber` **不同**（那个用正则找
     * 最后一段数字，能穿透查询串），两个源的路径形态不一样，不要互相同化。
     */
    fun trailingNumber(path: String): Long {
        if (path.isEmpty()) return 0
        val idx = path.lastIndexOf('/')
        val tail = if (idx >= 0) path.substring(idx + 1) else path
        return tail.trim().toLongOrNull() ?: 0L
    }

    /**
     * 搜索：`GET /vodsearch{sign}&wd=` → **裸数组** `[{Name, Rating, Tag, DId, TnId}, ...]`
     *
     * 顶层不是数组时判 [RequestOutcome.ParseFailed]（原实现用 `safeJsonArray` 返回 null
     * 表达「响应非数组」）。空数组是 [RequestOutcome.SuccessNoData]。
     */
    fun parseSearch(source: CmsSource, body: String?): FetchResult {
        val arr = JsonX.parseArray(body)
            ?: return failed(source, 1, "响应非数组", RequestOutcome.ParseFailed)

        val items = ArrayList<VideoItem>(JsonX.len(arr))
        for (i in 0 until JsonX.len(arr)) {
            val row = JsonX.optObjectAt(arr, i) ?: continue
            val detailPath = Digest.dubokuUnscramble(JsonX.str(row, "DId", ""))
            val vodId = trailingNumber(detailPath)
            if (vodId <= 0) continue
            items += VideoItem(
                vod_id = vodId,
                vod_name = JsonX.str(row, "Name", ""),
                vod_pic = Digest.dubokuUnscramble(JsonX.str(row, "TnId", "")),
                vod_remarks = ratingOf(row),
                _source = source.key,
            )
        }
        val outcome =
            if (items.isNotEmpty()) RequestOutcome.SuccessWithData else RequestOutcome.SuccessNoData
        return FetchResult(
            outcome = outcome, items = items, page = 1, pageCount = 1,
            total = items.size, sourceName = source.name,
        )
    }

    /**
     * `vod_remarks` 的回退链：`Tag` 非空用它；否则 `Rating` 非 null 就拼「N 分」。
     *
     * 原实现的 `Rating` 分支是 `"$rating 分"`，其中 `rating` 是 org.json 的 `opt()`
     * 返回值（`Any`）。数字插值结果与 `JsonX.textOf` 一致：`8.5` → `"8.5 分"`、
     * `8` → `"8 分"`。**已知边界**：科学计数法数字（如 `1e10`）org.json 得
     * `"1.0E10"` 而 `textOf` 得 `"1e10"`；评分值域 0–10，不触发。
     */
    private fun ratingOf(row: JsonObject): String {
        val tag = JsonX.str(row, "Tag", "")
        if (tag.isNotEmpty()) return tag
        val rating: JsonElement? = row["Rating"]
        if (rating == null || JsonX.isNull(rating)) return ""
        return "${JsonX.textOf(rating, "")} 分"
    }

    /**
     * 详情：`GET /voddetail/{id}{sign}` → `{Name, Playlist:[{EpisodeName, VId}], ...}`
     *
     * `Playlist[].VId` 解码后是 `/vodplay/4570-1-1` 这样的**相对路径**，这里补成
     * `${source.api}$playPath` 的绝对地址——相对路径会被 `parseEpisodes` 的 `http`
     * 校验丢掉，导致详情页线路数为 0（原实现已踩过这个坑，注释保留）。
     */
    fun parseDetail(source: CmsSource, body: String?, vodId: Long): VideoItem? {
        if (vodId <= 0) return null
        val raw = JsonX.parseObject(body) ?: return null

        val actors = JsonX.optArray(raw, "Actor")
        val actorText = buildString {
            if (actors != null) {
                for (i in 0 until JsonX.len(actors)) {
                    if (i > 0) append(',')
                    append(JsonX.strAt(actors, i, ""))
                }
            }
        }

        val list = JsonX.optArray(raw, "Playlist")
        val parts = ArrayList<String>()
        if (list != null) {
            for (i in 0 until JsonX.len(list)) {
                val item = JsonX.optObjectAt(list, i) ?: continue
                val playPath = Digest.dubokuUnscramble(JsonX.str(item, "VId", ""))
                if (playPath.isEmpty()) continue
                val name = JsonX.str(item, "EpisodeName", "").ifEmpty { "第${i + 1}集" }
                parts += "$name\$${source.api}$playPath"
            }
        }

        val year = JsonX.str(raw, "ReleaseYear", "")
        val director = JsonX.str(raw, "Director", "")
        val remarks = if (director.isNotEmpty()) "$year 导演:$director" else year

        return VideoItem(
            vod_id = vodId,
            vod_name = JsonX.str(raw, "Name", ""),
            vod_pic = "",
            vod_remarks = remarks,
            vod_actor = actorText,
            vod_content = JsonX.str(raw, "Description", ""),
            vod_play_from = if (parts.isNotEmpty()) PLAY_FROM else "",
            vod_play_url = parts.joinToString("#"),
            _source = source.key,
        )
    }

    /**
     * 播放换链的响应：`GET {target}{sign}` → `{HId}`，解码即最终 m3u8 地址。
     *
     * 文才的 m3u8 直连不需要请求头，独播库同样返回空 headers。
     */
    fun parsePlay(body: String?): PlayResolveResult {
        val raw = JsonX.parseObject(body) ?: return PlayResolveResult()
        return PlayResolveResult(
            url = Digest.dubokuUnscramble(JsonX.str(raw, "HId", "")),
            headers = "",
        )
    }

    /**
     * 换链目标地址：token 是绝对地址就原样用，否则拼 `source.api` 前缀。
     *
     * 空 token 返回 `""`，调用方据此提前返回空 [PlayResolveResult]（不发请求）。
     */
    fun resolveTarget(source: CmsSource, token: String): String {
        if (token.isEmpty()) return ""
        return if (token.startsWith("http")) token else "${source.api}$token"
    }
}
