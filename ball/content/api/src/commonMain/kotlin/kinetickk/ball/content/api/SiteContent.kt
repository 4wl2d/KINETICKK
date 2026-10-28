// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.floor

enum class DirectedReward { WEAPON, RELIC, ITEM_AND_REPAIR }
enum class RewardFocus(val label: String) {
    MOTION("Motion"), CONTROL("Control"), STRIKE("Strike"),
    KINETIC("Kinetic"), THERMAL("Thermal"), CRYO("Cryo"),
    ION("Ion"), GRAVITIC("Gravitic"), ENTROPY("Entropy"),
    OFFENSE("Offense"), DEFENSE("Defense"), ECONOMY("Economy"),
    SONIC("Sonic"), VOID("Void"),
}

/** What a site is for; drives HUD labels, edge-marker icons, minimap glyphs and which system runs it. */
enum class SiteFamily { ANOMALY, RACE, CHALLENGE, BOUNTY, CACHE, SECRET, PORTAL, SIGNAL }

/**
 * Every site kind. Not persisted (the discovery mask uses [WorldDiscovery]); the order is the definition order of
 * [SitePolicy.definitions] and the three anomalies stay first, so entries are append-only.
 */
enum class SiteKind(val family: SiteFamily) {
    RESONANT_CIRCUIT(SiteFamily.ANOMALY),
    SEALED_ANOMALY(SiteFamily.ANOMALY),
    COLLAPSING_ORBIT(SiteFamily.ANOMALY),
    GATE_RACE(SiteFamily.RACE),
    /** No Brake. */
    BRAKELESS_TRIAL(SiteFamily.CHALLENGE),
    /** Dash kills only. */
    RAM_TRIAL(SiteFamily.CHALLENGE),
    /** No damage taken. */
    UNTOUCHED_TRIAL(SiteFamily.CHALLENGE),
    /** Hold velocity tier 3 or above. */
    VELOCITY_TRIAL(SiteFamily.CHALLENGE),
    /** Slalom poles. */
    SLALOM_TRIAL(SiteFamily.CHALLENGE),
    BOUNTY_ARENA(SiteFamily.BOUNTY),
    DRIFT_CACHE(SiteFamily.CACHE),
    LOST_CURSOR(SiteFamily.SECRET),
    /** Hidden proximity trigger that releases the Golden Drifter. */
    GOLDEN_DRIFTER(SiteFamily.SECRET),
    QUIET_SIGNAL(SiteFamily.SECRET),
    /** Summons the Cartographer. */
    FAR_SIGIL(SiteFamily.SECRET),
    ZENITH_RIFT(SiteFamily.PORTAL),
    NADIR_RIFT(SiteFamily.PORTAL),
}

/** The anomaly kinds lead [SiteKind] in this count (the 0.5.0 window kinds). */
const val ANOMALY_KIND_COUNT: Int = 3

/**
 * One reward bundle, every part optional; Gameplay grants all parts at once. Bounds keep any single site inside the
 * existing channels: at most one directed reward, two plain offers and one floor offer.
 */
data class SiteReward(
    val directed: DirectedReward? = null,
    /** Plain item offers queued like a level-up offer. */
    val itemOffers: Int = 0,
    /** One item offer whose cards roll at least this rarity (the rarity-floor queue). */
    val itemOfferFloor: ItemRarity? = null,
    val keys: Int = 0,
    val relics: Int = 0,
    val repairs: Int = 0,
    val data: Int = 0,
    /** Run Matter before the run's Matter gain multiplier. */
    val matter: Int = 0,
    /** Share of maximum integrity healed at once. */
    val heal: Float = 0f,
    val overdrive: Float = 0f,
    val rerolls: Int = 0,
) {
    init {
        require(itemOffers in 0..2 && keys in 0..2 && relics in 0..1 && repairs in 0..3 && data in 0..40) {
            "Site reward counts are outside their bounds"
        }
        require(matter in 0..120 && rerolls in 0..2) { "Site reward Matter or rerolls are outside their bounds" }
        require(heal.isFinite() && heal in 0f..1f && overdrive.isFinite() && overdrive in 0f..100f) {
            "Site reward heal or Overdrive is outside its bounds"
        }
    }

    val isEmpty: Boolean get() = this == NONE

    companion object {
        val NONE: SiteReward = SiteReward()
    }
}

