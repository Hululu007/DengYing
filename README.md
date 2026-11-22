# 灯影 · Android + iOS + 鸿蒙（单工程 / KMP / CPF 毕昇工具链）

无广告、无账号、无埋点的本地影视播放器。**一套 Kotlin 源码在一个 Gradle 构建里同时产出**
Android APK、iOS framework（`DengYing.framework`）与鸿蒙 native 库（`libdengyui.so`）。

## 为什么是单工程 + CPF 工具链

鸿蒙 target（`ohosArm64` / `ohosX64`）只存在于 CPF（毕昇）定制的 Kotlin 发行版里，
官方 KGP 根本没有这个 target —— 这决定了整仓只能走 `CPF 2.2.21-1.2.0` + `CMP 1.9.2-1.2.0` 一条线。

CPF 发行版在 **macOS host** 上是 Apple + OHOS 的全并集，一个工具链能同时编 iOS 与鸿蒙；
**Windows host** 的发行包里只有 OHOS（`klib/platform` 里没有 `ios_arm64` /
`ios_simulator_arm64`），所以 Windows 上编 iOS 前必须先跑
`runscript/prepareApplePlatformLibs.ps1`，从官方发行包补 3 个 iOS 平台库。

| target | 产物 | 壳 |
|---|---|---|
| `androidTarget` | `app-debug.apk` | `app/`（Activity、ExoPlayer 装配、投屏） |
| `iosArm64` / `iosSimulatorArm64` | `DengYing.framework` | `iosApp/`（SwiftUI + Xcode） |
| `ohosArm64` / `ohosX64` | `libdengyui.so` | `harmony/`（ArkTS + DevEco/hvigor） |
| `jvm` | 无（仅供单测口径，CPF CMP 缺 jvm 变体，`:ui:jvmTest` 不可用） | — |

曾有 `DengYing`（官方线 Android）、`DengYingIos`、`DengYingOhos` 三个平级工程，
2026-11-22 起合并到本工程：三端 target 并存已验证，合并细节与未验证项见
`docs/16-单工程迁移记录.md`。

## 模块

```
app/      Android 壳（Activity、ExoPlayer 装配、GMS 投屏、debug 广播）
shared/   跨平台逻辑：core / domain / data / player / platform
            commonMain + androidMain + jvmMain + jvmSharedMain
            ohosMain（ArkTS interop 的 actual）/ iosMain（Apple 平台 actual）
ui/       跨平台 Compose UI：pages / components / navigation / theme / viewmodel
            commonMain（同一份界面源码，三端共用）
            androidMain（PlayerView 互操作）/ ohosMain（Skia、Poster、平台桩）
            iosMain（AVPlayer 引擎、UIKitView 视频层、MainViewController）
            产物：ohosArm64/ohosX64 各出一个 sharedLib = libdengyui.so
                  iosArm64/iosSimulatorArm64 各出一个 framework = DengYing.framework
iosApp/   iOS 壳工程（Xcode），Run Script 调 :ui:embedAndSignAppleFrameworkForXcode
harmony/  鸿蒙壳工程（DevEco Studio / hvigor），不参与 Gradle 构建
            entry/src/main/ets  —— ArkTS 页面，import { Compose } from 'compose'（compose.har）
            entry/src/main/cpp  —— napi_init.cpp：dlopen("libdengyui.so") 后 dlsym 全部 @CName 入口
```

依赖方向单向：`app → ui → shared`。

## 构建

前置：JDK 17+（本机用 JDK 18）、Android SDK Platform 36 + Build-Tools 36.0.0。

