package com.dengying.player.domain.store

/**
 * 观看记录淘汰策略（FR-504）—— 纯函数，不依赖 Android
 *
 * 问题：原实现按「全局最近」整体淘汰（超容量即丢队尾）。
 * 长剧连看会以**集**为单位刷屏队列，把低频但仍在看的旧片唯一一条记录挤出容量，
 * 该片从此失去续播定位——用户回来「继续观看」找不到那部剧。
 *
 * 规则（可断言）：
 *
 * 1. **分组不变式**：每个 `(vod_id, source)` 分组至少保留其 `updateTime` 最新的一条。
 *    该片的续播定位永不因容量丢失。
 * 2. **淘汰顺序**：超容量时先淘汰「各组中非最新的记录里 `updateTime` 最旧者」；
 *    若各组只剩最新一条仍超容量，则按各组最新记录的 `updateTime` 从最旧**整组**淘汰。
 * 3. 容量与既有语义不变；本对象只决定「保留哪些记录」，不看内容。
 *
 * 淘汰顺序确定（同 `updateTime` 时按键值稳定排序），故可写确定性用例。
 */
object LedgerPrune {

    /** 参与淘汰的最小记录形状（只需这几个字段，便于单测构造） */
    interface Entry {
        val vodId: Long
        val source: String
        val episodeIndex: Int
        val updateTime: Long
    }

    /** 分组键：同一部片在同一来源下视为一组（即续播定位的粒度） */
    fun groupKeyOf(e: Entry): String = "${e.vodId}@${e.source}"

    /** 组内排序：updateTime 降序；同值按集号降序，保证顺序确定 */
    private val byRecencyDesc = Comparator<Entry> { a, b ->
        if (b.updateTime != a.updateTime) {
            b.updateTime.compareTo(a.updateTime)
        } else {
            b.episodeIndex.compareTo(a.episodeIndex)
        }
    }

    /** 组间淘汰序：updateTime 升序；同值按分组键升序，保证顺序确定 */
    private val byOldestFirst = Comparator<Entry> { a, b ->
        if (a.updateTime != b.updateTime) {
            a.updateTime.compareTo(b.updateTime)
        } else {
            groupKeyOf(a).compareTo(groupKeyOf(b))
        }
    }

    /**
     * 按 FR-504 规则裁剪到容量上限。
     *
     * @param list 记录列表（调用方通常已把最新一条放在队首）
     * @param max 容量上限
     * @return 新列表，按 `updateTime` 降序（最新在前），不修改入参
     */
    fun <T : Entry> pruneLedger(list: List<T>, max: Int): List<T> {
        if (max <= 0) return emptyList()

        if (list.size <= max) {
            return list.sortedWith(byRecencyDesc)
        }

        // 按分组聚合，组内最新在前
        val groups = LinkedHashMap<String, MutableList<T>>()
        for (e in list) {
            groups.getOrPut(groupKeyOf(e)) { ArrayList() }.add(e)
        }

        // 规则 1：每组最新一条必留；其余进候选淘汰池
        val mustKeep = ArrayList<T>(groups.size)
        val spill = ArrayList<T>()
        for ((_, arr) in groups) {
            arr.sortWith(byRecencyDesc)
            mustKeep += arr[0]
            for (i in 1 until arr.size) spill += arr[i]
        }

        val roomForSpill = max - mustKeep.size

        if (roomForSpill < 0) {
            // 规则 2 后半：分组数本身已超容量，按各组最新记录的 updateTime 从最旧整组淘汰。
            // 被淘汰整组的续播定位无法保全——这是容量硬约束下的必然损失，属已知行为。
            mustKeep.sortWith(byOldestFirst)
            val survivors = mustKeep.subList(mustKeep.size - max, mustKeep.size).toMutableList()
            survivors.sortWith(byRecencyDesc)
            return survivors
        }

        // 规则 2 前半：从非最新记录里淘汰最旧者，保留最近的 `roomForSpill` 条
        spill.sortWith(byOldestFirst)
        val keptSpill: List<T> =
            if (roomForSpill == 0) emptyList()
            else spill.subList(spill.size - roomForSpill, spill.size)

        val out = ArrayList<T>(mustKeep.size + keptSpill.size)
        out += mustKeep
        out += keptSpill
        out.sortWith(byRecencyDesc)
        return out
    }

    /** 分组数（续播定位的条数） */
    fun groupCountOf(list: List<Entry>): Int {
        val s = HashSet<String>(list.size)
        for (e in list) s.add(groupKeyOf(e))
        return s.size
    }

    /**
     * 每组最新一集的记录（用于「续播定位是否仍在」的自检）。
     * 键为分组键，值为该组 `updateTime` 最大的记录。
     */
    fun <T : Entry> latestPerGroup(list: List<T>): Map<String, T> {
        val m = HashMap<String, T>()
        for (e in list) {
            val k = groupKeyOf(e)
            val cur = m[k]
            if (cur == null || e.updateTime > cur.updateTime) m[k] = e
        }
        return m
    }
}
