# 单 Gradle 工程承载 Android + HarmonyOS + iOS：实测结论

调研对象：本工程（`D:\workspace\dengying`），由 CPF 毕昇模板生成，单个 `:composeApp` 同时声明
`androidTarget` / `iosX64` / `iosArm64` / `iosSimulatorArm64` / `ohosArm64`。
本机环境：Windows + JDK 18（`C:/Program Files/Java/jdk-18.0.2.1`），Gradle 8.9，Kotlin 2.2.21-1.2.0，
Compose Multiplatform 1.9.2-1.2.0，AGP 8.6.0。

## 1. 结论

同一套构建脚本、同一个 `:composeApp` 模块可以同时挂三个平台的 target，
**Android 与 HarmonyOS 已在本机实测通过；iOS 在 Windows 上只能做到 klib 编译，且需要补平台库（见 §3）。**

平台能力矩阵：

| 宿主 | Android | HarmonyOS native | iOS klib | iOS link / Xcode / 模拟器 / 真机 |
|---|---|---|---|---|
| Windows（本机） | 实测通过 | 实测通过 | 补平台库后通过 | 不可能（无 Xcode / Apple SDK） |
| macOS | 预期通过（未验证） | 预期通过（未验证） | 预期通过（未验证） | 预期通过（未验证） |

macOS 一行全部标「未验证」：本机没有 Mac，不能凭发行包内容推定构建结果。

## 2. 实测证据（本次命令输出）

| 任务 | 结果 | 关键输出 |
|---|---|---|
| `:composeApp:assembleDebug` | 通过 | `BUILD SUCCESSFUL in 6s`，42 tasks，产物 `composeApp/build/outputs/apk/debug/composeApp-debug.apk` |
| `:composeApp:linkDebugSharedOhosArm64` | 通过 | `BUILD SUCCESSFUL in 3m 49s`，产物 `composeApp/build/bin/ohosArm64/debugShared/libkn.so` + `libkn_api.h` |
| `:composeApp:publishDebugBinariesToHarmonyApp` | 通过 | `BUILD SUCCESSFUL in 1m 51s`，已复制到 `harmonyApp/entry/libs/arm64-v8a/` 与 `harmonyApp/entry/src/main/cpp/include/arm64-v8a/` |
| `:composeApp:compileKotlinIosArm64`（补平台库前） | 失败 | `BUILD FAILED in 33s`，4 条错误见 §3 |
| `:composeApp:compileKotlinIosArm64`（补平台库后） | 通过 | `BUILD SUCCESSFUL in 3m 30s` |
| `:composeApp:compileKotlinIosX64` + `compileKotlinIosSimulatorArm64`（补平台库后） | 通过 | `BUILD SUCCESSFUL in 16s` |

## 3. iOS 编译失败的真实原因

补平台库之前的错误：

```
e: .../composeApp/src/iosMain/kotlin/com.dengying.player/MainViewController.kt:5:28
   Cannot access class 'UIViewController'. Check your module classpath for missing or conflicting dependencies.
e: .../composeApp/src/iosMain/kotlin/com.dengying.player/Platform.ios.kt:2:8 Unresolved reference 'platform'.
e: .../composeApp/src/iosMain/kotlin/com.dengying.player/Platform.ios.kt:5:33 Unresolved reference 'UIDevice'.
e: .../composeApp/src/iosMain/kotlin/com.dengying.player/Platform.ios.kt:5:77 Unresolved reference 'UIDevice'.
```

根因不是 `iosMain` 的 `dependsOn` 接线，也不是 Compose 依赖解析：
`androidx.compose.ui.window.ComposeUIViewController` 已经能解析（它来自
`org.jetbrains.compose.ui:ui-uikit`），真正缺的是 Kotlin/Native 的 **Apple 平台库 klib**。

各发行包 `klib/platform` 目录的实际内容对比：

| 发行包 | 自带平台库 |
|---|---|
| CPF `windows-x86_64-2.2.21-1.2.0`（本机在用） | android_×4、linux_×3、mingw_x64、ohos_arm64、ohos_x64——**无任何 Apple 平台库** |
| 官方 `windows-x86_64-2.2.20` | android_×4、linux_×3、mingw_x64、ios_arm64、ios_x64、ios_simulator_arm64、macos/tvos/watchos——**无 ohos** |
| CPF `macos-aarch64-2.2.21-1.2.0`（从镜像流式核对 tar 目录） | 上两者的并集：Apple 全家桶 + ohos_arm64/ohos_x64，共 24 个目录 |

