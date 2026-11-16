package org.siloserver.silo.playback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackLiveSubtitlesTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test fun decodesCuesAndDropsMalformedOnes() {
        val event = decodePlaybackLiveSubtitleEvent(
            "subtitle_translation_cues",
            obj(
                """{"session_id":"s1","file_id":7,"job_id":42,"track_key":"ai-42","done":3,"total":10,
                "cues":[{"start":10.0,"end":12.5,"text":"Zdravo"},{"start":5,"end":4,"text":"bad"},{"start":"x","end":1,"text":"bad"}]}""",
            ),
        )
        val cues = assertIs<PlaybackLiveSubtitleEvent.Cues>(event)
        assertEquals("s1", cues.sessionId)
        assertEquals(7, cues.fileId)
        assertEquals(42L, cues.jobId)
        assertEquals(listOf(LiveSubtitleCue(10.0, 12.5, "Zdravo")), cues.cues)
        assertEquals(3, cues.done)
        assertEquals(10, cues.total)
    }

    @Test fun decodesLifecycleEventsAndRejectsUnknownOrJoblessOnes() {
        assertIs<PlaybackLiveSubtitleEvent.Started>(
            decodePlaybackLiveSubtitleEvent("subtitle_translation_started", obj("""{"job_id":1,"language":"sl","total_cues":5}""")),
        )
        val done = decodePlaybackLiveSubtitleEvent("subtitle_translation_completed", obj("""{"job_id":1,"subtitle_id":9}"""))
        assertEquals(9, assertIs<PlaybackLiveSubtitleEvent.Completed>(done).subtitleId)
        assertIs<PlaybackLiveSubtitleEvent.Failed>(
            decodePlaybackLiveSubtitleEvent("subtitle_translation_failed", obj("""{"job_id":1}""")),
        )
        assertNull(decodePlaybackLiveSubtitleEvent("subtitle_translation_cues", obj("""{"cues":[]}""")))
        assertNull(decodePlaybackLiveSubtitleEvent("subtitle_ready", obj("""{"job_id":1}""")))
    }

    @Test fun mergesOutOfOrderBatchesAndFindsTheShowingLine() {
        // Playhead-first: the later region arrives before the start.
        val track = LiveSubtitleTrack(sessionId = "s1", jobId = 1, trackKey = "k")
            .plus(listOf(LiveSubtitleCue(100.0, 103.0, "later")), done = 1, total = 3)
            .plus(listOf(LiveSubtitleCue(1.0, 3.0, "first"), LiveSubtitleCue(4.0, 6.0, "second")), done = 3, total = 3)
            // A resent batch must not duplicate lines.
            .plus(listOf(LiveSubtitleCue(4.0, 6.0, "second")), done = 3, total = 3)
        assertEquals(3, track.cues.size)
        assertEquals(listOf("first"), track.textAt(2.0))
        assertEquals(listOf("second"), track.textAt(5.0))
        assertEquals(listOf("later"), track.textAt(101.0))
        assertTrue(track.textAt(3.5).isEmpty())
        assertTrue(track.textAt(0.0).isEmpty())
        assertTrue(track.textAt(200.0).isEmpty())
    }

    @Test fun stripsSubtitleMarkupFromLiveCues() {
        assertEquals("Kje si bil?", liveCueDisplayText("<i>Kje si bil?</i>"))
        assertEquals("Napis", liveCueDisplayText("{\\an8}Napis"))
        assertEquals("Red text", liveCueDisplayText("<font color=\"#ff0000\">Red</font> <b>text</b>"))
        assertEquals("Hello there", liveCueDisplayText("<v Bob><c.yellow>Hello</c> <00:00:01.500>there"))
        assertEquals("one\ntwo", liveCueDisplayText("one\\Ntwo"))
        assertEquals("a < b & c", liveCueDisplayText("a &lt; b &amp; c"))
        assertEquals("3 < 4 > 2", liveCueDisplayText("3 < 4 > 2"))
        assertEquals("", liveCueDisplayText("<i></i>"))
    }

    @Test fun decodedCuesCarryDisplayText() {
        val event = decodePlaybackLiveSubtitleEvent(
            "subtitle_translation_cues",
            obj("""{"job_id":1,"cues":[{"start":1,"end":2,"text":"<i>Zdravo</i>"}]}"""),
        )
        assertEquals(listOf(LiveSubtitleCue(1.0, 2.0, "Zdravo")), assertIs<PlaybackLiveSubtitleEvent.Cues>(event).cues)
    }

    @Test fun dismissedJobsStayDismissedUntilRestored() {
        val dismissals = LiveSubtitleDismissals(capacity = 2)
        dismissals.dismiss(1)
        assertTrue(dismissals.isDismissed(1))
        assertTrue(!dismissals.isDismissed(2))
        dismissals.restore(1)
        assertTrue(!dismissals.isDismissed(1))
        dismissals.dismiss(1)
        dismissals.dismiss(2)
        dismissals.dismiss(3)
        assertTrue(!dismissals.isDismissed(1), "oldest id drops past capacity")
        assertTrue(dismissals.isDismissed(2) && dismissals.isDismissed(3))
    }

    @Test fun coversLiveCuesAndShortPausesButNotUnfilledRegions() {
        val track = LiveSubtitleTrack(sessionId = "s", jobId = 1, trackKey = "k")
            .plus(listOf(LiveSubtitleCue(100.0, 102.0, "a"), LiveSubtitleCue(105.0, 107.0, "b")), done = 2, total = 10)
        assertTrue(track.covers(101.0))
        assertTrue(track.covers(103.5), "pause between two live cues")
        assertTrue(track.covers(97.0), "just before the first live cue")
        assertTrue(!track.covers(50.0), "region the job has not reached")
        assertTrue(!track.covers(120.0))
        assertTrue(!LiveSubtitleTrack(sessionId = "s", jobId = 1, trackKey = "k").covers(0.0))
    }
}
