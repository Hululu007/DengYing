package com.dengying.player.ui.update

import com.dengying.player.platform.KeyValueStore

/**
 * 更新提示的本地状态：上次检查时间与已忽略的 release tag。
 *
 * 与 Store 共用同一个 KV，但只写两个独立键，不触碰用户资产。KV 为 null 时
 * 退化成进程内状态：本次运行仍能提示，重启后忘记「已忽略」，不会崩溃。
 */
class UpdatePrefs(private val kv: KeyValueStore?) {

    fun lastCheckMs(): Long {
        val raw = kv?.getString(KEY_LAST_CHECK_MS, "") ?: ""
        return raw.toLongOrNull() ?: 0L
    }

    fun ignoredTag(): String = kv?.getString(KEY_IGNORED_TAG, "") ?: ""

    /**
     * 自动检查是否到期。
     *
     * 用户把系统时间调早时 `nowMs < lastCheck`，此时不自动检查，避免频繁请求。
     */
    fun shouldAutoCheck(nowMs: Long): Boolean {
        val last = lastCheckMs()
        if (last <= 0L) return true
        if (nowMs < last) return false
        return nowMs - last >= CHECK_INTERVAL_MS
    }

    fun markChecked(nowMs: Long) {
        kv?.putString(KEY_LAST_CHECK_MS, nowMs.toString(), sync = false)
    }

    fun markLater(nowMs: Long) = markChecked(nowMs)

    fun isIgnored(tag: String): Boolean = tag.isNotEmpty() && ignoredTag() == tag

    fun ignore(tag: String) {
        if (tag.isNotEmpty()) kv?.putString(KEY_IGNORED_TAG, tag, sync = false)
    }

    fun clearIgnored() {
        kv?.remove(KEY_IGNORED_TAG, sync = false)
    }

    fun setIgnored(tag: String, ignored: Boolean) {
        if (ignored) ignore(tag) else clearIgnored()
    }

    /** 「我的」页副标题：上次检查的相对时间。 */
    fun statusText(nowMs: Long): String {
        val last = lastCheckMs()
        if (last <= 0L) return "检查 Gitee / GitHub 上的最新版本"
        val elapsed = (nowMs - last).coerceAtLeast(0L)
        return when {
            elapsed < MINUTE_MS -> "刚刚检查"
            elapsed < HOUR_MS -> "${elapsed / MINUTE_MS} 分钟前检查"
            elapsed < DAY_MS -> "${elapsed / HOUR_MS} 小时前检查"
            else -> "${elapsed / DAY_MS} 天前检查"
        }
    }

    companion object {
        const val KEY_LAST_CHECK_MS = "ui_update_last_check_ms"
        const val KEY_IGNORED_TAG = "ui_update_ignored_tag"

        const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
        private const val MINUTE_MS = 60L * 1000L
        private const val HOUR_MS = 60L * MINUTE_MS
        private const val DAY_MS = 24L * HOUR_MS
    }
}
