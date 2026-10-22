// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.test.*

/** Codex facts show the game's data: real offer gates, no slot positions, rules only behind (!). */
class CodexEntryFactsTest {
    private val catalog = codexTestCatalog()

    @Test
    fun matterUnlockedLevelsFollowTheGameRuleUpToLevelEighty() {
        // The game offers an item from max(runLevel, min(80, 1 + lifetimeMatter / 40)).
        assertEquals(1, lifetimeMatterOfferLevel(0L))
        assertEquals(1, lifetimeMatterOfferLevel(39L))
        assertEquals(2, lifetimeMatterOfferLevel(40L))
        assertEquals(62, lifetimeMatterOfferLevel(2_440L))
        assertEquals(80, lifetimeMatterOfferLevel(3_160L))
        assertEquals(80, lifetimeMatterOfferLevel(Long.MAX_VALUE))
        assertEquals(1, lifetimeMatterOfferLevel(-5L))
    }

    @Test
    fun anItemUnlockedByLifetimeMatterShowsNoRunLevelGate() {
        val item = catalog.items[7].copy(unlockLevel = 62)
        val model = CodexRenderModel(immutableSetOf(item.id), CodexRunStacks(), immutableListOf(item))
        for (language in AppLanguage.entries) {
            fun offeredFrom(lifetimeMatter: Long) = codexItemEntry(item, model, lifetimeMatter, language)
                .facts.single { it.label == language.text(SessionText.OFFERS_FROM_LEVEL) }
            // 3,200 lifetime Matter unlocks catalog level 80: the item is offered from the run's start.
            assertEquals(language.text(SessionText.LEVEL_SHORT, 1), offeredFrom(3_200L).value)
            assertEquals(language.text(SessionText.LEVEL_SHORT, 1), offeredFrom(2_440L).value)
            // One Matter short of level 62, only the run level opens it.
            assertEquals(language.text(SessionText.LEVEL_SHORT, 62), offeredFrom(2_439L).value)
            assertEquals(language.text(SessionText.LEVEL_SHORT, 62), offeredFrom(0L).value)
            // The Codex path reads the profile's lifetime Matter.
            val progress = codexTestProgress(setOf(item.id)).copy(economy = PlayerEconomy(matter = 10L, lifetimeMatter = 3_200L))
            val entry = codexCatalogEntries(0, "", CodexItemFilter.ALL, model, catalog, progress, language).single()
            assertEquals(language.text(SessionText.LEVEL_SHORT, 1), entry.facts.single { !it.isStatus }.value)
            // The (!) explains the Matter rule the game applies: 40 Matter per level, up to level 80.
            val info = requireNotNull(entry.facts.single { !it.isStatus }.info)
            assertTrue("40" in info && language.text(SessionText.LEVEL_SHORT, 80) in info, info)
        }
    }

