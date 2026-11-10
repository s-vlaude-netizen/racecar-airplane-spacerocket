package app.roadtoorbit.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Procedural background music: small step-sequenced loops (drums, bass, pads, arpeggios, bells with a
 * feedback delay) rendered to mono PCM. Notes that ring past the end of a loop wrap around to its
 * start, so every track loops without a seam. Deterministic: the same bytes every run.
 */
object MusicSynth {
    private const val RATE = SoundSynth.SAMPLE_RATE

    /**
     * The menu loop and, per level, one for each leg and the finale: the Martian ones are in another key and mood,
     * the ocean ones are the sunny waterplane (B-flat mixolydian), the dive (C-sharp dorian), the seabed drive
     * (F minor) and the arrival at Atlantis (E-flat lydian).
     */
    enum class Track { MENU, CAR, PLANE, ROCKET, FINALE, MARS_CAR, MARS_PLANE, MARS_ROCKET, MARS_FINALE, OCEAN_PLANE, OCEAN_SUB, OCEAN_CAR, OCEAN_FINALE }

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
            Track.OCEAN_PLANE -> return oceanPlane()
            Track.OCEAN_SUB -> return oceanSub()
            Track.OCEAN_CAR -> return oceanCar()
            Track.OCEAN_FINALE -> return oceanFinale()
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

    // ---- the ocean kit -----------------------------------------------------------------------------
    // The ocean loops are built differently from the older ones: sines come from a wavetable instead of a
    // sin() per sample, one reusable "desk" bus replaces five full-length buses, and every effect runs around
    // the loop, so a tail that rings past the last bar comes back in at the first. They render in a fraction
    // of the time and memory of the biggest older loops.

    /** One period of a sine in 1024 steps plus a guard point, read with linear interpolation (error below -100 dB). */
    private val SINE = FloatArray(1025) { sin(2.0 * PI * it / 1024.0).toFloat() }

    /** Sine of a phase measured in cycles. */
    private fun tsin(cycles: Double): Float {
        val x = (cycles - Math.floor(cycles)) * 1024.0
        val i = x.toInt()
        val a = SINE[i]
        return a + (SINE[i + 1] - a) * (x - i).toFloat()
    }

    private fun smooth(u: Float): Float {
        val c = if (u < 0f) 0f else if (u > 1f) 1f else u
        return c * c * (3f - 2f * c)
    }

    private fun hzi(midi: Int): Float = hz(midi.toFloat())

    /** A slow wave that is 0 at the start of the loop and 1 half-way through each of its [cycles] cycles. */
    private fun lfo(i: Int, n: Int, cycles: Int): Float = 0.5f - 0.5f * tsin(cycles.toDouble() * i / n + 0.25)

    /** How much of the end of the loop an effect replays before it starts writing, so that its tail wraps round to the start. */
    private const val WARM = 4 * RATE

    /**
     * Runs [step] over a looping buffer. The first run only warms the effect's state up on the last seconds of the
     * loop; the second writes the result, so whatever rings at the end of the loop carries on at the start.
     */
    private inline fun looped(b: FloatArray, step: (Int, Float) -> Float) {
        for (i in b.size - min(b.size, WARM) until b.size) step(i, b[i])
        for (i in b.indices) b[i] = step(i, b[i])
    }

    /** Feedback echo, y[i] = x[i] + feedback * y[i - delay], around the loop. */
    private fun echo(b: FloatArray, delay: Int, feedback: Float) {
        if (delay <= 0 || delay >= b.size) return
        val line = FloatArray(delay)
        var p = 0
        looped(b) { _, x ->
            val y = x + line[p] * feedback
            line[p] = y
            if (++p == delay) p = 0
            y
        }
    }

    /** Two one-pole low-passes in a row (12 dB per octave); [corner] gives the cutoff in Hz at each sample. */
    private inline fun lowpass(b: FloatArray, corner: (Int) -> Float) {
        var y1 = 0f
        var y2 = 0f
        looped(b) { i, x ->
            val a = min(1f, 2f * PI.toFloat() * corner(i) / RATE)
            y1 += (x - y1) * a
            y2 += (y1 - y2) * a
            y2
        }
    }

    /** Adds a resonant band around [fc] Hz, [gain] times as loud as the signal there: a vowel-like formant. */
    private fun formant(b: FloatArray, fc: Float, q: Float, gain: Float) {
        val w = 2.0 * PI * fc / RATE
        val alpha = sin(w) / (2.0 * q)
        val a0 = 1.0 + alpha
        val g = (alpha / a0).toFloat()
        val a1 = (-2.0 * kotlin.math.cos(w) / a0).toFloat()
        val a2 = ((1.0 - alpha) / a0).toFloat()
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f
        looped(b) { _, x ->
            val y = g * (x - x2) - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            x + gain * y
        }
    }

    private val COMBS = intArrayOf(558, 594, 638, 678, 711, 745)
    private val ALLPASSES = intArrayOf(278, 220, 170)

