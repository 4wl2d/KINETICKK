// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The 0.6.0 world contracts (sites, discoveries, run variety, dimensions, boons, structures): each policy accepts
 * its bounds and rejects the first value past them, and the shipped values keep every new system out of the run until
 * its lane turns it on.
 */
class WorldContractBoundsTest {
    @Test
    fun onlyTheThreeAnomaliesTakeWindowWeightAndTheyLeadTheSiteKinds() {
        val sites = SitePolicy()
        assertEquals(SiteKind.entries, sites.definitions.map { it.kind })
        assertEquals(3, ANOMALY_KIND_COUNT)
        assertEquals(
            listOf(SiteKind.RESONANT_CIRCUIT, SiteKind.SEALED_ANOMALY, SiteKind.COLLAPSING_ORBIT),
            SiteKind.entries.take(ANOMALY_KIND_COUNT),
        )
        assertTrue(SiteKind.entries.take(ANOMALY_KIND_COUNT).all { it.family == SiteFamily.ANOMALY })
        assertTrue(SiteKind.entries.drop(ANOMALY_KIND_COUNT).none { it.family == SiteFamily.ANOMALY })
        assertEquals(SiteKind.entries.take(ANOMALY_KIND_COUNT), sites.definitions.filter { it.windowWeight > 0f }.map { it.kind })
        assertTrue(sites.definitions.all { it.activationRadius == 65f || it.windowWeight == 0f })
        assertEquals(DirectedReward.WEAPON, sites.definition(SiteKind.RESONANT_CIRCUIT).reward.directed)
        assertEquals(DirectedReward.RELIC, sites.definition(SiteKind.SEALED_ANOMALY).reward.directed)
        assertEquals(DirectedReward.ITEM_AND_REPAIR, sites.definition(SiteKind.COLLAPSING_ORBIT).reward.directed)
        // The 0.5.0 window schedule is unchanged.
        assertEquals(SiteWindowPolicy(45f, 120f, 645f, 40f, 25f, 8f), sites.windows)
        assertEquals(EnemyType.WARDEN_PRIME, sites.bounty.bossType)
        assertEquals(SiteReward(itemOfferFloor = ItemRarity.EPIC, matter = 60), sites.secrets.secretReward)
    }

