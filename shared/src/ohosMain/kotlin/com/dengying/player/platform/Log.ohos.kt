package com.dengying.player.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.PerformanceAnalysisKit.HiLog.LOG_APP
import platform.PerformanceAnalysisKit.HiLog.LOG_WARN
import platform.PerformanceAnalysisKit.HiLog.OH_LOG_Print

/**
 * [Log] 的 OHOS 实现
 *
 * 【选型：HiLog（cinterop 真实现）】
 *
 * - `OH_LOG_Print` 的**格式串必须是字面量**：HiLog 在编译期校验格式串与实参，
 *   运行时拼出的字符串（例如 `"$tag: $message"`）既不安全也会被隐私检查拦截。
 *   故这里用两个固定格式串 `%{public}s`，把已拼好的文本作为实参传入。
 * - **domain 取 `0xD001`**：这是应用自定义 domain 区间内的取值（OHOS 规定
 *   应用日志 domain 为 0x0000–0xFFFF，第三方应用建议 0xD000–0xDFFF）。
 * - **tag 不做截断**：Android actual 截断到 23 字符是因为 `android.util.Log`
 *   有硬限制；HiLog 无此限制，故保留完整 tag。这是「平台约束只在对应 actual
 *   里处理」这一设计原则的直接体现（见 commonMain 的 Kdoc）。
 */
@OptIn(ExperimentalForeignApi::class)
actual object Log {

    private const val DOMAIN: UInt = 0xD001u

    actual fun w(tag: String, message: String) {
        OH_LOG_Print(LOG_APP, LOG_WARN, DOMAIN, tag, "%{public}s", message)
    }

    actual fun w(tag: String, message: String, error: Throwable) {
        val detail = "${error::class.simpleName ?: "Throwable"}: ${error.message ?: ""}"
        OH_LOG_Print(LOG_APP, LOG_WARN, DOMAIN, tag, "%{public}s | %{public}s", message, detail)
    }
}
