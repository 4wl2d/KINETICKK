// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/**
 * Active skills a run drafts into its Z/X/C slots. Append only: gameplay indexes runtime cells,
 * cues and link styles by ordinal.
 */
enum class SkillId {
    SOLAR_FLARE,
    METEOR,
    FROST_LINE,
    FROST_SNAP,
    ARC_HOOK,
    STORM_COIL,
    GRAVITY_WELL,
    MASS_DRIVER,
    DECAY_SPORES,
    ENTROPIC_TIDE,
    SONIC_BOOM,
    RESONATOR,
    VOID_STEP,
    NULL_SHROUD,
    SLINGSHOT,
    FULL_STOP,
    CATALYZE,
    DUAL_INFUSION,
}

/**
 * Where a cast lands: [SELF] at the Core, [AIM] along the Core-to-pointer direction, [NEAREST] on the
 * enemy nearest the pointer within reach of the Core, [AREA] at the pointer clamped to reach.
 */
enum class SkillTargeting { SELF, AIM, NEAREST, AREA }

/** Balance band a skill is measured in. */
enum class SkillFamily { STRIKE, MOTION, ZONE, CATALYST, GUARD }

/**
 * One rank of a skill: its base cooldown and the content-owned numbers its effect reads by index
 * (every rank of a skill lists the same number of values, in the order its [SkillId] documents).
 */
data class SkillRank(
    val cooldownSeconds: Float,
    val values: ImmutableList<Float>,
) {
    init {
        require(cooldownSeconds.isFinite() && cooldownSeconds >= ContentBounds.MIN_SKILL_COOLDOWN_SECONDS) {
            "Skill cooldown must be finite and at least ${ContentBounds.MIN_SKILL_COOLDOWN_SECONDS} s"
        }
        require(values.size <= ContentBounds.MAX_SKILL_VALUES) {
            "Skill rank values size ${values.size} exceeds ${ContentBounds.MAX_SKILL_VALUES}"
        }
        require(values.all(Float::isFinite)) { "Skill rank values must be finite" }
    }
}

/**
 * Content owns a skill's identity, text, element, build tags, targeting and per-rank numbers; the
 * run owns slots, cooldowns and casting. [element] is null for kinetic and build-element skills.
 * [reach] is the rank I distance a cast can reach from the Core (0 for [SkillTargeting.SELF]).
 */
data class SkillDefinition(
    val id: SkillId,
    val name: String,
    val summary: String,
    /** What rank III adds, shown on the rank-up card and in the Codex. */
    val rankThree: String,
    val element: StatusElement?,
    val tags: ImmutableList<BuildTag>,
    val targeting: SkillTargeting,
    val reach: Float,
    val family: SkillFamily,
    val ranks: ImmutableList<SkillRank>,
) {
    init {
        require(name.isNotBlank() && summary.isNotBlank() && rankThree.isNotBlank()) {
            "Skill $id needs a name, a summary and a rank III text"
        }
        require(tags.size == SKILL_TAG_COUNT && tags.toSet().size == SKILL_TAG_COUNT) {
            "Skill $id carries exactly two distinct build tags"
        }
        require(reach.isFinite() && reach >= 0f) { "Skill $id reach must be finite and non-negative" }
        require((targeting == SkillTargeting.SELF) == (reach == 0f)) {
            "Skill $id reach is zero exactly when it targets itself"
        }
        require(ranks.size == ContentBounds.MAX_SKILL_RANK) {
            "Skill $id defines exactly ${ContentBounds.MAX_SKILL_RANK} ranks"
        }
        require(ranks.zipWithNext().all { (lower, higher) -> higher.cooldownSeconds <= lower.cooldownSeconds }) {
            "Skill $id cooldown must not grow with rank"
        }
        require(ranks.all { it.values.size == ranks.first().values.size }) {
            "Skill $id ranks list the same number of values"
        }
    }

    /** Rank 1..[ContentBounds.MAX_SKILL_RANK]. */
    fun rank(rank: Int): SkillRank = ranks[rank.coerceIn(1, ranks.size) - 1]

    companion object {
        /** Build tags per skill; each owned skill adds a point to both. */
        const val SKILL_TAG_COUNT: Int = 2
    }
}

