package app.roadtoorbit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import app.roadtoorbit.game.HudState
import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.Tuning
import app.roadtoorbit.gfx.VehicleMode
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Transparent overlay on top of the 3D view: draws the HUD and all menus with Canvas and turns touches
 * into steering / boost / transform input. Layout is in "units" u = 1% of the screen height.
 */
class HudView(context: Context, private val bridge: UiBridge) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val rect2 = RectF()

    private val sans: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val sansItalic: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    private val light: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    private var w = 1f
    private var h = 1f
    private var u = 10f
    private var insetL = 0f
    private var insetR = 0f
    private var insetT = 0f
    private var insetB = 0f
    private val startMs = SystemClock.uptimeMillis()

    // interactive elements (centre x, centre y, radius) – recomputed on layout
    private val pause = FloatArray(3)
    private val boost = FloatArray(3)
    private val xform = FloatArray(3)
    private val stickHome = FloatArray(2)

    // finger tracking
    private var stickId = -1
    private var stickOx = 0f
    private var stickOy = 0f
    private var stickX = 0f
    private var stickY = 0f
    private var boostId = -1
    private var xformPressUntil = 0L

    // menu buttons (rebuilt every frame while an overlay is up)
    private class Btn { val r = RectF(); var id = 0; var label = ""; var primary = false }
    private val buttons = Array(4) { Btn() }
    private var buttonCount = 0
    private var pressedButton = -1
    private var downX = 0f
    private var downY = 0f
    private val pills = Array(4) { RectF() }

    // cached formatted numbers (avoid re-allocating strings every frame)
    private var scoreCache = -1
    private var scoreText = "0"
    private var bestCache = -1
    private var bestText = "0"
    private var speedCache = -1
    private var speedText = "0"
    private var altCache = -1f
    private var altText = ""

    private fun t(): Float = (SystemClock.uptimeMillis() - startMs) / 1000f

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw.toFloat(); h = nh.toFloat()
        u = h / 100f
        layoutControls()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            insets.displayCutout?.let {
                insetL = it.safeInsetLeft.toFloat(); insetR = it.safeInsetRight.toFloat()
                insetT = it.safeInsetTop.toFloat(); insetB = it.safeInsetBottom.toFloat()
            }
            layoutControls()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (android.os.Build.VERSION.SDK_INT >= 29 && changed) {
            // keep the system back-swipe from stealing drags meant for the stick and the boost button;
            // Android honours at most 200 dp of exclusion per screen edge
            val d = resources.displayMetrics.density
            val strip = (64 * d).toInt()
            val tall = (200 * d).toInt()
            systemGestureExclusionRects = listOf(
                android.graphics.Rect(0, (b - t) - tall, strip, b - t),
                android.graphics.Rect((r - l) - strip, (b - t) - tall, r - l, b - t),
            )
        }
    }

    private fun layoutControls() {
        val m = 3f * u
        pause[0] = w - m - insetR - 4.2f * u; pause[1] = m + insetT + 4.2f * u; pause[2] = 5.2f * u
        boost[0] = w - m - insetR - 11.5f * u; boost[1] = h - m - insetB - 11f * u; boost[2] = 10f * u
        xform[0] = w - m - insetR - 11.5f * u; xform[1] = boost[1] - 24.8f * u; xform[2] = 11.5f * u
        stickHome[0] = m + insetL + 15f * u; stickHome[1] = h - m - insetB - 15f * u
    }

    // ===================================================================================================
    // drawing
    // ===================================================================================================

    override fun onDraw(c: Canvas) {
        val err = bridge.fatalError
        if (err != null) {
            drawError(c, err)
            return
        }
        val s = bridge.hud.latest
        buttonCount = 0
        when (s.phase) {
            Phase.MENU -> drawMenu(c, s)
            else -> {
                drawGameplay(c, s)
                when {
                    bridge.paused -> drawPause(c)
                    s.phase == Phase.GAME_OVER -> drawGameOver(c, s)
                    s.phase == Phase.VICTORY -> drawVictory(c, s)
                }
            }
        }
        postInvalidateOnAnimation()
    }

    private fun drawError(c: Canvas, msg: String) {
        fill.shader = null
        fill.color = Color.rgb(40, 4, 10)
        c.drawRect(0f, 0f, w, h, fill)
        label(c, "Something went wrong", w / 2, h * 0.38f, 5f * u, Color.WHITE, Paint.Align.CENTER, sans)
        label(c, msg, w / 2, h * 0.50f, 2.6f * u, Color.rgb(255, 190, 190), Paint.Align.CENTER, light)
        val hint = if (msg.startsWith("Graphics setup")) "Your device may not support OpenGL ES 3.0" else "Please restart the game"
        label(c, hint, w / 2, h * 0.58f, 2.4f * u, Color.rgb(200, 200, 220), Paint.Align.CENTER, light)
    }

    // ---- in-game HUD ------------------------------------------------------------------------------------

    private fun drawGameplay(c: Canvas, s: HudState) {
        val playing = s.phase == Phase.RUN || s.phase == Phase.COUNTDOWN
        val showControls = playing || s.phase == Phase.CRASHING
        // full-screen feedback tints
        if (s.flash > 0.01f) {
            fill.shader = null
            fill.color = Color.argb((s.flash.coerceAtMost(1f) * 120).toInt(), 255, 255, 255)
            c.drawRect(0f, 0f, w, h, fill)
        }
        val lowHealth = s.health == 1 && playing
        val vignette = max(s.damageFlash, if (lowHealth) 0.25f + 0.15f * sin(t() * 6f) else 0f)
        if (vignette > 0.02f) drawVignette(c, Color.rgb(255, 30, 30), vignette.coerceAtMost(1f))

        if (s.phase != Phase.FINALE && s.phase != Phase.VICTORY && s.phase != Phase.GAME_OVER) {
            drawScrims(c)
            drawTopBar(c, s)
            drawSpeedometer(c, s)
        }
        if (showControls && !bridge.paused) drawControls(c, s)
        drawZoneBanner(c, s)
        drawPopups(c, s)
        if (s.phase == Phase.COUNTDOWN) drawCountdown(c, s)
    }

    private fun drawVignette(c: Canvas, color: Int, amount: Float) {
        val r = hypot(w, h) * 0.6f
        val a = (amount * 190).toInt().coerceIn(0, 255)
        fill.color = Color.WHITE
        fill.shader = RadialGradient(w / 2, h / 2, r, intArrayOf(Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))), floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
    }

    /** Soft dark gradients at the top and bottom edges keep white text readable over bright clouds. */
    private fun drawScrims(c: Canvas) {
        fill.color = Color.WHITE
        fill.shader = LinearGradient(0f, 0f, 0f, 22f * u, Color.argb(95, 6, 10, 28), Color.argb(0, 6, 10, 28), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, 22f * u, fill)
        fill.shader = LinearGradient(0f, h, 0f, h - 20f * u, Color.argb(150, 6, 10, 28), Color.argb(0, 6, 10, 28), Shader.TileMode.CLAMP)
        c.drawRect(0f, h - 20f * u, w, h, fill)
        fill.shader = null
    }

    private fun drawTopBar(c: Canvas, s: HudState) {
        val m = 3f * u
        val left = m + insetL
        val top = m + insetT

        // journey track with three stage icons
        val trackW = 40f * u
        val ty = top + 3.6f * u
        line.strokeWidth = 1.3f * u
        line.color = Color.argb(110, 255, 255, 255)
        c.drawLine(left + 3f * u, ty, left + trackW - 3f * u, ty, line)
        line.color = Color.rgb(111, 232, 255)
        val prog = s.journey.coerceIn(0f, 1f)
        val x0 = left + 3f * u
        val x1 = x0 + (trackW - 6f * u) * prog
        if (x1 > x0) c.drawLine(x0, ty, x1, ty, line)
        for (i in 0 until 3) {
            val cx = left + 3f * u + (trackW - 6f * u) * i / 2f
            val reached = prog >= i / 2f - 0.001f || s.legIndex >= i
            val current = s.legIndex == i
            fill.shader = null
            fill.color = if (reached) Color.rgb(24, 40, 70) else Color.argb(150, 20, 24, 40)
            c.drawCircle(cx, ty, 3.1f * u, fill)
            line.strokeWidth = (if (current) 0.55f else 0.35f) * u
            line.color = if (current) Color.rgb(111, 232, 255) else Color.argb(if (reached) 220 else 100, 255, 255, 255)
            c.drawCircle(cx, ty, 3.1f * u, line)
            val ic = if (reached) Color.WHITE else Color.argb(120, 255, 255, 255)
            when (i) {
                0 -> iconCar(c, cx, ty, 1.7f * u, ic)
                1 -> iconPlane(c, cx, ty, 1.8f * u, ic)
                else -> iconRocket(c, cx, ty, 1.9f * u, ic)
            }
        }
        label(c, s.legName, left + 1f * u, ty + 8.2f * u, 3.0f * u, Color.WHITE, Paint.Align.LEFT, sansItalic)
        label(c, s.legSubtitle, left + 1f * u, ty + 11.2f * u, 2.1f * u, Color.argb(200, 200, 215, 255), Paint.Align.LEFT, light)

        // score + best, centred
        if (s.score != scoreCache) { scoreCache = s.score; scoreText = String.format(Locale.US, "%,d", s.score) }
        if (s.bestScore != bestCache) { bestCache = s.bestScore; bestText = String.format(Locale.US, "%,d", s.bestScore) }
        label(c, scoreText, w / 2, top + 6.8f * u, 7f * u, Color.WHITE, Paint.Align.CENTER, sansItalic)
        label(c, "BEST $bestText", w / 2, top + 10.6f * u, 2.1f * u, Color.argb(190, 200, 215, 255), Paint.Align.CENTER, light)

        // pause button + shields
        if (s.phase == Phase.RUN || s.phase == Phase.COUNTDOWN) {
            fill.color = Color.argb(120, 10, 14, 30)
            c.drawCircle(pause[0], pause[1], pause[2], fill)
            line.strokeWidth = 0.35f * u
            line.color = Color.argb(200, 255, 255, 255)
            c.drawCircle(pause[0], pause[1], pause[2], line)
            fill.color = Color.WHITE
            rect.set(pause[0] - 1.9f * u, pause[1] - 2.2f * u, pause[0] - 0.6f * u, pause[1] + 2.2f * u)
            c.drawRoundRect(rect, 0.4f * u, 0.4f * u, fill)
            rect.set(pause[0] + 0.6f * u, pause[1] - 2.2f * u, pause[0] + 1.9f * u, pause[1] + 2.2f * u)
            c.drawRoundRect(rect, 0.4f * u, 0.4f * u, fill)
        }
        val shieldSize = 2.8f * u
        for (i in 0 until s.maxHealth) {
            val cx = pause[0] - pause[2] - 3.2f * u - (s.maxHealth - 1 - i) * 6.2f * u
            val on = i < s.health
            val blink = s.invulnerable && on && (SystemClock.uptimeMillis() / 90) % 2L == 0L
            iconShield(c, cx, pause[1], shieldSize, on, blink)
        }
    }

    private fun drawSpeedometer(c: Canvas, s: HudState) {
        if (s.phase != Phase.RUN && s.phase != Phase.COUNTDOWN) return
        if (s.speedKmh != speedCache) { speedCache = s.speedKmh; speedText = String.format(Locale.US, "%,d", s.speedKmh) }
        val y = h - 3f * u - insetB
        label(c, speedText, w / 2 + 1f * u, y - 2f * u, 6f * u, Color.WHITE, Paint.Align.RIGHT, sansItalic)
        label(c, " KM/H", w / 2 + 1f * u, y - 2f * u, 2.4f * u, Color.argb(235, 235, 243, 255), Paint.Align.LEFT, sans)
        if (s.legIndex > 0) {
            if (abs(s.altitudeKm - altCache) > 0.05f) {
                altCache = s.altitudeKm
                altText = if (s.altitudeKm >= 1000f) String.format(Locale.US, "%,.0f km", s.altitudeKm) else String.format(Locale.US, "%.1f km", s.altitudeKm)
            }
            label(c, "ALT $altText", w / 2, y - 8.6f * u, 2.9f * u, Color.argb(255, 215, 240, 255), Paint.Align.CENTER, sans)
        }
    }

    private fun drawControls(c: Canvas, s: HudState) {
        // --- stick
        val active = stickId != -1
        val sx = if (active) stickOx else stickHome[0]
        val sy = if (active) stickOy else stickHome[1]
        val baseR = 9.5f * u
        fill.shader = null
        fill.color = Color.argb(if (active) 70 else 36, 255, 255, 255)
        c.drawCircle(sx, sy, baseR, fill)
        line.strokeWidth = 0.4f * u
        line.color = Color.argb(if (active) 190 else 100, 255, 255, 255)
        c.drawCircle(sx, sy, baseR, line)
        val kx = if (active) stickX else sx
        val ky = if (active) stickY else sy
        fill.color = Color.argb(if (active) 230 else 120, 200, 235, 255)
        c.drawCircle(kx, ky, 4.2f * u, fill)
        if (!active) label(c, "DRAG TO STEER", sx, sy + baseR + 3.2f * u, 2f * u, Color.argb(140, 255, 255, 255), Paint.Align.CENTER, sans)

        // --- boost
        val bp = boostId != -1
        val br = boost[2] * (if (bp) 0.94f else 1f)
        fill.color = if (bp) Color.argb(200, 255, 150, 40) else Color.argb(120, 10, 14, 30)
        c.drawCircle(boost[0], boost[1], br, fill)
        line.strokeWidth = 1.1f * u
        line.color = Color.argb(70, 255, 255, 255)
        c.drawCircle(boost[0], boost[1], br - 0.6f * u, line)
        line.color = if (s.boost > 0.25f) Color.rgb(255, 176, 60) else Color.rgb(255, 90, 70)
        rect.set(boost[0] - br + 0.6f * u, boost[1] - br + 0.6f * u, boost[0] + br - 0.6f * u, boost[1] + br - 0.6f * u)
        c.drawArc(rect, -90f, 360f * s.boost.coerceIn(0f, 1f), false, line)
        drawBolt(c, boost[0], boost[1] - 1.4f * u, 4.2f * u, Color.WHITE)
        label(c, "BOOST", boost[0], boost[1] + 6.2f * u, 2.3f * u, Color.WHITE, Paint.Align.CENTER, sans)

        // --- transform (not shown in the final leg)
        if (s.legIndex < 2) drawTransformButton(c, s)
    }

    private fun drawTransformButton(c: Canvas, s: HudState) {
        val ready = s.transformReady
        val pressed = SystemClock.uptimeMillis() < xformPressUntil
        val pulse = if (ready) 1f + 0.07f * sin(t() * 9f) else 1f
        val r = xform[2] * pulse * (if (pressed) 0.93f else 1f)
        if (ready) {
            fill.color = Color.WHITE
            fill.shader = RadialGradient(xform[0], xform[1], r * 1.5f, intArrayOf(Color.argb(150, 255, 77, 210), Color.argb(0, 255, 77, 210)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(xform[0], xform[1], r * 1.5f, fill)
            fill.shader = LinearGradient(xform[0], xform[1] - r, xform[0], xform[1] + r, Color.rgb(255, 98, 220), Color.rgb(90, 90, 255), Shader.TileMode.CLAMP)
        } else {
            fill.shader = null
            fill.color = Color.argb(110, 12, 16, 34)
        }
        c.drawCircle(xform[0], xform[1], r, fill)
        fill.shader = null
        line.strokeWidth = 0.7f * u
        line.color = if (ready) Color.WHITE else Color.argb(120, 255, 255, 255)
        c.drawCircle(xform[0], xform[1], r, line)

        // morph glyph: car -> plane -> rocket
        val ic = if (ready) Color.WHITE else Color.argb(130, 255, 255, 255)
        val next = s.legIndex + 1
        if (next == 1) iconPlane(c, xform[0], xform[1] - 2.2f * u, 5.2f * u, ic) else iconRocket(c, xform[0], xform[1] - 2.2f * u, 5.4f * u, ic)
        label(c, "TRANSFORM", xform[0], xform[1] + 6.2f * u, 2.5f * u, if (ready) Color.WHITE else Color.argb(150, 255, 255, 255), Paint.Align.CENTER, sansItalic)
        if (!ready) {
            // distance until the zone opens
            val zoneStart = Tuning.LEGS[s.legIndex].length - Tuning.LEGS[s.legIndex].zoneLength
            val toZone = (zoneStart - s.legProgress * Tuning.LEGS[s.legIndex].length).coerceAtLeast(0f)
            if (toZone < 900f) label(c, "ZONE IN ${(toZone / 10f).toInt() * 10} m", xform[0], xform[1] + 9.4f * u, 1.9f * u, Color.argb(190, 200, 215, 255), Paint.Align.CENTER, light)
        } else {
            line.strokeWidth = 1.0f * u
            line.color = Color.argb(230, 255, 255, 255)
            rect.set(xform[0] - r - 1.4f * u, xform[1] - r - 1.4f * u, xform[0] + r + 1.4f * u, xform[1] + r + 1.4f * u)
            c.drawArc(rect, -90f, 360f * (1f - s.zoneProgress), false, line)
            label(c, "TAP!", xform[0], xform[1] - r - 3.2f * u, 3.2f * u, Color.rgb(255, 230, 120), Paint.Align.CENTER, sansItalic)
        }
    }

    private fun drawZoneBanner(c: Canvas, s: HudState) {
        if (!s.transformReady || s.phase != Phase.RUN) return
        val a = 0.75f + 0.25f * sin(t() * 8f)
        val bw = 46f * u
        val bh = 6.2f * u
        val cy = 22f * u
        rect.set(w / 2 - bw / 2, cy - bh / 2, w / 2 + bw / 2, cy + bh / 2)
        fill.shader = null
        fill.color = Color.argb((170 * a).toInt(), 80, 20, 120)
        c.drawRoundRect(rect, bh / 2, bh / 2, fill)
        line.strokeWidth = 0.4f * u
        line.color = Color.argb((230 * a).toInt(), 255, 120, 230)
        c.drawRoundRect(rect, bh / 2, bh / 2, line)
        label(c, "TRANSFORM ZONE  ·  PRESS THE BUTTON!", w / 2, cy + 1.0f * u, 2.6f * u, Color.WHITE, Paint.Align.CENTER, sansItalic)
        // gate distance bar
        rect2.set(w / 2 - bw / 2 + 2f * u, cy + bh / 2 + 0.8f * u, w / 2 + bw / 2 - 2f * u, cy + bh / 2 + 1.7f * u)
        fill.color = Color.argb(90, 255, 255, 255)
        c.drawRoundRect(rect2, 0.45f * u, 0.45f * u, fill)
        val trackLeft = rect2.left
        val trackW = rect2.width()
        // the final stretch before the gate is the PERFECT LAUNCH window
        val zoneLen = Tuning.LEGS[s.legIndex.coerceAtMost(1)].zoneLength
        val perfectFrac = (Tuning.PERFECT_WINDOW / zoneLen).coerceIn(0.05f, 0.5f)
        rect.set(trackLeft + trackW * (1f - perfectFrac), rect2.top - 0.25f * u, trackLeft + trackW, rect2.bottom + 0.25f * u)
        fill.color = Color.argb(235, 255, 214, 74)
        c.drawRoundRect(rect, 0.5f * u, 0.5f * u, fill)
        rect2.right = trackLeft + trackW * s.zoneProgress.coerceIn(0f, 1f)
        fill.color = Color.rgb(255, 120, 230)
        c.drawRoundRect(rect2, 0.45f * u, 0.45f * u, fill)
        label(c, "PERFECT", trackLeft + trackW * (1f - perfectFrac / 2f), rect2.bottom + 3.2f * u, 1.8f * u, Color.rgb(255, 224, 110), Paint.Align.CENTER, sans)
    }

    private fun drawPopups(c: Canvas, s: HudState) {
        var y = 33f * u
        for (i in s.popupCount - 1 downTo 0) {
            val age = s.popupAge[i]
            val ttl = s.popupTtl[i]
            val appear = (age / 0.16f).coerceIn(0f, 1f)
            val fade = ((ttl - age) / 0.4f).coerceIn(0f, 1f)
            val big = s.popupBig[i]
            val size = (if (big) 6.2f else 3.4f) * u * (0.7f + 0.3f * appear + (if (age < 0.12f) 0.15f else 0f))
            val col = s.popupColor[i]
            label(c, s.popupText[i], w / 2, y, size, Color.argb((255 * fade).toInt(), Color.red(col), Color.green(col), Color.blue(col)), Paint.Align.CENTER, sansItalic, alpha = fade)
            y += (if (big) 7.4f else 4.6f) * u
        }
    }

    private fun drawCountdown(c: Canvas, s: HudState) {
        val n = s.countdown
        val phase = (t() * 1f) % 1f
        val text = if (n > 0) n.toString() else "GO!"
        val scale = 1f + 0.35f * (1f - phase)
        label(c, text, w / 2, h * 0.52f, 22f * u * scale, if (n > 0) Color.WHITE else Color.rgb(125, 255, 154), Paint.Align.CENTER, sansItalic, alpha = 0.55f + 0.45f * (1f - phase))
    }

    // ---- menus -------------------------------------------------------------------------------------------

    private fun drawMenu(c: Canvas, s: HudState) {
        val m = 3f * u
        // title
        val cx = w / 2
        txt.typeface = sansItalic
        txt.textAlign = Paint.Align.CENTER
        txt.textSize = 13.5f * u
        txt.textSkewX = -0.18f
        txt.letterSpacing = 0.04f
        val ty = insetT + 19f * u
        txt.style = Paint.Style.STROKE
        txt.strokeWidth = 1.6f * u
        txt.color = Color.argb(200, 10, 8, 40)
        txt.shader = null
        c.drawText("ROAD TO ORBIT", cx + 0.5f * u, ty + 0.6f * u, txt)
        txt.style = Paint.Style.FILL
        txt.shader = LinearGradient(0f, ty - 10f * u, 0f, ty + 1f * u, intArrayOf(Color.rgb(255, 255, 255), Color.rgb(140, 225, 255), Color.rgb(255, 160, 90)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawText("ROAD TO ORBIT", cx, ty, txt)
        txt.shader = null
        txt.textSkewX = 0f
        txt.letterSpacing = 0f
        label(c, "RACE   ·   FLY   ·   LAUNCH", cx, ty + 5.6f * u, 3f * u, Color.argb(230, 230, 220, 255), Paint.Align.CENTER, sans, spacing = 0.25f)

        // play prompt
        val pulse = 0.5f + 0.5f * sin(t() * 3.2f)
        val pw = 46f * u
        val ph = 10f * u
        val py = h - insetB - 30f * u
        rect.set(cx - pw / 2, py - ph / 2, cx + pw / 2, py + ph / 2)
        val pressed = pressedButton == PLAY_HIT
        fill.color = Color.WHITE
        fill.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, Color.rgb(255, 150, 60), Color.rgb(255, 77, 150), Shader.TileMode.CLAMP)
        if (pressed) { rect.inset(0.8f * u, 0.5f * u) }
        c.drawRoundRect(rect, ph / 2, ph / 2, fill)
        fill.shader = null
        line.strokeWidth = 0.5f * u
        line.color = Color.argb((140 + 100 * pulse).toInt(), 255, 255, 255)
        c.drawRoundRect(rect, ph / 2, ph / 2, line)
        label(c, "TAP TO PLAY", cx, py + 1.6f * u, 5.2f * u * (1f + 0.03f * pulse), Color.WHITE, Paint.Align.CENTER, sansItalic)

        if (s.bestScore > 0) {
            if (s.bestScore != bestCache) { bestCache = s.bestScore; bestText = String.format(Locale.US, "%,d", s.bestScore) }
            label(c, "BEST  $bestText", cx, py + ph / 2 + 4.6f * u, 3.2f * u, Color.argb(230, 255, 224, 130), Paint.Align.CENTER, sans)
        }
        // difficulty selector
        val dw = 30f * u
        val dh = 6.2f * u
        val dy = py + ph / 2 + 11.5f * u
        rect.set(cx - dw / 2, dy - dh / 2, cx + dw / 2, dy + dh / 2)
        pills[3].set(rect)
        val diff = s.difficulty
        val diffColor = when (diff) {
            app.roadtoorbit.game.Difficulty.EASY -> Color.argb(190, 40, 140, 80)
            app.roadtoorbit.game.Difficulty.NORMAL -> Color.argb(190, 30, 110, 170)
            app.roadtoorbit.game.Difficulty.HARD -> Color.argb(200, 170, 40, 50)
        }
        drawPillColored(c, rect, "DIFFICULTY: ${diff.label}", diffColor, 3)
        label(c, "Drag the left side to steer  ·  Hold BOOST  ·  Tap TRANSFORM in the glowing zone", cx, h - insetB - 3f * u, 2.1f * u, Color.argb(190, 220, 225, 255), Paint.Align.CENTER, light)

        // settings pills: sound + music bottom-left, steering mode bottom-right
        val pw2 = 21f * u
        val ph2 = 6.2f * u
        val rowBottom = h - insetB - m - 6f * u
        rect.set(m + insetL, rowBottom - ph2 * 2 - 1.6f * u, m + insetL + pw2, rowBottom - ph2 - 1.6f * u)
        pills[0].set(rect)
        drawPill(c, rect, if (bridge.soundOn) "SOUND ON" else "SOUND OFF", bridge.soundOn, 0)
        rect.set(m + insetL, rowBottom - ph2, m + insetL + pw2, rowBottom)
        pills[1].set(rect)
        drawPill(c, rect, if (bridge.musicOn) "MUSIC ON" else "MUSIC OFF", bridge.musicOn, 1)
        val tiltOn = bridge.tiltOn
        rect.set(w - m - insetR - pw2 - 4f * u, rowBottom - ph2, w - m - insetR, rowBottom)
        pills[2].set(rect)
        drawPill(c, rect, if (tiltOn) "TILT STEERING" else "TOUCH STEERING", tiltOn, 2)
    }

    private fun drawPill(c: Canvas, r: RectF, text: String, on: Boolean, index: Int) =
        drawPillColored(c, r, text, if (on) Color.argb(190, 20, 120, 150) else Color.argb(150, 20, 26, 54), index)

    private fun drawPillColored(c: Canvas, r: RectF, text: String, color: Int, index: Int) {
        val pressed = pressedButton == PILL_HIT + index
        fill.shader = null
        fill.color = color
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
        line.strokeWidth = 0.35f * u
        line.color = Color.argb(if (pressed) 255 else 170, 255, 255, 255)
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, line)
        label(c, text, r.centerX(), r.centerY() + 0.9f * u, 2.5f * u, Color.WHITE, Paint.Align.CENTER, sans)
    }

    private fun drawPause(c: Canvas) {
        dim(c, 150)
        label(c, "PAUSED", w / 2, h * 0.30f, 11f * u, Color.WHITE, Paint.Align.CENTER, sansItalic)
        beginButtons()
        addButton(BTN_RESUME, "RESUME", true, h * 0.46f)
        addButton(BTN_RESTART, "RESTART RUN", false, h * 0.60f)
        addButton(BTN_MENU, "MAIN MENU", false, h * 0.74f)
        drawButtons(c)
    }

    private fun drawGameOver(c: Canvas, s: HudState) {
        dim(c, 120)
        label(c, "CRASHED!", w / 2, h * 0.24f, 12f * u, Color.rgb(255, 110, 100), Paint.Align.CENTER, sansItalic)
        label(c, s.legName + "  ·  " + s.difficulty.label, w / 2, h * 0.32f, 3.4f * u, Color.argb(220, 200, 215, 255), Paint.Align.CENTER, sans, spacing = 0.15f)
        if (s.score != scoreCache) { scoreCache = s.score; scoreText = String.format(Locale.US, "%,d", s.score) }
        label(c, "SCORE  $scoreText", w / 2, h * 0.43f, 6f * u, Color.WHITE, Paint.Align.CENTER, sansItalic)
        if (s.newBest) label(c, "NEW BEST!", w / 2, h * 0.50f, 4f * u, Color.rgb(255, 224, 110), Paint.Align.CENTER, sansItalic)
        beginButtons()
        addButton(BTN_RETRY, "RETRY THIS LEG", true, h * 0.62f)
        addButton(BTN_MENU, "MAIN MENU", false, h * 0.77f)
        drawButtons(c)
    }

    private fun drawVictory(c: Canvas, s: HudState) {
        // darken only the left part so the victory drive stays visible on the right
        fill.color = Color.WHITE
        fill.shader = LinearGradient(0f, 0f, w * 0.62f, 0f, Color.argb(190, 4, 6, 18), Color.argb(0, 4, 6, 18), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w * 0.62f, h, fill)
        fill.shader = null
        val cx = w * 0.27f
        label(c, "MISSION COMPLETE", cx, h * 0.17f, 9.4f * u, Color.rgb(255, 224, 110), Paint.Align.CENTER, sansItalic)
        label(c, s.difficulty.label, cx, h * 0.225f, 2.8f * u, Color.argb(220, 200, 215, 255), Paint.Align.CENTER, sans, spacing = 0.2f)
        for (i in 0 until 3) {
            iconStar(c, cx + (i - 1) * 10.5f * u, h * 0.31f, (if (i == 1) 5.4f else 4.4f) * u, i < s.stars)
        }
        val lineY = h * 0.45f
        val gap = 4.6f * u
        rows(c, cx, lineY, gap, arrayOf("JOURNEY", "HULL BONUS", "TIME BONUS"), intArrayOf(s.breakdownBase, s.breakdownHealth, s.breakdownTime))
        if (s.score != scoreCache) { scoreCache = s.score; scoreText = String.format(Locale.US, "%,d", s.score) }
        label(c, "TOTAL  $scoreText", cx, lineY + gap * 3 + 4f * u, 6.4f * u, Color.WHITE, Paint.Align.CENTER, sansItalic)
        if (s.newBest) label(c, "NEW BEST!", cx, lineY + gap * 3 + 10.2f * u, 3.6f * u, Color.rgb(255, 224, 110), Paint.Align.CENTER, sansItalic)
        beginButtons()
        addButton(BTN_PLAY_AGAIN, "PLAY AGAIN", true, h * 0.86f, widthU = 31f, heightU = 8f, xOffsetU = -16.5f, centerX = cx)
        addButton(BTN_MENU, "MAIN MENU", false, h * 0.86f, widthU = 25f, heightU = 8f, xOffsetU = 14.5f, centerX = cx)
        drawButtons(c)
    }

    private fun rows(c: Canvas, cx: Float, y0: Float, gap: Float, names: Array<String>, values: IntArray) {
        for (i in names.indices) {
            val y = y0 + i * gap
            label(c, names[i], cx - 4f * u, y, 3.2f * u, Color.argb(210, 200, 215, 255), Paint.Align.RIGHT, sans)
            label(c, String.format(Locale.US, "%,d", values[i]), cx + 4f * u, y, 3.4f * u, Color.WHITE, Paint.Align.LEFT, sansItalic)
        }
    }

    private fun dim(c: Canvas, alpha: Int) {
        fill.shader = null
        fill.color = Color.argb(alpha, 4, 6, 18)
        c.drawRect(0f, 0f, w, h, fill)
    }

    // ---- buttons ----------------------------------------------------------------------------------------------

    private fun beginButtons() { buttonCount = 0 }

    private fun addButton(id: Int, text: String, primary: Boolean, cy: Float, widthU: Float = 46f, heightU: Float = 9f, xOffsetU: Float = 0f, centerX: Float = w / 2) {
        val b = buttons[buttonCount++]
        b.id = id; b.label = text; b.primary = primary
        val bw = widthU * u
        val bh = heightU * u
        b.r.set(centerX - bw / 2 + xOffsetU * u, cy - bh / 2, centerX + bw / 2 + xOffsetU * u, cy + bh / 2)
    }

    private fun drawButtons(c: Canvas) {
        for (i in 0 until buttonCount) {
            val b = buttons[i]
            val pressed = pressedButton == i
            rect.set(b.r)
            if (pressed) rect.inset(0.7f * u, 0.5f * u)
            val rad = rect.height() / 2
            if (b.primary) {
                fill.color = Color.WHITE
                fill.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, Color.rgb(255, 150, 60), Color.rgb(255, 77, 150), Shader.TileMode.CLAMP)
            } else {
                fill.shader = null
                fill.color = Color.argb(190, 24, 32, 70)
            }
            c.drawRoundRect(rect, rad, rad, fill)
            fill.shader = null
            line.strokeWidth = 0.4f * u
            line.color = Color.argb(if (b.primary) 230 else 160, 255, 255, 255)
            c.drawRoundRect(rect, rad, rad, line)
            label(c, b.label, rect.centerX(), rect.centerY() + 1.6f * u, 4.2f * u, Color.WHITE, Paint.Align.CENTER, sansItalic)
        }
    }

    // ---- text & icons --------------------------------------------------------------------------------------------

    private fun label(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align, face: Typeface, alpha: Float = 1f, spacing: Float = 0f,
    ) {
        txt.typeface = face
        txt.textAlign = align
        txt.textSize = size
        txt.letterSpacing = spacing
        txt.style = Paint.Style.FILL
        txt.shader = null
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        txt.color = Color.argb((a * 0.7f).toInt(), 0, 0, 20)
        c.drawText(s, x + size * 0.05f, y + size * 0.07f, txt)
        txt.color = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
        c.drawText(s, x, y, txt)
        txt.letterSpacing = 0f
    }

    private fun iconCar(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.shader = null; fill.color = color
        path.reset()
        path.moveTo(cx - s, cy + 0.25f * s); path.lineTo(cx - 0.92f * s, cy + 0.02f * s); path.lineTo(cx - 0.5f * s, cy - 0.12f * s)
        path.lineTo(cx - 0.25f * s, cy - 0.5f * s); path.lineTo(cx + 0.35f * s, cy - 0.5f * s); path.lineTo(cx + 0.6f * s, cy - 0.1f * s)
        path.lineTo(cx + 0.95f * s, cy + 0.02f * s); path.lineTo(cx + s, cy + 0.25f * s); path.close()
        c.drawPath(path, fill)
        c.drawCircle(cx - 0.55f * s, cy + 0.32f * s, 0.22f * s, fill)
        c.drawCircle(cx + 0.55f * s, cy + 0.32f * s, 0.22f * s, fill)
    }

    private fun iconPlane(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.shader = null; fill.color = color
        path.reset()
        // top-down plane pointing up
        path.moveTo(cx, cy - s); path.lineTo(cx + 0.14f * s, cy - 0.5f * s); path.lineTo(cx + 0.95f * s, cy + 0.12f * s)
        path.lineTo(cx + 0.95f * s, cy + 0.32f * s); path.lineTo(cx + 0.14f * s, cy + 0.12f * s); path.lineTo(cx + 0.12f * s, cy + 0.62f * s)
        path.lineTo(cx + 0.42f * s, cy + 0.85f * s); path.lineTo(cx + 0.42f * s, cy + 0.98f * s); path.lineTo(cx, cy + 0.88f * s)
        path.lineTo(cx - 0.42f * s, cy + 0.98f * s); path.lineTo(cx - 0.42f * s, cy + 0.85f * s); path.lineTo(cx - 0.12f * s, cy + 0.62f * s)
        path.lineTo(cx - 0.14f * s, cy + 0.12f * s); path.lineTo(cx - 0.95f * s, cy + 0.32f * s); path.lineTo(cx - 0.95f * s, cy + 0.12f * s)
        path.lineTo(cx - 0.14f * s, cy - 0.5f * s); path.close()
        c.drawPath(path, fill)
    }

    private fun iconRocket(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.shader = null; fill.color = color
        path.reset()
        path.moveTo(cx, cy - s)
        path.cubicTo(cx + 0.55f * s, cy - 0.6f * s, cx + 0.5f * s, cy + 0.2f * s, cx + 0.32f * s, cy + 0.55f * s)
        path.lineTo(cx - 0.32f * s, cy + 0.55f * s)
        path.cubicTo(cx - 0.5f * s, cy + 0.2f * s, cx - 0.55f * s, cy - 0.6f * s, cx, cy - s)
        path.close()
        c.drawPath(path, fill)
        path.reset()
        path.moveTo(cx + 0.32f * s, cy + 0.15f * s); path.lineTo(cx + 0.78f * s, cy + 0.78f * s); path.lineTo(cx + 0.32f * s, cy + 0.55f * s); path.close()
        path.moveTo(cx - 0.32f * s, cy + 0.15f * s); path.lineTo(cx - 0.78f * s, cy + 0.78f * s); path.lineTo(cx - 0.32f * s, cy + 0.55f * s); path.close()
        c.drawPath(path, fill)
        path.reset()
        path.moveTo(cx - 0.2f * s, cy + 0.68f * s); path.lineTo(cx, cy + 1.05f * s); path.lineTo(cx + 0.2f * s, cy + 0.68f * s); path.close()
        c.drawPath(path, fill)
    }

    private fun iconShield(c: Canvas, cx: Float, cy: Float, s: Float, on: Boolean, bright: Boolean) {
        path.reset()
        path.moveTo(cx, cy - s); path.lineTo(cx + 0.82f * s, cy - 0.68f * s); path.lineTo(cx + 0.82f * s, cy + 0.1f * s)
        path.cubicTo(cx + 0.82f * s, cy + 0.62f * s, cx + 0.3f * s, cy + 0.92f * s, cx, cy + s)
        path.cubicTo(cx - 0.3f * s, cy + 0.92f * s, cx - 0.82f * s, cy + 0.62f * s, cx - 0.82f * s, cy + 0.1f * s)
        path.lineTo(cx - 0.82f * s, cy - 0.68f * s); path.close()
        fill.shader = null
        fill.color = if (on) (if (bright) Color.rgb(255, 255, 255) else Color.rgb(90, 220, 255)) else Color.argb(80, 255, 255, 255)
        c.drawPath(path, fill)
        line.strokeWidth = 0.3f * u
        line.color = Color.argb(if (on) 255 else 120, 255, 255, 255)
        c.drawPath(path, line)
    }

    private fun iconStar(c: Canvas, cx: Float, cy: Float, r: Float, on: Boolean) {
        path.reset()
        for (i in 0 until 10) {
            val a = (-PI / 2 + i * PI / 5).toFloat()
            val rr = if (i % 2 == 0) r else r * 0.45f
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        fill.shader = null
        fill.color = if (on) Color.rgb(255, 214, 70) else Color.argb(70, 255, 255, 255)
        c.drawPath(path, fill)
        line.strokeWidth = 0.4f * u
        line.color = if (on) Color.rgb(255, 245, 190) else Color.argb(110, 255, 255, 255)
        c.drawPath(path, line)
    }

    private fun drawBolt(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        path.reset()
        path.moveTo(cx + 0.15f * s, cy - 0.55f * s); path.lineTo(cx - 0.35f * s, cy + 0.08f * s); path.lineTo(cx - 0.02f * s, cy + 0.08f * s)
        path.lineTo(cx - 0.18f * s, cy + 0.6f * s); path.lineTo(cx + 0.38f * s, cy - 0.1f * s); path.lineTo(cx + 0.04f * s, cy - 0.1f * s); path.close()
        fill.shader = null; fill.color = color
        c.drawPath(path, fill)
    }

    // ===================================================================================================
    // touch
    // ===================================================================================================

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val s = bridge.hud.latest
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                down(ev.getPointerId(i), ev.getX(i), ev.getY(i), s)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until ev.pointerCount) move(ev.getPointerId(i), ev.getX(i), ev.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = ev.actionIndex
                up(ev.getPointerId(i), ev.getX(i), ev.getY(i), s)
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun inCircle(c: FloatArray, x: Float, y: Float, slop: Float = 1f): Boolean =
        hypot(x - c[0], y - c[1]) <= c[2] * slop

    private fun down(id: Int, x: Float, y: Float, s: HudState) {
        if (bridge.fatalError != null) return
        downX = x; downY = y
        val gameplay = (s.phase == Phase.RUN || s.phase == Phase.COUNTDOWN || s.phase == Phase.CRASHING) && !bridge.paused
        if (!gameplay) {
            pressedButton = hitButton(x, y, s)
            return
        }
        if ((s.phase == Phase.RUN || s.phase == Phase.COUNTDOWN) && inCircle(pause, x, y, 1.25f)) {
            bridge.paused = true
            releaseControls()
            return
        }
        if (s.legIndex < 2 && inCircle(xform, x, y, 1.18f)) {
            if (s.transformReady) bridge.input.requestTransform()
            xformPressUntil = SystemClock.uptimeMillis() + 160
            return
        }
        if (inCircle(boost, x, y, 1.22f)) {
            boostId = id
            bridge.input.boost = true
            return
        }
        if (stickId == -1 && x < w * 0.58f) {
            stickId = id
            stickOx = x; stickOy = y; stickX = x; stickY = y
            bridge.stickActive = true
            bridge.input.steerX = 0f; bridge.input.steerY = 0f
        }
    }

    private fun move(id: Int, x: Float, y: Float) {
        if (id != stickId) return
        val r = 9.5f * u
        var dx = x - stickOx
        var dy = y - stickOy
        val d = hypot(dx, dy)
        if (d > r * 1.35f) {
            // drag the stick's origin along so the thumb never runs out of travel
            val k = (d - r * 1.35f) / d
            stickOx += dx * k; stickOy += dy * k
            dx = x - stickOx; dy = y - stickOy
        }
        val dd = hypot(dx, dy)
        val cl = if (dd > r) r / dd else 1f
        stickX = stickOx + dx * cl
        stickY = stickOy + dy * cl
        val nx = dx * cl / r
        val ny = -dy * cl / r
        bridge.input.steerX = shape(nx)
        bridge.input.steerY = shape(ny)
    }

    private fun shape(v: Float): Float {
        val a = abs(v)
        if (a < 0.06f) return 0f
        val s = (((a - 0.06f) / 0.94f).coerceAtMost(1f)).let { Math.pow(it.toDouble(), 1.15).toFloat() }
        return if (v < 0f) -s else s
    }

    private fun up(id: Int, x: Float, y: Float, s: HudState) {
        if (id == stickId) {
            stickId = -1
            bridge.stickActive = false
            bridge.input.steerX = 0f; bridge.input.steerY = 0f
        }
        if (id == boostId) {
            boostId = -1
            bridge.input.boost = false
        }
        if (pressedButton != -1) {
            val hit = hitButton(x, y, s)
            val pressed = pressedButton
            pressedButton = -1
            if (hit == pressed && hit != -1) activate(hit, s)
        }
    }

    private fun cancelAll() {
        releaseControls()
        pressedButton = -1
    }

    private fun releaseControls() {
        stickId = -1; boostId = -1
        bridge.stickActive = false
        bridge.input.neutral()
    }

    /** Index of the overlay/menu element under (x, y): button index, PILL_HIT + n, PLAY_HIT or -1. */
    private fun hitButton(x: Float, y: Float, s: HudState): Int {
        if (s.phase == Phase.MENU && !bridge.paused) {
            for (i in 0 until pills.size) if (pills[i].contains(x, y)) return PILL_HIT + i
            return PLAY_HIT
        }
        for (i in 0 until buttonCount) if (buttons[i].r.contains(x, y)) return i
        return -1
    }

    private fun activate(index: Int, s: HudState) {
        when {
            index == PLAY_HIT -> { bridge.tiltRecalibrate = true; bridge.post(UiAction.PLAY) }
            index == PILL_HIT -> bridge.post(UiAction.TOGGLE_SOUND)
            index == PILL_HIT + 1 -> bridge.post(UiAction.TOGGLE_MUSIC)
            index == PILL_HIT + 2 -> { bridge.post(UiAction.TOGGLE_TILT); bridge.tiltRecalibrate = true }
            index == PILL_HIT + 3 -> bridge.post(UiAction.CYCLE_DIFFICULTY)
            index in 0 until buttonCount -> when (buttons[index].id) {
                BTN_RESUME -> bridge.paused = false
                BTN_RESTART -> { bridge.tiltRecalibrate = true; bridge.post(UiAction.PLAY) }
                BTN_RETRY -> { bridge.tiltRecalibrate = true; bridge.post(UiAction.RETRY) }
                BTN_PLAY_AGAIN -> { bridge.tiltRecalibrate = true; bridge.post(UiAction.PLAY) }
                BTN_MENU -> bridge.post(UiAction.MENU)
            }
        }
    }

    private companion object {
        const val PLAY_HIT = 100
        const val PILL_HIT = 200
        const val BTN_RESUME = 1
        const val BTN_RESTART = 2
        const val BTN_RETRY = 3
        const val BTN_PLAY_AGAIN = 4
        const val BTN_MENU = 5
    }
}
