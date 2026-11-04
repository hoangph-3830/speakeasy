package com.speakeasy.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.speakeasy.app.data.AiSettings
import com.speakeasy.app.data.ContentRepository
import com.speakeasy.app.data.ProgressStore
import com.speakeasy.app.data.ReviewStore
import com.speakeasy.app.speech.Speaker
import com.speakeasy.app.ui.AppNav
import com.speakeasy.app.ui.theme.SpeakEasyTheme

/** App-wide singletons; small enough that no DI framework is needed. */
class AppContainer(app: Application) {
    val content = ContentRepository(app)
    val progress = ProgressStore(app)
    val review = ReviewStore(app)
    val ai = AiSettings(app)
    val speaker = Speaker(app).also {
        it.rate = progress.progress.value.speechRate
        it.accent = progress.progress.value.accent
    }
}

val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

class SpeakEasyApp : Application() {
    val container by lazy { AppContainer(this) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SpeakEasyApp).container
        setContent {
            SpeakEasyTheme {
                CompositionLocalProvider(LocalApp provides container) {
                    AppNav()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as SpeakEasyApp).container.speaker.stop()
    }
}
