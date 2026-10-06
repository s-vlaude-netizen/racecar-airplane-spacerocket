package app.roadtoorbit.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Every sound effect / loop in the game, synthesised from scratch so no audio assets are shipped. */
enum class SoundId {
    COIN, NITRO, REPAIR, HIT, SOFT_HIT, TRANSFORM, TRANSFORM_DONE, RING, RING_MISS, NEAR_MISS,
    EXPLOSION, BEEP, GO, VICTORY, GAME_OVER, ZONE, BOOST, TOUCHDOWN, UI,
    ENGINE_CAR, ENGINE_PLANE, ENGINE_ROCKET,
}

/**
 * Tiny additive/noise synthesiser producing mono 16-bit PCM at [SAMPLE_RATE]. Deterministic, so the
 * same bytes come out every run. Engine loops are built from integer-frequency partials so they
 * repeat seamlessly with no click at the loop point.
 */
object SoundSynth {
    const val SAMPLE_RATE = 22050

    fun looping(id: SoundId): Boolean = id == SoundId.ENGINE_CAR || id == SoundId.ENGINE_PLANE || id == SoundId.ENGINE_ROCKET

    fun render(id: SoundId): ShortArray = toPcm(
        when (id) {
            SoundId.COIN -> coin()
            SoundId.NITRO -> nitro()
            SoundId.REPAIR -> repair()
            SoundId.HIT -> hit()
            SoundId.SOFT_HIT -> softHit()
            SoundId.TRANSFORM -> transform()
            SoundId.TRANSFORM_DONE -> transformDone()
            SoundId.RING -> ring()
            SoundId.RING_MISS -> ringMiss()
            SoundId.NEAR_MISS -> nearMiss()
            SoundId.EXPLOSION -> explosion()
            SoundId.BEEP -> beep(880f, 0.13f)
            SoundId.GO -> beep(1320f, 0.40f)
            SoundId.VICTORY -> victory()
            SoundId.GAME_OVER -> gameOver()
            SoundId.ZONE -> zone()
            SoundId.BOOST -> boost()
            SoundId.TOUCHDOWN -> touchdown()
            SoundId.UI -> ui()
            SoundId.ENGINE_CAR -> engineCar()
            SoundId.ENGINE_PLANE -> enginePlane()
            SoundId.ENGINE_ROCKET -> engineRocket()
        },
    )

