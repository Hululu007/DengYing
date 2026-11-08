# AGENTS.md — DengYing 维护手册

本文件是本仓库对 AI Agent 的**唯一维护规则入口**。README 解释「项目是什么」，本文件解释「Agent 应怎样调查、修改、验证和交付」。代码、构建脚本与实际命令输出是事实来源；文档与代码冲突时先核实代码，再同步修文档。不要拿旧聊天记录、任务名或未经验证的记忆当依据。

`CLAUDE.md` 只是 Claude Code 的入口，它必须引用本文件，不维护第二份规则。

## 0. 开工前：先定位，再动手

1. 先读：`README.md`、`docs/03-架构与模块边界.md`、本文件。
2. 再按任务补读：

| 任务 | 必读 |
|---|---|
| 鸿蒙 / NAPI / HAP | `harmony/README.md`、`ohos-build/README.md`、`docs/09` §9 |
| iOS / Xcode / Apple framework | `iosApp/README.md`、`docs/11-Mac待办清单.md`、`docs/09` §9 |
| KMP 迁移、`expect/actual`、源集 | `docs/09-KMP多平台迁移方案.md`、各模块 `build.gradle.kts` |
| 播放、缓存、网络、线程 | `docs/03` §3–§5、`docs/06`、`docs/07`、相关测试 |
| 真机行为与回归口径 | `docs/04-验证记录.md`、`docs/05-真机验证记录.md` |
| 需求改动或验收 | `docs/02-需求追踪矩阵.md`，同步需求编号与测试证据 |

3. 检查工作区：`rtk git status --short`。工作区可能有用户尚未提交的改动；不覆盖、不回滚、不顺手格式化无关文件。
4. 仓库有 `.codegraph/` 时，定位符号、调用链和跨文件影响**先用 CodeGraph**，再决定是否需要 grep/读文件。命令：`codegraph explore "<符号或问题>"`；若 MCP 工具可用，用 `codegraph_explore`。仅凭文件名猜测调用关系不算调查完成。
5. 涉及历史结论时区分三类证据：当前代码/构建 → 当前命令实测 → 文档/记忆。前两者优先；记忆和旧文档只能提供线索，不能直接当验收结论。

## 1. 项目硬边界

灯影：无广告、无账号、无埋点的本地影视播放器。数据来自编译期内置的公开采集源清单，应用不建内容库、不做推荐、不上报任何数据。

- 包名 `com.dengying.player`；debug 包多一个 `.debug` 后缀，两者可同机共存（数据各自独立）。
- Android：compileSdk/targetSdk 36，minSdk 31，Kotlin + Jetpack Compose，Media3 ExoPlayer。
- 共享层：KMP（`:shared` 逻辑）+ CMP（`:ui` 界面）。Kotlin 2.4.21，AGP 8.13.2，Gradle 8.14.3。
- 三端壳：`:app`（Android）、`iosApp/`（iOS Swift 壳）、`harmony/`（鸿蒙 ArkTS/ArkUI 壳 + NAPI 桥）。
- 本工程是鸿蒙工程 `hmVideo` 的移植与多平台扩展；行为契约逐条沿用，差异与理由见 `docs/01`。

| 层 / 模块 | 职责 | 不得做什么 |
|---|---|---|
| `shared/.../core/` | 契约类型、常量、源清单 | 依赖 `data` / `domain` / `ui` / `playback` / `app` |
| `shared/.../domain/` | 平台无关业务不变式 | 导入 `android.*`、`androidx.*`，依赖 data/ui/playback/app |
| `shared/.../data/` | 网络、源适配、存储、缓存 | 依赖 UI；绕过 `HttpClient` 直接出网 |
| `shared/.../platform/` | `expect` 平台能力契约 | 把平台实现细节泄漏进 common 签名 |
| `shared/*Main/` | 各平台 `actual` | 在 `commonMain` 复制平台代码 |
| `ui/` | CMP 页面、组件、ViewModel、路由、平台 UI actual | 放进 `shared` 引入 Compose，破坏鸿蒙逻辑复用退路 |
| `app/` | Android 组合根、Media3 引擎装配、Manifest、入口 | 让 `playback` 反向依赖 `app` 或 `ui` |
| `harmony/` | ArkTS/ArkUI 壳与手写 NAPI 桥 | 假设共享层 Android 行为等于鸿蒙行为 |
| `ohos-build/` | 独立的 CPF Kotlin 构建，索引/编译 `ohosMain` | 并入根 Gradle 构建，或复制 `shared` 源码 |
| `iosApp/` | iOS SwiftUI 壳与 Xcode 工程 | 在 Windows 声称 Xcode 构建/运行已验证 |

