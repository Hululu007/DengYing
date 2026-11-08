package com.dengying.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 灯影的自绘矢量图标集（补齐 FR-709 ③「图标用矢量而非字体字形」）。
 *
 * ## 为什么手绘，而不是引图标库 / 用 iconfont 资源
 *
 * 三条现成路线都被否掉：
 *
 * 1. **`material-icons-extended`**：45.6 MB 的 aar（约 5000 个图标），本应用只需要
 *    二十来个图形。它会拖长每次构建的 `mergeExtDexDebug`（实测 16.6s），包体多 20 MB。
 * 2. **`core-ktx` 自带的那 30 多个 `Icons.Filled.*`**：播放/暂停能覆盖，但**没有
 *    上一集 / 下一集 / 全屏 / 亮度 / 搜索历史**，一套里缺一半 → 最后还是得手绘。
 * 3. **iconfont 的 TTF 字体包**：本质仍是"字体字形"，要往 `res/font` 塞字体文件再靠
 *    码位映射取图。字体缺字/字重不一致时渲染成方框——本仓
 *    `res/drawable/ic_launcher_foreground.xml` 头注释里记的就是这个老坑。
 *    （iconfont 平台也支持导出 SVG，但 SVG 还要经 VectorDrawable 解析一层，
 *    且 `pathData` 里嵌的 `<clipPath>`/变换未必兼容，收益不如直画 Path。）
 *
 * 所以：**Compose `Path` 手绘，零依赖、纯矢量、任意尺寸清晰**，造型口径自己掌握。
 *
 * ## 统一口径
 *
 * - 画布固定 **24×24** 网格（与 Material 图标网格一致，便于互相替换/对齐）。
 * - 线宽 2、端点与连接均为圆角；实心图形（播放三角、暂停条）走 [DrawScope.fill]。
 * - 图标本身**不含颜色**，颜色由调用方传入。
 *
 * ## 命名
 *
 * 与 Material 同义的用同名（`Play` / `Pause` / `ArrowBack` / `Search` / `Close`），
 * 应用特有的（[Lantern] 灯形、[ClearAll] 这类语义动作）用自造名——名字说清**动作**。
 */
object DyIcons {

    // ------------------------------------------------------------ 播放控制

    /** 播放：实心三角 */
    val Play: DyIcon = icon { p ->
        p.fill(
            Path().apply {
                moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
            }
        )
    }

    /** 暂停：两根等宽圆角竖条 */
    val Pause: DyIcon = icon { p ->
        p.bar(7f, 10f, 5f, 19f)
        p.bar(14f, 17f, 5f, 19f)
    }

    /** 上一集：左向实心三角 + 左侧竖线（三角左边贴着竖线，视觉上是一个整体） */
    val SkipPrevious: DyIcon = icon { p ->
        p.bar(6f, 8f, 5f, 19f)
        p.fill(
            Path().apply {
                moveTo(19f, 5f); lineTo(8.6f, 12f); lineTo(19f, 19f); close()
            }
        )
    }

    /** 下一集：[SkipPrevious] 的镜像 */
    val SkipNext: DyIcon = icon { p ->
        p.bar(16f, 18f, 5f, 19f)
        p.fill(
            Path().apply {
                moveTo(5f, 5f); lineTo(15.4f, 12f); lineTo(5f, 19f); close()
            }
        )
    }

    /** 快进：双实心三角（滑动 seek 预览用） */
    val FastForward: DyIcon = icon { p ->
        p.fill(Path().apply { moveTo(3f, 6f); lineTo(11f, 12f); lineTo(3f, 18f); close() })
        p.fill(Path().apply { moveTo(13f, 6f); lineTo(21f, 12f); lineTo(13f, 18f); close() })
    }

    /** 快退：[FastForward] 的镜像 */
    val FastRewind: DyIcon = icon { p ->
        p.fill(Path().apply { moveTo(21f, 6f); lineTo(13f, 12f); lineTo(21f, 18f); close() })
        p.fill(Path().apply { moveTo(11f, 6f); lineTo(3f, 12f); lineTo(11f, 18f); close() })
    }

    // ------------------------------------------------------------ 窗口 / 手势调节

