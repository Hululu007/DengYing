package com.dengying.player.ui.viewmodel

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.core.CmsSources
import com.dengying.player.core.DiscoverItem
import com.dengying.player.core.VideoItem
import com.dengying.player.core.capsOf
import com.dengying.player.data.source.discover.DiscoverAdapter
import com.dengying.player.domain.page.ConcurrencyGate
import com.dengying.player.domain.page.PageBudget
import com.dengying.player.domain.search.SearchNotice
import com.dengying.player.domain.search.DiscoverRules
import com.dengying.player.domain.search.SearchSession
import kotlinx.coroutines.launch

/**
 * 搜索页 —— 多源聚合搜索 + 搜索历史（FR-201/202/203/204/205/206/207/208/209）
 *
 * 这里集中了原工程最复杂的两组并发契约：
 *  - **FR-207 会话序号**：换词/取消后，旧搜索的 `await` 回来不得覆盖新结果
 *  - **NFR-003 完成度**：预算耗尽时结果可能不完整，必须告知用户
 */
class SearchViewModel(
    private val repo: SourceRepository,
    private val store: Store,
) : ViewModel() {

    data class UiState(
        val keyword: String = "",
        val results: List<VideoItem> = emptyList(),
        val searching: Boolean = false,
        val searched: Boolean = false,
        val tip: String = "",
        val history: List<String> = emptyList(),
        val showClearConfirm: Boolean = false,
        /** 爱奇艺联想词（未搜索时的输入提示）。失败静默，见 [loadSuggest] */
        val suggest: List<DiscoverItem> = emptyList(),
    )

    var state by mutableStateOf(UiState())
        private set

    /** FR-207：会话序号。判定"这次 await 回来后是否仍是当前会话" */
    private val session = SearchSession()

    private companion object {
        /** 联想防抖窗口：低于 250ms 挡不住连击，高于 400ms 用户会觉得「没反应」 */
        const val SUGGEST_DEBOUNCE_MS = 300L
    }

    /** 联想词的独立会话序号：与主搜索互不干扰（两者可同时在飞） */
    private val suggestSession = SearchSession()

    /** 联想请求的防抖：新输入到来时取消上一次的等待（语义见 [SuggestDebouncer]） */
    private val suggestDebounce = SuggestDebouncer(viewModelScope, SUGGEST_DEBOUNCE_MS)

    init {
        reloadHistory()
    }

    fun reloadHistory() {
        viewModelScope.launch {
            state = state.copy(history = store.getSearchHistory())
        }
    }

    fun onKeywordChange(v: String) {
        state = state.copy(keyword = v)
    }

    /** FR-208：实际参与搜索的源数量（口径与聚合层筛选一致，且含覆盖层） */
    fun searchSourceCount(): Int = SearchNotice.countSearchable(
        CmsSources.ALL.map {
            SearchNotice.SearchableSource(enabled = repo.isSourceEnabled(it), capsSearch = capsOf(it).search)
        }
    )

    /**
     * 拉爱奇艺联想词（未搜索时的输入提示）。
     *
     * **失败静默**：联想是锦上添花，网络抖动时弹一条错误只会干扰输入。
     * 唯一的例外是「没有可展示条目」——那时也不提示，直接不显示该区块。
     *
     * 会话序号沿用 [SearchSession]：快速连续输入时，旧词的联想回来不得覆盖新词的。
     */
    fun loadSuggest(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            suggestDebounce.cancel()
            state = state.copy(suggest = emptyList())
            return
        }
        val token = suggestSession.begin(kw)
        // 防抖在 [SuggestDebouncer] 里做：提交前先取消上一次等待，
        // 否则每敲一个字都各睡 300ms 再各发一次请求。
        suggestDebounce.submit {
            val feed = try {
                DiscoverAdapter.iqiyiSuggest(kw)
            } catch (e: Exception) {
                null
            }
            // 过期会话（用户已改词）→ 丢弃
            if (!suggestSession.shouldAdopt(token, state.keyword.trim())) return@submit
            // 过滤规则在 domain 层（可单测），见 DiscoverRules.suggestItems
            state = state.copy(suggest = DiscoverRules.suggestItems(feed))
        }
    }

    fun doSearch() {
        val kw = state.keyword.trim()
        // FR-207：判据抽在 SearchSession.shouldSkip，且**不再因"正在搜索"而拒绝**——
        // 那是改动前的行为，会让用户换词时点搜索没反应。
        if (session.shouldSkip(kw, state.results.isNotEmpty())) {
            if (kw.isEmpty()) state = state.copy(tip = "请输入片名")
            return
        }

        val token = session.begin(kw)
        val count = searchSourceCount()
        state = state.copy(
            searching = true,
            searched = true,
            tip = SearchNotice.searchingText(count),
            // FR-207：换词时**不清空结果**——立刻清空会让用户看到列表先消失再逐条出现（闪烁）。
            // 旧结果保留到新结果到达，由序号判定保证替换来自当前会话。
        )

        // 搜索历史：副作用而非用户显式动作，失败不清空结果也不弹提示
        // （搜索本身已成功，打断它反而更差）
        viewModelScope.launch { store.addSearchHistory(kw) }
        reloadHistory()

        viewModelScope.launch {
            try {
                // 流式：每个源返回立即上屏，不等全部源
                val r = repo.searchAllBudgeted(
                    repo.allSources(), kw, com.dengying.player.platform.Clock.nowMs(),
                ) { partial ->
                    // FR-207：过期会话的部分结果也丢弃——否则旧词的结果会闪进新词的列表
                    if (session.shouldAdopt(token, kw)) {
                        state = state.copy(results = partial)
                    }
                }

                // FR-207 关键判定：await 回来后若已不是当前会话，本次结果整体丢弃。
                if (!session.shouldAdopt(token, kw)) return@launch

                state = state.copy(
                    results = r.items,
                    tip = when {
                        r.items.isEmpty() -> SearchNotice.doneText(0, kw, count)
                        // 有结果但不完整：说明只显示了先返回的部分，而不是"就这么多"
                        r.outcome == PageBudget.Outcome.Expired ->
                            PageBudget.outcomeNotice(r.outcome)
                        else -> ""
                    },
                )
            } catch (e: Exception) {
                if (!session.shouldAdopt(token, kw)) return@launch
                state = state.copy(results = emptyList(), tip = "搜索失败，请检查网络")
            } finally {
                // FR-207：只有当前会话能结束自己——旧会话迟到的收尾不得把
                // "搜索中"置回 false（那会让提示在新搜索还在跑时提前消失）。
                if (session.finish(token)) {
                    state = state.copy(searching = false)
                }
            }
        }
    }

    fun searchFromHistory(kw: String) {
        state = state.copy(keyword = kw)
        doSearch()
    }

    /**
     * FR-207：取消当前搜索。
     *
     * 两件事都要做，缺一不可：
     * 1. **推进序号**使在途结果失效
     * 2. **丢弃闸门里排队的请求**——否则它们仍会逐个执行、占着并发空位
     */
    fun cancelSearch() {
        if (!state.searching) return
        val t = session.begin(state.keyword.trim())
        session.finish(t)
        viewModelScope.launch {
            val dropped = ConcurrencyGate.outbound.cancelQueued()
            val count = searchSourceCount()
            state = state.copy(
                searching = false,
                tip = if (state.results.isNotEmpty()) "" else SearchNotice.doneText(0, state.keyword.trim(), count),
            )
            com.dengying.player.platform.Log.w("DY/Search", "FR-207 cancelled, dropped=$dropped")
        }
    }

    fun onDeleteHistory(kw: String) {
        viewModelScope.launch {
            // FR-507：未落盘则不刷新列表并给可见提示
            if (store.removeSearchHistory(kw)) {
                state = state.copy(history = store.getSearchHistory())
            } else {
                state = state.copy(tip = "本地存储不可用，改动未保存；请重试")
            }
        }
    }

    /** FR-505：先确认再写 */
    fun requestClearHistory() {
        state = state.copy(showClearConfirm = true)
    }

    fun dismissClearHistory() {
        state = state.copy(showClearConfirm = false)
    }

    fun doClearHistory() {
        state = state.copy(showClearConfirm = false)
        viewModelScope.launch {
            if (store.clearSearchHistory()) {
                state = state.copy(history = store.getSearchHistory())
            } else {
                state = state.copy(tip = "本地存储不可用，改动未保存；请重试")
            }
        }
    }

    fun consumeTip() {
        if (state.tip.isNotEmpty()) state = state.copy(tip = "")
    }
}
