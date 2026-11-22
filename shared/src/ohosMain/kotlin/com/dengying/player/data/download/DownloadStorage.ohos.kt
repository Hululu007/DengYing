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
import platform.posix.O_APPEND
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.lseek
import platform.posix.SEEK_END
import platform.posix.mkdir
import platform.posix.open
import platform.posix.read
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

    private fun appendAll(path: String, bytes: ByteArray): Boolean = memScoped {
        val fd = open(path, O_WRONLY or O_CREAT or O_APPEND, 420.convert<UInt>())
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


    actual fun appendBytes(relPath: String, bytes: ByteArray): Boolean {
        val path = pathOf(relPath) ?: return false
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) ensureDir(parent)
        return appendAll(path, bytes)
    }

    actual fun sizeOf(relPath: String): Long {
        val path = pathOf(relPath) ?: return -1L
        val fd = open(path, O_RDONLY)
        if (fd < 0) return -1L
        return try {
            lseek(fd, 0, SEEK_END).convert<Long>()
        } finally {
            close(fd)
        }
    }

    actual fun rename(from: String, to: String): Boolean = memScoped {
        val src = pathOf(from) ?: return@memScoped false
        val dst = pathOf(to) ?: return@memScoped false
        val parent = dst.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) ensureDir(parent)
        if (exists(to)) remove(dst)
        // OHOS C 库无 rename 绑定时退回 拷贝+删除
        val inFd = open(src, O_RDONLY)
        if (inFd < 0) return@memScoped false
        val outFd = open(dst, O_CREAT or O_WRONLY or O_TRUNC, 420.convert<UInt>())
        if (outFd < 0) { close(inFd); return@memScoped false }
        val ok = try {
            val buf = allocArray<ByteVar>(8192)
            while (true) {
                val n = read(inFd, buf, 8192.convert<ULong>()).convert<Int>()
                if (n <= 0) break
                var off = 0
                while (off < n) {
                    val w = write(outFd, buf.plus(off), (n - off).convert<ULong>()).convert<Int>()
                    if (w <= 0) return@memScoped false
                    off += w
                }
            }
            true
        } finally {
            close(inFd)
            close(outFd)
        }
        ok && remove(src) == 0
    }

    actual fun listParts(relPath: String): List<String> {
        val dirPath = pathOf(relPath) ?: return emptyList()
        val handle = opendir(dirPath) ?: return emptyList()
        val out = mutableListOf<String>()
        try {
            while (true) {
                val entry = readdir(handle) ?: break
                val name = entry.pointed?.d_name?.toKString() ?: continue
                if (name == "." || name == ".." || !name.endsWith(".part")) continue
                out += "$relPath/$name"
            }
        } finally {
            closedir(handle)
        }
        return out
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
}

