package com.dengying.player.core

/**
 * 数据源配置
 *
 * 三类源：
 *  1. CMS 采集源（苹果CMS/海洋CMS 标准协议）—— 提供分类、搜索、详情、播放列表
 *  2. 直链源 —— 直接给 m3u8 播放地址（当前为空，见下）
 *  3. 多协议源（AH 接口清单）—— 签名 JSON / HTML 抓取 / protobuf 加密体，
 *     由 `protocol` 字段分派到各适配器（`adapters` 包）
 *
 * 【重要】以下采集源均已在原工程实测通过（2026-10-03）：
 *   - ac=list 返回分类表
 *   - ac=detail&wd=庆余年 能搜到结果且含播放地址
 */
data class CmsSource(
    /** 唯一标识 */
    val key: String,
    /** 显示名 */
    val name: String,
    /** 采集接口基址 */
    val api: String,
    /** 响应速度（越小越快，实测排序） */
    val speed: Int,
    /** 是否返回直链（false 表示返回分享页/token，需解析） */
    val direct: Boolean,
    /** 基线启用状态 */
    val enabled: Boolean,
    /** 优先级：1=优选（分片可直连，实测通过） 2=一般 3=备用 */
    val tier: Int = 2,
    /** 协议族，缺省 Cms */
    val protocol: SourceProtocol = SourceProtocol.Cms,
    /** 能力位，缺省 = CMS 全能力 */
    val caps: SourceCaps = CAPS_CMS,
    /** 非标搜索端点模板：`{kw}` / `{page}` 占位 */
    val searchUrl: String? = null,
    /** 备用根地址（按序容灾） */
    val mirrors: List<String> = emptyList(),
    /** HTML 抓取规则（protocol=Html） */
    val rule: WebRule? = null,
    /** 播放链缺口说明：非空 = 该源当前不可播，UI 必须显式提示而不是静默失败 */
    val playGap: String? = null,
    /** 实测备注：日期 + 结论，供后来者判断是否还值得重测 */
    val note: String? = null,
)

/** 直链源（预置播放地址） */
data class DirectSource(
    val key: String,
    val name: String,
    /** 完整 m3u8 地址 */
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** 归属分组名 */
    val group: String,
)

/** 取源能力位（缺省 = CMS 全能力） */
fun capsOf(source: CmsSource): SourceCaps = source.caps

/** 取协议族 */
fun protocolOf(source: CmsSource): SourceProtocol = source.protocol

object CmsSources {

    // ==================== 网站型源的抓取规则 ====================
    //
    // 规则以正则承载（原工程无 XPath 引擎），正则只用基础语法、不使用 lookbehind。
    // 命中证据见各条 note 与 docs/06 §3.2 验证记录。

    /**
     * 非凡资源（原「蓝光4k」位，2026-11-21 换数据源）：标准 CMS `ac=list/detail/wd`。
     * 旧的 `api2233.yiys06.com` 非标搜索端点已不再使用，搜索走 CmsAdapter 的默认 `ac=detail&wd=`。
     */
    private const val FFZY_API = "https://api.ffzyapi.com/api.php/provide/vod/"

    /**
     * 奇优（wdbay）—— 搜索与详情结构已实测命中；
     * 站点同时支持 HTTPS，走 HTTPS 避免明文流量被拦。
     */
    private val QIYOU_RULE = WebRule(
        search = WebSearchRule(
            method = "POST",
            url = "https://www.wdbay.com/search.php",
            field = "searchword",
            itemPattern = "<a[^>]*class=\"v-thumb[^\"]*\"[^>]*data-original=\"([^\"]*)\"[^>]*href=\"([^\"]*)\"" +
                "[^>]*title=\"([^\"]*)\"[\\s\\S]{0,200}?pic-text[^>]*>([^<]*)<",
            base = "https://www.wdbay.com",
        ),
        item = WebItemRule(nameGroup = 3, hrefGroup = 2, picGroup = 1, statusGroup = 4),
        line = WebLineRule(
            pattern = "<a[^>]*href=\"#(down\\d+)\"[^>]*>([^<]*)</a>",
            nameGroup = 2,
            anchorGroup = 1,
        ),
        episode = WebEpisodeRule(
            pattern = "<li><a[^>]*href=\"([^\"]+)\"[^>]*title=\"([^\"]*)\"",
            hrefGroup = 1, nameGroup = 2, lineGroup = 0, epGroup = 0,
        ),
        play = WebPlayRule(
            iframePattern = "src=\"(https://meizi[^\"]+)\"",
            urlConstPattern = "const Url\\s*=\\s*\"([^\"]+)\"",
            signConstPattern = "const Sign\\s*=\\s*\"([^\"]+)\"",
            fromConstPattern = "const From\\s*=\\s*\"([^\"]+)\"",
            // 换链端点在 iframe 源的 /player/api.php（2026-10-31 实测闭环：
            // const Url/Sign/From → GET /player/api.php?url&sign&t=m3u8 → JSON → dytt-cinema m3u8）。
            // 旧值 {origin}/content.php/api.php 是路径拼错，永 404。
            // 响应是 JSON（url 字段才是媒体地址），jsonUrlKey 走两步换链。
            mediaUrlTemplate = "{origin}/player/api.php?url={url}&sign={sign}&t={from}",
            headers = mapOf("Referer" to "{page}"),
            jsonUrlKey = "url",
        ),
        sniff = listOf("mp4", "m3u8", "1ljx"),
    )

