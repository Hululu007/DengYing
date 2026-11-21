# AGENTS.md — DengYingOhos 维护手册

本文件是本工程对 AI Agent 的维护规则入口。README 解释工程是什么，本文件解释 Agent 应怎样调查、修改、验证和交付。代码、构建脚本与本次命令输出是事实来源；文档与代码冲突时先核实代码，再同步修文档。

`CLAUDE.md` 只写 `@AGENTS.md`，不维护第二份规则。

## 1. 开工前：先定位，再动手

1. 先读 `README.md`、`docs/03-架构与模块边界.md` 和本文件。
2. 按任务补读：HarmonyOS / NAPI / HAP 看 `harmony/README.md` 与 `ohos-player-notes.md`；KMP/expect/actual 看 `docs/09`、各模块 `build.gradle.kts` 与 `gradle/libs.versions.toml`；播放、网络和线程看 `docs/03`、`docs/06`、`docs/07` 和相关测试；真机行为看 `docs/04` / `docs/05`；需求或验收看 `docs/02`。
3. 检查工作区：`rtk git status --short`。保留用户未提交的改动，不覆盖、不回滚、不顺手格式化无关文件。
4. 本工程通常没有本地 CodeGraph 索引；缺索引时直接 Grep / Read。定位顺序：符号 → 当前源码 → 本次命令输出 → 文档。
5. 历史结论区分当前代码/构建、本次命令实测、文档/记忆；前两者优先。

## 2. 项目硬边界

灯影是无广告、无账号、无埋点的本地影视播放器。数据来自编译期内置源清单，不建内容库、不做推荐、不上报数据。

- 本工程是 Android + HarmonyOS 双目标：CPF 毕昇 KGP 2.2.21-1.1.0 + CMP 1.9.2-1.1.0，target 为 `androidTarget`、`jvm`、`ohosArm64`、`ohosX64`。官方 Kotlin 版本线属于 `DengYing` / `DengYingIos`，两份构建脚本不得拼成同一 Gradle 工程。
- 模块：`app/` 是 Android 壳；`shared/` 是 KMP 逻辑与 `ohosMain` actual；`ui/` 产 `libdengyui.so`；`harmony/` 是 ArkTS/ArkUI + 手写 NAPI 桥 + hvigor 打包 HAP 的现行鸿蒙壳。
- 依赖方向按 `docs/03`：`core` 不依赖上层，`domain` 不导入 Android/UI/data/playback/app，`data` 不依赖 UI，`ui → playback → data → core` 不得倒置。改 `expect` 签名同步检查各平台 `actual`；改共享数据模型检查 ArkTS/NAPI 序列化边界。
- 所有网络请求走统一 `HttpClient` / 平台传输契约，不在适配器里另建出网通道。不为「能播」放宽 URL 校验、重定向策略或明文 HTTP 例外。
- 不引入账号、广告、埋点、推荐、内容库、在线源市场、第三方 SDK 或新网络栈，除非任务明确要求并给出体积、安全、维护成本评估。日志不输出密钥、完整播放地址、搜索词或历史；`local.properties`、签名材料、探针转储不提交。

## 3. 本机命令纪律

- 本机是 Windows，工作区使用 Windows 原生路径，例如 `D:\workspace\dengY\DengYingOhos`；不用 WSL。所有 shell 命令加 `rtk` 前缀。
- Gradle 前固定 `JAVA_HOME` 为 `C:/Program Files/Java/jdk-18.0.2.1` 或其他已验证的 JDK 17+。Android SDK Platform 36 + Build-Tools 36.0.0；CPF 的 `org.jetbrains.compose.*` 构件不能直接当 jvm 变体使用。
- 构建、测试和常用 Git 操作优先用 RTK 子命令。运行 `runscript/full-check.sh` 前先读脚本与 `README.md`；脚本含鸿蒙 native 库验证。用户未明确要求时不 commit、不 push、不改签、不删分支、不清理未跟踪文件。

## 4. 验证矩阵

