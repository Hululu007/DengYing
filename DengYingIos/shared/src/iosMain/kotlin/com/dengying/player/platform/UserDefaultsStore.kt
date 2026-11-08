package com.dengying.player.platform

import platform.Foundation.NSUserDefaults

/**
 * [KeyValueStore] 的 iOS 实现 —— `NSUserDefaults` 直通（KMP P5-4）
 *
 * ## 为什么是 `NSUserDefaults` 而不是文件
 *
 * Android 侧是 `SharedPreferences`（XML + 内存缓存），JVM 侧是 `Properties` 文件。
 * iOS 上语义最近的系统设施就是 `NSUserDefaults`：同样是「键 → 基础类型的扁平映射」、
 * 同样**加载一次后内存读**、同样按 App 沙盒隔离。自己用 `NSFileManager` 造一份
 * 文件存储会引入「进程被杀时的半截文件」「多线程并发写」两类问题，而这些正是
 * `SharedPreferences` / `NSUserDefaults` 已经替我们处理掉的。
 *
 * ## 语义对齐（逐条对应 [KeyValueStore] 的契约）
 *
 * | 本方法 | `NSUserDefaults` 调用 | 语义 |
 * |---|---|---|
 * | `getString` | `stringForKey(k) ?: defValue` | 内存读，不触盘 |
 * | `allKeys` | `dictionaryRepresentation().keys` | 内存读，用于启动自检 |
 * | `putString(k, v, true)` | `setObject(...)` + `synchronize()` | 同步落盘，返回是否成功 |
 * | `putString(k, v, false)` | `setObject(...)` | 异步，返回 true |
 * | `remove(k, true)` | `removeObjectForKey(...)` + `synchronize()` | 同步 |
 * | `remove(k, false)` | `removeObjectForKey(...)` | 异步 |
 *
 * 【`synchronize()` 为什么能承载 `sync = true` 的语义】
 * 它是 `NSUserDefaults` 里唯一「立即写盘并返回是否成功」的调用，与
 * `SharedPreferences.commit()` 一一对应。注意：Apple 文档说它「已不再必要」
 * （系统会在合适时机自动写），但**「自动写」没有返回值**，而 FR-507 要求
 * 写操作不得静默丢弃 —— `Store.addFavorite` 要靠这个布尔值决定是否告诉用户
 * 「已收藏」。故这里必须显式调用，代价是每个同步写多一次 fsync。
 *
 * 【`sync = false` 为什么也返回 true】与 `SharedPrefsStore` 同口径：
 * `setObject` 只保证「已写入内存并排队落盘」，返回 true 表示**已提交**，
 * 调用方不该把它当成「已落盘」。契约在 [KeyValueStore.putString] 的 Kdoc 里写明，
 * `PageCache` 是唯一用异步写的地方（派生数据，丢了可重建）。
 *
 * 【已知差异：`synchronize()` 在现代 iOS 上恒返回 true】
 * Apple 已把它降级为「提示性」调用，返回值不再反映真实落盘结果。故同步路径
 * 虽然照契约把它的布尔值传出去，但**iOS 上永远不会是 false** —— 即磁盘满时
 * Android 的 `commit()` 会返回 false、iOS 不会。这个差异记在 docs/04 §16.10
 * 的已知差异表里，不在代码里假装抹平。
 *
 * ## 线程安全
 *
 * `NSUserDefaults` 自身是线程安全的（Apple 文档明确说明），故这里不再包
 * [PlatformLock] —— 多一层锁只会增加死锁面（`FileKeyValueStore` 需要锁是因为
 * `Properties` 非线程安全，两者前提不同）。
 *
 * @param defaults 默认用 `standardUserDefaults`（App 沙盒内的主域）。
 *        测试或将来要隔离域时可传入 `NSUserDefaults(suiteName = ...)`。
 */
class UserDefaultsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : KeyValueStore {

    override fun getString(key: String, defValue: String): String =
        defaults.stringForKey(key) ?: defValue

    /**
     * 列出全部键。
     *
     * 用 `dictionaryRepresentation()` 而不是遍历某个固定键表：`Store` 的启动自检
     * 要靠它发现「文件存在但内容损坏」这类问题，键集合必须来自真实存储。
     *
     * 注意：`dictionaryRepresentation()` 会包含系统注册域（`NSRegistrationDomain`）
     * 里的键，那是 `NSUserDefaults` 的既有行为。本项目从不调用
     * `registerDefaults`，故实际不会混入额外键；若将来引入，需在此过滤。
     */
    override fun allKeys(): Set<String> =
        defaults.dictionaryRepresentation().keys
            .mapNotNull { it as? String }
            .toSet()

    override fun putString(key: String, value: String, sync: Boolean): Boolean {
        defaults.setObject(value, forKey = key)
        if (!sync) return true
        return runCatching { defaults.synchronize() }.getOrDefault(false)
    }

    override fun remove(key: String, sync: Boolean): Boolean {
        defaults.removeObjectForKey(key)
        if (!sync) return true
        return runCatching { defaults.synchronize() }.getOrDefault(false)
    }
}
