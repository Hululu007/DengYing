package com.dengying.player.playback

/**
 * 回前台自动续播的判定（DVP-PORT-PLAN-014 §3）。
 *
 * 旧实现里 `MainActivity.onStart` 只要发现引擎就 `play()`，于是「用户自己暂停后锁屏再亮屏」
 * 也会自己响起来。规则收紧为：只有**退后台时确实在播**、且仍有播放表面在组合里，
 * 才允许 onStart 恢复；其余情况保持暂停。
 *
 * 抽成纯函数是为了能在 JVM 上验证，不必拉 Activity 生命周期。
 */
fun shouldResumeAfterStop(wasPlayingBeforeStop: Boolean, surfaceCount: Int): Boolean =
    wasPlayingBeforeStop && surfaceCount > 0

/**
 * 退后台的恢复计划：主引擎与发现页预览引擎**分别**判定（DVP §12）。
 *
 * 发现页预览走独立实例（`previewInstance`），旧实现 onStop 只 pause 主引擎，于是
 * 「在发现页看中心卡预览时退到桌面」声音继续播。两个引擎各自记录是否本来在播，
 * 回前台各自恢复；表面计数归零时按 [shouldResumeAfterStop] 一律不恢复。
 */
data class ForegroundResumePlan(val resumeMain: Boolean, val resumePreview: Boolean) {
    /** 有任一引擎需要恢复 */
    val any: Boolean get() = resumeMain || resumePreview
}

/** 见 [ForegroundResumePlan]；`onStop` 用当次状态算计划，`onStart` 用计划 + 当次表面数再确认。 */
fun planForegroundResume(
    mainWasPlaying: Boolean,
    previewWasPlaying: Boolean,
    surfaceCount: Int,
): ForegroundResumePlan = ForegroundResumePlan(
    resumeMain = shouldResumeAfterStop(mainWasPlaying, surfaceCount),
    resumePreview = shouldResumeAfterStop(previewWasPlaying, surfaceCount),
)
