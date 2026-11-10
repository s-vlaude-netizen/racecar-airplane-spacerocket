package app.roadtoorbit.online

import android.app.Activity

/** The plain build (the APK on GitHub): no Google code, no leaderboard, no network. */
internal object OnlineFlavor {
    const val NAME = "plain"

    @Suppress("UNUSED_PARAMETER")
    fun leaderboards(activity: Activity): Leaderboards = NoLeaderboards
}
