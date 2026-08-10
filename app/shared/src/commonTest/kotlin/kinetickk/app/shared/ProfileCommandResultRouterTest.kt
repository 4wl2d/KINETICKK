// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandRef
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileRevision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ProfileCommandResultRouterTest {
    @Test
    fun exactSourceInstanceSelectsOneOfTheTwoDeclaredRoutes() {
        val routedToSession = mutableListOf<ProfileCommandResult.Accepted>()
        val routedToGameplay = mutableListOf<ProfileCommandResult.Accepted>()
        val router = ProfileCommandResultRouter().apply {
            bind(routedToSession::add, routedToGameplay::add)
        }
        val sessionResult = result(ProfileCommandSource.LocalSession, ordinal = 0)
        val gameplayResult = result(ProfileCommandSource.GameplayRun(17L), ordinal = 1)

        router.route(sessionResult)
        router.route(gameplayResult)

        assertEquals(listOf(sessionResult), routedToSession)
        assertEquals(listOf(gameplayResult), routedToGameplay)
        assertSame(sessionResult, routedToSession.single())
        assertSame(gameplayResult, routedToGameplay.single())
    }

    @Test
    fun routesMustBeBoundBeforeTheFirstResult() {
        assertFailsWith<IllegalStateException> {
            ProfileCommandResultRouter().route(
                result(ProfileCommandSource.LocalSession, ordinal = 0),
            )
        }
    }

    @Test
    fun routesCanBeBoundOnlyOnce() {
        val router = ProfileCommandResultRouter()
        router.bind({}, {})

        assertFailsWith<IllegalStateException> { router.bind({}, {}) }
    }
}

private fun result(
    source: ProfileCommandSource,
    ordinal: Int,
): ProfileCommandResult.Accepted = ProfileCommandResult.Accepted(
    commandRef = ProfileCommandRef(
        sourceInstance = source,
        targetInstance = LOCAL_PROFILE_INSTANCE_ID,
        sourceRevision = 4L,
        ordinal = ordinal,
    ),
    targetRevision = ProfileRevision(9L),
    outcome = ProfileCommandOutcome.GameplayProgressApplied,
)
