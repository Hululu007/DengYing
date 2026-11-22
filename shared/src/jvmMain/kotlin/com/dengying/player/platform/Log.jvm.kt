package com.dengying.player.platform

/**
 * [Log] 的 JVM 实现（仅用于 `:shared:jvmTest` 与未来的桌面端）
 *
 * 用 `java.util.logging` 而非 `System.err.println`：前者自动带上时间戳与级别，
 * 且可以被测试按需重定向（`Logger.addHandler`），后者只能全局拦截 stderr。
 *
 * 不做 tag 截断——23 字符是 Android 的约束，不是日志本身的约束。
 */
actual object Log {

    private val logger: java.util.logging.Logger =
        java.util.logging.Logger.getLogger("com.dengying.player")

    actual fun w(tag: String, message: String) {
        logger.warning("[$tag] $message")
    }

    actual fun w(tag: String, message: String, error: Throwable) {
        logger.log(java.util.logging.Level.WARNING, "[$tag] $message", error)
    }
}
