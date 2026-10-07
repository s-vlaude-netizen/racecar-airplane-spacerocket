package app.roadtoorbit

import android.content.Context
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.Levels

/** Tiny SharedPreferences wrapper for the best score and settings. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("road_to_orbit", Context.MODE_PRIVATE)

    /** Index into [Difficulty.values]. */
    var difficulty: Int
        get() = sp.getInt("difficulty", Difficulty.NORMAL.ordinal).coerceIn(0, Difficulty.values().size - 1)
        set(v) = sp.edit().putInt("difficulty", v).apply()

    /** Index into [Levels.ALL]: the level chosen on the menu. */
    var level: Int
        get() = sp.getInt("level", 0).coerceIn(0, Levels.count - 1)
        set(v) = sp.edit().putInt("level", v.coerceIn(0, Levels.count - 1)).apply()

    /**
     * Best score per level and difficulty. The first level keeps the keys it had before there were levels (and the
     * single best from before there were difficulties counts as Normal), so nobody loses a score by updating.
     */
    fun best(level: Int, d: Difficulty): Int =
        if (level <= 0) sp.getInt("best_${d.ordinal}", if (d == Difficulty.NORMAL) sp.getInt("best", 0) else 0)
        else sp.getInt("best_l${level + 1}_${d.ordinal}", 0)

    fun setBest(level: Int, d: Difficulty, score: Int) =
        sp.edit().putInt(if (level <= 0) "best_${d.ordinal}" else "best_l${level + 1}_${d.ordinal}", score).apply()

    var soundOn: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    /** Fraction of the native resolution the 3D view renders at (adaptive; 1.0 = full). */
    var renderScale: Float
        get() = sp.getFloat("scale", 1f)
        set(v) = sp.edit().putFloat("scale", v).apply()

    var musicOn: Boolean
        get() = sp.getBoolean("music", true)
        set(v) = sp.edit().putBoolean("music", v).apply()

    var tiltOn: Boolean
        get() = sp.getBoolean("tilt", false)
        set(v) = sp.edit().putBoolean("tilt", v).apply()
}
