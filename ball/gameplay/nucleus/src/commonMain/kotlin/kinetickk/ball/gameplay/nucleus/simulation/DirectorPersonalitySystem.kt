// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectorPersonalityDefinition
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.DirectorPolicy
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.PhaseScales
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/*
 * Director personalities and onslaught themes. Each run rolls one personality from a stateless hash of its seed. The
 * personality scales the director's phase rates and durations, its event weights, pack sizes and cooldowns and the
 * weight of each ordinary kind; its loop budget stays within 5 % of the plain director's. Every surge features one
 * ordinary kind, its onslaught theme, picked by a stateless hash of the surge's index. Neither draws from a stream, and
 * a run without a personality or theme paces exactly like the plain director.
 */

/** The personality [policy] rolls for [runSeed]: a pick by weight from a stateless hash, or none while they are off. */
internal fun directorPersonalityFor(runSeed: Int, policy: DirectorPolicy): DirectorPersonalityId? {
    if (!policy.personalitiesEnabled) return null
    val personalities = policy.personalities
    var total = 0f
    for (index in personalities.indices) total += personalities[index].weight
    var roll = hashUnit(runHash(runSeed, PERSONALITY_SALT, 0L)) * total
    for (index in personalities.indices) {
        roll -= personalities[index].weight
        if (roll < 0f) return personalities[index].id
    }
    return personalities[personalities.lastIndex].id
}

/**
 * Makes [id] the run's personality and sets the featured aspect that follows from it and the Keynote: the aspect weak to
 * the Keynote element (none without a Keynote). The Elemental Storm needs an aspect ordinary kinds carry, so it replaces
 * any other (or none) with one of [STORM_ASPECTS], picked by a stateless hash.
 */
internal fun MutableGameState.applyDirectorPersonality(id: DirectorPersonalityId?) {
    directorPersonality = id
    featuredAspect = keynoteElement?.let(::weakAspectFor) ?: -1
    if (id == DirectorPersonalityId.ELEMENTAL_STORM && STORM_ASPECTS.none { it.ordinal == featuredAspect }) {
        val pick = (hashUnit(runHash(runSeed, STORM_ASPECT_SALT, 0L)) * STORM_ASPECTS.size).toInt()
        featuredAspect = STORM_ASPECTS[pick.coerceAtMost(STORM_ASPECTS.lastIndex)].ordinal
    }
}

/** The aspects ordinary kinds carry, in the order the Elemental Storm picks from. */
internal val STORM_ASPECTS: ImmutableList<StatusElement> = immutableListOf(
    StatusElement.THERMAL, StatusElement.CRYO, StatusElement.ION, StatusElement.GRAVITIC, StatusElement.ENTROPY,
)

/** The run's personality definition, or null for the plain director. */
private fun MutableGameState.personality(): DirectorPersonalityDefinition? =
    directorPersonality?.let { content.variety.director.personalities[it.ordinal] }

private fun PhaseScales.of(phase: SpawnDirectorPhase): Float = when (phase) {
    SpawnDirectorPhase.CALM -> calm
    SpawnDirectorPhase.BUILD -> build
    SpawnDirectorPhase.SURGE -> surge
    SpawnDirectorPhase.BREATHER -> breather
}

internal fun MutableGameState.personalityDurationScale(phase: SpawnDirectorPhase): Float =
    personality()?.phaseDuration?.of(phase) ?: 1f

internal fun MutableGameState.personalityRateScale(phase: SpawnDirectorPhase): Float =
    personality()?.phaseRate?.of(phase) ?: 1f

internal fun MutableGameState.personalityBudgetScale(): Float = personality()?.budgetScale ?: 1f

/** Scale of the [SpawnEventKind] with ordinal [kind] in the event roll; ambushes are planned outside the roll. */
internal fun MutableGameState.personalityEventScale(kind: Int): Float {
    val weights = personality()?.eventWeights ?: return 1f
    return when (SpawnEventKind.entries[kind]) {
        SpawnEventKind.SINGLE -> weights.single
        SpawnEventKind.PACK -> weights.pack
        SpawnEventKind.FLANK -> weights.flank
        SpawnEventKind.SWARM -> weights.swarm
        SpawnEventKind.WALL -> weights.wall
        SpawnEventKind.ENCIRCLE -> weights.encircle
        SpawnEventKind.AMBUSH -> 1f
    }
}

