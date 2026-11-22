package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSources
import com.dengying.player.core.RequestOutcome
import com.dengying.player.data.source.crypto.Digest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 蓝光4k（Api2233）解析层守卫 —— 金样本驱动
 *
 * 样本见 `shared/src/jvmTest/resources/golden/`（`_meta.json` 登记字节数）：
 * `ap2233_catlist` / `ap2233_videolist` / `ap2233_detail906` 是 `ac=list` /
 * `ac=videolist&pg=1` / `ac=detail&ids=906` 的真实响应；
 * `ap2233_nby` / `ap2233_bba` 是二层解密端点 `api/nby.php` / `api/bba.php`
 * 对真实加密串的响应（纯 base64 密文）。
 *
 * 本测试钉住三件事：
 *
 * 1. **三端点可用**——推翻了源码里 `ah_ap2233` 原 note 的「ac=list / ac=detail 全部
 *    返回 closed」结论，故 `caps` 已从 `CAPS_SEARCH_ONLY` 改为 `CAPS_CMS`。
 * 2. **播放串全是加密串、无一条明文地址**——这是 `playGap` 存在的依据。
 * 3. **二层响应解不出明文**——把「播放链不闭环」这个事实固定在测试里，
 *    将来若上游恢复直链或算法被解出，这里会立刻红，而不是靠人记得。
 */
class Ap2233GoldenTest {

    private val src = CmsSources.byKey("ah_ap2233")!!

    /** Kotlin 字符串里 `$` 是模板起始符，紧跟中文还是合法标识符——故用常量拼接 */
    private val D = "$"

    // ---------------- 分类表（ac=list） ----------------

    @Test
    fun catlist_classes_count_and_type_ids() {
        val classes = CmsParser.classes(Golden.text("ap2233_catlist"))
        assertEquals(18, classes.size, "ac=list 的 class 数组共 18 项")
        assertEquals((1..18).toList(), classes.map { it.type_id }, "type_id 是 1..18 连续")
    }

