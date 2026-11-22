package com.dengying.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import com.dengying.player.ui.theme.Palette

/**
 * 页面底层「透射源」——玻璃要透，背后必须先有东西可透。
 *
 * 【为什么需要它】v3 之前每个页面最外层都是 `.background(Palette.AppBg)`，
 * 而 `appBg` 是**纯不透明单色**（浅色 `#F4F4F1` / 深色 `#111114`）。玻璃件
 * 采样到的「背后」永远只有这一片死色——模糊与折射再怎么调，观感都只是
 * 灰白磨砂片（用户实测否掉）。所以把纯色底换成「底色 + 四团柔光」：
 * 品牌靛蓝、暖金、青绿、玫红各自以大半径径向渐变铺开，位置按屏幕比例
 * 固定（不随滚动漂移，避免玻璃里的颜色跳舞）。
 *
 * 【为什么采样层只录渐变，不录内容树】
 * 之前 `Modifier.glassBackdrop` 挂在 NavHost 外层，`layer.record { drawContent() }`
 * 把**整棵内容树**录进图层——玻璃件自己也在里面，玻璃再 `drawLayer(同一个图层)`
 * 就是自引用，RenderThread 在 `RenderNode::prepareTreeImpl` 里无限递归、
 * 栈溢出崩溃（`SIGSEGV` / `stack pointer is not in a rw map` / 512 frames）。
 * 把采样层改成玻璃件的**兄弟**层能解决，但那要把 8 个页面都拆成
 * content/overlay 两层，改动面大、布局回归风险高。
 *
 * 这里换一条等价且零结构改动的路：**图层里只录这层渐变本身**。
 * 渐变不含任何玻璃件，天然不可能递归；玻璃采样到的是「模糊 + 折射 + 提饱和
 * 后的柔光色雾」，透射感来自真实色相与明暗，而不是靠 tint 堆白。
 * 代价是海报滚动时不会从玻璃背后流过——柔光底是静态的，这也是手机系统
 * 壁纸式玻璃的观感，可接受。
 *
 * 【为什么要每次绘制都写 state.layer】采样层按页各建一份。若在组合期赋值，
 * 从 B 页返回 A 页时 A 不再重组，state 会停留在已释放的 B 图层上。改成在
 * **绘制期**赋值：谁这一帧在画，谁就是采样源；页面被 pop 掉后，下一帧下面
 * 那页重新绘制即自动接管。玻璃件的绘制在父节点之后，同帧读到的必然是本页图层。
 *
 * 用法：页面根 `Box(Modifier.fillMaxSize().glassPageBackdrop())`，
 * 替换原来的 `.background(Palette.AppBg)`。
 */
@Composable
fun Modifier.glassPageBackdrop(): Modifier {
    val glow = rememberPageGlow()
    val backdrop = LocalGlassBackdrop.current
    val layer = rememberGraphicsLayer()

    return this
        .onGloballyPositioned { backdrop?.origin = it }
        .drawWithContent {
            // ① 只把渐变录进图层（无玻璃件 → 无自引用风险）
            layer.record { drawPageGlow(glow) }
            // ② 本帧的采样源 = 本页
            backdrop?.layer = layer
            // ③ 渐变上屏，内容照常画在上面
            drawLayer(layer)
            drawContent()
        }
}

/**
 * 一页柔光底的全部色值。抽成数据类是因为 [glassPageBackdrop] 与 [GlassPage]
 * 两个入口都要用同一套配色——此前两份内联计算各自维护，改一处忘另一处就会
 * 出现「首页玻璃和登录页玻璃不是同一个色」。
 */
private class PageGlow(
    val bg: Color,
    /** 主色光（左上）：品牌靛蓝，最大的一团，定调 */
    val primary: Color,
    /** 副色光（右上）：暖金，与主色形成冷暖对位 */
    val accent: Color,
    /** 第三色光（左下）：青绿，压住下半屏避免整体发灰 */
    val tertiary: Color,
    /** 第四色光（右下）：玫红，给底部 Dock 背后一点暖 */
    val quaternary: Color,
    /** 顶部薄白：浅色主题用来提亮顶栏区域 */
    val topAir: Color,
    /** 底部压暗：把视觉重心压回 Dock 一侧，增加纵深 */
    val bottomShade: Color,
)

