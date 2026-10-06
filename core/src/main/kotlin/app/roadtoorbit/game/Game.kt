package app.roadtoorbit.game

import app.roadtoorbit.gfx.Col
import app.roadtoorbit.gfx.ParticleSystem
import app.roadtoorbit.gfx.VehicleMode
import app.roadtoorbit.gfx.VehicleModel
import app.roadtoorbit.math.Mathx
import app.roadtoorbit.math.Rng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** State of the in-progress vehicle transformation. */
class TransformState {
    var active = false
    var t = 0f
    var from = VehicleMode.CAR
    var to = VehicleMode.PLANE
}

private class Checkpoint(val score: Int, val coins: Int, val rings: Int, val nearMisses: Int)

/**
 * The whole game simulation: pure Kotlin, deterministic for a given seed and input stream, no
 * platform dependencies. The host calls [update] once per frame and reads the public state to
 * render, play sounds and draw the HUD.
 *
 * The world is a "treadmill": the player stays at z = 0 and world objects stream toward +z.
 */
class Game(seed: Long = 1L) {
    // ---- public, read-only-ish state ----------------------------------------------------------
    var phase = Phase.MENU
        private set
    var phaseTime = 0f
        private set
    val player = Player()
    val entities = ArrayList<Entity>(320)
    val particles = ParticleSystem(2400)
    val popups = ArrayList<Popup>()
    val look = WorldLook()
    val xf = TransformState()

    var legIndex = 0
        private set
    var legDist = 0f
        private set
    /** Total distance scrolled in this run; drives terrain and road scrolling. */
    var travelled = 0.0
        private set

    var score = 0
        private set
    var coins = 0
        private set
    var rings = 0
        private set
    var ringStreak = 0
        private set
    var nearMisses = 0
        private set
    var runTime = 0f
        private set

    var countdown = 0
        private set
    var transformReady = false
        private set
    var zoneProgress = 0f
        private set
    var shake = 0f
        private set
    var flash = 0f
        private set
    var damageFlash = 0f
        private set
    var timeScale = 1f
        private set
    var clock = 0f
        private set
    var menuTime = 0f
        private set
    var finaleT = 0f
        private set
    var finaleGroundY = -300f
        private set

    var bestScore = 0
    var newBest = false
        private set
    var stars = 0
        private set
    var lastScoreBreakdown = IntArray(0)
        private set
    /** The hazard that last damaged the player (for feedback and diagnostics). */
    var lastHit: Entity? = null
        private set
    var lastHitLegDist = 0f
        private set

    // ---- internals ---------------------------------------------------------------------------
    internal var rng = Rng(seed)
    private var seed = seed
    internal var spawnCursor = 0f
    internal var decorCursor = 0f
    internal var gateSpawned = false
    private var zoneAnnounced = false
    private var checkpoint = Checkpoint(0, 0, 0, 0)
    private var countdownLen = Tuning.COUNTDOWN_SECONDS
    private var scoreAcc = 0f
    private var wheelSpin = 0f
    private var sparkAcc = 0f
    private var dustAcc = 0f
    private val spawner = Spawner(this)
    private val pool = ArrayList<Entity>(256)
    private val sfxQueue = ArrayDeque<Sfx>()
    private var perfectLaunch = false

    val leg: LegSpec get() = Tuning.LEGS[legIndex]
    val journeyProgress: Float
        get() = when (phase) {
            Phase.FINALE, Phase.VICTORY -> 1f
            else -> ((legIndex + Mathx.clamp01(legDist / leg.length)) / Tuning.LEGS.size)
        }

    /** Metres of road/flight left before the leg's gate. */
    val distanceToGate: Float get() = max(0f, leg.length - legDist)

    init {
        player.reset(VehicleMode.CAR, 0f, 0f)
    }

    // ---- host-facing API ----------------------------------------------------------------------

    /** Copies everything the HUD needs into [h] (allocation-free). */
    fun fillHud(h: HudState) = fillHudInternal(h)

    fun pollSfx(): Sfx? = sfxQueue.removeFirstOrNull()

    fun clearSfx() = sfxQueue.clear()

    internal fun sfx(s: Sfx) {
        if (sfxQueue.size < 24) sfxQueue.addLast(s)
    }

    fun startRun(newSeed: Long = System.nanoTime()) {
        seed = newSeed
        rng = Rng(seed)
        score = 0; coins = 0; rings = 0; ringStreak = 0; nearMisses = 0; runTime = 0f
        scoreAcc = 0f
        newBest = false; stars = 0
        travelled = 0.0
        checkpoint = Checkpoint(0, 0, 0, 0)
        particles.clear()
        popups.clear()
        beginLeg(0, Tuning.COUNTDOWN_SECONDS)
    }

    /** After a crash: restart the current leg from its checkpoint. */
    fun retryLeg() {
        score = checkpoint.score; coins = checkpoint.coins; rings = checkpoint.rings
        nearMisses = checkpoint.nearMisses; ringStreak = 0
        scoreAcc = 0f
        newBest = false
        particles.clear()
        popups.clear()
        beginLeg(legIndex, 1.6f)
    }

