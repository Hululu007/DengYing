package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSource
import com.dengying.player.core.RequestOutcome
import com.dengying.player.core.SourceCaps
import com.dengying.player.core.SourceProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 天翼视讯（tv189）解析层守卫测试 —— P3 批 C-8。
 *
 * 三份金样本是 2026-10-25 从 `https://h5.tv189.com` BFF 实录（真响应，非合成）：
 * `tv189_list.txt`（17904 B）、`tv189_detail.txt`（29026 B）、`tv189_play.txt`（7586 B）。
 *
 * 与其它源的守卫测试同一口径：**先钉样本规模再钉字段**（防止解析器退化成「返回空列表」
 * 还全绿），断言值全部取自实测输出，不是照规则脑补。
 */
class Tv189GoldenTest {

    /** `$` 在 Kotlin 字符串里是模板起始符，紧跟的中文/字母是合法标识符字符 → 用常量拼接 */
    private val D = "$"

    private val source = CmsSource(
        key = "tv189", name = "天翼超高清",
        api = "https://h5.tv189.com",
        speed = 7, direct = false, enabled = true, tier = 2,
        protocol = SourceProtocol.Tv189,
        caps = SourceCaps(search = true, category = true, detail = true, token = true, home = true),
    )

    // ---------- 列表金样本 ----------

    @Test
    fun list_golden_scale() {
        val r = Tv189Parser.parseList(source, Golden.text("tv189_list"), 1)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(20, r.items.size, "每页条数（psize=20）")
        assertEquals(589, r.total, "电影分类免费存量")
        assertEquals(30, r.pageCount, "pageCount = ceil(589/20)")
        assertEquals(1, r.page)
        assertEquals("天翼超高清", r.sourceName)
        assertNull(r.err)
    }

    @Test
    fun list_golden_first_item() {
        val r = Tv189Parser.parseList(source, Golden.text("tv189_list"), 1)
        val it0 = r.items.first()
        assertEquals(47772463L, it0.vod_id, "contentId 剥掉 C 前缀")
        assertEquals("《匠声》", it0.vod_name)
        assertEquals("https://tp.nty.tv189.com/mcover/2026/9/16/C47772463/131072_2026091615B37zd7l0EQ1p7e4180392.jpg", it0.vod_pic)
        assertEquals("免费正片", it0.vod_remarks, "nowSeriesCount=0 → 免费正片")
        assertEquals("2026", it0.vod_year)
        assertEquals("电影", it0.type_name)
        assertEquals("C47772463", it0.vod_uri, "完整 contentId 存 vod_uri")
        assertEquals("tv189", it0._source)
    }

    @Test
    fun list_golden_all_ids_positive_and_uri_matches_id() {
        val r = Tv189Parser.parseList(source, Golden.text("tv189_list"), 1)
        r.items.forEach { item ->
            assertTrue(item.vod_id > 0, "id 必须为正：${item.vod_id}")
            assertEquals("C${item.vod_id}", item.vod_uri, "vod_uri 必须是 C + vod_id")
        }
    }

    @Test
    fun list_golden_names_non_empty() {
        val r = Tv189Parser.parseList(source, Golden.text("tv189_list"), 1)
        r.items.forEach { assertTrue(it0Name(it) != "", "片名不能为空") }
        assertEquals("小玩意（阮玲玉版）", r.items[1].vod_name)
        assertEquals("1933", r.items[1].vod_year, "老片年份照常返回")
    }

    private fun it0Name(item: com.dengying.player.core.VideoItem): String = item.vod_name

    // ---------- 列表破损体与降级 ----------

    @Test
    fun list_malformed_bodies() {
        assertEquals(RequestOutcome.RequestFailed, Tv189Parser.parseList(source, null, 1).outcome)
        assertEquals(RequestOutcome.RequestFailed, Tv189Parser.parseList(source, "", 1).outcome)
        assertEquals(RequestOutcome.RequestFailed, Tv189Parser.parseList(source, "   ", 1).outcome)
        assertEquals(RequestOutcome.RequestFailed, Tv189Parser.parseList(source, "not json", 1).outcome)
        assertEquals(RequestOutcome.RequestFailed, Tv189Parser.parseList(source, "[]", 1).outcome, "顶层必须是对象")
        assertEquals("响应非 JSON", Tv189Parser.parseList(source, "x", 1).err)
    }

