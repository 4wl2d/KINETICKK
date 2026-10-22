// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.RelicPairingKind
import kinetickk.ball.content.api.RunTempoProfile
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentBootstrapValidationTest {
    @Test
    fun exactCatalogBoundsAreAccepted() {
        val data = defaultContentBootstrapData()
        val gameplay = createContentCatalogForTesting(data).gameplayContent()

        assertEquals(173, gameplay.items.size)
        assertEquals(ContentBounds.MAX_WEAPONS, gameplay.weapons.size)
        assertEquals(MatrixNodeId.entries.size, gameplay.matrixNodes.size)
        assertEquals(RelicId.entries.size, gameplay.relics.size)
        assertEquals(20, gameplay.build.pairings.size)
        assertEquals(
            ContentBounds.MAX_REBIRTH_LEVEL - ContentBounds.MIN_REBIRTH_LEVEL + 1,
            gameplay.rebirth.profiles.size,
        )

        val full = createContentCatalogForTesting(data.copy(items = data.items.paddedTo(ContentBounds.MAX_ITEMS)))
        assertEquals(ContentBounds.MAX_ITEMS, full.gameplayContent().items.size)
        assertEquals(ContentBounds.MAX_ITEMS, full.profilePolicy().itemCount)
    }

    @Test
    fun itemBoundRejectsNPlusOne() {
        val data = defaultContentBootstrapData()

        assertRejected(data.copy(items = data.items.paddedTo(ContentBounds.MAX_ITEMS + 1)), "items size 321")
    }

    @Test
    fun itemUnlockLevelsMayReachTheRunLevelCapButNotPassIt() {
        val data = defaultContentBootstrapData().copy(tempo = RunTempoProfile(maximumLevel = 42))
        val capped = data.items.last().copy(unlockLevel = 42)

        val accepted = createContentCatalogForTesting(data.copy(items = data.items.replaceLast(capped)))
        assertEquals(42, accepted.gameplayContent().items.last().unlockLevel)
        assertRejected(
            data.copy(items = data.items.replaceLast(capped.copy(unlockLevel = 43))),
            "Item unlock levels must not exceed the maximum run level 42",
        )
    }

    @Test
    fun weaponBoundRejectsNPlusOne() {
        val data = defaultContentBootstrapData()
        val extra = data.weapons.first().copied(name = "Overflow Weapon")

        assertRejected(data.copy(weapons = data.weapons + extra), "weapons size 25")
    }

    @Test
    fun skillPoolFollowsTheSkillIdOrderAndRejectsTheFirstSkillPastTheBound() {
        val data = defaultContentBootstrapData()
        val gameplay = createContentCatalogForTesting(data).gameplayContent()

        assertEquals(SkillId.entries.toList(), gameplay.skills.map { it.id })
        assertEquals(gameplay.skills, createContentCatalogForTesting(data).uiCatalog().skills)
        val padded = data.skills + List(ContentBounds.MAX_SKILLS + 1 - data.skills.size) { data.skills.last() }
        assertRejected(data.copy(skills = padded), "skills size 25")
        assertRejected(data.copy(skills = data.skills.swappedHead()), "Skill ids must match")
        assertRejected(data.copy(skills = data.skills.dropLast(1)), "Skill ids must match")
    }

    @Test
    fun upgradeBasesAcceptTheBoundAndRejectOneMore() {
        val data = defaultContentBootstrapData()
        val weapon = data.weapons.first()

        assertEquals(weapon, weapon.copied(upgradeBaseCost = weapon.upgradeBaseCost))
        weapon.copied(upgradeBaseCost = ContentBounds.MAX_UPGRADE_BASE_COST)
        assertFailsWith<IllegalArgumentException> {
            weapon.copied(upgradeBaseCost = ContentBounds.MAX_UPGRADE_BASE_COST + 1)
        }
        assertFailsWith<IllegalArgumentException> { weapon.copied(upgradeBaseCost = 0) }
        val policy = createContentCatalogForTesting(data).profilePolicy()
        assertEquals(ContentBounds.MAX_UPGRADE_LEVEL, policy.upgrades.maxLevel)
        assertEquals(policy.upgrades, createContentCatalogForTesting(data).gameplayContent().upgrades)
    }

    @Test
    fun aWeaponKeyMustBeCarriedByTwoRelicsOneOfThemOrdinary() {
        val data = defaultContentBootstrapData()
        // Fragile rides on a single Relic: harmony with it would be a dead end.
        assertEquals(1, data.relics.count { RelicKey.FRAGILE in it.keys })
        assertRejected(
            data.copy(weapons = data.weapons.replaceLast(data.weapons.last().copied(key = RelicKey.FRAGILE))),
            "key FRAGILE must be carried by two Relics",
        )
        // Shield rides on exactly two ordinary Relics, the minimum the rule accepts.
        assertEquals(2, data.relics.count { RelicKey.SHIELD in it.keys && !it.isSovereign })
        createContentCatalogForTesting(data.copy(weapons = data.weapons.replaceLast(data.weapons.last().copied(key = RelicKey.SHIELD))))
    }

    @Test
    fun aWeaponKeyCarriedOnlyBySovereignRelicsIsRejected() {
        val data = defaultContentBootstrapData()
        // Null Covenant is the only ordinary Void Relic; without it Void rides on two Sovereign Relics alone.
        val voidless = data.relics.map { relic ->
            if (relic.id == RelicId.NULL_COVENANT) relic.copy(keys = relic.keys.filter { it != RelicKey.VOID }.toImmutableList()) else relic
        }.toImmutableList()
        val carriers = voidless.filter { RelicKey.VOID in it.keys }
        assertEquals(2, carriers.size)
        assertTrue(carriers.all { it.isSovereign })
        assertTrue(data.weapons.any { it.key == RelicKey.VOID })

        assertRejected(data.copy(relics = voidless), "key VOID must be carried by two Relics, one of them not Sovereign")
    }

    @Test
    fun matrixNodeBoundRejectsNPlusOne() {
        val data = defaultContentBootstrapData()
        val filler = (data.matrixNodes.size until ContentBounds.MAX_MATRIX_NODES).map { data.matrixNodes.first() }

        // At the bound only the stable-id rule refuses the duplicates; one past it the bound refuses first.
        assertRejected(data.copy(matrixNodes = data.matrixNodes + filler), "matrix-node ids must be unique")
        assertRejected(data.copy(matrixNodes = data.matrixNodes + filler + data.matrixNodes.first()), "matrix nodes size 41")
    }

    @Test
    fun matrixPrerequisitesStayInTheirBranchOneRowAboveTheNode() {
        val data = defaultContentBootstrapData()
        fun replaced(node: MatrixNodeDefinition) = data.copy(matrixNodes = data.matrixNodes.map { if (it.id == node.id) node else it })
        val criticalLattice = data.matrixNodes.first { it.id == MatrixNodeId.CRITICAL_LATTICE }

        assertRejected(replaced(criticalLattice.copy(prerequisites = immutableListOf(MatrixNodeId.REINFORCED_HULL))), "another branch")
        assertRejected(replaced(criticalLattice.copy(row = 2)), "one row below")
        assertRejected(
            replaced(criticalLattice.copy(name = data.matrixNodes.first().name)),
            "Matrix node names must be unique",
        )
    }

    @Test
    fun relicBoundRejectsNPlusOne() {
        val data = defaultContentBootstrapData()
        val filler = (data.relics.size until ContentBounds.MAX_RELICS).map { data.relics.first() }

        // At the bound only the stable-id rule refuses the duplicates; one past it the bound refuses first.
        assertRejected(data.copy(relics = data.relics + filler), "relic ids must be unique")
        assertRejected(data.copy(relics = data.relics + filler + data.relics.first()), "relics size 25")
    }

    @Test
    fun synergyBoundRejectsNPlusOne() {
        val data = defaultContentBootstrapData()
        val pairings = data.build.pairings

        // Synergies became named Relic pairings, exactly one per RelicPairingId. The bound sits above the
        // id count, so a list of N entries is refused by the id-order rule; N + 1 is refused by the bound.
        assertEquals(RelicPairingId.entries.size, pairings.size)
        assertTrue(RelicPairingId.entries.size <= ContentBounds.MAX_RELIC_PAIRINGS)
        val oversized = (pairings + List(ContentBounds.MAX_RELIC_PAIRINGS + 1 - pairings.size) { pairings.first() }).toImmutableList()
        assertEquals(
            "Relic pairings size ${ContentBounds.MAX_RELIC_PAIRINGS + 1} exceeds ${ContentBounds.MAX_RELIC_PAIRINGS}",
            assertFailsWith<IllegalArgumentException> { data.build.copy(pairings = oversized) }.message,
        )
        assertEquals(
            "Pairings follow RelicPairingId order",
            assertFailsWith<IllegalArgumentException> { data.build.copy(pairings = oversized.dropLast(1).toImmutableList()) }.message,
        )
        assertFailsWith<IllegalArgumentException> { data.build.copy(pairings = (pairings + pairings.first()).toImmutableList()) }
        assertFailsWith<IllegalArgumentException> { data.build.copy(pairings = pairings.dropLast(1).toImmutableList()) }
    }

    @Test
    fun buildCatalogRejectsOutOfOrderVocabularyAndMalformedPolicy() {
        val build = defaultContentBootstrapData().build

        fun rejection(block: () -> Unit) = assertFailsWith<IllegalArgumentException> { block() }.message
        assertEquals("StatusElement definitions follow StatusElement order", rejection { build.copy(elements = build.elements.swappedHead()) })
        assertEquals("Reaction definitions follow ReactionId order", rejection { build.copy(reactions = build.reactions.swappedHead()) })
        assertEquals("Resonance definitions follow BuildTag order", rejection { build.copy(resonances = build.resonances.swappedHead()) })
        rejection { build.copy(affinityTiers = build.affinityTiers.dropLast(1).toImmutableList()) }

        val policy = build.policy
        rejection { policy.copy(resonanceThresholds = immutableListOf(3, 3, 7)) }
        rejection { policy.copy(resonanceThresholds = immutableListOf(0, 5, 7)) }
        rejection { policy.copy(resonanceThresholds = immutableListOf(3, 5)) }
        rejection { policy.copy(affinityThresholds = immutableListOf(4, 2, 6)) }
        rejection { policy.copy(stackMagnitudes = immutableListOf(1f, 1f, 2.2f)) }
        rejection { policy.copy(rebornMagnitude = 0.99f) }
        // The tightest legal thresholds and a neutral Reborn are still accepted.
        policy.copy(resonanceThresholds = immutableListOf(1, 2, 3), affinityThresholds = immutableListOf(1, 2, 3), rebornMagnitude = 1f)
    }

    @Test
    fun rebirthBoundRejectsLevelEleven() {
        val data = defaultContentBootstrapData()
        val extra = data.rebirthProfiles.last().copy(tier = ContentBounds.MAX_REBIRTH_LEVEL + 1)

        assertRejected(data.copy(rebirthProfiles = data.rebirthProfiles + extra), "Rebirth profiles size 12")
    }

    @Test
    fun bootstrapRejectsEveryCatalogIdCollision() {
        val data = defaultContentBootstrapData()

        assertRejected(
            data.copy(items = data.items.replaceLast(data.items.last().copy(id = 0))),
            "item ids must be unique",
        )
        assertRejected(
            data.copy(
                weapons = data.weapons.replaceLast(
                    data.weapons.last().copied(id = WeaponId.FLUX_WAKE),
                ),
            ),
            "weapon ids must be unique",
        )
        assertRejected(
            data.copy(
                matrixNodes = data.matrixNodes.replaceLast(
                    data.matrixNodes.last().copy(id = MatrixNodeId.IMPACT_DRIVER),
                ),
            ),
            "matrix-node ids must be unique",
        )
        assertRejected(
            data.copy(relics = data.relics.replaceLast(data.relics.last().copy(id = RelicId.PERPETUAL_VECTOR))),
            "relic ids must be unique",
        )
        assertRejected(
            data.copy(
                rebirthProfiles = data.rebirthProfiles.replaceLast(
                    data.rebirthProfiles.last().copy(tier = 0),
                ),
            ),
            "Rebirth ids must be unique",
        )
    }

    @Test
    fun bootstrapRejectsAnyNonCanonicalContentVersion() {
        val data = defaultContentBootstrapData().copy(version = ContentVersion("kinetickk-content-1"))

        assertRejected(data, KINETICKK_CONTENT_VERSION.value)
    }

    @Test
    fun pairingsMustAgreeWithTheirRelicKeys() {
        val data = defaultContentBootstrapData()
        val pairings = data.build.pairings
        val concord = pairings.indexOfFirst { it.kind == RelicPairingKind.CONCORD }
        val discord = pairings.indexOfFirst { it.kind == RelicPairingKind.DISCORD }

        val opposedConcord = pairings.withReplaced(concord, pairings[concord].copy(kind = RelicPairingKind.DISCORD))
        assertRejected(data.copy(build = data.build.copy(pairings = opposedConcord)), "${pairings[concord].id} must join opposed keys")

        val harmlessDiscord = pairings.withReplaced(discord, pairings[discord].copy(kind = RelicPairingKind.CONCORD))
        assertRejected(data.copy(build = data.build.copy(pairings = harmlessDiscord)), "${pairings[discord].id} must join Relics that share a key")

        // A concord needs a shared key too: Perpetual Vector (Motion) and Echo Chamber (Echo) share none.
        val keyless = pairings.withReplaced(
            concord,
            pairings[concord].copy(first = RelicId.PERPETUAL_VECTOR, second = RelicId.ECHO_CHAMBER),
        )
        assertRejected(data.copy(build = data.build.copy(pairings = keyless)), "${pairings[concord].id} must join Relics that share a key")

        // Faraday Cage holds only while Aegis Inversion carries Charge alongside Stormheart Capacitor.
        val aegis = data.relics.indexOfFirst { it.id == RelicId.AEGIS_INVERSION }
        val shieldAegis = data.relics.withReplaced(
            aegis,
            data.relics[aegis].copy(keys = immutableListOf(RelicKey.SHIELD, RelicKey.LIFE)),
        )
        assertRejected(data.copy(relics = shieldAegis), "FARADAY_CAGE must join Relics that share a key")

        // A Glass Comet discord with a Relic that has no opposed key is unpredictable from the keys.
        val unpredictable = pairings.withReplaced(
            discord,
            pairings[discord].copy(first = RelicId.GLASS_COMET, second = RelicId.ECHO_CHAMBER),
        )
        assertRejected(data.copy(build = data.build.copy(pairings = unpredictable)), "must join opposed keys")

        assertFailsWith<IllegalArgumentException> {
            data.build.copy(pairings = pairings.withReplaced(1, pairings[1].copy(first = pairings[0].first, second = pairings[0].second)))
        }
    }

    @Test
    fun itemsMustDeclareEveryElementTheyTouch() {
        val data = defaultContentBootstrapData()
        val base = data.items.first { it.elements.isEmpty() && it.clauses.isNotEmpty() }
        val sneaky = base.copy(
            clauses = immutableListOf(
                EffectClause(EffectTrigger.IMPACT, actions = immutableListOf(EffectAction.ApplyStatus(ElementPick.CRYO, 2))),
            ),
        )
        assertRejected(data.copy(items = data.items.withReplaced(base.id, sneaky)), "${base.name} touches undeclared elements")

        val chained = base.copy(
            clauses = immutableListOf(
                EffectClause(
                    EffectTrigger.WEAPON_HIT,
                    actions = immutableListOf(EffectAction.Chain(2, 200f, DamageBasis.HIT, 0.3f, ElementPick.ION)),
                    everyNth = 3,
                ),
            ),
        )
        assertRejected(data.copy(items = data.items.withReplaced(base.id, chained)), "touches undeclared elements")
        val ruled = base.copy(rules = immutableListOf(ItemRule.BreakPower(StatusElement.GRAVITIC, 0.2f)))
        assertRejected(data.copy(items = data.items.withReplaced(base.id, ruled)), "touches undeclared elements")

        // Dominant and secondary picks name no element and stay legal on element-less items.
        val dominant = base.copy(
            clauses = immutableListOf(
                EffectClause(EffectTrigger.WEAPON_HIT, actions = immutableListOf(EffectAction.ApplyStatus(ElementPick.DOMINANT, 1)), everyNth = 4),
            ),
        )
        createContentCatalogForTesting(data.copy(items = data.items.withReplaced(base.id, dominant)))
        createContentCatalogForTesting(
            data.copy(items = data.items.withReplaced(base.id, sneaky.copy(elements = immutableListOf(StatusElement.CRYO)))),
        )
    }

    @Test
    fun thresholdCutsAcrossTheWholeCatalogCannotDropABreakBelowTwoStacks() {
        val data = defaultContentBootstrapData()
        val cryo = data.build.element(StatusElement.CRYO)
        val existing = data.items.sumOf { item ->
            item.rules.filterIsInstance<ItemRule.BreakThreshold>().filter { it.element == StatusElement.CRYO }.sumOf { it.reduction }
        }
        // threshold − every cut − one affinity step must stay at two or more.
        val allowed = cryo.breakThreshold - 1 - 2 - existing
        val atFloor = data.items + cutter(data.items.size, StatusElement.CRYO, allowed)
        createContentCatalogForTesting(data.copy(items = atFloor))
        val belowFloor = data.items + cutter(data.items.size, StatusElement.CRYO, allowed + 1)
        assertRejected(data.copy(items = belowFloor), "CRYO break threshold can fall below two stacks")

        assertFailsWith<IllegalArgumentException> { cryo.copy(breakFloor = 1) }
        assertFailsWith<IllegalArgumentException> { cryo.copy(breakFloor = cryo.breakThreshold + 1) }
        assertEquals(cryo.breakThreshold, cryo.copy(breakFloor = cryo.breakThreshold).breakFloor)
        assertFailsWith<IllegalArgumentException> { ItemRule.BreakThreshold(StatusElement.CRYO, 5) }
    }

    @Test
    fun sustainOnFrequentTriggersNeedsACooldownOrAnEveryNthGate() {
        val heal = immutableListOf<EffectAction>(EffectAction.Heal(2f))

        assertFailsWith<IllegalArgumentException> { EffectClause(EffectTrigger.IMPACT, actions = heal) }
        assertFailsWith<IllegalArgumentException> { EffectClause(EffectTrigger.KILL, actions = heal, cooldownSeconds = 0.49f) }
        assertFailsWith<IllegalArgumentException> {
            EffectClause(EffectTrigger.WEAPON_HIT, actions = immutableListOf(EffectAction.RefreshWeapon(0.1f)))
        }
        listOf(EffectTrigger.RICOCHET, EffectTrigger.SLAM).forEach { trigger ->
            assertFailsWith<IllegalArgumentException>("$trigger") { EffectClause(trigger, actions = heal) }
            EffectClause(trigger, actions = heal, cooldownSeconds = 0.5f)
            EffectClause(trigger, actions = heal, everyNth = 2)
        }
        EffectClause(EffectTrigger.KILL, actions = heal, cooldownSeconds = 0.5f)
        EffectClause(EffectTrigger.IMPACT, actions = heal, everyNth = 2)
        EffectClause(EffectTrigger.DASH, actions = heal)
        EffectClause(EffectTrigger.IMPACT, actions = immutableListOf(EffectAction.ApplyStatus(ElementPick.THERMAL, 1)))
    }

    @Test
    fun riskItemsMustDeclareALiability() {
        assertFailsWith<IllegalArgumentException> {
            riskItem(stats = immutableListOf(StatDelta(ItemEffect.WEAPON_POWER, 0.3f)))
        }
        assertFailsWith<IllegalArgumentException> {
            riskItem(rules = immutableListOf(ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.DAMAGE_TAKEN, -0.2f)))
        }
        riskItem(stats = immutableListOf(StatDelta(ItemEffect.WEAPON_POWER, 0.3f), StatDelta(ItemEffect.COOLING, -0.2f)))
        riskItem(rules = immutableListOf(ItemRule.WhileModifier(EffectCondition.SpeedBelow(400f), BuffStat.DAMAGE_TAKEN, 0.2f)))
        riskItem(rules = immutableListOf(ItemRule.NonCriticalDamage(-0.1f)))
        riskItem(rules = immutableListOf(ItemRule.ReactionPower(0.5f), ItemRule.ReactionRecoil(4f, 120f)))
        assertFailsWith<IllegalArgumentException> { riskItem(rules = immutableListOf(ItemRule.NonCriticalDamage(0.1f))) }
    }

    @Test
    fun itemCatalogValidationRefusesUndraftableArchetypesAndElements() {
        val items = defaultContentBootstrapData().items
        validateItems(items)

        // Leave Fortune with exactly four items by retagging the rest.
        val retagged = items.filter { BuildTag.FORTUNE in it.tags }.drop(4).map { it.id }.toSet()
        val fourFortune = items.map { item ->
            if (item.id !in retagged) item else item.copy(
                tags = item.tags.map { tag ->
                    if (tag != BuildTag.FORTUNE) tag else BuildTag.entries.first { it != BuildTag.FORTUNE && it !in item.tags }
                }.toImmutableList(),
            )
        }
        val failure = assertFailsWith<IllegalStateException> { validateItems(fourFortune) }
        assertTrue(failure.message.orEmpty().contains("FORTUNE needs at least five items"), failure.message)

        val earlyIon = items.filter { StatusElement.ION in it.elements && it.unlockLevel <= 2 }
        val lateIon = items.map { item -> if (item in earlyIon) item.copy(unlockLevel = 3) else item }
        assertFailsWith<IllegalStateException> { validateItems(lateIon) }

        val renamed = items.mapIndexed { index, item -> if (index == 1) item.copy(name = items[0].name) else item }
        assertFailsWith<IllegalStateException> { validateItems(renamed) }
    }

    @Test
    fun keystonesAndExclusiveSkillRulesStayOnePerCatalog() {
        val items = defaultContentBootstrapData().items
        fun epic(name: String, rules: List<ItemRule>, vararg elements: StatusElement) = ItemDefinition(
            id = 0, name = name, description = "$name exercises the exclusive rules.", rarity = ItemRarity.EPIC,
            tags = immutableListOf(BuildTag.CHAIN), elements = elements.toList().toImmutableList(), rules = rules.toImmutableList(),
            maxStacks = 1, unlockLevel = 1,
        )
        fun withAppended(vararg extra: ItemDefinition) = items + extra.mapIndexed { index, item -> item.copy(id = items.size + index) }
        val star = epic("Star Seal", listOf(ItemRule.ReactionKeystone(ReactionId.FUSION)), StatusElement.THERMAL, StatusElement.GRAVITIC)
        validateItems(withAppended(star))

        val twin = epic("Second Star Seal", listOf(ItemRule.ReactionKeystone(ReactionId.FUSION)), StatusElement.THERMAL, StatusElement.GRAVITIC)
        val doubled = assertFailsWith<IllegalStateException> { validateItems(withAppended(star, twin)) }
        assertTrue(doubled.message.orEmpty().contains("FUSION has more than one keystone item"), doubled.message)
        val halfDeclared = epic("Half Seal", listOf(ItemRule.ReactionKeystone(ReactionId.WILDFIRE)), StatusElement.THERMAL)
        val undeclared = assertFailsWith<IllegalStateException> { validateItems(withAppended(halfDeclared)) }
        assertTrue(undeclared.message.orEmpty().contains("without declaring both of its elements"), undeclared.message)

        val charges = epic("Twin Chamber", listOf(ItemRule.SkillCharges))
        validateItems(withAppended(charges))
        assertFailsWith<IllegalStateException> { validateItems(withAppended(charges, charges.copy(name = "Third Chamber", description = "Another."))) }
        val attune = epic("Alchemical Core", listOf(ItemRule.SkillAttunement(ElementPick.DOMINANT)))
        validateItems(withAppended(attune))
        assertFailsWith<IllegalStateException> { validateItems(withAppended(attune, attune.copy(name = "Second Core", description = "Again."))) }
    }

    @Test
    fun skillRulesDeclareTheElementsTheyName() {
        val data = defaultContentBootstrapData()
        val base = data.items.first { it.elements.isEmpty() && it.clauses.isNotEmpty() }
        listOf(
            ItemRule.SkillPower(StatusElement.SONIC, 0.2f),
            ItemRule.SkillCooldown(StatusElement.SONIC, 0.1f),
            ItemRule.SkillAttunement(ElementPick.SONIC),
        ).forEach { rule ->
            assertRejected(data.copy(items = data.items.withReplaced(base.id, base.copy(rules = immutableListOf(rule)))), "touches undeclared elements")
            createContentCatalogForTesting(
                data.copy(items = data.items.withReplaced(base.id, base.copy(rules = immutableListOf(rule), elements = immutableListOf(StatusElement.SONIC)))),
            )
        }
        // Rules for every skill, or for the build's own element, name none.
        createContentCatalogForTesting(data.copy(items = data.items.withReplaced(base.id, base.copy(rules = immutableListOf(
            ItemRule.SkillPower(null, 0.2f), ItemRule.SkillAttunement(ElementPick.DOMINANT),
        )))))
    }

    private fun riskItem(
        stats: ImmutableList<StatDelta> = immutableListOf(),
        rules: ImmutableList<ItemRule> = immutableListOf(),
    ) = ItemDefinition(
        id = 0, name = "Risk", description = "Exercises the liability rule.", rarity = ItemRarity.EPIC,
        tags = immutableListOf(BuildTag.ARSENAL), stats = stats, rules = rules, maxStacks = 1, unlockLevel = 1, risk = true,
    )

    private fun cutter(id: Int, element: StatusElement, reduction: Int) = ItemDefinition(
        id = id, name = "Cutter $id", description = "Lowers a threshold for the floor test.", rarity = ItemRarity.RARE,
        tags = immutableListOf(), elements = immutableListOf(element),
        rules = immutableListOf(ItemRule.BreakThreshold(element, reduction)), maxStacks = 2, unlockLevel = 1,
    )

    private fun assertRejected(data: ContentBootstrapData, expectedMessage: String) {
        val failure = assertFailsWith<IllegalArgumentException> {
            createContentCatalogForTesting(data)
        }
        assertTrue(
            failure.message.orEmpty().contains(expectedMessage),
            "Expected '$expectedMessage' in '${failure.message}'",
        )
    }
}

