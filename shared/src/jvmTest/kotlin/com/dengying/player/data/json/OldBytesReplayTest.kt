package com.dengying.player.data.json

import com.dengying.player.data.source.SourceOverrideCodec
import com.dengying.player.data.source.adapters.Golden
import com.dengying.player.domain.source.SourceOverrideRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 「旧字节回放」测试 —— KMP P3 批 E 的核心验收项
 *
 * ## 为什么必须有这一组
 *
 * 批 E 把 `StoreEnvelope` / `SourceOverrideCodec` / `Store` / `PageCache` 的 JSON 实现
 * 从 org.json 换成 kotlinx-serialization。**用户设备上已经存在 org.json 写出的字节**，
 * 迁移后第一次启动必须能原样读懂；读不动就是「升级即丢收藏与历史」。
 *
 * ## 样本从哪来
 *
 * `shared/src/jvmTest/resources/golden/old_*.txt` 全部由**真实的 org.json 20250107**
 * 在 `:app` 的 unitTest 里跑出来（一次性探针 `OldBytesProbe`，已删），不是手写猜的格式。
 * 这很关键：org.json 的键序来自 `HashMap`、对 `/` 的转义行为、对数字的 `toString()`
 * 形态都属于实现细节，手写样本会把自己的猜测当成事实。
 *
 * ## 实测结论（订正了此前的错误判断）
 *
 * 迁移前的 Kdoc 曾断言「org.json `toString()` 会把 `/` 转义成 `\/`」。**实测是错的**：
 * org.json 20250107 对 `/` 不转义（样本里 `https://img.example.com/a.jpg` 原样出现）。
 * 因此新旧字节的真实差异**只有键序**（org.json = HashMap 序，`JsonX.buildObject` =
 * 插入序），不存在转义差异。写出的字节形态仍会变，但读路径双向兼容。
 */
class OldBytesReplayTest {

    // ---------------- 1. 信封层：旧字节 -> 解出内层文本 ----------------

    @Test
    fun replay_old_favorites_envelope_decodes_to_inner_array() {
        val raw = Golden.text("old_favorites_envelope")
        val d = StoreEnvelope.decodePayload(raw)

        assertEquals(StoreEnvelope.Kind.Ok, d.kind, "旧版收藏信封必须判为 Ok")
        assertEquals(1, d.version, "版本号必须解出 1")
        assertFalse(d.needsUpgrade, "v1 是当前版本，不该要求升级")

        // 内层是旧版收藏数组，逐字节等于当时的 payload
        assertEquals(Golden.text("old_favorites_payload"), d.payload, "内层 payload 必须原样取出")
        assertTrue(d.payload.startsWith("["), "内层必须是数组形态")
    }

    @Test
    fun replay_old_history_envelope_decodes_to_inner_array() {
        val raw = Golden.text("old_history_envelope")
        val d = StoreEnvelope.decodePayload(raw)

        assertEquals(StoreEnvelope.Kind.Ok, d.kind)
        assertEquals(1, d.version)
        assertEquals(Golden.text("old_history_payload"), d.payload)
    }

    @Test
    fun replay_old_search_envelope_decodes_with_escaped_quotes() {
        val raw = Golden.text("old_search_envelope")
        val d = StoreEnvelope.decodePayload(raw)

        assertEquals(StoreEnvelope.Kind.Ok, d.kind)
        assertEquals(Golden.text("old_search_payload"), d.payload)

        // 旧字节里含 `\"` 转义（关键词 三体 "黑暗森林"），
        // 解出来必须还原成真实引号 —— 这是双层编码最容易错的地方
        val arr = JsonX.parseArray(d.payload) ?: error("内层必须能解析为数组")
        assertEquals(3, JsonX.len(arr))
        assertEquals("三体 \"黑暗森林\"", JsonX.textOf(arr[2]))
    }

