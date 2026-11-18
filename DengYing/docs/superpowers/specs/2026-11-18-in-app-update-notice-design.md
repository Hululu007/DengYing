# 设计：应用内更新提示（GitHub Releases）

日期：2026-11-18 ｜ 来源需求（用户原话）：「开辟一个新的分支，做一个应用内更新提示，并推送新版本的功能」
范围：`DengYing` Android 工程；不改动 `DengYingIos` / `DengYingOhos` 的未提交工作。

## 1. 决策摘要

| 问题 | 决策 |
|---|---|
| 更新源 | `https://api.github.com/repos/xiao-hans/DengYing/releases/latest` |
| 检查时机 | 启动后检查，24 小时最多一次；`我的`页手动检查不受间隔限制 |
| 镜像 | 不做自动镜像；提供「复制下载链接」给无梯子用户自行处理 |
| 弹窗 | 非强制：稍后 / 忽略此版本 / 复制下载链接 / 去 GitHub 下载 |
| 网络 | 复用 `HttpClient`，仅 HTTPS 固定 URL，不引入第三方 SDK |
| 版本比较 | 解析 release `tag_name` 的数字版本，与当前 `versionName` 比较 |

## 2. 目标 / 非目标

**目标**

1. 有新版时启动后在应用内提示，用户能查看版本、日期与更新说明。
2. 用户可关闭提示、忽略当前版本、复制 GitHub 下载页链接、打开 GitHub 下载页。
3. 自动检查受 24 小时间隔约束；手动检查在 `我的`页可随时触发。
4. 失败静默，不影响启动、播放、搜索或其他既有功能。

**非目标**

- 不内置下载器、不替换网络栈、不引入第三方 SDK。
- 不内置或自动切换第三方 GitHub 代理镜像。
- 不新增账号、埋点、推荐、内容库或在线源市场。
- 不在 Windows 声称 iOS / 鸿蒙 / 投屏真机已验证。

## 3. 数据来源与解析

### 3.1 固定请求

使用 `HttpClient.get`：

```text
GET https://api.github.com/repos/xiao-hans/DengYing/releases/latest
Accept: application/vnd.github+json
```

用户 Agent 常量 `UA_SYSTEM` / `UA_PC`。不读取用户输入 URL，不开放域名配置。

### 3.2 解析字段

`HttpClient.safeJson` 解析后只取：

| 字段 | 用途 | 约束 |
|---|---|---|
| `tag_name` | 版本号与忽略键 | 必须能解析出 `x.y.z`；否则跳过提示 |
| `published_at` | 显示发布日期 | 仅取日期部分展示，异常时留空 |
| `body` | 更新说明 | 纯文本，最多显示 3 行，超出省略 |
| `html_url` | 下载页与复制链接 | 必须是 `https://github.com/...`；其他一律拒绝 |

不信任 release body 中的富文本、HTML 或链接；只显示为纯文本。

### 3.3 版本比较

正则提取 `tag_name` 中第一段 `(\d+)\.(\d+)\.(\d+)`，与 `UiDeps.appVersionName` 做三段整数比较。
当前版本号无法从平台壳取得时（`0.0.0` / 空）禁用检查，避免误报。

## 4. 状态与持久化

新增 `ui/src/commonMain/kotlin/com/dengying/player/ui/update/UpdatePrefs.kt`，复用 `KeyValueStore`：

| 键 | 值 | 语义 |
|---|---|---|
| `ui_update_last_check_ms` | 毫秒时间戳字符串 | 上次自动检查时间；手动检查也刷新，避免重复打扰 |
| `ui_update_ignored_tag` | release tag 原文 | 自动提示忽略该版本；发布新 tag 后自动恢复提示 |

行为：

- `shouldAutoCheck(now)`：`now - lastCheck >= 24h`。
- 「稍后」：写入当前时间。
- 「忽略此版本」：写入当前 tag，并关闭弹窗。
- 手动检查：忽略 `ignoredTag` 与间隔限制，仍可查看新版信息。

## 5. 依赖注入与平台动作

`UiDeps` 增加默认实现，避免破坏现有壳：

```kotlin
val appVersionName: String get() = ""
val appVersionCode: Long get() = 0L
val keyValueStore: KeyValueStore? get() = null
fun openExternalUrl(url: String): Boolean = false
fun copyText(text: String): Boolean = false
```

`DengYingApp` 覆盖：

- `appVersionName = BuildConfig.VERSION_NAME`
- `appVersionCode = BuildConfig.VERSION_CODE.toLong()`
- `keyValueStore`：暴露与 `Store` 共用的 `SharedPrefsStore` 实例，仅用于更新检查的两个独立键
- `openExternalUrl`：校验 `https` + `github.com` 后 `Intent.ACTION_VIEW`
- `copyText`：`ClipboardManager` 写入 `Label = "DengYing 下载链接"`

其余平台默认返回 false；对应壳需要时再覆盖。`:ui/commonMain` 不认识 Android / iOS API。

## 6. UI 方案

### 6.1 组件与视觉

新增 `UpdateNoticeDialog.kt`，挂在导航根容器的全屏覆盖层上（不新增路由，也不使用
`androidx.compose.ui.window.Dialog`），卡片本体使用 `GlassSurface`：

