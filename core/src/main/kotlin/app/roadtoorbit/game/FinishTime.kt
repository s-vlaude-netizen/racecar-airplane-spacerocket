package app.roadtoorbit.game

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The time it took to finish a level: the run clock from GO to the gate at the end of the last leg, in milliseconds. The clock does not
 * run during the countdown, in the pause menu or in the finale, and a retry after a crash does not stop it, so the time of the lost attempt
 * counts too. The same number is the player's best time on the device and the score of an online leaderboard (smaller is better).
 */
object FinishTime {
    /** No real run is faster than this; anything below is a bug (or tampering) and is neither kept nor sent anywhere. */
    const val MIN_MILLIS = 30_000L

    /** A day: nobody plays that long, and a leaderboard should not be able to hold a number that is clearly wrong. */
    const val MAX_MILLIS = 24L * 3_600_000L

    fun millisOf(seconds: Float): Long = if (seconds.isFinite()) (seconds * 1000.0).roundToLong() else 0L

    fun isValid(millis: Long): Boolean = millis in MIN_MILLIS..MAX_MILLIS

    /** "3:05.14": minutes, seconds and hundredths (an hour is 60:00.00, nobody gets there). */
    fun format(millis: Long): String {
        val ms = max(0L, millis)
        val minutes = ms / 60_000L
        val seconds = (ms / 1_000L) % 60L
        val hundredths = (ms / 10L) % 100L
        val sb = StringBuilder(10)
        sb.append(minutes).append(':')
        if (seconds < 10L) sb.append('0')
        sb.append(seconds).append('.')
        if (hundredths < 10L) sb.append('0')
        sb.append(hundredths)
        return sb.toString()
    }
}
