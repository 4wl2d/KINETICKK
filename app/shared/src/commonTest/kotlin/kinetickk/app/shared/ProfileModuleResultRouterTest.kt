// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileResultSourceToken
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSemanticHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Exact transport-only routing for accepted Profile result deliveries. */
class ProfileModuleResultRouterTest {
    @Test
    fun exactSourceInstanceSelectsOneOfTheTwoDeclaredRoutes() {
        val routedToSession = mutableListOf<ProfileModuleResultDelivery>()
        val routedToGameplay = mutableListOf<ProfileModuleResultDelivery>()
        val router = ProfileModuleResultRouter().apply {
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
            ProfileModuleResultRouter().route(
                result(ProfileCommandSource.LocalSession, ordinal = 0),
            )
        }
    }

    @Test
    fun routesCanBeBoundOnlyOnce() {
        val router = ProfileModuleResultRouter()
        router.bind({}, {})

        assertFailsWith<IllegalStateException> { router.bind({}, {}) }
    }
}

private fun result(
    source: ProfileCommandSource,
    ordinal: Int,
): ProfileModuleResultDelivery {
    val handle = ProfileSemanticHandle(
        sourceInstance = source,
        sourceRevision = 4L,
        sourceOrdinal = ordinal,
    )
    val identity: ProfileEffectiveProtocolIdentity
    val payload: ProfileModuleResult
    when (source) {
        ProfileCommandSource.LocalSession -> {
            identity = ProfileEffectiveProtocolIdentity.SESSION_MUTE
            payload = ProfileModuleResult.PreferencesChanged(PlayerPreferences())
        }
        is ProfileCommandSource.GameplayRun -> {
            identity = ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS
            payload = ProfileModuleResult.GameplayProgressApplied
        }
    }
    return ProfileModuleResultDelivery(
        commandSource = ProfileCommandSourceToken(
            semanticHandle = handle,
            targetInstance = LOCAL_PROFILE_INSTANCE_ID,
            causalScope = 31L,
            causalDepth = 0,
        ),
        resultSource = ProfileResultSourceToken(
            semanticHandle = handle,
            targetInstance = LOCAL_PROFILE_INSTANCE_ID,
            targetRevision = ProfileRevision(9L),
            sourceOrdinal = 1,
            causalScope = 31L,
            causalDepth = 1,
        ),
        effectiveProtocolIdentity = identity,
        result = payload,
        issuerProvenance = ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING,
    )
}
