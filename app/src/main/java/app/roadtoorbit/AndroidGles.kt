package app.roadtoorbit

import android.opengl.GLES30
import app.roadtoorbit.gl.Gles
import java.nio.ByteBuffer

/** [Gles] backed by the platform's OpenGL ES 3.0 bindings. Must only be used on the GL thread. */
class AndroidGles : Gles {
    private val tmp = IntArray(1)

    override fun createShader(type: Int): Int = GLES30.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GLES30.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GLES30.glCompileShader(shader)
    override fun getShaderCompileStatus(shader: Int): Boolean {
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, tmp, 0)
        return tmp[0] != 0
    }
    override fun getShaderInfoLog(shader: Int): String = GLES30.glGetShaderInfoLog(shader)
    override fun deleteShader(shader: Int) = GLES30.glDeleteShader(shader)
    override fun createProgram(): Int = GLES30.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GLES30.glAttachShader(program, shader)
    override fun linkProgram(program: Int) = GLES30.glLinkProgram(program)
    override fun getProgramLinkStatus(program: Int): Boolean {
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, tmp, 0)
        return tmp[0] != 0
    }
    override fun getProgramInfoLog(program: Int): String = GLES30.glGetProgramInfoLog(program)
    override fun useProgram(program: Int) = GLES30.glUseProgram(program)
    override fun deleteProgram(program: Int) = GLES30.glDeleteProgram(program)
    override fun getUniformLocation(program: Int, name: String): Int = GLES30.glGetUniformLocation(program, name)

    override fun genBuffer(): Int {
        GLES30.glGenBuffers(1, tmp, 0)
        return tmp[0]
    }
    override fun deleteBuffer(buffer: Int) {
        tmp[0] = buffer
        GLES30.glDeleteBuffers(1, tmp, 0)
    }
    override fun bindBuffer(target: Int, buffer: Int) = GLES30.glBindBuffer(target, buffer)
    override fun bufferData(target: Int, sizeBytes: Int, data: ByteBuffer?, usage: Int) =
        GLES30.glBufferData(target, sizeBytes, data, usage)
    override fun bufferSubData(target: Int, offsetBytes: Int, sizeBytes: Int, data: ByteBuffer) =
        GLES30.glBufferSubData(target, offsetBytes, sizeBytes, data)

    override fun genVertexArray(): Int {
        GLES30.glGenVertexArrays(1, tmp, 0)
        return tmp[0]
    }
    override fun deleteVertexArray(vao: Int) {
        tmp[0] = vao
        GLES30.glDeleteVertexArrays(1, tmp, 0)
    }
    override fun bindVertexArray(vao: Int) = GLES30.glBindVertexArray(vao)
    override fun enableVertexAttribArray(index: Int) = GLES30.glEnableVertexAttribArray(index)
    override fun disableVertexAttribArray(index: Int) = GLES30.glDisableVertexAttribArray(index)
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, strideBytes: Int, offsetBytes: Int) =
        GLES30.glVertexAttribPointer(index, size, type, normalized, strideBytes, offsetBytes)

    override fun uniform1i(location: Int, v: Int) = GLES30.glUniform1i(location, v)
    override fun uniform1f(location: Int, v: Float) = GLES30.glUniform1f(location, v)
    override fun uniform2f(location: Int, x: Float, y: Float) = GLES30.glUniform2f(location, x, y)
    override fun uniform3f(location: Int, x: Float, y: Float, z: Float) = GLES30.glUniform3f(location, x, y, z)
    override fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) = GLES30.glUniform4f(location, x, y, z, w)
    override fun uniformMatrix3fv(location: Int, m: FloatArray) = GLES30.glUniformMatrix3fv(location, 1, false, m, 0)
    override fun uniformMatrix4fv(location: Int, m: FloatArray) = GLES30.glUniformMatrix4fv(location, 1, false, m, 0)

    override fun enable(cap: Int) = GLES30.glEnable(cap)
    override fun disable(cap: Int) = GLES30.glDisable(cap)
    override fun depthFunc(func: Int) = GLES30.glDepthFunc(func)
    override fun depthMask(flag: Boolean) = GLES30.glDepthMask(flag)
    override fun blendFunc(src: Int, dst: Int) = GLES30.glBlendFunc(src, dst)
    override fun cullFace(mode: Int) = GLES30.glCullFace(mode)
    override fun viewport(x: Int, y: Int, w: Int, h: Int) = GLES30.glViewport(x, y, w, h)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = GLES30.glClearColor(r, g, b, a)
    override fun clear(mask: Int) = GLES30.glClear(mask)

    override fun drawArrays(mode: Int, first: Int, count: Int) = GLES30.glDrawArrays(mode, first, count)
    override fun getError(): Int = GLES30.glGetError()
}
