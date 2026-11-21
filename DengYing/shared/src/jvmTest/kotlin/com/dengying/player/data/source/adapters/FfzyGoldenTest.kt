package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSources
import com.dengying.player.core.RequestOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 非凡资源2（key 沿用 `ah_ap2233`）解析层守卫 —— 2026-11-21 换数据源后的金样本
 *
 * 样本见 `shared/src/jvmTest/resources/golden/`（`_meta.json` 登记字节数）：
 * `ffzy_catlist` = `ac=list&pg=1`、`ffzy_detail100474` = `ac=detail&ids=100474`、
 * `ffzy_search_qyn` = `ac=detail&wd=庆余年`，均为 api.ffzyapi.com 的真实响应。
 *
 * 本测试钉住换源依据：
 *
 * 1. **标准 CMS 三端点可用**——total=99215 / pagecount=4961 / 31 分类；
 * 2. **播放串是 https 分享页或直连 m3u8，不再是加密 token**——这就是
 *    旧源 `playGap` 被删除、`direct` 改 false 的依据；
 * 3. **搜索走默认 `ac=detail&wd=`**——`searchUrl` 必须为空，不能再打旧
 *    `search/index` 非标端点。
 *
 * 旧的 token 形态回归留在 `Ap2233GoldenTest`（样本是改源前的响应）。
 */
class FfzyGoldenTest {

    private val src = CmsSources.byKey("ah_ap2233")!!

    /** Kotlin 字符串里 `$` 是模板起始符，紧跟中文还是合法标识符——故用常量拼接 */
    private val D = "$"

    // ---------------- 源配置 ----------------

    @Test
    fun source_config_points_at_ffzy_standard_cms() {
        assertEquals("非凡资源2", src.name)
        assertTrue(src.api.contains("api.ffzyapi.com"), "api 指向非凡资源")
        assertNull(src.searchUrl, "标准 CMS 不配非标搜索端点")
        assertNull(src.playGap, "分享页可换 m3u8，无播放链缺口")
        assertTrue(!src.direct, "分享页不是直链")
        assertTrue(src.caps.search && src.caps.detail, "搜索与详情可用")
        assertTrue(src.caps.home, "首页仍由 AhHomeAdapter 合成")
    }

    // ---------------- 分类表（ac=list） ----------------

    @Test
    fun catlist_classes_count_and_top_level() {
        val classes = CmsParser.classes(Golden.text("ffzy_catlist"))
        assertEquals(31, classes.size, "ac=list 的 class 数组共 31 项")
        assertEquals(
            listOf(1, 2, 3, 4),
            classes.take(4).map { it.type_id },
            "前 4 项是顶级分类：电影片 / 连续剧 / 综艺片 / 动漫片",
        )
        assertEquals(
            listOf("电影片", "连续剧", "综艺片", "动漫片"),
            classes.take(4).map { it.type_name },
        )
        assertEquals(
            listOf(0, 0, 0, 0),
            classes.take(4).map { it.type_pid },
            "顶级分类 type_pid 显式为 0",
        )
    }

    /**
     * 首页 rail 用的是叶子分类，不是顶层分类。
     *
     * 顶层 `t=1..4` 在 ffzy 上几乎不挂直属影片（实测 t=1/2/3 返回 total=0，
     * t=4 只有 44 条 2022 年前的旧动漫），直接用会把首页刷成老片墙。
     * AhHomeAdapter 因此改用 6/7、13/14、25/26、29/30 这些叶子 id，
     * 这里钉住它们在分类表里的父级，防止对面改表后静默跑偏。
     */
    @Test
    fun catlist_leaf_ids_used_by_home_rails() {
        val classes = CmsParser.classes(Golden.text("ffzy_catlist"))
        val homeLeaves = listOf(6, 7, 13, 14, 25, 26, 29, 30)
        assertEquals(
            homeLeaves,
            classes.map { it.type_id }.filter { it in homeLeaves },
            "首页用到的 8 个叶子分类都在分类表里",
        )
        val parentOf = classes.associate { it.type_id to (it.type_pid to it.type_name) }
        assertEquals(listOf(1 to "动作片", 1 to "喜剧片"), listOf(parentOf.getValue(6), parentOf.getValue(7)))
        assertEquals(listOf(2 to "国产剧", 2 to "香港剧"), listOf(parentOf.getValue(13), parentOf.getValue(14)))
        assertEquals(listOf(3 to "大陆综艺", 3 to "港台综艺"), listOf(parentOf.getValue(25), parentOf.getValue(26)))
        assertEquals(listOf(4 to "国产动漫", 4 to "日韩动漫"), listOf(parentOf.getValue(29), parentOf.getValue(30)))
    }