internal fun MutableGameState.personalityPackBonus(): Int = personality()?.packSizeBonus ?: 0

internal fun MutableGameState.ambushCooldownScale(): Float = personality()?.ambushCooldownScale ?: 1f

internal fun MutableGameState.encircleCooldownScale(): Float = personality()?.encircleCooldownScale ?: 1f

internal fun MutableGameState.wallCooldownScale(): Float = personality()?.wallCooldownScale ?: 1f

/** The personality's weight on [type]: its bias, times its aspect weight when it features [type]'s aspect. */
internal fun MutableGameState.personalityTypeMultiplier(type: EnemyType): Float {
    val definition = personality() ?: return 1f
    val featured = definition.featuresAspect && featuredAspect >= 0 && type.profile.aspect == featuredAspect
    return definition.typeMultipliers[type.ordinal] * if (featured) definition.aspectWeight else 1f
}

/** The onslaught theme's weight: [type] is the current surge's featured kind. */
internal fun MutableGameState.surgeThemeMultiplier(type: EnemyType): Float =
    if (type.ordinal == surgeThemeType) content.variety.director.surgeThemeWeight else 1f

/**
 * A new phase: a surge counts itself and, with enough eligible ordinary kinds, features one of them, picked by weight
 * (personality included, recency and the old theme left out) from a stateless hash of its index, and announces it.
 * Every other phase has no theme.
 */
internal fun MutableGameState.onDirectorPhaseStarted(phase: SpawnDirectorPhase) {
    surgeThemeType = -1
    if (phase != SpawnDirectorPhase.SURGE) return
    surgeIndex++
    val director = content.variety.director
    if (!director.surgeThemesEnabled) return
    val roster = content.enemies
    var eligible = 0
    var total = 0f
    for (index in roster.indices) {
        val weight = themeWeight(roster[index])
        if (weight <= 0f) continue
        eligible++
        total += weight
    }
    if (eligible < director.surgeThemeMinimumKinds) return
    var roll = hashUnit(runHash(runSeed, SURGE_THEME_SALT, surgeIndex.toLong())) * total
    var theme: EnemyDefinition? = null
    for (index in roster.indices) {
        val definition = roster[index]
        val weight = themeWeight(definition)
        if (weight <= 0f) continue
        theme = definition
        roll -= weight
        if (roll < 0f) break
    }
    val featured = theme ?: return
    surgeThemeType = featured.type.ordinal
    message = onslaughtBanner(featured)
    messageTime = ONSLAUGHT_BANNER_SECONDS
}

/** A kind's weight as an onslaught theme: its ordinary weight now (zero for scheduled kinds), without recency. */
private fun MutableGameState.themeWeight(definition: EnemyDefinition): Float =
    typeWeight(definition, weakOnly = false, recency = false)

/**
 * The first run-start announcement: once the run reaches the announcement time, the banner names the hostile pattern
 * (a run without a personality passes the stage silently).
 */
internal fun MutableGameState.announceHostilePattern() {
    if (varietyAnnouncement >= HOSTILE_PATTERN_ANNOUNCEMENT || elapsed < content.variety.director.announceAtSeconds) return
    varietyAnnouncement = HOSTILE_PATTERN_ANNOUNCEMENT
    val definition = personality() ?: return
    message = hostilePatternBanner(definition)
    messageTime = HOSTILE_PATTERN_BANNER_SECONDS
}

/** The hostile pattern banner, an English content key the feed localizes. */
internal fun hostilePatternBanner(definition: DirectorPersonalityDefinition): String =
    "HOSTILE PATTERN // ${definition.name.uppercase()}"

/** The onslaught banner naming a surge's featured kind, an English content key the feed localizes. */
internal fun onslaughtBanner(definition: EnemyDefinition): String = "ONSLAUGHT // ${definition.name.uppercase()}"

/** [MutableGameState.varietyAnnouncement] once the hostile pattern banner's time has passed. */
internal const val HOSTILE_PATTERN_ANNOUNCEMENT: Int = 1

/** How long the hostile pattern banner shows (it follows the 2.2 s run-start banner). */
internal const val HOSTILE_PATTERN_BANNER_SECONDS: Float = 2.4f

/** How long a surge's onslaught banner shows. */
internal const val ONSLAUGHT_BANNER_SECONDS: Float = 1.6f
