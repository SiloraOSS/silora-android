package org.siloserver.silo.updates

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class SiloraPlaybackStatus { IDLE, PLAYING, UNKNOWN }

fun interface SiloraPlaybackReader {
    suspend fun read(): SiloraPlaybackStatus
}

internal suspend fun SiloraPlaybackReader.automaticInstallBlock(
    method: InstallMethod,
    readSettings: () -> UpdateSettings,
    canUseStandard: () -> Boolean,
): UpdateStatus? {
    val initial = readSettings()
    val playbackBlocked = initial.waitUntilPlaybackStops && read() != SiloraPlaybackStatus.IDLE
    // The user can change update preferences while the media session is connecting.
    val current = if (initial.waitUntilPlaybackStops) readSettings() else initial
    return when {
        !current.automatic || !current.interval.enabled || current.method != method -> UpdateStatus.AVAILABLE
        method == InstallMethod.STANDARD && !canUseStandard() -> UpdateStatus.NEEDS_INSTALL_PERMISSION
        current.waitUntilPlaybackStops && playbackBlocked -> UpdateStatus.WAITING_FOR_PLAYBACK
        else -> null
    }
}

/** Read the actual player state, including buffering with the intent to play. */
@androidx.annotation.OptIn(UnstableApi::class)
class SiloraMediaSession(context: Context) : SiloraPlaybackReader {
    private val context = context.applicationContext

    override suspend fun read(): SiloraPlaybackStatus = withContext(Dispatchers.Main.immediate) {
        try {
            withTimeoutOrNull(5_000) {
                val token = SessionToken(context, ComponentName(context.packageName, SERVICE_CLASS))
                val future = MediaController.Builder(context, token).buildAsync()
                try {
                    val controller = suspendCancellableCoroutine<MediaController> { continuation ->
                        future.addListener({
                            if (continuation.isActive) {
                                try {
                                    continuation.resume(future.get())
                                } catch (error: Exception) {
                                    continuation.resumeWithException(error)
                                }
                            }
                        }, { it.run() })
                    }
                    if (!controller.isConnected) SiloraPlaybackStatus.UNKNOWN
                    else status(controller.playbackState, controller.playWhenReady)
                } finally {
                    // Also releases a controller that connects after a timeout/cancellation.
                    MediaController.releaseFuture(future)
                }
            } ?: SiloraPlaybackStatus.UNKNOWN
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Missing/old service, rejected connection or dead process is not proof of idle.
            SiloraPlaybackStatus.UNKNOWN
        }
    }

    companion object {
        const val SERVICE_CLASS = "org.siloserver.silo.common.player.SiloPlaybackService"

        fun status(playbackState: Int, playWhenReady: Boolean): SiloraPlaybackStatus =
            if (playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING))
                SiloraPlaybackStatus.PLAYING
            else SiloraPlaybackStatus.IDLE
    }
}
