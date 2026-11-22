package com.dengying.player.domain.ui

/**
 * 点击热区达标判定（NFR-011）—— 纯函数，不依赖 Android
 *
 * 需求：可点击目标不小于 **44dp 见方**；关键操作有明确禁用态。
 *
 * 44dp 的来历：触摸目标的下限由人体工程学决定（指尖接触面约 8-10mm），
 * 低于此值的按钮在移动端会频繁误触。这不是"看起来小"的审美问题，
 * 而是可用性问题。
 *
 * ## 本对象解决的两件事
 *
 * 1. **判定**：给一个声明的宽高，判断是否达标。之所以要看**两个维度**而不是只看着色区域——
 *    视觉尺寸与热区可以不同：一个 30dp 高的文字按钮若外层容器有 8dp 垂直内边距，
 *    实际热区是 46dp，已经达标。所以判定输入是**热区**而不是视觉尺寸。
 * 2. **补齐**：算出把热区补到 44dp 所需的外扩量，供实现直接取值，
 *    避免各页面各算一遍。
 *
 * 【与鸿蒙版的差异】单位由 vp 改称 dp。两者是同一个概念（密度无关像素），
 * Compose 的 `Modifier.size` 也以 dp 为单位，数值语义完全一致。
 */
object TouchTarget {

    /** 最小热区边长（dp） */
    const val MIN_TOUCH_DP = 44f

    data class Target(
        val name: String,
        val where: String,
        /** 声明热区宽（dp） */
        val w: Float,
        /** 声明热区高（dp） */
        val h: Float,
        val hasDisabledState: Boolean,
    )

    data class Verdict(
        val name: String,
        val where: String,
        val ok: Boolean,
        /** 未达标时的说明；达标时为空串 */
        val reason: String,
    )

    /**
     * 判定单个目标是否达标。
     *
     * 规则：宽与高**都**不得小于 44dp。仅一个维度达标不算——
     * 一条 44×20dp 的横条在垂直方向仍然很难点中。
     */
    fun checkTarget(t: Target): Verdict {
        val bad = ArrayList<String>(2)
        if (t.w < MIN_TOUCH_DP) bad += "宽 ${t.w}dp"
        if (t.h < MIN_TOUCH_DP) bad += "高 ${t.h}dp"
        if (bad.isEmpty()) return Verdict(t.name, t.where, true, "")
        return Verdict(t.name, t.where, false, "${bad.joinToString("、")} < ${MIN_TOUCH_DP}dp")
    }

    /** 批量核对；返回未达标项（达标的被滤掉，报告只关心问题） */
    fun failingTargets(list: List<Target>): List<Verdict> =
        list.map { checkTarget(it) }.filter { !it.ok }

    /**
     * 由「视觉尺寸 + 内边距」求实际热区。
     *
     * 用途：判断能否靠加内边距达标，而不必放大视觉元素——这是保住版式的常见手段。
     * 两侧各加一次内边距，故总量是 2 倍。
     */
    fun hitSize(visual: Float, padding: Float): Float = visual + padding * 2

    /**
     * 使热区达标所需的最小内边距（不足则补，已达标返回 0）。
     * 供实现时直接取值，避免各页面各算一遍。
     *
     * 向上取整到 0.5dp，避免因小数导致仍差一点点。
     */
    fun paddingToReach44(visual: Float): Float {
        if (visual >= MIN_TOUCH_DP) return 0f
        val need = (MIN_TOUCH_DP - visual) / 2
        return kotlin.math.ceil(need * 2) / 2
    }
}
