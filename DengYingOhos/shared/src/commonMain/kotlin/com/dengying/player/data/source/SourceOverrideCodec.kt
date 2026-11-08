package com.dengying.player.data.source

import com.dengying.player.data.json.JsonX
import com.dengying.player.domain.source.SourceOverrideRules

/**
 * 源覆盖层的 JSON 编解码（FR-601 的存储侧）
 *
 * ## 为什么单独成文件
 *
 * `SourceOverrideRules` 的纯逻辑被 `core/Sources.kt` 的 `baselines()` 引用，
 * 而 `core/` 在 KMP P1 已迁入 `:shared/commonMain`。当时 `org.json` 在
 * Kotlin/Native 上不存在，故按「纯逻辑 / 平台编解码」把 JSON 部分切出来留在 `:app`。
 *
 * P3 引入 kotlinx-serialization 后，JSON 编解码不再是平台专属能力，本文件
 * 随批 E 迁回 `:shared/commonMain`，改用 [JsonX]（org.json 语义的跨平台门面）。
 * 判定逻辑与拆分前**逐条等价**，P3 批 E 的 parity 与金样本回放守护这一点。
 */
object SourceOverrideCodec {

    /**
     * 校验并归一化一份外部来的覆盖层数据（**数组 JSON 文本**形态）。
     *
     * 用途：从存储读回时**不信任**存储内容——可能被手改、
     * 可能是旧版本写的、可能被别的应用写坏。归一化而非拒绝：
     * 丢弃坏项、保留能用的部分，坏项不影响其它源。
     *
     * **入参是数组的 JSON 文本**：覆盖层与收藏/历史一样走 `StoreEnvelope` 的
     * 版本信封，信封的 `payload` 就是这层数组。这样它自动获得 FR-508 的
     * 「更高版本写入不被覆盖」保护，不需要另建一套。
     *
     * @param json 信封解出的数组文本；空串或解析失败 → 空覆盖层
     */
    fun normalizePayload(json: String?): SourceOverrideRules.Payload {
        if (json.isNullOrEmpty()) return SourceOverrideRules.emptyPayload()

        val rawList = JsonX.parseArray(json) ?: return SourceOverrideRules.emptyPayload()

        val list = ArrayList<SourceOverrideRules.Override>(rawList.size)
        val seen = HashSet<String>()
        for (i in 0 until rawList.size) {
            val item = JsonX.optObjectAt(rawList, i) ?: continue

            // `key` 必须是非空字符串。运行时数据不可信，故用类型判定而非直接信任
            val key: String = if (JsonX.isNull(item["key"])) "" else JsonX.str(item, "key", "")
            if (key.isEmpty()) continue

            // `enabled` 非布尔时按 false 处理——不猜
            val enabled = JsonX.bool(item, "enabled", false)

            val at: Long = if (JsonX.isNull(item["updatedAt"])) 0L else JsonX.long(item, "updatedAt", 0L)

            if (seen.contains(key)) {
                // 同一个 key 出现多次：保留**最后一条**（与"后写覆盖先写"一致）
                val idx = list.indexOfFirst { it.key == key }
                if (idx >= 0) list[idx] = SourceOverrideRules.Override(key, enabled, at)
                continue
            }
            seen.add(key)
            list += SourceOverrideRules.Override(key, enabled, at)
        }
        return SourceOverrideRules.Payload(SourceOverrideRules.OVERRIDE_VERSION, list)
    }

    /**
     * 序列化成数组 JSON 文本（写入信封的载荷）。
     *
     * 【当前零调用者】`Store.setSourceEnabled` / `clearSourceOverride` 自己内联
     * 构造同一份 JSON。保留本函数是为了让编解码成对，避免「只有读没有写」的
     * 不对称——P3 批 E 未改变这个现状（改调用点属于重构，不在迁移范围内）。
     */
    fun toJson(payload: SourceOverrideRules.Payload): String {
        val arr = payload.list.map { o ->
            JsonX.buildObject(
                "key" to o.key,
                "enabled" to o.enabled,
                "updatedAt" to o.updatedAt,
            )
        }
        return JsonX.buildArray(arr).toString()
    }
}