    /** A small damped-comb reverb (the Schroeder / Freeverb layout) that runs around the loop. */
    private fun reverb(b: FloatArray, feedback: Float, damp: Float) {
        val comb = Array(COMBS.size) { FloatArray(COMBS[it]) }
        val ci = IntArray(COMBS.size)
        val cf = FloatArray(COMBS.size)
        val pass = Array(ALLPASSES.size) { FloatArray(ALLPASSES[it]) }
        val pi = IntArray(ALLPASSES.size)
        looped(b) { _, x ->
            var s = 0f
            for (c in comb.indices) {
                val line = comb[c]
                val o = line[ci[c]]
                cf[c] = o * (1f - damp) + cf[c] * damp
                line[ci[c]] = x * 0.25f + cf[c] * feedback
                if (++ci[c] == line.size) ci[c] = 0
                s += o
            }
            for (a in pass.indices) {
                val line = pass[a]
                val o = line[pi[a]]
                line[pi[a]] = s + o * 0.5f
                if (++pi[a] == line.size) pi[a] = 0
                s = o - s
            }
            s
        }
    }

    /** A steady sine tuned to a whole number of cycles per loop (so it has no seam), optionally swelling [breaths] times per loop. */
    private fun drone(b: FloatArray, freq: Float, vol: Float, breaths: Int = 0, depth: Float = 0f) {
        val cycles = Math.round(freq * b.size / RATE).toDouble()
        for (i in b.indices) {
            val swell = if (breaths > 0) 1f - depth * lfo(i, b.size, breaths) else 1f
            b[i] += tsin(cycles * i / b.size) * vol * swell
        }
    }

    /**
     * The ocean mixing desk: instruments are played one layer at a time into a single scratch bus, which gets its
     * effect, is mixed into the dry sum and sent to the shared reverb: two loop-length buffers and the scratch bus
     * instead of five buses and a dozen temporaries.
     */
    private class Sea(bpm: Float) {
        val n = loopSamples(bpm)
        val beat = RATE * 60f / bpm
        val step = beat / 4f
        private val dry = FloatArray(n)
        private val wet = FloatArray(n)
        private val desk = Bus(n)

        /** Start of a sixteenth-note step (fractions allowed) of a bar. */
        fun at(bar: Int, s: Float = 0f): Int = ((bar * 16 + s) * step).toInt()

        fun layer(gain: Float, reverb: Float = 0f, fx: (FloatArray) -> Unit = {}, play: (Bus) -> Unit) {
            play(desk)
            val b = desk.buf
            fx(b)
            for (i in 0 until n) {
                dry[i] += b[i] * gain
                wet[i] += b[i] * reverb
                b[i] = 0f
            }
        }

        /** Adds the reverb, squeezes the peaks with a soft clip and scales to the same peak level as the older loops. */
        fun render(feedback: Float, damp: Float, level: Float, drive: Float): ShortArray {
            reverb(wet, feedback, damp)
            var peak = 0f
            for (i in 0 until n) { dry[i] += wet[i] * level; peak = max(peak, abs(dry[i])) }
            val k = if (peak > 0f) drive / peak else 1f
            var top = 0f
            for (i in 0 until n) { dry[i] = tanh(dry[i] * k); top = max(top, abs(dry[i])) }
            val g = if (top > 0f) 0.88f / top else 1f
            return ShortArray(n) { (dry[it] * g * 32767f).toInt().coerceIn(-32767, 32767).toShort() }
        }
    }

    /** A struck or plucked sound: decaying sine partials, each with a ratio to the pitch, a level and a decay rate per second. */
    private class Timbre(val ratio: FloatArray, val level: FloatArray, val decay: FloatArray, val bend: Float = 0f)

    private val MARIMBA = Timbre(floatArrayOf(1f, 4f), floatArrayOf(1f, 0.32f), floatArrayOf(6f, 42f))
    private val STEEL = Timbre(
        floatArrayOf(1f, 1.0045f, 2.005f, 3f, 4.02f), floatArrayOf(1f, 0.5f, 0.62f, 0.28f, 0.1f), floatArrayOf(3.4f, 3.4f, 4.6f, 7f, 11f), bend = 0.012f,
    )
    private val GLASS = Timbre(floatArrayOf(1f, 2f, 3.01f), floatArrayOf(1f, 0.2f, 0.07f), floatArrayOf(8f, 14f, 24f))
    private val HARP = Timbre(floatArrayOf(1f, 2f, 3f, 4f), floatArrayOf(1f, 0.4f, 0.14f, 0.06f), floatArrayOf(2.4f, 4f, 6.5f, 10f))
    private val WOOD = Timbre(floatArrayOf(1f, 2.4f), floatArrayOf(1f, 0.45f), floatArrayOf(60f, 120f))
    private val SUBTHUMP = Timbre(floatArrayOf(1f, 2f, 3f), floatArrayOf(1f, 0.5f, 0.2f), floatArrayOf(3f, 5f, 8f))
    private val PING = Timbre(floatArrayOf(1f), floatArrayOf(1f), floatArrayOf(8f), bend = 0.03f)
    private val CENTS_ONE = floatArrayOf(0f)
    private val CENTS_PAIR = floatArrayOf(-7f, 7f)
    private val CENTS_CHOIR = floatArrayOf(-11f, 0f, 11f)

