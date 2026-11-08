# 09 · KMP + CMP 多平台迁移方案（提案，待确认）

状态：**已确认**（2026-10-25），按阶段推进。本文是迁移的权威设计稿，每阶段独立验收。
**P0~P4 全部完成并合并**（Windows 侧 KMP+CMP 改造收官，P4 合并 cd41135；验证记录
`docs/04-验证记录.md` §10~§14，最终 517 例 0 失败；真机回归因设备离线未做，§14 如实列明）。
P5（iOS）/ P6（鸿蒙）执行策略（用户 2026-10-26 拍板）：**代码先行，测试辅助，
真机最后校验**——不等 D-2 Mac（真机校验时用）；P6 环境 DevEco Studio 6.1.0.830
已在 D:\DevEco Studio（HarmonyOS 6.1.0/API 23 SDK 全），Windows 全量编译可用。
后迁移任务（AH 接口实现 + 可扩展方向文档）**已收官合并**（§15，见下）：
15.1 桔子动态根域 ✓（f8e8eff）、15.2 蓝光4k ✓（ba5a651）、15.3 农民影视 ✓（39371e2，
死源记录 + WebParser 容器型/逆转选集能力）、15.4 两个BT ✓（a1779e7，wasm HMAC-SHA256
复刻 + oracle 金样本，全链路接入）、15.5 NBY/bba ✓（b18285a，播放页解析层落地，
二层密文=客户端不可解如实记录）、15.6 发现类 ✓（dde42fe，发现层三件套：爱奇艺
联想实测可用，豆瓣 frodo key 失效/风控按实情记录）、15.7 死源开关统一裁决 ✓
（7810aee，逐源实测收口：独播库等停用、奇优保留启用+playGap、桔子保持开启）、
15.8 可扩展方向 ✓（51ce816，docs/10 内容维度盘点+成本评估，用户点名交付物）。
**§15 总结 58dd62d → feat/ah-interfaces --no-ff 合回 feat/ios-port（cbd704c）→
feat/ios-port --no-ff 合回 main（3d0857d）→ 工序分支 -d 安全删除。main 终验
663 例 0 失败、双 APK 绿（监工 2026-10-27 复核）。AH 阶段收官。**
现状行为以代码为准；目标行为以本文批准稿为准（与 `docs/03-架构与模块边界.md` 冲突处，本文批准后覆盖之）。
决策点取值：D-1 = A（允许开源基建库）；D-3 = 保留 jvm 桌面验证目标；D-2/D-4 至 P5/P6 时定。

## 1. 目标与范围

**目标**：把现有 Android 单端工程改造为 Kotlin Multiplatform（KMP）+ Compose Multiplatform（CMP）
工程，使「业务逻辑 + UI」共享，后续以最小代价接入 iOS 与鸿蒙（OpenHarmony CPF 路线）。

**范围内**：模块结构调整、平台服务抽象、依赖台账变更、分阶段迁移与验收标准。
**范围外**：鸿蒙端的最终落地（Phase 6 单独决策）；新功能开发；播放行为变更——迁移期间
Android 行为不应变化（播放体验契约见 `docs/06`~`08`）。

**不变式**（每阶段结束时都应成立）：

- I-1 `:app:assembleDebug` 与 `:app:assembleRelease` 成功。
- I-2 领域层 103 例单测全部通过（位置可变，数量与断言不应减少）。
- I-3 Android 真机可安装、可播片（按 `docs/05` 的真机验证流程冒烟）。
- I-4 迁移不应引入行为差异；发现既有缺陷按缺陷流程单独处理。

## 2. 现状边界基线（2026-10-25 实测）

单模块 `:app`，74 个 Kotlin 文件 / 18,520 行；Kotlin 2.2.20、AGP 8.13.2、Gradle 8.14.3、
compileSdk 36 / minSdk 31。分层已清晰，这是迁移最有利的起点：

| 层 | 文件 | 平台耦合 |
|---|---|---|
| `core/`（模型、源类型） | 6 | 基本纯 Kotlin |
| `domain/`（分页/播放/搜索/存储规则） | 18 | **纯 Kotlin + 协程**，103 例单测 |
| `data/source/adapters/`（10 个源适配器） | 10 | `org.json`（21 处）、`java.net.URLEncoder` |
| `data/source/crypto/`（AES/RSA/摘要/Protobuf） | 5 | `javax.crypto`、`java.security`、`Base64`、Java `Random` |
| `data/net/HttpClient`、`Poster` 下载 | 2 | `HttpURLConnection`（SEC-001 刻意选系统栈） |
| `data/store/Store.kt`（676 行）、`PageCache` | 2 | `SharedPreferences` |
| `data/cache/MediaCache` | 1 | Media3 `SimpleCache` |
| `playback/`（EngineHolder/Session/PlayerFactory） | 3 | Media3 ExoPlayer 全家 |
| `ui/pages·components·theme·DyIcons` | 14 | Compose（379 处 import），绝大部分可移植 |
| `ui/viewmodel/` | 7 | `androidx.lifecycle.ViewModel`（已有 KMP 版）；`PlayerViewModel` 1200 行与 Media3 引擎强耦合 |
| `ui/platform/`（FullscreenController、SystemAdjust） | 2 | 横竖屏/系统栏/亮度/音量，Android 专属 |
| `app/`（DengYingApp、MainActivity） | 2 | Application / 单 Activity |

