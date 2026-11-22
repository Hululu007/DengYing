package com.dengying.player.playback.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/**
 * Google Cast 框架的选项提供者（FR-418..423 的真实入口）。
 *
 * ## 为什么这个类必须存在
 *
 * `AndroidManifest.xml` 用 `com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`
 * 登记本类的全名。`CastContext.getSharedInstance()` 在进程启动时会**反射实例化**它；
 * 类不存在时框架抛 `IllegalStateException`，投屏通道从根上不可用。
 * PR #6 之前 Manifest 声明的就是这个名字，但类从未落地，只能靠
 * `tools:ignore="MissingClass"` 掩盖——本类把这条悬空引用补成真实实现。
 *
 * ## 为什么用默认媒体接收器
 *
 * 灯影不建内容库、不上报、不部署自定义 Cast Receiver（`SEC-001`）。
 * `DEFAULT_MEDIA_RECEIVER_APPLICATION_ID` 是 Google 提供的通用媒体接收器，
 * 接收端拿到 [com.dengying.player.playback.cast.CastMedia.url] 后自己拉流，
 * 与本应用「把媒体地址交给接收端」的语义一致，也不引入自建云端服务。
 *
 * 口径变化：接入 GMS Cast 框架后，设备发现/会话管理由 Google Play 服务承担，
 * 媒体地址会离开本机交给局域网内的接收端；这**不是**「不接入任何云端服务」，
 * 但应用自身仍不建服务端、不上传用户数据。见 `docs/01`。
 *
 * ## 可测性
 *
 * `CastOptions` 是 GMS 的 Parcelable，纯 JVM 单测（无 Robolectric）构造它依赖
 * GMS stub 行为。故把「选哪个接收器 / 开哪些开关」抽成纯常量 [descriptor]，
 * 由 `:app:testDebugUnitTest` 直接断言；[getCastOptions] 只做一次无分支映射。
 */
class DengYingCastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        // 生产路径用 GMS 常量；纯 JVM 单测只断言 [descriptor]，不执行本方法，
        // 避免 GMS stub（unitTests.isReturnDefaultValues）把静态常量读成 null。
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setCastMediaOptions(
            CastMediaOptions.Builder()
                // 不注册 MediaIntentReceiver / ExpandedControllerActivity：
                // 播放页自己就是控制器，通知栏与锁屏控条不在本移植的验收范围。
                .setMediaSessionEnabled(false)
                .build(),
        )
        // 断开会话时停止接收器应用：否则电视会一直停在投屏界面耗电（FR-422）。
        .setStopReceiverApplicationWhenEndingSession(true)
        // 不自动恢复上次会话：用户重新进入播放页应明确重新选择设备，
        // 避免"打开 App 就悄悄投到上次那台电视"。
        .setResumeSavedSession(false)
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()

    /** 可在纯 JVM 单测里断言的选项口径（不触碰 GMS 类）。 */
    data class Descriptor(
        val receiverApplicationId: String,
        val stopReceiverOnEnd: Boolean,
        val resumeSavedSession: Boolean,
        val mediaSessionEnabled: Boolean,
    )

    companion object {
        /**
         * Google 默认媒体接收器的公开 ID（`CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID`）。
         *
         * 写成字面量而不是直接引用 GMS 常量，是为了让 [descriptor] 能在纯 JVM 单测里
         * 被断言：GMS 类在 `src/test` 里是 stub，静态常量会返回 null。
         * 生产路径 [getCastOptions] 仍使用 GMS 常量，二者由
         * `DengYingCastOptionsProviderTest` 的口径说明与 GMS 文档共同保证一致。
         */
        const val RECEIVER_APPLICATION_ID: String = "CC1AD845"

        /** Manifest 登记的 value，必须与本类全名一致。 */
        const val CLASS_NAME: String =
            "com.dengying.player.playback.cast.DengYingCastOptionsProvider"

        val descriptor: Descriptor = Descriptor(
            receiverApplicationId = RECEIVER_APPLICATION_ID,
            stopReceiverOnEnd = true,
            resumeSavedSession = false,
            mediaSessionEnabled = false,
        )
    }
}
