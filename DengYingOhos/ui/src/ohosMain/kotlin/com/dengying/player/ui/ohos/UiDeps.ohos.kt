package com.dengying.player.ui.ohos

import com.dengying.player.app.OhosApp
import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.player.PlayerEngine
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.ui.UiDeps
import com.dengying.player.ui.UiDepsHolder

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
     * 鸿蒙侧还没有 AVPlayer 绑定，故给一个**明确的空实现**：
     * `peek()` 恒 null 让详情页内嵌位显示空态（而不是崩），
     * `engine()` 显式抛错——一旦有人真去起播，错误信息能直接指出「缺什么」，
     * 而不是一个难以定位的 NPE。
     */
    override val engineHost: PlayerEngineHost = object : PlayerEngineHost {
        override fun engine(): PlayerEngine = error(
            "鸿蒙侧尚未接入播放引擎：需在 :shared/ohosMain 用 OH_AVPlayer 实现 PlayerEngine，" +
                "再由本文件替换 engineHost",
        )

        override fun peek(): PlayerEngine? = null

        override fun surfaceActive() = Unit

        override fun surfaceInactive() = Unit

        override fun forceRelease() = Unit

        override fun surfaceCount(): Int = 0
    }

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
}
