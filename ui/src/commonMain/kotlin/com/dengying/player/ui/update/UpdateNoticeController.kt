package com.dengying.player.ui.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dengying.player.platform.Clock
import com.dengying.player.ui.UiDeps
import kotlinx.coroutines.CancellationException

/**
 * 更新提示的进程级状态。
 *
 * 不放在 ViewModel 里：自动检查发生在导航根，弹窗要覆盖任意页面；手动检查
 * 发生在「我的」页。两者必须共享同一份 checking/已忽略/结果状态，否则要么
 * 重复请求，要么弹窗与卡片各说各话。
 */
object UpdateNoticeController {

    var state: UpdateCheckState by mutableStateOf(UpdateCheckState.Idle)
        private set

    var dialogInfo: UpdateInfo? by mutableStateOf(null)
        private set

    var ignoredTag: String by mutableStateOf("")
        private set

    private var inFlight = false

    /**
     * 进程级「冷启动自动检查」闸门：进程内只放行一次。
     *
     * NavHost 会在转屏/重建/返回前台时重组，`LaunchedEffect` 因此可能重跑；
     * 没有闸门就会反复请求并反复弹窗。闸门是进程级（object 字段），
     * 与组合生命周期无关——这正是「仅冷启动」的语义。
     */
    internal val coldStartGate = ColdStartGate()

    /** 让页面重新读取持久化的忽略状态（进入「我的」页时调用）。 */
    fun refresh(deps: UiDeps) {
        ignoredTag = UpdatePrefs(deps.keyValueStore).ignoredTag()
    }

    /** 冷启动入口：本次进程只查一次；手动检查 [manualCheck] 不受此闸门限制。 */
    suspend fun autoCheckOnColdStart(deps: UiDeps) {
        if (!coldStartGate.tryConsume()) return
        autoCheck(deps)
    }

    suspend fun autoCheck(deps: UiDeps) {
        val prefs = UpdatePrefs(deps.keyValueStore)
        if (!prefs.shouldAutoCheck(Clock.nowMs())) return
        check(deps, manual = false)
    }

    suspend fun manualCheck(deps: UiDeps) {
        check(deps, manual = true)
    }

    private suspend fun check(deps: UiDeps, manual: Boolean) {
        if (inFlight) return
        val prefs = UpdatePrefs(deps.keyValueStore)
        if (UpdateChecker.parseCheckableVersion(deps.appVersionName) == null) {
            state = UpdateCheckState.Disabled
            return
        }

        inFlight = true
        state = UpdateCheckState.Checking
        try {
            when (val result = UpdateChecker.fetchLatest(deps.appVersionName)) {
                is UpdateCheckResult.Available -> {
                    prefs.markChecked(Clock.nowMs())
                    ignoredTag = prefs.ignoredTag()
                    state = UpdateCheckState.Available(result.info)
                    if (manual || !prefs.isIgnored(result.info.tag)) {
                        dialogInfo = result.info
                    }
                }

                UpdateCheckResult.UpToDate -> {
                    prefs.markChecked(Clock.nowMs())
                    state = UpdateCheckState.UpToDate
                }

                UpdateCheckResult.Failed -> {
                    prefs.markChecked(Clock.nowMs())
                    state = UpdateCheckState.Failed
                }

                UpdateCheckResult.Disabled -> state = UpdateCheckState.Disabled
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            prefs.markChecked(Clock.nowMs())
            state = UpdateCheckState.Failed
        } finally {
            inFlight = false
        }
    }

    fun dismissDialog() {
        dialogInfo = null
    }

    fun setIgnored(deps: UiDeps, info: UpdateInfo, ignored: Boolean) {
        val prefs = UpdatePrefs(deps.keyValueStore)
        prefs.setIgnored(info.tag, ignored)
        ignoredTag = prefs.ignoredTag()
    }

    fun isIgnored(info: UpdateInfo): Boolean =
        ignoredTag.isNotEmpty() && ignoredTag == info.tag

    /** 「我的」页「检查更新」卡片的状态文案。 */
    fun statusText(deps: UiDeps): String = when (val current = state) {
        UpdateCheckState.Idle -> UpdatePrefs(deps.keyValueStore).statusText(Clock.nowMs())
        UpdateCheckState.Checking -> "正在检查…"
        UpdateCheckState.UpToDate -> "已是最新版本"
        UpdateCheckState.Failed -> "检查失败，点此重试"
        UpdateCheckState.Disabled -> "当前平台不提供版本信息"
        is UpdateCheckState.Available -> "发现新版本 ${current.info.version}"
    }
}

/** 冷启动自动检查的一次性闸门（纯逻辑，单独可测） */
internal class ColdStartGate {
    private var consumed = false

    /** 首次返回 true 并记住；之后一律 false */
    fun tryConsume(): Boolean {
        if (consumed) return false
        consumed = true
        return true
    }
}
