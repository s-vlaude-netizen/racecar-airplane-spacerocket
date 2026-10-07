package app.roadtoorbit.audio

import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.World

/** Which loop belongs to which moment of the game. Pure, so it can be tested for every phase. */
object MusicPolicy {
    /** The loop that should be audible now, or null for silence. */
    fun wanted(phase: Phase, legIndex: Int, soundOn: Boolean, musicOn: Boolean, world: World = World.EARTH): MusicSynth.Track? {
        if (!soundOn || !musicOn) return null
        val mars = world == World.MARS
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
