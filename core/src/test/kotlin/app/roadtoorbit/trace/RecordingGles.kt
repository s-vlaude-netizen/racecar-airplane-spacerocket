package app.roadtoorbit.trace

import app.roadtoorbit.gl.Gles
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opcodes of the binary GL command trace. Keep in sync with tools/render-check/replay.js. */
object Op {
    const val CREATE_SHADER = 1
    const val SHADER_SOURCE = 2
    const val COMPILE_SHADER = 3
    const val DELETE_SHADER = 4
    const val CREATE_PROGRAM = 5
    const val ATTACH_SHADER = 6
    const val LINK_PROGRAM = 7
    const val USE_PROGRAM = 8
    const val DELETE_PROGRAM = 9
    const val GET_UNIFORM_LOCATION = 10
    const val GEN_BUFFER = 11
    const val BIND_BUFFER = 12
    const val BUFFER_DATA = 13
    const val BUFFER_SUB_DATA = 14
    const val DELETE_BUFFER = 15
    const val GEN_VERTEX_ARRAY = 16
    const val BIND_VERTEX_ARRAY = 17
    const val DELETE_VERTEX_ARRAY = 18
    const val ENABLE_VERTEX_ATTRIB = 19
    const val DISABLE_VERTEX_ATTRIB = 20
    const val VERTEX_ATTRIB_POINTER = 21
    const val UNIFORM_1I = 22
    const val UNIFORM_1F = 23
    const val UNIFORM_2F = 24
    const val UNIFORM_3F = 25
    const val UNIFORM_4F = 26
    const val UNIFORM_MATRIX_3 = 27
    const val UNIFORM_MATRIX_4 = 28
    const val ENABLE = 30
    const val DISABLE = 31
    const val DEPTH_FUNC = 32
    const val DEPTH_MASK = 33
    const val BLEND_FUNC = 34
    const val CULL_FACE = 35
    const val VIEWPORT = 36
    const val CLEAR_COLOR = 37
    const val CLEAR = 38
    const val DRAW_ARRAYS = 40
    const val DRAW_ELEMENTS = 41
    const val GEN_TEXTURE = 50
    const val DELETE_TEXTURE = 51
    const val ACTIVE_TEXTURE = 52
    const val BIND_TEXTURE = 53
    const val TEX_PARAMETER = 54
    const val TEX_IMAGE_2D = 55
    const val GENERATE_MIPMAP = 56
    const val FRAME_END = 99
}

/** A [Gles] that does nothing but append every call to a little-endian binary trace. */
class RecordingGles(private val width: Int, private val height: Int) : Gles {
    private var buf: ByteBuffer = ByteBuffer.allocate(1 shl 20).order(ByteOrder.LITTLE_ENDIAN)
    private var next = 1
    private var frames = 0
    var drawCalls = 0
        private set
    var drawCallsThisFrame = 0
        private set
    var maxDrawCallsPerFrame = 0
        private set

    init {
        // header: magic, width, height
        ensure(16)
        buf.putInt(0x474C5452)
        buf.putInt(width)
        buf.putInt(height)
    }

    private fun ensure(extra: Int) {
        if (buf.remaining() >= extra) return
        var cap = buf.capacity() * 2
        while (cap - buf.position() < extra) cap *= 2
        val n = ByteBuffer.allocate(cap).order(ByteOrder.LITTLE_ENDIAN)
        buf.flip()
        n.put(buf)
        buf = n
    }

    private fun op(code: Int) {
        ensure(64)
        buf.put(code.toByte())
    }

    private fun i(v: Int) { ensure(8); buf.putInt(v) }
    private fun f(v: Float) { ensure(8); buf.putFloat(v) }
    private fun s(v: String) {
        val bytes = v.toByteArray(Charsets.UTF_8)
        ensure(bytes.size + 8)
        buf.putInt(bytes.size)
        buf.put(bytes)
    }

    private fun blob(data: ByteBuffer, size: Int) {
        ensure(size + 8)
        buf.putInt(size)
        val dup = data.duplicate()
        dup.position(0)
        val tmp = ByteArray(size)
        dup.get(tmp, 0, size)
        buf.put(tmp)
    }

    fun endFrame() {
        op(Op.FRAME_END)
        frames++
        maxDrawCallsPerFrame = maxOf(maxDrawCallsPerFrame, drawCallsThisFrame)
        drawCallsThisFrame = 0
    }

