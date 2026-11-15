package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dengying.player.domain.playback.GestureRules
import com.dengying.player.domain.playback.HlsVariants
import com.dengying.player.player.PlaybackProbe
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.GlassBackButton
import com.dengying.player.ui.components.GlassSurface
import com.dengying.player.ui.components.SeekPreviewBubble
import com.dengying.player.ui.platform.PlatformBackHandler
import com.dengying.player.ui.platform.VideoSurface
import com.dengying.player.ui.platform.shouldRevealVideoSurface
import com.dengying.player.ui.platform.videoSurfaceIsBelowCompose
import com.dengying.player.ui.platform.rememberBrightnessController
import com.dengying.player.ui.platform.rememberIsLandscape
import com.dengying.player.ui.platform.rememberVolumeController
import com.dengying.player.ui.theme.Glass
import com.dengying.player.ui.theme.Palette
import com.dengying.player.ui.viewmodel.PlayerViewModel

/**
 * 播放页 UI。
 *
 * 【为什么用 AndroidView(PlayerView) 而不是 media3-ui-compose 的 PlayerSurface】
 * `media3-ui-compose` 目前仍是 alpha，其 `PlayerSurface` 只画画面、不提供
 * `AspectRatioFrameLayout` 的 `resizeMode`（fit/crop/fill）与字幕层。
 * 本应用要处理三种宽高比差异很大的内容（横屏电影 16:9、竖屏短剧 9:16、老片 4:3），
 * 需要 `RESIZE_MODE_FIT` + 黑边，用传统 PlayerView 最省事且行为确定。
 * 这属于"平台适配层"的选择，不影响其他代码。
 */