    @Test
    fun catlist_header_fields() {
        val r = CmsParser.fill(Golden.text("ffzy_catlist"), src, 1)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(1, r.page)
        assertEquals(4961, r.pageCount)
        assertEquals(99215, r.total)
        assertEquals(20, r.items.size)
        assertNull(r.err)
    }

    // ---------------- 详情（ac=detail&ids=） ----------------

    @Test
    fun detail_fields_and_two_lines() {
        val item = CmsParser.detail(Golden.text("ffzy_detail100474"), src, 100474L)
        assertNotNull(item)
        assertEquals(100474L, item.vod_id)
        assertEquals("两京十五日", item.vod_name)
        assertEquals("2026", item.vod_year)
        assertTrue(item.vod_pic?.startsWith("https://") == true, "封面走 https")
        assertEquals("ah_ap2233", item._source)
        // 两条线路：feifan（分享页）$$$ ffm3u8（直连），各 6 集
        val lines = item.vod_play_url!!.split("$D$D$D")
        assertEquals(2, lines.size, "两行播放链")
        assertEquals(6, lines[0].split("#").size)
        assertEquals(6, lines[1].split("#").size)
    }

    @Test
    fun detail_share_line_then_direct_line() {
        val item = CmsParser.detail(Golden.text("ffzy_detail100474"), src, 100474L)!!
        val lines = item.vod_play_url!!.split("$D$D$D")

        val share = lines[0].split("#")
        for (ep in share) {
            val url = ep.substringAfter(D)
            assertTrue(url.contains("/share/"), "第一行每集是分享页：$url")
            assertTrue(url.startsWith("https://"), "分享页走 https")
        }
        assertEquals(
            "https://super.ffzy-online6.com/share/d5c4a9269e461042632f0e07414edf9d",
            share.first().substringAfter(D),
            "首集分享页原样保留，不改写",
        )

        val direct = lines[1].split("#")
        for (ep in direct) {
            val url = ep.substringAfter(D)
            assertTrue(url.endsWith(".m3u8"), "第二行每集是直连 m3u8：$url")
        }
    }

    @Test
    fun detail_has_no_legacy_token_shape() {
        val item = CmsParser.detail(Golden.text("ffzy_detail100474"), src, 100474L)!!
        val url = item.vod_play_url!!
        assertTrue(!url.contains("ETH-"), "不再下发 BBA 密文")
        assertTrue(!url.contains("NBY-"), "不再下发 NBY 密文")
        assertTrue(!url.contains("http://"), "全样本无明文 HTTP")
    }

    // ---------------- 搜索（ac=detail&wd=） ----------------

    @Test
    fun search_returns_same_title_variants() {
        val r = CmsParser.fill(Golden.text("ffzy_search_qyn"), src, 1)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(8, r.total)
        assertEquals(8, r.items.size)
        assertEquals("庆余年第二季粤语", r.items.first().vod_name)
        assertTrue(
            r.items.all { it._source == "ah_ap2233" },
            "搜索结果全部带源 key，跨源换源才认得出处",
        )
    }
}
