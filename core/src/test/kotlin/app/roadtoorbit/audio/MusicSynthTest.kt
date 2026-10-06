package app.roadtoorbit.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class MusicSynthTest {
    @Test fun everyTrackRendersALoopableAudibleLoop() {
        for (t in MusicSynth.Track.values()) {
            val pcm = MusicSynth.render(t)
            val seconds = pcm.size.toFloat() / SoundSynth.SAMPLE_RATE
            assertTrue(seconds in 12f..24f, "$t loop is $seconds s")
            var peak = 0
            var energy = 0.0
            for (s in pcm) { peak = maxOf(peak, abs(s.toInt())); energy += s.toDouble() * s }
            assertTrue(peak in 8000..32767, "$t peak=$peak")
            assertTrue(energy / pcm.size > 3e5, "$t is nearly silent")
            // no click at the loop point: the step across the seam is like any other step
            val seam = abs(pcm[0].toInt() - pcm[pcm.size - 1].toInt())
            var maxStep = 0
            for (i in 1 until pcm.size) maxStep = maxOf(maxStep, abs(pcm[i].toInt() - pcm[i - 1].toInt()))
            assertTrue(seam <= maxStep + 1, "$t seam $seam exceeds the largest in-loop step $maxStep")
        }
    }
}
