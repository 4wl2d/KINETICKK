// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandRef
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandRef
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppSessionApiContractTest {
    @Test
    fun singletonIdentityAndRouteTokenKeepIdentitySeparateFromRevision() {
        assertEquals(
            "kinetickk.local/AppSession/local-session",
            LOCAL_APP_SESSION_INSTANCE_ID.canonicalValue,
        )
        assertEquals(SessionRevision.ZERO, SessionRevision(0L))
        assertEquals(SessionRouteToken(9L), SessionRouteToken.from(SessionRevision(9L)))
        assertFailsWith<IllegalArgumentException> { SessionRevision(-1L) }
        assertFailsWith<IllegalArgumentException> { SessionRouteToken(-1L) }
    }

    @Test
    fun configurationRetainsTheCapturedGameplayContentObject() {
        val content = minimalGameplayContent()

        val configuration = SessionConfiguration(content)

        assertSame(content, configuration.gameplayContent)
    }

    @Test
    fun shellProjectionRetainsSevenRoutesAndLastObservedGameplayPhase() {
        val destinations = listOf(
            AppDestination.Home,
            AppDestination.Gameplay,
            AppDestination.Settings,
            AppDestination.Lab,
            AppDestination.Armory,
            AppDestination.Rebirth,
            AppDestination.Codex,
        )
        assertEquals(7, destinations.size)
        assertEquals(2, destinations.count(AppDestination::isBaseDestination))
        assertEquals(5, destinations.count(AppDestination::isOverlayDestination))

        val runId = RunId(4L)
        val projection = AppShellProjection(
            instanceId = LOCAL_APP_SESSION_INSTANCE_ID,
            revision = SessionRevision(12L),
            base = AppDestination.Gameplay,
            overlay = AppDestination.Rebirth,
            activeRunId = runId,
            gameplayPhase = GameplayRunPhase.VICTORY,
            pendingWorkflow = null,
            resetLifecycle = SessionResetLifecycle.READY,
            rebirthConfirmationArmed = true,
            workflowFailure = null,
        )

        assertEquals(SessionRouteToken(12L), projection.routeToken)
        assertEquals(AppDestination.Rebirth, projection.active)
        assertEquals(
            immutableListOf(AppDestination.Gameplay, AppDestination.Rebirth),
            projection.entries,
        )
        assertEquals(GameplayRunPhase.VICTORY, projection.gameplayPhase)
        assertTrue(projection.rebirthEligible)
        assertTrue(projection.normalInputEnabled)

        assertFailsWith<IllegalArgumentException> {
            projection.copy(base = AppDestination.Settings)
        }
        assertFailsWith<IllegalArgumentException> {
            projection.copy(overlay = AppDestination.Home)
        }
        assertFailsWith<IllegalArgumentException> {
            projection.copy(activeRunId = null)
        }
        assertFailsWith<IllegalArgumentException> {
            homeProjection().copy(base = AppDestination.Gameplay)
        }
    }

    @Test
    fun resetAndPendingWorkflowBothBlockNormalInput() {
        val ready = homeProjection()

        assertTrue(ready.normalInputEnabled)
        assertFalse(
            ready.copy(
                resetLifecycle = SessionResetLifecycle.CONFIRMATION_REQUIRED,
            ).normalInputEnabled,
        )
        assertFalse(
            ready.copy(
                pendingWorkflow = SessionWorkflowPhase.STARTING_RUN,
            ).normalInputEnabled,
        )
        assertTrue(ready.rebirthEligible)
    }

    @Test
    fun interactionAndControlPulseInventoriesAreClosedAndTyped() {
        val interactions: List<SessionInteractionPulse> = listOf(
            SessionInteractionPulse.StartRunRequested,
            SessionInteractionPulse.RestartRunRequested,
            SessionInteractionPulse.ExitRunRequested,
            SessionInteractionPulse.OpenOverlay(AppDestination.Settings),
            SessionInteractionPulse.CloseOverlay,
            SessionInteractionPulse.ShortcutObserved(SessionShortcut.MUTE),
            SessionInteractionPulse.ToggleMuteRequested,
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            SessionInteractionPulse.RebirthRequested,
            SessionInteractionPulse.ResetCancelled,
            SessionInteractionPulse.ResetConfirmed,
            SessionInteractionPulse.ResetRetryRequested,
        )
        assertEquals(12, interactions.size)

        val profileRef = ProfileCommandRef(
            ProfileCommandSource.LocalSession,
            LOCAL_PROFILE_INSTANCE_ID,
            sourceRevision = 2L,
            ordinal = 0,
        )
        val gameplayInstance = GameplayInstanceId(RunId(3L))
        val gameplayRef = GameplayCommandRef(
            GameplayCommandSource.LocalSession,
            gameplayInstance,
            sourceRevision = 3L,
            ordinal = 0,
        )
        val controls: List<SessionControlPulse> = listOf(
            SessionControlPulse.ProfileCommandCompleted(
                ProfileCommandResult.Accepted(
                    profileRef,
                    ProfileRevision(8L),
                    ProfileCommandOutcome.CoreShapeSelected(CoreShape.PRISM),
                ),
            ),
            SessionControlPulse.ProfileCommandRejectedBeforeAcceptance(
                profileRef,
                ProfileAcceptance.Rejected(
                    LOCAL_PROFILE_INSTANCE_ID,
                    ProfileRevision(7L),
                    ProfileRejection.NoChange,
                ),
            ),
            SessionControlPulse.GameplayCommandCompleted(
                GameplayCommandResult.Accepted(
                    gameplayRef,
                    GameplayRevision(1L),
                    GameplayCommandOutcome.RunStarted,
                ),
            ),
            SessionControlPulse.GameplayCommandRejectedBeforeAcceptance(
                gameplayRef,
                GameplayAcceptance.Rejected(
                    gameplayInstance,
                    GameplayRevision.ZERO,
                    GameplayRejection.AlreadyStarted,
                ),
            ),
        )

        assertEquals(4, controls.size)
        assertIs<SessionControlPulse.ProfileCommandCompleted>(controls.first())
        assertIs<SessionControlPulse.GameplayCommandRejectedBeforeAcceptance>(controls.last())
    }

    @Test
    fun sessionOwnedProfileOutcomesExcludeGameplayProgress() {
        assertTrue(
            ProfileCommandOutcome.CoreShapeSelected(CoreShape.SHARD).isSessionOwnedOutcome(),
        )
        assertTrue(
            ProfileCommandOutcome.PreferencesChanged(PlayerPreferences()).isSessionOwnedOutcome(),
        )
        assertFalse(ProfileCommandOutcome.GameplayProgressApplied.isSessionOwnedOutcome())
    }
}

