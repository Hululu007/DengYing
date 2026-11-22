package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSources
import com.dengying.player.core.RequestOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 金样本基准：网站型源（HTML 正则抓取）解析层
 *
 * 期望值取自**迁移完成后实际输出**（批 C-2 的一次性 dump 打印），不是手抄原代码。
 * 「迁移当时没改坏」由 `app/src/test/.../WebParityTest.kt` 与 `WebAdapterOrgJson.kt`
 * 逐字段比对证明（22 例全绿后删除），本文件负责「以后有人改坏」。
 *
 * ## 为什么金样本断言是「0 条」而不是具体内容
 *
 * 奇优站点在金样本录制后改了版：`web_search_qiyou.txt` 里
 * `class="v-thumb` 出现 **0** 次、`data-original` 也不在条目锚点上（改用
 * `stui-vodlist__thumb` + `style="background: url(...)"`），因此
 * `CmsSources` 里的 `QIYOU_RULE`（锚 `class="v-thumb"` + `data-original`）
 * 对这份样本**匹配不到任何条目**，解析产出 `SuccessNoData`。
 *
 * 这是**迁移前就有的真实状态**，不是 org.json → kotlinx 引入的差异
 * （parity 测试已证明两个实现在同一输入上逐字段一致，包括这份产 0 条的样本）。
 * 之所以仍然留着这条断言，是因为它把「规则与站点已脱节」这件事固定在测试里：
 * 一旦有人重新抓样本或修规则，这条会立刻提示需要同步复查。
 *
 * 真正验证解析逻辑的断言用**按规则形状手工构造**的合成 HTML（见下方各 `synthetic_*`），
 * 这样规则对了逻辑就对了，不被上游改版掩盖。
 */
class WebGoldenTest {

    /**
     * `$` 在 Kotlin 字符串里是模板起始符，而紧跟其后的中文是**合法标识符字符**，
     * 直接写 `"第1集$https://..."` 或 `"线路一$$$线路二"` 会被当成模板引用
     * （编译报 `Unresolved reference '线路二'`）。故期望值里统一用 [D] 拼接。
     */
    private val D = "$"

    private val qiyou = CmsSources.byKey("ah_qiyou")!!
    private val bttwo = CmsSources.byKey("ah_bttwo")!!
    private val nongmin = CmsSources.byKey("ah_nongmin")!!
    private val subaibai = CmsSources.byKey("ah_subaibai")!!

    // ---------------- 金样本：规则与站点已脱节 ----------------

    @Test
    fun golden_qiyou_yields_no_items_because_rule_is_stale() {
        val r = WebParser.parseSearchHtml(qiyou, Golden.text("web_search_qiyou"), qiyou.rule!!)

        assertEquals(RequestOutcome.SuccessNoData, r.outcome, "抓不到条目是 SuccessNoData，不是失败（FR-108）")
        assertEquals(0, r.items.size)
        assertEquals(1, r.page)
        assertEquals(1, r.pageCount)
        assertEquals(0, r.total)
        assertNull(r.err)
    }

    @Test
    fun golden_qiyou_sample_no_longer_contains_rule_anchor() {
        // 诊断断言：把「规则锚点已消失」写成可执行的事实，避免后人误以为解析器坏了。
        val html = Golden.text("web_search_qiyou")

        assertTrue(!html.contains("class=\"v-thumb"), "样本里已无 QIYOU_RULE 的 class 锚点")
        assertTrue(html.contains("stui-vodlist__thumb"), "样本实际用的是 stui 模板")
        assertTrue(!html.contains("const Url"), "播放器常量在搜索页里本来也没有，这里顺带钉住")
    }

    // ---------------- 金样本：2026-10-31 奇优换链闭环（/player/api.php 两步） ----------------
    // 样本 tools/dy_capture_1031.py 实抓（浪浪山小妖怪 /view/38519）。
    // 10-27 把换链判死是误测：抓到的 139700 B「MP4」是 artplayer.js 伪装文件，不是换链端点。

    @Test
    fun golden_qiyou_search2_yields_three_items() {
        val r = WebParser.parseSearchHtml(qiyou, Golden.text("qiyou_search2"), qiyou.rule!!)

        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(3, r.items.size)
        val first = r.items.first()
        assertEquals(13691L, first.vod_id)
        assertEquals("庆余年第一季", first.vod_name)
        assertEquals("已完结", first.vod_remarks)
        assertEquals("https://www.wdbay.com/view/13691.html", first.vod_uri)
    }

