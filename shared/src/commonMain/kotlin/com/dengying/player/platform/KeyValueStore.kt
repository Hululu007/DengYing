package com.dengying.player.platform

/**
 * 轻量键值存储门面（KMP P3 批 E）
 *
 * ## 为什么是接口而不是 expect/actual
 *
 * `SharedPreferences` 是**构造参数**，不是全局服务：`Store` 与 `PageCache` 都要
 * 拿着同一个实例。用 `expect class` 就得让 expect 声明里出现平台类型
 * （`SharedPreferences`），或者被迫改成工厂函数——两者都把平台细节漏进 commonMain。
 *
 * 改成「commonMain 定接口 + 各平台实现接口」后，commonMain 只认识这四个方法，
 * `androidMain` 的 `SharedPrefsStore` 与 `jvmMain` 的 `FileKeyValueStore` 各自实现，
 * 构造点（`DengYingApp` / 测试）决定用哪个。这也顺带保住了 `:shared:jvmTest`
 * 能跑存储相关用例的能力（设计稿 D-3）。
 *
 * ## 为什么只有四个方法
 *
 * 只收 `data/store/Store.kt` 与 `data/cache/PageCache.kt` 实际用到的操作：
 * 读字符串、列出全部键（启动自检）、写字符串（区分同步/异步）、删键。
 * 多一个方法就多一份两个实现都要对齐的语义，没有调用者的方法不该存在。
 *
 * ## 同步与异步的语义差别是**契约**，不是优化
 *
 * `sync = true` 对应 `SharedPreferences.commit()`：**同步返回是否已落盘**，
 * 这是 FR-507「写操作不得静默丢弃」的落点，`Store` 的每个写函数都靠它返回布尔值。
 * `sync = false` 对应 `apply()`：异步写、不阻塞调用线程，只用于派生数据
 * （`PageCache` 在主线程调用，用 commit 会引入可见卡顿）。
 *
 * 实现**不得**把 `sync = true` 退化成异步写：那会让 `Store.addFavorite` 在
 * 落盘失败时返回 `true`，用户看到「已收藏」而重启后消失。
 */
interface KeyValueStore {

    /** 读字符串；键不存在时返回 [defValue] */
    fun getString(key: String, defValue: String): String

    /** 列出当前全部键（用于启动自检：`SharedPreferences` 不触盘，文件损坏要到读时才发现） */
    fun allKeys(): Set<String>

    /**
     * 写字符串。
     *
     * @param sync `true` = 同步写并返回是否落盘（`commit()` 语义）；
     *             `false` = 异步写、返回值仅表示「已提交给写入队列」（`apply()` 语义）
     * @return 是否已落盘（`sync = true`）或是否已提交（`sync = false`）
     */
    fun putString(key: String, value: String, sync: Boolean): Boolean

    /** 删键，[sync] 语义同 [putString] */
    fun remove(key: String, sync: Boolean): Boolean
}
