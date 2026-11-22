package com.dengying.player.data.source.adapters

import com.dengying.player.data.net.HttpClient
import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.CmsClass
import com.dengying.player.core.VideoItem
import com.dengying.player.platform.UrlCodec

/**
 * 适配器：苹果CMS 标准协议 + api2233 变体
 *
 * 形态：
 *   GET {api}?ac=list                       分类表
 *   GET {api}?ac=videolist&pg=1&t=分类id     分类列表
 *   GET {api}?ac=detail&wd=关键词            搜索（部分源忽略 wd → 回落 videolist&wd）
 *   GET {api}?ac=detail&ids=id               详情
 *
 * api2233 变体（蓝光4k）：详情仍走 provide/vod，但分类表端点返回 `closed`、
 * 搜索走 `{searchUrl}`（`info.vod.list` 非标嵌套）。
 *
 * 解析规则已抽到 `:shared` 的 `CmsParser`（KMP P3 批 C）：本文件只管发请求、
 * 拼 URL、判状态码。这样解析逻辑能编进 iOS 端，也能用金样本离线钉住。
 */
object CmsAdapter {

    private const val CONNECT_MS = 8000
    private const val READ_MS = 10000
    private const val SEARCH_CONNECT_MS = 4000
    private const val SEARCH_READ_MS = 5000

    /** 百分号编码（与 `encodeURIComponent` 的保留字符集一致） */
    private fun enc(s: String): String =
        UrlCodec.encode(s)

    /** 按分类取列表 */
    suspend fun list(source: CmsSource, typeId: Int?, page: Int): FetchResult {
        var url = "${source.api}?ac=videolist&pg=$page"
        if (typeId != null) url += "&t=$typeId"
        val r = HttpClient.get(url, null, CONNECT_MS, READ_MS)
        if (r.status != 200) return CmsParser.failed(source, page, "HTTP ${r.status}")
        return CmsParser.fill(r.body, source, page)
    }

    /**
     * 搜索：普通搜索双端点**并行**发出、命中即用，短超时让死源快速出局。
     *
     * 先看 `ac=detail&wd`（带完整详情），空则看 `ac=videolist&wd`。
     * `searchQuick` 是跨源候选专用：只发 detail 端点，避免每个候选源都占两条
     * 出网槽位、把详情页「其他资源站」的首批结果拖到 10+ 秒。
     */
    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        val kw = enc(keyword)
        val detailUrl = "${source.api}?ac=detail&wd=$kw"
        val listUrl = "${source.api}?ac=videolist&wd=$kw"

        // 并行：两个协程同时发，谁先回来都行；这里等两个都回来再择优，
        // 因为「detail 有数据但为空」与「detail 失败」的处置不同（见下）
        val pair = Parallel.get(
            detailUrl to { HttpClient.getText(detailUrl, SEARCH_CONNECT_MS, SEARCH_READ_MS) },
            listUrl to { HttpClient.getText(listUrl, SEARCH_CONNECT_MS, SEARCH_READ_MS) },
        )

        return pickSearchResult(pair[0], pair[1], source)
    }

    /** 跨源候选搜索：只发 detail 端点，endpoint 没数据时才回落 videolist */
    suspend fun searchQuick(source: CmsSource, keyword: String): FetchResult {
        val kw = enc(keyword)
        val detailUrl = "${source.api}?ac=detail&wd=$kw"
        val r1 = CmsParser.fill(
            HttpClient.getText(detailUrl, SEARCH_CONNECT_MS, SEARCH_READ_MS), source, 1,
        )
        if (r1.hasData) return r1
        val listUrl = "${source.api}?ac=videolist&wd=$kw"
        return pickSearchResult(
            detailText = "",
            listText = HttpClient.getText(listUrl, SEARCH_CONNECT_MS, SEARCH_READ_MS),
            source = source,
            detailFallback = r1,
        )
    }

    /** 两端点响应的择优：detail 有数据即用；否则看 list；两者都没数据取更可诊断的一态 */
    private fun pickSearchResult(
        detailText: String,
        listText: String,
        source: CmsSource,
        detailFallback: FetchResult? = null,
    ): FetchResult {
        // 响应不是 JSON 时 CmsParser 返回 ParseFailed（hasData=false），
        // 与「解析成功但列表为空」一样都跳到第二个端点，等价于原来的 `safeJson ?: 跳过`。
        val r1 = detailFallback ?: CmsParser.fill(detailText, source, 1)
        if (r1.hasData) return r1
        val r2 = CmsParser.fill(listText, source, 1)
        if (r2.outcome != RequestOutcome.ParseFailed) return r2
        return CmsParser.failed(source, 1, "搜索无响应")
    }

    /** 详情 */
    suspend fun detail(source: CmsSource, vodId: Long): VideoItem? {
        val r = HttpClient.get("${source.api}?ac=detail&ids=$vodId", null, CONNECT_MS, READ_MS)
        if (r.status != 200) return null
        return CmsParser.detail(r.body, source, vodId)
    }

    /**
     * 批量详情：`?ac=detail&ids=1,2,3`（苹果 CMS 标准端点，api2233 实测支持）。
     *
     * 首页热播宫格要封面和年份/地区。`ac=videolist` 虽带封面，但字段不全；
     * 一次批量 detail 能补齐 12 张卡的完整字段，只多一个请求。
     * 失败或空列表都返回空，由调用方回落到列表条目。
     */
    suspend fun details(source: CmsSource, vodIds: List<Long>): List<VideoItem> {
        if (vodIds.isEmpty()) return emptyList()
        val ids = vodIds.joinToString(",")
        val r = HttpClient.get("${source.api}?ac=detail&ids=$ids", null, CONNECT_MS, READ_MS)
        if (r.status != 200) return emptyList()
        return CmsParser.fill(r.body, source, 1).items
    }

    /**
     * 分类表（ac=list）
     *
     * 用 3s/3s 短超时：分类表是首屏的**前置依赖**，这里卡住会拖住整屏。
     * 拉不到就返回空，由上层决定跳过该源（不缺这一条映射也能出结果）。
     */
    suspend fun classes(source: CmsSource): List<CmsClass> {
        val r = HttpClient.get("${source.api}?ac=list", null, 3000, 3000)
        if (r.status != 200) return emptyList()
        return CmsParser.classes(r.body)
    }

    // ---------------- api2233 变体 ----------------

    /**
     * api2233 搜索：非标端点 + `info.vod.list` 嵌套。
     *
     * 该源是短剧站，长剧/老片 0 命中属内容池特征，不是故障——故空列表判 `SuccessNoData`。
     */
    suspend fun api2233Search(source: CmsSource, keyword: String): FetchResult {
        val tpl = source.searchUrl ?: ""
        if (tpl.isEmpty()) return CmsParser.failed(source, 1, "未配置搜索端点")
        val url = tpl.replace("{kw}", enc(keyword)).replace("{page}", "1")
        val r = HttpClient.get(url, null, SEARCH_CONNECT_MS, SEARCH_READ_MS)
        if (r.status != 200) return CmsParser.failed(source, 1, "HTTP ${r.status}")
        return CmsParser.api2233Search(r.body, source)
    }
}
