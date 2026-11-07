package org.siloserver.silo.common.player.video

import org.siloserver.silo.domain.player.MarkerTimeline
import org.siloserver.silo.domain.player.CreditsSkipMode
import org.siloserver.silo.model.catalog.MarkerSegment
import org.siloserver.silo.model.catalog.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkerSkipTargetTest {
    private fun legacy(recap: TimeRange?, credits: TimeRange?) =
        MarkerTimeline.resolve(null, intro = null, credits = credits, recap = recap, preview = null)

    @Test fun currentRangeControlsTheSeekAndDeletion() {
        val markers = legacy(TimeRange(40.0, 60.0), TimeRange(150.0, 180.0))
        assertNull(markerSkipTarget(39.9, markers))
        assertEquals(MarkerSkipTarget(ManualMarkerKind.Recap, 60.0), markerSkipTarget(40.0, markers))
        assertNull(markerSkipTarget(60.0, markers))
        assertEquals(MarkerSkipTarget(ManualMarkerKind.Credits, 180.0), markerSkipTarget(160.0, markers))
        assertNull(markerSkipTarget(160.0, legacy(TimeRange(40.0, 60.0), null)))
        assertEquals(ManualMarkerKind.Recap, markerSkipTarget(45.0, legacy(TimeRange(40.0, 60.0), TimeRange(30.0, 70.0)))?.kind)
    }

    @Test fun eachCreditsOccurrenceSkipsOnlyToItsOwnEnd() {
        val markers = MarkerTimeline.resolve(
            segments = listOf(
                MarkerSegment("recap", 20.0, 107.0),
                MarkerSegment("credits", 2975.5, 3167.4),
                MarkerSegment("credits", 3189.0, 3300.0),
            ),
            intro = null, credits = TimeRange(2975.5, 3167.4), recap = TimeRange(20.0, 107.0), preview = null,
        )
        assertEquals(MarkerSkipTarget(ManualMarkerKind.Recap, 107.0), markerSkipTarget(30.0, markers))
        assertEquals(MarkerSkipTarget(ManualMarkerKind.Credits, 3167.4), markerSkipTarget(3000.0, markers))
        assertNull(markerSkipTarget(3175.0, markers))
        assertEquals(MarkerSkipTarget(ManualMarkerKind.Credits, 3300.0), markerSkipTarget(3189.0, markers))
    }

    @Test fun creditsModesControlTheManualButtonWithoutAffectingRecaps() {
        val markers = legacy(TimeRange(10.0, 20.0), TimeRange(80.0, 100.0))
        val credits = MarkerSkipTarget(ManualMarkerKind.Credits, 100.0)
        assertEquals(credits, markerSkipTarget(85.0, markers, CreditsSkipMode.Ask))
        assertNull(markerSkipTarget(85.0, markers, CreditsSkipMode.Auto))
        assertNull(markerSkipTarget(85.0, markers, CreditsSkipMode.Off))
        // Do not flash a credits button before the persisted mode is loaded.
        assertNull(markerSkipTarget(85.0, markers, null))
        for (mode in CreditsSkipMode.entries) {
            assertEquals(MarkerSkipTarget(ManualMarkerKind.Recap, 20.0), markerSkipTarget(15.0, markers, mode))
        }
        assertEquals(credits, markerSkipTarget(85.0, markers, CreditsSkipMode.Auto, inWatchParty = true))
        assertNull(markerSkipTarget(85.0, markers, CreditsSkipMode.Off, inWatchParty = true))
    }

    @Test fun invalidRangesCannotOfferASeek() {
        for (range in listOf(TimeRange(-1.0, 10.0), TimeRange(10.0, 10.0), TimeRange(20.0, 10.0), TimeRange(0.0, Double.POSITIVE_INFINITY))) {
            assertNull(markerSkipTarget(5.0, legacy(range, range)))
        }
        assertNull(markerSkipTarget(Double.NaN, legacy(TimeRange(0.0, 10.0), null)))
    }
}
