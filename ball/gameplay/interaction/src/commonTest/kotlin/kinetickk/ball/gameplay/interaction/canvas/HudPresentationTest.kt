// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.TargetDeviceProfiles
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HudPresentationTest {
    @Test
    fun dashPipsCountTheDashesTheDomainWouldAcceptInARow() {
        listOf(12f, 20f, 27f, 36f, 50f, 100f).forEach { cost ->
            var heat = 0f
            while (heat <= GameplayRenderModel.MAX_HEAT) {
                // The render model's own rule: a dash is accepted while heat <= MAX - cost / 2.
                var chained = 0
                var simulated = heat
                while (simulated <= GameplayRenderModel.MAX_HEAT - cost * 0.5f && simulated < GameplayRenderModel.MAX_HEAT) {
                    chained++
                    simulated += cost
                }
                assertEquals(chained.coerceAtMost(dashChargeCapacity(cost)), dashChargesReady(heat, cost, overheated = false),
                    "cost $cost heat $heat")
                heat += 0.5f
            }
            assertEquals(0, dashChargesReady(0f, cost, overheated = true))
        }
        assertEquals(3, dashChargeCapacity(36f))
        assertEquals(8, dashChargeCapacity(12f))
    }

    @Test
    fun integrityUsesTwentyPointCellsAndTurnsCriticalAtOneFifth() {
        assertEquals(9, integritySegments(180f))
        assertEquals(5, integritySegments(100f))
        assertEquals(6, integritySegments(101f))
        assertEquals(40, integritySegments(10_000f))
        assertTrue(isIntegrityCritical(36f, 180f))
        assertFalse(isIntegrityCritical(36.01f, 180f))
        assertEquals(2, shieldCells(40f))
        assertEquals(0, filledShieldCells(0f, 60f, 3))
        assertEquals(1, filledShieldCells(1f, 60f, 3))
        assertEquals(3, filledShieldCells(60f, 60f, 3))
    }

    @Test
    fun velocityLadderAndStretchGrowMonotonicallyAndStayBounded() {
        var previous = 0
        var speed = 0f
        while (speed <= 6_000f) {
            val lit = velocityLadderLit(speed, 30)
            assertTrue(lit in previous..30)
            previous = lit
            speed += 50f
        }
        assertEquals(0, velocityLadderLit(0f, 30))
        assertEquals(1f, speedStretch(0))
        assertEquals(1.34f, speedStretch(4), 0.0001f)
        assertEquals(1.34f, speedStretch(9), 0.0001f)
    }

    @Test
    fun adjacentRelicsLinkOnlyWhenTheyFormASynergy() {
        val content = fixtureContent()
        fun linked(first: RelicId, second: RelicId) =
            relicsLinked(content, listOf(EquippedRelic(first, 1), EquippedRelic(second, 1)), 0)
        // Two different relics of one aspect form its aspect synergy.
        assertTrue(linked(RelicId.KINETIC_FLYWHEEL, RelicId.GHOST_VECTOR))
        // A named pair links across aspects; unrelated relics and the same relic do not.
        assertTrue(linked(RelicId.BRAKEPOINT_MEMORY, RelicId.MASS_ECHO))
        assertFalse(linked(RelicId.KINETIC_FLYWHEEL, RelicId.ORBITAL_NAIL))
        assertFalse(linked(RelicId.KINETIC_FLYWHEEL, RelicId.KINETIC_FLYWHEEL))
        val sovereign = RelicId.entries.filter { content.relic(it).aspect == RelicAspect.SOVEREIGN }
        if (sovereign.size >= 2) assertFalse(linked(sovereign[0], sovereign[1]))
        assertFalse(relicsLinked(content, listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, 1)), 0))
    }

    @Test
    fun weaponIconsCoverEveryWeaponAndMasteriesCountFromTheContent() {
        WeaponId.entries.forEach { assertTrue(weaponIcon(it) != null, "missing icon for $it") }
        val content = fixtureContent()
        assertEquals(1, weaponMasteriesReached(content, 1))
        assertEquals(2, weaponMasteriesReached(content, 3))
        assertEquals(WeaponMastery.entries.size, weaponMasteriesReached(content, 99))
    }

    @Test
    fun trialPanelAndItsInfoTargetStayInsideEveryLayout() {
        val viewports = listOf(Triple(1_440f, 810f, 1f), Triple(1_000f, 720f, 1f), Triple(844f, 390f, 1f), Triple(390f, 844f, 1f)) +
            TargetDeviceProfiles.flatMap { listOf(Triple(it.widthPx, it.heightPx, it.density), Triple(it.heightPx, it.widthPx, it.density)) }
        viewports.forEach { (width, height, density) ->
            listOf(1f, 1.75f).forEach { textScale ->
                val layout = HudTrialPanelLayout().update(width, height, density, textScale)
                val info = layout.infoTarget(density)
                assertTrue(layout.left >= 0f && layout.right <= width && layout.bottom < height * 0.6f, "panel outside at $width x $height")
                assertTrue(info.width >= 48f * density - 0.01f && info.height >= 48f * density - 0.01f)
                assertTrue(layout.infoLeft + layout.infoSize <= layout.right && layout.infoLeft >= layout.left)
                assertTrue(layout.infoTop >= layout.top && layout.infoTop + layout.infoSize <= layout.bottom)
                // The panel never sits on a running control.
                runningControlBounds(width, height, density).forEach { control ->
                    val bounds = control.bounds
                    assertFalse(bounds.left < layout.right && bounds.right > layout.left && bounds.top < layout.bottom && bounds.bottom > layout.top,
                        "${control.target} overlaps the trial panel at $width x $height")
                }
            }
        }
        assertEquals(GameplayLayoutMode.REGULAR, gameplayLayoutMode(1_440f, 810f, 1f))
    }

    @Test
    fun hudTextsFollowTheCopyRules() {
        val banned = listOf("·", "→", "←", "↑", "↓", "›", "‹", "▶", "◀", "◇", "§")
        HudRedesignText.entries.forEach { text ->
            listOf(text.english, text.russian).forEach { value ->
                assertTrue(value.isNotBlank())
                assertTrue(banned.none { it in value }, "$text contains a banned glyph")
                assertFalse(value.endsWith(".") && !value.endsWith("Ур."), "$text ends with punctuation")
            }
        }
        assertEquals("Lvl {0}", HudRedesignText.WeaponLevel.english)
        assertEquals("Ур. {0}", HudRedesignText.WeaponLevel.russian)
    }
}

