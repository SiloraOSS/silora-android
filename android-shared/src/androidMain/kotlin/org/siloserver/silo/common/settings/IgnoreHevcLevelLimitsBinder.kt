package org.siloserver.silo.common.settings

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import org.siloserver.silo.common.player.MediaCodecCapabilitiesProbe

/**
 * Mirrors the active profile's HEVC level-limit preference into
 * [MediaCodecCapabilitiesProbe], the single source every capability report
 * (playback, downloads, diagnostics) reads, so no call site needs the setting
 * threaded through it.
 */
object IgnoreHevcLevelLimitsBinder {
    fun start(scope: CoroutineScope, store: PlayerSettingsStore) {
        scope.launch {
            store.ignoreHevcLevelLimitsFlow
                // Resubscribe after a transient settings read failure so later
                // preference and profile changes still reach the probe.
                .retryWhen { cause, attempt ->
                    if (cause !is IOException) return@retryWhen false
                    val retryDelayMs = minOf(30_000L, 1_000L shl attempt.coerceAtMost(5).toInt())
                    Log.w(TAG, "HEVC level-limit setting read failed: ${cause::class.simpleName}")
                    delay(retryDelayMs)
                    true
                }
                .distinctUntilChanged()
                .collect { enabled -> MediaCodecCapabilitiesProbe.ignoreHevcLevelLimits = enabled }
        }
    }

    private const val TAG = "IgnoreHevcLevelLimits"
}
