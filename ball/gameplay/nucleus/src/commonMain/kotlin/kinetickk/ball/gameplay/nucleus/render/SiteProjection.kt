// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** A course mark's offset from its site's centre (circuit beacon, race gate, slalom pole). */
data class SiteMarkProjection(val dx: Float, val dy: Float)

/** A site as presentation draws it: a window offer or trial, an ambient site or the secret sigil. */
data class SiteProjection(
    val kind: SiteKind,
    /** English content key. */
    val name: String,
    val x: Float,
    val y: Float,
    val active: Boolean,
    /** Offer: seconds until it closes; timed trial: seconds left; untimed: 0. */
    val remaining: Float,
    /** Next course mark (circuit beacon 0..2, race gate, slalom pole). */
    val step: Int,
    val progress: Float,
    /** Enemies the site owns: vault defenders, marked foes, the bounty or secret boss. */
    val foeIds: ImmutableList<Int>,
    val warningRemaining: Float,
    val volleyAngle: Float,
    val course: ImmutableList<SiteMarkProjection> = immutableListOf(),
    /** Ring or arena radius drawn around an active site (brakeless: the current shrinking ring). */
    val radius: Float = 0f,
    val sides: Int = 0,
    /** Race: seconds since the start and the medal the clock still allows (3 gold … 0 none). */
    val clock: Float = 0f,
    val medal: Int = 0,
    val activationRadius: Float = 65f,
    /** Off-screen reveal distance for markers and the minimap (0: never). */
    val reveal: Float = Float.POSITIVE_INFINITY,
    /** Untouched volley aim offset from (x, y). */
    val aimDx: Float = 0f,
    val aimDy: Float = 0f,
)

enum class TrialClock { COUNTDOWN, ELAPSED_TENTHS, NONE }
enum class TrialValue { COUNT, SECONDS, NONE }

/** Reward label of the trial panel. */
enum class TrialReward {
    WEAPON, RELIC, ITEM_AND_REPAIR, ITEM, KEY_AND_RELIC, MATTER, MEDAL, UNKNOWN, STRUCTURE_REWARDS, BLESSING, PACT, TROVE,
}

/** Which engagement the trial panel shows. */
enum class TrialSource { SITE, STRUCTURE, DIMENSION }

/** The one HUD trial panel's facts; Gameplay maps whichever engagement is active (site, dungeon, dimension). */
data class TrialProjection(
    val family: SiteFamily,
    val kind: SiteKind?,
    /** English content key. */
    val title: String,
    /** English content key for the (!) button. */
    val rules: String,
    val clock: TrialClock,
    val seconds: Float,
    /** 1..24. */
    val segments: Int,
    /** 0..1. */
    val progress: Float,
    val value: TrialValue,
    val done: Float,
    val required: Float,
    val reward: TrialReward,
    val medal: Int = 0,
    val source: TrialSource = TrialSource.SITE,
    val structure: StructureTemplateId? = null,
    val dimension: WorldLayer? = null,
)
