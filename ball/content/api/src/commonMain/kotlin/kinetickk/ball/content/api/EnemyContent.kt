// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/**
 * Every hostile kind. Runs and profiles never persist an enemy kind, but the order is the roster order
 * of [defaultEnemyDefinitions] and of the Codex, so entries are append-only. Ordinals 19..34 are new in 0.6.0.
 */
enum class EnemyType {
    DRIFTER,
    SHOOTER,
    CHARGER,
    INTERCEPTOR,
    WEAVER,
    WARDEN,
    SPLITTER,
    ELITE,
    ARCHITECT,
    SWARMLING,
    BOMBER,
    LANCER,
    SNIPER,
    AEGIS,
    MINELAYER,
    PHANTOM,
    BROODMOTHER,
    REFLECTOR,
    HERALD,
    COMET,
    PYLON,
    SENTRY,
    CRAWLER,
    SERAPH,
    CHOIR,
    IMP,
    HOUND,
    CIPHER,
    MIRROR,
    WARDEN_PRIME,
    BROOD_QUEEN,
    THRONE,
    PYRE_HERALD,
    CARTOGRAPHER,
    ANTECEDENT,
}

/** How the run treats a kind. BOSS is the run's final boss (the Architect) and nothing else. */
enum class EnemyRank { ORDINARY, ELITE, MINI_BOSS, BOSS, SECRET_BOSS }

/** Where a kind lives. The ambient director and encounter picks read weights per habitat. */
enum class EnemyHabitat {
    SPACE, STRUCTURE, ZENITH, NADIR, ARCHIVE;

    val bit: Int get() = 1 shl ordinal

    companion object {
        const val ALL: Int = 0b1_1111
    }
}

/** Ordinary spawn weights outside open space ([EnemyDefinition.weight] is the SPACE weight). */
data class HabitatWeights(
    val structure: Float = 0f,
    val zenith: Float = 0f,
    val nadir: Float = 0f,
    val archive: Float = 0f,
) {
    init {
        require(
            structure.isFinite() && structure >= 0f && zenith.isFinite() && zenith >= 0f &&
                nadir.isFinite() && nadir >= 0f && archive.isFinite() && archive >= 0f,
        ) {
            "Habitat weights must be finite and non-negative"
        }
    }

    companion object {
        val NONE: HabitatWeights = HabitatWeights()
    }
}

/** A boss's phase count fits the 2-bit phase field of the enemy affinity bits. */
const val MAX_BOSS_PHASES: Int = 4
const val MAX_BOSS_HIT_CAP_SHARE: Float = 0.1f
const val MAX_BOSS_HIT_CAP_TIER_SHRINK: Float = 0.1f
const val MAX_BOSS_SHIFT_SECONDS: Float = 12f
const val MAX_BOSS_ENRAGE_RATE: Float = 2f

/**
 * The phase gate of a boss-class kind: [phases] equal integrity parts (gates at 1/phases steps), at most
 * [hitCapShare] of the tier-0 integrity per damage instance, shrunk by [hitCapTierShrink] per selected tier but
 * never below [hitCapMinimumScale] of it, [shiftSeconds] without damage or attacks at each gate. [enragePhases]
 * extra phases exist while [enrageMutator] is selected and run [enrageAttackRate] faster; [enrageAfterSeconds] > 0
 * also enrages a boss that long after its encounter started (secret and mini-bosses never stall forever).
 */
data class BossEncounterSpec(
    val phases: Int,
    val hitCapShare: Float,
    val hitCapTierShrink: Float,
    val hitCapMinimumScale: Float,
    val shiftSeconds: Float,
    val enragePhases: Int = 0,
    val enrageMutator: RebirthMutator? = null,
    val enrageAttackRate: Float = 1f,
    val enrageAfterSeconds: Float = 0f,
) {
    init {
        require(phases in 1..MAX_BOSS_PHASES) { "Boss phases must be in 1..$MAX_BOSS_PHASES" }
        require(enragePhases in 0..(MAX_BOSS_PHASES - phases)) { "Enraged phases must fit the phase field" }
        require((enragePhases > 0) == (enrageMutator != null)) { "Enraged phases need their mutator" }
        require(hitCapShare > 0f && hitCapShare <= MAX_BOSS_HIT_CAP_SHARE) { "Hit-cap share must be in (0, 0.1]" }
        require(hitCapTierShrink in 0f..MAX_BOSS_HIT_CAP_TIER_SHRINK) { "Hit-cap tier shrink must be in [0, 0.1]" }
        require(hitCapMinimumScale > 0f && hitCapMinimumScale <= 1f) { "Hit-cap minimum scale must be in (0, 1]" }
        require(shiftSeconds in 0f..MAX_BOSS_SHIFT_SECONDS) { "Shift seconds must be in [0, 12]" }
        require(enrageAttackRate in 1f..MAX_BOSS_ENRAGE_RATE) { "Enrage attack rate must be in [1, 2]" }
        require(enrageAfterSeconds.isFinite() && enrageAfterSeconds >= 0f) { "Enrage time must be non-negative" }
    }
}

