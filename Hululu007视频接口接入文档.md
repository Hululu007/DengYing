# Hululu007 视频接口接入文档

结论：全仓库盘点完成。Hululu007 共 400 个仓库，195 个视频类仓库。视频接口分布在 29 个仓库，其中 TVBox 配置 15 个、直播源 13 个、XPTV 配置 1 个。已导出 141 条去重 MacCMS 端点，见 [Hululu007 MacCMS 源清单](Hululu007_MacCMS源清单.md)。接入顺序：先取 TVBox 聚合配置中的 MacCMS 源，再接直播源，XPTV 与自托管服务单列评估。

## 1. 全仓库盘点

| 类别 | 仓库数 | 仓库 | 产出 | 接入方式 | 证据级别 |
|---|---|---|---|---|---|
| TVBox / FongMi 配置 | 15 | 见 5.1 表 | JSON 配置 | 提取 `sites[].api`，生成 `CmsSource` | A：文件 HTTP 200；C：端点待实测 |
| 直播源 | 13 | 见 7.1 表 | `m3u` / `m3u8` / `txt` | 解析频道行，写入直链表 | A：文件 HTTP 200 |
| XPTV 配置 | 1 | `xptv-my4k` | `xptv.json` | 新增 XPTV 适配器 | A：文件 HTTP 200；C：适配器待实现 |
| 自托管软件 | 10 | 见 8.7 表 | 服务源码或 Docker | 部署后接 HTTP 接口 | B：README |
| 客户端 / 工具 / 爬虫 | 156 | 播放器、下载器、录制器、Java spider | 无 | 范围外 | — |

盘点口径：

- 仓库总数来自 GitHub API 用户仓库列表，共 400 个。
- 视频类仓库按名称与描述关键词筛选，共 195 个。
- 可直接消费指仓库内存在 `json` / `m3u` / `txt` 配置文件，且有 HTTP 200 证据。
- 客户端、下载器、录制器、Java spider 工程只消费接口，不产出接口。

首版覆盖 6 个仓库。当前覆盖 29 个仓库。

## 2. 接入优先级

| 优先级 | 仓库 | 产出 | 接入方式 | 理由 |
|---|---|---|---|---|
| 0 | `Hululu007/tvbox-api-backup--` | 聚合 JSON，71 站点、53 个 CMS | 提取 `sites[].api` | 单文件产出密度最高 |
| 1 | `Hululu007/cluntop.github.io` | `box.json`，101 站点、18 个 CMS | 同上 | 站点数量最多 |
| 2 | `Hululu007/iptvtw`、`Hululu007/gao` | 多份配置，合计 45 / 8 个 CMS | 同上 | 配置数量多，注释格式需容错 |
| 3 | `Hululu007/live-sources`、`Hululu007/IPTV---` | `live.m3u` | 解析直播条目 | 文件稳定，体积适中 |
| 4 | `Hululu007/xptv-my4k` | `xptv.json` | 新 XPTV 适配器 | 需新增协议分支 |
| 5 | `Hululu007/drpy-node`、`Hululu007/video` | 自托管服务 | 部署后接 HTTP | 需服务与运行环境 |
| 6 | `Hululu007/ziyuanzhan` | 候选域名 | 生成候选清单 | 只做源发现 |

## 3. 项目约束

- 源清单保持编译期常量。项目不引入在线源市场。
- 三端源模型分别维护。共享代码存在于三个平级工程，不存在根级 `shared/`。
- 网络请求走统一 `HttpClient`。适配器内不另建出网通道。
- 项目不引入账号、广告、埋点、推荐系统。
- 文档和脚本只记录令牌长度或掩码，不记录签名材料、完整用户数据。
- `CmsAdapter` 只消费苹果 CMS v10 风格端点。TVBox 配置中的 `spider` 脚本和 `csp_*` 条目进入独立适配器清单。

## 4. 已核实地址

本机 DNS 将 GitHub 域名解析到 `198.18.x.x`。获取公开文件时加 `--resolve`，让命令直连已验证地址。

