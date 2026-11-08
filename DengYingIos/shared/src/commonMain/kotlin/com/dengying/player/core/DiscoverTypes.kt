package com.dengying.player.core

import com.dengying.player.platform.UrlCodec

/**
 * 发现层条目来源。
 *
 * 与 [CmsSource] 是**两套体系**：`CmsSource` 描述「站内可播放的内容源」（带 api/caps/rule/
 * playGap，条目必须能换链播放）；发现层条目是**站外元数据**，只有站外详情页地址，
 * 本应用无法换链。详见 `docs/04-验证记录.md` §15.6.1 的设计定稿。
 */
enum class DiscoverKind(
    /** 展示名 */
    val label: String,
) {
    /** 豆瓣动漫榜单（`movie.douban.com/j/new_search_subjects?tags=动漫`） */
    DoubanAnime("豆瓣动漫"),

    /** 爱奇艺搜索联想（`suggest.video.iqiyi.com`） */
    IqiyiSuggest("爱奇艺联想"),
}

/**
 * 发现层条目 —— **不是 [VideoItem]**。
 *
 * 刻意不复用 `VideoItem`：
 *  - `VideoItem._source` 必须指向一个能换链的源 key，发现层条目没有；
 *  - `VideoItem.vod_id` 是数字 id，而豆瓣 id 是字符串、爱奇艺联想词 `aid=0`；
 *  - 联想词压根不是作品（只是候选关键词），塞进 `VideoItem` 会得到一堆 id=0 的假条目。
 *
 * 字段形态由两个端点的实测响应决定，差异全部显式建模（见 [isEntity] / [year]）。
 */
data class DiscoverItem(
    /** 显示名。豆瓣 `title`；爱奇艺 `name` */
    val title: String = "",
    /** 副标题。豆瓣 `rate`（字符串如 "9.0"，空串表示暂无评分）；爱奇艺空 */
    val subtitle: String = "",
    /** 封面。豆瓣 `cover`；爱奇艺 `picture_url`（联想词为空串） */
    val pic: String = "",
    /**
     * 年份。豆瓣**没有这个字段**（恒空串）；爱奇艺 `year`。
     *
     * 注意：爱奇艺联想词一律返回 `2026`（当前年），**不可信**，不要用它做筛选。
     */
    val year: String = "",
    /** 站外详情页地址。豆瓣 `url`；爱奇艺 `link`（联想词为空串） */
    val link: String = "",
    /**
     * 站外 id。豆瓣 `id`（字符串，如 "37421775"）；爱奇艺 `vid`（长整数，联想词为 0）。
     *
     * 统一成字符串保存，因为豆瓣的 id 天生是字符串，转 Long 会丢前导位风险。
     */
    val externalId: String = "",
    /** 来源 */
    val kind: DiscoverKind = DiscoverKind.IqiyiSuggest,
    /**
     * 是否实体作品。
     *
     * 豆瓣恒 `true`；爱奇艺 = `aid > 0`（实测 `aid` 与 `vid` 同值，取 `aid`）。
     * 联想词（`aid=0`）的唯一用途是「当搜索关键词」，UI 应把它们渲染成可点的关键词而非作品卡片。
     */
    val isEntity: Boolean = true,
)

/**
 * 发现层一次拉取的结果。
 *
 * **刻意不并入 [FetchResult]**：`FetchResult.outcome` 是四态判定，参与源健康度统计
 * （`SourceHealthRules` / `SearchNotice` 的计数口径）。发现层端点会出现第三种失败形态——
 * 「本次拿不到、稍后再试」（豆瓣 IP 级风控），它既不是 `RequestFailed`（源没坏）
 * 也不是 `SuccessNoData`（不是确实没数据），硬塞四态会污染统计。
 * 因此这里只有「有没有拿到条目」+ 一条面向用户的 [err]。
 */
data class DiscoverFeed(
    val kind: DiscoverKind,
    val items: List<DiscoverItem> = emptyList(),
    /** 页号，从 0 起（豆瓣 `start` 就是 0 基数） */
    val page: Int = 0,
    /** 面向用户的失败说明；空表示成功 */
    val err: String = "",
) {
    val hasData: Boolean get() = items.isNotEmpty()
}

/**
 * 发现层端点 URL 构造 —— 单独成函数以便单测钉住拼接，
 * 避免 URL 拼装散落在解析代码里（P3 起全仓的既有约定）。
 */
object DiscoverQuery {

    const val IQIYI_SUGGEST_BASE: String = "https://suggest.video.iqiyi.com/"
    const val DOUBAN_SEARCH_BASE: String = "https://movie.douban.com/j/new_search_subjects"

