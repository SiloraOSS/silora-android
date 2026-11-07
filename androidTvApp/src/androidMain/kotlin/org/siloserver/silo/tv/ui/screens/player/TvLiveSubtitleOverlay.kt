package org.siloserver.silo.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.siloserver.silo.playback.LiveSubtitleTrack

private const val LIVE_SUBTITLE_TICK_MS = 120L

/**
 * Fork: draws a running AI translation's streamed lines over the picture.
 *
 * The server sends translated cues playhead-first while the job runs, long
 * before the finished subtitle file exists. Media3's own subtitle view is
 * hidden meanwhile (the caller does that), so the viewer sees one set of lines;
 * once the finished track is loaded and selected, the caller drops this
 * overlay and Media3 takes over again.
 *
 * @param positionSourceSeconds the playhead in source (file) seconds, the clock
 *   the server's cue times use.
 * @param bottomLiftPx extra lift so lines clear the transport controls.
 */
@Composable
internal fun TvLiveSubtitleOverlay(
    track: LiveSubtitleTrack,
    positionSourceSeconds: () -> Double,
    bottomLiftPx: Int,
    hidden: Boolean,
) {
    val latestTrack by rememberUpdatedState(track)
    val latestPosition by rememberUpdatedState(positionSourceSeconds)
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val next = latestTrack.textAt(latestPosition())
            if (next != lines) lines = next
            delay(LIVE_SUBTITLE_TICK_MS)
        }
    }
    if (hidden) return
    val text = when {
        lines.isNotEmpty() -> lines.joinToString("\n")
        // Nothing has arrived yet: say so instead of showing nothing.
        track.cues.isEmpty() -> "Translating subtitles…"
        else -> return
    }
    val liftDp = with(LocalDensity.current) { bottomLiftPx.toDp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, bottom = 40.dp + liftDp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                shadow = Shadow(color = Color.Black, offset = Offset(2f, 2f), blurRadius = 6f),
            ),
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