    /**
     * 分类缓存**不走信封** —— 这条用例把这件事钉死。
     *
     * 实测依据：`Store.writeClassCache()` 直接
     * `store.putString(KEY_CLASS_CACHE, obj.toString(), sync = true)`，
     * `readClassCache()` 直接 `JsonX.parseObject(raw)`，两端都没有 `StoreEnvelope`。
     *
     * 所以把 `old_class_payload`（对象形态）塞进 `decodePayload` 会判 Corrupt
     * —— 那不是 bug，是信封层只接受数组内层（`StoreEnvelope` 的既有约定）。
     * 这里校验的是**真实读路径**能读懂旧字节。
     */
    @Test
    fun replay_old_class_cache_bypasses_envelope() {
        val raw = Golden.text("old_class_payload")
        val obj = JsonX.parseObject(raw)
        assertNotNull(obj, "旧版分类缓存必须能按裸对象解析")

        assertEquals(1, JsonX.keys(obj).size, "只有一个源键")
        val src = JsonX.optObject(obj, "ah_wencai")
        assertNotNull(src, "源键下必须是对象")
        val arr = JsonX.optArray(src, "classes")
        assertNotNull(arr, "classes 必须是数组")
        assertEquals(2, JsonX.len(arr))
        assertEquals("电影", JsonX.str(JsonX.optObjectAt(arr, 0), "type_name", ""))
        assertEquals(1700000000999L, JsonX.long(src, "time", 0L))

        // 反向钉死：把它当信封喂进去必然 Corrupt（内层不是数组）
        assertEquals(
            StoreEnvelope.Kind.Corrupt,
            StoreEnvelope.decodePayload(raw).kind,
            "对象内层不是信封合法形态 —— 说明分类缓存确实没走信封",
        )
    }

    @Test
    fun replay_old_override_envelope_decodes_to_inner_array() {
        val raw = Golden.text("old_override_envelope")
        val d = StoreEnvelope.decodePayload(raw)

        assertEquals(StoreEnvelope.Kind.Ok, d.kind)
        assertEquals(1, d.version)
        assertEquals(Golden.text("old_override_payload"), d.payload)
    }

    // ---------------- 2. 裸数组（v0 无信封） ----------------

    @Test
    fun replay_old_bare_array_is_accepted_as_v0() {
        val raw = Golden.text("old_bare_array")
        val d = StoreEnvelope.decodePayload(raw)

        assertEquals(StoreEnvelope.Kind.Ok, d.kind, "裸数组是 v0 形态，必须可读")
        assertEquals(0, d.version, "裸数组按 v0 处理")
        assertEquals(raw, d.payload, "裸数组原样作为 payload")

        // 注意：`DecodeResult.needsUpgrade` 字段在 v0 下是 false ——
        // 「要不要惰性升级」由独立方法判定，不是这个字段
        assertFalse(d.needsUpgrade, "DecodeResult.needsUpgrade 字段只看 kind==NeedsUpgrade")
        assertTrue(StoreEnvelope.needsLazyUpgrade(raw), "v0 应触发惰性升级（独立方法判定）")
        assertTrue(StoreEnvelope.canOverwrite(raw), "v0 允许被覆盖写")
    }

    // ---------------- 3. 损坏与更高版本（负向语义） ----------------

    @Test
    fun replay_old_corrupt_envelope_is_corrupt_not_crash() {
        val raw = Golden.text("old_corrupt_envelope")
        val d = StoreEnvelope.decodePayload(raw)

        assertEquals(StoreEnvelope.Kind.Corrupt, d.kind, "截断的信封必须判 Corrupt")
        assertEquals(-1, d.version, "Corrupt 的版本号约定为 -1")
        assertEquals(StoreEnvelope.EMPTY_PAYLOAD, d.payload, "Corrupt 时载荷回落空数组")
        assertFalse(d.needsUpgrade)
        // canOverwrite 只拒绝 NeedsUpgrade：损坏数据允许被覆盖写。
        // 「保留原始字节」由 Store.readList 的 Corrupt 分支负责（先拷到 store_corrupt 诊断键），
        // 不是靠禁止覆盖实现的 —— 否则用户永远修不好一个损坏的键。
        assertTrue(StoreEnvelope.canOverwrite(raw), "Corrupt 允许覆盖（原始字节已先拷入诊断键）")
    }

