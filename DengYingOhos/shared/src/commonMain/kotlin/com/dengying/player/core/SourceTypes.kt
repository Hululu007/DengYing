package com.dengying.player.core


/**
 * 协议类型与内部结果 DTO —— 源接入层内部使用
 *
 * 归属：本文件是纯类型 + 纯常量，不导入网络/UI 层。
 *
 * 【与鸿蒙版的差异】`FetchResult.ok` 在 ArkTS 里保留为 `@deprecated` 派生字段。
 * Kotlin 侧**直接删除**它——理由是该字段的全部价值是"兼容尚未迁移的调用点"，
 * 而 Kotlin 版是一次新写，没有历史调用点需要兼容。保留一个与 outcome 可能失配的
 * 派生字段，只会让新代码有踩坑的机会。判 `outcome` 是唯一入口。
 */
enum class SourceProtocol {
    /** 苹果CMS/海洋CMS 标准 provide/vod */
    Cms,

    /** provide/vod 详情 + 非标搜索端点（蓝光4k） */
    Api2233,

    /** 米米4k：普通 JSON + /parse 换链 */
    Mino,

    /** 文才：MD5+SHA1 签名 JSON */
    Wencai,

    /** 独播库：Java LCG 签名 JSON + 自定义解码 */
    Duboku,

    /** 网站型：HTML 正则抽取（奇优/农民影视/两个BT/素白白） */
    Html,

    /** 桔子：protobuf + AES + RSA */
    Juzhi,

    /** 瓜皮：AES + RSA + 设备鉴权 */
    Guapi,

    /** 山楂4k：RSA 加密体 */
    Shanzha,

    /** 天翼视讯：h5.tv189.com BFF 接口，无密钥 MD5 签名（分类/详情/换链，无搜索） */
    Tv189,
}

/** 源能力位 —— 聚合层按能力挑选源，缺能力不参与对应路径 */
data class SourceCaps(
    /** 支持关键词搜索 */
    val search: Boolean,
    /** 支持按分类取列表（需要 ac=list 类分类表） */
    val category: Boolean,
    /** 支持按 id 取详情 */
    val detail: Boolean,
    /** 播放地址是 token，必须经适配器二次换取（`direct == false` 的语义来源） */
    val token: Boolean,
)

/** CMS 全能力 */
val CAPS_CMS = SourceCaps(search = true, category = true, detail = true, token = false)

/** 仅搜索（详情不可用，如蓝光4k 的 provide/vod 已 closed） */
val CAPS_SEARCH_ONLY = SourceCaps(search = true, category = false, detail = false, token = false)

/** 搜索 + token 换链 */
val CAPS_TOKEN_SEARCH = SourceCaps(search = true, category = false, detail = true, token = true)

/** 网站型（搜索 + 详情 + token 换链） */
val CAPS_WEB = SourceCaps(search = true, category = false, detail = true, token = true)

/**
 * 单次源请求的内部结果。
 *
 * **`outcome` 是权威字段**（四态，FR-108）：它区分「请求成功但无数据」与「请求失败」——
 * 这两者的处置完全不同（前者不触发故障转移、不扣健康分）。
 */
data class FetchResult(
    /** 四态判定结果——**权威** */
    val outcome: RequestOutcome,
    val items: List<VideoItem> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 0,
    val total: Int = 0,
    val sourceName: String = "",
    val err: String? = null,
) {
    /** 便捷判定：是否拿到数据 */
    val hasData: Boolean get() = outcome == RequestOutcome.SuccessWithData

    /** 便捷判定：是否算"源本身是好的" */
    val sourceHealthy: Boolean get() = outcome.isSourceHealthy()
}

/** 播放地址换取结果 */
data class PlayResolveResult(
    val url: String = "",
    /** 播放该地址需要的自定义请求头（JSON 串），无则空串 */
    val headers: String = "",
)

// ---------------- HTML 抓取规则 ----------------

/** HTML 抓取：搜索请求形态 */
data class WebSearchRule(
    /** 'GET' | 'POST' */
    val method: String,
    /** 支持 `{kw}` 占位；GET 时参数直接拼在 URL 上 */
    val url: String,
    /** POST 表单字段名（method=POST 时用） */
    val field: String,
    /** 结果条目正则（捕获组下标由 item 指定） */
    val itemPattern: String,
    /** 详情链接基础地址（补全相对路径） */
    val base: String,
    /** 条目 id 提取方式；slug 型路径（如 `/play/ch2mo68f3`）没有数字尾巴，必须换方式 */
    val idMode: WebIdMode = WebIdMode.TrailingNumber,
)

/**
 * 条目 id 提取方式。
 *
 * `TrailingNumber`：取链接里最后一段连续数字（`/view/13691.html` -> 13691）。
 * `SlugHash`：链接里没有数字尾时，对整条链接做稳定哈希（`/play/ch2mo68f3`）。
 *   id 只用于本地收藏 / 历史 / 进度的键，不与线上接口交互，故哈希可接受；
 *   **哈希算法一旦上线就不能改**，否则老用户的收藏会全部失联。
 */
enum class WebIdMode {
    TrailingNumber,
    SlugHash,
}

/** HTML 抓取：结果条目字段下标（1 起，0 表示无此字段） */
data class WebItemRule(
    val nameGroup: Int,
    val hrefGroup: Int,
    val picGroup: Int,
    val statusGroup: Int,
)

