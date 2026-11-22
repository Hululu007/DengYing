package com.dengying.player.platform

/**
 * [KeyValueStore] 的内存实现 —— 文件 kv 构造失败时的兜底。
 *
 * ## 为什么需要它
 *
 * `OhosApp.init` 里 `FileKeyValueStore(dataDir)` 一旦构造抛错（如锁分配失败），
 * 原实现会把 `lateinit var kv` 留在未初始化状态；随后 `installOhosUiDeps()` 里的
 * `AhAuth.init(OhosApp.kv)` / `ThemePrefs.init(OhosApp.kv)` 直接
 * `UninitializedPropertyAccessException`，启动期崩溃。
 *
 * 退化为内存实现后：账号签名令牌、主题偏好、收藏等写入**本次进程内仍可读写**，
 * 只是重启即丢。这比「整个 UI 起不来」好，而且日志会明确标出降级。
 *
 * ## 语义
 *
 * 遵守 [KeyValueStore] 的契约，但**不能假装已落盘**：
 * - `sync = true`：内存写入成功，但数据只在本次进程内，重启即丢。契约要求
 *   `sync = true` 的返回值表示「是否已落盘」，故这里返回 `false`——写入确实
 *   生效于内存，却无法承诺持久化，谎报 `true` 会让 `Store.addFavorite` /
 *   `removeFavorite` 静默丢失改动（FR-507）。
 * - `sync = false`：契约只要求表示「已提交给写入队列」，内存写是即时的，
 *   返回 `true`。
 *
 * 降级本身已在 `OhosApp.init` 记录日志；`sync = true` 的 `false` 返回值是
 * 调用方唯一能观测到的信号。
 */
class MemoryKeyValueStore : KeyValueStore {

    private val lock = PlatformLock()
    private val props = HashMap<String, String>()

    override fun getString(key: String, defValue: String): String =
        lock.withLock { props[key] ?: defValue }

    override fun allKeys(): Set<String> = lock.withLock { props.keys.toSet() }

    override fun putString(key: String, value: String, sync: Boolean): Boolean =
        lock.withLock {
            props[key] = value
            // sync=true 返回值语义是「是否已落盘」：内存写成功也无法持久化，必须回 false。
            !sync
        }

    override fun remove(key: String, sync: Boolean): Boolean =
        lock.withLock {
            props.remove(key)
            !sync
        }
}
