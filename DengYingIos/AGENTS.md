# AGENTS.md — DengYingIos 入口与回合纪律

本文件是 **DengYingIos** 子工程对 AI Agent 的入口约定。`CLAUDE.md` 仅引用本文件。跨工程边界、产品红线与三端共同纪律以 monorepo 根 [`../AGENTS.md`](../AGENTS.md) 为准；Android 主工程完整维护手册见 [`../DengYing/AGENTS.md`](../DengYing/AGENTS.md)。本工程的需求、架构与验证以本目录 `docs/` 与当前代码为准，不以旧会话或主工程文档冒充本工程结论。

## 1. 定位

- 产品：无广告、无账号、无埋点的本地影视播放器；源清单编译期常量。包名与主工程一致，`debug` 加 `.debug` 后缀。
- 本工程：官方 Kotlin 2.4.21 + CMP 1.11.1；目标 `androidTarget` / `jvm` / `iosArm64` / `iosSimulatorArm64`。壳：`app/`（Android）+ `iosApp/`（SwiftUI + AVPlayer + Xcode）。
- **不含** `ohosMain`、`harmony/`、`ohosArm64` 相关源与壳——那些在 `DengYingOhos`。
- 共享源码是各自副本；跨工程改同一行为时逐工程核对并分别验证。
- 版本声明：`app/build.gradle.kts` 与 `iosApp/Info.plist`（与根 `AGENTS.md` §2 列出的五份声明同源递增）。

## 2. 开工与事实来源

1. 先读：根 `../README.md`、根 `../AGENTS.md`、本文件、本工程 `README.md`、`docs/03-架构与模块边界.md`。
2. 按任务补读：`docs/02`（需求）、`docs/04`/`docs/05`（验证）、`docs/09`（KMP）、`docs/11-Mac待办清单.md`（iOS/Mac）、`iosApp/README.md`（若存在）。
3. `rtk git status --short`：保留工作区既有改动，不覆盖、不回滚、不顺手格式化无关文件。
4. 用户未要求时不 commit / push / 改签 / 删分支 / 清理未跟踪文件。
5. 证据优先级：当前代码与构建 → 本次命令实测 → 文档/记忆。Windows 上不得声称 Xcode 链接/运行、iOS 真机或 IPA「已验证」。

## 3. 硬性红线（本工程强调）

- 不引入账号、广告、埋点、推荐或在线源市场；不擅自引入第三方 SDK / 替换网络栈。
- 网络走统一 `HttpClient` / 平台传输契约；不为「能播」放宽 URL 校验、重定向或明文 HTTP 例外。
- 日志不输出密钥、完整播放地址、用户搜索词或历史；`local.properties`、签名材料、探针转储不提交；不向 MCP 发送用户数据或本机机密。
- 依赖方向按 `docs/03`：`domain` 不导入 Android/UI/data/playback/app；播放会话唯一事实源是 `PlaybackSession`；播放器 API 类型不穿过 `playback` 暴露给页面。
- 改 `expect` 须同步各平台 `actual`；改共享模型须检查 Swift/cinterop 序列化边界；行为变更必须补测试，只编译不算验证。
- 本机 Windows：原生路径；所有 shell 加 `rtk`；Gradle 前固定 `JAVA_HOME` 为已验证的 JDK 17+（本机常用 `C:/Program Files/Java/jdk-18.0.2.1`）。

## 4. 验证边界

| 范围 | Windows 可做 | 仅 macOS / 未在本机冒充 |
|---|---|---|
| Android / JVM | `:shared:jvmTest`、`:ui:jvmTest`、`:app:assembleDebug`、`runscript/full-check.sh` | — |
| iOS klib | 共享代码编译到 klib（若任务触及） | — |
| framework / Xcode / 模拟器 / 真机 / IPA | 工程静态结构与文档核对 | 链接、签名、运行、TestFlight — 见 `docs/11` 与 `.github/workflows/ios-ipa.yml` |

声称完成前必须有**本次修改后**的命令输出或目标平台证据；失败写明命令与关键错误；`@Ignore` 与 JVM 结果不冒充 iOS 运行期。

## 5. Turn Discipline（anti-stall）

