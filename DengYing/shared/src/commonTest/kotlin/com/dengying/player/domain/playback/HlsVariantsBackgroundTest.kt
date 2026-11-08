package com.dengying.player.domain.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 后台变体清单的两个判据（FR-412）。
 *
 * 这两条原本埋在 `PlayerViewModel` 的协程里：
 * [HlsVariants.wantsVariants] 是 `startBackgroundVariantsIfWanted` 的第一行，
 * [HlsVariants.isStale] 是 `startBackgroundVariants` 协程里的一行 `return@launch`。
 * 抽出之前后者**既无单测也无真机证据**（docs/04 §16.18.6.4：两次抓取都在
 * 400–500ms 内失败，从未在时间上重叠，`bg-variants-stale` 一次也没打出来）。
 */
class HlsVariantsBackgroundTest {

    // ---------------- wantsVariants：值不值得抓 ----------------

    @Test
    fun m3u8SuffixIsWanted() {
        assertTrue(HlsVariants.wantsVariants("https://a.example/x/master.m3u8", fromSource = false))
    }

    @Test
    fun m3u8SuffixIsWantedIgnoringCase() {
        assertTrue(HlsVariants.wantsVariants("https://a.example/x/master.M3U8", fromSource = false))
    }

    @Test
    fun queryStringDoesNotHideTheSuffix() {
        // 主列表地址常带签名参数，substringBefore('?') 先把它们切掉
        assertTrue(
            HlsVariants.wantsVariants(
                "https://a.example/x/master.m3u8?sign=abc&=123",
                fromSource = false,
            )
        )
    }

    @Test
    fun mp4IsNotWanted() {
        assertFalse(HlsVariants.wantsVariants("https://a.example/x/video.mp4", fromSource = false))
    }

    @Test
    fun suffixLookalikeIsNotWanted() {
        // 只有 `.m3u8` 结尾才算，`.m3u8x` 之类不能误判
        assertFalse(HlsVariants.wantsVariants("https://a.example/x/master.m3u8x", fromSource = false))
    }

    @Test
    fun fromSourceAlwaysWants() {
        // 换链来源的地址常常是 `content.php?id=...` 这种没有后缀的形式，但就是主列表；
        // 靠后缀会把它们全挡掉，所以 fromSource 直接放行
        assertTrue(HlsVariants.wantsVariants("https://a.example/content.php?id=88", fromSource = true))
    }

    @Test
    fun emptyUrlIsNotWanted() {
        assertFalse(HlsVariants.wantsVariants("", fromSource = false))
    }

    // ---------------- isStale：抓回来时还算不算数 ----------------

    @Test
    fun sameUrlIsNotStale() {
        assertFalse(HlsVariants.isStale("https://a/x.m3u8", "https://a/x.m3u8"))
    }

    @Test
    fun changedUrlIsStale() {
        // 换集/换线/换档 → 上一集的档位不能盖到当前集上
        assertTrue(HlsVariants.isStale("https://a/ep1.m3u8", "https://a/ep2.m3u8"))
    }

    @Test
    fun queryOnlyDifferenceIsStale() {
        // 同一路径不同签名也是不同地址：签名往往绑定集数
        assertTrue(HlsVariants.isStale("https://a/x.m3u8?ep=1", "https://a/x.m3u8?ep=2"))
    }

    @Test
    fun emptiedUrlIsStale() {
        // 会话被释放时 resolvedUrl 会被清空，此时任何返回结果都该丢
        assertTrue(HlsVariants.isStale("https://a/x.m3u8", ""))
    }
}
