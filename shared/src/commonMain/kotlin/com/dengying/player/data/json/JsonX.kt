package com.dengying.player.data.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 跨平台 JSON 取值层 —— 用 `org.json` 语义包住 `kotlinx.serialization.json`
 *
 * ## 为什么需要这一层
 *
 * 迁移前全项目有 96 处 `JSONObject` / 一批 `JSONArray`，用法集中在 `optString`(114)、
 * `.length`(32)、`optJSONObject`(43)、`optInt`(27) 这几类。直接把每个调用点改写成
 * `jsonObject["k"]?.jsonPrimitive?.contentOrNull` 不但改动面大，更危险的是**语义会漂**：
 *
 * | 场景 | `org.json` | `kotlinx` 原生写法 |
 * |---|---|---|
 * | `null` 字面量取字符串 | `optString(k)` 返回 fallback（无 fallback 则 `""`） | `contentOrNull` 返回 `null` |
 * | 取字段但类型不对 | `optString(k)` 把数字/布尔转成文本 | `jsonPrimitive` 抛异常 |
 * | 缺字段且无默认值 | `optString(k)` 返回 `""` | `getString` 抛异常 |
 * | 重复 key | 后者覆盖前者 | 同样后者覆盖 |
 *
 * 所以这里不是「顺手封装」，而是**把 org.json 的取值规则逐条固定下来**，
 * 让 adapter 的迁移退化成机械替换（`o.optString("k","")` → `JsonX.str(o,"k","")`），
 * 且新旧实现的输出可以逐字段比对（见 `docs/04` 的金样本测试）。
 *
 * ## 「解析失败→空结果」契约
 *
 * `org.json` 的构造函数遇到坏数据抛 `JSONException`；原代码一律 `try/catch` 成 `null`。
 * 这里的 [parseObject] / [parseArray] 保持同一契约：**任何解析失败返回 `null`，不抛**。
 * 调用方原有的 `?: return failed(...)` 分支因此一行都不用改。
 *
 * 所有函数都对**任意** [JsonElement?] 安全（含 `null` 与 [JsonNull]），不抛异常。
 */
object JsonX {

