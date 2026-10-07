package app.roadtoorbit.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Procedural background music: small step-sequenced loops (drums, bass, pads, arpeggios, bells with a
 * feedback delay) rendered to mono PCM. Notes that ring past the end of a loop wrap around to its
 * start, so every track loops without a seam. Deterministic: the same bytes every run.
 */
object MusicSynth {
    private const val RATE = SoundSynth.SAMPLE_RATE

    /** The menu loop and, per level, one for each leg and the finale: the Martian ones are in another key and mood. */
    enum class Track { MENU, CAR, PLANE, ROCKET, FINALE, MARS_CAR, MARS_PLANE, MARS_ROCKET, MARS_FINALE }

    private const val BARS = 8

    fun render(track: Track): ShortArray {
        val m = when (track) {
            Track.MENU -> menu()
            Track.CAR -> car(false)
            Track.PLANE -> plane(false)
            Track.ROCKET -> rocket(false)
            Track.FINALE -> finale(false)
            Track.MARS_CAR -> car(true)
            Track.MARS_PLANE -> plane(true)
            Track.MARS_ROCKET -> rocket(true)
            Track.MARS_FINALE -> finale(true)
        }
        return m.toPcm()
    }

    /** Loop length in samples for a tempo (4 beats per bar). */
    private fun loopSamples(bpm: Float): Int = (BARS * 4 * 60f / bpm * RATE).toInt()

    // ---- tiny synth kit ------------------------------------------------------------------------

    private enum class Wave { SINE, SAW, SQUARE, PULSE, TRI }

    private class Bus(val n: Int) {
        val buf = FloatArray(n)
        fun add(i: Int, v: Float) { buf[((i % n) + n) % n] += v }
    }

    private class Mix(val n: Int, val bpm: Float) {
        val drums = Bus(n); val bass = Bus(n); val pad = Bus(n); val lead = Bus(n); val bell = Bus(n)
        val beat = RATE * 60f / bpm
        val step = beat / 4f
        fun toPcm(): ShortArray {
            echo(lead.buf, (beat * 0.75f).toInt(), 0.42f)
            echo(bell.buf, (beat * 0.75f).toInt(), 0.5f)
            val out = FloatArray(n)
            for (i in 0 until n) out[i] = drums.buf[i] * 0.9f + bass.buf[i] + pad.buf[i] + lead.buf[i] + bell.buf[i]
            var peak = 0f
            for (v in out) peak = max(peak, kotlin.math.abs(v))
            val g = if (peak > 0f) 0.88f / peak else 1f
            return ShortArray(n) { (out[it] * g * 32767f).toInt().coerceIn(-32767, 32767).toShort() }
        }

        /** Looping feedback delay: two passes make the echo tail wrap around the loop end. */
        private fun echo(b: FloatArray, delay: Int, feedback: Float) {
            if (delay <= 0 || delay >= b.size) return
            for (pass in 0 until 2) for (i in b.indices) {
                val j = (i - delay + b.size) % b.size
                b[i] += b[j] * feedback * 0.55f
            }
        }
    }

    private fun hz(midi: Float): Float = 440f * 2f.pow((midi - 69f) / 12f)

    private fun wave(w: Wave, phase: Double): Float {
        val p = (phase - Math.floor(phase)).toFloat()
        return when (w) {
            Wave.SINE -> sin(2.0 * PI * phase).toFloat()
            Wave.SAW -> 2f * p - 1f
            Wave.SQUARE -> if (p < 0.5f) 1f else -1f
            Wave.PULSE -> if (p < 0.25f) 1.5f else -0.5f // zero-mean 25% pulse
            Wave.TRI -> 4f * kotlin.math.abs(p - 0.5f) - 1f
        }
    }

    /** One note: oscillator through a one-pole low-pass with attack / sustain / exponential release. */
    private fun note(
        bus: Bus, startSample: Int, dur: Float, freq: Float, vol: Float, w: Wave,
        attack: Float = 0.005f, release: Float = 0.08f, cutoff: Float = 20000f, detune: Float = 0f, vibrato: Float = 0f,
    ) {
        val len = ((dur + release * 4f) * RATE).toInt()
        val a = (1f - exp(-2f * PI.toFloat() * cutoff / RATE)).coerceIn(0.001f, 1f)
        var lp = 0f
        var ph1 = 0.0
        var ph2 = 0.0
        for (k in 0 until len) {
            val t = k.toFloat() / RATE
            val env = min(1f, t / attack) * (if (t < dur) 1f else exp(-(t - dur) / release))
            val f = freq * (1f + vibrato * sin(2f * PI.toFloat() * 5.2f * t))
            ph1 += f / RATE
            var x = wave(w, ph1)
            if (detune != 0f) {
                ph2 += f * (1f + detune) / RATE
                x = (x + wave(w, ph2)) * 0.5f
            }
            lp += (x - lp) * a
            bus.add(startSample + k, lp * env * vol)
        }
    }