import 统计：androidx.compose 379、media3 20、lifecycle 20、android.content 13、navigation 6；
全库平台标记行 276 行——**耦合面小且集中**，预估可共享代码占比 85% 以上。

既有边界问题（基线记录，本迁移不扩大、顺带收敛）：`ui/pages` 直接用 `LocalContext`/
`findActivity`；ViewModel 持 `Context`。这些在 P4 抽象时一并收口。

## 3. 目标架构

```
DengYing/
├─ gradle/libs.versions.toml        # 版本唯一来源（沿用现行约定）
├─ shared/        KMP 库：core + domain + data（源适配、HTTP、存储、加密、缓存规则）
│   ├─ commonMain / commonTest(103 例)     ← 无任何平台 import，编译器强制
│   ├─ androidMain / iosMain / jvmMain     ← expect/actual 平台实现
│   └─ (P6) ohosMain                        ← CPF 工具链加入
├─ ui/            CMP 库：pages、components、theme、viewmodels、导航
│   ├─ commonMain                          ← Compose 多平台 API
│   ├─ androidMain / iosMain / jvmMain     ← VideoSurface、系统栏、亮度音量等 expect/actual
├─ app/           Android 壳：Application、MainActivity、Media3 引擎装配、投屏
├─ iosApp/        iOS 壳：Xcode 工程，AVPlayer 引擎装配（Swift export 默认开启）
└─ (P6) harmony 壳：DevEco 工程 + CPF 插件（Gitee 的 CPF 仓库，用户提供坐标）
```

**边界与 enforcement**：

- 模块边界由**编译器强制**，不靠目录约定：`shared/commonMain` 编不出 android API 引用；
  `shared` 不依赖 Compose（ohos 走 ArkUI 壳的退路因此保留）。
- `ui` 只经 `ui/platform` 与 `ui/player` 两个收敛点触达平台能力；页面不应直接 import
  平台 API（现状违例在 P4 清零，记入基线）。
- 播放抽象：`shared` 定义 `PlayerEngine` 接口（状态流：进度/时长/缓冲/错误/画面尺寸，
  命令：prepare/seek/speed/pause/release，含换集重建语义），Android actual = Media3（现
  PlayerFactory/EngineHolder 行为不变），iOS actual = AVPlayer。**不引入跨平台播放器第三方库**。
- `jvmMain`（桌面）只作为 Windows 本机的快速验证目标，不发布、不应投入 UI 打磨。

## 4. 依赖台账变更与 SEC-001 决策（需确认）

现契约 SEC-001 要求「运行时第三方依赖清单为空 / 不接入第三方 SDK」（HttpClient、手绘图标
皆为它服务）。迁移后提案如下——**所有新增均为开源库、无埋点无上报**，但字面上突破了
「清单为空」，应先获同意：

| 关注点 | 现状（Android） | 提案（commonMain） | 备注 |
|---|---|---|---|
| HTTP | `HttpURLConnection` | **A**：ktor-client（Android 可继续用 HUC actual）／**B**：保留手写 expect/actual（每端系统栈） | A 省力、iOS 走 darwin 引擎（即 NSURLSession）；B 完全守住 SEC-001 字面，代价是三端各写一份 |
| JSON | `org.json` | kotlinx-serialization（JsonElement 遍历式迁移，改动最接近原代码） | 10 个适配器逐个换，金样本测试护航 |
| 键值存储 | `SharedPreferences` | multiplatform-settings（底层仍是各端系统存储） | 键名不变，数据可迁移 |
| 加密 | `javax.crypto`/`java.security` | cryptography-kotlin（whyoleg，AES/RSA/摘要）或 expect/actual | 与 HTTP 同一决策口径 |
| 图片 | 手写 Poster + `BitmapFactory` | **保留手写 Poster**，解码换 CMP 自带 Skia（无新依赖） | https→http 降级逻辑原样保留 |
| 日志 | `android.util.Log` | 极简 expect Log 门面（各端 actual 一行桥接），不引库 | tag ≤23 字符约束仅 Android actual 内处理 |
| UI/导航 | androidx.compose + navigation-compose | CMP 1.10.x + Navigation 3（CMP 1.10 起支持非 Android 目标） | 现仅 6 处 nav import，迁移面小 |
| ViewModel | androidx.lifecycle 2.9.4 | 同家族 KMP 构件（包名不变） | 最小改动 |
| 时间/Base64/UUID | `Date`/`SimpleDateFormat`/`java.util.Base64` | kotlinx-datetime；Base64/UUID 用 stdlib 或 expect/actual | |

