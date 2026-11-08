package com.dengying.player.platform

import android.content.SharedPreferences

/**
 * [KeyValueStore] 的 Android 实现 —— `SharedPreferences` 直通
 *
 * 迁移前 `Store` 与 `PageCache` 直接持有 `SharedPreferences`；这里只是把四个
 * 用到的调用原样转发，语义逐条对应：
 *
 * | 本方法 | 原调用 | 语义 |
 * |---|---|---|
 * | `getString` | `prefs.getString(k, def) ?: def` | 内存读，不触盘 |
 * | `allKeys` | `prefs.all` | 内存读，用于启动自检 |
 * | `putString(k, v, true)` | `prefs.edit().putString(k, v).commit()` | 同步，返回是否落盘 |
 * | `putString(k, v, false)` | `prefs.edit().putString(k, v).apply()` | 异步，返回 true |
 * | `remove(k, true)` | `prefs.edit().remove(k).commit()` | 同步 |
 * | `remove(k, false)` | `prefs.edit().remove(k).apply()` | 异步 |
 *
 * 【为什么每次新建 `edit()`】`SharedPreferences.Editor` 是一次性的（提交后不可复用），
 * 原实现也是每个写点新建一个，这里保持一致。
 *
 * 【`apply()` 为什么返回 true】`apply()` 没有返回值，它只保证「已写入内存并排队落盘」。
 * 返回 true 表示**已提交**，调用方不该把它当成「已落盘」——契约在
 * [KeyValueStore.putString] 的 Kdoc 里写明，`PageCache` 是唯一用异步写的地方
 * （派生数据，丢了可重建）。
 */
class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {

    override fun getString(key: String, defValue: String): String =
        prefs.getString(key, defValue) ?: defValue

    override fun allKeys(): Set<String> = prefs.all.keys

    override fun putString(key: String, value: String, sync: Boolean): Boolean {
        val editor = prefs.edit().putString(key, value)
        return if (sync) editor.commit() else { editor.apply(); true }
    }

    override fun remove(key: String, sync: Boolean): Boolean {
        val editor = prefs.edit().remove(key)
        return if (sync) editor.commit() else { editor.apply(); true }
    }
}