    @Test
    fun catlist_type_pid_is_present_with_real_parents() {
        // 该字段**存在且为 0**（不是缺失）。三态判定的 null 分支不在这份样本上生效，
        // 故这里钉的是「0 被读成 0」而非「缺失读成 null」。
        val classes = CmsParser.classes(Golden.text("ap2233_catlist"))
        assertEquals(
            listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 5, 5),
            classes.map { it.type_pid },
            "type_pid 全是显式数字：前 5 项 0（顶级），6..12 属 1，13..16 属 2，17..18 属 5",
        )
    }

    @Test
    fun catlist_header_fields() {
        // ac=list 与 ac=videolist 共享分页头，但**只有 ac=list 带 class 数组**
        val r = CmsParser.fill(Golden.text("ap2233_catlist"), src, 1)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(1, r.page)
        assertEquals(394, r.pageCount)
        assertEquals(7874, r.total)
        assertEquals(20, r.items.size)
    }

    // ---------------- 列表（ac=videolist） ----------------

    @Test
    fun videolist_header_fields() {
        val r = CmsParser.fill(Golden.text("ap2233_videolist"), src, 1)
        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(1, r.page)
        assertEquals(394, r.pageCount)
        assertEquals(7874, r.total)
        assertEquals(20, r.items.size)
    }

    @Test
    fun videolist_ids_in_response_order() {
        // 顺序原样保留：源站按更新时间排，客户端不得重排
        val r = CmsParser.fill(Golden.text("ap2233_videolist"), src, 1)
        assertEquals(
            listOf<Long>(
                8267L, 8264L, 8263L, 8257L, 8256L, 8176L, 8131L, 8117L, 7981L, 7975L,
                7974L, 7973L, 7972L, 7971L, 7967L, 7963L, 7962L, 7961L, 7866L, 8242L,
            ),
            r.items.map { it.vod_id },
        )
    }

    @Test
    fun videolist_first_item_field_by_field() {
        val r = CmsParser.fill(Golden.text("ap2233_videolist"), src, 1)
        val it = r.items.first()
        assertEquals(8267L, it.vod_id)
        assertEquals("BBA", it.vod_play_from)
        assertEquals("ah_ap2233", it._source)
        assertTrue(it.vod_name.isNotEmpty(), "样本首项 vod_name 非空")
        assertEquals("2026", it.vod_year, "首项带 vod_year=2026（该源有年份字段）")
    }

    // ---------------- 播放串形态（核心事实） ----------------

    @Test
    fun videolist_all_tokens_are_ciphertext() {
        val r = CmsParser.fill(Golden.text("ap2233_videolist"), src, 1)
        var total = 0
        var bba = 0
        var nby = 0
        var url = 0
        var unknown = 0
        for (item in r.items) {
            val s = Ap2233PlayParser.scan(item.vod_play_url ?: "")
            total += s.total
            bba += s.bba
            nby += s.nby
            url += s.url
            unknown += s.unknown
        }
        assertEquals(197, total, "20 行合计 197 个播放串")
        assertEquals(130, bba, "其中 BBA 系 130 个")
        assertEquals(67, nby, "NBY 系 67 个")
        assertEquals(0, url, "明文地址 0 个——这正是 playGap 的依据")
        assertEquals(0, unknown, "未知形态 0 个：上游没有给出第三种形态")
    }

    @Test
    fun videolist_first_row_scan() {
        val r = CmsParser.fill(Golden.text("ap2233_videolist"), src, 1)
        val s = Ap2233PlayParser.scan(r.items.first().vod_play_url ?: "")
        assertEquals(6, s.total, "首行 6 集")
        assertEquals(6, s.bba, "首行 6 集全是 BBA 系")
    }

    @Test
    fun detail_all_tokens_are_nby_and_none_plain() {
        val item = CmsParser.detail(Golden.text("ap2233_detail906"), src, 906L)
        assertNotNull(item)
        val s = Ap2233PlayParser.scan(item.vod_play_url ?: "")
        assertEquals(1247, s.total)
        assertEquals(1247, s.nby, "ids=906 全部是 NBY 系")
        assertEquals(0, s.url)
        assertEquals(0, s.unknown)
        assertTrue(
            !item.vod_play_url!!.contains("http", ignoreCase = true),
            "1247 个播放串里没有任何一个含 http——不存在可直连的媒体地址",
        )
    }

    @Test
    fun detail_episode_names_keep_upstream_order() {
        // 集名不是 1..1247 的顺序：首段之后是 40、41、42… 末段回到 37、38、39。
        // 这是上游原样，**不得排序修正**——排序会让「第几集」与地址错位。
        val item = CmsParser.detail(Golden.text("ap2233_detail906"), src, 906L)!!
        val names = item.vod_play_url!!.split("#").map { it.substringBefore(D) }
        assertEquals(listOf("1", "40", "41", "42", "43", "44"), names.take(6))
        assertEquals(listOf("37", "38", "39"), names.takeLast(3))
    }

    @Test
    fun detail_first_token_shape() {
        val item = CmsParser.detail(Golden.text("ap2233_detail906"), src, 906L)!!
        val token = item.vod_play_url!!.split("#").first().substringAfter(D)
        assertEquals(Ap2233PlayParser.TokenKind.Nby, Ap2233PlayParser.kindOf(token))
        assertEquals(139, token.length, "NBY 系串总长恒 139")
        assertEquals(96, Ap2233PlayParser.bodyHexOf(token).length, "去掉前缀与尾部后 96 个 hex")
        assertEquals(
            "49936b5cd4bd3c5474ad387e45145e5d",
            Ap2233PlayParser.tailHexOf(token),
            "尾部 32 位在全部样本里恒同——不是每集独立的密钥",
        )
    }

    @Test
    fun bba_token_shape() {
        val r = CmsParser.fill(Golden.text("ap2233_videolist"), src, 1)
        val token = r.items.first().vod_play_url!!.split("#").first().substringAfter(D)
        assertEquals(Ap2233PlayParser.TokenKind.Bba, Ap2233PlayParser.kindOf(token))
        assertEquals(420, token.length, "BBA 系串总长恒 420")
        assertEquals(384, Ap2233PlayParser.bodyHexOf(token).length, "剥掉 ETH- 与 32 位常量前缀后 384 个 hex")
        assertEquals("", Ap2233PlayParser.tailHexOf(token), "BBA 系没有 | 尾部")
    }

    @Test
    fun bba_const_prefix_is_shared_but_not_an_address_identity() {
        // 130 个 BBA token 的前 32 个 hex 字符全同。**不能**据此判定「同一地址」：
        // 同一部片第 01 集与第 02 集前缀相同、其后分叉。
        val r = CmsParser.fill(Golden.text("ap2233_videolist"), src, 1)
        val tokens = r.items
            .flatMap { (it.vod_play_url ?: "").split("$$$") }
            .flatMap { it.split("#") }
            .map { if (it.contains(D)) it.substringAfterLast(D) else it }
            .filter { Ap2233PlayParser.kindOf(it) == Ap2233PlayParser.TokenKind.Bba }
        assertEquals(130, tokens.size)
        val prefixes = tokens.map { it.removePrefix(Ap2233PlayParser.PREFIX_BBA).take(32) }.toSet()
        assertEquals(setOf(Ap2233PlayParser.BBA_CONST_PREFIX_HEX), prefixes)
        // 但整串去重后远多于 1 —— 前缀相同 ≠ 地址相同
        assertTrue(tokens.toSet().size > 100, "130 个 token 里不同串有 ${tokens.toSet().size} 个")
    }

    // ---------------- 二层端点响应：解不出明文 ----------------

    @Test
    fun nby_response_decodes_to_binary_without_media_hints() {
        val raw = binText("ap2233_nby.bin")
        val decoded = Digest.base64DecodeBytes(raw)
        assertEquals(368, decoded.size, "nby.php 响应 base64 解出 368 字节")
        val text = decoded.decodeToString()
        assertTrue(!text.contains("#EXT"), "无 m3u8 头")
        assertTrue(!text.contains("m3u8", ignoreCase = true), "无 m3u8 线索")
        assertTrue(!text.contains("http", ignoreCase = true), "无 http 线索")
    }

    @Test
    fun bba_response_decodes_to_binary_without_media_hints() {
        val raw = binText("ap2233_bba.bin")
        val decoded = Digest.base64DecodeBytes(raw)
        assertEquals(96, decoded.size, "bba.php 响应 base64 解出 96 字节")
        val text = decoded.decodeToString()
        assertTrue(!text.contains("#EXT"), "无 m3u8 头")
        assertTrue(!text.contains("http", ignoreCase = true), "无 http 线索")
    }

    // ---------------- 识别层单元语义 ----------------

    @Test
    fun kind_of_recognizes_prefixes_case_insensitively_for_scheme() {
        assertEquals(Ap2233PlayParser.TokenKind.Url, Ap2233PlayParser.kindOf("http://a/b.m3u8"))
        assertEquals(Ap2233PlayParser.TokenKind.Url, Ap2233PlayParser.kindOf("HTTPS://a/b.m3u8"))
        assertEquals(Ap2233PlayParser.TokenKind.Bba, Ap2233PlayParser.kindOf("ETH-abc"))
        assertEquals(Ap2233PlayParser.TokenKind.Nby, Ap2233PlayParser.kindOf("NBY-XMYAESabc|def"))
        assertEquals(Ap2233PlayParser.TokenKind.Unknown, Ap2233PlayParser.kindOf(""))
        assertEquals(Ap2233PlayParser.TokenKind.Unknown, Ap2233PlayParser.kindOf("junk"))
        assertEquals(
            Ap2233PlayParser.TokenKind.Url,
            Ap2233PlayParser.kindOf("  http://a/b  "),
            "首尾空白先 trim 再判",
        )
    }

    @Test
    fun body_hex_strips_prefix_and_tail() {
        assertEquals(
            "abc",
            Ap2233PlayParser.bodyHexOf("NBY-XMYAESabc|def"),
            "NBY 剥掉前缀与 | 之后的尾部",
        )
        assertEquals(
            "abc",
            Ap2233PlayParser.bodyHexOf("ETH-abc"),
            "BBA 剥掉 ETH-（无可剥的常量前缀时原样保留）",
        )
        assertEquals(
            "abc",
            Ap2233PlayParser.bodyHexOf("ETH-${Ap2233PlayParser.BBA_CONST_PREFIX_HEX}abc"),
            "BBA 命中常量前缀时一并剥掉",
        )
        assertEquals("", Ap2233PlayParser.bodyHexOf("http://a/b"), "明文地址没有串体")
        assertEquals("", Ap2233PlayParser.bodyHexOf("junk"), "未知形态没有串体")
        assertEquals(
            "abc",
            Ap2233PlayParser.bodyHexOf("NBY-XMYAESabc"),
            "缺 | 时整段都是串体",
        )
    }

    @Test
    fun tail_hex_only_for_nby() {
        assertEquals("def", Ap2233PlayParser.tailHexOf("NBY-XMYAESabc|def"))
        assertEquals("", Ap2233PlayParser.tailHexOf("NBY-XMYAESabc"), "缺 | 时没有尾部")
        assertEquals("", Ap2233PlayParser.tailHexOf("ETH-abc"), "BBA 没有尾部")
        assertEquals("", Ap2233PlayParser.tailHexOf("http://a/b"))
        assertEquals("", Ap2233PlayParser.tailHexOf("junk"))
    }

    @Test
    fun only_plain_url_is_playable() {
        assertTrue(Ap2233PlayParser.isPlayable("http://a/b.m3u8"))
        assertTrue(Ap2233PlayParser.isPlayable("https://a/b.m3u8"))
        assertTrue(!Ap2233PlayParser.isPlayable("ETH-abc"), "加密串不能交给播放器")
        assertTrue(!Ap2233PlayParser.isPlayable("NBY-XMYAESabc|def"))
        assertTrue(!Ap2233PlayParser.isPlayable("junk"))
        assertTrue(!Ap2233PlayParser.isPlayable(""))
    }

    @Test
    fun gap_notice_is_actionable() {
        assertNull(Ap2233PlayParser.gapNoticeOf("http://a/b.m3u8"), "可播时不给提示")
        val cipher = Ap2233PlayParser.gapNoticeOf("ETH-abc")
        assertNotNull(cipher)
        assertEquals(
            "该线路地址为加密串，App 内无法解出媒体地址。请换一条线路或换源播放",
            cipher,
        )
        assertEquals(cipher, Ap2233PlayParser.gapNoticeOf("NBY-XMYAESabc|def"), "两种加密串文案相同")
        assertEquals(
            "该线路地址无法识别，请换一条线路或换源播放",
            Ap2233PlayParser.gapNoticeOf("junk"),
            "未知形态文案区分开：让用户知道不是「加密」而是「认不出」",
        )
    }

    @Test
    fun scan_handles_multi_line_and_missing_dollar() {
        val s = Ap2233PlayParser.scan("")
        assertEquals(Ap2233PlayParser.Scan(0, 0, 0, 0, 0), s, "空串零计数")

        val one = Ap2233PlayParser.scan("第01集${D}http://a/1.m3u8")
        assertEquals(Ap2233PlayParser.Scan(1, 0, 0, 1, 0), one)

        val multi = Ap2233PlayParser.scan(
            "第01集${D}http://a/1.m3u8#第02集${D}ETH-aa" +
                "$$$" +
                "第01集${D}NBY-XMYAESbb|cc#第02集${D}junk",
        )
        assertEquals(Ap2233PlayParser.Scan(4, 1, 1, 1, 1), multi, "两线路各两集，四类各一")

        // 没有 $ 的段落：整段当播放串（老数据可能是裸地址）
        assertEquals(
            Ap2233PlayParser.Scan(1, 0, 0, 1, 0),
            Ap2233PlayParser.scan("http://a/1.m3u8"),
            "缺 $ 时整段视为播放串",
        )
        assertEquals(Ap2233PlayParser.Scan(0, 0, 0, 0, 0), Ap2233PlayParser.scan("#"), "空段跳过")
    }

    @Test
    fun scan_splits_on_last_dollar() {
        // 集名理论上可能含 $；取最后一个 $ 才不会被集名带偏
        val s = Ap2233PlayParser.scan("第${D}01集${D}http://a/1.m3u8")
        assertEquals(Ap2233PlayParser.Scan(1, 0, 0, 1, 0), s)
    }

    /** 读 `.bin` 样本（[Golden.text] 固定拼 `.txt`，故此处单独读） */
    private fun binText(name: String): String {
        val stream = javaClass.getResourceAsStream("/golden/$name")
            ?: error("金样本缺失: /golden/$name")
        return stream.use { it.readBytes().decodeToString() }
    }
}
