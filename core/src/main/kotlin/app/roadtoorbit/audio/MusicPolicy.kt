package app.roadtoorbit.audio

import app.roadtoorbit.game.Phase

/** Which loop belongs to which moment of the game. Pure, so it can be tested for every phase. */
object MusicPolicy {
    /** The loop that should be audible now, or null for silence. */
    fun wanted(phase: Phase, legIndex: Int, soundOn: Boolean, musicOn: Boolean): MusicSynth.Track? {
        if (!soundOn || !musicOn) return null
        return when (phase) {
            Phase.MENU -> MusicSynth.Track.MENU
            Phase.COUNTDOWN, Phase.RUN, Phase.CRASHING -> when (legIndex) {
                0 -> MusicSynth.Track.CAR
                1 -> MusicSynth.Track.PLANE
                else -> MusicSynth.Track.ROCKET
            }
            Phase.FINALE, Phase.VICTORY -> MusicSynth.Track.FINALE
            Phase.GAME_OVER -> null
        }
    }
}
