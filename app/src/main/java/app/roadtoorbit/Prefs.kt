package app.roadtoorbit

import android.content.Context
import app.roadtoorbit.game.Difficulty

/** Tiny SharedPreferences wrapper for the best score and settings. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("road_to_orbit", Context.MODE_PRIVATE)

    /** Index into [Difficulty.values]. */
    var difficulty: Int
        get() = sp.getInt("difficulty", Difficulty.NORMAL.ordinal).coerceIn(0, Difficulty.values().size - 1)
        set(v) = sp.edit().putInt("difficulty", v).apply()

    /** Best score per difficulty (the pre-difficulty single best counts as Normal). */
    fun best(d: Difficulty): Int = sp.getInt("best_${d.ordinal}", if (d == Difficulty.NORMAL) sp.getInt("best", 0) else 0)

    fun setBest(d: Difficulty, score: Int) = sp.edit().putInt("best_${d.ordinal}", score).apply()

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
