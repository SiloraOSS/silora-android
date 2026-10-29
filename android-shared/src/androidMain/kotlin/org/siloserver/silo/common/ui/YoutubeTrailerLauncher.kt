package org.siloserver.silo.common.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.siloserver.silo.common.player.TvModeDetector
import org.siloserver.silo.model.catalog.ItemVideo

private const val YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv"

/**
 * Plays a remote trailer in Silo's own player ([YoutubeTrailerActivity]),
 * which needs no YouTube account and so skips YouTube's profile picker. Falls
 * back to [openYoutubeTrailerExternally] when that player can't start.
 */
fun openYoutubeTrailer(context: Context, video: ItemVideo) {
    if (!YoutubeTrailerActivity.start(context, video.siteKey)) {
        openYoutubeTrailerExternally(context, video.siteKey)
    }
}

/**
 * Hands a remote trailer to the installed YouTube app, falling back to the
 * public watch page in a browser when no app accepts the deep link.
 *
 * YouTube for Android TV opens a `vnd.youtube:` link on its home screen
 * without starting the video, so TVs give that app the watch page link
 * first. Without it, TVs use the same order as phones.
 */
fun openYoutubeTrailerExternally(context: Context, videoId: String) {
    val watchPage = Uri.parse("https://www.youtube.com/watch?v=$videoId")
    val intents = buildList {
        if (TvModeDetector.isTv(context)) {
            add(Intent(Intent.ACTION_VIEW, watchPage).setPackage(YOUTUBE_TV_PACKAGE))
        }
        add(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")))
        add(Intent(Intent.ACTION_VIEW, watchPage))
    }
    val opened = intents.any { intent ->
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.isSuccess
    }
    if (!opened) {
        Toast.makeText(context, "No app is available to open this trailer", Toast.LENGTH_SHORT).show()
    }
}
