// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.ball.profile.interaction.testRebirthPolicy
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.KkRebirthTiers
import kinetickk.foundation.design.KkRolePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RebirthPresentationTest {
    private val policy = testRebirthPolicy()

    private fun model(level: Int, canAdvance: Boolean = true) = RebirthRenderModel(
        current = policy.profile(level),
        next = policy.profile(level + 1),
        canAdvance = canAdvance,
        minimumTier = policy.minimumLevel,
        maximumTier = policy.maximumLevel,
    )

    @Test
    fun ladderAndActionStatesFollowTheTierAndTheTwoPressConfirm() {
        val model = model(3)
        assertEquals(4, model.targetTier)
        assertEquals(RebirthTierCell.CLEARED, model.tierCell(2))
        assertEquals(RebirthTierCell.CURRENT, model.tierCell(3))
        assertEquals(RebirthTierCell.NEXT, model.tierCell(4))
        assertEquals(RebirthTierCell.LATER, model.tierCell(5))
        assertEquals(RebirthActionState.READY, model.actionState(confirmationArmed = false))
        assertEquals(RebirthActionState.ARMED, model.actionState(confirmationArmed = true))
        assertEquals(RebirthActionState.LOCKED, model(3, canAdvance = false).actionState(confirmationArmed = true))
        val maximum = model(10)
        assertTrue(maximum.isMaximumTier)
        assertEquals(10, maximum.targetTier)
        assertEquals(RebirthActionState.MAXIMUM, maximum.actionState(confirmationArmed = false))
        assertEquals(RebirthTierCell.CURRENT, maximum.tierCell(10))
    }

    @Test
    fun themeComesFromTheTierTokens() {
        val roles = KkRolePalette.Default
        val four = RebirthTheme(4, roles)
        assertEquals(KkRebirthTiers.color(4), four.accent)
        assertEquals(KkRebirthTiers.background(4), four.background)
        assertEquals(four.accent, four.roles.you)
        assertEquals(3, four.rings)
        assertFalse(four.band)
        assertTrue(RebirthTheme(5, roles).band)
        assertEquals(roles.threat, four.threat)

        val horizon = RebirthTheme(10, roles)
        assertTrue(horizon.eventHorizon)
        assertEquals(Color.Black, horizon.background)
        assertEquals(0, horizon.rings)
        assertFalse(horizon.band)
        // Grayscale: every color the event horizon shows has equal channels.
        for (color in listOf(horizon.accent, horizon.threat, horizon.tierColor(4))) {
            assertEquals(color.red, color.green, 0.001f)
            assertEquals(color.green, color.blue, 0.001f)
        }
        assertTrue(horizon.halftoneSpacing < four.halftoneSpacing)
    }

    @Test
    fun comparisonRowsFlagIncreasesAndFormatMultipliers() {
        val current = profile(tier = 3, speed = 1.08f, rerolls = 1)
        val next = profile(tier = 4, speed = 1.16f, rerolls = 1)
        val hostile = rebirthHostileRows(current, next, AppLanguage.English)
        assertEquals(7, hostile.size)
        val speed = hostile[4]
        assertEquals("×1.08", speed.current)
        assertEquals("×1.16", speed.next)
        assertEquals(RebirthChange.UP, speed.change)
        assertEquals(RebirthChange.SAME, hostile[0].change)
        val compensation = rebirthCompensationRows(current, next, AppLanguage.Russian)
        assertEquals(4, compensation.size)
        assertEquals(RebirthChange.SAME, compensation[3].change)
        assertEquals("×1,00", rebirthMultiplier(1f, AppLanguage.Russian))
        assertEquals("×1.05", rebirthMultiplier(1.05f, AppLanguage.English))
    }

    @Test
    fun advanceSequenceKeepsTheBoardTimings() {
        assertEquals(1600, REBIRTH_ADVANCE_MS)
        assertEquals(Offset.Zero, rebirthAdvanceShake(0.2f))
        assertEquals(Offset.Zero, rebirthAdvanceShake(0.7f))
        assertTrue(rebirthAdvanceShake(0.33f).x < 0f)
        assertEquals(0f, rebirthAdvanceDim(0.3f))
        assertEquals(1f, rebirthAdvanceDim(0.4f))
        assertFalse(rebirthAdvanceFlash(0.3f))
        assertTrue(rebirthAdvanceFlash(0.32f))
        assertFalse(rebirthAdvanceFlash(0.36f))
        assertEquals(0f, rebirthAdvanceNumeral(0.27f).third)
        assertEquals(2.6f, rebirthAdvanceNumeral(0.1f).first)
        assertEquals(Triple(1f, -3f, 1f), rebirthAdvanceNumeral(0.5f))
    }

    private fun profile(tier: Int, speed: Float, rerolls: Int) = RebirthProfile(
        tier = tier,
        directive = RebirthDirective.SWARM,
        openingEnemyCount = 14,
        enemyCapMultiplier = 1.1f,
        spawnRateMultiplier = 1.1f,
        enemyHealthMultiplier = 1.15f,
        enemySpeedMultiplier = speed,
        incomingDamageMultiplier = 1.1f,
        eliteRateMultiplier = 1f,
        threatTimeOffsetSeconds = tier * 10f,
        playerPowerMultiplier = 1f,
        playerIntegrityBonus = 12f,
        matterGainMultiplier = 1.15f,
        bonusRerolls = rerolls,
        maximumActiveEnemies = 120,
        minimumSpawnIntervalSeconds = 0.09f,
        minimumEliteIntervalSeconds = 24f,
    )
}
