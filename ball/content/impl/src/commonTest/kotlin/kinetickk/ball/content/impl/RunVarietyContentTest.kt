// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ChampionPolicy
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DataStormTuning
import kinetickk.ball.content.api.DirectorPersonalityDefinition
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.DirectorPolicy
import kinetickk.ball.content.api.EliteAffixPolicy
import kinetickk.ball.content.api.EnemyHabitat
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.EnemyWeightBias
import kinetickk.ball.content.api.EventKindScales
import kinetickk.ball.content.api.GravityStormTuning
import kinetickk.ball.content.api.IonStormTuning
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.KeynotePolicy
import kinetickk.ball.content.api.MeteorShowerTuning
import kinetickk.ball.content.api.OmenDefinition
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.OmenPolicy
import kinetickk.ball.content.api.PhaseScales
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.ball.content.api.RunTempoProfile
import kinetickk.ball.content.api.RunVarietyContent
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.SignalPolicy
import kinetickk.ball.content.api.SignalTuning
import kinetickk.ball.content.api.SolarFlareTuning
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.WorldEventPolicy
import kinetickk.ball.content.api.defaultOmenDefinitions
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Run-variety content: every policy accepts the last value of each bound and rejects the first one past it, the
 * bootstrap fits the signal and world-event schedules around the site windows and the Architect, and the shipped
 * catalog keeps the properties the run relies on (a full omen offer at every tier, biases on kinds open space spawns).
 */
class RunVarietyContentTest {
    private val data = defaultContentBootstrapData()

    @Test
    fun theShippedCatalogPublishesRunVarietyAndTheDisabledArmBootsToo() {
        assertEquals(RunVarietyContent(), data.variety)
        assertEquals(data.variety, createContentCatalogForTesting(data).gameplayContent().variety)
        val off = data.copy(variety = RunVarietyContent.DISABLED)
        assertEquals(RunVarietyContent.DISABLED, createContentCatalogForTesting(off).gameplayContent().variety)
    }

    @Test
    fun theShippedOmensFillAFullOfferAtEveryRebirthTier() {
        // A tier that already carries an omen's mutator never offers that omen; a fresh rank sees every omen.
        val omens = data.variety.omens
        val eligible = data.rebirthProfiles.map { profile ->
            omens.definitions.count { omen ->
                omen.minimumRank == 0 &&
                    omen.costs.none { it is RunModifierEffect.Mutator && profile.has(it.mutator) }
            }
        }
        assertTrue(eligible.all { it >= omens.offers }, "Eligible omens per tier: $eligible")
        assertEquals(OmenId.entries.size, eligible.first())
        assertEquals(7, eligible.last())
    }

    @Test
    fun theShippedPersonalitiesBiasOnlyKindsThatOpenSpaceSpawns() {
        data.variety.director.personalities.forEach { personality ->
            personality.typeBias.forEach { bias ->
                val enemy = data.enemies.first { it.type == bias.type }
                assertTrue(enemy.livesIn(EnemyHabitat.SPACE) && enemy.weight > 0f, "${personality.id} biases ${bias.type}")
            }
        }
    }

    @Test
    fun runModifierEffectsHoldTheirRangesAtBothEnds() {
        RunModifierEffect.Stat(ItemEffect.LUCK, Float.MIN_VALUE)
        RunModifierEffect.Stat(ItemEffect.MAX_INTEGRITY, -30f)
        listOf(0f, Float.NaN, Float.POSITIVE_INFINITY).forEach { rejects { RunModifierEffect.Stat(ItemEffect.LUCK, it) } }

        floatRange(0.25f, 2f) { RunModifierEffect.MatterGainScale(it) }
        RunModifierEffect.Rerolls(-2)
        RunModifierEffect.Rerolls(3)
        rejects { RunModifierEffect.Rerolls(-3) }
        rejects { RunModifierEffect.Rerolls(4) }
        listOf<(Float) -> Unit>(
            { RunModifierEffect.HostileScale(health = it) },
            { RunModifierEffect.HostileScale(speed = it) },
            { RunModifierEffect.HostileScale(incomingDamage = it) },
            { RunModifierEffect.HostileScale(spawnRate = it) },
            { RunModifierEffect.HostileScale(eliteRate = it) },
        ).forEach { floatRange(0.5f, 1.5f, it) }
        RunModifierEffect.KeynoteFloor(Float.MIN_VALUE)
        RunModifierEffect.KeynoteFloor(1f)
        rejects { RunModifierEffect.KeynoteFloor(0f) }
        rejects { RunModifierEffect.KeynoteFloor(1f.nextAbove()) }
    }

