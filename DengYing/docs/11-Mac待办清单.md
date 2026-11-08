# 11. Mac 待办清单 —— P5-6 收敛版

**定位**：本清单是 P5 六类 28 项工单的**逐项终审结果**。P5-6 在 Windows 上把
「凡 Windows 能实做或实跑的」全部做完，剩下的才是真正需要 macOS 的工作量。

**每一项只有两种状态**：

- **done-on-Windows** —— 附证据（命令 + 输出摘句）。Mac 上无须重做。
- **Mac-only** —— 附原因：为什么 Windows 上做不了，Mac 上要做什么。

**收敛结果：28 项 = 13 项 done-on-Windows + 15 项 Mac-only。**

**P5-6 收官基线（Windows 实测全绿）**

| 门槛 | 命令 | 结果 |
|---|---|---|
| klib 四任务 | `:shared:compileKotlinIosArm64` `:shared:compileKotlinIosSimulatorArm64` `:ui:compileKotlinIosArm64` `:ui:compileKotlinIosSimulatorArm64` | `--rerun-tasks` 强制全跑：`BUILD SUCCESSFUL in 25s` / `12 actionable tasks: 12 executed`，零 `e:` 行 |
| 663 例 0 失败 | `:shared:jvmTest` + `:shared:testDebugUnitTest` + `:app:testDebugUnitTest` | `TOTAL tests=663 failures=0 skipped=0`（jvm 539 + android 99 + app 25） |
| Android 双 APK | `:app:assembleDebug` + `:app:assembleRelease` | `app-debug.apk` 42020093 B / `app-release.apk` 33320046 B |
| commonMain 纯净（import 级） | `shared/src/commonMain` + `ui/src/commonMain` 的 `android.*` / `androidx.activity` / `androidx.media3` / `java.*` / `javax.*` / `dalvik.*` / `okhttp3` / `org.apache.http` / `platform.*` | `FILES_WITH_HITS=0` |
| commonMain 纯净（klib 级） | 上表四个 klib 任务 | 编译通过即证明（`docs/09` §9.4 第 4 条要求「以 klib 编译器输出为准」） |

与 P5-4b 基线（663 例）**持平，只增不减**：P5-6 未删改任何既有测试；新增代码全部落在
`iosMain` / `iosApp`，不进 commonMain，不进 JVM 测试面。

**`TODO(Mac)` 桩计数：12 处 → 2 处。** 剩余 2 处是 `MediaCacheHost.ios.kt`（M-24，前置未满足）
与 `BackHandler.ios.kt`（M-23，已判定不做，桩注释保留为设计记录）。

---

## 一、工具链与仓库状态

| # | 状态 | 结论与证据 |
|---|---|---|
| M-1 | **done-on-Windows** | `git update-index --chmod=+x gradlew` 后 `git ls-files -s gradlew` → `100755 23d15a93… 0	gradlew`。可执行位存在 Git index 里，与文件系统无关，Mac 克隆即带。 |
| M-2 | **Mac-only** | Windows 侧已实做：JDK 18.0.2.1 + Kotlin 2.4.21 + CMP 1.11.1 下四个 klib 任务全绿，**跨主机可编译已证明**。剩下只有一项必须在 Mac 上确认：`xcodebuild -version` ≥ 16.0。这不是本仓代码问题，是环境确认。注意本机默认 `JAVA_HOME` 是 JDK 1.8，Gradle 配置期直接失败；跑任何 Gradle 任务前必须 `export JAVA_HOME="/c/Program Files/Java/jdk-18.0.2.1"`。 |
| M-3 | **done-on-Windows** | 实验三步。① `local.properties` 的 `sdk.dir` 指向 `D:\nonexistent-no-such-sdk` → `:app:compileDebugKotlin`报 `SDK location not found. Define a valid SDK location …`（证明该配置真的被读取）。② 同一状态下 `:ui:compileKotlinIosArm64 --rerun-tasks` → `BUILD SUCCESSFUL`（33 秒，`8 actionable tasks: 8 executed`，即强制真实配置 + 执行）。③ 整个删除 `local.properties`（= Mac 上的真实状态）→ 两个 iOS klib 任务 `BUILD SUCCESSFUL in 17s`。**结论：iOS 编译链与 Android SDK 完全解耦**，且 `local.properties` 已在 `.gitignore`（`.gitignore:3`），Mac 上不需要任何额外动作。`docs/09` §9.6 末行的退路（把 iOS target 收进条件块）**不需要启用**——该退路保持未使用状态，不必施作。 |

