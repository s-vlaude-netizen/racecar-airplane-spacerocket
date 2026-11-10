package app.roadtoorbit

import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.FinishTime
import app.roadtoorbit.game.Levels
import app.roadtoorbit.online.Leaderboards

/**
 * Hands the player's best finish times to the online leaderboards, when there are any and the player can use them. The local best times
 * are the truth and are kept either way; the leaderboard only ever receives copies. What has been handed over is remembered per level and
 * difficulty, so that a time earned while the leaderboard was not available (not signed in, no Google Play services yet) is sent as soon as
 * it is, and nothing is sent twice.
 */
class BoardSync(private val prefs: Prefs, private val boards: () -> Leaderboards) {
    /** A run has just been finished faster than ever before: offer it (the leaderboard keeps the best of what it is given anyway). */
    fun finished(level: Int, difficulty: Difficulty, millis: Long) {
        if (FinishTime.isValid(millis)) send(level, difficulty, millis)
    }

    /** Offers every local best time that the leaderboard has not been given yet; called when the boards have become available. */
    fun syncPending() {
        for (level in 0 until Levels.count) for (d in Difficulty.values()) {
            val best = prefs.bestMillis(level, d)
            val sent = prefs.sentMillis(level, d)
            if (FinishTime.isValid(best) && (sent <= 0L || best < sent)) send(level, d, best)
        }
    }

    private fun send(level: Int, difficulty: Difficulty, millis: Long) {
        val b = boards()
        if (!b.canShow(level, difficulty)) return
        b.submit(level, difficulty, millis)
        prefs.setSentMillis(level, difficulty, millis)
    }
}
