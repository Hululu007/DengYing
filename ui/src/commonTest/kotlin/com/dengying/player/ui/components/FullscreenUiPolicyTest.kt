package com.dengying.player.ui.components

import com.dengying.player.ui.platform.VideoZoomMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FullscreenUiPolicyTest {

    @Test
    fun `multi episode shows name and episode`() {
        assertEquals("庆余年 第3集", fullscreenTitleText(" 庆余年 ", " 第3集 ", 40, false))
    }

    @Test
    fun `single episode and blank episode omit episode`() {
        assertEquals("庆余年", fullscreenTitleText("庆余年", "第1集", 1, false))
        assertEquals("庆余年", fullscreenTitleText("庆余年", "", 40, false))
    }

    @Test
    fun `live shows channel name only`() {
        assertEquals("湖南卫视", fullscreenTitleText("湖南卫视", "直播", 1, true))
    }

    @Test
    fun `zoom label stays in sync`() {
        assertEquals("保留比例", zoomLabel(VideoZoomMode.FIT))
        assertEquals("裁剪铺满", zoomLabel(VideoZoomMode.COVER))
        assertEquals("拉伸铺满", zoomLabel(VideoZoomMode.FILL))
    }

    @Test
    fun `dropdowns are mutually exclusive`() {
        assertEquals(true to false, toggleZoomSheet(false, false))
        assertEquals(false to false, toggleZoomSheet(true, false))
        assertEquals(false to true, toggleSpeedSheet(false, true))
        assertEquals(false to false, toggleSpeedSheet(true, true))
        // 互斥：打开一个必须关掉另一个
        val (zoom, speed) = toggleZoomSheet(false, true)
        assertTrue(zoom)
        assertFalse(speed)
    }

    @Test
    fun `hide timer waits for idle playback without dropdown`() {
        assertTrue(shouldAutoHideControls(true, false, false, false, false, false))
        assertFalse(shouldAutoHideControls(true, false, false, false, false, true))
        assertFalse(shouldAutoHideControls(true, true, false, false, false, false))
        assertFalse(shouldAutoHideControls(false, false, false, false, false, false))
    }

    @Test
    fun `tap with a sheet open also hides the controls`() {
        val zoom = resolveSurfaceTap(zoomOpen = true, speedOpen = false)
        assertFalse(zoom.zoomOpen)
        assertFalse(zoom.speedOpen)
        // docs/17 §9.1：关下拉的同时收起上下栏，不是"保持原样"
        assertEquals(ControlsAction.Hide, zoom.controls)

        val speed = resolveSurfaceTap(zoomOpen = false, speedOpen = true)
        assertFalse(speed.zoomOpen)
        assertFalse(speed.speedOpen)
        assertEquals(ControlsAction.Hide, speed.controls)
    }

    @Test
    fun `tap with both sheets open closes both and hides controls`() {
        val both = resolveSurfaceTap(zoomOpen = true, speedOpen = true)
        assertFalse(both.zoomOpen)
        assertFalse(both.speedOpen)
        assertEquals(ControlsAction.Hide, both.controls)
    }

    @Test
    fun `tap without sheet keeps toggling controls`() {
        val none = resolveSurfaceTap(zoomOpen = false, speedOpen = false)
        assertFalse(none.zoomOpen)
        assertFalse(none.speedOpen)
        assertEquals(ControlsAction.Toggle, none.controls)
    }

    @Test
    fun `live badge only for live`() {
        assertTrue(liveBadgeVisible(true))
        assertFalse(liveBadgeVisible(false))
    }

    @Test
    fun `poster fallback is stable and never pure gray`() {
        assertEquals('庆', posterFallbackChar(" 庆余年 "))
        assertNull(posterFallbackChar("  "))
        val first = posterFallbackColors("庆余年")
        assertEquals(first, posterFallbackColors("庆余年"))
        assertTrue(first.first != 0xFF808080L)
        // 【不透明】Compose 的 Color(Long) 取低 32 位；alpha 必须落在 24..31 位，
        // 否则兜底渐变会变全透明，卡片显示成白板（2026-11-21 真机回归）。
        assertEquals(0xFFL, (first.first ushr 24) and 0xFFL)
        assertEquals(0xFFL, (first.second ushr 24) and 0xFFL)
        assertTrue(first.first != first.second)
    }
}
