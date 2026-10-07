package app.roadtoorbit.gfx

import app.roadtoorbit.gl.GL
import app.roadtoorbit.gl.Gles
import app.roadtoorbit.gl.GpuMesh
import app.roadtoorbit.gl.ShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import app.roadtoorbit.math.Mat4

/** How the next draws blend with the framebuffer. */
enum class Blend { OPAQUE, ALPHA, ADDITIVE }

/**
 * Owns the shader programs and the frame-level GL state. Scene code (SceneRenderer, particles, the
 * vehicle) issues draws through here and never touches raw GL.
 *
 * Call order per frame: [beginFrame] → opaque draws → [setBlend] + translucent draws → [endFrame].
 */
class Renderer(private val gl: Gles, library: MeshLibrary) {
    val meshes = GpuMeshCache(gl, library)

    var width = 1
        private set
    var height = 1
        private set

    private val lit = ShaderProgram(gl, "lit", Shaders.LIT_VERT, Shaders.LIT_FRAG)
    private val sky = ShaderProgram(gl, "sky", Shaders.SKY_VERT, Shaders.SKY_FRAG)
    private val terrain = ShaderProgram(gl, "terrain", Shaders.TERRAIN_VERT, Shaders.TERRAIN_FRAG)
    private val particle = ShaderProgram(gl, "particle", Shaders.PARTICLE_VERT, Shaders.PARTICLE_FRAG)

    // ---- lit uniforms
    private val lModel = lit.uniform("uModel")
    private val lNormal = lit.uniform("uNormalMat")
    private val lView = lit.uniform("uView")
    private val lProj = lit.uniform("uProj")
    private val lBend = lit.uniform("uBend")
    private val lCam = lit.uniform("uCamPos")
    private val lSunDir = lit.uniform("uSunDir")
    private val lSunCol = lit.uniform("uSunColor")
    private val lAmbSky = lit.uniform("uAmbSky")
    private val lAmbGround = lit.uniform("uAmbGround")
    private val lFogCol = lit.uniform("uFogColor")
    private val lFogDen = lit.uniform("uFogDensity")
    private val lTint = lit.uniform("uTint")
    private val lMat = lit.uniform("uMat")
    private val lRim = lit.uniform("uRim")

    // ---- sky uniforms
    private val sRight = sky.uniform("uCamRight")
    private val sUp = sky.uniform("uCamUp")
    private val sFwd = sky.uniform("uCamFwd")
    private val sTan = sky.uniform("uTanFov")
    private val sZenith = sky.uniform("uZenith")
    private val sHorizon = sky.uniform("uHorizon")
    private val sGround = sky.uniform("uGroundCol")
    private val sSunDir = sky.uniform("uSunDir")
    private val sSunCol = sky.uniform("uSunColor")
    private val sSky = sky.uniform("uSky")
    private val sNebula = sky.uniform("uNebula")

    // ---- terrain uniforms
    private val tView = terrain.uniform("uView")
    private val tProj = terrain.uniform("uProj")
    private val tBend = terrain.uniform("uBend")
    private val tGrid = terrain.uniform("uGrid")
    private val tSlide = terrain.uniform("uSlide")
    private val tTer = terrain.uniform("uTer")
    private val tTerCol = terrain.uniform("uTerCol")
    private val tWorld = terrain.uniform("uWorld")
    private val tCam = terrain.uniform("uCamPos")
    private val tSunDir = terrain.uniform("uSunDir")
    private val tSunCol = terrain.uniform("uSunColor")
    private val tAmbSky = terrain.uniform("uAmbSky")
    private val tAmbGround = terrain.uniform("uAmbGround")
    private val tFogCol = terrain.uniform("uFogColor")
    private val tFogDen = terrain.uniform("uFogDensity")

    // ---- particle uniforms
    private val pView = particle.uniform("uView")
    private val pProj = particle.uniform("uProj")
    private val pBend = particle.uniform("uBend")
    private val pScale = particle.uniform("uPixelScale")