@Composable
fun PlayerScreen(
    params: PlayerViewModel.PlayerParams,
    onBack: () -> Unit,
    fullscreen: com.dengying.player.ui.platform.FullscreenState,
) {
    val vm: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "player_${params.source}_${params.vodId}_${params.episodeIndex}",
    ) {
        val deps = com.dengying.player.ui.UiDepsHolder.get()
        PlayerViewModel(
            params = params,
            repo = deps.sources,
            store = deps.store,
            engineHost = deps.engineHost,
            isDebug = deps.isDebug,
        )
    }
    val s = vm.state

    // 【系统返回键也走同一条返回逻辑】NavHost 的默认返回是 popBackStack()，
    // 会绕过调用方传进来的 onBack。而「换源失败后返回要回详情页」这条语义
    // 写在 onBack 里（见 MainActivity 的说明），故这里必须显式接管。
    PlatformBackHandler { onBack() }

    // 引擎在 ViewModel init 里就已创建（见那里的说明），故这里不再创建，
    // 只处理"离开页面"的收尾。释放交给 VM.onCleared，避免配置变更时误释放。
    //
    // 【登记活跃播放表面】计数 +1；离开时 -1（`onCleared` 里已配对）。
    // 归零时 PlaybackEngineHolder 内部暂停播放——这是"离开后没有声音"的保证。
    //
    // 【FR-711 宿主职责】进入全屏页 = 进入全屏形态：请求横屏 + 隐藏系统栏。
    // 放在这里而不是点击全屏键的回调里——方向是**页面形态**的属性，
    // 无论从哪条路径进入本页（内嵌切全屏 / 历史直达），形态都应一致。
    // 离开本页回详情页时，详情页的 `SideEffect`（FullscreenState）会把
    // 方向与系统栏按新页面状态收敛回去。
    DisposableEffect(Unit) {
        com.dengying.player.ui.UiDepsHolder.get().engineHost.surfaceActive()
        fullscreen.enter(landscape = true)
        onDispose {
            com.dengying.player.ui.UiDepsHolder.get().engineHost.surfaceInactive()
        }
    }

    // 横屏与否**由系统方向反推**，不在应用侧另存布尔——
    // 这样旋转、Activity 重建、系统栏变化都能自愈，不存在"锁死横屏"
    val isLandscape = rememberIsLandscape()

    // FR-708：画面实际宽度（像素）。由 `onSizeChanged` 写入，
    // 供滑动 seek 把"滑过多大距离"换算成"跳多少时长"。
    // 用状态而非直接读 scope：全屏/内嵌/分屏下宽度都不同，必须取当前实际值。
    var viewWidthPx by remember {
        androidx.compose.runtime.mutableFloatStateOf(0f)
    }
    // P-04/P-05：画面高度，用于把竖向位移换算成亮度/音量的变化比例。
    // 口径与宽度一致：全屏/内嵌/分屏各取当前实际值。
    var viewHeightPx by remember {
        androidx.compose.runtime.mutableFloatStateOf(1f)
    }

    // ---- P-04/P-05 平台调节器（`ui/platform` 层）----
    // 拿不到 Activity 时亮度退化为"不可调"（见手势判定的 mode=-1 分支），
    // 音量不受影响：AudioManager 只需要 Context。
    val brightnessCtl = rememberBrightnessController()
    val volumeCtl = rememberVolumeController()

    // 竖向手势的即时反馈浮层：kind 2=亮度、3=音量。
    // `adjustVisible` 控制显隐，`adjustInfo` 只承载数值；
    // `adjustActive` 标记"手指仍在调节手势中"；`adjustHideTick` 是手势结束计数器。
    var adjustInfo by remember { mutableStateOf<GestureAdjust?>(null) }
    var adjustVisible by remember { mutableStateOf(false) }
    var adjustActive by remember { mutableStateOf(false) }
    var adjustHideTick by remember { mutableStateOf(0) }
    // 双击动作闪现：true 表示本次动作是"暂停"（显示 ⏸），false 表示"播放"。
    var playFlash by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(playFlash) {
        if (playFlash != null) {
            kotlinx.coroutines.delay(600)
            playFlash = null
        }
    }

    // 离开播放页：撤掉窗口亮度覆写（P-04 的恢复规则）。
    // 与 [WindowBrightnessController.restore] 的注释配套：不恢复会让用户在
    // 应用其它页面顶着这个亮度且找不到原因。
    DisposableEffect(brightnessCtl) {
        onDispose { brightnessCtl?.restore() }
    }

    // 手势浮层的收起时机：**手势结束时开始计时**。
    // `adjustHideTick` 每次手势结束自增一次，效果随之重启——
    // 若改成"值变化就重启"，手指停住不动（值不变）时浮层会提前消失。
    //
    // 【为什么还要判 adjustActive】两次调节手势间隔小于 800ms 时，
    // 上一轮的延时收起会在新一轮手势进行中到期。到期时重新读一次
    // "是否仍在手势中"，仍在就放弃收起，由新一轮的结束事件再计时。
    LaunchedEffect(adjustHideTick) {
        if (adjustHideTick > 0) {
            kotlinx.coroutines.delay(800)
            if (!adjustActive) adjustVisible = false
        }
    }

    // 【ohos 画面打洞】视频 surface 铺在 Compose 之下（ComposePage.ets 的全屏
    // XComponent）。Compose 这边的根 Box 若铺不透明黑，会把整块视频盖住
    // （音频正常、画面全黑）。故背景改为**有画面时让位**：引擎绑上播放面且
    // 不在缓冲/错误态时根背景透明，视频从下层透出；其余时机仍铺黑（缓冲
    // 转圈、错误文案、空态都落在黑底上）。
    //
    // 打洞是鸿蒙独有的层序需求：Android 的 PlayerView 在 Compose 树内出图，
    // FIT 缩放的黑边之外若根背景透明会露出 Activity 的暖白 windowBackground；
    // 桌面/JVM 侧也没有下层视频。故由平台开关限定，详见
    // platform/VideoSurfaceLayer.kt 与 shouldRevealVideoSurface 的单测。
    var surfaceBound by remember { mutableStateOf(false) }
    val revealVideoSurface = shouldRevealVideoSurface(
        videoBelowCompose = videoSurfaceIsBelowCompose,
        surfaceBound = surfaceBound,
        prepared = s.prepared,
        buffering = s.buffering,
        hasError = s.errMsg.isNotEmpty(),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (revealVideoSurface) Color.Transparent else Color.Black)
            // FR-708 + P-02..P-05 手势总入口（docs/08）
            //
            // 【一次手势只落入一种模式】未判定前累计位移；越过阈值后按
            // "主导方向 + 起点 x"一次定模，此后不再切换，从结构上排除
            // "一次手势触发多个动作"：
            //   横向主导            → seek（FR-708）
            //   竖向主导 + 起点在左半屏 → 亮度（P-04）
            //   竖向主导 + 起点在右半屏 → 音量（P-05）
            // 阈值取 20px：够小以免"想滑动却判成点击"，够大以免轻微抖动触发。
            //
            // 【为什么用 detectDragGestures 而不是 detectHorizontalDragGestures】
            // 后者在手势一开始就锁定横向，手指先竖后横的常见操作会被丢弃。
            //
            // 【宽高怎么拿】`pointerInput` 的 `PointerInputScope` 虽有 `size`，
            // 但在嵌套的 `onDrag` lambda 里标签引用容易失效（编译期踩过）。
            // 改用 `onSizeChanged` 把宽高存进 remember 状态，手势里读它——
            // 这样在全屏/内嵌/分屏各种尺寸下都是当前实际值。
            .onSizeChanged {
                viewWidthPx = it.width.toFloat()
                viewHeightPx = it.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                var totalX = 0f
                var totalY = 0f
                var startX = 0f
                // 0=未判定 1=seek 2=亮度 3=音量 -1=竖向但亮度不可调（本次手势作废）
                var mode = 0
                var brightBase = 0f
                var volBase = 0f

                detectDragGestures(
                    onDragStart = { offset ->
                        totalX = 0f
                        totalY = 0f
                        startX = offset.x
                        mode = 0
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        if (mode == 0) {
                            // 方向仲裁是**纯函数**（`domain/GestureRules`）：
                            // 可单测，且"一次手势只落入一种模式"由它的返回值
                            // 唯一确定（docs/08 A-06 / F-11 / F-12）。
                            when (GestureRules.decide(
                                totalX = totalX,
                                totalY = totalY,
                                startX = startX,
                                viewWidth = viewWidthPx,
                                brightnessAvailable = brightnessCtl != null,
                            )) {
                                GestureRules.Action.Undecided -> Unit
                                GestureRules.Action.Seek -> {
                                    mode = 1
                                    vm.onSeekStart()
                                }
                                GestureRules.Action.Brightness -> {
                                    mode = 2
                                    brightBase = brightnessCtl!!.current()
                                    adjustInfo = GestureAdjust(2, brightBase)
                                    adjustVisible = true
                                    adjustActive = true
                                    PlaybackProbe.note("gesture-brightness-start")
                                }
                                GestureRules.Action.Volume -> {
                                    mode = 3
                                    volBase = volumeCtl?.currentFraction() ?: 0f
                                    adjustInfo = GestureAdjust(3, volBase)
                                    adjustVisible = true
                                    adjustActive = true
                                    PlaybackProbe.note("gesture-volume-start")
                                }
                                GestureRules.Action.Discard -> {
                                    // 竖向意图已明确但该侧不可调：本次手势作废。
                                    // 不退回 seek（方向已经确定），也不当成点击。
                                    mode = -1
                                    PlaybackProbe.note("gesture-discard", "reason=left-no-brightness")
                                }
                            }
                        }
                        when (mode) {
                            1 -> vm.onSeekDrag(totalX, viewWidthPx)
                            // 上滑为正：竖向位移取负号（Y 轴向下为正）
                            2 -> adjustInfo = GestureAdjust(
                                2,
                                brightnessCtl!!.adjust(brightBase, -totalY / viewHeightPx),
                            )
                            3 -> adjustInfo = GestureAdjust(
                                3,
                                volumeCtl?.adjust(volBase, -totalY / viewHeightPx)
                                    ?: volBase,
                            )
                        }
                    },
                    onDragEnd = {
                        when (mode) {
                            1 -> vm.onSeekCommit()
                            // 亮度/音量：浮层在**手势结束**后延时收起（adjustHideTick），
                            // 让用户看清最终数值；不在这里清掉，避免松手瞬间消失。
                            2, 3 -> {
                                adjustActive = false
                                adjustHideTick++
                            }
                            0 -> vm.toggleControls()   // 位移不足阈值 → 仍按点击处理
                            // -1：作废手势，不切换控制层（竖向意图已明确）
                        }
                    },
                    onDragCancel = {
                        when (mode) {
                            1 -> vm.onSeekCancel()
                            2, 3 -> {
                                adjustActive = false
                                adjustHideTick++
                            }
                        }
                    },
                )
            }
            // 单击 / 双击：与上面的拖动互补。`detectTapGestures` 只在"未形成拖动"时触发，
            // 故不会与 seek、亮度、音量冲突。
            //
            // 【单击会比初版慢约 300ms】提供 `onDoubleTap` 后，`onTap` 必须等
            // 双击判定窗口过期才回调——这是同一根手指上区分单击/双击的固有代价，
            // 主流播放器同样如此（docs/08 §2 约束 4 的优先级：先判双击）。
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { vm.toggleControls() },
                    onDoubleTap = {
                        // P-03：双击画面切换播放/暂停，并闪现动作图标作为确认
                        val wasPlaying = vm.state.isPlaying
                        vm.togglePlay()
                        playFlash = wasPlaying
                        PlaybackProbe.note("gesture-double-tap", "wasPlaying=$wasPlaying")
                    },
                )
            },
    ) {
        // ---- 渲染面 ----
        //
        // 【为什么用 key(engine) 包一层】真机实测黑屏的根因是绑定时序：
        // `AndroidView` 的 `factory` 先执行、`update` 只在重组时执行，而引擎是在
        // `DisposableEffect` 里创建的 —— factory 早于引擎，首次 update 必然拿到 null。
        // 即便把 engine 改成可观察状态触发了重组，`update` 也只是"再跑一次"，
        // 而 `PlayerView` 实例与它内部持有的 player 状态是上一次的，容易出现
        // 绑上了但 surface 未就绪的中间态。
        //
        // `key(engine)` 让引擎变化时**整个 AndroidView 重建**：新 PlayerView
        // 在 factory 阶段就拿到已就绪的 player，绑定与 surface 初始化在同一步完成，
        // 不存在中间态。代价是引擎重建时多一次视图创建，而引擎只在换集/换档时重建，
        // 频率极低。
        VideoSurface(
            engine = vm.engine,
            modifier = Modifier.fillMaxSize(),
            onBind = { bound ->
                // 【探针】绑定时是否真的拿到了 player —— 黑屏的第一嫌疑点
                com.dengying.player.player.PlaybackProbe.note(
                    "view-bind",
                    "engine=${vm.engine != null} boundPlayer=$bound",
                )
                // 上面根背景的打洞开关：绑上播放面才允许根背景让位。
                surfaceBound = bound
            },
        )

        // ---- 加载/缓冲 / 错误态：**互斥** ----
        //
        // 【真机反馈的 bug】初版写成两个独立 `if`，于是 `buffering = true` 且
        // `errMsg` 非空时两个浮层同时渲染——用户看到的是
        // "转圈 + 播放异常/点击重试" 叠在一起。
        //
        // 更麻烦的是 `buffering` 的清除只依赖 `onBuffering(false)`：
        // 播放器报错后不会再发那个回调，转圈就**永远不停**。
        //
        // 故改为：错误态优先，且一旦有错就不显示转圈；两者共用一个 when 分支，
        // 结构上不可能同时出现。
        when {
            s.errMsg.isNotEmpty() -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = s.errMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        Row(modifier = Modifier.padding(top = 16.dp)) {
                            ActionChip("重试") { vm.retry() }
                            Spacer(Modifier.width(12.dp))
                            ActionChip("返回") { onBack() }
                        }
                    }
                }
            }

            s.buffering || !s.prepared -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                        if (s.status.isNotEmpty()) {
                            Text(
                                text = s.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        // ---- 一次性轻提示（如换档失败已回退）----
        // 与 errMsg 的常驻错误浮层分开：这种提示可自愈，不应遮挡控制层或要求重试。
        if (s.noticeMsg.isNotEmpty() && s.errMsg.isEmpty()) {
            Text(
                text = s.noticeMsg,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }

        // ---- 手势调节浮层（P-04 亮度靠左 / P-05 音量靠右）----
        //
        // 【为什么放在这里而不是 ControlOverlay 里】控制层会随 4 秒自动隐藏，
        // 而调节反馈必须独立于控制层显隐——用户常在控制层已隐藏时滑动调节。
        //
        // 【为什么不用入场/退场动画】数值跟着手指实时刷新，动画会在每次
        // 数值变化时被打断重来，观感反而抖动。显隐由 `adjustVisible` 直接控制，
        // 收起时机由"手势结束后 800ms"决定（见 adjustHideTick 的说明）。
        val info = adjustInfo
        if (adjustVisible && info != null) {
            val pct = (info.fraction * 100).toInt().coerceIn(0, 100)
            val isBrightness = info.kind == 2
            GlassSurface(
                modifier = Modifier
                    .align(if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 28.dp),
                shape = RoundedCornerShape(20.dp),
                fill = Glass.DarkFill,
                elevation = 0.dp,
            ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 【P-04/P-05 图标改为矢量】原来是 ☀ 与 🔊：前者是字形（粗细随字重漂移），
                // 后者是彩色 emoji（各 ROM 造型不一致，部分字体下渲染成方框）。
                DyIcon(
                    icon = if (isBrightness) DyIcons.Brightness else DyIcons.Volume,
                    tint = Color.White,
                    size = 20.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${if (isBrightness) "亮度" else "音量"} $pct%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
            }
        }

        // ---- 双击动作闪现（P-03）----
        // 双击是"无按钮的隐式操作"，必须给一次可见确认，否则用户不知道生效没有。
        playFlash?.let { wasPlaying ->
            DyIcon(
                icon = if (wasPlaying) DyIcons.Pause else DyIcons.Play,
                tint = Color.White.copy(alpha = 0.85f),
                size = 56.dp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ---- 控制层 ----
        if (s.controlsVisible && s.errMsg.isEmpty()) {
            ControlOverlay(
                s = s,
                vm = vm,
                onBack = onBack,
                isLandscape = isLandscape,
                fullscreen = fullscreen,
            )
        }

        // ---- 滑动 seek 预览（FR-708 / P-02）----
        //
        // 【为什么放在 `ControlOverlay` 之外】真机复现：控制层 4 秒自动隐藏后
        // 横向滑动，播放器确实 seek 了（探针 `seek-start → engine-seek →
        // seek-commit` 齐全），但画面上**一帧反馈都没有**——浮层跟着控制层一起
        // 被 `if (s.controlsVisible)` 挡掉了。而"猜我滑到哪了"恰恰是控制层已隐藏
        // 时最需要的信息，故预览必须独立于控制层显隐。
        SeekPreviewOverlay(s = s)
    }
}

/**
 * 滑动 seek 预览浮层（FR-708 / P-02）。
 *
 * 【P-02 的两半，缺一不可】
 *  1. **浮层不随控制层隐藏**：真机复现——控制层 4 秒自动隐藏后横滑，
 *     播放器确实 seek 了（探针 `seek-start → engine-seek → seek-commit` 齐全），
 *     但画面上**一帧反馈都没有**。浮层原先写在 `ControlOverlay` 内部，
 *     被 `if (s.controlsVisible)` 一起挡掉了。
 *  2. **底部进度条要跟着手指走**：由 `PlayerViewModel.onSeekStart` 唤出控制层实现。
 *
 * 具体的呈现（方向图标 / 目标时间 / 差值 / 入场收尾动画）抽在
 * `ui/components/SeekPreviewBubble`，与详情页内嵌位共用同一份口径。
 */
@Composable
private fun BoxScope.SeekPreviewOverlay(s: PlayerViewModel.UiState) {
    SeekPreviewBubble(
        visible = s.seeking,
        fromMs = s.seekStartMs,
        targetMs = s.seekTarget,
        modifier = Modifier.align(Alignment.Center),
    )
}

/**
 * 手势调节浮层的状态。
 *
 * @param kind 2=亮度、3=音量。用整数而不是枚举：值只在 `PlayerScreen` 内部
 *   流转，不构成对外契约，不需要额外的类型与导入。
 * @param fraction 0..1 的实际生效值
 */
private data class GestureAdjust(val kind: Int, val fraction: Float)

@Composable
private fun ActionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

/**
 * 控制层。
 *
 * 版式对齐原工程：
 *  - 顶栏：返回 / 标题（居中）/ 投屏
 *  - 底部：进度行（当前 / 总长 / 进度条）+ 控制行（倍速 / 上一集 / 播放暂停 / 下一集 / 全屏）
 *  - 连播开关在控制行上方
 *
 * FR-415：上一集/下一集在首末集时禁用（视觉 0.35 透明度 + 点击不响应）
 * FR-424：竖屏紧凑形态只留进度条 + 全屏键——由调用方传入的 `compact` 决定
 */
@Composable
private fun ControlOverlay(
    s: PlayerViewModel.UiState,
    vm: PlayerViewModel,
    onBack: () -> Unit,
    isLandscape: Boolean,
    fullscreen: com.dengying.player.ui.platform.FullscreenState,
) {
    Box(Modifier.fillMaxSize()) {
        // ---- 顶栏 ----
        // 【玻璃化 2026-11-08】通栏黑条 → 悬浮胶囊暗玻璃条，与首页顶栏同一设计语言。
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .height(44.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(22.dp),
            fill = Glass.DarkFill,
            elevation = 0.dp,
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBackButton(onClick = onBack, dark = true, modifier = Modifier.padding(end = 10.dp))
            Text(
                text = s.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 试看链提示：5 分钟是 tv189 免费额度，详情页的「其他资源站」可换全片
            if (s.isPreview) {
                Text(
                    text = "试看",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            // FR-419：不可投的源入口为禁用态且点击不响应；理由常驻显示在下方提示行
            Text(
                text = "投屏",
                style = MaterialTheme.typography.bodySmall,
                color = if (s.castEnabled) Color.White else Color.White.copy(alpha = 0.35f),
                modifier = Modifier
                    .clickable(enabled = s.castEnabled) { /* 见下方说明 */ }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            )
        }
        }

        if (s.castBlockReason.isNotEmpty()) {
            Text(
                text = s.castBlockReason,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 46.dp, end = 12.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (s.castMsg.isNotEmpty()) {
            Text(
                text = s.castMsg,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
                    .background(Palette.Danger.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // ---- 倍速面板 ----
        if (s.showSpeedSheet) {
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 150.dp),
                shape = RoundedCornerShape(18.dp),
                fill = Glass.DarkFill,
                elevation = 0.dp,
            ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 6.dp),
            ) {
                for (v in listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) {
                    Text(
                        text = "${if (v == 1.0f) "1.0" else if (v == 2.0f) "2.0" else v.toString()}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.speed == v) Palette.Danger else Color.White,
                        modifier = Modifier
                            .clickable { vm.setSpeed(v) }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    )
                }
            }
            }
        }

        // ---- 清晰度面板（FR-412：只在 >1 且档位可区分时出现）----
        if (s.showQualitySheet && vm.showQualityEntry) {
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 150.dp),
                shape = RoundedCornerShape(18.dp),
                fill = Glass.DarkFill,
                elevation = 0.dp,
            ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 6.dp),
            ) {
                for (v in s.variants) {
                    Text(
                        text = HlsVariants.variantLabel(v),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.selectedVariant == v.url) Palette.Danger else Color.White,
                        modifier = Modifier
                            .clickable { vm.applyQuality(v.url) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
            }
        }

        // ---- 底部控制条（FR-707：**单行**）----
        //
        // 【为什么改成单行】用户反馈：「进入全屏之后，下方的状态栏占画幅过多，
        // 应该将播放上下集倍速、退出全屏都放一条线上」。
        // 初版是"进度行 + 控制行 + 连播行"三行堆叠，全屏时占掉近三分之一画面。
        //
        // 现在：**一行**，从左到右为
        //   当前时间 · 倍速 · 上一集 · 播放暂停 · 下一集 · 退出全屏 · 总时长
        // 进度条放在这一行**上方**，但它很薄（24dp）且半透明，视觉上仍是一条。
        // 连播/清晰度这两个低频开关移到了顶栏区域（见上方），不再占底部高度。
        //
        // 【玻璃化 2026-11-08】通栏黑条 → 悬浮暗玻璃圆角条。
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp)
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(20.dp),
            fill = Glass.DarkFill,
            elevation = 0.dp,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            // 进度条（细，贴在控制行上方）
            val duration = if (s.totalTime > 0) s.totalTime else 1L
            val shown = if (s.dragging) s.dragValue else s.curTime
            Slider(
                value = shown.coerceIn(0L, duration).toFloat(),
                onValueChange = { vm.onDrag(it.toLong()) },
                onValueChangeFinished = { vm.onDragEnd() },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Palette.Danger,
                    activeTrackColor = Palette.Danger,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .pointerInput(Unit) {
                        // 拖动开始/结束的显式信号：Slider 自身的回调不足以区分
                        // "手指按下"与"值变化"，而 FR-409 要求拖动期间不被时间更新覆盖
                        detectTapGestures(onPress = {
                            vm.onDragStart()
                            tryAwaitRelease()
                            vm.onDragEnd()
                        })
                    },
            )

            // 单行控制行（FR-707 + FR-709：键位放大到 48dp 见方）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fmt(shown),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))

                // 倍速（纯文字 —— 它不是图形，给图标反而看不懂，故仍走文字分支）
                CtrlIcon(
                    label = if (s.speed == 1.0f) "1x" else if (s.speed == 2.0f) "2x" else "${s.speed}x",
                    enabled = true,
                    isText = true,
                    onClick = { vm.toggleSpeedSheet() },
                )
                // 上一集（首集禁用）
                CtrlIcon(
                    icon = DyIcons.SkipPrevious,
                    label = "上一集",
                    enabled = vm.hasEp(-1),
                    onClick = { vm.switchEpisode(-1) },
                )
                // 播放/暂停（主键：**更大**——FR-709 的"过小不美观"）
                CtrlIcon(
                    icon = if (s.isPlaying) DyIcons.Pause else DyIcons.Play,
                    label = if (s.isPlaying) "暂停" else "播放",
                    enabled = true,
                    primary = true,
                    onClick = { vm.togglePlay() },
                )
                // 下一集（末集禁用）
                CtrlIcon(
                    icon = DyIcons.SkipNext,
                    label = "下一集",
                    enabled = vm.hasEp(1),
                    onClick = { vm.switchEpisode(1) },
                )
                // 退出全屏 / 进全屏
                CtrlIcon(
                    icon = if (isLandscape) DyIcons.FullscreenExit else DyIcons.Fullscreen,
                    label = if (isLandscape) "退出全屏" else "全屏",
                    enabled = true,
                    onClick = {
                        if (isLandscape) {
                            fullscreen.exit()
                        } else {
                            // 竖向视频不全屏横屏：只隐藏系统栏，避免把竖向画面拧成
                            // 横向两侧大黑边。宽高比未知时按横屏处理。
                            val landscape = !(s.videoW > 0 && s.videoH > 0) || s.videoW >= s.videoH
                            fullscreen.enter(landscape)
                        }
                        vm.scheduleHide()
                    },
                )

                Spacer(Modifier.weight(1f))
                Text(
                    text = if (s.totalTime > 0) fmt(s.totalTime) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }

            // 连播 / 清晰度：低频开关，**只在非全屏时显示**，且压到最底部一行小字，
            // 全屏时它们让位给画面（FR-707 的"不占画幅"）
            if (!isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "自动连播：${if (s.autoNext) "开" else "关"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clickable { vm.setAutoNext(!s.autoNext) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    if (vm.showQualityEntry) {
                        Text(
                            text = "清晰度：${vm.qualityLabel()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .clickable { vm.toggleQualitySheet() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        }

        // ---- 滑动 seek 提示已迁出 ----
        // 旧实现在这里（`ControlOverlay` 内部），随控制层显隐。
        // 真机复现 P-02：控制层隐藏后滑动完全没有反馈。
        // 现在由 [SeekPreviewOverlay] 在 `PlayerScreen` 顶层绘制，独立于控制层。
    }
}

/**
 * 控制行按钮（FR-709）。
 *
 * 【尺寸口径】初版 `CtrlText` 是 64×56dp、图标用 `titleSmall`（17sp），
 * 用户反馈「暂停键和全屏键过小不美观」。现在：
 *  - 普通键：**40dp 见方**，图标视觉 **22dp**
 *  - 主键（播放/暂停）：**46dp 见方**，图标视觉 **28dp**
 *  - 圆形半透明底：给图标一个明确的可点视觉边界，而不是裸字形
 * （2026-10-25 再压：用户反馈上下控制栏占画幅过多，48/56 再收一档，
 *  热区略低于 44dp 指南但四周空白仍可点，播放器场景可接受）
 *
 * 【图标从字形改为矢量】原先 `⏸ ▶ ⏮ ⏭ ⤡ ⤢` 是字体字形：粗细随系统字重漂移、
 * 部分 ROM 缺字渲染成方框，且尺寸受 `fontSize` 与行高约束，"视觉 ≥24dp"根本无法
 * 保证。改用 [DyIcon] 后尺寸就是 `size` 参数，与字体无关（FR-709 ③）。
 *
 * @param icon 图标；为 null 时走 [label] 文字分支（倍速键用）
 * @param label 文字分支的内容 / 图标分支的无障碍描述
 */
@Composable
private fun CtrlIcon(
    icon: DyIcon? = null,
    label: String = "",
    enabled: Boolean,
    primary: Boolean = false,
    isText: Boolean = false,
    onClick: () -> Unit,
) {
    // 【变量名不能叫 size】它会遮蔽 `Modifier.size()` 扩展函数，
    // 导致 `Modifier.size(size)` 解析失败（真机编译时踩到）。
    val buttonSize = if (primary) 46.dp else 40.dp
    Box(
        modifier = Modifier
            .size(buttonSize)
            .background(
                if (primary) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
        when {
            isText -> Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )

            icon != null -> DyIcon(
                icon = icon,
                tint = tint,
                size = if (primary) 28.dp else 22.dp,
                contentDescription = label.ifEmpty { null },
            )
        }
    }
}

/** 毫秒 → `m:ss` 或 `h:mm:ss` */
private fun fmt(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    } else {
        "$m:${sec.toString().padStart(2, '0')}"
    }
}