    val frameCount: Int get() = frames

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        val data = ByteArray(buf.position())
        val dup = buf.duplicate()
        dup.flip()
        dup.get(data)
        file.writeBytes(data)
    }

    override fun createShader(type: Int): Int { val id = next++; op(Op.CREATE_SHADER); i(id); i(type); return id }
    override fun shaderSource(shader: Int, source: String) { op(Op.SHADER_SOURCE); i(shader); s(source) }
    override fun compileShader(shader: Int) { op(Op.COMPILE_SHADER); i(shader) }
    override fun getShaderCompileStatus(shader: Int) = true // verified by the replayer
    override fun getShaderInfoLog(shader: Int) = ""
    override fun deleteShader(shader: Int) { op(Op.DELETE_SHADER); i(shader) }
    override fun createProgram(): Int { val id = next++; op(Op.CREATE_PROGRAM); i(id); return id }
    override fun attachShader(program: Int, shader: Int) { op(Op.ATTACH_SHADER); i(program); i(shader) }
    override fun linkProgram(program: Int) { op(Op.LINK_PROGRAM); i(program) }
    override fun getProgramLinkStatus(program: Int) = true
    override fun getProgramInfoLog(program: Int) = ""
    override fun useProgram(program: Int) { op(Op.USE_PROGRAM); i(program) }
    override fun deleteProgram(program: Int) { op(Op.DELETE_PROGRAM); i(program) }
    override fun getUniformLocation(program: Int, name: String): Int {
        val id = next++
        op(Op.GET_UNIFORM_LOCATION); i(program); s(name); i(id)
        return id
    }

    override fun genBuffer(): Int { val id = next++; op(Op.GEN_BUFFER); i(id); return id }
    override fun deleteBuffer(buffer: Int) { op(Op.DELETE_BUFFER); i(buffer) }
    override fun bindBuffer(target: Int, buffer: Int) { op(Op.BIND_BUFFER); i(target); i(buffer) }
    override fun bufferData(target: Int, sizeBytes: Int, data: ByteBuffer?, usage: Int) {
        op(Op.BUFFER_DATA); i(target); i(sizeBytes); i(usage)
        if (data != null) { i(1); blob(data, sizeBytes) } else i(0)
    }

    override fun bufferSubData(target: Int, offsetBytes: Int, sizeBytes: Int, data: ByteBuffer) {
        op(Op.BUFFER_SUB_DATA); i(target); i(offsetBytes); blob(data, sizeBytes)
    }

    override fun genVertexArray(): Int { val id = next++; op(Op.GEN_VERTEX_ARRAY); i(id); return id }
    override fun deleteVertexArray(vao: Int) { op(Op.DELETE_VERTEX_ARRAY); i(vao) }
    override fun bindVertexArray(vao: Int) { op(Op.BIND_VERTEX_ARRAY); i(vao) }
    override fun enableVertexAttribArray(index: Int) { op(Op.ENABLE_VERTEX_ATTRIB); i(index) }
    override fun disableVertexAttribArray(index: Int) { op(Op.DISABLE_VERTEX_ATTRIB); i(index) }
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, strideBytes: Int, offsetBytes: Int) {
        op(Op.VERTEX_ATTRIB_POINTER); i(index); i(size); i(type); i(if (normalized) 1 else 0); i(strideBytes); i(offsetBytes)
    }

    override fun uniform1i(location: Int, v: Int) { op(Op.UNIFORM_1I); i(location); i(v) }
    override fun uniform1f(location: Int, v: Float) { op(Op.UNIFORM_1F); i(location); f(v) }
    override fun uniform2f(location: Int, x: Float, y: Float) { op(Op.UNIFORM_2F); i(location); f(x); f(y) }
    override fun uniform3f(location: Int, x: Float, y: Float, z: Float) { op(Op.UNIFORM_3F); i(location); f(x); f(y); f(z) }
    override fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) { op(Op.UNIFORM_4F); i(location); f(x); f(y); f(z); f(w) }
    override fun uniformMatrix3fv(location: Int, m: FloatArray) { op(Op.UNIFORM_MATRIX_3); i(location); for (k in 0 until 9) f(m[k]) }
    override fun uniformMatrix4fv(location: Int, m: FloatArray) { op(Op.UNIFORM_MATRIX_4); i(location); for (k in 0 until 16) f(m[k]) }

    override fun enable(cap: Int) { op(Op.ENABLE); i(cap) }
    override fun disable(cap: Int) { op(Op.DISABLE); i(cap) }
    override fun depthFunc(func: Int) { op(Op.DEPTH_FUNC); i(func) }
    override fun depthMask(flag: Boolean) { op(Op.DEPTH_MASK); i(if (flag) 1 else 0) }
    override fun blendFunc(src: Int, dst: Int) { op(Op.BLEND_FUNC); i(src); i(dst) }
    override fun cullFace(mode: Int) { op(Op.CULL_FACE); i(mode) }
    override fun viewport(x: Int, y: Int, w: Int, h: Int) { op(Op.VIEWPORT); i(x); i(y); i(w); i(h) }
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) { op(Op.CLEAR_COLOR); f(r); f(g); f(b); f(a) }
    override fun clear(mask: Int) { op(Op.CLEAR); i(mask) }
    override fun genTexture(): Int { val id = next++; op(Op.GEN_TEXTURE); i(id); return id }
    override fun deleteTexture(texture: Int) { op(Op.DELETE_TEXTURE); i(texture) }
    override fun activeTexture(unit: Int) { op(Op.ACTIVE_TEXTURE); i(unit) }
    override fun bindTexture(target: Int, texture: Int) { op(Op.BIND_TEXTURE); i(target); i(texture) }
    override fun texParameteri(target: Int, pname: Int, value: Int) { op(Op.TEX_PARAMETER); i(target); i(pname); i(value) }
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, data: ByteBuffer?) {
        op(Op.TEX_IMAGE_2D); i(target); i(level); i(internalFormat); i(width); i(height); i(format); i(type)
        if (data != null) { i(1); blob(data, width * height * 4) } else i(0)
    }
    override fun generateMipmap(target: Int) { op(Op.GENERATE_MIPMAP); i(target) }

    override fun drawArrays(mode: Int, first: Int, count: Int) {
        op(Op.DRAW_ARRAYS); i(mode); i(first); i(count)
        drawCalls++; drawCallsThisFrame++
    }

    override fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int) {
        op(Op.DRAW_ELEMENTS); i(mode); i(count); i(type); i(offsetBytes)
        drawCalls++; drawCallsThisFrame++
    }

    override fun getError() = 0
}