data class SiteDefinition(
    val kind: SiteKind,
    /** English content key. */
    val name: String,
    /** English content key behind the trial panel's (!) button; never shown as HUD text. */
    val instruction: String,
    /** Success reward; a race's medal rewards live in [RacePolicy]. */
    val reward: SiteReward,
    /** Above zero: a POI window may draw this kind. Only anomaly, race, challenge, bounty and portal kinds. */
    val windowWeight: Float = 0f,
    /** Earliest window (its scheduled second) that may offer this kind. */
    val minimumOfferAt: Float = 0f,
    /** A window offers it only when `scheduledAt + offerLifetime + reserveSeconds < bossAt`. */
    val reserveSeconds: Float = 25f,
    val minimumRebirth: Int = 0,
    /** Fly-through radius (window sites keep 65 so positions and the bots' clearance stay exact). */
    val activationRadius: Float = 65f,
    /** Trial seconds once active; 0 = untimed (bounty, secret duels, instant sites). */
    val duration: Float = 25f,
) {
    init {
        require(name.isNotBlank() && instruction.isNotBlank()) { "Site texts must not be blank" }
        require(windowWeight.isFinite() && windowWeight >= 0f) { "Site window weight must be finite and non-negative" }
        require(minimumOfferAt.isFinite() && minimumOfferAt >= 0f && reserveSeconds.isFinite() && reserveSeconds >= 0f) {
            "Site offer times must be finite and non-negative"
        }
        require(minimumRebirth in ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL) {
            "Site minimum Rebirth must be a valid tier"
        }
        require(activationRadius.isFinite() && activationRadius in 20f..200f) { "Site radius must be in 20..200" }
        require(duration.isFinite() && duration in 0f..25f) { "Site duration must be in 0..25 s" }
        require(windowWeight == 0f || kind.family in WindowFamilies) { "Only window families take a window weight" }
        require(duration > 0f || kind.family in UntimedFamilies) { "Only untimed families may last 0 s" }
    }
}

internal val WindowFamilies: Set<SiteFamily> =
    setOf(SiteFamily.ANOMALY, SiteFamily.RACE, SiteFamily.CHALLENGE, SiteFamily.BOUNTY, SiteFamily.PORTAL)
internal val UntimedFamilies: Set<SiteFamily> =
    setOf(SiteFamily.BOUNTY, SiteFamily.CACHE, SiteFamily.SECRET, SiteFamily.PORTAL, SiteFamily.SIGNAL)

/** The six-window schedule; all clocks use active simulation seconds. */
data class SiteWindowPolicy(
    val firstOfferAt: Float = 45f,
    val offerInterval: Float = 120f,
    val lastOfferAt: Float = 645f,
    val offerLifetime: Float = 40f,
    val trialDuration: Float = 25f,
    val orbitRequiredSeconds: Float = 8f,
) {
    init {
        require(firstOfferAt.isFinite() && firstOfferAt >= 0f)
        require(offerInterval.isFinite() && offerInterval > 0f)
        require(lastOfferAt.isFinite() && lastOfferAt >= firstOfferAt)
        require(offerLifetime.isFinite() && offerLifetime in 1f..40f)
        require(trialDuration.isFinite() && trialDuration in 1f..25f)
        require(orbitRequiredSeconds.isFinite() && orbitRequiredSeconds in 1f..trialDuration)
        require((lastOfferAt - firstOfferAt) / offerInterval < 6f)
    }
}

