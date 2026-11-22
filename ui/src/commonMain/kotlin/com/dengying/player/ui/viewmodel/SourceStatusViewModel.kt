package com.dengying.player.ui.viewmodel

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.core.CmsSources
import com.dengying.player.domain.source.SourceHealthRules
import com.dengying.player.domain.source.SourceOverrideRules
import kotlinx.coroutines.launch

/**
 * 源状态（OPS-005、FR-107、FR-601）
 *
 * 需求：**源失效要有可操作的人工发现途径**（应用内标记 + 文档记录）。
 *
 * 每源一行：状态（正常/可疑）、滚动分数、样本数、最近一次结果、一句话说明。
 * **可疑的排在前面**——人工排查先看有问题的。
 *
 * 一条重要的显示约定：可疑源必须标注「**仍会尝试**」。否则用户会以为源被禁用了，
 * 而规则明确"排末尾不删除"——它会继续参与聚合，只是排在后面。
 *
 * FR-601：每行还有启用开关，且列表含**停用源**——否则停用过的源在列表里消失，
 * 再也无法重新启用，那样"启用停用"只是单向的。
 */
class SourceStatusViewModel(
    private val repo: SourceRepository,
    private val store: Store,
) : ViewModel() {

    data class Row(
        val key: String,
        val displayName: String,
        val health: SourceHealthRules.Row,
        val enabled: Boolean,
        val changed: Boolean,
    )

    data class UiState(
        val rows: List<Row> = emptyList(),
        val fallbackAll: Boolean = false,
        val overridden: Int = 0,
        val tip: String = "",
    )

    var state by mutableStateOf(UiState())
        private set

    fun reload() {
        val healthRows = repo.allSourceHealthRows().associateBy { it.key }
        val override = store.getSourceOverride()
        val rows = CmsSources.ALL.map { src ->
            val h = healthRows[src.key] ?: SourceHealthRules.toRow(SourceHealthRules.newHealth(src.key))
            Row(
                key = src.key,
                displayName = src.name,
                health = h,
                enabled = repo.isSourceEnabled(src),
                changed = SourceOverrideRules.overrideOf(override, src.key) != null,
            )
        }
        state = state.copy(
            rows = rows,
            fallbackAll = repo.allSourcesSuspicious(),
            overridden = SourceOverrideRules.changedCount(override),
        )
    }

    /** FR-601：切换某源的启用状态并落盘 */
    fun toggle(key: String) {
        val src = CmsSources.byKey(key) ?: return
        val next = !repo.isSourceEnabled(src)
        viewModelScope.launch {
            // FR-507：未落盘时不改 UI 状态——否则界面与存储不一致，且用户以为改成了
            if (store.setSourceEnabled(key, next, src.enabled)) {
                // 缓存已更新，同步给聚合层（同步过滤的前提）
                repo.override = store.getSourceOverride()
                reload()
                state = state.copy(tip = if (next) "已启用 ${src.name}" else "已停用 ${src.name}")
            } else {
                state = state.copy(tip = "本地存储不可用，改动未保存；请重试")
            }
        }
    }

    /** FR-601：恢复默认（清空全部覆盖） */
    fun restoreDefaults() {
        viewModelScope.launch {
            if (store.clearSourceOverride()) {
                repo.override = store.getSourceOverride()
                reload()
                state = state.copy(tip = "已恢复默认源清单")
            } else {
                state = state.copy(tip = "本地存储不可用，改动未保存；请重试")
            }
        }
    }

    fun consumeTip() {
        if (state.tip.isNotEmpty()) state = state.copy(tip = "")
    }
}
