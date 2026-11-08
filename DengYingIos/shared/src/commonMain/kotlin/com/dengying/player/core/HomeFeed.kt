package com.dengying.player.core

/**
 * 首页版块（发现流）契约类型 —— `jsondata` 类「服务端编排首页」的通用形状
 *
 * 归属：纯类型，零 import（与 [VideoItem] 同层同口径）。
 *
 * 【为什么进 core】版块结构是 data 适配器产出、ui 页面消费的**契约**：
 * 放 data 层会重演「data 定义 UI 依赖形状」的穿透（见 docs/03 §1.2 对
 * `FetchResult` 归属的同类论证），放 ui 层则 data 够不着。
 *
 * 【为什么是通用形状而非 tv189 专属】tv189 的 `jsondata` 是当前唯一已接的
 * 服务端编排首页，但「tab + 版块 + 卡片」是流媒体首页的公共形状——类型不带
 * tv189 前缀，后续其它源接同类接口时直接复用。
 */
/**
 * 首页导航 tab（jsondata `tabs[]`）。
 *
 * 外链型 tab（tv189 的 `areaType=130`，如「少儿教育」跳 H5）在解析层就被过滤，
 * 不会出现在这里——本应用不做站外跳转。
 */
data class HomeTab(
    val name: String,
    /** 该 tab 的版块数据地址（tv189：jsondata path，`/clt4/...`） */
    val path: String,
)
/** 单个首页版块：一个标题 + 一排可进详情的卡片（横滑栏目） */
data class HomeSection(
    val title: String,
    val items: List<VideoItem>,
)