## 二、framework 链接与壳工程

| # | 状态 | 结论与证据 |
|---|---|---|
| M-4 | **Mac-only** | 已实测到底，结论是**硬阻断**，不是「没试过」。① 自然执行：`> Task :ui:linkDebugFrameworkIosArm64 SKIPPED`，`--info` 给出 `Skipping task ':ui:linkDebugFrameworkIosArm64' as task onlyIf 'Task is enabled' is false`（KGP 在非 macOS 宿主直接禁用 link 任务）。② 强制打开（init script 里 `gradle.taskGraph.whenReady { … .enabled = true }`）后任务真跑并失败，原文：`Execution failed for task ':ui:linkDebugFrameworkIosArm64'. > java.io.FileNotFoundException: D:\workspace\dengY\DengYing\ui\build\kotlin\swiftImportLdDump\iphoneos\arm64.framework.ld (系统找不到指定的路径。)`。③ 根因已定位：生成该 `.ld` 的 `dumpXcodebuildArgsIphoneos` / `fingerprintXcodebuildIphoneos` 本身是 SKIPPED（`SwiftPM import is only supported on macOS hosts`），且 Windows 版 Konan 发行版 `~/.konan/kotlin-native-prebuilt-windows-x86_64-2.4.21/bin/` 里只有 `cinterop / klib / konanc / kotlinc-native / generate-platform / konan-lldb / run_konan`，**没有 `ld` / `lipo` / `clang`**——Apple 链接器只随 Xcode 提供。**Mac 上要做**：`./gradlew :ui:linkDebugFrameworkIosArm64`真机）与 `…IosSimulatorArm64`（模拟器），验收 = `ui/build/bin/ios_arm64/debugFramework/DengYing.framework/` 内含 `DengYing` 二进制 + `Headers/DengYing.h` + `Info.plist`。**另注**：`docs/09` §9.5 首条「不在 Windows 上尝试 `linkDebugFramework`」已被本次要求覆盖，且**产生了信息**（上列 ①②③），故该条判据在原地已不成立；按硬边界不改 `docs/09`，此处留档，待 Mac 侧完成后由文档维护者一并订正。 |
| M-5 | **Mac-only** | pbxproj 已在 Windows 侧改到结构自洽（见 M-6），但「Xcode 能不能打开、能不能列出 target」只有 Xcode 能回答。**Mac 上要做**：`plutil -lint iosApp/Info.plist`、`plutil -lint iosApp/DengYing.xcodeproj/project.pbxproj`、`xcodebuild -list -project iosApp/DengYing.xcodeproj`。Windows 侧已做的等价替代：`plistlib` 解析 Info.plist 通过；pbxproj 结构与 ID 引用全部校验通过；`iosApp/DengYing.xcodeproj/xcshareddata/xcschemes/DengYing.xcscheme` 已写入（`BlueprintIdentifier = 1A000501`，`ReferencedContainer = container:iosApp/DengYing.xcodeproj`，两处均校验可解析），故 `xcodebuild -scheme DengYing` 不需要 Xcode 自动生成 scheme。**原清单推荐的 xcodegen 重建不必做了**：P5-6 已按 KGP 源码实证把手写工程修成正确形态（见 M-6），手写工程的可评审价值已经兑现。 |
| M-6 | **done-on-Windows** | 原清单 4 处缺陷全部就地修完，并**查出并修了第 5 处原清单没写的**。① 入口名：`git mv ui/src/iosMain/.../ui/IosEntry.kt …/ui/MainViewController.kt` + 函数升格为 `fun MainViewController()`，导出符号即 `MainViewControllerKt.MainViewController()`，与 CMP 官方模板同形、与 Swift 侧既有调用一致。② ATS 见 M-7。③ 工程层级：三个文件移到 `iosApp/` 与 `.xcodeproj` 同级（`SRCROOT = iosApp/`），满足 `INFOPLIST_FILE = Info.plist`、`shellScript = "$SRCROOT"/build-phase.sh`、`FRAMEWORK_SEARCH_PATHS = $(SRCROOT)/../ui/…` 三处既有假设。④ framework 引用：删掉写死 `iphonesimulator/debugFramework` 的 FileReference，改走 `FRAMEWORK_SEARCH_PATHS = "$(SRCROOT)/../ui/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"` + `OTHER_LDFLAGS = ("$(inherited)", "-framework", DengYing)`。**第 5 处（新发现）**：旧 Swift 写 `MainViewControllerKt.MainViewController().ignoresSafeArea(.all)`——`UIViewController` 不是 SwiftUI `View`，该行连类型都不成立；已改为 `UIViewControllerRepresentable` 桥接。**依据是 KGP 源码实证**（读 `kotlin-gradle-plugin-2.4.21-sources.jar` 的 `org/jetbrains/kotlin/gradle/plugin/mpp/XcodeEnvironment.kt` 与 `AppleXcodeTasks.kt`）：`embedAndSignAppleFrameworkForXcode` 自己就把 framework 拷进 `TARGET_BUILD_DIR/FRAMEWORKS_FOLDER_PATH` 并重签，故模板里手写的 Embed Frameworks + `CodeSignOnCopy` 是**重复签名**，已删；`ENABLE_USER_SCRIPT_SANDBOXING = NO` 是 KGP `CheckSandboxAndWriteProtectionTask` 的硬要求，已加。校验输出（`python /tmp/checkios.py`）：`pbxproj braces balanced / parens balanced / every object id unique 19 objects / all referenced ids resolve / every exception opts into insecure HTTP / script phase runs before Sources / no manual Embed Frameworks phase / link step pulls in the framework / RESULT: SELF-CONSISTENT`。 |
| M-7 | **done-on-Windows** | `iosApp/Info.plist` 加 `NSAppTransportSecurity`：`NSAllowsArbitraryLoads = false`、`NSAllowsLocalNetworking = true`、`NSExceptionDomains` 精确三项 = `43.248.128.122`、`103.36.167.27`、`qkys.qukanwh.com`（均 `NSExceptionAllowsInsecureHTTPLoads = true`；前两项 `NSIncludesSubdomains = false`，`qkys` 为 `true`；均钉 `TLSv1.2`）。三项来源是 `shared/src/commonMain/kotlin/com/dengying/player/core/Sources.kt:381,389,409` 的明文 `http://` 端点（其余 `http://` 命中是注释/测试字面量）。校验项：`plist has NSAppTransportSecurity / loads disabled / exception domains == the 3 plaintext endpoints / every exception opts into insecure HTTP`。**已知遗留（必须由真机判，不由 Windows 判）**：`shared/src/commonMain/kotlin/com/dengying/player/data/source/adapters/JuzhiRoot.kt:32` 的 `ROSS_URL` 会**运行期发现**明文根域（上次实测 `fuckducai.biosyne.cn`），无法静态写进 ATS 白名单。当前取舍 = 不放开 `NSAllowsArbitraryLoads`（那会全局废掉 ATS），该源在 iOS 上预计报 `NSURLErrorDomain -1022`；`AvPlayerEngine.classify()` 已把 `-1022` 单列并归入 `Network`（刻意不归 `SourceGone`，避免无意义换线）。**Mac 上待办**：真机跑一次巨滋滋，看 `-1022` 是否出现，再裁决「该源标 `playGap`」还是「加一条域通配例外」——这是**产品取舍**，不是代码缺陷。 |
| M-8 | **Mac-only** | Windows 侧已做：`bash -n iosApp/build-phase.sh` 通过；脚本末行 `exec ./gradlew :ui:embedAndSignAppleFrameworkForXcode`；pbxproj 里脚本相位已排到 Sources **之前**，并带 `alwaysOutOfDate = 1`（校验项 `script phase runs before Sources` + `script phase has alwaysOutOfDate`）。**Mac 上要做**：Xcode 构建日志里出现 `note: build-phase platform=iphonesimulator configuration=Debug …`，且产物里 `Frameworks/DengYing.framework` 存在并已重签。 |

