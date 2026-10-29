// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SitePolicy
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteMarkProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.TrialClock
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.ball.gameplay.nucleus.render.TrialValue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * The trial panel facts Gameplay publishes with an active [site] (its render mapping's table, under the shipped
 * [policy]), for HUD fixtures that build their sites by hand. The nucleus tests pin the real mapping.
 */
internal fun siteTrial(site: SiteProjection, policy: SitePolicy = SitePolicy()): TrialProjection {
    val definition = policy.definition(site.kind)
    val challenges = policy.challenges
    val directed = when (definition.reward.directed) {
        DirectedReward.WEAPON -> TrialReward.WEAPON
        DirectedReward.RELIC -> TrialReward.RELIC
        DirectedReward.ITEM_AND_REPAIR -> TrialReward.ITEM_AND_REPAIR
        null -> TrialReward.UNKNOWN
    }
    fun trial(clock: TrialClock, segments: Int, value: TrialValue, required: Float, reward: TrialReward) = TrialProjection(
        family = site.kind.family,
        kind = site.kind,
        title = definition.name,
        rules = definition.instruction,
        clock = clock,
        seconds = when (clock) {
            TrialClock.COUNTDOWN -> site.remaining
            TrialClock.ELAPSED_TENTHS -> site.clock
            TrialClock.NONE -> 0f
        },
        segments = segments.coerceIn(1, 24),
        progress = site.progress,
        value = value,
        done = site.progress * required,
        required = required,
        reward = reward,
        rewardMatter = if (site.kind.family == SiteFamily.CHALLENGE) definition.reward.matter else 0,
        medal = site.medal,
    )
    fun seconds(required: Float) = trial(TrialClock.COUNTDOWN, ceil(required).toInt(), TrialValue.SECONDS, required, directed)
    return when (site.kind) {
        SiteKind.RESONANT_CIRCUIT -> trial(TrialClock.COUNTDOWN, 3, TrialValue.COUNT, 3f, directed)
        SiteKind.SEALED_ANOMALY -> trial(TrialClock.COUNTDOWN, site.foeIds.size, TrialValue.COUNT, 3f, directed)
        SiteKind.COLLAPSING_ORBIT -> seconds(policy.windows.orbitRequiredSeconds)
        SiteKind.GATE_RACE -> trial(TrialClock.ELAPSED_TENTHS, site.course.size, TrialValue.COUNT, site.course.size.toFloat(), TrialReward.MEDAL)
        SiteKind.BRAKELESS_TRIAL -> seconds(challenges.brakelessRequired)
        SiteKind.UNTOUCHED_TRIAL -> seconds(challenges.untouchedRequired)
        SiteKind.VELOCITY_TRIAL -> seconds(challenges.velocityRequired)
        SiteKind.RAM_TRIAL -> trial(TrialClock.COUNTDOWN, challenges.ramRequired, TrialValue.COUNT, challenges.ramRequired.toFloat(), directed)
        SiteKind.SLALOM_TRIAL -> trial(TrialClock.COUNTDOWN, challenges.slalomPoles, TrialValue.COUNT, challenges.slalomPoles.toFloat(), directed)
        SiteKind.BOUNTY_ARENA -> trial(TrialClock.NONE, 3, TrialValue.NONE, 0f, TrialReward.KEY_AND_RELIC)
        SiteKind.FAR_SIGIL -> trial(TrialClock.NONE, 3, TrialValue.NONE, 0f, TrialReward.UNKNOWN)
        SiteKind.DRIFT_CACHE, SiteKind.LOST_CURSOR, SiteKind.GOLDEN_DRIFTER, SiteKind.QUIET_SIGNAL, SiteKind.ZENITH_RIFT,
        SiteKind.NADIR_RIFT,
        -> error("${site.kind} runs no trial")
    }
}

/** This model with [sites] and the trial of the active one, as Gameplay publishes them together. */
internal fun GameplayRenderModel.withSites(vararg sites: SiteProjection): GameplayRenderModel =
    with("sites" to sites.toList().toImmutableList(), "trial" to sites.firstOrNull { it.active }?.let { siteTrial(it, content.sites) })

/** A course of [count] marks on a loop of [radius] around its site (race gates or slalom poles). */
internal fun courseOf(count: Int, radius: Float = 1_300f): ImmutableList<SiteMarkProjection> = List(count) { index ->
    val angle = index * 0.9f
    SiteMarkProjection(cos(angle) * radius, sin(angle) * radius)
}.toImmutableList()

/**
 * One running site of every kind with a trial, at ([x], [y]): the three anomalies, the gate race (silver still in
 * reach), the five challenges, the bounty and the summoned sigil.
 */
internal fun trialSites(x: Float = 0f, y: Float = 0f, policy: SitePolicy = SitePolicy()): List<SiteProjection> {
    fun site(kind: SiteKind, remaining: Float, step: Int, progress: Float, foes: ImmutableList<Int> = immutableListOf(),
        course: ImmutableList<SiteMarkProjection> = immutableListOf(), radius: Float = 0f, clock: Float = 0f, medal: Int = 0) =
        SiteProjection(kind, policy.definition(kind).name, x, y, true, remaining, step, progress, foes, 0f, 0f,
            course = course, radius = radius, clock = clock, medal = medal)
    return listOf(
        site(SiteKind.RESONANT_CIRCUIT, 12f, 1, 1f / 3f),
        site(SiteKind.SEALED_ANOMALY, 12f, 0, 1f / 3f, immutableListOf(1, 2, 3)),
        site(SiteKind.COLLAPSING_ORBIT, 12f, 0, 0.4f),
        site(SiteKind.GATE_RACE, 8.4f, 2, 2f / 7f, course = courseOf(7), clock = 6.3f, medal = 2),
        site(SiteKind.BRAKELESS_TRIAL, 14f, 0, 0.5f, radius = 700f, clock = 6f),
        site(SiteKind.RAM_TRIAL, 14f, 0, 0.5f, immutableListOf(11, 12, 13, 14, 15, 16, 17, 18), radius = 650f),
        site(SiteKind.UNTOUCHED_TRIAL, 16f, 0, 0.25f, radius = 650f),
        site(SiteKind.VELOCITY_TRIAL, 19f, 0, 0.6f, radius = 1_800f),
        site(SiteKind.SLALOM_TRIAL, 11f, 3, 0.5f, course = courseOf(6, 900f)),
        site(SiteKind.BOUNTY_ARENA, 0f, 0, 0.3f, immutableListOf(40), radius = 900f, clock = 21f),
        site(SiteKind.FAR_SIGIL, 0f, 0, 0f, immutableListOf(41)),
    )
}

/** The window kinds a POI window can offer (anomalies, race, challenges, bounty, rifts). */
internal val WindowSiteKinds: List<SiteKind> = SiteKind.entries.filter {
    it.family in setOf(SiteFamily.ANOMALY, SiteFamily.RACE, SiteFamily.CHALLENGE, SiteFamily.BOUNTY, SiteFamily.PORTAL)
}