**推荐组合**：HTTP/加密取 A（ktor + cryptography-kotlin），前提是接受 SEC-001 释法为
「不接内容/埋点类 SDK，允许开源基础设施库」；若坚持字面零依赖，则取 B，工期约 +40%。

## 5. 分阶段计划与验收标准

每阶段独立分支合入；迁移窗口内 Android 新功能与迁移串行（尤其 PlayerScreen 双头改会翻车）。

| 阶段 | 内容 | 验收 |
|---|---|---|
| **P0 工具链** | Kotlin→2.4.x、CMP 1.10.x、AGP/Gradle 按官方兼容表跟进；仍单模块 | I-1~I-4 |
| **P1 逻辑下沉** | 建 `:shared`（先 android+jvm 目标）；`core/`+`domain/`+103 测试移入 commonMain/commonTest；`app` 依赖之 | 测试在 JVM 与 Android 双绿；无行为变化 |
| **P2 平台服务抽象**（已完成） | 实际落地：expect 门面 Log/Clock/Hash/AesCipher/RsaCipher（commonMain）+ `jvmSharedMain` 中间源集（关闭默认层级模板，P5 加 iOS 时 iosMain 接线需手动补）；data 层因 org.json 出现在**函数签名**上顺延 P3（docs/04 §12.1） | 145 例 0 失败；复验 docs/04 §12.9 |
| **P3 通用实现替换** | 按 **§5.1 细则**分批执行：金样本先行 → 逐 adapter 换 kotlinx-serialization 并升舱 commonMain → HttpClient expect 契约 → 用户数据格式最后；URLEncoder expect/actual | 金样本全绿；tv189/duboku 等真源抽测 |
| **P4 UI 上 CMP** | 建 `:ui`；theme/components/pages/VM/导航迁移；`ui/platform` 与播放表面 expect/actual；`PlayerEngine` 接口抽取（Android actual 复用 Media3 现链路）；Context 从 VM 构造中移除 | Android 全量真机回归（`docs/05` 清单 + `docs/07` 播放测试计划）；基线违例清零 |
| **P4 追加：data 层收尾** | 实现期发现 `PlaybackSession` → `SourceRepository` 的依赖方向违反 `:app → :ui → :shared`，故 P4 吸收 data 收尾：`SourceRepository` + 9 Adapter 外壳 + `MediaCache`（含 `MediaCacheHost` expect/actual）迁入 `:shared`。详见 `docs/04` §14.1.5 裁决 A | 同上（并入 P4 验收） |
| **P5 iOS** | `iosApp` Xcode 壳；iosMain actual（AVPlayer、NSUserDefaults、darwin/系统栈、亮度音量）；投屏以系统 AirPlay 入口替代；媒体缓存首版**不做**（范围裁剪，记录在案） | iPhone 真机冒烟可播；需 macOS 环境 |
| **P6 鸿蒙（决策门）** | 按 Gitee CPF 仓库接入 ohos target（KMP 库编为 .so + CPF 统一渲染，ArkTS 壳 + interop）；先验证 ktor/settings 等库在 ohos target 的可用性；若 CPF 成熟度不足，退路 = 只共享 `:shared` 逻辑 + ArkUI 壳（hmVideo 原工程经验可复用） | 鸿蒙模拟器/真机跑通首页+详情+播放；否则记录差距择期 |

### 5.1 P3 执行细则（2026-10-25 定稿，P2 验收时）

总则：阶段分支 `feat/kmp-p3-json`，分批提交，每批独立可验收、可 revert；新增依赖仅
`kotlinx-serialization-json`（D-1=A）；优先 `JsonElement` 遍历式改写贴近 org.json 语义，
不引 `@Serializable` 编译插件；`Json` 配置显式定死（`ignoreUnknownKeys`/`isLenient`/
`coerceInputValues`）并记录依据。

**行为红线**：`optString/optBoolean/optLong` 默认值语义、数字宽松性、未知字段忽略、
「解析失败→空结果」契约——以金样本锁定，新旧实现输出逐条一致。

| 批 | 内容 | 关键约束 |
|---|---|---|
| A | 金样本先行：每 adapter 录制真实响应入库，先在 :app 侧用 org.json 现实现断言解析产出 | 样本即迁移基准，双实现比对用 |
| B | 试点 1-2 个最简 adapter：换 kotlinx + 升舱 commonMain + 金样本比对 | 走通全流程再铺开 |
| C | 其余 adapter 逐个推进 | Tv189 依赖的 crypto/Protobuf 已 common，应可直接升舱 |
| D | HttpClient：`safeJson/safeJsonArray` 签名换 kotlinx 类型后升舱 | **commonMain 不可引用 jvmSharedMain**：adapter 升舱后调用 HttpClient 需在 commonMain 立 `expect HttpTransport` 契约（保持 status/elapsedMs/全局闸门语义），HUC 实现落 jvmSharedMain actual |
| E | 最后：StoreEnvelope、SourceOverrideCodec、Store、PageCache（落盘用户数据格式） | 金样本应含「读取既有持久化字节」回放；解析失败保持「保留原始字节」；KeyValueStore expect 随 Store 升舱立契约 |
| F | URLEncoder expect/actual | commonMain 门面 + jvmSharedMain 一份 actual |

