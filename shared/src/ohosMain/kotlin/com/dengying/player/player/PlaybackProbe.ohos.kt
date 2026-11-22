package com.dengying.player.player

/**
 * [PlaybackProbe] 的 OHOS 实现 —— **Unit 桩**，`TODO(OHOS-device)`
 *
 * 【为什么允许打桩】与 iOS 先例一致：这个门面的唯一用途是**真机排障时导出日志**，
 * 不参与任何业务逻辑（写入失败不影响播放、不影响解析）。它的价值只在
 * 「vivo Android 16 全量过滤本应用日志」这种场景下才体现，而 OHOS 的日志
 * 默认可通过 `hdc hilog` 直接抓取，被系统静默过滤的风险低得多。
 *
 * 【为什么不做成 HiLog 落文件】OHOS 应用沙箱内写文件需要额外的
 * `@ohos.file.fs` 权限与目录解析，且排障日志文件本身也要能被导出——
 * 在**没有真机可以验证**「文件确实写出来了、确实能被取走」的前提下，
 * 写它属于「看起来实现了但没人验证过」的代码，比不写更糟。
 *
 * `TODO(OHOS-device)`：拿到鸿蒙真机/模拟器后，按 Android actual 的口径实现：
 * 用 `@ohos.file.fs` 写 `<filesDir>/playback.log`，单线程 executor 追加。
 */
actual object PlaybackProbe {

    actual fun init(dir: String) {
        // TODO(OHOS-device): 见 Kdoc
    }

    actual fun note(tag: String, detail: String) {
        // TODO(OHOS-device): 见 Kdoc
    }

    actual fun reset() {
        // TODO(OHOS-device): 见 Kdoc
    }

    actual fun dumpSharePage(url: String) {
        // TODO(OHOS-device): 见 Kdoc
    }
}
