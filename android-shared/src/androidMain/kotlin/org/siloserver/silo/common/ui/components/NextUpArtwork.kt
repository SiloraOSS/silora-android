package org.siloserver.silo.common.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.siloserver.silo.common.cards.LocalEpisodeSpoilerPrefs
import org.siloserver.silo.model.catalog.ArtworkRef

/** Artwork in place of the finished video's preview, shared by phone and TV. */
@Composable
fun NextUpArtwork(
    stillUrl: String?,
    stillThumbhash: String?,
    stillIsEpisodeStill: Boolean?,
    fallbackArtwork: ArtworkRef?,
    isEpisode: Boolean,
    isUnwatched: Boolean,
    modifier: Modifier = Modifier,
) {
    val hidden = isEpisode && LocalEpisodeSpoilerPrefs.current.hidesImage(isUnwatched, stillIsEpisodeStill)
    var stillFailed by remember(stillUrl) { mutableStateOf(false) }
    val showStill = !hidden && !stillFailed && !stillUrl.isNullOrBlank()
    // Discard the previous painter when spoiler preferences change rather than
    // letting an already loaded still remain visible during an image transition.
    key(stillUrl, showStill, hidden, fallbackArtwork?.url) {
        ThumbhashImage(
            url = if (showStill) stillUrl else fallbackArtwork?.url,
            thumbhash = if (showStill) stillThumbhash else fallbackArtwork?.thumbhash,
            contentDescription = null,
            modifier = modifier,
            crossfadeMillis = 0,
            onError = { if (showStill) stillFailed = true },
            defaultArtwork = if (isEpisode) DefaultArtworkKind.Tv else DefaultArtworkKind.Video,
        )
    }
}