    @Test
    fun omenDefinitionsAndTheOmenPolicyHoldEveryRule() {
        val luck = RunModifierEffect.Stat(ItemEffect.LUCK, 0.5f)
        val blackout = RunModifierEffect.Mutator(RebirthMutator.BLACKOUT)
        val packs = RunModifierEffect.Mutator(RebirthMutator.PACK_HUNTERS)
        fun omen(
            name: String = "Probe",
            upside: String = "A gain.",
            liability: String = "A cost.",
            gains: List<RunModifierEffect> = listOf(luck),
            costs: List<RunModifierEffect> = listOf(luck),
            minimumRank: Int = 0,
            weight: Float = 1f,
        ) = OmenDefinition(OmenId.GLASS_SUN, name, upside, liability, gains.toImmutableList(), costs.toImmutableList(), minimumRank, weight)

        omen()
        rejects { omen(name = " ") }
        rejects { omen(upside = "") }
        rejects { omen(liability = " ") }
        omen(gains = List(ContentBounds.MAX_RUN_MODIFIER_EFFECTS) { luck }, costs = List(ContentBounds.MAX_RUN_MODIFIER_EFFECTS) { luck })
        rejects { omen(gains = emptyList()) }
        rejects { omen(costs = emptyList()) }
        rejects { omen(gains = List(ContentBounds.MAX_RUN_MODIFIER_EFFECTS + 1) { luck }) }
        rejects { omen(costs = List(ContentBounds.MAX_RUN_MODIFIER_EFFECTS + 1) { luck }) }
        // A mutator is a cost, at most one, and the omen's liability reads as that mutator.
        omen(costs = listOf(blackout, luck), liability = RebirthMutator.BLACKOUT.description)
        rejects { omen(costs = listOf(blackout)) }
        rejects { omen(gains = listOf(blackout), liability = RebirthMutator.BLACKOUT.description) }
        rejects { omen(costs = listOf(blackout, packs), liability = RebirthMutator.BLACKOUT.description) }
        omen(minimumRank = 0)
        rejects { omen(minimumRank = -1) }
        omen(weight = Float.MIN_VALUE)
        listOf(0f, Float.POSITIVE_INFINITY, Float.NaN).forEach { rejects { omen(weight = it) } }

        val shipped = defaultOmenDefinitions()
        OmenPolicy(offers = 1)
        OmenPolicy(offers = 3)
        rejects { OmenPolicy(offers = 0) }
        rejects { OmenPolicy(offers = 4) }
        assertTrue(OmenId.entries.size <= ContentBounds.MAX_OMENS)
        rejects { OmenPolicy(definitions = shipped.dropLast(1).toImmutableList()) }
        rejects { OmenPolicy(definitions = (listOf(shipped[1], shipped[0]) + shipped.drop(2)).toImmutableList()) }
        rejects { OmenPolicy(definitions = shipped.mapIndexed { index, it -> if (index == 1) it.copy(name = shipped[0].name) else it }.toImmutableList()) }
    }