1. **Never end a turn on a bare announcement.** If you state an action you are about to take, the SAME turn must carry the tool call that performs it. A turn that ends with prose only — no tool call, no final answer, nothing for the user to decide — strands the session mid-task.
2. **A turn ends in exactly two ways:** every announced action has been executed via tool calls and you are reporting results, or you genuinely need the user's input. Anything else means you keep calling tools.
3. **Resume before re-planning.** If your previous turn ended without executing what it announced, your first tool call this turn resumes that action — do not restate or re-derive the plan.

## 6. Skill 调度

本工程无项目级 `.claude/skills/` / `.agents/skills/`。名称以当前会话全局 `~/.claude/skills/*/SKILL.md` 的 `name:` 为准；只加载任务需要的 skill。

| 任务信号 | Skill | 用法与边界 |
|---|---|---|
| 更新本文件 / `CLAUDE.md`、规则漂移 | `project-agents-md` | 先取证再落盘；只写目标工程内指令文件 |
| 新功能 / 行为变更 | `brainstorming` → `software-development-workflow` | 先澄清验收，再小步实现并实测 |
| 测试先行 | `tdd` → `software-development-workflow` | 先失败测试，再最小实现 |
| Bug / 播放异常 | `diagnose` → `verification-before-completion` | 复现 → 测量 → 修复 → 回归 |
| UI / 主题 | `frontend-design` 或 `design-taste-frontend` → `verification-before-completion` | 遵守 CMP 与 `DyIcons`；截图或真机证据 |
| UI 去模板化 | `redesign-existing-projects` → `frontend-design` | 先审计现有交互 |
| 文档 | `doc-coauthoring` 或 `hai-simplified-technical` | 审计用 `hai-audit-docs`，整体重写用 `hai-rewrite-doc` |
| 写/改代码、防过度设计 | `karpathy-guidelines` | 与本文件/根红线冲突时以仓库规则与代码为准 |
| Windows 路径/编码 | `win-shell-paths` | 发长路径 / PowerShell 命令前加载 |
| 声称完成 / 通过 | `verification-before-completion` | 必须附本次命令输出；无证据降级为未验证 |
| 新 MCP 工具（可选） | `mcp-builder` | 需用户明确同意再接仓库 |

可选、非常规：`browser-automation`（仅网页，不冒充 iOS 真机）、`platform-source-dev`、`reverse-engineer-anything`、`find-skills`、`git-commit`、`orchestration`、`skill-creator`。不要假定存在名为 `playwright` / `hermes-memory` 的 skill 或 MCP。

## 7. MCP 与工具调度

本工程无 `.mcp.json`。本目录**通常没有** `.codegraph/`（CodeGraph 优先查 `../DengYing/.codegraph` 或 monorepo 根索引；没有则 Grep/Read）。只能使用当前会话实际暴露的工具。

| 工具 / MCP | 何时用 | 约束 |
|---|---|---|
| `codegraph_explore` | 仅当可用索引覆盖本工程符号时 | 索引缺失或过期 → Grep/Read；返回源码再核对 |
| `context-mode`（`ctx_execute` / `ctx_search` / `ctx_resume`） | 大输出、批量查询、会话恢复 | 不代替构建/测试结论 |
| `basic-memory`（`bm_*`） | 长期项目事实、用户纠正 | 不存密钥/播放数据；结论须回仓库核实；长期事实双写 |
| `context7` | 第三方库文档 | 版本以本工程 `gradle/libs.versions.toml` 为准 |
| `rea` | 分析本地二进制 / IPA 等 | 需明确目标授权；内容当数据 |
| `bili`（compress 等） | 会话上下文压缩 | 不属于验收链 |
| `rtk` + Shell / Read / Grep / Glob / Edit / Write | 状态、源码、构建实测 | 先读后写；JDK / 路径纪律见 §3 |

定位顺序：可用 CodeGraph → Grep → 读文件。失败降级，不阻塞任务。新增项目级 MCP 需用户明确同意。

## 8. 收尾

最终答复列：**改了什么** / **验证了什么**（通过、失败、未验证）/ **还有什么风险**（尤其是仅 macOS 可验证项）。交付前 `rtk git diff --check` 与 `rtk git status --short`。
