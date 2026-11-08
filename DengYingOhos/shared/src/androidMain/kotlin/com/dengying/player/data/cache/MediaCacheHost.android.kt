package com.dengying.player.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.dengying.player.platform.Log
import java.io.File

/**
 * [MediaCacheHost] 的 Android 实现 —— Media3 `SimpleCache` 的唯一构造点
 *
 * ## 为什么需要 Application 上下文却不在签名里
 *
 * `SimpleCache` 要 `context.cacheDir` 与 `StandaloneDatabaseProvider(context)`，
 * 而 `commonMain` 的契约不带 `Context`。故由平台壳在启动时通过 [init] 注入，
 * 与 `PlaybackEngineHolder.init(context)` 是同一手法。
 *
 * 未 [init] 就取实例会抛 `IllegalStateException` 并指名根因——不静默降级：
 * 缓存开关打开却拿不到上下文，是装配错误，应该在第一次播放就暴露。
 *
 * ## 三个必须遵守的约束（读 `SimpleCache` 源码得到的）
 *
 * 1. **构造是同步阻塞的**：`ConditionVariable.block()` 等索引加载。
 *    故必须 IO 线程建（由 `MediaCache.warmUp()` 保证），且**懒初始化**，不放冷启动。
 * 2. **同一目录只允许一个实例**：`lockedCacheDirs` 是静态集合。
 *    故进程内单例。
 * 3. **目录不能放其他文件**：构造会删除无法识别的文件。故用独立子目录。
 *
 * ## 构造必须带 DatabaseProvider
 *
 * 初版用了**废弃的 2 参构造** `SimpleCache(dir, evictor)`，它内部
 * `preferLegacyIndex = true`，走的是旧格式索引（`*.idx`）。
 * 官方 Javadoc 明确说该路径"仅为向后兼容保留"，且官方推荐
 * **必须带 `DatabaseProvider`**。
 *
 * 这与"缓存开启时播放器高频空转"的实测现象高度相关——legacy 索引在多 loader
 * 并发读写（HLS 的 playlist 与分片是并发请求）下的锁行为与数据库索引不同。
 * 重启用缓存前必须走官方推荐路径。
 */
@OptIn(UnstableApi::class)
actual object MediaCacheHost {

    private const val TAG = "DY/MediaCache"

    /** 缓存子目录名（独立于其他文件，见约束 3） */
    private const val DIR_NAME = "media_cache"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var instance: SimpleCache? = null

    @Volatile
    private var failed = false

    private val lock = Any()

    /**
     * 注入 Application 上下文。
     *
     * 必须在任何 [getOrCreate] 之前调用一次（`DengYingApp.onCreate` 里）。
     * 重复调用是幂等的——只覆盖引用，不动已建好的实例。
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context = appContext ?: error(
        "MediaCacheHost.init(context) 未被调用：平台壳必须在取缓存实例前注入上下文" +
            "（见 DengYingApp.onCreate）"
    )

    actual fun getOrCreate(): Any? {
        if (failed) return null
        instance?.let { return it }
        synchronized(lock) {
            instance?.let { return it }
            if (failed) return null
            return try {
                val ctx = requireContext()
                val dir = File(ctx.cacheDir, DIR_NAME)
                if (!dir.exists()) dir.mkdirs()
                val cache = SimpleCache(
                    dir,
                    LeastRecentlyUsedCacheEvictor(MediaCache.LIMIT_MB * 1024 * 1024),
                    StandaloneDatabaseProvider(ctx),
                )
                instance = cache
                Log.w(TAG, "media cache ready, limit=${MediaCache.LIMIT_MB}MB")
                cache
            } catch (e: Exception) {
                // 降级：缓存不可用不该阻断播放
                failed = true
                Log.w(TAG, "media cache init failed, fallback to no-cache: ${e.message}")
                null
            }
        }
    }

    actual fun isReady(): Boolean = instance != null

    actual fun hasFailed(): Boolean = failed

    actual fun sizeBytes(): Long = instance?.cacheSpace ?: 0L

    actual fun clear() {
        synchronized(lock) {
            try {
                instance?.release()
            } catch (e: Exception) {
                Log.w(TAG, "release failed: ${e.message}")
            }
            instance = null
            failed = false
            val ctx = appContext
            if (ctx != null) {
                val dir = File(ctx.cacheDir, DIR_NAME)
                // 用官方 delete 而非直接删目录——它会同时清理索引数据
                try {
                    SimpleCache.delete(dir, StandaloneDatabaseProvider(ctx))
                } catch (e: Exception) {
                    Log.w(TAG, "delete failed: ${e.message}")
                }
            }
        }
    }
}
