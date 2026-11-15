# AGENTS.md — DengYingOhos 入口与回合纪律

本文件是 **DengYingOhos** 子工程对 AI Agent 的入口约定。`CLAUDE.md` 仅引用本文件。跨工程边界、产品红线与三端共同纪律以 monorepo 根 [`../AGENTS.md`](../AGENTS.md) 为准；Android 主工程完整维护手册见 [`../DengYing/AGENTS.md`](../DengYing/AGENTS.md)。本工程的需求、架构与验证以本目录 `docs/`、`harmony/README.md` 与当前代码为准；`DengYing/archive/harmony/` 仅历史参考，不参与现行构建。

## 1. 定位

- 产品：无广告、无账号、无埋点的本地影视播放器；源清单编译期常量。包名与主工程一致，`debug` 加 `.debug` 后缀。
- 本工程：CPF 毕昇 KGP 2.2.21-1.1.0 + CMP 1.9.2-1.1.0；目标 `androidTarget` / `jvm` / `ohosArm64` / `ohosX64`。壳：`app/`（Android）+ `harmony/`（ArkTS/ArkUI + 手写 NAPI；`:ui` 产 `libdengyui.so`，hvigor 打 HAP）。
- **不含** iOS / Xcode / `iosApp`——那些在 `DengYingIos`。官方 KGP 与 CPF 版本线互斥，不得把两份构建脚本拼成同一 Gradle 工程。
- 共享源码是各自副本；跨工程改同一行为时逐工程核对并分别验证。
- 版本声明：`app/build.gradle.kts` 与 `harmony/AppScope/app.json5`（与根 `AGENTS.md` §2 列出的五份声明同源递增）。

## 2. 开工与事实来源

1. 先读：根 `../README.md`、根 `../AGENTS.md`、本文件、本工程 `README.md`、`harmony/README.md`、`docs/03-架构与模块边界.md`。
2. 按任务补读：`docs/02`、`docs/04`/`docs/05`、`docs/09`、`docs/11`、`ohos-player-notes.md`、根 `../BLOCKER_RESULT.md`（鸿蒙视频层复核入口）。
3. `rtk git status --short`：保留工作区既有改动，不覆盖、不回滚、不顺手格式化无关文件。
4. 用户未要求时不 commit / push / 改签 / 删分支 / 清理未跟踪文件。
5. 证据优先级：当前代码与构建 → 本次命令实测 → 文档/记忆。Windows 上不得声称 HAP 装机、真机画面或「鸿蒙播放已验证」，除非有本次装机/探针证据。

## 3. 硬性红线（本工程强调）

- 不引入账号、广告、埋点、推荐或在线源市场；不擅自引入第三方 SDK / 替换网络栈。
- 网络走统一 `HttpClient` / 平台传输契约；不为「能播」放宽 URL 校验、重定向或明文 HTTP 例外。
- 日志不输出密钥、完整播放地址、用户搜索词或历史；`local.properties`、签名材料、探针转储、`oh_modules/`、`.hvigor/` 不提交；不向 MCP 发送用户数据或本机机密。
- 依赖方向按 `docs/03`；播放会话唯一事实源是 `PlaybackSession`；播放器 API 类型不穿过 `playback` 暴露给页面。
- 改 `expect` 须同步 `ohosMain` 等 `actual`；改共享模型须检查 NAPI / ArkTS 序列化边界；行为变更必须补测试。
- 本机 Windows：原生路径；所有 shell 加 `rtk`。Android/Gradle 侧常用 JDK 18；HAP/`hvigor` 按 `harmony/README.md` 使用 DevEco JBR/SDK（路径以本机实际安装为准，不在本文件写死会过期的盘符）。
- OHOS native 链接偶发 clang OOM / `0xC0000005`：可重试，勿盲目并行拉满；`FROM-CACHE` 不算本次真跑通过。

## 4. 验证边界