/** The Architect's gate; the nucleus Architect constants (hit cap, tier shrink, shift, enrage) must equal it. */
val ARCHITECT_ENCOUNTER: BossEncounterSpec = BossEncounterSpec(
    phases = 3, hitCapShare = 0.015f, hitCapTierShrink = 0.02f, hitCapMinimumScale = 0.5f, shiftSeconds = 8f,
    enragePhases = 1, enrageMutator = RebirthMutator.SINGULARITY, enrageAttackRate = 1.45f,
)

private val MINI_BOSS_ENCOUNTER = BossEncounterSpec(
    phases = 3, hitCapShare = 0.03f, hitCapTierShrink = 0.02f, hitCapMinimumScale = 0.5f, shiftSeconds = 2f,
    enrageAttackRate = 1.3f, enrageAfterSeconds = 75f,
)

/**
 * Roster data of one [EnemyType]: what the Codex shows and when the spawn director may pick it.
 * [timeGateSeconds] is compared with the run's threat time and [minimumRebirth] with the selected
 * Rebirth tier. [weight] is the ordinary weight in open space; [habitatWeights] carry the other habitats
 * and [habitats] names where an ordinary kind lives. Every rank but [EnemyRank.ORDINARY] is scheduled by its
 * owner and never takes ordinary weight. An ordinary kind's weight can fall linearly to [lateWeightFraction]
 * of itself by [lateWeightAtSeconds].
 */
