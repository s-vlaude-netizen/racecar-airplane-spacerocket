package app.roadtoorbit.online

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The plain build is the APK on GitHub: it has no leaderboard and none of Google's code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlainFlavorTest {
    private fun missing(className: String) {
        try {
            Class.forName(className)
            fail("$className is in the plain build")
        } catch (_: ClassNotFoundException) {
            // as it should be
        }
    }

    @Test fun thePlainBuildHasNoLeaderboard() {
        assertEquals("plain", OnlineFlavor.NAME)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertSame(NoLeaderboards, OnlineFlavor.leaderboards(activity))
    }

    @Test fun noneOfGooglesCodeIsInThePlainBuild() {
        missing("app.roadtoorbit.playgames.PlayGamesLeaderboards")
        missing("com.google.android.gms.games.PlayGames")
        missing("com.google.android.gms.games.PlayGamesSdk")
        missing("com.google.android.gms.common.GoogleApiAvailability")
        missing("com.google.android.gms.tasks.Task")
    }
}
