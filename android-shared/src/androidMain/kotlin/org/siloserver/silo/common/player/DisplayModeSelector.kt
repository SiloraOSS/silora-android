package org.siloserver.silo.common.player

import kotlin.math.abs

/** HDMI mode selection with independent resolution and refresh-rate preferences. */
internal object DisplayModeSelector {
    data class Mode(val id: Int, val width: Int, val height: Int, val refreshRate: Float) {
        val pixels: Long get() = width.toLong() * height
    }

    fun pick(
        modes: List<Mode>,
        original: Mode,
        videoWidth: Int,
        videoHeight: Int,
        frameRateHz: Float,
        matchFrameRate: Boolean,
        matchResolution: Boolean,
    ): Mode? {
        if (!matchFrameRate && !matchResolution) return original
        val targetRate = if (matchFrameRate && frameRateHz.isFinite() && frameRateHz > 0f) {
            frameRateHz
        } else {
            original.refreshRate
        }
        // An unchecked option is a constraint, never a preference that scoring
        // can override. Always use the mode captured BEFORE playback switches.
        var candidates = modes.filter {
            (matchResolution || (it.width == original.width && it.height == original.height)) &&
                (matchFrameRate || abs(it.refreshRate - original.refreshRate) < 0.01f)
        }
        if (matchResolution) {
            if (videoWidth <= 0 || videoHeight <= 0) return null
            // Cropped widescreen frames (1920x816, 3840x1600) still belong in
            // their 1080p/2160p output modes. Use the smallest mode containing
            // the frame; if none can contain it, keep the largest available.
            val fitting = candidates.filter { it.width >= videoWidth && it.height >= videoHeight }
            val pixels = fitting.minOfOrNull { it.pixels } ?: candidates.maxOfOrNull { it.pixels }
            candidates = candidates.filter { it.pixels == pixels }
        }
        return RefreshRateSelector.pickByRefreshRateAmong(
            candidates = candidates,
            frameRateHz = targetRate,
            refreshRate = { it.refreshRate },
            tieBreak = compareByDescending<Mode> { it.width * 9 == it.height * 16 }
                .thenByDescending { it.pixels }
                .thenBy { if (it.id == original.id) 0 else 1 }
                .thenBy { it.id },
        )
    }
}
