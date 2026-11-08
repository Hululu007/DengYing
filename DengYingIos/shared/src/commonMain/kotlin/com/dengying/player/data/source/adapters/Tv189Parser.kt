package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsClass
import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.source.crypto.Digest

/**
 * KMP P3：天翼视讯（tv189）纯解析层 —— 从 `:app` 的 `Tv189Adapter` 切出。
 *
 * 切分边界与其它源一致：
 * - 本文件只做**无平台依赖**的事：分类表、签名原文、响应解析。
 * - `:app` 侧 `Tv189Adapter` 保留 HTTP 与三样平台 API：
 *   `java.util.UUID`（cltrid）、`java.text.SimpleDateFormat` + `java.util.Date`（cltts）、
 *   `java.net.URLEncoder`（参数编码，批 F 换 expect/actual）。
 *
 * 迁移前的 org.json 调用 12 处，语义全部由 [JsonX] 承接。
 * 迁移前的 `System.arraycopy` / `java.util` 用法集中在 `:app`，本文件一处没有。
 *
 * 【为什么列表固定带 productId】见 [FREE_PRODUCT_ID] 注释。
 */
object Tv189Parser {

    /**
     * 只出「全片免费」内容的产品包 id。
     *
     * 该 productId 只出全片免费内容（authPlayVod 返回 code=0、playUrl 无 `end=` 截断）。
     * 不过滤的话大部分电影/电视剧是 VIP 内容，点进去只有 300 秒试看
     * （playUrl 带 `end=300`，服务端按签名截断，去掉参数无效）。
     * 在多源聚合里这个源的定位是「免费整片补充」，宁少而精：
     * 免费存量实测 电影 589 / 电视剧 72 / 少儿 586 / 动漫 123 / 短剧 21。
     */
    const val FREE_PRODUCT_ID = "1000000218"

    /** programsearch 每页条数（原实现硬编码 20，pageCount 也按 20 计算） */
    private const val PAGE_SIZE = 20

    /**
     * 静态分类表：DengYing 标准分类名 → tv189 categoryId（实测 2026-10-24）。
     *
     * tv189 没有分类表接口（categoryId 是前端硬编码常量），这里静态返回，
     * `SourceRepository.fetchClasses` 按协议分派到本表，resolveOne 的分类名匹配逻辑原样复用。
     */
    val CLASSES = listOf(
        CmsClass(type_id = 2, type_name = "电影"),
        CmsClass(type_id = 3, type_name = "电视剧"),
        CmsClass(type_id = 11, type_name = "综艺"),
        CmsClass(type_id = 6, type_name = "动漫"),
        CmsClass(type_id = 5214, type_name = "短剧"),
        CmsClass(type_id = 229, type_name = "记录片"),
    )

    /** 供聚合层静态分类分派使用（见 SourceRepository.fetchClasses） */
    fun classes(): List<CmsClass> = CLASSES

    /**
     * `"C47919262"` → `47919262`；解析失败返回 0（调用方跳过该条）。
     *
     * 只剥前缀 `C`，不做其它清洗 —— 迁移前就是 `removePrefix("C").toLongOrNull() ?: 0L`。
     */
    fun cidToLong(cid: String): Long = cid.removePrefix("C").toLongOrNull() ?: 0L

    /**
     * 签名原文：`k1=v1&k2=v2...（key 升序，值不编码）` + `&cltts=` + cltts + `&`。
     *
     * secret 为空串 → **无密钥，客户端可自行生成**，故这是纯函数。
     * `cltrid` 也参与排序（它在 `p` 里），调用方必须把它放进 [params]。
     */
    fun signRaw(params: Map<String, String>, cltts: String): String {
        val raw = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return "$raw&cltts=$cltts&"
    }

    /** `cltkey = md5(签名原文)`（小写 hex，与 `Digest.md5Hex` 一致） */
    fun cltKey(params: Map<String, String>, cltts: String): String = Digest.md5Hex(signRaw(params, cltts))

    fun failed(source: CmsSource, page: Int, err: String): FetchResult = FetchResult(
        outcome = RequestOutcome.RequestFailed, page = page, sourceName = source.name, err = err,
    )

