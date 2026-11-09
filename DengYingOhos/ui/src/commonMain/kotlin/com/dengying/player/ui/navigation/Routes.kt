package com.dengying.player.ui.navigation

import com.dengying.player.platform.UrlCodec
import com.dengying.player.platform.percentDecode

/**
 * 路由表与参数编码。
 *
 * 【为什么手写路由参数而不是用 Navigation Compose 的 type-safe routes】
 * 播放页要传 8 个参数，其中两个是**字符串数组**（剧集清单）。Navigation Compose
 * 的字符串路由对数组没有原生支持，type-safe routes（Kotlin Serialization）
 * 又会引入序列化插件与额外依赖。这里的每条路由只有一个入口与一个出口，
 * 手写一次 encode/decode 比引一套机制更清楚，且参数全部可打印（便于排查）。
 *
 * 【参数编码纪律】
 * 所有字符串参数一律 [enc]；数组用 `\u0001` 分隔（该字符不出现在业务数据里）。
 * **不要**把 URL 直接拼进 path —— 地址里带 `?` `&` `#` `%`，不编码会把路由拆坏。
 * 原工程用 router.pushUrl({params}) 的对象通道，Android 侧只能用字符串，
 * 故这一层必须补上编码。
 */
object Routes {
    const val INDEX = "index"
    const val SEARCH = "search"

    /**
     * 带初始关键词进搜索页（发现层榜单点击用）。
     *
     * 【为什么单独一个函数】[SEARCH] 是固定常量，`navigate(SEARCH)` 的既有调用点
     * 不该被迫改；只有需要预填关键词才用这个。空关键词返回裸 [SEARCH]，
     * 与原调用点完全等价。
     */
    fun search(keyword: String = ""): String =
        if (keyword.isBlank()) SEARCH else "$SEARCH?kw=${enc(keyword)}"
    const val DETAIL = "detail"
    const val PLAYER = "player"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SOURCE_STATUS = "source_status"
    /** AH 账号体系：独立登录 / 注册页，无参数（从「我的」页进入） */
    const val LOGIN = "login"
    const val REGISTER = "register"

    // ---- detail ----
    fun detail(
        vodId: Long,
        source: String,
        name: String,
        pic: String,
        vodUri: String = "",
        resumeEpisode: Int = -1,
        autoPlay: Boolean = false,
    ): String =
        "$DETAIL?vodId=$vodId&source=${enc(source)}&name=${enc(name)}&pic=${enc(pic)}" +
            "&vodUri=${enc(vodUri)}&resumeEpisode=$resumeEpisode&autoPlay=$autoPlay"

    // ---- player ----
    fun player(
        url: String,
        title: String,
        vodId: Long,
        source: String,
        vodName: String,
        vodPic: String,
        vodUri: String,
        lineFrom: String,
        episodeIndex: Int,
        episodeName: String,
        altUrls: List<String>,
        epUrls: List<String>,
        epNames: List<String>,
        /**
         * 起播位置（毫秒）。
         *
         * FR-705：详情页内嵌播放态点全屏键时，把当前进度带过来，
         * 避免全屏后从头播。默认 0 = 从头。
         *
         * 【为什么用路由参数而不是 VM 共享状态】详情页与播放页是两个独立的
         * ViewModel，靠路由传值最简单可靠，也不会在返回栈重建时丢失。
         */
        startAtMs: Long = 0L,
        /**
         * 适配器换链产出的请求头（JSON 字符串）。
         *
         * FR-705：内嵌态切全屏时，若地址由源适配器换出，必须把配套请求头一起带过去——
         * 否则播放页要重新换链才能拿到头，等于白解析一次。
         */
        headersJson: String = "",
    ): String {
        val sb = StringBuilder("$PLAYER?url=${enc(url)}&title=${enc(title)}&vodId=$vodId")
        sb.append("&source=").append(enc(source))
        sb.append("&vodName=").append(enc(vodName))
        sb.append("&vodPic=").append(enc(vodPic))
        sb.append("&vodUri=").append(enc(vodUri))
        sb.append("&lineFrom=").append(enc(lineFrom))
        sb.append("&episodeIndex=").append(episodeIndex)
        sb.append("&episodeName=").append(enc(episodeName))
        sb.append("&altUrls=").append(enc(altUrls.joinToString(SEP)))
        sb.append("&epUrls=").append(enc(epUrls.joinToString(SEP)))
        sb.append("&epNames=").append(enc(epNames.joinToString(SEP)))
        sb.append("&startAtMs=").append(startAtMs)
        sb.append("&headersJson=").append(enc(headersJson))
        return sb.toString()
    }

    /** 数组分隔符：Unicode 控制字符，不可能出现在片名/地址里 */
    const val SEP = "\u0001"
}

/** URL 编码（含 `+` 的空格处理：路由里空格必须是 %20，不能是 +） */
fun enc(s: String): String = UrlCodec.encode(s)

/**
 * URL 解码（null → 空串，非法转义保留字面，不抛）。
 *
 * 语义基准是 `android.net.Uri.decode`（迁移前的实现），**不是** `URLDecoder`：
 * 字面 `+` 原样保留（不转空格），非法 `%XX` 保留字面 `%` 而不抛异常。
 * 实现在 [percentDecode]，逐条差异见其 Kdoc。
 *
 * 返回非空而非 null：路由参数缺失与空值是同一件事（都由调用方的默认值兜住），
 * 调用方不必写 `?: ""`。
 */
fun dec(s: String?): String {
    if (s == null) return ""
    return percentDecode(s)
}

/** 把查询串解析成 map（同名键取第一个） */
fun queryOf(s: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (pair in s.split("&")) {
        if (pair.isEmpty()) continue
        val i = pair.indexOf('=')
        if (i < 0) continue
        val k = pair.substring(0, i)
        val v = pair.substring(i + 1)
        if (!out.containsKey(k)) out[k] = v
    }
    return out
}

/** 分隔符串 → 列表（空串 → 空列表） */
fun splitList(s: String): List<String> =
    if (s.isEmpty()) emptyList() else s.split(Routes.SEP)
