package com.dengying.player.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Liquid Glass 背景采样（2026-11-08 二次改版）。
 *
 * 【第一版为什么不够】v1 玻璃 = 半透明填充 + 细边 + 柔影，只能"透出底色"，
 * 做不出主流系统（Apple Liquid Glass / HyperOS / ColorOS 模糊栈）那种
 * 「背后内容实时磨砂流动」——因为 Compose 的 `Modifier.blur` 只能把**自己**
 * 糊掉，没有"采样背后"的原语。
 *
 * 【原理：先录再采】整棵内容树先被 [glassBackdrop] **录制**进一张
 * `GraphicsLayer`（displaylist，每帧一次，GPU 侧合成不重新 measure/layout）；
 * 每个 [GlassSurface] 再把这张图层**按自身位置反向平移**地画进自己的边界，
 * 套上 `BlurEffect` —— 内容就在玻璃背后"活"了。这正是 Haze 库的核心手法，
 * ~120 行自研实现，不引第三方依赖（AGENTS.md 约束）。
 *
 * 【平台边界】`RenderEffect` 在 Android 12+（minSdk 31 ✓）走硬件模糊；
 * iOS/desktop 目标上若平台未实现 RenderEffect 则自动退化为无模糊的
 * 半透明玻璃（v1 观感），不崩、不需要分支代码。
 *
 * 【成本】录制每帧一次；每个玻璃件多做一次"图层重合成 + 区域模糊"，
 * 成本 ∝ 玻璃件面积。所以**只有导航件**（顶栏/Dock/播放器控制层）开
 * liquid，海报卡保持 v1 轻填充——全屏几十张卡都采样会把帧率拖穿，
 * Apple 的 Liquid Glass 也只给 chrome 不给内容卡。
 */
class GlassBackdropState {
    /** 内容树的录制图层；[glassBackdrop] 挂上后每帧更新 */
    internal var layer: GraphicsLayer? = null

    /** 采样根节点的布局坐标（玻璃件用 positionInRoot 差值做反向平移） */
    internal var origin: LayoutCoordinates? = null
}

/** 无 backdrop 时为 null：GlassSurface 自动退化为 v1 填充玻璃（预览/测试安全） */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdropState?> { null }

/**
 * 内容根采样修饰符：包住 NavHost 所在的 Box。
 * 录制与正常绘制同帧完成——先把整棵子树画进图层，再把图层画上屏。
 */
fun Modifier.glassBackdrop(state: GlassBackdropState): Modifier = composed {
    val layer = rememberGraphicsLayer()
    state.layer = layer
    this
        .onGloballyPositioned { state.origin = it }
        .drawWithContent {
            // record() 的 ContentDrawScope 形态：默认参数继承当前绘制作用域的
            // density/layoutDirection/size，块内把"正常内容"录进图层，再照常上屏
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
}