    @Test
    fun directorPersonalitiesAndTheDirectorPolicyHoldEveryRule() {
        listOf<(Float) -> Unit>(
            { PhaseScales(calm = it) }, { PhaseScales(build = it) }, { PhaseScales(surge = it) }, { PhaseScales(breather = it) },
        ).forEach { floatRange(0.5f, 2f, it) }
        listOf<(Float) -> Unit>(
            { EventKindScales(single = it) }, { EventKindScales(pack = it) }, { EventKindScales(flank = it) },
            { EventKindScales(swarm = it) }, { EventKindScales(wall = it) }, { EventKindScales(encircle = it) },
        ).forEach { floatRange(0.25f, 2.5f, it) }
        floatRange(0.25f, 2.5f) { EnemyWeightBias(EnemyType.DRIFTER, it) }

        val ordinary = data.enemies.filter { !it.isScheduled }.map { it.type }
        fun personality(
            name: String = "Probe",
            description: String = "A pattern.",
            weight: Float = 1f,
            budgetScale: Float = 1f,
            ambush: Float = 1f,
            encircle: Float = 1f,
            wall: Float = 1f,
            packSizeBonus: Int = 0,
            biases: List<EnemyType> = emptyList(),
            aspectWeight: Float = 1f,
        ) = DirectorPersonalityDefinition(
            DirectorPersonalityId.TIDE, name, description, weight = weight, budgetScale = budgetScale,
            ambushCooldownScale = ambush, encircleCooldownScale = encircle, wallCooldownScale = wall, packSizeBonus = packSizeBonus,
            typeBias = biases.map { EnemyWeightBias(it, 1.5f) }.toImmutableList(), aspectWeight = aspectWeight,
        )

        personality()
        rejects { personality(name = "") }
        rejects { personality(description = " ") }
        personality(weight = Float.MIN_VALUE)
        listOf(0f, Float.POSITIVE_INFINITY).forEach { rejects { personality(weight = it) } }
        floatRange(0.85f, 1.15f) { personality(budgetScale = it) }
        listOf<(Float) -> Unit>({ personality(ambush = it) }, { personality(encircle = it) }, { personality(wall = it) })
            .forEach { floatRange(0.5f, 1.5f, it) }
        personality(packSizeBonus = 0)
        personality(packSizeBonus = 2)
        rejects { personality(packSizeBonus = -1) }
        rejects { personality(packSizeBonus = 3) }
        personality(biases = ordinary.take(ContentBounds.MAX_DIRECTOR_TYPE_BIASES))
        rejects { personality(biases = ordinary.take(ContentBounds.MAX_DIRECTOR_TYPE_BIASES + 1)) }
        rejects { personality(biases = listOf(EnemyType.DRIFTER, EnemyType.DRIFTER)) }
        floatRange(1f, 3f) { personality(aspectWeight = it) }
        val biased = personality(biases = listOf(EnemyType.SNIPER))
        assertEquals(EnemyType.entries.map { if (it == EnemyType.SNIPER) 1.5f else 1f }, biased.typeMultipliers.toList())

        val shipped = DirectorPolicy().personalities
        rejects { DirectorPolicy(personalities = shipped.drop(1).toImmutableList()) }
        rejects { DirectorPolicy(personalities = (listOf(shipped[1], shipped[0]) + shipped.drop(2)).toImmutableList()) }
        floatRange(1f, 4f) { DirectorPolicy(surgeThemeWeight = it) }
        DirectorPolicy(surgeThemeMinimumKinds = 1)
        DirectorPolicy(surgeThemeMinimumKinds = 8)
        rejects { DirectorPolicy(surgeThemeMinimumKinds = 0) }
        rejects { DirectorPolicy(surgeThemeMinimumKinds = 9) }
        DirectorPolicy(announceAtSeconds = 0f, keynoteAnnounceAtSeconds = 0f)
        rejects { DirectorPolicy(announceAtSeconds = 0f.nextBelow()) }
        DirectorPolicy(announceAtSeconds = 3f, keynoteAnnounceAtSeconds = 3f)
        rejects { DirectorPolicy(announceAtSeconds = 3f, keynoteAnnounceAtSeconds = 3f.nextBelow()) }
        rejects { DirectorPolicy(keynoteAnnounceAtSeconds = Float.POSITIVE_INFINITY) }
    }