    @Test
    fun list_code_non_zero_is_request_failed() {
        val r = Tv189Parser.parseList(source, """{"code":1,"msg":"x"}""", 1)
        assertEquals(RequestOutcome.RequestFailed, r.outcome)
        assertEquals("code 1", r.err)
    }

    @Test
    fun list_code_accepts_numeric_string() {
        val r = Tv189Parser.parseList(source, """{"code":"0","info":{"total":3,"data":[]}}""", 1)
        assertEquals(RequestOutcome.SuccessNoData, r.outcome, "空列表 → SuccessNoData（FR-108）")
        assertEquals(3, r.total)
        assertEquals(1, r.pageCount)
    }

    @Test
    fun list_missing_info_is_request_failed() {
        assertEquals(RequestOutcome.RequestFailed, Tv189Parser.parseList(source, """{"code":0}""", 1).outcome)
        assertEquals("无 info", Tv189Parser.parseList(source, """{"code":0}""", 1).err)
    }

    @Test
    fun list_info_without_data_is_success_no_data() {
        assertEquals(RequestOutcome.SuccessNoData, Tv189Parser.parseList(source, """{"code":0,"info":{}}""", 1).outcome)
        assertEquals(RequestOutcome.SuccessNoData, Tv189Parser.parseList(source, """{"code":0,"info":{"data":null}}""", 1).outcome)
    }

    @Test
    fun list_total_falls_back_to_item_count() {
        val r = Tv189Parser.parseList(
            source, """{"code":0,"info":{"data":[{"contentId":"C1","title":"A"}]}}""", 1,
        )
        assertEquals(1, r.total, "无 total 字段 → items.size")
        assertEquals(1, r.pageCount)
    }

    @Test
    fun list_zero_total_gives_zero_page_count() {
        val r = Tv189Parser.parseList(source, """{"code":0,"info":{"total":0,"data":[]}}""", 1)
        assertEquals(0, r.pageCount, "total<=0 → pageCount=0")
    }

    @Test
    fun list_skips_non_positive_ids() {
        val body = """{"code":0,"info":{"total":9,"data":[
            {"contentId":"C0","title":"zero"},
            {"contentId":"","title":"empty"},
            {"contentId":"abc","title":"nonnum"},
            {"contentId":"C-3","title":"neg"},
            {"contentId":"C7","title":"ok"},
            {"title":"nocid"}
        ]}}"""
        val r = Tv189Parser.parseList(source, body, 1)
        assertEquals(1, r.items.size, "只留 C7")
        assertEquals(7L, r.items[0].vod_id)
        assertEquals("ok", r.items[0].vod_name)
    }

    @Test
    fun list_cover_falls_back_to_imgM8() {
        val body = """{"code":0,"info":{"data":[
            {"contentId":"C1","title":"A","cover":"https://c/1.jpg","imgM8":"https://m8/1.jpg"},
            {"contentId":"C2","title":"B","cover":"","imgM8":"https://m8/2.jpg"},
            {"contentId":"C3","title":"C","imgM8":"https://m8/3.jpg"},
            {"contentId":"C4","title":"D"}
        ]}}"""
        val r = Tv189Parser.parseList(source, body, 1)
        assertEquals("https://c/1.jpg", r.items[0].vod_pic, "cover 优先")
        assertEquals("https://m8/2.jpg", r.items[1].vod_pic, "cover 空 → imgM8")
        assertEquals("https://m8/3.jpg", r.items[2].vod_pic)
        assertEquals("", r.items[3].vod_pic, "两者皆无 → 空串")
    }

    @Test
    fun list_remarks_counts_episodes() {
        val body = """{"code":0,"info":{"data":[
            {"contentId":"C1","title":"A","nowSeriesCount":36},
            {"contentId":"C2","title":"B","nowSeriesCount":0},
            {"contentId":"C3","title":"C"}
        ]}}"""
        val r = Tv189Parser.parseList(source, body, 1)
        assertEquals("共36集", r.items[0].vod_remarks)
        assertEquals("免费正片", r.items[1].vod_remarks)
        assertEquals("免费正片", r.items[2].vod_remarks)
    }

    // ---------- 详情金样本 ----------

