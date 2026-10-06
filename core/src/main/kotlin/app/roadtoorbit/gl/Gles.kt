package app.roadtoorbit.gl

import java.nio.ByteBuffer

/**
 * The tiny slice of OpenGL ES 3.0 the game needs. The renderer talks only to this interface so it
 * runs unchanged on Android (GLES30), in unit tests (no-op / recording implementations) and in the
 * headless render check that replays recorded command streams in WebGL2.
 *
 * Object names are plain Ints. Buffers passed in are read from position 0 up to [ByteBuffer.limit].
 */
interface Gles {
    // ---- shaders & programs
    fun createShader(type: Int): Int
    fun shaderSource(shader: Int, source: String)
    fun compileShader(shader: Int)
    fun getShaderCompileStatus(shader: Int): Boolean
    fun getShaderInfoLog(shader: Int): String
    fun deleteShader(shader: Int)
    fun createProgram(): Int
    fun attachShader(program: Int, shader: Int)
    fun linkProgram(program: Int)
    fun getProgramLinkStatus(program: Int): Boolean
    fun getProgramInfoLog(program: Int): String
    fun useProgram(program: Int)
    fun deleteProgram(program: Int)
    fun getUniformLocation(program: Int, name: String): Int

    // ---- buffers & vertex arrays
    fun genBuffer(): Int
    fun deleteBuffer(buffer: Int)
    fun bindBuffer(target: Int, buffer: Int)

    /** Allocates [sizeBytes] and uploads [data] (may be null to only allocate). */
    fun bufferData(target: Int, sizeBytes: Int, data: ByteBuffer?, usage: Int)
    fun bufferSubData(target: Int, offsetBytes: Int, sizeBytes: Int, data: ByteBuffer)
    fun genVertexArray(): Int
    fun deleteVertexArray(vao: Int)
    fun bindVertexArray(vao: Int)
    fun enableVertexAttribArray(index: Int)
    fun disableVertexAttribArray(index: Int)
    fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, strideBytes: Int, offsetBytes: Int)

    // ---- uniforms
    fun uniform1i(location: Int, v: Int)
    fun uniform1f(location: Int, v: Float)
    fun uniform2f(location: Int, x: Float, y: Float)
    fun uniform3f(location: Int, x: Float, y: Float, z: Float)
    fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float)
    fun uniformMatrix3fv(location: Int, m: FloatArray)
    fun uniformMatrix4fv(location: Int, m: FloatArray)

    // ---- fixed-function state
    fun enable(cap: Int)
    fun disable(cap: Int)
    fun depthFunc(func: Int)
    fun depthMask(flag: Boolean)
    fun blendFunc(src: Int, dst: Int)
    fun cullFace(mode: Int)
    fun viewport(x: Int, y: Int, w: Int, h: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear(mask: Int)

    // ---- textures (only a small baked cube map is used)
    fun genTexture(): Int
    fun deleteTexture(texture: Int)
    fun activeTexture(unit: Int)
    fun bindTexture(target: Int, texture: Int)
    fun texParameteri(target: Int, pname: Int, value: Int)
    fun texImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, data: ByteBuffer?)
    fun generateMipmap(target: Int)

    // ---- drawing
    fun drawArrays(mode: Int, first: Int, count: Int)
    fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int)
    fun getError(): Int
}

/** GL enum values (identical in OpenGL ES 3.0 and WebGL2). */
object GL {
    const val VERTEX_SHADER = 0x8B31
    const val FRAGMENT_SHADER = 0x8B30

    const val ARRAY_BUFFER = 0x8892
    const val ELEMENT_ARRAY_BUFFER = 0x8893
    const val STATIC_DRAW = 0x88E4
    const val DYNAMIC_DRAW = 0x88E8
    const val STREAM_DRAW = 0x88E0

    const val FLOAT = 0x1406
    const val UNSIGNED_BYTE = 0x1401
    const val UNSIGNED_SHORT = 0x1403

