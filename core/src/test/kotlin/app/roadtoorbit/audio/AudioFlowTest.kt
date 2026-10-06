package app.roadtoorbit.audio

import app.roadtoorbit.audio.MusicSynth.Track
import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Phase
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioFlowTest {
    @Test
    fun policyMapsEveryPhaseAndRespectsTheSwitches() {
        assertEquals(Track.MENU, MusicPolicy.wanted(Phase.MENU, 0, true, true))
        for (phase in listOf(Phase.COUNTDOWN, Phase.RUN, Phase.CRASHING)) {
            assertEquals(Track.CAR, MusicPolicy.wanted(phase, 0, true, true))
            assertEquals(Track.PLANE, MusicPolicy.wanted(phase, 1, true, true))
            assertEquals(Track.ROCKET, MusicPolicy.wanted(phase, 2, true, true))
        }
        assertEquals(Track.FINALE, MusicPolicy.wanted(Phase.FINALE, 2, true, true))
        assertEquals(Track.FINALE, MusicPolicy.wanted(Phase.VICTORY, 2, true, true))
        assertNull(MusicPolicy.wanted(Phase.GAME_OVER, 1, true, true))
        for (phase in Phase.values()) {
            assertNull(MusicPolicy.wanted(phase, 0, soundOn = false, musicOn = true), "sound off silences $phase")
            assertNull(MusicPolicy.wanted(phase, 0, soundOn = true, musicOn = false), "music off silences $phase")
        }
    }

    /**
     * Drives the music exactly like the app does - one request per frame, in every phase - through whole
     * sessions, and checks the request only changes when the game moves on. The shipped bug asked for the
     * menu loop and cancelled it again on every single frame.
     */
    @Test
    fun theRequestChangesOnlyWhenThePhaseDoes() {
        val out = object : MusicScheduler.Output {
            val events = CopyOnWriteArrayList<String>()
            override fun start(track: Track, pcm: ShortArray) { events.add("start:$track") }
            override fun stop() { events.add("stop") }
        }
        val scheduler = MusicScheduler(out, { _ -> ShortArray(16) }, "music-flow")
        val game = Game(5)
        val input = GameInput()
        val dt = 1f / 60f

        var requests = 0
        var changes = 0
        var last: Track? = null
        var first = true
        fun frame() {
            game.update(dt, input)
            val wanted = MusicPolicy.wanted(game.phase, game.legIndex, true, true)
            scheduler.request(wanted)
            requests++
            if (first || wanted != last) changes++
            first = false
            last = wanted
        }

        repeat(60 * 6) { frame() } // sitting in the menu

        // a whole victory run
        game.startRun(21)
        val bot = Bot(game)
        var t = 0f
        while (t < 400f && !(game.phase == Phase.VICTORY && game.phaseTime > 3f)) {
            bot.control(input, dt)
            frame()
            t += dt
        }
        assertEquals(Phase.VICTORY, game.phase)
        repeat(60 * 3) { frame() }

        // back to the menu, then crash on purpose (a player who never steers hits traffic) and sit on game over
        game.toMenu()
        repeat(60 * 2) { frame() }
        game.startRun(22)
        input.neutral()
        t = 0f
        while (t < 120f && game.phase != Phase.GAME_OVER) {
            frame()
            t += dt
        }
        assertEquals(Phase.GAME_OVER, game.phase, "an idle player should crash")
        repeat(60 * 4) { frame() }

        // menu -> car -> plane -> rocket -> finale -> menu -> car -> silence is about eight changes
        assertTrue(requests > 10_000, "the session should span many frames ($requests)")
        assertTrue(changes <= 12, "the music request flipped $changes times in $requests frames")

        Thread.sleep(300)
        scheduler.release()
        assertTrue(scheduler.rendered <= Track.values().size, "each loop is synthesised at most once: ${scheduler.rendered}")
        val starts = out.events.count { it.startsWith("start") }
        assertTrue(starts <= 10, "music restarted $starts times: ${out.events}")
    }
}
