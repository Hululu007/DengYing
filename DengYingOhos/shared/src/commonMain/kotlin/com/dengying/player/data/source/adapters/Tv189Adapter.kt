package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsClass
import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.HomeSection
import com.dengying.player.core.HomeTab
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.VideoItem
import com.dengying.player.data.net.HttpClient
import com.dengying.player.platform.UrlCodec
import com.dengying.player.platform.Clock
import com.dengying.player.platform.TimeFormat

/**
 * 适配器：天翼视讯（tv189）—— h5.tv189.com BFF 接口，无密钥 MD5 签名
 *
 * 接口形态（逆向自大师兄影视 H5 通道，实测 2026-10-24，详见
 * dashixiong-reverse/docs/07-视频接口(安卓鸿蒙通用).md）：
 *   分类表   静态映射（DengYing 分类名 → tv189 categoryId），不发请求
 *   分类列表 GET /bff/apis/content/programsearch?categoryId=&productId=&pno=&psize=
 *   关键词搜索 GET /bff/apis/content/keywordSearchCtyun?keyword=&pno=&psize=&comeFrom=
 *   首页版块 GET /bff/apis/layout/jsondata?path=/clt4/...（服务端编排首页）
 *   详情分集 GET /bff/apis/content/vodmix/{contentId}
 *   换播放链 GET /bff/apis/content/authPlayVod?contentId=&isTryLook=1（必须带签名）
 *
 * 签名：`cltkey = md5("k1=v1&k2=v2...（key 升序，值不编码）" + "&cltts=" + cltts + "&")`，
 * secret 为空串 → **无密钥，客户端可自行生成**。cltrid 为随机 UUID。
 *
 * 【为什么列表固定带 productId=1000000218】见 [Tv189Parser.FREE_PRODUCT_ID] 注释。
 * 【搜索只搜自营库】programsearch 的 keyword 参数实测无效（传任何词都返回全量）；
 * 真搜索走 keywordSearchCtyun，固定 `comeFrom=NEW_CPMS` 只搜自营免费可播库——
 * 全库模式会混入爱奇艺外采（AQY）条目，authPlayVod 对其返回 6201 无版权播不了。
 * 【id 映射】tv189 的 contentId 是 "C47919262" 形式字符串，而 [VideoItem.vod_id]
 * 是 Long——取 C 后的数字部分存 vod_id，完整 contentId 存 vod_uri（与 slug 型
 * 源同一手法），详情/换链一律优先用 vod_uri 寻址。
 *
 * 【KMP P3】纯解析与签名原文已切到 `shared` 的 [Tv189Parser]（零平台依赖）。
 * 【KMP P4-1b】平台 API 收尾：cltrid 改本地 [randomUuid]（不引实验性 `kotlin.uuid`）、
 * cltts 改 [com.dengying.player.platform.TimeFormat]、参数编码走
 * [com.dengying.player.platform.UrlCodec]（批 F）。本文件已无平台 import，
 * 仅剩 HTTP 控制流，随时可升舱 commonMain。
 */
object Tv189Adapter {

    private const val CONNECT_MS = 6000
    private const val READ_MS = 8000

    /** 只出全片免费内容的产品包 id（见 [Tv189Parser.FREE_PRODUCT_ID] 注释） */
    private const val FREE_PRODUCT_ID = Tv189Parser.FREE_PRODUCT_ID