    /** An inharmonic bell (partials at 1, 2.76 and 5.4 times the pitch) that rings for about 5 / [decay] seconds. */
    private fun bellTimbre(decay: Float) = Timbre(floatArrayOf(1f, 2.76f, 5.4f), floatArrayOf(1f, 0.4f, 0.2f), floatArrayOf(decay, decay + 3f, decay + 6f))

    /**
     * Plays a [Timbre] from the wavetable: every partial is a sine with its own exponential decay, high notes die
     * faster than low ones, [ring] stretches the ringing and [attack] softens the strike.
     */
    private fun pluck(bus: Bus, at: Int, freq: Float, vol: Float, t: Timbre, ring: Float = 1f, attack: Float = 0.002f) {
        val shorten = max(1f, (freq / 440f).pow(0.35f))
        val n = bus.n
        val buf = bus.buf
        val soft = max(1f, attack * RATE)
        for (p in t.ratio.indices) {
            val f = freq * t.ratio[p]
            if (f > RATE * 0.45f) continue
            val decay = t.decay[p] * shorten / ring
            val len = (5f / decay * RATE).toInt()
            val fall = exp(-decay / RATE)
            val inc = f.toDouble() / RATE
            val bendFall = exp(-90f / RATE)
            var amp = vol * t.level[p]
            var bend = t.bend
            var ph = 0.0
            var idx = Math.floorMod(at, n)
            for (k in 0 until len) {
                buf[idx] += tsin(ph) * amp * min(min(1f, k / soft), (len - k) / 64f)
                ph += inc * (1f + bend)
                bend *= bendFall
                amp *= fall
                if (++idx == n) idx = 0
            }
        }
    }

    /** One pad voice: detuned oscillators under a smooth attack and release (no exponentials, so it ends exactly). */
    private fun padVoice(bus: Bus, at: Int, hold: Float, freq: Float, vol: Float, attack: Float, release: Float, w: Wave, cents: FloatArray) {
        val voices = cents.size
        val inc = DoubleArray(voices) { freq.toDouble() * 2.0.pow(cents[it] / 1200.0) / RATE }
        val ph = DoubleArray(voices) { it * 0.37 }
        val att = attack * RATE
        val holdEnd = hold * RATE
        val rel = release * RATE
        val n = bus.n
        val buf = bus.buf
        val g = vol / voices
        var idx = Math.floorMod(at, n)
        for (k in 0 until (holdEnd + rel).toInt()) {
            val e = if (k < att) smooth(k / att) else if (k < holdEnd) 1f else smooth(1f - (k - holdEnd) / rel)
            var s = 0f
            for (v in 0 until voices) { s += wave(w, ph[v]); ph[v] += inc[v] }
            buf[idx] += s * e * g
            if (++idx == n) idx = 0
        }
    }

    /** A drum made of one sine that slides from [hi] to [lo] Hz: soft kicks, toms, timpani. */
    private fun thump(bus: Bus, at: Int, vol: Float, hi: Float, lo: Float, slide: Float, decay: Float, seconds: Float) {
        val slideFall = exp(-slide / RATE)
        val fall = exp(-decay / RATE)
        var glide = hi - lo
        var amp = vol
        var ph = 0.0
        for (k in 0 until (seconds * RATE).toInt()) {
            bus.add(at + k, tsin(ph) * amp * min(1f, k / 30f))
            ph += (lo + glide).toDouble() / RATE
            glide *= slideFall
            amp *= fall
        }
    }

    /** A shaker: a soft puff of high-passed noise, a touch slower to speak than a hat. */
    private fun shaker(bus: Bus, at: Int, n: Noise, vol: Float) {
        val fall = exp(-55f / RATE)
        var amp = 1f
        var prev = 0f
        for (k in 0 until (0.08f * RATE).toInt()) {
            val x = n.next()
            bus.add(at + k, (x - prev * 0.8f) * amp * min(1f, k / 90f) * vol)
            prev = x
            amp *= fall
        }
    }

    /** A wash of high-passed noise (spray, a cymbal): it swells up to [peak] (a fraction of [seconds]) and then dies away. */
    private fun wash(bus: Bus, at: Int, n: Noise, vol: Float, seconds: Float, peak: Float) {
        val len = (seconds * RATE).toInt()
        var prev = 0f
        for (k in 0 until len) {
            val u = k.toFloat() / len
            val env = if (u < peak) smooth(u / peak).let { it * it } else exp(-5f * (u - peak) / (1f - peak))
            val x = n.next()
            bus.add(at + k, (x - prev * 0.7f) * env * vol)
            prev = x
        }
    }

