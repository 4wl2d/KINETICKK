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
    fun runIdentityIsStableAndIndependentFromRevision() {
        val runId = RunId(42)
        val instance = GameplayInstanceId(runId)

        assertEquals("kinetickk.local/GameplayRun/42", instance.canonicalValue)
        assertEquals(GameplayRevision.ZERO, GameplayRevision(0))
        assertEquals("kinetickk.local/AppSession/local-session", GameplayCommandSource.LocalSession.canonicalValue)
        assertFailsWith<IllegalArgumentException> { RunId(-1) }
        assertFailsWith<IllegalArgumentException> { GameplayRevision(-1) }
    }

    @Test
    fun commandCorrelationRejectsNegativeTupleMembersAtConstruction() {
        val instance = GameplayInstanceId(RunId(3))

        assertFailsWith<IllegalArgumentException> {
            GameplayCommandRef(GameplayCommandSource.LocalSession, instance, -1, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayCommandRef(GameplayCommandSource.LocalSession, instance, 0, -1)
        }
    }

    @Test
    fun closedProjectionInventoryCarriesIdentityAndRevision() {
        val instance = GameplayInstanceId(RunId(7))
        val revision = GameplayRevision(9)
        val projections: List<GameplayProjection> = listOf(
            GameplayRenderProjection(instance, revision, null),
            GameplayRunStatusProjection(instance, revision, GameplayRunPhase.CREATED, false),
            GameplayActiveWeaponProjection(instance, revision, WeaponId.FLUX_WAKE),
            GameplayCodexStacksProjection(instance, revision, immutableListOf(1, 2)),
        )

        projections.forEach { projection ->
            assertEquals(instance, projection.instanceId)
            assertEquals(revision, projection.revision)
        }
        assertIs<GameplayCodexStacksProjection>(projections.last())
    }

    @Test
    fun closedEnumInventoriesRemainExplicit() {
        assertEquals(7, GameplayRunPhase.entries.size)
        assertEquals(7, GameplayInputField.entries.size)
        assertEquals(3, GameplayInputReason.entries.size)
        assertEquals(3, GameplayCommandRefRejection.entries.size)
        assertEquals(4, GameplayProfileResultRejection.entries.size)
        assertEquals(9, GameplayConfigurationRejection.entries.size)
    }
}
