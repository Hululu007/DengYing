package com.dengying.player.ui.pages

/**
 * 发现页播放规则的纯函数集合。
 *
 * 抽出来是为了给「误报看不了 / 海报残影 / 换集残帧 / 进详情交接 / 缓冲转圈」
 * 这些门控条件一个可单测的落点——它们都在 [IndexScreen] 的 Compose 组合里，
 * 组合本身不好在 JVM 单测里跑；规则本身必须可回归。
 *
 * 只放判断、不放状态：状态仍由页面/VM 持有。
 */
internal object DiscoverPlaybackRules {

    /**
     * 中心卡媒体就绪后是否还画海报。
     *
     * FIT 缩放的 letterbox 是透明的，海报留在底层会从黑边露出（用户实测
     * 「视频下半截还是旧海报」）。就绪后只留黑底 + VideoSurface。
     */
    fun showPoster(isCenter: Boolean, mediaReady: Boolean): Boolean =
        !isCenter || !mediaReady

    /**
     * 中心卡失败项是否值得再解析一次。
     *
     * 只对中心项、且确实解析失败（url 为空）时重试；[attempts] 限制总次数，
     * 避免临时失败被永久缓存后无限重试，也避免一次网络抖动就让卡片
     * 从此钉死在「看不了」上。
     */
    fun shouldRetry(isCenter: Boolean, failed: Boolean, attempts: Int): Boolean =
        isCenter && failed && attempts < MAX_CENTER_RETRY

    /**
     * 是否该给用户看「这部暂时看不了」。
     *
     * 必须等重试全部用尽才认输：真机上出现过「发现页报看不了、
     * 进详情等一下又能播」——那是解析链临时失败，不是片真的没源。
     * 重试期间继续转圈，比先报错再自愈更不打扰人。
     */
    fun showFailure(failed: Boolean, attempts: Int): Boolean =
        failed && attempts >= MAX_CENTER_RETRY

    /**
     * 中心卡能不能报「看不了」（DVP §10）。
     *
     * 三个条件缺一不可：解析确实没拿到地址、重试用尽、**全部源跑完仍无可播首集**。
     * 最后一条是新增的：跨源搜索只有 6s 预算，快照里没有可播候选往往只是慢源还没
     * 回来——拿快照当结论就会重演「发现页报看不了、进详情等一下又能播」。
     */
    fun finalFailure(emptyUrl: Boolean, attempts: Int, allSourcesDone: Boolean): Boolean =
        showFailure(failed = emptyUrl && allSourcesDone, attempts = attempts)

    /** 中心卡解析失败后的总尝试次数（含首次批量解析）。 */
    const val MAX_CENTER_RETRY = 3

    /**
     * 发现页转圈是否可见。
     *
     * 解析中和引擎缓冲都要转圈；失败态优先显示「看不了」，不转圈。
     */
    fun showSpinner(resolving: Boolean, buffering: Boolean, failed: Boolean): Boolean =
        (resolving || buffering) && !failed

    /**
     * 从发现页进入详情时的续播位置。
     *
     * 只有视频真的在播（mediaReady）才带位置；否则 -1（详情页按常规流程走）。
     */
    fun handoffStartAtMs(mediaReady: Boolean, positionMs: Long): Long =
        if (mediaReady && positionMs >= 0L) positionMs else -1L
}
