package org.siloserver.silo.common.player

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Window
import android.view.WindowManager
import androidx.annotation.MainThread

/**
 * TV-only HDMI switching. Resolution and frame-rate matching are independent;
 * disabled dimensions retain the mode captured by [attach] before playback.
 * [restore] returns to that mode when playback closes or both options are off.
 * HDR negotiation follows the decoder's `Format.colorInfo`.
 */
class HdrDisplayController {

    companion object {
        private const val TAG = "HdrDisplayController"
    }

    private var window: Window? = null
    private var originalMode: DisplayModeSelector.Mode? = null
    private var lastAppliedSignature: String? = null

    @MainThread
    fun attach(activity: Activity) {
        window = activity.window
        // activity.display requires API 30+; fall back to the deprecated
        // getter below API 30.
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }
        originalMode = display?.mode?.toSelectionMode()
    }

    @MainThread
    fun applyForMedia(
        videoWidth: Int,
        videoHeight: Int,
        frameRateHz: Float,
        matchFrameRate: Boolean,
        matchResolution: Boolean,
    ) {
        val w = window ?: return
        if (!matchFrameRate && !matchResolution) {
            restore()
            return
        }
        val original = originalMode ?: return
        val display = defaultDisplay(w) ?: return
        val supported = display.supportedModes
        if (supported.isEmpty()) return

        val signature = "${videoWidth}x${videoHeight}@$frameRateHz:$matchFrameRate:$matchResolution"
        if (signature == lastAppliedSignature) return

        val best = DisplayModeSelector.pick(
            modes = supported.map { it.toSelectionMode() },
            original = original,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            frameRateHz = frameRateHz,
            matchFrameRate = matchFrameRate,
            matchResolution = matchResolution,
        ) ?: return
        val params = w.attributes
        if (params.preferredDisplayModeId == best.id) {
            lastAppliedSignature = signature
            return
        }
        params.preferredDisplayModeId = best.id
        w.attributes = params
        lastAppliedSignature = signature
        Log.i(
            TAG,
            "Applied display mode ${best.id}: ${best.width}x${best.height}@${best.refreshRate} for content $signature",
        )
    }

    @MainThread
    fun restore() {
        val w = window ?: return
        val params = w.attributes
        if (lastAppliedSignature == null) return
        val original = originalMode ?: return
        params.preferredDisplayModeId = original.id
        w.attributes = params
        lastAppliedSignature = null
        Log.i(TAG, "Restored display mode ${original.id}")
    }

    private fun Display.Mode.toSelectionMode() = DisplayModeSelector.Mode(
        id = modeId,
        width = physicalWidth,
        height = physicalHeight,
        refreshRate = refreshRate,
    )

    private fun defaultDisplay(window: Window): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.context.display
        } else {
            @Suppress("DEPRECATION")
            (window.context.getSystemService(WindowManager::class.java))?.defaultDisplay
        }
    }
}