    fun toMenu() {
        phase = Phase.MENU
        phaseTime = 0f
        menuTime = 0f
        legIndex = 0
        legDist = 0f
        clearWorld()
        particles.clear()
        popups.clear()
        xf.active = false
        player.reset(VehicleMode.CAR, 0f, 0f)
        player.visual.flame = 0f
    }

    private fun beginLeg(index: Int, countdownSeconds: Float) {
        legIndex = index
        legDist = 0f
        val l = Tuning.LEGS[index]
        clearWorld()
        val startY = when (l.mode) {
            VehicleMode.CAR -> 0f
            VehicleMode.PLANE -> Tuning.PLANE_TAKEOFF_Y
            VehicleMode.ROCKET -> 14f
        }
        player.reset(l.mode, startY, if (index == 0) 0f else l.speedStart)
        xf.active = false
        spawnCursor = l.firstPatternAt
        decorCursor = -40f
        gateSpawned = false
        zoneAnnounced = false
        transformReady = false
        zoneProgress = 0f
        countdownLen = countdownSeconds
        countdown = ceil(countdownSeconds).toInt()
        phase = Phase.COUNTDOWN
        phaseTime = 0f
        timeScale = 1f
        look.update(this)
        spawner.fill()
        if (index == 0) spawner.startLine()
    }

    private fun clearWorld() {
        for (e in entities) {
            e.alive = false
            pool.add(e)
        }
        entities.clear()
    }

    internal fun obtain(kind: Kind): Entity {
        val e = if (pool.isNotEmpty()) pool.removeAt(pool.size - 1) else Entity()
        e.reset(kind)
        entities.add(e)
        return e
    }

    // ---- main update -------------------------------------------------------------------------

    fun update(dtReal: Float, input: GameInput) {
        val dt = min(dtReal, 0.05f)
        clock += dt
        phaseTime += dt
        shake = max(0f, shake - dt * 2.4f)
        flash = max(0f, flash - dt * 2.2f)
        damageFlash = max(0f, damageFlash - dt * 1.6f)
        for (i in popups.indices.reversed()) {
            popups[i].age += dt
            if (popups[i].age >= popups[i].ttl) popups.removeAt(i)
        }

        when (phase) {
            Phase.MENU -> updateMenu(dt)
            Phase.COUNTDOWN -> updateCountdown(dt, input)
            Phase.RUN -> updateRun(dt, input)
            Phase.CRASHING -> updateCrashing(dt)
            Phase.GAME_OVER -> updateGameOver(dt)
            Phase.FINALE -> updateFinale(dt)
            Phase.VICTORY -> updateVictory(dt)
        }
        look.update(this)
    }

    // ---- menu ----------------------------------------------------------------------------------

    private fun updateMenu(dt: Float) {
        menuTime += dt
        val v = player.visual
        // cycle car -> plane -> rocket -> car, holding each form for a moment
        val hold = 2.2f
        val morphTime = 1.6f
        val step = hold + morphTime
        val cycle = menuTime % (step * 3f)
        val idx = (cycle / step).toInt().coerceIn(0, 2)
        val local = cycle - idx * step
        val from = VehicleMode.values()[idx]
        val to = VehicleMode.values()[(idx + 1) % 3]
        if (local < hold) {
            v.settle(from)
        } else {
            v.from = from; v.to = to; v.morph = Mathx.clamp01((local - hold) / morphTime)
        }
        val dom = v.dominant
        v.flame = if (dom == VehicleMode.CAR) 0.15f else 0.8f
        v.time = menuTime
        wheelSpin += 120f * dt
        v.wheelSpinDeg = wheelSpin
        v.steerDeg = 0f
        player.mode = dom
        player.x = 0f; player.y = 0f; player.yaw = 0f; player.roll = 0f; player.pitch = 0f
        player.speed = 0f
    }

    // ---- countdown -----------------------------------------------------------------------------

    private fun updateCountdown(dt: Float, input: GameInput) {
        input.takeTransform()
        val remaining = countdownLen - phaseTime
        val c = ceil(remaining).toInt()
        if (c != countdown && c > 0) {
            countdown = c
            sfx(Sfx.BEEP)
        }
        player.visual.time = clock
        player.visual.flame = if (player.mode == VehicleMode.CAR) 0.1f else 0.6f
        particlesUpdate(dt, 0f)
        if (remaining <= 0f) {
            countdown = 0
            phase = Phase.RUN
            phaseTime = 0f
            sfx(Sfx.GO)
            popup("GO!", COLOR_GOOD, 0.9f, true)
            if (player.mode == VehicleMode.CAR) player.speed = 0f
        }
    }

    // ---- run -----------------------------------------------------------------------------------

