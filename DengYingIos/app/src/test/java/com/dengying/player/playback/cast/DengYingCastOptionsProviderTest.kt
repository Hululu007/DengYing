package com.dengying.player.playback.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `DengYingCastOptionsProvider` 的构造与选项口径（FR-418）。
 *
 * ## 为什么主要测 [DengYingCastOptionsProvider.Companion.descriptor]
 *
 * `CastOptions` 是 GMS 的 Parcelable；`src/test` 里 GMS 类是 stub
 * （`unitTests.isReturnDefaultValues = true`），直接调 `getCastOptions()`
 * 会把静态常量读成 null，测出来的不是本类的行为。故把「选哪个接收器、
 * 开哪些开关」抽成纯数据 [DengYingCastOptionsProvider.Descriptor]，由这里断言；
 * `getCastOptions()` 只做一次无分支映射，由 `:app:assembleDebug` 的
 * 编译与 Manifest 合并保证其类型正确。
 *
 * 这几条同时锁住 PR #6 的整改要点：Manifest 登记的不再是悬空类名，
 * 类名与实际类、与默认接收器 ID 必须对得上。
 */
class DengYingCastOptionsProviderTest {

    @Test
    fun `Manifest 登记的类名指向真实存在的类`() {
        val expected = "com.dengying.player.playback.cast.DengYingCastOptionsProvider"
        assertEquals(expected, DengYingCastOptionsProvider.CLASS_NAME)
        // 能被 Class.forName 拿到，才证明「悬空引用」已消除；反射实例化不触发 GMS 初始化。
        val clazz = Class.forName(DengYingCastOptionsProvider.CLASS_NAME)
        assertNotNull(clazz)
        val provider = clazz.getDeclaredConstructor().newInstance()
        assertTrue(provider is com.google.android.gms.cast.framework.OptionsProvider)
    }

    @Test
    fun `无参构造可用`() {
        // 反射实例化不触发 GMS 初始化；Provider 由 CastContext 反射构造，故必须有无参构造。
        val provider = DengYingCastOptionsProvider()
        assertNotNull(provider)
    }

    @Test
    fun `默认媒体接收器 ID 与 Cast SDK 公开常量一致`() {
        // GMS 文档中 DEFAULT_MEDIA_RECEIVER_APPLICATION_ID 的值（javap -constants 核对 23.0.0）。
        assertEquals("CC1AD845", DengYingCastOptionsProvider.RECEIVER_APPLICATION_ID)
        assertEquals(
            DengYingCastOptionsProvider.RECEIVER_APPLICATION_ID,
            DengYingCastOptionsProvider.descriptor.receiverApplicationId,
        )
    }

    @Test
    fun `断开会话时停止接收器应用`() {
        // FR-422：不停止的话电视会一直停在投屏界面耗电。
        assertTrue(DengYingCastOptionsProvider.descriptor.stopReceiverOnEnd)
    }

    @Test
    fun `不自动恢复上次会话且不启用通知栏媒体会话`() {
        val d = DengYingCastOptionsProvider.descriptor
        assertFalse(d.resumeSavedSession)
        assertFalse(d.mediaSessionEnabled)
    }
}