    @Test
    fun golden_qiyou_detail2_panels_group_lines() {
        val v = WebParser.parseDetailHtml(qiyou, Golden.text("qiyou_detail2"), qiyou.rule!!, 38519L)!!

        assertEquals("播放源1" + D + D + D + "播放源2" + D + D + D + "播放源3", v.vod_play_from)
        val lines = v.vod_play_url!!.split(D + D + D)
        assertEquals(3, lines.size)
        assertEquals("HD国语" + D + "https://www.wdbay.com/play/38519-0-0.html", lines[0])
        assertEquals("HD国语" + D + "https://www.wdbay.com/play/38519-1-0.html", lines[1])
        assertEquals("HD中字" + D + "https://www.wdbay.com/play/38519-2-0.html", lines[2])
    }

    @Test
    fun golden_qiyou_play_page_builds_player_api_url() {
        val iframe = Golden.text("qiyou_iframe_url2")
        val page = Golden.text("qiyou_playpage2")

        val m = Regex(qiyou.rule!!.play!!.iframePattern).find(page)
        assertEquals(iframe, m?.groupValues?.getOrNull(1), "播放页 iframe 捕获与实抓一致")

        val built = WebParser.playFromPlayerPage(qiyou.rule!!.play!!, iframe, Golden.text("qiyou_playerpage2"))!!
        // const Url/Sign/From 三常量齐备；t=From 实测为 "m3u8"
        assertEquals(
            "https://meizi.yongfan99.com/player/api.php?url=" +
                "Udc6kKlDDXmFMhIcRUqlQKx524rQxEdqZuL6heZKDnrqQuC0PHaICIGV8lylsOO0OOxRmmdEGTQYCQmNus8XIv2O2Q0O0O00O0O0" +
                "&sign=fd66fd9819067e9eb88ca8c5834cedac&t=m3u8",
            built.url,
        )
        assertEquals("{\"Referer\":\"$iframe\"}", built.headers)
    }

    @Test
    fun golden_qiyou_player_api_json_yields_media_url() {
        // 第二步：/player/api.php 返回 JSON（\/ 是标准 JSON 转义），url 字段才是媒体地址
        val r = WebParser.playFromJsonResponse(qiyou.rule!!.play!!, Golden.text("qiyou_player_api2"))!!

        assertEquals("https://vip.dytt-cinema.com/20251121/41158_e3f37a80/index.m3u8", r.url)
        assertEquals("", r.headers)
    }

    @Test
    fun qiyou_play_gap_is_cleared() {
        // 换链已闭环：playGap 必须为空（UI 靠它决定是否显示「不可播」提示）
        assertEquals(null, qiyou.playGap)
    }

    // ---------------- 合成样本：搜索 ----------------

    @Test
    fun synthetic_search_single_item_field_by_field() {
        val item = "<a class=\"v-thumb lazyload\" data-original=\"https://p.example/c.jpg\" href=\"/view/13691.html\"" +
            " title=\"庆余年\"><div><span class=\"pic-text\">第2集</span></div></a>"
        val r = WebParser.parseSearchHtml(qiyou, "<div>$item</div>", qiyou.rule!!)

        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(1, r.items.size)
        assertEquals(1, r.total, "网站型源没有分页，total = 条目数")
        assertEquals(1, r.page)
        assertEquals(1, r.pageCount)
        assertEquals("奇优", r.sourceName)

        val v = r.items[0]
        assertEquals(13691L, v.vod_id)
        assertEquals("庆余年", v.vod_name)
        assertEquals("https://p.example/c.jpg", v.vod_pic)
        assertEquals("第2集", v.vod_remarks)
        assertEquals("https://www.wdbay.com/view/13691.html", v.vod_uri)
        assertEquals("ah_qiyou", v._source)
        // 搜索条目只填这 6 个字段，其余走 VideoItem 默认的 null（与 CmsParser 给空串的
        // 约定不同，两种形状在原实现里就并存）
        assertNull(v.vod_play_from)
        assertNull(v.vod_year)
    }