每批验收：assemble 双绿 + 三处单测全绿（总数 ≥145 且断言未减）+ commonMain 纯净扫描；
docs/04 §13 分批记录。全部完成写汇总（含行为差异裁决表），`--no-ff` 合回 `feat/ios-port`。

P0–P4 在 Windows 上全部可完成、可验证；P5 需 macOS（编译 iOS 目标是硬约束，云 Mac/CI 亦可）。

## 6. 验证策略

- 每阶段：`:app:assembleDebug` + `:app:testDebugUnitTest` + `:shared:jvmTest`（P1 起）。
- 结构检查即测试：`commonMain` 无 android import 由编译器强制；`:shared` 无 Compose 依赖
  由 Gradle 模块图强制。两者进 CI/本地脚本。
- JSON 替换（P3）应先落金样本夹具（失败应阻断替换合入）。
- Android 行为回归以 `docs/05/07` 的真机清单为准；播放转场类缺陷须录屏逐帧（历史教训）。
- 声明限制：本机无 macOS，P5 验收在获得 Mac 前保持「未验证」状态，不虚报。

## 7. 风险与回退

| 风险 | 等级 | 缓解 |
|---|---|---|
| `PlayerViewModel`(1200 行) 与 Media3 强耦合，接口抽取最易翻车 | 高 | P4 单独子方案；Android actual 保持现链路不动；先接口后搬码；真机回归兜底 |
| SEC-001 依赖口径变化 | 中 | §4 决策点先确认；B 案保底 |
| iOS 无 Mac 环境 | 高（环境） | P5 前置条件单列；云 Mac / CI 方案备选 |
| CPF 非官方、2026-09 才首版 | 中 | P6 独立决策门；ArkUI 壳退路已由 `:shared` 无 UI 保证 |
| Kotlin 2.2→2.4 大版本跳升 | 中 | P0 独立成阶段，出问题即回退该 merge |
| kotlinx.serialization 与 org.json 行为差（数字类型/宽松性/未知字段） | 中 | 金样本夹具；`ignoreUnknownKeys`/`isLenient` 显式定死 |
| 单线程约束差异（Native 主线程派发） | 低 | 引擎命令走 expect 定义的调度约定，P4 设计时明确 |

回退原则：strangler 式渐进，`app/` 的 Android 发版能力任何阶段不应中断；每阶段一次 merge，
可单独 revert；P6 可整体放弃而不影响 iOS。

## 8. 待确认决策点

- D-1 SEC-001 依赖口径：A（ktor/cryptography-kotlin 等开源基建）或 B（三端手写系统栈）？**推荐 A**。
- D-2 macOS 获取方式（本地 Mac / 云 Mac / CI），决定 P5 排期。
- D-3 桌面 jvm 目标是否保留为长期验证面（推荐保留，成本≈0）。
- D-4 鸿蒙 CPF 仓库坐标与版本（你已定位 Gitee 的 CPF 仓库；P6 时提供即可）。

## 9. P5/P6 代码先行子方案（2026-10-27 定稿，工具链探明后）

### 9.1 决策与前提

P5（iOS）/P6（鸿蒙）不再等环境齐备再动手，改为**代码先行、测试辅助、真机最后校验**
（用户 2026-10-26 拍板）：

- 能在 Windows 上编译/校验的部分现在就做完（target 接线、纯净性修复、门面 actual、
  壳与构建脚本、klib 级编译验证）。
- 只能在 macOS 上做的部分（`linkDebugFramework`、Xcode 工程、模拟器/真机运行）
  明确标注为「未验证」，拿到 Mac 后一次补齐，不虚报。
- 鸿蒙侧同构：DevEco Studio 6.1.0.830 与 HarmonyOS 6.1.0/API 23 SDK 已在
  `D:\DevEco Studio`，Windows 上可做 ArkTS 壳与 `:shared` 逻辑复用，但 ohos target
  的 KGP 版本线与本仓不兼容（见 9.2.c），故 P6 只走 ArkUI 壳退路。

### 9.2 工具链探明实测（2026-10-27，本机 Windows 11 + JDK 18）

探明方式：临时给 `:shared`/`:ui` 加 `iosArm64()` + `iosSimulatorArm64()` 与手动
`iosMain` 接线（`kotlin.mpp.applyDefaultHierarchyTemplate=false` 下必须手接），
跑真实 Gradle 任务取编译器输出，探明后全部回滚（`git status` 回到 clean）。