关键规则：

- 依赖方向以 `docs/03` 为准：`ui → app（页面取组合根）`、`ui → playback → data → core`；`app` 不依赖 `ui`，下层不反向依赖上层。
- ViewModel 通过构造函数收依赖，**不摸 `DengYingApp.get()`**；只在页面层装配。
- 播放器 API 类型不得穿过 `playback` 暴露给页面，窄口子按现有 `Engine` 契约走。
- 所有网络请求走统一 `HttpClient` / 平台传输契约；不能在每个适配器里另建出网通道。
- 源清单为编译期常量（当前不做在线源市场）。不要擅自引入账号、埋点、推荐或内容库。
- 不引入第三方 SDK 或替换网络栈，除非任务明确要求且给出体积、安全、维护成本评估（SEC-001）。
- 算法、签名、解析状态的修改必须同时补/改测试；只改注释或只跑编译不算验证。

### 1.1 包边界检查

`docs/03` §2 的 grep 仍以旧 `app/src/main/java/...` 路径为示例；KMP 迁移后应以当前源码树为准。执行前先确认目标目录存在，空目录 grep 不算通过：

```powershell
$S = 'shared/src/commonMain/kotlin/com/dengying/player'
if (-not (Test-Path -LiteralPath "$S/domain")) { Write-Output '路径已变化，先定位新源码树' }
Select-String -Path "$S/domain/*.kt","$S/domain/*/*.kt" -Pattern '^import (android|androidx)\.' -ErrorAction SilentlyContinue
Select-String -Path "$S/domain/*.kt","$S/domain/*/*.kt" -Pattern '^import com\.dengying\.player\.(data|ui|playback|app)' -ErrorAction SilentlyContinue
Select-String -Path "$S/core/*.kt" -Pattern '^import com\.dengying\.player\.(data|ui|playback|app|domain)' -ErrorAction SilentlyContinue
Select-String -Path "$S/data/*.kt","$S/data/*/*.kt","$S/data/*/*/*.kt" -Pattern '^import com\.dengying\.player\.ui' -ErrorAction SilentlyContinue
Select-String -Path 'ui/src/commonMain/kotlin/com/dengying/player/ui/viewmodel/*.kt' -Pattern '^import com\.dengying\.player\.ui\.(pages|components)' -ErrorAction SilentlyContinue
```

有输出就说明发现违规；先判断是否为合法例外并记录理由，不能为了 grep 通过而机械改代码。

## 2. 本机命令纪律

- 本机是 Windows，工作区使用 Windows 原生路径，例如 `D:\workspace\dengY\DengYing`。命令必须用 PowerShell 7（`pwsh`）执行，不要在 WSL、bash、sh 或 zsh 里执行。
- 不要使用 `/mnt/d/...` 之类的 WSL 路径。传入的工具工作目录已经是正确的项目目录，直接执行 `rtk <命令>`，不要再次拼接或复制项目目录。
- 所有 shell 命令都加 `rtk` 前缀。RTK 没有专用子命令时，使用 `rtk proxy pwsh -NoProfile -Command '<命令>'`。RTK 已位于 Windows 的 PATH 中，不需要使用 Linux 的绝对路径。
- 构建、测试和常用 Git 操作优先使用 RTK 子命令，例如 `rtk gradlew :shared:jvmTest`、`rtk git status`。含特殊字符的长文本使用 PowerShell 原生命令写入，不要使用 bash heredoc。
- 控制台回显中文可能乱码，不代表文件内容损坏；以读回文件为准。
- 运行 Gradle 前先确认 `JAVA_HOME`。本机脚本以 JDK 18 验证；不要用默认 JDK 8 配置。
- 构建和测试产物已被忽略，不要提交 `build/`、`.gradle/`、`ohos-build/build/`、`.idea/`、`.tmp/`。
- 用户未要求时不要提交、推送、改签、删除分支或清理未跟踪文件。