    @Test
    fun synthetic_search_link_forms() {
        fun mk(pic: String, href: String, name: String) =
            "<a class=\"v-thumb lazyload\" data-original=\"$pic\" href=\"$href\" title=\"$name\">" +
                "<span class=\"pic-text\">正片</span></a>"

        val html = mk("//cdn.example/p.jpg", "//h.example/view/7.html", "相对协议") +
            mk("/rel/p.jpg", "/view/8.html", "根相对") +
            mk("https://abs/p.jpg", "https://abs/view/9.html", "绝对") +
            mk("", "", "空链接") +
            mk("p.jpg", "view/10.html", "无斜杠")

        val items = WebParser.parseSearchHtml(qiyou, html, qiyou.rule!!).items

        // 「空链接」被丢弃：href 为空 → trailingNumber=0 → vodId 不 > 0
        assertEquals(4, items.size, "空 href 必须被丢弃")
        assertEquals(listOf(7L, 8L, 9L, 10L), items.map { it.vod_id })

        assertEquals("https://cdn.example/p.jpg", items[0].vod_pic, "// 补 https:")
        assertEquals("https://h.example/view/7.html", items[0].vod_uri)

        assertEquals("https://www.wdbay.com/rel/p.jpg", items[1].vod_pic, "/ 前缀拼接 base")
        assertEquals("https://www.wdbay.com/view/8.html", items[1].vod_uri)

        assertEquals("https://abs/p.jpg", items[2].vod_pic, "已是绝对地址则原样")
        assertEquals("https://abs/view/9.html", items[2].vod_uri)

        assertEquals("https://www.wdbay.com/p.jpg", items[3].vod_pic, "无斜杠 → base + '/' + href")
        assertEquals("https://www.wdbay.com/view/10.html", items[3].vod_uri)
    }

    @Test
    fun synthetic_search_empty_page_is_success_no_data() {
        for (src in listOf(qiyou, bttwo, nongmin)) {
            val r = WebParser.parseSearchHtml(src, "<html><body>无条目</body></html>", src.rule!!)

            assertEquals(RequestOutcome.SuccessNoData, r.outcome, "${src.key}：空页不是失败")
            assertTrue(r.items.isEmpty())
            assertOutcomeConsistent(r.outcome, r.items.size, "empty/${src.key}")
        }
    }

    @Test
    fun synthetic_search_blank_html_is_success_no_data() {
        val r = WebParser.parseSearchHtml(qiyou, "", qiyou.rule!!)

        assertEquals(RequestOutcome.SuccessNoData, r.outcome)
        assertTrue(r.items.isEmpty())
    }

    // ---------------- 合成样本：详情 ----------------

    @Test
    fun synthetic_detail_panel_groups_episodes_per_line() {
        // 奇优是面板型：锚点 down1/down2 各切一段，段内剧集归属对应线路
        val html = """
            <a href="#down1">线路一</a><a href="#down2">线路二</a>
            <div id="down1">
              <li><a href="/play/1-1-1.html" title="第1集">
              <li><a href="/play/1-1-2.html" title="第2集">
            </div>
            <div id="down2">
              <li><a href="https://other/play/1-2-1.html" title="">
            </div>
        """.trimIndent()

        val v = WebParser.parseDetailHtml(qiyou, html, qiyou.rule!!, 42L)!!

        assertEquals(42L, v.vod_id)
        assertEquals("线路一${D}${D}${D}线路二", v.vod_play_from)
        assertEquals(
            "第1集${D}https://www.wdbay.com/play/1-1-1.html" +
                "#第2集${D}https://www.wdbay.com/play/1-1-2.html" +
                "${D}${D}${D}第1集${D}https://other/play/1-2-1.html",
            v.vod_play_url,
        )
        // 第二条线路的剧集 title 为空 → 序号从该段内重新数起（"第1集"而非"第3集"）
        assertEquals("", v.vod_name, "详情解析不回填名称")
        assertEquals("", v.vod_pic)
        assertEquals("ah_qiyou", v._source)
        assertNull(v.vod_year)
    }

    @Test
    fun synthetic_detail_relative_episode_links_are_absolutized() {
        // 相对路径不被补全的话，下游 parseEpisodes 的 http 校验会把它们丢掉
        val html = "<a href=\"#down1\">A线</a><div id=\"down1\">" +
            "<li><a href=\"1-1-1.html\" title=\"第1集\">" +
            "<li><a href=\"//cdn.example/play/x.html\" title=\"\">" +
            "</div>"

        val v = WebParser.parseDetailHtml(qiyou, html, qiyou.rule!!, 1L)!!

        assertEquals(
            "第1集${D}https://www.wdbay.com/1-1-1.html#第2集${D}https://cdn.example/play/x.html",
            v.vod_play_url,
        )
    }

