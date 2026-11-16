package org.siloserver.silo.common.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import java.util.UUID

/**
 * Plays a YouTube trailer inside Silo with YouTube's embedded player.
 *
 * Handing a trailer to the YouTube app can strand the viewer on YouTube's
 * "Who's watching?" profile picker, which drops the video. The embedded
 * player needs no account, so the trailer starts straight away.
 *
 * Remote: OK / Play-Pause toggles, Left / Right seek 10 seconds, Back closes.
 * The activity closes itself when the trailer ends. If the owner blocks
 * embedding, the video is unavailable, the player script can't be fetched,
 * or the trailer doesn't start playing in time (for example YouTube shows a
 * sign-in panel instead), it hands the trailer to the YouTube app instead.
 * An error after the trailer has started just closes the player: handing off
 * mid-trailer would bring back YouTube's profile picker.
 *
 * The page reports to [Bridge] with a per-launch token. The bridge object is
 * visible to every frame, including YouTube's cross-origin iframe, but only
 * this activity's own page knows the token, so other frames can't drive it.
 */
class YoutubeTrailerActivity : Activity() {

    private var webView: WebView? = null
    private var spinner: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val bridgeToken = UUID.randomUUID().toString()
    private var ready = false
    private var handedOff = false
    private var startedPlaying = false
    private var resumed = false
    private var handOffWhenResumed = false

    /** The armed watchdog delay, or 0; re-armed in full after a pause. */
    private var watchdogMs = 0L
    private lateinit var videoId: String

