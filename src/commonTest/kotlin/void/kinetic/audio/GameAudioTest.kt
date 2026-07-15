package void.kinetic.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class GameAudioTest {
    @Test
    fun urgentCuesSurviveNoisyMultiKillFrames() {
        val selected = selectSoundCues(
            listOf(
                SoundCue.ENEMY_DESTROYED,
                SoundCue.DASH,
                SoundCue.ENEMY_DESTROYED,
                SoundCue.PICKUP,
                SoundCue.HURT,
            ),
            limit = 3,
        )

        assertEquals(listOf(SoundCue.HURT, SoundCue.DASH, SoundCue.PICKUP), selected)
    }
}
