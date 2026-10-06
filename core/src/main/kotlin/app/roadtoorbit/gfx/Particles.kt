package app.roadtoorbit.gfx

import app.roadtoorbit.gl.GL
import app.roadtoorbit.gl.Gles
import app.roadtoorbit.math.Rng
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * CPU particle simulation (struct-of-arrays, no allocation per particle). Particles live in the
 * scrolling world frame: those flagged `treadmill` are carried toward the camera by the world speed
 * so trails stream backwards past a stationary vehicle.
 */
class ParticleSystem(val capacity: Int = 1800) {
    private val px = FloatArray(capacity)
    private val py = FloatArray(capacity)
    private val pz = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val vz = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size0 = FloatArray(capacity)
    private val size1 = FloatArray(capacity)
    private val col0 = IntArray(capacity)
    private val col1 = IntArray(capacity)
    private val gravity = FloatArray(capacity)
    private val drag = FloatArray(capacity)
    private val additive = BooleanArray(capacity)
    private val treadmill = BooleanArray(capacity)

    var count = 0
        private set

    val rng = Rng(0x5eed)

    fun clear() {
        count = 0
    }

    fun emit(
        x: Float, y: Float, z: Float,
        dx: Float, dy: Float, dz: Float,
        lifeSeconds: Float, startSize: Float, endSize: Float,
        startColor: Int, endColor: Int,
        additiveBlend: Boolean = true, gravityY: Float = 0f, dragRate: Float = 0f, carriedByWorld: Boolean = true,
    ) {
        // when full, recycle a random slot so bursts never silently vanish
        val i = if (count < capacity) count++ else rng.int(capacity)
        px[i] = x; py[i] = y; pz[i] = z
        vx[i] = dx; vy[i] = dy; vz[i] = dz
        life[i] = lifeSeconds; maxLife[i] = lifeSeconds
        size0[i] = startSize; size1[i] = endSize
        col0[i] = startColor; col1[i] = endColor
        gravity[i] = gravityY; drag[i] = dragRate
        additive[i] = additiveBlend; treadmill[i] = carriedByWorld
    }

    /** Radial burst helper used by explosions, sparks and pickups. */
    fun burst(
        x: Float, y: Float, z: Float, n: Int, speed: Float, lifeSeconds: Float,
        size: Float, startColor: Int, endColor: Int,
        additiveBlend: Boolean = true, gravityY: Float = 0f, dragRate: Float = 1.5f, carriedByWorld: Boolean = true,
    ) {
        for (k in 0 until n) {
            var dx = rng.range(-1f, 1f); var dy = rng.range(-1f, 1f); var dz = rng.range(-1f, 1f)
            val l = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-3f)
            val s = speed * rng.range(0.35f, 1f) / l
            dx *= s; dy *= s; dz *= s
            emit(x, y, z, dx, dy, dz, lifeSeconds * rng.range(0.6f, 1.1f), size * rng.range(0.7f, 1.2f), size * 0.2f, startColor, endColor, additiveBlend, gravityY, dragRate, carriedByWorld)
        }
    }

    fun update(dt: Float, worldSpeed: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = count - 1
                if (i != last) copy(last, i)
                count--
                continue
            }
            if (drag[i] > 0f) {
                val k = exp(-drag[i] * dt)
                vx[i] *= k; vy[i] *= k; vz[i] *= k
            }
            vy[i] += gravity[i] * dt
            px[i] += vx[i] * dt
            py[i] += vy[i] * dt
            pz[i] += vz[i] * dt
            if (treadmill[i]) pz[i] += worldSpeed * dt
            i++
        }
    }

    private fun copy(from: Int, to: Int) {
        px[to] = px[from]; py[to] = py[from]; pz[to] = pz[from]
        vx[to] = vx[from]; vy[to] = vy[from]; vz[to] = vz[from]
        life[to] = life[from]; maxLife[to] = maxLife[from]
        size0[to] = size0[from]; size1[to] = size1[from]
        col0[to] = col0[from]; col1[to] = col1[from]
        gravity[to] = gravity[from]; drag[to] = drag[from]
        additive[to] = additive[from]; treadmill[to] = treadmill[from]
    }

    /** Packs particles of one blend class into [out] (8 floats each); returns how many were written. */
    internal fun pack(out: java.nio.FloatBuffer, wantAdditive: Boolean): Int {
        var written = 0
        for (i in 0 until count) {
            if (additive[i] != wantAdditive) continue
            val t = 1f - life[i] / maxLife[i]
            val size = size0[i] + (size1[i] - size0[i]) * t
            val c0 = col0[i]; val c1 = col1[i]
            out.put(px[i]); out.put(py[i]); out.put(pz[i]); out.put(size)
            out.put(Col.r(c0) + (Col.r(c1) - Col.r(c0)) * t)
            out.put(Col.g(c0) + (Col.g(c1) - Col.g(c0)) * t)
            out.put(Col.b(c0) + (Col.b(c1) - Col.b(c0)) * t)
            out.put(Col.a(c0) + (Col.a(c1) - Col.a(c0)) * t)
            written++
        }
        return written
    }
}

/** GPU side of the particle system: one streaming vertex buffer drawn as point sprites. */
class ParticleRenderer(private val gl: Gles, private val capacity: Int) {
    private val vao = gl.genVertexArray()
    private val vbo = gl.genBuffer()
    private val bytes = ByteBuffer.allocateDirect(capacity * FLOATS * 4).order(ByteOrder.nativeOrder())
    private val floats = bytes.asFloatBuffer()

    init {
        gl.bindVertexArray(vao)
        gl.bindBuffer(GL.ARRAY_BUFFER, vbo)
        gl.bufferData(GL.ARRAY_BUFFER, capacity * FLOATS * 4, null, GL.STREAM_DRAW)
        gl.enableVertexAttribArray(0)
        gl.vertexAttribPointer(0, 4, GL.FLOAT, false, FLOATS * 4, 0)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribPointer(1, 4, GL.FLOAT, false, FLOATS * 4, 16)
        gl.bindVertexArray(0)
    }

    fun draw(renderer: Renderer, system: ParticleSystem, additive: Boolean) {
        floats.clear()
        val n = system.pack(floats, additive)
        if (n == 0) return
        renderer.setBlend(if (additive) Blend.ADDITIVE else Blend.ALPHA)
        renderer.usePoints()
        bytes.limit(n * FLOATS * 4)
        bytes.position(0)
        gl.bindVertexArray(vao)
        gl.bindBuffer(GL.ARRAY_BUFFER, vbo)
        gl.bufferSubData(GL.ARRAY_BUFFER, 0, n * FLOATS * 4, bytes)
        gl.drawArrays(GL.POINTS, 0, n)
    }

    fun release() {
        gl.deleteVertexArray(vao)
        gl.deleteBuffer(vbo)
    }

    companion object {
        const val FLOATS = 8
    }
}
