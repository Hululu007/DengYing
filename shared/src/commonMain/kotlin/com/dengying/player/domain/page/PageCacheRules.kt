package com.dengying.player.domain.page

/**
 * 分页缓存策略（FR-701、FR-702）—— 纯模块，不依赖 Android
 *
 * ## 需求
 *
 * 首页每次进入都要重新请求全部分类映射与列表，实测很慢。用户要求
 * 「加载完了要保存缓存，但不要过多保存，每一页都要」——即**按页缓存**且有上限。
 *
 * ## 为什么策略与存储分开
 *
 * 键构造、TTL 判定、淘汰顺序这三件事**不含 IO**，放在 domain 就能脱设备单测。
 * 存储（SharedPreferences 落盘）在 data 层。这样"缓存该不该命中"的判定
 * 不依赖设备，用例能穷举边界。
 *
 * ## 键的构成
 *
 * `分类 | 页码`，**不含源集合版本**——刻意如此：
 * 源启用状态变化时由调用方整体 `invalidate()`，而不是让键变复杂。
 * 理由：键里塞版本号会让"哪些键过期了"变成需要遍历推断的问题；
 * 而"用户改了源清单"是一个**明确的整体失效时机**，直接清空更简单可靠。
 */
object PageCacheRules {

    /** 缓存条目上限（「不要过多保存」的落实）。 */
    const val MAX_ENTRIES = 30

    /**
     * 陈旧阈值：**30 分钟**。超过就该后台刷新——但**仍然可以命中**。
     *
     * 【为什么"陈旧"与"失效"必须分开】用户抱怨"首页每次进入都很慢"。
     * 初版把两者合一（超过 30 分钟就完全不命中），于是隔得久一点进来，
     * 又要重新等 4-8 秒——真机探针抓到过 `[index-load] elapsed=3786`。
     *
     * 正确做法是 **stale-while-revalidate**：
     *  - 有缓存 → **立刻显示**（哪怕内容旧了），用户零等待
     *  - 同时后台拉新的 → 内容真的变了才替换（不闪、不跳）
     *
     * 于是"陈旧"只决定**要不要后台刷新**，不决定"能不能用"。
     */
    const val STALE_MS = 30 * 60 * 1000L

    /**
     * 保留期：**24 小时**。超过才真正删除条目。
     *
     * 与 [STALE_MS] 分开的理由同上：缓存的价值在于"立刻有内容"，
     * 删除门槛应当远高于刷新门槛。24 小时足够覆盖"一天内再看同一分类"，
     * 也保证存储不会无限增长（与 [MAX_ENTRIES] 构成双重上限）。
     */
    const val RETAIN_MS = 24 * 60 * 60 * 1000L

    /** 缓存键：分类名 + 页码 */
    fun keyOf(category: String, page: Int): String = "$category#$page"

    /** 从键里取页码；格式不符返回 -1 */
    fun pageOf(key: String): Int {
        val i = key.lastIndexOf('#')
        if (i < 0) return -1
        return key.substring(i + 1).toIntOrNull() ?: -1
    }

    /** 从键里取分类名；格式不符返回空串 */
    fun categoryOf(key: String): String {
        val i = key.lastIndexOf('#')
        return if (i < 0) "" else key.substring(0, i)
    }

    /** 一条缓存记录的最小形状（只需这几个字段，便于单测构造） */
    interface Entry {
        val key: String
        /** 写入时刻（毫秒） */
        val savedAt: Long
        /** 最后访问时刻（毫秒），供 LRU */
        val accessedAt: Long
    }

    /**
     * 该条目此刻能否命中（即"能不能先给用户看"）。
     *
     * @param nowMs 当前时刻
     * @return true 表示可命中，调用方**不必等网络**
     */
    fun shouldServe(entry: Entry, nowMs: Long): Boolean {
        // 时钟回拨保护：savedAt 在未来时视为不可用（避免"永不过期"的坏条目）
        if (entry.savedAt > nowMs) return false
        // 保留期内一律可命中——**先给内容，再后台刷新**（stale-while-revalidate）
        return nowMs - entry.savedAt < RETAIN_MS
    }

    /**
     * 该条目是否已陈旧（该后台刷新）。
     *
     * 与 [shouldServe] 回答的是两个不同问题：
     *  - 本函数：内容**该不该更新**
     *  - [shouldServe]：内容**还能不能给用户看**
     *
     * 陈旧不影响可用性——这正是"立刻出内容"的前提。
     */
    fun isStale(entry: Entry, nowMs: Long): Boolean {
        if (entry.savedAt > nowMs) return true
        return nowMs - entry.savedAt >= STALE_MS
    }

    /**
     * 写入或更新后是否需要淘汰。
     *
     * @param entries 现有条目（**不含**即将写入的这条）
     * @param incomingKey 即将写入的键
     * @param nowMs 当前时刻
     * @param max 容量上限。默认 [MAX_ENTRIES]；**参数化的理由**：
     *   容量为 0 的退化情形（关闭缓存）无法用常量测试，而"关掉缓存"是
     *   将来可能要用的回滚手段，必须可测。
     * @return 需要淘汰的键（按 LRU 顺序，最旧的在前）；不需要淘汰时返回空列表
     */
    fun evictOrder(
        entries: List<PageCacheRules.Entry>,
        incomingKey: String,
        nowMs: Long,
        max: Int = MAX_ENTRIES,
    ): List<String> {
        // 已存在的同键先移除（它是"更新"，不占新位置）
        val others = entries.filter { it.key != incomingKey }
        val total = others.size + 1
        if (total <= max) return emptyList()

        val need = total - max
        // LRU：按最后访问时间升序；同值按写入时间升序；再同值按键升序保证顺序确定
        return others
            .sortedWith(
                compareBy<PageCacheRules.Entry> { it.accessedAt }
                    .thenBy { it.savedAt }
                    .thenBy { it.key }
            )
            .take(need)
            .map { it.key }
    }

    /**
     * 需要**删除**的条目（超过保留期）。
     *
     * 与 [staleKeys] 分开：删除是"内容太旧，留着没意义"，
     * 陈旧是"内容该更新但仍可用"。两者触发时机与语义都不同。
     */
    fun expiredKeys(entries: List<Entry>, nowMs: Long): List<String> =
        entries.filterNot { shouldServe(it, nowMs) }.map { it.key }

    /** 该后台刷新的条目（陈旧但仍可命中） */
    fun staleKeys(entries: List<Entry>, nowMs: Long): List<String> =
        entries.filter { shouldServe(it, nowMs) && isStale(it, nowMs) }.map { it.key }

    /**
     * 源启用状态变化时该清哪些。
     *
     * **返回全部键**——因为列表内容是"多源聚合"的结果，任一源的启用状态变化
     * 都会改变聚合结果，故整体失效。这一点刻意写出来：若将来有人想"只清某源相关"，
     * 需要先证明聚合结果可按源拆分（当前不能）。
     */
    fun invalidateOnSourceChange(entries: List<Entry>): List<String> = entries.map { it.key }
}