    /** Wraps PCM in a minimal 44-byte-header WAV container. */
    fun wav(pcm: ShortArray): ByteArray {
        val data = pcm.size * 2
        val out = ByteArrayOutputStream(44 + data)
        fun le32(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); le32(36 + data); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1); le32(SAMPLE_RATE); le32(SAMPLE_RATE * 2); le16(2); le16(16)
        out.write("data".toByteArray()); le32(data)
        for (s in pcm) le16(s.toInt())
        return out.toByteArray()
    }

    // ---- building blocks ---------------------------------------------------------------------

    private class Noise(seed: Int) {
        private var s = seed * 747796405 + 12345
        fun next(): Float {
            s = s * 1664525 + 1013904223
            return ((s ushr 8) and 0xFFFFFF) / 8388608f - 1f
        }
    }

    private fun buffer(seconds: Float) = FloatArray((seconds * SAMPLE_RATE).toInt())

    private fun env(t: Float, attack: Float, decay: Float): Float {
        val a = if (attack > 0f) min(1f, t / attack) else 1f
        return a * exp(-t * decay)
    }

    private fun tone(out: FloatArray, start: Float, dur: Float, freq: Float, vol: Float, decay: Float, harmonics: Boolean = false) {
        val i0 = (start * SAMPLE_RATE).toInt()
        val n = (dur * SAMPLE_RATE).toInt()
        for (i in 0 until n) {
            val idx = i0 + i
            if (idx >= out.size) break
            val t = i.toFloat() / SAMPLE_RATE
            val e = env(t, 0.004f, decay)
            var v = sin(2.0 * PI * freq * t).toFloat()
            if (harmonics) v += 0.35f * sin(2.0 * PI * freq * 2 * t).toFloat() + 0.18f * sin(2.0 * PI * freq * 3 * t).toFloat()
            out[idx] += v * e * vol
        }
    }

    private fun sweep(out: FloatArray, start: Float, dur: Float, f0: Float, f1: Float, vol: Float, shape: Float = 1f) {
        val i0 = (start * SAMPLE_RATE).toInt()
        val n = (dur * SAMPLE_RATE).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val idx = i0 + i
            if (idx >= out.size) break
            val u = i.toFloat() / n
            val f = f0 + (f1 - f0) * u.pow(shape)
            phase += 2.0 * PI * f / SAMPLE_RATE
            val e = min(1f, i / (0.02f * SAMPLE_RATE)) * min(1f, (n - i) / (0.06f * SAMPLE_RATE))
            out[idx] += sin(phase).toFloat() * e * vol
        }
    }

    private fun noiseBurst(out: FloatArray, start: Float, dur: Float, vol: Float, decay: Float, lowpass: Float, seed: Int) {
        val rnd = Noise(seed)
        val i0 = (start * SAMPLE_RATE).toInt()
        val n = (dur * SAMPLE_RATE).toInt()
        var y = 0f
        for (i in 0 until n) {
            val idx = i0 + i
            if (idx >= out.size) break
            val t = i.toFloat() / SAMPLE_RATE
            y += (rnd.next() - y) * lowpass
            out[idx] += y * env(t, 0.002f, decay) * vol
        }
    }

    private fun toPcm(f: FloatArray): ShortArray {
        var peak = 0f
        for (v in f) peak = max(peak, abs(v))
        val g = if (peak > 0.95f) 0.95f / peak else 1f
        return ShortArray(f.size) { (f[it] * g * 32767f).toInt().coerceIn(-32767, 32767).toShort() }
    }

    // ---- effects -----------------------------------------------------------------------------

    private fun coin(): FloatArray {
        val o = buffer(0.24f)
        tone(o, 0f, 0.12f, 1318.5f, 0.5f, 14f, true)
        tone(o, 0.07f, 0.18f, 1975.5f, 0.5f, 11f, true)
        return o
    }

    private fun nitro(): FloatArray {
        val o = buffer(0.5f)
        sweep(o, 0f, 0.42f, 380f, 1700f, 0.45f, 1.6f)
        noiseBurst(o, 0f, 0.45f, 0.35f, 5f, 0.35f, 3)
        return o
    }

    private fun repair(): FloatArray {
        val o = buffer(0.55f)
        tone(o, 0.00f, 0.22f, 523.3f, 0.45f, 9f, true)
        tone(o, 0.09f, 0.22f, 659.3f, 0.45f, 9f, true)
        tone(o, 0.18f, 0.32f, 784f, 0.5f, 7f, true)
        return o
    }

    private fun hit(): FloatArray {
        val o = buffer(0.5f)
        noiseBurst(o, 0f, 0.4f, 0.8f, 9f, 0.5f, 7)
        sweep(o, 0f, 0.35f, 140f, 45f, 0.9f)
        return o
    }

    private fun softHit(): FloatArray {
        val o = buffer(0.2f)
        noiseBurst(o, 0f, 0.15f, 0.55f, 22f, 0.7f, 11)
        tone(o, 0f, 0.1f, 220f, 0.3f, 25f)
        return o
    }

    private fun transform(): FloatArray {
        val o = buffer(1.9f)
        // servo whine rising through the whole sequence, plus mechanical clanks and a shimmering finish
        sweep(o, 0f, 1.7f, 160f, 940f, 0.28f, 1.7f)
        sweep(o, 0.1f, 1.5f, 320f, 1880f, 0.14f, 1.9f)
        noiseBurst(o, 0f, 0.5f, 0.28f, 4f, 0.25f, 21)
        for (k in 0 until 6) {
            val t = 0.22f + k * 0.21f
            noiseBurst(o, t, 0.06f, 0.55f, 55f, 0.8f, 30 + k)
            tone(o, t, 0.08f, 180f + 60f * k, 0.3f, 40f)
        }
        tone(o, 1.55f, 0.35f, 1568f, 0.3f, 8f, true)
        tone(o, 1.6f, 0.3f, 2093f, 0.25f, 9f, true)
        return o
    }

    private fun transformDone(): FloatArray {
        val o = buffer(0.7f)
        tone(o, 0f, 0.6f, 1046.5f, 0.4f, 6f, true)
        tone(o, 0f, 0.6f, 1568f, 0.35f, 6.5f, true)
        noiseBurst(o, 0f, 0.12f, 0.3f, 25f, 0.6f, 5)
        return o
    }

    private fun ring(): FloatArray {
        val o = buffer(0.4f)
        for (k in 0 until 3) tone(o, k * 0.05f, 0.2f, 1568f * 2f.pow(k / 6f * 2f), 0.38f, 10f, true)
        return o
    }

    private fun ringMiss(): FloatArray {
        val o = buffer(0.3f)
        sweep(o, 0f, 0.28f, 520f, 200f, 0.4f)
        return o
    }

    private fun nearMiss(): FloatArray {
        val o = buffer(0.3f)
        noiseBurst(o, 0f, 0.28f, 0.45f, 9f, 0.12f, 17)
        sweep(o, 0f, 0.25f, 700f, 1500f, 0.12f)
        return o
    }

    private fun explosion(): FloatArray {
        val o = buffer(1.6f)
        noiseBurst(o, 0f, 1.5f, 0.85f, 2.6f, 0.1f, 99)
        noiseBurst(o, 0f, 0.4f, 0.7f, 10f, 0.6f, 98)
        sweep(o, 0f, 1.2f, 110f, 28f, 0.85f)
        return o
    }

    private fun beep(freq: Float, dur: Float): FloatArray {
        val o = buffer(dur + 0.05f)
        tone(o, 0f, dur, freq, 0.55f, 5f)
        return o
    }

    private fun victory(): FloatArray {
        val o = buffer(1.9f)
        val notes = floatArrayOf(523.3f, 659.3f, 784f, 1046.5f, 784f, 1046.5f, 1318.5f)
        val starts = floatArrayOf(0f, 0.16f, 0.32f, 0.48f, 0.80f, 0.96f, 1.12f)
        val durs = floatArrayOf(0.16f, 0.16f, 0.16f, 0.30f, 0.16f, 0.16f, 0.70f)
        for (i in notes.indices) tone(o, starts[i], durs[i] + 0.3f, notes[i], 0.4f, 4.5f, true)
        return o
    }

    private fun gameOver(): FloatArray {
        val o = buffer(1.3f)
        tone(o, 0f, 0.5f, 392f, 0.45f, 5f, true)
        tone(o, 0.28f, 0.5f, 329.6f, 0.45f, 5f, true)
        tone(o, 0.56f, 0.8f, 261.6f, 0.5f, 3.5f, true)
        return o
    }

    private fun zone(): FloatArray {
        val o = buffer(0.7f)
        for (k in 0 until 2) {
            tone(o, k * 0.3f, 0.12f, 660f, 0.4f, 10f)
            tone(o, k * 0.3f + 0.12f, 0.14f, 990f, 0.4f, 10f)
        }
        return o
    }

    private fun boost(): FloatArray {
        val o = buffer(0.55f)
        noiseBurst(o, 0f, 0.5f, 0.6f, 4f, 0.2f, 61)
        sweep(o, 0f, 0.5f, 200f, 900f, 0.2f, 1.5f)
        return o
    }

    private fun touchdown(): FloatArray {
        val o = buffer(0.9f)
        sweep(o, 0f, 0.5f, 90f, 35f, 0.9f)
        noiseBurst(o, 0f, 0.8f, 0.55f, 4f, 0.15f, 77)
        return o
    }

    private fun ui(): FloatArray {
        val o = buffer(0.1f)
        tone(o, 0f, 0.08f, 1200f, 0.4f, 40f)
        return o
    }

    // ---- loops (exactly 2 s, integer-Hz partials → seamless) -----------------------------------

    private fun loop(partials: List<Triple<Int, Float, Float>>): FloatArray {
        val n = SAMPLE_RATE * 2
        val out = FloatArray(n)
        for ((freq, amp, phase) in partials) {
            for (i in 0 until n) out[i] += (amp * sin(2.0 * PI * freq * i / SAMPLE_RATE + phase)).toFloat()
        }
        return out
    }

    private fun engineCar(): FloatArray {
        val p = ArrayList<Triple<Int, Float, Float>>()
        // a ~55 Hz fundamental with a growly harmonic stack
        for (h in 1..14) p.add(Triple(55 * h, 0.5f / h.toFloat().pow(0.75f), h * 0.9f))
        // slight detune beating
        p.add(Triple(111, 0.18f, 0.3f)); p.add(Triple(166, 0.1f, 1.1f))
        val f = loop(p)
        // add periodic rumble noise
        val rnd = Noise(5)
        for (k in 0 until 40) {
            val fr = 90 + (rnd.next() * 0.5f + 0.5f) * 900
            val ph = rnd.next() * 3f
            for (i in f.indices) f[i] += 0.012f * sin(2.0 * PI * fr.toInt() * i / SAMPLE_RATE + ph).toFloat()
        }
        return f
    }

    private fun enginePlane(): FloatArray {
        val p = ArrayList<Triple<Int, Float, Float>>()
        val rnd = Noise(8)
        // broadband "air" made of many integer-frequency partials
        for (k in 0 until 140) {
            val fr = 500 + ((rnd.next() * 0.5f + 0.5f) * 5200f).toInt()
            p.add(Triple(fr, 0.014f + 0.01f * (rnd.next() * 0.5f + 0.5f), rnd.next() * 3.14f))
        }
        p.add(Triple(1320, 0.12f, 0f)); p.add(Triple(2640, 0.05f, 1f)); p.add(Triple(180, 0.12f, 0.4f)); p.add(Triple(360, 0.06f, 0.1f))
        return loop(p)
    }

    private fun engineRocket(): FloatArray {
        val p = ArrayList<Triple<Int, Float, Float>>()
        val rnd = Noise(13)
        for (k in 0 until 160) {
            val fr = 35 + ((rnd.next() * 0.5f + 0.5f) * 700f).toInt()
            p.add(Triple(fr, 0.018f + 0.016f * (rnd.next() * 0.5f + 0.5f) * (1f - fr / 800f), rnd.next() * 3.14f))
        }
        p.add(Triple(42, 0.25f, 0f)); p.add(Triple(63, 0.16f, 0.7f)); p.add(Triple(84, 0.1f, 1.4f))
        return loop(p)
    }
}
