package com.dengying.player.core

/**
 * 日志标签的**单一来源**（OPS-004）—— 纯模块，不依赖 Android
 *
 * 需求：日志领域与标签统一。验收两条：**单一常量来源**；**按上下文分标签**。
 *
 * ## 改动前的混乱（实测统计）
 *
 * | 问题 | 数量 |
 * |---|---|
 * | `0x0000` 字面量散落 | **44 处** |
 * | `'HmVideo'` 字面量混用 | 5 个文件（适配器与页面里手写） |
 * | 各自 `const DOMAIN = 0x0000` | 10 个文件 |
 *
 * 后果：改一次领域号要动 15 个文件；而标签混用使日志过滤失效——
 * 排查"某个源的请求"或"播放链路"时，无法靠单一标签把相关日志捞出来。
 *
 * ## 为什么标签要按上下文分
 *
 * 一个标签只描述**一个上下文**，排查时才能收敛范围。混用同一个标签会让
 * 过滤结果里混入无关上下文，等于没有标签。
 *
 * ## 与鸿蒙版的差异
 *
 * 鸿蒙用 `hilog` 的 16 位领域号做子系统过滤。Android 的 `Log` 没有领域号概念，
 * 等价的过滤维度是 **tag**。故这里保留「单一来源 + 按上下文分标签」两条契约，
 * 并把原来的领域号折进 tag 前缀（见 [tag]），使 `adb logcat -s DengYing.*`
 * 能一次捞出本应用全部日志——这正是原领域号要解决的问题。
 */
object LogTags {

    /**
     * 标签前缀。
     *
     * 原鸿蒙版的领域号是 `0xD001`（D = 自建应用惯例位），用于按子系统过滤。
     * Android 侧用字符串前缀达到同样效果：带前缀的标签可以整组过滤，
     * 不带前缀的标签混在系统日志里捞不出来。
     */
    const val PREFIX = "DY"

    /**
     * Android 的 tag 上限是 23 字符（`Log.isLoggable` 的约定，超长会被截断）。
     * 本对象用 [isValidTag] 在单测层把这条约束固定住，而不是等运行期截断。
     */
    const val TAG_MAX_LEN = 23

    /** 拼出最终 tag：`DY/Player` */
    fun tag(name: String): String = "$PREFIX/$name"

    // ---- 标签登记表：一个上下文一个标签 ----

    /** 应用生命周期 */
    const val APP = "App"

    /** 聚合入口：搜索、分类、详情的编排与收口 */
    const val CMS_API = "CmsApi"

    /** 桔子源适配器（protobuf 协议） */
    const val JUZHI = "Juzhi"

    /** 网站型源的正则抓取适配器 */
    const val WEB = "Web"

    /** 详情页 */
    const val DETAIL = "Detail"

    /** 首页（分类浏览与分页） */
    const val INDEX = "Index"

    /** 搜索页 */
    const val SEARCH = "Search"

    /** 播放页（状态机、绑定、换线、全屏） */
    const val PLAYER = "Player"

    /** 地址解析与可达性探测 */
    const val HLS = "Hls"

    /** 本地存储与个人资产 */
    const val STORE = "Store"

    /** 投屏端口与状态 */
    const val CAST = "Cast"

    /** 破坏性操作确认弹窗 */
    const val CONFIRM = "Confirm"

    /** 提示浮层 */
    const val TOAST = "Toast"

    /** 收藏与观看记录页 */
    const val FAVORITES = "Favorites"

    /** 观看历史页 */
    const val HISTORY = "History"

    /** HTTP 客户端与并发闸门 */
    const val HTTP = "Http"

    /**
     * 全部登记标签。
     *
     * 供单测断言「无重复、长度合规、非空」——登记表本身的正确性也要能被验证，
     * 否则拼错一个标签只会在排查时才发现。
     */
    fun allTags(): List<String> = listOf(
        APP, CMS_API, JUZHI, WEB, DETAIL, INDEX,
        SEARCH, PLAYER, HLS, STORE, CAST, CONFIRM,
        TOAST, FAVORITES, HISTORY, HTTP,
    )

    /** 全部登记标签的**最终形态**（含前缀），供单测核对长度 */
    fun allFinalTags(): List<String> = allTags().map { tag(it) }

    /**
     * 标签是否可用。
     *
     * 三条：非空、加上前缀后不超上限、不含空白与逗号——
     * logcat 的过滤器以空格与逗号切分表达式，标签里含这些字符会使过滤失灵。
     */
    fun isValidTag(name: String): Boolean {
        if (name.isEmpty()) return false
        if (tag(name).length > TAG_MAX_LEN) return false
        for (c in name) {
            if (c == ' ' || c == '\t' || c == '\n' || c == ',') return false
        }
        return true
    }
}
