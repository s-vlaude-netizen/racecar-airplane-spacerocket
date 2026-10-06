package app.roadtoorbit.gfx

/**
 * Everything the renderer needs to know about the "look of the world" for one frame: sky colours,
 * light, fog, the curved-world bend and the terrain parameters. Filled in by EnvironmentModel.
 */
class Environment {
    val zenith = floatArrayOf(0.15f, 0.40f, 0.85f)
    val horizon = floatArrayOf(0.75f, 0.85f, 0.95f)
    val groundCol = floatArrayOf(0.55f, 0.60f, 0.62f)
    val sunDir = floatArrayOf(0.4f, 0.7f, -0.6f)
    val sunColor = floatArrayOf(1.0f, 0.95f, 0.85f)
    val ambSky = floatArrayOf(0.45f, 0.5f, 0.6f)
    val ambGround = floatArrayOf(0.25f, 0.22f, 0.2f)
    val fogColor = floatArrayOf(0.75f, 0.85f, 0.95f)
    var fogDensity = 0.004f

    var starAmount = 0f
    var nebula = 0f
    var sunDisc = 0f
    var time = 0f

    // curved world
    var bendX = 0f
    var bendY = 0f
    var bendStart = 12f

    // terrain
    var terrainOn = true
    var groundY = 0f
    var terrainAmp = 90f
    var corridor = 9f
    var terrainStyle = 0f // 0 grass, 1 cloud deck, 2 moon
    val terrainTint = floatArrayOf(1f, 1f, 1f)

    fun setSun(x: Float, y: Float, z: Float) {
        val l = kotlin.math.sqrt(x * x + y * y + z * z)
        sunDir[0] = x / l; sunDir[1] = y / l; sunDir[2] = z / l
    }
}