## 三、运行期正确性（真正决定「能不能用」）

**这一类整体 Mac-only**：全部是 ObjC/Swift 运行期行为，Windows 上连二进制都链接不出来（M-4），
不存在「在 Windows 上验一部分」的空间。P5-6 能做的是**把被测代码写实**（M-17~M-25 已完成），
让 Mac 上第一轮就真跑，而不是撞桩。

| # | 状态 | Mac 上要做什么 |
|---|---|---|
| M-9 | **Mac-only** | 冷启动到首屏。装配顺序已在 `MainViewController()` 内照 Android 对齐：`ensureStarted()` → `UiDepsHolder.init(deps)` → 再起 Compose 内容。崩在 `UiDepsHolder.get()` 即装配顺序被改坏。**此项同时承担 M-19~M-22 的运行期手感确认**（横屏进/退、亮度手势是否自动关闭、音量滑块、旋转后控制层版式），不重复计入。 |
| M-10 | **Mac-only** | `shared/src/iosMain/kotlin/com/dengying/player/data/net/HttpTransport.ios.kt` 6 条契约逐条实测：断网 `status = 0` 不抛、3xx 原样返回、非 2xx 能拿错误页正文、不设 `Accept-Encoding` 时 gzip 透明解压、取消协程真中断、并发 6 闸门。代码未改，仍是 klib 编译级通过。 |
| M-11 | **Mac-only** | 加密三件套与 `jvmSharedMain` **逐字节**比对：MD5/SHA-1/SHA-256 各 3 组已知向量；AES-128-CBC 与 AES-256-ECB（含 PKCS#7、超长密钥截断）各 3 组；RSA PKCS#1 v1.5 往返解（填充随机，只验往返不验密文相等）；`maxPlainChunk = 密钥字节数 − 11`。代码未改。 |
| M-12 | **Mac-only** | `UserDefaultsStore` 冒烟：收藏一条 → 杀进程 → 重启仍在；`Store.init()` 的 `readList/writeList` 无静默丢数据；`allKeys()` 不含系统注册域键。**已知差异**：`synchronize()` 在现代 iOS 恒返回 true，磁盘满时不会像 Android `commit()` 那样返回 false（`docs/04` §16.10.6）。代码未改。 |
| M-13 | **Mac-only** | `PlatformLock`（`NSRecursiveLock`）跨线程：并发跑 `Store` 写 + `PageCache` 读写不自死锁；确认 Native 内存模型下 9 处 `kotlin.concurrent.Volatile` 可见性。代码未改。 |
| M-14 | **Mac-only** | 系统语言切泰历/和历后 `yyyyMMddHHmmss` 仍输出公历年份；改系统时间后 `elapsedMs()` 不倒退（单调源 `NSProcessInfo.systemUptime`）。代码未改。 |
| M-15 | **Mac-only**（后果已写进代码） | 「iOS 可用性矩阵」必须真机产出（`AVPlayer` 无公开自定义头 API）。P5-6 的实做让这件事**不用靠猜**：`AvPlayerEngine.setMedia()` 显式不消费 `headersJson`，非空时记 `engine-headers-dropped` 探针，Kdoc 写明 `AVURLAssetHTTPHeaderFieldsKey` 是私有键不用。**已静态盘出的受影响面**（`PlayResolveResult(url=…, headers=非空)` 的产出点）：`JuzhiParser.kt:417`（f6 repeated 自定义头，含 `User-Agent`）与 `WebParser.kt:240`（`playHeaders(rule, iframeUrl)`，按站点规则注入 `Referer`/`User-Agent`）；其余适配器的 `headers` 参数只用于**抓页面/调接口**（走 `HttpClient`，iOS 侧 `NSURLSession` 支持自定义头，不受影响），不用于播放。故 Mac 上第一轮只需看这两处对应的源：从 `playback.log` 里读 `engine-headers-dropped` 是否出现，即可定「哪些源必须标 `playGap`」。矩阵定稿后回写 `docs/09`/`docs/10`。 |
| M-16 | **Mac-only** | `newFixedThreadPoolContext(4, "dy-io")` 的 UI 流畅度观感（并发拉列表 + 落盘时是否掉帧）。代码未改。 |

