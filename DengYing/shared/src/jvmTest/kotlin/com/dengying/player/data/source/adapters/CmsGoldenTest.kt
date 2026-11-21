package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsClass
import com.dengying.player.core.CmsSources
import com.dengying.player.core.RequestOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 金样本基准：苹果CMS 协议解析层（`CmsParser`）
 *
 * 期望值取自**迁移前后的真实输出**（批 C 用 `CmsParserOrgJson` 逐字段比对通过后打印），
 * 不是手抄原代码。两重守卫的分工：
 *
 * - 本文件负责「以后有人改坏」（字面值钉死）；
 * - 批 C 的一致性测试（`app/src/test/.../CmsParityTest.kt`）负责「迁移当时没改坏」，
 *   它比对新实现与 org.json 原实现在同一批输入上的逐字段输出，通过后删除。
 */
class CmsGoldenTest {

    /** 金样本就是从这个源录的，直接用生产配置避免测试自建漂移 */
    private val cms = CmsSources.byKey("jyzy")!!
    private val api2233 = CmsSources.byKey("ah_ap2233")!!

    // ---------------- 列表 ----------------

    @Test
    fun list_golden_header_fields() {
        val r = CmsParser.fill(Golden.text("cms_list"), cms, 1)

        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(20, r.items.size)
        assertEquals(1, r.page)
        assertEquals(5820, r.pageCount)
        assertEquals(116382, r.total)
        assertEquals("金鹰资源", r.sourceName)
        assertNull(r.err)
    }

    @Test
    fun list_golden_ids_in_order() {
        val ids = CmsParser.fill(Golden.text("cms_list"), cms, 1).items.map { it.vod_id }

        // 顺序敏感：这条同时验证「遍历不跳行、不打乱」
        assertEquals(
            listOf(
                161487L, 162127L, 117252L, 162126L, 162125L, 162124L, 162123L,
                162122L, 162121L, 162120L, 162119L, 162118L, 162117L, 162116L,
                162115L, 162114L, 162113L, 162112L, 162111L, 162110L,
            ),
            ids,
        )
    }

    @Test
    fun list_golden_first_item_field_by_field() {
        val first = CmsParser.fill(Golden.text("cms_list"), cms, 1).items[0]

        assertEquals(161487L, first.vod_id)
        assertEquals("修学旅行时被分进了合不来的组第二季", first.vod_name)
        assertEquals("日剧", first.type_name)
        assertEquals("第2集", first.vod_remarks)
        assertEquals("jinyingyun,jinyingm3u8", first.vod_play_from)
        assertEquals("jyzy", first._source)
        // CmsParser 的约定与 api2233 变体相反：**所有字符串字段缺失都给空串**，
        // 因为 parseItem 显式传了 fallback=""；而 api2233 只填 5 个字段、其余留 null。
        assertEquals("", first.vod_pic, "样本未下发 vod_pic → 空串")
        assertEquals("", first.vod_year)
        assertEquals("", first.vod_content)
        assertEquals("", first.vod_uri)
    }

    @Test
    fun list_golden_remarks_are_free_text() {
        val items = CmsParser.fill(Golden.text("cms_list"), cms, 1).items

        // 集数/正片/全集完结/更新至N集混在一起：解析层不得按枚举过滤
        assertEquals("第2集", items[0].vod_remarks)
        assertEquals("正片", items[1].vod_remarks)
        assertEquals("全集完结", items[13].vod_remarks)
        assertEquals("第261024期", items[2].vod_remarks)
    }

    @Test
    fun list_golden_type_names_cover_many_buckets() {
        val names = CmsParser.fill(Golden.text("cms_list"), cms, 1).items.map { it.type_name }.toSet()

        // 样本里至少含这几类，说明 type_name 是逐条透传而非按分类参数回填
        assertTrue(names.containsAll(listOf("日剧", "足球", "篮球", "日韩综艺", "现代都市")), "实际=$names")
    }

    // ---------------- 空与失败（契约守恒） ----------------

    @Test
    fun list_empty_is_success_no_data() {
        val r = CmsParser.fill("""{"list":[],"total":0}""", cms, 1)

        assertEquals(RequestOutcome.SuccessNoData, r.outcome, "空列表是源健康而非失败")
        assertTrue(r.items.isEmpty())
        assertNull(r.err)
    }

