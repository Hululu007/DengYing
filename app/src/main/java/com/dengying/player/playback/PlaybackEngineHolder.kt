package com.dengying.player.playback

import android.content.Context
import android.util.Log
import com.dengying.player.player.PlayerEngine
import com.dengying.player.player.PlayerEngineHost

/**
 * 播放引擎的全局持有者（FR-704 配套，**唯一实例**）。
 *
 * ## 为什么必须全局唯一（查官方资料后的修正）
 *
 * 初版让内嵌播放位与全屏播放页**各建一个引擎**，真机验证失败：
 * 点剧集长时间无画面、反复点击触发解码器抢占。
 *
 * 查 [google/ExoPlayer Issue #9556](https://github.com/google/ExoPlayer/issues/9556)
 * 后确认这是官方明确反对的做法。维护者 claincly 的原话：
 *
 * > "The best approach would be keep one player instance and use it.
 * > If you spin off multiple players, there will be time wasted in things like
 * > codec init; and you might run out of available codec."
 *
 * 即：**全应用一个 ExoPlayer 实例**，多处 UI 只是它的不同显示宿主。
 *
 * ## 引用计数的作用
 *
 * 同一时刻只应有一个 `PlayerView` 绑定该 player（两个宿主同时绑会互相抢画面）。
 * 但"何时释放"不能由某一处单独决定——详情页可能只是暂时退到后台，
 * 而全屏页正在用它。故用引用计数：
 *
 *  - [acquire]：某处 UI 要开始用（计数 +1）
 *  - [release]：某处 UI 用完（计数 -1），**归零时才真正释放**
 *
 * 计数归零后释放而不是常驻，理由：应用退到后台时不该继续占着解码器
 * （没有 `MediaSessionService` 就没有后台播放的必要）。
 *
 * ## 线程约束
 *
 * ExoPlayer 的所有 API **必须在主线程**调用（真机踩过
 * `IllegalStateException: Player is accessed on the wrong thread`）。
 * 故 [engine] 的取用与释放都由调用方保证在主线程；
 * 只有 `SimpleCache` 的预热在 IO（见 [MediaCache.warmUp]）。
 */
object PlaybackEngineHolder : PlayerEngineHost {

    private const val TAG = "DY/EngineHolder"

    @Volatile
    private var instance: Media3Engine? = null

    /**
     * **活跃播放表面的数量**（有几个 UI 正在显示这个引擎）。
     *
     * ## 为什么不用 acquire/release 引用计数（真机踩坑后的修正）
     *
     * 初版用 `acquire()`/`release()` 配对计数，结果泄漏：
     * ```
     * [inline-release] engine=false refCount=2   ← inlineEngine 已被置 null，
     *                                              e==null 导致没执行 release()
     * [inline-release] engine=false refCount=1   ← 计数卡住，引擎永不释放
     * ```
     * 根因：切全屏时 `toFullscreen()` 先 `inlineEngine = null`，
     * 之后 `releaseInline()` 取到的 `e` 就是 null，归还逻辑被跳过。
     * **计数与"谁持有"绑定的设计太脆弱**——任何一处提前清字段都会漏计数。
     *
     * ## 现在：计数只表示"有几个表面在显示"，与谁持有无关
     *
     * - `surfaceActive()`：某处 UI 开始显示（+1）
     * - `surfaceInactive()`：某处 UI 不再显示（-1）
     * - 计数归零 → **暂停播放**（不是释放）
     *
     * ## 为什么归零只暂停、不释放
     *
     * 用户可能很快返回继续看（切全屏、返回详情页）。频繁释放重建会让
     * codec 反复初始化——正是官方反对的做法（见文件头引用）。
     * 暂停即可达到"没有声音"的目标，成本远低于重建。
     *
     * 引擎只在 [forceRelease]（进程退出、清缓存）时真正销毁。
     */
    @Volatile
    private var surfaceCount = 0

    /**
     * 平台壳注入的 Application 上下文。
     *
     * 【为什么需要它】[PlayerEngineHost.engine] 的签名不带 `Context`——那是
     * `commonMain` 的契约，不能让 Android 类型漏进去。但 ExoPlayer 的构造确实要
     * 上下文，故由平台壳在 `Application.onCreate` 里 [init] 一次，
     * 之后 [engine] 才可用。未注入时 [engine] 抛异常（属装配错误，不是运行时状态）。
     */
    @Volatile
    private var appContext: Context? = null

    /** 由平台壳（`DengYingApp.onCreate`）注入 Application 上下文。**先于任何取引擎调用** */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** [PlayerEngineHost] 契约实现：取（必要时创建）全局引擎。**必须先 [init]** */
    override fun engine(): PlayerEngine {
        val ctx = appContext
            ?: error("PlaybackEngineHolder.init(context) 未被调用：平台壳必须在取引擎前注入上下文")
        return get(ctx)
    }

    /**
     * 取引擎（不存在则创建）。**必须主线程调用**。
     *
     * 与 [surfaceActive] 分开：取引擎不等于"开始显示"——
     * 内嵌位可能先取引擎做准备，真正显示时才计表面。
     *
     * 带 `Context` 的重载保留：`PlaybackSession` / `PlayerViewModel` 手里本来就有
     * 上下文，直接传比依赖全局注入更明确。
     */
    fun get(context: Context): Media3Engine {
        synchronized(this) {
            instance?.let { return it }
            val e = Media3Engine(context.applicationContext)
            instance = e
            Log.w(TAG, "engine created")
            return e
        }
    }

    /** 某处 UI 开始显示（计数 +1） */
    override fun surfaceActive() {
        synchronized(this) {
            surfaceCount++
            Log.w(TAG, "surface active, count=$surfaceCount")
        }
    }

    /**
     * 某处 UI 不再显示（计数 -1）。
     *
     * **归零时暂停播放**——这是"返回首页还有声音"的修法。
     * 不释放引擎，理由见类注释。
     */
    override fun surfaceInactive() {
        synchronized(this) {
            if (surfaceCount > 0) surfaceCount--
            Log.w(TAG, "surface inactive, count=$surfaceCount")
            if (surfaceCount == 0) {
                // 没有表面显示了 → 不该有声音留下
                try {
                    instance?.pause()
                    Log.w(TAG, "no surface left, paused")
                } catch (e: Exception) {
                    Log.w(TAG, "pause failed: ${e.message}")
                }
            }
        }
    }

    /** [PlayerEngineHost] 契约实现：当前是否已有引擎（不改变任何计数） */
    override fun peek(): PlayerEngine? = instance

    /** [PlayerEngineHost] 契约实现：当前表面计数（排障用） */
    override fun surfaceCount(): Int = surfaceCount

    /** 强制释放（进程退出、清缓存）。忽略计数。**必须主线程** */
    override fun forceRelease() {
        synchronized(this) {
            surfaceCount = 0
            val e = instance ?: return
            instance = null
            try {
                e.release()
                Log.w(TAG, "engine force-released")
            } catch (ex: Exception) {
                Log.w(TAG, "force release failed: ${ex.message}")
            }
        }
    }
}
