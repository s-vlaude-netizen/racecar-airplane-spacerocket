package app.roadtoorbit

import android.content.Context

/** Tiny SharedPreferences wrapper for the best score and settings. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("road_to_orbit", Context.MODE_PRIVATE)

    var bestScore: Int
        get() = sp.getInt("best", 0)
        set(v) = sp.edit().putInt("best", v).apply()

    var soundOn: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    var tiltOn: Boolean
        get() = sp.getBoolean("tilt", false)
        set(v) = sp.edit().putBoolean("tilt", v).apply()
}
