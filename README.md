# 灯影 DengYing

**无广告、无账号、无埋点的本地影视播放器。** 基于 Kotlin Multiplatform + Compose Multiplatform，
一套 Kotlin 源码同时覆盖 Android、iOS 与 HarmonyOS 三端。

A local video player — no ads, no account, no tracking. One Kotlin codebase built on
Kotlin Multiplatform + Compose Multiplatform ships Android, iOS and HarmonyOS from the
same sources.

## 特点 Highlights

- **纯本地播放器**：不建内容库、不做推荐、不上报任何数据，播放与收藏记录只留在设备上。
- **一套代码，三端同源**：领域逻辑、数据层与整套 Compose 界面共用同一份源码，平台差异收敛在各端 `actual` 实现与壳工程里。
- **原生引擎装配**：Android 走 Media3 ExoPlayer，iOS 走 AVPlayer，鸿蒙经 NAPI 桥装载 KMP 产出的 `libdengyui.so`。
- **文档齐全**：需求追踪矩阵、架构与模块边界、验证记录逐条可查（见各工程的 `docs/`）。

## 工程结构

鸿蒙侧使用 CPF（毕昇）定制的 Kotlin 工具链（官方 KGP 没有 `ohosArm64` / `ohosX64` target），
iOS 侧使用官方工具链，两条 Kotlin 版本线互斥，因此拆成两个自包含的平级 KMP 工程，
各自完整携带 `shared/` 与 `ui/`：

| 目录 | 平台 | 工具链 / 壳 |
|---|---|---|
| [`DengYing/`](DengYing) | Android | Kotlin + Jetpack Compose + Media3 ExoPlayer（minSdk 31 / targetSdk 36） |
| [`DengYingIos/`](DengYingIos) | Android + iOS | 官方 Kotlin 2.4.21 + CMP 1.11.1，`iosApp/` Xcode 壳（SwiftUI + AVPlayer） |
| [`DengYingOhos/`](DengYingOhos) | Android + HarmonyOS | CPF 毕昇 KGP 2.2.21-1.1.0 + CMP 1.9.2-1.1.0，`harmony/` DevEco 壳（ArkTS + NAPI） |

## 构建

前置：JDK 17 或更高（用 JDK 18 验证过）、Android SDK Platform 36 + Build-Tools 36.0.0。

```bash
# Android（三个工程任选其一）
cd DengYing
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :testDebugUnitTest      # 领域层单测

# iOS framework（需要 macOS + Xcode）
cd DengYingIos
./gradlew :ui:linkDebugFrameworkIosSimulatorArm64
./gradlew :ui:linkDebugFrameworkIosArm64

# 鸿蒙 native 库（需要 DevEco Studio 的 JDK / SDK / hvigor 打包 HAP）
cd DengYingOhos
./gradlew :ui:linkDebugSharedOhosArm64   # → ui/build/bin/ohosArm64/debugShared/libdengyui.so
```

各端详细构建步骤、测试与已知边界，见各工程 `README.md` 与 `docs/`。
