// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CharacterTraits
import kinetickk.ball.content.api.CharacterTriggerKind
import kinetickk.ball.content.api.CharacterUnlockRequirement
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.ruleLines
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.breakThresholdCut
import kinetickk.ball.content.api.isLiability
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContentCatalogTest {
    private val catalog = createContentCatalog()
    private val gameplay = catalog.gameplayContent()
    private val profile = catalog.profilePolicy()
    private val ui = catalog.uiCatalog()

    @Test
    fun authorityPublishesOneVersionedSetOfCachedQuerySnapshots() {
        assertEquals("kinetickk-content-3", catalog.version.value)
        assertEquals(KINETICKK_CONTENT_VERSION, catalog.version)
        assertEquals(catalog.version, gameplay.version)
        assertEquals(catalog.version, profile.version)
        assertEquals(catalog.version, ui.version)

        assertSame(gameplay, catalog.gameplayContent())
        assertSame(profile, catalog.profilePolicy())
        assertSame(ui, catalog.uiCatalog())
        assertSame(gameplay.rebirth, profile.rebirth)
        assertSame(gameplay.rebirth, ui.rebirth)
        assertSame(gameplay.build, ui.build)
        assertSame(gameplay.items, ui.items)
    }

    @Test
    fun publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs() {
        assertFalse((gameplay.items as Any) is MutableList<*>)
        assertFalse((gameplay.weapons as Any) is MutableList<*>)
        assertFalse((gameplay.weaponMasteries as Any) is MutableList<*>)
        assertFalse((gameplay.matrixNodes as Any) is MutableList<*>)
        assertFalse((gameplay.relics as Any) is MutableList<*>)
        assertFalse((gameplay.clauseOffsets as Any) is MutableList<*>)
        assertFalse((gameplay.build.pairings as Any) is MutableList<*>)
        assertFalse((profile.coreShapes as Any) is MutableList<*>)
        assertTrue(gameplay.weapons.all { weapon -> (weapon.tags as Any) !is MutableList<*> })
        assertTrue(gameplay.items.all { item -> (item.clauses as Any) !is MutableList<*> && (item.rules as Any) !is MutableList<*> })

        val bootstrapItems = defaultContentBootstrapData().items
        val mutableItems = bootstrapItems.toMutableList()
        val copiedCatalog = createContentCatalogForTesting(
            defaultContentBootstrapData().copy(items = mutableItems),
        )
        mutableItems.clear()

        assertEquals(bootstrapItems.size, copiedCatalog.gameplayContent().items.size)
        assertEquals(bootstrapItems, copiedCatalog.gameplayContent().items)
    }

    @Test
    fun itemCatalogHoldsDistinctHandAuthoredItemsWithinTheBound() {
        val items = gameplay.items

        assertEquals(233, items.size)
        assertEquals(320, ContentBounds.MAX_ITEMS)
        assertTrue(items.size <= ContentBounds.MAX_ITEMS)
        assertEquals((0 until items.size).toList(), items.map(ItemDefinition::id))
        assertEquals(items.size, items.map(ItemDefinition::name).toSet().size)
        assertEquals(items.size, items.map(ItemDefinition::description).toSet().size)
        assertEquals(items.size, items.map(::mechanicalSignature).toSet().size, "Two items share the same mechanics")

        items.forEach { item ->
            val expectedStacks = when (item.rarity) {
                ItemRarity.COMMON -> 3
                ItemRarity.UNCOMMON, ItemRarity.RARE -> 2
                ItemRarity.EPIC, ItemRarity.LEGENDARY -> 1
            }
            assertEquals(expectedStacks, item.maxStacks, "${item.name} stacks follow its rarity")
            assertTrue(item.unlockLevel in 1..ContentBounds.MAX_ITEMS, "${item.name} has an invalid unlock level")
            assertTrue(
                item.tags.isNotEmpty() || item.elements.isNotEmpty() ||
                    item.rules.any { it is ItemRule.ReactionPower || it is ItemRule.ReactionResidue },
                "${item.name} belongs to no archetype, element or reaction family",
            )
            if (item.risk) {
                assertTrue(item.stats.any { it.amount < 0f } || item.rules.any { it.isLiability }, "${item.name} hides its cost")
            }
            assertEquals(item, gameplay.item(item.id))
            assertEquals(item, ui.item(item.id))
            assertTrue(profile.containsItem(item.id))
        }

        assertNull(gameplay.item(-1))
        assertNull(gameplay.item(items.size))
        assertFalse(profile.containsItem(-1))
        assertFalse(profile.containsItem(items.size))
    }

    @Test
    fun everyArchetypeAndElementIsDraftableAndRebirthGatesTheLegendaries() {
        val items = gameplay.items

        BuildTag.entries.forEach { tag ->
            assertTrue(items.count { tag in it.tags } >= 5, "$tag needs at least five items")
        }
        StatusElement.entries.forEach { element ->
            assertTrue(items.count { element in it.elements } >= 5, "$element needs at least five items")
            assertTrue(items.any { element in it.elements && it.unlockLevel <= 2 }, "$element needs an early enabler")
        }
        val legendaries = items.filter { it.rarity == ItemRarity.LEGENDARY }
        assertEquals(
            mapOf(
                "Solar Choir" to 2, "Glacial Heart" to 2, "Resonance Fork" to 4,
                // The expansion's legendaries: Fortune, Ricochet, Weapon, Sonic, Void and Reaction.
                "Cornucopia Core" to 3, "Perfect Carom" to 4, "Pyre Magazine" to 5, "Cathedral Bell" to 6,
                "Nothing Left" to 8, "Last Word" to 10,
                // The 0.5.0 legendaries: the Stasis, Annihilate and Oblivion keystones.
                "Hourglass Relay" to 8, "Horizon Knife" to 6, "Requiem Engine" to 9,
            ),
            legendaries.associate { it.name to it.minimumRebirth },
        )
        assertTrue(legendaries.all { it.minimumRebirth > 0 }, "Every legendary waits for a Rebirth")
        // Before the expansion only legendaries were gated; the first ninety items keep that rule.
        assertTrue(items.take(EXPANSION_START).filter { it.rarity != ItemRarity.LEGENDARY }.all { it.minimumRebirth == 0 })
        assertTrue(items.filter { it.risk }.all { it.rarity == ItemRarity.EPIC }, "Volatile offers draw from high-risk epics")
    }

    @Test
    fun theExpansionAppendsEightyThreeItemsWithTheirRarityMix() {
        val expansion = expansion040()

        assertEquals(83, expansion.size)
        assertEquals((EXPANSION_START until EXPANSION_END).toList(), expansion.map(ItemDefinition::id))
        assertEquals(
            mapOf(ItemRarity.COMMON to 20, ItemRarity.UNCOMMON to 24, ItemRarity.RARE to 20, ItemRarity.EPIC to 13, ItemRarity.LEGENDARY to 6),
            ItemRarity.entries.associateWith { rarity -> expansion.count { it.rarity == rarity } },
        )
        // Four high-risk epics, one for each of Drive, Anchor, Fortune and Chain.
        val risky = expansion.filter { it.risk }
        assertEquals(setOf(BuildTag.DRIVE, BuildTag.ANCHOR, BuildTag.FORTUNE, BuildTag.CHAIN), risky.map { it.tags.single() }.toSet())
        assertTrue(risky.all { it.rarity == ItemRarity.EPIC && it.minimumRebirth in 7..9 }, "High-risk epics sit in Reborn 7 to 9")
    }

    @Test
    fun eachRebirthTierUnlocksFourExpansionItemsAndTheRestStayOpen() {
        val expansion = expansion040()
        val expected = mapOf(
            1 to listOf(ItemRarity.RARE, ItemRarity.RARE, ItemRarity.RARE, ItemRarity.RARE),
            2 to listOf(ItemRarity.RARE, ItemRarity.RARE, ItemRarity.RARE, ItemRarity.RARE),
            3 to listOf(ItemRarity.RARE, ItemRarity.RARE, ItemRarity.RARE, ItemRarity.LEGENDARY),
            4 to listOf(ItemRarity.RARE, ItemRarity.RARE, ItemRarity.RARE, ItemRarity.LEGENDARY),
            5 to listOf(ItemRarity.RARE, ItemRarity.RARE, ItemRarity.EPIC, ItemRarity.LEGENDARY),
            6 to listOf(ItemRarity.RARE, ItemRarity.RARE, ItemRarity.EPIC, ItemRarity.LEGENDARY),
            7 to listOf(ItemRarity.RARE, ItemRarity.RARE, ItemRarity.EPIC, ItemRarity.EPIC),
            8 to listOf(ItemRarity.EPIC, ItemRarity.EPIC, ItemRarity.EPIC, ItemRarity.LEGENDARY),
            9 to listOf(ItemRarity.EPIC, ItemRarity.EPIC, ItemRarity.EPIC, ItemRarity.EPIC),
            10 to listOf(ItemRarity.UNCOMMON, ItemRarity.EPIC, ItemRarity.EPIC, ItemRarity.LEGENDARY),
        )
        expected.forEach { (tier, rarities) ->
            val gated = expansion.filter { it.minimumRebirth == tier }
            assertEquals(rarities, gated.map { it.rarity }.sortedBy { it.rank }, "Reborn $tier unlocks")
            assertTrue(gated.all { it.unlockLevel == 1 }, "Reborn $tier items are offered from the first level")
        }
        val open = expansion.filter { it.minimumRebirth == 0 }
        assertEquals(20, open.count { it.rarity == ItemRarity.COMMON })
        assertEquals(23, open.count { it.rarity == ItemRarity.UNCOMMON })
        assertEquals(43, open.size, "Only Commons and Uncommons are open without a Rebirth")
        open.filter { it.rarity == ItemRarity.COMMON }.forEach { assertTrue(it.unlockLevel in 1..8, "${it.name} unlock level") }
        open.filter { it.rarity == ItemRarity.UNCOMMON }.forEach { assertTrue(it.unlockLevel in 4..16, "${it.name} unlock level") }
    }

    @Test
    fun theExpansionBucketsCoverRicochetsSlamsWeaponsAndEveryNewReaction() {
        val expansion = expansion040()
        fun clausesOn(trigger: EffectTrigger) = expansion.filter { item -> item.clauses.any { it.trigger == trigger } }

        val ricochet = clausesOn(EffectTrigger.RICOCHET)
        val slam = clausesOn(EffectTrigger.SLAM)
        assertEquals(
            setOf(EffectAction.Burst::class, EffectAction.Shield::class, EffectAction.Vent::class, EffectAction.Overdrive::class, EffectAction.Buff::class),
            ricochet.map { it.clauses.first().actions.first()::class }.toSet(),
        )
        assertEquals(
            setOf(EffectAction.ApplyStatus::class, EffectAction.Shards::class, EffectAction.Strike::class, EffectAction.GainData::class, EffectAction.Detonate::class),
            slam.map { it.clauses.first().actions.first()::class }.toSet(),
        )
        assertEquals(10, ricochet.size + slam.size)
        // Both are frequent events: every clause on them is rate-limited.
        (ricochet + slam).flatMap { it.clauses }.filter { it.trigger == EffectTrigger.RICOCHET || it.trigger == EffectTrigger.SLAM }
            .forEach { clause -> assertTrue(clause.cooldownSeconds >= 0.5f || clause.everyNth >= 2, "$clause is rate-limited") }

        val arsenal = expansion.filter { BuildTag.ARSENAL in it.tags }
        assertEquals(12, arsenal.size)
        assertEquals(3, arsenal.count { item -> item.stats.any { it.effect == ItemEffect.ATTACK_SPEED } })
        assertEquals(2, arsenal.count { item -> item.stats.any { it.effect == ItemEffect.WEAPON_POWER } })
        val infused = arsenal.mapNotNull { item ->
            item.clauses.firstOrNull { it.trigger == EffectTrigger.WEAPON_HIT }?.actions?.first()?.let { it as? EffectAction.ApplyStatus }?.pick?.element
        }
        assertEquals(listOf(StatusElement.THERMAL, StatusElement.CRYO, StatusElement.ION, StatusElement.GRAVITIC, StatusElement.ENTROPY),
            infused.sortedBy { it.ordinal })
        assertTrue(arsenal.any { item ->
            item.clauses.any { it.trigger == EffectTrigger.KILL && EffectCondition.SourceWeapon in it.conditions && it.actions.first() is EffectAction.RefreshWeapon }
        })
        val legendaryArsenal = arsenal.single { it.rarity == ItemRarity.LEGENDARY }
        assertTrue(legendaryArsenal.clauses.first().let { it.trigger == EffectTrigger.WEAPON_HIT && it.everyNth > 1 && it.actions.first() is EffectAction.Detonate })

        // One item for each reaction of Sonic and Void: a reaction clause naming both of its elements.
        val newReactions = ReactionId.entries.drop(ReactionId.THUNDERCLAP.ordinal)
        newReactions.forEach { reaction ->
            val matching = expansion.filter { item ->
                item.clauses.any { clause ->
                    clause.trigger == EffectTrigger.REACTION &&
                        clause.conditions.filterIsInstance<EffectCondition.ElementIs>().map { it.element }.toSet() == setOf(reaction.first, reaction.second)
                }
            }
            assertEquals(1, matching.size, "$reaction has exactly one item")
            assertEquals(setOf(reaction.first, reaction.second), matching.single().elements.toSet(), "${matching.single().name} declares both elements")
        }
        assertEquals(11, clausesOn(EffectTrigger.REACTION).size)
    }

    @Test
    fun everyReactionHasExactlyOneKeystoneItemOfEpicRarityOrBetterThatDeclaresItsPair() {
        val keystones = gameplay.items.filter { it.keystone != null }

        assertEquals(ReactionId.entries.toList(), keystones.map { requireNotNull(it.keystone) }.sortedBy { it.ordinal })
        keystones.forEach { item ->
            val reaction = requireNotNull(item.keystone)
            assertTrue(item.rarity.rank >= ItemRarity.EPIC.rank, "${item.name} is Epic or Legendary")
            assertEquals(setOf(reaction.first, reaction.second), item.elements.toSet(), "${item.name} declares the elements of $reaction")
            // The keystone rule and one part that pushes the new form.
            assertTrue(item.stats.size + item.clauses.size + item.rules.size >= 2, "${item.name} supports its keystone form")
        }
    }

    @Test
    fun expansionItemsNeverShareTheirMechanicalHeadline() {
        val expansion = gameplay.items.drop(EXPANSION_START)
        val headlines = expansion.groupBy(::headline).filterValues { it.size > 1 }
        assertTrue(headlines.isEmpty(), "Items sharing trigger or rule, action and element: " +
            headlines.entries.joinToString { (key, group) -> "$key = " + group.joinToString { it.name } })
    }

    @Test
    fun thresholdCutsStayOffTheOriginalElementsAndAreRareForSonicAndVoid() {
        val expansion = gameplay.items.drop(EXPANSION_START)
        // Stacks cut at full copies, as the build applies them: a second copy cuts one more.
        fun cuts(items: List<ItemDefinition>, element: StatusElement) = items.sumOf { item ->
            item.rules.filterIsInstance<ItemRule.BreakThreshold>().filter { it.element == element }
                .sumOf { breakThresholdCut(it.reduction, item.maxStacks) }
        }
        StatusElement.entries.filter { it != StatusElement.SONIC && it != StatusElement.VOID }.forEach { element ->
            assertEquals(0, cuts(expansion, element), "The expansion adds no $element threshold cut")
        }
        assertTrue(cuts(gameplay.items, StatusElement.SONIC) <= 2, "Sonic cuts at most 2 stacks catalog-wide")
        assertTrue(cuts(gameplay.items, StatusElement.VOID) <= 2, "Void cuts at most 2 stacks catalog-wide")
    }

    @Test
    fun everyExpansionItemReadsAsPlainSentencesInBothLanguages() {
        gameplay.items.drop(EXPANSION_START).forEach { item ->
            val english = item.ruleLines(AppLanguage.English)
            val russian = item.ruleLines(AppLanguage.Russian)
            assertEquals(english.map { it.kind }, russian.map { it.kind }, item.name)
            assertTrue(english.isNotEmpty(), "${item.name} has rules text")
            english.forEach { line ->
                assertTrue(line.text.isNotBlank() && '{' !in line.text && "  " !in line.text, "${item.name}: '${line.text}'")
                assertTrue(line.text.first().isUpperCase() || line.text.first() in "+−", "${item.name}: '${line.text}' starts a sentence")
                assertTrue(line.text.length <= 140, "${item.name}: '${line.text}' fits a card")
            }
            russian.forEach { line -> assertFalse(line.text.any { it in 'A'..'Z' || it in 'a'..'z' }, "${item.name}: '${line.text}'") }
            val triggers = english.filter { it.kind == RuleLineKind.TRIGGER }.map { it.text }
            item.clauses.filter { it.trigger == EffectTrigger.REACTION }.forEach { clause ->
                val named = clause.conditions.filterIsInstance<EffectCondition.ReactionIs>().singleOrNull()?.reaction
                when {
                    // A clause for an element pair names the pair (0.4.0), one for a named reaction names it (0.5.0).
                    clause.conditions.any { it is EffectCondition.ElementIs } ->
                        assertTrue(triggers.any { it.startsWith("On a reaction between") }, item.name)
                    named != null -> assertTrue(triggers.any { gameplay.build.reaction(named).name in it }, "${item.name}: $triggers")
                    // Any reaction: the line says so.
                    else -> assertTrue(triggers.any { "reaction" in it }, "${item.name}: $triggers")
                }
            }
        }
    }

    @Test
    fun sonicAndVoidItemsAreAppendedUngatedCommonOrUncommonWithoutThresholdCuts() {
        val items = gameplay.items
        val sonic = items.subList(80, 85)
        val void = items.subList(85, 90)

        assertTrue(sonic.all { it.elements.toList() == listOf(StatusElement.SONIC) }, "ids 80-84 are Sonic")
        assertTrue(void.all { it.elements.toList() == listOf(StatusElement.VOID) }, "ids 85-89 are Void")
        (sonic + void).forEach { item ->
            assertTrue(item.rarity == ItemRarity.COMMON || item.rarity == ItemRarity.UNCOMMON, "${item.name} rarity")
            assertEquals(0, item.minimumRebirth, "${item.name} is ungated")
            assertFalse(item.rules.any { it is ItemRule.BreakThreshold }, "${item.name} cuts no threshold")
            assertFalse(item.risk)
        }
        assertEquals(1, sonic.count { it.unlockLevel <= 2 }, "one early Sonic enabler")
        assertEquals(1, void.count { it.unlockLevel <= 2 }, "one early Void enabler")
        // An enabler applies its element on an event every run sees often, not on a rare one such as a crit.
        val frequent = setOf(EffectTrigger.IMPACT, EffectTrigger.HURT, EffectTrigger.WEAPON_HIT)
        listOf(sonic, void).forEach { group ->
            val enabler = group.single { it.unlockLevel <= 2 }
            assertTrue(enabler.clauses.any { it.trigger in frequent }, "${enabler.name} applies its element on a frequent event")
        }
    }

    @Test
    fun clauseOffsetsFollowTheCapturedItemsWhenTheSnapshotIsCopied() {
        val items = gameplay.items

        assertEquals(items.sumOf { it.clauses.size }, gameplay.totalClauseCount)
        assertEquals(items.size, gameplay.clauseOffsets.size)
        var expected = 0
        items.forEach { item ->
            assertEquals(expected, gameplay.clauseOffsets[item.id])
            item.clauses.indices.forEach { clause -> assertEquals(expected + clause, gameplay.clauseIndex(item.id, clause)) }
            expected += item.clauses.size
        }

        val empty = gameplay.copy(items = immutableListOf())
        assertEquals(0, empty.totalClauseCount)
        assertTrue(empty.clauseOffsets.isEmpty())
        val oneClause = items.first { it.clauses.size == 1 }
        val single = gameplay.copy(items = immutableListOf(oneClause.copy(id = 0, clauses = (oneClause.clauses + oneClause.clauses).toImmutableList())))
        assertEquals(2, single.totalClauseCount)
        assertEquals(1, single.clauseIndex(0, 1))
        assertEquals(expected, gameplay.totalClauseCount)
    }

    @Test
    fun buildCatalogCoversEveryElementPairWithExactlyOneReaction() {
        val build = gameplay.build

        assertEquals(StatusElement.entries.toList(), build.elements.map { it.element })
        assertEquals(ReactionId.entries.toList(), build.reactions.map { it.id })
        assertEquals(BuildTag.entries.toList(), build.resonances.map { it.tag })
        assertEquals(ContentBounds.RESONANCE_TIERS, build.affinityTiers.size)

        val pairs = StatusElement.entries.flatMapIndexed { index, first ->
            StatusElement.entries.drop(index + 1).map { second -> first to second }
        }
        assertEquals(ReactionId.entries.size, pairs.size)
        pairs.forEach { (first, second) ->
            val reaction = ReactionId.between(first, second)
            assertEquals(reaction, ReactionId.between(second, first))
            assertTrue(reaction != null && reaction.involves(first) && reaction.involves(second))
        }
        assertEquals(ReactionId.entries.toSet(), pairs.mapNotNull { (a, b) -> ReactionId.between(a, b) }.toSet())
        StatusElement.entries.forEach { element -> assertNull(ReactionId.between(element, element)) }

        build.elements.forEach { definition ->
            assertTrue(definition.breakFloor in 2..definition.breakThreshold)
            assertEquals(definition.breakThreshold + 4, definition.stackCap)
        }
        build.reactions.forEach { reaction ->
            assertTrue(reaction.residue == null || reaction.residue in setOf(reaction.id.first, reaction.id.second))
        }
    }

    @Test
    fun buildPolicyTiersMagnitudesAndRebornChanceFollowTheirCurves() {
        val policy = gameplay.build.policy

        assertEquals(listOf(0, 0, 0, 1, 1, 2, 2, 3, 3), (0..8).map { policy.tier(it, policy.resonanceThresholds) })
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3), (0..6).map { policy.tier(it, policy.affinityThresholds) })
        assertEquals(3, policy.tier(99, policy.affinityThresholds))

        assertEquals(0f, policy.stackMagnitude(0))
        assertEquals(1f, policy.stackMagnitude(1))
        assertEquals(policy.stackMagnitude(ContentBounds.MAX_ITEM_STACKS), policy.stackMagnitude(ContentBounds.MAX_ITEM_STACKS + 1))
        val gains = (1..ContentBounds.MAX_ITEM_STACKS).map(policy::stackMagnitude).zipWithNext { a, b -> b - a }
        assertTrue(gains.zipWithNext().all { (a, b) -> b < a }, "Extra stacks give diminishing returns")

        assertEquals(0f, policy.rebornChance(-1))
        assertEquals(0f, policy.rebornChance(0))
        assertEquals(0.05f, policy.rebornChance(1))
        assertEquals(policy.maximumRebornChance, policy.rebornChance(ContentBounds.MAX_REBIRTH_LEVEL))
    }

    @Test
    fun weaponCatalogAndCapturedMasteryPolicyAreOrderedAndQueryable() {
        val weapons = gameplay.weapons

        assertEquals(ContentBounds.MAX_WEAPONS, WeaponId.entries.size)
        assertEquals(ContentBounds.MAX_WEAPONS, weapons.size)
        assertEquals(WeaponId.entries.toList(), weapons.map(WeaponDefinition::id))
        assertEquals(weapons.size, weapons.map(WeaponDefinition::id).toSet().size)
        assertEquals(weapons.size, weapons.map(WeaponDefinition::name).toSet().size)
        assertEquals(weapons.size, weapons.map(WeaponDefinition::description).toSet().size)
        // The original twelve keep their rising Matter costs; the twelve additions rise with their Rebirth
        // gate (a higher gate never costs less) and never share a price.
        val original = weapons.filter { it.id.ordinal < ORIGINAL_WEAPONS }
        assertTrue(original.zipWithNext().all { (left, right) -> right.permanentUnlockCost > left.permanentUnlockCost })
        assertTrue(original.all { it.minimumRebirth == 0 })
        val additions = weapons.filter { it.id.ordinal >= ORIGINAL_WEAPONS }.sortedBy(WeaponDefinition::permanentUnlockCost)
        assertEquals(ORIGINAL_WEAPONS, additions.size)
        assertTrue(additions.zipWithNext().all { (left, right) ->
            right.permanentUnlockCost > left.permanentUnlockCost && right.minimumRebirth >= left.minimumRebirth
        })
        assertEquals(0, weapons.first().permanentUnlockCost, "the starter weapon is free")
        weapons.forEach { weapon -> assertEquals(weapon, gameplay.weapon(weapon.id)) }

        val milestones = gameplay.weaponMasteries
        assertEquals(WeaponMastery.entries.toList(), milestones)
        assertEquals(WeaponMastery.CALIBRATED, gameplay.weaponMasteryForLevel(1))
        assertEquals(WeaponMastery.AMPLIFIED, gameplay.weaponMasteryForLevel(3))
        assertEquals(WeaponMastery.RESONANT, gameplay.weaponMasteryForLevel(6))
        assertEquals(WeaponMastery.ASCENDED, gameplay.weaponMasteryForLevel(10))
        assertEquals(WeaponMastery.AMPLIFIED, gameplay.weaponMasteryAfter(1))
        assertNull(gameplay.weaponMasteryAfter(10))
        assertTrue(milestones.zipWithNext().all { (left, right) ->
            right.minimumLevel > left.minimumLevel &&
                right.damageBonus > left.damageBonus &&
                right.activationSpeedBonus > left.activationSpeedBonus
        })
    }

    @Test
    fun everyWeaponCarriesItsTypedSynergyContract() {
        val weapons = gameplay.weapons
        val roster = weapons.associate { weapon ->
            weapon.id to listOf(
                weapon.family.name, weapon.element?.name ?: "-", weapon.cadence.name,
                weapon.tags.joinToString("+") { it.name }, weapon.key.name, weapon.scaling.name, weapon.signature.name,
            ).joinToString(" ")
        }
        assertEquals(
            mapOf(
                WeaponId.FLUX_WAKE to "MOMENTUM THERMAL CONTINUOUS DRIVE+OVERCLOCK MOTION SPEED ORB",
                WeaponId.MORNINGSTAR to "ORBITAL - CONTACT RAM+ANCHOR GRAVITY MASS PRISM",
                WeaponId.PHASE_LATTICE to "AURA CRYO CONTINUOUS BULWARK+ANCHOR STILLNESS SHIELD RING",
                WeaponId.NULL_LANCE to "PROJECTILE - DISCRETE DRIVE+PRECISION MOTION SPEED SHARD",
                WeaponId.GRAVITY_MINES to "TRAP GRAVITIC DISCRETE ANCHOR+CHAIN STILLNESS BRAKE DIAMOND",
                WeaponId.ION_SWARM to "ORBITAL ION DISCRETE ARSENAL+CHAIN CHARGE ATTACK_SPEED HEXAGON",
                WeaponId.RIFT_BLADES to "ORBITAL VOID CONTACT PRECISION+RAM CRITICAL CRIT STAR",
                WeaponId.ARC_COIL to "CHAIN ION DISCRETE ARSENAL+CHAIN CHARGE STATUS PENTAGON",
                WeaponId.QUASAR_CANNON to "PROJECTILE SONIC DISCRETE ARSENAL+OVERCLOCK HEAT HEAT STAR",
                WeaponId.ENTROPY_FIELD to "AURA ENTROPY CONTINUOUS BULWARK+CHAIN DECAY STATUS SPIRAL",
                WeaponId.SINGULARITY_SPEAR to "BEAM VOID DISCRETE OVERCLOCK+PRECISION VOID OVERDRIVE CRESCENT",
                WeaponId.PRISM_RELAY to "CHAIN - DISCRETE PRECISION+FORTUNE SPREAD LUCK TESSERACT",
                WeaponId.GYRE_DISC to "MOMENTUM - DISCRETE DRIVE+PRECISION MOTION SPEED ORB",
                WeaponId.SCATTER_BLOOM to "PROJECTILE - DISCRETE DRIVE+RAM MOTION DASH SHARD",
                WeaponId.TETHER_ARC to "BEAM ION CONTINUOUS ARSENAL+PRECISION CHARGE ATTACK_SPEED DIPOLE",
                WeaponId.SENTRY_GRID to "TRAP - DISCRETE ANCHOR+ARSENAL STILLNESS ATTACK_SPEED TESSERACT",
                WeaponId.HUNTER_SALVO to "PROJECTILE ENTROPY DISCRETE CHAIN+ARSENAL SPREAD COMBO HEXAGON",
                WeaponId.CONCUSSION_ENGINE to "PULSE SONIC DISCRETE RAM+BULWARK MOTION SPEED SPIRAL",
                WeaponId.GLACIER_BELL to "PULSE CRYO DISCRETE ANCHOR+BULWARK COLD BRAKE DIAMOND",
                WeaponId.EVENT_LENS to "TRAP GRAVITIC DISCRETE ANCHOR+CHAIN GRAVITY MASS DIPOLE",
                WeaponId.SOLAR_SWEEP to "BEAM THERMAL CONTINUOUS OVERCLOCK+PRECISION HEAT HEAT RING",
                WeaponId.AEGIS_SHARDS to "ORBITAL - DISCRETE BULWARK+ARSENAL SHIELD SHIELD PRISM",
                WeaponId.HOLLOW_RIFT to "TRAP VOID DISCRETE DRIVE+CHAIN VOID DASH CRESCENT",
                WeaponId.RESONANCE_DRUM to "PULSE SONIC DISCRETE ANCHOR+RAM ECHO MASS PENTAGON",
            ),
            roster,
        )
        assertTrue(weapons.all { it.tags.size == 2 && it.tags.toSet().size == 2 })
        assertTrue(weapons.all { it.masteryForms.size == 3 && it.masteryForms.none(String::isBlank) && it.signatureText.isNotBlank() })
        assertEquals(weapons.size * 3, weapons.flatMap { it.masteryForms }.toSet().size, "mastery forms are each weapon's own")
        assertEquals(weapons.size, weapons.map(WeaponDefinition::signatureText).toSet().size)
        CoreShape.entries.forEach { shape -> assertEquals(2, weapons.count { it.signature == shape }, "$shape signature weapons") }
        weapons.forEach { weapon ->
            val carriers = ui.relics.filter { weapon.key in it.keys }
            assertTrue(carriers.size >= 2 && carriers.any { !it.isSovereign }, "${weapon.id} key ${weapon.key} carriers")
        }
    }

    @Test
    fun matrixHasFourBranchesOfEightOrderedNodesEachEndingInOneKeystone() {
        val nodes = profile.matrixNodes

        assertEquals(32, MatrixNodeId.entries.size)
        assertTrue(MatrixNodeId.entries.size <= ContentBounds.MAX_MATRIX_NODES)
        assertEquals(MatrixNodeId.entries.toList(), nodes.map(MatrixNodeDefinition::id))
        assertEquals(nodes.size, nodes.map(MatrixNodeDefinition::name).toSet().size)
        assertSame(nodes, gameplay.matrixNodes)
        assertSame(nodes, ui.matrixNodes)
        MatrixBranch.entries.forEach { branch ->
            val inBranch = nodes.filter { it.branch == branch }
            assertEquals(8, inBranch.size, "$branch")
            assertEquals(1, inBranch.count(MatrixNodeDefinition::keystone), "$branch keystone")
            assertEquals(listOf(0, 1, 2, 3), inBranch.map(MatrixNodeDefinition::row).distinct().sorted(), "$branch rows")
            assertTrue(inBranch.single(MatrixNodeDefinition::keystone).row == 3, "$branch keystone is last")
        }
        nodes.forEach { node ->
            assertEquals(node, profile.matrixNode(node.id))
            assertEquals(node.maxRanks, node.costs.size)
            assertTrue(node.costs.zipWithNext().all { (left, right) -> right > left }, "${node.id} costs rise")
            assertFailsWith<IllegalArgumentException> { node.cost(-1) }
            assertFailsWith<IllegalArgumentException> { node.cost(node.maxRanks) }
            assertEquals(node.costs.sumOf { it.toLong() }, node.spent(node.maxRanks))
        }
    }

    @Test
    fun everyPureStatNodeMovesItsStatByANoticeableShareAtRankOne() {
        val percentEffects = setOf(
            ItemEffect.IMPACT_DAMAGE, ItemEffect.WEAPON_POWER, ItemEffect.COOLING, ItemEffect.DASH_POWER,
            ItemEffect.ATTACK_SPEED, ItemEffect.DATA_GAIN, ItemEffect.MATTER_GAIN, ItemEffect.OVERDRIVE_GAIN,
        )
        profile.matrixNodes.forEach { node ->
            val stats = node.effects.filterIsInstance<MatrixEffect.Stat>().map { it.delta }
            val behaviors = node.effects.filterNot { it is MatrixEffect.Stat }
            if (behaviors.isEmpty()) {
                // A pure stat node: a positive percent stat of at least 12 % (weapon power and attack
                // speed; the rest give 15 %), or a flat stat worth more than 15 % of the Core's starting
                // value (25 of 100 integrity, 20 shield, 70 pickup radius, 0.5 luck).
                assertTrue(stats.any { delta ->
                    delta.amount > 0f && (delta.effect !in percentEffects || delta.amount >= 0.12f - 1e-4f)
                }, "${node.id} must change its stat noticeably")
            }
        }
    }

    @Test
    fun matrixNodesOnlySpendMoreForLaterRowsAndLaterGates() {
        MatrixBranch.entries.forEach { branch ->
            val inBranch = profile.matrixNodes.filter { it.branch == branch }
            val firstRankByRow = (0..3).map { row -> inBranch.filter { it.row == row }.minOf { it.costs.first() } }
            assertTrue(firstRankByRow.zipWithNext().all { (upper, lower) -> lower >= upper }, "$branch first ranks by row")
            inBranch.filter { it.row == 0 }.forEach { assertEquals(0, it.minimumRebirth, "${it.id} is open from the start") }
        }
    }

    @Test
    fun coreShapeUnlockPolicyIsCapturedInStableIdOrder() {
        assertEquals(CoreShape.entries.toList(), profile.coreShapes.map { definition -> definition.id })
        assertEquals(0, profile.coreShape(CoreShape.ORB).unlockTarget)
        assertEquals(3, profile.coreShape(CoreShape.PRISM).unlockTarget)
        assertEquals(20, profile.coreShape(CoreShape.SHARD).unlockTarget)
        assertEquals(1, profile.coreShape(CoreShape.RING).unlockTarget)
        assertEquals(1, profile.coreShape(CoreShape.DIAMOND).unlockTarget)
        assertEquals(3, profile.coreShape(CoreShape.TESSERACT).unlockTarget)
        assertEquals(profile.coreShapes, gameplay.coreShapes)
    }

    @Test
    fun twelveCharactersCarryTheirMechanicTriggerTraitsAndUnlocks() {
        assertEquals(12, CoreShape.entries.size)
        val shapes = profile.coreShapes
        assertEquals(CoreShape.entries.size, shapes.map { it.mechanicName }.toSet().size, "every mechanic is unique")
        assertEquals(CoreShape.entries.size, shapes.map { it.displayName }.toSet().size)
        // At most four characters use Dash as their main trigger; only the Diamond uses Brake.
        assertTrue(shapes.count { it.triggerKind == CharacterTriggerKind.DASH } <= 4)
        assertEquals(listOf(CoreShape.DIAMOND), shapes.filter { it.triggerKind == CharacterTriggerKind.BRAKE }.map { it.id })
        assertTrue(shapes.all { it.difficulty in 1..3 })
        val unlocks = shapes.associate { it.id to (it.unlockRequirement to it.unlockTarget) }
        assertEquals(CharacterUnlockRequirement.TOTAL_KILLS to 1_500, unlocks[CoreShape.STAR])
        assertEquals(CharacterUnlockRequirement.REBIRTH_RANK to 1, unlocks[CoreShape.SPIRAL])
        assertEquals(CharacterUnlockRequirement.RICOCHETS to 40, unlocks[CoreShape.DIPOLE])
        assertEquals(CharacterUnlockRequirement.REBIRTH_RANK to 3, unlocks[CoreShape.PENTAGON])
        assertEquals(CharacterUnlockRequirement.REBIRTH_RANK to 5, unlocks[CoreShape.CRESCENT])
        assertEquals(CharacterUnlockRequirement.REBIRTH_RANK to 7, unlocks[CoreShape.HEXAGON])
        val prism = profile.coreShape(CoreShape.PRISM).traits
        assertEquals(1.25f, prism.massMultiplier)
        assertEquals(20, prism.integrityBonus)
        assertEquals(CharacterTraits(), profile.coreShape(CoreShape.ORB).traits)
        assertEquals(60f, profile.coreShape(CoreShape.HEXAGON).traits.pickupRadiusBonus)
    }

    /**
     * What an item does first: its first clause's trigger, action and element, else its first rule's kind
     * (with the stat a modifier changes) and element, else its first stat.
     */
    private fun headline(item: ItemDefinition): Triple<String, String, String> {
        item.clauses.firstOrNull()?.let { clause ->
            val action = clause.actions.first()
            val element = when (action) {
                is EffectAction.ApplyStatus -> action.pick.name
                is EffectAction.Chain -> action.pick?.name
                is EffectAction.Shards -> action.pick?.name
                is EffectAction.Well -> action.pick?.name
                is EffectAction.Detonate -> action.elements.first().name
                // The 0.5.0 actions are told apart by what they pick.
                is EffectAction.RefundSkills -> action.pick.name
                is EffectAction.Zone -> action.kind.name
                is EffectAction.CoreImpulse -> action.direction.name
                is EffectAction.ApplyMark -> action.mark.name
                else -> null
            }
            return Triple(clause.trigger.name, action::class.simpleName.orEmpty(), element ?: "-")
        }
        item.rules.firstOrNull()?.let { rule ->
            val (kind, element) = when (rule) {
                is ItemRule.WhileModifier -> "WhileModifier ${rule.stat} ${rule.condition::class.simpleName}" to null
                is ItemRule.ElementPotency -> "ElementPotency" to rule.element
                is ItemRule.StatusDuration -> "StatusDuration" to rule.element
                is ItemRule.BreakThreshold -> "BreakThreshold" to rule.element
                is ItemRule.BreakPower -> "BreakPower" to rule.element
                is ItemRule.BreakSpread -> "BreakSpread" to rule.element
                is ItemRule.CritDamageVersus ->
                    "CritDamageVersus ${rule.condition::class.simpleName}" to (rule.condition as? EffectCondition.TargetHas)?.element
                is ItemRule.DamageVersus ->
                    "DamageVersus ${rule.condition::class.simpleName}" to (rule.condition as? EffectCondition.TargetHas)?.element
                is ItemRule.ReactionKeystone -> "ReactionKeystone ${rule.reaction}" to null
                is ItemRule.ReactionTuning -> "ReactionTuning ${rule.reaction ?: "all"} ${rule.aspect}" to null
                is ItemRule.FieldTuning -> "FieldTuning ${rule.aspect}" to null
                is ItemRule.SkillCooldown -> "SkillCooldown" to rule.element
                is ItemRule.SkillPower -> "SkillPower" to rule.element
                is ItemRule.Convert -> "Convert ${rule.kind}" to null
                else -> (rule::class.simpleName.orEmpty()) to null
            }
            return Triple("rule $kind", "", element?.name ?: "-")
        }
        return Triple("stat ${item.stats.first().effect}", "", "-")
    }

    /** The 0.4.0 expansion, ids [EXPANSION_START] until [EXPANSION_END]; later releases append after it. */
    private fun expansion040(): List<ItemDefinition> = gameplay.items.subList(EXPANSION_START, EXPANSION_END)

    private companion object {
        /** The first id of the 0.4.0 expansion (ids 90 to 172). */
        const val EXPANSION_START = 90

        /** One past the last id of the 0.4.0 expansion: the 0.5.0 items start here. */
        const val EXPANSION_END = 173
    }

    /** Everything that changes how an item plays, ignoring its name, flavor and id. */
    private fun mechanicalSignature(item: ItemDefinition): List<Any> =
        listOf(item.rarity, item.tags, item.elements, item.stats, item.clauses, item.rules, item.maxStacks, item.risk)
}

private const val ORIGINAL_WEAPONS = 12