data class RacePolicy(
    val minGates: Int = 5,
    val maxGates: Int = 7,
    val minRadius: Float = 1_150f,
    val maxRadius: Float = 1_550f,
    /** Share of a full turn the gates sweep around the start (the loop never closes on the start ring). */
    val loopShare: Float = 0.85f,
    val angleJitter: Float = 0.18f,
    /** Swept pass radius = post half-spacing. */
    val gateRadius: Float = 110f,
    val gateClearance: Float = 140f,
    val nudgeStep: Float = 80f,
    val nudgeAttempts: Int = 5,
    /** Par flight speed: the steady model at this cursor reach. */
    val parReach: Float = 0.5f,
    val turnAllowanceSeconds: Float = 0.3f,
    val goldFactor: Float = 1.0f,
    val silverFactor: Float = 1.3f,
    val bronzeFactor: Float = 1.75f,
    val limitFactor: Float = 2.4f,
    val minimumLimit: Float = 12f,
    /** Share of the accelerator-gate impulse a passed race gate grants. */
    val gateBoostShare: Float = 0.5f,
    val gold: SiteReward = SiteReward(directed = DirectedReward.ITEM_AND_REPAIR, keys = 1, matter = 15),
    val silver: SiteReward = SiteReward(directed = DirectedReward.ITEM_AND_REPAIR),
    val bronze: SiteReward = SiteReward(itemOffers = 1),
    val finish: SiteReward = SiteReward(matter = 10),
) {
    init {
        require(minGates >= 5 && minGates <= maxGates && maxGates <= ContentBounds.MAX_RACE_GATES) {
            "Race gates must satisfy 5 <= min <= max <= ${ContentBounds.MAX_RACE_GATES}"
        }
        require(minRadius in 600f..2_000f && maxRadius in 600f..2_000f && minRadius <= maxRadius) { "Race radii must be in 600..2000" }
        require(maxRadius + nudgeStep * nudgeAttempts <= 2_100f) { "A race course must stay inside the obstacle window" }
        require(nudgeStep.isFinite() && nudgeStep >= 0f && nudgeAttempts >= 0)
        require(gateRadius.isFinite() && gateRadius > 0f && gateClearance.isFinite() && gateClearance >= 0f)
        require(angleJitter.isFinite() && angleJitter >= 0f && turnAllowanceSeconds.isFinite() && turnAllowanceSeconds >= 0f)
        require(loopShare in 0.5f..0.95f) { "Race loop share must be in 0.5..0.95" }
        require(parReach in 0.2f..0.8f) { "Race par reach must be in 0.2..0.8" }
        require(goldFactor >= 1f && goldFactor <= silverFactor && silverFactor <= bronzeFactor && bronzeFactor < limitFactor) {
            "Race medal factors must satisfy 1 <= gold <= silver <= bronze < limit"
        }
        require(minimumLimit in 5f..25f) { "Race minimum limit must be in 5..25 s" }
        require(gateBoostShare in 0f..1f) { "Race gate boost share must be in 0..1" }
    }
}