| 改动范围 | Windows 可验证 | 平台限制 |
|---|---|---|
| `shared/commonMain` / `shared/jvmTest` | `rtk gradlew :shared:jvmTest`、对应 Android 单测 | NAPI/HAP 行为不能由 JVM 结果代替 |
| `ui/commonMain` / ViewModel | `rtk gradlew :ui:jvmTest`、`rtk gradlew :ui:compileDebugKotlinAndroid` | ArkUI 页面与交互需目标设备证据 |
| `ui/ohosMain` / NAPI | `libdengyui.so` 链接与静态检查 | HAP 需 DevEco 的 JDK/SDK/hvigor；装机与画面需模拟器或真机 |
| Android 壳 | `rtk gradlew :app:assembleDebug`、`rtk gradlew :app:testDebugUnitTest` | 真机行为单独记录 |
| 跨模块或交付前 | `runscript/full-check.sh` 或等价逐步验证 | 检查 BUILD 成功、无新增 `e:` / `FAILED` |

- 声称「修好了 / 通过了 / 能跑」之前必须有本次修改后的命令输出或平台证据。
- 失败也写清命令、关键错误、环境差异和未完成项。Windows 上不得声称 HAP 装机、真机画面或投屏链路已验证。
- `harmony/README.md` 的模拟器 x86_64 与真机 ohosArm64 结论以当期实测为准；结论写 `docs/04` / `docs/05`，不稳定或未复测的标「未验证」。

## 5. 开发、验证与交付

- 小步、可回退：先复现或明确失败口径，再做最小闭环，再验证。不要并行跑多个鸿蒙 native 构建，clang OOM 可重试但 FROM-CACHE 不算真跑。
- 行为契约优先：播放、搜索取消、续播、源健康、存储信封、URL 校验等已有不变式，改行为必须更新需求矩阵和测试证据。
- 架构或模块边界变化先改 `docs/03`；新行为更新 `docs/02`；验证结果写 `docs/04` / `docs/05`；迁移和平台差异写 `docs/09`；鸿蒙壳状态看 `harmony/README.md`。
- 三端版本同源，功能迭代交付前递增版本；版本以本工程 `app/build.gradle.kts`、`harmony/AppScope/app.json5` 以及另外两个工程的版本声明为准，不在本文件写死数字。
- 交付前读回改动文件，`rtk git diff --check`，`rtk git status --short` 确认无构建产物或他人改动混入。最终按「改了什么 / 验证了什么 / 还有什么风险」回答。
- 发布门槛见根级 `AGENTS.md` §5.1。

## 6. Skill 调度

Skill 是按任务意图触发的操作手册，不是项目依赖。当前四份指令文档需按各工程实际任务选配安装 skill；本表只列已安装到本项目 `.agents/skills/<name>/` 的项目级 skill 和确实全局可用的 skill。未安装到项目级的选择池候选不得写入表格或当作可用能力。