    /**
     * 两个BT —— 搜索与播放链已实测【均命中】（2026-10）
     *
     * 形态特殊：**没有详情页**，搜索结果直接指向 `/play/<slug>`，播放页本身就是条目页。
     * 播放地址由浏览器端 Rust→wasm 模块算出，算法已逆向并复刻为纯 Kotlin
     * （见 [com.dengying.player.data.source.adapters.BttwoSign]），不依赖 wasm 运行时。
     *
     * 三个与别处不同的地方：
     *  1. `idMode = SlugHash`：链接是 `/play/ch2mo68f3`，没有数字尾巴，
     *     沿用 `trailingNumber` 会把每一条搜索结果都丢成 0；
     *  2. `tokenPlay` 而不是 `play`：没有 iframe、没有播放器页常量；
     *  3. `dataIdPattern` 里的 `{path}` 必须锚定当前页——多集条目的播放页里有
     *     十几个 `dataid`，取错一个就播到别的集。
     */
    private val BTTWO_RULE = WebRule(
        search = WebSearchRule(
            method = "GET",
            url = "https://www.bttwo.life/search?q={kw}",
            field = "q",
            itemPattern = "<div class=\"group relative\">\\s*<a href=\"([^\"]+)\"[\\s\\S]{0,300}?" +
                "<img data-src=\"([^\"]+)\"[\\s\\S]{0,200}?alt=\"([^\"]*)\"",
            base = "https://www.bttwo.life",
            idMode = WebIdMode.SlugHash,
        ),
        item = WebItemRule(nameGroup = 3, hrefGroup = 1, picGroup = 2, statusGroup = 0),
        line = WebLineRule(pattern = "", nameGroup = 0, anchorGroup = 0),
        episode = WebEpisodeRule(
            pattern = "<a href=\"([^\"]+)\"[^>]*data-line=\"(\\d+)\"[^>]*data-episode=\"(\\d+)\"",
            hrefGroup = 1, nameGroup = 0, lineGroup = 2, epGroup = 3,
        ),
        tokenPlay = WebTokenPlayRule(
            userlinkPattern = "userlink:'([^']+)'",
            dataIdPattern = "<a href=\"{path}\"[^>]*?dataid=\"(\\d+)\"",
            endpoint = "/video/play",
            quality = "1080",
            alg = WebTokenPlayAlg.BttwoV1,
        ),
        sniff = listOf("mp4", "m3u8"),
    )

    /**
     * 农民影视（2026-10 清单第四轮迁移新域 nmystv.com 救活）—— 苹果CMS10 模板。
     *
     * 老域 vip.wwgz.cn:5200 模板损坏判死（见源条目 note）；新域全链实测（金样本
     * nongmin2_*）：搜索页 /vod/search/wd/{kw}.html → 详情页 /vod/detail/id/{vid}.html
     * （每条线路一个 `module-play-list-content` 容器，线名在标签条的
     * `data-dropdown-value`，先于所有容器出现、按序对应）→ 播放页
     * /vod/play/id/{vid}/sid/{s}/nid/{n}.html 的 `var player_aaaa` 带明文 m3u8。
     *
     * 线路分化：1080zyk 线可用（master playlist 由播放器自动下钻）、dbm3u8 线 404、
     * nmdvd 线是相对令牌（player_aaaa.url 非 http 开头，[WebAdapter] 视为不可播回落嗅探）。
     */
    private val NONGMIN_RULE = WebRule(
        search = WebSearchRule(
            method = "GET",
            url = "https://nmystv.com/index.php/vod/search/wd/{kw}.html",
            field = "wd",
            itemPattern = "href=\"(/index\\.php/vod/detail/id/\\d+\\.html)\"[^>]*class=\"module-card-item-poster\"" +
                "[\\s\\S]{0,400}?module-item-note\">([^<]*)<[\\s\\S]{0,300}?data-original=\"([^\"]*)\"" +
                "[\\s\\S]{0,600}?<strong>([^<]*)</strong>",
            base = "https://nmystv.com",
        ),
        item = WebItemRule(nameGroup = 4, hrefGroup = 1, picGroup = 3, statusGroup = 2),
        // 线名标签条（data-dropdown-value）按文档序先于剧集容器出现，与容器一一对应
        line = WebLineRule(
            pattern = "data-dropdown-value=\"([^\"]*)\"",
            nameGroup = 1,
            anchorGroup = 0,
        ),
        episode = WebEpisodeRule(
            // 真实页面 <a 与 class 之间是两个空格（模板压缩产物），用 \s+ 容忍
            pattern = "<a\\s+class=\"module-play-list-link\" href=\"([^\"]+)\"[^>]*>\\s*<span>([^<]*)</span>",
            hrefGroup = 1, nameGroup = 2, lineGroup = 0, epGroup = 0,
        ),
        // 每条线路一个 module-play-list-content 容器；容器内只有 <a>（无嵌套 div），
        // 懒匹配到首个 </div> 恰好是整段。
        seasonListPattern = "<div class=\"module-play-list-content[^\"]*\">([\\s\\S]*?)</div>",
        playerAaaa = true,
        sniff = listOf("mp4", "m3u8"),
    )

