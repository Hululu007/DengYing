package com.dengying.player.ui

import com.dengying.player.core.VideoItem
import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.ui.viewmodel.AltItem
import com.dengying.player.ui.viewmodel.LruCache

/**
 * `:ui` 需要的应用级依赖（KMP P4 批 pages）
 *
 * ## 为什么要有这一层
 *
 * 迁移前 6 个 Screen 都直接写 `val deps = DengYingApp.get()`，再取
 * `deps.sources` / `deps.store` / `deps.pageCache` / `deps.applicationContext`。
 * 那是 `:ui` → `:app` 的**反向依赖**，与 docs/09 §3 的
 * `:app → :ui → :shared` 依赖图矛盾，也让 `:ui:commonMain` 无法编译（`DengYingApp`
 * 是 `android.app.Application` 的子类）。
 *
 * 这里把「Screen 需要什么」抽成一个接口，由 `:app` 实现后注入。
 * 方向因此反转成 `:app → :ui`：`:ui` 只声明需求，不知道谁满足它。
 *
 * ## 为什么用 interface + 注册表，而不是 expect/actual
 *
 * 与 [com.dengying.player.platform.KeyValueStore]、[PlayerEngineHost] 同一理由 ——
 * 这些是**装配关系**，不是平台服务。`expect/actual` 表达的是「同一件事在各平台
 * 有不同的实现方式」（`HttpTransport` 要 `HttpURLConnection` / `NSURLSession`），
 * 而这里各平台的实现完全可能相同（都由各自的壳注入），差异只在「谁来注入」。
 *
 * ## 为什么这里没有 `applicationContext`
 *
 * 迁移前只有 `DetailViewModel` / `PlayerViewModel` 的构造参数用到它，且
 * `DetailViewModel` 的 `appContext` 字段声明后**从未使用**（纯冗余），
 * `PlayerViewModel` 只用它调 `PlaybackEngineHolder.get(ctx)` ——
 * 而 `PlayerEngineHost.engine()` 是无参的（上下文已由平台壳 `init` 注入）。
 * 故本接口不含任何 `Context`，两个 VM 的 `Context` 参数在 P4-5 一并删除。
 */
interface UiDeps {

    /** 源接入聚合客户端（含健康表与分类缓存） */
    val sources: SourceRepository

    /** 本地存储（收藏/历史/搜索历史/源覆盖层/分类缓存） */
    val store: Store

    /** 首页分页结果缓存（FR-701/702） */
    val pageCache: PageCache

    /** 播放引擎宿主（全局唯一引擎的计数与生命周期） */
    val engineHost: PlayerEngineHost

    /**
     * 当前是否为 debug 构建（KMP P4-4b）。
     *
     * 迁移前 `PlayerViewModel` 直接读 `com.dengying.player.BuildConfig.DEBUG`，
     * 那是 `:app` 的生成类 —— `:ui` 模块的 `buildConfig = false`，
     * 升舱后 commonMain 里根本不存在这个符号（`:ui:compileKotlinJvm` 报
     * `Unresolved reference 'BuildConfig'`）。
     *
     * 不用 `expect/actual` 另造门面的理由：这是**构建形态**而不是平台能力，
     * 同一个 target 的 debug/release 取值不同，与「哪个平台」无关；
     * 且只有平台壳（`:app`）真正持有 `BuildConfig`。
     * 故与其余装配事实一起走本接口注入。
     */
    val isDebug: Boolean

    /**
     * 跨源候选缓存（FR-417），**进程级**。
     *
     * 【为什么挂在 UiDeps 而不是 DetailViewModel 里】候选搜索要打十几个源、
     * 真机实测一次 5–7 秒（`alt-sources-loaded elapsed=5836/4803/5767`）。
     * 它原先放在 VM 字段上，而 `DetailViewModel` 的 key 是
     * `"detail_$source$vodId"`（DetailScreen.kt:74），挂在 NavBackStackEntry
     * 上——**退出详情页 VM 就销毁，缓存跟着没了**。真机抓包证实：同一部片
     * 进出 3 次，3 次全是 `alt-sources-loaded`，`alt-cache-hit` 一次都没出现，
     * 每次白等 5 秒。缓存的生命周期必须长于一次页面访问才有意义。
     *
     * 用 `by lazy` 的默认实现而不是加抽象属性：各平台壳无需改动，
     * 且 VM 与 UI 线程的首次访问都发生在主线程，无并发初始化问题。
     */
    val altSourceCache: LruCache<String, List<AltItem>>
        get() = sharedAltSourceCache

