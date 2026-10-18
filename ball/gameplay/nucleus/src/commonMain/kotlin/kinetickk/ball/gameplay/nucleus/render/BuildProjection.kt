// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** How two bound relics interact: a named pairing, or the generic key rule. */
enum class RelicLinkKind { CONCORD, DISCORD, HARMONY, DISSONANCE }

data class RelicLinkProjection(
    val first: RelicId,
    val second: RelicId,
    val kind: RelicLinkKind,
    /** The named pairing, when one exists; presentation hides its name until [discovered]. */
    val pairing: RelicPairingId?,
    val discovered: Boolean,
)

data class SeveredRelicProjection(val id: RelicId, val remaining: Float)

/** Immutable presentation of the compiled build, produced once per accepted frame that changes it. */
data class BuildProjection(
    val tagPoints: ImmutableList<Int> = immutableListOf(),
    val tagTiers: ImmutableList<Int> = immutableListOf(),
    val affinity: ImmutableList<Int> = immutableListOf(),
    val affinityTiers: ImmutableList<Int> = immutableListOf(),
    val dominant: StatusElement? = null,
    val secondary: StatusElement? = null,
    val links: ImmutableList<RelicLinkProjection> = immutableListOf(),
    /** Upside multiplier per RelicId ordinal (1 when unbound). */
    val relicUpsides: ImmutableList<Float> = immutableListOf(),
    val severed: ImmutableList<SeveredRelicProjection> = immutableListOf(),
    /** Owned items whose effects bound relic liabilities disable entirely. */
    val suppressedItemIds: ImmutableList<Int> = immutableListOf(),
    val rebornItemIds: ImmutableList<Int> = immutableListOf(),
    val reachableReactions: ImmutableList<ReactionId> = immutableListOf(),
    val stormCharge: Float = 0f,
    val stasisMeter: Float = 1f,
    val stasisBank: Int = 0,
    val ouroborosSpent: Boolean = false,
    val invulnerable: Boolean = false,
    val overdriveBurnout: Boolean = false,
    val breakThresholds: ImmutableList<Int> = immutableListOf(),
) {
    fun tagPoints(tag: BuildTag): Int = tagPoints.getOrElse(tag.ordinal) { 0 }
    fun tagTier(tag: BuildTag): Int = tagTiers.getOrElse(tag.ordinal) { 0 }
    fun affinity(element: StatusElement): Int = affinity.getOrElse(element.ordinal) { 0 }
    fun affinityTier(element: StatusElement): Int = affinityTiers.getOrElse(element.ordinal) { 0 }
    fun relicUpside(id: RelicId): Float = relicUpsides.getOrElse(id.ordinal) { 1f }
    fun breakThreshold(element: StatusElement): Int = breakThresholds.getOrElse(element.ordinal) { 0 }
}

/** A build consequence of taking an offered card. */
enum class ForecastKind { SUPPRESSED, REACTION, TAG_TIER, AFFINITY, CONCORD, DISCORD, HARMONY, DISSONANCE }

data class BuildForecast(
    val kind: ForecastKind,
    val tag: BuildTag? = null,
    val element: StatusElement? = null,
    val reaction: ReactionId? = null,
    val pairing: RelicPairingId? = null,
    val relic: RelicId? = null,
    val before: Int = 0,
    val after: Int = 0,
    val discovered: Boolean = true,
)