    @Test
    fun detail_golden_fields() {
        val item = Tv189Parser.parseDetail(source, Golden.text("tv189_detail"), 47772463L, "C47772463")
        assertNotNull(item)
        assertEquals(47772463L, item.vod_id)
        assertEquals("《匠声》", item.vod_name)
        assertEquals("https://tp.nty.tv189.com/mcover/2026/9/16/C47772463/131072_2026091615B37zd7l0EQ1p7e4180392.jpg", item.vod_pic)
        assertEquals("无", item.vod_actor)
        assertEquals("无", item.vod_director)
        assertEquals("2026", item.vod_year)
        assertEquals("《匠声》——技能改变世界，技能照亮前程", item.vod_content)
        assertEquals("电影", item.type_name)
        assertEquals("C47772463", item.vod_uri)
        assertEquals("tv189", item._source)
    }

    @Test
    fun detail_golden_single_episode_falls_back_to_self() {
        val item = Tv189Parser.parseDetail(source, Golden.text("tv189_detail"), 47772463L, "C47772463")
        assertNotNull(item)
        assertEquals("天翼免费", item.vod_play_from)
        assertEquals("正片" + D + "C47772463", item.vod_play_url, "programList 为空 → 正片即自身")
    }

    @Test
    fun detail_vodUri_empty_uses_c_plus_id() {
        val item = Tv189Parser.parseDetail(source, Golden.text("tv189_detail"), 47772463L, "")
        assertNotNull(item)
        assertEquals("C47772463", item.vod_uri)
        assertEquals("正片" + D + "C47772463", item.vod_play_url)
    }

    @Test
    fun detail_malformed_returns_null() {
        assertNull(Tv189Parser.parseDetail(source, null, 1L, "C1"))
        assertNull(Tv189Parser.parseDetail(source, "", 1L, "C1"))
        assertNull(Tv189Parser.parseDetail(source, "not json", 1L, "C1"))
        assertNull(Tv189Parser.parseDetail(source, "[]", 1L, "C1"))
        assertNull(Tv189Parser.parseDetail(source, """{"code":0}""", 1L, "C1"), "缺 info")
        assertNull(Tv189Parser.parseDetail(source, """{"code":1,"info":{}}""", 1L, "C1"), "code 非 0")
    }

    @Test
    fun detail_contInfo_missing_still_returns_item() {
        val item = Tv189Parser.parseDetail(source, """{"code":0,"info":{}}""", 5L, "C5")
        assertNotNull(item, "contInfo 缺失不导致 null")
        assertEquals("", item.vod_name)
        assertEquals("", item.vod_pic)
        assertNull(item.vod_actor, "cast 缺失 → null（optString 后 trim 仍为 null 分支）")
        assertEquals("天翼免费", item.vod_play_from)
        assertEquals("正片" + D + "C5", item.vod_play_url)
    }

    @Test
    fun detail_episode_order_preserved_when_episode_no_is_zero() {
        val body = """{"code":0,"info":{"contInfo":{"title":"T"},"programList":{"total":3,"data":[
            {"contentId":"C103","title":"第3集"},
            {"contentId":"C101","title":"第1集"},
            {"contentId":"C102","title":"第2集"}
        ]}}}"""
        val item = Tv189Parser.parseDetail(source, body, 1L, "C1")
        assertNotNull(item)
        assertEquals(
            "第3集" + D + "C103#第1集" + D + "C101#第2集" + D + "C102",
            item.vod_play_url,
            "episodeNo 只认结尾数字，第N集 全部得 0 → 稳定排序保持输入原序（上游既有缺陷，见 §13.10.7）",
        )
    }

    @Test
    fun detail_episode_without_number_keeps_original_order() {
        val body = """{"code":0,"info":{"contInfo":{"title":"T"},"programList":{"data":[
            {"contentId":"C1","title":"上"},
            {"contentId":"C2","title":"下"}
        ]}}}"""
        val item = Tv189Parser.parseDetail(source, body, 1L, "C1")
        assertNotNull(item)
        assertEquals("上" + D + "C1#下" + D + "C2", item.vod_play_url, "无数字键值 0，稳定排序保原序")
    }

    @Test
    fun detail_episode_title_empty_uses_index() {
        val body = """{"code":0,"info":{"contInfo":{"title":"T"},"programList":{"data":[
            {"contentId":"C11","title":""},
            {"contentId":"C12"}
        ]}}}"""
        val item = Tv189Parser.parseDetail(source, body, 1L, "C1")
        assertNotNull(item)
        assertEquals("第1集" + D + "C11#第2集" + D + "C12", item.vod_play_url)
    }

