// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.max

/** Run omens, in catalog order (append-only). */
enum class OmenId {
    GLASS_SUN, IRON_TIDE, HUNGRY_DARK, CROWDED_SKY, QUICKENED_HOST, RICH_VEINS,
    AMPLIFIED_KEYNOTE, VOLATILE_PACT, FAULT_LINE, ASCETIC_PATH, CROWNED_QUARRY, STILL_WATER,
}

/** Director personalities, in catalog order (append-only). */
enum class DirectorPersonalityId { TIDE, HUNTER_CELL, SIEGE, AMBUSHERS, ELEMENTAL_STORM, LONG_CALM }

/** Elite affixes (append-only: the ordinal is carried in the enemy affinity bits). */
enum class EliteAffix(val displayName: String, val signalNotice: String) {
    SHIELDED("Shielded", "ELITE SIGNAL // SHIELDED"),
    SPLITTING("Splitting", "ELITE SIGNAL // SPLITTING"),
    MAGNETIC("Magnetic", "ELITE SIGNAL // MAGNETIC"),
    LINKED("Linked", "ELITE SIGNAL // LINKED"),
    VOLATILE("Volatile", "ELITE SIGNAL // VOLATILE"),
    HASTED("Hasted", "ELITE SIGNAL // HASTED"),
}

/** Ambient world events (append-only). */
enum class WorldEventKind(val displayName: String) {
    METEOR_SHOWER("Meteor shower"),
    DATA_STORM("Data storm"),
    GRAVITY_STORM("Gravity storm"),
    ION_STORM("Ion storm"),
    SOLAR_FLARE("Solar flare"),
}

/** Event signals (append-only). */
enum class SignalKind(val displayName: String) {
    MERCHANT("Merchant"),
    SHRINE("Shrine"),
    GAMBLE("Gamble"),
    CURSED_CACHE("Cursed cache"),
    DERELICT("Derelict"),
}

/** Every prompt card; [kind] null only for [IGNORE]. Three per kind, in card order. */
enum class SignalOption(val kind: SignalKind?) {
    MERCHANT_RARE_OFFER(SignalKind.MERCHANT), MERCHANT_EPIC_OFFER(SignalKind.MERCHANT), MERCHANT_REROLLS(SignalKind.MERCHANT),
    SHRINE_BLOOD(SignalKind.SHRINE), SHRINE_WARD(SignalKind.SHRINE), SHRINE_RELIC(SignalKind.SHRINE),
    GAMBLE_REROLL(SignalKind.GAMBLE), GAMBLE_MATTER(SignalKind.GAMBLE), GAMBLE_INTEGRITY(SignalKind.GAMBLE),
    CACHE_CRATE(SignalKind.CURSED_CACHE), CACHE_RELIQUARY(SignalKind.CURSED_CACHE), CACHE_IDOL(SignalKind.CURSED_CACHE),
    DERELICT_REPAIR(SignalKind.DERELICT), DERELICT_CARGO(SignalKind.DERELICT), DERELICT_VAULT(SignalKind.DERELICT),
    IGNORE(null),
}

/** One run-long change an omen, Blessing or Pact applies; every value is validated at construction. */
sealed interface RunModifierEffect {
    /** One application of an item stat (ItemEffect units). */
    data class Stat(val effect: ItemEffect, val amount: Float) : RunModifierEffect {
        init { require(amount.isFinite() && amount != 0f) { "A run stat change must be finite and non-zero" } }
    }

    /** Multiplies the run's Matter gain after the Rebirth multiplier. */
    data class MatterGainScale(val factor: Float) : RunModifierEffect {
        init { require(factor in 0.25f..2f) { "Matter gain scale must be in 0.25..2" } }
    }

    /** Adds or removes rerolls; never below zero. */
    data class Rerolls(val delta: Int) : RunModifierEffect {
        init { require(delta in -2..3) { "Reroll change must be in -2..3" } }
    }

    /** Multiplies hostile Rebirth-profile scalars for the rest of the run. */
    data class HostileScale(
        val health: Float = 1f,
        val speed: Float = 1f,
        val incomingDamage: Float = 1f,
        val spawnRate: Float = 1f,
        val eliteRate: Float = 1f,
    ) : RunModifierEffect {
        init {
            listOf(health, speed, incomingDamage, spawnRate, eliteRate).forEach {
                require(it in 0.5f..1.5f) { "Hostile scales must be in 0.5..1.5" }
            }
        }
    }

    /** Activates a Rebirth mutator for the rest of the run. */
    data class Mutator(val mutator: RebirthMutator) : RunModifierEffect

