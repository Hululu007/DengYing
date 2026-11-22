package com.dengying.player.playback.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.dengying.player.domain.playback.CastUtil
import com.dengying.player.playback.cast.CastController
import com.dengying.player.playback.cast.CastDevice
import com.dengying.player.playback.cast.CastEvent
import com.dengying.player.playback.cast.CastMedia
import com.dengying.player.playback.cast.DisconnectCause
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient

/**
 * 基于 Google Cast 框架的真实投屏通道（Android 侧，FR-418..423）。
 *
 * ## 为什么用 MediaRouter 而不是 CastButtonFactory 的对话框
 *
 * `CastButtonFactory` 提供的 `MediaRouteButton` / `MediaRouteDialogFactory` 依赖
 * AppCompat 主题与 FragmentActivity，而本应用是单 Activity + 纯 Compose、
 * 主题是 `Theme.DengYing`（非 AppCompat）。故这里用**程序化选择**：
 * 自己注册 [MediaRouter.Callback] 拿设备列表，用户点设备后调
 * [MediaRouter.selectRoute] —— 这一步会让 Cast 框架建立 [CastSession]，
 * 随后由 [SessionManagerListener] 把生命周期事件回给 ViewModel。
 * 界面仍是 `PlayerScreen` 里的 Compose 设备选择面板，无 Android View 混入。
 *
 * ## 失败分流
 *
 * 所有失败都经 [CastUtil.classifyCastFailure] 分类，由 ViewModel 映射文案：
 *  - 没有活动会话就下发 → [CastUtil.Stage.Link] / `hasAddress=true` → 设备不可达；
 *  - `RemoteMediaClient.load` 返回失败 → [CastUtil.Stage.Remote] → 远端拉流失败；
 *  - 会话被系统回收 / 接收端离线 → [DisconnectCause.LinkLost]，回落本机播放。
 *
 * ## 线程
 *
 * MediaRouter / Cast SDK 的回调都在主线程；这里用 [mainHandler] 把所有
 * [onDevices] / [onState] 回调统一 post 回主线程再交给 ViewModel，避免
 * ViewModel 在任意线程写 `mutableStateOf`。
 */
class GmsCastController(private val context: Context) : CastController {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** null 表示 GMS 不可用（无 Play 服务 / 未登记 Provider），投屏入口据此禁用。 */
    private val castContext: CastContext? = runCatching {
        CastContext.getSharedInstance(context.applicationContext)
    }.getOrNull()

