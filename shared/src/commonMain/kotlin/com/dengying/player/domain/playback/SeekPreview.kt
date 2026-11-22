package com.dengying.player.domain.playback

/**
 * 滑动 seek 的**纯计算**部分（FR-708 / `docs/08` P-02）。
 *
 * ## 为什么从 `PlayerViewModel` 里搬出来
 *
 * 真机复现 `docs/08 §6.7`：控制层自动隐藏后横向滑动屏幕，播放器确实 seek 了
 * （探针 `seek-start → engine-seek → seek-commit` 齐全），但**画面上一帧反馈都没有**。
 * 修法要把预览浮层从控制层里搬出来并补一条进度条动画，
 * 而"滑多远、到哪个位置、显示什么差值"这三件事与 Android 无关，可脱离设备验证。
 *
 * 放在 `domain` 的第二个理由：原实现把"起点"存在 ViewModel 的私有字段里，
 * 浮层却拿 `state.curTime` 算差值——播放进度每 1 秒推进一次，
 * **同一次按压内的差值会自己缩水、方向甚至会翻**。
 * 现在起点进 [UiState] 并作为参数显式传递，"用哪个起点"由调用方决定，可断言。
 */
object SeekPreview {

    /**
     * 引擎位置与待生效 seek 目标的容差（毫秒）。
     *
     * seek 提交后播放器要重新缓冲，这段时间 `currentPosition` 可能仍停在旧位置。
     * 若照抄引擎值，界面会**先跳回旧位置、再跳到新位置**——用户看到的就是"跳变"。
     * 差距超过本容差时不采信引擎位置，界面保持目标位置。
     */
    const val ADOPT_TOLERANCE_MS: Long = 1500L

    /**
     * 滑动位移 → 目标位置。
     *
     * 口径不变（`docs/06` D4 定稿）：**滑过一个屏幕宽度 = 走完整片时长**。
     * 负位移向左退，正位移向右进，结果夹在 `0..totalMs`。
     *
     * @param fromMs 本次手势的起点（按下瞬间的位置），**不是**当前播放位置
     * @param deltaX 累计横向位移（像素，右为正）
     * @param viewWidth 播放画面宽度（像素）
     * @param totalMs 总时长；未知（≤0）时保持起点不动
     */
    fun targetOf(fromMs: Long, deltaX: Float, viewWidth: Float, totalMs: Long): Long {
        if (totalMs <= 0L) return fromMs.coerceAtLeast(0L)
        if (viewWidth <= 0f) return fromMs.coerceIn(0L, totalMs)
        val ratio = (deltaX / viewWidth).toDouble()
        val raw = fromMs + (totalMs.toDouble() * ratio).toLong()
        return raw.coerceIn(0L, totalMs)
    }

    /** 方向：目标在起点右侧为快进。相等时归快进（与 [deltaLabel] 的空串同源，不矛盾）。 */
    fun isForward(fromMs: Long, targetMs: Long): Boolean = targetMs >= fromMs

    /**
     * 差值文案：`+1:20` / `-0:35`。
     *
     * 不足 1 秒返回空串——显示 `+0:00` 只会让浮层看起来在抖。
     * 用**手势起点**算，不用当前播放位置算。
     */
    fun deltaLabel(fromMs: Long, targetMs: Long): String {
        val d = (targetMs - fromMs) / 1000
        if (d == 0L) return ""
        val sign = if (d > 0) "+" else "-"
        val a = kotlin.math.abs(d)
        val m = a / 60
        val s = a % 60
        return "$sign$m:${s.toString().padStart(2, '0')}"
    }

    /**
     * seek 提交后，是否该采信引擎报告的播放位置。
     *
     * @param engineMs 引擎此刻的 `currentPosition`
     * @param pendingTargetMs 刚提交的目标；0 表示没有待生效的 seek（一律采信）
     */
    fun shouldAdoptEnginePosition(
        engineMs: Long,
        pendingTargetMs: Long,
        toleranceMs: Long = ADOPT_TOLERANCE_MS,
    ): Boolean {
        if (pendingTargetMs <= 0L) return true
        return kotlin.math.abs(engineMs - pendingTargetMs) <= toleranceMs
    }
}
