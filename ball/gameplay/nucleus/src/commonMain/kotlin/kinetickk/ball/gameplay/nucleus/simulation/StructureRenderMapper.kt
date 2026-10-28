// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.StructureRenderProjection

/**
 * Structures, walls, signals and the dungeon as presentation draws them. Nothing places a structure yet, so the
 * projection is always the shared [StructureRenderProjection.EMPTY].
 */
@Suppress("UNUSED_PARAMETER")
internal fun MutableGameState.structureRenderProjection(
    previous: StructureRenderProjection?,
    identitySource: MutableGameState?,
): StructureRenderProjection = StructureRenderProjection.EMPTY
