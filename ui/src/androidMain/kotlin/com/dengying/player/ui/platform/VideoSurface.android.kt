package com.dengying.player.ui.platform

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dengying.player.player.PlayerEngine
import com.dengying.player.ui.R

/**
 * Android actual：`AndroidView(PlayerView)`。
 *
 * 为什么不用 media3-ui-compose 的 `PlayerSurface`：这里需要
 * `AspectRatioFrameLayout` 的 `resizeMode`（fit/crop/fill）与字幕层，
 * 且必须 `RESIZE_MODE_FIT` + 黑边，传统 `PlayerView` 最省事且行为确定。
 *
 * `key(engine)` 的理由见 expect 声明处的契约第 1 条 —— 引擎一变整个视图重建，
 * 让新 `PlayerView` 在 `factory` 阶段就拿到已就绪的 player，
 * 绑定与 surface 初始化在同一步完成，不存在中间态。
 *
 * 渲染面用 TextureView（布局资源 `dy_player_view.xml` 的 surface_type）：
 * 它参与 View/Compose 的正常图层合成，滚动时严格跟随卡片框；默认的
 * SurfaceView 是独立窗口，LazyColumn 滚动时画面位置更新滞后一拍，
 * 真机表现为发现页中心卡里视频上下漂移、底部露黑边。
 * 代价是多一次 GPU 合成；本项目是本地 SDR 源，不涉及 secure/HDR 限制。
 */
/**
 * 每个播放器当前有几个渲染面在用它。
 *
 * 导航转场期间新旧两个 PlayerView 会短暂共存；旧视图 dispose 时不能无条件
 * `player = null`，否则会把新视图刚接上的画面输出清掉（黑屏但有声音）。
 *
 * 注意：**不要**用「延迟后解绑再重绑」来补救——`player = null` 会真的拆掉
 * 画面输出，Media3 随即回到缓冲态，用户看到的就是「进详情先黑一下再缓冲」。
 * 画面能连续的前提是：同一个引擎、同一条媒体、转场期间不解绑。
 */
private val boundRefs = HashMap<Any, Int>()

@UnstableApi
@Composable
actual fun VideoSurface(
    engine: PlayerEngine?,
    modifier: Modifier,
    zoomMode: VideoZoomMode,
    onBind: (bound: Boolean) -> Unit,
) {
    key(engine) {
        AndroidView(
            factory = { c ->
                FrameLayout(c).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    val pv = LayoutInflater.from(c)
                        .inflate(R.layout.dy_player_view, this, false) as PlayerView
                    pv.apply {
                        useController = false // 控制层全部自绘
                        // 三种宽高比差异大：FIT 保证不裁切（黑边好过切掉字幕）
                        resizeMode = when (zoomMode) {
                            VideoZoomMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            VideoZoomMode.COVER -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            VideoZoomMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        // 透明快门：解绑（player=null）时露出底下的海报，而不是一块纯黑。
                        // 换片残帧靠「引擎置 null → 解绑」清掉，不靠快门涂黑。
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        // factory 阶段直接绑：key 保证重建发生在引擎就绪后
                        val bound = engine?.viewHandle() as? androidx.media3.common.Player
                        player = bound
                        if (bound != null) boundRefs[bound] = (boundRefs[bound] ?: 0) + 1
                        com.dengying.player.player.PlaybackProbe.note(
                            "surface-create", "bound=${bound != null} refs=${bound?.let { boundRefs[it] }} eng=${engine?.hashCode()}",
                        )
                    }
                    onBind(pv.player != null)
                    addView(pv)
                }
            },
            update = { container ->
                val pv = container.getChildAt(0) as PlayerView
                // 引擎可能在换集/换档后被重建，故每次重组都重绑。
                // viewHandle() 返回 Any?，这里转型是安全边界：只有 Media3Engine 会返回非空。
                pv.resizeMode = when (zoomMode) {
                    VideoZoomMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    VideoZoomMode.COVER -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    VideoZoomMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                val player = engine?.viewHandle() as? androidx.media3.common.Player
                if (pv.player !== player) {
                    pv.player = player
                    if (player != null) boundRefs[player] = (boundRefs[player] ?: 0) + 1
                    onBind(player != null)
                }
            },
            modifier = modifier,
            onRelease = { container ->
                // 离开组合时摘掉 player：否则旧 PlayerView 仍占着引擎，
                // 新中心卡的 PlayerView 绑不上，或两者抢画面。
                //
                // 【导航转场会同时存在两个渲染面】从发现页进详情时，旧页面的
                // AndroidView 在转场结束才 dispose，而新页面已经绑好同一个播放器。
                // 此时若无条件 `player = null`，会把**新页面刚接上的画面输出清掉**——
                // 表现为「有时间轴在走、有声音，但画面全黑」。所以只在没有其它
                // 渲染面还在用这个播放器时才解绑。
                val pv = container.getChildAt(0) as? PlayerView
                val p = pv?.player
                com.dengying.player.player.PlaybackProbe.note(
                    "surface-release", "refs=${p?.let { boundRefs[it] }}",
                )
                if (p != null) {
                    val left = (boundRefs[p] ?: 1) - 1
                    if (left > 0) {
                        boundRefs[p] = left
                    } else {
                        boundRefs.remove(p)
                        pv.player = null
                    }
                }
            },
        )
    }
}
