package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.SourceCaps
import com.dengying.player.core.SourceProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 山楂4k 解析守卫（批 C-3）。
 *
 * 上游 `qkys.qukanwh.com` 长期 502，金样本录不到，所以这里钉的是
 * **接口契约**而不是线上样本：容器回退链、候选字段名容错、跳过规则、
 * `total` 缺省、换链接口、请求体形状。
 *
 * 这些期望值的来源：先用 org.json 原实现（`git show 91f400e`）与
 * [ShanzhaParser] 做 12 组 parity 比对全绿，再把同一批输入固化成断言。
 */
class ShanzhaGoldenTest {

    private val src = CmsSource(
        key = "ah_shanzha",
        name = "山楂4k",
        api = "http://qkys.qukanwh.com/api/",
        speed = 28,
        direct = false,
        enabled = false,
        protocol = SourceProtocol.Shanzha,
        caps = SourceCaps(search = true, category = false, detail = false, token = true),
    )

    // ---------------- 容器回退链 ----------------

    @Test
    fun container_falls_back_list_records_rows_then_toplevel() {
        assertEquals(
            listOf("A"),
            ShanzhaParser.parseSearch(src, """{"data":{"list":[{"id":1,"name":"A"}]}}""")
                .items.map { it.vod_name },
        )
        assertEquals(
            listOf("B"),
            ShanzhaParser.parseSearch(src, """{"data":{"records":[{"id":2,"name":"B"}]}}""")
                .items.map { it.vod_name },
        )
        assertEquals(
            listOf("C"),
            ShanzhaParser.parseSearch(src, """{"data":{"rows":[{"id":3,"name":"C"}]}}""")
                .items.map { it.vod_name },
        )
        assertEquals(
            listOf("D"),
            ShanzhaParser.parseSearch(src, """{"list":[{"id":4,"name":"D"}]}""")
                .items.map { it.vod_name },
        )
    }

    @Test
    fun list_wins_over_records() {
        val r = ShanzhaParser.parseSearch(
            src,
            """{"data":{"list":[{"id":1,"name":"L"}],"records":[{"id":2,"name":"R"}]}}""",
        )
        assertEquals(listOf("L"), r.items.map { it.vod_name })
    }

    // ---------------- 候选字段名 ----------------

    @Test
    fun id_candidates_in_order() {
        assertEquals(7L, idOf("""{"id":7}"""))
        assertEquals(8L, idOf("""{"vodId":8}"""))
        assertEquals(9L, idOf("""{"movieId":9}"""))
        // 0 与负数都算「没取到」，继续找下一个候选
        assertEquals(10L, idOf("""{"id":0,"vodId":10}"""))
        assertEquals(11L, idOf("""{"id":-5,"movieId":11}"""))
    }

    @Test
    fun id_accepts_numeric_strings() {
        assertEquals(12L, idOf("""{"id":"12"}"""))
        assertEquals(13L, idOf("""{"id":" 13 "}"""))
        assertEquals(14L, idOf("""{"id":"abc","vodId":14}"""))
    }

    @Test
    fun id_truncates_float_and_rejects_boolean() {
        assertEquals(1L, idOf("""{"id":1.9}"""))
        // 布尔不在 pickNumber 的接受范围，落到下一个候选
        assertEquals(15L, idOf("""{"id":true,"vodId":15}"""))
    }

    @Test
    fun name_candidates_in_order() {
        assertEquals("N1", nameOf("""{"id":1,"name":"N1"}"""))
        assertEquals("N2", nameOf("""{"id":1,"vodName":"N2"}"""))
        assertEquals("N3", nameOf("""{"id":1,"title":"N3"}"""))
        // 空串与 null 都继续往下找
        assertEquals("N4", nameOf("""{"id":1,"name":"","vodName":"N4"}"""))
        assertEquals("N5", nameOf("""{"id":1,"name":null,"title":"N5"}"""))
    }

    @Test
    fun name_accepts_number_and_boolean_as_text() {
        assertEquals("123", nameOf("""{"id":1,"name":123}"""))
        assertEquals("true", nameOf("""{"id":1,"name":true}"""))
    }