| 地址 | 结果 | 观察 |
|---|---|---|
| `https://raw.githubusercontent.com/Hululu007/tvbox-api-backup--/main/list.txt` | HTTP 200 | 3493 B，含 `饭太硬.json` 行 |
| `https://raw.githubusercontent.com/Hululu007/tvbox-api-backup--/main/tvbox/%E6%B5%B7%E9%87%8F%E9%87%87%E9%9B%86%E8%81%9A%E5%90%88%E6%8E%A5%E5%8F%A3.json` | HTTP 200 | 22704 B，71 站点，53 个 CMS |
| `https://raw.githubusercontent.com/Hululu007/cluntop.github.io/main/box.json` | HTTP 200 | 31412 B，101 站点，18 个 CMS，17 个直播 |
| `https://raw.githubusercontent.com/Hululu007/live-sources/main/live.m3u` | HTTP 200 | 98127 B，含 `#EXTM3U`、`#EXTINF` |
| `https://raw.githubusercontent.com/Hululu007/live/main/tv/m3u/ipv6.m3u` | HTTP 200 | 36817 B，含 `#EXTM3U` |
| `https://raw.githubusercontent.com/Hululu007/xptv-my4k/main/xptv.json` | HTTP 200 | 21813 B，`sites[]` 使用 `csp_*` 与 `ext=*.js` |
| `https://mylazily.github.io/ziyuanzhan/data/latest.json` | HTTP 404 | 该 fork 无生成数据 |

固定解析参数：

- `api.github.com:443:140.82.112.5`
- `raw.githubusercontent.com:443:185.199.108.133`
- `codeload.github.com:443:140.82.112.9`

## 5. TVBox 聚合接入

### 5.1 配置清单

| 仓库 | 分支 | 配置路径 | 大小 | 站点数 | CMS 数 | 说明 |
|---|---|---|---|---|---|---|
| `tvbox-api-backup--` | main | `tvbox/海量采集聚合接口.json` | 22704 B | 71 | 53 | 聚合主配置，另有 `list.txt` |
| `cluntop.github.io` | main | `box.json` | 31412 B | 101 | 18 | 另有 `js/aa.json`，37 站点 |
| `gao` | master | `0827.json` | 9973 B | 33 | 6 | 仓库含多份日期配置 |
| `iptvtw` | main | `0618.json` | 23134 B | 58 个 csp | 0 | 注释格式非标准，需容错解析 |
| `CatVodSpider-1` | main | `json/index.json` | 18615 B | 15 | 44 | 仓库 13 份 JSON，1229 站点；`json/18sex.json` 与 `json/index18.json` 为成人向配置 |
| `CatVodSpider----` | main | `json/index.json` | 18615 B | 15 | 44 | 与 `CatVodSpider-1` 同内容 |
| `CatVodSpider-backup` | main | `json/config.json` | 10845 B | 36 | 12 | 另有 `txt/live.txt` |
| `CatVodTVSpider1` | main | `json/tvbox_config.json` | 5531 B | 24 | 0 | 17 个 csp |
| `CatVodSpider---` | main | `json/config.json` | 1634 B | 3 | 0 | 仓库共 10 站点 |
| `CatVodSpider-standalone` | main | `tv.json` | 1108 B | 5 | 0 | 5 个 csp |
| `ddys-tvbox` | main | `tvbox.json` | 1722 B | 1 | 0 | DDYS API |
| `fwd-rex-webhtv` | main | `vod_lite.json` | 5962 B | 15 | 0 | 仓库 181 站点 |
| `files` | master | `m.json` | 20185 B | 24 | 2 | 仓库 206 站点 |
| `4k_anime` | main | `public/4k_anime.json` | 756 B | 1 | 1 | 含 `127.0.0.1:8787` 占位条目 |
| `520pro` | master | `能力层/tvbox/master_config.example.json` | 443 B | — | — | 仅示例配置 |

### 5.2 获取配置

```bash
curl -fsSL --resolve raw.githubusercontent.com:443:185.199.108.133 \
  'https://raw.githubusercontent.com/Hululu007/tvbox-api-backup--/main/tvbox/%E6%B5%B7%E9%87%8F%E9%87%87%E9%9B%86%E8%81%9A%E5%90%88%E6%8E%A5%E5%8F%A3.json' \
  -o tvbox-aggregate.json
```

### 5.3 提取源地址

```bash
python3 - <<'PY'
import json

with open('tvbox-aggregate.json', encoding='utf-8') as f:
    data = json.load(f)

for site in data.get('sites', []):
    api = str(site.get('api', ''))
    if api.startswith('http') and '/api.php/provide/vod/' in api:
        print(site.get('name', ''), api, sep='\t')
PY
```

