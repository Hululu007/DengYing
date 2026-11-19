package com.dengying.player.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dengying.player.data.source.SourceRepository
import com.dengying.player.data.store.Store
import com.dengying.player.platform.KeyValueStore
import com.dengying.player.platform.SharedPrefsStore
import com.dengying.player.ui.UiDeps
import com.dengying.player.ui.UiDepsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用容器 —— 手写依赖装配。
 *
 * 【为什么不用 Hilt/Koin】
 * 全应用只有 3 个长生命周期对象（Store、SourceRepository、播放器持有者），
 * 依赖图是一条直线，没有任何条件装配、作用域嵌套或跨模块注入需求。
 * 引一个 DI 框架的代价是：一个编译期插件、一套注解语义、以及"这个依赖从哪来"
 * 多了一层间接。这里 20 行显式装配比框架更清楚，且启动更快。
 *
 * 装配方向：Application → Store / SourceRepository → ViewModel（页面自建）。
 * 反向依赖为零——domain 不依赖 data，data 不依赖 ui。
 */
class DengYingApp : Application(), UiDeps {

    /** 应用级协程作用域：只承载「不该阻塞首帧」的初始化与后台补齐 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 本地存储（收藏/历史/搜索历史/源覆盖层/分类缓存） */
    override lateinit var store: Store
        private set

    /**
     * 更新提示状态使用的 KV，与 [store] 共用同一个 preferences 文件。
     *
     * `Store` 不暴露底层 KV；更新提示只需要两个独立键，单独持有同一个实例
     * 比给 Store 增加公共 getter 更窄。
     */
    override lateinit var keyValueStore: KeyValueStore
        private set

    /** 源接入聚合客户端（含健康表与分类缓存） */
    override lateinit var sources: SourceRepository
        private set

    /**
     * [UiDeps] 契约实现：播放引擎宿主。
     *
     * 直接返回 [PlaybackEngineHolder] —— 它已实现 `PlayerEngineHost`。
     * 这层转发让 `:ui` 只看见接口，不知道引擎具体由谁持有。
     */
    override val engineHost: com.dengying.player.player.PlayerEngineHost
        get() = com.dengying.player.playback.PlaybackEngineHolder

    /**
     * [UiDeps] 契约实现：投屏通道（FR-418..423）。
     *
     * `GmsCastController` 构造时会取 `CastContext.getSharedInstance()`，
     * 依赖 Manifest 里已登记的 `DengYingCastOptionsProvider`。用 `lazy`
     * 而不是 `onCreate` 里立即建：CastContext 初始化要等 Application 完全就绪，
     * 且并非每次启动都会点投屏，懒建省一次 GMS 绑定。
     *
     * 拿不到 Play 服务时 `GmsCastController` 自己降级为 `isAvailable() == false`，
     * 不会让启动崩溃。
     */
    override val castController: com.dengying.player.playback.cast.CastController by lazy {
        com.dengying.player.playback.cast.GmsCastController(this)
    }

    /**
     * [UiDeps] 契约实现：是否 debug 构建。
     *
     * 迁移前 `PlayerViewModel` 直接读 `BuildConfig.DEBUG`，升舱到 `:ui` 后
     * 该字段不可见（`BuildConfig` 由各模块自己生成，`:ui` 关闭了 `buildConfig`），
     * 故由唯一持有它的平台壳注入。语义不变：release 下恒 false。
     */
    override val isDebug: Boolean
        get() = com.dengying.player.BuildConfig.DEBUG

    override val appVersionName: String
        get() = com.dengying.player.BuildConfig.VERSION_NAME

    override val appVersionCode: Long
        get() = com.dengying.player.BuildConfig.VERSION_CODE.toLong()

    /** 首页分页结果缓存（FR-701/702）—— 派生数据，与用户资产分开存 */
    override lateinit var pageCache: com.dengying.player.data.cache.PageCache
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 播放链路探针：写应用私有目录。**不依赖日志**——实测 vivo 会全量过滤
        // 本应用日志，排查时读不到任何自打日志。
        com.dengying.player.player.PlaybackProbe.init(filesDir.absolutePath)
        // 离线下载文件区：与播放缓存分开，清缓存不会误删已下载剧集。
        com.dengying.player.data.download.DownloadStorage.init(
            java.io.File(filesDir, "downloads").absolutePath,
        )

        // 播放引擎宿主注入 Application 上下文：`PlayerEngineHost.engine()` 的
        // 跨平台契约不带 `Context`，Android 侧的构造上下文由平台壳在这里补上。
        com.dengying.player.playback.PlaybackEngineHolder.init(this)
        // P4-1d：PlaybackSession 升舱到 commonMain 后不再带 Context，
        // 引擎由宿主注入（PlaybackEngineHolder 已实现 PlayerEngineHost）。
        com.dengying.player.playback.PlaybackSession.init(
            com.dengying.player.playback.PlaybackEngineHolder,
        )