    /**
     * 进入全屏：四个角括号（向外张开）。
     *
     * 【为什么只画角括号】初版在里面又加了一组斜向短线希望“更像放大”，
     * 但括号已经在角上，短线只能向内指——反而读成“退出全屏”。
     * 两个方向性图标必须**只差一个方向**，多出来的装饰就是这个差的敌人。
     */
    val Fullscreen: DyIcon = icon { p ->
        p.stroke(
            Path().apply {
                moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)      // 左上
                moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)   // 右上
                moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f) // 右下
                moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)   // 左下
            }
        )
    }

    /**
     * 退出全屏：四个角括号**向内收**。
     *
     * 与 [Fullscreen] 同一组角、同一线宽，只管两对坐标的“里/外”——
     * 並排看就是一组镜像，不会让用户猜哪个是进、哪个是退。
     */
    val FullscreenExit: DyIcon = icon { p ->
        p.stroke(
            Path().apply {
                moveTo(9f, 4f); lineTo(9f, 9f); lineTo(4f, 9f)      // 左上
                moveTo(15f, 4f); lineTo(15f, 9f); lineTo(20f, 9f)   // 右上
                moveTo(15f, 20f); lineTo(15f, 15f); lineTo(20f, 15f) // 右下
                moveTo(9f, 20f); lineTo(9f, 15f); lineTo(4f, 15f)   // 左下
            }
        )
    }

    /** 亮度（太阳）：实心圆 + 八向射线 */
    val Brightness: DyIcon = icon { p ->
        p.circle(12f, 12f, 4.2f)
        val rays = Path().apply {
            moveTo(12f, 2.4f); lineTo(12f, 5.0f)
            moveTo(12f, 19.0f); lineTo(12f, 21.6f)
            moveTo(2.4f, 12f); lineTo(5.0f, 12f)
            moveTo(19.0f, 12f); lineTo(21.6f, 12f)
            moveTo(5.6f, 5.6f); lineTo(7.4f, 7.4f)
            moveTo(16.6f, 16.6f); lineTo(18.4f, 18.4f)
            moveTo(18.4f, 5.6f); lineTo(16.6f, 7.4f)
            moveTo(7.4f, 16.6f); lineTo(5.6f, 18.4f)
        }
        p.stroke(rays, 1.8f)
    }

    /** 音量（喇叭 + 两道声波） */
    val Volume: DyIcon = icon { p ->
        p.fill(
            Path().apply {
                moveTo(3f, 9f)
                lineTo(7f, 9f)
                lineTo(12f, 4.6f)
                lineTo(12f, 19.4f)
                lineTo(7f, 15f)
                lineTo(3f, 15f)
                close()
            }
        )
        p.stroke(
            Path().apply {
                moveTo(15.0f, 8.8f)
                quadraticTo(17.0f, 10.4f, 17.0f, 12f)
                quadraticTo(17.0f, 13.6f, 15.0f, 15.2f)
                moveTo(18.2f, 6.2f)
                quadraticTo(21.0f, 8.8f, 21.0f, 12f)
                quadraticTo(21.0f, 15.2f, 18.2f, 17.8f)
            },
            1.8f,
        )
    }

    // ------------------------------------------------------------ 导航与通用

    /** 返回：左向箭头（替换原来的全角 `‹` 字形，后者粗细随字重漂移） */
    val ArrowBack: DyIcon = icon { p ->
        p.stroke(
            Path().apply {
                moveTo(14.8f, 4.4f); lineTo(7.2f, 12f); lineTo(14.8f, 19.6f)
            },
            2.2f,
        )
    }

    /** 搜索：圆 + 斜柄 */
    val Search: DyIcon = icon { p ->
        p.ring(10.4f, 10.4f, 6.2f, 2.2f)
        p.stroke(Path().apply { moveTo(15.1f, 15.1f); lineTo(20.6f, 20.6f) }, 2.2f)
    }

    /** 我的：头部圆 + 肩部弧 */
    val Person: DyIcon = icon { p ->
        p.circle(12f, 8.0f, 3.6f)
        p.stroke(
            Path().apply {
                moveTo(4.8f, 20.2f)
                quadraticTo(4.8f, 13.4f, 12f, 13.4f)
                quadraticTo(19.2f, 13.4f, 19.2f, 20.2f)
            },
            2.2f,
        )
    }

    /** 收藏（已收藏）：实心五角星 */
    val Star: DyIcon = icon { p -> p.fill(starPath()) }

    /** 收藏（未收藏）：空心五角星 */
    val StarOutline: DyIcon = icon { p -> p.stroke(starPath(), 2f) }

    /** 关闭 / 删除单条：45° 十字 */
    val Close: DyIcon = icon { p ->
        p.stroke(
            Path().apply {
                moveTo(6.2f, 6.2f); lineTo(17.8f, 17.8f)
                moveTo(17.8f, 6.2f); lineTo(6.2f, 17.8f)
            },
            2.2f,
        )
    }

    /** 清空（垃圾桶）：提手 + 盖 + 桶身 + 两道内线 */
    val ClearAll: DyIcon = icon { p ->
        p.stroke(
            Path().apply {
                moveTo(9.4f, 4.4f); lineTo(14.6f, 4.4f)      // 提手
                moveTo(3.6f, 7.4f); lineTo(20.4f, 7.4f)      // 盖
                moveTo(6.2f, 7.4f)                            // 桶身
                lineTo(7.3f, 19.4f)
                lineTo(16.7f, 19.4f)
                lineTo(17.8f, 7.4f)
                moveTo(10.3f, 10.8f); lineTo(10.6f, 15.8f)   // 内线
                moveTo(13.7f, 10.8f); lineTo(13.4f, 15.8f)
            },
            2f,
        )
    }

    /** 刷新：开口圆环 + 箭头 */
    val Refresh: DyIcon = icon { p ->
        p.arc(startAngle = 300f, sweepAngle = 300f, inset = 3.6f, width = 2.2f)
        p.fill(
            Path().apply {
                moveTo(11.6f, 1.8f); lineTo(16.2f, 5.4f); lineTo(11.6f, 8.2f); close()
            }
        )
    }

    /** 列表 / 历史：三行「圆点 + 横线」 */
    val History: DyIcon = icon { p ->
        for (y in floatArrayOf(7f, 12f, 17f)) {
            p.circle(5.2f, y, 1.3f)
            p.stroke(Path().apply { moveTo(9f, y); lineTo(20f, y) }, 2f)
        }
    }

    /** 出错（替代原来的 😕 emoji —— emoji 在部分 ROM/字体下渲染成方框） */
    val ErrorFace: DyIcon = icon { p ->
        p.ring(12f, 12f, 8.6f, 2f)
        p.circle(9f, 9.6f, 1.25f)
        p.circle(15f, 9.6f, 1.25f)
        p.stroke(
            Path().apply {
                moveTo(8.2f, 16.8f)
                quadraticTo(12f, 13.4f, 15.8f, 16.8f)
            },
            1.9f,
        )
    }

    // ------------------------------------------------------------ 品牌：灯影

    /**
     * 灯影的品牌灯形（首页标题旁的小标记）。
     *
     * 与自适应图标前景 `res/drawable/ic_launcher_foreground.xml` **同一造型**：
     * 上窄下宽的梯形灯罩 + 顶部横杆 + 两级光晕。这里用圆角描边版本，
     * 使其在 20dp 尺寸下仍然清晰（启动图标那份是实心填充，二者视觉同源）。
     */
    val Lantern: DyIcon = icon { p ->
        p.bar(8.4f, 15.6f, 2.8f, 4.4f)                       // 灯顶横杆
        p.stroke(
            Path().apply {
                moveTo(9.4f, 4.8f); lineTo(14.6f, 4.8f)      // 灯罩：上窄下宽
                lineTo(17.6f, 15.4f)
                lineTo(6.4f, 15.4f)
                close()
            },
            1.9f,
        )
        p.bar(8.6f, 15.4f, 17.2f, 18.6f)                     // 光晕一级
        p.bar(10.2f, 13.8f, 20.2f, 21.6f)                    // 光晕二级
    }

    /** 分类：四宫格（四个圆角块，Material dashboard 造型） */
    val Grid: DyIcon = icon { p ->
        val r = CornerRadius(2.2f, 2.2f)
        listOf(
            4f to 4f, 13f to 4f, 4f to 13f, 13f to 13f,
        ).forEach { (x, y) ->
            p.fill(
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(offset = Offset(x, y), size = Size(7f, 7f)),
                            cornerRadius = r,
                        ),
                    )
                },
            )
        }
    }

    /** 发现：罗盘（外圈 + 东北-西南指针，Material explore 造型） */
    val Compass: DyIcon = icon { p ->
        p.ring(12f, 12f, 8.4f, 1.9f)
        p.fill(
            Path().apply {
                moveTo(16.2f, 7.8f); lineTo(13.4f, 13.4f)
                lineTo(7.8f, 16.2f); lineTo(10.6f, 10.6f)
                close()
            },
        )
    }

    /** 设置：调节滑杆（三横杆 + 错位环形旋钮，Material tune 造型） */
    val Sliders: DyIcon = icon { p ->
        p.bar(4f, 20f, 6f, 8f)
        p.bar(4f, 20f, 11f, 13f)
        p.bar(4f, 20f, 16f, 18f)
        p.ring(15.5f, 7f, 2.6f, 1.7f)
        p.ring(8f, 12f, 2.6f, 1.7f)
        p.ring(14f, 17f, 2.6f, 1.7f)
    }

    /** 全部图标（预览/一致性检查用，业务代码不遍历） */
    val all: List<DyIcon> = listOf(
        Play, Pause, SkipPrevious, SkipNext, FastForward, FastRewind,
        Fullscreen, FullscreenExit, Brightness, Volume,
        ArrowBack, Search, Person, Star, StarOutline, Close, ClearAll,
        Refresh, History, ErrorFace, Lantern, Grid, Compass,
    )
}