    @Test
    fun sitePoliciesAcceptTheirBoundsAndRejectTheNextValue() {
        SitePolicy(maxPendingDirectedRewards = ContentBounds.MAX_DIRECTED_REWARDS)
        SitePolicy(maxPendingDirectedRewards = 6)
        assertFailsWith<IllegalArgumentException> { SitePolicy(maxPendingDirectedRewards = ContentBounds.MAX_DIRECTED_REWARDS + 1) }
        assertFailsWith<IllegalArgumentException> { SitePolicy(maxPendingDirectedRewards = 5) }
        SitePolicy(maxPendingFloorOffers = ContentBounds.MAX_FLOOR_OFFERS)
        assertFailsWith<IllegalArgumentException> { SitePolicy(maxPendingFloorOffers = ContentBounds.MAX_FLOOR_OFFERS + 1) }
        assertFailsWith<IllegalArgumentException> { SitePolicy(maxPendingFloorOffers = 0) }
        RacePolicy(maxGates = ContentBounds.MAX_RACE_GATES)
        assertFailsWith<IllegalArgumentException> { RacePolicy(maxGates = ContentBounds.MAX_RACE_GATES + 1) }
        assertFailsWith<IllegalArgumentException> { RacePolicy(minGates = 4) }
        ChallengePolicy(ramFoes = ContentBounds.MAX_SITE_FOES)
        assertFailsWith<IllegalArgumentException> { ChallengePolicy(ramFoes = ContentBounds.MAX_SITE_FOES + 1) }
        assertFailsWith<IllegalArgumentException> { ChallengePolicy(ramFoes = 5, ramRequired = 6) }
        SecretPolicy(spinBuckets = ContentBounds.MAX_SPIN_BUCKETS)
        assertFailsWith<IllegalArgumentException> { SecretPolicy(spinBuckets = ContentBounds.MAX_SPIN_BUCKETS + 1) }
        assertFailsWith<IllegalArgumentException> { SiteReward(matter = 121) }
        assertFailsWith<IllegalArgumentException> { SiteReward(relics = 2) }
        // Only window families take window weight; only untimed families last 0 s.
        assertFailsWith<IllegalArgumentException> { SiteDefinition(SiteKind.DRIFT_CACHE, "Cache", "Open.", SiteReward.NONE, windowWeight = 1f, duration = 0f) }
        assertFailsWith<IllegalArgumentException> { SiteDefinition(SiteKind.GATE_RACE, "Race", "Fly.", SiteReward.NONE, duration = 0f) }
        // Every window must still be able to draw a pair.
        val oneAnomaly = SitePolicy().definitions.map { if (it.kind == SiteKind.SEALED_ANOMALY || it.kind == SiteKind.COLLAPSING_ORBIT) it.copy(windowWeight = 0f) else it }
        assertFailsWith<IllegalArgumentException> { SitePolicy(definitions = oneAnomaly.toImmutableList()) }
        assertFailsWith<IllegalArgumentException> { SitePolicy(definitions = SitePolicy().definitions.reversed().toImmutableList()) }
        // A second draw always finds a kind: a zero same-family weight needs unconditional kinds of two families.
        fun weighted(vararg kinds: SiteKind) = SitePolicy().definitions
            .map { it.copy(windowWeight = if (it.kind in kinds) 1f else 0f) }.toImmutableList()
        val anomalies = weighted(SiteKind.RESONANT_CIRCUIT, SiteKind.SEALED_ANOMALY, SiteKind.COLLAPSING_ORBIT)
        SitePolicy(sameFamilyWeight = Float.MIN_VALUE, definitions = anomalies)
        assertFailsWith<IllegalArgumentException> { SitePolicy(sameFamilyWeight = 0f, definitions = anomalies) }
        SitePolicy(sameFamilyWeight = 0f, definitions = weighted(SiteKind.RESONANT_CIRCUIT, SiteKind.GATE_RACE))
        SitePolicy(sameFamilyWeight = 1f, definitions = anomalies)
        assertFailsWith<IllegalArgumentException> { SitePolicy(sameFamilyWeight = 1.0001f, definitions = anomalies) }
    }

    @Test
    fun theAmbientSiteWindowFitsItsBound() {
        val shipped = AmbientSitePolicy()
        assertEquals(38, shipped.windowSize())
        fun rule(kind: SiteKind, cell: Float, enabled: Boolean = true) =
            AmbientSiteRule(kind, enabled, cell, minDistance = 6_000f, fullDistance = 12_000f, maxChance = 0.1f)
        val full = immutableListOf(rule(SiteKind.DRIFT_CACHE, 3_000f), rule(SiteKind.LOST_CURSOR, 3_000f), rule(SiteKind.GOLDEN_DRIFTER, 3_000f))
        assertEquals(ContentBounds.MAX_AMBIENT_SITES, AmbientSitePolicy(rules = full).windowSize())
        AmbientSitePolicy(rules = (full + rule(SiteKind.QUIET_SIGNAL, 8_000f, enabled = false)).toImmutableList())
        assertFailsWith<IllegalArgumentException> { AmbientSitePolicy(rules = (full + rule(SiteKind.QUIET_SIGNAL, 8_000f)).toImmutableList()) }
        assertFailsWith<IllegalArgumentException> { AmbientSitePolicy(rules = immutableListOf(rule(SiteKind.LOST_CURSOR, 3_000f), rule(SiteKind.LOST_CURSOR, 6_000f))) }
        assertFailsWith<IllegalArgumentException> { rule(SiteKind.GATE_RACE, 3_000f) }
        assertFailsWith<IllegalArgumentException> {
            AmbientSiteRule(SiteKind.DRIFT_CACHE, cellSize = 3_000f, minDistance = 5_999f, fullDistance = 12_000f, maxChance = 0.1f)
        }
    }

