# 灯影 · Android

无广告、无账号、无埋点的本地影视播放器（Android 版）。

数据来自内置的公开采集源清单，应用本身不建内容库、不做推荐、不上报任何数据。

- **包名**：`com.dengying.player`
- **compileSdk / targetSdk**：36（Android 16）
- **minSdk**：31（Android 12）
- **语言 / UI**：Kotlin + Jetpack Compose
- **播放引擎**：AndroidX Media3 ExoPlayer

行为契约、错误分类与大量实测结论沉淀在 [`docs/`](docs) 目录，逐条可追溯。

## 构建

前置：JDK 17 或更高（本仓库用 JDK 18 验证）、Android SDK Platform 36 + Build-Tools 36.0.0。

```bash
# 指向本机 SDK（或用环境变量 ANDROID_HOME）
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk（约 39 MB）
./gradlew :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk（约 31 MB）
./gradlew :app:testDebugUnitTest  # 领域层单测（103 例）
```

`local.properties` 不入库。

### 两个包的区别

| | 包名 | 体积 | 用途 |
|---|---|---|---|
| `app-debug.apk` | `com.dengying.player.debug` | 39.3 MB | 日常调试，可覆盖安装不丢数据 |
| `app-release.apk` | `com.dengying.player` | 31.2 MB | 自用装机，**体积小 8 MB** |

**两者签名相同**（`CN=Android Debug`，复用 AGP 自动生成的 debug 签名配置，有效期到 2055-11-04），
因为本项目只做本地安装、不上架，不需要正式发布密钥。未签名的 APK 是装不上的，
所以 `release` 必须带签名——这一步已在 `app/build.gradle.kts` 里配好，直接 `assembleRelease` 即可。

**注意**：包名不同意味着两者可以同时装在同一台设备上（各自独立的数据）。
若将来换成正式密钥，必须**先卸载再装**——签名变更 Android 不允许覆盖安装。

调试专用的 `DebugPlayReceiver`（广播触发播放）只在 debug 包中，
release 包的 manifest 里不含它，已用 `aapt2 dump xmltree` 核对。

## 代码结构

```
app/src/main/java/com/dengying/player/
├── domain/          纯 Kotlin，零 Android 依赖 —— 全部不变式在此，可在 JVM 上单测
│   ├── model/       协议 DTO 与线路解析
│   ├── TitleKey                跨源折叠键（FR-204）
│   ├── RequestOutcome          源请求四态（FR-108）
│   ├── UrlGuard                播放地址校验（SEC-005）
│   ├── StoreGate / StoreEnvelope   存储就绪闸门与版本信封（FR-507/508）
│   ├── LedgerPrune / FavoritePrune 容量淘汰（FR-504/509）
│   ├── ResumeDecide            续播直达判定（FR-506）
│   ├── SearchSession           搜索会话序号（FR-207）
│   ├── PageBudget              页面级预算与完成度（NFR-003）
│   ├── ConcurrencyGate         全局出站并发闸门（FR-105）
│   ├── HlsVariants             清晰度变体（FR-412）
│   ├── SourceHealthRules       源健康分（FR-107）
│   ├── SourceOverrideRules     源启用覆盖层（FR-601）
│   ├── CastUtil                可投性与投屏失败分流（FR-419/423）
│   └── ConfirmSpec / SearchNotice / TouchTarget / LogTags
├── data/
│   ├── net/         HttpClient（唯一出网口，经闸门）、HlsUtil（解析与探测）
│   ├── source/      源清单、聚合客户端、8 个协议适配器、签名常量
│   └── store/       SharedPreferences 落地
├── playback/        Media3 封装（页面不认识 ExoPlayer 类型）
└── ui/              Compose：7 个页面 + 主题 + 组件 + 路由
```

依赖方向严格单向：

```
ui ──> playback ──> domain
 │                    ↑
 └──> data ───────────┘        data ✗→ ui,  domain ✗→ 任何 Android 类型
```

`domain` 不导入任何 `android.*`（`org.json` 与 `java.security` 除外，见
[`docs/03-架构与模块边界.md`](docs/03-架构与模块边界.md) 的说明）。

## 文档

| 文档 | 内容 |
|---|---|
| [docs/01-移植说明.md](docs/01-移植说明.md) | 范围、分层设计与逐项设计决策 |
| [docs/02-需求追踪矩阵.md](docs/02-需求追踪矩阵.md) | 需求编号 → 代码 → 测试证据 |
| [docs/03-架构与模块边界.md](docs/03-架构与模块边界.md) | 组件职责、依赖约束、强制手段 |
| [docs/04-验证记录.md](docs/04-验证记录.md) | 实际执行的命令与观察结果 |
| [docs/05-真机验证记录.md](docs/05-真机验证记录.md) | 真机装机后的逐项走查与探针证据 |
| [docs/06-播放体验与性能改造.md](docs/06-播放体验与性能改造.md) | 播放体验与性能改造的需求、设计与决策 |
| [docs/07-播放体验测试计划.md](docs/07-播放体验测试计划.md) | 播放体验改造的测试计划与需求追踪矩阵 |
| [docs/08-播放器交互缺陷修复计划.md](docs/08-播放器交互缺陷修复计划.md) | seek 反馈、播放手势、亮度/音量调节与内嵌位残留的根因与修法 |

## 已知边界

- **深色模式**未实现：页面内大量硬编码浅色，当前沿用浅色且在主题里注明，不假装支持。
- **投屏**只到"可投性判定 + 入口禁用态"：`CastUtil` 的纯逻辑已完成并有测试，
  平台侧（DLNA/Cast 通道）未接入。
- **源清单**为编译期常量，改源需发版。