即：CPF 的 Windows host 包为了塞进 OHOS 目标，砍掉了 Apple 平台库；
它的 macOS host 包是完整的，**一个 CPF 工具链在 Mac 上就能同时出 iOS 与 OHOS**，
问题只存在于 Windows 侧。镜像上 `macos-aarch64` / `macos-x86_64` / `linux-x86_64`
三个 host 的 `kotlin-native-prebuilt-2.2.21-1.2.0-*.tar.gz` 均返回 HTTP 200，包确实发布。

修复方式（已落地）：`runscript/prepareApplePlatformLibs.ps1` 从本机另一份官方 Kotlin/Native
发行包里把 `ios_arm64` / `ios_x64` / `ios_simulator_arm64` 三个目录补进 CPF 的发行包，
优先取同一 2.2.x 版本线。脚本幂等，重复执行只报告「已存在」。
已验证：删掉这 3 个目录后重跑脚本 → 三个 iOS target 重新编译通过。

限制说明：

- 这是**仅用于 Windows 侧 klib 编译验证**的本机手段，不进仓库、不影响 macOS 构建；换机器要重跑一次脚本。
- klib 编译通过 ≠ iOS 可运行。framework link、Xcode 构建、模拟器与真机仍然只能在 macOS 上做。
- 借来的平台库与 CPF 编译器版本不完全一致（2.2.20 vs 2.2.21），只在 klib 编译层面成立，不能作为链接依据。

## 4. 把现有三工程迁进单工程的代价

`D:\workspace\dengY` 下三工程当前工具链与本工程差异：

| 项 | `DengYing` / `DengYingIos`（官方线） | 本工程 / CPF 线 | 迁移动作 |
|---|---|---|---|
| Kotlin | 2.4.21 | 2.2.21-1.2.0 | 降级 |
| Compose Multiplatform | 1.11.1 | 1.9.2-1.2.0 | 降级 |
| AGP | 8.13.2 | 8.6.0 | 降级 |
| Gradle | 8.14.3 | 8.9 | 降级 |
| `DengYingOhos` | CPF 2.2.21-1.1.0 / CMP 1.9.2-1.1.0 | CPF 2.2.21-1.2.0 | 小步升级 |

主要风险（按严重程度）：

1. **Kotlin 2.4.21 → 2.2.21 降级**：现有 `shared` / `ui` 源码与依赖 ABI 都建立在 2.4.21 上，
   降级要逐文件排查语言特性、stdlib API 与库版本；这是全盘迁移最大的成本，且没有现成验证。
2. **iOS 侧验证断档**：`DengYingIos` 现在跑官方 KGP + AVPlayer + cinterop，Xcode 链路是既有成果；
   换成 CPF 后 iOS framework link、Xcode 构建、真机全部要在 Mac 上重新验证，Windows 给不出证据。
3. **OHOS 侧要动版本线**：`DengYingOhos` 从 1.1.0 升 1.2.0，属于可控但需重跑验证的变更。
4. **模块边界要重做**：三套 `:app` / `:shared` / `:ui` 合并进单 `:composeApp`（或同一构建的多模块），
   debug 包 `.debug` 后缀隔离、依赖方向契约、版本单一事实源、`docs/` 追踪体系都要重新设计。

收益是「一套源码、一次编译出三端」，代价是**全平台工具链降级 + iOS/OHOS 需在真实平台重新验证**。
代价只集中在版本线上，不在目录结构上：目录合并本身不难，难的是把 Kotlin 2.4.21 的代码退回 2.2.21。

## 5. 建议

- 若接受工具链降级：可以迁移，按「先建单工程骨架 → 挪 `shared` 平台无关代码 → 逐端接壳 → 逐端验证」推进，
  iOS 与 OHOS 的验收必须放到 macOS / DevEco 环境完成。
- 若不接受降级：**单仓库 + 双 Gradle 构建**更稳——Android/iOS 用官方 KGP，OHOS 用 CPF 的 composite build
  （`includeBuild`），共享源码用 `srcDirs` 指向同一份目录。目录上仍是「一个项目」，但不强求一条版本线。
- 无论选哪条，先把 `runscript/prepareApplePlatformLibs.ps1` 当作 Windows 上的既有前提写进开发说明。
