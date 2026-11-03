// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.ReductionSink
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** Owner-held outputs of one tick. The owner reuses one instance; values are valid until its next tick. */
class GameplayTickOutputs {
    var visualFxCues: ImmutableList<VisualFxCue> = immutableListOf(); internal set
    var audioCues: ImmutableList<GameplayAudioCue> = immutableListOf(); internal set
    var progressUpdate: GameplayProgressUpdate? = null; internal set
    /** An accepted FrameElapsed: the owner advances music exactly once by [frameRealDeltaSeconds]. */
    var frameAccepted: Boolean = false; internal set
    var frameRealDeltaSeconds: Float = 0f; internal set
    var ensuresAudioUnlocked: Boolean = false; internal set
    /** No render-visible fact changed: the owner restamps the previous snapshot instead of projecting. */
    var renderNeutral: Boolean = false; internal set

    /** The reduction's drain buffer, reused across ticks. */
    internal val sink: ReductionSink = ReductionSink()

    internal fun reset() {
        visualFxCues = immutableListOf()
        audioCues = immutableListOf()
        progressUpdate = null
        frameAccepted = false
        frameRealDeltaSeconds = 0f
        ensuresAudioUnlocked = false
        renderNeutral = false
    }
}

sealed interface GameplayTickResult {
    class Accepted(val nextState: GameplayState) : GameplayTickResult
    class Rejected(val reason: GameplayRejection) : GameplayTickResult
}