    @Test
    fun synthetic_detail_missing_anchor_segment_is_skipped() {
        val html = "<a href=\"#down1\">A线</a><a href=\"#down2\">B线</a>" +
            "<div id=\"down2\"><li><a href=\"/play/2.html\" title=\"末集\"></div>"

        val v = WebParser.parseDetailHtml(qiyou, html, qiyou.rule!!, 5L)!!

        // down1 在页面里找不到 id="down1" → 该线路跳过；down2 的标号仍是 2
        // 线路名按锚点下标回填（不按锚点名猜），down2 是第 2 个锚点 → lineNames[1]\        assertEquals("B线", v.vod_play_from)
        assertEquals("末集${D}https://www.wdbay.com/play/2.html", v.vod_play_url)
    }

    @Test
    fun synthetic_detail_without_panel_falls_back_to_line_one() {
        // 素白白是「无面板型」：line.pattern 为空、anchorGroup=0，全页抽取归到线路 1。
        // （农民影视原本也走这条分支，15.3 起改用 seasonListPattern 切容器，不再是无面板样本。）
        val html = "<li><a href=\"/p/1.html\">第一集</a><li><a href=\"/p/2.html\"></a>" +
            "<li><a href=\"//cdn.example/p/3.html\">第三集</a>"

        val v = WebParser.parseDetailHtml(subaibai, html, subaibai.rule!!, 7L)!!

        assertEquals("线路1", v.vod_play_from)
        assertEquals(
            "第一集${D}https://www.subaibai.com/p/1.html" +
                "#第2集${D}https://www.subaibai.com/p/2.html" +
                "#第三集${D}https://cdn.example/p/3.html",
            v.vod_play_url,
        )
        assertEquals("ah_subaibai", v._source)
    }

    @Test
    fun synthetic_detail_bttwo_data_attributes() {
        val b = bttwo
        val html = "<a href=\"/play/9-1-1.html\" data-line=\"1\" data-episode=\"1\">" +
            "<a href=\"/play/9-1-2.html\" data-line=\"1\" data-episode=\"2\">"

        val v = WebParser.parseDetailHtml(b, html, b.rule!!, 9L)!!

        assertEquals("线路1", v.vod_play_from)
        assertEquals(
            "第1集${D}https://www.bttwo.life/play/9-1-1.html#第2集${D}https://www.bttwo.life/play/9-1-2.html",
            v.vod_play_url,
        )
    }

    @Test
    fun synthetic_detail_no_episodes_returns_null() {
        for (src in listOf(qiyou, bttwo, nongmin)) {
            assertNull(
                WebParser.parseDetailHtml(src, "<html>无剧集</html>", src.rule!!, 1L),
                "${src.key}：抓不到剧集必须返回 null（不是空壳对象）",
            )
        }
    }

    // ---------------- 播放器链与请求头 ----------------

    @Test
    fun synthetic_play_headers_are_compact_json() {
        val rule = qiyou.rule!!.play!!

        // 形状必须与 JSONObject().put(...).toString() 一致：紧凑、无空格、插入序
        assertEquals(
            """{"Referer":"https://meizi.example/p?x=1"}""",
            WebParser.playHeaders(rule, "https://meizi.example/p?x=1"),
        )
    }

    @Test
    fun synthetic_sniff_headers_are_compact_json() {
        assertEquals(
            """{"User-Agent":"UA/1.0","Referer":"https://www.wdbay.com/view/1.html"}""",
            WebParser.sniffHeaders("UA/1.0", "https://www.wdbay.com/view/1.html"),
        )
    }

    @Test
    fun synthetic_play_from_player_page_builds_media_url() {
        val rule = qiyou.rule!!.play!!
        val body = "var x=1;const Url = \"abc/def.m3u8\";const Sign = \"SIG\";const From = \"qq\";"

        val r = WebParser.playFromPlayerPage(rule, "https://meizi.example/p?q=1", body)!!

        assertEquals(
            "https://meizi.example/player/api.php?url=abc/def.m3u8&sign=SIG&t=qq",
            r.url,
            "{origin} 取 iframe 的 scheme://host（2026-10-31 换链端点更正为 /player/api.php）",
        )
        assertEquals("""{"Referer":"https://meizi.example/p?q=1"}""", r.headers, "{page} 换成播放器页地址")
    }