private fun homeProjection(): AppShellProjection = AppShellProjection(
    instanceId = LOCAL_APP_SESSION_INSTANCE_ID,
    revision = SessionRevision.ZERO,
    base = AppDestination.Home,
    overlay = null,
    activeRunId = null,
    gameplayPhase = null,
    pendingWorkflow = null,
    resetLifecycle = SessionResetLifecycle.READY,
    rebirthConfirmationArmed = false,
    workflowFailure = null,
)

private fun minimalGameplayContent(): GameplayContentSnapshot {
    val profile = RebirthProfile(
        tier = 0,
        directive = RebirthDirective.BASELINE,
        openingEnemyCount = 1,
        enemyCapMultiplier = 1f,
        spawnRateMultiplier = 1f,
        enemyHealthMultiplier = 1f,
        enemySpeedMultiplier = 1f,
        incomingDamageMultiplier = 1f,
        eliteRateMultiplier = 1f,
        threatTimeOffsetSeconds = 0f,
        playerPowerMultiplier = 1f,
        playerIntegrityBonus = 0f,
        matterGainMultiplier = 1f,
        bonusRerolls = 0,
        maximumActiveEnemies = 1,
        minimumSpawnIntervalSeconds = 1f,
        minimumEliteIntervalSeconds = 1f,
    )
    return GameplayContentSnapshot(
        version = ContentVersion("session-api-test"),
        items = immutableListOf(),
        weapons = immutableListOf(),
        weaponMasteries = immutableListOf(),
        metaUpgrades = immutableListOf(),
        relics = immutableListOf(),
        rebirth = RebirthPolicySnapshot(
            minimumLevel = 0,
            maximumLevel = 0,
            profiles = immutableListOf(profile),
            maxActiveEnemies = 1,
            minSpawnIntervalSeconds = 1f,
            minEliteIntervalSeconds = 1f,
        ),
        relicPolicy = RelicPolicy(maxSlots = 1, maxRank = 1),
    )
}