    @Test
    fun worldDiscoveriesFitTheirPersistedMask() {
        assertEquals(20, WorldDiscovery.entries.size)
        assertTrue(WorldDiscovery.entries.size <= ContentBounds.MAX_WORLD_DISCOVERIES)
        assertEquals((1 shl 20) - 1, WorldDiscovery.VALID_MASK)
        assertEquals(WorldDiscovery.VALID_MASK, WorldDiscovery.entries.fold(0) { mask, discovery -> mask or discovery.bit })
        assertEquals(WorldDiscovery.entries, defaultWorldDiscoveryDefinitions().map { it.id })
        assertEquals(
            listOf(WorldDiscovery.CARTOGRAPHER, WorldDiscovery.ANTECEDENT),
            WorldDiscovery.entries.filter { it.kind == WorldDiscoveryKind.SECRET_BOSS },
        )
        // Discovery names reuse the owners' keys: the secret bosses' enemy names, the layers' names.
        val enemies = defaultEnemyDefinitions().associateBy { it.type }
        val definitions = defaultWorldDiscoveryDefinitions().associateBy { it.id }
        assertEquals(enemies.getValue(EnemyType.CARTOGRAPHER).name, definitions.getValue(WorldDiscovery.CARTOGRAPHER).name)
        assertEquals(enemies.getValue(EnemyType.ANTECEDENT).description, definitions.getValue(WorldDiscovery.ANTECEDENT).description)
        assertEquals(DimensionNotices.layerName(WorldLayer.ZENITH), definitions.getValue(WorldDiscovery.ZENITH).name)
    }

    @Test
    fun runVarietyShipsEveryDefinitionAndItsSwitchesTurnItOff() {
        val variety = RunVarietyContent()
        assertEquals(OmenId.entries, variety.omens.definitions.map { it.id })
        assertTrue(OmenId.entries.size <= ContentBounds.MAX_OMENS)
        assertEquals(DirectorPersonalityId.entries, variety.director.personalities.map { it.id })
        assertTrue(variety.director.personalities.all { it.typeMultipliers.size == EnemyType.entries.size })
        assertEquals(1.8f, variety.director.personalities.first().typeMultipliers[EnemyType.SWARMLING.ordinal])
        assertEquals(1f, variety.director.personalities.first().typeMultipliers[EnemyType.WARDEN.ordinal])
        assertEquals(SignalKind.entries.toSet(), SignalOption.entries.mapNotNull { it.kind }.toSet())
        assertTrue(SignalKind.entries.all { kind -> SignalOption.entries.count { it.kind == kind } == ContentBounds.SIGNAL_OPTIONS })
        assertEquals(RunVarietyNotices.size, RunVarietyNotices.toSet().size)

        val off = RunVarietyContent.DISABLED
        assertFalse(off.omens.enabled || off.director.personalitiesEnabled || off.director.surgeThemesEnabled)
        assertFalse(off.affixes.enabled || off.champions.enabled || off.keynote.enabled || off.worldEvents.enabled || off.signals.enabled)
    }

