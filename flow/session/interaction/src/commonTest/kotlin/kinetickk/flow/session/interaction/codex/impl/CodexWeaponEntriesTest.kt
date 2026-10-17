// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponScaling
import kinetickk.ball.gameplay.api.BuildTagSummary
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Codex weapon entries: always named with their typed facts, and their live synergy from the run or the Profile. */
class CodexWeaponEntriesTest {
    /** The shipped Event Lens: Trap, Gravitic, Anchor + Chain, key Gravity, scales with Mass, Dipole's, Reborn 3 for 1 100. */
    private val lens = WeaponDefinition(
        WeaponId.EVENT_LENS, "Event Lens", "Fires a lens that collapses into a gravity well.", WeaponFamily.TRAP, StatusElement.GRAVITIC,
        listOf(BuildTag.ANCHOR, BuildTag.CHAIN), RelicKey.GRAVITY, CoreShape.DIPOLE, WeaponScaling.MASS, WeaponCadence.DISCRETE,
        permanentUnlockCost = 1_100, minimumRebirth = 3,
        masteryForms = listOf("A wider well", "Two lenses fly at once", "The well implodes for 120 damage"),
        signatureText = "Attract doubles the pull; Repel turns the implosion into a throw.",
    )
    private val catalog = codexTestCatalog().let { base ->
        base.copy(weapons = base.weapons.map { if (it.id == lens.id) lens else it }.toImmutableList())
    }
    private val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items)

    private fun facts(entry: CodexEntry) = entry.facts.associate { it.label to it.value }

    @Test
    fun anUnownedWeaponIsNamedWithItsTypedFactsCostAndRebirthRank() {
        val entry = codexWeaponEntry(lens, model, codexTestProgress(), catalog)
        assertEquals("Event Lens", entry.title)
        // Not owned: the locked look and the Armory help, yet every fact is shown.
        assertFalse(entry.discovered)
        assertEquals("Locked", entry.status)
        assertEquals(
            mapOf(
                "Status" to "Locked", "Unlock cost" to "1100", "Unlocks at" to "Reborn 3", "Family" to "Trap", "Element" to "Gravitic",
                "Relic key" to "Gravity", "Scales with" to "Mass", "Cadence" to "Periodic", "Signature" to "Dipole",
            ),
            facts(entry),
        )
        val detail = assertIs<CodexDetail.Weapon>(entry.detail)
        assertTrue(detail.rebirthLocked)
        assertEquals(listOf(3, 6, 10), detail.formLevels)
        // The catalog lists every weapon and finds an unowned one by name.
        val found = codexCatalogEntries(1, "lens", CodexItemFilter.ALL, model, catalog, codexTestProgress())
        assertEquals(listOf("weapon/EVENT_LENS"), found.map { it.key })
        assertEquals(catalog.weapons.size, codexCatalogEntries(1, "", CodexItemFilter.ALL, model, catalog, codexTestProgress()).size)
    }

    @Test
    fun anOwnedWeaponDropsItsPriceAndAKineticOneSaysSo() {
        val owned = codexTestProgress().copy(loadout = PlayerLoadout(unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.EVENT_LENS)),
            rebirthProgress = RebirthProgress(level = 3, highestCleared = 3))
        val entry = codexWeaponEntry(lens, model, owned, catalog)
        assertTrue(entry.discovered)
        assertEquals("Unlocked", entry.status)
        assertFalse("Unlock cost" in facts(entry))
        assertFalse("Unlocks at" in facts(entry))
        assertFalse(assertIs<CodexDetail.Weapon>(entry.detail).rebirthLocked)
        val kinetic = catalog.weapons.first { it.element == null }
        assertEquals("Kinetic", facts(codexWeaponEntry(kinetic, model, owned, catalog))["Element"])
        assertEquals("Кинетика", codexWeaponElementLabel(kinetic, AppLanguage.Russian))
    }

    @Test
    fun outsideARunTheSynergyReadsTheNextCharacterAndTheHeirloomCandidates() {
        val progress = codexTestProgress().let { base ->
            base.copy(
                loadout = PlayerLoadout(coreShape = CoreShape.DIPOLE),
                collection = base.collection.copy(heirloomRelicIds = immutableListOf(RelicId.NEUTRON_HEART, RelicId.ECHO_CHAMBER, RelicId.EVENT_HORIZON)),
            )
        }
        val detail = assertIs<CodexDetail.Weapon>(codexWeaponEntry(lens, model, progress, catalog).detail)
        assertEquals("Dipole", detail.signature)
        assertTrue(detail.signatureActive)
        // Neutron Heart and Event Horizon carry Gravity; Echo Chamber does not.
        assertEquals(listOf(RelicId.NEUTRON_HEART, RelicId.EVENT_HORIZON), detail.heirlooms.map { it.id })
        assertTrue(detail.bound.isEmpty())
        assertNull(detail.tagPoints)
        assertFalse(assertIs<CodexDetail.Weapon>(codexWeaponEntry(lens, model, codexTestProgress(), catalog).detail).signatureActive)
    }

    @Test
    fun inARunTheSynergyReadsItsCharacterBoundRelicsAndTagPoints() {
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(), character = CoreShape.DIPOLE, weapon = WeaponId.EVENT_LENS,
            weaponLevel = 4, relics = immutableListOf(EquippedRelic(RelicId.EVENT_HORIZON, 1), EquippedRelic(RelicId.FURNACE_COVENANT, 2)),
            tags = immutableListOf(BuildTagSummary(BuildTag.ANCHOR, 3, 1), BuildTagSummary(BuildTag.CHAIN, 2, 0), BuildTagSummary(BuildTag.RAM, 5, 1)),
        )
        val running = CodexRenderModel(immutableSetOf(), CodexRunStacks(build = build), catalog.items)
        // The Profile's next character (the Circle) does not matter while the Dipole runs.
        val entry = codexWeaponEntry(lens, running, codexTestProgress(), catalog)
        assertTrue(entry.active)
        assertTrue(entry.discovered, "the run's weapon reads as owned")
        assertEquals("Lvl 4", facts(entry)["Level"])
        val detail = assertIs<CodexDetail.Weapon>(entry.detail)
        assertTrue(detail.signatureActive)
        assertEquals(listOf(RelicId.EVENT_HORIZON), detail.bound.map { it.id })
        assertEquals(5, detail.tagPoints, "Anchor 3 + Chain 2; Ram is not its tag")
        assertEquals(25, codexResonancePercent(5))
        assertEquals(50, codexResonancePercent(14))
        assertEquals(0, codexResonancePercent(-1))
        // The Build tab lists it too.
        assertTrue(codexBuildEntries(running, catalog, codexTestProgress()).any { it.key == "weapon/EVENT_LENS" && it.detail is CodexDetail.Weapon })
    }

    @Test
    fun russianFactsCarryNoLatinLetters() {
        val entry = codexWeaponEntry(lens, model, codexTestProgress(), catalog, AppLanguage.Russian)
        entry.facts.forEach { fact ->
            listOf(fact.label, fact.value).forEach { text ->
                assertFalse(text.any { it in 'a'..'z' || it in 'A'..'Z' }, "Latin letters in Russian ${fact.label}: $text")
            }
        }
        assertEquals("Ловушка", entry.facts.first { it.label == "Семейство" }.value)
        WeaponCadence.entries.forEach { cadence ->
            assertFalse(codexCadenceLabel(cadence, AppLanguage.Russian).any { it in 'a'..'z' || it in 'A'..'Z' }, "$cadence")
        }
    }
}