data class ChallengePolicy(
    val brakelessRequired: Float = 12f,
    val brakelessDuration: Float = 20f,
    val brakelessStartRadius: Float = 900f,
    val brakelessEndRadius: Float = 450f,
    val brakelessMinSpeed: Float = 500f,
    val brakelessGrace: Float = 0.5f,
    val ramFoes: Int = 8,
    val ramRequired: Int = 6,
    val ramDuration: Float = 20f,
    val ramFoeHealthShare: Float = 0.6f,
    val ramRingRadius: Float = 320f,
    val ramClamp: Float = 650f,
    val untouchedRequired: Float = 12f,
    val untouchedDuration: Float = 22f,
    val untouchedRadius: Float = 650f,
    val untouchedVolleyInterval: Float = 1.6f,
    val untouchedWarning: Float = 0.8f,
    val untouchedShotSpeed: Float = 520f,
    val untouchedShotDamage: Float = 10f,
    val velocityRequired: Float = 6f,
    val velocityDuration: Float = 25f,
    val velocityMinSpeed: Float = 1_400f,
    val velocityZoneRadius: Float = 1_800f,
    val slalomPoles: Int = 6,
    val slalomDuration: Float = 18f,
    val slalomFirstAlong: Float = 360f,
    val slalomSpacing: Float = 340f,
    val slalomPoleOffset: Float = 150f,
    val slalomMaxLateral: Float = 900f,
    val slalomPoleClearance: Float = 90f,
    val slalomAxisTurn: Float = 0.35f,
) {
    init {
        require(ramFoes in 1..ContentBounds.MAX_SITE_FOES && ramRequired in 1..ramFoes) {
            "Ram foes must be in 1..${ContentBounds.MAX_SITE_FOES} and the requirement within them"
        }
        listOf(brakelessDuration, ramDuration, untouchedDuration, velocityDuration, slalomDuration).forEach {
            require(it in 1f..25f) { "Challenge durations must be in 1..25 s" }
        }
        require(brakelessRequired in 0f..brakelessDuration && untouchedRequired in 0f..untouchedDuration &&
            velocityRequired in 0f..velocityDuration) { "A challenge never asks for more seconds than it lasts" }
        listOf(
            brakelessStartRadius, brakelessEndRadius, ramRingRadius, ramClamp, untouchedRadius, velocityZoneRadius,
            slalomSpacing, slalomPoleOffset, slalomMaxLateral, slalomPoleClearance,
        ).forEach { require(it.isFinite() && it > 0f) { "Challenge radii must be positive" } }
        // The Ram rite's foes are an encounter: its area, ring and integrity bounds hold for them.
        require(ramFoeHealthShare in 0.1f..1f) { "Ram foe integrity share must be in 0.1..1" }
        require(ramClamp in 120f..2_400f && ramRingRadius <= ramClamp) {
            "The Ram rite's area must be in 120..2400 and hold its ring"
        }
        require(slalomPoles in 2..7) { "Slalom poles must be in 2..7" }
        require(slalomFirstAlong + (slalomPoles - 1) * slalomSpacing <= 2_300f) { "A slalom must stay inside the obstacle window" }
    }
}

data class BountyPolicy(
    /** The duel's mini-boss; it must be a MINI_BOSS rank enemy (checked at bootstrap). */
    val bossType: EnemyType = EnemyType.WARDEN_PRIME,
    val bossIntegrityShare: Float = 0.55f,
    val arenaRadius: Float = 900f,
    val exitMargin: Float = 400f,
    val bossSpawnOffset: Float = 300f,
) {
    init {
        require(bossIntegrityShare in 0.1f..1f) { "Bounty integrity share must be in 0.1..1" }
        require(arenaRadius in 400f..1_400f) { "Bounty arena radius must be in 400..1400" }
        require(exitMargin in 100f..1_000f) { "Bounty exit margin must be in 100..1000" }
        require(bossSpawnOffset.isFinite() && bossSpawnOffset >= 0f)
    }
}

data class AmbientSiteRule(
    /** A cache or secret kind. */
    val kind: SiteKind,
    val enabled: Boolean = true,
    val cellSize: Float,
    /** Chance 0 below [minDistance] from the absolute origin, rising linearly to [maxChance] at [fullDistance]. */
    val minDistance: Float,
    val fullDistance: Float,
    val maxChance: Float,
    /** Edge marker and minimap reveal distance (0: never shown off-screen). */
    val revealDistance: Float = 0f,
    /** Proximity trigger instead of fly-through; 0 = fly-through. */
    val triggerRadius: Float = 0f,
) {
    init {
        require(kind.family == SiteFamily.CACHE || kind.family == SiteFamily.SECRET) { "Ambient sites are caches or secrets" }
        require(minDistance >= 6_000f && fullDistance > minDistance) { "Ambient sites start at least 6000 u from the origin" }
        require(maxChance in 0f..0.5f) { "Ambient site chance must be in 0..0.5" }
        require(cellSize in 2_400f..12_000f) { "Ambient site cells must be in 2400..12000" }
        require(revealDistance.isFinite() && revealDistance >= 0f && triggerRadius.isFinite() && triggerRadius >= 0f)
    }
}