注意：配置含 JS 爬虫字段和 `csp_*` 条目。`CmsAdapter` 只处理 `api.php/provide/vod` 路径。其他条目进入独立适配器清单。

### 5.4 生成源定义

按以下规则写入源清单：

1. `key` 使用仓库名加源域名段，例如 `hulu_haiwaikan`。
2. `name` 使用 `sites[].name`，去除不可读控制字符。
3. `api` 填入 `sites[].api` 原样值。
4. `protocol` 设为 `SourceProtocol.Cms`。
5. `caps` 设为 `CAPS_CMS`。
6. `direct` 设为 `true`。
7. `tier` 设为 `3`。
8. `enabled` 先设为 `false`。端点实测通过后再设为 `true`。
9. `note` 写实测日期和结论。

示例结构：

```kotlin
CmsSource(
    key = "hulu_<source_key>",
    name = "<sites[].name>",
    api = "<sites[].api 原样值>",
    speed = 8,
    direct = true,
    enabled = false,
    tier = 3,
    protocol = SourceProtocol.Cms,
    caps = CAPS_CMS,
    note = "2026-11-21 Hululu007 聚合配置发现；端点待实测",
)
```

### 5.5 同步三端文件

- `DengYing/shared/src/commonMain/kotlin/com/dengying/player/core/Sources.kt`
- `DengYingIos/shared/src/commonMain/kotlin/com/dengying/player/core/Sources.kt`
- `DengYingOhos/shared/src/commonMain/kotlin/com/dengying/player/core/Sources.kt`

能力位来自：

- `DengYing/shared/src/commonMain/kotlin/com/dengying/player/core/SourceTypes.kt`
- `DengYingIos/shared/src/commonMain/kotlin/com/dengying/player/core/SourceTypes.kt`
- `DengYingOhos/shared/src/commonMain/kotlin/com/dengying/player/core/SourceTypes.kt`

适配器实现来自：

- `DengYing/shared/src/commonMain/kotlin/com/dengying/player/data/source/adapters/CmsAdapter.kt`
- `DengYingIos/shared/src/commonMain/kotlin/com/dengying/player/data/source/adapters/CmsAdapter.kt`
- `DengYingOhos/shared/src/commonMain/kotlin/com/dengying/player/data/source/adapters/CmsAdapter.kt`

## 6. 端点实测

对每个候选 `api` 执行四个请求。请求串行发出。每次请求间隔 1 秒。

```bash
API='<sites[].api 原样值>'

curl -sS -m 8 -w '\nHTTP %{http_code}\n' "${API}?ac=list"
sleep 1
curl -sS -m 8 -w '\nHTTP %{http_code}\n' "${API}?ac=videolist&pg=1"
sleep 1
curl -sS -m 8 -w '\nHTTP %{http_code}\n' "${API}?ac=detail&ids=1"
sleep 1
curl -sS -m 8 -w '\nHTTP %{http_code}\n' "${API}?ac=detail&wd=%E5%BA%86%E4%BD%99%E5%B9%B4"
```

通过条件：

- `ac=list` 返回 JSON，含 `class` 数组。
- `ac=videolist` 返回 JSON，含 `list` 数组。
- `ac=detail` 返回 JSON，含 `vod_id` 和 `vod_play_url`。
- 四个请求均返回 HTTP 200。

记录字段：

| 字段 | 说明 |
|---|---|
| `api` | 源地址 |
| `HTTP` | 四个端点状态码 |
| `class_count` | 分类数量 |
| `list_count` | 列表数量 |
| `play_url` | 播放地址形态：直链或 token |
| `date` | ISO 日期 |

## 7. 直播源接入

### 7.1 直播源清单

