package com.dengying.player.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.dengying.player.data.cache.PageCache
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.platform.UserDefaultsStore
import com.dengying.player.player.PlaybackProbe
import com.dengying.player.player.PlayerEngineHost
import com.dengying.player.playback.cast.CastController
import com.dengying.player.playback.cast.NoopCastController
import com.dengying.player.ui.navigation.DengYingNavHost
import com.dengying.player.ui.platform.configureAudioSession
import com.dengying.player.ui.player.IosPlayerEngineHost
import com.dengying.player.ui.theme.DengYingTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import com.dengying.player.ui.update.UpdateChecker
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
     * iOS 侧无 Google Cast：保留投屏入口但不可用，与鸿蒙无 GMS 口径一致。
     */
    override val castController: CastController = NoopCastController

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
     * 版本号来自 Info.plist。
     *
     * `iosApp/Info.plist` 的 `CFBundleShortVersionString` / `CFBundleVersion`
     * 就是 Xcode 写进产物的 `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION`，
     * 与 Android `BuildConfig.VERSION_NAME` / `versionCode` 同义。
     *
     * 必须走 plist 而不是写常量：`:ui` 关了 BuildConfig，写死会让「应用内显示
     * 1.3.27、安装包却是 1.3.26」重新出现。`CFBundleVersion` 是字符串且可能
     * 不是纯数字，解析失败回 0 —— 只有 `appVersionName` 参与更新比较，
     * 这个值当前只作记录。
     */
    override val appVersionName: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

    override val appVersionCode: Long =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.toLongOrNull() ?: 0L

    /**
     * 只允许打开本仓库的 Release 页面，校验与 Android `DengYingApp.openExternalUrl`
     * 同一份（[UpdateChecker.isAllowedReleaseUrl]），再交给系统浏览器打开。
     *
     * 用 `openURL(_:options:completionHandler:)` 而不是已废弃的 `openURL(_:)`：
     * iOS 10 起后者仍能用但打 deprecation 警告，且没有「用户拒绝」回调。
     * 返回值只代表「已交给系统」，不保证 Safari 真的打开（与 Android
     * `startActivity` 同语义）。
     */
    override fun openExternalUrl(url: String): Boolean {
        if (!UpdateChecker.isAllowedReleaseUrl(url)) return false
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(nsUrl)) return false
        app.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
        return true
    }

    override fun copyText(text: String): Boolean {
        if (text.isBlank()) return false
        UIPasteboard.generalPasteboard.string = text
        return true
    }

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
        com.dengying.player.data.download.DownloadStorage.init(downloadsDir())

        // mute switch must not interrupt playback == Android handleAudioFocus = true
        runCatching { configureAudioSession() }

        // NSUserDefaults 是 App 沙盒内的主域，与 Android 的 SharedPreferences
        // 同构（键 → 字符串扁平映射、加载一次内存读）。
        val kv = UserDefaultsStore()
        store = Store(kv)
        // P4-1d：PlaybackSession 升舱到 commonMain 后不再带 Context，引擎由宿主注入。
        // 与 Android `DengYingApp.onCreate` 对齐：单例宿主即 IosPlayerEngineHost。
        com.dengying.player.playback.PlaybackSession.init(IosPlayerEngineHost)
        // 两个BT 登录态与用户资产共用同一 NSUserDefaults 域（独立键，见 BttwoAuth）
        com.dengying.player.data.source.adapters.BttwoAuth.init(kv)
        // AH 账号体系的签名令牌 / device_id / 会话原始数据也落在同一份 kv；
        // 与 BttwoAuth 的键互不相交，初始化顺序必须在 setContent 之前。
        com.dengying.player.ui.account.AhAuth.init(kv)
        // 主题风格偏好（我的页可切，见 ThemePrefs）同样走这份 kv
        com.dengying.player.ui.theme.ThemePrefs.init(kv)
        com.dengying.player.ui.prefs.PlaybackPrefs.init(kv)
        com.dengying.player.data.download.DownloadCenter.init(kv)
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

    /**
     * 离线下载目录：<Documents>/downloads。
     *
     * Documents 会随 iTunes/iCloud 备份，符合用户可见资产的语义；
     * probe 仍留在可回收的 Library/Caches。
     */
    private fun downloadsDir(): String {
        val docs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String
        return (docs ?: NSHomeDirectory()) + "/downloads"
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
