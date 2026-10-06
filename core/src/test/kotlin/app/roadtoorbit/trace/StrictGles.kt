package app.roadtoorbit.trace

import app.roadtoorbit.gl.GL
import app.roadtoorbit.gl.Gles
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Thrown by [StrictGles] when the renderer misuses the GL API in a way a real driver may crash on. */
class GlViolation(message: String) : RuntimeException(message)

/**
 * A [Gles] with no GPU behind it that validates every call the way a strict driver would: objects must exist,
 * the right program/VAO/buffer must be bound, uploads must read the bytes they claim from position 0 of a
 * direct native-order buffer, draw calls must stay inside the buffers they read, uniforms must belong to the
 * program in use, and numbers must be finite. Android's GLES bindings read a [ByteBuffer] from its *current
 * position* and a draw that reads past a buffer can crash the driver, neither of which the WebGL replay shows.
 */
class StrictGles : Gles {
    private var nextName = 1
    private var nextLocation = 1
    private fun name() = nextName++

    private class Program {
        var linked = false
        val attached = HashSet<Int>()
        val uniforms = HashMap<String, Int>()
        val locations = HashSet<Int>()
    }

    private class Attrib {
        var enabled = false
        var buffer = 0
        var size = 0
        var type = 0
        var stride = 0
        var offset = 0
    }

    private class Vao {
        var element = 0
        val attribs = Array(16) { Attrib() }
    }

    private val shaders = HashSet<Int>()
    private val programs = HashMap<Int, Program>()
    private var current = 0
    private val buffers = HashMap<Int, Int>() // name -> storage in bytes (-1 = no storage yet)
    private val vaos = HashMap<Int, Vao>()
    private val defaultVao = Vao()
    private var boundVao = 0
    private var arrayBuffer = 0
    private val textures = HashSet<Int>()
    private var boundCube = 0

    // per-frame statistics, reset by [endFrame]
    var drawCalls = 0; private set
    var vertices = 0L; private set
    var maxDrawCalls = 0; private set
    var maxVertices = 0L; private set
    var frames = 0; private set

    fun endFrame() {
        maxDrawCalls = maxOf(maxDrawCalls, drawCalls)
        maxVertices = maxOf(maxVertices, vertices)
        drawCalls = 0
        vertices = 0
        frames++
    }

    /** Live object counts, for leak checks. */
    fun liveObjects(): Int = shaders.size + programs.size + buffers.size + vaos.size + textures.size

    private fun fail(msg: String): Nothing = throw GlViolation(msg)

    private fun finite(vararg v: Float) {
        for (x in v) if (!x.isFinite()) fail("non-finite value passed to GL: ${v.toList()}")
    }

    private fun vao(): Vao = if (boundVao == 0) defaultVao else vaos.getValue(boundVao)

    private fun typeBytes(type: Int): Int = when (type) {
        GL.FLOAT -> 4
        GL.UNSIGNED_BYTE -> 1
        GL.UNSIGNED_SHORT -> 2
        else -> fail("unexpected GL type 0x${type.toString(16)}")
    }

    private fun checkUpload(data: ByteBuffer, bytes: Int, what: String) {
        if (!data.isDirect) fail("$what: buffer must be direct")
        if (data.order() != ByteOrder.nativeOrder()) fail("$what: buffer must use the native byte order")
        if (data.position() != 0) fail("$what: buffer position is ${data.position()}, must be 0 (Android reads from the position)")
        if (data.limit() < bytes) fail("$what: needs $bytes bytes but the limit is ${data.limit()}")
    }

    // ---- shaders & programs
    override fun createShader(type: Int): Int {
        if (type != GL.VERTEX_SHADER && type != GL.FRAGMENT_SHADER) fail("bad shader type")
        return name().also { shaders.add(it) }
    }
    override fun shaderSource(shader: Int, source: String) {
        if (shader !in shaders) fail("shaderSource on unknown shader $shader")
        if (!source.startsWith("#version 300 es")) fail("GLSL ES 3.00 shaders must start with '#version 300 es'")
    }
    override fun compileShader(shader: Int) { if (shader !in shaders) fail("compile of unknown shader $shader") }
    override fun getShaderCompileStatus(shader: Int): Boolean = true
    override fun getShaderInfoLog(shader: Int) = ""
    override fun deleteShader(shader: Int) { if (!shaders.remove(shader)) fail("deleteShader($shader) of an unknown or deleted shader") }
    override fun createProgram(): Int = name().also { programs[it] = Program() }
    override fun attachShader(program: Int, shader: Int) {
        val p = programs[program] ?: fail("attachShader to unknown program $program")
        if (shader !in shaders) fail("attachShader of unknown shader $shader")
        p.attached.add(shader)
    }
    override fun linkProgram(program: Int) {
        val p = programs[program] ?: fail("link of unknown program $program")
        if (p.attached.size != 2) fail("program $program needs exactly a vertex and a fragment shader")
        p.linked = true
    }
    override fun getProgramLinkStatus(program: Int): Boolean = programs[program]?.linked ?: fail("unknown program")
    override fun getProgramInfoLog(program: Int) = ""
    override fun useProgram(program: Int) {
        if (program != 0 && programs[program]?.linked != true) fail("useProgram($program): not a linked program")
        current = program
    }
    override fun deleteProgram(program: Int) {
        if (programs.remove(program) == null) fail("deleteProgram($program) of an unknown or deleted program")
        if (current == program) current = 0
    }
    override fun getUniformLocation(program: Int, name: String): Int {
        val p = programs[program] ?: fail("getUniformLocation on unknown program $program")
        if (!p.linked) fail("getUniformLocation before linking")
        return p.uniforms.getOrPut(name) { nextLocation++.also { p.locations.add(it) } }
    }