    /** Deals reserve passives (at most Rare) until [share] of the Keynote tag's and element's qualifying passives are dealt. */
    data class KeynoteFloor(val share: Float) : RunModifierEffect {
        init { require(share > 0f && share <= 1f) { "Keynote floor share must be in (0, 1]" } }
    }
}

private fun requireRunModifierSide(effects: List<RunModifierEffect>, side: String) {
    require(effects.size in 1..ContentBounds.MAX_RUN_MODIFIER_EFFECTS) {
        "$side must hold 1..${ContentBounds.MAX_RUN_MODIFIER_EFFECTS} effects"
    }
}

data class OmenDefinition(
    val id: OmenId,
    val name: String,
    val upside: String,
    val liability: String,
    val gains: ImmutableList<RunModifierEffect>,
    val costs: ImmutableList<RunModifierEffect>,
    val minimumRank: Int = 0,
    val weight: Float = 1f,
) {
    init {
        require(name.isNotBlank() && upside.isNotBlank() && liability.isNotBlank()) { "Omen texts must not be blank" }
        requireRunModifierSide(gains, "Omen gains")
        requireRunModifierSide(costs, "Omen costs")
        require(gains.none { it is RunModifierEffect.Mutator }) { "A mutator is only ever an omen's cost" }
        val mutators = costs.filterIsInstance<RunModifierEffect.Mutator>()
        require(mutators.size <= 1) { "An omen carries at most one mutator" }
        mutators.firstOrNull()?.let { require(liability == it.mutator.description) { "A mutator omen reads as its mutator" } }
        require(minimumRank >= 0) { "Omen rank must be non-negative" }
        require(weight.isFinite() && weight > 0f) { "Omen weight must be positive" }
    }
}

data class OmenPolicy(
    val enabled: Boolean = true,
    val offers: Int = 3,
    val definitions: ImmutableList<OmenDefinition> = defaultOmenDefinitions(),
) {
    init {
        require(offers in 1..3) { "Omen offers must be in 1..3" }
        require(definitions.size == OmenId.entries.size && OmenId.entries.size <= ContentBounds.MAX_OMENS) {
            "Omens must define every OmenId within ${ContentBounds.MAX_OMENS}"
        }
        require(definitions.withIndex().all { (index, omen) -> omen.id.ordinal == index }) { "Omens must follow the OmenId order" }
        require(definitions.map { it.name }.toSet().size == definitions.size) { "Omen names must be unique" }
    }
}

data class PhaseScales(val calm: Float = 1f, val build: Float = 1f, val surge: Float = 1f, val breather: Float = 1f) {
    init { listOf(calm, build, surge, breather).forEach { require(it in 0.5f..2f) { "Phase scales must be in 0.5..2" } } }
}

data class EventKindScales(
    val single: Float = 1f,
    val pack: Float = 1f,
    val flank: Float = 1f,
    val swarm: Float = 1f,
    val wall: Float = 1f,
    val encircle: Float = 1f,
) {
    init {
        listOf(single, pack, flank, swarm, wall, encircle).forEach { require(it in 0.25f..2.5f) { "Event kind scales must be in 0.25..2.5" } }
    }
}

data class EnemyWeightBias(val type: EnemyType, val multiplier: Float) {
    init { require(multiplier in 0.25f..2.5f) { "Enemy weight bias must be in 0.25..2.5" } }
}

data class DirectorPersonalityDefinition(
    val id: DirectorPersonalityId,
    val name: String,
    val description: String,
    val weight: Float = 1f,
    val budgetScale: Float = 1f,
    val phaseRate: PhaseScales = PhaseScales(),
    val phaseDuration: PhaseScales = PhaseScales(),
    val eventWeights: EventKindScales = EventKindScales(),
    val ambushCooldownScale: Float = 1f,
    val encircleCooldownScale: Float = 1f,
    val wallCooldownScale: Float = 1f,
    val packSizeBonus: Int = 0,
    /** Ordinary kinds only (checked at bootstrap). */
    val typeBias: ImmutableList<EnemyWeightBias> = immutableListOf(),
    val featuresAspect: Boolean = false,
    val aspectWeight: Float = 1f,
) {
    init {
        require(name.isNotBlank() && description.isNotBlank()) { "Personality texts must not be blank" }
        require(weight.isFinite() && weight > 0f) { "Personality weight must be positive" }
        require(budgetScale in 0.85f..1.15f) { "Personality budget scale must be in 0.85..1.15" }
        listOf(ambushCooldownScale, encircleCooldownScale, wallCooldownScale).forEach {
            require(it in 0.5f..1.5f) { "Personality cooldown scales must be in 0.5..1.5" }
        }
        require(packSizeBonus in 0..2) { "Personality pack bonus must be in 0..2" }
        require(typeBias.size <= ContentBounds.MAX_DIRECTOR_TYPE_BIASES) {
            "A personality biases at most ${ContentBounds.MAX_DIRECTOR_TYPE_BIASES} kinds"
        }
        require(typeBias.map { it.type }.toSet().size == typeBias.size) { "A personality biases each kind once" }
        require(aspectWeight in 1f..3f) { "Personality aspect weight must be in 1..3" }
    }

    /** Per-EnemyType multiplier, derived once (allocation-free reads on the spawn path). */
    val typeMultipliers: ImmutableList<Float> =
        List(EnemyType.entries.size) { ordinal -> typeBias.firstOrNull { it.type.ordinal == ordinal }?.multiplier ?: 1f }.toImmutableList()
}