    /**
     * 豆瓣榜单**老端点**。
     *
     * `new_search_subjects`（[DOUBAN_SEARCH_BASE]）在 2026-11-01 实测被豆瓣按 IP
     * 软风控：返回 `{"data":[]}` 空数组而非报错，连原本可用的「动漫」也一起失效。
     * `search_subjects` 风控更松，同 IP 连续 6 次请求仍返回 20 条（间隔 15–20s）。
     */
    const val DOUBAN_SUBJECTS_BASE: String = "https://movie.douban.com/j/search_subjects"

    /** 豆瓣动漫榜单默认标签 */
    const val DOUBAN_ANIME_TAG: String = "动漫"

    /** 豆瓣每页条数（实测一次 20 条是端点默认上限，显式传同值让行为可控） */
    const val DOUBAN_PAGE_SIZE: Int = 20

    /**
     * 爱奇艺搜索联想。
     *
     * `p=1` / `t=0` 是移动端接口的固定参数（`p` 页码、`t` 疑似时间戳，实测传 0 可用）。
     */
    fun iqiyiSuggestUrl(keyword: String): String =
        IQIYI_SUGGEST_BASE +
            "?if=mobile&key=" + UrlCodec.encode(keyword) +
            "&p=1&t=0"

    /**
     * 豆瓣条目搜索（动漫标签）。
     *
     * [start] 是 0 基偏移量（不是页号）。`sort=U` = 按「近期热度」。
     */
    fun doubanAnimeUrl(start: Int = 0, tag: String = DOUBAN_ANIME_TAG): String {
        val s = if (start < 0) 0 else start
        return DOUBAN_SEARCH_BASE +
            "?sort=U&range=0," + DOUBAN_PAGE_SIZE +
            "&tags=" + UrlCodec.encode(tag) +
            "&start=" + s
    }

    /**
     * 豆瓣分类榜单（`search_subjects` 老端点）。
     *
     * 条目字段与 [doubanAnimeUrl] 那个端点完全兼容（`title`/`rate`/`cover`/`url`/`id`），
     * 只有顶层数组名不同（`data` vs `subjects`），故解析器共用。
     *
     * @param type 豆瓣的粗分类：`movie` 或 `tv`
     * @param tag 细分标签（中文，如「热门」「综艺」「日本动画」）
     * @param start 0 基偏移量（不是页号）
     */
    fun doubanRankingUrl(type: String, tag: String, start: Int = 0): String {
        val s = if (start < 0) 0 else start
        return DOUBAN_SUBJECTS_BASE +
            "?type=" + UrlCodec.encode(type) +
            "&tag=" + UrlCodec.encode(tag) +
            "&sort=recommend&page_limit=" + DOUBAN_PAGE_SIZE +
            "&page_start=" + s
    }

    /** 豆瓣 `start` 偏移量 → 页号（0 基） */
    fun doubanPageOf(start: Int): Int = if (start < 0) 0 else start / DOUBAN_PAGE_SIZE
}

/**
 * 首页分类 → 豆瓣榜单 (type, tag) 的映射。
 *
 * 【为什么单独成对象】首页 6 个分类要各自对应豆瓣的一个榜单。这层映射是纯数据，
 * 与网络、Android 都无关，抽出来就能被单测钉住——否则「哪个分类对应哪个 tag」
 * 只能靠读 ViewModel 的 when 分支确认，改错了没人发现。
 *
 * 映射依据是 2026-11-01 实测（`docs/04-验证记录.md` §16.28）：六个组合全部返回 20 条。
 */
object DoubanRankingTag {

    /** 豆瓣 `type` 粗分类 */
    const val TYPE_MOVIE = "movie"
    const val TYPE_TV = "tv"
    /**
     * 豆瓣「短片」粗分类。
     *
     * 短剧的榜单只能用 `mv`：`tv/短剧` 实测返回 `{"subjects":[]}`（0 条），
     * 而 `mv/短剧` 返回 20 条且内容确实是短剧（《通灵妃》《十八岁》等）。
     */
    const val TYPE_MV = "mv"

    /**
     * 分类名 → (type, tag)；未知分类返回 null（**不回落第一个**——与
     * `CmsSources.byKey` 同一口径：猜一个错的榜单比不显示更糟）。
     */
    fun of(category: String): Pair<String, String>? = when (category) {
        "电影" -> TYPE_MOVIE to "热门"
        "电视剧" -> TYPE_TV to "热门"
        "综艺" -> TYPE_TV to "综艺"
        "动漫" -> TYPE_TV to "日本动画"
        "短剧" -> TYPE_MV to "短剧"
        "记录片" -> TYPE_MOVIE to "纪录片"
        else -> null
    }
}