    @Test
    fun runModifierEffectsAcceptTheirBoundsAndRejectTheNextValue() {
        RunModifierEffect.MatterGainScale(0.25f)
        RunModifierEffect.MatterGainScale(2f)
        assertFailsWith<IllegalArgumentException> { RunModifierEffect.MatterGainScale(0.24f) }
        assertFailsWith<IllegalArgumentException> { RunModifierEffect.MatterGainScale(2.01f) }
        RunModifierEffect.Rerolls(-2)
        RunModifierEffect.Rerolls(3)
        assertFailsWith<IllegalArgumentException> { RunModifierEffect.Rerolls(-3) }
        assertFailsWith<IllegalArgumentException> { RunModifierEffect.Rerolls(4) }
        RunModifierEffect.HostileScale(health = 0.5f, speed = 1.5f)
        assertFailsWith<IllegalArgumentException> { RunModifierEffect.HostileScale(health = 0.49f) }
        assertFailsWith<IllegalArgumentException> { RunModifierEffect.HostileScale(eliteRate = 1.51f) }
        RunModifierEffect.KeynoteFloor(1f)
        assertFailsWith<IllegalArgumentException> { RunModifierEffect.KeynoteFloor(1.01f) }
        assertFailsWith<IllegalArgumentException> { RunModifierEffect.KeynoteFloor(0f) }
        assertFailsWith<IllegalArgumentException> { RunModifierEffect.Stat(ItemEffect.LUCK, 0f) }
    }

    @Test
    fun omensAndPersonalitiesKeepTheirSideAndBiasBounds() {
        val luck = RunModifierEffect.Stat(ItemEffect.LUCK, 0.5f)
        fun omen(gains: List<RunModifierEffect>, costs: List<RunModifierEffect>, liability: String = "A cost.") =
            OmenDefinition(OmenId.GLASS_SUN, "Probe", "A gain.", liability, gains.list(), costs.list())
        omen(List(ContentBounds.MAX_RUN_MODIFIER_EFFECTS) { luck }, listOf(luck))
        assertFailsWith<IllegalArgumentException> { omen(List(ContentBounds.MAX_RUN_MODIFIER_EFFECTS + 1) { luck }, listOf(luck)) }
        assertFailsWith<IllegalArgumentException> { omen(emptyList(), listOf(luck)) }
        assertFailsWith<IllegalArgumentException> { omen(listOf(luck), emptyList()) }
        val blackout = RunModifierEffect.Mutator(RebirthMutator.BLACKOUT)
        omen(listOf(luck), listOf(blackout), liability = RebirthMutator.BLACKOUT.description)
        assertFailsWith<IllegalArgumentException> { omen(listOf(luck), listOf(blackout)) }
        assertFailsWith<IllegalArgumentException> { omen(listOf(blackout), listOf(luck)) }
        OmenPolicy(offers = 3)
        assertFailsWith<IllegalArgumentException> { OmenPolicy(offers = 4) }
        assertFailsWith<IllegalArgumentException> { OmenPolicy(offers = 0) }
        assertFailsWith<IllegalArgumentException> { OmenPolicy(definitions = defaultOmenDefinitions().drop(1).list()) }

        val ordinary = EnemyType.entries.filter { !defaultEnemyDefinitions()[it.ordinal].isScheduled }
        fun personality(biases: Int) = DirectorPersonalityDefinition(
            DirectorPersonalityId.TIDE, "Probe", "A pattern.",
            typeBias = ordinary.take(biases).map { EnemyWeightBias(it, 1.5f) }.list(),
        )
        personality(ContentBounds.MAX_DIRECTOR_TYPE_BIASES)
        assertFailsWith<IllegalArgumentException> { personality(ContentBounds.MAX_DIRECTOR_TYPE_BIASES + 1) }
        DirectorPersonalityDefinition(DirectorPersonalityId.TIDE, "Probe", "A pattern.", budgetScale = 0.85f)
        DirectorPersonalityDefinition(DirectorPersonalityId.TIDE, "Probe", "A pattern.", budgetScale = 1.15f)
        assertFailsWith<IllegalArgumentException> { DirectorPersonalityDefinition(DirectorPersonalityId.TIDE, "Probe", "A pattern.", budgetScale = 1.16f) }
        assertFailsWith<IllegalArgumentException> { EnemyWeightBias(EnemyType.DRIFTER, 2.51f) }
    }