#### a) Windows 上 iOS klib 编译可行

| 观测项 | 实测结果 |
|---|---|
| `:shared:downloadKotlinNativeDistribution` | **成功**（Kotlin/Native 发行版可在 Windows 下载，无需 Xcode） |
| `:shared:compileKotlinIosArm64` | **真实执行并进入编译**（不是 SKIPPED），失败原因仅为源码问题（见 b） |
| `:ui:checkIosArm64MainComposeLibrariesCompatibility` | **真实执行**，CMP 1.11.1 有 iOS 构件，依赖可解析 |
| `:shared:iosSimulatorArm64Test` | **被禁用**：`cannot run on the current host (windows-x86_64)`，`simulator tests require macOS` |
| `linkDebugFramework` | 未尝试（预期失败：链接需要 Xcode 工具链），按设计只验到 klib 级 |

结论：**Windows 可完成「target 接线 + 源码级纯净性修复 + klib 编译验证」全流程**，
这是代码先行的可行性依据。simulator 测试与 framework 链接留给 Mac。

#### b) 加 iOS target 后暴露 11 处 commonMain 泄漏

静态扫描（import 级 `android.*`/`androidx.*`/`java.*`/`javax.*`/`org.json`）**0 命中**，
但 klib 编译器抓出 11 处真实泄漏——**这些都不需要 import，静态扫描抓不到**：

| 文件:行 | 泄漏构造 | 来源 |
|---|---|---|
| `shared/src/commonMain/kotlin/com/dengying/player/data/cache/PageCache.kt:52` | `@Volatile` | default import `kotlin.jvm.Volatile` |
| `shared/src/commonMain/kotlin/com/dengying/player/data/source/SourceRepository.kt:96` | `@Volatile` | 同上 |
| `shared/src/commonMain/kotlin/com/dengying/player/data/store/Store.kt:134`、`:556` | `@Volatile` ×2 | 同上 |
| `shared/src/commonMain/kotlin/com/dengying/player/domain/store/StoreGate.kt:51,54,57,61,65` | `@Volatile` ×5 | 同上 |
| `shared/src/commonMain/kotlin/com/dengying/player/playback/PlaybackSession.kt:195`、`:198` | `e.javaClass.simpleName` ×2 | JVM-only 扩展属性 |

修法已验证（本轮探明时试过、回滚，P5-1 重做）：`@Volatile` → `@kotlin.concurrent.Volatile`
（多平台注解，JVM 上映射到 `kotlin.jvm.Volatile`）；`e.javaClass.simpleName` →
`e::class.simpleName`。

修完 11 处后**下一层阻塞点正好是 12 个 expect 缺 Native actual**（编译器逐条报出），
这为 P5-2 划定了准确边界：

| 门面 | 文件:行 | iosMain 实现难度 |
|---|---|---|
| `MediaCacheHost` | `data/cache/MediaCacheHost.kt:30` | 桩（iOS 首版不做媒体缓存） |
| `HttpTransport` | `data/net/HttpTransport.kt:64` | 中（NSURLSession，需照 6 条契约） |
| `AesCipher` | `platform/AesCipher.kt:17` | 中（CommonCrypto 或纯 Kotlin） |
| `Clock` | `platform/Clock.kt:21` | 低（纯 Kotlin） |
| `Hash` | `platform/Hash.kt:15` | 中（CommonCrypto 或纯 Kotlin SHA-2/MD5） |
| `ioDispatcher` | `platform/IoDispatcher.kt:26` | 低（`Dispatchers.Default`/自定义） |
| `Log` | `platform/Log.kt:17` | 低（`println`/os_log） |
| `PlatformLock` | `platform/PlatformLock.kt:36` | 低（`kotlinx.atomicfu` 不可用则自旋/单线程假设） |
| `RsaCipher` | `platform/RsaCipher.kt:19` | 高（iOS 无直接 RSA 公钥加密 API，需 Security.framework 或纯 Kotlin） |
| `TimeFormat` | `platform/TimeFormat.kt:22` | 低（纯 Kotlin） |
| `UrlCodec` | `platform/UrlCodec.kt:31` | 低（纯 Kotlin 百分号编码） |
| `PlaybackProbe` | `player/PlaybackProbe.kt:22` | 低（写文件桩） |

`KeyValueStore` 是 interface（非 expect）故不在清单内，iosMain 需另给
`NSUserDefaults` 实现。

#### c) 鸿蒙工具链版本线不兼容

社区 KMP-for-OHOS 生态实测组合：**Kotlin `2.2.21-1.0.0`（定制 KGP）+ Gradle 8.14.1
+ JDK 21 + DevEco Studio 26.0.0 / HarmonyOS 7.0.0(API 26)**，配 `ohosArm64` +
`ohosX64` 双 target，`linkDebugShared` 可通；社区 CMP 侧是 **1.9.2**。上游 CMP 1.12 +
Kotlin 2.4 无法直接加 `ohosArm64()`（Painter 基类仍在
`androidx.compose.ui.graphics.painter.Painter`，「这不是加两行 `ohosArm64()` 的事，
这是再打一套 Compose 发行版的事」）。