data class DirectorPolicy(
    val personalitiesEnabled: Boolean = true,
    val personalities: ImmutableList<DirectorPersonalityDefinition> = defaultDirectorPersonalities(),
    val surgeThemesEnabled: Boolean = true,
    val surgeThemeWeight: Float = 2.5f,
    val surgeThemeMinimumKinds: Int = 3,
    val announceAtSeconds: Float = 2.4f,
    val keynoteAnnounceAtSeconds: Float = 5.2f,
) {
    init {
        require(personalities.size == DirectorPersonalityId.entries.size &&
            personalities.withIndex().all { (index, personality) -> personality.id.ordinal == index }) {
            "Personalities must follow the DirectorPersonalityId order"
        }
        require(surgeThemeWeight in 1f..4f) { "Surge theme weight must be in 1..4" }
        require(surgeThemeMinimumKinds in 1..8) { "Surge theme minimum kinds must be in 1..8" }
        require(announceAtSeconds.isFinite() && announceAtSeconds >= 0f && keynoteAnnounceAtSeconds.isFinite() &&
            keynoteAnnounceAtSeconds >= announceAtSeconds) { "Announcements must be ordered and non-negative" }
    }
}

data class EliteAffixPolicy(
    val enabled: Boolean = true,
    val fromThreatSeconds: Float = 120f,
    /** chance(t) = min(1, base + perTier × t). */
    val baseChance: Float = 0.5f,
    val chancePerTier: Float = 0.05f,
    val shieldSeconds: Float = 3f,
    val shieldIntervalSeconds: Float = 12f,
    val firstShieldSeconds: Float = 6f,
    val wellStrength: Float = 0.6f,
    val linkCount: Int = 3,
    val splitFragments: Int = 3,
    val burstRadius: Float = 110f,
    val burstDelaySeconds: Float = 0.5f,
    val hasteSpeed: Float = 1.3f,
    val hasteCadence: Float = 1.25f,
) {
    init {
        require(linkCount in 1..3) { "Linked affix count must be in 1..3" }
        require(splitFragments in 1..3) { "Splitting affix fragments must be in 1..3" }
        require(burstRadius > 0f && burstRadius <= ContentBounds.MAX_EFFECT_RADIUS) { "Burst radius must be within the effect bound" }
        require(baseChance in 0f..1f && chancePerTier in 0f..1f) { "Affix chances must be in 0..1" }
        listOf(fromThreatSeconds, shieldSeconds, shieldIntervalSeconds, firstShieldSeconds, wellStrength, burstDelaySeconds).forEach {
            require(it.isFinite() && it >= 0f) { "Affix times and strengths must be finite and non-negative" }
        }
        require(hasteSpeed >= 1f && hasteCadence >= 1f) { "Haste never slows an Elite" }
    }
}

data class ChampionPolicy(
    val enabled: Boolean = true,
    val fromThreatSeconds: Float = 90f,
    val baseChance: Float = 0.04f,
    val chancePerTier: Float = 0.002f,
    val integrityScale: Float = 1.8f,
    val bonusData: Int = 2,
    val matterScale: Float = 2f,
) {
    init {
        require(baseChance >= 0f && chancePerTier >= 0f && baseChance + chancePerTier * ContentBounds.MAX_REBIRTH_LEVEL <= 0.10f) {
            "Champion chance must stay at most 0.10 at the top tier"
        }
        require(fromThreatSeconds.isFinite() && fromThreatSeconds >= 0f) { "Champion start must be non-negative" }
        require(integrityScale >= 1f && matterScale >= 1f && bonusData >= 0) { "Champions are never weaker or poorer" }
    }
}

