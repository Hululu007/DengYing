package com.dengying.player.ui.platform

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
 */
@UnstableApi
@Composable
actual fun VideoSurface(
    engine: PlayerEngine?,
    modifier: Modifier,
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
                    val pv = PlayerView(c).apply {
                        useController = false // 控制层全部自绘
                        // 三种宽高比差异大：FIT 保证不裁切（黑边好过切掉字幕）
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        // factory 阶段直接绑：key 保证重建发生在引擎就绪后
                        player = engine?.viewHandle() as? androidx.media3.common.Player
                    }
                    onBind(pv.player != null)
                    addView(pv)
                }
            },
            update = { container ->
                val pv = container.getChildAt(0) as PlayerView
                // 引擎可能在换集/换档后被重建，故每次重组都重绑。
                // viewHandle() 返回 Any?，这里转型是安全边界：只有 Media3Engine 会返回非空。
                val player = engine?.viewHandle() as? androidx.media3.common.Player
                if (pv.player !== player) {
                    pv.player = player
                    onBind(player != null)
                }
            },
            modifier = modifier,
        )
    }
}
