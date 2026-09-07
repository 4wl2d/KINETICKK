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
    fun runIdentityAndRevisionAreStable() {
        val instance = GameplayInstanceId(RunId(42))

        assertEquals("kinetickk.local/GameplayRun/42", instance.canonicalValue)
        assertEquals(GameplayRevision.ZERO, GameplayRevision(0))
        assertFailsWith<IllegalArgumentException> { RunId(-1) }
        assertFailsWith<IllegalArgumentException> { GameplayRevision(-1) }
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
            GameplayBuildSummaryProjection(instance, revision, immutableListOf(1, 2)),
        )

        projections.forEach { projection ->
            assertEquals(instance, projection.instanceId)
            assertEquals(revision, projection.revision)
        }
        val weapon = assertIs<GameplayActiveWeaponProjection>(projections[1])
        assertEquals(WeaponId.FLUX_WAKE, weapon.weapon)
    }

    @Test
    fun configurationRejectionInventoryRemainsExact() {
        assertEquals(9, GameplayConfigurationRejection.entries.size)
    }
}
