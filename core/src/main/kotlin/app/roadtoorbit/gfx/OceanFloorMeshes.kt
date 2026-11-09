package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb

/** The models of the seabed and Atlantis: submersible traffic, containers, kelp tufts, crabs, the sunken highway, vents, wrecks, ruins, the lost city and its gate. See [OceanSpec] for what each one has to be like. */
internal object OceanFloorMeshes {
    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF)) // not modelled yet
        }
    }
}
