// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileModuleResultDelivery

/** Static Assembly transport for the two declared Profile result routes. */
internal class ProfileModuleResultRouter {
    private var sessionSink: ((ProfileModuleResultDelivery) -> Unit)? = null
    private var gameplaySink: ((ProfileModuleResultDelivery) -> Unit)? = null

    fun bind(
        sessionSink: (ProfileModuleResultDelivery) -> Unit,
        gameplaySink: (ProfileModuleResultDelivery) -> Unit,
    ) {
        check(this.sessionSink == null && this.gameplaySink == null) {
            "Profile command result routes may be bound only once"
        }
        this.sessionSink = sessionSink
        this.gameplaySink = gameplaySink
    }

    fun route(delivery: ProfileModuleResultDelivery) {
        when (delivery.commandSource.sourceInstance) {
            ProfileCommandSource.LocalSession -> checkNotNull(sessionSink) {
                "Session Profile result route is not bound"
            }(delivery)
            is ProfileCommandSource.GameplayRun -> checkNotNull(gameplaySink) {
                "Gameplay Profile result route is not bound"
            }(delivery)
        }
    }
}