    @Test
    fun synthetic_play_from_player_page_without_from_const() {
        val rule = qiyou.rule!!.play!!
        val body = "const Url = \"a.m3u8\";const Sign = \"S\";"

        val r = WebParser.playFromPlayerPage(rule, "https://meizi.example/p", body)!!

        assertEquals(
            "https://meizi.example/player/api.php?url=a.m3u8&sign=S&t=",
            r.url,
            "缺 From 时 {from} 替换成空串，不能整条失败",
        )
    }

    @Test
    fun synthetic_play_from_player_page_missing_sign_returns_null() {
        val rule = qiyou.rule!!.play!!

        assertNull(
            WebParser.playFromPlayerPage(rule, "https://meizi.example/p", "const Url = \"a.m3u8\";"),
            "缺 Sign 必须返回 null，好让调用方落到嗅探兜底",
        )
        assertNull(
            WebParser.playFromPlayerPage(rule, "https://meizi.example/p", "const Sign = \"S\";"),
            "缺 Url 同理",
        )
    }

    // ---------------- 工具函数 ----------------

    @Test
    fun trailing_number_takes_last_digit_run() {
        assertEquals(13691L, WebParser.trailingNumber("/view/13691.html"))
        assertEquals(1L, WebParser.trailingNumber("/voddetail/4570-1-1.html"), "取最后一段数字")
        assertEquals(0L, WebParser.trailingNumber("/abc.html"), "无数字 → 0")
        assertEquals(12L, WebParser.trailingNumber("/view/12"), "无后缀也成立")
        assertEquals(0L, WebParser.trailingNumber(""), "空串 → 0")
        assertEquals(123L, WebParser.trailingNumber("123"))
        assertEquals(8L, WebParser.trailingNumber("/a/9/b/8"))
        assertEquals(7L, WebParser.trailingNumber("/x/007.html"), "前导零按十进制解析")
        // 最后一段数字是查询串里的 1，不是路径里的 999——正则取的是「其后不再有数字」
        assertEquals(1L, WebParser.trailingNumber("https://h/p/999?x=1"))
    }

    @Test
    fun absolutize_handles_all_link_forms() {
        val base = "https://www.wdbay.com"

        assertEquals("", WebParser.absolutize(base, ""))
        assertEquals("http://a/b", WebParser.absolutize(base, "http://a/b"))
        assertEquals("https://a/b", WebParser.absolutize(base, "https://a/b"))
        assertEquals("https://cdn/x", WebParser.absolutize(base, "//cdn/x"))
        assertEquals("https://www.wdbay.com/root/x", WebParser.absolutize(base, "/root/x"))
        assertEquals("https://www.wdbay.com/rel/x", WebParser.absolutize(base, "rel/x"))
    }

    @Test
    fun sniff_media_takes_first_match_only() {
        assertEquals(
            "https://a.b/c/index.m3u8?k=1",
            WebParser.sniffMedia("<x>https://a.b/c/index.m3u8?k=1</x>", listOf("m3u8")),
        )
        assertEquals(
            "https://a.b/c/movie.MP4",
            WebParser.sniffMedia("<x>https://a.b/c/movie.MP4</x>", listOf("mp4")),
            "扩展名大小写不敏感",
        )
        assertEquals("", WebParser.sniffMedia("<x>https://a.b/c/nothing.avi</x>", listOf("m3u8")))
        assertEquals("", WebParser.sniffMedia("", listOf("m3u8")))
        assertEquals(
            "https://a.b/c/x.m3u8",
            WebParser.sniffMedia("https://a.b/c/x.m3u8 https://a.b/c/y.mp4", listOf("m3u8")),
            "多个命中取第一个",
        )
    }

    @Test
    fun origin_of_extracts_scheme_host_port() {
        assertEquals("http://h:8080", WebParser.originOf("http://h:8080/a/b"))
        assertEquals("https://h", WebParser.originOf("https://h/a"))
        assertEquals("", WebParser.originOf("/rel/path"), "相对地址没有 origin")
        assertEquals("", WebParser.originOf(""))
    }
    // ---------------- 2026-10-31 农民影视（nmystv 新域）：容器切分 + 线名对应 ----------------

