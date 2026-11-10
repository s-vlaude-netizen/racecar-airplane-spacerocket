package app.roadtoorbit.playgames

import android.app.Activity
import android.util.Log
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.online.Leaderboards

/**
 * The leaderboards on Google Play Games. They are an extra: the game is complete without them and every way for them to be unavailable
 * (no Google Play services, as on LineageOS or microG, a game that is not set up in the Play Console, a player who is not signed in, no
 * network, anything Google's code throws) just means [canShow] is false, quietly. Nothing here ever lets an exception out, and nothing here
 * touches Google's code until it is known that the services are there and that the ids are entered.
 */
class PlayGamesLeaderboards internal constructor(
    private val ids: BoardIds,
    private val backend: GamesBackend,
) : Leaderboards {
    /** The real thing: this device's Google Play services and the ids in this module's resources. */
    constructor(activity: Activity) : this(BoardIds.fromResources(activity), RealGamesBackend(activity))

    @Volatile private var started = false
    @Volatile private var failed = false
    @Volatile private var signedIn = false
    @Volatile private var destroyed = false

    override var onAvailable: (() -> Unit)? = null

    override fun canShow(level: Int, difficulty: Difficulty): Boolean = signedIn && !failed && ids.board(level, difficulty) != null

    override fun submit(level: Int, difficulty: Difficulty, millis: Long) = quietly {
        val board = ids.board(level, difficulty) ?: return@quietly
        if (signedIn && !failed && millis > 0L) backend.submit(board, millis)
    }

    override fun show(level: Int, difficulty: Difficulty) = quietly {
        val board = ids.board(level, difficulty) ?: return@quietly
        if (signedIn && !failed) backend.open(board)
    }

    /**
     * Starts Google's SDK as early as possible (the game calls this when its screen is created, before the screen is shown: the SDK signs the
     * player in by itself when it sees the first screen), if the services are there and the game is set up, and asks who is signed in.
     */
    fun start() = onResume()

    /** The screen is in front: the place to find out whether the services are there and the player is signed in (they may have changed it). */
    override fun onResume() = quietly {
        if (destroyed) return@quietly
        if (failed || !ids.configured) {
            signedIn = false
            return@quietly
        }
        if (!started) {
            if (!backend.servicesAvailable()) {
                signedIn = false
                return@quietly
            }
            backend.initialize()
            started = true
        }
        backend.checkSignedIn { ok -> quietly { setSignedIn(ok) } }
    }

    override fun onDestroy() {
        destroyed = true
        onAvailable = null
        signedIn = false
    }

    private fun setSignedIn(ok: Boolean) {
        if (destroyed) return // (an answer that arrives after the screen has gone)
        val was = signedIn
        signedIn = ok && !failed
        if (signedIn && !was) onAvailable?.invoke()
    }

    /** Runs [block], and if it throws anything, switches the leaderboards off for the rest of the session. */
    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            failed = true
            signedIn = false
            Log.w(TAG, "the Play Games leaderboard is switched off: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private companion object {
        const val TAG = "RoadToOrbit"
    }
}
