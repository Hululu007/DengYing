# DengYing 首页「推荐」版块接入 AH 数据源方案（路线2：消费 AH 真实首页数据）

> 交付口径：本文只出方案，不改动 DengYing 项目文件。
> 证据分级：A = 真机/服务端实测；B = 静态证据（反编译/源码）；C = 待实测。
> 结论先行：AH 首页「推荐」是**服务端编排的整页 JSON**（Banner 轮播 + 热播推荐宫格 + 频道 rail），字段形态与 CMS `ac=detail` 一致。DengYing 已具备同名契约 `HomeTab`/`HomeSection`/`VideoItem`，接入即把 AH 编排 JSON 映射成这套契约。

---

## 0. 一句话结论

DengYing 首页契约（`HomeFeed.kt`）已经足够装下 AH 首页「推荐」全套版块；缺的不是模型，是**一个把 AH 编排 JSON 解析成 `List<HomeSection>` 的适配器**。当前唯一 `caps.home=true` 的是 tv189，AH 系 11 源 `caps=CAPS_TOKEN_SEARCH/CAPS_WEB`（无 `home` 位），所以要让 AH 首页进 DengYing，需（a）拿到 AH 首页编排 JSON 的数据源，或（b）用 AH 各站源现场编排（本地合成首页）。

---

## 1. AH 首页「推荐」真实版块拆解（A 级，真机截图）

真机截图 `AHVideo/shots/cap_ahhome/screen.png`（1216×2640，sha256 c8546b89…），推荐 tab 自上而下：

| 序号 | 版块 | 结构 | 每卡字段（AH 实显） |
|---|---|---|---|
| 0 | 顶部搜索框 | 滚动默认热词「伟大的长征」「两京十五日」+ 搜索钮 + 历史钮 | — |
| 1 | 分类 tab | 推荐 / 电视剧 / 电影 / 动漫 / 综艺（推荐绿短划线选中） | — |
| 2 | Banner 轮播 | 单张大图 + 标题 + 分类标签 + 副标题 + 右下图点（多张自动轮播） | 标题「大王饶命 第3季」、标签「动漫」、副标题「灵气复苏爆笑修炼」、封面图 |
| 3 | 热播推荐 | 2 列宫格 4 卡（可下拉续接） | 封面图 + 片名 + 副标题（见下） |
| 4 | 电视剧推荐 | 横向 rail（下一版块，截图仅露标题） | 同卡片形态 |
| 5… | 后续版块 | 电影/动漫/综艺同类 rail | 同卡片形态 |

「热播推荐」4 卡实测内容：

| 片名 | 副标题 |
|---|---|
| 多喜一家人 | 蒋欣李光洁嫂叔争拆迁款 |
| 晚秋寄情 | 暗恋成真！弟弟不装了 |
| 一马当先 | 草根逆袭商界大佬 |
| 八仙！ | 8.2分！20亿国漫之光 |

关键观察：**副标题不是 CMS 的 `vod_remarks`（那是"更新至02集"类状态），而是运营手写的宣传语**。这一点决定映射不能只靠 CMS 字段，见 §3.2。

---

## 2. AH 首页数据从哪来（两条数据通路）

### 通路 A：AH 首页编排 JSON（服务端整页下发）—— 首选，与 tv189 同型

依据（B 级，静态）：`libapp125.so` 内联常量存在 `/home`、`/rank_list`、`/api/v2/movie/recommend`、`/api/v2/tv/recommend` 等相对路径，说明 AH 首页由单个 `home` 接口按版块下发，形态等同服务端编排首页。

现状（A 级，实探）：`/home` 在已知主机 `danmu.bmsp9.xyz`、`api.ahsp.cc`、`api.ahsp.top`、`api.bmsp9.xyz` 全部返回 404；真实 API 主机名未以完整 URL 出现在 so 中（运行时拼接或加密常量），PC 侧当前取不到。**结论：通路 A 需在设备侧抓一次 `home` 响应体，才能把真实字段名钉死。**

若拿到 `home` 响应，映射见 §3.1。

### 通路 B：用 AH 各站源现场编排（本地合成首页）—— 今天就能落地的兜底

依据（A 级，实拉）：AH 系 11 站源中多个 `enabled=true` 且 `caps` 含 search+detail，能出真实影片列表与封面。示例（`ah_ap2233` 蓝光4k，`api2233.yiys06.com`）：

