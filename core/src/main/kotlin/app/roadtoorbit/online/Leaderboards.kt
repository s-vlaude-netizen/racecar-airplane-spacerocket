package app.roadtoorbit.online

import app.roadtoorbit.game.Difficulty

/**
 * An optional online leaderboard of finish times, one board per level and difficulty (the speed of a run depends on the difficulty, so
 * their times cannot be ranked together). The game never depends on it: it is complete and plays the same without one, and the local best
 * times are kept either way. [NoLeaderboards] is what the plain build has; the Play Games build says yes only when Google Play services are
 * there, the player is signed in and the board is set up, and everything else (no services, not signed in, no network, any failure) means
 * "no", silently, and the buttons that lead to a board are not drawn.
 *
 * Unless said otherwise the methods may be called from any thread and none of them throws.
 */
interface Leaderboards {
    /** Whether the board of [level] (an index into `Levels`) at [difficulty] can be shown and submitted to right now. Cheap; called every frame. */
    fun canShow(level: Int, difficulty: Difficulty): Boolean

    /** Offers a finish time in milliseconds (smaller is better); the service keeps the best one. Does nothing when [canShow] is false. */
    fun submit(level: Int, difficulty: Difficulty, millis: Long)

    /** Opens the service's own screen for the board. Main thread. Does nothing when [canShow] is false. */
    fun show(level: Int, difficulty: Difficulty)

    /** The game's screen is in front again (the player may have signed in or out meanwhile), goes away, is gone. Main thread. */
    fun onResume() {}
    fun onPause() {}
    fun onDestroy() {}

    /** Set by the host: called on the main thread when boards may have become available (the player has signed in). */
    var onAvailable: (() -> Unit)?
}

/** No leaderboard at all: the offline game. */
object NoLeaderboards : Leaderboards {
    override fun canShow(level: Int, difficulty: Difficulty) = false
    override fun submit(level: Int, difficulty: Difficulty, millis: Long) {}
    override fun show(level: Int, difficulty: Difficulty) {}
    override var onAvailable: (() -> Unit)?
        get() = null
        set(_) {} // (an object lives for ever: it must not keep the host alive)
}
