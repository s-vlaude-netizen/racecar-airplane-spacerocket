package app.roadtoorbit.online

import android.app.Activity
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.Levels
import app.roadtoorbit.playgames.PlayGamesLeaderboards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Google Play build has the Play Games leaderboard, and it has to be as quiet as no leaderboard where it cannot work: on a device
 * without Google Play services (Robolectric is one), and while the ids of the Play Console are not entered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleFlavorTest {
    @Test fun theGooglePlayBuildHasThePlayGamesLeaderboardAndItStaysHiddenWithoutGooglePlayServices() {
        assertEquals("google", OnlineFlavor.NAME)
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        val boards = OnlineFlavor.leaderboards(controller.get())
        assertTrue(boards is PlayGamesLeaderboards)
        boards.onResume()
        for (l in 0 until Levels.count) for (d in Difficulty.values()) {
            assertFalse("level $l $d", boards.canShow(l, d))
            boards.submit(l, d, 123_456)
            boards.show(l, d)
        }
        boards.onPause()
        boards.onDestroy()
    }
}