    @Test
    fun list_non_json_is_parse_failed() {
        val r = CmsParser.fill("<html>502 Bad Gateway</html>", cms, 2)

        assertEquals(RequestOutcome.ParseFailed, r.outcome)
        assertEquals("响应非 JSON", r.err)
        assertEquals(2, r.page, "失败也回带入参 page")
    }

    @Test
    fun list_missing_fields_use_defaults() {
        val r = CmsParser.fill("""{"list":[{"vod_id":7}]}""", cms, 5)

        assertEquals(1, r.items.size)
        assertEquals(7L, r.items[0].vod_id)
        assertEquals("", r.items[0].vod_name, "字符串字段缺失给空串，不是 null")
        assertEquals(5, r.page, "响应无 page → 用入参")
        assertEquals(1, r.pageCount, "响应无 pagecount → 1")
        assertEquals(1, r.total, "响应无 total → 条目数")
    }

    @Test
    fun list_accepts_string_typed_numbers() {
        // 苹果CMS 各站对 page/pagecount/total 是数字还是数字字符串并不统一，
        // org.json 的 optInt 两者都能取，故 JsonX 也必须能。
        val r = CmsParser.fill("""{"page":"3","pagecount":"9","total":"100","list":[{"vod_id":"7"}]}""", cms, 1)

        assertEquals(3, r.page)
        assertEquals(9, r.pageCount)
        assertEquals(100, r.total)
        assertEquals(7L, r.items[0].vod_id)
    }

    @Test
    fun list_skips_non_object_rows() {
        val r = CmsParser.fill("""{"list":["字符串",null,{"vod_id":5}]}""", cms, 1)

        assertEquals(1, r.items.size)
        assertEquals(5L, r.items[0].vod_id)
    }

    // ---------------- 详情 ----------------

    @Test
    fun detail_golden_empty_list_returns_null() {
        // 这个样本是 ids=1 的真实返回：list 为空说明该 id 不存在
        val body = Golden.text("cms_detail")

        assertNull(CmsParser.detail(body, cms, 1L))
        // 同一份文本走 fill 则是成功无数据——两者语义不同，都对
        assertEquals(RequestOutcome.SuccessNoData, CmsParser.fill(body, cms, 1).outcome)
    }

    @Test
    fun detail_takes_id_from_response_not_argument() {
        val body = """{"list":[{"vod_id":9,"vod_name":"单片","vod_play_url":"第01集${'$'}http://a/1.m3u8"}]}"""
        val v = CmsParser.detail(body, cms, 42L)!!

        assertEquals(9L, v.vod_id, "用响应里的 vod_id，不用传参（与另一源相反，这是协议差异）")
        assertEquals("单片", v.vod_name)
        assertEquals("第01集\$http://a/1.m3u8", v.vod_play_url)
    }

    @Test
    fun detail_broken_bodies_return_null() {
        listOf("""{"list":[]}""", """{"list":null}""", """{}""", """{"list":[null]}""", "not json").forEach {
            assertNull(CmsParser.detail(it, cms, 1L), "应返回 null：$it")
        }
    }

    // ---------------- 分类表 ----------------

    @Test
    fun classes_golden_counts() {
        val cls = CmsParser.classes(Golden.text("cms_list"))

        assertEquals(44, cls.size, "该源分类表共 44 项（从同一次 ac=videolist 响应里的 class 字段）")
        assertEquals(0, cls.count { it.type_pid == null }, "该源每项都下发 type_pid")
    }

    @Test
    fun classes_golden_first_six() {
        val cls = CmsParser.classes(Golden.text("cms_list")).take(6)

        assertEquals(
            listOf(
                CmsClass(type_id = 1, type_name = "电视剧", type_pid = 0),
                CmsClass(type_id = 2, type_name = "电影", type_pid = 0),
                CmsClass(type_id = 3, type_name = "欧美剧", type_pid = 1),
                CmsClass(type_id = 4, type_name = "香港剧", type_pid = 1),
                CmsClass(type_id = 5, type_name = "韩剧", type_pid = 1),
                CmsClass(type_id = 6, type_name = "日剧", type_pid = 1),
            ),
            cls,
        )
    }

