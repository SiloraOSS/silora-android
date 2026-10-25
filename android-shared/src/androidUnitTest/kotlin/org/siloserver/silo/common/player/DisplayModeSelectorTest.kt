package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayModeSelectorTest {
    private val original = DisplayModeSelector.Mode(17, 3840, 2160, 59.94f)
    // Match the ordering advertised by the onn 4K Pro: lower resolutions first.
    private val modes = listOf(
        DisplayModeSelector.Mode(1, 1280, 720, 23.976f),
        DisplayModeSelector.Mode(5, 1280, 720, 59.94f),
        DisplayModeSelector.Mode(7, 1920, 1080, 23.976f),
        DisplayModeSelector.Mode(8, 1920, 1080, 24f),
        DisplayModeSelector.Mode(11, 1920, 1080, 59.94f),
        DisplayModeSelector.Mode(13, 3840, 2160, 23.976f),
        DisplayModeSelector.Mode(14, 3840, 2160, 24f),
        original,
    )

    private fun pick(
        rate: Boolean,
        resolution: Boolean,
        width: Int = 1920,
        height: Int = 816,
        fps: Float = 23.976f,
        supported: List<DisplayModeSelector.Mode> = modes,
    ) = DisplayModeSelector.pick(supported, original, width, height, fps, rate, resolution)

    @Test
    fun `both disabled retain original 4K 59_94 mode`() {
        assertEquals(original, pick(rate = false, resolution = false))
    }

    @Test
    fun `frame rate alone keeps 4K for cropped 1080p content`() {
        assertEquals(13, pick(rate = true, resolution = false)?.id)
    }

    @Test
    fun `resolution alone selects 1080p without changing 59_94 refresh`() {
        assertEquals(11, pick(rate = false, resolution = true)?.id)
    }

    @Test
    fun `both enabled select 1080p 23_976 for cropped 1080p content`() {
        assertEquals(7, pick(rate = true, resolution = true)?.id)
    }

    @Test
    fun `cropped 4K content stays 4K with both enabled`() {
        assertEquals(13, pick(rate = true, resolution = true, width = 3840, height = 1600)?.id)
    }

    @Test
    fun `720p content uses 720p only with resolution matching enabled`() {
        assertEquals(1, pick(rate = true, resolution = true, width = 1280, height = 536)?.id)
        assertEquals(13, pick(rate = true, resolution = false, width = 1280, height = 536)?.id)
    }

    @Test
    fun `24 and 23_976 content select their respective 4K rates`() {
        assertEquals(14, pick(rate = true, resolution = false, fps = 24f)?.id)
        assertEquals(13, pick(rate = true, resolution = false, fps = 23.976f)?.id)
    }

    @Test
    fun `missing 4K film rate never allows resolution fallback`() {
        assertEquals(original, pick(
            rate = true, resolution = false,
            supported = modes.filter { it.width != 3840 || it.id == original.id },
        ))
    }

    @Test
    fun `resolution alone never allows refresh fallback`() {
        assertEquals(original, pick(
            rate = false, resolution = true,
            supported = modes.filter { it.width != 1920 || it.refreshRate == 24f }
                .filter { it.width != 1280 },
        ))
    }

    @Test
    fun `integer multiple at 4K beats a closer rate with judder`() {
        val rate120 = DisplayModeSelector.Mode(20, 3840, 2160, 120f)
        assertEquals(rate120, pick(
            rate = true, resolution = false, fps = 24f,
            supported = listOf(original, rate120),
        ))
    }

    @Test
    fun `missing frame rate still permits resolution-only matching`() {
        for (fps in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(11, pick(rate = false, resolution = true, fps = fps)?.id)
            assertEquals(original, pick(rate = true, resolution = false, fps = fps))
        }
    }

    @Test
    fun `unknown video dimensions wait before changing resolution`() {
        assertNull(pick(rate = true, resolution = true, width = 0, height = 0))
    }

    @Test
    fun `oversized content uses largest supported output`() {
        assertEquals(13, pick(rate = true, resolution = true, width = 7680, height = 4320)?.id)
    }
}
