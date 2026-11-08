# iosApp —— CMP iOS 壳工程（P5-6 收敛版）

**整工程仍未在 macOS 上构建过**：本目录全部文件在 Windows 上落盘。
P5-6 把能静态做到的全做了——入口符号名、工程层级、framework 搜索路径与链接方式、
ATS 例外、版本号、脚本相位顺序，全部按 KGP 源码实证改到一致，并逐项校验通过。
**剩下的只有「Xcode 能不能打开、能不能链接、能不能跑」**，见文末。

## 文件清单

| 文件 | 作用 |
|---|---|
| `DengYing.xcodeproj/project.pbxproj` | 单 target `DengYing`。构建相位顺序 `Compile Kotlin Framework → Sources → Frameworks → Resources`；`FRAMEWORK_SEARCH_PATHS = "$(SRCROOT)/../ui/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"`；`OTHER_LDFLAGS = ("$(inherited)", "-framework", DengYing)`；`ENABLE_USER_SCRIPT_SANDBOXING = NO` |
| `DengYing.xcodeproj/xcshareddata/xcschemes/DengYing.xcscheme` | 共享 scheme（`BlueprintIdentifier = 1A000501`），`xcodebuild -scheme DengYing` 直接可用，不依赖 Xcode 自动生成 |
| `DengYingApp.swift` | SwiftUI 入口。`UIViewControllerRepresentable` 包 `MainViewControllerKt.MainViewController()`，外层 `OrientationHostingController` 回答 `supportedInterfaceOrientations` / `prefersStatusBarHidden` |
| `Info.plist` | bundle 标识、版本（与 Android 同步：`1.1.0` / `1000100`）、方向、启动屏、`NSAppTransportSecurity` 例外（见下） |
| `build-phase.sh` | 构建相位脚本，末行 `exec ./gradlew :ui:embedAndSignAppleFrameworkForXcode` |

## 三条接线为什么是这样（读 KGP 源码得出，不是猜的）

依据 `kotlin-gradle-plugin-2.4.21-sources.jar` 里的 `XcodeEnvironment.kt` 与
`AppleXcodeTasks.kt`：

1. `embedAndSignAppleFrameworkForXcode` 把 framework 写到
   `<project>/build/xcode-frameworks/<CONFIGURATION>/<SDK_NAME>/`，
   **然后自己**拷进 `TARGET_BUILD_DIR/FRAMEWORKS_FOLDER_PATH` 并重签。
   ⇒ 所以搜索路径必须是 `xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`；
   ⇒ 所以**不能**再手写 Embed Frameworks 相位 + `CodeSignOnCopy`（那是重复签名）。
2. 没有 FileReference 之后，链接靠 `OTHER_LDFLAGS` 的 `-framework DengYing`。
3. KGP 的 `CheckSandboxAndWriteProtectionTask` 会因脚本沙箱拒绝写入而失败
   ⇒ `ENABLE_USER_SCRIPT_SANDBOXING = NO` 是硬要求。
4. 脚本相位必须排在 Sources **之前**，且带 `alwaysOutOfDate = 1`（否则 Xcode
   认为无输入无输出，直接跳过）。

## `Info.plist` 的 ATS 例外

`NSAllowsArbitraryLoads = false`，只精确放开与 `core/Sources.kt` 里明文端点
一一对应的三项：`43.248.128.122`、`103.36.167.27`、`qkys.qukanwh.com`，
均 `NSExceptionAllowsInsecureHTTPLoads = true` 并钉 `TLSv1.2`。

**已知遗留**：`shared/src/commonMain/kotlin/com/dengying/player/data/source/adapters/JuzhiRoot.kt:32`
的 `ROSS_URL` 会运行期发现明文根域，无法静态白名单。当前取舍是不放开全局
`NSAllowsArbitraryLoads`；该源预计报 `NSURLErrorDomain -1022`（`AvPlayerEngine.classify()`
已单列并归入 `Network`）。真机实测后再定「标 `playGap`」还是「加域通配例外」。

## 在 macOS 上要做的三件事

```sh
plutil -lint iosApp/Info.plist
plutil -lint iosApp/DengYing.xcodeproj/project.pbxproj
xcodebuild -list -project iosApp/DengYing.xcodeproj
```

然后把 `DEVELOPMENT_TEAM` 填成真实 Team ID（现在是空串，写假值只会让
`xcodebuild` 报更难的错），再 `xcodebuild -scheme DengYing -sdk iphonesimulator`.

**不需要 xcodegen 重建**：手写工程已按上节四条实证修到正确形态。

## 真机分发前提（与 docs/09 §9.5 一致）

免费 Apple ID 签名 7 天过期且不可分发他人；给朋友长期安装需付费开发者账号
（TestFlight 或 Ad Hoc）。这属打包环节，不影响本工程骨架的编译验证。
