package com.dengying.player.platform

import java.io.File
import java.util.Properties

/**
 * [KeyValueStore] 的 JVM 实现 —— 单个 Properties 文件
 *
 * ## 为什么需要它
 *
 * 设计稿 D-3 决定保留 `jvm` target，价值是让 `:shared:jvmTest` 能在不启 Android
 * 运行时的纯 JVM 上跑存储相关用例。`SharedPreferences` 在 JVM 上不存在，
 * 故需要一份等价实现；否则 `Store`/`PageCache` 迁进 commonMain 后它们的测试
 * 就只能在 Android 上跑（`:shared:testDebugUnitTest`），失去快速反馈。
 *
 * ## 为什么用 `Properties` 而不是 JSON 文件
 *
 * 这里存的是「键 → 字符串」的扁平映射，与 `SharedPreferences` 同构；
 * `Properties` 自带转义与 load/store，不必自己处理换行与特殊字符。
 * 注意：`Properties` 的键值都是字符串，而 `SharedPreferences` 允许混合类型——
 * 本项目所有键都只写字符串（含 `v1:true` 这类自编码值），故不构成差异。
 *
 * ## 语义对齐
 *
 * - `sync = true`：写临时文件 → 原子重命名，落盘后返回。等价于 `commit()`。
 * - `sync = false`：**也同步写**，但按契约只承诺「已提交」。JVM 单测里异步写
 *   会让「写完立刻读」的用例产生竞态，而这份实现只服务于测试，不值得引入线程。
 * - 读：首次访问时把文件读进内存 `Properties`，之后都是内存读（与
 *   `SharedPreferences` 的「加载一次、内存读」一致）。
 *
 * 【线程安全】所有访问都过 [PlatformLock]，避免 `Properties`（非线程安全）被并发改。
 */
class FileKeyValueStore(private val file: File) : KeyValueStore {

    private val lock = PlatformLock()
    private var loaded = false
    private val props = Properties()

    private fun ensureLoaded() {
        if (loaded) return
        lock.withLock {
            if (loaded) return@withLock
            if (file.exists()) {
                runCatching { file.inputStream().use { props.load(it) } }
            }
            loaded = true
        }
    }

    override fun getString(key: String, defValue: String): String {
        ensureLoaded()
        return lock.withLock { props.getProperty(key) ?: defValue }
    }

    override fun allKeys(): Set<String> {
        ensureLoaded()
        return lock.withLock { props.stringPropertyNames().toSet() }
    }

    override fun putString(key: String, value: String, sync: Boolean): Boolean {
        ensureLoaded()
        return lock.withLock {
            props.setProperty(key, value)
            flush()
        }
    }

    override fun remove(key: String, sync: Boolean): Boolean {
        ensureLoaded()
        return lock.withLock {
            props.remove(key)
            flush()
        }
    }

    /** 调用方必须已持锁。写临时文件再重命名，避免半截文件被读到 */
    private fun flush(): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { props.store(it, null) }
        if (!tmp.renameTo(file)) {
            // Windows 上目标存在时 renameTo 会失败，先删再重命名
            file.delete()
            tmp.renameTo(file)
        }
    }.isSuccess
}