data class EnemyDefinition(
    val type: EnemyType,
    val name: String,
    val description: String,
    val threat: Int,
    val timeGateSeconds: Float,
    val minimumRebirth: Int,
    /** Ordinary weight in open space (SPACE); other habitats in [habitatWeights]. */
    val weight: Float,
    val rank: EnemyRank = EnemyRank.ORDINARY,
    val lateWeightFraction: Float = 1f,
    val lateWeightAtSeconds: Float = 0f,
    /** [EnemyHabitat] bits; ordinary kinds carry weight in exactly these (checked at bootstrap). */
    val habitats: Int = EnemyHabitat.SPACE.bit,
    val habitatWeights: HabitatWeights = HabitatWeights.NONE,
    val encounter: BossEncounterSpec? = null,
    /** The persisted discovery that reveals a secret boss in the Codex. */
    val revealedBy: WorldDiscovery? = null,
    /**
     * How a summoned kind arrives (an English content phrase), stated by the Codex in place of the [timeGateSeconds]
     * arrival time; null for a kind the run brings by its clock.
     */
    val arrival: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Enemy name must not be blank" }
        require(description.isNotBlank()) { "Enemy description must not be blank" }
        require(threat in MIN_ENEMY_THREAT..MAX_ENEMY_THREAT) { "Enemy threat must be in $MIN_ENEMY_THREAT..$MAX_ENEMY_THREAT" }
        require(timeGateSeconds.isFinite() && timeGateSeconds >= 0f) { "Enemy time gate must be finite and non-negative" }
        require(minimumRebirth in ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL) {
            "Enemy minimum Rebirth must be a valid tier"
        }
        require(weight.isFinite() && weight >= 0f) { "Enemy weight must be finite and non-negative" }
        require(lateWeightFraction.isFinite() && lateWeightFraction in 0f..1f) { "Late weight fraction must be in 0..1" }
        require(lateWeightAtSeconds.isFinite() && lateWeightAtSeconds >= 0f) { "Late weight time must be non-negative" }
        require(habitats in 1..EnemyHabitat.ALL) { "Enemy habitats must name at least one habitat" }
        require(rank == EnemyRank.ORDINARY || weight == 0f && habitatWeights == HabitatWeights.NONE) {
            "Scheduled enemies never take ordinary spawn weight"
        }
        when (rank) {
            EnemyRank.ORDINARY, EnemyRank.ELITE -> require(encounter == null) { "Only bosses carry an encounter spec" }
            EnemyRank.BOSS, EnemyRank.SECRET_BOSS -> require(encounter != null) { "Bosses carry an encounter spec" }
            EnemyRank.MINI_BOSS -> Unit
        }
        require((revealedBy != null) == (rank == EnemyRank.SECRET_BOSS)) {
            "Exactly the secret bosses name the discovery that reveals them"
        }
        require(arrival == null || arrival.isNotBlank()) { "An enemy's arrival must not be blank" }
    }

    /** The scheduled Elite. */
    val isElite: Boolean get() = rank == EnemyRank.ELITE

    /** The run's final boss. */
    val isBoss: Boolean get() = rank == EnemyRank.BOSS

    /** A scheduled or owner-spawned mini-boss (the Herald among them). */
    val isMiniBoss: Boolean get() = rank == EnemyRank.MINI_BOSS

    /** A boss that never ends the run, revealed in the Codex by [revealedBy]. */
    val isSecretBoss: Boolean get() = rank == EnemyRank.SECRET_BOSS

    /** True for kinds the director never picks for ordinary events. */
    val isScheduled: Boolean get() = rank != EnemyRank.ORDINARY

    fun livesIn(habitat: EnemyHabitat): Boolean = habitats and habitat.bit != 0

    fun weightIn(habitat: EnemyHabitat): Float = when (habitat) {
        EnemyHabitat.SPACE -> weight
        EnemyHabitat.STRUCTURE -> habitatWeights.structure
        EnemyHabitat.ZENITH -> habitatWeights.zenith
        EnemyHabitat.NADIR -> habitatWeights.nadir
        EnemyHabitat.ARCHIVE -> habitatWeights.archive
    }

    /** Ordinary weight in [habitat] before ramp-in and repetition; the late decay applies in every habitat. */
    fun baseWeight(elapsedSeconds: Float, habitat: EnemyHabitat = EnemyHabitat.SPACE): Float {
        val base = weightIn(habitat)
        if (lateWeightAtSeconds <= 0f || lateWeightFraction >= 1f) return base
        val progress = (elapsedSeconds / lateWeightAtSeconds).coerceIn(0f, 1f)
        return base * (1f - (1f - lateWeightFraction) * progress)
    }
}

/** Run notices the enemy systems emit; ContentLocalizationTest iterates [all]. */
object EnemyNotices {
    const val SECRET_SIGNAL: String = "SECRET SIGNAL"
    const val SECRET_SIGNAL_LOST: String = "SECRET SIGNAL LOST"
    const val SECRET_BOSS_DEFEATED: String = "SECRET BOSS DEFEATED"
    const val CARTOGRAPHER_ENRAGES: String = "THE CARTOGRAPHER ENRAGES"
    const val ANTECEDENT_ENRAGES: String = "THE ANTECEDENT ENRAGES"
    val all: ImmutableList<String> = listOf(
        SECRET_SIGNAL, SECRET_SIGNAL_LOST, SECRET_BOSS_DEFEATED, CARTOGRAPHER_ENRAGES, ANTECEDENT_ENRAGES,
    ).toImmutableList()
}

const val MIN_ENEMY_THREAT: Int = 1
const val MAX_ENEMY_THREAT: Int = 5