/** Pads the catalog with distinct copies of its last item so only the size changes. */
private fun List<ItemDefinition>.paddedTo(size: Int): List<ItemDefinition> =
    this + (this.size until size).map { id ->
        last().copy(id = id, name = "Padding $id", description = "Bound padding $id.")
    }

private fun <Value> List<Value>.swappedHead(): ImmutableList<Value> =
    (listOf(this[1], this[0]) + drop(2)).toImmutableList()

private fun <Value> List<Value>.replaceLast(replacement: Value): List<Value> =
    dropLast(1) + replacement

private fun <Value> List<Value>.withReplaced(index: Int, replacement: Value): ImmutableList<Value> =
    mapIndexed { i, value -> if (i == index) replacement else value }.toImmutableList()

private fun WeaponDefinition.copied(
    id: WeaponId = this.id,
    name: String = this.name,
    key: RelicKey = this.key,
    upgradeBaseCost: Int = this.upgradeBaseCost,
): WeaponDefinition = WeaponDefinition(
    id = id,
    name = name,
    description = description,
    family = family,
    element = element,
    tags = tags,
    key = key,
    signature = signature,
    scaling = scaling,
    cadence = cadence,
    permanentUnlockCost = permanentUnlockCost,
    minimumRebirth = minimumRebirth,
    masteryForms = masteryForms,
    signatureText = signatureText,
    upgradeBaseCost = upgradeBaseCost,
)