```
GET https://api2233.yiys06.com/api.php/provide/vod/?ac=list&pg=1
→ {code:1, pagecount:427, total:8531, list:[{vod_id,vod_name,type_id,vod_time,vod_remarks,vod_play_from,...}]}

GET https://api2233.yiys06.com/api.php/provide/vod?ac=detail&ids=8911,8888
→ list:[{vod_id,vod_name,vod_pic,vod_sub,vod_year,vod_area,vod_lang,vod_class,vod_actor,vod_blurb,vod_remarks,...}]
```

字段实测（A 级）：
- `ac=list` 只出 `vod_id,vod_name,type_id,type_name,vod_en,vod_time,vod_remarks,vod_play_from`——**无封面**。
- `ac=detail&ids=a,b,c` **批量**返回，含 `vod_pic`（封面）、`vod_sub`（常空）、`vod_blurb`（一句话简介）、`vod_remarks`（状态）、`vod_year/vod_area/vod_lang/vod_class/vod_actor`。
- 结论：宫格要封面，必须走 `ac=detail`（可批量 ids，一次拿多卡）。

---

## 3. 字段映射表（AH → DengYing 契约）

DengYing 契约（B 级，`core/HomeFeed.kt` + `core/Models.kt`）：

```kotlin
data class HomeTab(val name: String, val path: String)
data class HomeSection(val title: String, val items: List<VideoItem>)
data class VideoItem(
    val vod_id: Long, val vod_name: String, val vod_pic: String,
    val vod_remarks: String?, val vod_year: String?, val vod_area: String?,
    val vod_lang: String?, val vod_actor: String?, val vod_director: String?,
    val vod_content: String?, val vod_play_from: String?, val vod_play_url: String?,
    val type_name: String?, val vod_uri: String?, val _source: String?,
)
```

### 3.1 通路 A（编排 JSON → HomeSection）映射

| HomeSection 字段 | 来源（AH 编排 JSON 假设键，实测后钉死） | 说明 |
|---|---|---|
| `title` | 版块标题（`label.name > label > areaName`，同 tv189 优先级） | 「热播推荐」「电视剧推荐」等 |
| `items[i].vod_id` | 条目 id（数字） | Long；无数字 id 的源用 `vod_uri` 兜底 |
| `items[i].vod_name` | 条目标题 | 片名 |
| `items[i].vod_pic` | 封面 URL | 宫格图 |
| `items[i].type_name` | 分类标签（Banner 的「动漫」chip） | 可空 |
| `items[i].vod_uri` | 原站寻址串（contentId/slug） | 详情/播放用 |

**副标题（Banner 与宫格下方那行宣传语）**：编排列若无独立字段，则该字段在 `VideoItem` 无对应位，需在 `HomeSection` 旁加一个可选 `subtitle`（§5 讨论）或临时塞进 `vod_content`。

### 3.2 通路 B（CMS `ac=detail` → VideoItem）映射

| VideoItem 字段 | CMS 字段 | 实测可用性（A 级） |
|---|---|---|
| `vod_id` | `vod_id` | ✅ |
| `vod_name` | `vod_name` | ✅ |
| `vod_pic` | `vod_pic` | ✅（需 `ac=detail`） |
| `vod_remarks` | `vod_remarks` | ✅「更新至02集」 |
| `vod_year` | `vod_year` | ✅ |
| `vod_area` | `vod_area` | ✅「韩国」 |
| `vod_lang` | `vod_lang` | ✅「韩语」 |
| `vod_actor` | `vod_actor` | ✅ |
| `vod_content` | `vod_blurb` 或 `vod_content` | ✅ 简介 |
| `type_name` | `type_name` / `vod_class` | ✅ |
| `_source` | 固定写源 key（如 `ah_ap2233`） | 本地附加 |

**副标题缺口（B 级 + A 级实拉对照）**：CMS 的 `vod_remarks` 是状态（"已完结"），`vod_sub` 常空，`vod_blurb` 偏简介。AH 首页那行宣传语（"暗恋成真！弟弟不装了"）**CMS 源给不出**，属 AH 运营侧独有内容。→ 通路 B 合成的宫格**无法 1:1 还原 AH 副标题**；只能用 `vod_remarks`+`vod_year/area` 拼"2026·韩国·更新至02集"这类替代副标题。

---

## 4. 与 DengYing 现有能力的差距（B 级，读源码）