    private class Noise(seed: Int) {
        private var s = seed * 1103515245 + 12345
        fun next(): Float { s = s * 1664525 + 1013904223; return ((s ushr 8) and 0xFFFFFF) / 8388608f - 1f }
    }

    private fun kick(bus: Bus, at: Int, vol: Float = 0.85f) {
        val len = (0.22f * RATE).toInt()
        for (k in 0 until len) {
            val t = k.toFloat() / RATE
            val phase = 2.0 * PI * (42.0 * t + 120.0 * (1.0 - exp(-32.0 * t)) / 32.0)
            bus.add(at + k, sin(phase).toFloat() * exp(-t * 14f) * vol)
        }
    }

    private fun snare(bus: Bus, at: Int, n: Noise, vol: Float = 0.5f) {
        val len = (0.2f * RATE).toInt()
        var lp = 0f
        for (k in 0 until len) {
            val t = k.toFloat() / RATE
            lp += (n.next() - lp) * 0.62f
            val tone = sin(2.0 * PI * 190.0 * t).toFloat() * exp(-t * 28f) * 0.5f
            bus.add(at + k, (lp * exp(-t * 20f) + tone) * vol)
        }
    }

    private fun hat(bus: Bus, at: Int, n: Noise, vol: Float = 0.12f, open: Boolean = false) {
        val len = ((if (open) 0.14f else 0.04f) * RATE).toInt()
        var prev = 0f
        for (k in 0 until len) {
            val t = k.toFloat() / RATE
            val x = n.next()
            val hp = x - prev * 0.85f // crude high-pass
            prev = x
            bus.add(at + k, hp * exp(-t * (if (open) 22f else 90f)) * vol)
        }
    }

    private fun bell(bus: Bus, at: Int, midi: Float, vol: Float, decay: Float = 3f) {
        val f = hz(midi)
        val len = (2.4f * RATE).toInt()
        for (k in 0 until len) {
            val t = k.toFloat() / RATE
            val s = sin(2.0 * PI * f * t) + 0.4 * sin(2.0 * PI * f * 2.76 * t) * exp(-t * 3.0) + 0.2 * sin(2.0 * PI * f * 5.4 * t) * exp(-t * 6.0)
            bus.add(at + k, (s * exp(-t * decay) * vol).toFloat())
        }
    }

    private val MINOR = intArrayOf(0, 3, 7)
    private val MAJOR = intArrayOf(0, 4, 7)

    // ---- tracks ------------------------------------------------------------------------------------

    private fun car(mars: Boolean): Mix {
        val bpm = if (mars) 124f else 132f
        val m = Mix(loopSamples(bpm), bpm)
        val n = Noise(if (mars) 23 else 3)
        // Mars: D minor, i - VI - iv - V, then i - VI - VII - V
        val roots = if (mars) intArrayOf(50, 46, 43, 45, 50, 46, 48, 45) else intArrayOf(45, 41, 48, 43, 45, 41, 43, 40)
        val minor = if (mars) booleanArrayOf(true, false, true, false, true, false, false, false) else booleanArrayOf(true, false, false, false, true, false, false, false)
        val arp = if (mars) intArrayOf(0, 2, 1, 2, 1, 2, 0, 1) else intArrayOf(0, 1, 2, 1, 2, 1, 0, 2)
        for (bar in 0 until BARS) {
            val b0 = (bar * 4 * m.beat).toInt()
            val root = roots[bar]
            val q = if (minor[bar]) MINOR else MAJOR
            // pad
            for (d in q) note(m.pad, b0, 3.6f * m.beat / RATE, hz(root + 24f + d), 0.05f, Wave.SAW, 0.12f, 0.3f, 1100f, 0.004f)
            for (s in 0 until 16) {
                val at = b0 + (s * m.step).toInt()
                if (s % 4 == 0) kick(m.drums, at)
                if (s % 4 == 2) hat(m.drums, at, n)
                if (s == 4 || s == 12) snare(m.drums, at, n)
                // driving eighth-note bass with octave jumps
                if (s % 2 == 0) {
                    val oct = if ((s / 2) % 4 == 2) 12 else 0
                    note(m.bass, at, m.step * 1.6f / RATE, hz(root.toFloat() + oct), 0.30f, Wave.SAW, 0.004f, 0.06f, 520f)
                }
                // arpeggio
                val idx = arp[s % 8]
                val top = if (s % 8 >= 4) 12 else 0
                note(m.lead, at, m.step * 0.8f / RATE, hz(root + 36f + q[idx] + top), 0.085f, if (mars) Wave.SAW else Wave.PULSE, 0.003f, 0.07f, if (mars) 3000f else 4200f)
            }
        }
        return m
    }