data class AmbientSitePolicy(
    val enabled: Boolean = true,
    val queryHalfSize: Float = 3_600f,
    val settleDistance: Float = 1_800f,
    val rules: ImmutableList<AmbientSiteRule> = immutableListOf(
        AmbientSiteRule(
            SiteKind.DRIFT_CACHE, cellSize = 3_000f, minDistance = 6_000f, fullDistance = 14_000f, maxChance = 0.18f,
            revealDistance = 1_800f,
        ),
        AmbientSiteRule(SiteKind.LOST_CURSOR, cellSize = 6_000f, minDistance = 9_000f, fullDistance = 18_000f, maxChance = 0.10f),
        AmbientSiteRule(
            SiteKind.GOLDEN_DRIFTER, cellSize = 4_000f, minDistance = 6_000f, fullDistance = 12_000f, maxChance = 0.08f,
            triggerRadius = 1_300f,
        ),
        AmbientSiteRule(
            SiteKind.QUIET_SIGNAL, cellSize = 8_000f, minDistance = 20_000f, fullDistance = 30_000f, maxChance = 0.30f,
            revealDistance = 3_000f,
        ),
    ),
    val cacheData: SiteReward = SiteReward(data = 12, matter = 10),
    val cacheRepairs: SiteReward = SiteReward(repairs = 2, matter = 10),
    val cacheRelic: SiteReward = SiteReward(relics = 1),
    val cacheDataShare: Float = 0.60f,
    val cacheRepairShare: Float = 0.25f,
) {
    init {
        require(queryHalfSize.isFinite() && queryHalfSize > 0f) { "Ambient query half size must be positive" }
        require(rules.filter { it.enabled }.sumOf { rule ->
            val side = floor(2f * queryHalfSize / rule.cellSize).toInt() + 2
            side * side
        } <= ContentBounds.MAX_AMBIENT_SITES) { "The ambient site window must fit ${ContentBounds.MAX_AMBIENT_SITES} sites" }
        require(settleDistance.isFinite() && settleDistance >= 0f && settleDistance + 6f * 60f + 200f <= 2_400f) {
            "Ambient sites must settle inside the obstacle window"
        }
        require(rules.map { it.kind }.toSet().size == rules.size) { "Ambient site rules must name distinct kinds" }
        require(cacheDataShare in 0f..1f && cacheRepairShare in 0f..1f && cacheDataShare + cacheRepairShare <= 1f) {
            "Cache reward shares must sum to at most 1"
        }
    }
}

data class SecretPolicy(
    val farTravelDistance: Float = 30_000f,
    val farTravelBefore: Float = 480f,
    /** Sigils are refused and fade at `bossAt − sigilCutoff`. */
    val sigilCutoff: Float = 90f,
    val sigilAhead: Float = 1_100f,
    val sigilRadius: Float = 80f,
    val duelLeash: Float = 2_400f,
    val secretBossIntegrityShare: Float = 1f,
    /** Granted by the sigil when its boss falls; the boss's own kill payout is separate. */
    val secretReward: SiteReward = SiteReward(itemOfferFloor = ItemRarity.EPIC, matter = 60),
    val spinEnabled: Boolean = true,
    val spinTurns: Float = 10f,
    val spinBuckets: Int = 9,
    val spinMinDistance: Float = 40f,
    val spinMaxDistance: Float = 900f,
    val lostCursorReward: SiteReward = SiteReward(rerolls = 1, matter = 15),
    val goldenReward: SiteReward = SiteReward(data = 40, matter = 25),
    val goldenBaseSpeed: Float = 620f,
    val goldenSpeedRamp: Float = 40f,
    val goldenMaxSpeed: Float = 1_050f,
    val goldenLifetime: Float = 18f,
    val goldenEscapeDistance: Float = 2_800f,
    val spinReward: SiteReward = SiteReward(overdrive = 100f, matter = 10),
    val quietReward: SiteReward = SiteReward(matter = 20),
    val doodleReward: SiteReward = SiteReward(matter = 30),
) {
    init {
        require(spinBuckets in 1..ContentBounds.MAX_SPIN_BUCKETS) { "Spin buckets must be in 1..${ContentBounds.MAX_SPIN_BUCKETS}" }
        require(spinTurns in 1f..20f) { "Spin turns must be in 1..20" }
        require(farTravelDistance >= 6_000f) { "Far travel starts at least 6000 u from the origin" }
        require(sigilCutoff in 30f..300f) { "Sigil cutoff must be in 30..300 s" }
    }
}

