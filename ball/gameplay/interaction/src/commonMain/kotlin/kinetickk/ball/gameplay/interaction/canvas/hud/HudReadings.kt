// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kinetickk.ball.gameplay.interaction.canvas.*

/** Integrity reads critical at or below a fifth of its maximum (`HUD-Critical` board). */
internal fun isIntegrityCritical(hp: Float, maxHp: Float): Boolean = hp <= maxHp.coerceAtLeast(1f) * 0.2f

/** Integrity cells of 20 points each (SPEC Meter component), bounded for very large pools. */
internal fun integritySegments(maxHp: Float): Int = ceil(maxHp / 20f).toInt().coerceIn(1, 40)

/** Shield cells of 20 points each, at most six. */
internal fun shieldCells(maxShield: Float): Int = ceil(maxShield / 20f).toInt().coerceIn(1, 6)

internal fun filledShieldCells(shield: Float, maxShield: Float, cells: Int): Int =
    if (maxShield <= 0f || shield <= 0f) 0 else ceil(shield / maxShield * cells - 0.001f).toInt().coerceIn(0, cells)

/** Dash charge pips: the heat headroom counted in dash costs, rounded (a fresh core's chain length). */
internal fun dashChargeCapacity(dashHeatCost: Float): Int =
    floor(GameplayRenderModel.MAX_HEAT / dashHeatCost.coerceAtLeast(1f) + 0.5f).toInt().coerceIn(1, 8)

/** Dashes the core can chain from [heat] before overheating (0 while overheated). */
internal fun dashChargesReady(heat: Float, dashHeatCost: Float, overheated: Boolean): Int {
    if (overheated) return 0
    val ready = floor((GameplayRenderModel.MAX_HEAT - heat) / dashHeatCost.coerceAtLeast(1f) + 0.5f).toInt()
    return ready.coerceIn(0, dashChargeCapacity(dashHeatCost))
}

/** Lit ticks of the velocity ladder for [speed] on the shared logarithmic speed scale. */
internal fun velocityLadderLit(speed: Float, count: Int): Int = (speedVisualRatio(speed) * count).roundToInt().coerceIn(0, count)

/** Horizontal stretch of the speed number by velocity tier (1 at rest, 1.34 at the top tier). */
internal fun speedStretch(velocityTier: Int): Float = 1f + velocityTier.coerceIn(0, 4) / 4f * 0.34f

/** The level badge's tier this frame: the decade, or APEX at the content's level cap. */
internal fun GameplayRenderModel.levelTier(): KkLevelTier = KkLevelTier.of(level.coerceAtLeast(0), maxLevel)

/**
 * The Data meters' fill 0..1: the level's Data share below the cap; at the cap they show the Apex
 * (the recharged share while it cools, full while ready, the remaining activation while it runs).
 */
internal fun GameplayRenderModel.dataMeterFill(tier: KkLevelTier, apexState: KkApexState): Float = when {
    tier != KkLevelTier.T6 -> (data.toFloat() / nextLevelData.coerceAtLeast(1)).coerceIn(0f, 1f)
    apexState == KkApexState.READY -> 1f
    apexState == KkApexState.ACTIVE -> apex.active01.coerceIn(0f, 1f)
    else -> (1f - apex.cooldown01).coerceIn(0f, 1f)
}
