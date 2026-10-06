package app.roadtoorbit.gl

import app.roadtoorbit.gfx.MeshData
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A compiled + linked program. Uniform locations are looked up once and cached by the caller. */
class ShaderProgram(private val gl: Gles, val name: String, vertexSource: String, fragmentSource: String) {
    val id: Int

    init {
        val vs = compile(GL.VERTEX_SHADER, vertexSource)
        val fs = compile(GL.FRAGMENT_SHADER, fragmentSource)
        id = gl.createProgram()
        gl.attachShader(id, vs)
        gl.attachShader(id, fs)
        gl.linkProgram(id)
        if (!gl.getProgramLinkStatus(id)) {
            val log = gl.getProgramInfoLog(id)
            gl.deleteProgram(id)
            throw IllegalStateException("Program '$name' failed to link: $log")
        }
        gl.deleteShader(vs)
        gl.deleteShader(fs)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = gl.createShader(type)
        gl.shaderSource(shader, source)
        gl.compileShader(shader)
        if (!gl.getShaderCompileStatus(shader)) {
            val log = gl.getShaderInfoLog(shader)
            gl.deleteShader(shader)
            val kind = if (type == GL.VERTEX_SHADER) "vertex" else "fragment"
            throw IllegalStateException("Shader '$name' ($kind) failed to compile: $log")
        }
        return shader
    }

    fun use() = gl.useProgram(id)

    fun uniform(name: String): Int = gl.getUniformLocation(id, name)

    fun release() = gl.deleteProgram(id)
}

/**
 * A vertex array + buffer holding interleaved [MeshData] vertices. Static meshes upload once;
 * dynamic meshes reserve [dynamicCapacity] vertices and can be re-filled with [update].
 */
class GpuMesh private constructor(
    private val gl: Gles,
    initial: MeshData?,
    private val dynamicCapacity: Int,
) {
    private val vao: Int = gl.genVertexArray()
    private val vbo: Int = gl.genBuffer()
    private var staging: ByteBuffer? = null

    var vertexCount: Int = 0
        private set

    init {
        gl.bindVertexArray(vao)
        gl.bindBuffer(GL.ARRAY_BUFFER, vbo)
        if (initial != null && dynamicCapacity == 0) {
            val buf = initial.toByteBuffer()
            gl.bufferData(GL.ARRAY_BUFFER, initial.byteSize, buf, GL.STATIC_DRAW)
            vertexCount = initial.count
        } else {
            gl.bufferData(GL.ARRAY_BUFFER, dynamicCapacity * MeshData.STRIDE, null, GL.DYNAMIC_DRAW)
            staging = ByteBuffer.allocateDirect(dynamicCapacity * MeshData.STRIDE).order(ByteOrder.nativeOrder())
            if (initial != null) update(initial)
        }
        gl.enableVertexAttribArray(0)
        gl.vertexAttribPointer(0, 3, GL.FLOAT, false, MeshData.STRIDE, 0)
        gl.enableVertexAttribArray(1)
        gl.vertexAttribPointer(1, 3, GL.FLOAT, false, MeshData.STRIDE, MeshData.OFFSET_NORMAL)
        gl.enableVertexAttribArray(2)
        gl.vertexAttribPointer(2, 4, GL.UNSIGNED_BYTE, true, MeshData.STRIDE, MeshData.OFFSET_COLOR)
        gl.bindVertexArray(0)
    }

    /** Replaces the contents of a dynamic mesh (must fit the reserved capacity). */
    fun update(data: MeshData) {
        val buf = checkNotNull(staging) { "update() called on a static mesh" }
        require(data.count <= dynamicCapacity) { "dynamic mesh overflow: ${data.count} > $dynamicCapacity" }
        data.writeTo(buf)
        gl.bindBuffer(GL.ARRAY_BUFFER, vbo)
        if (data.count > 0) gl.bufferSubData(GL.ARRAY_BUFFER, 0, data.byteSize, buf)
        vertexCount = data.count
    }

    fun draw() {
        if (vertexCount == 0) return
        gl.bindVertexArray(vao)
        gl.drawArrays(GL.TRIANGLES, 0, vertexCount)
    }

    fun release() {
        gl.deleteVertexArray(vao)
        gl.deleteBuffer(vbo)
    }

    companion object {
        fun static(gl: Gles, data: MeshData) = GpuMesh(gl, data, 0)
        fun dynamic(gl: Gles, capacityVertices: Int, initial: MeshData? = null) = GpuMesh(gl, initial, capacityVertices)
    }
}
