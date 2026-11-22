package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.FetchResult
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.SourceSecrets
import com.dengying.player.core.VideoItem
import com.dengying.player.data.json.JsonX
import com.dengying.player.data.source.crypto.Rsa
import kotlinx.serialization.json.JsonElement

/**
 * 解析层：山楂4k（RSA 加密体 + 多候选字段名容错）
 *
 * 从 `ShanzhaAdapter` 拆出，只做「字符串 ↔ 数据」的转换，不含任何 HTTP 调用。
 *
 * ## 这个源的特殊之处：字段名靠猜
 *
 * 山楂的接口文档不可得，线上又长期 502（`Sources.kt` 的 `ah_shanzha` 备注与
 * 2026-10-04 实测均如此），所以原作者用**多候选名 + 取第一个非空**的方式兜底：
 * `id` / `vodId` / `movieId`、`name` / `vodName` / `title`、
 * `pic` / `cover` / `vodPic`、`remarks` / `state` / `score`。
 * 列表容器也这么猜：`data.list` → `data.records` → `data.rows` → 顶层 `list`。
 *
 * 这套「猜」是**既有行为**，不是本文件引入的容错。迁移时逐条照搬，
 * 因为上游一旦恢复，实际命中的是哪一组候选名只有线上才说得清。
 *
 * ## `pick*` 的语义
 *
 * 原实现签名是 `pickNumber(vararg candidates: Any?)`，靠 `is Number` 判断类型
 * （`org.json` 的 `opt()` 返回 `Any?`）。迁移后元素是 [JsonElement]，
 * 判类型改用 [JsonX.longOf] / [JsonX.textOf]，两者都复刻了 org.json 的
 * 「数字字符串宽容」与「`null` 字面量转 `"null"`」规则。
 */
object ShanzhaParser {

    /** 组装搜索请求体：`{"condition":{"value":"<kw>"},"pageNum":1,"pageSize":40}` */
    fun searchBody(keyword: String): String = JsonX.buildObject(
        "condition" to JsonX.buildObject("value" to keyword),
        "pageNum" to 1,
        "pageSize" to 40,
    ).toString()

    /**
     * RSA 加密搜索体：`{"key":"<Base64(RSA_PKCS1(明文, 117B 分块))>"}`
     *
     * 山楂要求把明文 JSON 用**请求公钥**加密后提交。该函数当前无调用者
     * （线上 502，搜索走的是明文 [searchBody]），保留是为了上游恢复时能直接接上。
     */
    fun encryptedBody(plainJson: String): String {
        val pem = Rsa.wrapPem(SourceSecrets.SHANZHA_REQUEST_PUBLIC_KEY, "PUBLIC KEY")
        val b64 = Rsa.encryptToBase64(pem, plainJson, 117)
        return JsonX.buildObject("key" to b64).toString()
    }

    /** 响应解密用的私钥 PEM（2048 位，256B 分块） */
    fun responseKeyPem(): String =
        Rsa.wrapPem(SourceSecrets.SHANZHA_RESPONSE_PRIVATE_KEY, "PRIVATE KEY")

    fun failed(
        source: CmsSource,
        err: String,
        outcome: RequestOutcome = RequestOutcome.RequestFailed,
    ) = FetchResult(outcome = outcome, page = 1, sourceName = source.name, err = err)

    /**
     * 取候选里第一个**正数**。
     *
     * 逐个尝试 `id` / `vodId` / `movieId`，第一个 > 0 的胜出——注意是
     * 「> 0」而不是「非空」，所以候选里放一个 0 会继续往下找。
     */
    private fun pickNumber(vararg candidates: JsonElement?): Long {
        for (c in candidates) {
            val n = JsonX.longOf(c, 0L)
            if (n > 0L) return n
        }
        return 0L
    }

    /** 取候选里第一个非空文本（`null` 与 `JsonNull` 都跳过） */
    private fun pickString(vararg candidates: JsonElement?): String {
        for (c in candidates) {
            if (JsonX.isNull(c)) continue
            val s = JsonX.textOf(c)
            if (s.isNotEmpty()) return s
        }
        return ""
    }

    /**
     * 解析搜索结果。
     *
     * 容器回退链：`data.list` → `data.records` → `data.rows` → 顶层 `list` → 空数组。
     * `total` 取 `data.total`，缺失时回落条目数。
     */
    fun parseSearch(source: CmsSource, body: String?): FetchResult {
        val raw = JsonX.parseObject(body) ?: return failed(source, "响应非 JSON")

        val data = JsonX.optObject(raw, "data")
        val rows = JsonX.optArray(data, "list")
            ?: JsonX.optArray(data, "records")
            ?: JsonX.optArray(data, "rows")
            ?: JsonX.optArray(raw, "list")

        val items = ArrayList<VideoItem>(JsonX.len(rows))
        for (i in 0 until JsonX.len(rows)) {
            val row = JsonX.optObjectAt(rows, i) ?: continue
            val id = pickNumber(row["id"], row["vodId"], row["movieId"])
            val name = pickString(row["name"], row["vodName"], row["title"])
            if (id <= 0L || name.isEmpty()) continue
            items += VideoItem(
                vod_id = id,
                vod_name = name,
                vod_pic = pickString(row["pic"], row["cover"], row["vodPic"]),
                vod_remarks = pickString(row["remarks"], row["state"], row["score"]),
                _source = source.key,
            )
        }

        val outcome =
            if (items.isNotEmpty()) RequestOutcome.SuccessWithData else RequestOutcome.SuccessNoData
        return FetchResult(
            outcome = outcome, items = items, page = 1, pageCount = 1,
            total = if (data != null) JsonX.int(data, "total", items.size) else items.size,
            sourceName = source.name,
        )
    }

    /**
     * 从换链接口响应里取 `url`。
     *
     * 原实现判断 `direct is String`——即**只有字符串才认**，数字/对象一律空串。
     * 这里用「是否 JsonPrimitive 且是字符串」保持同一收窄。
     */
    fun playUrl(body: String?): String {
        val raw = JsonX.parseObject(body) ?: return ""
        val direct = raw["url"] ?: return ""
        return if (JsonX.isString(direct)) JsonX.textOf(direct) else ""
    }
}