    /** 素白白 —— 规则按清单 XPath 转写；站点返回 JS 反爬挑战页，未实测 */
    private val SUBAIBAI_RULE = WebRule(
        search = WebSearchRule(
            method = "GET",
            url = "https://www.subaibai.com/search?q={kw}",
            field = "q",
            itemPattern = "<img[^>]*class=\"thumb lazy\"[^>]*data-original=\"([^\"]*)\"[\\s\\S]{0,200}?alt=\"([^\"]*)\"",
            base = "https://www.subaibai.com",
        ),
        item = WebItemRule(nameGroup = 2, hrefGroup = 0, picGroup = 1, statusGroup = 0),
        line = WebLineRule(pattern = "", nameGroup = 0, anchorGroup = 0),
        episode = WebEpisodeRule(
            pattern = "<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]*)</a>",
            hrefGroup = 1, nameGroup = 2, lineGroup = 0, epGroup = 0,
        ),
        sniff = listOf("mp4", "m3u8"),
    )

    /**
     * 采集源列表
     *
     * tier 分级（2026-10-03 实测，PC 绕代理直连复核）：
     *   tier 1 — m3u8 + 分片均可拉取，返回「媒体列表」，可直接播放 ★首选
     *   tier 2 — 接口可用且分片直连可达（暴风/豆瓣/量子 206-200 实测通过）
     *   tier 3 — 分片直连不可达（墙）或协议非标（闪电码点流），全部停用；
     *            配置保留，将来上自建中转后可一键恢复
     *
     * 【分片吞吐实测（2026-10-03 深夜）】卡顿归因：
     *   金鹰/虎牙 c.baisiweiting.com 直连超时或 45KB/s（走代理 156-268KB/s）
     *   豆瓣 vodcnd17.ajupf.com 直连 103KB/s；暴风 fengbao12 直连超时（走代理 357KB/s）
     *   1080p HLS 需持续 200-400KB/s —— 免费源 CDN 对直连用户劣化是播放卡顿主因，
     *   不是 App 代码问题。缓解：多源聚合选最快源播放 + 自动换线 + （可选）设备全局代理。
     */
    val ALL: List<CmsSource> = listOf(
        // ---------- tier 1：实测可播 ----------
        CmsSource(
            key = "jyzy", name = "金鹰资源",
            api = "https://jyzyapi.com/provide/vod/",
            speed = 1, direct = true, enabled = true, tier = 1,
        ),
        CmsSource(
            key = "huyazy", name = "虎牙资源",
            api = "https://huyazy.net/api.php/provide/vod/",
            speed = 2, direct = true, enabled = true, tier = 1,
        ),
        CmsSource(
            key = "hhzy", name = "火狐资源",
            api = "https://hhzyapi.com/api.php/provide/vod/",
            speed = 3, direct = true, enabled = true, tier = 1,
        ),

        // ---------- tier 2：分片直连可达 ----------
        CmsSource(
            key = "baofeng", name = "暴风资源",
            api = "https://bfzyapi.com/api.php/provide/vod/",
            speed = 3, direct = true, enabled = true, tier = 2,
        ),
        CmsSource(
            key = "dbzy", name = "豆瓣资源",
            api = "https://dbzy.tv/api.php/provide/vod/",
            // 2026-10-24 停用：源站网关死亡（PC 侧代理/直连 https 均 502，
            // http 301 回跳 https），真机 DY/Net 多次记录其 UnknownHost/超时。
            // 每次冷启动它都白吃一轮 8s 超时；FR-601 用户可在源管理里重新启用。
            // 2026-10-27 复核：网关已恢复，ac=list 200 / total=148403 / pagecount=7421。
            // 探活只覆盖列表端点，分片播放未复核，故仍保持停用（FR-601 可一键启用）。
            speed = 4, direct = true, enabled = false, tier = 2,
        ),
        CmsSource(
            key = "tv189", name = "天翼超高清",
            api = "https://h5.tv189.com",
            // 逆向自大师兄影视 H5 通道（dashixiong-reverse/docs/07），实测 2026-10-24：
            // 分类浏览 / 详情 / 换链全通，免费整片（m3u8 无 end= 截断）。
            // 列表固定筛 productId=1000000218（全片免费）：电影 589 / 电视剧 72 /
            // 少儿 586 / 动漫 123；不过滤则大部分是 VIP 试看（300s）。
            // 2026-11-08 复核：keywordSearchCtyun 搜索 + jsondata 首页版块打通
            // （均免登录；移动端 UA 必须——桌面 UA 被 WAF 拦 403）。
            speed = 7, direct = false, enabled = true, tier = 2,
            protocol = SourceProtocol.Tv189,
            caps = SourceCaps(search = true, category = true, detail = true, token = true, home = true),
            note = "2026-10-24 全链路通；2026-11-08 补搜索与首页版块",
        ),
        CmsSource(
            key = "lzi", name = "量子资源",
            api = "https://cj.lziapi.com/api.php/provide/vod/",
            speed = 5, direct = false, enabled = true, tier = 2,
        ),
        // m3u8 为非标码点流（待解码分支），分片直连 000
        // 2026-10-27 复核：ac=list 200 / total=124790，列表端点活着；分片问题未解决，维持停用。
        CmsSource(
            key = "sdzy", name = "闪电资源",
            api = "https://sdzyapi.com/api.php/provide/vod/",
            speed = 6, direct = true, enabled = false, tier = 2,
        ),
        CmsSource(
            key = "sdzy2", name = "闪电资源(备)",
            api = "https://sdzyapi.com/api.php/provide/vod/",
            speed = 8, direct = true, enabled = false, tier = 2,
        ),

        // ---------- tier 3：分片实测不可达（需代理），全部停用 ----------
        // 2026-10-27 复核：下列源的 ac=list 端点全部恢复 200 且返回真实数据：
        //   guangsu 116497 / mdzy 90835 / tyys 73205 / iqizy 69867 / bdzy 49452 /
        //   360zy 73785 / ruyi 88589 / ikun 67472 / ffzy 98802 / jisu 114831 / sdzy 124790。
        // 但「分片不可达、需代理」是播放侧结论，列表探活不能推翻它，故一律维持 enabled=false。
        // 复活验证方法见 docs/04 §15.7.4（必须实测分片 GET 200 再开）。
        CmsSource(
            key = "guangsu", name = "光速资源",
            api = "https://api.guangsuapi.com/api.php/provide/vod/",
            speed = 9, direct = true, enabled = false, tier = 3,
        ),
        CmsSource(
            key = "mdzy", name = "魔都资源",
            api = "https://www.mdzyapi.com/api.php/provide/vod/",
            speed = 10, direct = true, enabled = false, tier = 3,
        ),
        CmsSource(
            key = "tyys", name = "天涯影视",
            api = "https://tyyszyapi.com/api.php/provide/vod/",
            speed = 11, direct = true, enabled = false, tier = 3,
        ),
        CmsSource(
            key = "iqiyizy", name = "爱奇艺采集",
            api = "https://iqiyizyapi.com/api.php/provide/vod",
            speed = 12, direct = true, enabled = false, tier = 3,
        ),
        CmsSource(
            key = "bdzy", name = "百度云资源",
            api = "https://api.apibdzy.com/api.php/provide/vod/",
            speed = 13, direct = true, enabled = false, tier = 3,
        ),
        CmsSource(
            key = "360zy", name = "360资源",
            api = "https://360zy.com/api.php/provide/vod/",
            speed = 14, direct = true, enabled = false, tier = 3,
        ),
        CmsSource(
            key = "ruyi", name = "如意资源",
            api = "https://cj.rycjapi.com/api.php/provide/vod/",
            speed = 15, direct = true, enabled = false, tier = 3,
        ),
        CmsSource(
            key = "ikun", name = "iKun资源",
            api = "https://ikunzyapi.com/api.php/provide/vod/",
            speed = 16, direct = true, enabled = false, tier = 3,
        ),
        CmsSource(
            key = "ffzy", name = "非凡资源",
            api = "https://cj.ffzyapi.com/api.php/provide/vod/",
            speed = 17, direct = false, enabled = false, tier = 3,
        ),
        CmsSource(
            key = "jisu", name = "极速资源",
            api = "https://jszyapi.com/api.php/provide/vod/",
            speed = 18, direct = false, enabled = false, tier = 3,
        ),

        // ==================== AH 接口清单接入（2026-10-04） ====================
        // 与上面 18 个 CMS 源不同协议，靠 protocol 分派到各适配器。
        // 启用策略：搜索/详情链路跑通的才 enabled=true；服务不通或响应形态不闭环的一律 false 并保留配置与实测结论。
        CmsSource(
            key = "ah_wencai", name = "文才",
            api = "https://y2s52n7.com",
            speed = 20, direct = false, enabled = true, tier = 2,
            protocol = SourceProtocol.Wencai,
            caps = CAPS_TOKEN_SEARCH,
            note = "2026-10-04 实测：搜索「流浪地球」code:200 / 107 条；详情与播放走 t+sign（SHA1(MD5(参数串&key&t))）",
        ),
        CmsSource(
            key = "ah_duboku", name = "独播库",
            api = "https://api.dbokutv.com",
            speed = 21, direct = false, enabled = true, tier = 2,
            protocol = SourceProtocol.Duboku,
            caps = CAPS_TOKEN_SEARCH,
            mirrors = listOf("https://w.duboku.io", "https://www.duboku.tv"),
            note = "2026-10-04 实测：Java LCG 签名链全通（搜索→/voddetail/4570→/vodplay/4570-1-1→HId 解码得 m3u8）；" +
                "2026-10-27 复核把「三入口全死」是**误测**——400「Failure」正是**不带签名**时的固定返回（清单 §1.11 实测口径），站点活着；" +
                "2026-10-31 复核（对照 AH 清单第四轮）：带上 LCG 三件套签名后三层全 200 真数据，d(HId) 解码 m3u8 实拉 81 分片，端到端定级 T3。" +
                "硬条件：api.dbokutv.com 直连 TLS 握手超时，必须走代理（设备全局代理可用时即可播；无代理时按源健康度自然降级，不影响其它源）",
        ),
        CmsSource(
            key = "ah_qiyou", name = "奇优",
            api = "https://www.wdbay.com",
            speed = 22, direct = false, enabled = true, tier = 3,
            protocol = SourceProtocol.Html,
            caps = CAPS_WEB,
            rule = QIYOU_RULE,
            playGap = null,
            note = "2026-10-04 实测：搜索 3 条命中（庆余年 1/2 季、粤语）；播放链 = 播放页→播放器 iframe→const Url/Sign/From→换链接口直出媒体；" +
                "2026-10-27 复核把换链判死是**误测**：抓到的 139700 B「MP4」是 meizi 站 artplayer.js 的伪装文件，不是换链端点；" +
                "2026-10-31 复核（对照 AH 清单第四轮）换链闭环实证：播放页 iframe（meizi.yongfan99.com/content.php?vid=…）→ 页内 const Url/Sign/From（From 实测=\"m3u8\"）→ GET {iframe 源}/player/api.php?url&sign&t=m3u8 → dytt-cinema m3u8 实拉 #EXTM3U（金样本 qiyou_player*）；" +
                "注意老片播放页会 404「此影片已做删除处理」（站点清理行为，用新热片验证）",
        ),
        CmsSource(
            key = "ah_ap2233", name = "非凡资源2",
            api = FFZY_API,
            speed = 23, direct = false, enabled = true, tier = 3,
            protocol = SourceProtocol.Api2233,
            // 首页由 AhHomeAdapter 按分类现场合成（无服务端编排接口），见 docs/02 FR-630
            caps = CAPS_CMS.copy(home = true),
            playGap = null,
            note = "2026-11-21 换数据源（原蓝光4k）：api.ffzyapi.com 标准 CMS——ac=list total=99215/pagecount=4961、31 分类（顶级 电影1/连续剧2/综艺3/动漫4）、ac=detail 字段齐全；" +
                "vod_play_url 每集是 https 分享页（/share/<32hex>），由 HlsUtil.resolveSharePage 换 m3u8，故 direct=false、无 playGap；" +
                "40 部同名样本 38 部搜索命中（95%）；搜索走默认 ac=detail&wd=，不再用非标 search/index",
        ),
        CmsSource(
            key = "ah_bttwo", name = "两个BT",
            api = "https://www.bttwo.life",
            speed = 24, direct = false, enabled = true, tier = 3,
            protocol = SourceProtocol.Html,
            caps = CAPS_WEB,
            rule = BTTWO_RULE,
            playGap = null,
            note = "2026-10-08 实测：搜索 3 条命中（/play/<slug>）、播放页含 dataid 与 userlink；播放链闭环——wasm 签名已逆向为纯 Kotlin（HMAC-SHA256，48 组交叉验证 + 真实端点 200）",
        ),
        CmsSource(
            key = "ah_mimi4k", name = "米米4k",
            api = "http://43.248.128.122:8080/api",
            speed = 25, direct = false, enabled = true, tier = 3,
            protocol = SourceProtocol.Mino,
            caps = CAPS_TOKEN_SEARCH,
            note = "2026-10-04 实测：服务在（/api/search 返回 code:0）但任何关键词 total:0，内容池空壳；" +
                "2026-10-27 复核：内容池已非空壳（搜索/详情真数据），但 POST /api/parse 一律 code:40110「请登录后观看」⇒ 误判为登录墙停用；" +
                "2026-10-31 复核（对照 AH 清单第四轮）：40110 根因=请求缺 X-Device-ID 头，补上（任意 32 位 hex）即免登录可播——" +
                "实测 /api/parse 出 cibn-edge-5g.1ljx.com m3u8 真 200 #EXTM3U，bilibili/youku/qq/qiyi 四线可用（mgtv/duanju 线坏）；" +
                "游客每日约 2 次配额绑在 X-Device-ID 上（code:40311 用尽），换新设备号即重置，适配器已实现自动换号重试",
        ),
        CmsSource(
            key = "ah_juzhi", name = "桔子",
            api = "http://103.36.167.27:18004",
            speed = 26, direct = false, enabled = true, tier = 3,
            protocol = SourceProtocol.Juzhi,
            caps = CAPS_TOKEN_SEARCH,
            note = "2026-10-04 实测全链路通：zone 二级公钥 → search 10 条 → getDetail 8 线路/289 集 → videoUsableUrl 换出 m3u8（直连 200 #EXTM3U）。两处与逆向规则不符，以实测为准：搜索 id 是 varint（规则当字符串读会得空）、换链 vodPlayFrom 必须传 Vid；" + "2026-10-27 复核：裸 IP api（http://103.36.167.27:18004）确实 502，但它不是业务入口——真实入口由 JuzhiRoot 读 ross.txt 动态下发（本次实测 ross.txt 200 / 98 B：domain=http://fuckducai.biosyne.cn, enabled=true, offlineUrl=https://juzhi7777.app）；新根域 POST /api/proto/v5/drama/search 返回 200「公共参数请求头不能为空」⇒ 端点活着、只缺 AES 公共参数头，源未死，保持 enabled=true",
        ),
        CmsSource(
            key = "ah_guapi", name = "瓜皮影视",
            api = "https://sdapi.e2wu4ht.com",
            speed = 27, direct = false, enabled = true, tier = 3,
            protocol = SourceProtocol.Guapi,
            caps = CAPS_TOKEN_SEARCH,
            mirrors = listOf(
                "https://api.w32z7vtd.com", "https://api.6a7nnf7.com",
                "https://api.umygrx3.com", "https://api.rmedphk.com",
            ),
            note = "2026-10-04/10-27 两轮把鉴权链判死是**误测**（10-27 探的 /api/* 根本不是该站的路由，业务路径在 /App/…；" +
                "10-04 缺全套协议头走进服务端异常分支）；2026-10-31 复核（对照 AH 清单第四轮 + tools/guazi_run.py 实测）：全链 T3——" +
                "新设备 signIn 恒 403 属预期，signUp 用随机 40 位大写 hex 自动注册拿 token（无限可注册），findMoreVod 99 条、" +
                "Vurl/show 36 集、showOne 换出 vd.wmvbo.com m3u8 200 #EXTM3U（真信封金样本 guapi_*2.json）；" +
                "caps 从 SEARCH_ONLY 修正为 TOKEN_SEARCH（此前 detail 实为不可用才标的）；播放需带 Referer=http://WJiZxLXA2.com/ + Lavf UA（适配器已带）",
        ),
        CmsSource(
            key = "ah_shanzha", name = "山楂4k",
            api = "http://qkys.qukanwh.com/api/",
            speed = 28, direct = false, enabled = false, tier = 3,
            protocol = SourceProtocol.Shanzha,
            caps = CAPS_SEARCH_ONLY,
            note = "2026-10-04 实测：POST /api/v1/app/search/searchMovie 返回 502 Bad Gateway（上游不可达），保留配置；" + "2026-10-27 复核：根路径 /api/ 仍 502 ⇒ 维持停用",
        ),
        CmsSource(
            key = "ah_nongmin", name = "农民影视",
            api = "https://nmystv.com",
            speed = 29, direct = false, enabled = false, tier = 3,
            protocol = SourceProtocol.Html,
            caps = CAPS_WEB,
            rule = NONGMIN_RULE,
            note = "2026-10-04/10-27 两次实测：老域 vip.wwgz.cn:5200 模板损坏（「缺少文件 …vod_search.html」）判死不变；" +
                "2026-10-31 复核（对照 AH 清单第四轮）：站点迁移新域 nmystv.com，全链 T3 实证——搜索/详情/播放页 player_aaaa 真数据，" +
                "1080zyk 线 m3u8 实拉分片 magic=0x47（庆余年 681 段/狂飙 1210 段/三体 345 段；master playlist 由播放器自动下钻）；" +
                "dbm3u8 线 404、nmdvd 线是相对令牌不可播。" +
                "【停用原因】站点 TLS 证书（Let's Encrypt 90 天，CN=www.nmystv.com）2026-10-29 07:59 GMT 到期未续，且 http 301 强跳 https；" +
                "App 传输层按契约校验证书（不放宽，ADR-0006 边界）→ 页面请求全部握手失败；流媒体 CDN（high26-playback.com 等）证书有效不受影响。" +
                "证书续期后把 enabled 改回 true 即可——规则、playerAaaa 播放链与金样本（nongmin2_*）均已就绪",
        ),
        CmsSource(
            key = "ah_subaibai", name = "素白白",
            api = "https://www.subaibai.com",
            speed = 30, direct = false, enabled = false, tier = 3,
            protocol = SourceProtocol.Html,
            caps = CAPS_WEB,
            rule = SUBAIBAI_RULE,
            note = "2026-10-04 实测：HTTPS 握手失败 / HTTP 返回 JS 反爬挑战页（_0x 混淆），未做绕过（ADR-0006 边界）；" + "2026-10-27 复核：HTTPS 仍 TLS 握手失败（UNEXPECTED_EOF_WHILE_READING），HTTP 返回 _0xcc9b 混淆挑战页（body 仅 <a id='baidu'> 与一段混淆 JS）⇒ 维持停用，不绕过",
        ),
        CmsSource(
            key = "hulu_huya", name = "虎牙采集",
            api = "https://www.huyaapi.com/api.php/provide/vod/",
            speed = 40, direct = true, enabled = true, tier = 3,
            note = "2026-11-20 Hululu007 tvbox-api-backup-- 聚合筛选：list/videolist/detail(ids)/detail(wd) 四端点 JSON 200，class=44，videolist=20，ids 与庆余年搜索均返回 vod_id/vod_play_url（m3u8 直链）；首页样本未见成人条目",
        ),
        CmsSource(
            key = "hulu_ikun", name = "ikun采集",
            api = "https://ikunzyapi.com/api.php/provide/vod/",
            speed = 41, direct = true, enabled = true, tier = 3,
            note = "2026-11-20 Hululu007 tvbox-api-backup-- 聚合筛选：list/videolist/detail(ids)/detail(wd) 四端点 JSON 200，class=53，videolist=20，ids 与庆余年搜索均返回 vod_id/vod_play_url（m3u8 直链）",
        ),
        CmsSource(
            key = "hulu_subo", name = "速播采集",
            api = "https://subocaiji.com/api.php/provide/vod/",
            speed = 42, direct = true, enabled = true, tier = 3,
            note = "2026-11-20 Hululu007 tvbox-api-backup-- 聚合筛选：list/videolist/detail(ids)/detail(wd) 四端点 JSON 200，class=44，videolist=20，ids 与庆余年搜索均返回 vod_id/vod_play_url（m3u8 直链）",
        ),
        CmsSource(
            key = "hulu_ffzy", name = "非凡资源",
            api = "http://ffzy.tv/api.php/provide/vod/",
            speed = 43, direct = true, enabled = true, tier = 3,
            note = "2026-11-20 Hululu007 tvbox-api-backup-- 聚合筛选：list/videolist/detail(ids)/detail(wd) 四端点 JSON 200，class=31，videolist=20，ids 与庆余年搜索均返回 vod_id/vod_play_url；站点为 HTTP，仅保留清单声明，传输层校验不放松",
        ),
        CmsSource(
            key = "hulu_mdzy", name = "魔都影视",
            api = "https://www.mdzyapi.com/api.php/provide/vod",
            speed = 44, direct = true, enabled = true, tier = 3,
            note = "2026-11-20 Hululu007 tvbox-api-backup-- 聚合筛选：list/videolist/detail(ids)/detail(wd) 四端点 JSON 200，class=42，videolist=20，ids 与庆余年搜索均返回 vod_id/vod_play_url（m3u8 直链）",
        ),
        CmsSource(
            key = "hulu_xptv_cg", name = "橙果短剧",
            api = "https://chengguodj.com",
            speed = 45, direct = false, enabled = true, tier = 3,
            protocol = SourceProtocol.Xptv,
            caps = CAPS_XPTV,
            note = "2026-11-21 Hululu007 xptv-my4k csp_chengguo 接入：Nuxt 扁平数组分类/搜索/详情可解析，播放页实测含 m3u8；只保留普通短剧标题，成人条目在解析层过滤",
        ),

        // ==================== Hululu007 清单批量接入（2026-11-21） ====================
        // 141 条里先剔除 41 条成人源（file=18sex.json / 名称带 🔞·色·麻豆·成人），
        // 再只保留 https + 标准 provide/vod 的 27 条实测可用端点；与已有源同主机去重后
        // 剩 10 条，其中搜索/详情都闭环的 4 条启用。其余 48 条非标准 CMS 或不可达，
        // 不做协议硬接（证据见 docs/04 §19）。speed 按实测响应排序。
        CmsSource(
            key = "hulu_hongniu", name = "红牛资源",
            api = "https://www.hongniuzy2.com/api.php/provide/vod/",
            speed = 46, direct = false, enabled = true, tier = 3,
            note = "2026-11-21 实测：ac=list 118333 条 2.3s；搜索「庆余年」8 条 36 集；首行是 /play/ 分享页，由 HlsUtil.resolveSharePage 换 m3u8",
        ),
        CmsSource(
            key = "hulu_aidan", name = "艾旦影视",
            api = "https://lovedan.net/api.php/provide/vod",
            speed = 47, direct = false, enabled = true, tier = 3,
            note = "2026-11-21 实测：ac=list 190257 条 4.2s；搜索「庆余年」12 条 36 集，播放链直接给 hd.ijycnd.com 的 m3u8",
        ),
        CmsSource(
            key = "hulu_xinlang", name = "新浪资源",
            api = "https://api.xinlangapi.com/xinlangapi.php/provide/vod",
            speed = 48, direct = false, enabled = true, tier = 3,
            note = "2026-11-21 实测：ac=list 118956 条 2.8s；搜索「流浪地球」5 条（「庆余年」该站索引为空）；首行 /play/ 分享页，第二行直连 m3u8",
        ),
        CmsSource(
            key = "hulu_uku", name = "U酷资源",
            api = "https://api.ukuapi.com/api.php/provide/vod",
            speed = 49, direct = false, enabled = true, tier = 3,
            note = "2026-11-21 实测：ac=list 60984 条 9.9s（较慢）；搜索「庆余年」3 条 71 集；首行 /share/ 分享页，第二行直连 m3u8",
        ),
    )

