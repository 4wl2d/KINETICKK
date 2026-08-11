// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.api

import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GameplayApiContractTest {
    @Test
    fun runAndProtocolIdentitiesAreStable() {
        val instance = GameplayInstanceId(RunId(42))

        assertEquals("kinetickk.local/GameplayRun/42", instance.canonicalValue)
        assertEquals(GameplayRevision.ZERO, GameplayRevision(0))
        assertEquals(
            "kinetickk.local/AppSession/local-session",
            GameplayCommandSource.LocalSession.canonicalValue,
        )
        assertFailsWith<IllegalArgumentException> { RunId(-1) }
        assertFailsWith<IllegalArgumentException> { GameplayRevision(-1) }
    }

    @Test
    fun canonicalCommandAndResultTokensRejectInvalidScalarMembers() {
        val instance = GameplayInstanceId(RunId(3))
        val handle = GameplaySemanticHandle(GameplayCommandSource.LocalSession, 7, 2)
        val request = GameplayModuleCommandRequest(
            semanticHandle = handle,
            sourceOrdinal = 2,
            targetInstance = instance,
            command = GameplayModuleCommand.StartRun,
        )
        val source = GameplayCommandSourceToken(handle, instance, causalScope = 11, causalDepth = 1)
        val resultSource = GameplayResultSourceToken(
            semanticHandle = handle,
            targetInstance = instance,
            targetRevision = GameplayRevision(1),
            sourceOrdinal = 0,
            causalScope = 11,
            causalDepth = 2,
        )

        assertEquals(handle, request.semanticHandle)
        assertEquals(handle, source.semanticHandle)
        assertEquals(handle, resultSource.semanticHandle)
        assertFailsWith<IllegalArgumentException> {
            GameplaySemanticHandle(GameplayCommandSource.LocalSession, -1, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayModuleCommandRequest(handle, 3, instance, GameplayModuleCommand.StartRun)
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayCommandSourceToken(handle, instance, -1, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayResultSourceToken(
                handle,
                instance,
                GameplayRevision.ZERO,
                0,
                0,
                -1,
            )
        }
    }

    @Test
    fun fixedInteractionRepresentationsRequireValidatedFactories() {
        val frame = GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f)
        val viewport = GameplayInteractionPulse.ViewportChanged.fromValidated(1_280f, 720f, 2f)
        val pointer = GameplayInteractionPulse.PointerMoved.fromValidated(-1f, 721f)
        val choice = GameplayInteractionPulse.ChoiceSelected.fromValidated(3)

        assertEquals(0.1f, frame.realDeltaSeconds)
        assertEquals(1_280f, viewport.width)
        assertEquals(-1f, pointer.x)
        assertEquals(3, choice.index)
        listOf(Float.NaN, -0.001f, 1.001f).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                GameplayInteractionPulse.FrameElapsed.fromValidated(invalid)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayInteractionPulse.ViewportChanged.fromValidated(0f, 720f, 1f)
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayInteractionPulse.PointerMoved.fromValidated(Float.POSITIVE_INFINITY, 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayInteractionPulse.ChoiceSelected.fromValidated(4)
        }
    }

    @Test
    fun publicProjectionInventoryIsNarrowAndUsesExactOpaqueWeaponId() {
        val instance = GameplayInstanceId(RunId(7))
        val revision = GameplayRevision(9)
        val projections: List<GameplayProjection> = listOf(
            GameplayRunStatusProjection(instance, revision, GameplayRunPhase.CREATED, false),
            GameplayActiveWeaponProjection(instance, revision, WeaponId.FLUX_WAKE),
            GameplayCodexStacksProjection(instance, revision, immutableListOf(1, 2)),
        )

        projections.forEach { projection ->
            assertEquals(instance, projection.instanceId)
            assertEquals(revision, projection.revision)
        }
        val weapon = assertIs<GameplayActiveWeaponProjection>(projections[1])
        assertEquals(WeaponId.FLUX_WAKE, weapon.weapon)
    }

    @Test
    fun moduleCommandAndIdentityInventoriesRemainExact() {
        val commands: List<GameplayModuleCommand> = listOf(
            GameplayModuleCommand.StartRun,
            GameplayModuleCommand.PauseForOverlay,
            GameplayModuleCommand.ApplyPreferences,
            GameplayModuleCommand.ExitRun,
        )

        assertEquals(4, commands.size)
        assertEquals(4, GameplayEffectiveProtocolIdentity.entries.size)
        assertEquals(1, GameplayCommandIssuerProvenance.entries.size)
        assertEquals(1, GameplayResultIssuerProvenance.entries.size)
        assertEquals(9, GameplayConfigurationRejection.entries.size)
    }
}