    @Test
    fun classes_type_pid_tri_state() {
        // 这三种输入必须在结果上可区分：字段缺失→null、显式 null→null、显式 0→0。
        // 「顶级分类」与「父 id 真的是 0」在部分源里都存在，混成一样会让分类树画错。
        assertNull(CmsParser.classes("""{"class":[{"type_id":1}]}""").single().type_pid, "字段缺失 → null")
        assertNull(
            CmsParser.classes("""{"class":[{"type_id":1,"type_pid":null}]}""").single().type_pid,
            "显式 null → null",
        )
        assertEquals(
            0,
            CmsParser.classes("""{"class":[{"type_id":1,"type_pid":0}]}""").single().type_pid,
            "显式 0 → 0",
        )
    }

    @Test
    fun classes_broken_bodies_return_empty() {
        listOf("""{"class":[]}""", """{"class":null}""", """{}""", "not json", "").forEach {
            assertTrue(CmsParser.classes(it).isEmpty(), "应返回空表：$it")
        }
    }

    // ---------------- api2233 变体 ----------------

    @Test
    fun api2233_golden_single_item() {
        val r = CmsParser.api2233Search(Golden.text("api2233_search"), api2233)

        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(1, r.items.size)
        assertEquals(1, r.total)
        assertEquals(1, r.page)
        assertEquals(1, r.pageCount, "该端点单一来源，恒为 1")
        assertEquals("蓝光4k", r.sourceName)

        val item = r.items.single()
        assertEquals(4020L, item.vod_id)
        assertEquals("庆余年", item.vod_name)
        assertEquals("已完结", item.vod_remarks)
        assertEquals(
            "https://p1.music.126.net/obj/w5vCg8OCwrfCmjrDjcKw/80911053009/9bd7/5f27/538f/1cee5fa9e28fa3c9d0aac5a3bcfcd6c1.jpg",
            item.vod_pic,
            "JSON 里的 \\/ 要还原成 /",
        )
        assertEquals("ah_ap2233", item._source)
        // 该端点只给 5 个字段，其余保持 data class 默认 null——不是漏填
        assertNull(item.vod_year, "样本响应里有 year 字段，但本实现不映射它")
        assertNull(item.vod_content)
        assertNull(item.vod_actor)
    }

    @Test
    fun api2233_reads_nested_info_vod_list() {
        // 非标嵌套：info.vod.list + info.vod.total，缺一层就要退化成空
        val r = CmsParser.api2233Search("""{"info":{"vod":{"total":2,"list":[{"id":1,"name":"a"},{"id":2,"name":"b"}]}}}""", api2233)

        assertEquals(2, r.items.size)
        assertEquals(2, r.total)
        assertEquals(listOf(1L, 2L), r.items.map { it.vod_id })
    }

    @Test
    fun api2233_skips_non_positive_ids() {
        val body = """{"info":{"vod":{"list":[{"id":0,"name":"零"},{"id":-1,"name":"负"},{"id":3,"name":"正"}]}}}"""
        val r = CmsParser.api2233Search(body, api2233)

        assertEquals(1, r.items.size)
        assertEquals(3L, r.items[0].vod_id)
    }

    @Test
    fun api2233_empty_is_success_no_data() {
        // 短剧站对长剧 0 命中属内容池特征，不是故障
        val r = CmsParser.api2233Search("""{"info":{"vod":{"total":0,"list":[]}}}""", api2233)

        assertEquals(RequestOutcome.SuccessNoData, r.outcome)
        assertTrue(r.items.isEmpty())
        assertEquals(0, r.total)
    }

    @Test
    fun api2233_broken_bodies() {
        val parseFailed = CmsParser.api2233Search("not json", api2233)
        assertEquals(RequestOutcome.ParseFailed, parseFailed.outcome)
        assertEquals("响应非 JSON", parseFailed.err)

        // 结构合法但缺 info/info.vod/list：算成功无数据，不算解析失败
        listOf("""{}""", """{"info":{}}""", """{"info":{"vod":{}}}""").forEach {
            assertEquals(RequestOutcome.SuccessNoData, CmsParser.api2233Search(it, api2233).outcome, "输入=$it")
        }
    }
}
