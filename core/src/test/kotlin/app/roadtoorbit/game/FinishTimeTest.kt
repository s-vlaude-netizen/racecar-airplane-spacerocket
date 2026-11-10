package app.roadtoorbit.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The finish time of a level: how it is written, which numbers count, and how the game keeps the best one. */
class FinishTimeTest {
    private val dt = 1f / 60f

    @Test fun timesAreWrittenAsMinutesSecondsAndHundredths() {
        assertEquals("0:00.00", FinishTime.format(0))
        assertEquals("0:05.14", FinishTime.format(5_140))
        assertEquals("0:05.01", FinishTime.format(5_019))
        assertEquals("3:05.14", FinishTime.format(185_140))
        assertEquals("0:59.99", FinishTime.format(59_999))
        assertEquals("1:00.00", FinishTime.format(60_000))
        assertEquals("12:34.56", FinishTime.format(12 * 60_000L + 34_567))
        assertEquals("60:00.00", FinishTime.format(3_600_000))
        assertEquals("0:00.00", FinishTime.format(-5), "nothing is ever negative")
    }

    @Test fun secondsBecomeWholeMillisecondsAndNonsenseBecomesNothing() {
        assertEquals(3_500L, FinishTime.millisOf(3.5f))
        assertEquals(185_140L, FinishTime.millisOf(185.14f))
        assertEquals(0L, FinishTime.millisOf(Float.NaN))
        assertEquals(0L, FinishTime.millisOf(Float.POSITIVE_INFINITY))
        assertEquals(0L, FinishTime.millisOf(Float.NEGATIVE_INFINITY))
    }

    @Test fun onlyRealTimesCount() {
        assertFalse(FinishTime.isValid(0))
        assertFalse(FinishTime.isValid(-1))
        assertFalse(FinishTime.isValid(FinishTime.MIN_MILLIS - 1))
        assertTrue(FinishTime.isValid(FinishTime.MIN_MILLIS))
        assertTrue(FinishTime.isValid(185_140))
        assertTrue(FinishTime.isValid(FinishTime.MAX_MILLIS))
        assertFalse(FinishTime.isValid(FinishTime.MAX_MILLIS + 1))
    }

    private fun finish(level: LevelSpec, best: Long, seed: Long = 1): Game {
        val game = Game(seed)
        game.level = level
        game.difficulty = Difficulty.EASY
        game.bestMillis = best
        val input = GameInput()
        val bot = Bot(game)
        game.startRun(seed)
        var t = 0f
        while (t < 600f && game.phase != Phase.VICTORY && game.phase != Phase.GAME_OVER) {
            bot.control(input, dt)
            game.update(dt, input)
            while (game.pollSfx() != null) { /* drain */ }
            t += dt
        }
        assertEquals(Phase.VICTORY, game.phase, "${level.name}: the bot should win on EASY")
        return game
    }

    @Test fun everyLevelRecordsARealTimeWhenItIsWon() {
        for (level in Levels.ALL) {
            val game = finish(level, best = 0)
            assertTrue(FinishTime.isValid(game.finishMillis), "${level.name}: the time ${game.finishMillis} is a real one")
            assertEquals(FinishTime.millisOf(game.runTime), game.finishMillis, "the time is the run clock at the end of the last leg")
            assertTrue(game.finishMillis > level.parSeconds * 1000L * 0.4f, "${level.name}: ${game.finishMillis} ms is not a plausible time")
            assertTrue(game.newBestTime, "the first win is the best")
            assertEquals(game.finishMillis, game.bestMillis)
        }
    }

    @Test fun onlyAFasterTimeBeatsTheBest() {
        val level = Levels[0]
        val first = finish(level, best = 0)
        val slower = finish(level, best = first.finishMillis - 1_000)
        assertFalse(slower.newBestTime, "a time that is not faster than the best is not a new best")
        assertEquals(first.finishMillis - 1_000, slower.bestMillis, "and leaves the best alone")
        assertEquals(first.finishMillis, slower.finishMillis, "the run itself is the same (same seed, same pilot)")

        val equal = finish(level, best = first.finishMillis)
        assertFalse(equal.newBestTime, "the same time is not better")

        val faster = finish(level, best = first.finishMillis + 60_000)
        assertTrue(faster.newBestTime)
        assertEquals(faster.finishMillis, faster.bestMillis)
    }

    @Test fun theHudSeesTheTimes() {
        val game = finish(Levels[0], best = 0)
        val h = HudState()
        game.fillHud(h)
        assertEquals(game.finishMillis, h.finishMillis)
        assertEquals(game.bestMillis, h.bestMillis)
        assertTrue(h.newBestTime)
    }

    @Test fun aCrashRecordsNoTimeAndALaterRunStartsClean() {
        val game = Game(11)
        game.bestMillis = 123_456
        val input = GameInput()
        game.startRun(11)
        var t = 0f
        while (t < 120f && game.phase != Phase.GAME_OVER) { game.update(dt, input); t += dt }
        assertEquals(Phase.GAME_OVER, game.phase)
        assertEquals(0L, game.finishMillis)
        assertFalse(game.newBestTime)
        assertEquals(123_456L, game.bestMillis)
    }

    @Test fun theClockKeepsRunningThroughARetryAndStartsAtZeroOnANewRun() {
        val game = Game(11)
        val input = GameInput()
        game.startRun(11)
        var t = 0f
        while (t < 120f && game.phase != Phase.GAME_OVER) { game.update(dt, input); t += dt }
        assertEquals(Phase.GAME_OVER, game.phase)
        val lost = game.runTime
        assertTrue(lost > 5f, "some time passed before the crash: $lost")
        game.retryLeg()
        assertEquals(lost, game.runTime, "the lost attempt still counts")
        var t2 = 0f
        while (t2 < 6f && game.phase != Phase.RUN) { game.update(dt, input); t2 += dt }
        for (i in 0 until 120) game.update(dt, input)
        assertTrue(game.runTime > lost + 1f, "and the clock goes on from there")
        game.startRun(12)
        assertEquals(0f, game.runTime)
        assertEquals(0L, game.finishMillis)
    }
}
