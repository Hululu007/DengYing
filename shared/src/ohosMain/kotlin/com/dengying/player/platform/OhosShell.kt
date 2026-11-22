package com.dengying.player.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlin.concurrent.Volatile
import platform.posix.RTLD_NOLOAD
import platform.posix.dlopen
import platform.posix.dlsym

/**
 * 鸿蒙壳推过来的应用版本（EntryAbility 读 `bundleManager` 后经 NAPI 推入）。
 *
 * 【为什么由壳推】`:ui` 关了 BuildConfig，读不到 HAP 的 `build-profile.json5`；
 * 更新提示必须和真正安装的包同版本，否则会出现「应用内显示 1.3.27、装的是
 * 1.3.26」的错位。Android 侧同口径在 `DengYingApp.appVersionName`（读 BuildConfig），
 * iOS 侧在 `IosAppDeps`（读 Info.plist）。
 *
 * 未注入时保持空串/0 —— 与 `UiDeps.appVersionName` 默认值的语义一致：
 * 「本平台不提供版本信息」，更新检查直接跳过。
 */
object OhosAppInfo {

    @Volatile
    var versionName: String = ""
        private set

    @Volatile
    var versionCode: Long = 0L
        private set

    /**
     * 当前 HAP 是否为 debug 构建（壳从 bundleManager 的 appInfo.debug 读出）。
     *
     * 之前写死 true，导致 release 包也渲染账号密码表单与
     * 「令牌设置」；Android 读 BuildConfig.DEBUG、iOS 读 Platform.isDebugBinary，
     * 鸿蒙只能由壳推。默认 false：没推上来就按 release 处理（宁可少显示调试入口）。
     */
    @Volatile
    var isDebug: Boolean = false
        private set

    fun setVersion(name: String, code: Long, debug: Boolean) {
        versionName = name
        versionCode = code
        isDebug = debug
    }
}

/**
 * 让 ArkTS 改窗口方向与系统栏（Kotlin → ArkTS 的反向调用）。
 *
 * Android 用 `requestedOrientation` + `WindowInsetsController`，iOS 用
 * `IosOrientationLock` 请求方向；鸿蒙的方向和系统栏只能由 ArkUI 的 `window`
 * 接口改，而 Kotlin 调不到 ArkTS。于是 ArkTS 在 EntryAbility 里注册 sink
 * （`registerOrientationSink`），这里经 `dlopen("libentry.so", RTLD_NOLOAD)`
 * + `dlsym` 调 `dengy_request_orientation` 投递请求。
 *
 * 未注册（老 HAP、桌面预览）时 [requestLandscape] 返回 false，调用方静默降级 ——
 * 与 `FullscreenState` 契约「只请求、不保证生效，且不得抛异常」一致。
 */
@OptIn(ExperimentalForeignApi::class)
object OhosWindowControl {

    private var resolved = false
    private var requestSymbol: CPointer<CFunction<(Int) -> Int>>? = null

    /**
     * 请求窗口进入/退出全屏方向。
     *
     * @param landscape true = 横屏并把系统栏收起；false = 竖屏并恢复系统栏
     * @return 请求是否已投递给 ArkTS（true 只代表投递成功，不保证窗口已转）
     */
    fun requestLandscape(landscape: Boolean): Boolean {
        val fn = resolve() ?: return false
        return runCatching { fn(if (landscape) 1 else 0) == 1 }.getOrDefault(false)
    }

    private fun resolve(): CPointer<CFunction<(Int) -> Int>>? {
        if (resolved) return requestSymbol
        val handle = dlopen("libentry.so", RTLD_NOLOAD)
        val symbol = handle?.let { dlsym(it, "dengy_request_orientation") }
        @Suppress("UNCHECKED_CAST")
        requestSymbol = symbol as? CPointer<CFunction<(Int) -> Int>>
        resolved = true
        return requestSymbol
    }
}