    private fun updateRun(dtReal: Float, input: GameInput) {
        var ts = 1f
        if (xf.active) {
            xf.t += dtReal / Tuning.TRANSFORM_SECONDS
            ts = Mathx.lerp(0.5f, 1f, Mathx.smoothstep(0.15f, 1f, xf.t))
        }
        timeScale = ts
        val dt = dtReal * ts
        runTime += dtReal

        val pressed = input.takeTransform()
        stepPlayer(dt, input)

        val adv = player.speed * dt
        legDist += adv
        travelled += adv
        scoreAcc += adv * Tuning.SCORE_PER_METRE
        if (scoreAcc >= 1f) {
            val whole = scoreAcc.toInt()
            score += whole
            scoreAcc -= whole
        }

        look.update(this)
        updateEntities(dt)
        spawner.update()
        if (player.alive && !xf.active) collide(dt)
        updateZone(pressed)
        if (xf.active && xf.t >= 1f) finishTransform()
        updateFx(dt)
        if (phase == Phase.RUN && legIndex == 2 && legDist >= leg.length && !xf.active) beginFinale()
    }

    private fun stepPlayer(dt: Float, input: GameInput) {
        val p = player
        val l = leg
        p.invuln = max(0f, p.invuln - dt)
        p.speedPenalty = min(1f, p.speedPenalty + Tuning.PENALTY_RECOVERY * dt)

        val wantsBoost = input.boost && p.boostMeter > 0.02f
        if (wantsBoost && !p.boosting) sfx(Sfx.BOOST_ON)
        p.boosting = wantsBoost
        p.boostMeter = Mathx.clamp01(p.boostMeter + (if (wantsBoost) -Tuning.BOOST_DRAIN else Tuning.BOOST_REGEN) * dt)
        p.boostFactor = Mathx.damp(p.boostFactor, if (wantsBoost) 1f else 0f, 6f, dt)

        val prog = Mathx.clamp01(legDist / l.length)
        val base = Mathx.lerp(l.speedStart, l.speedEnd, prog)
        val target = base * (1f + l.boostPower * p.boostFactor) * p.speedPenalty
        val accel = if (l.mode == VehicleMode.CAR) 26f else 34f
        p.speed = Mathx.approach(p.speed, target, accel * dt)

        val v = p.visual
        v.time = clock
        when (l.mode) {
            VehicleMode.CAR -> stepCar(dt, input)
            VehicleMode.PLANE -> stepFlyer(dt, input, Tuning.PLANE_MAX_X, Tuning.PLANE_MIN_Y, Tuning.PLANE_MAX_Y, Tuning.PLANE_LATERAL_SPEED, Tuning.PLANE_VERTICAL_SPEED, Tuning.PLANE_ACCEL, 42f, 17f)
            VehicleMode.ROCKET -> stepFlyer(dt, input, Tuning.ROCKET_MAX_X, Tuning.ROCKET_MIN_Y, Tuning.ROCKET_MAX_Y, Tuning.ROCKET_LATERAL_SPEED, Tuning.ROCKET_VERTICAL_SPEED, Tuning.ROCKET_ACCEL, 24f, 12f)
        }

        // transformation overrides: take-off arc and nose-up swoop
        if (xf.active) {
            val e = Mathx.clamp01(xf.t)
            v.from = xf.from; v.to = xf.to; v.morph = e
            if (xf.from == VehicleMode.CAR) {
                p.y = Tuning.PLANE_TAKEOFF_Y * Mathx.easeOutCubic(min(1f, e * 1.25f))
                p.vy = 0f
                p.pitch += 20f * sin(PI.toFloat() * Mathx.clamp01(e * 1.4f))
            } else {
                p.pitch += 24f * sin(PI.toFloat() * e)
            }
        }

        v.wheelSpinDeg = wheelSpin
        val dom = v.dominant
        val flameBase = when (dom) {
            VehicleMode.CAR -> p.boostFactor * 0.9f
            VehicleMode.PLANE -> 0.55f + 0.5f * p.boostFactor
            VehicleMode.ROCKET -> 0.7f + 0.5f * p.boostFactor
        }
        v.flame = flameBase
        v.visible = p.alive && !(p.invuln > 0f && !xf.active && ((clock * 14f).toInt() % 2 == 0))
    }

    private fun stepCar(dt: Float, input: GameInput) {
        val p = player
        val target = input.steerX * Tuning.CAR_LATERAL_SPEED
        val reversing = p.vx * target < 0f
        p.vx = Mathx.approach(p.vx, target, Tuning.CAR_LATERAL_ACCEL * (if (reversing) 1.4f else 1f) * dt)
        p.x += p.vx * dt
        if (p.x > Tuning.CAR_MAX_X || p.x < -Tuning.CAR_MAX_X) {
            val side = Mathx.sign(p.x)
            p.x = side * Tuning.CAR_MAX_X
            if (abs(p.vx) > 5f && p.vx * side > 0f) {
                // scraping the guard rail: sparks, a little shake, lose some lateral speed
                shake = max(shake, 0.25f)
                railSparks(side)
                p.speedPenalty = min(p.speedPenalty, 0.93f)
            }
            p.vx = if (p.vx * side > 0f) -p.vx * 0.15f else p.vx
        }
        p.y = 0f
        // suspension spring
        p.bounceV += (-70f * p.bounce - 9f * p.bounceV + (rng.float() - 0.5f) * 40f * (p.speed / 50f)) * dt
        p.bounce += p.bounceV * dt
        p.bounce = Mathx.clamp(p.bounce, -0.08f, 0.08f)

        val frac = p.vx / Tuning.CAR_LATERAL_SPEED
        p.yaw = -frac * 8f
        p.roll = frac * 3.5f
        p.pitch = Mathx.clamp(-p.bounceV * 4f, -3f, 3f)
        wheelSpin += p.speed / 0.36f * (180f / PI.toFloat()) * dt
        wheelSpin %= 3600f
        p.visual.steerDeg = -frac * 24f
    }