    @Test
    fun affixChampionAndKeynotePoliciesHoldEveryRule() {
        EliteAffixPolicy(linkCount = 1, splitFragments = 1)
        EliteAffixPolicy(linkCount = 3, splitFragments = 3)
        listOf(0, 4).forEach { rejects { EliteAffixPolicy(linkCount = it) } }
        listOf(0, 4).forEach { rejects { EliteAffixPolicy(splitFragments = it) } }
        EliteAffixPolicy(burstRadius = Float.MIN_VALUE)
        EliteAffixPolicy(burstRadius = ContentBounds.MAX_EFFECT_RADIUS)
        rejects { EliteAffixPolicy(burstRadius = 0f) }
        rejects { EliteAffixPolicy(burstRadius = ContentBounds.MAX_EFFECT_RADIUS.nextAbove()) }
        floatRange(0f, 1f) { EliteAffixPolicy(baseChance = it) }
        floatRange(0f, 1f) { EliteAffixPolicy(chancePerTier = it) }
        listOf<(Float) -> Unit>(
            { EliteAffixPolicy(fromThreatSeconds = it) }, { EliteAffixPolicy(shieldSeconds = it) },
            { EliteAffixPolicy(shieldIntervalSeconds = it) }, { EliteAffixPolicy(firstShieldSeconds = it) },
            { EliteAffixPolicy(wellStrength = it) }, { EliteAffixPolicy(burstDelaySeconds = it) },
        ).forEach(::nonNegative)
        EliteAffixPolicy(hasteSpeed = 1f, hasteCadence = 1f)
        rejects { EliteAffixPolicy(hasteSpeed = 1f.nextBelow()) }
        rejects { EliteAffixPolicy(hasteCadence = 1f.nextBelow()) }

        // chance(top tier) = base + perTier × 10 stays at most one in ten.
        ChampionPolicy(baseChance = 0.1f, chancePerTier = 0f)
        rejects { ChampionPolicy(baseChance = 0.1f.nextAbove(), chancePerTier = 0f) }
        ChampionPolicy(baseChance = 0f, chancePerTier = 0.005f)
        rejects { ChampionPolicy(baseChance = 0.05f, chancePerTier = 0.0051f) }
        rejects { ChampionPolicy(baseChance = 0f.nextBelow()) }
        rejects { ChampionPolicy(baseChance = 0f, chancePerTier = 0f.nextBelow()) }
        nonNegative { ChampionPolicy(fromThreatSeconds = it) }
        ChampionPolicy(integrityScale = 1f, matterScale = 1f, bonusData = 0)
        rejects { ChampionPolicy(integrityScale = 1f.nextBelow()) }
        rejects { ChampionPolicy(matterScale = 1f.nextBelow()) }
        rejects { ChampionPolicy(bonusData = -1) }

        listOf<(Float) -> Unit>(
            { KeynotePolicy(tagShare = it) }, { KeynotePolicy(elementShare = it) },
            { KeynotePolicy(eliteAspectBias = it) }, { KeynotePolicy(stormEliteAspectBias = it) },
        ).forEach { floatRange(0f, 1f, it) }
    }

