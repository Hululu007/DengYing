package com.dengying.player.domain.playback

/**
 * 全屏播放手势的方向仲裁（`docs/08` §2 约束 4、§6.1 P-04/P-05）。
 *
 * ## 为什么是纯函数、为什么放在 `domain`
 *
 * 判定规则只有"位移、起点、画面尺寸、亮度是否可用"四个输入，
 * 与 Android 无关，因此可单测——这正是 `docs/08` 验收项 A-06
 * （斜向拖动只触发一个动作）与 F-11/F-12（左右分区互不串台）
 * 唯一能被自动化验证的部分。
 *
 * 窗口亮度与系统音量的实际操作留在 `ui/platform/SystemAdjust.kt`，
 * 本文件不引用任何 Android 类型（S-1 约束）。
 *
 * ## 规则
 *
 * 1. 两个方向都没越过阈值 → [Action.Undecided]，交回 UI 继续累计。
 * 2. 横向分量 ≥ 竖向分量 → [Action.Seek]。**相等时归 seek**：
 *    横向是主操作，同等位移下优先给 seek 更符合预期。
 * 3. 竖向主导 → 按**起点 x** 分区：左半屏亮度、右半屏音量。
 *    左半屏在亮度不可用时（拿不到 Activity）→ [Action.Discard]：
 *    竖向意图已明确，不能退回 seek，也不能当成点击。
 * 4. 判定只做一次。调用方越过阈值后即锁定结果，本函数不参与后续位移。
 */
object GestureRules {

    /** 手势判定结果 */
    enum class Action {
        /** 位移不足，尚未定模 */
        Undecided,

        /** 横向滑动 → seek */
        Seek,

        /** 竖向滑动 + 起点在左半屏 → 亮度 */
        Brightness,

        /** 竖向滑动 + 起点在右半屏 → 音量 */
        Volume,

        /** 竖向意图明确但该侧不可调 → 本次手势作废 */
        Discard,
    }

    /**
     * 判定阈值（像素）。
     *
     * 取 20：够小以免"想滑动却判成点击"，够大以免轻微抖动触发。
     * 与既有滑动 seek 的取值一致，未重新标定（`docs/08 §7.2` 已记为待真机确认项）。
     */
    const val THRESHOLD_PX: Float = 20f

    /**
     * 判定一次手势的动作。
     *
     * @param totalX 累计横向位移（像素，右为正）
     * @param totalY 累计竖向位移（像素，下为正）
     * @param startX 手势起点的横坐标（像素）
     * @param viewWidth 播放画面的宽度（像素）
     * @param brightnessAvailable 亮度是否可调（拿不到 Activity 时为 false）
     */
    fun decide(
        totalX: Float,
        totalY: Float,
        startX: Float,
        viewWidth: Float,
        brightnessAvailable: Boolean,
        threshold: Float = THRESHOLD_PX,
    ): Action {
        val ax = kotlin.math.abs(totalX)
        val ay = kotlin.math.abs(totalY)
        if (ax <= threshold && ay <= threshold) return Action.Undecided
        if (ax >= ay) return Action.Seek
        return if (startX <= viewWidth / 2f) {
            if (brightnessAvailable) Action.Brightness else Action.Discard
        } else {
            Action.Volume
        }
    }
}