data class KeynotePolicy(
    val enabled: Boolean = true,
    val tagShare: Float = 0.8f,
    val elementShare: Float = 0.8f,
    val eliteAspectBias: Float = 0.35f,
    val stormEliteAspectBias: Float = 0.5f,
) {
    init {
        listOf(tagShare, elementShare, eliteAspectBias, stormEliteAspectBias).forEach { require(it in 0f..1f) { "Keynote shares must be in 0..1" } }
    }
}

data class MeteorShowerTuning(
    val durationSeconds: Float = 30f,
    val intervalSeconds: Float = 1.1f,
    val armSeconds: Float = 1.4f,
    val minDistance: Float = 220f,
    val distanceSpread: Float = 480f,
    val angleSpread: Float = 2.6f,
    val radius: Float = 95f,
    val coreDamage: Float = 10f,
    /** Enemy damage in units of a Drifter's spawn integrity. */
    val enemyDamageScale: Float = 2.2f,
    val pushSpeed: Float = 260f,
) {
    init {
        listOf(durationSeconds, intervalSeconds, armSeconds, radius).forEach { require(it.isFinite() && it > 0f) { "Meteor shower times and radius must be positive" } }
        listOf(minDistance, distanceSpread, angleSpread, coreDamage, enemyDamageScale, pushSpeed).forEach {
            require(it.isFinite() && it >= 0f) { "Meteor shower values must be finite and non-negative" }
        }
    }
}

data class DataStormTuning(
    val durationSeconds: Float = 30f,
    val intervalSeconds: Float = 0.6f,
    val minDistance: Float = 280f,
    val maxDistance: Float = 620f,
    val angleSpread: Float = 2.4f,
    val killBonusData: Int = 1,
) {
    init {
        require(durationSeconds.isFinite() && durationSeconds > 0f && intervalSeconds.isFinite() && intervalSeconds > 0f) {
            "Data storm times must be positive"
        }
        require(minDistance >= 0f && maxDistance >= minDistance && angleSpread >= 0f && killBonusData >= 0) {
            "Data storm placement must be ordered and non-negative"
        }
    }
}

data class GravityStormTuning(
    val durationSeconds: Float = 30f,
    val turnRate: Float = 0.22f,
    val rampSeconds: Float = 3f,
    val coreAcceleration: Float = 150f,
    val enemyAcceleration: Float = 90f,
) {
    init {
        require(durationSeconds.isFinite() && durationSeconds > 0f) { "Gravity storm duration must be positive" }
        require(rampSeconds >= 0f && rampSeconds * 2f <= durationSeconds) { "Gravity storm ramps must fit its duration" }
        listOf(turnRate, coreAcceleration, enemyAcceleration).forEach { require(it.isFinite() && it >= 0f) { "Gravity storm values must be non-negative" } }
    }
}

data class IonStormTuning(
    val durationSeconds: Float = 30f,
    val intervalSeconds: Float = 2.2f,
    val armSeconds: Float = 1f,
    val minDistance: Float = 160f,
    val maxDistance: Float = 420f,
    val radius: Float = 120f,
    val ionStacks: Int = 3,
    /** Enemy damage in units of a Drifter's spawn integrity. */
    val enemyDamageScale: Float = 1f,
    val coreRadius: Float = 70f,
    val coreDamage: Float = 8f,
) {
    init {
        listOf(durationSeconds, intervalSeconds, armSeconds, radius).forEach { require(it.isFinite() && it > 0f) { "Ion storm times and radius must be positive" } }
        require(minDistance >= 0f && maxDistance >= minDistance) { "Ion storm distances must be ordered" }
        require(ionStacks in 1..ContentBounds.MAX_STATUS_THRESHOLD) { "Ion storm stacks must be in 1..${ContentBounds.MAX_STATUS_THRESHOLD}" }
        listOf(enemyDamageScale, coreRadius, coreDamage).forEach { require(it.isFinite() && it >= 0f) { "Ion storm values must be non-negative" } }
    }
}

data class SolarFlareTuning(
    val durationSeconds: Float = 30f,
    val pulseSeconds: Float = 1f,
    val coolingPenalty: Float = 0.35f,
    val weaponBonus: Float = 0.15f,
    val buffSeconds: Float = 1.2f,
    val sweepIntervalSeconds: Float = 7.5f,
    val sweepStacks: Int = 2,
    val sweepRadius: Float = 1_100f,
) {
    init {
        listOf(durationSeconds, pulseSeconds, buffSeconds, sweepIntervalSeconds, sweepRadius).forEach {
            require(it.isFinite() && it > 0f) { "Solar flare times and radius must be positive" }
        }
        require(coolingPenalty in 0f..1f && weaponBonus in 0f..1f) { "Solar flare buffs must be in 0..1" }
        require(sweepStacks in 0..ContentBounds.MAX_STATUS_THRESHOLD) { "Solar flare stacks must be in 0..${ContentBounds.MAX_STATUS_THRESHOLD}" }
    }
}

