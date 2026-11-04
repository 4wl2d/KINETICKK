// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BossEncounterSpecTest {
    private fun spec(
        phases: Int = 3,
        hitCapShare: Float = 0.03f,
        hitCapTierShrink: Float = 0.02f,
        hitCapMinimumScale: Float = 0.5f,
        shiftSeconds: Float = 2f,
        enragePhases: Int = 0,
        enrageMutator: RebirthMutator? = null,
        enrageAttackRate: Float = 1f,
        enrageAfterSeconds: Float = 0f,
    ) = BossEncounterSpec(
        phases, hitCapShare, hitCapTierShrink, hitCapMinimumScale, shiftSeconds, enragePhases, enrageMutator, enrageAttackRate,
        enrageAfterSeconds,
    )

    @Test
    fun theArchitectEncounterCarriesTheArchitectsGate() {
        assertEquals(3, ARCHITECT_ENCOUNTER.phases)
        assertEquals(0.015f, ARCHITECT_ENCOUNTER.hitCapShare)
        assertEquals(0.02f, ARCHITECT_ENCOUNTER.hitCapTierShrink)
        assertEquals(0.5f, ARCHITECT_ENCOUNTER.hitCapMinimumScale)
        assertEquals(8f, ARCHITECT_ENCOUNTER.shiftSeconds)
        assertEquals(1, ARCHITECT_ENCOUNTER.enragePhases)
        assertEquals(RebirthMutator.SINGULARITY, ARCHITECT_ENCOUNTER.enrageMutator)
        assertEquals(1.45f, ARCHITECT_ENCOUNTER.enrageAttackRate)
        assertEquals(0f, ARCHITECT_ENCOUNTER.enrageAfterSeconds)
        assertEquals(ARCHITECT_ENCOUNTER, defaultEnemyDefinitions()[EnemyType.ARCHITECT.ordinal].encounter)
    }

    @Test
    fun phasesAndEnragedPhasesFitTheTwoBitPhaseField() {
        spec(phases = 4)
        assertFailsWith<IllegalArgumentException> { spec(phases = 5) }
        assertFailsWith<IllegalArgumentException> { spec(phases = 0) }
        spec(phases = 3, enragePhases = 1, enrageMutator = RebirthMutator.SINGULARITY)
        assertFailsWith<IllegalArgumentException> { spec(phases = 3, enragePhases = 2, enrageMutator = RebirthMutator.SINGULARITY) }
        assertFailsWith<IllegalArgumentException> { spec(enragePhases = 1) }
        assertFailsWith<IllegalArgumentException> { spec(enrageMutator = RebirthMutator.SINGULARITY) }
        assertEquals(4, MAX_BOSS_PHASES)
    }

    @Test
    fun hitCapShiftAndEnrageAcceptTheirBoundsAndRejectTheNextValue() {
        spec(hitCapShare = 0.1f)
        assertFailsWith<IllegalArgumentException> { spec(hitCapShare = 0.1001f) }
        assertFailsWith<IllegalArgumentException> { spec(hitCapShare = 0f) }
        spec(hitCapTierShrink = 0.1f)
        spec(hitCapTierShrink = 0f)
        assertFailsWith<IllegalArgumentException> { spec(hitCapTierShrink = 0.1001f) }
        spec(hitCapMinimumScale = 1f)
        assertFailsWith<IllegalArgumentException> { spec(hitCapMinimumScale = 0f) }
        assertFailsWith<IllegalArgumentException> { spec(hitCapMinimumScale = 1.0001f) }
        spec(shiftSeconds = 12f)
        spec(shiftSeconds = 0f)
        assertFailsWith<IllegalArgumentException> { spec(shiftSeconds = 12.01f) }
        spec(enrageAttackRate = 2f)
        assertFailsWith<IllegalArgumentException> { spec(enrageAttackRate = 2.01f) }
        assertFailsWith<IllegalArgumentException> { spec(enrageAttackRate = 0.99f) }
        spec(enrageAfterSeconds = 150f)
        assertFailsWith<IllegalArgumentException> { spec(enrageAfterSeconds = -1f) }
        assertFailsWith<IllegalArgumentException> { spec(enrageAfterSeconds = Float.POSITIVE_INFINITY) }
    }
}
