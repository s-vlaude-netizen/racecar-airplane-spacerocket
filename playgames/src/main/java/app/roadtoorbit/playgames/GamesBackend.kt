package app.roadtoorbit.playgames

import android.app.Activity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk

/**
 * The few things the leaderboard needs from Google Play Games, and the only code that calls Google's classes. Kept this small so that the
 * rest ([PlayGamesLeaderboards]) can be tested without Google Play services, with a fake. Any of these may throw; the caller catches.
 */
internal interface GamesBackend {
    /** Whether Google Play services are on the device and usable (not missing, disabled, out of date or broken). */
    fun servicesAvailable(): Boolean

    /** Starts the Play Games SDK (which then signs the player in by itself if they have a Play Games profile). Once per process. */
    fun initialize()

    /** Asks whether the player is signed in; [callback] gets the answer on the main thread, or not at all when the services never answer. */
    fun checkSignedIn(callback: (Boolean) -> Unit)

    /** Hands a time to the board; the service queues it when there is no network and keeps the player's best. */
    fun submit(boardId: String, millis: Long)

    /** Opens the service's own screen of the board. */
    fun open(boardId: String)
}

internal class RealGamesBackend(private val activity: Activity) : GamesBackend {
    override fun servicesAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS

    override fun initialize() = PlayGamesSdk.initialize(activity.applicationContext)

    override fun checkSignedIn(callback: (Boolean) -> Unit) {
        PlayGames.getGamesSignInClient(activity).isAuthenticated().addOnCompleteListener { task ->
            callback(task.isSuccessful && task.result?.isAuthenticated == true)
        }
    }

    override fun submit(boardId: String, millis: Long) {
        PlayGames.getLeaderboardsClient(activity).submitScore(boardId, millis)
    }

    override fun open(boardId: String) {
        PlayGames.getLeaderboardsClient(activity).getLeaderboardIntent(boardId).addOnSuccessListener { intent ->
            if (!activity.isFinishing && !activity.isDestroyed) activity.startActivityForResult(intent, REQUEST_BOARD)
        }
    }

    private companion object {
        const val REQUEST_BOARD = 9004
    }
}