    @Test
    fun detail_skips_non_positive_episode_ids() {
        val body = """{"code":0,"info":{"contInfo":{"title":"T"},"programList":{"data":[
            {"contentId":"C0","title":"第1集"},
            {"contentId":"","title":"第2集"},
            {"contentId":"C9","title":"第9集"}
        ]}}}"""
        val item = Tv189Parser.parseDetail(source, body, 1L, "C1")
        assertNotNull(item)
        assertEquals("第9集" + D + "C9", item.vod_play_url, "全部跳过则只剩 C9")
    }

    @Test
    fun detail_all_episodes_skipped_falls_back_to_self() {
        val body = """{"code":0,"info":{"contInfo":{"title":"T"},"programList":{"data":[
            {"contentId":"C0","title":"第1集"}
        ]}}}"""
        val item = Tv189Parser.parseDetail(source, body, 1L, "C1")
        assertNotNull(item)
        assertEquals("正片" + D + "C1", item.vod_play_url)
    }

    // ---------- 换链金样本 ----------

    @Test
    fun play_golden_first_video() {
        val r = Tv189Parser.parsePlay(Golden.text("tv189_play"))
        assertTrue(r.url.startsWith("https://vod3.nty.tv189.cn/6/ol/st06/2026/09/16/Q600_bb209a4d-"), r.url)
        assertTrue(r.url.contains(".mp4.m3u8?sign=24f64b5afd03817119b42b0144c9bb55"), r.url)
        assertEquals("", r.headers, "tv189 不需要自定义请求头")
    }

    @Test
    fun play_golden_preview_marks_previewOnly() {
        // 2026-11-08 真实抓样：免登录试看响应（code=917）—— videos 在响应顶层
        // 而非 info.videoInfo（旧实现解析不到这个形态，试看链被整条丢掉）。
        val r = Tv189Parser.parsePlay(Golden.text("tv189_play_preview"))
        assertTrue(r.url.startsWith("https://vod3.nty.tv189.cn/"), r.url)
        assertTrue(r.url.contains("end=300"), "试看链带 end=300 截断参数")
        assertTrue(r.previewOnly, "fullPlayable=false 必须标 previewOnly")
        assertEquals("", r.headers)
    }

    @Test
    fun play_full_stream_not_marked_preview() {
        // code=0 全片流没有 fullPlayable/trySeeLength 字段，不得误标
        assertFalse(Tv189Parser.parsePlay(Golden.text("tv189_play")).previewOnly)
    }

    @Test
    fun play_preview_synthetic_top_level_videos() {
        val body = """{"code":917,"msg":"用户未登录","trySeeLength":300,"fullPlayable":false,"videos":[{"playType":"P300","playUrl":"https://x.example/a.m3u8?end=300"}]}"""
        val r = Tv189Parser.parsePlay(body)
        assertEquals("https://x.example/a.m3u8?end=300", r.url)
        assertTrue(r.previewOnly)
    }

    @Test
    fun play_malformed_returns_empty() {
        assertEquals("", Tv189Parser.parsePlay(null).url)
        assertEquals("", Tv189Parser.parsePlay("").url)
        assertEquals("", Tv189Parser.parsePlay("not json").url)
        assertEquals("", Tv189Parser.parsePlay("[]").url)
        assertEquals("", Tv189Parser.parsePlay("""{"code":0}""").url, "缺 info")
        assertEquals("", Tv189Parser.parsePlay("""{"code":0,"info":{}}""").url, "缺 videoInfo")
        assertEquals("", Tv189Parser.parsePlay("""{"code":0,"info":{"videoInfo":{}}}""").url, "缺 videos")
        assertEquals("", Tv189Parser.parsePlay("""{"code":0,"info":{"videoInfo":{"videos":[]}}}""").url)
    }

    @Test
    fun play_picks_first_http_url_skipping_invalid() {
        val body = """{"code":0,"info":{"videoInfo":{"videos":[
            {"playUrl":""},
            {"playUrl":"rtmp://x/1"},
            {"qualityName":"超清","playUrl":"https://a/1.m3u8"},
            {"playUrl":"https://a/2.m3u8"}
        ]}}}"""
        val r = Tv189Parser.parsePlay(body)
        assertEquals("https://a/1.m3u8", r.url, "跳过空与非 http，取第一个 http")
    }