/**
 * The shipped pool in [SkillId] order: two skills per element, two kinetic and two catalyst skills;
 * every [BuildTag] appears on exactly four. Values per rank, in order:
 * - SOLAR_FLARE: vented heat cap, radius, damage, damage per vented heat, base Scorch
 * - METEOR: landing radius, damage, Scorch, Core boost speed, landing delay s, hurl speed
 * - FROST_LINE: length, duration s, bolts erased cap, Chill per tick, band half-width
 * - FROST_SNAP: radius, Chill, shatter impact scale, coiled Dash push, coiled s
 * - ARC_HOOK: reach, Charge, hook speed, chain links, link damage, Dash-hit window s
 * - STORM_COIL: radius, duration s, targets per zap, zap damage, zap interval s
 * - GRAVITY_WELL: radius, duration s, pull, damage per second, Data pull range
 * - MASS_DRIVER: duration s, mass multiplier, damage taken cut, throw speed, throw threshold speed
 * - DECAY_SPORES: radius, Decay, Spored s, Data pickups cap
 * - ENTROPIC_TIDE: radius, detonation scale, heal per stack, heal stack cap
 * - SONIC_BOOM: range, damage, Resonance, bolts erased cap, push speed, recoil speed
 * - RESONATOR: radius, duration s, echo scale, echoes cap
 * - VOID_STEP: distance, rift damage, Hollow, rift radius, phase s, rift delay s
 * - NULL_SHROUD: duration s, stored bolts cap, returned shot damage, return range
 * - SLINGSHOT: speed scale, minimum speed, maximum speed, hurl window s, hurl speed
 * - FULL_STOP: radius, impact scale, Break threshold fraction
 * - CATALYZE: radius, targets cap, reaction scale, fallback stacks
 * - DUAL_INFUSION: duration s, weapon hits per secondary stack, stacks per second cap
 */
fun defaultSkillDefinitions(): ImmutableList<SkillDefinition> = SkillId.entries.map(::defaultSkill).toImmutableList()