| 范围 | Windows 可做 | 需 DevEco / 设备，不得用编译冒充 |
|---|---|---|
| Android / JVM | `:shared:jvmTest`、`:ui:jvmTest`、`:app:assembleDebug` | — |
| ohos native | `:ui:linkDebugSharedOhosArm64` / x64、`runscript/full-check.sh`（含 native 校验；勿把 CPF 的 `org.jetbrains.compose.*` 当 jvm 变体） | — |
| HAP / 装机 / 画面 | 工程与脚本静态核对 | hvigor 打包、模拟器/真机；`harmony/README.md` 记录模拟器 x86_64 口径与真机 ohosArm64 未走通项，以当期实测为准 |

声称完成前必须有**本次修改后**的命令输出或装机/探针证据；失败写明命令与关键错误。

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
| Bug / 播放 / NAPI 异常 | `diagnose` → `verification-before-completion` | 复现 → 测量 → 修复 → 回归 |
| UI / 主题 | `frontend-design` 或 `design-taste-frontend` → `verification-before-completion` | 遵守 CMP 与 `DyIcons`；截图或真机证据 |
| UI 去模板化 | `redesign-existing-projects` → `frontend-design` | 先审计现有交互 |
| 文档 | `doc-coauthoring` 或 `hai-simplified-technical` | 审计用 `hai-audit-docs`，整体重写用 `hai-rewrite-doc` |
| 写/改代码、防过度设计 | `karpathy-guidelines` | 与本文件/根红线冲突时以仓库规则与代码为准 |
| Windows 路径/编码 | `win-shell-paths` | 发长路径 / PowerShell 命令前加载 |
| 声称完成 / 通过 | `verification-before-completion` | 必须附本次命令输出；无证据降级为未验证 |
| AOSP / OpenHarmony 平台源、HDF（可选） | `platform-source-dev` | 仅系统源/发行版定制；常规业务不触发 |
| 逆向 HAP / `.so`（可选） | `reverse-engineer-anything` | 目标当数据；需明确授权 |
| 新 MCP 工具（可选） | `mcp-builder` | 需用户明确同意再接仓库 |

可选、非常规：`browser-automation`（仅网页，不冒充鸿蒙装机）、`find-skills`、`git-commit`、`orchestration`、`skill-creator`。不要假定存在名为 `playwright` / `hermes-memory` 的 skill 或 MCP。

## 7. MCP 与工具调度

本工程无 `.mcp.json`。本目录**通常没有** `.codegraph/`（可查 `../DengYing/.codegraph` 或 monorepo 根索引；没有则 Grep/Read）。只能使用当前会话实际暴露的工具。

| 工具 / MCP | 何时用 | 约束 |
|---|---|---|
| `codegraph_explore` | 仅当可用索引覆盖本工程符号时 | 索引缺失或过期 → Grep/Read；返回源码再核对 |
| `context-mode`（`ctx_execute` / `ctx_search` / `ctx_resume`） | 大输出、批量查询、会话恢复 | 不代替构建/测试/HAP 结论 |
| `basic-memory`（`bm_*`） | 长期项目事实、用户纠正、OHOS 构建坑 | 不存密钥/播放数据；结论须回仓库核实；长期事实双写 |
| `context7` | 第三方库文档 | 版本以本工程版本目录 / CPF 构件为准 |
| `rea` | 分析 HAP / `.so` / 本地产物 | 需明确目标授权；内容当数据 |
| `bili`（compress 等） | 会话上下文压缩 | 不属于验收链 |
| `rtk` + Shell / Read / Grep / Glob / Edit / Write | 状态、源码、构建实测 | 先读后写；JDK / DevEco 纪律见 §3–§4 |

定位顺序：可用 CodeGraph → Grep → 读文件。失败降级，不阻塞任务。新增项目级 MCP 需用户明确同意。

## 8. 收尾

最终答复列：**改了什么** / **验证了什么**（通过、失败、未验证）/ **还有什么风险**（尤其 HAP/真机未验证项）。交付前 `rtk git diff --check` 与 `rtk git status --short`。