    private fun stepFlyer(
        dt: Float, input: GameInput,
        maxX: Float, minY: Float, maxY: Float,
        latSpeed: Float, vertSpeed: Float, accel: Float,
        bankDeg: Float, pitchDeg: Float,
    ) {
        val p = player
        p.vx = Mathx.approach(p.vx, input.steerX * latSpeed, accel * dt)
        p.vy = Mathx.approach(p.vy, input.steerY * vertSpeed, accel * dt)
        p.x += p.vx * dt
        p.y += p.vy * dt
        if (p.x > maxX) { p.x = maxX; if (p.vx > 0f) p.vx = 0f }
        if (p.x < -maxX) { p.x = -maxX; if (p.vx < 0f) p.vx = 0f }
        if (p.y > maxY) { p.y = maxY; if (p.vy > 0f) p.vy = 0f }
        if (p.y < minY) { p.y = minY; if (p.vy < 0f) p.vy = 0f }
        p.roll = -(p.vx / latSpeed) * bankDeg
        p.pitch = (p.vy / vertSpeed) * pitchDeg
        p.yaw = -(p.vx / latSpeed) * 7f
        p.visual.steerDeg = 0f
        p.bounce = 0f
    }

    // ---- entities ------------------------------------------------------------------------------

    private fun updateEntities(dt: Float) {
        val speed = player.speed
        var i = 0
        while (i < entities.size) {
            val e = entities[i]
            e.age += dt
            e.z += (speed * e.parallax - e.speed) * dt
            e.x += e.vx * dt
            e.y += e.vy * dt
            e.rx += e.spinX * dt
            e.ry += e.spinY * dt
            e.rz += e.spinZ * dt
            if (e.z > CLEANUP_Z) {
                e.alive = false
                pool.add(e)
                val last = entities.size - 1
                entities[i] = entities[last]
                entities.removeAt(last)
                continue
            }
            i++
        }
    }

    private inline fun removeWhere(predicate: (Entity) -> Boolean) {
        var i = 0
        while (i < entities.size) {
            val e = entities[i]
            if (predicate(e)) {
                e.alive = false
                pool.add(e)
                val last = entities.size - 1
                entities[i] = entities[last]
                entities.removeAt(last)
            } else i++
        }
    }

    internal fun entityY(e: Entity): Float = if (e.groundBound) e.y + look.groundY else e.y

