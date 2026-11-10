package com.dengying.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.theme.Glass
import com.dengying.player.ui.theme.Palette

/**
 * 玻璃下拉刷新指示器（v5）。
 *
 * 【为什么要自定义】Material3 的默认 [PullToRefreshDefaults.Indicator][androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator]
 * 有两个问题：
 * 1. **位置**：`PullToRefreshBox` 把 indicator 放在 Box 的 `TopCenter`，而首页的
 *    玻璃顶栏（品牌胶囊 + 搜索框）正好压在那个位置——下拉时转圈出现在顶栏
 *    **上方**，像从屏幕外飞出来（用户实测否决）。
 * 2. **材质**：默认是实心 Material 圆面，和全站玻璃语言不一致。
 *
 * 所以这里自己画：一个 36dp 的玻璃圆环，由调用方通过 `Modifier.align(...)`
 * 放到顶栏**下方**，并且只跟随 `state.distanceFraction` 做透明度与缩放，
 * 不做默认那种从 -height 滑入的位移动画（那正是"从顶部冒出"的观感来源）。
 *
 * @param state 由 `rememberPullToRefreshState()` 得到，提供下拉距离比例
 * @param isRefreshing 刷新中：转圈持续旋转且常显
 */
@Composable
fun GlassPullIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    // 只有真的在下拉或刷新时才占位——否则一个透明圆环会吃掉点击。
    val fraction = state.distanceFraction
    val visible = isRefreshing || fraction > 0.01f
    if (!visible) return

    val fill = Glass.Fill
    val border = Glass.Border
    val dark = Palette.AppBg.luminance() < 0.4f
    val spinnerColor = if (dark) Palette.TextPrimary else Palette.Brand

    // 下拉越多越实：0.45 → 1.0。刷新态固定 1.0，不要闪烁。
    val alpha = if (isRefreshing) 1f else (0.45f + fraction * 0.55f).coerceIn(0f, 1f)
    // 轻微放大：0.82 → 1.0，给"拉到位"一个物理反馈
    val scale = if (isRefreshing) 1f else (0.82f + fraction * 0.18f).coerceIn(0.82f, 1f)

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .size(36.dp)
            .drawBehind {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                // 玻璃面：半透明白 + 页面柔光透上来
                drawCircle(color = fill, radius = r, center = center)
                // 左上高光：玻璃厚度的镜面反光（与 GlassSurface 静态分支同语言）
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (dark) 0.16f else 0.55f),
                            Color.Transparent,
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    radius = r,
                    center = center,
                )
                // 细边
                drawCircle(
                    color = border,
                    radius = r - 0.5.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.Center)
                .size(18.dp),
            color = spinnerColor,
            strokeWidth = 2.dp,
        )
    }
}