| 仓库 | 分支 | 文件 | 大小 | 格式 | 说明 |
|---|---|---|---|---|---|
| `live-sources` | main | `live.m3u` | 98127 B | m3u | 央视 / 卫视 / 地方台，每小时测活 |
| `live` | main | `tv/m3u/ipv6.m3u`、`tv/m3u/index.m3u` | 36817 B / 28517 B | m3u | 图标库配套直播源 |
| `live--` | main | `tv.m3u` | 29371 B | m3u | 央视频道为主 |
| `IPTV---` | main | `live.m3u` | 1225547 B | m3u | 另有 `live_lite.m3u`、`live_platforms.m3u` |
| `IPTV--1` | main | `home.m3u8` | 10515 B | m3u | 另有 `iptv.m3u8`、`m3u/`、`sources/` |
| `IPTV--` | main | `m3u/ipv6.m3u` | 35922 B | m3u | IPv4 / IPv6 源收集 |
| `IPTV-Multicast-source` | main | `anhui/anhui.txt` | 18568 B | txt | 按省份目录，共 1212 文件 |
| `iptv` | master | `TV01.m3u8` 至 `TV92.m3u8` | — | m3u | 92 个播放列表 |
| `lanjing_live` | main | `IPTV_FREE/live.m3u` | 48503 B | m3u | 长期维护直播源 |
| `m3u` | main | `Gather.m3u`、`Migu.m3u` | 24499 B | m3u | 聚合直播 |
| `m3u8` | main | `TV.txt` | 191002 B | txt | 自建服务器直播源 |
| `guovin` | master | `output/result.m3u` | 工作流生成 | m3u | HTTP 200 |
| `iptv-api` | master | `output/result.m3u` | 工作流生成 | m3u | HTTP 200 |

`4K-IPTV-M3U` 只有 `rtp/b.py` 与 `README.md`，无静态播放列表，按省份动态生成。该仓库不进入直链表。

### 7.2 接入步骤

`live.m3u` 给出频道名和播放地址。直播源不经过 `CmsAdapter`。

1. 读取 `live.m3u`。
2. 从 `#EXTINF` 行取频道名。
3. 从下一行取播放地址。
4. 保留 `.m3u8` 和 `.mp4` 地址。
5. 写入编译期直链表。
6. 用播放器打开一个频道并核对画面。

当前直链模型位于 `DengYing/shared/src/commonMain/kotlin/com/dengying/player/core/Sources.kt:47`，类型名为 `DirectSource`。三端同名文件都要核对。

## 8. 其他接口仓库

### 8.1 CatVodSpider 系列

`CatVodSpider-1`、`CatVodSpider----`、`CatVodSpider-backup`、`CatVodTVSpider1`、`CatVodSpider---`、`CatVodSpider-standalone` 是同一类工程：CatVod / TVBox 的 spider 插件。

- 构建形态：Android 工程，`applicationId com.github.catvod.demo`，`minSdk 17`，产物是 `jar/custom_spider.jar`。
- 入口组件：`app/src/main/java/com/github/catvod/debug/MainActivity.java`，只有六个调试按钮，分别触发 `homeContent`、`homeVideoContent`、`categoryContent`、`detailContent`、`playerContent`、`searchContent`。
- 业务实现：`app/src/main/java/com/github/catvod/spider/*.java`，每个站点一个类，继承 `com.github.catvod.crawler.Spider`。
- 面向对象：TVBox / FongMi 加载 jar 后按 `csp_*` 名称调用，不是独立影视 App。
- 成人向配置：`json/18sex.json`（104.6 KB）、`json/18sexN.json`（47.0 KB）、`json/index18.json`（36.9 KB）。

接入含义：这些仓库的 JSON 可以直接当 MacCMS 端点来源。jar 与 spider 类不进入 DengY，因为 `CmsAdapter` 只处理 `api.php/provide/vod`。

### 8.2 xptv-my4k

`xptv.json` 的 `sites[]` 使用 `api=csp_*` 和 `ext=*.js`。当前 `SourceProtocol` 没有 XPTV 分支。接入前需要新增协议枚举和适配器。配置内存在成人向条目，导入时按名称过滤并单独记录。

### 8.3 drpy-node

`drpy-node` 是自托管 T4 服务。接口如下：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET / POST | `/api/:module?pwd=<PASSWORD>` | 进入 `play`、`cate`、`detail`、`action`、`search`、`init`、`home` |
| GET | `/proxy/:module/*` | 代理站点请求 |
| GET | `/parse/:jx?url=<URL>` | 解析播放地址 |

响应含 `type`、`class`、`filters`、`list`。列表项含 `vod_id`、`vod_name`、`vod_pic`、`vod_remarks`。

接入条件：部署服务、准备模块脚本、实现 `DrpyAdapter`。当前阶段只记录接口，源清单保持原状。

### 8.4 video

`video` 是自托管 Go / Docker 采集服务。README 写明预置源为空。部署后填入 MacCMS v10 域名，再由导出结果走第 5 节流程。

### 8.5 miraplay-mega