    /** A current: noise through a resonant band whose centre glides from [f0] to [f1] Hz while the swell rises and falls. */
    private fun swell(bus: Bus, at: Int, len: Int, vol: Float, f0: Float, f1: Float, damping: Float, n: Noise) {
        var low = 0f
        var band = 0f
        for (k in 0 until len) {
            val u = k.toFloat() / len
            val f = 2f * PI.toFloat() * (f0 + (f1 - f0) * u) / RATE
            low += f * band
            val high = n.next() - low - damping * band
            band += f * high
            val s = tsin(u * 0.5) // sin(pi * u): 0, 1, 0
            bus.add(at + k, band * s * s * vol)
        }
    }

    /** A bubble: a short sine that climbs in pitch as it rings out. */
    private fun bubble(bus: Bus, at: Int, freq: Float, vol: Float) {
        val climbFall = exp(-60f / RATE)
        val fall = exp(-40f / RATE)
        var climb = 1f
        var amp = vol
        var ph = 0.0
        for (k in 0 until (0.12f * RATE).toInt()) {
            bus.add(at + k, tsin(ph) * amp * min(1f, k / 20f))
            ph += freq * (1f + 0.45f * (1f - climb)).toDouble() / RATE
            climb *= climbFall
            amp *= fall
        }
    }

    /** A sonar ping: a sine blip with a small downward chirp, then [taps] echoes, each fainter, softer-edged and longer. */
    private fun ping(bus: Bus, at: Int, freq: Float, vol: Float, spacing: Int, taps: Int) {
        for (t in 0..taps) pluck(bus, at + t * spacing, freq, vol * 0.5f.pow(t), PING, ring = 1f + 0.5f * t, attack = 0.003f + 0.012f * t)
    }

    /** A distant call: a sine gliding from [f0] to [f1] Hz with a slow vibrato, a little second harmonic and a long swell. */
    private fun call(bus: Bus, at: Int, f0: Float, f1: Float, seconds: Float, vol: Float) {
        val len = (seconds * RATE).toInt()
        var ph = 0.0
        for (k in 0 until len) {
            val u = k.toFloat() / len
            val f = f0 + (f1 - f0) * smooth(u)
            ph += (f * (1f + 0.012f * tsin(4.6 * k / RATE))).toDouble() / RATE
            val s = tsin(u * 0.5)
            bus.add(at + k, (tsin(ph) + 0.3f * tsin(2.0 * ph)) * s * s * vol)
        }
    }

    // ---- the ocean tracks --------------------------------------------------------------------------

    private val SHAKE = floatArrayOf(1f, 0.4f, 0.7f, 0.4f)

    /** The bass of the sunny loop: (step, semitones above the root, length in steps), a lilting root - fifth - octave - fifth. */
    private val BOUNCE = intArrayOf(0, 0, 5, 6, 7, 2, 10, 12, 2, 12, 7, 3)

