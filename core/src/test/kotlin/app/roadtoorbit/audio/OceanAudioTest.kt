package app.roadtoorbit.audio

import app.roadtoorbit.audio.MusicSynth.Track
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Spectral analysis for the audio tests: a mean power spectrum by FFT and the numbers derived from it. */
internal object Spectrum {
    fun toDoubles(pcm: ShortArray) = DoubleArray(pcm.size) { pcm[it].toDouble() }

    /** Mean power of the bins 0..size/2 over Hann-windowed frames (half overlapping) of x[from, to). */
    fun power(x: DoubleArray, from: Int, to: Int, size: Int = 1024): DoubleArray {
        val acc = DoubleArray(size / 2 + 1)
        var s = from
        while (s + size <= to) {
            val re = DoubleArray(size) { x[s + it] * (0.5 - 0.5 * cos(2.0 * PI * it / size)) }
            val im = DoubleArray(size)
            fft(re, im)
            for (k in acc.indices) acc[k] += re[k] * re[k] + im[k] * im[k]
            s += size / 2
        }
        return acc
    }

    private fun hz(k: Int, bins: Int) = k * SoundSynth.SAMPLE_RATE.toDouble() / (2.0 * (bins - 1))

    fun centroid(p: DoubleArray): Double {
        var num = 0.0
        var den = 0.0
        for (k in p.indices) { num += hz(k, p.size) * p[k]; den += p[k] }
        return num / den
    }

