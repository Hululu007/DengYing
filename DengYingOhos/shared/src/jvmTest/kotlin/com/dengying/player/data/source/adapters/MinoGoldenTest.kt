package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSources
import com.dengying.player.core.RequestOutcome
import com.dengying.player.data.json.JsonX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 金样本基准：米米4k（MINO）解析层
 *
 * 这批断言在 P3 **批 A 时用 org.json 实现跑绿过**（当时的 `MinoGoldenTest` 在
 * `:app` 里，JUnit 4），批 B 把 [MinoParser] 升舱到 `commonMain` 并改用
 * kotlinx-serialization 后，断言一条未改，仍然必须全绿——这就是「迁移无行为差异」
 * 的证据。
 *
 * 断言写法刻意选「看得见的字面值」而不是整快照比对：快照在实现变更时整片翻红，
 * 看不出到底哪一字段漂了；逐字段字面值能直接指出漂移点。
 *
 * 注：JUnit 4 → kotlin.test 的迁移规则是「message 从首位挪到末位」，
 * `assertEquals(message, expected, actual)` → `assertEquals(expected, actual, message)`。
 */
class MinoGoldenTest {

    /** 直接用生产配置里的源对象：样本就是从这个 api 录的，避免测试自建配置漂移 */
    private val source = CmsSources.byKey("ah_mimi4k")!!

    // - 搜索 ----------------

    @Test
    fun search_golden_six_items_field_by_field() {
        val r = MinoParser.search(Golden.text("mino_search"), source)

        assertEquals(RequestOutcome.SuccessWithData, r.outcome, "样本 code=0 且 list 非空，应判有数据")
        assertEquals(6, r.items.size, "样本 data.list 有 6 条")
        assertEquals(6, r.total, "total 取自 data.total")
        assertEquals(1, r.page, "该源搜索无分页参数，恒为 1")
        assertEquals("米米4k", r.sourceName)
        assertNull(r.err, "成功路径不应带 err")
        assertOutcomeConsistent(r.outcome, r.items.size, "mino.search")

        val first = r.items[0]
        assertEquals(7194L, first.vod_id)
        assertEquals("流浪地球2", first.vod_name)
        assertEquals("完结", first.vod_remarks)
        assertEquals("ah_mimi4k", first._source, "_source 由适配层盖章")
        assertTrue(
            first.vod_pic.startsWith("http://i0.hdslb.com/bfs/bangumi/image/"),
            "封面应取自 vod_pic；实际=" + first.vod_pic,
        )

        // 逐条 id 顺序：没有因为跳过某些行而错位
        assertEquals(
            listOf(7194L, 24966L, 7192L, 28714L, 28139L, 8771L),
            r.items.map { it.vod_id },
        )
    }

    @Test
    fun search_golden_keeps_free_text_remarks() {
        val items = MinoParser.search(Golden.text("mino_search"), source).items

        // 7192 的 vod_remarks 是剧情简介而非「完结」，说明该字段是自由文本，
        // 解析层不得按枚举校验，否则会把它当脏数据丢掉。
        val busy = items.first { it.vod_id == 7192L }
        assertEquals("吴京点燃木星拯救地球", busy.vod_remarks)
    }

    @Test
    fun search_nonzero_code_is_request_failed() {
        val r = MinoParser.search("""{"code":1,"message":"维护中"}""", source)

        assertEquals(RequestOutcome.RequestFailed, r.outcome, "code!=0 属请求级失败，非解析失败")
        assertTrue(r.items.isEmpty())
        assertEquals("code 1", r.err, "err 应带上服务端 code 便于排查")
    }

    @Test
    fun search_non_json_is_parse_failed() {
        val r = MinoParser.search("<html>502 Bad Gateway</html>", source)

        assertEquals(RequestOutcome.ParseFailed, r.outcome, "响应不是 JSON 用 ParseFailed 区分于请求失败")
        assertTrue(r.items.isEmpty())
        assertEquals("响应非 JSON", r.err)
    }

    @Test
    fun search_empty_list_is_success_no_data() {
        val r = MinoParser.search("""{"code":0,"data":{"list":[],"total":0}}""", source)

        assertEquals(RequestOutcome.SuccessNoData, r.outcome, "code=0 但无条目 → 源健康、无数据")
        assertTrue(r.items.isEmpty())
        assertEquals(0, r.total)
    }

