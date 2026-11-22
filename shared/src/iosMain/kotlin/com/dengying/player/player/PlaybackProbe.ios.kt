package com.dengying.player.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.writeToFile
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs

/**
 * [PlaybackProbe] 的 iOS 实现（KMP P5-6 落真身）。
 *
 * ## 为什么 iOS 也要有这个探针
 *
 * Android 侧的探针是为 vivo Android 16 全量过滤本应用日志而做的落盘旁路
 * （写 `playback.log`）。iOS 上 Xcode 控制台不过滤，但真机 Release 下同样
 * 拿不到控制台；跨平台排障时「两端字段同名、同一份口径」也比「一端有一端没有」
 * 有用。故这里落真实现，形状与
 * `shared/src/jvmSharedMain/kotlin/com/dengying/player/player/PlaybackProbe.jvm.kt`
 * 逐条对齐。
 *
 * ## 与 JVM 实现的差异，及为什么用 POSIX 而不是 NSFileHandle
 *
 * JVM 版用单线程 daemon executor 把 `appendText` 挪出主线程（992 次同步 append
 * 本身会加重卡顿）。iOS 这里每次 `note()` 用 `fopen(path, "a")` + `fputs` 追加，
 * 仍在调用线程完成——探针只在播放状态切换时写，每次起播几十行，不值得为它
 * 引入线程与队列。
 *
 * 之所以走 POSIX 的 `fopen`/`fputs` 而不用 `NSFileHandle`：
 *  1. `fopen(..., "a")` 的追加语义由 libc 保证，不需要「文件不存在就建、存在就
 *     seek 到末尾」这段自己写的状态机；
 *  2. iOS 上共享 `NSFileHandle` 长连接会遇到「多句柄写同一文件」与「后台挂起后
 *     句柄失效抛 `NSFileHandleOperationException`」两个已知坑，探针不该有生命周期；
 *  3. 失败一律静默（`?: return`），探针永不影响播放。
 *
 * 目录创建仍用 `NSFileManager`：它比 `mkdir` 更适合处理沙盒内的中间目录与已存在
 * 目录的幂等性。
 *
 * ## 纪律（与 commonMain 契约一致）
 *
 * 1. `init` 之前所有方法都是空操作 —— 单元测试与未装配场景不产生文件副作用。
 * 2. 只写状态名与布尔/长度，**绝不写完整 URL**（-004）。
 * 3. `dumpSharePage` 落 `sharepage.html`，与 JVM 版同名同目录，便于两端对拍。
 */
@OptIn(ExperimentalForeignApi::class)
actual object PlaybackProbe {

    private var dir: String? = null

    actual fun init(dir: String) {
        this.dir = dir
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    actual fun note(tag: String, detail: String) {
        val file = logPath() ?: return
        appendLine(file, "[$tag] $detail\n")
    }

    actual fun reset() {
        val file = logPath() ?: return
        NSFileManager.defaultManager.removeItemAtPath(file, error = null)
    }

    /**
     * 落一份分享页 HTML，供解析器离线复现。
     *
     * 直接写 `NSData` 的原始字节，不经字符串：解出来再编码回去只会多一次可能
     * 失败的解码，而这里要的正是「原样字节」。
     */
    actual fun dumpSharePage(url: String) {
        val logFile = logPath() ?: return
        val htmlFile = logFile.substringBeforeLast('/') + "/sharepage.html"
        val nsUrl = NSURL.URLWithString(url) ?: return
        val request = NSURLRequest.requestWithURL(nsUrl)
        NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, error ->
            if (data == null || error != null) {
                note("sharePage-dump-failed", "len=0")
                return@dataTaskWithRequest
            }
            data.writeToFile(htmlFile, atomically = true)
            note("sharePage-dumped", "len=${data.length}")
        }.resume()
    }

    private fun logPath(): String? = dir?.let { "$it/playback.log" }

    private fun appendLine(file: String, line: String) {
        val handle = fopen(file, "a") ?: return
        try {
            fputs(line, handle)
        } finally {
            fclose(handle)
        }
    }
}