    private val nebulaTexture: Int = createNebulaTexture()
    private val grids = ArrayList<TerrainGrid>(2)

    private class TerrainGrid(val cols: Int, val rows: Int, val vao: Int, val ibo: Int, val indexCount: Int)

    private fun createNebulaTexture(): Int {
        val faces = NebulaCubeCache.faces
        val tex = gl.genTexture()
        gl.activeTexture(GL.TEXTURE0)
        gl.bindTexture(GL.TEXTURE_CUBE_MAP, tex)
        val n = NebulaCube.SIZE
        for (f in 0 until 6) {
            val buf = ByteBuffer.allocateDirect(n * n * 4).order(ByteOrder.nativeOrder())
            buf.put(faces[f]); buf.flip()
            gl.texImage2D(GL.TEXTURE_CUBE_MAP_POSITIVE_X + f, 0, GL.RGBA, n, n, GL.RGBA, GL.UNSIGNED_BYTE, buf)
        }
        gl.texParameteri(GL.TEXTURE_CUBE_MAP, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_CUBE_MAP, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_CUBE_MAP, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
        gl.texParameteri(GL.TEXTURE_CUBE_MAP, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
        gl.texParameteri(GL.TEXTURE_CUBE_MAP, GL.TEXTURE_WRAP_R, GL.CLAMP_TO_EDGE)
        return tex
    }

    private fun terrainGrid(cols: Int, rows: Int): TerrainGrid {
        for (g in grids) if (g.cols == cols && g.rows == rows) return g
        val stride = cols + 1
        val count = cols * rows * 6
        require(stride * (rows + 1) < 65536) { "terrain grid too large for 16-bit indices" }
        val bytes = ByteBuffer.allocateDirect(count * 2).order(ByteOrder.nativeOrder())
        val shorts = bytes.asShortBuffer()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val v00 = r * stride + c
                val v10 = v00 + 1
                val v01 = v00 + stride
                val v11 = v01 + 1
                // two CCW triangles seen from above
                shorts.put(v00.toShort()); shorts.put(v10.toShort()); shorts.put(v11.toShort())
                shorts.put(v00.toShort()); shorts.put(v11.toShort()); shorts.put(v01.toShort())
            }
        }
        bytes.limit(count * 2); bytes.position(0)
        val vao = gl.genVertexArray()
        val ibo = gl.genBuffer()
        gl.bindVertexArray(vao)
        gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, ibo)
        gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, count * 2, bytes, GL.STATIC_DRAW)
        gl.bindVertexArray(0)
        return TerrainGrid(cols, rows, vao, ibo, count).also { grids.add(it) }
    }

    private val normalMat = FloatArray(9)
    private var env = Environment()
    private var cam = Camera()
    private var current = 0
    private var blend = Blend.OPAQUE
    private var litReady = false
    private var terrainReady = false
    private var particleReady = false

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        gl.viewport(0, 0, width, height)
    }

    /** Clears the frame, draws the sky and leaves the GL in "opaque world" state. */
    fun beginFrame(environment: Environment, camera: Camera) {
        env = environment
        cam = camera
        litReady = false; terrainReady = false; particleReady = false
        current = 0

        gl.viewport(0, 0, width, height)
        gl.depthMask(true)
        gl.clearColor(env.horizon[0], env.horizon[1], env.horizon[2], 1f)
        gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)

        // sky: full-screen triangle generated from gl_VertexID, no depth interaction
        gl.disable(GL.DEPTH_TEST)
        gl.disable(GL.CULL_FACE)
        gl.disable(GL.BLEND)
        blend = Blend.OPAQUE
        sky.use()
        current = sky.id
        gl.uniform3f(sRight, cam.right[0], cam.right[1], cam.right[2])
        gl.uniform3f(sUp, cam.up[0], cam.up[1], cam.up[2])
        gl.uniform3f(sFwd, cam.fwd[0], cam.fwd[1], cam.fwd[2])
        val t = cam.tanHalfFov
        gl.uniform2f(sTan, t * cam.aspect, t)
        gl.uniform3f(sZenith, env.zenith[0], env.zenith[1], env.zenith[2])
        gl.uniform3f(sHorizon, env.horizon[0], env.horizon[1], env.horizon[2])
        gl.uniform3f(sGround, env.groundCol[0], env.groundCol[1], env.groundCol[2])
        gl.uniform3f(sSunDir, env.sunDir[0], env.sunDir[1], env.sunDir[2])
        gl.uniform3f(sSunCol, env.sunColor[0], env.sunColor[1], env.sunColor[2])
        gl.uniform4f(sSky, env.starAmount, env.nebula, env.time, env.sunDisc)
        gl.activeTexture(GL.TEXTURE0)
        gl.bindTexture(GL.TEXTURE_CUBE_MAP, nebulaTexture)
        gl.uniform1i(sNebula, 0)
        gl.bindVertexArray(0)
        gl.drawArrays(GL.TRIANGLES, 0, 3)

        gl.enable(GL.DEPTH_TEST)
        gl.depthFunc(GL.LEQUAL)
        gl.enable(GL.CULL_FACE)
        gl.cullFace(GL.BACK)
    }

    fun endFrame() {
        setBlend(Blend.OPAQUE)
    }

    fun setBlend(mode: Blend) {
        if (mode == blend) return
        blend = mode
        when (mode) {
            Blend.OPAQUE -> {
                gl.disable(GL.BLEND)
                gl.depthMask(true)
            }
            Blend.ALPHA -> {
                gl.enable(GL.BLEND)
                gl.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)
                gl.depthMask(false)
            }
            Blend.ADDITIVE -> {
                gl.enable(GL.BLEND)
                gl.blendFunc(GL.SRC_ALPHA, GL.ONE)
                gl.depthMask(false)
            }
        }
    }

    fun setCull(enabled: Boolean) {
        if (enabled) gl.enable(GL.CULL_FACE) else gl.disable(GL.CULL_FACE)
    }

    // ---- lit meshes ----------------------------------------------------------------------------

    private fun useLit() {
        if (current != lit.id) {
            lit.use()
            current = lit.id
        }
        if (!litReady) {
            litReady = true
            gl.uniformMatrix4fv(lView, cam.view)
            gl.uniformMatrix4fv(lProj, cam.proj)
            gl.uniform3f(lBend, env.bendX, env.bendY, env.bendStart)
            gl.uniform3f(lCam, cam.ex, cam.ey, cam.ez)
            gl.uniform3f(lSunDir, env.sunDir[0], env.sunDir[1], env.sunDir[2])
            gl.uniform3f(lSunCol, env.sunColor[0], env.sunColor[1], env.sunColor[2])
            gl.uniform3f(lAmbSky, env.ambSky[0], env.ambSky[1], env.ambSky[2])
            gl.uniform3f(lAmbGround, env.ambGround[0], env.ambGround[1], env.ambGround[2])
            gl.uniform3f(lFogCol, env.fogColor[0], env.fogColor[1], env.fogColor[2])
            gl.uniform1f(lFogDen, env.fogDensity)
        }
    }

    /** Draws [mesh] with a model matrix; silently skips meshes whose scale collapsed to ~0. */
    fun drawLit(mesh: GpuMesh, model: FloatArray, m: Material) {
        if (Mat4.maxColumnLengthSq(model) < 1e-8f) return
        useLit()
        Mat4.normalMatrix(normalMat, model)
        gl.uniformMatrix4fv(lModel, model)
        gl.uniformMatrix3fv(lNormal, normalMat)
        gl.uniform4f(lTint, m.r, m.g, m.b, m.a)
        gl.uniform4f(lMat, m.spec, m.shine, m.emissive, m.fogScale)
        gl.uniform4f(lRim, m.rimR, m.rimG, m.rimB, m.rimPower)
        mesh.draw()
    }

    // ---- terrain -------------------------------------------------------------------------------

    /**
     * Draws the procedural terrain grid. [travelled] is the distance along the route (the grid is
     * anchored to multiples of [cell] so hills never swim); [cols]/[rows] size the grid and
     * [rowsBehind] is how many rows lie behind the camera.
     */
    fun drawTerrain(travelled: Double, cell: Float, cols: Int, rows: Int, rowsBehind: Int, tint: FloatArray) {
        if (!env.terrainOn) return
        if (current != terrain.id) {
            terrain.use()
            current = terrain.id
        }
        if (!terrainReady) {
            terrainReady = true
            gl.uniformMatrix4fv(tView, cam.view)
            gl.uniformMatrix4fv(tProj, cam.proj)
            gl.uniform3f(tBend, env.bendX, env.bendY, env.bendStart)
            gl.uniform3f(tCam, cam.ex, cam.ey, cam.ez)
            gl.uniform3f(tSunDir, env.sunDir[0], env.sunDir[1], env.sunDir[2])
            gl.uniform3f(tSunCol, env.sunColor[0], env.sunColor[1], env.sunColor[2])
            gl.uniform3f(tAmbSky, env.ambSky[0], env.ambSky[1], env.ambSky[2])
            gl.uniform3f(tAmbGround, env.ambGround[0], env.ambGround[1], env.ambGround[2])
            gl.uniform3f(tFogCol, env.fogColor[0], env.fogColor[1], env.fogColor[2])
            gl.uniform1f(tFogDen, env.fogDensity)
        }
        val rowIndex = Math.floor(travelled / cell).toLong()
        val slide = (travelled - rowIndex * cell).toFloat()
        gl.uniform4f(tGrid, cell, cols.toFloat(), rowsBehind.toFloat(), (rowIndex - rowsBehind).toFloat())
        gl.uniform1f(tSlide, slide)
        gl.uniform4f(tTer, env.groundY, env.terrainAmp, env.corridor, env.terrainStyle)
        gl.uniform4f(tTerCol, tint[0], tint[1], tint[2], 1f)
        gl.uniform1f(tWorld, env.world)
        gl.disable(GL.CULL_FACE)
        val grid = terrainGrid(cols, rows)
        gl.bindVertexArray(grid.vao)
        gl.drawElements(GL.TRIANGLES, grid.indexCount, GL.UNSIGNED_SHORT, 0)
        gl.enable(GL.CULL_FACE)
    }

    // ---- particles -----------------------------------------------------------------------------

    /** Prepares the point-sprite program; follow with [ParticleBatch.draw]. */
    fun usePoints() {
        if (current != particle.id) {
            particle.use()
            current = particle.id
        }
        if (!particleReady) {
            particleReady = true
            gl.uniformMatrix4fv(pView, cam.view)
            gl.uniformMatrix4fv(pProj, cam.proj)
            gl.uniform3f(pBend, env.bendX, env.bendY, env.bendStart)
            val pixelScale = height / (2f * cam.tanHalfFov)
            gl.uniform1f(pScale, pixelScale)
        }
    }

    val glRef: Gles get() = gl

    fun release() {
        meshes.release()
        for (g in grids) { gl.deleteVertexArray(g.vao); gl.deleteBuffer(g.ibo) }
        grids.clear()
        gl.deleteTexture(nebulaTexture)
        lit.release(); sky.release(); terrain.release(); particle.release()
    }
}

/** The baked nebula pixels are generated once per process and reused after a GL context loss. */
internal object NebulaCubeCache {
    val faces: Array<ByteArray> by lazy { NebulaCube.generate() }
}

/** Lazily uploads library meshes to the GPU the first time they are drawn. */
class GpuMeshCache(private val gl: Gles, private val library: MeshLibrary) {
    private val cache = arrayOfNulls<GpuMesh>(MeshId.values().size)

    operator fun get(id: MeshId): GpuMesh =
        cache[id.ordinal] ?: GpuMesh.static(gl, library.data(id)).also { cache[id.ordinal] = it }

    fun release() {
        for (i in cache.indices) {
            cache[i]?.release()
            cache[i] = null
        }
    }
}
