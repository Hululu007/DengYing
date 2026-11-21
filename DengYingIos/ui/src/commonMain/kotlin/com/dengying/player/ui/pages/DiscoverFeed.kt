package com.dengying.player.ui.pages

import com.dengying.player.core.VideoItem
import kotlin.random.Random

/** 发现页无限流：每批追加的条数（滚近尾部 +1 批） */
internal const val DISCOVER_BATCH = 9

/**
 * 发现页一张卡。
 *
 * [round] 是「第几轮洗牌」：同一部片会在不同 round 里再次出现——池子只有几百条，
 * 一旦要求全局不重复就必然触底停住。把 round 编进 [key]，LazyColumn 才会把
 * 「同一部片的第 N 次出现」当成不同的项（否则 key 冲突直接抛异常）。
 */
internal data class DiscoverEntry(val vod: VideoItem, val round: Int) {
    val key: String get() = "$round:${vod.vod_id}"
}

/**
 * 发现页取片器：在一轮内遍历当前池（洗牌顺序），取尽后轮次 +1 重洗。
 *
 * 【为什么允许跨轮重复】用户口径是「视频源很多，实在触底就洗牌复用」；
 * 严格不重复的序列在有限池子上只能走到头，触底后列表就再也长不出来了。
 *
 * 【为什么池子是每次取批时传入】六分类分页会在浏览中陆续到货，池子会扩容。
 * 每轮开始时读当前池，新片自然进入下一轮——不必重建列表，也不打断
 * 已有卡片、解析缓存和滚动位置。
 *
 * 纯逻辑、不碰 Compose 状态，便于单测（同轮不重复 / 跨轮允许重复 / 扩容生效）。
 */
internal class DiscoverFeedSource(
    private val batch: Int = DISCOVER_BATCH,
    /** 洗牌函数（注入以便单测确定性；默认按 round 作种子） */
    private val shuffle: (List<VideoItem>, Int) -> List<VideoItem> = { pool, round ->
        pool.shuffled(Random(round))
    },
) {
    private var round = -1
    private var order: List<VideoItem> = emptyList()
    private var cursor = 0

    /** 当前轮次（尚未开始为 -1） */
    val currentRound: Int get() = round

    /** 取下一批卡片；池子为空返回空表（调用方继续显示空态，不放宽） */
    fun nextBatch(pool: List<VideoItem>): List<DiscoverEntry> {
        val unique = pool.distinctBy { it.vod_id }
        if (unique.isEmpty()) return emptyList()
        val out = ArrayList<DiscoverEntry>(batch)
        var guard = 0
        while (out.size < batch && guard++ < batch) {
            if (round < 0 || cursor >= order.size) startRound(unique)
            while (cursor < order.size && out.size < batch) {
                val v = order[cursor++]
                // 池子缩减（换批 / 源被关掉）后旧顺序里可能留下已不在池中的片
                if (unique.none { it.vod_id == v.vod_id }) continue
                out += DiscoverEntry(v, round)
            }
        }
        return out
    }

    private fun startRound(unique: List<VideoItem>) {
        round++
        order = shuffle(unique, round)
        cursor = 0
    }
}

/**
 * 发现页只放「这条线路现在就能整片播」的片。
 *
 * 两条硬门槛：
 *  - 片子的来源必须被用户在「源状态」里启用着；
 *  - 来源不得有已知播放缺口（`playGap`）——有缺口的源进了池子，用户点开就是
 *    一句「播不了」，比干脆不展示更差。
 *
 * 池子被过滤空时由界面显示空态，不在这里放宽条件补救。
 */
internal fun playableInDiscover(
    item: VideoItem,
    repo: com.dengying.player.data.source.SourceRepository,
): Boolean {
    val key = item._source ?: return false
    val src = com.dengying.player.core.CmsSources.byKey(key) ?: return false
    return repo.isSourceEnabled(src) && src.playGap.isNullOrEmpty()
}
