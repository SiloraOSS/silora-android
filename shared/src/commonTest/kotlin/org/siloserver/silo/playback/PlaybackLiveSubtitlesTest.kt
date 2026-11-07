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
}