    /**
     * 解析配置：**逐项定死并写明依据**，不用默认值。
     *
     * - `isLenient = true`：`org.json` 对被引号缺失的裸字符串、单引号包裹的字符串是
     *   宽容的；源站接口（尤其小型采集站）偶有这类不合规响应。关掉它会把原本能解析的
     *   响应判成「解析失败」，等于引入行为差异。
     * - `ignoreUnknownKeys = true`：本项目**从不**按固定 schema 反序列化，全部是逐字段
     *   `optXxx` 取值；源站加字段是常态。这个开关对 `JsonElement` 遍历式用法其实不起
     *   作用（它只影响 `@Serializable` 解码），这里显式写上是为了防止以后有人加
     *   `@Serializable` 时忘掉——那时默认 `false` 会因为源站多一个字段就整条失败。
     * - `coerceInputValues = false`：默认值。`coerceInputValues` 会把「类型不符」静默
     *   改写成默认值，而 `org.json` 的 `optXxx` 是**尽力转换**（数字→字符串成立，
     *   对象→字符串取 `toString`）。开启它会让两者对不上，故保持关闭，由本文件显式
     *   实现 org.json 的转换规则。
     * - `allowTrailingComma` / `allowComments` 保持 `false`：`org.json` 的构造器同样不接受，
     *   放开就是放宽了「解析失败」的边界。
     */
    val json: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = false
    }

    // ---------------- 解析 ----------------

    /**
     * 解析成对象；失败返回 `null`（对齐 `org.json.JSONObject(text)` 抛异常的调用方约定）。
     *
     * 只接受**顶层是对象**的文本：`org.json.JSONObject(text)` 对 `"[1,2]"` 会抛异常，
     * 这里必须一致，否则 `safeJson` 的判空语义就会变。
     */
    fun parseObject(text: String?): JsonObject? {
        if (text.isNullOrEmpty()) return null
        return try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析成数组；失败返回 `null`（对齐 `org.json.JSONArray(text)`）。
     *
     * 顶层必须是数组。注意 `org.json.JSONObject` 也能吃 `[...]` 文本（它按 index 存），
     * 但项目里所有 `JSONArray(text)` 的调用点都明确期望数组，故这里要求数组。
     */
    fun parseArray(text: String?): JsonArray? {
        if (text.isNullOrEmpty()) return null
        return try {
            json.parseToJsonElement(text) as? JsonArray
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析成任意元素；失败返回 `null`。
     *
     * 给 `org.json.JSONObject(text)` 那种「对象或数组都收」的位置用。
     * 本项目只有 `StoreEnvelope` 需要这个语义。
     */
    fun parseAny(text: String?): JsonElement? {
        if (text.isNullOrEmpty()) return null
        return try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- 类型判断 ----------------

    /** 是否为「可当作对象看」的元素；`null`/`JsonNull` 返回 `null` */
    fun obj(e: JsonElement?): JsonObject? = e as? JsonObject

    /** 是否为数组；其余返回 `null` */
    fun arr(e: JsonElement?): JsonArray? = e as? JsonArray

    /** 是否为 `null` 字面量（含 Kotlin `null`） */
    fun isNull(e: JsonElement?): Boolean = e == null || e is JsonNull

    /**
     * 是否为**字符串**原语（`isString` 谓词）。
     *
     * 用来复刻原代码里的 `x is String` 收窄：`org.json` 的 `opt()` 返回 `Any?`，
     * 判 `is String` 能区分「真的字符串」与「数字/布尔/对象」；
     * `kotlinx` 的 [JsonPrimitive] 把三者都装在一个类型里，必须再看 [JsonPrimitive.isString]。
     */
    fun isString(e: JsonElement?): Boolean =
        e is JsonPrimitive && e.isString

    /** 是否有该字段（`org.json` 的 `has`：字段存在即真，值可以是 null） */
    fun has(o: JsonObject?, key: String): Boolean = o?.containsKey(key) == true

    /** 数组长度（`org.json` 的 `length()`） */
    fun len(a: JsonArray?): Int = a?.size ?: 0

    // ---------------- 取对象 / 数组 ----------------

    /** `optJSONObject(key)`：不是对象就返回 `null` */
    fun optObject(o: JsonObject?, key: String): JsonObject? = o?.get(key) as? JsonObject

    /** `optJSONArray(key)`：不是数组就返回 `null` */
    fun optArray(o: JsonObject?, key: String): JsonArray? = o?.get(key) as? JsonArray

    /** `arr.optJSONObject(i)`：越界或元素不是对象返回null` */
    fun optObjectAt(a: JsonArray?, i: Int): JsonObject? {
        if (a == null || i < 0 || i >= a.size) return null
        return a[i] as? JsonObject
    }

    /** `arr.optJSONArray(i)` */
    fun optArrayAt(a: JsonArray?, i: Int): JsonArray? {
        if (a == null || i < 0 || i >= a.size) return null
        return a[i] as? JsonArray
    }

    // ---------------- 取字符串 ----------------

    /**
     * `optString(key)` / `optString(key, fallback)`。
     *
     * 逐条对齐 org.json 的规则（这是迁移最容易出错的地方）：
     * - 字段缺失 → [fallback]
     * - 值是 `null` 字面量 → [fallback]（实测 `JSONObject.optString("a","X")` 对 `{"a":null}`
     *   返回 `"X"`，无 fallback 时返回 `""`；**不是**字符串 `"null"`。批 C-5 的 parity
     *   比对抓到过这条错误，探针见 docs/04 §13.7）
     * - 值是字符串 → 原样
     * - 值是数字/布尔 → 文本形式（`1` → `"1"`，`true` → `"true"`）
     * - 值是对象/数组 → 其 JSON 文本（org.json 走 `toString()`）
     */
    fun str(o: JsonObject?, key: String, fallback: String = ""): String {
        val e = o?.get(key) ?: return fallback
        return textOf(e, fallback)
    }

    /** `arr.optString(i)`越界返回 [fallback] */
    fun strAt(a: JsonArray?, i: Int, fallback: String = ""): String {
        if (a == null || i < 0 || i >= a.size) return fallback
        return textOf(a[i], fallback)
    }

    /**
     * 元素 → 文本，规则同 [str]。
     *
     * 注意这里****递归处理嵌套：对象/数组直接给 JSON 文本，与 `org.json` 一致。
     */
    fun textOf(e: JsonElement?, fallback: String = ""): String {
        if (e == null || e is JsonNull) return fallback
        return when (e) {
            is JsonPrimitive -> e.content
            else -> e.toString()
        }
    }

    // ---------------- 取数字 ----------------

    /**
     * `optInt(key)` / `optInt(key, fallback)`。
     *
     * org.json 的宽松性：数字字符串 `"12"` 能取到 12，`"12.9"` 取 12（截断），
     * 无法转换时返回 [fallback]（**不抛**）。布尔 `true` 在 org.json 里 `optInt` 取不到
     * （返回默认值），这里也保持不转。
     */
    fun int(o: JsonObject?, key: String, fallback: Int = 0): Int {
        val e = o?.get(key) ?: return fallback
        return intOf(e, fallback)
    }

    /** 元素 → Int，规则同 [int] */
    fun intOf(e: JsonElement?, fallback: Int = 0): Int {
        if (e == null || e is JsonNull) return fallback
        val p = e as? JsonPrimitive ?: return fallback
        p.longOrNull?.let { return it.toInt() }
        // 数字字符串（org.json 宽容）
        val d = p.doubleOrNull ?: p.content.trim().toDoubleOrNull() ?: return fallback
        return d.toInt()
    }

    /**
     * `optLong(key)` / `optLong(key, fallback)`。
     *
     * 与 [int] 同一套规则，只是不做 Int 截断。注意 org.json 对超范围值会**饱和**到
     * `Long.MAX_VALUE`/`MIN_VALUE`，这里 `toLong()` 的 Kotlin 语义是取低位——
     * 差异见 docs/04 §13 行为差异裁决表（源站不会下发超范围 id，实测无影响）。
     */
    fun long(o: JsonObject?, key: String, fallback: Long = 0L): Long {
        val e = o?.get(key) ?: return fallback
        return longOf(e, fallback)
    }

    /** 元素 → Long，规则同 [long] */
    fun longOf(e: JsonElement?, fallback: Long = 0L): Long {
        if (e == null || e is JsonNull) return fallback
        val p = e as? JsonPrimitive ?: return fallback
        p.longOrNull?.let { return it }
        val d = p.doubleOrNull ?: p.content.trim().toDoubleOrNull() ?: return fallback
        return d.toLong()
    }

    /** `optDouble(key)` */
    fun double(o: JsonObject?, key: String, fallback: Double = 0.0): Double {
        val e = o?.get(key) ?: return fallback
        return doubleOf(e, fallback)
    }

    /** 元素 → Double，规则同 [double] */
    fun doubleOf(e: JsonElement?, fallback: Double = 0.0): Double {
        if (e == null || e is JsonNull) return fallback
        val p = e as? JsonPrimitive ?: return fallback
        return p.doubleOrNull ?: p.content.trim().toDoubleOrNull() ?: fallback
    }

    // ---------------- 取布尔 ----------------

    /**
     * `optBoolean(key)` / `optBoolean(key, fallback)`。
     *
     * org.json 的宽容规则：`true`/`"true"`/`"TRUE"` 都算真，`false`/`"false"` 算假，
     * 其余返回 [fallback]。这里保持同一口径（大小写不敏感地比较文本）。
     */
    fun bool(o: JsonObject?, key: String, fallback: Boolean = false): Boolean {
        val e = o?.get(key) ?: return fallback
        return boolOf(e, fallback)
    }

    /** 元素 → Boolean，规则同 [bool] */
    fun boolOf(e: JsonElement?, fallback: Boolean = false): Boolean {
        if (e == null || e is JsonNull) return fallback
        val p = e as? JsonPrimitive ?: return fallback
        p.booleanOrNull?.let { return it }
        return when (p.content.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> fallback
        }
    }

    // ---------------- 便捷：`opt("k") ?: opt("k2")` 这类兜底链 ----------------

    /**
     * 「取第一个非空文本」—— 对应项目里大量的
     * `o.optString("a", "").ifEmpty { o.optString("b", "") }` 写法。
     *
     * 把这种链提出来是因为它**不止是省字**：原写法里 `ifEmpty` 只看结果是否为空串，
     * 而多字段兜底的语义本该是「字段缺失才换下一个」。这里用 [str] 的
     * fallback 语义实现，行为与逐字展开等价。
     */
    fun firstStr(o: JsonObject?, vararg keys: String): String {
        for (k in keys) {
            val s = str(o, k, "")
            if (s.isNotEmpty()) return s
        }
        return ""
    }

    /** 「取第一个能解析成正数的数字字段」—— 用于 `vod_id ?: id` 这类兜底 */
    fun firstLong(o: JsonObject?, vararg keys: String): Long {
        for (k in keys) {
            val v = longOf(o?.get(k), 0L)
            if (v != 0L) return v
        }
        return 0L
    }

    // ---------------- 遍历 ----------------

    /**
     * 遍历对象字段（`org.json` 的 `keys()`）。
     *
     * 返回快照列表而非序列，避免调用方在遍历中改动 map 引发的并发问题
     * （原 `keys()` 返回的是独立 iterator，语义等价）。
     */
    fun keys(o: JsonObject?): List<String> = o?.keys?.toList() ?: emptyList()

    // ---------------- 构造（写路径用） ----------------

    /** 把 Kotlin 值包成 `JsonElement`；`null` 转 `JsonNull` */
    fun value(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JsonElement -> v
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Double -> JsonPrimitive(v)
        is Float -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    /** 按顺序构造对象（保持插入顺序，便于稳定输出与快照比对） */
    fun buildObject(vararg pairs: Pair<String, Any?>): JsonObject {
        val m = LinkedHashMap<String, JsonElement>(pairs.size)
        for ((k, v) in pairs) m[k] = value(v)
        return JsonObject(m)
    }

    /** 构造数组 */
    fun buildArray(items: List<Any?>): JsonArray = JsonArray(items.map { value(it) })
}
