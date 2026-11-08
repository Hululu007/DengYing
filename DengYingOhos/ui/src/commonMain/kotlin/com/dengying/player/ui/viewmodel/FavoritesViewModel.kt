package com.dengying.player.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengying.player.data.store.Store
import com.dengying.player.core.FavoriteItem
import kotlinx.coroutines.launch

/**
 * 收藏页（FR-502、FR-507、FR-508、FR-509、OPS-005 入口）
 *
 * 空态的两分法：**「还没有收藏」与「数据由更高版本写入」不是一回事**。
 * 后者说明数据在、只是本版本读不懂；显示成"还没有"会让用户以为收藏丢了。
 */
class FavoritesViewModel(
    private val store: Store,
) : ViewModel() {

    data class UiState(
        val list: List<FavoriteItem> = emptyList(),
        val newerNotice: String = "",
        val tip: String = "",
    )

    var state by mutableStateOf(UiState())
        private set

    fun reload() {
        viewModelScope.launch {
            state = state.copy(
                list = store.getFavorites(),
                newerNotice = store.newerVersionNotice(),
            )
        }
    }

    fun remove(item: FavoriteItem) {
        viewModelScope.launch {
            // FR-507：未落盘则不刷新列表并给可见提示，条目留在原位
            if (store.removeFavorite(item.vod_id, item.source)) {
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