    // ---- buffers & vertex arrays
    override fun genBuffer(): Int = name().also { buffers[it] = -1 }
    override fun deleteBuffer(buffer: Int) {
        if (buffers.remove(buffer) == null) fail("deleteBuffer($buffer) of an unknown or deleted buffer")
        if (arrayBuffer == buffer) arrayBuffer = 0
        for (v in vaos.values + defaultVao) {
            if (v.element == buffer) v.element = 0
            for (a in v.attribs) if (a.buffer == buffer) a.buffer = 0
        }
    }
    override fun bindBuffer(target: Int, buffer: Int) {
        if (buffer != 0 && buffer !in buffers) fail("bindBuffer of unknown buffer $buffer")
        when (target) {
            GL.ARRAY_BUFFER -> arrayBuffer = buffer
            GL.ELEMENT_ARRAY_BUFFER -> {
                if (boundVao == 0) fail("element array buffer bound while no VAO is bound (the binding belongs to the VAO)")
                vao().element = buffer
            }
            else -> fail("bindBuffer: unexpected target 0x${target.toString(16)}")
        }
    }
    private fun boundBuffer(target: Int): Int = when (target) {
        GL.ARRAY_BUFFER -> arrayBuffer
        GL.ELEMENT_ARRAY_BUFFER -> vao().element
        else -> fail("unexpected buffer target")
    }
    override fun bufferData(target: Int, sizeBytes: Int, data: ByteBuffer?, usage: Int) {
        val b = boundBuffer(target)
        if (b == 0) fail("bufferData with no buffer bound")
        if (sizeBytes <= 0) fail("bufferData of $sizeBytes bytes")
        if (data != null) checkUpload(data, sizeBytes, "bufferData")
        buffers[b] = sizeBytes
    }
    override fun bufferSubData(target: Int, offsetBytes: Int, sizeBytes: Int, data: ByteBuffer) {
        val b = boundBuffer(target)
        if (b == 0) fail("bufferSubData with no buffer bound")
        val storage = buffers[b] ?: -1
        if (offsetBytes < 0 || sizeBytes < 0 || offsetBytes + sizeBytes > storage) fail("bufferSubData($offsetBytes, $sizeBytes) outside the $storage bytes of buffer $b")
        checkUpload(data, sizeBytes, "bufferSubData")
    }
    override fun genVertexArray(): Int = name().also { vaos[it] = Vao() }
    override fun deleteVertexArray(vao: Int) {
        if (vaos.remove(vao) == null) fail("deleteVertexArray($vao) of an unknown or deleted VAO")
        if (boundVao == vao) boundVao = 0
    }
    override fun bindVertexArray(vao: Int) {
        if (vao != 0 && vao !in vaos) fail("bindVertexArray of unknown VAO $vao")
        boundVao = vao
    }
    override fun enableVertexAttribArray(index: Int) {
        if (boundVao == 0) fail("enableVertexAttribArray with no VAO bound")
        vao().attribs[index].enabled = true
    }
    override fun disableVertexAttribArray(index: Int) {
        if (boundVao == 0) fail("disableVertexAttribArray with no VAO bound")
        vao().attribs[index].enabled = false
    }
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, strideBytes: Int, offsetBytes: Int) {
        if (boundVao == 0) fail("vertexAttribPointer with no VAO bound")
        if (arrayBuffer == 0) fail("vertexAttribPointer with no ARRAY_BUFFER bound (client-side arrays are not allowed)")
        if (index !in 0..15) fail("attribute index $index")
        if (size !in 1..4) fail("attribute size $size")
        typeBytes(type)
        if (strideBytes < 0 || offsetBytes < 0) fail("negative stride/offset")
        val a = vao().attribs[index]
        a.buffer = arrayBuffer; a.size = size; a.type = type; a.stride = strideBytes; a.offset = offsetBytes
    }

    // ---- uniforms
    private fun uniformTarget(location: Int) {
        if (current == 0) fail("uniform set with no program in use")
        if (location != -1 && location !in programs.getValue(current).locations) fail("uniform location $location does not belong to the program in use ($current)")
    }
    override fun uniform1i(location: Int, v: Int) = uniformTarget(location)
    override fun uniform1f(location: Int, v: Float) { uniformTarget(location); finite(v) }
    override fun uniform2f(location: Int, x: Float, y: Float) { uniformTarget(location); finite(x, y) }
    override fun uniform3f(location: Int, x: Float, y: Float, z: Float) { uniformTarget(location); finite(x, y, z) }
    override fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) { uniformTarget(location); finite(x, y, z, w) }
    override fun uniformMatrix3fv(location: Int, m: FloatArray) {
        uniformTarget(location)
        if (m.size < 9) fail("mat3 uniform from ${m.size} floats")
        finite(*m.copyOf(9))
    }
    override fun uniformMatrix4fv(location: Int, m: FloatArray) {
        uniformTarget(location)
        if (m.size < 16) fail("mat4 uniform from ${m.size} floats")
        finite(*m.copyOf(16))
    }

    // ---- fixed-function state
    private val caps = setOf(GL.DEPTH_TEST, GL.BLEND, GL.CULL_FACE)
    override fun enable(cap: Int) { if (cap !in caps) fail("enable(0x${cap.toString(16)})") }
    override fun disable(cap: Int) { if (cap !in caps) fail("disable(0x${cap.toString(16)})") }
    override fun depthFunc(func: Int) { if (func != GL.LESS && func != GL.LEQUAL && func != GL.ALWAYS) fail("depthFunc") }
    override fun depthMask(flag: Boolean) = Unit
    override fun blendFunc(src: Int, dst: Int) = Unit
    override fun cullFace(mode: Int) { if (mode != GL.BACK && mode != GL.FRONT) fail("cullFace") }
    override fun viewport(x: Int, y: Int, w: Int, h: Int) { if (w < 0 || h < 0) fail("viewport ${w}x$h") }
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = finite(r, g, b, a)
    override fun clear(mask: Int) { if (mask and (GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT).inv() != 0) fail("clear mask") }

    // ---- textures
    override fun genTexture(): Int = name().also { textures.add(it) }
    override fun deleteTexture(texture: Int) {
        if (!textures.remove(texture)) fail("deleteTexture($texture) of an unknown or deleted texture")
        if (boundCube == texture) boundCube = 0
    }
    override fun activeTexture(unit: Int) { if (unit < GL.TEXTURE0 || unit > GL.TEXTURE0 + 15) fail("activeTexture") }
    override fun bindTexture(target: Int, texture: Int) {
        if (target != GL.TEXTURE_CUBE_MAP) fail("only cube map textures are used")
        if (texture != 0 && texture !in textures) fail("bindTexture of unknown texture $texture")
        boundCube = texture
    }
    override fun texParameteri(target: Int, pname: Int, value: Int) { if (boundCube == 0) fail("texParameteri with no texture bound") }
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, data: ByteBuffer?) {
        if (boundCube == 0) fail("texImage2D with no cube map bound")
        if (target !in GL.TEXTURE_CUBE_MAP_POSITIVE_X..GL.TEXTURE_CUBE_MAP_POSITIVE_X + 5) fail("texImage2D target")
        if (width <= 0 || height <= 0) fail("texImage2D size ${width}x$height")
        if (format != GL.RGBA || type != GL.UNSIGNED_BYTE) fail("texImage2D: only RGBA/UNSIGNED_BYTE is used")
        if (data != null) checkUpload(data, width * height * 4, "texImage2D")
    }
    override fun generateMipmap(target: Int) { if (boundCube == 0) fail("generateMipmap with no texture bound") }

    // ---- drawing
    private fun readsInside(first: Int, count: Int) {
        val v = vao()
        for ((i, a) in v.attribs.withIndex()) {
            if (!a.enabled) continue
            val size = buffers[a.buffer] ?: fail("attribute $i reads from a deleted or unset buffer")
            val elem = a.size * typeBytes(a.type)
            val stride = if (a.stride == 0) elem else a.stride
            val end = (first + count - 1).toLong() * stride + a.offset + elem
            if (end > size) fail("draw reads $end bytes of buffer ${a.buffer} through attribute $i but it only holds $size")
        }
    }
    override fun drawArrays(mode: Int, first: Int, count: Int) {
        if (current == 0) fail("draw with no program in use")
        if (first < 0 || count < 0) fail("drawArrays($first, $count)")
        if (mode == GL.TRIANGLES && count % 3 != 0) fail("drawArrays of $count vertices as triangles")
        if (count > 0) readsInside(first, count)
        drawCalls++; vertices += count
    }
    override fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int) {
        if (current == 0) fail("draw with no program in use")
        if (boundVao == 0) fail("drawElements with no VAO bound")
        val e = vao().element
        if (e == 0) fail("drawElements with no element array buffer in the VAO")
        val size = buffers[e] ?: -1
        if (count < 0 || offsetBytes < 0 || offsetBytes + count.toLong() * typeBytes(type) > size) fail("drawElements($count, offset $offsetBytes) reads outside the $size-byte index buffer")
        if (mode == GL.TRIANGLES && count % 3 != 0) fail("drawElements of $count indices as triangles")
        drawCalls++; vertices += count
    }
    override fun getError(): Int = GL.NO_ERROR
}
