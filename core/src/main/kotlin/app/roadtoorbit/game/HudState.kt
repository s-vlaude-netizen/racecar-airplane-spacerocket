package app.roadtoorbit.game

import app.roadtoorbit.gfx.VehicleMode
import app.roadtoorbit.math.Mathx
import kotlin.math.pow

/**
 * A plain-data snapshot of everything the HUD / menus need. The game thread fills it each frame via
 * [Game.fillHud]; the UI thread reads the most recently published copy ([HudBuffer]). Fixed-size
 * arrays only, so filling it never allocates.
 */
class HudState {
    var phase = Phase.MENU
    var levelIndex = 0
    var levelName = ""
    /** The level after this one, or "" when this is the last. */
    var nextLevelName = ""
    var legIndex = 0
    var legName = ""
    var legSubtitle = ""
    var legLength = 2800f
    var legZoneLength = 320f
    var mode = VehicleMode.CAR
    var journey = 0f
    var legProgress = 0f
    var zoneProgress = 0f
    var distanceToGate = 0f

    var score = 0
    var bestScore = 0
    var coins = 0
    var rings = 0
    var ringStreak = 0
    var nearMisses = 0

    var health = 0
    var maxHealth = Tuning.MAX_HEALTH
    var difficulty = Difficulty.NORMAL
    var boost = 0f
    var boosting = false
    var invulnerable = false

    var speedKmh = 0
    var altitudeKm = 0f
    var runTime = 0f

    var transformReady = false
    var transforming = false
    var countdown = 0
    var flash = 0f
    var damageFlash = 0f
    var shake = 0f

    var stars = 0
    var newBest = false
    var breakdownBase = 0
    var breakdownHealth = 0
    var breakdownTime = 0

    var popupCount = 0
    val popupText = Array(MAX_POPUPS) { "" }
    val popupColor = IntArray(MAX_POPUPS)
    val popupAge = FloatArray(MAX_POPUPS)
    val popupTtl = FloatArray(MAX_POPUPS)
    val popupBig = BooleanArray(MAX_POPUPS)

    companion object {
        const val MAX_POPUPS = 5
    }
}

/** Two alternating [HudState]s: the game fills one while the UI reads the other. */
class HudBuffer {
    private val a = HudState()
    private val b = HudState()
    private var writeToA = true

    @Volatile
    var latest: HudState = a
        private set

    /** Called by the game thread once per frame. */
    fun publish(game: Game) {
        val target = if (writeToA) a else b
        game.fillHud(target)
        latest = target
        writeToA = !writeToA
    }
}

internal fun Game.fillHudInternal(h: HudState) {
    val l = leg
    h.phase = phase
    h.levelIndex = level.index
    h.levelName = level.name
    h.nextLevelName = if (level.index + 1 < Levels.count) Levels[level.index + 1].name else ""
    h.legIndex = legIndex
    h.legName = l.name
    h.legSubtitle = l.subtitle
    h.legLength = l.length
    h.legZoneLength = l.zoneLength
    h.mode = player.visual.dominant
    h.journey = journeyProgress
    h.legProgress = Mathx.clamp01(legDist / l.length)
    h.zoneProgress = zoneProgress
    h.distanceToGate = distanceToGate
    h.score = score
    h.bestScore = bestScore
    h.coins = coins
    h.rings = rings
    h.ringStreak = ringStreak
    h.nearMisses = nearMisses
    h.health = player.health
    h.maxHealth = maxHealth
    h.difficulty = difficulty
    h.boost = player.boostMeter
    h.boosting = player.boosting
    h.invulnerable = player.blink > 0f
    h.speedKmh = (player.speed * l.displayFactor).toInt()
    h.altitudeKm = altitudeKm()
    h.runTime = runTime
    h.transformReady = transformReady
    h.transforming = xf.active
    h.countdown = countdown
    h.flash = flash
    h.damageFlash = damageFlash
    h.shake = shake
    h.stars = stars
    h.newBest = newBest
    val bd = lastScoreBreakdown
    h.breakdownBase = if (bd.size >= 3) bd[0] else 0
    h.breakdownHealth = if (bd.size >= 3) bd[1] else 0
    h.breakdownTime = if (bd.size >= 3) bd[2] else 0
    val n = minOf(popups.size, HudState.MAX_POPUPS)
    h.popupCount = n
    for (i in 0 until n) {
        val p = popups[popups.size - n + i]
        h.popupText[i] = p.text
        h.popupColor[i] = p.color
        h.popupAge[i] = p.age
        h.popupTtl[i] = p.ttl
        h.popupBig[i] = p.big
    }
}

/** Fictional altitude: sea level → 100 km (edge of space) in the sky leg, then on toward the level's destination. */
internal fun Game.altitudeKm(): Float = when {
    phase == Phase.FINALE || phase == Phase.VICTORY -> level.destinationKm
    legIndex == 0 -> 0f
    legIndex == 1 -> 100f * Mathx.smoothstep(0f, 1f, Mathx.clamp01(legDist / leg.length))
    else -> 100f + (level.destinationKm - 100f) * Mathx.clamp01(legDist / leg.length).pow(2.2f)
}
