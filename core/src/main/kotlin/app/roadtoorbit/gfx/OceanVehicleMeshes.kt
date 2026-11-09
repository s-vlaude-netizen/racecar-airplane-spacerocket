package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb

/** The models of the extra parts of the vehicle's ocean skin (pontoons, propeller, conning tower, periscope, portholes, searchlight, light beam). See [OceanSpec] for what each one has to be like. */
internal object OceanVehicleMeshes {
    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF)) // not modelled yet
        }
    }
}
