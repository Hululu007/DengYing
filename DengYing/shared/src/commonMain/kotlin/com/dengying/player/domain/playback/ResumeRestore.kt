package com.dengying.player.domain.playback

/**
 * 「是否用历史续播点覆盖当前进度」的判定 —— 纯函数，不依赖 Android。
 *
 * ## 为什么独立出来
 *
 * 真机复现（探针 `files/playback.log`）：用户手动 seek 到 54:31，
 * 画面却在 `STATE_READY` 之后被拉回上次的位置：
 *
 * ```
 * 492 [engine-seek] target=3245598        ← 用户手动 seek
 * 494 [STATE_READY]
 * 497 [engine-ready]
 * 498 [resume-restore] ep=0 pos=3271638   ← 续播恢复把位置覆盖了
 * 499 [engine-seek] target=3271638
 * ```
 *
 * 原实现把四道守卫写成 `restorePosition()` 里的连续 `return`，混在
 * 协程与磁盘读取之间。判定本身与 Android 无关，一旦和 IO 缠在一起，
 * 就只能靠"跑一遍真机看日志"来确认，而真机观察又受网络与版式位移干扰。
 * 搬出来之后，四个早退分支都能被断言。
 *
 * ## 一处必须记住的语义
 *
 * 判据不是"历史位置是多少"，而是「本次进入是否被显式指定了起播位置」。
 * 内嵌播放切全屏时，调用方带着"用户刚刚看到的位置"重进播放页；
 * 那时历史记录里的位置是旧的，用它覆盖等于把用户弹回去。
 */
object ResumeRestore {

    /** 小于该值视为从头播放，不做无意义的 seek（用户从片头进入） */
    const val HEAD_GUARD_MS: Long = 3_000L

    /** 第一阶段结论：要不要读历史续播点 */
    data class Load(val shouldLoad: Boolean, val reason: String)

    /** 第二阶段结论：拿到了历史位置，要不要真的 seek */
    data class Apply(val targetMs: Long, val shouldSeek: Boolean, val reason: String)

    /**
     * 第一阶段 —— 是否值得去磁盘读续播点。
     *
     * 读盘是 IO，四个早退条件都不依赖它，所以先判掉再读。
     *
     * @param startAtMs 路由显式指定的起播位置（0 = 未指定）
     * @param vodId 影片 id（0 = 非影片入口，如调试直达）
     * @param source 采集源 id
     * @param alreadyRequested 本次会话是否已经恢复过（防止重复 seek）
     */
    fun shouldLoad(
        startAtMs: Long,
        vodId: Long,
        source: String,
        alreadyRequested: Boolean,
    ): Load = when {
        // 显式起播位置优先于历史记录：调用方比历史更知道用户此刻看的是哪一帧
        startAtMs > 0L -> Load(false, "explicit-start")
        vodId == 0L || source.isEmpty() -> Load(false, "no-identity")
        alreadyRequested -> Load(false, "already-requested")
        else -> Load(true, "load")
    }

    /**
     * 第二阶段 —— 历史的续播位置是否该生效。
     *
     * @param storedMs 读到的历史位置；来源缺失时为 0
     * @param manualSeekOccurred 会话里用户是否已手动 seek 过
     */
    fun apply(
        storedMs: Long,
        manualSeekOccurred: Boolean,
        headGuardMs: Long = HEAD_GUARD_MS,
    ): Apply = when {
        // 用户已经动手拖过/滑过了：他的意图比历史记录新，不能覆盖
        manualSeekOccurred -> Apply(storedMs, false, "manual-seek")
        storedMs > headGuardMs -> Apply(storedMs, true, "restore")
        else -> Apply(storedMs, false, "near-start")
    }
}