    @Test
    fun worldEventTuningsAndTheWorldEventPolicyHoldEveryRule() {
        listOf<(Float) -> Unit>(
            { MeteorShowerTuning(durationSeconds = it) }, { MeteorShowerTuning(intervalSeconds = it) },
            { MeteorShowerTuning(armSeconds = it) }, { MeteorShowerTuning(radius = it) },
            { DataStormTuning(durationSeconds = it) }, { DataStormTuning(intervalSeconds = it) },
            { GravityStormTuning(durationSeconds = it, rampSeconds = 0f) },
            { IonStormTuning(durationSeconds = it) }, { IonStormTuning(intervalSeconds = it) },
            { IonStormTuning(armSeconds = it) }, { IonStormTuning(radius = it) },
            { SolarFlareTuning(durationSeconds = it) }, { SolarFlareTuning(pulseSeconds = it) }, { SolarFlareTuning(buffSeconds = it) },
            { SolarFlareTuning(sweepIntervalSeconds = it) }, { SolarFlareTuning(sweepRadius = it) },
        ).forEach(::positive)
        listOf<(Float) -> Unit>(
            { MeteorShowerTuning(minDistance = it) }, { MeteorShowerTuning(distanceSpread = it) },
            { MeteorShowerTuning(angleSpread = it) }, { MeteorShowerTuning(coreDamage = it) },
            { MeteorShowerTuning(enemyDamageScale = it) }, { MeteorShowerTuning(pushSpeed = it) },
            { GravityStormTuning(turnRate = it) }, { GravityStormTuning(coreAcceleration = it) },
            { GravityStormTuning(enemyAcceleration = it) }, { GravityStormTuning(rampSeconds = it) },
            { IonStormTuning(enemyDamageScale = it) }, { IonStormTuning(coreRadius = it) }, { IonStormTuning(coreDamage = it) },
        ).forEach(::nonNegative)
        DataStormTuning(minDistance = 0f, maxDistance = 0f, angleSpread = 0f, killBonusData = 0)
        rejects { DataStormTuning(minDistance = 0f.nextBelow()) }
        rejects { DataStormTuning(minDistance = 300f, maxDistance = 300f.nextBelow()) }
        rejects { DataStormTuning(angleSpread = 0f.nextBelow()) }
        rejects { DataStormTuning(killBonusData = -1) }
        // Both ramps fit inside the storm.
        GravityStormTuning(durationSeconds = 30f, rampSeconds = 15f)
        rejects { GravityStormTuning(durationSeconds = 30f, rampSeconds = 15f.nextAbove()) }
        IonStormTuning(minDistance = 200f, maxDistance = 200f)
        rejects { IonStormTuning(minDistance = 200f, maxDistance = 200f.nextBelow()) }
        rejects { IonStormTuning(minDistance = 0f.nextBelow()) }
        IonStormTuning(ionStacks = 1)
        IonStormTuning(ionStacks = ContentBounds.MAX_STATUS_THRESHOLD)
        rejects { IonStormTuning(ionStacks = 0) }
        rejects { IonStormTuning(ionStacks = ContentBounds.MAX_STATUS_THRESHOLD + 1) }
        floatRange(0f, 1f) { SolarFlareTuning(coolingPenalty = it) }
        floatRange(0f, 1f) { SolarFlareTuning(weaponBonus = it) }
        SolarFlareTuning(sweepStacks = 0)
        SolarFlareTuning(sweepStacks = ContentBounds.MAX_STATUS_THRESHOLD)
        rejects { SolarFlareTuning(sweepStacks = -1) }
        rejects { SolarFlareTuning(sweepStacks = ContentBounds.MAX_STATUS_THRESHOLD + 1) }

        WorldEventPolicy(minPerRun = 1, maxPerRun = 1)
        WorldEventPolicy(minPerRun = 3, maxPerRun = ContentBounds.MAX_WORLD_EVENTS_PER_RUN)
        rejects { WorldEventPolicy(minPerRun = 0) }
        rejects { WorldEventPolicy(minPerRun = 3, maxPerRun = 2) }
        rejects { WorldEventPolicy(maxPerRun = ContentBounds.MAX_WORLD_EVENTS_PER_RUN + 1) }
        assertTrue(ContentBounds.MAX_WORLD_EVENTS_PER_RUN <= WorldEventKind.entries.size)
        floatRange(0f, 1f) { WorldEventPolicy(extraEventChance = it) }
        rejects { WorldEventPolicy(weights = immutableListOf(1f, 1f, 1f, 1f)) }
        rejects { WorldEventPolicy(weights = immutableListOf(1f, 1f, 1f, 1f, 1f, 1f)) }
        rejects { WorldEventPolicy(weights = immutableListOf(1f, 1f, 1f, 1f, 0f.nextBelow())) }
        rejects { WorldEventPolicy(weights = immutableListOf(1f, 1f, 1f, 1f, Float.NaN)) }
        // Without repeats, a run of three events needs three weighted kinds.
        WorldEventPolicy(weights = immutableListOf(0f, 1f, 0f, 1f, 1f))
        rejects { WorldEventPolicy(weights = immutableListOf(0f, 1f, 0f, 0f, 1f)) }
        WorldEventPolicy(minPerRun = 2, maxPerRun = 2, weights = immutableListOf(0f, 1f, 0f, 0f, 1f))
        WorldEventPolicy(telegraphSeconds = 0f, gapSeconds = 0f, firstAtSeconds = 0f)
        rejects { WorldEventPolicy(telegraphSeconds = 0f.nextBelow()) }
        rejects { WorldEventPolicy(gapSeconds = 0f.nextBelow()) }
        rejects { WorldEventPolicy(firstAtSeconds = 0f.nextBelow()) }
        rejects { WorldEventPolicy(firstAtSeconds = 660f, lastEndSeconds = 660f) }
        // (660 − 60) / 3 = 200 s per event: a 3 s telegraph, the longest event and a 10 s gap.
        WorldEventPolicy(meteor = MeteorShowerTuning(durationSeconds = 187f))
        rejects { WorldEventPolicy(meteor = MeteorShowerTuning(durationSeconds = 187f.nextAbove())) }
        rejects { WorldEventPolicy(solar = SolarFlareTuning(durationSeconds = 187f.nextAbove())) }
        assertEquals(187f, WorldEventPolicy(ion = IonStormTuning(durationSeconds = 187f)).longestDuration)
    }