    private fun collide(dt: Float) {
        val p = player
        val ext = VehicleModel.hitHalfExtents(leg.mode)
        // forgiving hit box, slightly smaller than the visual
        val phx = ext[0] * 0.88f
        val phy = ext[1] * 0.9f
        val phz = ext[2] * 0.9f
        val cy = p.y + 0.62f
        val speed = p.speed

        for (i in 0 until entities.size) {
            val e = entities[i]
            if (e.hit) continue
            when (e.kind.category) {
                Category.HAZARD -> {
                    val reach = abs(speed * e.parallax - e.speed) * dt
                    if (overlaps(e, p.x, cy, phx, phy, phz + reach)) {
                        if (p.invuln <= 0f) onHazardHit(e) else e.hit = false
                    } else if (!e.passed) {
                        trackNearMiss(e, p.x, cy, phx, phy, phz)
                    }
                }
                Category.PICKUP -> {
                    if (e.kind == Kind.RING) {
                        if (!e.passed && e.z >= 0f) {
                            e.passed = true
                            val d = hypot(e.x - p.x, entityY(e) - cy)
                            if (d < e.param - 0.7f) onRingPassed(e) else onRingMissed(e)
                        }
                    } else {
                        val reach = abs(speed * e.parallax - e.speed) * dt
                        if (overlaps(e, p.x, cy, phx + 0.6f, phy + 0.5f, phz + reach + 0.4f)) onPickup(e)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun overlaps(e: Entity, px: Float, py: Float, phx: Float, phy: Float, phz: Float): Boolean {
        val ey = entityY(e)
        if (e.radius > 0f) {
            val cx = Mathx.clamp(e.x, px - phx, px + phx)
            val cy = Mathx.clamp(ey, py - phy, py + phy)
            val cz = Mathx.clamp(e.z, -phz, phz)
            val dx = e.x - cx; val dy = ey - cy; val dz = e.z - cz
            return dx * dx + dy * dy + dz * dz < e.radius * e.radius
        }
        return abs(e.x - px) < e.hx + phx && abs(ey - py) < e.hy + phy && abs(e.z) < e.hz + phz
    }

    private fun trackNearMiss(e: Entity, px: Float, py: Float, phx: Float, phy: Float, phz: Float) {
        if (e.damage <= 0) return
        val ey = entityY(e)
        val reachZ = phz + max(e.hz, e.radius) + 1f
        // sample the clearance while the entity is alongside the vehicle
        if (abs(e.z) < reachZ) {
            val gap = if (e.radius > 0f) {
                hypot(e.x - px, ey - py) - e.radius - min(phx, phy) * 0.8f
            } else {
                hypot(max(0f, abs(e.x - px) - e.hx - phx), max(0f, abs(ey - py) - e.hy - phy))
            }
            if (gap < e.minGap) e.minGap = gap
        }
        if (e.z > reachZ) {
            e.passed = true
            if (e.minGap < NEAR_MISS_GAP && e.age > 0.5f && player.invuln <= 0f && !xf.active) {
                nearMisses++
                val bonus = Tuning.NEAR_MISS_SCORE
                score += bonus
                player.boostMeter = Mathx.clamp01(player.boostMeter + 0.04f)
                popup("CLOSE CALL +$bonus", COLOR_ACCENT, 0.9f, false)
                sfx(Sfx.NEAR_MISS)
            }
        }
    }

    private fun onHazardHit(e: Entity) {
        e.hit = true
        val p = player
        if (e.damage <= 0) {
            // soft obstacle (cones): knock it away and lose a little speed
            p.speedPenalty = min(p.speedPenalty, 1f - e.slowdown)
            e.vy = 6f; e.vx = if (e.x >= p.x) 5f else -5f; e.spinZ = 540f; e.spinX = 360f
            e.speed = -20f
            shake = max(shake, 0.25f)
            sfx(Sfx.SOFT_HIT)
            particles.burst(e.x, entityY(e), e.z, 8, 7f, 0.5f, 0.25f, Col.rgb(0xFF8A2A), Col.rgb(0x552200), true, -9f)
            return
        }
        p.health -= e.damage
        lastHit = e
        lastHitLegDist = legDist
        p.invuln = Tuning.INVULN_AFTER_HIT
        p.speedPenalty = min(p.speedPenalty, Tuning.HIT_SPEED_PENALTY)
        shake = 1f
        damageFlash = 1f
        ringStreak = 0
        sfx(Sfx.HIT)
        // wreck the thing we hit
        e.vy = 10f; e.vx = if (e.x >= p.x) 9f else -9f
        e.spinZ = 420f * rng.sign(); e.spinX = 300f; e.spinY = 200f
        e.speed = -30f
        val ey = entityY(e)
        particles.burst(p.x, p.y + 0.8f, 0f, 26, 12f, 0.7f, 0.5f, Col.rgb(0xFFD36B), Col.rgb(0xAA2200), true, -6f)
        particles.burst(p.x, p.y + 0.8f, 0f, 14, 5f, 1.1f, 1.1f, Col.rgba(0x555555, 0.7f), Col.rgba(0x222222, 0f), false, 2f, 1f)
        particles.burst(e.x, ey, e.z, 10, 8f, 0.8f, 0.4f, Col.rgb(0xFFFFFF), Col.rgb(0xFF8800), true, -6f)
        if (p.health <= 0) crash()
    }

    private fun onPickup(e: Entity) {
        e.hit = true
        e.alive = true
        e.vy = 14f; e.scale *= 0.01f // vanish (handled by renderer skipping hit pickups)
        val ey = entityY(e)
        when (e.kind) {
            Kind.COIN -> {
                coins++
                score += Tuning.COIN_SCORE
                sfx(Sfx.COIN)
                particles.burst(e.x, ey, e.z, 8, 5f, 0.45f, 0.35f, Col.rgb(0xFFE066), Col.rgb(0xFF9A00), true)
            }
            Kind.NITRO, Kind.ORB, Kind.CRYSTAL -> {
                player.boostMeter = Mathx.clamp01(player.boostMeter + Tuning.NITRO_GAIN * (if (e.kind == Kind.CRYSTAL) 0.6f else 1f))
                score += if (e.kind == Kind.CRYSTAL) 60 else 50
                sfx(if (e.kind == Kind.CRYSTAL) Sfx.COIN else Sfx.NITRO)
                val c = if (e.kind == Kind.NITRO) Col.rgb(0x4FD6FF) else Col.rgb(0x7DF9FF)
                particles.burst(e.x, ey, e.z, 14, 8f, 0.6f, 0.5f, c, Col.rgb(0x1060FF), true)
                if (e.kind != Kind.CRYSTAL) popup("NITRO!", COLOR_ACCENT, 0.7f, false)
            }
            Kind.REPAIR -> {
                if (player.health < Tuning.MAX_HEALTH) player.health++
                score += 30
                sfx(Sfx.REPAIR)
                popup("REPAIRED", COLOR_GOOD, 0.9f, false)
                particles.burst(e.x, ey, e.z, 16, 6f, 0.8f, 0.5f, Col.rgb(0x7DFF9A), Col.rgb(0x10A040), true)
            }
            else -> Unit
        }
    }

    private fun onRingPassed(e: Entity) {
        e.hit = true
        ringStreak++
        rings++
        val mult = min(ringStreak, 5)
        val bonus = Tuning.RING_SCORE * mult
        score += bonus
        player.boostMeter = Mathx.clamp01(player.boostMeter + 0.07f)
        sfx(Sfx.RING)
        popup(if (ringStreak >= 2) "RING x$ringStreak  +$bonus" else "RING +$bonus", COLOR_ACCENT, 0.8f, false)
        particles.burst(e.x, entityY(e), e.z, 22, 10f, 0.6f, 0.5f, Col.rgb(0x9DFBFF), Col.rgb(0x2080FF), true)
    }

    private fun onRingMissed(e: Entity) {
        e.hit = true
        if (ringStreak > 0) {
            popup("STREAK LOST", COLOR_WARN, 0.8f, false)
            sfx(Sfx.RING_MISS)
        }
        ringStreak = 0
    }

    private fun crash() {
        val p = player
        p.alive = false
        phase = Phase.CRASHING
        phaseTime = 0f
        shake = 1.2f
        flash = 1f
        sfx(Sfx.EXPLOSION)
        val x = p.x; val y = p.y + 0.7f
        particles.burst(x, y, 0f, 70, 22f, 1.1f, 1.2f, Col.rgb(0xFFF1B0), Col.rgb(0xFF4A00), true, -4f, 1.2f)
        particles.burst(x, y, 0f, 30, 9f, 1.8f, 2.2f, Col.rgba(0x444444, 0.8f), Col.rgba(0x111111, 0f), false, 3f, 0.8f)
        particles.burst(x, y, 0f, 26, 18f, 1.4f, 0.35f, Col.rgb(0xFFFFFF), Col.rgb(0xFFAA33), true, -9f, 0.6f)
        popups.clear()
    }

    // ---- zone, transformation, finale ---------------------------------------------------------------

    private fun updateZone(pressed: Boolean) {
        val l = leg
        if (xf.active || phase != Phase.RUN) {
            transformReady = false
            return
        }
        if (l.zoneLength > 0f) {
            val zoneStart = l.length - l.zoneLength
            zoneProgress = Mathx.clamp01((legDist - zoneStart) / l.zoneLength)
            transformReady = legDist >= zoneStart
            if (transformReady && !zoneAnnounced) {
                zoneAnnounced = true
                sfx(Sfx.ZONE)
                popup("TRANSFORM ZONE!", COLOR_ACCENT, 1.6f, true)
            }
            if (transformReady && pressed) beginTransform(manual = true)
            else if (legDist >= l.length) beginTransform(manual = false)
        } else {
            transformReady = false
            zoneProgress = 0f
        }
    }

    private fun beginTransform(manual: Boolean) {
        val from = leg
        val next = Tuning.LEGS[legIndex + 1]
        val remaining = from.length - legDist
        perfectLaunch = manual && remaining <= Tuning.PERFECT_WINDOW
        val bonus = if (perfectLaunch) Tuning.PERFECT_TRANSFORM_SCORE else Tuning.TRANSFORM_SCORE
        score += bonus
        popup(if (perfectLaunch) "PERFECT LAUNCH! +$bonus" else "TRANSFORM! +$bonus", if (perfectLaunch) COLOR_GOLD else COLOR_ACCENT, 1.8f, true)

        xf.active = true
        xf.t = 0f
        xf.from = from.mode
        xf.to = next.mode
        player.visual.from = from.mode
        player.visual.to = next.mode
        player.visual.morph = 0f

        legIndex++
        legDist = 0f
        spawnCursor = next.firstPatternAt
        decorCursor = -20f
        gateSpawned = false
        zoneAnnounced = false
        transformReady = false
        ringStreak = 0

        // old-leg hazards and pickups are no longer relevant; scenery and the gate stay to slide past
        removeWhere { it.kind.category == Category.HAZARD || it.kind.category == Category.PICKUP }
        checkpoint = Checkpoint(score, coins, rings, nearMisses)
        player.invuln = Tuning.TRANSFORM_SECONDS + 0.9f
        shake = 0.6f
        flash = 0.9f
        sfx(Sfx.TRANSFORM)
        transformBurst()
    }

    private fun finishTransform() {
        xf.active = false
        xf.t = 1f
        player.mode = xf.to
        player.visual.settle(xf.to)
        flash = 0.5f
        shake = max(shake, 0.4f)
        sfx(Sfx.TRANSFORM_DONE)
        transformBurst()
        popup(leg.name, COLOR_WHITE, 2.2f, true)
    }

    private fun transformBurst() {
        val p = player
        val y = p.y + 0.7f
        // expanding shock-ring plus sparks
        for (k in 0 until 48) {
            val a = (k / 48f) * 2f * PI.toFloat()
            particles.emit(
                p.x, y, 0f, cos(a) * 16f, sin(a) * 16f, 0f, 0.7f, 0.5f, 0.2f,
                Col.rgb(0x9DFBFF), Col.rgba(0x2060FF, 0f), true, 0f, 1.6f, true,
            )
        }
        particles.burst(p.x, y, 0f, 40, 14f, 0.9f, 0.45f, Col.rgb(0xFFFFFF), Col.rgb(0x60A0FF), true, 0f, 1.4f)
    }

    private fun beginFinale() {
        phase = Phase.FINALE
        phaseTime = 0f
        finaleT = 0f
        finaleStartY = player.y
        finaleStartSpeed = player.speed
        finaleMorphed = false
        touchedDown = false
        player.invuln = 99f
        player.boostFactor = 0f
        player.boosting = false
        removeWhere { it.kind.category == Category.HAZARD || it.kind.category == Category.PICKUP || it.kind == Kind.PLANET }
        finaleGroundY = -330f
        flash = 0.6f
        popup("APPROACHING THE MOON", COLOR_WHITE, 2.6f, true)
    }

    private var finaleStartY = 14f
    private var finaleStartSpeed = 100f
    private var finaleMorphed = false
    private var touchedDown = false

    private fun updateFinale(dt: Float) {
        finaleT += dt
        val t = finaleT
        val p = player
        val v = p.visual
        v.time = clock
        v.steerDeg = 0f

        // A: glide in and descend, B: touch down and skid, C: unfold into the car, D: drive away
        val descend = Mathx.smoother(t / 4.2f)
        p.speed = if (t < 4.2f) Mathx.lerp(finaleStartSpeed, 24f, descend) else Mathx.lerp(24f, 0f, Mathx.smoother((t - 4.2f) / 1.6f))
        if (t >= 6.4f) p.speed = Mathx.lerp(0f, 18f, Mathx.smoother((t - 6.4f) / 2.0f))
        // the rocket's belly fin skids on the ground; once it folds away the car settles onto its wheels
        val morphT = if (finaleMorphed) Mathx.clamp01((t - 5.0f) / Tuning.TRANSFORM_SECONDS) else 0f
        p.y = if (finaleMorphed) Mathx.lerp(LAND_Y, 0f, Mathx.smoothstep(0.35f, 0.95f, morphT)) else Mathx.lerp(finaleStartY, LAND_Y, descend)
        finaleGroundY = Mathx.lerp(-330f, -0.02f, descend)
        p.x = Mathx.damp(p.x, 0f, 2f, dt)
        p.roll = Mathx.damp(p.roll, 0f, 3f, dt)
        p.pitch = if (t < 4.2f) -9f * sin(PI.toFloat() * Mathx.clamp01(t / 4.2f)) else Mathx.damp(p.pitch, 0f, 4f, dt)
        p.yaw = 0f

        if (t >= 4.2f && !touchedDown) {
            touchedDown = true
            sfx(Sfx.TOUCHDOWN)
            shake = 0.7f
            particles.burst(0f, 0.3f, -1f, 60, 14f, 1.6f, 1.6f, Col.rgba(0xB8B4AE, 0.85f), Col.rgba(0x777470, 0f), false, 1.5f, 1.2f)
        }
        v.flame = if (t < 3.6f) 0.9f * (1f - Mathx.smoothstep(2.4f, 3.6f, t)) + 0.2f else 0f
        if (t >= 5.0f && !finaleMorphed) {
            finaleMorphed = true
            v.from = VehicleMode.ROCKET; v.to = VehicleMode.CAR; v.morph = 0f
            sfx(Sfx.TRANSFORM)
            flash = 0.5f
            transformBurst()
        }
        if (finaleMorphed && v.from != v.to) {
            v.morph = morphT
            if (morphT >= 1f) {
                v.settle(VehicleMode.CAR)
                p.mode = VehicleMode.CAR
                sfx(Sfx.TRANSFORM_DONE)
            }
        }
        if (t > 6.4f) {
            wheelSpin += p.speed / 0.36f * (180f / PI.toFloat()) * dt
            wheelSpin %= 3600f
            v.wheelSpinDeg = wheelSpin
        }
        travelled += p.speed * dt
        // dust trail while sliding / driving
        if (touchedDown && p.speed > 3f) {
            dustAcc += dt * 40f
            while (dustAcc >= 1f) {
                dustAcc -= 1f
                particles.emit(
                    p.x + rng.range(-1.2f, 1.2f), 0.2f, 1.5f, rng.range(-1f, 1f), rng.range(0.5f, 2f), 3f + p.speed * 0.2f,
                    rng.range(0.6f, 1.2f), 0.5f, 1.4f, Col.rgba(0xB8B4AE, 0.5f), Col.rgba(0x888480, 0f), false, 0f, 1f, true,
                )
            }
        }
        updateEntities(dt)
        particlesUpdate(dt, p.speed)
        if (t >= 7.4f) completeRun()
    }

    private fun completeRun() {
        phase = Phase.VICTORY
        phaseTime = 0f
        val healthBonus = player.health * Tuning.HEALTH_BONUS
        val timeBonus = max(0, ((330f - runTime) * 12f).toInt())
        val base = score
        score += healthBonus + timeBonus
        lastScoreBreakdown = intArrayOf(base, healthBonus, timeBonus)
        stars = Tuning.STAR_THRESHOLDS.count { score >= it }
        newBest = score > bestScore
        if (newBest) bestScore = score
        sfx(Sfx.VICTORY)
        popups.clear()
    }

    private fun updateVictory(dt: Float) {
        val p = player
        val v = p.visual
        v.time = clock
        p.speed = Mathx.damp(p.speed, 12f, 1.5f, dt)
        wheelSpin += p.speed / 0.36f * (180f / PI.toFloat()) * dt
        wheelSpin %= 3600f
        v.wheelSpinDeg = wheelSpin
        travelled += p.speed * dt
        updateEntities(dt)
        particlesUpdate(dt, p.speed)
    }

    // ---- crash / game over -----------------------------------------------------------------------

    private fun updateCrashing(dt: Float) {
        timeScale = 0.6f
        val p = player
        p.speed = Mathx.damp(p.speed, 0f, 3f, dt)
        p.visual.visible = false
        updateEntities(dt * 0.4f)
        particlesUpdate(dt, p.speed)
        if (phaseTime >= Tuning.CRASH_SECONDS) {
            phase = Phase.GAME_OVER
            phaseTime = 0f
            sfx(Sfx.GAME_OVER)
            newBest = score > bestScore
            if (newBest) bestScore = score
            stars = 0
        }
    }

    private fun updateGameOver(dt: Float) {
        timeScale = 1f
        player.speed = 0f
        particlesUpdate(dt, 0f)
    }

    // ---- fx ------------------------------------------------------------------------------------

    private fun particlesUpdate(dt: Float, worldSpeed: Float) = particles.update(dt, worldSpeed)

    private fun updateFx(dt: Float) {
        val p = player
        if (!p.alive) {
            particlesUpdate(dt, p.speed)
            return
        }
        val dom = p.visual.dominant
        val exhaustZ = when (dom) {
            VehicleMode.CAR -> 2.4f
            VehicleMode.PLANE -> 3.5f
            VehicleMode.ROCKET -> 4.3f
        }
        val y0 = p.y + 0.62f
        // exhaust sparks / smoke
        val rate = when (dom) {
            VehicleMode.CAR -> if (p.boostFactor > 0.3f) 60f else 0f
            VehicleMode.PLANE -> 40f + 50f * p.boostFactor
            VehicleMode.ROCKET -> 70f + 60f * p.boostFactor
        }
        sparkAcc += rate * dt
        while (sparkAcc >= 1f) {
            sparkAcc -= 1f
            val spread = if (dom == VehicleMode.ROCKET) 0.45f else 0.22f
            val life = if (dom == VehicleMode.CAR) rng.range(0.25f, 0.5f) else rng.range(0.07f, 0.16f)
            particles.emit(
                p.x + rng.range(-spread, spread), y0 + rng.range(-spread, spread), exhaustZ + 0.3f,
                rng.range(-1.2f, 1.2f), rng.range(-1.2f, 1.2f), rng.range(3f, 7f),
                life, if (dom == VehicleMode.ROCKET) 0.34f else 0.22f, 0.04f,
                Col.rgb(0xFFC060), Col.rgba(0xFF3000, 0f), true, 0f, 0.8f, true,
            )
        }
        // speed dust streaming toward the camera
        val dustRate = when (leg.mode) {
            VehicleMode.CAR -> 18f
            VehicleMode.PLANE -> 30f
            VehicleMode.ROCKET -> 70f
        } * (0.6f + 0.6f * p.speed / leg.speedEnd)
        dustAcc += dustRate * dt
        while (dustAcc >= 1f) {
            dustAcc -= 1f
            val spawnZ = -rng.range(60f, 190f)
            val sideX = rng.range(-26f, 26f)
            val dy = when (leg.mode) {
                VehicleMode.CAR -> rng.range(0.3f, 5f)
                else -> p.y + rng.range(-14f, 14f)
            }
            val c = if (leg.mode == VehicleMode.CAR) Col.rgba(0xFFFFFF, 0.35f) else Col.rgba(0xCFE8FF, 0.7f)
            particles.emit(sideX, dy, spawnZ, 0f, 0f, 0f, 1.6f, 0.16f, 0.12f, c, Col.withAlpha(c, 0f), true, 0f, 0f, true)
        }
        particlesUpdate(dt, p.speed)
    }

    private fun railSparks(side: Float) {
        sparkAcc += 0f
        for (k in 0 until 3) {
            particles.emit(
                player.x + side * 1.0f, 0.4f, rng.range(-1.5f, 1.5f),
                -side * rng.range(1f, 4f), rng.range(1f, 4f), rng.range(2f, 8f), 0.4f, 0.25f, 0.05f,
                Col.rgb(0xFFE08A), Col.rgba(0xFF6A00, 0f), true, -9f, 0.5f, true,
            )
        }
    }

    internal fun popup(text: String, color: Int, ttl: Float, big: Boolean) {
        if (popups.size >= 5) popups.removeAt(0)
        popups.add(Popup(text, color, ttl, big))
    }

    companion object {
        const val CLEANUP_Z = 30f
        const val LAND_Y = 0.68f
        const val NEAR_MISS_GAP = 1.25f

        val COLOR_GOOD = Col.rgb(0x7DFF9A)
        val COLOR_ACCENT = Col.rgb(0x6FE8FF)
        val COLOR_WARN = Col.rgb(0xFFB454)
        val COLOR_GOLD = Col.rgb(0xFFD44A)
        val COLOR_WHITE = Col.rgb(0xFFFFFF)
    }
}
