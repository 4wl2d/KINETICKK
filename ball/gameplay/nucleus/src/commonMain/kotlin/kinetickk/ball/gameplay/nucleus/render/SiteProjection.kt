// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldLayer

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
