# Hululu007 MacCMS 源接入设计

日期：2026-11-20

## 背景与目标

`D:/workspace/dengY/Hululu007视频接口接入文档.md` 调研了 Hululu007 相关仓库，建议优先从 `Hululu007/tvbox-api-backup--` 的 TVBox 聚合接口筛选苹果 CMS v10（MacCMS）源。本机没有自托管服务器，drpy-node 等需要部署服务的方案不做；本轮只完成可直接复用现有 `CmsAdapter` 的严格 JSON MacCMS 源接入。

本轮目标：从聚合 JSON 中筛出严格 JSON MacCMS 接口，按四端点实测结果把通过源加入三端编译期源清单，保持现有网络/解析/播放链路不变，并完成测试、真机验证、文档同步、提交推送和按现行规则发布 Release。

## 范围

### 本轮做

1. 读取 `https://raw.githubusercontent.com/Hululu007/tvbox-api-backup--/main/tvbox/海量采集聚合接口.json`。
2. 只保留 `sites[].api` 中含 `api.php/provide/vod` 的候选；排除 XML、`at/xml`、`at/m3u8`、`from/`、`spider`/`csp_*`、非 JSON 变体。
3. 对每个候选串行请求四个端点：`?ac=list`、`?ac=videolist&pg=1`、`?ac=detail&ids=1`、`?ac=detail&wd=庆余年`。只有四个端点均 HTTP 200、list/detail 为合法 JSON、list 端点返回非空 `list`、detail 包含 `vod_id` 与 `vod_play_url` 的候选才通过。
4. 四端点通过的候选按主机名去重，`enabled = true`，写入三端 `Sources.kt`；未通过的不写入仓库。
5. 在 Android 真机验证新增源至少一项：搜索结果、详情播放页和播放地址链路正常。
6. 更新需求追踪、架构说明、验证记录和真机记录；递增五处版本；按功能点提交并推送 GitHub/Gitee；满足发布门槛后发布 Release。

### 本轮不做

- `live-sources` 的 live.m3u 频道表。
- `xptv-my4k` 的 `csp_*` 协议与成人条目过滤。
- `drpy-node` 自托管服务；用户已明确没有服务器，此方案不做。
- `video` / `ziyuanzhan` 的发现输入与生成数据链路。
- 在线源市场、动态拉取聚合配置、账号、广告、埋点或推荐。

## 架构与数据流

沿用现有边界，不引入新层：

1. `data/source/SourceRepository.kt` 按 `CmsSource.api` 构造四端点请求。
2. `data/source/adapters/CmsAdapter.kt` 解析 MacCMS v10 JSON，承担 search/category/detail。
3. `core/Sources.kt` 只保存编译期常量；新增条目字段：`key = hulu_<source_key>`、`name` 为清洗控制字符后的站点名、`api` 为规范化的 JSON 接口 base URL、`protocol = SourceProtocol.Cms`、`caps = CAPS_CMS`、`direct = true`、`tier = 3`、`enabled = true`、`note` 写明 2026-11-20 四端点实测通过。
4. 三端共享源码各自副本，分别在 `DengYing`、`DengYingIos`、`DengYingOhos` 的 `shared/src/commonMain/kotlin/com/dengying/player/core/Sources.kt` 中加入相同条目；不建立根级共享源。

## 端点判定与候选写入规则

- 接口规范化：去掉 URL 中已有的 `?ac=list` 查询串；保留路径中的 `/api.php/provide/vod/`；尾部统一加 `/` 以便拼接 `?ac=...`。
- 严格 JSON：响应必须能被解析为 JSON 对象，且包含文档要求的 `class` / `list` / `vod_id` / `vod_play_url` 字段。
- 响应质量：list 端点 `list` 非空，detail by id 同时含 `vod_id` 和 `vod_play_url`；search 端点至少返回合法 JSON。
- 去重：按主机名去重，保留第一个通过的候选；同一主机名不同路径不重复启用。
- 记录：实测结果写入 `docs/04-验证记录.md`，至少包含 api、HTTP 状态、class/list 数量、play_url 和日期；不记录密钥或完整播放地址。

## 错误处理

- 端点请求失败、JSON 解析失败、字段缺失或非 JSON 变体均判为不通过，不加入清单。
- 请求串行并设置超时，避免一次性压测大量候选。
- 新增源上线后若真机播放失败，先把该源 `enabled=false` 并记录失败端点，再按功能点提交修复；不在没有证据时放宽 URL/重定向校验。

## 测试与验证

- 构建/单测：`DengYing` 跑 `:shared:jvmTest`、`:ui:jvmTest`、`:ui:compileDebugKotlinAndroid`、`:app:assembleDebug`，以及 `:app:assembleRelease`。
- 三端最小验证：`DengYingIos`、`DengYingOhos` 按各自 `runscript/full-check.sh` 或等价最小 Gradle 验证；平台专属链接/运行若只能在 macOS/DevEco 做，明确标未验证。
- 真机：Android vivo V2419A 安装 debug 包，新增源在搜索中可返回结果，进入详情能取到 `vod_play_url` 并进入播放页；保存截图与关键探针行。
- 静态检查：`rtk git diff --check`；核对未跟踪文件不进入提交；`rtk git status --short`。
- 发布：版本递增到下一版本号（当前基线 1.3.18/1000318 → 1.3.19/1000319），五处版本声明与三端 `docs/02` 抬头一致；满足根 `AGENTS.md` §5.1 后创建 tag 并发布 GitHub/Gitee Release。

## 风险与未决

- 第三方接口可用性与内容合规由源站控制；本轮只记录实测结果，不保证长期可用。
- 若四端点全部失败，则本轮停在清单/文档证据阶段，不发布新的用户可见功能版本，除非已有其他可交付功能点。
- 端点筛选是网络实测，DNS/网络环境变化会影响结果；文档记录测试日期与环境，不把一次通过写成永久保证。
- drpy/live/XPTV/发现输入明确延后，不在本轮版本中宣称支持。
