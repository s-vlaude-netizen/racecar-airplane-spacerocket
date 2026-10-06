package app.roadtoorbit.audio

import java.util.EnumMap
import java.util.EnumSet
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** What the audio engine needs from the music: state what should be audible, and let go at the end. */
interface MusicControl {
    /** The loop that should be audible now, or null for silence. Meant to be called every frame. */
    fun request(track: MusicSynth.Track?)

    fun release()
}

/**
 * Keeps the right music loop playing without ever doing real work on the caller's thread.
 *
 * Synthesising a loop takes about a second and ~13 MB of temporary memory, so it must never happen more than
 * once at a time (or more than once per loop). [request] only states what *should* be audible, is cheap and
 * idempotent, and is meant to be called every frame; a single worker thread turns the latest request into
 * sound. Requests that flip back and forth faster than a loop renders are coalesced into the latest one.
 */
class MusicScheduler(
    private val output: Output,
    private val render: (MusicSynth.Track) -> ShortArray = MusicSynth::render,
    private val threadName: String = "music",
) : MusicControl {
    /** Where the sound ends up (an AudioTrack on Android, a recorder in tests). Only the worker thread calls it. */
    interface Output {
        /** Starts looping [pcm], replacing whatever is playing. */
        fun start(track: MusicSynth.Track, pcm: ShortArray)

        fun stop()
    }

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    @Volatile private var wanted: MusicSynth.Track? = null

    // everything below is guarded by [lock], except [playing], which only the worker touches
    private var playing: MusicSynth.Track? = null
    private val cache = EnumMap<MusicSynth.Track, ShortArray>(MusicSynth.Track::class.java)
    private val failed = EnumSet.noneOf(MusicSynth.Track::class.java)
    private var worker: Thread? = null
    private var released = false

    /** How many loops have been synthesised so far (each loop at most once). */
    @Volatile var rendered = 0
        private set

    /** Safe to call every frame from any thread. */
    override fun request(track: MusicSynth.Track?) {
        if (track == wanted) return
        lock.withLock {
            if (released || track == wanted) return
            wanted = track
            if (worker == null && track != null) {
                worker = Thread(::run, threadName).apply { isDaemon = true; start() }
            }
            changed.signalAll()
        }
    }

    /** Stops the music and lets the worker thread end. */
    override fun release() {
        lock.withLock {
            released = true
            changed.signalAll()
        }
    }

    private class Job(val track: MusicSynth.Track?, val exit: Boolean)

    private fun hasWork(): Boolean {
        val w = wanted
        return w != playing && !(w != null && w in failed)
    }

    private fun nextJob(): Job = lock.withLock {
        while (!released && !hasWork()) changed.awaitUninterruptibly()
        Job(wanted, released)
    }

    private fun run() {
        while (true) {
            val job = nextJob()
            if (job.exit) {
                quietly { output.stop() }
                return
            }
            val track = job.track
            if (track == null) {
                quietly { output.stop() }
                playing = null
                continue
            }
            val pcm = lock.withLock { cache[track] } ?: synthesise(track) ?: continue
            // the game may have moved on while the loop was rendering
            if (lock.withLock { !released && wanted == track }) {
                quietly { output.start(track, pcm) }
                playing = track
            }
        }
    }

    private fun synthesise(track: MusicSynth.Track): ShortArray? {
        rendered++
        return try {
            render(track).also { pcm -> lock.withLock { cache[track] = pcm } }
        } catch (_: Throwable) {
            // music is optional: remember the failure instead of retrying every frame
            lock.withLock { failed.add(track) }
            null
        }
    }

    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // a broken audio device must never take the game down
        }
    }
}