// ---------------------------------------------------------------- 内部：构建与绘制

/**
 * 一个图标的绘制描述：在 **24×24 网格坐标**里画。
 *
 * 【为什么存 lambda 而不是 Path 常量】Path 一旦被某个 `DrawScope` 按特定 density
 * 栅格化就与那次绘制绑定，跨尺寸复用会有语义歧义。用"每次绘制现算"最省心；
 * 代价是每次重建几条 Path——图标数量极少，可忽略。
 */
class DyIcon internal constructor(internal val render: DrawScope.(Color) -> Unit)

/** 图标工厂：`p` 是 24 网格坐标系里的画笔 */
private fun icon(block: (Pen) -> Unit): DyIcon = DyIcon { color ->
    block(Pen(this, color))
}

/**
 * 24 网格画笔。
 *
 * 调用它的 `DrawScope` **已经**被 [DyIcon] 缩放到 24 单位见方，所以这里所有坐标
 * 都按 24 网格写，`size` 变化时线宽与几何一起等比缩放。
 */
private class Pen(private val scope: DrawScope, private val color: Color) {

    fun fill(path: Path) {
        scope.drawPath(path, color, style = Fill)
    }

    fun stroke(path: Path, width: Float = 2f) {
        scope.drawPath(
            path, color,
            style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    fun circle(cx: Float, cy: Float, r: Float) {
        scope.drawCircle(color, radius = r, center = Offset(cx, cy))
    }

    fun ring(cx: Float, cy: Float, r: Float, width: Float) {
        scope.drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(width))
    }

    /** 圆弧（[inset] 为从画布四边内缩的距离） */
    fun arc(startAngle: Float, sweepAngle: Float, inset: Float, width: Float) {
        val d = (12f - inset) * 2f
        scope.drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(d, d),
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }

    /** 实心圆角矩形块（暂停条、跳过竖线、光晕横条） */
    fun bar(x0: Float, x1: Float, y0: Float, y1: Float) {
        val r = minOf(x1 - x0, y1 - y0) / 2f
        scope.drawRoundRect(
            color = color,
            topLeft = Offset(x0, y0),
            size = Size(x1 - x0, y1 - y0),
            cornerRadius = CornerRadius(r, r),
        )
    }
}

/** 五角星路径（外接圆 y = 12.4，外半径 8.9，内半径按黄金比） */
private fun starPath(): Path {
    val outer = 8.9f
    val inner = outer * 0.382f
    val cy = 12.4f
    return Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            // kotlin.math 而非 java.lang.Math：后者在 Kotlin/Native 上不存在。
            val a = (-90 + i * 36) * kotlin.math.PI / 180.0
            val x = 12f + (r * kotlin.math.cos(a)).toFloat()
            val y = cy + (r * kotlin.math.sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * 画一个图标。
 *
 * 尺寸口径：默认 [size] = 24dp（Material 图标网格），**视觉尺寸与热区解耦**——
 * 热区由调用方的 `Modifier.size()` / `clickable` 决定，见 `PlayerScreen` 的 `CtrlIcon`。
 *
 * @param icon 图标句柄（见 [DyIcons]）
 * @param tint 填充/描边色
 * @param size 图标视觉边长
 * @param contentDescription 无障碍描述；null = 装饰图，不朗读
 */
@Composable
fun DyIcon(
    icon: DyIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            // 把 24 网格等比映射到实际像素；stroke 宽度随变换一起缩放
            val k = this.size.width / 24f
            if (k <= 0f) return@Canvas
            scale(scaleX = k, scaleY = k, pivot = Offset.Zero) {
                icon.render(this, tint)
            }
        }
    }
}
