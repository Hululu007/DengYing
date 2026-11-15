package com.dengying.player.ui.ohos

import com.dengying.player.app.OhosApp
import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.player.OhosPlayerEngineHost
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.platform.Log
import com.dengying.player.ui.UiDeps
import com.dengying.player.ui.UiDepsHolder
import com.dengying.player.ui.account.AhAuth
import com.dengying.player.ui.theme.ThemePrefs

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

    /** 当前只有 debug 产物（HAP 由 hvigor debug 构建），真接签名包时改由 ArkTS 传入 */
    override val isDebug: Boolean = true
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
    }
}
