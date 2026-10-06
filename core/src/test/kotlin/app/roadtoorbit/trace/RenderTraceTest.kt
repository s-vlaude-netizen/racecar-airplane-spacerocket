package app.roadtoorbit.trace

import app.roadtoorbit.gfx.Camera
import app.roadtoorbit.gfx.Environment
import app.roadtoorbit.gfx.Material
import app.roadtoorbit.gfx.MeshId
import app.roadtoorbit.gfx.MeshLibrary
import app.roadtoorbit.gfx.Renderer
import app.roadtoorbit.gfx.VehicleMode
import app.roadtoorbit.gfx.VehicleRenderer
import app.roadtoorbit.gfx.VehicleVisual
import app.roadtoorbit.math.Mat4
import java.io.File
import org.junit.Test

/**
 * Not assertions – these tests emit GL command traces under build/traces that the headless render
 * check (tools/render-check) replays into PNGs so the visuals can be inspected without a device.
 */
class RenderTraceTest {
    private fun outFile(name: String) = File(System.getProperty("traceDir") ?: "build/traces", name)

    @Test fun vehicleShowroomFront() = showroom("showroom_front.bin", yaw = 155f, eye = floatArrayOf(7.5f, 3.4f, 9.5f))
    @Test fun vehicleShowroomSide() = showroom("showroom_side.bin", yaw = 90f, eye = floatArrayOf(0.5f, 2.0f, 12.5f))
    @Test fun vehicleShowroomRear() = showroom("showroom_rear.bin", yaw = 35f, eye = floatArrayOf(7.5f, 3.4f, 9.5f))

    private fun showroom(file: String, yaw: Float, eye: FloatArray) {
        val gl = RecordingGles(960, 540)
        val renderer = Renderer(gl, MeshLibrary())
        renderer.resize(960, 540)
        val vehicle = VehicleRenderer(gl, renderer)
        val env = Environment().apply {
            terrainOn = false
            zenith[0] = 0.18f; zenith[1] = 0.32f; zenith[2] = 0.62f
            horizon[0] = 0.78f; horizon[1] = 0.74f; horizon[2] = 0.78f
            fogColor[0] = 0.78f; fogColor[1] = 0.74f; fogColor[2] = 0.78f
            fogDensity = 0.002f
            setSun(0.55f, 0.75f, 0.45f)
        }
        val cam = Camera().apply {
            fovY = 38f; aspect = 960f / 540f
            ex = eye[0]; ey = eye[1]; ez = eye[2]
            tx = 0f; ty = 0.9f; tz = 0f
            update()
        }
        val visual = VehicleVisual()
        val vm = FloatArray(16)
        val pm = FloatArray(16)
        val mat = Material()

        data class Shot(val from: VehicleMode, val to: VehicleMode, val t: Float)
        val shots = listOf(
            Shot(VehicleMode.CAR, VehicleMode.CAR, 0f),
            Shot(VehicleMode.CAR, VehicleMode.PLANE, 0.25f),
            Shot(VehicleMode.CAR, VehicleMode.PLANE, 0.5f),
            Shot(VehicleMode.CAR, VehicleMode.PLANE, 0.75f),
            Shot(VehicleMode.PLANE, VehicleMode.PLANE, 0f),
            Shot(VehicleMode.PLANE, VehicleMode.ROCKET, 0.25f),
            Shot(VehicleMode.PLANE, VehicleMode.ROCKET, 0.5f),
            Shot(VehicleMode.PLANE, VehicleMode.ROCKET, 0.75f),
            Shot(VehicleMode.ROCKET, VehicleMode.ROCKET, 0f),
        )
        for ((n, s) in shots.withIndex()) {
            visual.from = s.from; visual.to = s.to; visual.morph = s.t
            visual.flame = 1f; visual.time = n * 0.1f
            renderer.beginFrame(env, cam)
            Mat4.setTrs(pm, 0f, -0.2f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
            mat.reset().gloss(0.3f, 30f)
            renderer.drawLit(renderer.meshes[MeshId.PLATFORM], pm, mat)
            mat.reset().tint(0.6f, 0.95f, 1f).also { it.emissive = 1f }
            renderer.drawLit(renderer.meshes[MeshId.PLATFORM_RING], pm, mat)
            Mat4.setTrs(vm, 0f, 0f, 0f, 0f, yaw, 0f, 1f, 1f, 1f)
            vehicle.draw(visual, vm)
            renderer.endFrame()
            gl.endFrame()
        }
        gl.writeTo(outFile(file))
    }
}