    /** programsearch 每页条数（原实现硬编码 20，pageCount 也按 20 计算） */
    private const val PAGE_SIZE = 20
    /**
     * 天翼 H5 的移动端 UA。
     *
     * 【为什么显式带 UA】`jsondata` 入口被 WAF 按 UA 拦截：桌面 Chrome UA
     * 直接回 403 挑战页（2026-11-08 实测），移动端 UA（真机 App 同款）放行。
     * 统一给本源全部请求带上，与其余端点行为口径一致。
     */
    private val UA_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 16; V2419A) AppleWebKit/537.36",
    )

    private fun enc(s: String): String =
        UrlCodec.encode(s)

    /**
     * 组带签名的完整 URL。签名值不 URL 编码（qs.stringify encode:false），
     * 签名原文用**未编码**的值；查询串里的值再经 [enc] 编码——中文关键词
     * （搜索）与带斜杠的 path（jsondata）靠这一步安全上线。
     */
    /**
     * 生成 UUID v4 形态的请求 id（8-4-4-4-12 小写十六进制）。
     *
     * 原实现用 `java.util.UUID.randomUUID()`。跨平台层不用 `kotlin.uuid.Uuid`
     * （它仍是需 opt-in 的实验 API），改用随机字节自行格式化：cltrid 只要求
     * 唯一且形态合法，服务端不校验其来源。
     */
    private fun randomUuid(): String {
        val b = ByteArray(16)
        kotlin.random.Random.nextBytes(b)
        b[6] = ((b[6].toInt() and 0x0F) or 0x40).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = "0123456789abcdef"
        val sb = StringBuilder(36)
        for (i in 0 until 16) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
            sb.append(hex[(b[i].toInt() shr 4) and 0x0F])
            sb.append(hex[b[i].toInt() and 0x0F])
        }
        return sb.toString()
    }

    private fun signedUrl(source: CmsSource, path: String, params: Map<String, String>): String {
        val p = LinkedHashMap<String, String>()
        p["cltrid"] = randomUuid()
        p.putAll(params)
        val cltts = TimeFormat.compactLocalTimestamp(Clock.nowMs())
        val cltkey = Tv189Parser.cltKey(p, cltts)
        val qs = (p + ("cltts" to cltts) + ("cltkey" to cltkey))
            .entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        return "${source.api}$path?$qs"
    }

    /** 分类列表（programsearch；免签名接口，但统一走签名路径最省心） */
    suspend fun list(source: CmsSource, typeId: Int?, page: Int): FetchResult {
        val tid = typeId ?: return Tv189Parser.failed(source, page, "无分类 id")
        val url = signedUrl(
            source, "/bff/apis/content/programsearch",
            mapOf(
                "categoryId" to tid.toString(),
                "productId" to FREE_PRODUCT_ID,
                "pno" to page.toString(),
                "psize" to PAGE_SIZE.toString(),
            ),
        )
        val r = HttpClient.get(url, UA_HEADERS, CONNECT_MS, READ_MS)
        if (r.status != 200) return Tv189Parser.failed(source, page, "HTTP ${r.status}")
        return Tv189Parser.parseList(source, r.body, page)
    }

    /**
     * 关键词搜索（keywordSearchCtyun，2026-11-08 实测打通）。
     *
     * 聚合搜索是单页模型（[com.dengying.player.data.source.SourceRepository.search]
     * 无 page 参数），固定 pno=1。
     */
    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        val kw = keyword.trim()
        if (kw.isEmpty()) return Tv189Parser.failed(source, 1, "关键词为空")
        val url = signedUrl(
            source, "/bff/apis/content/keywordSearchCtyun",
            mapOf(
                "keyword" to kw,
                "pno" to "1",
                "psize" to PAGE_SIZE.toString(),
                "comeFrom" to Tv189Parser.SEARCH_COME_FROM,
            ),
        )
        val r = HttpClient.get(url, UA_HEADERS, CONNECT_MS, READ_MS)
        if (r.status != 200) return Tv189Parser.failed(source, 1, "HTTP ${r.status}")
        return Tv189Parser.parseSearch(source, r.body, 1)
    }

    /**
     * 首页导航 tab（jsondata）：返回各 tab 的名字与版块数据 path。
     *
     * 【签名必须带】2026-11-08 模拟器联调实测：jsondata 无签名会拿到 WAF
     * 挑战页（HTTP 200 但 body 非 JSON），解析层直接失败——此前"无签名也能过"
     * 的判断来自带签名的抓包回放，不成立。与其它业务接口统一走签名路径。
     * 另：真实入口是 `content/jsondata`（doc07 §2.1.1；layout 前缀 404）。
     */
    suspend fun homeTabs(source: CmsSource): List<HomeTab> {
        val url = signedUrl(
            source, "/bff/apis/content/jsondata",
            mapOf("path" to Tv189Parser.NAV_PATH),
        )
        val r = HttpClient.get(url, UA_HEADERS, CONNECT_MS, READ_MS)
        if (r.status != 200) return emptyList()
        return Tv189Parser.parseNavTabs(r.body)
    }

    /** 首页版块（jsondata）：某 tab path 下的横滑版块列表（外链型条目已在解析层滤掉） */
    suspend fun homeSections(source: CmsSource, path: String): List<HomeSection> {
        if (path.isEmpty()) return emptyList()
        val url = signedUrl(source, "/bff/apis/content/jsondata", mapOf("path" to path))
        val r = HttpClient.get(url, UA_HEADERS, CONNECT_MS, READ_MS)
        if (r.status != 200) return emptyList()
        return Tv189Parser.parseSections(source, r.body)
    }

    /**
     * 详情（vodmix）：contInfo 出简介/演职员/封面，programList 出分集。
     * 分集倒序修正与集名排序在 [Tv189Parser.parseDetail] 内。
     */
    suspend fun detail(source: CmsSource, vodId: Long, vodUri: String): VideoItem? {
        val cid = vodUri.ifEmpty { "C$vodId" }
        val url = signedUrl(source, "/bff/apis/content/vodmix/$cid", emptyMap())
        val r = HttpClient.get(url, UA_HEADERS, CONNECT_MS, READ_MS)
        if (r.status != 200) return null
        return Tv189Parser.parseDetail(source, r.body, vodId, vodUri)
    }

    /**
     * 换播放链（authPlayVod）：返回 videos 里第一个带 playUrl 的档位。
     * 免费内容 code=0 无 `end=`；若返回 917 且带 playUrl（试看），照常返回——
     * 能播 300 秒好过直接报错，由用户自己决定看不看。
     */
    suspend fun resolvePlay(
        source: CmsSource,
        vodId: Long,
        token: String,
        from: String,
    ): PlayResolveResult {
        val cid = token.ifEmpty { "C$vodId" }
        val url = signedUrl(
            source, "/bff/apis/content/authPlayVod",
            mapOf("contentId" to cid, "isTryLook" to "1"),
        )
        val r = HttpClient.get(url, UA_HEADERS, CONNECT_MS, READ_MS)
        if (r.status != 200) return PlayResolveResult()
        return Tv189Parser.parsePlay(r.body)
    }

    /** 供聚合层静态分类分派使用（见 SourceRepository.fetchClasses） */
    fun classes(): List<CmsClass> = Tv189Parser.classes()
}
