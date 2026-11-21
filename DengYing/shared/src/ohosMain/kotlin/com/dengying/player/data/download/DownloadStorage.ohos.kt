package com.dengying.player.data.download

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.mkdir
import platform.posix.open
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.closedir
import platform.posix.remove
import platform.posix.write

/**
 * 鸿蒙下载存储：ArkTS 壳把 `filesDir` 传给 `dengy_app_init`，这里拼出
 * `<filesDir>/downloads`。文件能力使用 POSIX，与 `FileKeyValueStore` 同一条通路。
 *
 * 真机写入与播放尚未验证；该实现不改变现有播放/网络链路。
 */
@OptIn(ExperimentalForeignApi::class)
actual object DownloadStorage {

    private var root: String? = null

    actual val supported: Boolean = true

    actual fun init(root: String) {
        this.root = root
        ensureDir(root)
    }

    actual fun isReady(): Boolean = !root.isNullOrEmpty()

    private fun pathOf(relPath: String): String? {
        val base = root ?: return null
        val rel = relPath.replace('\\', '/')
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) return null
        return "$base/$rel"
    }

    private fun ensureDir(path: String) {
        if (path.isEmpty() || path == "/") return
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) ensureDir(parent)
        mkdir(path, 495.convert<UInt>())
    }

    private fun writeAll(path: String, bytes: ByteArray): Boolean = memScoped {
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 420.convert<UInt>())
        if (fd < 0) return false
        try {
            if (bytes.isEmpty()) return true
            val buf = allocArray<ByteVar>(bytes.size)
            for (i in bytes.indices) buf[i] = bytes[i]
            var off = 0
            while (off < bytes.size) {
                val n = write(fd, buf.plus(off), (bytes.size - off).convert<ULong>()).convert<Int>()
                if (n <= 0) return@memScoped false
                off += n
            }
            true
        } finally {
            close(fd)
        }
    }

    actual fun writeBytes(relPath: String, bytes: ByteArray): Boolean {
        val path = pathOf(relPath) ?: return false
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) ensureDir(parent)
        return writeAll(path, bytes)
    }

    actual fun exists(relPath: String): Boolean {
        val path = pathOf(relPath) ?: return false
        return open(path, 0) >= 0
    }

    actual fun delete(relPath: String): Boolean {
        val path = pathOf(relPath) ?: return false
        return remove(path) == 0
    }

    actual fun deleteTree(relPath: String): Boolean {
        val path = pathOf(relPath) ?: return false
        val dir = opendir(path)
        if (dir == null) return delete(relPath)
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed?.d_name?.toKString() ?: continue
                if (name == "." || name == "..") continue
                val child = "$path/$name"
                if (!deleteTree(child.substringAfter("$root/", ""))) {
                    // 目录删不掉时按文件再试一次
                    remove(child)
                }
            }
        } finally {
            closedir(dir)
        }
        return remove(path) == 0
    }

    actual fun deleteAll(): Boolean {
        val dir = root ?: return false
        val handle = opendir(dir) ?: return true
        var ok = true
        try {
            while (true) {
                val entry = readdir(handle) ?: break
                val name = entry.pointed?.d_name?.toKString() ?: continue
                if (name == "." || name == "..") continue
                if (!deleteTree(name)) ok = false
            }
        } finally {
            closedir(handle)
        }
        return ok
    }

    actual fun fileUrl(relPath: String): String {
        val path = pathOf(relPath) ?: return ""
        return "file://$path"
    }

    actual fun appendBytes(relPath: String, bytes: ByteArray): Boolean = false
    actual fun sizeOf(relPath: String): Long = -1L
    actual fun rename(from: String, to: String): Boolean = false
    actual fun listParts(relPath: String): List<String> = emptyList()
}
