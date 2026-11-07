package org.siloserver.silo.domain.player

import org.siloserver.silo.model.catalog.TimeRange

/** Skips each credits occurrence once per playback, leaving scenes between credits intact. */
class CreditsAutoSkipController {
    private var playbackKey: String? = null
    private val skipped = mutableSetOf<TimeRange>()

    /**
     * Call with one playback snapshot. [playbackActive] must exclude pauses,
     * loading, content handoffs and Watch Party playback, where local automatic
     * seeks would fight the room's transport.
     */
    fun seekTarget(
        key: String?,
        position: Double,
        duration: Double,
        markers: MarkerTimeline,
        mode: CreditsSkipMode,
        playbackActive: Boolean,
    ): Double? {
        if (key != playbackKey) {
            reset()
            playbackKey = key
        }
        if (key == null || mode != CreditsSkipMode.Auto || !playbackActive || !duration.isFinite() || duration <= 0) return null
        val range = markers.active(MarkerKind.Credits, position) ?: return null
        val target = range.end.coerceAtMost(duration)
        if (target <= position || !skipped.add(range)) return null
        // Mark before seeking: stale engine positions and rewinds must not
        // repeatedly skip the same credits, so the viewer can watch them again.
        return target
    }

    fun reset() {
        playbackKey = null
        skipped.clear()
    }
}