    @Test
    fun pic_candidates_in_order() {
        assertEquals("P1", picOf("""{"id":1,"name":"A","pic":"P1"}"""))
        assertEquals("P2", picOf("""{"id":1,"name":"A","cover":"P2"}"""))
        assertEquals("P3", picOf("""{"id":1,"name":"A","vodPic":"P3"}"""))
        assertEquals("P4", picOf("""{"id":1,"name":"A","pic":"","cover":"P4"}"""))
        assertEquals("", picOf("""{"id":1,"name":"A"}"""))
    }

    @Test
    fun remarks_candidates_in_order() {
        assertEquals("R1", remarksOf("""{"id":1,"name":"A","remarks":"R1"}"""))
        assertEquals("R2", remarksOf("""{"id":1,"name":"A","state":"R2"}"""))
        assertEquals("9.6", remarksOf("""{"id":1,"name":"A","score":"9.6"}"""))
        assertEquals("9", remarksOf("""{"id":1,"name":"A","score":9}"""))
        assertEquals("9.6", remarksOf("""{"id":1,"name":"A","score":9.6}"""))
    }

    // ---------------- 跳过规则 ----------------

    @Test
    fun rows_are_dropped_when_id_or_name_missing() {
        assertEquals(0, count("""{"data":{"list":[{"id":0,"name":"A"}]}}"""))
        assertEquals(0, count("""{"data":{"list":[{"name":"A"}]}}"""))
        assertEquals(0, count("""{"data":{"list":[{"id":1}]}}"""))
        assertEquals(0, count("""{"data":{"list":[{"id":1,"name":""}]}}"""))
    }

    @Test
    fun non_object_rows_are_skipped_without_aborting() {
        assertEquals(1, count("""{"data":{"list":[null,{"id":1,"name":"A"}]}}"""))
        assertEquals(1, count("""{"data":{"list":["str",{"id":1,"name":"A"}]}}"""))
        assertEquals(1, count("""{"data":{"list":[[1,2],{"id":1,"name":"A"}]}}"""))
    }

    @Test
    fun order_is_preserved() {
        val r = ShanzhaParser.parseSearch(
            src,
            """{"data":{"list":[{"id":5,"name":"E"},{"id":4,"name":"D"},{"id":3,"name":"C"}]}}""",
        )
        assertEquals(listOf("E", "D", "C"), r.items.map { it.vod_name })
    }

    @Test
    fun every_item_carries_source_key() {
        val r = ShanzhaParser.parseSearch(src, """{"data":{"list":[{"id":1,"name":"A"}]}}""")
        assertEquals("ah_shanzha", r.items.single()._source)
    }

    // ---------------- total / 四态 ----------------

    @Test
    fun total_comes_from_data_total_else_item_count() {
        assertEquals(99, totalOf("""{"data":{"total":99,"list":[{"id":1,"name":"A"}]}}"""))
        assertEquals(2, totalOf("""{"data":{"list":[{"id":1,"name":"A"},{"id":2,"name":"B"}]}}"""))
        assertEquals(77, totalOf("""{"data":{"total":"77","list":[{"id":1,"name":"A"}]}}"""))
        assertEquals(1, totalOf("""{"data":{"total":null,"list":[{"id":1,"name":"A"}]}}"""))
        assertEquals(2, totalOf("""{"list":[{"id":1,"name":"A"},{"id":2,"name":"B"}]}"""))
    }

    @Test
    fun empty_result_is_success_no_data_not_failure() {
        val r = ShanzhaParser.parseSearch(src, """{"data":{"list":[]}}""")
        assertEquals(RequestOutcome.SuccessNoData, r.outcome)
        assertTrue(r.items.isEmpty())
    }

    @Test
    fun non_empty_result_is_success_with_data() {
        val r = ShanzhaParser.parseSearch(src, """{"data":{"list":[{"id":1,"name":"A"}]}}""")
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
    }