    @Test
    fun search_missing_data_does_not_throw() {
        val r = MinoParser.search("""{"code":0}""", source)

        assertEquals(RequestOutcome.SuccessNoData, r.outcome)
        assertTrue(r.items.isEmpty())
        assertEquals(0, r.total, "缺 data 时 total 回落为条目数 0")
    }

    @Test
    fun search_falls_back_to_short_field_names() {
        // 该源早期返回苹果 CMS 风格字段、后期改简写，两种都还在线上出现过。
        val body = """{"code":0,"data":{"list":[{"id":42,"title":"短名","pic":"http://p/x.png"}],"total":1}}"""
        val item = MinoParser.search(body, source).items.single()

        assertEquals(42L, item.vod_id, "vod_id 缺失时回退 id")
        assertEquals("短名", item.vod_name, "vod_name 缺失时回退 title")
        assertEquals("http://p/x.png", item.vod_pic, "vod_pic 缺失时回退 pic")
        assertEquals("更新中", item.vod_remarks, "remarks 与 year 都缺失时给默认文案")
    }

    @Test
    fun search_skips_rows_without_usable_id() {
        // id 取不到时整行丢弃：后续详情接口靠 id 寻址，留着只会点不进去。
        val body = """{"code":0,"data":{"list":[{"id":0,"title":"无 id"},{"id":7,"title":"有 id"}],"total":2}}"""
        val r = MinoParser.search(body, source)

        assertEquals(1, r.items.size)
        assertEquals(7L, r.items[0].vod_id)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
    }

    // ---------------- 详情 ----------------

    @Test
    fun detail_nonzero_code_returns_null() {
        assertNull(MinoParser.detail("""{"code":1,"message":"无此影片"}""", 7194L, source))
    }

    @Test
    fun detail_missing_data_returns_null() {
        assertNull(MinoParser.detail("""{"code":0}""", 7194L, source))
    }

    @Test
    fun detail_non_json_returns_null_without_throwing() {
        assertNull(MinoParser.detail("<html>deny</html>", 7194L, source))
    }

    @Test
    fun detail_fallback_chain_and_html_strip() {
        val body = """
            {"code":0,"data":{
              "vod_name":"测试片",
              "vod_pic":"http://p/1.png",
              "vod_year":"2020",
              "vod_director":"某导演",
              "vod_blurb":"<p>简介&nbsp;文本</p>",
              "vod_play_from":"m3u8",
              "vod_play_url":"第01集${'$'}http://a/1.m3u8"
            }}
        """.trimIndent()

        val v = MinoParser.detail(body, 99L, source)!!
        assertEquals(99L, v.vod_id, "id 用入参而非响应字段：该源详情不带 id")
        assertEquals("测试片", v.vod_name)
        assertEquals("2020", v.vod_remarks, "vod_remarks 缺失时回落 vod_year")
        assertEquals("某导演", v.vod_actor, "vod_actor 缺失时回落 vod_director")
        assertEquals("简介 文本", v.vod_content, "简介要去标签并把 &nbsp; 还原成空格")
        assertEquals("第01集\$http://a/1.m3u8", v.vod_play_url, "播放串里的 \$ 不得被转义破坏")
        assertEquals("ah_mimi4k", v._source)
    }

    @Test
    fun detail_remarks_wins_over_year() {
        val body = """{"code":0,"data":{"vod_name":"x","vod_remarks":"更新至12集","vod_year":"2021"}}"""
        assertEquals("更新至12集", MinoParser.detail(body, 1L, source)!!.vod_remarks)
    }

    // ---------------- 换链请求体 ----------------

    @Test
    fun parse_body_vod_id_must_be_bare_number() {
        val body = MinoParser.buildParseBody("tok", "m3u8", 7194L)

        // 这条是接口清单里实测出来的硬约束：传字符串会被服务端拒。
        // 序列化实现换代最容易在这里漂移（把 Long 写成 "7194"），故单独钉一条。
        assertTrue(body.contains("\"vod_id\":7194"), "vod_id 应为裸数字；实际=" + body)
        assertTrue(!body.contains("\"7194\""), "vod_id 不得是字符串；实际=" + body)
    }

