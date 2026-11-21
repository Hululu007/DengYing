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