/** HTML 抓取：线路标签 */
data class WebLineRule(
    /** 线路标签正则；`nameGroup` 取线路名，`anchorGroup` 取面板锚点（0 表示无面板） */
    val pattern: String,
    val nameGroup: Int,
    val anchorGroup: Int,
)

/** HTML 抓取：剧集条目 */
data class WebEpisodeRule(
    /** 剧集正则；`hrefGroup` 必填，`nameGroup` / `lineGroup` / `epGroup` 可 0 */
    val pattern: String,
    val hrefGroup: Int,
    val nameGroup: Int,
    val lineGroup: Int,
    val epGroup: Int,
)

/** HTML 抓取：播放页 → 媒体地址（站点播放器自解析链） */
data class WebPlayRule(
    /** 播放页里的播放器 iframe 正则（1 组） */
    val iframePattern: String,
    /** 播放器页常量正则（各 1 组）：{url} {sign} {from} 占位替换用 */
    val urlConstPattern: String,
    val signConstPattern: String,
    val fromConstPattern: String,
    /** 媒体地址模板：支持 {origin} {url} {sign} {from} 占位；为空表示该源播放链未闭环 */
    val mediaUrlTemplate: String,
    /** 播放请求头（{page} 占位 = 播放器页地址） */
    val headers: Map<String, String>,
    /**
     * 两步换链：非空时，`mediaUrlTemplate` 构造出的地址返回的是 **JSON**
     * （该字段的值才是最终媒体地址，奇优改版后 `/player/api.php` 的
     * `{"code":200,"type":"m3m8","url":"…m3u8"}` 形态）；适配器会再抓一次该
     * JSON 并提取本字段。为空（旧站直出字节流）时构造地址即媒体地址。
     */
    val jsonUrlKey: String? = null,
)

/**
 * HTML 抓取：签名换链型播放链（两个BT 的 `/video/play` 形态）。
 *
 * 与 [WebPlayRule] 的区别：没有 iframe、没有播放器页常量，而是
 * 「播放页 -> 取 userlink 与本集 dataid -> 按站点算法签名 -> 请求换链接口 -> 取媒体地址」。
 * 签名算法本身不放进规则（它是代码，不是数据），规则只描述**怎么取值**与**调哪个接口**。
 */
data class WebTokenPlayRule(
    /** 播放页里 userlink 的正则（1 组）。该值每次请求都变，必须现取，不能缓存 */
    val userlinkPattern: String,
    /** 播放页里本集 dataid 的正则（1 组）；`{path}` 占位 = 当前播放页路径 */
    val dataIdPattern: String,
    /** 换链接口路径（拼在源站根地址之后） */
    val endpoint: String,
    /** 画质位；站点只认 `1080` / `1` / 空，`4k` 须先归一为 `1` */
    val quality: String,
    /** 签名算法版本；新站点在此扩展，禁止按 source.key 分支 */
    val alg: WebTokenPlayAlg,
    /** 响应业务码字段；HTTP 200 但 code=401/403/404 也算失败 */
    val codeKey: String = "code",
    val okCode: Int = 200,
    /** 响应里媒体地址列表的容器键（形如 `data.<listKey>[0].<urlKey>`） */
    val listKey: String = "quality_urls",
    val urlKey: String = "url",
)

/** 签名换链算法版本 */
enum class WebTokenPlayAlg {
    /** 两个BT：`k = Base64(userlink XOR key)`、`s = HMAC-SHA256(key=v, msg="p:t:v")` */
    BttwoV1,
}

/** HTML 抓取规则全集 */
data class WebRule(
    val search: WebSearchRule,
    val item: WebItemRule,
    val line: WebLineRule,
    val episode: WebEpisodeRule,
    /** 播放链规则；缺省表示未闭环（只参与搜索/详情） */
    val play: WebPlayRule? = null,
    /** 签名换链型播放链（与 [play] 互斥，二者都为空 = 只走嗅探兜底） */
    val tokenPlay: WebTokenPlayRule? = null,
    /** 播放页媒体地址白名单关键字（嗅探兜底） */
    val sniff: List<String>,
    /**
     * 可选：详情页的「季 / 列表容器」切分正则（至少 1 个捕获组）。
     *
     * 非空时，每个匹配项独立抽取剧集并成为一条线路（线路名按序号回填），
     * 用于「多个容器被合并成一条线」的站点（农民影视的
     * `//div[@class="numList"]/ul` 就是这种形态）。为空时走原有分支
     * （面板型 / 无面板型）。
     */
    val seasonListPattern: String = "",
    /**
     * 剧集抽取后是否倒序。
     *
     * 对应 AH 规则里的「逆转选集 = true」：部分站点把第 1 集放在页面末尾，
     * 不倒序会让用户从结尾开始看。
     */
    val reverseEpisodes: Boolean = false,
    /**
     * 播放页是 macCMS 模板：从 `var player_aaaa={…}` 提取媒体地址（农民影视
     * nmystv 形态）。解码走 macCMS 明文级 `encrypt` 语义（0/1/2，复用
     * `Ap2233PlayPageParser`）；解出**非 http 开头**的地址（相对令牌，如
     * nmdvd 线的 `nm-xxx`）视为该线不可播，回落嗅探兜底。
     *
     * 与 [play] / [tokenPlay] 三选一，都没有时只走嗅探兜底。
     */
    val playerAaaa: Boolean = false,
)
