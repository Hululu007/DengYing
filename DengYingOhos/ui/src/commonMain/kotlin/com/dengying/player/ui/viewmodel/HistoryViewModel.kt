package com.dengying.player.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengying.player.data.store.Store
import com.dengying.player.core.HistoryItem
import kotlinx.coroutines.launch

/**
 * 播放历史页（FR-503/504/505/506/507/508）
 *
 * 点击条目 → **直达该集续播**（FR-506）。历史不存媒体地址（会过期），
 * 故由详情页拉一次最新线路后自动进入该集；拉不到就停在详情并提示换线路。
 */
class HistoryViewModel(
    private val store: Store,
) : ViewModel() {

    data class UiState(
        val list: List<HistoryItem> = emptyList(),
        val newerNotice: String = "",
        val tip: String = "",
        val showClearConfirm: Boolean = false,
    )

    var state by mutableStateOf(UiState())
        private set

    fun reload() {
        viewModelScope.launch {
            state = state.copy(
                list = store.getHistory(),
                newerNotice = store.newerVersionNotice(),
            )
        }
    }

    /** FR-505：先确认再写。清空逻辑移进确认回调——「确认前不写入」由结构保证。 */
    fun requestClear() {
        state = state.copy(showClearConfirm = true)
    }

    fun dismissClear() {
        state = state.copy(showClearConfirm = false)
    }

    /** FR-507：未落盘则保留原列表并给可见提示，不假装已清空 */
    fun doClear() {
        state = state.copy(showClearConfirm = false)
        viewModelScope.launch {
            if (store.clearHistory()) {
                reload()
            } else {
                state = state.copy(tip = "本地存储不可用，改动未保存；请重试")
            }
        }
    }

    fun consumeTip() {
        if (state.tip.isNotEmpty()) state = state.copy(tip = "")
    }
}