    /**
     * 直链源（编译期常量，不做在线源市场）。
     *
     * 2026-11-21：从 Hululu007 live-sources 的 live.m3u 选取五条公开频道，
     * 本机 curl -L 五条均 200（CCTV1 有 HLS 重导向）；只收 m3u8，不写凭据参数。
     * 这些是直播频道，不进入 CmsSources.ALL 的搜索/分类聚合，仅由「我的 → 直播频道」直接打开。
     */
    val DIRECT: List<DirectSource> = listOf(
        DirectSource(
            key = "live_hulu_cctv1", name = "CCTV-1 综合",
            url = "http://107.150.60.122/live/cctv1hd.m3u8",
            group = "央视",
        ),
        DirectSource(
            key = "live_hulu_cctv13", name = "CCTV-13 新闻",
            url = "http://120.76.248.139/live/bfgd/4200000067.m3u8",
            group = "央视",
        ),
        DirectSource(
            key = "live_hulu_cgtn", name = "CGTN 英语",
            url = "https://english-livebkali.cgtn.com/live/encgtn_0.m3u8",
            group = "国际",
        ),
        DirectSource(
            key = "live_hulu_dongfang", name = "东方卫视",
            url = "http://39.165.39.49:19901/tsfile/live/1128_1.m3u8",
            group = "卫视",
        ),
        DirectSource(
            key = "live_hulu_beijing", name = "北京卫视",
            url = "http://39.165.39.49:19901/tsfile/live/1130_1.m3u8",
            group = "卫视",
        ),
    )

