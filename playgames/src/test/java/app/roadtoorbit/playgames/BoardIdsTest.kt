package app.roadtoorbit.playgames

import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardIdsTest {
    private val ids = BoardIds(
        " 123456789012 ",
        arrayOf(
            arrayOf("", "MOON_NORMAL", " MOON_HARD "),
            arrayOf("MARS_EASY", "", "   "),
            arrayOf("", "", ""),
        ),
    )

    @Test fun blankIdsMeanThereIsNoBoardAndTheApplicationIdKeepsTheFeatureOff() {
        assertTrue(ids.configured)
        assertEquals("123456789012", ids.appId)
        assertNull(ids.board(0, Difficulty.EASY))
        assertEquals("MOON_NORMAL", ids.board(0, Difficulty.NORMAL))
        assertEquals("spaces around a pasted id do no harm", "MOON_HARD", ids.board(0, Difficulty.HARD))
        assertEquals("MARS_EASY", ids.board(1, Difficulty.EASY))
        assertNull(ids.board(1, Difficulty.NORMAL))
        assertNull("only spaces is blank too", ids.board(1, Difficulty.HARD))
        assertNull(ids.board(2, Difficulty.NORMAL))
        assertEquals(listOf("MOON_NORMAL", "MOON_HARD", "MARS_EASY"), ids.allBoards())
        assertFalse(BoardIds("", arrayOf(arrayOf("A", "B", "C"))).configured)
        assertFalse(BoardIds("  ", arrayOf(arrayOf("A", "B", "C"))).configured)
    }

    @Test fun aLevelThatIsNotThereHasNoBoard() {
        assertNull(ids.board(3, Difficulty.NORMAL))
        assertNull(ids.board(99, Difficulty.NORMAL))
        assertNull(ids.board(-1, Difficulty.NORMAL))
    }

    @Test fun theResourcesHaveTheIdsOfEveryLevel() {
        val fromResources = BoardIds.fromResources(RuntimeEnvironment.getApplication())
        assertEquals("a new level needs its three leaderboard ids in res/values/play_games_ids.xml and in BoardIds", Levels.count, fromResources.levels)
    }

    /** Catches a slip when the ids are pasted in from the Play Console: they are what they should look like, and no two boards share one. */
    @Test fun theEnteredIdsLookRight() {
        val fromResources = BoardIds.fromResources(RuntimeEnvironment.getApplication())
        if (fromResources.configured) assertTrue("the application id is digits only: '${fromResources.appId}'", Regex("[0-9]{6,}").matches(fromResources.appId))
        val boards = fromResources.allBoards()
        for (b in boards) assertTrue("a leaderboard id is letters, digits, - and _: '$b'", Regex("[A-Za-z0-9_-]{8,}").matches(b))
        assertEquals("every board has its own id", boards.size, boards.toSet().size)
    }
}
