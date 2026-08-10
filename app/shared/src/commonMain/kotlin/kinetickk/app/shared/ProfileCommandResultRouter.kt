// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource

/** Static Assembly transport for the two declared Profile result routes. */
internal class ProfileCommandResultRouter {
    private var sessionSink: ((ProfileCommandResult.Accepted) -> Unit)? = null
    private var gameplaySink: ((ProfileCommandResult.Accepted) -> Unit)? = null

    fun bind(
        sessionSink: (ProfileCommandResult.Accepted) -> Unit,
        gameplaySink: (ProfileCommandResult.Accepted) -> Unit,
    ) {
        check(this.sessionSink == null && this.gameplaySink == null) {
            "Profile command result routes may be bound only once"
        }
        this.sessionSink = sessionSink
        this.gameplaySink = gameplaySink
    }

    fun route(result: ProfileCommandResult.Accepted) {
        when (result.commandRef.sourceInstance) {
            ProfileCommandSource.LocalSession -> checkNotNull(sessionSink) {
                "Session Profile result route is not bound"
            }(result)
            is ProfileCommandSource.GameplayRun -> checkNotNull(gameplaySink) {
                "Gameplay Profile result route is not bound"
            }(result)
        }
    }
}
