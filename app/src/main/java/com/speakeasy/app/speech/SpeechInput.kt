package com.speakeasy.app.speech

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Launches the system speech recognizer in English and returns the best transcript.
 * Using the recognizer intent avoids asking for the microphone permission ourselves.
 */
@Composable
fun rememberSpeechInput(onResult: (String) -> Unit): (prompt: String) -> Unit =
    rememberSpeechAlternatives(onCancel = {}) { results -> onResult(results.first()) }

/**
 * Like [rememberSpeechInput] but returns every alternative the recognizer considered, best first.
 * Pronunciation drills use this: if the target word is among the guesses it was said clearly enough.
 */
@Composable
fun rememberSpeechAlternatives(onCancel: () -> Unit, onResult: (List<String>) -> Unit): (prompt: String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val texts = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty().filter { it.isNotBlank() }
        if (result.resultCode == Activity.RESULT_OK && texts.isNotEmpty()) onResult(texts) else onCancel()
    }
    return remember(launcher) {
        { prompt: String ->
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "Máy chưa có nhận diện giọng nói. Hãy cài ứng dụng Google để dùng tính năng này.",
                    Toast.LENGTH_LONG,
                ).show()
                onCancel()
            }
        }
    }
}