    @Test
    fun play_ignores_code_non_zero() {
        // 917 = 试看，也可能带 playUrl —— 迁移前的实现不检查 code，这里必须原样保留
        val body = """{"code":917,"msg":"试看","info":{"videoInfo":{"videos":[
            {"playUrl":"https://a/try.m3u8?end=300"}
        ]}}}"""
        assertEquals("https://a/try.m3u8?end=300", Tv189Parser.parsePlay(body).url, "试看照常返回")
    }

    // ---------- 签名（无密钥纯函数） ----------

    @Test
    fun sign_raw_shape() {
        assertEquals("&cltts=20261024123011&", Tv189Parser.signRaw(emptyMap(), "20261024123011"))
        assertEquals("a=1&b=2&cltts=20261024123011&", Tv189Parser.signRaw(mapOf("b" to "2", "a" to "1"), "20261024123011"), "key 升序")
    }

    @Test
    fun sign_raw_sorts_by_key_not_insertion() {
        val p = linkedMapOf("cltrid" to "u-1", "categoryId" to "2", "productId" to "1000000218", "pno" to "1", "psize" to "20")
        assertEquals(
            "categoryId=2&cltrid=u-1&pno=1&productId=1000000218&psize=20&cltts=TS&",
            Tv189Parser.signRaw(p, "TS").let { it },
            "预置期望（下面用真实值覆盖）",
        )
    }

    @Test
    fun sign_raw_known_vectors() {
        assertEquals("50eed1bd1948d9a609471954d5348657", Tv189Parser.cltKey(emptyMap(), "20261024123011"))
        assertEquals("8ab85c27991b03cd48631252fedfd91f", Tv189Parser.cltKey(mapOf("b" to "2", "a" to "1"), "20261024123011"))
    }

    @Test
    fun sign_raw_values_not_encoded() {
        val raw = Tv189Parser.signRaw(mapOf("a" to "有中文", "B" to "x y"), "TS")
        assertEquals("B=x y&a=有中文&cltts=TS&", raw, "值不编码（大写 B 排在小写 a 前）")
    }

    // ---------- 工具函数 ----------

    @Test
    fun cid_to_long_cases() {
        assertEquals(47919262L, Tv189Parser.cidToLong("C47919262"))
        assertEquals(47919262L, Tv189Parser.cidToLong("47919262"), "无 C 前缀也认")
        assertEquals(0L, Tv189Parser.cidToLong(""))
        assertEquals(0L, Tv189Parser.cidToLong("C"))
        assertEquals(0L, Tv189Parser.cidToLong("Cabc"))
        assertEquals(-1L, Tv189Parser.cidToLong("C-1"), "负数能解析 → 原样返回 -1")
        assertEquals(0L, Tv189Parser.cidToLong("C 7"))
        assertEquals(0L, Tv189Parser.cidToLong("C9223372036854775808"), "溢出 → 0")
    }

    @Test
    fun episode_no_cases() {
        // 正则 (\d+)\s*$ 要求数字在字符串末尾；「第1集」末尾是「集」→ 0
        // 这是迁移前就有的行为（上游既有缺陷），此处按实际值钉住
        assertEquals(0, Tv189Parser.episodeNo("第1集"))
        assertEquals(0, Tv189Parser.episodeNo("第12集"))
        assertEquals(0, Tv189Parser.episodeNo("第 7 集"))
        assertEquals(0, Tv189Parser.episodeNo("上"))
        assertEquals(0, Tv189Parser.episodeNo(""))
        assertEquals(0, Tv189Parser.episodeNo("正片"))
        assertEquals(99, Tv189Parser.episodeNo("EP99"))
        assertEquals(4, Tv189Parser.episodeNo("第3集 额外4"), "取尾部最后一段数字")
        assertEquals(0, Tv189Parser.episodeNo("第0集"), "结尾是「集」→ 0")
        assertEquals(12, Tv189Parser.episodeNo("12"), "纯数字名能匹配")
        assertEquals(12, Tv189Parser.episodeNo("12 "), "尾部空白被正则吃掉")
    }

    @Test
    fun classes_table_is_frozen() {
        val c = Tv189Parser.classes()
        assertEquals(6, c.size)
        assertEquals(listOf(2, 3, 11, 6, 5214, 229), c.map { it.type_id })
        assertEquals(listOf("电影", "电视剧", "综艺", "动漫", "短剧", "记录片"), c.map { it.type_name })
    }

    @Test
    fun free_product_id_frozen() {
        assertEquals("1000000218", Tv189Parser.FREE_PRODUCT_ID, "只出全片免费内容，改了就全是 300 秒试看")
    }