data class WorldEventPolicy(
    val enabled: Boolean = true,
    val minPerRun: Int = 2,
    val maxPerRun: Int = 3,
    val extraEventChance: Float = 0.5f,
    val firstAtSeconds: Float = 60f,
    val lastEndSeconds: Float = 660f,
    val telegraphSeconds: Float = 3f,
    val gapSeconds: Float = 10f,
    /** By [WorldEventKind] ordinal. */
    val weights: ImmutableList<Float> = immutableListOf(1f, 1f, 1f, 1f, 1f),
    val meteor: MeteorShowerTuning = MeteorShowerTuning(),
    val dataStorm: DataStormTuning = DataStormTuning(),
    val gravity: GravityStormTuning = GravityStormTuning(),
    val ion: IonStormTuning = IonStormTuning(),
    val solar: SolarFlareTuning = SolarFlareTuning(),
) {
    init {
        require(minPerRun >= 1 && minPerRun <= maxPerRun && maxPerRun <= ContentBounds.MAX_WORLD_EVENTS_PER_RUN) {
            "World events per run must satisfy 1 <= min <= max <= ${ContentBounds.MAX_WORLD_EVENTS_PER_RUN}"
        }
        require(maxPerRun <= WorldEventKind.entries.size) { "A run never repeats a world event kind" }
        require(extraEventChance in 0f..1f) { "Extra event chance must be in 0..1" }
        require(weights.size == WorldEventKind.entries.size && weights.all { it.isFinite() && it >= 0f }) {
            "World event weights must cover every kind and be non-negative"
        }
        require(telegraphSeconds >= 0f && gapSeconds >= 0f && firstAtSeconds >= 0f && lastEndSeconds > firstAtSeconds) {
            "World event window must be ordered"
        }
        require((lastEndSeconds - firstAtSeconds) / maxPerRun >= telegraphSeconds + longestDuration + gapSeconds) {
            "Every world event segment must fit its telegraph, duration and gap"
        }
    }

    /** The longest event duration of the five tunings. */
    val longestDuration: Float
        get() = max(
            max(meteor.durationSeconds, dataStorm.durationSeconds),
            max(max(gravity.durationSeconds, ion.durationSeconds), solar.durationSeconds),
        )
}

data class SignalTuning(
    val merchantRareCost: Float = 60f,
    val merchantEpicCost: Float = 150f,
    val merchantRerollCost: Float = 30f,
    val merchantRerolls: Int = 2,
    val shrineBloodShare: Float = 0.3f,
    val shrineBloodImpact: Float = 0.10f,
    val shrineBloodWeapon: Float = 0.10f,
    val shrineWardIntegrity: Float = 20f,
    val shrineWardMinimumIntegrity: Float = 40f,
    val shrineWardShield: Float = 25f,
    val shrineWardReduction: Float = 0.04f,
    val shrineRelicShare: Float = 0.4f,
    val gambleRerollChance: Float = 0.5f,
    val gambleRerollWin: Int = 3,
    val gambleMatterShare: Float = 0.25f,
    val gambleMatterMinimum: Int = 10,
    val gambleMatterChance: Float = 0.45f,
    val gambleMatterPayout: Float = 2.2f,
    val gambleIntegrityShare: Float = 0.15f,
    val gambleIntegrityChance: Float = 0.5f,
    val crateCurse: Float = 0.25f,
    val crateCurseSeconds: Float = 150f,
    val reliquaryCurse: Float = 0.20f,
    val reliquaryCurseSeconds: Float = 240f,
    val idolCurse: Float = 0.40f,
    val idolCurseSeconds: Float = 150f,
    val derelictRepairShare: Float = 0.40f,
    val derelictCargoMatter: Float = 40f,
) {
    init {
        listOf(
            shrineBloodShare, shrineRelicShare, gambleRerollChance, gambleMatterShare, gambleMatterChance, gambleIntegrityShare,
            gambleIntegrityChance, crateCurse, reliquaryCurse, idolCurse, derelictRepairShare, shrineWardReduction,
        ).forEach { require(it in 0f..1f) { "Signal shares and chances must be in 0..1" } }
        listOf(
            merchantRareCost, merchantEpicCost, merchantRerollCost, shrineBloodImpact, shrineBloodWeapon, shrineWardIntegrity,
            shrineWardMinimumIntegrity, shrineWardShield, gambleMatterPayout, crateCurseSeconds, reliquaryCurseSeconds,
            idolCurseSeconds, derelictCargoMatter,
        ).forEach { require(it.isFinite() && it >= 0f) { "Signal prices and payouts must be finite and non-negative" } }
        require(merchantRerolls >= 0 && gambleRerollWin >= 0 && gambleMatterMinimum >= 0) { "Signal counts must be non-negative" }
    }
}