    @Test
    fun worldEventsSignalsAndChampionsKeepTheirRunBounds() {
        WorldEventPolicy(maxPerRun = ContentBounds.MAX_WORLD_EVENTS_PER_RUN)
        assertFailsWith<IllegalArgumentException> { WorldEventPolicy(maxPerRun = ContentBounds.MAX_WORLD_EVENTS_PER_RUN + 1) }
        assertFailsWith<IllegalArgumentException> { WorldEventPolicy(minPerRun = 0) }
        // Three events of 30 s with a 3 s telegraph and a 10 s gap fit 60..660 s; a 190 s event does not.
        assertFailsWith<IllegalArgumentException> { WorldEventPolicy(meteor = MeteorShowerTuning(durationSeconds = 190f)) }
        SignalPolicy(slots = ContentBounds.MAX_SIGNAL_SLOTS)
        assertFailsWith<IllegalArgumentException> { SignalPolicy(slots = ContentBounds.MAX_SIGNAL_SLOTS + 1) }
        assertFailsWith<IllegalArgumentException> { SignalPolicy(slots = 2, perRun = 3) }
        // A champion appears at most one time in ten, at the top tier included.
        ChampionPolicy(baseChance = 0.1f, chancePerTier = 0f)
        assertFailsWith<IllegalArgumentException> { ChampionPolicy(baseChance = 0.1f, chancePerTier = 0.0001f) }
        assertFailsWith<IllegalArgumentException> { ChampionPolicy(baseChance = 0.1001f, chancePerTier = 0f) }
        EliteAffixPolicy(linkCount = 3, splitFragments = 3)
        assertFailsWith<IllegalArgumentException> { EliteAffixPolicy(linkCount = 4) }
        assertFailsWith<IllegalArgumentException> { EliteAffixPolicy(burstRadius = ContentBounds.MAX_EFFECT_RADIUS + 1f) }
    }

    @Test
    fun dimensionsShipRealspaceAsTheBaseLayerAndKeepTheirBounds() {
        val policy = DimensionPolicy()
        assertEquals(WorldLayer.entries, policy.definitions.map { it.layer })
        assertTrue(WorldLayer.entries.size <= 1 shl ContentBounds.OBSTACLE_LAYER_BITS)
        assertEquals(EnemyHabitat.SPACE, WorldLayer.BASE.habitat)
        assertEquals(LayerFeatures.REALSPACE, policy.definition(WorldLayer.BASE).features)
        assertTrue(WorldLayer.entries.drop(1).all { policy.definition(it).features == LayerFeatures.DIMENSION })
        assertTrue(DimensionObjective.entries.all { objective -> policy.definition(objective.layer).objectives.any { it.objective == objective } })

        DimensionPolicy(maxPortalOffersPerRun = ContentBounds.MAX_PORTAL_OFFERS)
        assertFailsWith<IllegalArgumentException> { DimensionPolicy(maxPortalOffersPerRun = ContentBounds.MAX_PORTAL_OFFERS + 1) }
        DimensionObjectiveTuning(harvestEmbers = ContentBounds.MAX_EMBERS)
        assertFailsWith<IllegalArgumentException> { DimensionObjectiveTuning(harvestEmbers = ContentBounds.MAX_EMBERS + 1) }
        val zenith = policy.definition(WorldLayer.ZENITH)
        fun withZenith(definition: DimensionDefinition) =
            DimensionPolicy(definitions = policy.definitions.map { if (it.layer == WorldLayer.ZENITH) definition else it }.list())
        withZenith(zenith.copy(physics = zenith.physics.copy(ambientHalos = ContentBounds.MAX_HALO_RINGS - 2)))
        assertFailsWith<IllegalArgumentException> { withZenith(zenith.copy(physics = zenith.physics.copy(ambientHalos = ContentBounds.MAX_HALO_RINGS - 1))) }
        assertFailsWith<IllegalArgumentException> {
            withZenith(zenith.copy(objectives = immutableListOf(DimensionObjectiveWeight(DimensionObjective.HARVEST, 1f))))
        }
        assertFailsWith<IllegalArgumentException> { withZenith(zenith.copy(reward = zenith.reward.copy(declineMatter = 10))) }
        assertFailsWith<IllegalArgumentException> {
            DimensionPolicy(definitions = policy.definitions.map { if (it.layer == WorldLayer.BASE) it.copy(visitSeconds = 30f) else it }.list())
        }
    }