    @Test
    fun replay_old_newer_envelope_needs_upgrade() {
        val raw = Golden.text("old_newer_envelope")
        val d = StoreEnvelope.decodePayload(raw)

        assertEquals(StoreEnvelope.Kind.NeedsUpgrade, d.kind, "v9 高于当前版本，必须判 NeedsUpgrade")
        assertEquals(9, d.version)
        assertEquals(StoreEnvelope.EMPTY_PAYLOAD, d.payload, "NeedsUpgrade 不得交出内层数据")
        assertTrue(d.needsUpgrade)
        assertFalse(StoreEnvelope.canOverwrite(raw), "更高版本数据禁止覆盖（否则降级会毁数据）")
    }

    /**
     * `payload` 不是字符串时**不是** Corrupt，而是回落空载荷。
     *
     * 实测依据（旧实现逐行）：`val text = if (body is String) body else ""` 之后
     * `val inner = text.ifEmpty { EMPTY_PAYLOAD }` —— 于是 `JSONArray("[]")` 解析成功，
     * 结果是 `Ok + "[]"`。新实现照抄了这条链，故行为一致。
     *
     * 这个用例的价值是钉死「容错而非拒绝」：用户数据里出现异常类型时不该整键报废。
     */
    @Test
    fun replay_payload_not_string_falls_back_to_empty() {
        val raw = Golden.text("old_payload_not_string")
        val d = StoreEnvelope.decodePayload(raw)

        assertEquals(StoreEnvelope.Kind.Ok, d.kind, "payload 类型不符时按空载荷处理，不判 Corrupt")
        assertEquals(1, d.version)
        assertEquals(StoreEnvelope.EMPTY_PAYLOAD, d.payload, "载荷回落空数组")
        assertTrue(StoreEnvelope.canOverwrite(raw), "空载荷可被覆盖写")
    }

    @Test
    fun replay_version_not_number_is_corrupt() {
        val d = StoreEnvelope.decodePayload(Golden.text("old_version_not_number"))
        assertEquals(StoreEnvelope.Kind.Corrupt, d.kind, "version 取不到整数 -> Corrupt")
    }

    // ---------------- 4. 覆盖层：旧字节 -> 覆盖表 ----------------

    @Test
    fun replay_old_override_payload_normalizes_to_two_entries() {
        val p = SourceOverrideCodec.normalizePayload(Golden.text("old_override_payload"))

        assertEquals(SourceOverrideRules.OVERRIDE_VERSION, p.version, "覆盖层版本号必须保留")
        assertEquals(2, p.list.size, "旧字节里两条覆盖")
        assertEquals("ah_wencai", p.list[0].key)
        assertFalse(p.list[0].enabled, "ah_wencai 旧值 = 关闭")
        assertEquals(1700000001111L, p.list[0].updatedAt)
        assertEquals("ah_duboku", p.list[1].key)
        assertTrue(p.list[1].enabled, "ah_duboku 旧值 = 开启")
        assertEquals(1700000002222L, p.list[1].updatedAt)
    }

    @Test
    fun replay_old_override_payload_through_envelope() {
        // 真实路径：Store.loadOverrideCache() 先解信封再交给 codec
        val env = StoreEnvelope.decodePayload(Golden.text("old_override_envelope"))
        assertEquals(StoreEnvelope.Kind.Ok, env.kind)

        val p = SourceOverrideCodec.normalizePayload(env.payload)
        assertEquals(2, p.list.size)
        assertEquals("ah_wencai", p.list[0].key)
        assertFalse(p.list[0].enabled)
    }

    // ---------------- 5. 新写出的字节必须能被自己读回（往返） ----------------

    @Test
    fun new_envelope_roundtrips_through_decode() {
        val payload = Golden.text("old_favorites_payload")
        val encoded = StoreEnvelope.encodePayload(payload)
        val d = StoreEnvelope.decodePayload(encoded)

        assertEquals(StoreEnvelope.Kind.Ok, d.kind)
        assertEquals(1, d.version)
        assertEquals(payload, d.payload, "新写出的信封必须能原样读回内层")
    }

    @Test
    fun new_envelope_keys_are_in_insertion_order() {
        // 记录「字节形态变了」这件事本身：新实现的键序是 version 在前
        val encoded = StoreEnvelope.encodePayload("[]")
        assertTrue(
            encoded.startsWith("{\"version\":1,\"payload\":"),
            "新实现的键序为插入序（version, payload），旧实现是 HashMap 序；实际：$encoded",
        )
    }
}