| 任务信号 | Skill | 来源与边界 |
|---|---|---|
| 会话常开输出形态 | `i-have-adhd` | 全局公共区；所有回复默认使用，用户说停止时再关闭 |
| 更新 `AGENTS.md` / `CLAUDE.md`、规则漂移 | `project-agents-md` | 全局公共区；先取证再落盘，`CLAUDE.md` 仅写 `@AGENTS.md` |
| 新功能、组件、交互、行为变更 | `superpowers-brainstorming` → `software-development-workflow` | 项目级（选择池，2026-11-21）；先读 `.agents/skills/superpowers-brainstorming/SKILL.md` 与 `.agents/skills/software-development-workflow/SKILL.md`，澄清验收后小步实现 |
| 明确要求测试先行、red-green-refactor | `tdd` → `software-development-workflow` | 项目级（选择池，2026-11-21）；先读 `.agents/skills/tdd/SKILL.md` 与 `.agents/skills/software-development-workflow/SKILL.md`，先写失败测试再最小实现 |
| Bug、崩溃、播放异常、性能回退 | `diagnose` → `superpowers-verification-before-completion` | 项目级（选择池，2026-11-21）；先读 `.agents/skills/diagnose/SKILL.md` 与 `.agents/skills/superpowers-verification-before-completion/SKILL.md`，复现、最小化、假设、修复、回归 |
| 页面/主题/视觉重做 | 全局公共区未发现专门设计 Skill；按本文件 CMP 与 `DyIcons` 约束执行 | 无项目级副本；不把池内未安装候选写入表格，截图或真机证据按验证矩阵执行 |
| 重构现有 UI、去模板化 | 先按本文件审计现有页面和交互，再按新功能流程实现 | 无项目级副本；不把池内未安装候选写入表格 |
| 文档、规格、技术说明 | `doc-coauthoring` 或 `hai-simplified-technical`；审计用 `hai-audit-docs`，整体重写用 `hai-rewrite-doc` | 项目级（选择池，2026-11-21）；按实际任务先读 `.agents/skills/doc-coauthoring/SKILL.md`、`.agents/skills/hai-simplified-technical/SKILL.md`、`.agents/skills/hai-audit-docs/SKILL.md`、`.agents/skills/hai-rewrite-doc/SKILL.md`；文档只写已验证结论 |
| 写/改/审代码、防过度设计 | `karpathy-guidelines` | 项目级（选择池，2026-11-21）；先读 `.agents/skills/karpathy-guidelines/SKILL.md`，小步可验证，与仓库红线冲突以本文件为准 |
| Windows 路径、编码、命令被吞 | 按本文件「本机命令纪律」执行 | 公共区 `win-shell-paths` 全局可用，未复制到项目级 |
| Python/TypeScript MCP server 或新 MCP 工具 | `mcp-builder` | 全局公共区；先定义工具契约、错误语义和权限，再接仓库；新增项目级 MCP 需用户明确同意 |
| 声称完成、已修复、通过 | `superpowers-verification-before-completion` | 项目级（选择池，2026-11-21）；先读 `.agents/skills/superpowers-verification-before-completion/SKILL.md`，必须附本次实际命令输出 |
| 浏览器自动化卡住（可选） | 当前项目未安装浏览器自动化项目级 skill | 遇到网页抓取任务时用当前会话实际暴露的工具；不得把池内未安装候选当作可用能力 |
| AOSP / OpenHarmony 平台源（可选） | 当前项目未安装平台源码项目级 skill | 涉及系统源/HDF/发行版时先用当前会话实际暴露能力；未安装前不得写入表格 |
| 逆向本地产物（可选） | 当前项目未安装逆向分析项目级 skill | 需要时用当前会话实际暴露的 MCP / 工具，并先确认目标授权 |

- 新增需求的选配规则：先按仓库证据判断是否需要新 skill；先从选择池挑；池内没有时用全局公共区 `find-skills` 搜索，把找到的 skill 安装到选择池，再复制到本项目 `.agents/skills/<name>/` 并接线；登记来源与选配日期后才加入本表。未安装前不得把名字写入表格或当可用能力。
- 项目级 skill 清单以各工程 `.agents/skills/` 下存在 `SKILL.md` 的目录为准；Claude Code 通过 `.claude/skills` junction 加载。本表不列未安装能力。
- 全局公共区技能按全局指令使用，未安装到项目级的不写入本表。
- 全局约定：RTK 命令压缩（所有 shell 命令加 `rtk`，结果视为完整输出）；`.codegraph/` 存在时优先用 CodeGraph 定位符号；Windows 原生路径与 JDK 18；交付前版本递增。规则以当前会话实际生效的全局指令为准。

## 7. MCP 与工具调度

MCP 是外部能力连接，不是事实来源。本工程与 monorepo 根都没有 `.mcp.json` / `mcp.json`，只能使用当前会话实际暴露的工具。能力不可用时按内置工具降级，在总结中说明。

- 先搜索再调用，不确定工具名和参数时先 `tool_search`。
- 先读后写：写文件、改外部系统、发消息或执行破坏性操作前先确认目标状态与范围。
- 不把 `local.properties`、签名密码、密钥、token、用户搜索词或播放历史发送到任何 MCP。需要 `cwd` 时传当前工作区绝对路径。
- 定位顺序：Grep / 文件搜索 → 当前源码 → 本次命令输出 → 文档；MCP 返回与仓库冲突时以仓库为准。
- 新增项目级 MCP 或扩大权限需要用户明确同意。

## 8. 文档与提交

- 新行为更新 `docs/02`；有实测追加 `docs/04` / `docs/05`。架构或边界变化先改 `docs/03`。文档只写已验证结论；推测标「未验证」。
- 文档链接使用仓库相对路径；新增文件放对应目录，不把截图或日志散落根目录。
- 提交前 `rtk git diff --check`、读回改动文件、确认 `rtk git status --short` 无意外文件。除非用户要求，不自动 commit/push。