data class SignalPolicy(
    val enabled: Boolean = true,
    val firstAtSeconds: Float = 115f,
    val intervalSeconds: Float = 120f,
    val slots: Int = 5,
    val perRun: Int = 3,
    val lifetimeSeconds: Float = 30f,
    val distance: Float = 640f,
    val sideAngle: Float = 0.9f,
    val touchRadius: Float = 70f,
    val lostDistance: Float = 2_400f,
    val trialDelaySeconds: Float = 10f,
    /** By [SignalKind] ordinal. */
    val weights: ImmutableList<Float> = immutableListOf(1f, 1f, 1f, 1f, 1f),
    val tuning: SignalTuning = SignalTuning(),
) {
    init {
        require(perRun >= 1 && perRun <= slots && slots <= ContentBounds.MAX_SIGNAL_SLOTS) {
            "Signals must satisfy 1 <= per run <= slots <= ${ContentBounds.MAX_SIGNAL_SLOTS}"
        }
        require(perRun <= SignalKind.entries.size) { "A run never repeats a signal kind" }
        require(weights.size == SignalKind.entries.size && weights.all { it.isFinite() && it >= 0f }) {
            "Signal weights must cover every kind and be non-negative"
        }
        listOf(firstAtSeconds, intervalSeconds, lifetimeSeconds, distance, touchRadius, lostDistance).forEach {
            require(it.isFinite() && it > 0f) { "Signal times and distances must be positive" }
        }
        require(sideAngle.isFinite() && sideAngle >= 0f && trialDelaySeconds.isFinite() && trialDelaySeconds >= 0f) {
            "Signal side angle and trial delay must be non-negative"
        }
    }
}

/** Every run-variety policy: omens, director personalities, elite affixes, champions, Keynote, world events, signals. */
data class RunVarietyContent(
    val omens: OmenPolicy = OmenPolicy(),
    val director: DirectorPolicy = DirectorPolicy(),
    val affixes: EliteAffixPolicy = EliteAffixPolicy(),
    val champions: ChampionPolicy = ChampionPolicy(),
    val keynote: KeynotePolicy = KeynotePolicy(),
    val worldEvents: WorldEventPolicy = WorldEventPolicy(),
    val signals: SignalPolicy = SignalPolicy(),
) {
    companion object {
        /** Every run-variety system switched off (the harness "without run variety" arm). */
        val DISABLED: RunVarietyContent = RunVarietyContent(
            omens = OmenPolicy(enabled = false),
            director = DirectorPolicy(personalitiesEnabled = false, surgeThemesEnabled = false),
            affixes = EliteAffixPolicy(enabled = false),
            champions = ChampionPolicy(enabled = false),
            keynote = KeynotePolicy(enabled = false),
            worldEvents = WorldEventPolicy(enabled = false),
            signals = SignalPolicy(enabled = false),
        )
    }
}