/**
 * 配色定稿（v1.3.1，对齐 vivo OriginOS 5）。
 *
 * v1.3.0 用了四支粉彩（淡紫 / 杏 / 薄荷 / 樱粉）冷暖对位，屏幕上是一块块
 * 可辨的色斑，用户反馈「有点 low」。OriginOS 的底色恰恰相反：
 *
 * - 浅色：近白底（`#F6F6F6` 一带），只有极淡的冷灰蓝从顶部渗下来，
 *   几乎看不出颜色，层次全靠玻璃件自己的明暗，不靠底色染色。
 * - 深色：纯黑底（`#000000`），不铺任何彩色光。深色下彩色光会把黑底
 *   染成紫雾，OriginOS 深色是干净的黑，层次靠卡片比底色亮一档来做。
 *
 * 【为什么浅色仍留一团极淡的光】纯平铺底色会让玻璃采样到一片死色，
 * 透射感消失（v1.2.8 的教训）。一团冷灰蓝、alpha 压到 0.10，
 * 只给玻璃里一点方向，肉眼几乎分辨不出是哪支颜色。
 */
@Composable
private fun rememberPageGlow(): PageGlow {
    val bg = Palette.AppBg
    // 深色判定跟着主题走：appBg 的亮度足够区分两套主题（0.90 vs 0.006）
    val dark = bg.luminance() < 0.4f
    return if (dark) {
        // OriginOS 深色：纯黑底，不铺彩色光。层次靠卡片比底色亮一档，不靠染色
        PageGlow(
            bg = Color(0xFF000000),
            primary = Color.Transparent,
            accent = Color.Transparent,
            tertiary = Color.Transparent,
            quaternary = Color.Transparent,
            topAir = Color.Transparent,
            bottomShade = Color.Transparent,
        )
    } else {
        // OriginOS 浅色：近白底 + 顶部极淡的冷灰蓝，几乎看不出颜色
        PageGlow(
            bg = Color(0xFFF6F6F6),
            primary = Color(0xFFD6DCE6).copy(alpha = 0.10f),
            accent = Color.Transparent,
            tertiary = Color.Transparent,
            quaternary = Color.Transparent,
            topAir = Color.White.copy(alpha = 0.40f),
            bottomShade = Color.Transparent,
        )
    }
}

/**
 * 当前组合是否处于 [GlassPage] 的**被采样内容层**内。
 *
 * 【为什么需要这个标记】内容层被整体录进采样图层。若内容层里还藏着一个
 * `liquid = true` 的玻璃件，它采样时读到的图层**包含它自己**，`drawLayer`
 * 层层嵌套 → RenderThread 栈溢出（实测 SIGSEGV / 512 帧）。这类件很难靠人工
 * 审查保证不漏（例如「我的」页右上设置钮就长在列表里），所以在采样子树里统
 * 一把 `liquid` 强制降级为静态玻璃：颜色仍从柔光底透上来，只是不做实时采样。
 */
val LocalGlassInSampledContent = compositionLocalOf { false }

