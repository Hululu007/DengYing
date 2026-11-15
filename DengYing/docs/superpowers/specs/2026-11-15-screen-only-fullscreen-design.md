# 设计：详情页内嵌全屏 = 纯屏幕形态切换（不触碰播放状态）

日期：2026-11-15 ｜ 来源需求（用户原话）：「全屏只更改屏幕状态不更改视频播放状态」
范围：DengYing / DengYingIos / DengYingOhos 三端 commonMain 同步；只改「详情页内嵌位 → 全屏」这一条路径。

## 1. 问题与根因

现状：详情页内嵌位点全屏键 → `DetailViewModel.toFullscreen()`（`ui/src/commonMain/kotlin/com/dengying/player/ui/viewmodel/DetailViewModel.kt:868`）构造一条完整 `Routes.player(...)` 路由 → 导航到独立 PLAYER 页 → 新建 `PlayerViewModel` → 新页 `surfaceActive()` / `enter(landscape=true)`，返回时再反向一遍。

后果（代码/真机已证）：

- 表面计数 +1/-1 抖动；返回时 `resumeInlineIfPlaying()` 重挂引擎；
- `player-adopt-session` / `startEngine` 等会话接管路径可能重复触发；
- 220ms 转场期间 SurfaceView 残帧；
- 「切全屏」在架构上被当成「换页」，而它本只是「换屏幕形态」。

## 2. 目标 / 非目标

**目标**：内嵌位点全屏 = 同一详情页内把**同一个** `InlinePlayer`（同一 `VideoSurface`、同一全局引擎、同一会话）从 16:9 内嵌形态切为铺满全屏形态。播放状态（`PlaybackSession`、引擎、进度、缓冲）零操作。

**非目标**：

- 不动 DEBUG_PLAY / 历史直达 / 自动起播走 PLAYER 路由的路径（`playEpisode`、`pendingPlayerRoute` 其余写入点保持原样）；
- 不动 `PlayerScreen` 独立播放页；
- 不改 `PlaybackSession` / `PlaybackEngineHolder` 任何契约。

## 3. 方案

### 3.1 状态（DetailViewModel）

新增可观察状态，与 `pendingPlayerRoute` 同款写法（`var ... by mutableStateOf`）：

```kotlin
/** 纯屏幕形态：true = 详情页内嵌位铺满全屏。只表示屏幕形态，不表示播放状态。 */
var screenOnlyFullscreen by mutableStateOf(false)
    private set

fun enterScreenFullscreen()   // 守卫 hasSession；置 true；探针 fullscreen-screen-only enter
fun exitScreenFullscreen()    // 置 false；探针 fullscreen-screen-only exit
```

- **状态放 VM 而非 Composable**：Android 转屏会重建 Activity，`remember{}` 丢；VM 天然存活，与 `inlineState` 同生命周期。
- **VM 只管状态与探针，不碰平台**：`FullscreenState`（方向/系统栏）由 UI 层持有，VM 不引用平台类型——与「VM 不持有导航器」同一分层原则。
- `toFullscreen()` 删除（唯一调用点是内嵌全屏键），连同这次 `Routes.player` 构造与 `pendingPlayerRoute` 写入一起消失；其余写入点不动。

### 3.2 组件（InlinePlayer）

签名加两个参数（`ui/src/commonMain/kotlin/com/dengying/player/ui/components/InlinePlayer.kt:71`）：

```kotlin
fullscreen: Boolean = false,          // true = 全屏形态
onExitFullscreen: () -> Unit = {},    // 全屏键的退出回调
```

- 根 Box（`:170-176`）：`fullscreen=true` 时 `fillMaxSize()`，否则保持 `fillMaxWidth().aspectRatio(16f/9f)`。
- 渲染面（`:259-264`）不动：`VideoSurface(engine, Modifier.fillMaxSize())` 同一实例继续工作。
- 控制栏/手势（`:383-492`）全部保留；全屏键（`:486-490`）在全屏态换为 `DyIcons.FullscreenExit` + 「退出全屏」+ `onExitFullscreen`（图标已存在，`DyIcons.kt:135`）。

### 3.3 页面（DetailScreen）