/** The shipped omens in [OmenId] order. */
fun defaultOmenDefinitions(): ImmutableList<OmenDefinition> = listOf(
    OmenDefinition(
        OmenId.GLASS_SUN, "Glass Sun", "+25% impact damage and +20% weapon power.", "−30 maximum integrity.",
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.IMPACT_DAMAGE, 0.25f), RunModifierEffect.Stat(ItemEffect.WEAPON_POWER, 0.20f)),
        costs = immutableListOf(RunModifierEffect.Stat(ItemEffect.MAX_INTEGRITY, -30f)),
    ),
    OmenDefinition(
        OmenId.IRON_TIDE, "Iron Tide", "+30% Matter gain and one more reroll.", "Enemies carry 20% more integrity.",
        gains = immutableListOf(RunModifierEffect.MatterGainScale(1.30f), RunModifierEffect.Rerolls(1)),
        costs = immutableListOf(RunModifierEffect.HostileScale(health = 1.20f)),
    ),
    OmenDefinition(
        OmenId.HUNGRY_DARK, "Hungry Dark", "+30% Data gain.", RebirthMutator.BLACKOUT.description,
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.DATA_GAIN, 0.30f)),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.BLACKOUT)),
    ),
    OmenDefinition(
        OmenId.CROWDED_SKY, "Crowded Sky", "+0.5 luck and one more reroll.", RebirthMutator.PACK_HUNTERS.description,
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.LUCK, 0.5f), RunModifierEffect.Rerolls(1)),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.PACK_HUNTERS)),
    ),
    OmenDefinition(
        OmenId.QUICKENED_HOST, "Quickened Host", "+20% Dash power and +25% cooling.",
        "Enemies move 12% faster and arrive 10% more often.",
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.DASH_POWER, 0.20f), RunModifierEffect.Stat(ItemEffect.COOLING, 0.25f)),
        costs = immutableListOf(RunModifierEffect.HostileScale(speed = 1.12f, spawnRate = 1.10f)),
    ),
    OmenDefinition(
        OmenId.RICH_VEINS, "Rich Veins", "+50% Matter gain.", "Enemy attacks deal 15% more damage.",
        gains = immutableListOf(RunModifierEffect.MatterGainScale(1.50f)),
        costs = immutableListOf(RunModifierEffect.HostileScale(incomingDamage = 1.15f)),
    ),
    OmenDefinition(
        OmenId.AMPLIFIED_KEYNOTE, "Amplified Keynote", "The run's keynote tag and element fill the deck.",
        "One reroll fewer; Elites arrive 15% more often.",
        gains = immutableListOf(RunModifierEffect.KeynoteFloor(0.95f)),
        costs = immutableListOf(RunModifierEffect.Rerolls(-1), RunModifierEffect.HostileScale(eliteRate = 1.15f)),
    ),
    OmenDefinition(
        OmenId.VOLATILE_PACT, "Volatile Pact", "+8% critical chance and +40% critical damage.",
        RebirthMutator.VOLATILE_REMAINS.description,
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.CRIT_CHANCE, 0.08f), RunModifierEffect.Stat(ItemEffect.CRIT_DAMAGE, 0.40f)),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.VOLATILE_REMAINS)),
    ),
    OmenDefinition(
        OmenId.FAULT_LINE, "Fault Line", "+20% mass and +15% impact damage.", RebirthMutator.FRACTURED_SPACE.description,
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.MASS, 0.20f), RunModifierEffect.Stat(ItemEffect.IMPACT_DAMAGE, 0.15f)),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.FRACTURED_SPACE)),
    ),
    OmenDefinition(
        OmenId.ASCETIC_PATH, "Ascetic Path", "Two more rerolls and +1 luck.", "−40% Matter gain.",
        gains = immutableListOf(RunModifierEffect.Rerolls(2), RunModifierEffect.Stat(ItemEffect.LUCK, 1f)),
        costs = immutableListOf(RunModifierEffect.MatterGainScale(0.60f)),
    ),
    OmenDefinition(
        OmenId.CROWNED_QUARRY, "Crowned Quarry", "Elites arrive 30% more often, each with a Key and a Relic.",
        RebirthMutator.HARDENED_ELITES.description,
        gains = immutableListOf(RunModifierEffect.HostileScale(eliteRate = 1.30f)),
        costs = immutableListOf(RunModifierEffect.Mutator(RebirthMutator.HARDENED_ELITES)),
    ),
    OmenDefinition(
        OmenId.STILL_WATER, "Still Water", "+25 shield capacity and +6% damage reduction.",
        "−15% impact damage and −10% weapon power.",
        gains = immutableListOf(RunModifierEffect.Stat(ItemEffect.SHIELD_CAPACITY, 25f), RunModifierEffect.Stat(ItemEffect.DAMAGE_REDUCTION, 0.06f)),
        costs = immutableListOf(RunModifierEffect.Stat(ItemEffect.IMPACT_DAMAGE, -0.15f), RunModifierEffect.Stat(ItemEffect.WEAPON_POWER, -0.10f)),
    ),
).toImmutableList()

