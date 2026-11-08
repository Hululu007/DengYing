package com.dengying.player.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.platform.UserDefaultsStore
import com.dengying.player.player.PlaybackProbe
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.ui.navigation.DengYingNavHost
import com.dengying.player.ui.platform.configureAudioSession
import com.dengying.player.ui.player.IosPlayerEngineHost
import com.dengying.player.ui.theme.DengYingTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/**
 * iOS 入口与依赖装配（KMP P5-4）
 *
 * ## 与 Android 侧的结构对应
 *
 * | Android | iOS |
 * |---|---|
 * | `DengYingApp : Application(), UiDeps` | [IosAppDeps] : [UiDeps] |
 * | `DengYingApp.onCreate()` 装配 | [IosAppDeps.ensureStarted]（懒启动，幂等） |
 * | `MainActivity.setContent { DengYingTheme { DengYingNavHost() } }` | [MainViewController] |
 * | `PlaybackEngineHolder.init(this)` | [IosPlayerEngineHost]（单例 object，无需 init） |
 *
 * 装配方向与 Android 完全一致：壳建 `Store` / `SourceRepository` / `PageCache`，
 * 通过 [UiDepsHolder.init] 注入 `:ui`，再起 Compose 内容。`:ui` 不知道谁满足它。
 *
 * ## 播放引擎
 *
 * Android 侧是 `PlaybackEngineHolder` + Media3；iOS 侧是 [IosPlayerEngineHost] +
 * `AvPlayerEngine`（`ui/src/iosMain/.../ui/player/AvPlayerEngine.kt`），语义逐条对齐：
 * 单例引擎、`peek()` 不创建、`surfaceCount` 归零只暂停不释放。
 *
 * 引擎本体用 Kotlin/Native cinterop 直连 AVFoundation，而不是写在 Swift 里：
 * `PlayerEngine.viewHandle()` 要把 `AVPlayerLayer` 交回 Compose，而 Swift 类实现
 * Kotlin 接口需要反向互操作，标准 CMP framework 导出不提供。docs/09 §3 的
 * 「AVPlayer 引擎装配」说的是**由 iOS 壳负责起引擎**，与本文件的分工不冲突。
 */
class IosAppDeps : UiDeps {

    /** 应用级协程作用域：只承载「不该阻塞首帧」的初始化与后台补齐 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override lateinit var store: Store
        private set

    override lateinit var sources: SourceRepository
        private set

    override lateinit var pageCache: PageCache
        private set

    /**
     * 播放引擎宿主：真实现，与 Android `PlaybackEngineHolder` 逐条同语义
     * （单例引擎、surface 计数归零只暂停不释放、`peek()` 不创建）。
     */
    override val engineHost: PlayerEngineHost = IosPlayerEngineHost

    /**
     * 是否 debug 构建。
     *
     * 用 `Platform.isDebugBinary`（Kotlin/Native 自带）：它对应 Xcode 的
     * `DEBUG` 编译条件，与 Android 侧 `BuildConfig.DEBUG` 同义。
     *
     * 需 `@OptIn(ExperimentalNativeApi::class)`：该属性自 Kotlin 1.9 起被标为
     * 实验性（它暴露的是编译器写入二进制的标记，属 ABI 细节）。这里用的是
     * 官方推荐用法，且只读一次，不存在行为风险。
     */
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    override val isDebug: Boolean = kotlin.native.Platform.isDebugBinary

    /**
     * 装配探针、音频会话、存储与源聚合。**幂等**：重复调用只执行一次。
     *
     * 与 Android `onCreate` 的顺序逐条对应，唯一的差别是这里显式调用
     * （iOS 没有 `Application.onCreate` 这个生命周期点，由 [MainViewController]
     * 在第一次创建视图控制器时触发）。
     */
    fun ensureStarted() {
        if (started) return
        started = true

        // probe file lives in Library/Caches (same semantics as Android cacheDir:
        // reclaimable by the system, excluded from iCloud backup).
        PlaybackProbe.init(probeDir())

        // mute switch must not interrupt playback == Android handleAudioFocus = true
        runCatching { configureAudioSession() }

        // NSUserDefaults 是 App 沙盒内的主域，与 Android 的 SharedPreferences
        // 同构（键 → 字符串扁平映射、加载一次内存读）。
        val kv = UserDefaultsStore()
        store = Store(kv)
        sources = SourceRepository()
        // 分页缓存与用户资产**共用同一个域**但独立键：这样「清空用户资产」
        // 不会误删缓存，反之亦然（与 Android 侧同口径）。
        pageCache = PageCache(kv)

        pageCache.pruneExpired()

        // 分类缓存的磁盘读写挂到 Store 上（避免 data.source 依赖 data.store 的具体实现）
        sources.classCacheReader = {
            store.readClassCache().mapValues {
                SourceRepository.ClassCacheEntry(it.value.first, it.value.second)
            }
        }
        sources.classCacheWriter = { map ->
            store.writeClassCache(map.mapValues { it.value.classes to it.value.time })
        }

        // NFR-010：存储初始化不阻塞首帧。页面首帧允许空列表（读路径返回空集合），
        // 写路径在闸门里等待收敛（FR-507），故这里只发起、不等待。
        appScope.launch {
            store.init()
            // 初始化收敛后再把覆盖层同步给聚合层——这是同步过滤的前提
            sources.override = store.getSourceOverride()
        }
    }

    /**
     * probe directory: <Library>/Caches.
     *
     * Falls back to the sandbox root when Library is unavailable: the probe is a
     * diagnostic facility, never a startup precondition, so it must not throw.
     */
    private fun probeDir(): String {
        val lib = NSSearchPathForDirectoriesInDomains(
            NSLibraryDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String
        return (lib ?: NSHomeDirectory()) + "/Caches"
    }

    private var started = false
}

/**
 * 创建 iOS 的根视图控制器。
 *
 * 由 `iosApp/DengYingApp.swift` 直接调用：
 * ```swift
 * MainViewControllerKt.MainViewController().ignoresSafeArea(.all)
 * ```
 *
 * The leading capital is deliberate: Kotlin/Native exports a top-level `fun Foo()`
 * as `MainViewControllerKt.Foo()`, which is exactly the CMP template shape
 * (M-6 defect 1 was this name mismatch).
 *
 * 装配顺序照 Android 侧：**先注入 [UiDeps]，再起 Compose 内容**。反过来会让
 * 首个组合读 `UiDepsHolder.get()` 时崩（`error(...)` 的明确消息就是为这个场景写的）。
 */
fun MainViewController(): UIViewController {
    val deps = IosAppDeps()
    deps.ensureStarted()
    UiDepsHolder.init(deps)
    return ComposeUIViewController {
        DengYingTheme {
            DengYingNavHost()
        }
    }
}