## 3. 修改纪律

- **小步、可回退**：先复现或明确失败口径，再改最小闭环，再验证。不要先大改架构再找理由。
- **行为契约优先**：播放、搜索取消、续播、源健康、存储信封、URL 校验等已有不变式，改行为必须更新需求矩阵和测试证据。
- **平台对称**：改 `expect` 签名要同时检查 `androidMain`、`jvmMain`、`iosMain`、`ohosMain` 的 `actual`；改共享数据模型时检查三端壳与 NAPI 的序列化边界。
- **线程和生命周期**：网络在 IO 且受 `ConcurrencyGate` 约束；Store 写操作要如实返回是否落盘；`viewModelScope` 已取消后不得拿它写关键数据；已有裸 `Thread` 例外不得随意复制。
- **内存和体积**：本应用不建内容库、不做推荐。新增图片、图标、依赖必须说明包体与启动成本；图标用 `DyIcons` 自绘，不引入整套图标库。
- **安全与隐私**：无账号、无埋点、不上报。日志不输出密钥、完整播放地址、用户搜索词或历史。对任何外部 URL 校验、重定向和明文 HTTP 例外保持现状原则，不为了「能播」放宽全局校验。
- **现场文件**：`local.properties`、签名材料、`*.hprof`、`hs_err_pid*.log`、`err.txt` 等不得纳入提交；发现未跟踪的个人文件时只提及，不擅删。
- **文档同步**：行为、命令、路径、验证结论变化时更新对应文档。文档只写已验证结论；推测标明「未验证」，失败就写失败原因和下一步，不粉饰。

## 4. 验证矩阵

按改动范围选择最小**足够**集合，不把所有任务都跑成全量：

| 改动范围 | 最低验证 | 补充 |
|---|---|---|
| 纯 `domain` / `core` 逻辑 | `rtk gradlew :shared:jvmTest` | 涉及 Android 特有分支再跑 `:shared:testDebugUnitTest` |
| `ui/commonMain` 或 ViewModel | `rtk gradlew :ui:jvmTest` | 编译 `:ui:compileDebugKotlinAndroid`，必要时装 APK 看页面 |
| Android 壳、Manifest、引擎装配 | `rtk gradlew :app:assembleDebug` | `:app:testDebugUnitTest`；真机行为看 `docs/05` |
| 网络、HLS、连接复用 | 对应 JVM 单测 | Android 差异必须跑 `HttpClientConnectionReuseDeviceTest`，不能只看 JVM |
| 跨模块或交付前 | `runscript/full-check.sh` 或等价逐步验证 | 检查 BUILD 成功、无新增 `e:` / `FAILED` |
| `ohosMain` / NAPI / ArkTS | `rtk gradlew --project-dir ohos-build :compileKotlinOhosArm64 --console=plain` 加 HAP 构建 | 按 `harmony/README.md` 的装机纪律验证生效 |
| iOS 共享代码 | `:shared` 的 iOS 编译（Windows 可到 klib） | 链接、Xcode 构建和运行只能在 macOS 验证 |
| 发布/文档验收 | 与任务相关命令的实际输出 | 更新 `docs/04` / `docs/05` 对应条目 |

验证规则：

- 声称「修好了 / 通过了 / 能跑」之前，必须有**本次修改后**的命令输出或真机证据。
- 失败也算结果：记录命令、关键错误、环境差异和未完成项；不得用「应该可以」替代。
- 只被 `@Ignore` 跳过、没有执行的测试不能算覆盖；真机测试不能拿 JVM 结果冒充。
- UI 截图、HAP/APK 安装结果、hilog/dumpLayout、Xcode 输出都是证据，附路径或关键行。
- 全量验证优先 `runscript/full-check.sh`；需要逐项测试统计时再运行 `runscript/counts.sh`。脚本自身的 JDK/路径已固定，运行前确认文件仍存在，脚本过期就先修脚本或说明。