    @Test
    fun synthetic_nongmin_play_list_containers_become_named_lines() {
        // 夹具按 nmystv 真实模板造：线名标签（data-dropdown-value）先于容器出现、按序对应；
        // 容器内 <a 与 class 之间是两个空格（模板压缩产物），规则用 \s+ 容忍。
        val html =
            "<div class=\"module-tab-item tab-item\" data-dropdown-value=\"农民专线\"><span>农民专线</span><small>45</small></div>" +
                "<div class=\"module-tab-item tab-item\" data-dropdown-value=\"农民副线\"><span>农民副线</span><small>12</small></div>" +
                "<div class=\"module-play-list\"><div class=\"module-play-list-content module-play-list-base\">" +
                "<a  class=\"module-play-list-link\" href=\"/index.php/vod/play/id/11/sid/1/nid/1.html\" title=\"播放第1集\"><span>第1集</span></a>" +
                "<a  class=\"module-play-list-link\" href=\"/index.php/vod/play/id/11/sid/1/nid/2.html\" title=\"播放第2集\"><span>第2集</span></a>" +
                "</div></div>" +
                "<div class=\"module-play-list\"><div class=\"module-play-list-content module-play-list-base\">" +
                "<a  class=\"module-play-list-link\" href=\"/index.php/vod/play/id/11/sid/2/nid/1.html\" title=\"播放花絮\"><span>花絮</span></a>" +
                "</div></div>"

        val v = WebParser.parseDetailHtml(nongmin, html, nongmin.rule!!, 11L)!!

        assertEquals("农民专线" + D + D + D + "农民副线", v.vod_play_from)
        assertEquals(
            "第1集" + D + "https://nmystv.com/index.php/vod/play/id/11/sid/1/nid/1.html" +
                "#第2集" + D + "https://nmystv.com/index.php/vod/play/id/11/sid/1/nid/2.html" +
                D + D + D +
                "花絮" + D + "https://nmystv.com/index.php/vod/play/id/11/sid/2/nid/1.html",
            v.vod_play_url,
        )
        assertEquals("ah_nongmin", v._source)
    }

    @Test
    fun synthetic_nongmin_line_names_fall_back_without_labels() {
        // 标签条缺失时容器仍成线，名称回填「线路N」（宁可无名也不猜线名）
        val html =
            "<div class=\"module-play-list\"><div class=\"module-play-list-content\">" +
                "<a  class=\"module-play-list-link\" href=\"/p/1-1.html\" title=\"播放第1集\"><span>第1集</span></a>" +
                "</div></div>"

        val v = WebParser.parseDetailHtml(nongmin, html, nongmin.rule!!, 12L)!!

        assertEquals("线路1", v.vod_play_from)
        assertEquals("第1集" + D + "https://nmystv.com/p/1-1.html", v.vod_play_url)
    }

    @Test
    fun synthetic_nongmin_without_containers_yields_nothing() {
        // 容器型规则：没有 module-play-list-content 容器时，裸 <a> 不得被当成剧集
        //（否则会把导航/推荐区收进来）
        assertNull(
            WebParser.parseDetailHtml(
                nongmin,
                "<ul><li><a href=\"/p/1.html\">第1集</a></li></ul>",
                nongmin.rule!!,
                13L,
            ),
        )
    }

    @Test
    fun golden_nongmin_search_page_reports_missing_template() {
        val body = Golden.text("nongmin_search")

        assertEquals("缺少文件：/data/img.wwgz.cn/template/pc/html//vod_search.html", body)
        val r = WebParser.parseSearchHtml(nongmin, body, nongmin.rule!!)
        assertEquals(RequestOutcome.SuccessNoData, r.outcome, "模板缺失页不是失败，是空结果")
        assertTrue(r.items.isEmpty())
    }

    @Test
    fun golden_nongmin_root_page_is_404() {
        val body = Golden.text("nongmin_root")

        assertTrue(body.contains("<title>404</title>"), "根路径返回 404 页")
        assertTrue(
            WebParser.parseSearchHtml(nongmin, body, nongmin.rule!!).items.isEmpty(),
            "404 页不应解析出条目",
        )
    }

    @Test
    fun golden_nongmin_type_page_rejects_parameters() {
        val body = Golden.text("nongmin_type")

        assertTrue(
            body.contains("获取数据失败，请勿非法传递参数"),
            "分类页直接拒绝参数",
        )
    }
}