    @Test
    fun boonsKeepTheirIdRangesAndSides() {
        val boons = defaultRunBoons()
        assertEquals((32..41).toList(), boons.filter { it.kind == RunBoonKind.BLESSING }.map { it.id })
        assertEquals((48..57).toList(), boons.filter { it.kind == RunBoonKind.PACT }.map { it.id })
        assertTrue(boons.size <= ContentBounds.MAX_BOON_DEFINITIONS)
        assertTrue(boons.filter { it.kind == RunBoonKind.BLESSING }.all { it.costs.isEmpty() })
        assertTrue(boons.filter { it.kind == RunBoonKind.PACT }.all { it.costs.isNotEmpty() })

        val gain = RunModifierEffect.Stat(ItemEffect.MASS, 0.1f)
        val cost = RunModifierEffect.Stat(ItemEffect.MASS, -0.1f)
        fun boon(id: Int, kind: RunBoonKind, gains: Int = 1, costs: Int = if (kind == RunBoonKind.PACT) 1 else 0) =
            RunBoonDefinition(id, kind, "Probe", "A boon.", List(gains) { gain }.list(), List(costs) { cost }.list())
        boon(32, RunBoonKind.BLESSING)
        boon(47, RunBoonKind.BLESSING)
        assertFailsWith<IllegalArgumentException> { boon(31, RunBoonKind.BLESSING) }
        assertFailsWith<IllegalArgumentException> { boon(48, RunBoonKind.BLESSING) }
        boon(48, RunBoonKind.PACT)
        boon(63, RunBoonKind.PACT)
        assertFailsWith<IllegalArgumentException> { boon(47, RunBoonKind.PACT) }
        assertFailsWith<IllegalArgumentException> { boon(64, RunBoonKind.PACT) }
        boon(33, RunBoonKind.BLESSING, gains = ContentBounds.MAX_RUN_MODIFIER_EFFECTS)
        assertFailsWith<IllegalArgumentException> { boon(33, RunBoonKind.BLESSING, gains = ContentBounds.MAX_RUN_MODIFIER_EFFECTS + 1) }
        assertFailsWith<IllegalArgumentException> { boon(33, RunBoonKind.BLESSING, gains = 0) }
        assertFailsWith<IllegalArgumentException> { boon(33, RunBoonKind.BLESSING, costs = 1) }
        assertFailsWith<IllegalArgumentException> { boon(49, RunBoonKind.PACT, costs = 0) }
        assertFailsWith<IllegalArgumentException> { boon(49, RunBoonKind.PACT, costs = ContentBounds.MAX_RUN_MODIFIER_EFFECTS + 1) }
    }

    @Test
    fun structuresShipDisabledWithAWallBoundThatCoversTheirWindow() {
        assertFalse(StructurePolicy().enabled)
        assertTrue(StructureTemplateId.entries.size <= ContentBounds.MAX_STRUCTURE_TEMPLATES)
        assertEquals(ContentBounds.MAX_STRUCTURES_IN_WINDOW * ContentBounds.MAX_STRUCTURE_TEMPLATE_WALLS, ContentBounds.MAX_STRUCTURE_WALLS)
    }

    private fun AmbientSitePolicy.windowSize(): Int = rules.filter { it.enabled }.sumOf { rule ->
        val side = kotlin.math.floor(2f * queryHalfSize / rule.cellSize).toInt() + 2
        side * side
    }

    private fun <Value> List<Value>.list(): ImmutableList<Value> = toImmutableList()
}
