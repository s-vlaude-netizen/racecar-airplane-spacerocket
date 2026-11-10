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
import app.roadtoorbit.game.FinishTime
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import app.roadtoorbit.online.Leaderboards
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

/**
 * The best finish times (kept on the device in every build) and the online leaderboard (only where there is one): what the menu and the
 * results show, when the buttons that lead to a board are there and when they are not, and what is handed to the board and when.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LeaderboardUiTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val w = 1600
    private val h = 740

    private class FakeBoards : Leaderboards {
        var available = true
        val submitted = ArrayList<Triple<Int, Difficulty, Long>>()
        val shown = ArrayList<Pair<Int, Difficulty>>()
        override fun canShow(level: Int, difficulty: Difficulty) = available
        override fun submit(level: Int, difficulty: Difficulty, millis: Long) { submitted += Triple(level, difficulty, millis) }
        override fun show(level: Int, difficulty: Difficulty) { shown += level to difficulty }
        override var onAvailable: (() -> Unit)? = null
    }

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

    private fun text(hud: HudView, field: String): String = HudView::class.java.getDeclaredField(field).also { it.isAccessible = true }.get(hud) as String

    // ---- what the device keeps ---------------------------------------------------------------------------------------

    @Test fun bestTimesAndTheTimesThatWereSentAreKeptPerLevelAndDifficulty() {
        val prefs = Prefs(app)
        for (l in 0 until Levels.count) for (d in Difficulty.values()) {
            assertEquals(0L, prefs.bestMillis(l, d))
            assertEquals(0L, prefs.sentMillis(l, d))
        }
        prefs.setBestMillis(0, Difficulty.NORMAL, 185_140)
        prefs.setBestMillis(3, Difficulty.HARD, 301_500)
        prefs.setSentMillis(0, Difficulty.NORMAL, 190_000)
        assertEquals(185_140L, Prefs(app).bestMillis(0, Difficulty.NORMAL))
        assertEquals(301_500L, Prefs(app).bestMillis(3, Difficulty.HARD))
        assertEquals(0L, prefs.bestMillis(0, Difficulty.EASY))
        assertEquals(0L, prefs.bestMillis(3, Difficulty.NORMAL))
        assertEquals(190_000L, prefs.sentMillis(0, Difficulty.NORMAL))
        assertEquals(0L, prefs.sentMillis(3, Difficulty.HARD))
    }

    // ---- the menu -----------------------------------------------------------------------------------------------------

    @Test fun theMenuShowsTheBestTimeNextToTheBestScore() {
        val bridge = UiBridge()
        bridge.hud.latest.apply { phase = Phase.MENU; bestScore = 18_420; bestMillis = 0 }
        assertEquals("BEST  18,420", text(view(bridge).also { }, "menuBestLine"))
        bridge.hud.latest.apply { bestMillis = 185_140 }
        assertEquals("BEST  18,420   ·   BEST TIME  3:05.14", text(view(bridge), "menuBestLine"))
    }

    @Test fun theLeaderboardPillIsThereOnlyWhenABoardCanBeShownAndOpensTheBoardOfTheChosenLevelAndDifficulty() {
        val boards = FakeBoards().apply { available = false }
        val bridge = UiBridge().apply { leaderboards = boards }
        bridge.hud.latest.apply { phase = Phase.MENU; levelIndex = 2; levelName = "THE DEEP"; difficulty = Difficulty.HARD }
        var hud = view(bridge)
        assertTrue("no board: nothing to tap, nothing drawn", pill(hud, 5).isEmpty)

        boards.available = true
        hud = view(bridge)
        val board = pill(hud, 5)
        assertFalse("a board: the pill is there", board.isEmpty)
        assertTrue("inside the screen: $board", board.left >= 0 && board.right <= w && board.top >= 0 && board.bottom <= h)
        for (i in 0..4) assertFalse("it does not overlap pill $i", RectF.intersects(board, pill(hud, i)))
        tap(hud, board.centerX(), board.centerY())
        assertEquals("the board of the level and difficulty on the menu", listOf(2 to Difficulty.HARD), boards.shown)
        assertNull("and it does not start the game", bridge.actions.poll())

        // when the player changes level or difficulty it is another board
        bridge.hud.latest.apply { levelIndex = 0; difficulty = Difficulty.EASY }
        hud = view(bridge)
        tap(hud, pill(hud, 5).centerX(), pill(hud, 5).centerY())
        assertEquals(2 to Difficulty.HARD, boards.shown[0])
        assertEquals(0 to Difficulty.EASY, boards.shown[1])
    }

    @Test fun withoutABoardTheMenuIsExactlyAsItWas() {
        // the plain build, or no Google Play services, or nobody signed in: the same pills, the same taps
        val bridge = UiBridge()
        bridge.hud.latest.apply { phase = Phase.MENU }
        val hud = view(bridge)
        assertTrue(pill(hud, 5).isEmpty)
        for (i in 0..4) assertFalse("pill $i is drawn", pill(hud, i).isEmpty)
        tap(hud, 10f, 10f)
        assertEquals("a tap anywhere else plays", UiAction.PLAY, bridge.actions.poll())
    }

    // ---- the results --------------------------------------------------------------------------------------------------

    @Test fun theResultsShowTheTimeAndWhetherItIsTheBest() {
        val bridge = UiBridge()
        bridge.hud.latest.apply { phase = Phase.VICTORY; levelName = "THE MOON"; finishMillis = 185_140; bestMillis = 185_140; newBestTime = true }
        assertEquals("TIME  3:05.14   ·   NEW BEST TIME!", text(view(bridge), "timeLine"))
        bridge.hud.latest.apply { bestMillis = 170_000; newBestTime = false }
        assertEquals("TIME  3:05.14   ·   BEST  2:50.00", text(view(bridge), "timeLine"))
        bridge.hud.latest.apply { bestMillis = 0 }
        assertEquals("TIME  3:05.14", text(view(bridge), "timeLine"))
    }

    @Test fun theResultsOfARunWithoutARealTimeShowNone() {
        val bridge = UiBridge()
        bridge.hud.latest.apply { phase = Phase.VICTORY; finishMillis = 0 }
        assertEquals("", text(view(bridge), "timeLine"))
        bridge.hud.latest.apply { finishMillis = FinishTime.MIN_MILLIS - 1 }
        assertEquals("", text(view(bridge), "timeLine"))
    }

    @Test fun theResultsOfferTheLeaderboardOnlyWhenABoardCanBeShown() {
        val boards = FakeBoards().apply { available = false }
        val bridge = UiBridge().apply { leaderboards = boards }
        for (next in listOf("MARS", "")) {
            boards.shown.clear()
            boards.available = false
            bridge.hud.latest.apply { phase = Phase.VICTORY; levelIndex = 1; levelName = "MARS"; nextLevelName = next; difficulty = Difficulty.NORMAL; stars = 3 }
            var bs = buttons(view(bridge))
            assertTrue("no board, no button: $bs", bs.none { it.first == "LEADERBOARD" })

            boards.available = true
            val hud = view(bridge)
            bs = buttons(hud)
            val names = bs.map { it.first }
            assertTrue("the board: $names", "LEADERBOARD" in names)
            assertTrue("the others stay: $names", "PLAY AGAIN" in names && "MAIN MENU" in names)
            assertEquals("next level: $next", next.isNotEmpty(), names.any { it.startsWith("NEXT LEVEL") })
            for ((name, r) in bs) assertTrue("$name is on the screen: $r", r.left >= 0 && r.right <= w && r.top >= 0 && r.bottom <= h)
            for (i in bs.indices) for (j in i + 1 until bs.size) assertFalse("${bs[i].first} overlaps ${bs[j].first}", RectF.intersects(bs[i].second, bs[j].second))
            val r = bs.first { it.first == "LEADERBOARD" }.second
            tap(hud, r.centerX(), r.centerY())
            assertEquals("the board of the level and difficulty just played", listOf(1 to Difficulty.NORMAL), boards.shown)
            assertNull("and nothing else happens", bridge.actions.poll())
        }
    }

    // ---- what is handed to the board ----------------------------------------------------------------------------------

    private fun renderer(prefs: Prefs, bridge: UiBridge): GameRenderer {
        val silent = object : MusicControl {
            override fun request(track: MusicSynth.Track?) {}
            override fun release() {}
        }
        return GameRenderer(bridge, AudioEngine(app, soundOn = false, musicOn = false, music = silent), prefs, haptic = {}, applyRenderScale = {})
    }

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

    @Test fun aWinKeepsItsTimeOnTheDeviceAndOffersItToTheBoardOnce() {
        val boards = FakeBoards()
        val prefs = Prefs(app).also { it.difficulty = Difficulty.EASY.ordinal }
        val r = renderer(prefs, UiBridge().apply { leaderboards = boards })
        r.handle(UiAction.PLAY)
        winWithoutSteering(r)
        val millis = r.game.finishMillis
        assertTrue(FinishTime.isValid(millis))
        r.saveBests()
        r.saveBests()
        assertEquals("the time is kept", millis, prefs.bestMillis(0, Difficulty.EASY))
        assertEquals("the board is offered it once", listOf(Triple(0, Difficulty.EASY, millis)), boards.submitted)
        assertEquals("and it is remembered as sent", millis, prefs.sentMillis(0, Difficulty.EASY))
        assertEquals("only the level and difficulty that were played", 0L, prefs.bestMillis(0, Difficulty.NORMAL))
    }

    @Test fun aWinWithoutABoardStillKeepsItsTimeAndSendsNothing() {
        val boards = FakeBoards().apply { available = false }
        val prefs = Prefs(app).also { it.difficulty = Difficulty.EASY.ordinal }
        val r = renderer(prefs, UiBridge().apply { leaderboards = boards })
        r.handle(UiAction.PLAY)
        winWithoutSteering(r)
        r.saveBests()
        assertEquals(r.game.finishMillis, prefs.bestMillis(0, Difficulty.EASY))
        assertTrue(boards.submitted.isEmpty())
        assertEquals(0L, prefs.sentMillis(0, Difficulty.EASY))

        // the player signs in later: the time that was earned meanwhile is offered then, and not again
        boards.available = true
        r.boardSync.syncPending()
        r.boardSync.syncPending()
        assertEquals(listOf(Triple(0, Difficulty.EASY, r.game.finishMillis)), boards.submitted)
    }

    @Test fun aSlowerWinLeavesTheBestTimeAloneAndSendsNothing() {
        val boards = FakeBoards()
        val prefs = Prefs(app).also {
            it.difficulty = Difficulty.EASY.ordinal
            it.setBestMillis(0, Difficulty.EASY, 31_000) // (nobody is that fast: a time that the run will not beat)
        }
        val r = renderer(prefs, UiBridge().apply { leaderboards = boards })
        assertEquals("the best time of the level and difficulty is what the game compares with", 31_000L, r.game.bestMillis)
        r.handle(UiAction.PLAY)
        winWithoutSteering(r)
        assertFalse(r.game.newBestTime)
        r.saveBests()
        assertEquals(31_000L, prefs.bestMillis(0, Difficulty.EASY))
        assertTrue("nothing is offered that is not an improvement", boards.submitted.isEmpty())
    }
}
