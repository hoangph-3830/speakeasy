package com.speakeasy.app.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.sqrt

/** Records the learner's voice from the microphone (16 kHz mono PCM16) and plays it back. */
class Recorder(context: Context) {

    private val wavFile = File(context.cacheDir, "shadow.wav")

    @Volatile
    private var stopRequested = false

    /**
     * Records until [stop] is called or [maxMs] has passed, reporting the elapsed time as it goes.
     * The caller must hold RECORD_AUDIO; cancelling the caller releases the microphone.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(maxMs: Long = 10_000, onElapsed: (Long) -> Unit): ShortArray = withContext(Dispatchers.IO) {
        stopRequested = false
        val rate = VoiceAnalysis.RATE
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, rate / 2),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IOException("Không mở được micro.")
        }
        val out = ShortArray((rate * maxMs / 1000).toInt())
        val chunk = ShortArray(rate / 20)
        var n = 0
        try {
            rec.startRecording()
            while (isActive && !stopRequested && n < out.size) {
                val read = rec.read(chunk, 0, minOf(chunk.size, out.size - n))
                if (read < 0) break
                chunk.copyInto(out, n, 0, read)
                n += read
                onElapsed(n * 1000L / rate)
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        out.copyOf(n)
    }

    fun stop() {
        stopRequested = true
    }

    /** Plays [samples] through a WAV in the cache folder; returns when playback ends or is cancelled. */
    suspend fun play(samples: ShortArray) {
        withContext(Dispatchers.IO) { wavFile.writeBytes(VoiceAnalysis.wav(samples)) }
        suspendCancellableCoroutine { cont ->
            val player = MediaPlayer()
            val released = AtomicBoolean(false)
            fun finish() {
                if (released.compareAndSet(false, true)) runCatching { player.release() }
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { if (released.compareAndSet(false, true)) runCatching { player.stop(); player.release() } }
            player.setOnCompletionListener { finish() }
            player.setOnErrorListener { _, _, _ -> finish(); true }
            try {
                player.setDataSource(wavFile.path)
                player.prepare()
                player.start()
            } catch (e: Exception) {
                finish()
            }
        }
    }
}

/** Intonation a sentence calls for, from its punctuation and first word. */
enum class Intonation(val hint: String, val rises: Boolean) {
    WH_QUESTION("↘ Câu hỏi Wh-: hạ giọng ở cuối", false),
    YES_NO("↗ Câu hỏi Yes/No: lên giọng ở cuối", true),
    STATEMENT("↘ Câu kể: hạ giọng ở cuối, nhấn từ quan trọng", false),
}

enum class PitchTrend { RISING, FALLING }

/** What the shadowing screen draws and says about one recording. */
class VoiceTake(
    val seconds: Double,
    /** Loudness per 20 ms frame, 0–1. */
    val rms: FloatArray,
    /** Pitch in Hz per 40 ms frame, null where the frame is silent or unvoiced. */
    val pitch: List<Float?>,
    val trend: PitchTrend?,
)

/** Pure signal analysis for shadowing, kept free of Android so it can be unit-tested. */
object VoiceAnalysis {
    const val RATE = 16_000
    const val MIN_HZ = 70f
    const val MAX_HZ = 400f

    private val whWords = setOf("what", "where", "when", "why", "who", "which", "how")

    fun intonation(sentence: String): Intonation {
        val text = sentence.trim()
        if (!text.endsWith("?")) return Intonation.STATEMENT
        val first = Regex("[a-z]+").find(text.lowercase())?.value
        return if (first in whWords) Intonation.WH_QUESTION else Intonation.YES_NO
    }

    /** A 44-byte RIFF header followed by the samples, little-endian. */
    fun wav(samples: ShortArray, rate: Int = RATE): ByteArray {
        val data = samples.size * 2
        val buf = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(data)
        samples.forEach { buf.putShort(it) }
        return buf.array()
    }

    /** Root-mean-square loudness of each [frameMs] frame, scaled so full volume is 1. */
    fun rms(samples: ShortArray, rate: Int = RATE, frameMs: Int = 20): FloatArray {
        val size = rate * frameMs / 1000
        return FloatArray(samples.size / size) { f ->
            var sum = 0.0
            for (i in f * size until (f + 1) * size) {
                val x = samples[i] / 32768.0
                sum += x * x
            }
            sqrt(sum / size).toFloat()
        }
    }