/** Captured Content policy of every site; all clocks use active simulation seconds. */
data class SitePolicy(
    /** False: no window offers, ambient sites, sigils or Spin Cycle. */
    val enabled: Boolean = true,
    val windows: SiteWindowPolicy = SiteWindowPolicy(),
    /** Second draw's weight multiplier for kinds of the first draw's family. */
    val sameFamilyWeight: Float = 0.35f,
    val maxPendingDirectedRewards: Int = 10,
    val maxPendingFloorOffers: Int = 4,
    val definitions: ImmutableList<SiteDefinition> = defaultSiteDefinitions(),
    val race: RacePolicy = RacePolicy(),
    val challenges: ChallengePolicy = ChallengePolicy(),
    val bounty: BountyPolicy = BountyPolicy(),
    val ambient: AmbientSitePolicy = AmbientSitePolicy(),
    val secrets: SecretPolicy = SecretPolicy(),
) {
    init {
        require(definitions.size == SiteKind.entries.size && definitions.withIndex().all { (i, d) -> d.kind.ordinal == i }) {
            "Site definitions must follow the SiteKind order"
        }
        require(definitions.map { it.name }.toSet().size == definitions.size) { "Site names must be unique" }
        require(sameFamilyWeight.isFinite() && sameFamilyWeight in 0f..1f) { "Same-family weight must be in 0..1" }
        require(maxPendingDirectedRewards in 6..ContentBounds.MAX_DIRECTED_REWARDS) {
            "Directed reward queue must be in 6..${ContentBounds.MAX_DIRECTED_REWARDS}"
        }
        require(maxPendingFloorOffers in 1..ContentBounds.MAX_FLOOR_OFFERS) {
            "Floor offer queue must be in 1..${ContentBounds.MAX_FLOOR_OFFERS}"
        }
        val unconditional = definitions.filter {
            it.windowWeight > 0f && it.kind.family != SiteFamily.PORTAL && it.minimumOfferAt <= windows.firstOfferAt &&
                it.minimumRebirth == 0 && it.reserveSeconds <= windows.trialDuration
        }
        require(unconditional.size >= 2) { "At least two unconditional window kinds must exist so every window can draw a pair" }
        require(sameFamilyWeight > 0f || unconditional.map { it.kind.family }.toSet().size >= 2) {
            "A zero same-family weight needs unconditional window kinds of two families, so the second draw always has a kind"
        }
        require(definitions.all { it.windowWeight == 0f || it.duration <= windows.trialDuration }) {
            "A window site never lasts longer than the trial"
        }
    }

    fun definition(kind: SiteKind): SiteDefinition = definitions[kind.ordinal]
}

/** Window weight of each of the five challenges (together 1.8, one family's share of a window's draw). */
private const val CHALLENGE_WINDOW_WEIGHT = 0.36f

private const val RIFT_INSTRUCTION = "Fly through to cross over. The layer releases you before the Architect arrives."

/**
 * The shipped sites in [SiteKind] order. The anomalies, the gate race and the five challenges take window weight; the
 * bounty and rifts stay at 0 until their lanes enable them.
 */