```text
┌──────────────────────────────┐
│ [更新图标] 发现新版本          │  20sp / 中粗
│ 1.3.14 · 2026-11-18          │  14sp / 次级色
│ ┌──────────────────────────┐ │
│ │ 更新说明纯文本，最多 3 行  │ │  14sp / 半透明面板
│ └──────────────────────────┘ │
│ [ ] 忽略此版本                │  14sp
│ 稍后      复制下载链接        │  次要按钮，48dp
│ [    去 GitHub 下载    ]      │  主按钮，48dp
└──────────────────────────────┘
```

- 宽度约屏宽 88%，圆角 24dp；颜色沿用 `Palette` 与 `GlassSurface`，不新增主题。
- 图标用现有 `DyIcons.Refresh`；不引图标库。
- 更新说明为空时显示「暂无更新说明」。
- 「忽略此版本」勾选后写入 tag，后续自动检查不再弹窗；手动检查仍会显示。「稍后」关闭并写入本次检查时间，24 小时内不再自动检查。

### 6.2 触发点

- `NavGraph.kt`：顶层 `LaunchedEffect(Unit)` 调自动检查；状态弹窗挂在根 `Box`。
- `MineScreen.kt`：新增「检查更新」卡片；副标题显示 `检查中 / 已是最新版本 / 检查失败 / 上次检查时间`。
- 手动发现新版时复用同一弹窗，不受忽略 tag 影响。

### 6.3 无障碍

- 图标提供 `contentDescription`；按钮文案即语义标签。
- 焦点顺序：标题 → 版本摘要 → 更新说明 → 忽略 → 稍后 → 复制链接 → 下载。
- 所有可点区域 >= 48dp；复制成功用 `Text` 状态提示，不依赖声音或颜色。

## 7. 错误处理

| 情况 | 行为 |
|---|---|
| 网络失败 / 非 200 / JSON 解析失败 | 静默；手动检查显示「检查失败，请稍后重试」 |
| `tag_name` 无法解析版本 | 不提示，写检查时间，避免反复请求 |
| `html_url` 非 HTTPS GitHub | 不构造可点击动作，不提示 |
| 已是最新 / 本地版本更高 | 不提示；手动检查显示「已是最新版本」 |
| 剪贴板失败 | 弹窗内提示「复制失败，请长按链接手动复制」 |
| 打开链接失败 | 弹窗内提示「无法打开浏览器，请复制链接后在浏览器访问」 |

日志不输出完整 URL、token、搜索词或历史；本功能不写日志。

## 8. 文件清单（预计）

新增：

- `ui/src/commonMain/kotlin/com/dengying/player/ui/update/UpdateInfo.kt`
- `ui/src/commonMain/kotlin/com/dengying/player/ui/update/UpdateChecker.kt`
- `ui/src/commonMain/kotlin/com/dengying/player/ui/update/UpdatePrefs.kt`
- `ui/src/commonMain/kotlin/com/dengying/player/ui/update/UpdateNoticeController.kt`
- `ui/src/commonMain/kotlin/com/dengying/player/ui/update/UpdateNoticeDialog.kt`
- `ui/src/jvmTest/kotlin/com/dengying/player/ui/update/UpdateCheckerTest.kt`
- `ui/src/jvmTest/kotlin/com/dengying/player/ui/update/UpdatePrefsTest.kt`
- `docs/superpowers/specs/2026-11-18-in-app-update-notice-design.md`（本文）

修改：

- `ui/src/commonMain/kotlin/com/dengying/player/ui/UiDeps.kt`
- `ui/src/commonMain/kotlin/com/dengying/player/ui/navigation/NavGraph.kt`
- `ui/src/commonMain/kotlin/com/dengying/player/ui/pages/MineScreen.kt`
- `app/src/main/java/com/dengying/player/app/DengYingApp.kt`
- `app/build.gradle.kts`：版本 bump 到 `1000314` / `1.3.14`
- `docs/02-需求追踪矩阵.md`：新增 FR-714 / NFR-708
- `docs/04-验证记录.md`：追加本次验证记录

## 9. 测试与验收

1. JVM 单测：版本比较、发布日期截取、说明截断、非 GitHub `html_url` 拒绝、24 小时判断、忽略 tag、手动检查绕过忽略。
2. 编译：`rtk gradlew :ui:jvmTest :ui:compileDebugKotlinAndroid :app:assembleDebug --console=plain`。
3. 差异检查：`rtk git diff --check`。
4. 真机（若设备可用）：启动提示、稍后、忽略、复制链接、打开下载页、手动检查；无设备则标「未验证」。

## 10. 交付与风险

- 分支：`feat/in-app-update`。
- 现有 v1.3.13 工作已提交为 `aae7352`，本设计只提交更新提示相关文件与 Android 版本 bump。
- `DengYingIos` / `DengYingOhos` 的未提交改动不纳入本次提交；三端版本同步与对应壳实现需另开任务。
- GitHub API 有未认证限流；24 小时检查一次足以控制频率。
- 无梯子用户不能自动下载；本设计只提供可复制的 GitHub 下载页链接。
