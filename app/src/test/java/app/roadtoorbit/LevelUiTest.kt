package app.roadtoorbit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import app.roadtoorbit.audio.AudioEngine
import app.roadtoorbit.audio.MusicControl
import app.roadtoorbit.audio.MusicSynth
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Choosing a level on the menu, going on to the next one after a victory, and keeping the scores of each level apart. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LevelUiTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val w = 1600
    private val h = 740

    private fun view(bridge: UiBridge): HudView {
        val hud = HudView(app, bridge)
        hud.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        hud.layout(0, 0, w, h)
        hud.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))) // lays out the pills and buttons
        return hud
    }

    private fun tap(hud: HudView, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e = MotionEvent.obtain(t, t, action, x, y, 0)
            hud.onTouchEvent(e)
            e.recycle()
        }
    }

    private fun pill(hud: HudView, i: Int): RectF {
        val f = HudView::class.java.getDeclaredField("pills").also { it.isAccessible = true }
        return (f.get(hud) as Array<*>)[i] as RectF
    }

    /** The labels and rectangles of the overlay's buttons, by drawing order. */
    private fun buttons(hud: HudView): List<Pair<String, RectF>> {
        val arr = HudView::class.java.getDeclaredField("buttons").also { it.isAccessible = true }.get(hud) as Array<*>
        val count = HudView::class.java.getDeclaredField("buttonCount").also { it.isAccessible = true }.getInt(hud)
        return (0 until count).map {
            val b = arr[it]!!
            val label = b.javaClass.getDeclaredField("label").also { f -> f.isAccessible = true }.get(b) as String
            val r = b.javaClass.getDeclaredField("r").also { f -> f.isAccessible = true }.get(b) as RectF
            label to r
        }
    }

    @Test fun theMenuShowsTheLevelSelectorAndTappingItAsksForTheNextLevel() {
        val bridge = UiBridge()
        bridge.hud.latest.apply { phase = Phase.MENU; levelIndex = 0; levelName = "THE MOON"; nextLevelName = "MARS" }
        val hud = view(bridge)
        val level = pill(hud, 4)
        val difficulty = pill(hud, 3)
        assertFalse("the level pill must not overlap the difficulty pill", RectF.intersects(level, difficulty))
        assertTrue("both pills sit under the PLAY button, inside the screen", level.left > 0 && difficulty.right < w && level.top > h / 2f)
        tap(hud, level.centerX(), level.centerY())
        assertEquals(UiAction.CYCLE_LEVEL, bridge.actions.poll())
        assertNull(bridge.actions.poll())
        tap(hud, difficulty.centerX(), difficulty.centerY())
        assertEquals(UiAction.CYCLE_DIFFICULTY, bridge.actions.poll())
    }

    @Test fun victoryOffersTheNextLevelOnlyWhenThereIsOne() {
        val bridge = UiBridge()
        bridge.hud.latest.apply { phase = Phase.VICTORY; levelIndex = 0; levelName = "THE MOON"; nextLevelName = "MARS"; stars = 2 }
        var hud = view(bridge)
        var bs = buttons(hud)
        assertTrue("buttons: $bs", bs.any { it.first == "NEXT LEVEL: MARS" })
        val next = bs.first { it.first.startsWith("NEXT LEVEL") }.second
        assertTrue("PLAY AGAIN and MAIN MENU stay available", bs.any { it.first == "PLAY AGAIN" } && bs.any { it.first == "MAIN MENU" })
        for ((name, r) in bs) assertTrue("$name is on the screen: $r", r.left >= 0 && r.right <= w && r.top >= 0 && r.bottom <= h)
        for (i in bs.indices) for (j in i + 1 until bs.size) assertFalse("${bs[i].first} overlaps ${bs[j].first}", RectF.intersects(bs[i].second, bs[j].second))
        tap(hud, next.centerX(), next.centerY())
        assertEquals(UiAction.NEXT_LEVEL, bridge.actions.poll())

        // on the last level there is nowhere further to go
        bridge.hud.latest.apply { levelIndex = 1; levelName = "MARS"; nextLevelName = "" }
        hud = view(bridge)
        bs = buttons(hud)
        assertTrue("buttons: $bs", bs.none { it.first.startsWith("NEXT LEVEL") })
        assertEquals(listOf("PLAY AGAIN", "MAIN MENU"), bs.map { it.first })
    }

    @Test fun bestScoresAreKeptPerLevelAndDifficultyAndTheOldOnesSurvive() {
        val prefs = Prefs(app)
        // what an older version stored: one best per difficulty, and before that a single best (which counts as Normal)
        app.getSharedPreferences("road_to_orbit", android.content.Context.MODE_PRIVATE).edit().putInt("best", 7000).putInt("best_2", 9100).apply()
        assertEquals(7000, prefs.best(0, Difficulty.NORMAL))
        assertEquals(9100, prefs.best(0, Difficulty.HARD))
        assertEquals("the new level starts from nothing", 0, prefs.best(1, Difficulty.NORMAL))
        prefs.setBest(1, Difficulty.NORMAL, 25_000)
        prefs.setBest(0, Difficulty.EASY, 4_000)
        assertEquals(25_000, prefs.best(1, Difficulty.NORMAL))
        assertEquals(0, prefs.best(1, Difficulty.EASY))
        assertEquals(7000, prefs.best(0, Difficulty.NORMAL))
        assertEquals(4_000, prefs.best(0, Difficulty.EASY))
        assertEquals(0, prefs.level)
        prefs.level = 1
        assertEquals(1, Prefs(app).level)
        prefs.level = 99
        assertEquals("a level that does not exist falls back to the nearest one", Levels.count - 1, prefs.level)
        app.getSharedPreferences("road_to_orbit", android.content.Context.MODE_PRIVATE).edit().putInt("level", -5).apply()
        assertEquals(0, prefs.level)
    }

    // ---- the renderer's handling of the actions --------------------------------------------------------------------

    private fun renderer(prefs: Prefs): GameRenderer {
        val silent = object : MusicControl {
            override fun request(track: MusicSynth.Track?) {}
            override fun release() {}
        }
        return GameRenderer(UiBridge(), AudioEngine(app, soundOn = false, musicOn = false, music = silent), prefs, haptic = {}, applyRenderScale = {})
    }

    /** Plays a level to its victory without steering: the vehicle is simply made unbreakable. */
    private fun winWithoutSteering(r: GameRenderer) {
        val input = GameInput()
        var t = 0f
        while (r.game.phase != Phase.VICTORY && t < 600f) {
            r.game.player.invuln = 99f
            r.game.update(1f / 60f, input)
            while (r.game.pollSfx() != null) { /* drain */ }
            t += 1f / 60f
        }
        assertEquals(Phase.VICTORY, r.game.phase)
    }

    @Test fun theSavedLevelAndItsBestScoreAreRestoredAtStartup() {
        val prefs = Prefs(app)
        prefs.level = 1
        prefs.difficulty = Difficulty.HARD.ordinal
        prefs.setBest(1, Difficulty.HARD, 31_000)
        prefs.setBest(0, Difficulty.HARD, 5_000)
        val r = renderer(prefs)
        assertEquals(1, r.game.level.index)
        assertEquals(Difficulty.HARD, r.game.difficulty)
        assertEquals(31_000, r.game.bestScore)
    }

    @Test fun choosingALevelIsRememberedAndOnlyWorksInTheMenu() {
        val prefs = Prefs(app)
        val r = renderer(prefs)
        assertEquals(0, r.game.level.index)
        r.handle(UiAction.CYCLE_LEVEL)
        assertEquals(1, r.game.level.index)
        assertEquals(1, prefs.level)
        r.handle(UiAction.CYCLE_LEVEL)
        assertEquals("cycling wraps round", 0, r.game.level.index)
        assertEquals(0, prefs.level)

        // the best score shown is the one of the chosen level and difficulty
        prefs.setBest(1, Difficulty.NORMAL, 12_345)
        r.handle(UiAction.CYCLE_LEVEL)
        assertEquals(12_345, r.game.bestScore)
        r.handle(UiAction.CYCLE_LEVEL)
        assertEquals(0, r.game.bestScore)

        // a tap that arrives after the run has started changes nothing, not even the saved choice
        r.handle(UiAction.PLAY)
        assertTrue(r.game.phase != Phase.MENU)
        val level = r.game.level.index
        r.handle(UiAction.CYCLE_LEVEL)
        assertEquals(level, r.game.level.index)
        assertEquals(level, prefs.level)
    }

    @Test fun aLateDifficultyTapDoesNotChangeTheSavedChoice() {
        val prefs = Prefs(app)
        val r = renderer(prefs)
        val chosen = r.game.difficulty
        r.handle(UiAction.PLAY)
        r.handle(UiAction.CYCLE_DIFFICULTY)
        assertEquals(chosen, r.game.difficulty)
        assertEquals("the saved choice must still match the game", chosen.ordinal, prefs.difficulty)
    }

    @Test fun nextLevelAfterAVictoryStartsTheNextLevelAndTheLastLevelHasNoNext() {
        val prefs = Prefs(app)
        val r = renderer(prefs)
        r.handle(UiAction.PLAY)
        r.handle(UiAction.NEXT_LEVEL)
        assertEquals("NEXT_LEVEL does nothing before a victory", 0, r.game.level.index)

        winWithoutSteering(r)
        prefs.setBest(1, r.game.difficulty, 4_321)
        r.handle(UiAction.NEXT_LEVEL)
        assertEquals(1, r.game.level.index)
        assertEquals(1, prefs.level)
        assertEquals(Phase.COUNTDOWN, r.game.phase)
        assertEquals(0, r.game.legIndex)
        assertEquals("the best score of the new level", 4_321, r.game.bestScore)

        winWithoutSteering(r)
        r.handle(UiAction.NEXT_LEVEL)
        assertEquals("there is no level after the last", Phase.VICTORY, r.game.phase)
        assertEquals(1, r.game.level.index)
    }
}