/**
 * 让 ArkTS 执行「壳动作」：打开外部链接、写系统剪贴板（Kotlin → ArkTS）。
 *
 * 与 [OhosWindowControl] 同一条反向通道形态，共用 C 侧 `dengy_shell_call`
 * + ArkTS 的 `registerShellSink((op, arg) => void)`。没注册 sink（老 HAP、
 * 桌面预览）时返回 false，调用方按「本平台不支持」处理 —— 与
 * `UiDeps.openExternalUrl` / `copyText` 返回 Boolean 的契约一致。
 */
@OptIn(ExperimentalForeignApi::class)
object OhosShellControl {

    /** 用系统浏览器打开链接。arg 必须是 https URL，校验在调用方。 */
    const val OP_OPEN_URL = 1

    /** 把文本写进系统剪贴板。 */
    const val OP_COPY_TEXT = 2

    /** 设置当前窗口亮度。arg 是 0.01..1 的浮点字符串（只影响本窗口，不改系统设置）。 */
    const val OP_SET_BRIGHTNESS = 3

    /** 撤掉窗口亮度覆写，交还系统亮度。arg 可为空。 */
    const val OP_RESTORE_BRIGHTNESS = 4

    private var resolved = false
    private var callSymbol: CPointer<CFunction<(Int, CPointer<ByteVar>) -> Int>>? = null

    /**
     * 投递一条壳动作。
     *
     * @return 是否已投递给 ArkTS（true 只代表投递成功，不保证系统动作已完成）
     */
    fun call(op: Int, arg: String): Boolean {
        // 不校验 arg 非空：OP_RESTORE_BRIGHTNESS 就是无参动作。
        if (op <= 0) return false
        val fn = resolve() ?: return false
        return runCatching {
            memScoped {
                fn(op, arg.cstr.ptr) == 1
            }
        }.getOrDefault(false)
    }

    private fun resolve(): CPointer<CFunction<(Int, CPointer<ByteVar>) -> Int>>? {
        if (resolved) return callSymbol
        val handle = dlopen("libentry.so", RTLD_NOLOAD)
        val symbol = handle?.let { dlsym(it, "dengy_shell_call") }
        @Suppress("UNCHECKED_CAST")
        callSymbol = symbol as? CPointer<CFunction<(Int, CPointer<ByteVar>) -> Int>>
        resolved = true
        return callSymbol
    }
}

/**
 * 壳回调入口（C 侧 `dlsym(handle, "dengy_set_app_version")` 取址调用）。
 *
 * 必须是顶层函数：@CName 标在 object 成员上会多出 receiver 参数，桥按两参
 * 签名调用时整体错位。
 */
@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@kotlin.native.CName("dengy_set_app_version")
fun setAppVersionFromShell(name: CPointer<ByteVar>?, code: Long, debug: Int) {
    OhosAppInfo.setVersion(name?.toKString() ?: "", code, debug != 0)
}

/**
 * 壳推送的屏幕/音量真值（ArkTS → Kotlin）。
 *
 * 亮度、媒体音量只有 ArkTS 能读（`window.getWindowProperties`.brightness、
 * `audioVolumeManager`），Kotlin 侧的手势需要一个「起点」，所以壳在
 * onWindowStageCreate 与音量回调里各推一次。取不到时保留 NaN，调用方退化为 0.5。
 */
object OhosScreenLevels {
    @Volatile
    var brightness: Float = Float.NaN
        private set

    /** ArkTS 推一次真值；负值表示「读不到」（跟随系统亮度），不覆盖已有值。 */
    fun setBrightness(brightness: Float) {
        if (brightness >= 0f) this.brightness = brightness.coerceIn(0f, 1f)
    }
}

/** 壳回调入口（C 侧 `dlsym(handle, "dengy_set_screen_info")` 取址调用）。 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
@kotlin.native.CName("dengy_set_screen_info")
fun setScreenInfoFromShell(brightness: Float) {
    OhosScreenLevels.setBrightness(brightness)
}
