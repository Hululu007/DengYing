package com.dengying.player.player

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * [PlaybackProbe] 的 JVM / Android 共用实现。
 *
 * 逐条沿用迁移前 `playback/PlayerFactory.kt` 里的 `object PlaybackProbe`：
 * 单线程 daemon executor 异步顺序追加、`runCatching` 包裹、未注入时不产生副作用。
 *
 * 放在 `jvmSharedMain` 而不是 `androidMain` 的理由：原实现只用 `java.io.File` +
 * `java.util.concurrent`，没有任何 Android API；放共用源集让 `:shared:jvmTest`
 * 也能验证它，而 `androidMain` 会把它挡在单测之外。
 */
actual object PlaybackProbe {

    @Volatile
    private var file: File? = null

    /**
     * 探针写入器：**单线程**，保证顺序追加且不阻塞调用线程。
     *
     * 理由见 [note] 的说明——同步写在高频状态下会成为卡顿来源。
     */
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DY-ProbeWriter").apply { isDaemon = true }
    }

    actual fun init(dir: String) {
        file = File(dir, "playback.log")
    }

    actual fun note(tag: String, detail: String) {
        val f = file ?: return
        // 【必须异步写】真机实测：状态高频变化时（`BUFFERING ↔ READY` 各 992 次）
        // 每次都同步 `appendText` 会做 992 次磁盘 IO，**本身就在加剧卡顿**。
        // 改成投递到单线程 executor：写入不在调用线程（通常是主线程）执行。
        //
        // 单线程而非线程池：探针文件是顺序追加，多线程写会交错、顺序不可读。
        val task = Runnable {
            runCatching { f.appendText("[$tag] $detail\n", Charsets.UTF_8) }
        }
        runCatching { writer.execute(task) }
    }

    actual fun reset() {
        runCatching { file?.writeText("", Charsets.UTF_8) }
    }

    /**
     * 把分享页原始 HTML 落到 `files/sharepage.html`，供开发期分析地址书写形态。
     *
     * 用途：真机实测遇到"页面里明明有 m3u8，三级正则都提不到"的情形
     * （探针 `htmlLen=1593 m3u8=true`）。要写出正确的正则，必须看到原始页面。
     *
     * **只在 debug 构建调用**（调用点有 `BuildConfig.DEBUG` 判断）：
     * 页面内容属站点自有，不该进 release 产物，也不该长期留存。
     */
    actual fun dumpSharePage(url: String) {
        val dir = file?.parentFile ?: return
        runCatching {
            // 在 IO 线程抓，避免阻塞主线程
            Thread {
                runCatching {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36",
                    )
                    val html = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    File(dir, "sharepage.html").writeText(html, Charsets.UTF_8)
                    note("sharePage-dumped", "len=${html.length}")
                }.onFailure {
                    note("sharePage-dump-failed", "${it.javaClass.simpleName}: ${it.message?.take(80)}")
                }
            }.start()
        }
    }
}