## 四、桩实现（原 12 处 `TODO(Mac)` → 现 2 处）

| # | 状态 | 结论与证据 |
|---|---|---|
| M-17 | **done-on-Windows** | 引擎真身落地 `ui/src/iosMain/kotlin/com/dengying/player/ui/player/AvPlayerEngine.kt`（14117 B）：`class AvPlayerEngine : PlayerEngine`，`AVPlayer` + `AVPlayerLayer`（`videoGravity = AVLayerVideoGravityResizeAspect`）。状态用 250 ms 周期观察者同时驱动进度与状态差分，七类回调全接（`onReady/onPlaying/onPaused/onBuffering/onCompleted/onError/onVideoSize`）；`PlaybackErrorKind` 四分类按 `NSURLErrorDomain` / `AVFoundationErrorDomain` 码表映射（`-1001/-1003/-1004/-1005/-1009/-1022` → `Network`，`-1100/-1101/-1102/-11850` → `SourceGone`，`-11800/-11828` → `Decode`，其余 `Unknown`）；`release()` 解观察者 + 清 item。**有意不用 KVO**（Kdoc 记了理由：keyPath 字符串匹配 + context 指针 + 成对注销，漏一次就是崩溃级缺陷；代价是最多 250 ms 回调延迟）。`object IosPlayerEngineHost : PlayerEngineHost` 与 Android `PlaybackEngineHolder` 逐条同语义：单例、`peek()` 不创建、`surfaceCount` 归零**只暂停不释放**、`forceRelease()` 清计数并释放。`MainViewController.kt` 的 `override val engineHost` 已从 `StubPlayerEngineHost` 换成它。编译证据：`:ui:compileKotlinIosArm64` / `:ui:compileKotlinIosSimulatorArm64` 双 `RESULT: OK`。 |
| M-18 | **done-on-Windows** | `ui/src/iosMain/kotlin/com/dengying/player/ui/platform/VideoSurface.ios.kt`（4457 B）改写为真实实现：`key(engine)` 整体重建 + CMP `UIKitView` 把 `engine?.viewHandle() as? AVPlayerLayer` 挂到 `view.layer`；`factory` 建黑底 `UIView`、`update` 跟随 `view.bounds`；`CATransaction` 关隐式动画，避免旋转/全屏时画面拉伸。契约逐条满足：`as?` 失败 → null → 黑底且**不抛**；未绑定不报 `onBind(true)`；无触摸、无 spinner、FIT 缩放。编译证据同上双绿。 |
| M-19 | **done-on-Windows** | 方向：`ui/src/iosMain/.../platform/Fullscreen.ios.kt` 新增 `object IosOrientationLock`（`mask` 只读、`request(newMask)` 改掩码并沿 `keyWindow.rootViewController → presentedViewController` 链调 `setNeedsUpdateOfSupportedInterfaceOrientations()`）；`enter/exit` 分别请求 `if (landscape) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait` 与 `UIInterfaceOrientationMaskPortrait`（**显式回竖屏，不是恢复快照**，符合 commonMain 契约）。壳侧：`iosApp/DengYingApp.swift` 新增 `OrientationHostingController`，其 `supportedInterfaceOrientations` 读 `DengYing.IosOrientationLock.shared.mask`（Kotlin `NSUInteger` → Swift `UInt`，显式包成 OptionSet），`prefersStatusBarHidden` 恒 `false` 并注明「全屏控制层由 Compose 承担，状态栏保留」。两边缺一不可：Kotlin 发起转屏，掩码放行。编译证据：双 iOS 目标绿。运行期手感归入 M-9。 |
| M-20 | **done-on-Windows**（判为不做，有契约依据） | **产品决策已在代码里落定**：`rememberBrightnessController()` 返回 `null`。理由写进 Kdoc——iOS 没有窗口级亮度，`UIScreen.main.brightness` 改的是**系统亮度**，违反 `docs/09` §9.5 的语义定稿（不写系统设置、不要权限、不改用户偏好）；返回 `null` 正好触发调用方既有的 `brightnessAvailable = controller != null` 分支，页自动不显示亮度手势。这不是「留个桩」，是一个**满足既有契约的实现**。`BrightnessController` 类保留可用的 `current/adjust/restore` 实现（当前无路径调用），以免将来真要接时再写出第二份。Mac 上无需动作；若产品后来改主意要「接受改系统亮度」，那是契约变更，需同步改 `docs/09` §9.5。 |
| M-21 | **done-on-Windows** | `VolumeController` 真实实现：在 1×1、`alpha = 0.01`（**刻意不用 `hidden`**，App Store 审核会盯隐藏系统音量 UI）的 `MPVolumeView` 里找到 `UISlider`，`adjust` 走 `setValue(target, animated = false)` 并返回 `s.value`（系统自己量化），步数沿用 `MAX_STEPS = 15` 与 JVM/Android 侧同形。调的是媒体流，不弹系统 HUD。运行期手感归入 M-9。 |
| M-22 | **done-on-Windows** | `rememberIsLandscape()` 改读 `UIWindowScene.interfaceOrientation`（**不**用 `UIScreen.bounds` 宽高比较——iPad 分屏下宽高会给相反答案）。编译证据：双 iOS 目标绿。运行期确认归入 M-9。 |
| M-23 | **done-on-Windows**（判为不做，有事实依据） | `PlatformBackHandler` 保持空实现，但**理由已从「待定」升级为「已判定」**：壳里没有任何 `UINavigationController`（`grep -rn "UINavigationController" iosApp/ ui/src/iosMain/` 零命中），导航栈完全在 Compose `NavHost` 内，iOS 侧不存在系统侧滑手势可接管。故「把 `interactivePopGestureRecognizer` 状态回传给 Compose」这个原方案**没有对象**——不是没做，是无处可接。`TODO(Mac)` 注释保留为设计记录（若将来引入 `UINavigationController` 再启用）。 |
| M-24 | **Mac-only**（前置未满足，且非 Windows 能做） | `MediaCacheHost.ios.kt` 保持全空桩，`hasFailed()` 恒 `false`（语义是「尝试过并失败」，返 `true` 会让 `MediaCache.warmUp` 永久短路，反而掩盖将来真实现的行为变化）。**前置条件不在 Windows 侧**：要先在 iOS 上复现「开缓存让 `STATE_BUFFERING` 从 21 次涨到 1743 次」这个 Android 结论（`MEDIA_CACHE_ENABLED` 当前 `false`），才轮到选 `AVAssetResourceLoaderDelegate` 自建 LRU。放 Mac 上做是对的，原因与 Mac 无关而与「Android 结论尚未跨平台复现」有关。`TODO(Mac)` 计数因此保留 1 处。 |
| M-25 | **done-on-Windows** | `shared/src/iosMain/kotlin/com/dengying/player/player/PlaybackProbe.ios.kt`（4283 B）写实：POSIX `fopen(path,"a")` + `fputs` + `fclose` 追加，`NSFileManager` 建目录，`reset()` 删文件，`dumpSharePage(url)` 用 `NSURLSession.sharedSession.dataTaskWithRequest` 写 `sharepage.html` 并 `note("sharePage-dumped", "len=…")`。`init` 所有方法 no-op（契约：单测不得产生文件副作用）。**没用 `NSFileHandle.fileHandleForWritingAtPath`**——cinterop 里不可用（`Unresolved reference`，实测），且共享 handle 在后台挂起时会抛 `NSFileHandleOperationException`。**接线一并做了**（原清单点名要求）：`MainViewController.kt` 的 `ensureStarted()` 调 `PlaybackProbe.init(probeDir())`，`probeDir()` = `NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, true)` 下的 `Caches`（回退 `NSHomeDirectory()`）。 |
| M-26 | **Mac-only** | pbxproj 两处 `DEVELOPMENT_TEAM = ""` 保持空串。**Mac 上要做**：填入 Team ID，`xcodebuild -allowProvisioningUpdates` 能自动签名。空串是刻意的——写一个假 Team ID 会让 `xcodebuild` 报更难的错。 |
| M-27 | **Mac-only**（非代码项） | 免费 Apple ID签名 7 天过期、不能给别人装；长期给朋友用需付费账号走 TestFlight 或 Ad Hoc。IPA 必须在 macOS + Xcode 上编出，Windows 无法转换。此项无 Windows 可做的工作。 |
| M-28 | **done-on-Windows** | `iosApp/Info.plist` 两个版本键已与 Android 对齐：`CFBundleShortVersionString = 1.1.0`、`CFBundleVersion = 1000100`（Android 侧 `app/build.gradle.kts:15-16` = `versionCode 1000100` / `versionName "1.1.0"`，`docs/01-移植说明.md:7` 已是「1.1.0（versionCode 1000100）」）。校验项：`plist version synced to Android 1.1.0` + `plist build number synced to 1000100`。`PRODUCT_BUNDLE_IDENTIFIER = com.dengying.player.ios` 未动。 |