    /**
     * 分类列表（`/bff/apis/content/programsearch` 的响应体）。
     *
     * 形如 `{"code":0,"info":{"total":11857,"data":[{"contentId":"C47919262",...}]}}`。
     */
    fun parseList(source: CmsSource, body: String?, page: Int): FetchResult {
        val root = JsonX.parseObject(body) ?: return failed(source, page, "响应非 JSON")
        val code = JsonX.int(root, "code", -1)
        if (code != 0) return failed(source, page, "code ${JsonX.int(root, "code", 0)}")
        val info = JsonX.optObject(root, "info") ?: return failed(source, page, "无 info")

        val rows = JsonX.optArray(info, "data")
        val items = ArrayList<VideoItem>()
        if (rows != null) {
            for (i in 0 until JsonX.len(rows)) {
                val row = JsonX.optObjectAt(rows, i) ?: continue
                val cid = JsonX.str(row, "contentId", "")
                val id = cidToLong(cid)
                if (id <= 0) continue
                items += VideoItem(
                    vod_id = id,
                    vod_name = JsonX.str(row, "title", ""),
                    // 封面字段两个样例口径不一（programsearch 有 cover，搜索形态给 imgM8），都取
                    vod_pic = JsonX.str(row, "cover", "").ifEmpty { JsonX.str(row, "imgM8", "") },
                    vod_remarks = JsonX.int(row, "nowSeriesCount", 0).let { n ->
                        if (n > 0) "共${n}集" else "免费正片"
                    },
                    vod_year = JsonX.str(row, "releaseYear", ""),
                    type_name = JsonX.str(row, "categoryName", ""),
                    vod_uri = cid,
                    _source = source.key,
                )
            }
        }

        val total = JsonX.int(info, "total", items.size)
        val outcome = if (items.isEmpty()) RequestOutcome.SuccessNoData else RequestOutcome.SuccessWithData
        return FetchResult(
            outcome = outcome, items = items, page = page,
            pageCount = if (total > 0) (total + PAGE_SIZE - 1) / PAGE_SIZE else 0,
            total = total, sourceName = source.name,
        )
    }

    /**
     * 详情（`/bff/apis/content/vodmix/{contentId}` 的响应体）。
     *
     * contInfo 出简介/演职员/封面，programList 出分集。
     * vodmix 的分集是**倒序**（最新集在前，实测），按集名尾部数字恢复正序，
     * 否则详情页第一集显示成最后一集。
     *
     * **排序稳定性**：尾部没有数字的集名（`toIntOrNull()` 为 null）键值取 0，
     * 与迁移前的 `ArrayList.sortBy` 一样是稳定排序 —— 同键保持原有先后。
     */
    fun parseDetail(source: CmsSource, body: String?, vodId: Long, vodUri: String): VideoItem? {
        val cid = vodUri.ifEmpty { "C$vodId" }
        val root = JsonX.parseObject(body) ?: return null
        if (JsonX.int(root, "code", -1) != 0) return null
        val info = JsonX.optObject(root, "info") ?: return null
        val ci = JsonX.optObject(info, "contInfo")

        // 分集：token = 每集 contentId，播放时经 resolvePlay 换真实 m3u8
        val eps = ArrayList<Pair<String, String>>() // (集名, contentId)
        val pl = JsonX.optObject(info, "programList")?.let { JsonX.optArray(it, "data") }
        if (pl != null) {
            for (i in 0 until JsonX.len(pl)) {
                val e = JsonX.optObjectAt(pl, i) ?: continue
                val ecid = JsonX.str(e, "contentId", "")
                if (cidToLong(ecid) <= 0) continue
                eps += JsonX.str(e, "title", "").ifEmpty { "第${i + 1}集" } to ecid
            }
        }
        // 单集内容（电影等 programList 为空）：正片即自身
        if (eps.isEmpty()) eps += ("正片" to cid)
        val ordered = eps.sortedBy { (name, _) -> episodeNo(name) }

        return VideoItem(
            vod_id = vodId,
            vod_name = ci?.let { JsonX.str(it, "title", "") } ?: "",
            vod_pic = ci?.let { JsonX.str(it, "imgM8", "") } ?: "",
            vod_actor = ci?.let { JsonX.str(it, "cast", "").trim() },
            vod_director = ci?.let { JsonX.str(it, "director", "").trim() },
            vod_year = ci?.let { JsonX.str(it, "releaseYear", "") },
            vod_content = ci?.let { JsonX.str(it, "description", "").trim() },
            vod_play_from = "天翼免费",
            vod_play_url = ordered.joinToString("#") { (name, ecid) -> "$name$$ecid" },
            type_name = ci?.let { JsonX.str(it, "categoryName", "") },
            vod_uri = cid,
            _source = source.key,
        )
    }

    /** 集名尾部数字；无数字返回 0（与迁移前 `toIntOrNull() ?: 0` 一致） */
    fun episodeNo(name: String): Int =
        Regex("(\\d+)\\s*$").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /**
     * 换播放链（`/bff/apis/content/authPlayVod` 的响应体）。
     *
     * 返回 videos 里第一个带 playUrl 的档位。
     * 免费内容 code=0 无 `end=`；若返回 917 且带 playUrl（试看），照常返回 ——
     * 能播 300 秒好过直接报错，由用户自己决定看不看。
     *
     * **注意**：迁移前的实现**不检查 code**，只看 videos 里有没有 http 开头的 playUrl。
     * 这里原样保留 —— 917（试看）也带 playUrl，若加了 code 判断会把试看也拒掉。
     */
    fun parsePlay(body: String?): PlayResolveResult {
        val root = JsonX.parseObject(body) ?: return PlayResolveResult()
        val videos = JsonX.optObject(root, "info")
            ?.let { JsonX.optObject(it, "videoInfo") }
            ?.let { JsonX.optArray(it, "videos") } ?: return PlayResolveResult()
        for (i in 0 until JsonX.len(videos)) {
            val v = JsonX.optObjectAt(videos, i) ?: continue
            val playUrl = JsonX.str(v, "playUrl", "")
            if (playUrl.startsWith("http")) return PlayResolveResult(url = playUrl)
        }
        return PlayResolveResult()
    }
}