    private val selector: MediaRouteSelector? = castContext?.let {
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    DengYingCastOptionsProvider.RECEIVER_APPLICATION_ID,
                ),
            )
            .build()
    }

    private val router: MediaRouter? = runCatching {
        MediaRouter.getInstance(context.applicationContext)
    }.getOrNull()

    @Volatile
    private var onDevices: ((List<CastDevice>) -> Unit)? = null

    @Volatile
    private var onState: ((CastEvent) -> Unit)? = null

    @Volatile
    private var discovering = false

    /** 用户主动断开时置 true，用于把 onSessionEnded 区分为「静默回落」。 */
    @Volatile
    private var userEnding = false

    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = publishRoutes()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = publishRoutes()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = publishRoutes()
        override fun onRouteSelected(
            router: MediaRouter,
            route: MediaRouter.RouteInfo,
            reason: Int,
            requestedRoute: MediaRouter.RouteInfo,
        ) = publishRoutes()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) =
            publishRoutes()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            emit(CastEvent.Connected)
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            emit(CastEvent.Failed(CastUtil.Stage.Link, hasAddress = true))
        }
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) {
            val cause = if (userEnding) DisconnectCause.UserInitiated else DisconnectCause.LinkLost
            userEnding = false
            emit(CastEvent.Disconnected(cause))
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            emit(CastEvent.Connected)
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            emit(CastEvent.Failed(CastUtil.Stage.Link, hasAddress = true))
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            emit(CastEvent.Disconnected(DisconnectCause.LinkLost))
        }
    }

    override fun isAvailable(): Boolean =
        castContext != null && selector != null && router != null

    override fun startDiscovery(
        onDevices: (List<CastDevice>) -> Unit,
        onState: (CastEvent) -> Unit,
    ) {
        this.onDevices = onDevices
        this.onState = onState
        val cc = castContext
        val sel = selector
        val mr = router
        if (cc == null || sel == null || mr == null) {
            // GMS 不可用：投屏入口虽被 isAvailable() 禁用，仍要给一次明确失败分流，
            // 避免用户从别处触发点击后静默无反应。
            emit(CastEvent.Failed(CastUtil.Stage.Precheck, hasAddress = true))
            return
        }
        if (discovering) return
        discovering = true
        mainHandler.post {
            cc.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
            mr.addCallback(sel, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            publishRoutes()
        }
    }

    override fun stopDiscovery() {
        if (!discovering) return
        discovering = false
        val cc = castContext
        val mr = router
        mainHandler.post {
            runCatching { mr?.removeCallback(routeCallback) }
            runCatching { cc?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java) }
        }
    }

    override fun selectDevice(deviceId: String): Boolean {
        val cc = castContext
        val mr = router
        val sel = selector
        if (cc == null || mr == null || sel == null) return false
        val route = mr.routes.firstOrNull { it.id == deviceId && it.matchesSelector(sel) }
            ?: return false
        mainHandler.post { mr.selectRoute(route) }
        return true
    }

    override fun load(media: CastMedia): Boolean {
        val client: RemoteMediaClient = castContext
            ?.sessionManager
            ?.currentCastSession
            ?.remoteMediaClient
            ?: return false
        if (!CastUtil.isCastableUrl(media.url)) {
            emit(CastEvent.Failed(CastUtil.Stage.Precheck, hasAddress = false))
            return false
        }
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, media.title)
            if (media.subtitle.isNotEmpty()) putString(MediaMetadata.KEY_SUBTITLE, media.subtitle)
        }
        val info = MediaInfo.Builder(media.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(media.contentType.ifEmpty { guessContentType(media.url) })
            .setMetadata(metadata)
            .build()
        val options = MediaLoadOptions.Builder()
            .setAutoplay(true)
            .setPlayPosition(media.startPositionMs)
            .build()
        mainHandler.post {
            val result = client.load(info, options)
            result.setResultCallback { channelResult ->
                val status = channelResult.status
                if (status.isSuccess) {
                    emit(CastEvent.HandoffStarted)
                } else {
                    emit(CastEvent.Failed(CastUtil.Stage.Remote, hasAddress = true))
                }
            }
        }
        return true
    }

    override fun endSession() {
        userEnding = true
        val cc = castContext
        if (cc == null) {
            // 没有 GMS 就没有会话可结束；必须立刻给一次「已断开」，否则调用方
            // 会一直停在「正在断开投屏…」且 castConnected 永不复位。
            userEnding = false
            emit(CastEvent.Disconnected(DisconnectCause.UserInitiated))
            return
        }
        mainHandler.post { cc.sessionManager.endCurrentSession(true) }
    }

    override fun release() {
        stopDiscovery()
        onDevices = null
        onState = null
    }

    // ------------------------------------------------------------------ 内部

    private fun publishRoutes() {
        val mr = router ?: return
        val sel = selector ?: return
        val defaultId = mr.defaultRoute?.id
        val devices = mr.routes
            .asSequence()
            .filter { it.id != defaultId && it.matchesSelector(sel) }
            .map { CastDevice(id = it.id, name = it.name?.toString().orEmpty().ifEmpty { "投屏设备" }) }
            .toList()
        post { onDevices?.invoke(devices) }
    }

    private fun emit(event: CastEvent) = post { onState?.invoke(event) }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun guessContentType(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.contains(".m3u8") -> "application/x-mpegURL"
            path.contains(".mpd") -> "application/dash+xml"
            path.contains(".mp4") -> "video/mp4"
            path.contains(".mkv") -> "video/x-matroska"
            else -> "video/*"
        }
    }
}