## 五、清单之外

- **P6 鸿蒙**不在本清单，且与 P5-6 不同：鸿蒙的代码已写、已在 Windows 上编译验证过，
  缺的只是真机/设备侧验证——但它**不在 P5-6 工单范围内**（本清单只管 iOS 28 项）。
  已落盘的真实状态：
  - `shared/src/ohosMain/` **12 个 actual 文件**（MediaCacheHost / HttpTransport / Aes / Rsa /
    Hash / Clock / TimeFormat / UrlCodec / IoDispatcher / Log / PlatformLock / PlaybackProbe），
    共 703 行，已在**独立构建** `ohos-build/` 里 `compileKotlinOhosArm64` 实测通过
    （Kotlin 2.2.21-1.1.0 CPF 版，与本仓 2.4.21 主构建版本线互斥，故不能同构建共存）。
  - `harmony/ ArkTS/ArkUI 壳：`hvigorw … assembleHap` 实测 **BUILD SUCCESSFUL in 14s**，
    产物 `entry-default-unsigned.hap` 119,743 B。
  - **哪部分还是桩（必须如实读）**：`RsaCipher.ohos.` 两个方法直接 `throw
    NotImplementedError`（`OH_CryptoAsymCipher_Init` 需真机验证）；`HttpTransport.ohos.kt`
    只做了 request 创建/释放 + 失败折叠，**「执行请求、读响应」仍是 TODO**——回调式
    `OH_Http_Request` 桥成挂起语义必须真机才能验，写死未验证行为比留 TODO 更危险；
    `MediaCacheHost.ohos.kt`、`PlaybackProbe.ohos.kt` 是空桩。其余 8 个（Hash/Clock/
    TimeFormat/UrlCodec/Aes/IoDispatcher/Log/PlatformLock）是真实现。
  结论与退路（**不迁移 `:ui`**，因 CMP 无官方 ohos target）见 `docs/09` §9.2.c 与 §9.3。
- **不引入跨平台播放器三方库**（`docs/09` §9.5）：iOS 播放走 `AVPlayer` + `PlayerEngine`。
  P5-6 的引擎实现落在 `ui/src/iosMain/.../player/AvPlayerEngine.kt`（Kotlin/Native cinterop），
  与 `docs/09` §3 的「iOS actual = AVPlayer」一致。**为什么引擎不写在 Swift 里**：
  `PlayerEngine.viewHandle(): Any?` 要把 UIKit 对象交回 `VideoSurface`，而 Swift 类要实现
  Kotlin 接口需反向互操作，标准 CMP framework 导出不提供；Kotlin/Native side 用 cinterop
  是唯一能在无额外接线的前提下编译通过的形态。Swift 壳仍承担入口、方向宿主、状态栏三件事。

---

## 六、上 Mac 之后的工作量

**15 项 Mac-only**，按依赖顺序：

1. **环境**：M-2（`xcodebuild -version` ≥ 16.0）。
2. **能编出来**：M-4（link framework）→ M-5（`xcodebuild -list` / `plutil -lint`）→ M-8（脚本相位真跑）→ M-26（Team ID 签名）→ M-27（账号选择、出 IPA）。
3. **能跑起来**：M-9（模拟器首屏 + M-19~M-22 的运行期手感）→ M-12/M-13/M-14（存储、锁、时间）。
4. **能对上字节**：M-11（加密逐字节）→ M-10（HTTP 6 条契约）→ M-16（线程池观感）。
5. **能定片源范围**：M-15（可用性矩阵，靠 `engine-headers-dropped` 探针读数）+ M-7 遗留的巨滋滋 `-1022` 裁决。
6. **最后才是**：M-24（缓存方案，前置是 Android 结论在 iOS 复现）。

**13 项 done-on-Windows 不需在 Mac 上重做**：M-1、M-3、M-6、M-7、M-17、M-18、M-19、M-20、
M-21、M-22、M-23、M-25、M-28。

---

## 完成后要做的两件事

1. 把每一项的实测结论（含失败与偏差）写进 `docs/04-验证记录.md` §16 的新小节，
   失败项不粉饰 —— 本清单的每条「验收标准」就是那一节的判据。
2. iOS 侧新增真机/模拟器验证记录到 `docs/05-真机验证记录.md`，播放体验按
   `docs/07-播放体验测试计划.md` 补 iOS 小节（Android 侧口径已在该文档）。