| 位置 | 现状 | 接入 AH 需补 |
|---|---|---|
| `SourceTypes.kt:47 SourceCaps` | `home: Boolean = false` 默认关闭 | 通路 A 需把目标 AH 源置 `caps.home=true` |
| `SourceRepository.kt:507 fetchHomeTabs` | 直接调 `Tv189Adapter.homeTabs`，写死 tv189 | 改成按 `source.protocol` 分派到 AH 适配器 |
| `SourceRepository.kt:527 fetchHomeSections` | 直接调 `Tv189Adapter.homeSections` | 同上，按协议分派 |
| `Tv189Parser.parseSections` | 只认 tv189 jsondata 结构 | AH 编排 JSON 结构不同，需独立 parser |
| 适配器层 | 有 Wencai/Duboku/Api2233/Juzhi/Guapi/Html 等 | 无 AH 首页适配器，需新增 `AhHomeAdapter` |
| `VideoItem` | 无 subtitle 字段 | 若要还原宣传语，需扩模型（§5） |

关键：`fetchHomeTabs`/`fetchHomeSections` 目前**写死 Tv189Adapter**（`SourceRepository.kt:518,544`），这是接入任何新首页源必须改的唯一硬分派点。

---

## 5. 落地步骤（两条路线的具体改法）

### 路线 A（首选，需先抓到 home 响应）
1. 设备侧抓一次 AH `home` 响应体：DNS 指向 PC（`tools/ah_net_capture.mjs`），关设备 Clash VPN 后触发首页刷新，落盘 JSON。
2. 按真实键名填满 §3.1 映射，新增 `AhHomeAdapter.homeTabs/homeSections`（仿 `Tv189Adapter`）。
3. `SourceRepository.fetchHomeTabs/fetchHomeSections` 由"写死 tv189"改成按 `source.protocol == AH_HOME` 分派。
4. 目标 AH 源 `caps = CAPS_...copy(home = true)`。
5. 用 `tv189_home.txt` 同款金样本做 `AhHomeGoldenTest`。

### 路线 B（兜底，今天可做，但副标题不可 1:1）
1. 新增 `AhComposeHome`：调 `ah_ap2233`/`ah_wencai` 等站源的 `ac=detail&ids=` 拿宫格卡，按 DengYing 五大 tab（推荐/电视剧/电影/动漫/综艺）各拉一页。
2. 组装成 `List<HomeSection>`（标题 = tab 名或"热播推荐"），`items` = `VideoItem`。
3. 副标题用 `vod_remarks / vod_year / vod_area` 拼接（说明：非 AH 原文）。
4. Banner 取该页 hit 最高一条（或运营手配 1 条固定）。
5. 复用现有 `HomeSection/VideoItem`，**零模型改动**，只加合成逻辑。

### 若要还原 AH 宣传语副标题（模型扩展）
- `HomeSection` 加 `val subtitle: String? = null`；或 `VideoItem` 加 `val vod_subtitle: String? = null`（本地扩展，CMS 不填）。
- 属契约变更，需同步鸿蒙版 `Models.ets`。

---

## 6. 结论与建议

- **AH 首页数据源确实在 App 里**（`/home` 常量确认存在，B 级），但主机名运行时拼装、PC 取不到，真字段名要抓一次设备响应才能钉死（A 级待补）。
- **不改模型就能接的是通路 B**：用现有 CMS 源现场编排，`HomeSection/VideoItem` 全部复用，副标题降级为"年份·地区·状态"。
- **要 1:1 还原 AH** 必须走通路 A + 抓包，并给 `VideoItem` 补副标题位。
- 唯一硬分派改动点：`SourceRepository.kt:518/544` 由写死 `Tv189Adapter` 换成按协议分派。

---

## 附：证据索引

- 首页版块结构（A）：`AHVideo/shots/cap_ahhome/screen.png`
- 首页接口常量（B）：`AHVideo/libs125/libapp125.so`（`/home`、`/rank_list`、`/api/v2/*/recommend`）
- CMS 数据形态（A）：`https://api2233.yiys06.com/api.php/provide/vod?ac=detail&ids=8911`
- DengYing 契约（B）：`shared/src/commonMain/kotlin/com/dengying/player/core/HomeFeed.kt`、`core/Models.kt`
- 首页分派现状（B）：`shared/src/commonMain/kotlin/com/dengying/player/data/source/SourceRepository.kt:507-550`
- AH 站源配置（B）：`shared/src/commonMain/kotlin/com/dengying/player/core/Sources.kt:352-476`
