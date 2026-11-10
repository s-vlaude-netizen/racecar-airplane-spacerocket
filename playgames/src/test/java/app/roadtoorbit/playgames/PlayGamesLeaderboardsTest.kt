package app.roadtoorbit.playgames

import android.app.Activity
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.Levels
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The leaderboard has to be there when Google Play Games is, and silent when anything is not: no Google Play services (LineageOS), a game
 * that is not set up in the Play Console, a player who is not signed in, an exception in Google's code. A fake stands in for Google; one test
 * at the end runs the real backend on a device without Google Play services (which is what Robolectric is).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayGamesLeaderboardsTest {
    private class Fake : GamesBackend {
        var services = true
        var signedIn = false
        var answerNow = true
        var pending: ((Boolean) -> Unit)? = null
        var servicesCalls = 0
        var initializeCalls = 0
        var failInitialize: Throwable? = null
        var failCheck: Throwable? = null
        var failSubmit: Throwable? = null
        var failOpen: Throwable? = null
        val submitted = ArrayList<Pair<String, Long>>()
        val opened = ArrayList<String>()

        override fun servicesAvailable(): Boolean { servicesCalls++; return services }
        override fun initialize() { initializeCalls++; failInitialize?.let { throw it } }
        override fun checkSignedIn(callback: (Boolean) -> Unit) {
            failCheck?.let { throw it }
            if (answerNow) callback(signedIn) else pending = callback
        }
        override fun submit(boardId: String, millis: Long) { failSubmit?.let { throw it }; submitted += boardId to millis }
        override fun open(boardId: String) { failOpen?.let { throw it }; opened += boardId }
    }

    private val boards = arrayOf(
        arrayOf("", "MOON_NORMAL", "MOON_HARD"),
        arrayOf("MARS_EASY", "MARS_NORMAL", ""),
        arrayOf("", "", ""),
        arrayOf("HOME_EASY", "HOME_NORMAL", "HOME_HARD"),
    )

    private fun setUp(fake: Fake = Fake(), appId: String = "123456789012") = fake to PlayGamesLeaderboards(BoardIds(appId, boards), fake)

    private fun assertNothingShown(b: PlayGamesLeaderboards) {
        for (l in 0 until Levels.count + 1) for (d in Difficulty.values()) assertFalse("level $l $d", b.canShow(l, d))
    }

    @Test fun aGameThatIsNotSetUpNeverTouchesGoogle() {
        val (fake, b) = setUp(appId = "")
        fake.signedIn = true
        b.onResume()
        b.onResume()
        assertEquals("not even a look at whether the services are there", 0, fake.servicesCalls)
        assertEquals(0, fake.initializeCalls)
        assertNothingShown(b)
        b.submit(0, Difficulty.NORMAL, 100_000)
        b.show(0, Difficulty.NORMAL)
        assertTrue(fake.submitted.isEmpty() && fake.opened.isEmpty())
    }

    @Test fun withoutGooglePlayServicesTheLeaderboardIsHiddenAndQuiet() {
        val (fake, b) = setUp(Fake().apply { services = false; signedIn = true })
        b.onResume()
        b.onResume()
        assertEquals("Google's SDK is not started on a device that cannot run it", 0, fake.initializeCalls)
        assertNothingShown(b)
        b.submit(0, Difficulty.NORMAL, 100_000)
        b.show(0, Difficulty.NORMAL)
        assertTrue(fake.submitted.isEmpty() && fake.opened.isEmpty())
        b.onPause()
        b.onDestroy()
    }

    @Test fun theServicesThatAppearLaterAreFoundOnTheNextResume() {
        val (fake, b) = setUp(Fake().apply { services = false; signedIn = true })
        b.onResume()
        assertNothingShown(b)
        fake.services = true
        b.onResume()
        assertEquals(1, fake.initializeCalls)
        assertTrue(b.canShow(0, Difficulty.NORMAL))
        b.onResume()
        assertEquals("the SDK is started once", 1, fake.initializeCalls)
    }

    @Test fun theSdkIsStartedAsSoonAsTheGameAsksAndNotAgainWhenTheScreenComesToTheFront() {
        val (fake, b) = setUp(Fake().apply { signedIn = true })
        b.start()
        assertEquals(1, fake.initializeCalls)
        assertTrue(b.canShow(0, Difficulty.NORMAL))
        b.onResume()
        assertEquals(1, fake.initializeCalls)
    }

    @Test fun aPlayerWhoIsNotSignedInSeesNothing() {
        val (fake, b) = setUp(Fake().apply { signedIn = false })
        b.onResume()
        assertEquals(1, fake.initializeCalls)
        assertNothingShown(b)
        b.submit(0, Difficulty.NORMAL, 100_000)
        b.show(0, Difficulty.NORMAL)
        assertTrue("nothing is sent for a player who is not signed in", fake.submitted.isEmpty() && fake.opened.isEmpty())
    }

    @Test fun aSignedInPlayerGetsTheBoardsThatAreSetUpAndNoOthers() {
        val (fake, b) = setUp(Fake().apply { signedIn = true })
        b.onResume()
        assertTrue(b.canShow(0, Difficulty.NORMAL))
        assertTrue(b.canShow(0, Difficulty.HARD))
        assertFalse("no id for this one", b.canShow(0, Difficulty.EASY))
        assertFalse("a level without any", b.canShow(2, Difficulty.NORMAL))
        assertFalse("a level that is not there", b.canShow(9, Difficulty.NORMAL))
        assertTrue(b.canShow(3, Difficulty.EASY))

        b.submit(0, Difficulty.NORMAL, 185_140)
        b.submit(0, Difficulty.EASY, 190_000)
        b.submit(3, Difficulty.HARD, 201_500)
        b.submit(3, Difficulty.NORMAL, 0)
        b.submit(3, Difficulty.NORMAL, -5)
        assertEquals(listOf("MOON_NORMAL" to 185_140L, "HOME_HARD" to 201_500L), fake.submitted)
        b.show(1, Difficulty.NORMAL)
        b.show(1, Difficulty.HARD)
        assertEquals(listOf("MARS_NORMAL"), fake.opened)
    }

    @Test fun beingSignedInIsAnnouncedWhenItHappensAndNotAgain() {
        val (fake, b) = setUp(Fake().apply { signedIn = false })
        var announced = 0
        b.onAvailable = { announced++ }
        b.onResume()
        assertEquals(0, announced)
        fake.signedIn = true
        b.onResume()
        assertEquals(1, announced)
        b.onResume()
        assertEquals("already known", 1, announced)
        fake.signedIn = false
        b.onResume()
        assertNothingShown(b)
        fake.signedIn = true
        b.onResume()
        assertEquals("signed in again", 2, announced)
        assertTrue(b.canShow(0, Difficulty.NORMAL))
    }

    @Test fun signingOutInThePlayGamesAppHidesTheBoardsWhenTheGameComesBack() {
        val (fake, b) = setUp(Fake().apply { signedIn = true })
        b.onResume()
        assertTrue(b.canShow(0, Difficulty.NORMAL))
        fake.signedIn = false
        b.onResume()
        assertNothingShown(b)
    }

    @Test fun anAnswerThatNeverComesLeavesTheBoardsHidden() {
        val (fake, b) = setUp(Fake().apply { answerNow = false; signedIn = true })
        b.onResume()
        assertNothingShown(b)
        assertTrue(fake.pending != null)
    }

    @Test fun anAnswerThatComesAfterTheScreenIsGoneDoesNothing() {
        val (fake, b) = setUp(Fake().apply { answerNow = false; signedIn = true })
        var announced = 0
        b.onAvailable = { announced++ }
        b.onResume()
        b.onDestroy()
        fake.pending!!.invoke(true)
        assertNothingShown(b)
        assertEquals(0, announced)
        b.onResume()
        assertEquals("a destroyed one is not started again", 1, fake.initializeCalls)
    }

    @Test fun anExceptionInGoogleCodeSwitchesTheBoardsOffAndNeverEscapes() {
        val failures = listOf<Throwable>(
            IllegalStateException("The meta-data tag in your app's AndroidManifest.xml does not have the right value"),
            SecurityException("Unknown calling package name"),
            NoClassDefFoundError("com/google/android/gms/games/PlayGames"),
            UnsatisfiedLinkError("no gms"),
        )
        for (failure in failures) {
            val (fake, b) = setUp(Fake().apply { signedIn = true; failInitialize = failure })
            b.onResume()
            b.onResume()
            assertEquals("${failure.javaClass.simpleName}: tried once, then left alone", 1, fake.initializeCalls)
            assertNothingShown(b)
            b.submit(0, Difficulty.NORMAL, 100_000)
            b.show(0, Difficulty.NORMAL)
            assertTrue(fake.submitted.isEmpty() && fake.opened.isEmpty())
        }
    }

    @Test fun failuresWhileAskingSubmittingOrOpeningAreSwallowedToo() {
        run {
            val (_, b) = setUp(Fake().apply { signedIn = true; failCheck = RuntimeException("boom") })
            b.onResume()
            assertNothingShown(b)
        }
        run {
            val (fake, b) = setUp(Fake().apply { signedIn = true; failSubmit = RuntimeException("boom") })
            b.onResume()
            assertTrue(b.canShow(0, Difficulty.NORMAL))
            b.submit(0, Difficulty.NORMAL, 100_000)
            assertNothingShown(b)
            assertTrue(fake.submitted.isEmpty())
        }
        run {
            val (_, b) = setUp(Fake().apply { signedIn = true; failOpen = RuntimeException("boom") })
            b.onResume()
            b.show(0, Difficulty.NORMAL)
            assertNothingShown(b)
        }
        run {
            // the host's own callback fails
            val (_, b) = setUp(Fake().apply { signedIn = true })
            b.onAvailable = { throw RuntimeException("boom") }
            b.onResume()
            assertNothingShown(b)
        }
    }

    @Test fun onADeviceWithoutGooglePlayServicesTheRealBackendStaysQuiet() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertNotEquals(
            "this test is about a device without Google Play services",
            ConnectionResult.SUCCESS, GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity),
        )
        // the game is set up (ids entered), the device has no Google Play services: LineageOS, microG, a tablet without Google
        val b = PlayGamesLeaderboards(BoardIds("123456789012", boards), RealGamesBackend(activity))
        b.onResume()
        b.onResume()
        assertNothingShown(b)
        b.submit(0, Difficulty.NORMAL, 100_000)
        b.show(0, Difficulty.NORMAL)
        b.onPause()
        b.onDestroy()

        // the ids of this module's own resources (which may or may not be entered yet)
        val own = PlayGamesLeaderboards(activity)
        own.onResume()
        assertNothingShown(own)
        own.onDestroy()
    }
}
