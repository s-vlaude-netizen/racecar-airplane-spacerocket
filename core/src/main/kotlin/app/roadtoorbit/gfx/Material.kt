package app.roadtoorbit.gfx

/** Per-draw look parameters for the lit shader (tint, specular, emissive, fog, rim light). */
class Material {
    var r = 1f; var g = 1f; var b = 1f; var a = 1f
    var spec = 0f
    var shine = 24f
    var emissive = 0f
    var fogScale = 1f
    var rimR = 0f; var rimG = 0f; var rimB = 0f; var rimPower = 0f

    fun reset(): Material {
        r = 1f; g = 1f; b = 1f; a = 1f
        spec = 0f; shine = 24f; emissive = 0f; fogScale = 1f
        rimR = 0f; rimG = 0f; rimB = 0f; rimPower = 0f
        return this
    }

    fun tint(nr: Float, ng: Float, nb: Float, na: Float = 1f): Material {
        r = nr; g = ng; b = nb; a = na
        return this
    }

    fun tintColor(c: Int): Material = tint(Col.r(c), Col.g(c), Col.b(c), Col.a(c))

    fun gloss(strength: Float, shininess: Float): Material {
        spec = strength; shine = shininess
        return this
    }

    fun rim(nr: Float, ng: Float, nb: Float, power: Float): Material {
        rimR = nr; rimG = ng; rimB = nb; rimPower = power
        return this
    }
}
