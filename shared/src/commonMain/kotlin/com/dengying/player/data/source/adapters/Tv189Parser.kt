package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsClass
import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.HomeSection
import com.dengying.player.core.HomeTab
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.source.crypto.Digest
import kotlinx.serialization.json.JsonArray

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

    /** 运营内部版块名 → 用户可见名（「KV图」= Key Visual 运营行话，不外露） */
    private val SECTION_NAME_ALIASES = mapOf(
        "推荐页KV图" to "今日焦点",
    )

    /**
     * 换播放链（`/bff/apis/content/authPlayVod` 的响应体）。
     *
     * 返回 videos 里第一个带 playUrl 的档位。两种响应形态都要兼容：
     * - code=0 全片流：`info.videoInfo.videos[].playUrl`（旧实现只认这个）；
     * - code=917 试看流：`videos[].playUrl` 在**响应顶层**（2026-11-08 探针实测，
     *   附带顶层字段 `trySeeLength`/`fullPlayable`）——旧实现解析不到，等于把
     *   试看链也丢了。
     *
     * 迁移前不检查 code 只看 playUrl 是否 http 开头，原样保留；但新增
     * [PlayResolveResult.previewOnly] 标记试看链（`fullPlayable=false` 或
     * `trySeeLength>0`），由 resolvePlayChain 决定「跨源找全片，找不到再回落」。
     */
    fun parsePlay(body: String?): PlayResolveResult {
        val root = JsonX.parseObject(body) ?: return PlayResolveResult()
        val videos = JsonX.optObject(root, "info")
            ?.let { JsonX.optObject(it, "videoInfo") }
            ?.let { JsonX.optArray(it, "videos") }
            ?: JsonX.optArray(root, "videos")
        // 试看判定只信服务端字段：code=0 全片响应无这两个字段（默认视为全片）
        val preview = !JsonX.bool(root, "fullPlayable", true) ||
            JsonX.int(root, "trySeeLength", 0) > 0
        if (videos != null) {
            for (i in 0 until JsonX.len(videos)) {
                val v = JsonX.optObjectAt(videos, i) ?: continue
                val playUrl = JsonX.str(v, "playUrl", "")
                if (playUrl.startsWith("http")) {
                    // 【URL 特征兜底】2026-11-08 实录：app 请求可能拿到 code=0 形态、
                    // 响应里却没有 fullPlayable/trySeeLength 字段，但 playUrl 仍是
                    // end=300 的截断授权链（服务端按免登录会话下发）——URL 的 end=
                    // 参数是最可靠的试看特征。误标全片的代价只是多跑一次跨源再回落，
                    // 漏标的代价是用户看 5 分钟就断，宁可误标。
                    return PlayResolveResult(
                        url = playUrl,
                        previewOnly = preview || playUrl.contains("end="),
                    )
                }
            }
        }
        return PlayResolveResult()
    }

    // ---------------- 关键词搜索（keywordSearchCtyun，2026-11-08 实测打通） ----------------

    /** 搜索固定只搜自营库（comeFrom=NEW_CPMS）的结果可播；外采（AQY）authPlayVod 必 6201 */
    const val SEARCH_COME_FROM = "NEW_CPMS"

    /**
     * 关键词搜索（`/bff/apis/content/keywordSearchCtyun` 的响应体）。
     *
     * 形如 `{"code":0,"info":{"total":1,"data":[{"ct":"1","v":{...}}]}}` ——
     * 条目在 `data[].v` 里多包一层（与 programsearch 的 `data[]` 不同）。
     *
     * 【只收自营库】请求已带 `comeFrom=NEW_CPMS`，理论上只会回自营内容；
     * 这里仍按 `v.comeFrom == NEW_CPMS` 再滤一遍：不带 comeFrom 的全库模式
     * 会混入爱奇艺外采（AQY）条目，那些 authPlayVod 返回 6201 无版权不可播。
     */
    fun parseSearch(source: CmsSource, body: String?, page: Int): FetchResult {
        val root = JsonX.parseObject(body) ?: return failed(source, page, "响应非 JSON")
        val code = JsonX.int(root, "code", -1)
        if (code != 0) return failed(source, page, "code ${JsonX.int(root, "code", 0)}")
        val info = JsonX.optObject(root, "info") ?: return failed(source, page, "无 info")
        val rows = JsonX.optArray(info, "data")
        val items = ArrayList<VideoItem>()
        if (rows != null) {
            for (i in 0 until JsonX.len(rows)) {
                val v = JsonX.optObjectAt(rows, i)?.let { JsonX.optObject(it, "v") } ?: continue
                if (JsonX.str(v, "comeFrom", "") != SEARCH_COME_FROM) continue
                val cid = JsonX.str(v, "contentId", "")
                val id = cidToLong(cid)
                if (id <= 0) continue
                items += VideoItem(
                    vod_id = id,
                    vod_name = JsonX.str(v, "title", ""),
                    vod_pic = JsonX.str(v, "imgM8", "").ifEmpty { JsonX.str(v, "himgM8", "") },
                    vod_remarks = JsonX.int(v, "nowSerialCount", 0).let { n ->
                        if (n > 0) "共${n}集" else "免费正片"
                    },
                    vod_year = JsonX.str(v, "releaseYear", ""),
                    vod_actor = JsonX.str(v, "cast", "").trim(),
                    vod_director = JsonX.str(v, "director", "").trim(),
                    vod_content = JsonX.str(v, "description", "").trim(),
                    type_name = JsonX.str(v, "categoryName", ""),
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

    // ---------------- 首页版块（jsondata，2026-11-08 实测打通） ----------------

    /**
     * 导航 tab 的 jsondata path（app 前端 JS 里硬编码的两个入口常量）。
     * [NAV_PATH] 取 tab 配置，各 tab 再带自己的 path 取版块数据。
     */
    const val NAV_PATH = "/clt4/xtysxkhd/h5mh/dsy/sdh/index.json"
    /** 「推荐」tab 的版块数据 path（tab 列表里 name=推荐 那项的 path，常量备用） */
    const val HOME_PATH = "/clt4/xtysxkhd/h5mh/tjysy/index.json"

    /**
     * 导航 tab（jsondata `{"tabs":[{name,path,classID,areaType,...}]}`）。
     *
     * 【过滤外链】`areaType=130` 的 tab（如「少儿教育」）path 是完整 H5 URL，
     * 本应用不做站外跳转，解析层直接滤掉——调用方拿到的都是可请求的 path。
     */
    fun parseNavTabs(body: String?): List<HomeTab> {
        val root = JsonX.parseObject(body) ?: return emptyList()
        val tabs = JsonX.optArray(root, "tabs") ?: return emptyList()
        val out = ArrayList<HomeTab>()
        for (i in 0 until JsonX.len(tabs)) {
            val t = JsonX.optObjectAt(tabs, i) ?: continue
            val name = JsonX.str(t, "name", "")
            val path = JsonX.str(t, "path", "")
            if (name.isEmpty() || path.isEmpty()) continue
            if (JsonX.int(t, "areaType", 0) == 130) continue
            out += HomeTab(name = name, path = path)
        }
        return out
    }

    /**
     * 首页版块（jsondata `{"areaName","data":[{path,data:{label,data:[条目]}}]}`）。
     *
     * 【标题优先级】`label` 实测是**对象**（`{"name":"精品纪实"}`，少数为字符串），
     * `areaName` 是运营写的长标题（如「精品纪实•必看宝藏纪录片」）——卡片栏目标题
     * 取 `label.name > label(字符串) > areaName`。
     *
     * 【只收 clickType==0】条目 `clickType`：0=影视内容（contentId 可进详情）、
     * 1=分类筛选、2=外链/H5（广告位、快捷入口）、3=站内功能页。后三类本应用
     * 都无法处理，直接跳过。
     * 【无标题条目丢弃】广告位模块里混有 clickType==0 但 title 为空的内容项
     * （金样本 tv189_home 实录），没有名字的卡片没有意义，跳过。
     * 【空版块丢弃】不少版块条目放在 `children`（嵌套子版块，多为直播/剧场位）
     * 或为空，v1 不展开嵌套，只保留有直出条目的版块。
     *
     * 【换一换备用池】部分版块（重磅推荐、精选好剧、爆款短剧）带 `changeBat`：
     * 与当前展示的片完全不重复的一批备用片，源站自己的「换一换」池。
     * 接在当前展示的片后面，下拉刷新时由 [com.dengying.player.ui.viewmodel.IndexViewModel]
     * 轮换展示——源站这份数据是运营固定排的，直拉网络也换不出新片。
     */
    fun parseSections(source: CmsSource, body: String?): List<HomeSection> {
        val root = JsonX.parseObject(body) ?: return emptyList()
        val mods = JsonX.optArray(root, "data") ?: return emptyList()
        val out = ArrayList<HomeSection>()
        for (i in 0 until JsonX.len(mods)) {
            val mod = JsonX.optObjectAt(mods, i) ?: continue
            val inner = JsonX.optObject(mod, "data") ?: continue
            val rows = JsonX.optArray(inner, "data") ?: continue
            val items = collectItems(source, rows)
            val shown = items.map { it.vod_id }.toHashSet()
            for (extra in collectItems(source, JsonX.optArray(inner, "changeBat"))) {
                if (shown.add(extra.vod_id)) items += extra
            }
            if (items.isEmpty()) continue
            val labelName = inner["label"]?.let { JsonX.str(JsonX.obj(it), "name", "") } ?: ""
            val labelText = JsonX.textOf(inner["label"], "")
            val rawTitle = labelName.ifEmpty { labelText }.ifEmpty { JsonX.str(inner, "areaName", "") }
            // 运营内部行话不外露（「KV图」= Key Visual 运营术语，用户看不懂）
            val title = SECTION_NAME_ALIASES[rawTitle] ?: rawTitle
            out += HomeSection(title = title, items = items)
        }
        return out
    }

    /**
     * 版块条目 → VideoItem。只收 `clickType==0`（影视内容）、有标题、
     * contentId 能解析成 id 的条目，规则与 [parseSections] 原内联循环一致。
     */
    private fun collectItems(source: CmsSource, rows: JsonArray?): ArrayList<VideoItem> {
        val items = ArrayList<VideoItem>()
        for (j in 0 until JsonX.len(rows)) {
            val r = JsonX.optObjectAt(rows, j) ?: continue
            if (JsonX.int(r, "clickType", -1) != 0) continue
            val name = JsonX.str(r, "title", "")
            if (name.isEmpty()) continue
            val cid = JsonX.str(r, "contentId", "")
            val id = cidToLong(cid)
            if (id <= 0) continue
            items += VideoItem(
                vod_id = id,
                vod_name = name,
                vod_pic = JsonX.str(r, "cover", ""),
                vod_uri = cid,
                _source = source.key,
            )
        }
        return items
    }
}