## 5. 任务收尾格式

最终答复给用户时简洁列出：

1. **改了什么**：用文件路径 + 关键行为说明。
2. **验证了什么**：实际命令与结果（通过/失败/未验证分别写）。
3. **还有什么风险**：平台差异、未跑的测试、需要用户决策的事项。

没有跑测试就不要写「全部通过」；文档改动至少做文件读回、链接/路径检查和 `rtk git diff --check`。交付前最后再看一次 `rtk git status --short`，确认没有把构建产物、临时文件或别人的改动混进提交。

## 6. Skill 调度

Skill 是**按任务意图触发的操作手册**，不是项目依赖，也不等于 MCP 工具。先判断任务形态，再加载最匹配的 skill；skill 与仓库规则冲突时以本文件、代码和构建脚本为准。

### 6.1 触发原则

- 只加载当前任务真正需要的 skill，不一次加载整个技能库。
- 同一个任务允许多个 skill 组合，但必须说明**先后顺序**：调查/诊断 → 设计/实现 → 验证。
- 用户点了某个 skill（例如 `$tdd`）时优先遵守其流程；但不得绕过本文件的安全、验证和代码边界。
- 项目内没有 `.claude/skills/`、`.agents/skills/` 或 `.codex/skills/` 时，不编造项目 skill；使用当前 Agent 环境已加载的 skill，找不到就按本文件直接工作。
- 新增项目专用 skill 时放在 `.claude/skills/<name>/SKILL.md`（Claude 读）或 `.agents/skills/<name>/SKILL.md`（通用 Agent 读），并在下面表格登记触发条件；不要放临时笔记。

### 6.2 路由表

| 任务信号 | 首选 skill | 用法与边界 |
|---|---|---|
| 新功能、组件、交互、行为变更 | `brainstorming` → `software-development-workflow` | 先澄清范围和设计，再按模块边界实现；不要跳过需求/验收口径 |
| 明确要求测试先行、red-green-refactor | `tdd` → `software-development-workflow` | 先写失败测试，再最小实现；纯 UI 视觉任务不强行套 TDD |
| Bug、崩溃、播放异常、性能回退 | `diagnose` → `verification-before-completion` | 复现 → 最小化 → 假设 → 测量 → 修复 → 回归；禁止先凭猜测改代码 |
| 前端页面、主题、视觉重做 | `frontend-design` 或 `design-taste-frontend` → `verification-before-completion` | 必须遵守现有 Compose/CMP 结构与 `DyIcons`；真实截图验证，不以「看起来应该行」收尾 |
| 重构现有 UI、去模板化 | `redesign-existing-projects` → `frontend-design` | 先审计现有页面和交互，保持需求与无障碍目标 |
| 文档、规格、README、设计说明 | `doc-coauthoring` 或 `hai-simplified-technical` | 事实先核实；需要审计时用 `hai-audit-docs`，需要整体重写时用 `hai-rewrite-doc` |
| 文档与代码冲突、术语漂移 | `hai-audit-docs` → `hai-rewrite-doc`（仅在要求重写时） | 先产出证据与优先级，不直接凭文档改代码 |
| Python MCP server / 新 MCP 工具 | `mcp-builder` | 先定义工具契约、错误语义和权限，再接本仓 |
| 真实浏览器自动化 | `playwright` | 需要页面截图/DOM 时用；不能替代 Android 真机或鸿蒙装机验证 |
| 创建/修改 PPT、Excel、PDF、Word | 对应 `presentations` / `spreadsheets` / `pdf` / `documents` skill | 生成后必须渲染或读回校验，不交付未检查的文件 |
| Windows 路径、编码、命令被吞 | `win-shell-paths` | 先读再发命令；本文件第 2 节是最低要求 |
| 声称「完成 / 已修复 / 通过」 | `verification-before-completion` | 必须有本次实际命令输出；没有证据就降级为「未验证」 |

### 6.3 Skill 调用后的记录

在任务总结中只需写：用了哪个 skill、它改变了什么（如先测试后实现），以及对应验证结果。不要把 skill 全文复制进仓库，也不要把 skill 当成交付物。

## 7. MCP 调度