    private val loadTimeout = Runnable {
        watchdogMs = 0L
        if (!startedPlaying) handOffToYoutubeApp()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = intent.getStringExtra(EXTRA_VIDEO_ID)?.takeIf { VIDEO_ID.matches(it) } ?: run {
            finish()
            return
        }
        setKeepScreenOn(true)
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
            allowFileAccess = false
            allowContentAccess = false
        }
        view.webChromeClient = WebChromeClient()
        view.webViewClient = object : WebViewClient() {
            // Keep the player in place: links inside it (the YouTube logo,
            // end-screen suggestions) must not navigate this view away.
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = request.isForMainFrame

            // A network that can't reach YouTube fails the player script or
            // the embed itself; hand off now instead of after the timeout.
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!startedPlaying && isPlayerResource(request)) handOffToYoutubeApp()
            }

            // Count the start-up allowance from when the page has loaded, not
            // from onCreate, so a slow connection isn't mistaken for a broken
            // player and handed off while the embed is about to start.
            override fun onPageFinished(view: WebView, url: String?) {
                if (!ready) armWatchdog(LOAD_TIMEOUT_MS)
            }
        }
        view.addJavascriptInterface(Bridge(), "SiloTrailer")
        val progress = ProgressBar(this).apply { isIndeterminate = true }
        spinner = progress
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                addView(progress, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            },
        )
        // YouTube's embed refuses players without an HTTPS origin and referrer
        // (error 152/153), so the page is given one.
        view.loadDataWithBaseURL(
            EMBED_ORIGIN,
            playerHtml(videoId, bridgeToken),
            "text/html",
            "utf-8",
            null,
        )
        // Backstop in case the page itself never finishes loading.
        armWatchdog(PAGE_LOAD_TIMEOUT_MS)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val command = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_SPACE -> {
                // A held OK key auto-repeats; toggling on each repeat would
                // leave playback in an arbitrary state.
                if (event.repeatCount > 0) return true
                "siloToggle()"
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> "siloPlay()"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "siloPause()"
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
        resumed = false
        // The watchdog only runs while the trailer is in front; otherwise it
        // could launch YouTube over whatever the viewer switched to.
        handler.removeCallbacks(loadTimeout)
        webView?.evaluateJavascript("siloPauseForBackground()", null)
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (handOffWhenResumed) {
            handOffWhenResumed = false
            handOffToYoutubeApp()
            return
        }
        if (watchdogMs > 0L) armWatchdog(watchdogMs)
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
        spinner = null
        super.onDestroy()
    }

    private fun armWatchdog(delayMs: Long) {
        watchdogMs = delayMs
        handler.removeCallbacks(loadTimeout)
        if (resumed) handler.postDelayed(loadTimeout, delayMs)
    }

    private fun disarmWatchdog() {
        watchdogMs = 0L
        handler.removeCallbacks(loadTimeout)
    }

    private fun setKeepScreenOn(on: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (on) window.addFlags(flag) else window.clearFlags(flag)
    }

    private fun handOffToYoutubeApp() {
        if (handedOff || isFinishing) return
        // Starting YouTube from the background is blocked on Android 10+ and
        // jumps over other apps on older versions; wait until the viewer is back.
        if (!resumed) {
            handOffWhenResumed = true
            return
        }
        handedOff = true
        openYoutubeTrailerExternally(this, videoId)
        finish()
    }

    private fun onPlayerState(state: Int) {
        when (state) {
            STATE_PLAYING -> {
                startedPlaying = true
                disarmWatchdog()
                spinner?.visibility = View.GONE
                setKeepScreenOn(true)
            }
            STATE_BUFFERING -> {
                setKeepScreenOn(true)
                // A slow network is still starting, not stuck.
                if (!startedPlaying) armWatchdog(START_TIMEOUT_MS)
            }
            STATE_ENDED -> finish()
            // Paused or cued: let the screensaver and screen timeout run.
            else -> setKeepScreenOn(false)
        }
    }

    /**
     * Called by the player page on a WebView thread; hops to the main thread.
     * Calls without this launch's token come from another frame and are ignored.
     */
    private inner class Bridge {
        @JavascriptInterface
        fun onReady(token: String) {
            if (token != bridgeToken) return
            handler.post {
                ready = true
                spinner?.visibility = View.GONE
                // Ready isn't playing: YouTube can still show a sign-in or
                // age panel, or refuse autoplay, without reporting an error.
                if (!startedPlaying) armWatchdog(START_TIMEOUT_MS)
            }
        }

        @JavascriptInterface
        fun onState(token: String, state: Int) {
            if (token != bridgeToken) return
            handler.post { onPlayerState(state) }
        }

        /**
         * Embedding blocked (101/150), unavailable (100), a player error, or
         * -1 when the player script failed to load.
         */
        @JavascriptInterface
        fun onError(token: String, @Suppress("UNUSED_PARAMETER") code: Int) {
            if (token != bridgeToken) return
            handler.post { if (startedPlaying) finish() else handOffToYoutubeApp() }
        }
    }

    companion object {
        private const val EXTRA_VIDEO_ID = "org.siloserver.silo.extra.YOUTUBE_VIDEO_ID"
        private const val EMBED_ORIGIN = "https://www.siloserver.org"
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val PAGE_LOAD_TIMEOUT_MS = 45_000L
        private const val START_TIMEOUT_MS = 15_000L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        // YT.PlayerState values.
        private const val STATE_ENDED = 0
        private const val STATE_PLAYING = 1
        private const val STATE_BUFFERING = 3
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{6,20}$")

        /** False when [videoId] isn't a YouTube video ID this player accepts. */
        fun start(context: Context, videoId: String): Boolean {
            if (!VIDEO_ID.matches(videoId)) return false
            val intent = Intent(context, YoutubeTrailerActivity::class.java)
                .putExtra(EXTRA_VIDEO_ID, videoId)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(intent) }.isSuccess
        }

        /** The player script, the embed page, and the scripts the embed runs on. */
        private fun isPlayerResource(request: WebResourceRequest): Boolean {
            if (request.isForMainFrame) return true
            val url = request.url
            if (url.host != "www.youtube.com") return false
            val path = url.path.orEmpty()
            return path == "/iframe_api" || path.startsWith("/embed/") || path.startsWith("/s/player/")
        }

        private fun playerHtml(videoId: String, token: String): String = """
            <!doctype html>
            <html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
            #player{position:fixed;inset:0;width:100%;height:100%}</style>
            </head><body>
            <div id="player"></div>
            <script>
              var siloToken = '$token';
              var player = null;
              // The player's methods exist only once onReady has fired.
              var siloReady = false;
              var siloInBackground = false;
              var siloWasPlaying = false;
              function siloPauseForBackground() {
                siloInBackground = true;
                if (!siloReady) { siloWasPlaying = true; return; }
                var state = player.getPlayerState();
                siloWasPlaying = state === YT.PlayerState.PLAYING ||
                  state === YT.PlayerState.BUFFERING;
                player.pauseVideo();
              }
              function siloResumeFromBackground() {
                siloInBackground = false;
                if (siloReady && siloWasPlaying) player.playVideo();
                siloWasPlaying = false;
              }
              function siloToggle() {
                if (!siloReady) return;
                if (player.getPlayerState() === YT.PlayerState.PLAYING) player.pauseVideo();
                else player.playVideo();
              }
              function siloPlay() { if (siloReady) player.playVideo(); }
              function siloPause() { if (siloReady) player.pauseVideo(); }
              function siloSeek(delta) {
                if (!siloReady) return;
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
                    onReady: function (e) {
                      siloReady = true;
                      SiloTrailer.onReady(siloToken);
                      if (siloInBackground) siloWasPlaying = true;
                      else e.target.playVideo();
                    },
                    onStateChange: function (e) {
                      if (e.data === YT.PlayerState.PLAYING && siloInBackground) {
                        siloWasPlaying = true;
                        e.target.pauseVideo();
                        return;
                      }
                      SiloTrailer.onState(siloToken, e.data);
                    },
                    onError: function (e) { SiloTrailer.onError(siloToken, e.data); }
                  }
                });
              }
            </script>
            <script src="https://www.youtube.com/iframe_api"
              onerror="SiloTrailer.onError(siloToken, -1)"></script>
            </body></html>
        """.trimIndent()
    }
}
