package app.roadtoorbit.game

/** One-shot sound/feedback cues raised by the game; the platform layer plays them. */
enum class Sfx {
    COIN, NITRO, REPAIR, HIT, SOFT_HIT, TRANSFORM, TRANSFORM_DONE, RING, RING_MISS, NEAR_MISS,
    EXPLOSION, BEEP, GO, VICTORY, GAME_OVER, ZONE, BOOST_ON, TOUCHDOWN, UI,
}

/** Floating text shown by the HUD ("NEAR MISS +40", "PERFECT LAUNCH!"). */
class Popup(val text: String, val color: Int, val ttl: Float, val big: Boolean) {
    var age = 0f
}

enum class Phase { MENU, COUNTDOWN, RUN, CRASHING, GAME_OVER, FINALE, VICTORY }