    // ---------- 搜索金样本（2026-11-08 keywordSearchCtyun 实录） ----------

    @Test
    fun search_golden_scale() {
        val r = Tv189Parser.parseSearch(source, Golden.text("tv189_search"), 1)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(1, r.items.size, "「空枪」自营库（NEW_CPMS）命中 1 部")
        assertEquals(1, r.total)
        assertEquals(1, r.pageCount)
        assertNull(r.err)
    }
    @Test
    fun search_golden_first_item() {
        val r = Tv189Parser.parseSearch(source, Golden.text("tv189_search"), 1)
        val it0 = r.items.first()
        assertEquals(47919262L, it0.vod_id, "contentId 剥掉 C 前缀")
        assertEquals("空枪", it0.vod_name)
        assertEquals("https://tp.nty.tv189.com/mcover/2026/10/22/C47919262/131072_2026102218A2cS48W7Z97DY84480311.jpg", it0.vod_pic)
        assertEquals("免费正片", it0.vod_remarks, "nowSerialCount=0 → 免费正片")
        assertEquals("2026", it0.vod_year)
        assertEquals("电影", it0.type_name)
        assertEquals("C47919262", it0.vod_uri)
        assertEquals("tv189", it0._source)
        assertTrue(it0.vod_actor.orEmpty().contains("朱一龙"))
        assertEquals("韩延", it0.vod_director)
    }
    @Test
    fun search_filters_out_non_cpms() {
        // 请求固定带 comeFrom=NEW_CPMS；解析层再滤一道——全库模式下 AQY（爱奇艺
        // 外采）条目 authPlayVod 返回 6201 不可播，不能混进结果
        val body = """{"code":0,"info":{"total":2,"data":[
            {"ct":"1","v":{"contentId":"C47919262","title":"空枪","comeFrom":"NEW_CPMS"}},
            {"ct":"1","v":{"contentId":"C123","title":"爱奇艺外采","comeFrom":"AQY"}}
        ]}}"""
        val r = Tv189Parser.parseSearch(source, body, 1)
        assertEquals(1, r.items.size)
        assertEquals("空枪", r.items.first().vod_name)
    }

    // ---------- 首页版块金样本（2026-11-08 jsondata 实录） ----------

    @Test
    fun nav_golden_tabs() {
        val tabs = Tv189Parser.parseNavTabs(Golden.text("tv189_nav"))
        assertEquals(8, tabs.size, "9 个 tab 滤掉外链「少儿教育」（areaType=130）")
        assertEquals("5G专区", tabs.first().name)
        assertEquals("推荐", tabs[1].name)
        assertEquals("/clt4/xtysxkhd/h5mh/tjysy/index.json", tabs[1].path, "推荐 tab 的版块数据 path")
        assertTrue(tabs.none { it.path.startsWith("http") }, "外链 tab 不得出现")
    }
    @Test
    fun home_golden_sections_scale() {
        val sections = Tv189Parser.parseSections(source, Golden.text("tv189_home"))
        assertEquals(5, sections.size, "12 模块 → 5 个有效版块（其余为空模块/children 嵌套/广告位）")
        assertEquals(
            listOf("今日焦点", "首播影院", "少儿益智", "热血动漫", "精品纪实"),
            sections.map { it.title },
            "标题取 label.name（对象形态），无 label 用 areaName；「推荐页KV图」是运营行话 → 别名映射为「今日焦点」",
        )
        assertEquals(213, sections.sumOf { it.items.size })
    }
    @Test
    fun home_golden_section_details() {
        val sections = Tv189Parser.parseSections(source, Golden.text("tv189_home"))
        val kv = sections.first()
        assertEquals("今日焦点", kv.title, "运营内部名「推荐页KV图」→ 别名映射")
        assertEquals("八仙！", kv.items.first().vod_name)
        val cinema = sections.first { it.title == "首播影院" }
        assertEquals(151, cinema.items.size)
        val it0 = cinema.items.first()
        assertEquals(47982123L, it0.vod_id)
        assertEquals("我想留在你身边", it0.vod_name)
        assertEquals("C47982123", it0.vod_uri)
        assertEquals("tv189", it0._source)
        // 广告位里 clickType=0 但 title 为空的条目不收（金样本实录）
        assertTrue(sections.none { it.title == "推荐页广告位" })
    }
}
