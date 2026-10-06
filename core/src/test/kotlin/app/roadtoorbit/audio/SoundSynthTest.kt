package app.roadtoorbit.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundSynthTest {
    @Test fun everySoundRendersAudibleBoundedPcm() {
        for (id in SoundId.values()) {
            val pcm = SoundSynth.render(id)
            assertTrue(pcm.size > SoundSynth.SAMPLE_RATE / 20, "$id too short: ${pcm.size}")
            assertTrue(pcm.size < SoundSynth.SAMPLE_RATE * 5, "$id too long: ${pcm.size}")
            var peak = 0
            var energy = 0.0
            for (s in pcm) { peak = maxOf(peak, abs(s.toInt())); energy += s.toDouble() * s }
            assertTrue(peak in 3000..32767, "$id peak=$peak")
            assertTrue(energy / pcm.size > 1e5, "$id is nearly silent")
        }
    }

    @Test fun loopsAreSeamless() {
        for (id in SoundId.values().filter { SoundSynth.looping(it) }) {
            val pcm = SoundSynth.render(id)
            assertEquals(SoundSynth.SAMPLE_RATE * 2, pcm.size, "$id loop length")
            // the sample after the end equals the first sample; the jump across the seam must be tiny
            val seam = abs(pcm[0].toInt() - pcm[pcm.size - 1].toInt())
            val typicalStep = (1 until 2000).map { abs(pcm[it].toInt() - pcm[it - 1].toInt()) }.average()
            assertTrue(seam <= typicalStep * 4 + 200, "$id seam jump $seam vs typical $typicalStep")
        }
    }

    @Test fun wavHeaderIsValid() {
        val pcm = SoundSynth.render(SoundId.COIN)
        val wav = SoundSynth.wav(pcm)
        assertEquals(44 + pcm.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals("WAVE", String(wav, 8, 4)); assertEquals("data", String(wav, 36, 4))
        val rate = (wav[24].toInt() and 0xFF) or ((wav[25].toInt() and 0xFF) shl 8) or ((wav[26].toInt() and 0xFF) shl 16)
        assertEquals(SoundSynth.SAMPLE_RATE, rate)
    }
}
