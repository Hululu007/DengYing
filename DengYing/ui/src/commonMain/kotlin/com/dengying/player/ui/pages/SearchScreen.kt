package com.dengying.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.core.CmsSources
import com.dengying.player.core.DiscoverItem
import com.dengying.player.core.VideoItem
import com.dengying.player.core.capsOf
import com.dengying.player.domain.search.SearchNotice
import com.dengying.player.domain.ui.ConfirmSpec
import com.dengying.player.ui.components.ConfirmDialog
import com.dengying.player.ui.components.DyIcon
import com.dengying.player.ui.components.DyIcons
import com.dengying.player.ui.components.Poster
import com.dengying.player.ui.navigation.Routes
import com.dengying.player.ui.theme.Palette
import com.dengying.player.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    /** 从首页榜单点进来时预填的关键词（空串 = 普通进页） */
    initialKeyword: String = "",
    vm: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        val deps = UiDepsHolder.get()
        SearchViewModel(repo = deps.sources, store = deps.store)
    },
) {
    val s = vm.state
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }

    // 进页面就聚焦输入框，手机键盘随即弹出----用户是来打字的，
    // 不该再点一下才出键盘。只在首次进入时请求。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 预填关键词并直接搜（`LaunchedEffect(initialKeyword)` 保证只触发一次）
    androidx.compose.runtime.LaunchedEffect(initialKeyword) {
        if (initialKeyword.isNotBlank() && s.keyword.isBlank()) {
            vm.searchFromHistory(initialKeyword)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.AppBg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ---- 搜索栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DyIcon(
                icon = DyIcons.ArrowBack,
                tint = Palette.TextPrimary,
                contentDescription = "返回",
                size = 26.dp,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(start = 4.dp, end = 10.dp),
            )

            OutlinedTextField(
                value = s.keyword,
                onValueChange = {
                    vm.onKeywordChange(it)
                    vm.loadSuggest(it)
                },
                placeholder = {
                    Text("输入片名搜索", style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary)
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.doSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Palette.SearchBg,
                    unfocusedContainerColor = Palette.SearchBg,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .focusRequester(focusRequester),
            )

            // 搜索中显示「取消」（FR-207 的可取消入口），否则显示「搜索」
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(
                        if (s.searching) Palette.AppBg else Palette.SearchBg,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { if (s.searching) vm.cancelSearch() else vm.doSearch() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (s.searching) "取消" else "搜索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.searching) Palette.TextSecondary else Palette.Brand,
                )
            }
        }

        // ---- 联想词（正在输入、尚未搜索时）----
        // 放在历史之前：用户正在打字时，联想比历史更贴近当下意图
        if (!s.searching && !s.searched && s.suggest.isNotEmpty()) {
            SuggestBlock(
                items = s.suggest,
                onClick = { kw ->
                    vm.onKeywordChange(kw)
                    vm.doSearch()
                },
            )
        }

        // ---- 搜索历史（未搜索时显示）----
        if (!s.searching && !s.searched && s.history.isNotEmpty()) {
            HistoryBlock(
                history = s.history,
                onClick = { vm.searchFromHistory(it) },
                onDelete = { vm.onDeleteHistory(it) },
                onClear = { vm.requestClearHistory() },
            )
        }

        // ---- 状态提示 ----
        if (s.tip.isNotEmpty() && !s.searching) {
            Text(
                text = s.tip,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            )
        }

        when {
            // 加载中（还没有任何结果时才显示整页 spinner）
            s.searching && s.results.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp), color = Palette.Brand, strokeWidth = 3.dp,
                )
                Text(
                    text = s.tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            s.results.isNotEmpty() -> {
                // FR-208：流式文案同样由启用集合计算
                Text(
                    text = if (s.searching) {
                        SearchNotice.partialText(s.results.size, vm.searchSourceCount())
                    } else {
                        "共 ${s.results.size} 个结果"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(s.results, key = { "${it._source}_${it.vod_id}_${it.vod_name}" }) { item ->
                        ResultRow(item) {
                            onOpenDetail(
                                Routes.detail(
                                    vodId = item.vod_id,
                                    source = item._source ?: "",
                                    name = item.vod_name,
                                    pic = item.vod_pic,
                                    vodUri = item.vod_uri ?: "",
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (s.showClearConfirm) {
        ConfirmDialog(
            spec = ConfirmSpec.searchHistoryClearSpec(),
            onConfirm = { vm.doClearHistory() },
            onDismiss = { vm.dismissClearHistory() },
        )
    }
}

/**
 * 爱奇艺联想词区块。
 *
 * 只列标题与评分：联想词条目的 `link` 指向站外，本应用无法播放，故**点了是把它
 * 当关键词去搜**，不是跳到站外。副标题用 `subtitle`（豆瓣评分）
 * 或 `year`，都为空时不占位。
 */
@Composable
private fun SuggestBlock(
    items: List<DiscoverItem>,
    onClick: (String) -> Unit,
) {
    Column {
        Text(
            text = "猜你想搜",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp),
        )
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (it in items) {
                Row(
                    modifier = Modifier
                        .background(Palette.CardBg, RoundedCornerShape(16.dp))
                        .clickable { onClick(it.title) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = it.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    val meta = it.subtitle.ifEmpty { it.year }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Brand,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryBlock(
    history: List<String>,
    onClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "搜索历史",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            // 原为「清空」纯文字：此处没用红色，图标是唯一的「这是删除」信号
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                DyIcon(
                    icon = DyIcons.ClearAll,
                    tint = Palette.TextSecondary,
                    size = 14.dp,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                )
            }
        }

        // 流式换行 chips
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (kw in history) {
                Row(
                    modifier = Modifier
                        .background(Palette.CardBg, RoundedCornerShape(16.dp))
                        .clickable { onClick(kw) }
                        .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = kw,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(120.dp),
                    )
                    // 【原为 "×" 字形】字号只有 bodySmall 时十字很细，热区也小。
                    // 改矢量图标 + 显式 size，热区由 padding 补足。
                    DyIcon(
                        icon = DyIcons.Close,
                        tint = Palette.TextSecondary,
                        size = 14.dp,
                        contentDescription = "删除",
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clickable { onDelete(kw) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(item: VideoItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.CardBg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Poster(
            url = item.vod_pic,
            modifier = Modifier
                .width(80.dp)
                .height(110.dp),
            cornerRadius = 6,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .height(110.dp)
                .padding(start = 10.dp),
        ) {
            Text(
                text = item.vod_name,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val remarks = item.vod_remarks
            if (!remarks.isNullOrEmpty()) {
                Text(
                    text = remarks,
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Brand,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val actor = item.vod_actor
            if (!actor.isNullOrEmpty()) {
                Text(
                    text = "主演：$actor",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val type = item.type_name
                if (!type.isNullOrEmpty()) {
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextSecondary,
                    )
                }
                Text(
                    text = sourceLabel(item._source),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Brand,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** FR-205：结果行标注来源，并区分「可直连」与「需解析」/「仅搜索」 */
private fun sourceLabel(key: String?): String {
    val s = CmsSources.byKey(key) ?: return ""
    // 能力位先于直链标记：search-only 源点进去必然拿不到详情，必须提前告知
    if (!capsOf(s).detail) return "${s.name}·仅搜索"
    return if (s.direct) "${s.name}·直链" else "${s.name}·需解析"
}
