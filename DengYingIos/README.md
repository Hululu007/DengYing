# 灯影 · Android + iOS（KMP / 官方工具链）

无广告、无账号、无埋点的本地影视播放器。本工程是**一套 Kotlin 源码同时产出 Android APK 与
iOS framework** 的那一半；鸿蒙那一半在同级工程 [`DengYingOhos`](../DengYingOhos)。

## 为什么是两个工程

鸿蒙侧只能用 CPF（毕昇）定制的 Kotlin 线（`KGP 2.2.21-1.1.0` + `CMP 1.9.2-1.1.0`，
官方 KGP 没有 `ohosArm64` / `ohosX64` target）；iOS 侧要官方 `Kotlin 2.4.21` + `CMP 1.11.1`。
两条线的 Kotlin 版本互斥，塞不进同一个 Gradle 构建，所以拆成两个自包含的平级工程：

| 工程 | target | 工具链 | 平台壳 |
|---|---|---|---|
| `DengYingOhos` | `androidTarget` / `jvm` / `ohosArm64` / `ohosX64` | CPF 2.2.21-1.1.0 + CMP 1.9.2-1.1.0 | `app/` + `harmony/` |
| **DengYingIos**（本工程） | `androidTarget` / `jvm` / `iosArm64` / `iosSimulatorArm64` | 官方 2.4.21 + CMP 1.11.1 | `app/` + `iosApp/` |

两个工程没有 `srcDir` 跨仓引用，各自的 `shared/`、`ui/` 是完整拷贝。

## 模块

```
app/      Android 壳（Activity、ExoPlayer 装配、debug 广播）
shared/   跨平台逻辑：core / domain / data / player / platform
            commonMain + androidMain + jvmMain + jvmSharedMain + iosMain
ui/       跨平台 Compose UI：pages / components / navigation / theme / viewmodel
            commonMain（同一份界面源码，Android 与 iOS 共用）
            androidMain（PlayerView 互操作） / iosMain（AVPlayer 引擎、MainViewController）
            iosArm64/iosSimulatorArm64 各出一个 framework：DengYing
iosApp/   Xcode 壳工程（SwiftUI 入口 + framework 集成）
```

依赖方向单向：`app → ui → shared`。

本工程**不含** `shared/src/ohosMain`、`harmony/`、`ohosArm64()` —— 鸿蒙相关的源码与壳工程
都在同级工程 `DengYingOhos` 里。

## 构建

前置：JDK 17+（本机用 JDK 18）、Android SDK Platform 36 + Build-Tools 36.0.0。

```bash
# Android
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease
./gradlew :test                   # 领域层 + UI 层单测

# iOS framework（需要 macOS + Xcode）
./gradlew :ui:linkDebugFrameworkIosSimulatorArm64
./gradlew :ui:linkDebugFrameworkIosArm64
```

Windows 上只能跑到 Android 与 jvm 侧；iOS 的 link 任务在非 macOS 上不可用（K/N 交叉编译
只支持从 macOS 出 Apple 目标）。

`local.properties` 不入库。

## 与 `DengYing` 工程的关系

`docs/` 是本工程的历史验证记录（含逐条实测结论），其中提到 `ohos-build/` 的地方描述的是
鸿蒙侧早前的独立 Gradle 构建，现已并入同级工程 `DengYingOhos` 的 `:ui` / `:shared` 模块。

## 已知边界

- iOS 侧播放引擎走 `AVPlayer`（`ui/src/iosMain/.../player/AvPlayerEngine.kt`），
  与 Android 的 Media3 是两套实现，共用的是 `PlaybackEngineHost` 契约。
- iOS framework 产物尚未在真机上跑过（本机是 Windows，无法 link / 签名 / 安装）。