    /**
     * B-flat mixolydian, 118 bpm: the waterplane skimming a sunny sea. Marimba ripples and a steel-pan tune over a
     * lilting bass, a swung shaker, a soft kick and a warm pad; a wave breaks every four bars.
     * Chords: B-flat, A-flat, E-flat, B-flat | G minor, E-flat, C minor 7, F.
     */
    private fun oceanPlane(): ShortArray {
        val m = Sea(118f)
        val n = Noise(61)
        val swing = 0.2f // the off-beat eighth (and the sixteenth after it) lags a little: a lilt
        val roots = intArrayOf(46, 44, 39, 46, 43, 39, 36, 41)
        val pads = arrayOf(
            intArrayOf(58, 62, 65), intArrayOf(56, 60, 63), intArrayOf(55, 58, 63), intArrayOf(53, 58, 62),
            intArrayOf(55, 58, 62), intArrayOf(55, 58, 63), intArrayOf(55, 58, 60, 63), intArrayOf(57, 60, 65),
        )
        val ripples = arrayOf(
            intArrayOf(65, 70, 74, 77), intArrayOf(63, 68, 72, 75), intArrayOf(67, 70, 75, 79), intArrayOf(65, 70, 74, 77),
            intArrayOf(62, 67, 70, 74), intArrayOf(67, 70, 75, 79), intArrayOf(67, 70, 72, 75), intArrayOf(65, 69, 72, 77),
        )
        val shapeA = intArrayOf(0, 1, 2, 1, 3, 2, 1, 2)
        val shapeB = intArrayOf(0, 2, 1, 3, 2, 1, 3, 2)
        val accent = floatArrayOf(1f, 0.6f, 0.6f, 1f, 0.6f, 0.6f, 1f, 0.6f) // eighths grouped 3 + 3 + 2
        // bar, step, length in steps, MIDI note
        val tune = intArrayOf(
            0, 0, 6, 74, 0, 6, 6, 77, 0, 12, 4, 82,
            1, 0, 6, 80, 1, 6, 3, 79, 1, 9, 3, 77, 1, 12, 4, 75,
            2, 0, 6, 79, 2, 6, 6, 82, 2, 12, 4, 79,
            3, 0, 8, 77, 3, 8, 4, 74, 3, 12, 4, 72,
            4, 0, 6, 82, 4, 6, 6, 86, 4, 12, 4, 84,
            5, 0, 6, 82, 5, 6, 3, 79, 5, 9, 3, 82, 5, 12, 4, 79,
            6, 0, 6, 84, 6, 6, 6, 82, 6, 12, 4, 79,
            7, 0, 6, 81, 7, 6, 6, 84, 7, 12, 4, 77,
        )
        val echoDelay = (0.75f * m.beat).toInt() // a dotted eighth

        m.layer(0.9f, 0.05f) { b ->
            for (bar in 0 until 8) for (s in 0 until 16) {
                val at = m.at(bar, s + if (s % 4 >= 2) swing else 0f)
                if (s == 0) kick(b, at, 0.24f)
                if (s == 8) kick(b, at, 0.18f)
                if (s == 6 && bar % 2 == 1) kick(b, at, 0.1f)
                if (s == 4 || s == 12) pluck(b, at, 1046f, 0.12f, WOOD)
                shaker(b, at, n, 0.24f * SHAKE[s % 4])
                if (s % 4 == 2) hat(b, at, n, 0.15f)
            }
        }
        m.layer(1f) { b ->
            for (bar in 0 until 8) {
                for (j in BOUNCE.indices step 3) {
                    note(b, m.at(bar, BOUNCE[j].toFloat()), BOUNCE[j + 2] * m.step / RATE, hzi(roots[bar] + BOUNCE[j + 1]), 0.1f, Wave.TRI, 0.006f, 0.09f, 650f)
                }
                // a pickup into the next bar: a step above G, a leading note below B-flat
                if (bar == 3) note(b, m.at(bar, 14f), 2 * m.step / RATE, hzi(44), 0.09f, Wave.TRI, 0.006f, 0.09f, 650f)
                if (bar == 7) note(b, m.at(bar, 14f), 2 * m.step / RATE, hzi(45), 0.09f, Wave.TRI, 0.006f, 0.09f, 650f)
            }
        }
        m.layer(1f, 0.35f, fx = { lowpass(it) { 1000f } }) { b ->
            for (bar in 0 until 8) for (midi in pads[bar]) {
                val at = m.at(bar) - (0.04f * RATE).toInt()
                padVoice(b, at, 3.9f * m.beat / RATE, hzi(midi), 0.03f, 0.25f, 0.4f, Wave.SAW, CENTS_PAIR)
                padVoice(b, at, 3.9f * m.beat / RATE, hzi(midi), 0.018f, 0.25f, 0.4f, Wave.TRI, CENTS_ONE)
            }
        }
        m.layer(1f, 0.22f, fx = { echo(it, echoDelay, 0.3f) }) { b ->
            for (bar in 0 until 8) {
                val shape = if (bar < 4) shapeA else shapeB
                for (e in 0 until 8) {
                    val at = m.at(bar, 2f * e + if (e % 2 == 1) swing else 0f)
                    pluck(b, at, hzi(ripples[bar][shape[e]]), 0.26f * accent[e], MARIMBA)
                }
            }
        }
        m.layer(1f, 0.3f, fx = { echo(it, echoDelay, 0.35f) }) { b ->
            for (j in tune.indices step 4) pluck(b, m.at(tune[j], tune[j + 1].toFloat()), hzi(tune[j + 3]), 0.4f, STEEL)
        }
        m.layer(1f, 0.1f, fx = { lowpass(it) { 5000f } }) { b ->
            wash(b, m.at(0), n, 0.08f, 2.4f, 0.3f)
            wash(b, m.at(4), n, 0.08f, 2.4f, 0.3f)
        }
        return m.render(feedback = 0.8f, damp = 0.35f, level = 0.3f, drive = 1.5f)
    }

