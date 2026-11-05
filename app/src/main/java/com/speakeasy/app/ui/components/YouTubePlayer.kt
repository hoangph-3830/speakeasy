package com.speakeasy.app.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.speakeasy.app.data.Videos

// YouTube's own embedded player in a WebView: the video is streamed by YouTube, never stored by the app.
// YouTube requires embeds to send a referrer, so the page gets an https origin named after the app.
private const val ORIGIN = "https://com.speakeasy.app"

private fun playerPage(id: String) = """
    <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
    <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
    iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style></head>
    <body><iframe id="player" src="${Videos.embedUrl(id)}&origin=$ORIGIN"
    allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen
    referrerpolicy="strict-origin-when-cross-origin"></iframe></body></html>
""".trimIndent()

/** Asks the embedded player to pause, through the iframe API's postMessage commands. */
private const val PAUSE_JS =
    "document.getElementById('player').contentWindow.postMessage('{\"event\":\"command\",\"func\":\"pauseVideo\",\"args\":\"\"}','*')"

/**
 * Plays YouTube video [id]. Paused when the app goes to the background, torn down when the screen
 * closes; its fullscreen button covers the whole window until back or the button again.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(id: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var fullscreen by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }

    val exitFullscreen: () -> Unit = {
        fullscreen?.let { (view, callback) ->
            (view.parent as? ViewGroup)?.removeView(view)
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            fullscreen = null
            callback.onCustomViewHidden()
        }
    }

    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                // Links inside the player ("Watch on YouTube", the logo) open the YouTube app or browser.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    val window = activity?.window
                    val decor = window?.decorView as? ViewGroup
                    if (window == null || decor == null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    view.setBackgroundColor(android.graphics.Color.BLACK)
                    decor.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    WindowCompat.getInsetsController(window, decor).apply {
                        hide(WindowInsetsCompat.Type.systemBars())
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                    fullscreen = view to callback
                }

                override fun onHideCustomView() = exitFullscreen()

                // Without a poster WebView draws a grey placeholder over the player before it starts.
                override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
    }

    BackHandler(enabled = fullscreen != null) { exitFullscreen() }

    LaunchedEffect(webView, id) { webView.loadDataWithBaseURL("$ORIGIN/", playerPage(id), "text/html", "utf-8", null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(webView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView.evaluateJavascript(PAUSE_JS, null)
                    webView.onPause()
                }
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            exitFullscreen()
            // Stops the sound at once, then frees the player.
            webView.loadUrl("about:blank")
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Opens the video in the YouTube app, or the browser when it is not installed. */
fun openOnYouTube(context: Context, id: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Videos.watchUrl(id)))) }
}
