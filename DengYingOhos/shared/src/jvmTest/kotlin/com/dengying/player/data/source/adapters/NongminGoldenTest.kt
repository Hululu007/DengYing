package com.dengying.player.data.source.adapters

import com.dengying.player.core.CmsSources
import com.dengying.player.core.RequestOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 农民影视（nmystv 新域，2026-10-31）金样本守卫。
 *
 * 样本由 `tools/dy_capture_1031.py` 自 nmystv.com 实抓（庆余年第一季粤语，id=135630）：
 * 搜索页 / 详情页 / 播放页（`var player_aaaa`）。钉住三件事：
 *  1. 搜索条目锚点（module-card-item-poster → module-item-note → data-original → strong）；
 *  2. 详情容器切分（`module-play-list-content`，线名 `data-dropdown-value`，\s+ 容忍双空格）；
 *  3. macCMS 播放页 `player_aaaa` 提取（[com.dengying.player.core.WebRule.playerAaaa] 路径，
 *     复用 [Ap2233PlayPageParser] 的 macCMS encrypt 解码）。
 *
 * 老域 vip.wwgz.cn 的死状样本（nongmin_search/root/type）保留在 WebGoldenTest，作历史判死证据。
 * **站点当前 TLS 证书过期（2026-10-29 到期未续）→ 源维持停用**；本组样本是证书续期后
 * 一键启用时的协议回归基准，也用于区分「站点改版」与「实现漂移」两类失败。
 */
class NongminGoldenTest {

    private val nongmin = CmsSources.byKey("ah_nongmin")!!

    @Test
    fun golden_search_yields_card_items() {
        val r = WebParser.parseSearchHtml(nongmin, Golden.text("nongmin2_search"), nongmin.rule!!)

        assertEquals(RequestOutcome.SuccessWithData, r.outcome)
        assertEquals(14, r.items.size)

        val first = r.items.first()
        assertEquals(135630L, first.vod_id)
        assertEquals("庆余年第一季粤语", first.vod_name)
        assertEquals("全45集", first.vod_remarks)
        assertEquals("https://pic3.yzzyimg.online/upload/vod/2025-05-14/17472101161.jpg", first.vod_pic)
        assertEquals("https://nmystv.com/index.php/vod/detail/id/135630.html", first.vod_uri)
        assertEquals("ah_nongmin", first._source)
    }

    @Test
    fun golden_detail_groups_episodes_into_named_line() {
        val v = WebParser.parseDetailHtml(
            nongmin, Golden.text("nongmin2_detail"), nongmin.rule!!, 135630L,
        )!!

        // 该片只有一条线（sid1=1080zyk），线名来自标签条 data-dropdown-value
        assertEquals("农民副线", v.vod_play_from)
        val eps = v.vod_play_url!!.split("#")
        assertEquals(45, eps.size)
        assertEquals(
            "第01集\$https://nmystv.com/index.php/vod/play/id/135630/sid/1/nid/1.html",
            eps.first(),
        )
        assertEquals(
            "第45集\$https://nmystv.com/index.php/vod/play/id/135630/sid/1/nid/45.html",
            eps.last(),
        )
    }

    @Test
    fun golden_play_page_player_aaaa_yields_media_url() {
        val pp = Ap2233PlayPageParser.parse(Golden.text("nongmin2_play"))
        assertNotNull(pp)

        assertEquals("1080zyk", pp.from)
        assertEquals(0, pp.encrypt)
        assertEquals(1, pp.sid)
        assertEquals(1, pp.nid)
        assertEquals(
            "https://vvip.high26-playback.com/20250515/7008_decc2e06/index.m3u8",
            pp.decodedUrl,
        )
        // master playlist（#EXT-X-STREAM-INF）由播放器自动下钻，解析层不递归
    }
}