    /**
     * C-sharp dorian, 82 bpm: the dive. A pedal on C-sharp under slow chords (two bars each: C#m9, B over the
     * pedal, F#7, G#m7), a breathing filtered pad, sub-bass pulses, a sonar ping every two beats with a long echo,
     * bubbles, a distant call, a low drone and sparse drums.
     */
    private fun oceanSub(): ShortArray {
        val m = Sea(82f)
        val n = Noise(71)
        val roots = intArrayOf(37, 37, 30, 32)
        val pads = arrayOf(
            intArrayOf(49, 56, 59, 63, 64), intArrayOf(59, 63, 66, 70), intArrayOf(54, 58, 61, 64), intArrayOf(56, 59, 63, 66),
        )
        val pingHigh = intArrayOf(85, 78, 85, 87)
        val pingLow = intArrayOf(80, 83, 78, 83)
        val pools = arrayOf(intArrayOf(1, 4, 8, 11, 3), intArrayOf(11, 3, 6, 10, 1), intArrayOf(6, 10, 1, 4), intArrayOf(8, 11, 3, 6))
        val pulse = floatArrayOf(1f, 0.5f, 0.8f, 0.5f)
        val echoDelay = (0.75f * m.beat).toInt()
        fun rnd() = n.next() * 0.5f + 0.5f // 0..1

        m.layer(1f, 0.2f) { b ->
            drone(b.buf, 34.65f, 0.1f, breaths = 2, depth = 0.4f)
            drone(b.buf, 69.3f, 0.035f)
            drone(b.buf, 103.8f, 0.02f, breaths = 4, depth = 0.6f)
        }
        m.layer(1f, 0.5f, fx = { b ->
            lowpass(b) { 330f + 520f * lfo(it, m.n, 4) }
            for (i in b.indices) b[i] *= 0.5f + 0.5f * lfo(i, b.size, 4)
        }) { b ->
            for (c in 0 until 4) for (midi in pads[c]) {
                padVoice(b, m.at(2 * c), 7f * m.beat / RATE, hzi(midi), 0.11f, 1.3f, 1.6f, Wave.SAW, CENTS_PAIR)
            }
        }
        m.layer(1f, 0.05f) { b ->
            for (bar in 0 until 8) for (j in 0 until 4) {
                pluck(b, m.at(bar, 4f * j), hzi(roots[bar / 2]), 0.22f * pulse[j], SUBTHUMP, ring = 0.55f, attack = 0.012f)
            }
        }
        m.layer(0.9f, 0.2f) { b ->
            for (bar in 0 until 8) {
                thump(b, m.at(bar), 0.3f, 120f, 46f, 38f, 9f, 0.5f)
                if (bar % 2 == 1) thump(b, m.at(bar, 10f), 0.2f, 150f, 70f, 30f, 8f, 0.4f)
                if (bar % 4 == 1) pluck(b, m.at(bar, 14f), 1480f, 0.05f, WOOD)
            }
        }
        m.layer(1f, 0.55f) { b ->
            for (bar in 0 until 8) {
                ping(b, m.at(bar), hzi(pingHigh[bar / 2]), 0.22f, echoDelay, 4)
                if (bar % 4 != 3) ping(b, m.at(bar, 8f), hzi(pingLow[bar / 2]), 0.11f, echoDelay, 2)
            }
        }
        m.layer(1f, 0.5f) { b ->
            for (bar in 0 until 8) {
                val pool = pools[bar / 2]
                for (cluster in 0 until (if (bar % 2 == 0) 2 else 1)) {
                    val start = rnd() * 12f
                    val count = 2 + (rnd() * 4f).toInt()
                    val gap = 0.35f + rnd() * 0.6f
                    for (j in 0 until count) {
                        val midi = 72 + pool[(rnd() * pool.size).toInt().coerceAtMost(pool.size - 1)] + if (rnd() > 0.5f) 12 else 0
                        bubble(b, m.at(bar, start + j * gap), hzi(midi), 0.09f * (0.5f + 0.5f * rnd()) * (1f - 0.12f * j))
                    }
                }
            }
        }
        m.layer(1f, 0.8f) { b ->
            call(b, m.at(2, 6f), hzi(66), hzi(71), 2.6f, 0.07f)
            call(b, m.at(6, 4f), hzi(71), hzi(68), 3.0f, 0.06f)
        }
        m.layer(1f, 0.3f) { b ->
            swell(b, m.at(3, 8f), (6 * m.beat).toInt(), 0.08f, 600f, 1800f, 0.5f, n)
            swell(b, m.at(7, 8f), (6 * m.beat).toInt(), 0.08f, 1800f, 600f, 0.5f, n)
        }
        return m.render(feedback = 0.9f, damp = 0.5f, level = 0.45f, drive = 1.6f)
    }

