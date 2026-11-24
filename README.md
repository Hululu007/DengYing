# 灯影 DengYing

无广告、无账号、无埋点的本地影视播放器。一套 Kotlin 代码，同时覆盖 Android、iOS 与鸿蒙三端。

A local video player with no ads, no account, and no tracking — one Kotlin codebase across Android, iOS and HarmonyOS.

## 功能

- 无广告、无账号、无埋点，本地播放
- 搜索、详情、播放、收藏、历史、投屏（Android）
- 一套代码三端共用：Android 出 APK，iOS 出 framework，鸿蒙出 native 库

## 快速开始

前置：JDK 17+、Android SDK Platform 36 + Build-Tools 36.0.0。

```bash
# Android
./gradlew :app:assembleDebug
```

## 构建

| target | 产物 | 壳 |
|---|---|---|
| `androidTarget` | `app-debug.apk` | `app/`（Activity、ExoPlayer 装配、投屏） |
| `iosArm64` / `iosSimulatorArm64` | `DengYing.framework` | `iosApp/`（SwiftUI + Xcode） |
| `ohosArm64` / `ohosX64` | `libdengyui.so` | `harmony/`（ArkTS + DevEco/hvigor） |

```bash
# 鸿蒙 native 库（先出 .so，再交给 hvigor 打包 HAP）
./gradlew :ui:linkDebugSharedOhosX64      # → ui/build/bin/ohosX64/debugShared/libdengyui.so
./gradlew :ui:linkDebugSharedOhosArm64

# iOS：Windows 上先补 Apple 平台库，再验 klib 级编译
pwsh -File runscript/prepareApplePlatformLibs.ps1
./gradlew :shared:compileKotlinIosArm64 :ui:compileKotlinIosArm64
# framework 链接 / 模拟器 / 真机只能在 macOS：
#   ./gradlew :ui:linkDebugFrameworkIosSimulatorArm64
```

构建用 CPF（毕昇）定制的 Kotlin 发行版（`CPF 2.2.21-1.2.0` + `CMP 1.9.2-1.2.0`），鸿蒙 target 只存在于该发行版中。`settings.gradle.kts` 把 eazytec Nexus 放在最前，只放行 `org.jetbrains.(kotlin|compose|kotlinx|androidx|skiko).*`，避免 mavenCentral 的官方构件盖掉定制版。

### 鸿蒙侧打包

```bash
# 1) 把 .so 放到壳工程 thirdparty，走 CMake 单通道打包
#    （不要放 entry/libs/x86_64/：与 CMake 中间产物重复，hvigor 00306049 拒打）
cp ui/build/bin/ohosX64/debugShared/libdengyui.so harmony/entry/src/main/cpp/thirdparty/

# 2) hvigor 打包（需 DevEco 的 JDK 与 SDK）
export JAVA_HOME="D:/DevEco Studio/jbr"
export DEVECO_SDK_HOME="D:/DevEco Studio/sdk"
"D:/DevEco Studio/tools/hvigor/bin/hvigorw.bat" --mode module \
  -p product=default -p module=entry@default -p buildMode=debug assembleHap --no-daemon
```

`harmony/entry/libs/compose.har` 是社区 CMP-ohos 运行时，不可替换成别的版本。

## 项目结构

```
app/      Android 壳：Activity、ExoPlayer 装配、GMS 投屏
shared/   跨平台逻辑：core / domain / data / player / platform
ui/       跨平台 Compose UI：pages / components / navigation / theme / viewmodel
iosApp/   iOS 壳工程（Xcode），Run Script 调 :ui:embedAndSignAppleFrameworkForXcode
harmony/  鸿蒙壳工程（DevEco Studio / hvigor），不参与 Gradle 构建
```

依赖方向单向：`app → ui → shared`。

`local.properties` 不入库。

## 当前状态

当前版本 **1.3.29 / 1000329**（Android / iOS / 鸿蒙三端版本声明一致）。

- **Android**：功能完整（搜索 / 详情 / 播放 / 收藏 / 历史 / GMS 投屏），Release APK 可构建；`:shared:jvmTest` 718 例、`:shared:testDebugUnitTest` 173 例、`:ui:testDebugUnitTest` 88 例、`:app:testDebugUnitTest` 41 例全绿（2026-11-24 实测）。
- **iOS**：已并入本工程，Windows 侧仅验证 klib 编译；framework 链接、Xcode 构建、模拟器与真机运行未验证，需 macOS。
- **鸿蒙**：编译、链接、`.so` 导出、HAP 安装启动已打通；首页分类与搜索经 NAPI 接入并实测。详情、播放、收藏、发现/排行尚未接入，Compose 业务首帧仍未上屏，当前运行的是 NAPI 探针页。