⇒ **本仓 Kotlin 2.4.21 / CMP 1.11.1 与 OHOS 定制 KGP（2.2.21-1.0.0）不在同一版本线**。
P6 因此只能走 §3 已备的退路：**只共享 `:shared` 逻辑 + ArkUI/ArkTS 壳**，不迁移 `:ui`
的 CMP 界面。若将来要让 `:shared` 出 ohos klib，需换定制 KGP，属独立版本线工程，
不在本子方案范围。

本机 DevEco 环境实测：`D:/DevEco Studio` 下 `bin/ jbr/ lib/ modules/ plugins/ sdk/
tools/ .phone/`；`sdk/default/` 含 `hms/`、`openharmony/`、`sdk-pkg.json`；
`openharmony/` 含 `ets/ js/ native/ previewer/ toolchains/`；`native/` 含
`build-tools/ docs/ llvm/ sysroot/ compatible_config.json
nativeapi_syscap_config.json ndk_system_capability.json oh-uni-package.json`
⇒ native 工具链（llvm/sysroot/build-tools）齐全，`ohosArm64` 的 cinterop 有工具基础。
（注意：`sdk/default/ohos` 不存在，实际目录名是 `openharmony`。）

### 9.3 批次划分

分批原则与 P3/P4 一致：**每批独立可验收、可 revert、错误数量单调下降可量化**。
P5 每批的验收判据不是「绿/红」二值，而是「klib 编译器报出的阻塞点收敛到预期集合」
——因为 P5 全程无法在本机产出 framework，最终绿只能由 Mac 补齐。

| 批 | 内容 | 验收判据（可量化） |
|---|---|---|
| **P5-1** target 接线 + 纯净性修复 | `:shared`/`:ui` 加 `iosArm64()`+`iosSimulatorArm64()`；手动接 `iosMain`（`applyDefaultHierarchyTemplate=false` 下必须手接）；修 11 处泄漏（`@Volatile`→`@kotlin.concurrent.Volatile` ×9、`javaClass.simpleName`→`::class.simpleName` ×2） | `:shared:compileKotlinIosArm64` 的错误**性质从「11 处源码泄漏」变为「12 条 expect 缺 actual」**，泄漏 0 残留；`:shared:jvmTest`/`:app:testDebugUnitTest`/`:shared:testDebugUnitTest` 总数只增 0 失败；`:app:assembleDebug`+`assembleRelease` 双绿（确认加 target 不影响 Android 产物） |
| **P5-2** iosMain 纯 Kotlin actual | 6 个纯 Kotlin 门面：`Clock`/`IoDispatcher`/`Log`/`PlatformLock`/`TimeFormat`/`UrlCodec`；2 个桩：`MediaCacheHost`（iOS 首版不做媒体缓存）/`PlaybackProbe`（写文件） | `compileKotlinIosArm64` 的 expect 缺口**从 12 条收敛到 4 条**（仅剩 `HttpTransport`/`AesCipher`/`Hash`/`RsaCipher`）；纯 Kotlin actual 的正确性用 `:shared:jvmTest` 已有 21 例 `PlatformServicesTest` 口径复核（iosMain 无法跑测试，靠同语义实现 + Kdoc 契约） |
| **P5-3** 加密与网络 actual | `Hash`（SHA-256/MD5/SHA-1）、`AesCipher`（AES-128-CBC / AES-256-ECB）、`RsaCipher`（PKCS#1 v1.5 公钥加密 + `maxPlainChunk`）、`HttpTransport`（NSURLSession，照 6 条契约） | `compileKotlinIosArm64` **全绿**（0 错误）；`:shared:jvmTest` 总数只增 0 失败；每个 actual 的 Kdoc 写清「与 jvmSharedMain 实现的语义对应关系」与「未在真机验证」 |
| **P5-4** KeyValueStore + iOS 壳骨架 | `NSUserDefaults` 实现 `KeyValueStore`；`MainViewController`（CMP `ComposeUIViewController`）；`iosApp/` 目录下的 Xcode 工程描述文件与 framework 接线脚本（`embedAndSignAppleFrameworkForXcode` 约定） | 代码与脚本齐备且 `compileKotlinIosArm64` 保持全绿；**`linkDebugFramework` 与 Xcode 构建标注「未验证（需 macOS）」**，脚本只做静态检查（路径/任务名存在） |
| **P5-5** 记录与待验证清单 | `docs/04` 新增 §16（探明数据 + 每批实测）；`docs/09` 本文 §9 定稿；Mac 待办清单单列 | 文档落盘、行尾按原样、`git diff --numstat` 无整文件重排 |
| **P6-1** 鸿蒙壳骨架（决策门后） | 只共享 `:shared` 逻辑 + ArkTS/ArkUI 壳；写清 Kotlin↔ArkTS 的 interop 边界（哪些能力必须由壳提供：播放、存储、网络栈） | ArkTS 壳骨架可编译（DevEco 侧）；**`:shared` 出 ohos klib 不在本批范围**（需定制 KGP，独立版本线） |
| **P6-2** CPF 评估或差距记录 | 若 CPF 成熟度达标则评估接入；否则把差距写成可重评条件 | 决策与证据落 `docs/04`，不虚报可用性 |

