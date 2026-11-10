package app.roadtoorbit.audio

import app.roadtoorbit.game.Finale
import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.World
import app.roadtoorbit.gfx.VehicleMode

/** Which loop belongs to which moment of the game. Pure, so it can be tested for every phase. */
object MusicPolicy {
    /** The loop that should be audible now, or null for silence. */
    fun wanted(
        phase: Phase, legIndex: Int, soundOn: Boolean, musicOn: Boolean, world: World = World.EARTH,
        /** The form of the current leg (the ocean levels go down and up: the loop follows the form, not the number of the leg). */
        legMode: VehicleMode? = null,
        finale: Finale? = null,
    ): MusicSynth.Track? {
        if (!soundOn || !musicOn) return null
        val mars = world == World.MARS
        if (world == World.OCEAN) {
            return when (phase) {
                Phase.MENU -> MusicSynth.Track.MENU
                Phase.COUNTDOWN, Phase.RUN, Phase.CRASHING -> when (legMode ?: when (legIndex) { 0 -> VehicleMode.PLANE; 1 -> VehicleMode.ROCKET; else -> VehicleMode.CAR }) {
                    VehicleMode.PLANE -> MusicSynth.Track.OCEAN_PLANE
                    VehicleMode.ROCKET -> MusicSynth.Track.OCEAN_SUB
                    VehicleMode.CAR -> MusicSynth.Track.OCEAN_CAR
                }
                // the lost city has its own finale; the welcome on the island airstrip is the triumphant one
                Phase.FINALE, Phase.VICTORY -> if (finale == Finale.AIRSTRIP) MusicSynth.Track.FINALE else MusicSynth.Track.OCEAN_FINALE
                Phase.GAME_OVER -> null
            }
        }
        return when (phase) {
            Phase.MENU -> MusicSynth.Track.MENU
            Phase.COUNTDOWN, Phase.RUN, Phase.CRASHING -> when (legIndex) {
                0 -> if (mars) MusicSynth.Track.MARS_CAR else MusicSynth.Track.CAR
                1 -> if (mars) MusicSynth.Track.MARS_PLANE else MusicSynth.Track.PLANE
                else -> if (mars) MusicSynth.Track.MARS_ROCKET else MusicSynth.Track.ROCKET
            }
            Phase.FINALE, Phase.VICTORY -> if (mars) MusicSynth.Track.MARS_FINALE else MusicSynth.Track.FINALE
            Phase.GAME_OVER -> null
        }
    }
}
