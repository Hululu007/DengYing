# 鸿蒙播放引擎接线说明（native OH_AVPlayer）

状态：2026-11-14，分支 `feat/ohos-avplayer-engine`。播放页遮挡已修，未真机验证；
内嵌小窗仍需 Compose ↔ ArkUI interop。

## 画面（XComponent surfaceId）

1. `harmony/entry/src/main/ets/pages/ComposePage.ets` 在 Compose 下方铺一个全屏
   `XComponent(type: SURFACE)`，`onLoad` 里取 `getXComponentSurfaceId()`。
2. `setVideoSurfaceId(id)` → `napi_init.cpp` → `dlsym("dengy_set_video_surface")`
   → `OhosVideoSurfaceBridge.updateSurface()`（shared/ohosMain）。
3. bridge 调 `OH_NativeWindow_CreateNativeWindowFromSurfaceId`（API 12）生成
   `OHNativeWindow`，通知活动引擎。
4. `OhosPlayerEngine.setMedia` 的顺序是 `SetURLSource` 成功之后，才在
   INITIALIZED 状态下 `SetVideoSurface` → `Prepare`；绝不在 `SetURLSource`
   之前 `Prepare`。
   - 无 surface 时：`SetURLSource` 成功后停在 INITIALIZED 等待；surface
     到达时先绑定再补 `Prepare`（`onVideoSurfaceChanged` 的 IDLE/INITIALIZED
     路径），不会永久停在 Buffering。
   - Prepare 之后再到达的新 surface（窗口重建、折叠屏切换）：不缓存等待，
     直接保留 url/headers/当前播放位置（含尚未应用的 pending seek）/起播意图
     重新 `setMedia`，新 surface 在 Prepare 前绑好。
   - 同一 native window 上「旧 player `ReleaseSync` 成功 → 新 player
     `SetVideoSurface`」按 window 串行：等待所有未完成的 ReleaseSync，
     等待期间窗口变化则改为对**当前** window 绑定；主线程不被阻塞，不同
     window 互不影响。
   - `ReleaseSync` 失败时保留回调锚点与 window 引用（只重试一次），并把该
     window 标记为不可再绑定：新 player 不会在同一 window 上
     `SetVideoSurface`，直到 surface 重建。

限制：XComponent 是全屏的，视频铺满窗口；内嵌小窗的位置对齐需要
Compose ↔ ArkUI interop（CMP `insertInteropView`），尚未实现。
`VideoSurface` actual 在 surface 就绪时透明、未就绪时黑底；播放页根 Box
在 surface 绑定且出画时透明（见下节），二者配合才能让下层视频透出。

### BLOCKER：视频层被 Compose 画布遮挡（2026-11-13 → 2026-11-14 已修，未真机验证）

现状（修后）：`ComposePage.ets` 的 Stack 顺序仍是「XComponent 在下、Compose 在上」，
外层 `Column` 显式透明；播放页根 Box 在画面生效后不再铺黑，视频从 Compose
透明区域透出。har 的私有 `bgColor` 没有可读取的默认值：`.d.ets` 不含初始值，
字节码里虽有 `#00000000` / `Transparent` 字面量、上游示例也不传 `bgColor`，
但这些只是旁证，不能直接证明缺省透明，仍需真机确认。因此不向 `Compose({...})`
传私有属性，避免 ArkTS 编译期报错。

真正的原因（修正 2026-11-13 的判断）：

- CMP 的 `Compose` 组件**不是**整块不透明的 EGL 输出面。解包 `compose.har` 看
  `Compose.d.ets`/`CanvasNode.d.ets`：渲染走 `CanvasNode`（RenderNode fusion）或
  `XComponent(TEXTURE)`，字节码里有 `Transparent` 与单个 `#00000000` 字面量
  ——都是 ArkUI 渲染树内的普通纹理，与下层的 SURFACE XComponent 同树合成，
  透明区域按 alpha 透出下层内容。私有 `bgColor` 的缺省值未直接证明，不能把
  字面量当成「已确认的缺省透明色」。（2026-11-13 的调查只翻了 klib，没拆 har，
  误判了这一点。）
- 遮住视频的其实是 Kotlin 侧自己画的黑底：`PlayerScreen.kt` 根
  `Box(fillMaxSize().background(Color.Black))` 全屏不透明，恰好盖在视频之上；
  `VideoSurface` 的透明擦不掉父 Box 的黑。其他页面根都有不透明柔光底
  （`GlassPage`/`glassPageBackdrop`），不会意外透出视频。

修法（2026-11-14）：

- `ui/src/commonMain/.../PlayerScreen.kt`：根 Box 背景改为条件让位：
  `surfaceBound && prepared && !buffering && 无错误` 时透明，其余时机仍铺黑。
  `surfaceBound` 由 `VideoSurface` 的 `onBind` 回写。
- `harmony/entry/.../ComposePage.ets`：包住 `Compose` 的外层 `Column` 显式
  `.backgroundColor(Color.Transparent)`，不依赖 ArkUI 容器默认底色；不改 har，
  也不向 `Compose({...})` 传私有 `bgColor`。
