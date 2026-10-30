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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The dimension policy and the boons: shipped values, every knob's last accepted and first rejected value. */
class DimensionContentTest {
    private val policy = DimensionPolicy()

    @Test
    fun theShippedLayersCarryTheirVisitsObjectivesPhysicsAndRewards() {
        val zenith = policy.definition(WorldLayer.ZENITH)
        val nadir = policy.definition(WorldLayer.NADIR)
        val archive = policy.definition(WorldLayer.ARCHIVE)
        assertEquals(listOf(75f, 60f, 100f), listOf(zenith, nadir, archive).map { it.visitSeconds })
        assertEquals(listOf(DimensionObjective.ASCENT, DimensionObjective.JUDGEMENT), zenith.objectives.map { it.objective })
        assertEquals(listOf(DimensionObjective.HARVEST, DimensionObjective.PYRE), nadir.objectives.map { it.objective })
        assertEquals(listOf(DimensionObjective.ANTECEDENT), archive.objectives.map { it.objective })
        assertEquals(DimensionReward(DimensionRewardKind.BLESSING_OFFER, cardsOnSuccess = 3, cardsOnFailure = 2), zenith.reward)
        assertEquals(DimensionReward(DimensionRewardKind.PACT_OFFER, cardsOnSuccess = 3, cardsOnFailure = 2, declineMatter = 10), nadir.reward)
        assertEquals(
            DimensionReward(DimensionRewardKind.ARCHIVE_TROVE, cardsOnSuccess = 3, matterOnSuccess = 120f, matterOnFailure = 60f),
            archive.reward,
        )
        assertEquals(0.82f, zenith.physics.dragMultiplier)
        assertEquals(6, zenith.physics.ambientHalos)
        assertEquals(0.035f, nadir.physics.scorchSharePerSecond)
        assertEquals(2f, nadir.physics.matterMultiplier)
        assertEquals(0.25f, archive.physics.desyncMinimumScale)
        assertEquals(listOf(0.9f, 1.2f, 0.85f), listOf(zenith, nadir, archive).map { it.spawnRateMultiplier })
        assertEquals(listOf(0.9f, 1.15f, 0.8f), listOf(zenith, nadir, archive).map { it.ordinaryCapMultiplier })
        assertEquals(listOf(0.16f, 0.12f, 1f), listOf(zenith, nadir, archive).map { it.bossIntegrityShare })
        assertEquals(listOf(1f, 0.6f, 1f), listOf(zenith, nadir, archive).map { it.bossDamageShare })
        // The Ascent's split and the Harvest's thrown embers; the Harvest's instruction names its goal.
        val objectives = policy.objectives
        assertEquals(10, objectives.ascentRings)
        assertEquals(4.25f, objectives.ascentSplitFactor)
        assertEquals(20, objectives.harvestEmbers)
        assertTrue(DimensionObjective.HARVEST.instruction.startsWith("Collect twenty embers"))
        assertEquals(1.25f, objectives.emberLifeSeconds)
        assertEquals(140f to 320f, objectives.emberThrowMinimum to objectives.emberThrowMaximum)
        // Only the duels name a boss, and each boss is the one the instruction names.
        assertEquals(
            mapOf(
                DimensionObjective.JUDGEMENT to EnemyType.THRONE,
                DimensionObjective.PYRE to EnemyType.PYRE_HERALD,
                DimensionObjective.ANTECEDENT to EnemyType.ANTECEDENT,
            ),
            DimensionObjective.entries.mapNotNull { objective -> objective.boss?.let { objective to it } }.toMap(),
        )
    }

    @Test
    fun dimensionPolicyRequiresOneDefinitionPerLayerInOrder() {
        DimensionPolicy(definitions = policy.definitions)
        assertEquals(WorldLayer.entries.size, policy.definitions.size)
        assertFailsWith<IllegalArgumentException> { DimensionPolicy(definitions = policy.definitions.dropLast(1).list()) }
        assertFailsWith<IllegalArgumentException> { DimensionPolicy(definitions = (policy.definitions + policy.definitions.last()).list()) }
        val swapped = policy.definitions.toMutableList().apply { add(1, removeAt(2)) }
        assertFailsWith<IllegalArgumentException> { DimensionPolicy(definitions = swapped.list()) }
    }