/** The shipped director personalities in [DirectorPersonalityId] order. */
fun defaultDirectorPersonalities(): ImmutableList<DirectorPersonalityDefinition> = listOf(
    DirectorPersonalityDefinition(
        DirectorPersonalityId.TIDE, "Tidewave", "Swarms and packs roll in on long surges, then the field goes quiet.",
        phaseDuration = PhaseScales(build = 0.85f, surge = 1.3f, breather = 1.5f),
        eventWeights = EventKindScales(single = 0.5f, pack = 1.4f, flank = 1.0f, swarm = 1.8f, wall = 0.7f, encircle = 1.2f),
        encircleCooldownScale = 0.85f, packSizeBonus = 1,
        typeBias = immutableListOf(
            EnemyWeightBias(EnemyType.SWARMLING, 1.8f), EnemyWeightBias(EnemyType.DRIFTER, 1.3f),
            EnemyWeightBias(EnemyType.BOMBER, 1.3f), EnemyWeightBias(EnemyType.SNIPER, 0.6f),
        ),
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.HUNTER_CELL, "Hunter Cell", "Ranged hunters lead, with walls and flanks to pin you down.",
        budgetScale = 0.97f,
        eventWeights = EventKindScales(single = 0.8f, pack = 0.9f, flank = 1.3f, swarm = 0.7f, wall = 1.5f, encircle = 1.0f),
        wallCooldownScale = 0.8f,
        typeBias = immutableListOf(
            EnemyWeightBias(EnemyType.SHOOTER, 1.6f), EnemyWeightBias(EnemyType.WEAVER, 1.6f), EnemyWeightBias(EnemyType.SNIPER, 1.6f),
            EnemyWeightBias(EnemyType.LANCER, 1.6f), EnemyWeightBias(EnemyType.MINELAYER, 1.6f),
            EnemyWeightBias(EnemyType.CHARGER, 0.75f), EnemyWeightBias(EnemyType.INTERCEPTOR, 0.75f),
        ),
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.SIEGE, "Siege", "Heavy wardens, aegises and reflectors grind forward in long builds.",
        phaseDuration = PhaseScales(build = 1.25f, surge = 0.9f),
        eventWeights = EventKindScales(single = 1.2f, pack = 1.0f, flank = 0.8f, swarm = 0.5f, wall = 1.6f, encircle = 0.6f),
        wallCooldownScale = 0.8f,
        typeBias = immutableListOf(
            EnemyWeightBias(EnemyType.WARDEN, 1.6f), EnemyWeightBias(EnemyType.AEGIS, 1.8f), EnemyWeightBias(EnemyType.REFLECTOR, 1.6f),
            EnemyWeightBias(EnemyType.BROODMOTHER, 1.6f), EnemyWeightBias(EnemyType.SPLITTER, 1.4f),
            EnemyWeightBias(EnemyType.SWARMLING, 0.5f), EnemyWeightBias(EnemyType.DRIFTER, 0.8f),
        ),
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.AMBUSHERS, "Ambushers", "Fast strikers spring ambushes and close in from the flanks.",
        eventWeights = EventKindScales(single = 0.7f, pack = 1.0f, flank = 1.6f, swarm = 1.0f, wall = 0.8f, encircle = 1.5f),
        ambushCooldownScale = 0.6f, encircleCooldownScale = 0.75f,
        typeBias = immutableListOf(
            EnemyWeightBias(EnemyType.INTERCEPTOR, 1.5f), EnemyWeightBias(EnemyType.CHARGER, 1.5f), EnemyWeightBias(EnemyType.PHANTOM, 1.6f),
            EnemyWeightBias(EnemyType.LANCER, 1.2f), EnemyWeightBias(EnemyType.WARDEN, 0.7f),
        ),
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.ELEMENTAL_STORM, "Elemental Storm", "Enemies of one aspect flood the field, and Elites share it.",
        featuresAspect = true, aspectWeight = 2.2f,
    ),
    DirectorPersonalityDefinition(
        DirectorPersonalityId.LONG_CALM, "Long Calm", "Long lulls broken by fierce surges.",
        budgetScale = 0.95f,
        phaseRate = PhaseScales(surge = 1.3f),
        phaseDuration = PhaseScales(calm = 1.6f, breather = 1.6f),
        eventWeights = EventKindScales(single = 1.0f, pack = 1.2f, flank = 1.0f, swarm = 1.2f, wall = 1.0f, encircle = 1.2f),
    ),
).toImmutableList()

/** Every run notice the run-variety systems emit (the uppercase forms come from the uppercase mirror). */
val RunVarietyNotices: List<String> = buildList {
    defaultDirectorPersonalities().forEach { add("HOSTILE PATTERN // ${it.name.uppercase()}") }
    add("KEYNOTE")
    add("ONSLAUGHT")
    defaultOmenDefinitions().forEach { add("OMEN // ${it.name.uppercase()}") }
    EliteAffix.entries.forEach { add(it.signalNotice) }
    WorldEventKind.entries.forEach { add(it.displayName.uppercase()) }
    add("SKIES CLEAR")
    SignalKind.entries.forEach { add("SIGNAL DETECTED // ${it.displayName.uppercase()}") }
    add("SIGNAL LOST")
    addAll(listOf("WAGER WON", "WAGER LOST", "TRADE COMPLETE", "BLESSING RECEIVED", "CURSE BOUND", "SALVAGE COMPLETE"))
}
