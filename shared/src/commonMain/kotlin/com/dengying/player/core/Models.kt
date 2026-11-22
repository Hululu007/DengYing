package com.dengying.player.core

/**
 * 数据模型 —— CMS 采集协议返回结构
 *
 * 与鸿蒙版 `common/Models.ets` 一一对应。字段名**必须**保持 `vod_*` 原样：
 * 它们是协议字段，采集站返回什么就存什么，不做驼峰转换——转换一次就多一处
 * 「哪一层负责映射」的歧义，且本地落盘的历史数据会与线上协议形状脱钩。
 */
data class VideoItem(
    val vod_id: Long = 0,
    val vod_name: String = "",
    val vod_pic: String = "",
    /** 更新状态，如 "更新至40集" */
    val vod_remarks: String? = null,
    val vod_year: String? = null,
    val vod_area: String? = null,
    val vod_lang: String? = null,
    val vod_actor: String? = null,
    val vod_director: String? = null,
    /** 简介 */
    val vod_content: String? = null,
    /** 播放源组名，多个用 $$$ 分隔 */
    val vod_play_from: String? = null,
    /** 剧集列表，格式: 第01集$url#第02集$url */
    val vod_play_url: String? = null,
    val type_name: String? = null,
    /** 原站寻址串（详情页绝对地址）：数字 id 无法寻址的源（如 slug 型路径）用它取详情 */
    val vod_uri: String? = null,
    /** 来源标识（本地附加，协议里没有这个字段） */
    val _source: String? = null,
)

/** 单集 */
data class Episode(
    val name: String,
    val url: String,
)

/** 播放线路 */
data class PlayLine(
    val name: String,
    val episodes: List<Episode>,
)

/** CMS 接口响应 */
data class CmsResponse(
    val code: Int = 0,
    val msg: String = "",
    val page: Int = 0,
    val pagecount: Int = 0,
    val limit: String = "",
    val total: Int = 0,
    val list: List<VideoItem> = emptyList(),
    val `class`: List<CmsClass> = emptyList(),
)

/** 分类 */
data class CmsClass(
    val type_id: Int = 0,
    val type_name: String = "",
    /** 父分类 id，顶级为 0；用于收集子分类 */
    val type_pid: Int? = null,
)

/** 收藏记录 */
data class FavoriteItem(
    val vod_id: Long = 0,
    val vod_name: String = "",
    val vod_pic: String = "",
    val source: String = "",
    val addTime: Long = 0,
    /** 原站寻址串，随条目一起落盘，保证 slug 型源也能从收藏直达详情 */
    val vod_uri: String? = null,
)

/** 播放历史 */
data class HistoryItem(
    val vod_id: Long = 0,
    val vod_name: String = "",
    val vod_pic: String = "",
    val source: String = "",
    val episodeIndex: Int = 0,
    val episodeName: String = "",
    /** 播放进度（毫秒） */
    val position: Long = 0,
    val duration: Long = 0,
    val updateTime: Long = 0,
    /** 原站寻址串，随历史一起落盘，保证 slug 型源也能续播 */
    val vod_uri: String? = null,
)

/**
 * 线路解析 —— 从协议字段还原成结构化线路表。
 *
 * 移植自 `Models.ets: parsePlayLines`，**规则逐条对应**：
 *
 * 输入示例：
 *   `"第01集$http://a.m3u8#第02集$http://b.m3u8"`        单线路
 *   `"线路1$$$线路2"` 结构配合 `vod_play_from` 使用        多线路
 *
 * 三个容易搞错的点：
 *  1. 线路名缺失时**不是跳过**，而是补 `线路N`（N 为 1 起序号）——
 *     跳过会让用户看到"线路 1/2/4"这种断号，以为丢了一条。
 *  2. 组内剧集用**第一个** `$` 分割名称与地址，不是 `split("$")` 取两段。
 *     地址里含 `$` 的情况真实存在（签名的 base64 尾部可能出现），
 *     取两段会把地址截断。
 *  3. 解析不出剧集的组**整组丢弃**，不产出空线路——否则详情页会多一个
 *     点了没反应的线路 chip。
 */
fun parsePlayLines(from: String?, url: String?): List<PlayLine> {
    if (url.isNullOrEmpty()) return emptyList()
    val fromNames: List<String> = if (from.isNullOrEmpty()) emptyList() else from.split("$$$")
    val urlGroups: List<String> = url.split("$$$")

    val lines = ArrayList<PlayLine>(urlGroups.size)
    for (i in urlGroups.indices) {
        val eps = parseEpisodes(urlGroups[i])
        if (eps.isEmpty()) continue
        lines += PlayLine(
            name = if (fromNames.size > i) fromNames[i] else "线路${i + 1}",
            episodes = eps,
        )
    }
    return lines
}

/**
 * 解析单组剧集字符串。
 *
 * 用 `indexOf('$')` 而非 `split('$')`：见 [parsePlayLines] 第 2 点。
 */
fun parseEpisodes(group: String?): List<Episode> {
    if (group.isNullOrEmpty()) return emptyList()
    val result = ArrayList<Episode>()
    for (part in group.split('#')) {
        if (part.isEmpty()) continue
        val idx = part.indexOf('$')
        if (idx < 0) continue
        val name = part.substring(0, idx)
        val url = part.substring(idx + 1)
        if (isPlayableEntry(url)) {
            result += Episode(name = name, url = url)
        }
    }
    return result
}

/** 形如 `/[\s<>"']/` 的空白与尖括号引号判定，用一次遍历避免建正则对象 */
private fun hasBadChar(url: String): Boolean {
    for (c in url) {
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r' ||
            c == '<' || c == '>' || c == '"' || c == '\''
        ) {
            return true
        }
    }
    return false
}

/**
 * 播放条目形态判定。
 *
 * 接受两类：
 *  1. 可直接拉取的地址：`http(s)://` 或协议相对 `//`
 *  2. 源侧 token（文才的 nid、独播库的 `/vodplay/...` 等）：非空、无空白与尖括号——
 *     这类值由 `resolveSourcePlay` 在播放前换出真实地址，形态上不是 URL 但对播放链路有效
 */
fun isPlayableEntry(url: String?): Boolean {
    if (url.isNullOrEmpty()) return false
    if (url.startsWith("http") || url.startsWith("//")) return true
    return !hasBadChar(url)
}
