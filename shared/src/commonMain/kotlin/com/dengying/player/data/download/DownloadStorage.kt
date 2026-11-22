package com.dengying.player.data.download

/**
 * 离线下载文件存储契约。
 *
 * 只接收**相对路径**，由平台实现负责拼到应用沙盒目录下；任何 `..` 或绝对路径都
 * 必须被拒绝，避免任务元数据里出现越界写入。
 */
expect object DownloadStorage {

    /** 当前平台是否实现了可写沙盒目录 */
    val supported: Boolean

    /** 注入下载根目录（Android/JVM = filesDir，iOS/OHOS = 应用沙盒） */
    fun init(root: String)

    fun isReady(): Boolean

    /** 写入字节；父目录不存在时自动创建 */
    fun writeBytes(relPath: String, bytes: ByteArray): Boolean
    /** 追加写入一块字节；不支持返回 false，上层回退整段写入 */
    fun appendBytes(relPath: String, bytes: ByteArray): Boolean

    fun exists(relPath: String): Boolean

    /** 已下载字节数；不存在返回 -1 */
    fun sizeOf(relPath: String): Long
    /** 原子改名（用于 .part 落盘）；失败返回 false */
    fun rename(from: String, to: String): Boolean
    /** 删除单个文件，失败返回 false */
    fun delete(relPath: String): Boolean

    /** 删除任务目录（递归），失败返回 false */
    fun deleteTree(relPath: String): Boolean
    /** 列出任务目录下以 .part 结尾的文件名（启动清理用）；默认空列表，平台可覆盖 */
    fun listParts(relPath: String): List<String>

    /** 清空根目录下的全部下载内容 */
    fun deleteAll(): Boolean

    /** 本地文件的可播放 URL（Android/iOS/OHOS 均为 file:// 形式） */
    fun fileUrl(relPath: String): String
}

/** 文本写入的通用实现，平台只需提供字节写入 */
fun DownloadStorage.writeText(relPath: String, text: String): Boolean =
    writeBytes(relPath, text.encodeToByteArray())

/** 生成安全的相对目录名（任务 id 只含小写字母、数字、下划线） */
internal fun safeDownloadDir(id: String): String =
    id.lowercase().map { c ->
        if (c in 'a'..'z' || c in '0'..'9' || c == '_') c else '_'
    }.joinToString("")

