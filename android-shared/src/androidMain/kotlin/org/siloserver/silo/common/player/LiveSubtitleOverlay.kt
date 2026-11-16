package org.siloserver.silo.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.siloserver.silo.common.R
import org.siloserver.silo.model.settings.SubtitleAppearance
import org.siloserver.silo.model.settings.SubtitleBackgroundStylePreset
import org.siloserver.silo.model.settings.SubtitleFontSizePreset
import org.siloserver.silo.model.settings.SubtitlePositionPreset
import org.siloserver.silo.model.settings.pointSize
import org.siloserver.silo.playback.LiveSubtitleTrack

private const val LIVE_SUBTITLE_TICK_MS = 120L

/** Fork: how the live overlay is sized on one device. */
data class LiveSubtitleOverlayStyle(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val sidePadding: Dp,
    val bottomPadding: Dp,
    val maxWidth: Dp,
) {
    companion object {
        val Tv = LiveSubtitleOverlayStyle(26.sp, 34.sp, 48.dp, 40.dp, 1100.dp)
        val Phone = LiveSubtitleOverlayStyle(18.sp, 24.sp, 16.dp, 24.dp, 720.dp)
    }
}

/**
 * Fork: draws a running AI job's streamed lines over the picture, on phone
 * and TV.
 *
 * The server sends cues playhead-first while a translation or transcription
 * runs, long before the finished subtitle file exists. While the live lines
 * own the subtitle slot ([LiveSubtitleTrack.covers]) the caller hides
 * Media3's own subtitle view, so the viewer sees one set of lines; where the
 * job has not reached yet, or before any line has arrived, Media3's selected
 * track stays visible. [onCoveringChanged] reports which, and reports false
 * when this overlay leaves the composition (picture-in-picture, cast).
 *
 * Lines follow the viewer's subtitle [appearance] (size, colours, background
 * style, outline, top position) so they look like the finished track.
 *
 * @param positionSourceSeconds the playhead in source (file) seconds, the clock
 *   the server's cue times use.
 * @param bottomLiftPx extra lift so lines clear the transport controls.
 */
@Composable
fun LiveSubtitleOverlay(
    track: LiveSubtitleTrack,
    positionSourceSeconds: () -> Double,
    bottomLiftPx: Int,
    hidden: Boolean,
    style: LiveSubtitleOverlayStyle,
    appearance: SubtitleAppearance,
    onCoveringChanged: (Boolean) -> Unit,
) {
    val latestTrack by rememberUpdatedState(track)
    val latestPosition by rememberUpdatedState(positionSourceSeconds)
    val latestOnCoveringChanged by rememberUpdatedState(onCoveringChanged)
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var covering by remember { mutableStateOf(false) }
    // Ticks only while shown, and only while frames are drawn: the frame
    // clock stops with the activity, so a stopped player costs nothing.
    LaunchedEffect(hidden) {
        if (hidden) {
            covering = false
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameMillis { }
            val position = latestPosition()
            val next = latestTrack.textAt(position)
            if (next != lines) lines = next
            val nextCovering = latestTrack.covers(position)
            if (nextCovering != covering) covering = nextCovering
            delay(LIVE_SUBTITLE_TICK_MS)
        }
    }
    LaunchedEffect(covering) { latestOnCoveringChanged(covering) }
    DisposableEffect(Unit) {
        onDispose { latestOnCoveringChanged(false) }
    }
    if (hidden) return
    val liftDp = with(LocalDensity.current) { bottomLiftPx.toDp() }
    if (lines.isEmpty()) {
        // Nothing has arrived yet: say so in a small chip at the top, clear of
        // the subtitle slot, where the selected track keeps showing.
        if (track.cues.isNotEmpty()) return
        val waiting = stringResource(
            if (track.transcribing) R.string.silora_liveSubtitle_transcribing else R.string.silora_liveSubtitle_translating,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = style.sidePadding, end = style.sidePadding, top = style.bottomPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            BasicText(
                waiting,
                style = TextStyle(
                    color = Color.White,
                    fontSize = style.fontSize * WAITING_CHIP_SCALE,
                    lineHeight = style.lineHeight * WAITING_CHIP_SCALE,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        return
    }
    val look = remember(appearance) { liveSubtitleLook(appearance) }
    val atTop = appearance.position == SubtitlePositionPreset.Top
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = style.sidePadding,
                end = style.sidePadding,
                top = if (atTop) style.bottomPadding else 0.dp,
                bottom = if (atTop) 0.dp else style.bottomPadding + liftDp,
            ),
        contentAlignment = if (atTop) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        BasicText(
            lines.joinToString("\n"),
            style = TextStyle(
                color = look.text,
                fontSize = style.fontSize * look.scale,
                lineHeight = style.lineHeight * look.scale,
                fontFamily = look.fontFamily,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                shadow = look.shadow,
            ),
            modifier = Modifier
                .widthIn(max = style.maxWidth)
                .then(
                    if (look.box != null) {
                        Modifier.background(look.box, RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private const val WAITING_CHIP_SCALE = 0.7f

/** The viewer's subtitle appearance, translated to what the overlay draws. */
private data class LiveSubtitleLook(
    val text: Color,
    val box: Color?,
    val shadow: Shadow?,
    val fontFamily: FontFamily,
    /** Relative to the default (Large) size the overlay style is tuned for. */
    val scale: Float,
)

private fun liveSubtitleLook(raw: SubtitleAppearance): LiveSubtitleLook {
    val appearance = raw.sanitized()
    val text = hexColor(appearance.fontColor, Color.White).copy(alpha = appearance.textOpacity / 100f)
    val background = hexColor(appearance.backgroundColor, Color.Black)
        .copy(alpha = appearance.backgroundOpacity / 100f)
    val outline = hexColor(appearance.textOutlineColor, Color.Black)
    val box = background.takeIf { appearance.backgroundStyle == SubtitleBackgroundStylePreset.Box }
    val shadow = when {
        appearance.textOutline || appearance.backgroundStyle == SubtitleBackgroundStylePreset.Outline ->
            Shadow(color = outline, offset = Offset.Zero, blurRadius = 4f)
        appearance.backgroundStyle == SubtitleBackgroundStylePreset.Shadow ->
            Shadow(color = background.copy(alpha = 1f), offset = Offset(2f, 2f), blurRadius = 6f)
        else -> null
    }
    val fontFamily = when (appearance.fontFamily) {
        SubtitleAppearance.SERIF -> FontFamily.Serif
        SubtitleAppearance.MONOSPACE -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    val scale = (appearance.fontSize.pointSize / SubtitleFontSizePreset.Large.pointSize).toFloat()
    return LiveSubtitleLook(text, box, shadow, fontFamily, scale)
}

private fun hexColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")) }
        .getOrDefault(fallback)