- 各端影响：ohos —— 绑定且出画后视频透出；Android —— PlayerView 自带黑
  shutter，观感不变；桌面 —— `onBind` 恒 false，根背景恒黑，行为不变。
- `InlinePlayer`（详情页 16:9 内嵌位）维持黑底占位：视频层是全屏的，小窗
  只会看到全屏画面的错误裁切，需要 Compose↔ArkUI interop 对位后才能开洞。
- 未在真机/hvigor 上验证 ArkTS 壳与视频出画；本轮只有本机 Gradle/JVM 侧
  编译与单测验证。

#### 工具链调查（2026-11-13，只读，未改依赖）

检查 `ui-ohosArm64Main-1.9.2-1.1.0.klib` 与 `ui-arkui-ohosArm64`（klib 内
metadata 字符串），确认存在 interop 基础设施符号：

- `androidx.compose.ui.viewinterop.InteropViewHolder` / `TypedInteropViewHolder`
- `androidx.compose.ui.viewinterop.TrackInteropPlacementModifierNode`
- `InteropContainer.insertInteropView` / `removeInteropView`（成员名）
- `androidx.compose.ui.interop.arkc.ArkUINativeView` /
  `EmbeddedNativeViewRenderNode` / `C2ACApiDriver` / `ArkUIInteropContext`
- 渲染侧 `EmbeddedInteropComponent`、`nativeHidden`、`wrapContentComposable`

结论：这些是 **CMP 内部**的 Compose→ArkUI 组件（C2AC）与渲染节点，klib 中
没有找到应用代码可直接调用的公开 Composable（例如 `InteropView(...)`）；
缓存里也没有 sources jar 可核对可见性/签名。用反射式/未公开路径插入 interop
视图会依赖内部二进制布局，不具备可维护性。

#### 其余候选路径与结论（2026-11-13 的调查记录，结论按 2026-11-14 修正）

1. **Compose 播放区域打洞（per-region 透明）**：2026-11-13 的假设是 CMP 整块
   EGL 输出面不透明、必须按矩形输出 alpha；拆 har 后确认这是误判。TEXTURE
   XComponent / CanvasNode 都是同树合成的普通纹理，让 Compose 自身的父背景与
   根容器透明即可露出下层 SURFACE。**本轮已按此修复全屏播放页**；klib 中
   仍未发现公开的矩形裁剪开关，所以内嵌小窗的位置对齐仍未解决。
2. **公开 interop 视图把 native surface 放进 `VideoSurface` 的坐标/裁剪**：
   正是 CMP 内部 `insertInteropView` + `InteropContainer` 做的事，但 1.9.2-1.1.0
   未暴露公开 API。**卡在 API 缺失，不能在应用层干净实现。**
3. **XComponent 移到 Compose 之上并按矩形裁剪**：ArkUI 层序可做（给
   XComponent 设 position/width/height + clip），但视频矩形会盖住 Compose 在
   该矩形内绘制的播放控件（控制条/手势层），必须把控件改成 ArkUI 覆盖层，
   属于壳层大改；且本机无法用 hvigor 构建/验证 ArkTS 壳。**本轮不能落地。**
   若后续采用，需要：`VideoSurface` 在 Compose 里 `onGloballyPositioned` 上报
   窗口坐标 → 新增 NAPI → ArkTS 调整 XComponent 几何与 clip，并处理
   px/vp 换算与页面切换。

播放页的遮挡按上面的修法解除（未真机验证）；内嵌播放位在 interop 落地前
仍视为「黑底占位」。

## 请求头（未支持）

结论：**当前 SDK 的 native OH_AVPlayer 没有请求头入口。**

- 系统头文件 `multimedia/player_framework/avplayer.h` 只有
  `OH_AVPlayer_SetURLSource` / `SetFDSource`，没有 header 参数。
- platform klib `org.jetbrains.kotlin.native.platform.AVPlayer` 里也没有
  `OH_AVMediaSource_*`（`strings *.knm` 实测无 `MediaSource`/`Header` 符号）。
- 因此 `setMedia(url, headersJson, ...)` 收到非空 headers 时只记 warning，
  **不生效**，代码里有 `TODO(请求头)` 标记。

可选方案（按代价从低到高）：

1. **运行时按设备 API 降级**：只有 API 20+ 才有 media source header API 时，
   在 17/18 设备上仍然不可用，不能作为通用解。
2. **ArkTS `media.createAVPlayer`**：其 `AVPlayer` 支持 `url` + `header`
   （`AVFileDescriptor`/`AVDataSrcDescriptor` 另说）。代价是播放器回到 ArkTS，
   需要重建一条 Kotlin ↔ ArkTS 的命令/回调通道。
3. **本地 HTTP 代理**：在 App 内起 loopback HTTP 服务，Kotlin 侧请求转发时
   注入 header，native player 只播 `http://127.0.0.1:<port>/...`。保持 native
   播放器不变，代价是多一个本地服务与端口生命周期管理。

在选定方案前，需要鉴权头的源请视为「暂不支持」。
