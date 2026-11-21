package com.dengying.player.data.download

import java.io.File
import java.io.FileOutputStream

actual object DownloadStorage {

    private var root: String? = null

    actual val supported: Boolean = true

    actual fun init(root: String) {
        this.root = root
        File(root).mkdirs()
    }

    actual fun isReady(): Boolean = !root.isNullOrEmpty()

    private fun fileOf(relPath: String): File? {
        val base = root ?: return null
        val rel = relPath.replace('\\', '/')
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) return null
        return File(base, rel)
    }

    actual fun writeBytes(relPath: String, bytes: ByteArray): Boolean {
        val f = fileOf(relPath) ?: return false
        return try {
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun appendBytes(relPath: String, bytes: ByteArray): Boolean {
        val f = fileOf(relPath) ?: return false
        return try {
            f.parentFile?.mkdirs()
            FileOutputStream(f, true).use { it.write(bytes) }
            true
        } catch (e: Exception) {
            false
        }
    }
    actual fun sizeOf(relPath: String): Long =
        fileOf(relPath)?.takeIf { it.isFile }?.length() ?: -1L
    actual fun rename(from: String, to: String): Boolean {
        val src = fileOf(from) ?: return false
        val dst = fileOf(to) ?: return false
        return try {
            dst.parentFile?.mkdirs()
            if (dst.exists() && !dst.delete()) return false
            src.renameTo(dst)
        } catch (e: Exception) {
            false
        }
    }
    actual fun exists(relPath: String): Boolean =
        fileOf(relPath)?.isFile == true

    actual fun delete(relPath: String): Boolean {
        val f = fileOf(relPath) ?: return false
        return try {
            !f.exists() || f.delete()
        } catch (e: Exception) {
            false
        }
    }

    actual fun deleteTree(relPath: String): Boolean {
        val f = fileOf(relPath) ?: return false
        return try {
            if (!f.exists()) true else f.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    actual fun listParts(relPath: String): List<String> {
        val f = fileOf(relPath) ?: return emptyList()
        return f.listFiles { it -> it.isFile && it.name.endsWith(".part") }
            ?.map { "$relPath/${it.name}" } ?: emptyList()
    }
    actual fun deleteAll(): Boolean {
        val base = root ?: return false
        val f = File(base)
        val children = f.listFiles() ?: return true
        var ok = true
        for (child in children) {
            if (!child.deleteRecursively()) ok = false
        }
        return ok
    }

    actual fun fileUrl(relPath: String): String {
        val f = fileOf(relPath) ?: return ""
        val path = f.absolutePath.replace('\\', '/')
        return if (path.startsWith("/")) "file://$path" else "file:///$path"
    }
}

