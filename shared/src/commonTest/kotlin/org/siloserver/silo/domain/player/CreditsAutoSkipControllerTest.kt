package org.siloserver.silo.domain.player

import org.siloserver.silo.model.catalog.MarkerSegment
import org.siloserver.silo.model.catalog.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreditsAutoSkipControllerTest {
    private val controller = CreditsAutoSkipController()
    private val credits = MarkerTimeline.resolve(
        segments = listOf(
            MarkerSegment("credits", 80.0, 90.0),
            MarkerSegment("credits", 95.0, 100.0),
        ),
        intro = null, credits = null, recap = null, preview = null,
    )

    private fun target(
        position: Double = 82.0,
        mode: CreditsSkipMode = CreditsSkipMode.Auto,
        playing: Boolean = true,
        key: String? = "session:file",
        duration: Double = 100.0,
        markers: MarkerTimeline = credits,
    ): Double? = controller.seekTarget(key, position, duration, markers, mode, playing)

    @Test
    fun `off and ask leave credits untouched and choosing auto during credits skips`() {
        assertNull(target(mode = CreditsSkipMode.Off))
        assertNull(target(mode = CreditsSkipMode.Ask))
        assertEquals(90.0, target())
    }

    @Test
    fun `inactive playback does not consume a skip`() {
        // Callers gate pauses, loads, scrubs, handoffs and Watch Party playback.
        assertNull(target(playing = false))
        assertEquals(90.0, target())
    }

    @Test
    fun `skips each credits segment without skipping the scene between them`() {
        assertNull(target(position = 79.0))
        assertEquals(90.0, target(position = 80.0))
        assertNull(target(position = 90.0))
        assertNull(target(position = 92.0))
        assertEquals(100.0, target(position = 95.0))
        assertNull(target(position = 100.0))
    }

    @Test
    fun `stale positions and rewinds never repeat a skip even after toggling`() {
        assertEquals(90.0, target())
        assertNull(target())
        assertNull(target(position = 92.0))
        assertNull(target(position = 81.0))
        assertNull(target(mode = CreditsSkipMode.Off))
        assertNull(target())
    }

    @Test
    fun `a different file or session can skip the same timestamps`() {
        assertEquals(90.0, target())
        assertEquals(90.0, target(key = "session:other-file"))
        assertEquals(90.0, target(key = "other-session:other-file"))
    }

    @Test
    fun `reset allows a new playback of the same file`() {
        assertEquals(90.0, target())
        controller.reset()
        assertEquals(90.0, target())
    }

    @Test
    fun `no active session or known runtime does not seek`() {
        assertNull(target(key = null))
        assertNull(target(duration = 0.0))
        assertNull(target(duration = Double.NaN))
        assertNull(target(duration = Double.POSITIVE_INFINITY))
        assertEquals(90.0, target())
    }

    @Test
    fun `credits ending beyond runtime seek only to the file end`() {
        assertEquals(85.0, target(duration = 85.0))
        assertNull(target(position = 86.0, duration = 85.0, key = "other-session:file"))
    }

    @Test
    fun `missing credits and other marker kinds never skip`() {
        assertNull(target(markers = MarkerTimeline.EMPTY))
        val recapOnly = MarkerTimeline.resolve(
            segments = null, intro = null, credits = null,
            recap = TimeRange(80.0, 90.0), preview = null,
        )
        assertNull(target(markers = recapOnly))
        assertNull(target(position = Double.NaN))
        assertEquals(90.0, target())
    }
}
