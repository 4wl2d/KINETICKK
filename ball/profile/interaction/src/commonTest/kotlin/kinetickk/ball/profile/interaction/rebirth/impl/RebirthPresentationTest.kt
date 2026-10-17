// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.BuildPolicy
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicValue
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.profile.interaction.rebirth.api.RebirthRenderModel
import kinetickk.ball.profile.interaction.rebirth.api.RebirthUnlock
import kinetickk.ball.profile.interaction.TestItems
import kinetickk.ball.profile.interaction.TestRelics
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.testRebirthPolicy
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.design.CanvasGlyphStyle
import kinetickk.foundation.design.CanvasRuneStyle
import kinetickk.foundation.design.Kk
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.KkRebirthTiers
import kinetickk.foundation.design.KkRolePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun buildRowsShowTheRebornOddsTheTargetUnlocksAndTheHeirlooms() {
        val content = RebirthBuildContent(TestRelics, TestItems, BuildPolicy())
        fun withBuild(level: Int, heirlooms: List<RelicId> = emptyList()) =
            model(level).copy(build = content.model(level, (level + 1).coerceAtMost(policy.maximumLevel), heirlooms))

        // Tier 1 to 2: Reborn odds, then both legendaries; two stored Heirlooms.
        val two = withBuild(1, listOf(RelicId.FURNACE_COVENANT, RelicId.PERPETUAL_VECTOR))
        assertEquals(3, two.buildRowCount())
        assertEquals(2, two.heirloomRowCount())
        val reborn = assertNotNull(rebirthRebornRow(two, AppLanguage.English))
        assertEquals("5%", reborn.current)
        assertEquals("10%", reborn.next)
        assertEquals(RebirthChange.UP, reborn.change)
        assertEquals("10%", assertNotNull(rebirthRebornRow(two, AppLanguage.Russian)).next)
        // The capped odds read as unchanged.
        assertEquals(RebirthChange.SAME, assertNotNull(rebirthRebornRow(withBuild(9), AppLanguage.English)).change)

        // No build content: no rows and no build description.
        val plain = model(1)
        assertEquals(0, plain.buildRowCount())
        assertEquals(0, plain.heirloomRowCount())
        assertNull(rebirthRebornRow(plain, AppLanguage.English))
        assertNull(rebirthBuildDescription(plain, AppLanguage.English))
        // How Reborn cards and Heirlooms work sits behind the build table's own (!); the Advance
        // (!) keeps what Rebirth resets, so neither tooltip runs past its line limit.
        for (language in AppLanguage.entries) {
            val buildInfo = language.text(ProfileScreensRedesignText.RebirthBuildInfo)
            assertEquals(buildInfo, rebirthBuildInfoText(language))
            for (state in RebirthActionState.entries) {
                val advanceInfo = rebirthInfoText(two, state, language)
                assertFalse(buildInfo in advanceInfo, "$language $state")
                assertTrue(language.text(ProfileScreensRedesignText.RebirthKeeps) in advanceInfo, "$language $state")
                assertEquals(rebirthInfoText(plain, state, language), advanceInfo)
            }
        }

        // The read-aloud summary names the odds, every unlock with what it does and every Heirloom.
        val description = assertNotNull(rebirthBuildDescription(two, AppLanguage.English))
        listOf("10%", "Test item 1", "Injected item 1", "Test item 3", "Test FURNACE_COVENANT", "Test PERPETUAL_VECTOR").forEach {
            assertTrue(it in description, "$it in $description")
        }
        assertFalse("Test AGONY_SCEPTER" in description, description)
    }

    @Test
    fun unlockRowsSayWhatTheContentDoes() {
        val scepter = RelicDefinition(
            id = RelicId.AGONY_SCEPTER, name = "Agony Scepter", aspect = RelicAspect.SOVEREIGN,
            keys = immutableListOf(RelicKey.ECHO), attunedTags = immutableListOf(),
            upside = "Your weapon takes its Forbidden form. Every rank deepens the mutation.",
            liability = "Weapon activations add heat.", minimumRebirth = 1,
        )
        // A Relic shows its rule rewrite (the first sentence, at rank 1), localized; an item its description.
        assertEquals("Your weapon takes its Forbidden form.", rebirthUnlockBlurb(RebirthUnlock.Relic(scepter), AppLanguage.English))
        assertEquals("Ваше оружие принимает Запретную форму.", rebirthUnlockBlurb(RebirthUnlock.Relic(scepter), AppLanguage.Russian))
        val crown = scepter.copy(id = RelicId.HOLLOW_CROWN, upside = "Each empty Relic slot grants {0} % damage.",
            values = immutableListOf(RelicValue(35f, 5f)))
        assertEquals("Each empty Relic slot grants 35 % damage.", rebirthUnlockBlurb(RebirthUnlock.Relic(crown), AppLanguage.English))
        assertEquals("Injected item 2", rebirthUnlockBlurb(RebirthUnlock.Item(TestItems[2]), AppLanguage.English))
    }

    @Test
    fun buildMarksUseGoldForRebirthAndTheElementHues() {
        val roles = KkRolePalette.Default
        val theme = RebirthTheme(4, roles)
        assertEquals(Kk.Gold, theme.gold)
        assertEquals(Kk.Gold, rebirthAspectColor(RelicAspect.SOVEREIGN, theme))
        assertEquals(Kk.EThermal, rebirthAspectColor(RelicAspect.THERMAL, theme))
        // Kinetic is the player's own volt, never the tier accent that re-tints `you` on this screen.
        assertEquals(roles.you, rebirthAspectColor(RelicAspect.KINETIC, theme))
        assertEquals(KkRolePalette.Protan.you, rebirthAspectColor(RelicAspect.KINETIC, RebirthTheme(4, KkRolePalette.Protan)))
        assertTrue(theme.roles.you != roles.you, "the tier accent re-tints you")
        // The color-vision palette drives the element hues; the event horizon stays grayscale.
        val mono = KkRolePalette.Mono
        assertEquals(mono.element(1), rebirthAspectColor(RelicAspect.CRYO, RebirthTheme(4, mono)))
        val horizon = RebirthTheme(10, roles)
        for (color in listOf(horizon.gold, rebirthAspectColor(RelicAspect.ION, horizon))) {
            assertEquals(color.red, color.green, 0.001f)
            assertEquals(color.green, color.blue, 0.001f)
        }
        // Every Relic has its own rune, the same as in the run (gameplay `RelicIconRenderer` and its
        // `InventoryIconParityTest` table), framed by its aspect's sides.
        assertEquals(RunRelicRunes, RelicId.entries.associateWith { it.toCanvasRuneStyle() })
        assertEquals(RelicId.entries.size, RelicId.entries.map { it.toCanvasRuneStyle() }.toSet().size)
        assertEquals(listOf(3, 6, 8, 4, 5, 7, 9, 12, 10), RelicAspect.entries.map { it.runeFrameSides() })
        // Item marks use the run's glyphs: the element over the first tag, a sunburst without either.
        val fork = TestItems[0].copy(tags = immutableListOf(), elements = immutableListOf())
        assertEquals(CanvasGlyphStyle.SUNBURST, fork.primaryGlyphStyle())
        assertEquals(CanvasGlyphStyle.SUNBURST, fork.secondaryGlyphStyle())
        val heart = TestItems[0].copy(tags = immutableListOf(BuildTag.RAM), elements = immutableListOf(StatusElement.CRYO))
        assertEquals(CanvasGlyphStyle.SNOWFLAKE, heart.primaryGlyphStyle())
        assertEquals(CanvasGlyphStyle.TRIPLE_ARROW, heart.secondaryGlyphStyle())
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

    private companion object {
        /** The run's relic runes, as gameplay's `InventoryIconParityTest` pins them. */
        val RunRelicRunes = mapOf(
            RelicId.PERPETUAL_VECTOR to CanvasRuneStyle.RADIAL_WHEEL,
            RelicId.BLINK_DRIVE to CanvasRuneStyle.OFFSET_CHEVRONS,
            RelicId.GYRE_ENGINE to CanvasRuneStyle.FOUR_SWIRL_ARMS,
            RelicId.GLASS_COMET to CanvasRuneStyle.CRACKED_RING,
            RelicId.EXECUTIONERS_PRISM to CanvasRuneStyle.SLASH_BLADE,
            RelicId.ECHO_CHAMBER to CanvasRuneStyle.NESTED_DIAMONDS,
            RelicId.FURNACE_COVENANT to CanvasRuneStyle.DIAMOND_FLAME,
            RelicId.CINDER_CROWN to CanvasRuneStyle.FOUR_DOT_CROWN,
            RelicId.OVERDRIVE_CASCADE to CanvasRuneStyle.COUNTER_ROTATING_ARCS,
            RelicId.ABSOLUTE_ZERO to CanvasRuneStyle.SIX_PETAL_ROSETTE,
            RelicId.STASIS_ANCHOR to CanvasRuneStyle.RINGED_ANCHOR,
            RelicId.STORMHEART_CAPACITOR to CanvasRuneStyle.BOLT_AND_DOT,
            RelicId.AEGIS_INVERSION to CanvasRuneStyle.CROSSED_DIAMOND,
            RelicId.NEUTRON_HEART to CanvasRuneStyle.OFFSET_RINGS,
            RelicId.EVENT_HORIZON to CanvasRuneStyle.INWARD_CHEVRONS,
            RelicId.VERDANT_ROT to CanvasRuneStyle.STITCHED_DIAGONAL,
            RelicId.PANDEMIC_CODEX to CanvasRuneStyle.FIVE_RAY_FAN,
            RelicId.AGONY_SCEPTER to CanvasRuneStyle.RADIATING_STAFF,
            RelicId.HOLLOW_CROWN to CanvasRuneStyle.OPEN_DIAL,
            RelicId.OUROBOROS_SEAL to CanvasRuneStyle.RETURN_ARROW,
            RelicId.THUNDER_CHOIR to CanvasRuneStyle.SPLIT_ARCS,
            RelicId.NULL_COVENANT to CanvasRuneStyle.OPEN_ELLIPSE_DOT,
        )
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
