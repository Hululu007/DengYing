package com.dengying.player.ui.ohos

import com.dengying.player.app.OhosApp
import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.player.OhosPlayerEngineHost
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.platform.Log
import com.dengying.player.platform.OhosAppInfo
import com.dengying.player.platform.OhosShellControl
import com.dengying.player.playback.cast.CastController
import com.dengying.player.playback.cast.NoopCastController
import com.dengying.player.ui.UiDeps
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.ui.account.AhAuth
import com.dengying.player.ui.theme.ThemePrefs
import com.dengying.player.ui.update.UpdateChecker

/**
 * `:ui` 需求的鸿蒙侧装配（对齐 Android 的 `DengYingApp : UiDeps`）。
 *
 * 【为什么不是 `DengYingApp`】那是 `android.app.Application` 的子类，属于 `:app`。
 * 跨平台层只认 [UiDeps] 接口，鸿蒙壳在这里给出实现，方向仍是 `壳 → :ui`。
 *
 * 【为什么字段用 getter 转发而不是在构造时抓一份】
 * [OhosApp] 的 `store` / `sources` / `pageCache` 都是 `lateinit var`，由
 * `dengy_app_init`（EntryAbility.onCreate → ArkTS → napi → dengy_app_init）填。
 * 抓一份会让"装配顺序"变成隐性约束；用 getter 则无论谁先谁后都取到最新值，
 * 真出错时抛的是 `lateinit` 的明确异常而不是一个空对象。
 */
object OhosUiDeps : UiDeps {

    override val sources: SourceRepository get() = OhosApp.sources

    override val store: Store get() = OhosApp.store

    override val pageCache: PageCache get() = OhosApp.pageCache

    /**
     * 播放引擎宿主。
     *
     * 实现见 `:shared/ohosMain` 的 [OhosPlayerEngineHost] / `OhosPlayerEngine`
     * （cinterop 直接调 MediaKit 的 `OH_AVPlayer`）。单例、归零只暂停不释放，
     * 语义与 Android `PlaybackEngineHolder` 对齐。
     *
     * 画面已接入 ArkUI XComponent surfaceId（见 `OhosVideoSurfaceBridge`）；
     * 播放页根背景按绑定/出画状态让位，让下层视频透出。内嵌小窗仍为黑底占位，
     * 详见 `docs/ohos-player-notes.md`。自定义请求头本 SDK 版本无 native 入口，
     * 暂不生效。
     */
    override val engineHost: PlayerEngineHost = OhosPlayerEngineHost

    /**
     * 投屏（FR-418..423）：鸿蒙侧没有 GMS Cast，注入空实现。
     *
     * UI 的投屏入口会显示但 `isAvailable() == false`，与 iOS 壳一致；
     * 真投屏只在 Android 壳由 `GmsCastController` 提供，口径见 docs/03。
     */
    override val castController: CastController = NoopCastController

    /**
     * 当前 HAP 是否为 debug 构建，由壳从 `bundleManager` 的 `appInfo.debug` 推送
     * （`EntryAbility` → `dengy.setAppVersion(name, code, debug)` → [OhosAppInfo]）。
     *
     * 【别再写死 true】之前恒 true，release 包也会渲染账号密码表单与「令牌设置」；
     * Android 读 `BuildConfig.DEBUG`、iOS 读 `Platform.isDebugBinary`，鸿蒙没有这两条
     * 通道，只能由壳推。未推送时按 release 处理。
     */
    override val isDebug: Boolean get() = OhosAppInfo.isDebug

    /**
     * 版本号由壳推送：EntryAbility 读 `bundleManager` 的 `versionName` / `versionCode`
     * 后经 `dengy.setAppVersion` → NAPI → [OhosAppInfo] 落到这里。
     *
     * 为什么用 getter 而不是构造时抓一份：NAPI 注入发生在 EntryAbility.onCreate、
     * `UiDepsHolder.init` 之后（首帧组合就能读 `appVersionName`），抓块照会让
     * 更新检查读到空串。用 getter 则谁先谁后都取到最新值。
     *
     * 未推送时是空串/0，`UiDeps` 的默认语义即「本平台不提供版本」，更新检查跳过。
     */
    override val appVersionName: String
        get() = OhosAppInfo.versionName

    override val appVersionCode: Long
        get() = OhosAppInfo.versionCode

    /**
     * 只允许打开本仓库的 Release 页面，校验与 Android `DengYingApp.openExternalUrl`
     * 同一份（[UpdateChecker.isAllowedReleaseUrl]），再经 NAPI 交给 ArkTS 用系统浏览器打开。
     *
     * 校验留在公共侧：`dengy_shell_call` 是通用通道，不该让壳替上层做 URL 白名单，
     * 否则以后有人在页面里传任意 URL 就会直接打开。
     */
    override fun openExternalUrl(url: String): Boolean {
        if (!UpdateChecker.isAllowedReleaseUrl(url)) return false
        return OhosShellControl.call(OhosShellControl.OP_OPEN_URL, url)
    }

    override fun copyText(text: String): Boolean {
        if (text.isBlank()) return false
        return OhosShellControl.call(OhosShellControl.OP_COPY_TEXT, text)
    }
}

/**
 * 注入依赖。必须在第一帧组合之前调用。
 *
 * 调用点在 [mainArkUIViewController]：Composable 的首个组合就会读
 * `UiDepsHolder.get()`，注入晚一步就是启动期崩溃。
 */
fun installOhosUiDeps() {
    UiDepsHolder.init(OhosUiDeps)

    // 对齐 Android DengYingApp.onCreate:102-104：AH 账号的签名令牌 / device_id /
    // 会话原始数据，以及主题风格偏好，都落在同一份 kv（与 BttwoAuth 键互不相交）。
    // 必须在这里、首帧组合之前完成——各 Screen 的首个组合就可能读这些单例。
    //
    // kv 由 EntryAbility.onCreate → dengy_app_init 先行填好；若 init 未执行或
    // FileKeyValueStore 构造失败（OhosApp 会退化为内存实现），这里只跳过初始化：
    // AhAuth/ThemePrefs 对「未注入 kv」本身是安全的（读空值、写丢弃），
    // 绝不能因为 kv 未就绪让整个启动崩溃。
    val kv = OhosApp.kvOrNull
    if (kv == null) {
        Log.w("DY/Ui", "kv 未就绪，跳过 AhAuth/ThemePrefs 初始化（账号与主题走默认值）")
    } else {
        AhAuth.init(kv)
        ThemePrefs.init(kv)
        com.dengying.player.ui.prefs.PlaybackPrefs.init(kv)
    }
}
