# 灯影 · Android + 鸿蒙（KMP / CPF 毕昇工具链）

无广告、无账号、无埋点的本地影视播放器。本工程是**一套 Kotlin 源码同时产出 Android APK 与
鸿蒙 native 库（`libdengyui.so`）** 的那一半；iOS 那一半在同级工程
[`DengYingIos`](../DengYingIos)。

## 为什么是两个工程

鸿蒙侧只能用 CPF（毕昇）定制的 Kotlin 线：`KGP 2.2.21-1.1.0` + `CMP 1.9.2-1.1.0`
（官方 KGP 根本没有 `ohosArm64` / `ohosX64` target）。iOS 侧要 `Kotlin 2.4.21` + `CMP 1.11.1`。
两条线的 Kotlin 版本互斥，塞不进同一个 Gradle 构建，所以拆成两个自包含的平级工程：

| 工程 | target | 工具链 | 平台壳 |
|---|---|---|---|
| **DengYingOhos**（本工程） | `androidTarget` / `jvm` / `ohosArm64` / `ohosX64` | CPF 2.2.21-1.1.0 + CMP 1.9.2-1.1.0 | `app/`（Android）+ `harmony/`（DevEco/hvigor） |
| `DengYingIos` | `androidTarget` / `jvm` / `iosArm64` / `iosSimulatorArm64` | 官方 2.4.21 + CMP 1.11.1 | `app/`（Android）+ `iosApp/`（Xcode） |

两个工程没有 `srcDir` 跨仓引用，各自的 `shared/`、`ui/` 是完整拷贝。

## 模块

```
app/      Android 壳（Activity、ExoPlayer 装配、debug 广播）
shared/   跨平台逻辑：core / domain / data / player / platform
            commonMain  + androidMain + jvmMain + jvmSharedMain
            commonMain  + ohosMain（ArkTS interop 的 actual）
ui/       跨平台 Compose UI：pages / components / navigation / theme / viewmodel
            commonMain（同一份界面源码，Android 与鸿蒙共用）
            androidMain（PlayerView 互操作） / ohosMain（Skia、Poster、平台桩）
            产物：ohosArm64/ohosX64 各出一个 sharedLib = libdengyui.so
harmony/  鸿蒙壳工程（DevEco Studio / hvigor），不参与 Gradle 构建
            entry/src/main/ets  —— ArkTS 页面，import { Compose } from 'compose'（compose.har）
            entry/src/main/cpp  —— napi_init.cpp：dlopen("libdengyui.so") 后 dlsym 全部 @CName 入口
```

依赖方向单向：`app → ui → shared`。

## 构建

前置：JDK 17+（本机用 JDK 18）、Android SDK Platform 36 + Build-Tools 36.0.0。

```bash
# Android（与原来一致）
./gradlew :app:assembleDebug

# 鸿蒙 native 库（先出 .so，再交给 hvigor 打包 HAP）
./gradlew :ui:linkDebugSharedOhosX64      # → ui/build/bin/ohosX64/debugShared/libdengyui.so
./gradlew :ui:linkDebugSharedOhosArm64
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

- **Android**：功能完整，与同级 [`DengYing`](../DengYing) 工程一致（搜索 / 详情 / 播放 / 收藏 / 历史）。
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
