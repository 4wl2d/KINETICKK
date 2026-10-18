// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.interaction.fixtureWeapon
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureRelics
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
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
    fun relicLinksFollowTheKeyRuleInAnySlotsAndMatchTheOverlay() {
        val content = fixtureContent()
        fun links(vararg ids: RelicId) = hudRelicLinks(content, ids.map { EquippedRelic(it, 1) })
            .map { it.kind to it.slots }
        // Two relics sharing the Motion key harmonize, but the row marks no harmony (pause and the
        // relic cards show it as multipliers); a named pairing across a relic between them is marked.
        assertEquals(emptyList(), links(RelicId.GYRE_ENGINE, RelicId.ECHO_CHAMBER, RelicId.STORMHEART_CAPACITOR))
        assertEquals(listOf(RelicLinkKind.CONCORD to listOf(0, 2)),
            links(RelicId.PERPETUAL_VECTOR, RelicId.ECHO_CHAMBER, RelicId.GYRE_ENGINE))
        // Named pairings win over the key rule wherever they sit; opposed keys interfere.
        assertEquals(
            setOf(
                RelicLinkKind.DISCORD to listOf(0, 1),
                RelicLinkKind.CONCORD to listOf(0, 2),
                RelicLinkKind.DISSONANCE to listOf(1, 2),
            ),
            links(RelicId.PERPETUAL_VECTOR, RelicId.STASIS_ANCHOR, RelicId.GYRE_ENGINE).toSet(),
        )
        // The same relic twice, a lone relic and unrelated keys form nothing.
        assertEquals(emptyList(), links(RelicId.GYRE_ENGINE, RelicId.GYRE_ENGINE))
        assertEquals(emptyList(), links(RelicId.GYRE_ENGINE))
        assertEquals(emptyList(), links(RelicId.GYRE_ENGINE, RelicId.ECHO_CHAMBER))
        // What the relic row draws for these links (bracket vs link bar, stacked levels) is checked
        // on the drawn HUD in HudRelicRowTest.
    }

    @Test
    fun weaponIconsCoverEveryWeaponAndMasteriesCountFromTheContent() {
        WeaponId.entries.forEach { assertEquals("weapons." + it.name.lowercase(), weaponIcon(it).key, "icon for $it") }
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
        // Russian copy carries no Latin letters (units and words are Cyrillic).
        val latin = Regex("[A-Za-z]")
        HudRedesignText.entries.forEach { text ->
            assertFalse(latin.containsMatchIn(text.russian), "$text: Russian copy contains Latin letters")
            assertEquals(Regex("\\{\\d+}").findAll(text.english).map { it.value }.toList(),
                Regex("\\{\\d+}").findAll(text.russian).map { it.value }.toList(), "$text keeps its arguments")
        }
    }
}

private fun fixtureContent() = GameplayContentSnapshot(
    version = KINETICKK_CONTENT_VERSION,
    items = immutableListOf(
        ItemDefinition(
            0, "Ballast Core", "Lead in the heart of the Core turns every collision into a shove.",
            ItemRarity.COMMON, immutableListOf(BuildTag.RAM),
            stats = immutableListOf(StatDelta(ItemEffect.MASS, 0.12f), StatDelta(ItemEffect.IMPACT_DAMAGE, 0.06f)),
            maxStacks = 3, unlockLevel = 1,
        ),
    ),
    weapons = WeaponId.entries.map { fixtureWeapon(it, description = "Fixture.") }.toImmutableList(),
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    relics = rewardFixtureRelics.toImmutableList(),
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 0,
        profiles = immutableListOf(RebirthProfile(
            0, "Baseline", 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
            120, 0.09f, 24f,
        )),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 3),
)