    /**
     * Pitch of each [frameMs] frame by normalized autocorrelation between [MIN_HZ] and [MAX_HZ].
     * Quiet frames and frames without a clear period (breath, s, f) are null.
     */
    fun pitch(samples: ShortArray, rate: Int = RATE, frameMs: Int = 40): List<Float?> {
        val size = rate * frameMs / 1000
        val minLag = (rate / MAX_HZ).toInt()
        val maxLag = minOf((rate / MIN_HZ).toInt(), size - size / 4)
        val frames = samples.size / size
        val loudness = rms(samples, rate, frameMs)
        val floor = maxOf(0.01f, (loudness.maxOrNull() ?: 0f) * 0.15f)
        return (0 until frames).map { f ->
            if (loudness[f] < floor) return@map null
            val x = DoubleArray(size) { samples[f * size + it] / 32768.0 }
            val mean = x.average()
            for (i in x.indices) x[i] -= mean
            val r = DoubleArray(maxLag + 2)
            for (lag in minLag - 1..maxLag + 1) {
                var xy = 0.0
                var xx = 0.0
                var yy = 0.0
                for (i in 0 until size - lag) {
                    xy += x[i] * x[i + lag]
                    xx += x[i] * x[i]
                    yy += x[i + lag] * x[i + lag]
                }
                r[lag] = if (xx > 0 && yy > 0) xy / sqrt(xx * yy) else 0.0
            }
            val best = (minLag..maxLag).maxOf { r[it] }
            if (best < 0.5) return@map null
            // The first strong peak, so a period's multiples (an octave down) do not win.
            val lag = (minLag..maxLag).firstOrNull { r[it] >= best * 0.9 && r[it] >= r[it - 1] && r[it] >= r[it + 1] }
                ?: (minLag..maxLag).maxBy { r[it] }
            // Parabolic interpolation between neighbouring lags for sub-sample accuracy.
            val (a, b, c) = Triple(r[lag - 1], r[lag], r[lag + 1])
            val denom = a - 2 * b + c
            val shift = if (denom != 0.0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
            (rate / (lag + shift)).toFloat()
        }
    }

    /** Whether the voice ends higher or lower: the last 30% of voiced frames against the middle ones. */
    fun trend(pitch: List<Float?>): PitchTrend? {
        val voiced = pitch.filterNotNull()
        if (voiced.size < 5) return null
        val tail = ceil(voiced.size * 0.3).toInt()
        val start = (voiced.size * 0.3).toInt()
        val middle = voiced.subList(start, voiced.size - tail).ifEmpty { voiced.subList(0, voiced.size - tail) }
        return if (voiced.takeLast(tail).average() > middle.average()) PitchTrend.RISING else PitchTrend.FALLING
    }

    fun analyze(samples: ShortArray, rate: Int = RATE): VoiceTake {
        val pitch = pitch(samples, rate)
        return VoiceTake(samples.size.toDouble() / rate, rms(samples, rate), pitch, trend(pitch))
    }

    /** Rough length of the model sentence: 2.5 words a second at normal speed. */
    fun modelSeconds(sentence: String, speechRate: Float): Double {
        val words = sentence.split(Regex("\\s+")).count { it.isNotBlank() }
        return words / (2.5 * speechRate)
    }

    fun paceFeedback(learnerSeconds: Double, modelSeconds: Double): String {
        val ratio = learnerSeconds / modelSeconds
        return when {
            ratio > 1.4 -> "Bạn nói chậm hơn người bản xứ khá nhiều, thử nói liền mạch hơn"
            ratio < 0.7 -> "Hơi nhanh, nói rõ từng từ nhé"
            else -> "Tốc độ ổn 👍"
        }
    }

    fun trendFeedback(trend: PitchTrend, intonation: Intonation): String = when {
        trend == PitchTrend.RISING && intonation.rises -> "Cuối câu bạn lên giọng ✓"
        trend == PitchTrend.FALLING && !intonation.rises -> "Cuối câu bạn hạ giọng ✓"
        intonation.rises -> "Cuối câu nên lên giọng"
        else -> "Cuối câu nên hạ giọng"
    }
}