    @Test
    fun signalTuningAndTheSignalPolicyHoldEveryRule() {
        listOf<(Float) -> Unit>(
            { SignalTuning(shrineBloodShare = it) }, { SignalTuning(shrineRelicShare = it) },
            { SignalTuning(gambleRerollChance = it) }, { SignalTuning(gambleMatterShare = it) },
            { SignalTuning(gambleMatterChance = it) }, { SignalTuning(gambleIntegrityShare = it) },
            { SignalTuning(gambleIntegrityChance = it) }, { SignalTuning(crateCurse = it) },
            { SignalTuning(reliquaryCurse = it) }, { SignalTuning(idolCurse = it) },
            { SignalTuning(derelictRepairShare = it) }, { SignalTuning(shrineWardReduction = it) },
        ).forEach { floatRange(0f, 1f, it) }
        listOf<(Float) -> Unit>(
            { SignalTuning(merchantRareCost = it) }, { SignalTuning(merchantEpicCost = it) },
            { SignalTuning(merchantRerollCost = it) }, { SignalTuning(shrineBloodImpact = it) },
            { SignalTuning(shrineBloodWeapon = it) }, { SignalTuning(shrineWardIntegrity = it) },
            { SignalTuning(shrineWardMinimumIntegrity = it) }, { SignalTuning(shrineWardShield = it) },
            { SignalTuning(gambleMatterPayout = it) }, { SignalTuning(crateCurseSeconds = it) },
            { SignalTuning(reliquaryCurseSeconds = it) }, { SignalTuning(idolCurseSeconds = it) },
            { SignalTuning(derelictCargoMatter = it) }, { SignalTuning(priceBase = it) },
        ).forEach(::nonNegative)
        SignalTuning(merchantRerolls = 0, gambleRerollWin = 0, gambleMatterMinimum = 0)
        rejects { SignalTuning(merchantRerolls = -1) }
        rejects { SignalTuning(gambleRerollWin = -1) }
        rejects { SignalTuning(gambleMatterMinimum = -1) }
        positive { SignalTuning(priceRampSeconds = it) }
        SignalTuning(priceStep = 1)
        rejects { SignalTuning(priceStep = 0) }

        SignalPolicy(slots = 1, perRun = 1)
        SignalPolicy(slots = ContentBounds.MAX_SIGNAL_SLOTS, perRun = SignalKind.entries.size)
        rejects { SignalPolicy(perRun = 0) }
        rejects { SignalPolicy(slots = 2, perRun = 3) }
        rejects { SignalPolicy(slots = ContentBounds.MAX_SIGNAL_SLOTS + 1) }
        assertTrue(ContentBounds.MAX_SIGNAL_SLOTS <= SignalKind.entries.size)
        rejects { SignalPolicy(weights = immutableListOf(1f, 1f, 1f, 1f)) }
        rejects { SignalPolicy(weights = immutableListOf(1f, 1f, 1f, 1f, 0f.nextBelow())) }
        // Three distinct signals a run need three weighted kinds.
        SignalPolicy(weights = immutableListOf(1f, 0f, 1f, 0f, 1f))
        rejects { SignalPolicy(weights = immutableListOf(1f, 0f, 0f, 0f, 1f)) }
        SignalPolicy(perRun = 2, weights = immutableListOf(1f, 0f, 0f, 0f, 1f))
        listOf<(Float) -> Unit>(
            { SignalPolicy(firstAtSeconds = it) }, { SignalPolicy(intervalSeconds = it) }, { SignalPolicy(lifetimeSeconds = it) },
            { SignalPolicy(distance = it) }, { SignalPolicy(touchRadius = it) }, { SignalPolicy(lostDistance = it) },
        ).forEach(::positive)
        listOf<(Float) -> Unit>({ SignalPolicy(sideAngle = it) }, { SignalPolicy(trialDelaySeconds = it) }).forEach(::nonNegative)
    }