    /**
     * F minor, 104 bpm: the seabed drive, tense wonder. A pulsing bass under a soft four-on-the-floor kick, eerie
     * bells and glassy arpeggios that flicker like bioluminescence, a low drone with a beating minor second and
     * currents of filtered noise. Chords: Fm, D-flat, A-flat, E-flat | Fm, D-flat, B-flat minor, C.
     */
    private fun oceanCar(): ShortArray {
        val m = Sea(104f)
        val n = Noise(81)
        val roots = intArrayOf(41, 37, 44, 39, 41, 37, 46, 48)
        val pads = arrayOf(
            intArrayOf(53, 56, 60), intArrayOf(53, 56, 60), intArrayOf(56, 60, 63), intArrayOf(55, 58, 63),
            intArrayOf(53, 56, 60), intArrayOf(53, 56, 60), intArrayOf(53, 58, 61), intArrayOf(55, 60, 64),
        )
        val glass = arrayOf(
            intArrayOf(77, 80, 84, 87), intArrayOf(73, 77, 80, 84), intArrayOf(80, 84, 87, 92), intArrayOf(79, 82, 87, 91),
            intArrayOf(77, 80, 84, 89), intArrayOf(73, 77, 80, 85), intArrayOf(82, 85, 89, 94), intArrayOf(79, 84, 88, 91),
        )
        val shapeUp = intArrayOf(0, 1, 2, 3, 2, 1, 3, 2)
        val shapeDown = intArrayOf(3, 2, 1, 0, 1, 2, 0, 1)
        val sparkle = floatArrayOf(1f, 0.45f, 0.6f, 0.45f, 0.8f, 0.45f, 0.6f, 0.45f)
        // two bells a bar: on the downbeat and on the sixth eighth
        val bells = arrayOf(
            intArrayOf(72, 68), intArrayOf(68, 77), intArrayOf(75, 72), intArrayOf(70, 79),
            intArrayOf(72, 78), intArrayOf(68, 73), intArrayOf(70, 77), intArrayOf(76, 67),
        )
        val bump = intArrayOf(0, 0, 0, 12, 0, 0, 0, 12) // the bass climbs an octave on the off-beats
        val echoDelay = (0.75f * m.beat).toInt()

        m.layer(1f, 0.1f) { b ->
            drone(b.buf, 43.65f, 0.08f, breaths = 2, depth = 0.4f)
            drone(b.buf, 65.41f, 0.03f)
            drone(b.buf, 46.25f, 0.015f) // a minor second above: a slow throb
        }
        m.layer(1f) { b ->
            for (bar in 0 until 8) for (j in 0 until 8) {
                // the on-beat notes duck under the kick
                note(b, m.at(bar, 2f * j), m.step * 1.1f / RATE, hzi(roots[bar] + bump[j]), if (j % 2 == 0) 0.06f else 0.17f, Wave.SAW, 0.004f, 0.05f, 480f)
            }
        }
        m.layer(0.9f, 0.05f) { b ->
            for (bar in 0 until 8) for (s in 0 until 16) {
                val at = m.at(bar, s.toFloat())
                if (s % 4 == 0) kick(b, at, 0.3f)
                if (s % 4 == 2) hat(b, at, n, 0.06f)
            }
        }
        m.layer(1f, 0.4f, fx = { lowpass(it) { 520f } }) { b ->
            for (bar in 0 until 8) for (midi in pads[bar]) {
                padVoice(b, m.at(bar), 3.8f * m.beat / RATE, hzi(midi), 0.04f, 0.6f, 0.8f, Wave.SAW, CENTS_PAIR)
            }
        }
        m.layer(1f, 0.45f, fx = { echo(it, echoDelay, 0.5f) }) { b ->
            for (bar in 0 until 8) {
                pluck(b, m.at(bar), hzi(bells[bar][0]), 0.26f, bellTimbre(2.4f))
                pluck(b, m.at(bar, 10f), hzi(bells[bar][1]), 0.2f, bellTimbre(2.4f))
            }
        }
        m.layer(1f, 0.45f, fx = { echo(it, echoDelay, 0.4f) }) { b ->
            for (bar in 0 until 8) for (s in 0 until 16) {
                val shape = if (bar < 4) shapeUp else shapeDown
                pluck(b, m.at(bar, s.toFloat()), hzi(glass[bar][shape[s % 8]]), 0.15f * sparkle[s % 8], GLASS)
            }
        }
        m.layer(1f, 0.3f) { b ->
            swell(b, m.at(0), (8 * m.beat).toInt(), 0.1f, 300f, 1200f, 0.4f, n)
            swell(b, m.at(2), (8 * m.beat).toInt(), 0.1f, 900f, 300f, 0.4f, n)
            swell(b, m.at(4), (8 * m.beat).toInt(), 0.1f, 300f, 1200f, 0.4f, n)
            swell(b, m.at(6), (8 * m.beat).toInt(), 0.14f, 300f, 1800f, 0.3f, n)
        }
        return m.render(feedback = 0.86f, damp = 0.45f, level = 0.35f, drive = 1.2f)
    }

