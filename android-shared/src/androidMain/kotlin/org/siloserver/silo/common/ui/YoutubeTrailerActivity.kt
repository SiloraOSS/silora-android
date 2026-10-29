package org.siloserver.silo.common.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Plays a YouTube trailer inside Silo with YouTube's embedded player.
 *
 * Handing a trailer to the YouTube app can strand the viewer on YouTube's
 * "Who's watching?" profile picker, which drops the video. The embedded
 * player needs no account, so the trailer starts straight away.
 *
 * Remote: OK / Play-Pause toggles, Left / Right seek 10 seconds, Back closes.
 * The activity closes itself when the trailer ends. If the owner blocks
 * embedding, the video is unavailable, or the player doesn't start within
 * [LOAD_TIMEOUT_MS] of the page loading, it hands the trailer to the YouTube
 * app instead. An error after the trailer has started just closes the
 * player: handing off mid-trailer would bring back YouTube's profile picker.
 */
class YoutubeTrailerActivity : Activity() {

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var handedOff = false
    private var startedPlaying = false
    private lateinit var videoId: String

    private val loadTimeout = Runnable { if (!ready) handOffToYoutubeApp() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.takeIf { VIDEO_ID.matches(it) } ?: run {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        // A device without a usable WebView provider throws here.
        val view = runCatching { WebView(this) }.getOrElse {
            handOffToYoutubeApp()
            return
        }
        webView = view
        view.setBackgroundColor(Color.BLACK)
        view.isFocusable = false
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        view.webChromeClient = WebChromeClient()
        view.webViewClient = object : WebViewClient() {
            // Keep the player in place: links inside it (the YouTube logo,
            // end-screen suggestions) must not navigate this view away.
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest,
            ): Boolean = request.isForMainFrame

            // Count the start-up allowance from when the page has loaded, not
            // from onCreate, so a slow connection isn't mistaken for a broken
            // player and handed off while the embed is about to start.
            override fun onPageFinished(view: WebView, url: String?) {
                if (!ready) {
                    handler.removeCallbacks(loadTimeout)
                    handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS)
                }
            }
        }
        view.addJavascriptInterface(Bridge(), "SiloTrailer")
        setContentView(view)
        // YouTube's embed refuses players without an HTTPS origin and referrer
        // (error 152/153), so the page is given one.
        view.loadDataWithBaseURL(EMBED_ORIGIN, playerHtml(videoId), "text/html", "utf-8", null)
        // Backstop in case the page itself never finishes loading.
        handler.postDelayed(loadTimeout, PAGE_LOAD_TIMEOUT_MS)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val command = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_SPACE -> "siloToggle()"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "player && player.playVideo()"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "player && player.pauseVideo()"
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "siloSeek(10)"
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> "siloSeek(-10)"
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                finish()
                return true
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        webView?.evaluateJavascript(command, null)
        return true
    }

    override fun onPause() {
        super.onPause()
        webView?.evaluateJavascript("siloPauseForBackground()", null)
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        webView?.evaluateJavascript("siloResumeFromBackground()", null)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView?.apply {
            removeJavascriptInterface("SiloTrailer")
            loadUrl("about:blank")
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun handOffToYoutubeApp() {
        if (handedOff || isFinishing) return
        handedOff = true
        openYoutubeTrailerExternally(this, videoId)
        finish()
    }

    /** Called by the player page on a WebView thread; hops to the main thread. */
    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            handler.post {
                ready = true
                handler.removeCallbacks(loadTimeout)
            }
        }

        @JavascriptInterface
        fun onEnded() {
            handler.post { finish() }
        }

        /** Embedding blocked (101/150), unavailable (100), or a player error. */
        @JavascriptInterface
        fun onError(@Suppress("UNUSED_PARAMETER") code: Int) {
            handler.post { if (startedPlaying) finish() else handOffToYoutubeApp() }
        }

        @JavascriptInterface
        fun onPlaying() {
            handler.post { startedPlaying = true }
        }
    }

    companion object {
        private const val EXTRA_VIDEO_ID = "org.siloserver.silo.extra.YOUTUBE_VIDEO_ID"
        private const val EMBED_ORIGIN = "https://www.siloserver.org"
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val PAGE_LOAD_TIMEOUT_MS = 45_000L
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{6,20}$")

        /** False when [videoId] isn't a YouTube video ID this player accepts. */
        fun start(context: Context, videoId: String): Boolean {
            if (!VIDEO_ID.matches(videoId)) return false
            val intent = Intent(context, YoutubeTrailerActivity::class.java)
                .putExtra(EXTRA_VIDEO_ID, videoId)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(intent) }.isSuccess
        }

        private fun playerHtml(videoId: String): String = """
            <!doctype html>
            <html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
            #player{position:fixed;inset:0;width:100%;height:100%}</style>
            </head><body>
            <div id="player"></div>
            <script>
              var player = null;
              var siloWasPlaying = false;
              function siloPauseForBackground() {
                if (!player) return;
                siloWasPlaying = player.getPlayerState() === YT.PlayerState.PLAYING;
                player.pauseVideo();
              }
              function siloResumeFromBackground() {
                if (player && siloWasPlaying) player.playVideo();
                siloWasPlaying = false;
              }
              function siloToggle() {
                if (!player) return;
                if (player.getPlayerState() === YT.PlayerState.PLAYING) player.pauseVideo();
                else player.playVideo();
              }
              function siloSeek(delta) {
                if (!player) return;
                player.seekTo(Math.max(0, player.getCurrentTime() + delta), true);
              }
              function onYouTubeIframeAPIReady() {
                player = new YT.Player('player', {
                  videoId: '$videoId',
                  width: '100%',
                  height: '100%',
                  playerVars: {
                    autoplay: 1, controls: 1, rel: 0, playsinline: 1,
                    fs: 0, iv_load_policy: 3, origin: '$EMBED_ORIGIN'
                  },
                  events: {
                    onReady: function (e) { SiloTrailer.onReady(); e.target.playVideo(); },
                    onStateChange: function (e) {
                      if (e.data === YT.PlayerState.PLAYING) SiloTrailer.onPlaying();
                      if (e.data === YT.PlayerState.ENDED) SiloTrailer.onEnded();
                    },
                    onError: function (e) { SiloTrailer.onError(e.data); }
                  }
                });
              }
            </script>
            <script src="https://www.youtube.com/iframe_api"></script>
            </body></html>
        """.trimIndent()
    }
}