        // 媒体缓存宿主注入 Application 上下文：`MediaCacheHost` 的跨平台契约同样不带
        // `Context`（`SimpleCache` 要 cacheDir 与 DatabaseProvider），故由平台壳补齐。
        // 只注入引用，不建实例——实例构造会阻塞等索引加载，由 `MediaCache.warmUp()`
        // 在首次起播的 IO 段落里懒建（缓存当前默认关闭，这条链路平时不会执行）。
        com.dengying.player.data.cache.MediaCacheHost.init(this)

        // P4-4b：把装配事实注入 :ui（依赖倒置）。:ui 只声明 UiDeps 需求，
        // 由本类满足——方向是 :app → :ui，不是反过来。
        // 必须在 setContent 之前完成：Screen 的首个组合就会读 UiDepsHolder.get()。
        UiDepsHolder.init(this)

        // 存储先建：它的 KeyValueStore 实例要随进程存活，且覆盖层需在
        // 第一次聚合前载入内存（聚合的源过滤是同步的）。
        // P3 批 E：SharedPreferences 由 SharedPrefsStore 包成跨平台接口，
        // commonMain 的 Store/PageCache 只认接口，不再认识 android.content。
        val prefs = getSharedPreferences("hmvideo_store", Context.MODE_PRIVATE)
        keyValueStore = SharedPrefsStore(prefs)
        val kv = keyValueStore
        store = Store(kv)
        // 两个BT 登录态与用户资产共用同一 preferences 文件（独立键，见 BttwoAuth）
        com.dengying.player.data.source.adapters.BttwoAuth.init(kv)
        // AH 账号体系的签名令牌 / device_id / 会话原始数据也落在同一份 kv；
        // 与 BttwoAuth 的键互不相交，初始化顺序必须在 setContent 之前。
        com.dengying.player.ui.account.AhAuth.init(kv)
        // 主题风格偏好（我的页可切，见 ThemePrefs）同样走这份 kv
        com.dengying.player.ui.theme.ThemePrefs.init(kv)
        // 播放偏好（长按倍速、媒体缓存开关）必须早于首个播放器数据源构建
        com.dengying.player.ui.prefs.PlaybackPrefs.init(kv)
        com.dengying.player.data.download.DownloadCenter.init(kv)
        sources = SourceRepository()
        // 分页缓存与用户资产**共用同一个 preferences 文件**但独立键：
        // 这样"清空用户资产"不会误删缓存，反之亦然
        pageCache = com.dengying.player.data.cache.PageCache(kv)

        // 启动时清一次过期条目（不每次读都清，避免无谓的落盘）
        pageCache.pruneExpired()

        // 分类缓存的磁盘读写挂到 Store 上（避免 data.source 依赖 data.store 的具体实现）
        sources.classCacheReader = {
            store.readClassCache().mapValues { com.dengying.player.data.source.SourceRepository.ClassCacheEntry(it.value.first, it.value.second) }
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
            // 【Log.w 而不是 Log.i】实测 vivo 机型会过滤第三方应用的 info/debug 日志，
            // 而这条"存储是否就绪"是排查 FR-507 类问题的第一手证据，必须能读到。
            android.util.Log.w("DY/App", "store ${store.storeState()} reason=${store.storeFailReason()}")
        }
    }

    /**
     * 只允许打开本仓库的 GitHub / Gitee Releases 页面。
     *
     * 更新检查本身已经校验了下载页地址，这里再做一次 host/path 校验，防止
     * 将来调用方把任意 URL 传进来时被 `ACTION_VIEW` 直接打开。
     */
    override fun openExternalUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (uri.scheme != "https") return false
        val path = uri.path ?: return false
        val allowed = when (uri.host) {
            "github.com" -> {
                path == "/xiao-hans/DengYing/releases" ||
                    path.startsWith("/xiao-hans/DengYing/releases/")
            }
            "gitee.com" -> {
                path == "/han-xiaos-whisper/DengYing/releases" ||
                    path.startsWith("/han-xiaos-whisper/DengYing/releases/")
            }
            else -> false
        }
        if (!allowed) return false
        return runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    override fun copyText(text: String): Boolean {
        if (text.isBlank()) return false
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("DengYing 下载链接", text))
        return true
    }

    companion object {
        @Volatile
        private var instance: DengYingApp? = null

        /**
         * 供页面取容器。
         *
         * 【失败要指名道姓】初版这里只写 `requireNotNull`，而调用方的
         * `catch (e: Exception)` 把它当网络故障显示成「网络异常，请检查连接」——
         * 一次真实故障（清单漏写 `android:name`，Application 从未执行）
         * 被伪装成网络问题，排查绕了一大圈。
         *
         * 故异常消息必须能直接指出根因与查法，并且**类型可区分**：
         * 用 [IllegalStateException] 而不是裸 `IllegalArgumentException`，
         * 调用方据此把「装配缺失」与「网络异常」分开提示。
         */
        fun get(): DengYingApp = instance ?: error(
            "DengYingApp 未初始化：AndroidManifest.xml 的 <application> 缺少 " +
                "android:name=\".DengYingApp\"，或应用进程被重建后尚未走完 onCreate"
        )
    }
}