    private fun plane(mars: Boolean): Mix {
        val bpm = if (mars) 100f else 108f
        val m = Mix(loopSamples(bpm), bpm)
        val n = Noise(if (mars) 25 else 5)
        // Mars: A minor, i - VI - VII - III, then i - VI - III - V
        val roots = if (mars) intArrayOf(45, 41, 43, 48, 45, 41, 48, 40) else intArrayOf(48, 43, 45, 41, 48, 43, 41, 43)
        val minor = if (mars) booleanArrayOf(true, false, false, false, true, false, false, false) else booleanArrayOf(false, false, true, false, false, false, false, false)
        val melody = if (mars) floatArrayOf(72f, 76f, 79f, 76f, 72f, 77f, 79f, 76f) else floatArrayOf(76f, 79f, 81f, 79f, 76f, 74f, 77f, 79f)
        for (bar in 0 until BARS) {
            val b0 = (bar * 4 * m.beat).toInt()
            val root = roots[bar]
            val q = if (minor[bar]) MINOR else MAJOR
            for (d in q) {
                note(m.pad, b0, 3.8f * m.beat / RATE, hz(root + 24f + d), 0.075f, Wave.SAW, 0.45f, 0.6f, 1500f, 0.005f)
                note(m.pad, b0, 3.8f * m.beat / RATE, hz(root + 12f + d), 0.04f, Wave.TRI, 0.45f, 0.6f, 1500f)
            }
            note(m.bass, b0, 1.9f * m.beat / RATE, hz(root.toFloat()), 0.26f, Wave.TRI, 0.01f, 0.2f, 700f)
            note(m.bass, b0 + (2 * m.beat).toInt(), 1.9f * m.beat / RATE, hz(root.toFloat() + if (bar % 2 == 0) 7f else 0f), 0.22f, Wave.TRI, 0.01f, 0.2f, 700f)
            // slow soaring melody, one long note per bar
            note(m.bell, b0 + (0.5f * m.beat).toInt(), 3f * m.beat / RATE, hz(melody[bar]), 0.06f, Wave.SINE, 0.06f, 0.5f, 6000f, 0f, 0.004f)
            for (s in 0 until 8) {
                val at = b0 + (s * m.beat / 2f).toInt()
                if (s % 4 == 0) kick(m.drums, at, 0.55f)
                hat(m.drums, at + (m.beat / 4f).toInt(), n, 0.07f)
                if (s % 4 == 2 && bar % 2 == 1) snare(m.drums, at, n, 0.22f)
                val tone = q[intArrayOf(0, 2, 1, 2, 0, 1, 2, 1)[s]] + intArrayOf(0, 0, 12, 12, 0, 12, 12, 24)[s]
                note(m.lead, at, 0.14f, hz(root + 36f + tone), 0.1f, Wave.TRI, 0.004f, 0.12f, 5000f)
            }
        }
        return m
    }

    private fun rocket(mars: Boolean): Mix {
        val bpm = if (mars) 90f else 96f
        val m = Mix(loopSamples(bpm), bpm)
        val n = Noise(if (mars) 29 else 9)
        // Mars: D minor, a slow descent: i - VII - VI - V, then i - VII - V - iv
        val roots = if (mars) intArrayOf(38, 36, 34, 33, 38, 36, 33, 43) else intArrayOf(40, 36, 43, 38, 40, 36, 38, 35)
        val minor = if (mars) booleanArrayOf(true, false, false, false, true, false, false, true) else booleanArrayOf(true, false, false, false, true, false, false, false)
        for (bar in 0 until BARS) {
            val b0 = (bar * 4 * m.beat).toInt()
            val root = roots[bar]
            val q = if (minor[bar]) MINOR else MAJOR
            for (d in q) {
                note(m.pad, b0, 3.9f * m.beat / RATE, hz(root + 24f + d), 0.07f, Wave.SAW, 0.8f, 1.0f, 1000f, 0.007f)
                note(m.pad, b0, 3.9f * m.beat / RATE, hz(root + 36f + d), 0.035f, Wave.SINE, 0.8f, 1.0f, 4000f)
            }
            // pulsing drone
            for (s in 0 until 8) {
                val at = b0 + (s * m.beat / 2f).toInt()
                note(m.bass, at, m.beat / 2f * 0.92f / RATE, hz(root.toFloat()), 0.3f, Wave.SAW, 0.01f, 0.08f, 380f)
                if (s % 4 == 0) kick(m.drums, at, 0.7f)
                if (s == 3 || s == 7) hat(m.drums, at, n, 0.1f, open = true)
                if (s % 4 == 2) snare(m.drums, at, n, 0.28f)
            }
            // sparse bells climbing the chord
            for (k in 0 until 4) {
                val tone = q[(k + bar) % 3] + intArrayOf(36, 48, 36, 48)[k]
                bell(m.bell, b0 + (k * m.beat).toInt() + (0.5f * m.step).toInt(), root + 24f + tone, 0.1f, 2.6f)
            }
        }
        return m
    }