/** The shipped roster in [EnemyType] order. */
fun defaultEnemyDefinitions(): ImmutableList<EnemyDefinition> = listOf(
    EnemyDefinition(
        EnemyType.DRIFTER, "Drifter", "Drifts straight at the Core. Weak alone, dangerous in numbers.",
        threat = 1, timeGateSeconds = 0f, minimumRebirth = 0, weight = 22f,
        lateWeightFraction = 0.4f, lateWeightAtSeconds = 360f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.ARCHIVE.bit,
        habitatWeights = HabitatWeights(structure = 10f, archive = 4f),
    ),
    EnemyDefinition(
        EnemyType.SHOOTER, "Shooter", "Holds its distance and fires aimed bolts.",
        threat = 2, timeGateSeconds = 38f, minimumRebirth = 0, weight = 17f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.ZENITH.bit,
        habitatWeights = HabitatWeights(structure = 8f, zenith = 6f),
    ),
    EnemyDefinition(
        EnemyType.CHARGER, "Charger", "Creeps closer, winds up and dashes through the Core.",
        threat = 2, timeGateSeconds = 150f, minimumRebirth = 0, weight = 14f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.NADIR.bit,
        habitatWeights = HabitatWeights(structure = 6f, nadir = 5f),
    ),
    EnemyDefinition(
        EnemyType.INTERCEPTOR, "Interceptor", "Leads your movement and boosts to cut you off.",
        threat = 2, timeGateSeconds = 90f, minimumRebirth = 0, weight = 14f,
    ),
    EnemyDefinition(
        EnemyType.WEAVER, "Weaver", "Weaves from side to side and fires walls of bolts.",
        threat = 2, timeGateSeconds = 150f, minimumRebirth = 0, weight = 12f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.ZENITH.bit,
        habitatWeights = HabitatWeights(structure = 5f, zenith = 5f),
    ),
    EnemyDefinition(
        EnemyType.WARDEN, "Warden", "Keeps its range, pulls the Core into its well and fires radial bursts.",
        threat = 3, timeGateSeconds = 240f, minimumRebirth = 0, weight = 9f,
    ),
    EnemyDefinition(
        EnemyType.SPLITTER, "Splitter", "Sways forward and breaks into two fragments when destroyed.",
        threat = 3, timeGateSeconds = 240f, minimumRebirth = 0, weight = 12f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.NADIR.bit,
        habitatWeights = HabitatWeights(structure = 5f, nadir = 3f),
    ),
    EnemyDefinition(
        EnemyType.ELITE, "Elite", "Carries an elemental aspect, fires radial volleys and drops a Key and a Relic.",
        threat = 4, timeGateSeconds = 38f, minimumRebirth = 0, weight = 0f, rank = EnemyRank.ELITE,
        habitats = EnemyHabitat.ALL,
    ),
    EnemyDefinition(
        EnemyType.ARCHITECT, "Architect", "The final boss. It shifts between phases and hardens against your build.",
        threat = 5, timeGateSeconds = 720f, minimumRebirth = 0, weight = 0f, rank = EnemyRank.BOSS,
        encounter = ARCHITECT_ENCOUNTER,
    ),
    EnemyDefinition(
        EnemyType.SWARMLING, "Swarmling", "Arrives in packs of six to ten and flocks around the Core.",
        threat = 1, timeGateSeconds = 75f, minimumRebirth = 0, weight = 10f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit,
        habitatWeights = HabitatWeights(structure = 6f),
    ),
    EnemyDefinition(
        EnemyType.BOMBER, "Bomber", "Accelerates at the Core, primes and explodes. A fast impact destroys it before it primes.",
        threat = 3, timeGateSeconds = 200f, minimumRebirth = 0, weight = 8f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.NADIR.bit,
        habitatWeights = HabitatWeights(structure = 6f, nadir = 5f),
    ),
    EnemyDefinition(
        EnemyType.LANCER, "Lancer", "Stops, marks a line and lances along it. Lancing into an obstacle stuns it.",
        threat = 3, timeGateSeconds = 240f, minimumRebirth = 0, weight = 7f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit,
        habitatWeights = HabitatWeights(structure = 4f),
    ),
    EnemyDefinition(
        EnemyType.SNIPER, "Sniper", "Keeps far away, shows its sight line and fires one heavy shot.",
        threat = 3, timeGateSeconds = 330f, minimumRebirth = 0, weight = 6f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.ZENITH.bit or EnemyHabitat.ARCHIVE.bit,
        habitatWeights = HabitatWeights(zenith = 3f, archive = 4f),
    ),
    EnemyDefinition(
        EnemyType.AEGIS, "Aegis", "Tethers up to three allies, who take far less damage, and flees the Core.",
        threat = 3, timeGateSeconds = 390f, minimumRebirth = 0, weight = 4f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.ZENITH.bit,
        habitatWeights = HabitatWeights(structure = 3f, zenith = 3f),
    ),
    EnemyDefinition(
        EnemyType.MINELAYER, "Minelayer", "Circles at mid range and drops mines that arm and explode.",
        threat = 3, timeGateSeconds = 450f, minimumRebirth = 0, weight = 5f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.NADIR.bit,
        habitatWeights = HabitatWeights(structure = 4f, nadir = 3f),
    ),
    EnemyDefinition(
        EnemyType.PHANTOM, "Phantom", "Phases out of reach, reappears near the Core and strikes.",
        threat = 4, timeGateSeconds = 300f, minimumRebirth = 2, weight = 6f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.ARCHIVE.bit,
        habitatWeights = HabitatWeights(structure = 4f, archive = 5f),
    ),
    EnemyDefinition(
        EnemyType.BROODMOTHER, "Broodmother", "Slow and massive, it births Swarmlings every few seconds.",
        threat = 4, timeGateSeconds = 420f, minimumRebirth = 3, weight = 3f,
    ),
    EnemyDefinition(
        EnemyType.REFLECTOR, "Reflector", "A front shield blunts impacts and returns shots. Strike it from behind.",
        threat = 4, timeGateSeconds = 360f, minimumRebirth = 4, weight = 5f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit or EnemyHabitat.ARCHIVE.bit,
        habitatWeights = HabitatWeights(structure = 4f, archive = 3f),
    ),
    EnemyDefinition(
        EnemyType.HERALD, "Herald", "A mini-boss with spiral volleys, aspect escorts and a shield only impacts can break.",
        threat = 5, timeGateSeconds = 360f, minimumRebirth = 2, weight = 0f, rank = EnemyRank.MINI_BOSS,
    ),
    EnemyDefinition(
        EnemyType.COMET, "Comet", "Marks a lane and streaks along it. A fast impact shatters it, and so does an obstacle in its path.",
        threat = 2, timeGateSeconds = 120f, minimumRebirth = 0, weight = 7f,
    ),
    EnemyDefinition(
        EnemyType.PYLON, "Pylon",
        "Docks on an obstacle and fires aimed volleys. Weapons barely scratch it; impacts and slammed enemies break it.",
        threat = 3, timeGateSeconds = 270f, minimumRebirth = 0, weight = 4f,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit,
        habitatWeights = HabitatWeights(structure = 6f),
    ),
    EnemyDefinition(
        EnemyType.SENTRY, "Sentry", "Mounts in place, tracks the Core and fires bolt trains down corridors.",
        threat = 3, timeGateSeconds = 0f, minimumRebirth = 0, weight = 0f,
        habitats = EnemyHabitat.STRUCTURE.bit, habitatWeights = HabitatWeights(structure = 10f),
    ),
    EnemyDefinition(
        EnemyType.CRAWLER, "Crawler", "Creeps along walls and obstacles in packs, then lunges at the Core.",
        threat = 1, timeGateSeconds = 0f, minimumRebirth = 0, weight = 0f,
        habitats = EnemyHabitat.STRUCTURE.bit, habitatWeights = HabitatWeights(structure = 12f),
    ),
    EnemyDefinition(
        EnemyType.SERAPH, "Seraph", "Weaves at mid range, mends nearby allies and wraps them in shields only impacts can break.",
        threat = 3, timeGateSeconds = 0f, minimumRebirth = 0, weight = 0f,
        habitats = EnemyHabitat.ZENITH.bit, habitatWeights = HabitatWeights(zenith = 12f),
    ),
    EnemyDefinition(
        EnemyType.CHOIR, "Choir", "Keeps its range and sings radial pulses that fire bolts and shove the Core away.",
        threat = 3, timeGateSeconds = 0f, minimumRebirth = 0, weight = 0f,
        habitats = EnemyHabitat.ZENITH.bit, habitatWeights = HabitatWeights(zenith = 9f),
    ),
    EnemyDefinition(
        EnemyType.IMP, "Imp", "Jinks at the Core and bursts, scattering embers that burn a slow Core.",
        threat = 2, timeGateSeconds = 0f, minimumRebirth = 0, weight = 0f,
        habitats = EnemyHabitat.NADIR.bit, habitatWeights = HabitatWeights(nadir = 14f),
    ),
    EnemyDefinition(
        EnemyType.HOUND, "Hound", "Hunts in packs, circles the Core and dashes through it.",
        threat = 2, timeGateSeconds = 0f, minimumRebirth = 0, weight = 0f,
        habitats = EnemyHabitat.NADIR.bit, habitatWeights = HabitatWeights(nadir = 12f),
    ),
    EnemyDefinition(
        EnemyType.CIPHER, "Cipher", "Glitches out of reach, reappears elsewhere around the Core and fires.",
        threat = 3, timeGateSeconds = 0f, minimumRebirth = 0, weight = 0f,
        habitats = EnemyHabitat.ARCHIVE.bit, habitatWeights = HabitatWeights(archive = 12f),
    ),
    EnemyDefinition(
        EnemyType.MIRROR, "Mirror", "Mirrors the Core through the singularity and meets it there.",
        threat = 3, timeGateSeconds = 0f, minimumRebirth = 0, weight = 0f,
        habitats = EnemyHabitat.ARCHIVE.bit, habitatWeights = HabitatWeights(archive = 8f),
    ),
    EnemyDefinition(
        EnemyType.WARDEN_PRIME, "Warden Prime", "A mini-boss whose well pulls, then pushes, with radial bursts and Warden guards.",
        threat = 4, timeGateSeconds = 45f, minimumRebirth = 0, weight = 0f, rank = EnemyRank.MINI_BOSS,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit, encounter = MINI_BOSS_ENCOUNTER,
    ),
    EnemyDefinition(
        EnemyType.BROOD_QUEEN, "Brood Queen", "A mini-boss that births Swarmling spirals from egg sacs. Fast impacts burst the sacs.",
        threat = 4, timeGateSeconds = 60f, minimumRebirth = 0, weight = 0f, rank = EnemyRank.MINI_BOSS,
        habitats = EnemyHabitat.SPACE.bit or EnemyHabitat.STRUCTURE.bit, encounter = MINI_BOSS_ENCOUNTER,
    ),
    EnemyDefinition(
        EnemyType.THRONE, "Throne", "A mini-boss behind rotating shield arcs. Only hits through the gaps land in full.",
        threat = 5, timeGateSeconds = 165f, minimumRebirth = 0, weight = 0f, rank = EnemyRank.MINI_BOSS,
        habitats = EnemyHabitat.ZENITH.bit, encounter = MINI_BOSS_ENCOUNTER.copy(shiftSeconds = 2.5f),
    ),
    EnemyDefinition(
        EnemyType.PYRE_HERALD, "Pyre Herald",
        "A mini-boss with spiral volleys, a trail of embers and a shield only impacts can break.",
        threat = 5, timeGateSeconds = 165f, minimumRebirth = 0, weight = 0f, rank = EnemyRank.MINI_BOSS,
        habitats = EnemyHabitat.NADIR.bit, encounter = MINI_BOSS_ENCOUNTER,
    ),
    EnemyDefinition(
        EnemyType.CARTOGRAPHER, "Cartographer",
        "A secret boss that draws lanes of fire and lines of mines, then jumps between obstacles.",
        threat = 5, timeGateSeconds = 60f, minimumRebirth = 0, weight = 0f, rank = EnemyRank.SECRET_BOSS,
        encounter = BossEncounterSpec(
            phases = 3, hitCapShare = 0.015f, hitCapTierShrink = 0.02f, hitCapMinimumScale = 0.5f, shiftSeconds = 4f,
            enrageAttackRate = 1.35f, enrageAfterSeconds = 120f,
        ),
        revealedBy = WorldDiscovery.CARTOGRAPHER,
        arrival = "When its far sigil is touched",
    ),
    EnemyDefinition(
        EnemyType.ANTECEDENT, "Antecedent",
        "A secret boss, the Architect's forerunner. It alternates Zenith and Nadir phases and hardens against your build.",
        threat = 5, timeGateSeconds = 300f, minimumRebirth = 0, weight = 0f, rank = EnemyRank.SECRET_BOSS,
        habitats = EnemyHabitat.ARCHIVE.bit,
        encounter = BossEncounterSpec(
            phases = 4, hitCapShare = 0.015f, hitCapTierShrink = 0.02f, hitCapMinimumScale = 0.5f, shiftSeconds = 5f,
            enrageAttackRate = 1.35f, enrageAfterSeconds = 150f,
        ),
        revealedBy = WorldDiscovery.ANTECEDENT,
    ),
).toImmutableList()
