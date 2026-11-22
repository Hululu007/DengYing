package com.dengying.player.ui.pages

/**
 * 发现页「视频画面平移进详情页」的一次性几何交接。
 *
 * ## 为什么不是普通的共享元素转场
 *
 * 画面是 Android 的 `TextureView`（原生 View），Compose 的
 * `SharedTransitionLayout` 无法对它做形变——原生视图不跟随 Compose 的
 * 变换矩阵，动画期间会「停在原地不动」，看上去比硬切更糟。
 *
 * 可行且代价最小的近似：两处播放窗**几何同为 16:9**
 * （发现页卡 `aspectRatio(16/9)`，详情页内嵌位也是 `fillMaxWidth + 16/9`），
 * 因此一次**等比缩放 + 平移**就能把两者精确对上。详情页拿到卡片的屏幕矩形后，
 * 用 `graphicsLayer` 从「卡片矩形」动画到「自己的布局矩形」，画面本体全程
 * 是同一个引擎、同一段媒体（交接不重载），视觉上就是「画面从卡片滑到详情位」。
 *
 * 只交接一次：详情页消费后清空，避免返回时误用陈旧矩形。
 */
internal object DiscoverHero {

    /** 点击瞬间卡片的窗口坐标（px）：left, top, width, height。 */
    private var rect: IntArray? = null

    fun record(left: Int, top: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        rect = intArrayOf(left, top, width, height)
    }

    /** 取走交接矩形（取一次即清空）。 */
    fun take(): IntArray? = rect.also { rect = null }

    /** 没有交接（非发现页进入）时清掉，防止旧矩形串到别的入口。 */
    fun clear() {
        rect = null
        DiscoverHandoff.clear()
    }
}

/**
 * 发现页 → 详情页的**地址交接**（一次性）。
 *
 * ## 为什么需要它
 *
 * 只交几何还不够：详情页仍会按自己的链路去解析首集地址，解析期间
 * 播放位是空的（「正在解析播放地址…」），画面平移过去也没有画面可看。
 *
 * 卡片里正在播的那条地址就是目标地址——把它一起交过去，详情页直接
 * 当作首集预解析结果，起播时同媒体幂等路径接管预览引擎：不解链、
 * 不重建 MediaSource，画面从头到尾是同一段流。
 *
 * 带上 `vodId`/`source` 是为了**串号保护**：用户点了发现页卡片又改从
 * 首页进别的片时，交接内容必须对不上、自动作废，不能把这条地址
 * 当成另一部片的播放地址。
 */
internal object DiscoverHandoff {

    /** 一次交接的内容。url 为空表示只是几何交接，没有地址可用。 */
    internal data class Payload(
        val url: String,
        val headers: String,
        val positionMs: Long,
        val vodId: Long,
        val source: String,
    )

    private var payload: Payload? = null

    fun record(payload: Payload) {
        if (payload.url.isBlank()) return
        this.payload = payload
    }

    /** 取走交接内容（取一次即清空，避免返回后重复使用）。 */
    fun take(): Payload? = payload.also { payload = null }

    fun clear() {
        payload = null
    }
}