    @Test
    fun parse_body_has_exactly_three_typed_fields() {
        val body = MinoParser.buildParseBody("tok", "line1", 7L)
        val o = JsonX.parseObject(body)!!

        // 不断言字节级字符串相等：org.json 的 JSONObject 底层是 HashMap，字段顺序
        // 不稳定；kotlinx 是 LinkedHashMap 保插入序。服务端按 key 取值，顺序无影响。
        // 该差异已记入 docs/04 §13.1.5 行为差异裁决表。
        assertEquals(3, o.size)
        assertEquals("tok", JsonX.str(o, "url"))
        assertEquals("line1", JsonX.str(o, "from"))
        assertEquals(7L, JsonX.long(o, "vod_id"))
        assertTrue(JsonX.obj(o) != null && !JsonX.isNull(o["vod_id"]), "vod_id 必须存在")
    }

    // ---------------- 换链响应 ----------------

    @Test
    fun play_three_level_url_fallback() {
        assertEquals(
            "http://a/1.m3u8",
            MinoParser.parsePlay("""{"code":0,"data":{"url":"http://a/1.m3u8"}}""").url,
        )
        assertEquals(
            "http://a/2.m3u8",
            MinoParser.parsePlay("""{"code":0,"data":{"play_url":"http://a/2.m3u8"}}""").url,
        )
        assertEquals(
            "http://a/3.m3u8",
            MinoParser.parsePlay("""{"code":0,"data":{"playUrl":"http://a/3.m3u8"}}""").url,
        )
    }

    @Test
    fun play_url_is_trimmed() {
        val r = MinoParser.parsePlay("""{"code":0,"data":{"url":"  http://a/x.m3u8 \n"}}""")
        assertEquals("http://a/x.m3u8", r.url)
    }

    @Test
    fun play_failure_paths_return_empty_url() {
        assertEquals("", MinoParser.parsePlay("""{"code":1}""").url)
        assertEquals("", MinoParser.parsePlay("""{"code":0}""").url)
        assertEquals("", MinoParser.parsePlay("not json").url)
    }

    @Test
    fun play_headers_always_empty() {
        // 该源换出的地址不需要自定义请求头；字段存在但为空是与其它源统一的形状。
        val r = MinoParser.parsePlay("""{"code":0,"data":{"url":"http://a/1.m3u8"}}""")
        assertEquals("", r.headers)
    }

    // ---------------- 2026-10-31：游客配额（X-Device-ID）与真 parse 样本 ----------------

    @Test
    fun quota_exceeded_is_40311_only() {
        // 40311「今日免费次数已用完」→ 换设备号重试；40110「请登录后观看」其实是
        // 缺 X-Device-ID 的返回（曾因此误判登录墙），不是配额，不能触发换号
        assertTrue(MinoParser.quotaExceeded("""{"code":40311,"message":"今日免费次数已用完"}"""))
        assertTrue(!MinoParser.quotaExceeded("""{"code":40110,"message":"请登录后观看"}"""))
        assertTrue(!MinoParser.quotaExceeded("""{"code":0}"""))
        assertTrue(!MinoParser.quotaExceeded("not json"))
        assertTrue(!MinoParser.quotaExceeded(""))
    }

    @Test
    fun play_golden_real_parse_response() {
        // 2026-10-31 实抓（tools/dy_capture_1031.py，X-Device-ID 免登录）：
        // /api/parse 出 cibn 系 CDN 的 m3u8，附 guest_remaining 配额余量字段
        val body = Golden.text("mino_parse2")
        val r = MinoParser.parsePlay(body)

        assertTrue(r.url.startsWith("https://cibn-edge-5g.1ljx.com/"), "实际换出 ${r.url.take(60)}")
        assertTrue(r.url.contains(".m3u8"))
        assertEquals("", r.headers)
        // 响应里确有配额余量字段（文档化信封形状，字段增减时这里先红）
        assertTrue(body.contains("guest_remaining"))
    }

    // ---------------- 去标签工具 ----------------

    @Test
    fun strip_html_tags_and_entities() {
        assertEquals("ab", MinoParser.stripHtml("<p>a</p><br/>b"))
        assertEquals("a b", MinoParser.stripHtml("a&nbsp;b"))
        assertEquals("a&b", MinoParser.stripHtml("a&amp;b"))
        assertEquals("", MinoParser.stripHtml("  <br>  "))
    }
}
