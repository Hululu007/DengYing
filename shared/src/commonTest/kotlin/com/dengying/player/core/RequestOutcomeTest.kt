package com.dengying.player.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * 四态判定的契约用例（FR-108、FR-109、NFR-008）
 *
 * 核心要证明的一件事：**两种 `ok:false` 必须可区分**——
 * 「请求失败」与「请求成功但无数据」在处置上完全不同。
 */
class RequestOutcomeTest {

    // ---- 判定顺序：status 优先于 itemCount ----

    @Test
    fun `未拿到响应判请求失败`() {
        // status = 0 表示网络层异常。此时 itemCount 也是 0，
        // 若不先判 status 就会被误判为"无数据"
        assertEquals(
            RequestOutcome.RequestFailed,
            classifyOutcome(OutcomeFacts(status = 0, parsed = false, itemCount = 0)),
        )
    }

    @Test
    fun `非 2xx 判请求失败`() {
        for (code in listOf(301, 400, 403, 404, 500, 502, 503)) {
            assertEquals(
                RequestOutcome.RequestFailed,
                classifyOutcome(OutcomeFacts(status = code, parsed = true, itemCount = 10)),
                "HTTP $code 应判请求失败",
            )
        }
    }

    @Test
    fun `2xx 但解析不出结构判解析失败`() {
        assertEquals(
            RequestOutcome.ParseFailed,
            classifyOutcome(OutcomeFacts(status = 200, parsed = false, itemCount = 0)),
        )
    }

    @Test
    fun `2xx 解析成功但零条目判无数据`() {
        assertEquals(
            RequestOutcome.SuccessNoData,
            classifyOutcome(OutcomeFacts(status = 200, parsed = true, itemCount = 0)),
        )
    }

    @Test
    fun `2xx 解析成功且有条目判成功`() {
        assertEquals(
            RequestOutcome.SuccessWithData,
            classifyOutcome(OutcomeFacts(status = 200, parsed = true, itemCount = 1)),
        )
    }

    // ---- 核心验收：故障转移的触发条件 ----

    @Test
    fun `无数据不触发故障转移`() {
        // FR-108 的验收条件之一：该源正常响应了，换别的源只是因为
        // 别处可能收录了这部片，与"这个源坏了"无关
        assertFalse(RequestOutcome.SuccessNoData.shouldFailover())
    }

    @Test
    fun `请求失败与解析失败都触发故障转移`() {
        assertTrue(RequestOutcome.RequestFailed.shouldFailover())
        assertTrue(RequestOutcome.ParseFailed.shouldFailover())
    }

    @Test
    fun `成功不触发故障转移`() {
        assertFalse(RequestOutcome.SuccessWithData.shouldFailover())
    }

    // ---- 健康分信号：无数据不扣分 ----

    @Test
    fun `无数据的健康分信号不是失败`() {
        assertEquals(HealthSignal.SuccessNoData, RequestOutcome.SuccessNoData.toHealthSignal())
        assertNotEquals(HealthSignal.Failed, RequestOutcome.SuccessNoData.toHealthSignal())
    }

    @Test
    fun `源是否健康的判定与扣分口径一致`() {
        assertTrue(RequestOutcome.SuccessWithData.isSourceHealthy())
        assertTrue(RequestOutcome.SuccessNoData.isSourceHealthy())
        assertFalse(RequestOutcome.RequestFailed.isSourceHealthy())
        assertFalse(RequestOutcome.ParseFailed.isSourceHealthy())
    }

    // ---- 聚合提示（NFR-008）：两条提示必须不同 ----

    @Test
    fun `全部失败提示含网络字样`() {
        val notice = aggregateNotice(listOf(RequestOutcome.RequestFailed, RequestOutcome.ParseFailed))
        assertEquals(OutcomeMessages.ALL_SOURCES_FAILED, notice)
        assertTrue(notice.contains("网络"), "全部失败应提示检查网络")
    }

    @Test
    fun `全部无数据提示不含网络字样`() {
        // 否则用户会去排查网络，方向就错了
        val notice = aggregateNotice(listOf(RequestOutcome.SuccessNoData, RequestOutcome.SuccessNoData))
        assertEquals(OutcomeMessages.NO_SOURCE_HAS_IT, notice)
        assertFalse(notice.contains("网络"), "「都没这部片」不应提网络")
    }

    @Test
    fun `两条提示确实不同`() {
        assertNotEquals(
            aggregateNotice(listOf(RequestOutcome.RequestFailed)),
            aggregateNotice(listOf(RequestOutcome.SuccessNoData)),
        )
    }

    @Test
    fun `有源拿到数据时不提示`() {
        assertEquals(
            aggregateNotice(
                listOf(
                    RequestOutcome.RequestFailed,
                    RequestOutcome.SuccessNoData,
                    RequestOutcome.SuccessWithData,
                )
            ),
            "",
)
    }

    @Test
    fun `无参与源时不提示`() {
        assertEquals(aggregateNotice(emptyList()), "")
    }

    @Test
    fun `混合失败与无数据时按原工程口径报「都没有这部片」`() {
        // 【移植核对】这条期望值来自**验证原实现**，不是我的设计选择：
        // 原 ArkTS 的 aggregateNotice 判定顺序是
        //   有数据 → 不提示；全失败 → 网络提示；有 noData → 「都没有这部片」
        // 故「1 个失败 + 1 个无数据」落进第三条，给出的是「都没有这部片」。
        //
        // 【已知局限】此时告诉用户"都没有"略过乐观——确实有一个源是真的坏了。
        // 但改动它会破坏 NFR-008 已验收的两分法（全失败 vs 有源正常），
        // 且原工程的实测记录（「已搜索全部来源，都没有这部片」）就是按这个口径采的。
        // 移植期**保持原行为**，把这条观察记在这里；要改需连同验收记录一起更新。
        assertEquals(
            OutcomeMessages.NO_SOURCE_HAS_IT,
            aggregateNotice(listOf(RequestOutcome.RequestFailed, RequestOutcome.SuccessNoData)),
        )
    }
}
