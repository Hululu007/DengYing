package com.dengying.player.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import com.dengying.player.player.PlayerEngine
import com.dengying.player.ui.player.IosPlayerEngineHost
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayerLayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIColor

/**
 * iOS 播放画面承载（KMP P5-6 落真身）。
 *
 * ## 做法
 *
 * 用 CMP 的 `UIKitView` 把一个 [UIView] 塞进 Compose 布局，再把
 * [AVPlayerLayer] 挂到这个 UIView 的 `layer` 上。画面尺寸跟随 UIView 的 bounds，
 * 由 `AVPlayerLayer.videoGravity = resizeAspect`（引擎侧已设）保证等比缩放不裁切。
 *
 * 为什么不用 `ComposeUIViewController` 里直接加图层：`:ui` 拿不到宿主
 * `UIViewController`，`UIKitView` 是 CMP 提供的、唯一稳定的「Compose 里插原生视图」入口。
 *
 * ## 契约照守（commonMain Kdoc 五条）
 *
 * 1. **引擎变化必须整体重建视图** —— 用 `key(engine)` 包住整个 [UIKitView]。
 *    Android 侧的黑屏根因就是「工厂先于引擎」导致旧视图留着旧 Surface；这里
 *    重建而不是改绑，同一理由同一处理。
 * 2. 不接管触摸：`UIKitView` 沿用默认（不吞事件）。
 * 3. 不画缓冲指示：缓冲态由调用方浮层表达，本文件不碰。
 * 4. FIT 缩放：`videoGravity = resizeAspect`。
 * 5. `viewHandle()` 转型失败按 `null` 处理，**永不抛异常**。
 *
 * ## 生命周期
 *
 * `DisposableEffect` 在离开组合时摘层并通知 host `surfaceInactive()`；host 在
 * 计数归零时只暂停、不释放（与 Android `PlaybackEngineHolder` 同语义）。
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(
    engine: PlayerEngine?,
    modifier: Modifier,
    onBind: (bound: Boolean) -> Unit,
) {
    // 引擎变化 ⇒ 整块重建（契约第 1 条）
    key(engine) {
        val layer = remember(engine) { engine?.viewHandle() as? AVPlayerLayer }

        if (layer == null) {
            // 没有可挂的层：保持黑底，且**不虚报** onBind(true)，
            // 否则排障时「绑上了但没有画面」与「压根没绑」无法区分。
            BlackCanvas(modifier)
            return@key
        }

        UIKitView(
            modifier = modifier,
            factory = {
                val view = UIView().apply {
                    backgroundColor = UIColor.blackColor
                    autoresizingMask =
                        UIViewAutoresizingFlexibleWidth or
                            UIViewAutoresizingFlexibleHeight
                }
                view.layer.addSublayer(layer)
                layer.frame = view.bounds
                // 图层不参与隐式动画：进全屏/旋转时否则会看到一段「拉伸动画」
                CATransaction.begin()
                CATransaction.setValue(true, kCATransactionDisableActions)
                CATransaction.commit()
                view
            },
            update = { view ->
                CATransaction.begin()
                CATransaction.setValue(true, kCATransactionDisableActions)
                layer.frame = view.bounds
                CATransaction.commit()
            },
        )

        DisposableEffect(layer) {
            IosPlayerEngineHost.surfaceActive()
            onBind(true)
            onDispose {
                // 不在这里 release 引擎：host 计数归零时只暂停（契约）
                IosPlayerEngineHost.surfaceInactive()
                onBind(false)
                layer.removeFromSuperlayer()
            }
        }
    }
}

/** 无引擎时的黑底占满，与桌面 actual 同形 */
@Composable
private fun BlackCanvas(modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black))
}