    /**
     * E-flat major with a lydian colour, 94 bpm: the arrival at Atlantis. A choir of detuned voices, a harp that
     * rises through every chord, bells, a soaring melody over soft timpani and a big wet reverb.
     * Chords: E-flat, F over E-flat (the lydian bar), G minor 7, A-flat major 7 | E-flat/G, C minor 7, A-flat, B-flat.
     */
    private fun oceanFinale(): ShortArray {
        val m = Sea(94f)
        val n = Noise(91)
        val roots = intArrayOf(39, 39, 43, 44, 43, 36, 44, 46)
        val choir = arrayOf(
            intArrayOf(51, 58, 63, 67), intArrayOf(57, 60, 65, 69), intArrayOf(55, 58, 62, 65), intArrayOf(56, 60, 63, 67),
            intArrayOf(55, 58, 63, 67), intArrayOf(55, 58, 60, 63), intArrayOf(56, 60, 63, 68), intArrayOf(58, 62, 65, 70),
        )
        val harp = arrayOf(
            intArrayOf(51, 58, 63, 67, 70, 75, 79, 82), intArrayOf(53, 60, 65, 69, 72, 77, 81, 84),
            intArrayOf(55, 62, 65, 70, 74, 77, 82, 86), intArrayOf(56, 63, 67, 72, 75, 79, 84, 87),
            intArrayOf(55, 58, 63, 67, 70, 75, 79, 82), intArrayOf(48, 55, 58, 63, 67, 72, 75, 79),
            intArrayOf(56, 60, 63, 68, 72, 75, 80, 84), intArrayOf(58, 65, 70, 74, 77, 82, 86, 89),
        )
        val bells = intArrayOf(87, 81, 86, 84, 82, 84, 87, 86)
        // bar, step, length in steps, MIDI note
        val tune = intArrayOf(
            0, 0, 4, 70, 0, 4, 4, 75, 0, 8, 8, 79,
            1, 0, 8, 81, 1, 8, 8, 82,
            2, 0, 8, 86, 2, 8, 4, 84, 2, 12, 4, 82,
            3, 0, 8, 80, 3, 8, 8, 79,
            4, 0, 4, 82, 4, 4, 12, 87,
            5, 0, 6, 86, 5, 6, 2, 84, 5, 8, 4, 82, 5, 12, 4, 79,
            6, 0, 8, 80, 6, 8, 4, 84, 6, 12, 4, 87,
            7, 0, 8, 86, 7, 8, 4, 84, 7, 12, 4, 82,
        )
        val echoDelay = (0.75f * m.beat).toInt()

        m.layer(1f, 0.5f, fx = { b ->
            lowpass(b) { 1900f }
            formant(b, 800f, 4f, 0.5f)
            formant(b, 1150f, 5f, 0.35f)
        }) { b ->
            for (bar in 0 until 8) for (midi in choir[bar]) {
                padVoice(b, m.at(bar) - (0.1f * RATE).toInt(), 3.9f * m.beat / RATE, hzi(midi), 0.08f, 0.7f, 1f, Wave.SAW, CENTS_CHOIR)
            }
        }
        m.layer(1f) { b ->
            for (bar in 0 until 8) {
                note(b, m.at(bar), 1.7f * m.beat / RATE, hzi(roots[bar]), 0.14f, Wave.TRI, 0.01f, 0.15f, 700f)
                note(b, m.at(bar, 8f), 1.7f * m.beat / RATE, hzi(roots[bar] + if (bar % 2 == 0) 12 else 7), 0.11f, Wave.TRI, 0.01f, 0.15f, 700f)
            }
        }
        m.layer(1f, 0.5f, fx = { echo(it, echoDelay, 0.3f) }) { b ->
            for (bar in 0 until 8) for (e in 0 until 8) {
                pluck(b, m.at(bar, 2f * e), hzi(harp[bar][e]), 0.2f * (if (e % 4 == 0) 1f else 0.7f), HARP)
            }
        }
        m.layer(1f, 0.5f) { b ->
            for (bar in 0 until 8) pluck(b, m.at(bar), hzi(bells[bar]), 0.2f, bellTimbre(1.8f))
        }
        m.layer(1f, 0.5f, fx = { echo(it, echoDelay, 0.3f) }) { b ->
            for (j in tune.indices step 4) {
                note(b, m.at(tune[j], tune[j + 1].toFloat()), tune[j + 2] * m.step / RATE, hzi(tune[j + 3]), 0.22f, Wave.TRI, 0.07f, 0.4f, 2600f, 0.003f, 0.004f)
            }
        }
        m.layer(0.9f, 0.2f) { b ->
            for (bar in 0 until 8) {
                val f = hzi(roots[bar] + 12)
                thump(b, m.at(bar), 0.15f, f * 1.5f, f, 40f, 5.5f, 0.8f)
                thump(b, m.at(bar, 8f), 0.08f, f * 1.5f, f, 40f, 5.5f, 0.8f)
            }
        }
        m.layer(1f, 0.4f, fx = { lowpass(it) { 7000f } }) { b ->
            wash(b, m.at(3, 11f), n, 0.06f, 1.2f, 0.9f)
            wash(b, m.at(4), n, 0.07f, 2.6f, 0.02f)
            wash(b, m.at(7, 11f), n, 0.06f, 1.2f, 0.9f)
            wash(b, m.at(0), n, 0.07f, 2.6f, 0.02f)
        }
        return m.render(feedback = 0.92f, damp = 0.4f, level = 0.4f, drive = 1.2f)
    }
}