    @Test
    fun malformed_bodies_fail_with_message() {
        for ((body, where) in listOf(
            null to "null",
            "" to "empty",
            "not json" to "garbage",
            "[1,2,3]" to "top-level array",
        )) {
            val r = ShanzhaParser.parseSearch(src, body)
            assertEquals(RequestOutcome.RequestFailed, r.outcome, where)
            assertEquals("响应非 JSON", r.err, where)
            assertTrue(r.items.isEmpty(), where)
        }
    }

    @Test
    fun wrong_shaped_containers_degrade_to_empty_not_failure() {
        // org.json 的 optJSONObject/optJSONArray 遇到类型不符返回 null 而不抛：
        // 于是容器链全部落空 → 空列表 → SuccessNoData。这是迁移前的既有语义。
        for ((body, where) in listOf(
            """{"data":"str"}""" to "data is string",
            """{"data":{"list":"notarray"}}""" to "list is not array",
            """{"data":{"list":{}}}""" to "list is object",
            """{"data":[]}""" to "data is array",
        )) {
            val r = ShanzhaParser.parseSearch(src, body)
            assertEquals(RequestOutcome.SuccessNoData, r.outcome, where)
            assertTrue(r.items.isEmpty(), where)
            assertEquals("", r.err ?: "", where)
        }
    }

    // ---------------- 换链接口 ----------------

    @Test
    fun play_url_only_accepts_json_string() {
        assertEquals("http://x/a.m3u8", ShanzhaParser.playUrl("""{"url":"http://x/a.m3u8"}"""))
        assertEquals("", ShanzhaParser.playUrl("""{"url":123}"""))
        assertEquals("", ShanzhaParser.playUrl("""{"url":null}"""))
        assertEquals("", ShanzhaParser.playUrl("""{}"""))
        assertEquals("", ShanzhaParser.playUrl("garbage"))
        assertEquals("", ShanzhaParser.playUrl(""))
        assertEquals("", ShanzhaParser.playUrl(null))
    }

    // ---------------- 请求体 ----------------

    @Test
    fun search_body_shape() {
        val body = ShanzhaParser.searchBody("三体")
        assertEquals(
            """{"condition":{"value":"三体"},"pageNum":1,"pageSize":40}""",
            body,
        )
    }

    @Test
    fun search_body_escapes_specials() {
        // 引号、反斜杠、尖括号由序列化器转义；中文与 emoji 原样保留
        assertEquals(
            """{"condition":{"value":"a\"b"},"pageNum":1,"pageSize":40}""",
            ShanzhaParser.searchBody("a\"b"),
        )
        assertEquals(
            """{"condition":{"value":"a\\b"},"pageNum":1,"pageSize":40}""",
            ShanzhaParser.searchBody("a\\b"),
        )
        assertEquals(
            """{"condition":{"value":"emoji🎬"},"pageNum":1,"pageSize":40}""",
            ShanzhaParser.searchBody("emoji🎬"),
        )
    }

    // ---------------- 小工具 ----------------

    private fun bodyOf(row: String): String {
        // 这些用例只考察「候选字段名容错」，name 不是被测字段时补一个占位，
        // 否则行会因 name 为空被跳过。
        val r = if (row.contains("\"name\"") || row.contains("\"vodName\"") ||
            row.contains("\"title\"")
        ) {
            row
        } else {
            row.dropLast(1) + ",\"name\":\"A\"}"
        }
        return """{"data":{"list":[$r]}}"""
    }

    private fun parseOne(row: String) = ShanzhaParser.parseSearch(src, bodyOf(row))

    private fun idOf(row: String) = parseOne(row).items.single().vod_id

    private fun nameOf(row: String) = parseOne(row).items.single().vod_name

    private fun picOf(row: String) = parseOne(row).items.single().vod_pic

    private fun remarksOf(row: String) = parseOne(row).items.single().vod_remarks

    private fun count(body: String) = ShanzhaParser.parseSearch(src, body).items.size

    private fun totalOf(body: String) = ShanzhaParser.parseSearch(src, body).total
}