    private fun menu(): Mix {
        val bpm = 84f
        val m = Mix(loopSamples(bpm), bpm)
        val roots = intArrayOf(50, 46, 53, 48, 50, 46, 48, 45)
        val minor = booleanArrayOf(true, false, false, false, true, false, false, false)
        for (bar in 0 until BARS) {
            val b0 = (bar * 4 * m.beat).toInt()
            val root = roots[bar]
            val q = if (minor[bar]) MINOR else MAJOR
            for (d in q) note(m.pad, b0, 3.9f * m.beat / RATE, hz(root + 12f + d), 0.075f, Wave.SAW, 0.6f, 0.9f, 900f, 0.006f)
            note(m.bass, b0, 3.8f * m.beat / RATE, hz(root.toFloat() - 12f), 0.22f, Wave.SINE, 0.05f, 0.4f, 600f)
            for (s in 0 until 8) {
                val tone = q[intArrayOf(0, 1, 2, 1, 2, 1, 0, 1)[s]] + (if (s % 4 >= 2) 24 else 12)
                bell(m.bell, b0 + (s * m.beat / 2f).toInt(), root + 24f + tone, 0.08f, 3.2f)
            }
        }
        return m
    }

    private fun finale(mars: Boolean): Mix {
        val bpm = if (mars) 116f else 112f
        val m = Mix(loopSamples(bpm), bpm)
        val n = Noise(if (mars) 37 else 17)
        // Mars: D major, I - VI - IV - I, then vi - IV - V - I
        val roots = if (mars) intArrayOf(50, 46, 55, 50, 47, 55, 57, 50) else intArrayOf(48, 53, 55, 48, 45, 53, 55, 48)
        val minor = if (mars) booleanArrayOf(false, false, false, false, true, false, false, false) else booleanArrayOf(false, false, false, false, true, false, false, false)
        for (bar in 0 until BARS) {
            val b0 = (bar * 4 * m.beat).toInt()
            val root = roots[bar]
            val q = if (minor[bar]) MINOR else MAJOR
            for (d in q) {
                note(m.pad, b0, 3.8f * m.beat / RATE, hz(root + 24f + d), 0.08f, Wave.SAW, 0.2f, 0.5f, 1800f, 0.005f)
                note(m.pad, b0, 3.8f * m.beat / RATE, hz(root + 12f + d), 0.05f, Wave.SQUARE, 0.2f, 0.5f, 1200f)
            }
            note(m.bass, b0, 1.8f * m.beat / RATE, hz(root.toFloat()), 0.28f, Wave.TRI, 0.01f, 0.15f, 800f)
            note(m.bass, b0 + (2 * m.beat).toInt(), 1.8f * m.beat / RATE, hz(root.toFloat() + 7f), 0.24f, Wave.TRI, 0.01f, 0.15f, 800f)
            for (s in 0 until 16) {
                val at = b0 + (s * m.step).toInt()
                if (s % 4 == 0) kick(m.drums, at, 0.6f)
                if (s == 4 || s == 12) snare(m.drums, at, n, 0.3f)
                if (s % 2 == 0) hat(m.drums, at, n, 0.08f)
                val idx = s % 6
                val tone = q[idx % 3] + 12 * (idx / 3)
                note(m.lead, at, m.step * 0.85f / RATE, hz(root + 36f + tone), 0.09f, Wave.PULSE, 0.003f, 0.09f, 4800f)
            }
            bell(m.bell, b0, root + 48f + q[2], 0.1f, 1.8f)
        }
        return m
    }
}
