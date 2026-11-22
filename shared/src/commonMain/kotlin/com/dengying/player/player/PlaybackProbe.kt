package com.dengying.player.player

/**
 * 播放链路的观测点（**写文件，不依赖日志**）。
 *
 * 【为什么不用 Log】实测 vivo Android 16 会全量过滤本应用的日志——`Log.w` 也读不到，
 * 只有系统 tag 能过。排查播放链路必须有可读的状态证据，故把关键迁移写到
 * 应用私有目录的 `files/playback.log`，用 `adb shell run-as` 读回。
 *
 * 【纪律】只写状态名与布尔/长度，不写完整地址（SEC-004 同一口径）。
 *
 * ## 为什么是 expect/actual
 *
 * 迁移前它是 `playback/PlayerFactory.kt` 里的普通 object，靠
 * `java.util.concurrent.ExecutorService` + `java.io.File` 实现异步顺序追加。
 * 两者都不在 `commonMain`，故拆成门面：`commonMain` 只留调用形状，
 * Android actual 逐行沿用原实现（单线程 executor、daemon 线程、`runCatching` 包裹），
 * iOS actual 走 `os_log`（P5）。
 *
 * 调用点（20+ 处，遍布 5 个文件）**零改动**——`PlaybackProbe.note(...)` 的形状不变。
 */
expect object PlaybackProbe {

    /**
     * 由平台壳注入落盘目录（Android = `Application.filesDir.absolutePath`）。
     *
     * 用 `String` 而不是平台文件类型：目录只是一个路径，没必要把
     * `java.io.File` / `NSURL` 漏进 `commonMain` 的签名里。
     *
     * 未注入前 [note] 是空操作——这保证单元测试里不产生文件副作用。
     */
    fun init(dir: String)

    fun note(tag: String, detail: String = "")

    fun reset()

    /**
     * 把分享页原始 HTML 落到 `files/sharepage.html`，供开发期分析地址书写形态。
     *
     * 用途：真机实测遇到「页面里明明有 m3u8，三级正则都提不到」的情形
     * （探针 `htmlLen=1593 m3u8=true`）。要写出正确的正则，必须看到原始页面。
     *
     * **只在 debug 构建调用**（调用点有 `BuildConfig.DEBUG` 判断）：
     * 页面内容属站点自有，不该进 release 产物，也不该长期留存。
     */
    fun dumpSharePage(url: String)
}
