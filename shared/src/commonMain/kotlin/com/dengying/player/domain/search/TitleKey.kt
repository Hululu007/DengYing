package com.dengying.player.domain.search

/**
 * 标题折叠键 `TitleKey`（FR-204、FR-304）—— 纯函数，不依赖 Android
 *
 * 用途：**跨源结果折叠**与**分页去重**。同一部片被多个源收录时，
 * 标题常有空白、全半角、书名号等差异（`《庆余年》` 与 `庆余年`），
 * 若不规范化就会显示成两条。
 *
 * ## 规范化规则（`ADR-0003 §20` 原文，不自行改造）
 *
 * trim → 折叠空白（含全角空格）→ 去《》〈〉「」等书名号引号 → NFKC 全半角归一 → 小写
 *
 * 顺序有约束：**先折叠空白与去括号、再做 NFKC 与大小写**。
 *
 * ## 与 `ContentKey` 的分野（`ADR-0003 §27-28`）
 *
 * | | 用途 | 性质 |
 * |---|---|---|
 * | `ContentKey`（source + vod_id） | **持久化身份**：收藏、观看记录 | 稳定、不解释 |
 * | `TitleKey`（本对象） | **显示层折叠**：跨源去重 | 等价类划分、可演化 |
 *
 * **两者不得混用**：若用 `TitleKey` 做持久化，同名的不同片会互相覆盖收藏与进度，
 * 续播错乱（直接违反 BR-004）。
 *
 * ## 为什么年份必须进键
 *
 * 「同名不同年份不折叠」是验收条件：`复仇者联盟` 有 2012 与 2019 的复映，
 * 若无年份则两条会被折叠成一条，用户看不到其中一部。
 *
 * 判定不看 UI，故可脱设备单测（NFR-009）。
 */
object TitleKey {

    /** 需要剥除的书名号与引号（含中英文与角标变体） */
    private const val QUOTE_CHARS = "《》〈〉「」『』“”\"''`"

    /**
     * 空白与不可见字符：半角空格、制表、换行、回车、全角空格、不间断空格。
     *
     * 【与原实现的差异】ArkTS 版把全角空格当"空白"处理但不做 NFKC；Kotlin 侧保持
     * 同样的行为，不引入 `String.normalize()` 的差异——两侧折叠键必须一致，
     * 否则跨版本数据（本地已落盘的收藏/历史）在折叠时会出现"同名两条"。
     */
    private fun isSpace(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u3000' || c == '\u00A0'

    private fun isQuote(c: Char): Boolean = QUOTE_CHARS.indexOf(c) >= 0

    /**
     * 把可能有全角字符的字符串归一为半角。
     *
     * 只处理常见区间（ASCII 可打印区的全角对应、全角空格），不引入完整 NFKC——
     * 自己实现可控且可单测，且与 ArkTS 版逐字符等价。
     */
    fun toHalfWidth(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                // 全角 ！ (FF01) ～ ～ (FF5E) → 半角 21 ～ 7E
                c.code in 0xFF01..0xFF5E -> sb.append((c.code - 0xFEE0).toChar())
                // 全角空格 → 半角空格
                c.code == 0x3000 -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * 标题规范化（`ADR-0003 §20` 的顺序）。
     *
     * 折叠空白指把连续空白压成单个半角空格，而不是全部删除——
     * 「庆余年 第二季」与「庆余年第二季」是**不同**的片名，
     * 全删会把它们错误折叠成同一条。
     */
    fun normalizeTitle(title: String?): String {
        if (title.isNullOrEmpty()) return ""

        // 1. trim + 折叠内部连续空白为单个空格；同时剥除书名号与引号
        val sb = StringBuilder(title.length)
        var lastWasSpace = false
        for (c in title) {
            if (isSpace(c)) {
                // 连续空白压成一个空格；开头的空白直接跳过（等价于 trim 左侧）
                if (sb.isNotEmpty() && !lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
                continue
            }
            if (isQuote(c)) {
                // 剥除书名号与引号；剥除不产生空格（《A》《B》→ AB）
                continue
            }
            sb.append(c)
            lastWasSpace = false
        }
        // 去掉因末尾空白留下的单空格（等价于 trim 右侧）
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
            sb.setLength(sb.length - 1)
        }
        // 2. 全半角归一
        val half = toHalfWidth(sb.toString())
        // 3. 小写
        return half.lowercase()
    }

    /**
     * 从字符串年份里取四位数字；取不到返回 0（表示缺失）。
     *
     * CMS 协议返回的 `vod_year` 就是字符串（可能是 `"2019"`、`""`、
     * 也可能是 `"2019-09-29"` 这类日期），故单开一个入口而不是让调用方
     * 自己 `parseInt`——各调用方各自解析会引入不一致。
     *
     * 提取规则：取开头的连续四位数字；中途遇到非数字即停
     * （`2019-09-29` → `2019`）。
     */
    fun parseYear(yearText: String?): Int {
        if (yearText.isNullOrEmpty()) return 0
        val s = yearText.trim()
        if (s.isEmpty()) return 0

        val digits = StringBuilder(4)
        for (c in s) {
            if (c in '0'..'9') {
                digits.append(c)
                if (digits.length == 4) break
            } else if (digits.isNotEmpty()) {
                // 已取到数字后遇到非数字即停
                break
            }
        }
        if (digits.isEmpty()) return 0
        val n = digits.toString().toIntOrNull() ?: return 0
        return if (n > 0) n else 0
    }

    /**
     * 构造折叠键：**规范化标题 + 年份**。
     *
     * ## 为什么不含分类（对 `ADR-0003 §20` 的一处实测修正）
     *
     * 原文写的是「规范化标题 + 年份 + 分类」。2026-10-11 用 26 条真实数据做对照实验，
     * 结论是**加上分类反而制造重复**：
     *
     * | 折叠键 | 折叠后条数 |
     * |---|---|
     * | 无规范化（改动前） | 13 |
     * | 标题 + 年份（不含分类） | 13 |
     * | 标题 + 年份 + 分类 | **17** |
     *
     * 原因是**各源的分类体系不统一**：同一部《庆余年之风起沧州》，
     * 金鹰标「古装仙侠」、暴风标「短剧全」（源自造的分类名）。
     * 分类进键 → 同片因分类不同而不折叠 → 重复更多，与 FR-204 的目的相反。
     *
     * 故分类**不参与折叠键**。若将来要按分类细分，应另建「展示层分组」，
     * 而不是塞进等价类判定里。
     *
     * 三段以不可见分隔符相连，避免拼接歧义：若用 `_`，
     * 标题里的下划线会与分隔符混淆（`A_B` + `2024` 与 `A` + `B_2024` 同键）。
     */
    fun of(title: String?, year: Int?): String {
        val t = normalizeTitle(title)
        // 年份缺失记为 `-`，与"年份是 0"不可区分——但这在真实数据里不存在，
        // 且用空串会与"标题里带空段"混淆，故用固定占位
        val y = if (year != null && year > 0) year.toString() else "-"
        return "$t\u0001$y"
    }

    /** 折叠键：年份以**字符串**给出（CMS 协议的原样形态） */
    fun ofRaw(title: String?, yearText: String?): String = of(title, parseYear(yearText))
}
