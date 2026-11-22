package com.dengying.player.domain.source

/**
 * 源启用覆盖层（FR-601）—— 纯模块，不依赖 Android
 *
 * 需求：源清单可在应用内查看、启用停用与增删，改动持久化。
 * 验收：**内置清单作为基线，用户改动为覆盖层**；停用源即时影响聚合与搜索；
 * 有「恢复默认」。
 *
 * ## 为什么用「覆盖层」而不是直接改清单
 *
 * `CmsSources.ALL` 是**编译期常量**。直接改它等于改代码——而用户改的只是自己的偏好。
 * 更重要的是：
 *
 * - 发版时会**覆盖**用户改动。若直接改清单，用户停用的源在升级后全部复活。
 * - 基线（内置清单）要能回答"这个源**本来**是启用还是停用"——这决定了
 *   「恢复默认」恢复到什么状态，也决定了发版记录的源清单快照该记什么。
 *
 * 故覆盖层只存**与基线不同的那些源**：用户没动过的源不写入覆盖层，
 * 这样将来基线变了（新版本启用了一个源），用户也能自动拿到新状态。
 *
 * ## 三个关键判定
 *
 * | 情形 | 判定 |
 * |---|---|
 * | 用户没动过 | 用基线值 |
 * | 用户停用了基线启用的源 | 停用 |
 * | 用户启用了基线停用的源 | 启用（容许——用户可能自己重测过） |
 *
 * ## 与 JSON 的分界（KMP P1）
 *
 * 本文件只做**内存中的纯逻辑**，不含任何 JSON 代码。序列化/反序列化搬到了
 * `com.dengying.player.data.source.SourceOverrideCodec`——因为 `org.json` 在
 * Kotlin/Native 上不存在，而本对象被 `core/Sources.kt` 的 `baselines()`
 * 引用（commonMain），必须保持零平台依赖。
 *
 * 分界的代价：`Payload` 是公共数据形状，需要维护一份 Kotlin 侧的等价编解码
 * 在 data 层。这个代价是可接受的——`data/` 在 P2 进来后才能谈通用 JSON，
 * 而 P1 的验收标准是"无行为变化"，重构范畴越小越安全。
 */
object SourceOverrideRules {

    /** 用户对单个源的覆盖决定 */
    data class Override(
        /** 源标识 */
        val key: String,
        /** 用户设定的启用状态 */
        val enabled: Boolean,
        /** 设定时间（毫秒）。供"最近改过哪些源"的展示与排查 */
        val updatedAt: Long,
    )

    /** 覆盖层存储形状（落 preferences 的一个键） */
    data class Payload(val version: Int, val list: List<Override>)

    /** 覆盖层结构版本 */
    const val OVERRIDE_VERSION = 1

    /** 空覆盖层 */
    fun emptyPayload() = Payload(OVERRIDE_VERSION, emptyList())

    /** 覆盖层中查找某源的决定；未覆盖返回 null */
    fun overrideOf(payload: Payload, key: String): Override? =
        payload.list.firstOrNull { it.key == key }

    /**
     * 求某源的实际启用状态。
     *
     * @param payload 覆盖层
     * @param key 源标识
     * @param baselineEnabled 基线（内置清单）里的启用状态
     */
    fun enabledOf(payload: Payload, key: String, baselineEnabled: Boolean): Boolean =
        // 未覆盖 → 用基线。这样新版本改了基线，用户能自动拿到
        overrideOf(payload, key)?.enabled ?: baselineEnabled

    /**
     * 写入一条覆盖决定。
     *
     * 若用户改回了基线值，则**删除覆盖项**而不是写一条"与基线相同"的记录——
     * 后者会让覆盖层越积越大，且当基线将来变化时，用户被一条无意义的旧记录钉住。
     *
     * @return 新的覆盖层（不修改入参）
     */
    fun setEnabled(
        payload: Payload,
        key: String,
        enabled: Boolean,
        baselineEnabled: Boolean,
        nowMs: Long,
    ): Payload {
        val list = payload.list.filterNot { it.key == key }.toMutableList()
        // 改回基线值 → 不写覆盖
        if (enabled != baselineEnabled) {
            list += Override(key, enabled, nowMs)
        }
        return Payload(payload.version, list)
    }

    /** 清空全部覆盖（「恢复默认」） */
    fun clearAll(payload: Payload) = Payload(payload.version, emptyList())

    /**
     * 与基线不同的源数量。
     *
     * 供 UI 显示「N 项已修改」——用户需要知道自己改过什么，
     * 否则「恢复默认」是个盲操作。
     */
    fun changedCount(payload: Payload): Int = payload.list.size

    /** 基线条目：源标识 + 它在**内置清单**里的启用状态 */
    data class Baseline(val key: String, val enabled: Boolean)

    /**
     * 应用覆盖层后的启用源数。
     *
     * 供状态提示使用（与 `SearchNotice.countSearchable` 的口径配合）。
     */
    fun effectiveEnabledCount(payload: Payload, baselines: List<Baseline>): Int =
        baselines.count { enabledOf(payload, it.key, it.enabled) }
}
