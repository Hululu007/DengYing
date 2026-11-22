package com.dengying.player.domain.search

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.Test

/**
 * `TitleKey` 的契约用例（FR-204、FR-304）
 *
 * 三条契约来自需求原文，逐条对应用例：
 *  1. 空白/全半角/书名号差异折叠为同一条
 *  2. 同名不同年份**不**折叠
 *  3. 折叠键与持久化的内容标识**不得混用**（用类型隔离保证，此处以用例固化语义）
 */
class TitleKeyTest {

    // ---- 契约 1：写法差异必须折叠 ----

    @Test
    fun `书名号差异折叠为同一条`() {
        // 受控实验的六种常见写法（原始验收数据）
        val key = TitleKey.ofRaw("庆余年", "2019")
        assertEquals(key, TitleKey.ofRaw("《庆余年》", "2019"))
        assertEquals(key, TitleKey.ofRaw("「庆余年」", "2019"))
        assertEquals(key, TitleKey.ofRaw("  庆余年  ", "2019"))
        assertEquals(key, TitleKey.ofRaw("庆余年", "2019"))
    }

    @Test
    fun `连续空白折叠为单个空格而不是删除`() {
        // 「庆余年 第二季」与「庆余年第二季」是**不同**的片名——
        // 全删会把它们错误折叠成同一条
        val withSpace = TitleKey.ofRaw("庆余年 第二季", "2019")
        val withoutSpace = TitleKey.ofRaw("庆余年第二季", "2019")
        assertNotEquals(withSpace, withoutSpace)

        // 但"多个空格"与"一个空格"是同一部
        assertEquals(withSpace, TitleKey.ofRaw("庆余年   第二季", "2019"))
        assertEquals(withSpace, TitleKey.ofRaw("庆余年\u3000第二季", "2019"))
    }

    @Test
    fun `全角字母数字归一为半角`() {
        assertEquals(
            TitleKey.ofRaw("ABC 123", "2024"),
            TitleKey.ofRaw("ＡＢＣ　１２３", "2024"),
        )
    }

    @Test
    fun `大小写差异折叠`() {
        assertEquals(
            TitleKey.ofRaw("The Matrix", "1999"),
            TitleKey.ofRaw("the matrix", "1999"),
        )
    }

    // ---- 契约 2：年份必须进键 ----

    @Test
    fun `同名不同年份不折叠`() {
        // 复仇者联盟有 2012 与 2019 的复映，无年份则两条会被折叠成一条
        assertNotEquals(
            TitleKey.ofRaw("复仇者联盟", "2012"),
            TitleKey.ofRaw("复仇者联盟", "2019"),
        )
    }

    @Test
    fun `年份缺失与年份为 0 归为同一类`() {
        val missing = TitleKey.ofRaw("某片", null)
        assertEquals(missing, TitleKey.ofRaw("某片", ""))
        assertEquals(missing, TitleKey.ofRaw("某片", "0"))
        assertEquals(missing, TitleKey.ofRaw("某片", "不是年份"))
    }

    // ---- 年份解析：CMS 返回的可能是日期串 ----

    @Test
    fun `年份解析取开头四位数字`() {
        assertEquals(2019, TitleKey.parseYear("2019"))
        assertEquals(2019, TitleKey.parseYear("2019-09-29"))
        assertEquals(2019, TitleKey.parseYear("  2019  "))
        assertEquals(0, TitleKey.parseYear(""))
        assertEquals(0, TitleKey.parseYear(null))
    }

    @Test
    fun `年份解析取串内第一段连续数字的前四位`() {
        // 【移植核对】原实现的循环是：
        //   遇数字就累加；**数字之前**遇到非数字继续扫（不放弃）；**数字之后**遇非数字才停。
        // 故「年份2019」得到 2019，不是 0——实现注释里写的"取开头连续四位"与代码不符，
        // 代码是权威。这里按代码行为固化，避免移植时把行为改成注释描述的样子。
        assertEquals(2019, TitleKey.parseYear("年份2019"))
        assertEquals(2019, TitleKey.parseYear("年份 2019"))
        // 数字之后遇非数字即停：取到 202 就断（不足四位但也返回该值）
        assertEquals(202, TitleKey.parseYear("202年"))
    }

    @Test
    fun `年份解析对纯非数字串返回缺失`() {
        assertEquals(0, TitleKey.parseYear("年份"))
        assertEquals(0, TitleKey.parseYear("暂无"))
    }

    // ---- 拼接歧义：分隔符必须不可见于标题 ----

    @Test
    fun `标题含下划线不产生拼接歧义`() {
        // 若用 `_` 作分隔符：`A_B` + `2024` 与 `A` + `B_2024` 会同键
        assertNotEquals(
            TitleKey.ofRaw("A_B", "2024"),
            TitleKey.ofRaw("A", "B_2024"),
        )
    }

    // ---- 规范化：边界输入不崩 ----

    @Test
    fun `空与 null 输入返回空键而不抛异常`() {
        assertEquals(TitleKey.normalizeTitle(null), "")
        assertEquals(TitleKey.normalizeTitle(""), "")
        assertEquals(TitleKey.normalizeTitle("《》"), "")
        assertEquals(TitleKey.normalizeTitle("   "), "")
    }

    @Test
    fun `未知的「找AV」类词汇只做规范化不做内容处理`() {
        // 规范化只碰空白/引号/全半角/大小写，不改动文字内容
        assertEquals(TitleKey.normalizeTitle("《东京热》"), "东京热")
    }
}