    @Test
    fun buildTabEmptyRelicSlotsCarryNoPositionNumber() {
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            character = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponLevel = 3,
            relics = immutableListOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 1)),
        )
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(build = build), catalog.items)
        for (language in AppLanguage.entries) {
            val empty = codexBuildEntries(model, catalog, codexTestProgress(), language).filter { it.icon == CodexIcon.Empty }
            assertEquals(catalog.relicPolicy.maxSlots - 1, empty.size)
            empty.forEach { entry ->
                assertEquals(language.text(SessionText.EMPTY_RELIC_SLOT), entry.title)
                assertFalse(entry.title.any(Char::isDigit), "${entry.key} title ${entry.title}")
                assertFalse(entry.summary.any(Char::isDigit), "${entry.key} announces ${entry.summary}")
            }
        }
    }

    /** The run's permanent upgrades: the played character's Refit and the weapon in hand's Honing, as levels. */
    @Test
    fun buildTabNamesTheRunsRefitAndHoningLevels() {
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            character = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponLevel = 3,
            upgrades = "Refit 4 · Honing 7", characterUpgradeLevel = 4, weaponUpgradeLevel = 7,
        )
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(build = build), catalog.items)
        for (language in AppLanguage.entries) {
            val entries = codexBuildEntries(model, catalog, codexTestProgress(), language)
            val character = entries.single { it.key == "shape/${CoreShape.ORB}" }
            assertEquals(language.text(SessionText.LEVEL_SHORT, 4), character.facts.single { it.label == language.text(CodexText.REFIT) }.value)
            val weapon = entries.single { it.icon is CodexIcon.Weapon }
            assertEquals(language.text(SessionText.LEVEL_SHORT, 7), weapon.facts.single { it.label == language.text(CodexText.HONING) }.value)
            // Facts never carry the summary's separator (no `·` on screen).
            (character.facts + weapon.facts).forEach { assertFalse('·' in it.value, "${it.label}: ${it.value}") }
        }
        // Outside a run the catalog shows no run upgrades.
        val idle = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items)
        val shape = codexShapeEntry(catalog.coreShapes.first { it.id == CoreShape.ORB }, idle, codexTestProgress())
        assertTrue(shape.facts.none { it.label == CodexText.REFIT.english })
    }

    @Test
    fun harmonyAndDissonanceFollowTheKeysWhileNamedPairingsStayHiddenUntilDiscovered() {
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items, discoveredRelicIds = RelicId.entries.toImmutableSet())
        for (language in AppLanguage.entries) {
            val entries = codexPairingEntries(model, catalog, language).associateBy { it.key }
            // Gyre Engine and Blink Drive share Motion: a harmony, shown with its effect.
            val harmony = entries.getValue(codexPairingKey(RelicId.BLINK_DRIVE, RelicId.GYRE_ENGINE))
            assertEquals(CodexLinkKind.HARMONY, (harmony.icon as CodexIcon.Pairing).kind)
            assertEquals(language.text(CodexText.HARMONY_EFFECT, 10), harmony.description)
            // Heat opposes Cold: Furnace Covenant and Stasis Anchor interfere.
            val dissonance = entries.getValue(codexPairingKey(RelicId.FURNACE_COVENANT, RelicId.STASIS_ANCHOR))
            assertEquals(language.text(CodexText.DISSONANCE_EFFECT, 15), dissonance.description)
            assertEquals(CodexPairingSection.DISSONANCE, dissonance.pairingSection())
            // A named pairing keeps its kind visible but its name and effect hidden.
            val gyre = entries.getValue(codexPairingKey(RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE))
            assertEquals(language.text(CodexText.UNKNOWN_PAIRING), gyre.title)
            assertEquals("", gyre.description)
            assertEquals(language.text(CodexText.CONCORD), gyre.kind)
            assertFalse(gyre.discovered)
            assertTrue(gyre.help != null && gyre.help.endsWith('.'))
            // Relics that neither share nor oppose a key form no row.
            assertNull(entries[codexPairingKey(RelicId.ECHO_CHAMBER, RelicId.EVENT_HORIZON)])
        }
        val known = model.copy(discoveredPairingIds = immutableSetOf(RelicPairingId.ENDLESS_GYRE))
        val gyre = codexPairingEntries(known, catalog).single { it.key == codexPairingKey(RelicId.GYRE_ENGINE, RelicId.PERPETUAL_VECTOR) }
        assertEquals("Endless Gyre", gyre.title)
        assertEquals(catalog.build.pairing(RelicPairingId.ENDLESS_GYRE).description, gyre.description)
        // Undiscovered relics never appear in a pairing.
        val partial = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items, discoveredRelicIds = immutableSetOf(RelicId.PERPETUAL_VECTOR))
        assertTrue(codexPairingEntries(partial, catalog).isEmpty())
    }

    @Test
    fun knownIncompatibilitiesListDiscoveredDiscordsSuppressionsAndDocumentedConflicts() {
        // The conflict follows the item's crit-chance conversion, whatever the item is called.
        val items = catalog.items.mapIndexed { index, item ->
            if (index == 7) item.copy(name = "Any Lens", rules = immutableListOf(ItemRule.Convert(ConversionKind.CRIT_CHANCE_TO_DAMAGE, 2.5f))) else item
        }.toImmutableList()
        val withReticle = catalog.copy(items = items)
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            relics = immutableListOf(EquippedRelic(RelicId.FURNACE_COVENANT, 1), EquippedRelic(RelicId.ABSOLUTE_ZERO, 1)),
            relicLinks = immutableListOf(BuildRelicLinkSummary(RelicId.FURNACE_COVENANT, RelicId.ABSOLUTE_ZERO, BuildRelicLinkKind.DISCORD,
                RelicPairingId.THERMAL_PARADOX, discovered = true)),
            suppressedItemIds = immutableListOf(3),
        )
        val model = CodexRenderModel(immutableSetOf(7), CodexRunStacks(build = build), items,
            discoveredRelicIds = immutableSetOf(RelicId.FURNACE_COVENANT, RelicId.ABSOLUTE_ZERO, RelicId.EXECUTIONERS_PRISM))
        val incompatible = codexPairingEntries(model, withReticle).filter { it.pairingSection() == CodexPairingSection.INCOMPATIBLE }.map { it.key }
        assertEquals(listOf(codexPairingKey(RelicId.FURNACE_COVENANT, RelicId.ABSOLUTE_ZERO), "conflict/item/3",
            "conflict/${RelicId.EXECUTIONERS_PRISM}/7"), incompatible)
        // The current run's link is enough to reveal the discord's name.
        assertEquals("Thermal Paradox", codexPairingEntries(model, withReticle).first { it.key == incompatible.first() }.title)
        // A documented conflict waits until both sides are discovered.
        val unknownReticle = model.copy(discoveredItemIds = immutableSetOf())
        assertFalse(codexPairingEntries(unknownReticle, withReticle).any { it.key.startsWith("conflict/${RelicId.EXECUTIONERS_PRISM}") })
        // A suppression says so once: its kind, without a status fact repeating it.
        val suppression = codexPairingEntries(model, withReticle).single { it.key == "conflict/item/3" }
        assertEquals(AppLanguage.English.text(CodexText.SUPPRESSED), suppression.kind)
        assertTrue(suppression.facts.none { it.value == suppression.kind })
    }

    @Test
    fun aDormantItemSaysItsEffectsWaitForASkillOrAnotherWeapon() {
        val item = catalog.items[3]
        fun build(dormant: List<Int>, suppressed: List<Int> = emptyList()) = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            suppressedItemIds = suppressed.toImmutableList(), dormantItemIds = dormant.toImmutableList(),
        )
        for (language in AppLanguage.entries) {
            fun status(build: GameplayBuildSummaryProjection) =
                codexItemEntry(item, CodexRenderModel(immutableSetOf(item.id), CodexRunStacks(build = build), catalog.items), 0L, language)
                    .facts.single { it.isStatus }
            val dormant = status(build(dormant = listOf(item.id)))
            assertEquals(language.text(CodexText.DORMANT), dormant.value)
            assertEquals(language.text(CodexText.DORMANT_HELP), dormant.info)
            // Relics disabling it say so first; an item that can act shows its offer state instead.
            assertEquals(language.text(CodexText.SUPPRESSED), status(build(dormant = listOf(item.id), suppressed = listOf(item.id))).value)
            assertNotEquals(language.text(CodexText.DORMANT), status(build(dormant = listOf(item.id + 1))).value)
        }
    }

    @Test
    fun anItemTheDeckLeftOutSaysItIsNotInThisRun() {
        val item = catalog.items[3]
        fun build(outOfDeck: List<Int>, dormant: List<Int> = emptyList(), eligible: List<Int> = emptyList()) = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            eligibleItemIds = eligible.toImmutableList(), dormantItemIds = dormant.toImmutableList(),
            outOfDeckItemIds = outOfDeck.toImmutableList(),
        )
        for (language in AppLanguage.entries) {
            fun status(build: GameplayBuildSummaryProjection) =
                codexItemEntry(item, CodexRenderModel(immutableSetOf(item.id), CodexRunStacks(build = build), catalog.items), 0L, language)
                    .facts.single { it.isStatus }
            val left = status(build(outOfDeck = listOf(item.id)))
            assertEquals(language.text(CodexText.NOT_IN_RUN), left.value)
            assertEquals(language.text(CodexText.NOT_IN_RUN_HELP), left.info)
            // A dormant item says so first; a dealt item shows its offer state instead.
            assertEquals(language.text(CodexText.DORMANT), status(build(outOfDeck = listOf(item.id), dormant = listOf(item.id))).value)
            assertEquals(language.text(SessionText.NEXT_ACQUISITION), status(build(outOfDeck = listOf(item.id + 1), eligible = listOf(item.id))).value)
        }
    }

    @Test
    fun spokenSummariesTellEveryPairingAndReactionApart() {
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items, discoveredRelicIds = RelicId.entries.toImmutableSet())
        for (language in AppLanguage.entries) {
            val pairings = codexPairingEntries(model, catalog, language)
            assertEquals(pairings.size, pairings.map { it.summary }.toSet().size, "two pairings read the same")
            // A generic link reads its keys and both relics, and never its kind twice.
            val harmony = pairings.single { it.key == codexPairingKey(RelicId.BLINK_DRIVE, RelicId.GYRE_ENGINE) }
            assertEquals(RelicKey.MOTION.displayLabel.localizedContent(language), harmony.title)
            assertTrue("Blink Drive".localizedContent(language) in harmony.summary && "Gyre Engine".localizedContent(language) in harmony.summary, harmony.summary)
            assertEquals(1, Regex(Regex.escape(language.text(CodexText.HARMONY))).findAll(harmony.summary).count(), harmony.summary)
            val reactions = codexReactionEntries(model, catalog, language)
            assertEquals(reactions.size, reactions.map { it.summary }.toSet().size, "two undiscovered reactions read the same")
        }
    }

    @Test
    fun buildTabRuleLinesUseTheRewardCardsAttunementAndDominantElement() {
        val item = catalog.items[0].copy(tags = immutableListOf(BuildTag.DRIVE))
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            relics = immutableListOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 1)),
            elements = immutableListOf(BuildElementSummary(StatusElement.ION, 2, 1, false), BuildElementSummary(StatusElement.THERMAL, 4, 2, true)),
        )
        val policy = catalog.build.policy
        // Perpetual Vector is attuned to Drive: the bound relic raises the item as on its reward card.
        val attuned = codexRuleContext(item, 1, reborn = true, catalog, build)
        assertEquals(policy.rebornMagnitude * (1f + policy.attunementBonus), attuned.bonus, 1e-5f)
        assertEquals(StatusElement.THERMAL, attuned.dominant)
        assertEquals(StatusElement.ION, attuned.secondary)
        val plain = codexRuleContext(item.copy(tags = immutableListOf(BuildTag.FORTUNE)), 1, reborn = false, catalog, build)
        assertEquals(1f, plain.bonus)
        // Out of a run the picks stay as their phrases.
        assertNull(codexRuleContext(item, 1, reborn = false, catalog).dominant)
    }

    @Test
    fun relicEntriesCarryEveryRankUpsideTheirCostKeysAndKnownPairings() {
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            relics = immutableListOf(EquippedRelic(RelicId.FURNACE_COVENANT, 2), EquippedRelic(RelicId.STASIS_ANCHOR, 1)),
        )
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(build = build), catalog.items,
            discoveredRelicIds = immutableSetOf(RelicId.FURNACE_COVENANT, RelicId.OVERDRIVE_CASCADE, RelicId.STASIS_ANCHOR))
        val furnace = codexRelicEntry(catalog.relic(RelicId.FURNACE_COVENANT), model, catalog)
        val detail = furnace.detail as CodexDetail.Relic
        assertEquals(listOf(20, 30, 40).map { "Full relic upside for Furnace Covenant grants $it % more." }, detail.upsides)
        assertEquals(2, detail.rank)
        assertEquals("Full relic cost for Furnace Covenant.", detail.liability)
        // Stasis Anchor's Cold opposes the furnace's Heat.
        assertEquals(setOf(RelicKey.HEAT), detail.opposedBound)
        // Meltdown with the discovered Overdrive Cascade is listed, nameless until discovered.
        val note = detail.pairings.single()
        assertEquals(RelicId.OVERDRIVE_CASCADE, note.partner.id)
        assertNull(note.name)
        assertEquals("2/3", furnace.facts.single { it.label == "Rank" }.value)
    }

    @Test
    fun rebirthGatedContentShowsItsTierButNeverItsIdentity() {
        val items = catalog.items.mapIndexed { index, item -> if (index == 5) item.copy(minimumRebirth = 2) else item }.toImmutableList()
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(), items)
        for (language in AppLanguage.entries) {
            val locked = codexCatalogEntries(0, "", CodexItemFilter.ALL, model, catalog.copy(items = items), codexTestProgress(emptySet()), language)
                .single { it.icon is CodexIcon.Locked }
            assertEquals("item/5", locked.key)
            assertEquals(language.text(SessionText.REBIRTH_LEVEL, 2), locked.title)
            assertEquals(CodexIcon.Locked(2), locked.icon)
            assertFalse(locked.discovered)
            assertFalse(locked.summary.contains("Item 5"))
            // Searching never reveals locked cells; reaching the tier turns it into an anonymous cell like any other unfound item.
            assertTrue(codexCatalogEntries(0, "item", CodexItemFilter.ALL, model, catalog.copy(items = items), codexTestProgress(emptySet()), language).isEmpty())
            val reached = codexTestProgress(emptySet()).copy(rebirthProgress = RebirthProgress(level = 2, highestCleared = 1))
            val open = codexCatalogEntries(0, "", CodexItemFilter.ALL, model, catalog.copy(items = items), reached, language)
            assertTrue(open.none { it.icon is CodexIcon.Locked })
            assertEquals(CodexIcon.Unknown, open.single { it.key == "item/5" }.icon)
            // The rank opens it, whatever tier is selected: dropping back to tier 0 keeps it open.
            val lowered = codexTestProgress(emptySet()).copy(rebirthProgress = RebirthProgress(level = 0, highestCleared = 1))
            assertTrue(codexCatalogEntries(0, "", CodexItemFilter.ALL, model, catalog.copy(items = items), lowered, language)
                .none { it.icon is CodexIcon.Locked })
        }
        // Sovereign relics gated by Rebirth do the same in the relic category.
        val relics = codexCatalogEntries(2, "", CodexItemFilter.ALL, model, catalog, codexTestProgress(emptySet()))
        assertEquals(listOf(RelicId.AGONY_SCEPTER, RelicId.HOLLOW_CROWN, RelicId.OUROBOROS_SEAL).map { "relic/$it" }, relics.map { it.key })
        assertTrue(relics.all { it.icon is CodexIcon.Locked && !it.discovered })
    }
}