MCP 是**外部能力连接**，不是事实来源。一次任务里优先用能直接验证仓库状态的工具：Shell、CodeGraph、测试和构建；MCP 只补足它们看不到的信息。

### 7.1 通用协议

1. **先搜索再调用**：不确定 server/tool 名称时先 `tool_search`；不能凭记忆臆造工具名和参数。
2. **先读后写**：写文件、发消息、建任务、改外部系统前，先读取目标状态并确认范围。
3. **不泄漏秘密**：不把 `local.properties`、签名密码、密钥、token、用户搜索词/历史发送到任何 MCP；调用需要 `cwd` 的项目记忆工具时传当前工作区绝对路径（例如 `D:\workspace\dengY\DengYing`）。
4. **结果交叉验证**：MCP 返回的代码/文档可能是旧索引或外部副本；与当前仓库冲突时以仓库为准，并重新读取文件。
5. **失败要降级**：MCP 不可用、无权限或返回异常时，不阻塞任务；用本地 Shell/文件工具完成，在总结中注明「该 MCP 未使用/不可用」。
6. **仓库没有 `.mcp.json`** 时，不能假设项目级 MCP 已配置。只能使用当前会话实际暴露的工具；新增项目级 MCP 需用户明确同意，并提交 server 配置与权限说明。

### 7.2 本仓库优先使用的 MCP

| MCP / 工具 | 何时用 | 约束 |
|---|---|---|
| `codegraph` / `codegraph_explore` | 定位符号、调用链、谁调用谁、跨模块影响 | 仓库存在 `.codegraph/` 时才用；先用它再 grep；以返回的当前源码为准 |
| `hermes-memory.memory_search` | 开工前不确定项目/环境事实，或需要找过去的纠正 | 只读辅助，不能替代读代码；结论仍要核实 |
| `hermes-memory.memory_add` / `memory_replace` / `memory_remove` | 用户明确纠正、长期有效的环境事实、稳定的工具坑 | 目标选 `memory`/`user`/`failure`；不存临时任务状态；改前先 search 原文 |
| `hermes-memory.session_search` | 需要从历史会话找原话、决策或失败证据 | 历史记录≠当前事实；索引旧结论后回到代码/文档核对 |
| `playwright` | 需要真实浏览器截图、DOM 检查或网页交互 | 仅网页；不冒充 Android/鸿蒙/iOS 真机验证；截图需读回检查 |
| `node_repl` | 需要脚本化数据整理、临时计算、仓库外小工具 | 不把运行结果当构建结果；文件改动仍走仓库路径和 diff |
| `context_mode` | 大输出命令、批量查询、会话恢复 | 用它压缩噪声；最终结论仍以仓库文件和实测命令为准 |

`context7`、`bili` 等由用户环境提供的 MCP 不属于本项目验收链。引用第三方 API 时可用 `context7` 查文档，但版本以 `gradle/libs.versions.toml` 和实际解析结果为准；`bili` 等外部数据服务不得把内容或用户数据写进本仓库。

### 7.3 工具选择顺序

```text
定位代码:  CodeGraph MCP/CLI → grep → 读文件
历史事实:  memory_search → session_search → 当前文档
官方/API:  当前代码与锁文件 → context7/官方文档 → 记忆
运行验证:  pwsh/gradlew/真机命令 → 必要时 Playwright（仅网页）
外部沟通:  先读目标状态 → 经用户确认 → 调用对应 MCP
```

## 8. 文档与提交

- 新行为：更新 `docs/02` 的追踪矩阵；有实测就追加 `docs/04` / `docs/05`，附日期与环境。
- 架构或模块边界变化：先改 `docs/03`，再改代码；不要先写「已实现」再补代码。
- 迁移/平台差异：`docs/09`；鸿蒙壳：`harmony/README.md`；iOS：`iosApp/README.md` + `docs/11`。
- 文档链接使用仓库相对路径；新增文件放对应目录，不把截图/日志散落到根目录。
- 提交前：`rtk git diff --check`、读回改动文件、确认 `rtk git status --short` 无意外文件。
- 提交信息说明「为什么」而不只是「改了什么」；除非用户要求，不自动 commit/push。