```bash
# Android
./gradlew :app:assembleDebug

# 鸿蒙 native 库（先出 .so，再交给 hvigor 打包 HAP）
./gradlew :ui:linkDebugSharedOhosX64      # → ui/build/bin/ohosX64/debugShared/libdengyui.so
./gradlew :ui:linkDebugSharedOhosArm64

# iOS：Windows 上先补 Apple 平台库，再验 klib 级编译
pwsh -File runscript/prepareApplePlatformLibs.ps1
./gradlew :shared:compileKotlinIosArm64 :ui:compileKotlinIosArm64
# framework 链接 / 模拟器 / 真机只能在 macOS：
#   ./gradlew :ui:linkDebugFrameworkIosSimulatorArm64   （Xcode 内部调 embedAndSignAppleFrameworkForXcode）
```

仓库顺序：`settings.gradle.kts` 把 eazytec Nexus 放在最前，且用 `includeGroupByRegex`
只放行 `org.jetbrains.(kotlin|compose|kotlinx|androidx|skiko).*` —— 该仓镜像了 CPF 定制构件，
若让 mavenCentral 先命中，官方构件会盖掉定制版（版本号不同名同版本时会静默取错）。

### 鸿蒙侧打包

```bash
# 1) 把 .so 放到壳工程（只放 thirdparty，走 CMake 单通道打包。
#    不要再放 entry/libs/x86_64/：与 CMake 中间产物重复，hvigor 00306049 拒打）
cp ui/build/bin/ohosX64/debugShared/libdengyui.so harmony/entry/src/main/cpp/thirdparty/

# 2) hvigor 打包（需 DevEco 的 JDK 与 SDK）
export JAVA_HOME="D:/DevEco Studio/jbr"
export DEVECO_SDK_HOME="D:/DevEco Studio/sdk"
"D:/DevEco Studio/tools/hvigor/bin/hvigorw.bat" --mode module \
  -p product=default -p module=entry@default -p buildMode=debug assembleHap --no-daemon
```

`harmony/entry/libs/compose.har` 是社区 CMP-ohos 运行时（byteCodeHar，硬编码 `libentry.so`
与 6 个 fusion NAPI 名字），不可替换成别的版本。

## 当前状态

- **Android**：功能完整（搜索 / 详情 / 播放 / 收藏 / 历史 / GMS 投屏），源码以本工程为准；
  `:shared:jvmTest` 715 例、`:shared:testDebugUnitTest` 173 例、`:ui:testDebugUnitTest` 39 例、
  `:app:testDebugUnitTest` 37 例全绿（2026-11-22 实测）。
- **iOS**：`iosMain` / `iosApp` 已并入，Windows 侧只能验到 klib 编译（`compileKotlinIosArm64` 通过）；
  framework 链接、Xcode 构建、模拟器与真机运行**未验证**，需 macOS。
- **鸿蒙**：`:shared` 与 `:ui` 的 ohos target 编译、链接、`.so` 导出、HAP 安装启动全部打通；
  ArkUI 布局树里 XComponent 与 RenderNode 都已正确挂载，Compose 组合与重组在跑
  （`SpikeScreen` 的 `first composition ran` / `recomposed` 有日志）。**但首帧始终没有提交上屏**，
  界面停在 `startWindowIcon`（白底大 D）。

  已定位到的现象：fusion 后端下 `nativeOHRenderNotifyRedraw` 每帧被调（~60fps），
  `nativeOHRenderNodeDraw` / CanvasNode.draw **0 次**；skia 后端下 `eglSwapBuffers` 报
  `error 1`、RenderService 侧 `waitfence timeout` + `gbuffer 0`。详细取证见
  `docs/04-验证记录.md` 的 CMP/鸿蒙段落。

  目前 `ui/src/ohosMain/.../ComposeHost.ohos.kt` 渲染的是 `SpikeScreen`（一个 material3 计数器），
  不是正式 app 导航——先用最小 Compose 树把「CMP 能否出图」与「业务代码能否跑」解耦。
  业务接线要等首帧打通之后。

## 文档

`docs/` 是本工程的历史验证记录（含逐条实测结论）。其中提到 `ohos-build/` 的地方描述的是
鸿蒙侧早前的独立 Gradle 构建，现已并入本工程的 `:ui` / `:shared` 模块。

`local.properties` 不入库。
