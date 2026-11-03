// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.ball.gameplay.nucleus.model.TotemChest
import kinetickk.ball.gameplay.nucleus.render.TotemChestProjection
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.mapToImmutableList

/**
 * The waiting Totem chests, published in realspace only (a dimension hides them; none is claimable there). The list is
 * replaced, never mutated, so the same list as the [identitySource]'s keeps [previous]; otherwise equal chests (raw
 * float bits) keep it, and none at all publishes the shared empty list.
 */
internal fun MutableGameState.totemChestProjections(
    previous: ImmutableList<TotemChestProjection>?,
    identitySource: MutableGameState?,
): ImmutableList<TotemChestProjection> {
    val chests = structures.totemChests
    if (!inBaseLayer() || chests.isEmpty()) return previous?.takeIf { it.isEmpty() } ?: NO_CHESTS
    chests.reuseIfIdentical(identitySource?.structures?.totemChests, previous)?.let { return it }
    chests.reuseProjectionIfContentEqual(previous) { value, projected -> projected.matches(value) }?.let { return it }
    return chests.mapToImmutableList { TotemChestProjection(it.structureId, it.x, it.y, it.charges, it.bornAt, it.armedAt) }
}

private fun TotemChestProjection.matches(chest: TotemChest): Boolean =
    id == chest.structureId &&
        x.sameBitsAs(chest.x) &&
        y.sameBitsAs(chest.y) &&
        charges == chest.charges &&
        bornAt.sameBitsAs(chest.bornAt) &&
        armedAt.sameBitsAs(chest.armedAt)

private val NO_CHESTS: ImmutableList<TotemChestProjection> = immutableListOf()
