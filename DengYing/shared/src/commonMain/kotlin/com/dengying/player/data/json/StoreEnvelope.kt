package com.dengying.player.data.json

/**
 * 本地数据版本信封（FR-508）—— 纯函数，不依赖平台
 *
 * 问题：三个列表键（`favorites`/`history`/`search_history`）原为**裸数组 JSON**，
 * 结构一旦演进就无法识别旧数据，`JSON.parse` 失败还只能静默返回空集合——
 * 用户数据无声消失。本对象给每个键加上 `{version, payload}` 信封，
 * 使「读得懂 / 读不懂但保住 / 确实坏了」三种情形可区分。
 *
 * 版本约定：
 *
 * | 存储内容 | 判定 | 处置 |
 * |---|---|---|
 * | 裸数组（旧格式） | `version = 0` | 直读；**惰性升级**——首次写入时改写信封 |
 * | `{version: 当前, payload}` | `version = 1` | 直读 |
 * | `{version: > 当前, ...}` | 更高版本 | **不清空、不覆盖**：读返回空集合并置 `needsUpgrade`，写拒绝该键 |
 * | 空串（键不存在） | 无数据 | 按 `version = 0` 空集合处理 |
 * | 其他任意内容 | 损坏 | 读返回空集合，调用方把原始串留到 `store_corrupt` 诊断键 |
 *
 * 单键损坏不连带他键：判定与处置都以**单键**为粒度。
 *
 * 【实现说明】`payload` 是**字符串**（内层数组的 JSON 文本），与 ArkTS 版一致——
 * 双层编码是刻意的：信封层不需要知道内层结构，内层演进时信封层零改动。
 *
 * 【为什么自己写解析而不引库（KMP P3 订正）】这条取舍在 P3 批 E 被推翻：
 * 当时拒绝引库的理由是「手写 JSON 解析器换来的零依赖，代价是它自己成为
 * 需要测试的代码，收益为负」。P3 引入了 kotlinx-serialization（设计稿 §4 的
 * 依赖决策 D-1=A）之后，`org.json` 在 Kotlin/Native 上根本不存在，继续自己写
 * 解析器就变成「为了不用一个已在依赖清单里的库，再写一个解析器」——同一笔账
 * 现在倒过来算。故本文件改用 [JsonX]（org.json 语义的跨平台门面）。
 *
 * 【字节形态变化】`org.json` 的 `toString()` 会转义 `/` 为 `\/`，且键序来自
 * HashMap；[JsonX] 不转义 `/`、键序按插入序。**读路径两者互相兼容**，故这是
 * 「格式兼容、字节不同」，不影响任何既有数据（见 docs/04 §13.12 裁决表）。
 */
object StoreEnvelope {

    /** 当前数据结构版本。新增字段或改变语义时递增。 */
    const val CURRENT_VERSION = 1

    /** 损坏数据的诊断键（保留原始串，避免无声清空） */
    const val KEY_CORRUPT = "store_corrupt"

    /** 空集合载荷 */
    const val EMPTY_PAYLOAD = "[]"

    /** 读判定结果种类 */
    enum class Kind { Ok, Corrupt, NeedsUpgrade }

    data class DecodeResult(
        /** 可用（含旧格式） / 损坏 / 由更高版本写入 */
        val kind: Kind,
        /** 解出的版本号；`Corrupt` 时为 -1 */
        val version: Int,
        /** 可用的 JSON 数组文本；不可用时为 `[]` */
        val payload: String,
        /** 存储版本高于当前版本（不可清空，需提示用户升级） */
        val needsUpgrade: Boolean,
    )

    /**
     * 写入：把载荷包成信封。
     *
     * @param payloadJson 载荷的 JSON 文本（数组）
     * @return 带版本号的信封文本
     */
    fun encodePayload(payloadJson: String): String =
        JsonX.buildObject("version" to CURRENT_VERSION, "payload" to payloadJson).toString()

    /**
     * 读取：判定版本与可用性。
     *
     * 不抛异常——一切异常都归入 `Corrupt`，由调用方决定留痕与提示。
     */
    fun decodePayload(raw: String?): DecodeResult {
        if (raw.isNullOrEmpty()) {
            return DecodeResult(Kind.Ok, 0, EMPTY_PAYLOAD, false)
        }
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return DecodeResult(Kind.Ok, 0, EMPTY_PAYLOAD, false)
        }

        // 旧格式：裸数组即 version 0。
        // 先按数组试解析，成功即直读——这条分支覆盖"升级前的数据"。
        if (trimmed.startsWith("[")) {
            return if (JsonX.parseArray(trimmed) != null) {
                DecodeResult(Kind.Ok, 0, trimmed, false)
            } else {
                corrupt()
            }
        }

        if (!trimmed.startsWith("{")) return corrupt()

        val obj = JsonX.parseObject(trimmed) ?: return corrupt()

        // 信封必须带**数字** version；字符串 "1" 不算（不猜类型，不替存储纠错）
        if (!JsonX.has(obj, "version")) return corrupt()
        if (JsonX.isNull(obj["version"])) return corrupt()
        val v = JsonX.int(obj, "version", Int.MIN_VALUE)
        if (v == Int.MIN_VALUE) return corrupt()

        if (v > CURRENT_VERSION) {
            // 更高版本写入：不清空、不覆盖，交由调用方提示用户升级
            return DecodeResult(Kind.NeedsUpgrade, v, EMPTY_PAYLOAD, true)
        }

        // payload 必须是字符串；非字符串一律按空载荷处理（不猜结构）
        val body = obj["payload"]
        val text = if (JsonX.isString(body)) JsonX.textOf(body) else ""
        val inner = text.ifEmpty { EMPTY_PAYLOAD }

        // 内层必须是数组文本
        if (JsonX.parseArray(inner) == null) return corrupt()
        return DecodeResult(Kind.Ok, v, inner, false)
    }

    private fun corrupt() = DecodeResult(Kind.Corrupt, -1, EMPTY_PAYLOAD, false)

    /**
     * 该键是否需要在下次写入时改写信封（惰性升级）。
     *
     * 仅旧格式（version < 当前）需要；损坏与更高版本都不改写。
     */
    fun needsLazyUpgrade(raw: String?): Boolean {
        val d = decodePayload(raw)
        return d.kind == Kind.Ok && d.version < CURRENT_VERSION
    }

    /**
     * 是否允许对该键整体重写。
     *
     * 更高版本写入的数据**拒绝**被旧结构覆盖，否则会抹掉用户数据。
     */
    fun canOverwrite(raw: String?): Boolean = decodePayload(raw).kind != Kind.NeedsUpgrade
}