    /**
     * 详情缓存（`"源key/vodId"` → [VideoItem]），**进程级**。
     *
     * 与 [altSourceCache] 同一理由：`DetailViewModel` 挂在 NavBackStackEntry 上，
     * 退出详情页即销毁。同一部片在一次会话里往往被反复打开（换源失败退回、
     * 从收藏/历史点回来、返回后重进），每次重打一次详情请求是纯浪费。
     */
    val detailCache: LruCache<String, VideoItem>
        get() = sharedDetailCache

    companion object {
        private val sharedAltSourceCache = LruCache<String, List<AltItem>>(ALT_CACHE_MAX)
        private val sharedDetailCache = LruCache<String, VideoItem>(DETAIL_CACHE_MAX)

        /** 候选缓存最多记多少部片：一次会话里不太可能翻过这个数 */
        const val ALT_CACHE_MAX = 32

        /** 详情缓存条数：详情比候选小得多，多留一些 */
        const val DETAIL_CACHE_MAX = 64

        /**
         * [detailCache] 的键：`"源key/vodId"`。
         *
         * 必须是函数而不是各处现拼字符串——详情页的读与首页预取的写**必须同键**，
         * 拼错一个字符就是「预取了却永远不命中」，且没有任何报错。
         */
        fun detailCacheKey(source: String, vodId: Long): String = "$source/$vodId"
    }
}

/**
 * [UiDeps] 的注册表 —— 由平台壳在启动时注入一次。
 *
 * 【为什么是可变全局】`:ui` 的 Composable 在任意深度都可能需要一个依赖，
 * 而把它们一路作为参数往下传会污染每一层签名（`IndexScreen` 的 12 个参数
 * 里会有 4 个是基础设施）。Compose 的 `CompositionLocal` 是另一种选择，
 * 但它要求所有取用点都在 `@Composable` 上下文里 —— VM 工厂虽然确实在
 * Composable 内，可 `UiDeps` 也可能被非 Composable 的装配代码需要，
 * 且 `CompositionLocal` 缺省值语义（无 Provider 时静默给默认）比
 * 「未初始化就 error」更难排查。
 *
 * 故沿用 `DengYingApp.get()` 的形状：一个 `@Volatile` 单例 + 明确的错误消息。
 * 区别只在**方向**——现在是 `:app` 调 `:ui` 的 `init`，不是 `:ui` 反查 `:app`。
 */
object UiDepsHolder {

    @kotlin.concurrent.Volatile
    private var instance: UiDeps? = null

    /**
     * 注入依赖。由 `:app` 的 `Application.onCreate` 调用，只调一次。
     *
     * 允许重复调用（后注入的覆盖前面的）：进程重建、测试装配都需要这个弹性，
     * 而「只允许一次」会让测试之间互相污染。
     */
    fun init(deps: UiDeps) {
        instance = deps
    }

    /**
     * 取依赖。**在 Composable 里应当用 [uiDeps] 而非直接调这个**。
     *
     * @throws IllegalStateException 平台壳未注入。消息写清修复方式，因为这条
     *   错误一旦出现就是启动期崩溃，没有上下文很难猜。
     */
    fun get(): UiDeps = instance ?: error(
        "UiDepsHolder.init(deps) 未被调用：平台壳必须在 Compose 内容设置前注入 UiDeps" +
            "（见 DengYingApp.onCreate → UiDepsHolder.init(this)）",
    )

    /** 仅供测试：清空注入状态，避免用例之间互相污染 */
    fun resetForTest() {
        instance = null
    }
}