### 9.4 每批通用验收标准

1. **klib 级编译**：`:shared:compileKotlinIosArm64`（`:ui` 改动批次加 `:ui:compileKotlinIosArm64`）
   错误集合必须与批次表的预期集合完全一致，不多不少。
2. **测试只增不减**：`:shared:jvmTest` + `:shared:testDebugUnitTest` + `:app:testDebugUnitTest`
   合计 **663 起底**，总数只增、断言不减、0 失败。
3. **Android 产物双绿**：`:app:assembleDebug` + `:app:assembleRelease` 成功（加 iOS target
   不得影响 Android 编译链）。
4. **纯净扫描零命中**：`shared/src/commonMain` + `ui/src/commonMain` 的 import 级平台依赖
   扫描 0 命中；**且以 klib 编译器输出为准**（静态扫描抓不到 `@Volatile`/`javaClass`
   这类无 import 的泄漏，本次探明已证明）。
5. **行尾按原样**：`docs/04` CRLF、`docs/09` 混合（追加段用 LF 与原样拼接需查
   `git diff --numstat`）、代码文件按各自基线（多数 CRLF，`PlayerFactory.kt`/
   `HlsUtil`/新文件 LF）。
6. **不虚报**：任何需要 macOS 的结论一律标注「未验证（需 macOS）」，并在 `docs/04`
   的未验证项小节列明。

### 9.5 明确不做（本子方案范围外）

- **不在 Windows 上尝试 `linkDebugFramework`**：链接需要 Xcode 工具链，失败无信息量，
  只会污染记录。拿到 Mac 后一次补齐。
- **不为 iOS 首版做媒体缓存**（`MediaCacheHost` 给桩）：P4-1c 的实测结论是缓存开启后
  `STATE_BUFFERING` 从 21 次涨到 1743 次（约 17 次/秒振荡），Android 侧开关本就是
  `MEDIA_CACHE_ENABLED = false`；iOS 首版跟随关闭，不引入第二套缓存实现。
- **不迁移 `:ui` 到 ohos**：CMP 无官方 ohos target，版本线不兼容（见 9.2.c）。
- **不为 `:shared` 换定制 KGP 出 ohos klib**：属独立版本线工程，需单独评估与单独分支。
- **不引入跨平台播放器第三方库**：iOS 播放走 AVPlayer + `PlayerEngine` actual
  （`docs/09` §3 已定，AVPlayer 不支持自定义 HTTP 头，故 headers 相关源在 iOS 侧降级）。

### 9.6 已知风险与本子方案的应对

| 风险 | 应对 |
|---|---|
| `RsaCipher` 在 iOS 无直接「公钥加密」高层 API（Security.framework 只给 `SecKeyCreateEncryptedData`，PKCS#1 v1.5 需 `kSecPaddingPKCS1`） | P5-3 单独一步，先写纯 Kotlin 的 PKCS#1 v1.5 填充 + 用 CommonCrypto/纯 Kotlin 大数运算；若成本过高，退路 = Juzhi 源在 iOS 侧标记不可用（该源 15.1 已改为动态根域，属高风险源） |
| `@Volatile` 换成 `kotlin.concurrent.Volatile` 后 JVM 语义是否完全等价 | 该注解在 JVM 上映射到 `kotlin.jvm.Volatile`（同一 JVM 注解），语义等价；`:shared:jvmTest` 的 539 例覆盖 `Store`/`PageCache`/`StoreGate` 并发路径，作为回归证据 |
| `PlatformLock` 在 Native 侧无 `synchronized` | Native 单线程模型下用 `@Volatile` + 简单自旋/重入计数，或直接退化为「调用方保证单线程」（`PageCache` 调用点本就在主线程或 IO 单线程），Kdoc 写清假设 |
| 加 iOS target 后 `:app` 构建变慢 | 探明实测加 target 后 `:shared:compileDebugKotlinAndroid` 仍 BUILD SUCCESSFUL 且增量正常；P5-1 验收含 Android 双绿，若显著变慢则把 iOS target 放进条件块（`if (providers.gradleProperty("enableIos").isPresent)`）——但会削弱「代码先行」的持续验证价值，故默认常开 |
| CMP iOS 构件版本漂移 | `:ui` 探明已确认 CMP 1.11.1 有 iOS 构件（`checkIosArm64MainComposeLibrariesCompatibility` 真实执行）；P5-4 前不升级 CMP |