/**
 * 页面骨架：**内容层**（被采样）+ **玻璃浮层**（去采样）。
 *
 * 【为什么必须分两层】透射玻璃要透出的是"背后真实内容"——海报、文字块、封面
 * 边缘。只把渐变录进采样层，玻璃里永远只有一片色雾（用户实测：滚动时海报从
 * 顶栏后面过，玻璃纹丝不动）。而把内容录进图层，玻璃件就**不能**再留在图层里：
 * 玻璃自身要 `drawLayer(采样层)`，若它也在被录制的子树内，就会录到自己 → 自
 * 引用 → RenderThread 栈溢出（SIGSEGV，512 帧，`stack pointer is not in a rw map`）。
 *
 * 所以固定拆成兄弟层：
 * ```
 * Box {
 *     Box(采样层) { content() }   // 只有内容，无玻璃
 *     overlay()                    // 顶栏 / Dock / 返回钮等玻璃件
 * }
 * ```
 *
 * 【约束】`content` 里不要再放 `liquid = true` 的 [GlassSurface]——那些件会
 * 采样包含自己的图层，同样触发自引用。内容区的卡片、输入框一律静态玻璃
 * （`liquid = false`，半透明填充 + 左上高光，页面柔光底照样透上来）。
 *
 * @param content 可滚动主体，被录入采样图层
 * @param overlay 悬浮玻璃件，采样 content 并实时磨砂
 */
@Composable
fun GlassPage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val glow = rememberPageGlow()
    val backdrop = LocalGlassBackdrop.current
    val layer = rememberGraphicsLayer()

    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { backdrop?.origin = it }
                .drawWithContent {
                    // ① 柔光底 + 真实内容一起录进采样图层
                    layer.record {
                        drawPageGlow(glow)
                        this@drawWithContent.drawContent()
                    }
                    // ② 本帧采样源 = 本页内容层（绘制期赋值，理由见 glassPageBackdrop）
                    backdrop?.layer = layer
                    // ③ 图层上屏；内容只经由图层画一次，不能重复 drawContent
                    drawLayer(layer)
                },
            content = {
                CompositionLocalProvider(LocalGlassInSampledContent provides true) {
                    content()
                }
            },
        )
        overlay()
    }
}

/**
 * 底色 + 四团柔光 + 顶部薄白 + 底部压暗。抽成独立函数，供录制块与预览共用。
 *
 * 【半径拉到 1.0~1.2 倍屏的理由】半径小于屏宽时，径向渐变的边界会落在屏幕
 * 之内——四团光各自有一圈可辨的「边」，叠起来就是四块圆形色斑。半径过屏后
 * 每团光在可见区域里只剩中心到中段的平滑过渡，四团互相吃边界，整体才是一片
 * 有方向的冷暖渐变（vivo 系统底色的做法）。
 */
private fun DrawScope.drawPageGlow(glow: PageGlow) {
    val w = size.width
    val h = size.height
    val maxDim = if (w > h) w else h

    drawRect(glow.bg)
    // ① 主色光：左上，唯一的一团色。其余三团在单色口径下是 Color.Transparent，跳过
    if (glow.primary != Color.Transparent) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow.primary, Color.Transparent),
                center = Offset(w * 0.10f, h * 0.02f),
                radius = maxDim * 1.05f,
            ),
        )
    }
    if (glow.accent != Color.Transparent) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow.accent, Color.Transparent),
                center = Offset(w * 0.98f, h * 0.20f),
                radius = maxDim * 1.00f,
            ),
        )
    }
    if (glow.tertiary != Color.Transparent) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow.tertiary, Color.Transparent),
                center = Offset(w * 0.00f, h * 0.72f),
                radius = maxDim * 1.10f,
            ),
        )
    }
    if (glow.quaternary != Color.Transparent) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow.quaternary, Color.Transparent),
                center = Offset(w * 0.92f, h * 1.02f),
                radius = maxDim * 1.15f,
            ),
        )
    }
    // ⑤ 顶部薄白：只压顶栏区域，让玻璃顶栏有"光从上面来"的错觉
    if (glow.topAir != Color.Transparent) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(glow.topAir, Color.Transparent),
                startY = 0f,
                endY = h * 0.42f,
            ),
        )
    }
    // ⑥ 底部压暗：视觉重心压回 Dock 一侧，同时给底部玻璃一点对比
    if (glow.bottomShade != Color.Transparent) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, glow.bottomShade),
                startY = h * 0.62f,
                endY = h,
            ),
        )
    }
}