private fun defaultSkill(id: SkillId): SkillDefinition = when (id) {
    SkillId.SOLAR_FLARE -> skill(
        id, "Solar Flare",
        "Vents heat into a flare; the more heat, the more Scorch.",
        "Enemies within two Scorch of Ignite ignite at once.",
        StatusElement.THERMAL, BuildTag.OVERCLOCK, BuildTag.BULWARK, SkillTargeting.SELF, 0f, SkillFamily.STRIKE,
        rank(10f, 30f, 200f, 30f, 0.8f, 2f),
        rank(9f, 40f, 220f, 40f, 0.8f, 2f),
        rank(8f, 50f, 240f, 50f, 0.8f, 2f),
    )
    SkillId.METEOR -> skill(
        id, "Meteor",
        "Call a meteor to the pointer; be inside to ride the blast.",
        "Riding the blast also sets the Core ablaze for 3 s.",
        StatusElement.THERMAL, BuildTag.RAM, BuildTag.DRIVE, SkillTargeting.AREA, 520f, SkillFamily.STRIKE,
        rank(12f, 140f, 70f, 4f, 550f, 0.8f, 520f),
        rank(11f, 150f, 90f, 5f, 600f, 0.8f, 520f),
        rank(10f, 160f, 110f, 6f, 650f, 0.8f, 520f),
    )
    SkillId.FROST_LINE -> skill(
        id, "Frost Line",
        "Raises a line of frost that slows enemies, stops hostile bolts and slams thrown enemies.",
        "Enemies on the line are embrittled while they stay on it.",
        StatusElement.CRYO, BuildTag.BULWARK, BuildTag.ANCHOR, SkillTargeting.AIM, 160f, SkillFamily.ZONE,
        rank(12f, 320f, 5f, 30f, 1f, 48f),
        rank(11f, 380f, 5.5f, 40f, 1f, 60f),
        rank(10f, 440f, 6f, 50f, 1f, 72f),
    )
    SkillId.FROST_SNAP -> skill(
        id, "Frost Snap",
        "A freezing pulse that shatters Frozen and Brittle enemies and coils your next Dash.",
        "Enemies within two Chill of Freeze freeze at once.",
        StatusElement.CRYO, BuildTag.ANCHOR, BuildTag.PRECISION, SkillTargeting.SELF, 0f, SkillFamily.STRIKE,
        rank(9f, 180f, 3f, 0.6f, 1.35f, 2f),
        rank(8f, 200f, 3f, 0.75f, 1.45f, 2f),
        rank(7f, 220f, 4f, 0.9f, 1.55f, 2f),
    )
    SkillId.ARC_HOOK -> skill(
        id, "Arc Hook",
        "Grapple the enemy nearest your pointer and fly to it; the impact counts as a Dash hit.",
        "If the target dies within 1.5 s, the hook is ready again 2.5 s after the cast.",
        StatusElement.ION, BuildTag.DRIVE, BuildTag.CHAIN, SkillTargeting.NEAREST, 460f, SkillFamily.MOTION,
        rank(7f, 460f, 3f, 1_050f, 2f, 60f, 0.7f),
        rank(6.5f, 520f, 4f, 1_100f, 3f, 65f, 0.7f),
        rank(6f, 580f, 5f, 1_150f, 3f, 70f, 0.7f),
    )
    SkillId.STORM_COIL -> skill(
        id, "Storm Coil",
        "Plant a lightning turret at the pointer.",
        "Zaps force Overload on enemies within two Charge of it.",
        StatusElement.ION, BuildTag.ARSENAL, BuildTag.CHAIN, SkillTargeting.AREA, 420f, SkillFamily.ZONE,
        rank(12f, 175f, 4.5f, 2f, 22f, 0.5f),
        rank(11f, 175f, 5.75f, 3f, 28f, 0.5f),
        rank(10f, 175f, 7f, 3f, 34f, 0.5f),
    )
    SkillId.GRAVITY_WELL -> skill(
        id, "Gravity Well",
        "Opens a well at the pointer that pulls enemies and Data in.",
        "When the well closes, enemies inside with three or more Mass collapse.",
        StatusElement.GRAVITIC, BuildTag.ANCHOR, BuildTag.FORTUNE, SkillTargeting.AREA, 420f, SkillFamily.ZONE,
        rank(13f, 190f, 3f, 260f, 10f, 300f),
        rank(12f, 210f, 3.5f, 260f, 14f, 300f),
        rank(11f, 230f, 4f, 260f, 18f, 300f),
    )
    SkillId.MASS_DRIVER -> skill(
        id, "Mass Driver",
        "Your Core turns heavy: fast impacts throw enemies and add Mass.",
        "Slams while it runs add 1 Mass to both enemies.",
        StatusElement.GRAVITIC, BuildTag.RAM, BuildTag.BULWARK, SkillTargeting.SELF, 0f, SkillFamily.MOTION,
        rank(11f, 2f, 1.25f, 0.1f, 700f, 500f),
        rank(10f, 2.25f, 1.35f, 0.15f, 760f, 500f),
        rank(9f, 2.5f, 1.45f, 0.2f, 820f, 500f),
    )
    SkillId.DECAY_SPORES -> skill(
        id, "Decay Spores",
        "Seed a crowd with Decay; the seeded spread rot and drop Data when they die.",
        "Each seeded death refunds 0.5 s to every skill, up to 3 s per cast.",
        StatusElement.ENTROPY, BuildTag.FORTUNE, BuildTag.CHAIN, SkillTargeting.AREA, 520f, SkillFamily.CATALYST,
        rank(11f, 170f, 4f, 6f, 8f),
        rank(9f, 205f, 6f, 6f, 8f),
        rank(7f, 240f, 7f, 6f, 8f),
    )
    SkillId.ENTROPIC_TIDE -> skill(
        id, "Entropic Tide",
        "Draws in all Decay around you: it bursts as damage and mends the Core.",
        "Healing past full integrity becomes shield.",
        StatusElement.ENTROPY, BuildTag.BULWARK, BuildTag.FORTUNE, SkillTargeting.SELF, 0f, SkillFamily.GUARD,
        rank(12f, 300f, 1f, 3f, 8f),
        rank(11f, 320f, 1.2f, 3.5f, 10f),
        rank(10f, 340f, 1.4f, 4f, 12f),
    )
    SkillId.SONIC_BOOM -> skill(
        id, "Sonic Boom",
        "A cone of sound blasts enemies away and erases shots; the recoil hops you back.",
        "Thrown enemies ring for 0.8 s; a slam into one forces Shatter.",
        StatusElement.SONIC, BuildTag.RAM, BuildTag.PRECISION, SkillTargeting.AIM, 300f, SkillFamily.STRIKE,
        rank(8f, 300f, 65f, 3f, 8f, 620f, 420f),
        rank(7f, 330f, 85f, 3f, 10f, 620f, 420f),
        rank(6f, 360f, 100f, 4f, 12f, 620f, 420f),
    )
    SkillId.RESONATOR -> skill(
        id, "Resonator",
        "Plants a resonator that pulses on the beat and replays reactions near it.",
        "Replays up to five reactions at 60% strength.",
        StatusElement.SONIC, BuildTag.ARSENAL, BuildTag.OVERCLOCK, SkillTargeting.AREA, 360f, SkillFamily.ZONE,
        rank(15f, 200f, 5f, 0.4f, 3f),
        rank(13f, 220f, 6f, 0.5f, 4f),
        rank(11f, 240f, 7f, 0.6f, 5f),
    )
    SkillId.VOID_STEP -> skill(
        id, "Void Step",
        "Step through the void toward the pointer, untouchable for a moment; a rift detonates where you were.",
        "Enemies along the path turn Brittle and take 1 Hollow.",
        StatusElement.VOID, BuildTag.PRECISION, BuildTag.DRIVE, SkillTargeting.AIM, 240f, SkillFamily.MOTION,
        rank(7f, 240f, 40f, 3f, 90f, 0.3f, 0.5f),
        rank(5.75f, 270f, 50f, 4f, 90f, 0.3f, 0.5f),
        rank(4.5f, 300f, 60f, 5f, 90f, 0.3f, 0.5f),
    )
    SkillId.NULL_SHROUD -> skill(
        id, "Null Shroud",
        "Swallow incoming fire, then spit it back.",
        "Returned shots pierce one enemy.",
        StatusElement.VOID, BuildTag.PRECISION, BuildTag.ARSENAL, SkillTargeting.SELF, 0f, SkillFamily.GUARD,
        rank(16f, 2.5f, 10f, 14f, 600f),
        rank(14.5f, 3.5f, 14f, 18f, 600f),
        rank(13f, 4.5f, 18f, 22f, 600f),
    )
    SkillId.SLINGSHOT -> skill(
        id, "Slingshot",
        "Swing your momentum onto your aim; counts as a sharp turn.",
        "Impacts during the swing keep your momentum.",
        null, BuildTag.DRIVE, BuildTag.RAM, SkillTargeting.SELF, 0f, SkillFamily.MOTION,
        rank(8.5f, 1.2f, 700f, 1_100f, 0.6f, 420f),
        rank(7f, 1.4f, 700f, 1_100f, 0.75f, 420f),
        rank(6f, 1.55f, 700f, 1_100f, 0.85f, 420f),
    )
    SkillId.FULL_STOP -> skill(
        id, "Full Stop",
        "Stop dead: your speed becomes a shockwave that breaks near-full statuses.",
        "Also counts as a Brake release.",
        null, BuildTag.ANCHOR, BuildTag.OVERCLOCK, SkillTargeting.SELF, 0f, SkillFamily.STRIKE,
        rank(10f, 160f, 0.7f, 0.6f),
        rank(9f, 190f, 0.85f, 0.5f),
        rank(8f, 220f, 1f, 0.4f),
    )
    SkillId.CATALYZE -> skill(
        id, "Catalyze",
        "Every enemy near the pointer carrying two elements reacts at once.",
        "Forces up to ten reactions at 130% strength.",
        null, BuildTag.CHAIN, BuildTag.OVERCLOCK, SkillTargeting.AREA, 420f, SkillFamily.CATALYST,
        rank(9f, 220f, 6f, 1f, 2f),
        rank(8f, 250f, 8f, 1.15f, 2f),
        rank(7f, 280f, 10f, 1.3f, 2f),
    )
    SkillId.DUAL_INFUSION -> skill(
        id, "Dual Infusion",
        "Your weapon sets up reactions: hits apply your two elements.",
        "Core impacts apply both elements too.",
        null, BuildTag.ARSENAL, BuildTag.FORTUNE, SkillTargeting.SELF, 0f, SkillFamily.CATALYST,
        rank(15f, 2.5f, 3f, 8f),
        rank(14f, 2.75f, 2f, 8f),
        rank(13f, 3f, 2f, 8f),
    )
}

private fun skill(
    id: SkillId,
    name: String,
    summary: String,
    rankThree: String,
    element: StatusElement?,
    firstTag: BuildTag,
    secondTag: BuildTag,
    targeting: SkillTargeting,
    reach: Float,
    family: SkillFamily,
    rankOne: SkillRank,
    rankTwo: SkillRank,
    rankThreeValues: SkillRank,
): SkillDefinition = SkillDefinition(
    id = id,
    name = name,
    summary = summary,
    rankThree = rankThree,
    element = element,
    tags = immutableListOf(firstTag, secondTag),
    targeting = targeting,
    reach = reach,
    family = family,
    ranks = immutableListOf(rankOne, rankTwo, rankThreeValues),
)

private fun rank(cooldownSeconds: Float, vararg values: Float): SkillRank =
    SkillRank(cooldownSeconds, values.toList().toImmutableList())
