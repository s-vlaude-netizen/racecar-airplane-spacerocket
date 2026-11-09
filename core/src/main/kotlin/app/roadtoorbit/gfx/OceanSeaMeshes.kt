package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb

/** The models of the sea and the dive: sea stacks, ships, gulls, rival seaplanes, islands, the dive gate, start buoys, mines, jellyfish, sharks, pearls, whales, fish, kelp and coral. See [OceanSpec] for what each one has to be like. */
internal object OceanSeaMeshes {
    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF)) // not modelled yet
        }
    }
}