    @Test
    fun bootstrapFitsSignalsBetweenTheSiteWindowsAndBeforeTheArchitect() {
        // Site windows open at 45 + 120k s for 40 s plus a 25 s trial; the Architect arrives at 720 s.
        fun withSignals(signals: SignalPolicy, tempo: RunTempoProfile = data.tempo, events: Boolean = true) = data.copy(
            tempo = tempo,
            variety = data.variety.copy(
                signals = signals,
                worldEvents = if (events) data.variety.worldEvents else WorldEventPolicy(enabled = false),
            ),
        )
        assertEquals(120f, data.sites.windows.offerInterval)
        rejected(withSignals(SignalPolicy(intervalSeconds = 120f.nextAbove())), "repeat with the site windows")
        rejected(withSignals(SignalPolicy(intervalSeconds = 120f.nextBelow())), "repeat with the site windows")
        accepted(withSignals(SignalPolicy(firstAtSeconds = 110f)))
        rejected(withSignals(SignalPolicy(firstAtSeconds = 110f.nextBelow())), "wait for the first site trial")
        accepted(withSignals(SignalPolicy(lifetimeSeconds = 50f)))
        rejected(withSignals(SignalPolicy(lifetimeSeconds = 50.01f)), "before the next site window")
        // The fifth slot fades at 115 + 4 × 120 + 30 = 625 s.
        accepted(withSignals(SignalPolicy(), RunTempoProfile(bossAtSeconds = 625f.nextAbove()), events = false))
        rejected(withSignals(SignalPolicy(), RunTempoProfile(bossAtSeconds = 625f), events = false), "last signal must fade")
        accepted(withSignals(SignalPolicy(slots = 4, perRun = 3), RunTempoProfile(bossAtSeconds = 505f.nextAbove()), events = false))
        // A switched-off system has no schedule to fit.
        accepted(withSignals(SignalPolicy(enabled = false, intervalSeconds = 121f, firstAtSeconds = 1f)))
    }

    @Test
    fun bootstrapEndsWorldEventsBeforeTheArchitect() {
        fun withEvents(events: WorldEventPolicy, tempo: RunTempoProfile = data.tempo) =
            data.copy(tempo = tempo, variety = data.variety.copy(worldEvents = events))
        accepted(withEvents(WorldEventPolicy(lastEndSeconds = 720f.nextBelow())))
        rejected(withEvents(WorldEventPolicy(lastEndSeconds = 720f)), "end before the Architect")
        accepted(withEvents(WorldEventPolicy(), RunTempoProfile(bossAtSeconds = 660f.nextAbove())))
        rejected(withEvents(WorldEventPolicy(), RunTempoProfile(bossAtSeconds = 660f)), "end before the Architect")
        accepted(withEvents(WorldEventPolicy(enabled = false, lastEndSeconds = 900f)))
    }

    @Test
    fun bootstrapLetsPersonalitiesBiasOrdinaryKindsOnly() {
        fun withSiegeBiases(types: List<EnemyType>) = data.copy(
            variety = data.variety.copy(
                director = data.variety.director.copy(
                    personalities = data.variety.director.personalities.map {
                        if (it.id == DirectorPersonalityId.SIEGE) {
                            it.copy(typeBias = types.map { type -> EnemyWeightBias(type, 1.5f) }.toImmutableList())
                        } else {
                            it
                        }
                    }.toImmutableList(),
                ),
            ),
        )
        val (scheduled, ordinary) = data.enemies.partition { it.isScheduled }
        assertTrue(scheduled.isNotEmpty() && ordinary.isNotEmpty())
        ordinary.map { it.type }.chunked(ContentBounds.MAX_DIRECTOR_TYPE_BIASES).forEach { accepted(withSiegeBiases(it)) }
        scheduled.forEach { rejected(withSiegeBiases(listOf(it.type)), "may bias only ordinary kinds") }
    }

    private fun accepted(candidate: ContentBootstrapData) {
        assertEquals(candidate.variety, createContentCatalogForTesting(candidate).gameplayContent().variety)
    }

    private fun rejected(candidate: ContentBootstrapData, expectedMessage: String) {
        val failure = assertFailsWith<IllegalArgumentException> { createContentCatalogForTesting(candidate) }
        assertTrue(failure.message.orEmpty().contains(expectedMessage), "Expected '$expectedMessage' in '${failure.message}'")
    }

    private fun rejects(block: () -> Unit) {
        assertFailsWith<IllegalArgumentException> { block() }
    }

    /** Accepts both ends of `low..high` and rejects the next representable value past each. */
    private fun floatRange(low: Float, high: Float, build: (Float) -> Unit) {
        build(low)
        build(high)
        rejects { build(low.nextBelow()) }
        rejects { build(high.nextAbove()) }
    }

    /** Accepts zero and rejects the first negative and an infinite value. */
    private fun nonNegative(build: (Float) -> Unit) {
        build(0f)
        rejects { build(0f.nextBelow()) }
        rejects { build(Float.POSITIVE_INFINITY) }
    }

    /** Accepts the smallest positive value and rejects zero and an infinite value. */
    private fun positive(build: (Float) -> Unit) {
        build(Float.MIN_VALUE)
        rejects { build(0f) }
        rejects { build(Float.POSITIVE_INFINITY) }
    }
}
