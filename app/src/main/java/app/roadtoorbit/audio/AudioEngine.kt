package app.roadtoorbit.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.Sfx
import app.roadtoorbit.gfx.VehicleMode
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * Plays the synthesised sounds through a [SoundPool]. The WAV files are generated once into the
 * app's cache directory (on a background thread) and loaded from there, so the APK ships no audio.
 * Engine hum is a looping stream whose pitch follows the vehicle's speed.
 */
class AudioEngine(private val context: Context, soundOn: Boolean) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val sampleIds = IntArray(SoundId.values().size)
    @Volatile private var enabled = soundOn

    private var engineStream = 0
    private var engineSound: SoundId? = null
    private var engineRate = 1f
    private var engineVolume = 0f

    private var lastCoinNanos = 0L
    private var coinChain = 0

    init {
        Thread({ prepare() }, "sfx-synth").apply { isDaemon = true; start() }
    }

    private fun prepare() {
        try {
            val dir = File(context.cacheDir, "sfx_v1")
            dir.mkdirs()
            for (id in SoundId.values()) {
                val file = File(dir, id.name.lowercase() + ".wav")
                if (!file.exists() || file.length() < 100) {
                    val tmp = File(dir, id.name.lowercase() + ".tmp")
                    tmp.writeBytes(SoundSynth.wav(SoundSynth.render(id)))
                    tmp.renameTo(file)
                }
                sampleIds[id.ordinal] = pool.load(file.absolutePath, 1)
            }
        } catch (_: Throwable) {
            // audio is optional: a failure here must never take the game down
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) silenceEngine()
    }

    fun play(sfx: Sfx) {
        if (!enabled) return
        val (id, volume, rate) = when (sfx) {
            Sfx.COIN -> {
                val now = System.nanoTime()
                coinChain = if (now - lastCoinNanos < 450_000_000L) min(coinChain + 1, 8) else 0
                lastCoinNanos = now
                Triple(SoundId.COIN, 0.55f, 1f + coinChain * 0.06f)
            }
            Sfx.NITRO -> Triple(SoundId.NITRO, 0.7f, 1f)
            Sfx.REPAIR -> Triple(SoundId.REPAIR, 0.7f, 1f)
            Sfx.HIT -> Triple(SoundId.HIT, 1f, 1f)
            Sfx.SOFT_HIT -> Triple(SoundId.SOFT_HIT, 0.7f, 1f)
            Sfx.TRANSFORM -> Triple(SoundId.TRANSFORM, 0.9f, 1f)
            Sfx.TRANSFORM_DONE -> Triple(SoundId.TRANSFORM_DONE, 0.8f, 1f)
            Sfx.RING -> Triple(SoundId.RING, 0.7f, 1f)
            Sfx.RING_MISS -> Triple(SoundId.RING_MISS, 0.5f, 1f)
            Sfx.NEAR_MISS -> Triple(SoundId.NEAR_MISS, 0.5f, 1f)
            Sfx.EXPLOSION -> Triple(SoundId.EXPLOSION, 1f, 1f)
            Sfx.BEEP -> Triple(SoundId.BEEP, 0.7f, 1f)
            Sfx.GO -> Triple(SoundId.GO, 0.8f, 1f)
            Sfx.VICTORY -> Triple(SoundId.VICTORY, 0.9f, 1f)
            Sfx.GAME_OVER -> Triple(SoundId.GAME_OVER, 0.8f, 1f)
            Sfx.ZONE -> Triple(SoundId.ZONE, 0.7f, 1f)
            Sfx.BOOST_ON -> Triple(SoundId.BOOST, 0.7f, 1f)
            Sfx.TOUCHDOWN -> Triple(SoundId.TOUCHDOWN, 0.9f, 1f)
            Sfx.UI -> Triple(SoundId.UI, 0.6f, 1f)
        }
        val sample = sampleIds[id.ordinal]
        if (sample != 0) pool.play(sample, volume, volume, 1, 0, rate)
    }

    /** Called every frame on the GL thread: keeps the right engine loop running at the right pitch. */
    fun updateEngine(game: Game) {
        if (!enabled) return
        val p = game.player
        val active = when (game.phase) {
            Phase.RUN, Phase.COUNTDOWN, Phase.FINALE -> true
            else -> false
        }
        if (!active) {
            silenceEngine()
            return
        }
        val mode = p.visual.dominant
        val wanted = when (mode) {
            VehicleMode.CAR -> SoundId.ENGINE_CAR
            VehicleMode.PLANE -> SoundId.ENGINE_PLANE
            VehicleMode.ROCKET -> SoundId.ENGINE_ROCKET
        }
        val leg = game.leg
        val frac = if (game.phase == Phase.COUNTDOWN) 0.25f + 0.2f * kotlin.math.sin(game.phaseTime * 9f).coerceAtLeast(0f)
        else (p.speed / (leg.speedEnd * 1.35f)).coerceIn(0f, 1.2f)
        val rate = when (mode) {
            VehicleMode.CAR -> 0.55f + 1.15f * frac
            VehicleMode.PLANE -> 0.8f + 0.55f * frac
            VehicleMode.ROCKET -> 0.75f + 0.45f * frac
        }.coerceIn(0.5f, 2f)
        val volume = (when (mode) {
            VehicleMode.CAR -> 0.28f
            VehicleMode.PLANE -> 0.22f
            VehicleMode.ROCKET -> 0.30f
        } + 0.12f * p.boostFactor) * (if (game.xf.active) 0.6f else 1f)

        if (engineSound != wanted || engineStream == 0) {
            if (engineStream != 0) pool.stop(engineStream)
            val sample = sampleIds[wanted.ordinal]
            if (sample == 0) return
            engineStream = pool.play(sample, volume, volume, 2, -1, rate)
            engineSound = wanted
            engineRate = rate
            engineVolume = volume
            return
        }
        if (abs(rate - engineRate) > 0.015f) {
            pool.setRate(engineStream, rate)
            engineRate = rate
        }
        if (abs(volume - engineVolume) > 0.015f) {
            pool.setVolume(engineStream, volume, volume)
            engineVolume = volume
        }
    }

    fun silenceEngine() {
        if (engineStream != 0) {
            pool.stop(engineStream)
            engineStream = 0
        }
        engineSound = null
    }

    fun release() {
        silenceEngine()
        pool.release()
    }
}