    const val TEXTURE0 = 0x84C0
    const val TEXTURE_CUBE_MAP = 0x8513
    const val TEXTURE_CUBE_MAP_POSITIVE_X = 0x8515
    const val TEXTURE_MIN_FILTER = 0x2801
    const val TEXTURE_MAG_FILTER = 0x2800
    const val TEXTURE_WRAP_S = 0x2802
    const val TEXTURE_WRAP_T = 0x2803
    const val TEXTURE_WRAP_R = 0x8072
    const val LINEAR = 0x2601
    const val LINEAR_MIPMAP_LINEAR = 0x2703
    const val CLAMP_TO_EDGE = 0x812F
    const val RGBA = 0x1908

    const val DEPTH_TEST = 0x0B71
    const val BLEND = 0x0BE2
    const val CULL_FACE = 0x0B44

    const val LESS = 0x0201
    const val LEQUAL = 0x0203
    const val ALWAYS = 0x0207

    const val ZERO = 0
    const val ONE = 1
    const val SRC_ALPHA = 0x0302
    const val ONE_MINUS_SRC_ALPHA = 0x0303

    const val BACK = 0x0405
    const val FRONT = 0x0404

    const val COLOR_BUFFER_BIT = 0x4000
    const val DEPTH_BUFFER_BIT = 0x0100

    const val POINTS = 0x0000
    const val LINES = 0x0001
    const val TRIANGLES = 0x0004

    const val NO_ERROR = 0
}

/** A [Gles] that ignores everything – handy for logic-only tests. Returns incrementing names. */
open class NullGles : Gles {
    private var next = 1
    private fun name() = next++
    override fun createShader(type: Int) = name()
    override fun shaderSource(shader: Int, source: String) {}
    override fun compileShader(shader: Int) {}
    override fun getShaderCompileStatus(shader: Int) = true
    override fun getShaderInfoLog(shader: Int) = ""
    override fun deleteShader(shader: Int) {}
    override fun createProgram() = name()
    override fun attachShader(program: Int, shader: Int) {}
    override fun linkProgram(program: Int) {}
    override fun getProgramLinkStatus(program: Int) = true
    override fun getProgramInfoLog(program: Int) = ""
    override fun useProgram(program: Int) {}
    override fun deleteProgram(program: Int) {}
    override fun getUniformLocation(program: Int, name: String) = name()
    override fun genBuffer() = name()
    override fun deleteBuffer(buffer: Int) {}
    override fun bindBuffer(target: Int, buffer: Int) {}
    override fun bufferData(target: Int, sizeBytes: Int, data: ByteBuffer?, usage: Int) {}
    override fun bufferSubData(target: Int, offsetBytes: Int, sizeBytes: Int, data: ByteBuffer) {}
    override fun genVertexArray() = name()
    override fun deleteVertexArray(vao: Int) {}
    override fun bindVertexArray(vao: Int) {}
    override fun enableVertexAttribArray(index: Int) {}
    override fun disableVertexAttribArray(index: Int) {}
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, strideBytes: Int, offsetBytes: Int) {}
    override fun uniform1i(location: Int, v: Int) {}
    override fun uniform1f(location: Int, v: Float) {}
    override fun uniform2f(location: Int, x: Float, y: Float) {}
    override fun uniform3f(location: Int, x: Float, y: Float, z: Float) {}
    override fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) {}
    override fun uniformMatrix3fv(location: Int, m: FloatArray) {}
    override fun uniformMatrix4fv(location: Int, m: FloatArray) {}
    override fun enable(cap: Int) {}
    override fun disable(cap: Int) {}
    override fun depthFunc(func: Int) {}
    override fun depthMask(flag: Boolean) {}
    override fun blendFunc(src: Int, dst: Int) {}
    override fun cullFace(mode: Int) {}
    override fun viewport(x: Int, y: Int, w: Int, h: Int) {}
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) {}
    override fun clear(mask: Int) {}
    override fun genTexture() = name()
    override fun deleteTexture(texture: Int) {}
    override fun activeTexture(unit: Int) {}
    override fun bindTexture(target: Int, texture: Int) {}
    override fun texParameteri(target: Int, pname: Int, value: Int) {}
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, data: ByteBuffer?) {}
    override fun generateMipmap(target: Int) {}
    override fun drawArrays(mode: Int, first: Int, count: Int) {}
    override fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int) {}
    override fun getError() = GL.NO_ERROR
}