fun defaultSiteDefinitions(): ImmutableList<SiteDefinition> = immutableListOf(
    SiteDefinition(
        SiteKind.RESONANT_CIRCUIT, "Resonant circuit", "Visit beacons 2, 3, then return to 1.",
        SiteReward(directed = DirectedReward.WEAPON), windowWeight = 1f,
    ),
    SiteDefinition(
        SiteKind.SEALED_ANOMALY, "Sealed anomaly", "Defeat the three marked defenders near the vault.",
        SiteReward(directed = DirectedReward.RELIC), windowWeight = 1f,
    ),
    SiteDefinition(
        SiteKind.COLLAPSING_ORBIT, "Collapsing orbit", "Move within the ring for eight seconds; dodge marked volleys.",
        SiteReward(directed = DirectedReward.ITEM_AND_REPAIR), windowWeight = 1f,
    ),
    // Medal rewards live in RacePolicy; the course sets its own limit within the window's trial cap.
    SiteDefinition(
        SiteKind.GATE_RACE, "Gate race", "Fly through the gates in order. Beat the par time for gold.", SiteReward.NONE,
        windowWeight = 1.4f,
    ),
    SiteDefinition(
        SiteKind.BRAKELESS_TRIAL, "Brakeless run",
        "Keep moving inside the shrinking ring for twelve seconds. Braking fails the trial.",
        SiteReward(directed = DirectedReward.ITEM_AND_REPAIR), windowWeight = CHALLENGE_WINDOW_WEIGHT, duration = 20f,
    ),
    SiteDefinition(
        SiteKind.RAM_TRIAL, "Ram rite", "Destroy six marked drifters with Dash impacts.",
        SiteReward(directed = DirectedReward.WEAPON), windowWeight = CHALLENGE_WINDOW_WEIGHT, duration = 20f,
    ),
    SiteDefinition(
        SiteKind.UNTOUCHED_TRIAL, "Untouched", "Stay inside the ring for twelve seconds without taking damage.",
        SiteReward(directed = DirectedReward.RELIC), windowWeight = CHALLENGE_WINDOW_WEIGHT, duration = 22f,
    ),
    SiteDefinition(
        SiteKind.VELOCITY_TRIAL, "Velocity hold", "Hold Overdrive velocity for six seconds within the zone.",
        SiteReward(directed = DirectedReward.ITEM_AND_REPAIR), windowWeight = CHALLENGE_WINDOW_WEIGHT,
    ),
    SiteDefinition(
        SiteKind.SLALOM_TRIAL, "Slalom", "Pass each pole on its flagged side, in order.",
        SiteReward(directed = DirectedReward.WEAPON), windowWeight = CHALLENGE_WINDOW_WEIGHT, duration = 18f,
    ),
    SiteDefinition(
        SiteKind.BOUNTY_ARENA, "Bounty", "Defeat the marked target. Leaving the arena forfeits the bounty.",
        SiteReward(matter = 30), minimumOfferAt = 160f, reserveSeconds = 90f, duration = 0f,
    ),
    SiteDefinition(SiteKind.DRIFT_CACHE, "Drift cache", "Fly through to open.", SiteReward.NONE, activationRadius = 70f, duration = 0f),
    SiteDefinition(
        SiteKind.LOST_CURSOR, "Lost cursor", "It seems to be waiting for someone.", SiteReward.NONE,
        activationRadius = 70f, duration = 0f,
    ),
    SiteDefinition(
        SiteKind.GOLDEN_DRIFTER, "Golden drifter", "Catch it before it slips away.", SiteReward.NONE,
        activationRadius = 70f, duration = 0f,
    ),
    SiteDefinition(SiteKind.QUIET_SIGNAL, "Quiet signal", "Listen.", SiteReward.NONE, activationRadius = 90f, duration = 0f),
    SiteDefinition(
        SiteKind.FAR_SIGIL, "Far sigil", "Touch the sigil to summon whoever drew this map.", SiteReward.NONE,
        activationRadius = 80f, duration = 0f,
    ),
    SiteDefinition(SiteKind.ZENITH_RIFT, "Zenith rift", RIFT_INSTRUCTION, SiteReward.NONE, reserveSeconds = 135f, duration = 0f),
    SiteDefinition(SiteKind.NADIR_RIFT, "Nadir rift", RIFT_INSTRUCTION, SiteReward.NONE, reserveSeconds = 135f, duration = 0f),
)
