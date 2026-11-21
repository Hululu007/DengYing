package com.dengying.player.data.download

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.set
import platform.Foundation.NSFileManager
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.open
import platform.posix.write

/**
 * iOS 下载存储：写入应用沙盒的 Documents/downloads。
 *
 * 根目录由 [com.dengying.player.ui.MainViewController] 一侧注入；这里只接受相对路径。
 * 文件字节走 POSIX `open/write`，与探针实现同一套避坑方式。
 */
@OptIn(ExperimentalForeignApi::class)
actual object DownloadStorage {

    private var root: String? = null

    actual val supported: Boolean = true

    actual fun init(root: String) {
        this.root = root
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = root,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    actual fun isReady(): Boolean = !root.isNullOrEmpty()

    private fun pathOf(relPath: String): String? {
        val base = root ?: return null
        val rel = relPath.replace('\\', '/')
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) return null
        return "$base/$rel"
    }

    actual fun writeBytes(relPath: String, bytes: ByteArray): Boolean {
        val path = pathOf(relPath) ?: return false
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
            NSFileManager.defaultManager.createDirectoryAtPath(
                path = parent,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        return writeAll(path, bytes)
    }

    actual fun exists(relPath: String): Boolean {
        val path = pathOf(relPath) ?: return false
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }

    actual fun delete(relPath: String): Boolean {
        val path = pathOf(relPath) ?: return false
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return true
        return NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    actual fun deleteTree(relPath: String): Boolean = delete(relPath)

    actual fun deleteAll(): Boolean {
        val base = root ?: return false
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(base, error = null)
            ?: return true
        var ok = true
        for (name in names) {
            val text = name as? String ?: continue
            if (!NSFileManager.defaultManager.removeItemAtPath("$base/$text", error = null)) {
                ok = false
            }
        }
        return ok
    }

    actual fun fileUrl(relPath: String): String {
        val path = pathOf(relPath) ?: return ""
        return "file://$path"
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

    actual fun appendBytes(relPath: String, bytes: ByteArray): Boolean = false
    actual fun sizeOf(relPath: String): Long = -1L
    actual fun rename(from: String, to: String): Boolean = false
    actual fun listParts(relPath: String): List<String> = emptyList()
}