    /**
     * 首页分类 Tab
     *
     * 【注意】名称必须与采集源的分类名一致，否则匹配失败。
     * 实测各源通用分类名（2026-10-03）：
     *   电视剧 / 电影 / 综艺 / 动漫 / 短剧 / 记录片
     * 曾踩坑：写成「剧集」「纪录片」都匹配不上。
     */
    val HOME_CATEGORIES: List<String> = listOf(
        "电影", "电视剧", "综艺", "动漫", "短剧", "记录片",
    )

    /** 分类名别名映射（若某些源用了不同叫法，在此统一） */
    val CATEGORY_ALIAS: Map<String, List<String>> = mapOf(
        "电视剧" to listOf("电视剧", "剧集", "连续剧", "电视节目"),
        "电影" to listOf("电影", "影片"),
        "综艺" to listOf("综艺", "综艺节目"),
        "动漫" to listOf("动漫", "动画", "动画片"),
        "短剧" to listOf("短剧", "微短剧"),
        "记录片" to listOf("记录片", "纪录片", "纪实"),
    )

    /** 按 key 查源；未知返回 null（**不回落第一项**——FR-305） */
    fun byKey(key: String?): CmsSource? = ALL.firstOrNull { it.key == key }

    /** 全部源的 api 主机集合（供 URL 校验） */
    fun apiHosts(): List<String> = ALL.map { it.api }.filter { it.isNotEmpty() }

    /** 全部源的基线（供覆盖层） */
    fun baselines(): List<com.dengying.player.domain.source.SourceOverrideRules.Baseline> =
        ALL.map { com.dengying.player.domain.source.SourceOverrideRules.Baseline(it.key, it.enabled) }
}