    /** Share of the power between [lo] and [hi] Hz. */
    fun share(p: DoubleArray, lo: Double, hi: Double): Double {
        var part = 0.0
        var all = 0.0
        for (k in p.indices) { all += p[k]; if (hz(k, p.size) in lo..hi) part += p[k] }
        return part / all
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val a = re[i]; re[i] = re[j]; re[j] = a; val b = im[i]; im[i] = im[j]; im[j] = b }
        }
        var len = 2
        while (len <= n) {
            val wr = cos(-2.0 * PI / len)
            val wi = sin(-2.0 * PI / len)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val vr = re[b] * cr - im[b] * ci
                    val vi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                    val t = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = t
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** The ocean level's music loops and its two sounds: the splash and the submarine's engine. */
class OceanAudioTest {
    private val ocean = listOf(Track.OCEAN_PLANE, Track.OCEAN_SUB, Track.OCEAN_CAR, Track.OCEAN_FINALE)
    private val rate = SoundSynth.SAMPLE_RATE

    private companion object {
        val rendered: Map<Track, ShortArray> by lazy { Track.values().associateWith { MusicSynth.render(it) } }
    }

    @Test fun theOceanLoopsRenderTheSameSamplesEveryTime() {
        for (t in ocean) assertTrue(MusicSynth.render(t).contentEquals(rendered.getValue(t)), "$t differs between two renders")
    }

    @Test fun theOceanLoopsLoopSeamlesslyUnclippedAndWithoutGaps() {
        for (t in ocean) {
            val pcm = rendered.getValue(t)
            var peak = 0
            var energy = 0.0
            var stepEnergy = 0.0
            for (i in pcm.indices) {
                val v = pcm[i].toInt()
                peak = maxOf(peak, abs(v))
                energy += v.toDouble() * v
                if (i > 0) { val d = (v - pcm[i - 1]).toDouble(); stepEnergy += d * d }
            }
            val rms = sqrt(energy / pcm.size)
            val rmsStep = sqrt(stepEnergy / (pcm.size - 1))
            assertTrue(pcm.none { abs(it.toInt()) >= 32767 }, "$t clips")
            assertTrue(peak in 28000..30000, "$t peak=$peak: normalised like the older loops")
            assertTrue(rms in 2600.0..11600.0, "$t rms=$rms is outside the loudness of the older loops")
            val seam = abs(pcm[0].toInt() - pcm[pcm.size - 1].toInt())
            assertTrue(seam <= 2.5 * rmsStep + 100, "$t: the jump across the loop point is $seam, a typical step is $rmsStep")
            // no dead air: nearly every 100 ms window is clearly audible
            val win = rate / 10
            var quiet = 0
            var windows = 0
            for (s in 0 until pcm.size - win step win) {
                var e = 0.0
                for (i in s until s + win) e += pcm[i].toDouble() * pcm[i]
                if (sqrt(e / win) < rms * 0.1) quiet++
                windows++
            }
            assertTrue(quiet <= windows / 50, "$t has $quiet near-silent windows of $windows")
        }
    }

    @Test fun theOceanLoopsAreNotAnyOtherTrackAndRunAtTheirOwnTempo() {
        for (t in ocean) for (o in Track.values()) {
            if (o == t) continue
            val a = rendered.getValue(t)
            val b = rendered.getValue(o)
            assertTrue(a.size != b.size, "$t and $o have the same length, so the same tempo")
            assertFalse(a.contentEquals(b), "$t is the same loop as $o")
        }
    }

    @Test fun theOceanLoopsHaveClearlyDifferentBrightness() {
        val centroid = ocean.associateWith {
            val x = Spectrum.toDoubles(rendered.getValue(it))
            Spectrum.centroid(Spectrum.power(x, 0, x.size, 2048))
        }
        println("ocean spectral centroids: " + centroid.entries.joinToString { "${it.key}=${it.value.toInt()} Hz" })
        for (a in ocean) for (b in ocean) if (a.ordinal < b.ordinal) {
            val lo = minOf(centroid.getValue(a), centroid.getValue(b))
            val hi = maxOf(centroid.getValue(a), centroid.getValue(b))
            assertTrue(hi >= lo * 1.2 && hi - lo >= 100.0, "$a and $b sound too alike: ${lo.toInt()} and ${hi.toInt()} Hz")
        }
    }

    @Test fun theSplashIsAOneShotSeaplaneSplash() {
        val pcm = SoundSynth.render(SoundId.SPLASH)
        val seconds = pcm.size.toFloat() / rate
        assertTrue(seconds in 0.9f..1.2f, "splash lasts $seconds s")
        assertFalse(SoundSynth.looping(SoundId.SPLASH))
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue(peak in 30500..31500, "peak=$peak: normalised to the peak of the loud effects")
        assertFalse(pcm.contentEquals(SoundSynth.render(SoundId.TOUCHDOWN)), "the placeholder is gone")
        fun energy(from: Float, to: Float): Double {
            var e = 0.0
            for (i in (from * rate).toInt() until minOf(pcm.size, (to * rate).toInt())) e += pcm[i].toDouble() * pcm[i]
            return e
        }
        val total = energy(0f, 2f)
        assertTrue(energy(0f, 0.35f) > 0.6 * total, "the impact comes first")
        assertTrue(energy(0.4f, 2f) in 0.003 * total..0.25 * total, "a bubbly tail follows the impact")
        assertTrue(pcm.takeLast(rate / 25).all { abs(it.toInt()) < peak / 30 }, "the splash fades out")
        // the falling low-pass: much more of the early sound is bright than of the sound a third of a second later
        val x = Spectrum.toDoubles(pcm)
        val early = Spectrum.share(Spectrum.power(x, 0, rate / 8), 1500.0, 11025.0)
        val later = Spectrum.share(Spectrum.power(x, rate * 3 / 10, rate * 3 / 5), 1500.0, 11025.0)
        println("splash: share of power above 1.5 kHz is $early in the first 125 ms and $later at 0.3-0.6 s")
        assertTrue(early > 2 * later, "the low-pass does not fall: $early vs $later")
    }

    /** What a sample player does at playback [speed]: reads the looping buffer with linear interpolation. */
    private fun playAt(loop: ShortArray, speed: Float, samples: Int): DoubleArray {
        var pos = 0.0
        return DoubleArray(samples) {
            val i = pos.toInt()
            val f = pos - i
            val v = loop[i % loop.size] * (1 - f) + loop[(i + 1) % loop.size] * f
            pos = (pos + speed) % loop.size
            v
        }
    }

    @Test fun theSubmarineEngineIsAMuffledGurglingHumAtEverySpeed() {
        val loop = SoundSynth.render(SoundId.ENGINE_SUB)
        assertTrue(SoundSynth.looping(SoundId.ENGINE_SUB))
        assertTrue(loop.size == rate * 2, "the engine loops are two seconds long")
        assertFalse(loop.contentEquals(SoundSynth.render(SoundId.ENGINE_ROCKET)), "the placeholder is gone")
        for (speed in listOf(0.5f, 1f, 2f)) {
            val x = playAt(loop, speed, rate * 2)
            val p = Spectrum.power(x, 0, x.size, 2048)
            val centroid = Spectrum.centroid(p)
            val bright = Spectrum.share(p, 3000.0, 11025.0)
            val rms = sqrt(x.sumOf { it * it } / x.size)
            println("engine at ${speed}x: centroid ${centroid.toInt()} Hz, ${(bright * 1e4).toInt() / 100.0}% above 3 kHz, rms ${rms.toInt()}")
            assertTrue(centroid in 40.0..700.0, "at ${speed}x the hum is not a low hum: centroid $centroid Hz")
            assertTrue(bright < 0.005, "at ${speed}x the hum is not muffled: $bright of the power is above 3 kHz")
            assertTrue(rms > 2500.0, "at ${speed}x the hum is nearly silent")
        }
        // gurgling: the level moves (a throb and bubbles) instead of sitting still
        val win = rate / 50
        val levels = (0 until loop.size - win step win).map { s -> sqrt((s until s + win).sumOf { loop[it].toDouble() * loop[it] } / win) }
        assertTrue(levels.max() > 1.3 * levels.min(), "the level only moves between ${levels.min()} and ${levels.max()}")
    }
}