private fun fixtureContent() = GameplayContentSnapshot(
    version = KINETICKK_CONTENT_VERSION,
    items = immutableListOf(
        ItemDefinition(
            0, "Cinder Ram", "Cinder Ram binds the Impact family to a Cinder component: +5% Impact damage and +4% Weapon power per stack (max 8).",
            ItemRarity.COMMON, ItemModifier(ItemEffect.IMPACT_DAMAGE, 0.05f), ItemModifier(ItemEffect.WEAPON_POWER, 0.04f),
            maxStacks = 8, unlockLevel = 1, family = "Impact",
        ),
    ),
    weapons = WeaponId.entries.map { WeaponDefinition(it, it.name, "Fixture.", listOf("TRAIL"), 0) }.toImmutableList(),
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    metaUpgrades = MetaUpgradeId.entries.map { id ->
        MetaUpgradeDefinition(id, "Fixture", "Fixture", 1, 1, ItemModifier(ItemEffect.MAX_INTEGRITY, 1f))
    }.toImmutableList(),
    relics = RelicId.entries.map { id ->
        RelicDefinition(id, id.name, RelicAspect.entries[(id.ordinal / 6).coerceAtMost(RelicAspect.entries.size - 1)], "Fixture", "Fixture")
    }.toImmutableList(),
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 0,
        profiles = immutableListOf(RebirthProfile(
            0, RebirthDirective.BASELINE, 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
            120, 0.09f, 24f,
        )),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 5),
)