- 全屏态分支：`if (vm.screenOnlyFullscreen)` 只渲染 `InlinePlayer(modifier = Modifier.fillMaxSize(), fullscreen = true, ...)`；否则渲染现有整页（返回栏 + 内容列）。
- 方向门控：无条件 `ConsumePortraitOrientation()`（`DetailScreen.kt:100`）改为 `if (!vm.screenOnlyFullscreen)`，否则进入全屏形态的下一帧就被顶回竖屏。
- 平台效果由 UI 层执行：进入 = `fullscreen.enter(landscape = true)`；退出 = `fullscreen.exit()`。与 VM 状态在同一回调里成对调用。

### 3.4 返回键

- DetailScreen 增加 `PlatformBackHandler(enabled = vm.screenOnlyFullscreen) { 退出全屏形态 }`；`enabled` 是可观察状态，满足 `BackHandler.kt` 契约。
- Android 优先级：`OnBackPressedDispatcher` 后注册者优先；NavGraph 级 `rememberFullscreenState()` 的回调先注册，DetailScreen 的后注册 → 全屏态返回键先被本页消费（仅退形态），形态退出后放行。
- iOS / OHOS / JVM：`PlatformBackHandler` 为空实现 → 全屏态只能靠「退出全屏」键退出；iOS 侧滑返回是既有平台限制（`BackHandler.ios.kt` 的 TODO(Mac)），如实标注。

### 3.5 NavGraph

保持不变。`LaunchedEffect(currentRoute) { ... fullscreen.exit() }`（`NavGraph.kt:68-82`）只在路由变化时触发；进入全屏形态不改路由，不会被覆盖。离开详情页/播放页时它照常兜底收敛竖屏。

### 3.6 平台行为矩阵

| 平台 | 方向/系统栏 | 全屏形态退出方式 | 备注 |
|---|---|---|---|
| Android | `enter` 锁横屏+隐系统栏（NavGraph SideEffect） | 返回键 / 退出键 | 真机验证 |
| iOS | `IosOrientationLock` 横屏锁 | 退出键（返回拦截空实现） | 编译验证；Swift 外壳既有 |
| OHOS | no-op（`Platform.ohos.kt`） | 退出键 | 形态=窗口内铺满，方向不变 |
| JVM | no-op | 退出键 | 同上；供测试 |

### 3.7 不变式（评审清单）

进入/退出全屏形态的前后，以下调用**一次都不许出现**：`setMedia` / `play()` / `pause()` / `seekTo` / `resolveUrl` / `release` / `PlaybackSession.stop()` / `surfaceActive()` 计数变化 / `Routes.player` 构造。探针上表现为：切形态前后 `pos` 连续、`enginePlaying` 不变、无 `player-adopt-session` / `startEngine` 新增。

## 4. 边界与错误处理

- 无会话点全屏键：控制栏仅在会话激活后出现，`enterScreenFullscreen()` 再加 `hasSession` 守卫双保险。
- 转屏重建：VM 状态存活；重建后 `rememberFullscreenState()` 是新对象但 `enter/exit` 无状态，NavGraph 的 SideEffect 按 `LocalConfiguration.orientation` 恢复系统栏。
- ON_STOP/ON_START：`clearInlineUi()` / `resumeInlineIfPlaying()` 语义不变；全屏形态下退后台再回来，`hidden` 复位后仍是全屏形态（状态在 VM）。

## 5. 测试与验收

1. **JVM 状态机单测**（`ui/src/jvmTest`，DengYing/Ios 两端跑）：enter 守卫（无会话不进入）、enter/exit 幂等与往返、转屏重建后状态保持（直接复用同 VM 实例断言）。
2. **三端编译/测试**：`runscript/full-check.sh` 三份全绿（exit 0）。
3. **真机（Android）**：内嵌播放入全屏 → 探针无 `adopt/startEngine`，`pos` 连续；返回键退形态 → 内嵌位继续播；再按返回 → 正常回首页。录探针证据入 `docs/04`。
4. **文档回写**：三端 `docs/02` FR-711 行更新；`docs/04` 追加实测小节。

## 6. 交付物

- 三端各 3 个文件改动：`DetailViewModel.kt` / `InlinePlayer.kt` / `DetailScreen.kt`（各端行尾保持原样：前三个文件 CRLF，`Fullscreen.kt`/`NavGraph.kt` 本设计不改）。
- JVM 测试新增；三端版本号 bump 至 **1.3.6 / 1000306**（1.3.5 已装真机，按「版本号随迭代递增」约定 bump patch 位）；文档回写。