    @Test
    fun realspaceHasNoVisitObjectivePhysicsDirectorChangeOrReward() {
        val base = policy.definition(WorldLayer.BASE)
        assertEquals(LayerFeatures.REALSPACE, base.features)
        listOf(
            base.copy(visitSeconds = 30f),
            base.copy(objectives = immutableListOf(DimensionObjectiveWeight(DimensionObjective.ASCENT, 1f))),
            base.copy(features = LayerFeatures.DIMENSION),
            base.copy(physics = DimensionPhysics(matterMultiplier = 2f)),
            base.copy(reward = DimensionReward(DimensionRewardKind.NONE, matterOnFailure = 5f)),
            base.copy(spawnRateMultiplier = 1.1f),
            base.copy(ordinaryCapMultiplier = 0.9f),
        ).forEach { realspace -> assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.BASE, realspace) } }
    }

    @Test
    fun visitsAcceptTheirBoundsAndRejectTheNextValue() {
        val zenith = policy.definition(WorldLayer.ZENITH)
        replacing(WorldLayer.ZENITH, zenith.copy(visitSeconds = 120f))
        assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.ZENITH, zenith.copy(visitSeconds = 121f)) }
        replacing(WorldLayer.ZENITH, zenith.copy(visitSeconds = policy.minimumVisitSeconds))
        assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.ZENITH, zenith.copy(visitSeconds = policy.minimumVisitSeconds - 1f)) }
        DimensionPolicy(minimumVisitSeconds = 10f)
        assertFailsWith<IllegalArgumentException> { DimensionPolicy(minimumVisitSeconds = 9.9f) }
        // The minimum may not exceed the shortest shipped visit (the Nadir's 60 s).
        val shortest = policy.definitions.filter { it.layer != WorldLayer.BASE }.minOf { it.visitSeconds }
        assertEquals(60f, shortest)
        DimensionPolicy(minimumVisitSeconds = shortest)
        assertFailsWith<IllegalArgumentException> { DimensionPolicy(minimumVisitSeconds = shortest + 1f) }
    }

    @Test
    fun objectivesStayInTheirLayerWithPositiveWeights() {
        val nadir = policy.definition(WorldLayer.NADIR)
        replacing(WorldLayer.NADIR, nadir.copy(objectives = immutableListOf(DimensionObjectiveWeight(DimensionObjective.PYRE, 0.01f))))
        assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.NADIR, nadir.copy(objectives = immutableListOf())) }
        assertFailsWith<IllegalArgumentException> {
            replacing(WorldLayer.NADIR, nadir.copy(objectives = immutableListOf(DimensionObjectiveWeight(DimensionObjective.ANTECEDENT, 1f))))
        }
        assertFailsWith<IllegalArgumentException> { DimensionObjectiveWeight(DimensionObjective.PYRE, 0f) }
        assertFailsWith<IllegalArgumentException> { DimensionObjectiveWeight(DimensionObjective.PYRE, Float.NaN) }
    }

    @Test
    fun physicsAcceptsItsBoundsAndRejectsTheNextValue() {
        val archive = policy.definition(WorldLayer.ARCHIVE)
        fun physics(change: DimensionPhysics.() -> DimensionPhysics) = replacing(WorldLayer.ARCHIVE, archive.copy(physics = archive.physics.change()))
        val accepted = listOf<DimensionPhysics.() -> DimensionPhysics>(
            { copy(dragMultiplier = 1f) }, { copy(dragMultiplier = 0.31f) }, { copy(dragFloor = 0f) },
            { copy(haloRepairShare = 0.2f) }, { copy(haloOverdrive = 50f) }, { copy(ambientHalos = ContentBounds.MAX_HALO_RINGS - 2) },
            { copy(scorchSharePerSecond = 0.1f) }, { copy(scorchGraceSeconds = 0f) }, { copy(scorchTickSeconds = 0.01f) },
            { copy(desyncMinimumScale = 0.1f, desyncBossFloor = 0.1f) }, { copy(desyncMaximumScale = 2f) },
            { copy(desyncBossFloor = 1f) }, { copy(matterMultiplier = 1f) }, { copy(matterMultiplier = 3f) },
        )
        val rejected = listOf<DimensionPhysics.() -> DimensionPhysics>(
            { copy(dragMultiplier = 1.01f) }, { copy(dragMultiplier = 0.3f) }, { copy(dragFloor = -0.01f) },
            { copy(dragFloor = Float.POSITIVE_INFINITY) }, { copy(haloRepairShare = 0.21f) }, { copy(haloOverdrive = 50.1f) },
            { copy(ambientHalos = ContentBounds.MAX_HALO_RINGS - 1) }, { copy(ambientHalos = -1) },
            { copy(scorchSharePerSecond = 0.11f) }, { copy(scorchGraceSeconds = -0.01f) }, { copy(scorchTickSeconds = 0f) },
            { copy(desyncMinimumScale = 0.09f) }, { copy(desyncMaximumScale = 2.01f) }, { copy(desyncMaximumScale = 0.99f) },
            { copy(desyncBossFloor = 0.24f) }, { copy(desyncBossFloor = 1.01f) }, { copy(matterMultiplier = 0.99f) },
            { copy(matterMultiplier = 3.01f) },
        )
        accepted.forEach { physics(it) }
        rejected.forEach { change -> assertFailsWith<IllegalArgumentException> { physics(change) } }
    }

    @Test
    fun rewardsDealAtMostThreeCardsAndOnlyAPactCanBeRefused() {
        val nadir = policy.definition(WorldLayer.NADIR)
        val zenith = policy.definition(WorldLayer.ZENITH)
        replacing(WorldLayer.NADIR, nadir.copy(reward = nadir.reward.copy(cardsOnSuccess = 3, cardsOnFailure = 3)))
        assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.NADIR, nadir.copy(reward = nadir.reward.copy(cardsOnSuccess = 4))) }
        assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.NADIR, nadir.copy(reward = nadir.reward.copy(cardsOnFailure = 4))) }
        assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.NADIR, nadir.copy(reward = nadir.reward.copy(cardsOnFailure = -1))) }
        assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.ZENITH, zenith.copy(reward = zenith.reward.copy(declineMatter = 1))) }
        assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.NADIR, nadir.copy(reward = nadir.reward.copy(declineMatter = -1))) }
        assertFailsWith<IllegalArgumentException> { replacing(WorldLayer.NADIR, nadir.copy(reward = nadir.reward.copy(matterOnSuccess = -1f))) }
        assertFailsWith<IllegalArgumentException> {
            replacing(WorldLayer.NADIR, nadir.copy(reward = nadir.reward.copy(matterOnFailure = Float.POSITIVE_INFINITY)))
        }
    }

    @Test
    fun anObjectiveBossShareAcceptsItsBoundsAndRejectsTheNextValue() {
        val zenith = policy.definition(WorldLayer.ZENITH)
        zenith.copy(bossIntegrityShare = 0.1f)
        zenith.copy(bossIntegrityShare = 1f)
        assertFailsWith<IllegalArgumentException> { zenith.copy(bossIntegrityShare = 0.099f) }
        assertFailsWith<IllegalArgumentException> { zenith.copy(bossIntegrityShare = 1.01f) }
        assertFailsWith<IllegalArgumentException> { zenith.copy(bossIntegrityShare = Float.NaN) }
    }

    @Test
    fun anObjectiveBossDamageShareAcceptsItsBoundsAndRejectsTheNextValue() {
        val nadir = policy.definition(WorldLayer.NADIR)
        nadir.copy(bossDamageShare = 0.25f)
        nadir.copy(bossDamageShare = 1f)
        assertFailsWith<IllegalArgumentException> { nadir.copy(bossDamageShare = 0.249f) }
        assertFailsWith<IllegalArgumentException> { nadir.copy(bossDamageShare = 1.01f) }
        assertFailsWith<IllegalArgumentException> { nadir.copy(bossDamageShare = Float.NaN) }
    }

    @Test
    fun directorMultipliersStayInTheHostileScaleRange() {
        val nadir = policy.definition(WorldLayer.NADIR)
        nadir.copy(spawnRateMultiplier = 1.5f, ordinaryCapMultiplier = 0.5f)
        assertFailsWith<IllegalArgumentException> { nadir.copy(spawnRateMultiplier = 1.51f) }
        assertFailsWith<IllegalArgumentException> { nadir.copy(spawnRateMultiplier = 0.49f) }
        assertFailsWith<IllegalArgumentException> { nadir.copy(ordinaryCapMultiplier = 1.51f) }
        assertFailsWith<IllegalArgumentException> { nadir.copy(ordinaryCapMultiplier = Float.NaN) }
    }

    @Test
    fun portalKnobsAcceptTheirBoundsAndRejectTheNextValue() {
        val accepted = listOf(
            { DimensionPolicy(portalWindowChance = 0f) }, { DimensionPolicy(portalWindowChance = 1f) },
            { DimensionPolicy(firstPortalWindow = 0) }, { DimensionPolicy(firstPortalWindow = 5) },
            { DimensionPolicy(maxPortalOffersPerRun = 0) }, { DimensionPolicy(maxPortalOffersPerRun = ContentBounds.MAX_PORTAL_OFFERS) },
            { DimensionPolicy(exitMarginSeconds = 30f) }, { DimensionPolicy(exitMarginSeconds = 120f) },
            { DimensionPolicy(arrivalClearRadius = 300f) }, { DimensionPolicy(arrivalClearRadius = 1_500f) },
            { DimensionPolicy(glitchPortalLifetimeSeconds = 10f) }, { DimensionPolicy(glitchPortalLifetimeSeconds = 40f) },
            { DimensionPolicy(paceReach = 0.2f) }, { DimensionPolicy(paceReach = 0.8f) },
            { DimensionPolicy(paceCeiling = 300f) }, { DimensionPolicy(paceCeiling = 2_000f) },
        )
        val rejected = listOf(
            { DimensionPolicy(portalWindowChance = -0.01f) }, { DimensionPolicy(portalWindowChance = 1.01f) },
            { DimensionPolicy(firstPortalWindow = -1) }, { DimensionPolicy(firstPortalWindow = 6) },
            { DimensionPolicy(maxPortalOffersPerRun = -1) }, { DimensionPolicy(maxPortalOffersPerRun = ContentBounds.MAX_PORTAL_OFFERS + 1) },
            { DimensionPolicy(exitMarginSeconds = 29.9f) }, { DimensionPolicy(exitMarginSeconds = 120.1f) },
            { DimensionPolicy(arrivalClearRadius = 299f) }, { DimensionPolicy(arrivalClearRadius = 1_501f) },
            { DimensionPolicy(glitchPortalLifetimeSeconds = 9.9f) }, { DimensionPolicy(glitchPortalLifetimeSeconds = 40.1f) },
            { DimensionPolicy(paceReach = 0.19f) }, { DimensionPolicy(paceReach = 0.81f) },
            { DimensionPolicy(paceCeiling = 299f) }, { DimensionPolicy(paceCeiling = 2_001f) },
            { DimensionPolicy(portalRadius = 0f) }, { DimensionPolicy(exitPortalDistance = Float.POSITIVE_INFINITY) },
            { DimensionPolicy(glitchPortalDistance = Float.NaN) },
        )
        accepted.forEach { it() }
        rejected.forEach { build -> assertFailsWith<IllegalArgumentException> { build() } }
    }

    @Test
    fun objectiveTuningAcceptsItsBoundsAndRejectsTheNextValue() {
        val accepted = listOf(
            { DimensionObjectiveTuning(ascentRings = 2) }, { DimensionObjectiveTuning(ascentRings = 16) },
            { DimensionObjectiveTuning(harvestEmbers = 1) }, { DimensionObjectiveTuning(harvestEmbers = ContentBounds.MAX_EMBERS) },
            { DimensionObjectiveTuning(ascentMinimumSpacing = 900f) },
            { DimensionObjectiveTuning(ascentMinimumTurnDegrees = 0f, ascentMaximumTurnDegrees = 180f) },
            { DimensionObjectiveTuning(bossDelaySeconds = 0f, antecedentDelaySeconds = 0f) },
            { DimensionObjectiveTuning(ascentSplitFactor = 1f) }, { DimensionObjectiveTuning(ascentSplitFactor = 10f) },
            { DimensionObjectiveTuning(emberThrowMinimum = 0f, emberThrowMaximum = 0f) },
            { DimensionObjectiveTuning(emberThrowMinimum = 600f, emberThrowMaximum = 600f) },
        )
        val rejected = listOf(
            { DimensionObjectiveTuning(ascentRings = 1) }, { DimensionObjectiveTuning(ascentRings = 17) },
            { DimensionObjectiveTuning(harvestEmbers = 0) }, { DimensionObjectiveTuning(harvestEmbers = ContentBounds.MAX_EMBERS + 1) },
            { DimensionObjectiveTuning(ascentMinimumSpacing = 901f) }, { DimensionObjectiveTuning(ascentMinimumTurnDegrees = 71f) },
            { DimensionObjectiveTuning(ascentMinimumTurnDegrees = -1f) }, { DimensionObjectiveTuning(ascentMaximumTurnDegrees = 181f) },
            { DimensionObjectiveTuning(haloRadius = 0f) }, { DimensionObjectiveTuning(emberLifeSeconds = Float.POSITIVE_INFINITY) },
            { DimensionObjectiveTuning(bossDelaySeconds = -0.01f) }, { DimensionObjectiveTuning(antecedentDelaySeconds = Float.NaN) },
            { DimensionObjectiveTuning(ascentSplitFactor = 0.99f) }, { DimensionObjectiveTuning(ascentSplitFactor = 10.01f) },
            { DimensionObjectiveTuning(ascentSplitFactor = Float.NaN) },
            { DimensionObjectiveTuning(emberThrowMinimum = -0.01f) }, { DimensionObjectiveTuning(emberThrowMaximum = 600.1f) },
            { DimensionObjectiveTuning(emberThrowMinimum = 300f, emberThrowMaximum = 299f) },
            { DimensionObjectiveTuning(emberThrowMaximum = Float.NaN) },
        )
        accepted.forEach { it() }
        rejected.forEach { build -> assertFailsWith<IllegalArgumentException> { build() } }
    }

    @Test
    fun aRiftWindowMustCloseItsLongestVisitAndTheExitMarginBeforeTheBoss() {
        // Shipped: windows open every 120 s from 45 s for 40 s; the boss arrives at 720 s; a window visit lasts 75 s.
        assertEquals(75f, policy.longestWindowVisitSeconds)
        val eligible = (0..5).filter { policy.windowMayHostRift(it, 45f + it * 120f, offerLifetime = 40f, bossAtSeconds = 720f) }
        assertEquals(listOf(1, 2, 3, 4), eligible)
        // A window closing exactly at boss - visit - margin still qualifies; a moment later does not.
        assertTrue(policy.windowMayHostRift(1, scheduledAt = 545f, offerLifetime = 40f, bossAtSeconds = 720f))
        assertFalse(policy.windowMayHostRift(1, scheduledAt = 545.1f, offerLifetime = 40f, bossAtSeconds = 720f))
        // A longer Nadir visit moves the cut-off.
        val longNadir = replacing(WorldLayer.NADIR, policy.definition(WorldLayer.NADIR).copy(visitSeconds = 120f))
        assertEquals(120f, longNadir.longestWindowVisitSeconds)
        assertFalse(longNadir.windowMayHostRift(4, scheduledAt = 525f, offerLifetime = 40f, bossAtSeconds = 720f))
        // The glitch rift's latest appearance leaves the Archive's 100 s visit, its 30 s offer and the margin.
        assertEquals(530f, policy.latestGlitchRiftAt(720f))
    }

    @Test
    fun boonsGainStatIncreasesAndAPactPaysWithAStatDecreaseOrATougherEnemy() {
        val boons = defaultRunBoons()
        assertEquals(26, boons.size)
        assertEquals(20, boons.count { it.kind != RunBoonKind.ASPECT }, "dimension offer pools are unchanged")
        assertTrue(boons.all { boon -> boon.gains.all { it is RunModifierEffect.Stat && it.amount > 0f } })
        assertEquals(
            listOf(
                RunModifierEffect.HostileScale(health = 1.12f), RunModifierEffect.HostileScale(incomingDamage = 1.15f),
                RunModifierEffect.HostileScale(speed = 1.08f), RunModifierEffect.HostileScale(incomingDamage = 1.08f),
            ),
            boons.flatMap { it.costs }.filterIsInstance<RunModifierEffect.HostileScale>(),
        )
        assertEquals(listOf("Halo of Mending", "Cinder Pact"), listOf(boons.first().name, boons.first { it.id == 48 }.name))
        assertNull(boons.firstOrNull { it.id == 42 })

        val gain = RunModifierEffect.Stat(ItemEffect.MASS, 0.1f)
        fun blessing(vararg gains: RunModifierEffect) = RunBoonDefinition(32, RunBoonKind.BLESSING, "Probe", "A boon.", gains.toList().list())
        fun pact(vararg costs: RunModifierEffect) =
            RunBoonDefinition(48, RunBoonKind.PACT, "Probe", "A boon.", immutableListOf(gain), costs.toList().list())
        blessing(gain)
        pact(RunModifierEffect.Stat(ItemEffect.MASS, -0.1f))
        pact(RunModifierEffect.HostileScale(health = 1.5f, speed = 1.01f, incomingDamage = 1f))
        listOf(
            { blessing(RunModifierEffect.Stat(ItemEffect.MASS, -0.1f)) },
            { blessing(RunModifierEffect.HostileScale(health = 0.9f)) },
            { blessing(RunModifierEffect.Rerolls(1)) },
            { blessing(RunModifierEffect.MatterGainScale(1.2f)) },
            { pact(RunModifierEffect.Stat(ItemEffect.MASS, 0.1f)) },
            { pact(RunModifierEffect.HostileScale()) },
            { pact(RunModifierEffect.HostileScale(health = 1.1f, speed = 0.99f)) },
            { pact(RunModifierEffect.HostileScale(spawnRate = 1.1f)) },
            { pact(RunModifierEffect.HostileScale(health = 1.1f, eliteRate = 1.1f)) },
            { pact(RunModifierEffect.Rerolls(-1)) },
            { pact(RunModifierEffect.MatterGainScale(0.8f)) },
            { pact(RunModifierEffect.Mutator(RebirthMutator.FRACTURED_SPACE)) },
            { pact(RunModifierEffect.KeynoteFloor(0.5f)) },
        ).forEach { build -> assertFailsWith<IllegalArgumentException> { build() } }
    }

    @Test
    fun dimensionNoticesNameEveryLayerObjectiveAndConstantOnce() {
        assertEquals(DimensionNotices.all.size, DimensionNotices.all.toSet().size)
        WorldLayer.entries.forEach { assertEquals(policy.definition(it).name, DimensionNotices.layerName(it)) }
        assertEquals("ZENITH // ASCENT", DimensionNotices.entry(WorldLayer.ZENITH, DimensionObjective.ASCENT))
        assertEquals("PYRE // COMPLETE", DimensionNotices.completed(DimensionObjective.PYRE))
        assertEquals("ASCENT // COURSE BROKEN", DimensionNotices.broken(DimensionObjective.ASCENT))
        assertTrue(DimensionNotices.broken(DimensionObjective.ASCENT) in DimensionNotices.all)
        assertTrue(DimensionObjective.entries.all { DimensionNotices.entry(it.layer, it) in DimensionNotices.all })
        assertTrue(DimensionObjective.entries.all { DimensionNotices.completed(it) in DimensionNotices.all })
    }

    @Test
    fun dungeonAspectsAcceptOnlyTheirIdRangeAndSupportedBoundedRules() {
        val gain = RunModifierEffect.Stat(ItemEffect.MASS, 0.1f)
        fun aspect(id: Int = 64, rules: List<ItemRule>) = RunBoonDefinition(
            id, RunBoonKind.ASPECT, "Probe aspect", "A lasting elemental reward.", immutableListOf(gain),
            buildRules = rules.toImmutableList(),
        )
        val supported = listOf(
            ItemRule.BreakSpread(StatusElement.CRYO, 1, 180f),
            ItemRule.StatusDuration(StatusElement.CRYO, 1.5f),
            ItemRule.BreakThreshold(StatusElement.ENTROPY, 1),
            ItemRule.SkillCooldown(StatusElement.ION, 0.2f),
            ItemRule.CoreLinkRadius(160f),
            ItemRule.ReactionTuning(ReactionId.SHATTERPOINT, ReactionAspect.FORCE, 0.3f),
            ItemRule.FieldTuning(FieldAspect.DURATION, 0.3f),
            ItemRule.ReactionKeystone(ReactionId.FUSION),
        )
        supported.forEach { aspect(rules = listOf(it)) }
        aspect(id = 69, rules = supported.take(3))
        assertFailsWith<IllegalArgumentException> { aspect(id = 63, rules = supported.take(1)) }
        assertFailsWith<IllegalArgumentException> { aspect(id = 70, rules = supported.take(1)) }
        assertFailsWith<IllegalArgumentException> { aspect(rules = emptyList()) }
        assertFailsWith<IllegalArgumentException> { aspect(rules = supported.take(4)) }
        assertFailsWith<IllegalArgumentException> { aspect(rules = listOf(ItemRule.SkillCharges)) }
        assertFailsWith<IllegalArgumentException> {
            RunBoonDefinition(
                32, RunBoonKind.BLESSING, "Probe blessing", "A stat-only reward.", immutableListOf(gain),
                buildRules = immutableListOf(supported.first()),
            )
        }
    }

    private fun replacing(layer: WorldLayer, definition: DimensionDefinition): DimensionPolicy =
        DimensionPolicy(definitions = policy.definitions.map { if (it.layer == layer) definition else it }.list())

    private fun <Value> List<Value>.list(): ImmutableList<Value> = toImmutableList()
}
