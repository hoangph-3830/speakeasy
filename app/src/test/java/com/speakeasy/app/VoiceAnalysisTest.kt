package com.speakeasy.app

import com.speakeasy.app.speech.Intonation
import com.speakeasy.app.speech.PitchTrend
import com.speakeasy.app.speech.VoiceAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class VoiceAnalysisTest {

    private val rate = VoiceAnalysis.RATE

    private fun sine(hz: Double, seconds: Double = 1.0) =
        ShortArray((rate * seconds).toInt()) { (sin(2 * PI * hz * it / rate) * 12000).toInt().toShort() }

    @Test
    fun detectsSteadyPitch() {
        for (hz in listOf(150.0, 220.0)) {
            val pitch = VoiceAnalysis.pitch(sine(hz))
            val voiced = pitch.filterNotNull()
            assertEquals(pitch.size, voiced.size)
            assertTrue("$hz → $voiced", voiced.all { abs(it - hz) <= 5 })
        }
    }

    @Test
    fun silenceIsUnvoiced() {
        val samples = sine(150.0, 0.5) + ShortArray(rate / 2)
        val pitch = VoiceAnalysis.pitch(samples)
        assertTrue(pitch.takeLast(5).all { it == null })
    }

    @Test
    fun risingSweepReportsRising() {
        // 120 → 250 Hz over 1.5 s with a continuous phase.
        var phase = 0.0
        val n = (rate * 1.5).toInt()
        val samples = ShortArray(n) {
            phase += 2 * PI * (120 + 130.0 * it / n) / rate
            (sin(phase) * 12000).toInt().toShort()
        }
        assertEquals(PitchTrend.RISING, VoiceAnalysis.trend(VoiceAnalysis.pitch(samples)))
        assertEquals(PitchTrend.FALLING, VoiceAnalysis.trend(VoiceAnalysis.pitch(samples.reversedArray())))
    }

    @Test
    fun rmsAndWavHeader() {
        val samples = sine(200.0, 0.1)
        assertEquals(5, VoiceAnalysis.rms(samples).size)
        val wav = VoiceAnalysis.wav(samples)
        assertEquals(44 + samples.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("data", String(wav, 36, 4))
    }

    @Test
    fun intonationHints() {
        assertEquals(Intonation.WH_QUESTION, VoiceAnalysis.intonation("Where is the station?"))
        assertEquals(Intonation.WH_QUESTION, VoiceAnalysis.intonation("What's your name?"))
        assertEquals(Intonation.YES_NO, VoiceAnalysis.intonation("Do you have a table for two?"))
        assertEquals(Intonation.STATEMENT, VoiceAnalysis.intonation("How lovely."))
        assertEquals("↗ Câu hỏi Yes/No: lên giọng ở cuối", Intonation.YES_NO.hint)
        assertEquals("Cuối câu bạn lên giọng ✓", VoiceAnalysis.trendFeedback(PitchTrend.RISING, Intonation.YES_NO))
        assertEquals("Cuối câu nên hạ giọng", VoiceAnalysis.trendFeedback(PitchTrend.RISING, Intonation.STATEMENT))
    }

    @Test
    fun paceFeedback() {
        assertEquals(2.0, VoiceAnalysis.modelSeconds("one two three four five", 1f), 1e-9)
        assertEquals("Tốc độ ổn 👍", VoiceAnalysis.paceFeedback(2.2, 2.0))
        assertEquals("Hơi nhanh, nói rõ từng từ nhé", VoiceAnalysis.paceFeedback(1.0, 2.0))
        assertTrue(VoiceAnalysis.paceFeedback(3.0, 2.0).startsWith("Bạn nói chậm"))
    }
}
