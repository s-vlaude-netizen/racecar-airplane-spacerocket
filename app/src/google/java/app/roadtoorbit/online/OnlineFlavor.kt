package app.roadtoorbit.online

import android.app.Activity
import app.roadtoorbit.playgames.PlayGamesLeaderboards

/**
 * The Google Play build: the leaderboard of Google Play Games. It is only a handle: everything that knows Google's libraries is in the
 * :playgames module, and on a device without Google Play services (or without the ids entered) it is as silent as NoLeaderboards.
 */
internal object OnlineFlavor {
    const val NAME = "google"

    fun leaderboards(activity: Activity): Leaderboards = PlayGamesLeaderboards(activity).also { it.start() }
}