### 9.7 与既有文档的关系

- 本 §9 是 `docs/09` §5 表中 P5/P6 两行的**执行细则**，与 §5.1（P3 细则）同级；
  冲突时以本 §9 为准（P5/P6 范围）。
- 探明实测数据同步落 `docs/04-验证记录.md` §16（含命令、原始错误清单、回滚记录）。
- 真机/模拟器验证流程仍以 `docs/05`（真机验证记录）与 `docs/07`（播放体验测试计划）为准，
  P5 完成后需新增 iOS 对应小节。

### 9.8 P5 代码先行收官状态（2026-10-27，本节为状态行）

**已完成（全部在 Windows 上落盘并验收）**：

| 批 | 提交 | 内容 | 本机验收 |
|---|---|---|---|
| P5-1 | `3ed4c6d` | iOS target 接线（`iosArm64`+`iosSimulatorArm64`，`applyDefaultHierarchyTemplate=false` 下手接 `iosMain`）；21 处构造级泄漏修复（`:shared` 11 + `:ui` 10）；**iosMain actual 18 文件、expect 缺口零残留**（`:shared` 13 + `:ui` 5；其中 6 文件含 12 处 `TODO(Mac)` 功能桩：播放引擎/画面/全屏/返回/媒体缓存/探针） | klib 四任务绿 |
| P5-4a | `2bf6151` | `UserDefaultsStore`（`NSUserDefaults` 实现 `KeyValueStore`）+ `IosEntry.kt`（`IosAppDeps : UiDeps` + `mainViewController()` + `StubPlayerEngineHost`） | klib 四任务绿 |
| P5-4b | `dfb42b5` | `iosApp/DengYing/` 壳骨架（pbxproj 7886 B / Swift 524 B / Info.plist 870 B / `build-phase.sh` 2321 B / README）+ `:ui` 的 `binaries { framework { baseName = "DengYing" } }` | `embedAndSignAppleFrameworkForXcode` 任务已注册（`:ui:tasks --all` 实测） |
| P5-5 | 本笔 | `docs/11-Mac待办清单.md`（28 项，逐项带验收标准）+ 本状态行 | 全套复跑见下 |

**三笔共同的验收口径**：`:shared:jvmTest` 539 + `:shared:testDebugUnitTest` 99 +
`:app:testDebugUnitTest` 25 = **663 例 0 失败**（与 P4 收官基线一致，只增不减）；
`:app:assembleDebug` 42,265,392 B + `:app:assembleRelease` 33,320,046 B 双绿；
commonMain 纯净扫描（import 级 + klib 编译级双重）零代码泄漏。

**P5 剩余 = Mac 待办，全量清单见 `docs/11-Mac待办清单.md`**（28 项，分六类）。
其中门槛性的 7 项：`linkDebugFramework`（M-4）、Xcode 工程构建（M-5）、
pbxproj/Swift 的 4 处接线缺陷（M-6）、ATS 例外（M-7）、模拟器运行（M-9）、
NSUserDefaults 冒烟（M-12）、签名与 `DEVELOPMENT_TEAM`（M-26）。
另有 12 处 `TODO(Mac)` 桩集中在 6 个文件（播放引擎/画面/全屏/亮度/音量/横屏判定/
返回处理/媒体缓存/探针，见清单第四节）。

**边界声明（不粉饰）**：Windows 可达的终点是 **klib 级编译**。所有 iOS **运行期**
行为 —— 真实网络请求、加解密字节级对齐、`NSUserDefaults` 读写、UI 启动、播放 ——
**均未验证**。本文任何声称 iOS 可用的表述都不成立，直到 `docs/11` 的对应项
在 Mac 上做完并记回 `docs/04` §16。

**2026-11-07 漂移复验**：P5-6 后 commonMain 漂移（`355f4f3` detail 提速 + P6-4~7
鸿蒙线）把 `:ui` iOS klib 打断 1 处（`PosterFetch.ios.kt` 缺
`platform.Foundation.setValue` import），补 1 行回绿；klib 四任务 `--rerun-tasks`
强制全量 0 `e:`、测试 775 例 0 失败，详见 `docs/04` §16.40。上方边界声明不变。

## 参考

- [Compose Multiplatform 1.10 发布说明（Navigation 3 支持）](https://blog.jetbrains.com/kotlin/2026/01/compose-multiplatform-1-10-0)
- [Kotlin 多平台兼容指南（当前稳定版）](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [CMP 版本兼容规则](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [KMP 上鸿蒙了（CPF 首版）](https://mings.in/posts/kmp-harmony)
- [reorderable 鸿蒙化实战：Kotlin/Native .so + CPF 统一渲染](https://blog.csdn.net/2601_95131637/article/details/166953326)
- [cryptography-kotlin](https://github.com/whyoleg/cryptography-kotlin)
- [OpenHarmony 跨平台 SIG 生态盘点](https://www.woshipm.com/share/6399283.html)