`miraplay-mega` 是 CatPawOpen / NodeJS 插件，不是 HTTP 服务。README 记录 12 个真果鉴源和 18 个 MacCMS 站点，共 30 个来源。入口为 `dist/index.js` 与 `dist/index.js.md5`。该插件需要 CatPawOpen 或 MiraPlay 运行环境，DengY 不适用。

### 8.6 ziyuanzhan

`ziyuanzhan` README 指向 `https://www.ziyuanzu.com/api/v1/sources`。生成数据页 `data/latest.json` 与 `data/online.json` 返回 HTTP 404。该仓库只用于发现候选站点。

### 8.7 需部署后才有接口的软件

| 仓库 | 形态 | 能力 | 证据级别 |
|---|---|---|---|
| `drpy-node` | Node T4 服务 | 模块化点播接口、代理、解析 | B |
| `video` | Go / Docker | MacCMS 采集与导出 | B |
| `miraplay-mega` | CatPawOpen 插件 | 30 个来源聚合 | B |
| `AIStream` | Kotlin 服务 + Android | 采集站 + IPTV + 上游解析 | B |
| `EcoHub` | Go 服务 | 多源采集聚合，支持 TVBox | B |
| `boxTV` | Windows 媒体服务 | Ktor + ffmpeg + M3U8 聚合 | B |
| `FlyTV` | Windows 本地引擎 | TVBox 生态，Java | B |
| `alist-tvbox` | Java 服务 | AList 影视化 | B |
| `maccms10` | PHP CMS | 自建苹果 CMS 站点 | B |
| `ChestnutCMS` | Java CMS | 内容管理系统 | B |

## 9. MacCMS 源清单

附件：[Hululu007 MacCMS 源清单](Hululu007_MacCMS源清单.md)、[hululu007_cms_sources.json](hululu007_cms_sources.json)。

| 项目 | 数值 |
|---|---|
| 条目数 | 141 |
| 唯一主机数 | 127 |
| 原始条目 | 166 |
| 去重规则 | 按 host + path 去重，同站点保留 https |
| 证据级别 | C：静态配置解析，逐条端点未实测 |

来源分布：

| 仓库 | 条目数 |
|---|---|
| `tvbox-api-backup--` | 50 |
| `iptvtw` | 45 |
| `CatVodSpider-1` | 40 |
| `cluntop.github.io` | 2 |
| `files` | 2 |
| `gao` | 1 |
| `CatVodSpider-backup` | 1 |

使用规则：

- 清单内每条 `api` 先走第 6 节四端点实测，通过后再写入 `Sources.kt`。
- `CatVodSpider-1` 的 40 条来自 `json/18sex.json` 等配置文件，导入前按名称过滤成人向条目。
- `4k_anime` 的 `127.0.0.1:8787` 占位条目已剔除。
- 同一主机存在 http 与 https 两种写法时，清单保留 https。

## 10. 接入验收

文档交付阶段验收：

- 文件路径存在：`D:\workspace\dengY\Hululu007视频接口接入文档.md`。
- 文内引用的源码路径存在。
- 文内不含令牌、签名材料、keystore 口令。
- A 级结论附 HTTP 状态码。
- C 级结论写「待实测」，不作为服务端契约。

代码落地后验收：

1. 候选源至少一个通过第 6 节四个端点。
2. 三端 `Sources.kt` 的新增条目一致。
3. 新增适配器或模型时补测试。
4. 在 `DengYing` 工程执行 `./gradlew :shared:jvmTest :app:assembleDebug`。
5. 在 `DengYingIos` 与 `DengYingOhos` 执行各自的 `runscript/full-check.sh`。
6. 真机播放一个新增源并保存截图路径。

Windows 构建前置条件：`JAVA_HOME=C:/Program Files/Java/jdk-18.0.2.1`。iOS framework 链接、Xcode 运行、鸿蒙 HAP 装机与投屏链路需要对应平台证据后才能写「已验证」。

## 11. 待复核项

- 141 条 `api` 的四端点状态码。
- `cluntop.github.io` 的 `js/s/*.json` 逐站点配置未解析。
- `iptvtw`、`gao` 中带注释的配置需要容错解析器。
- `sites[]` 中 `csp_*` 与 `js_*` 条目的适配器方案。
- `vod_play_url` 的直链比例和 token 比例。
- `live.m3u` 中每条播放地址的可播状态。
- XPTV 适配器的数据模型和解析规则。
- drpy-node 模块脚本来源与 `pwd` 配置方式。
