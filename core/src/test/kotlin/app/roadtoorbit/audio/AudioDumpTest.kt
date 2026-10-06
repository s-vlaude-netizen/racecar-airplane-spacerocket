package app.roadtoorbit.audio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Writes every sound and music loop as a WAV under build/audio so they can be auditioned or analysed. */
class AudioDumpTest {
    @Test fun dumpWavs() {
        val dir = File(System.getProperty("audioDir") ?: "build/audio")
        dir.mkdirs()
        for (id in SoundId.values()) File(dir, "sfx_${id.name.lowercase()}.wav").writeBytes(SoundSynth.wav(SoundSynth.render(id)))
        for (t in MusicSynth.Track.values()) File(dir, "music_${t.name.lowercase()}.wav").writeBytes(SoundSynth.wav(MusicSynth.render(t)))
        assertTrue(dir.listFiles()!!.size >= SoundId.values().size + MusicSynth.Track.values().size)
    }
}
