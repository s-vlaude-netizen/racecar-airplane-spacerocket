package app.roadtoorbit.playgames

import android.content.Context
import app.roadtoorbit.game.Difficulty

/**
 * The ids of the Play Games project and of its leaderboards, one per level and difficulty (res/values/play_games_ids.xml). A blank id means
 * "not set up": without an application id the whole feature stays off, and a level and difficulty without a leaderboard id has no board.
 */
internal class BoardIds(appId: String, private val boards: Array<Array<String>>) {
    val appId: String = appId.trim()

    /** Whether the Play Games project is entered at all; the SDK is not started for a game that is not set up. */
    val configured: Boolean get() = this.appId.isNotEmpty()

    /** How many levels the ids are there for (a new level needs its three ids added to the resources). */
    val levels: Int get() = boards.size

    /** The leaderboard of [level] (an index into `Levels`) at [difficulty], or null when there is none. */
    fun board(level: Int, difficulty: Difficulty): String? =
        boards.getOrNull(level)?.getOrNull(difficulty.ordinal)?.trim()?.takeIf { it.isNotEmpty() }

    /** Every leaderboard id that is entered. */
    fun allBoards(): List<String> = boards.flatMap { it.toList() }.map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /** The ids in this module's resources, in the order of `Levels.ALL` and then `Difficulty.values()` (easy, normal, hard). */
        fun fromResources(context: Context): BoardIds {
            val r = context.resources
            fun s(id: Int): String = r.getString(id)
            return BoardIds(
                s(R.string.pg_app_id),
                arrayOf(
                    arrayOf(s(R.string.pg_board_moon_easy), s(R.string.pg_board_moon_normal), s(R.string.pg_board_moon_hard)),
                    arrayOf(s(R.string.pg_board_mars_easy), s(R.string.pg_board_mars_normal), s(R.string.pg_board_mars_hard)),
                    arrayOf(s(R.string.pg_board_deep_easy), s(R.string.pg_board_deep_normal), s(R.string.pg_board_deep_hard)),
                    arrayOf(s(R.string.pg_board_homecoming_easy), s(R.string.pg_board_homecoming_normal), s(R.string.pg_board_homecoming_hard)),
                ),
            )
        }
    }
}
