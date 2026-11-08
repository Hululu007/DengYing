package com.dengying.player.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.set
import platform.posix.FILE
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.open
import platform.posix.rename
import platform.posix.write
import platform.posix.SEEK_END
import platform.posix.SEEK_SET

/**
 * [KeyValueStore] 的 OHOS 实现 —— 单文件「键=转义值」行式存储
 *
 * ## 为什么是自写文件而不是 @ohos.data.preferences 桥
 *
 * Android 侧用 SharedPreferences、iOS 侧用 UserDefaults，两者都是平台自带 KV。
 * 鸿蒙对应物是 ArkTS 的 @ohos.data.preferences——但它在 ArkTS 层，Kotlin 侧要用
 * 就得再走一条「挂起等 ArkTS 回调」的桥；而 Store/PageCache 对 KV 的需求只是
 * 扁平「键→字符串」，一个 POSIX 文件就装得下（本项目全部键值实测 < 200KB）。
 * 少一条桥就少一类竞态，故走文件。
 *
 * ## 语义对齐 [FileKeyValueStore]（jvmMain）
 *
 * - 读：首次访问整体读入内存 map，之后内存读。
 * - `sync = true`：同步落盘并返回是否成功（FR-507 契约）。
 * - `sync = false`：同样同步写——与 jvm 实现同理由：调用方写完立刻读，
 *   异步化会引入竞态，而写盘本身在这台设备上足够快（整文件重写 < 数 ms）。
 * - 原子替换：写 `<file>.tmp` 再 rename，半截文件不会被读到。
 *
 * 【转义】值里可能出现 JSON（含引号与大括号，但都安全），唯三必须转义的是
 * `\`、换行/回车（行式格式的分隔符）与 `=`（键值分隔符）：
 * `\\`→`\\\\`、`\n`→`\\n`、`\r`→`\r`、`=`→`\\=`。解析按首个未转义 `=` 切分。
 */
@OptIn(ExperimentalForeignApi::class)
class FileKeyValueStore(private val dir: String, private val fileName: String = "hmvideo_store.kv") : KeyValueStore {

    private val lock = PlatformLock()
    private var loaded = false
    private val props = HashMap<String, String>()

    private val path: String get() = "$dir/$fileName"

    private fun ensureLoaded() {
        if (loaded) return
        lock.withLock {
            if (loaded) return@withLock
            try {
                readAll(path)?.decodeToString()?.split('\n')?.forEach { line ->
                    if (line.isEmpty()) return@forEach
                    val sep = line.indexOf('=')
                    if (sep < 1) return@forEach
                    val k = line.substring(0, sep)
                    val v = unescape(line.substring(sep + 1))
                    if (v != null) props[k] = v
                }
            } catch (e: Exception) {
                // 读失败按空表处理——对齐 SharedPrefs「损坏文件不阻断启动」
            }
            loaded = true
        }
    }

    override fun getString(key: String, defValue: String): String {
        ensureLoaded()
        return lock.withLock { props[key] ?: defValue }
    }

    override fun allKeys(): Set<String> {
        ensureLoaded()
        return lock.withLock { props.keys.toSet() }
    }

    override fun putString(key: String, value: String, sync: Boolean): Boolean {
        ensureLoaded()
        return lock.withLock {
            props[key] = value
            flushLocked()
        }
    }

    override fun remove(key: String, sync: Boolean): Boolean {
        ensureLoaded()
        return lock.withLock {
            props.remove(key)
            flushLocked()
        }
    }

    /** 调用方必须已持锁。写 tmp 再 rename，避免半截文件被读到 */
    private fun flushLocked(): Boolean {
        val text = buildString {
            for ((k, v) in props) {
                append(k)
                append('=')
                appendEscaped(v)
                append('\n')
            }
        }
        val tmp = "$path.tmp"
        if (!writeAll(tmp, text.encodeToByteArray())) return false
        return rename(tmp, path) == 0
    }

    private fun readAll(at: String): ByteArray? = memScoped {
        val f: CPointer<FILE>? = fopen(at, "rb")
        if (f == null) return null
        try {
            fseek(f, 0, SEEK_END)
            val size = ftell(f).toInt()
            if (size <= 0) return@memScoped ByteArray(0)
            fseek(f, 0, SEEK_SET)
            val buf = allocArray<ByteVar>(size)
            val got = fread(buf, 1.convert(), size.convert(), f).toInt()
            if (got != size) ByteArray(0) else ByteArray(size) { i -> buf[i] }
        } finally {
            fclose(f)
        }
    }

    /** posix.def 未导出 fwrite，走 open/write 系统调用 */
    private fun writeAll(at: String, bytes: ByteArray): Boolean = memScoped {
        val fd = open(at, O_WRONLY or O_CREAT or O_TRUNC, 420.convert<UInt>())
        if (fd < 0) return false
        try {
            if (bytes.isEmpty()) true
            else {
                val buf = allocArray<ByteVar>(bytes.size)
                for (i in bytes.indices) buf[i] = bytes[i]
                var off = 0
                while (off < bytes.size) {
                    val n = write(fd, buf.plus(off), (bytes.size - off).convert<ULong>()).convert<Int>()
                    if (n <= 0) return@memScoped false
                    off += n
                }
                true
            }
        } finally {
            close(fd)
        }
    }

    private fun StringBuilder.appendEscaped(v: String) {
        for (c in v) {
            when (c) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '=' -> append("\\=")
                else -> append(c)
            }
        }
    }

    /** 反解析；遇到未知转义序列视为文件损坏，返回 null 让调用方跳过该行 */
    private fun unescape(raw: String): String? {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\') {
                sb.append(c); i++; continue
            }
            if (i + 1 >= raw.length) return null
            when (val n = raw[i + 1]) {
                '\\', 'n', 'r', '=' -> {
                    sb.append(if (n == 'n') '\n' else if (n == 'r') '\r' else n)
                }
                else -> return null
            }
            i += 2
        }
        return sb.toString()
    }

    /** 释放 pthread 互斥锁（进程退出前调用；不调也只是泄漏一把小锁） */
    fun dispose() {
        lock.dispose()
    }
}
