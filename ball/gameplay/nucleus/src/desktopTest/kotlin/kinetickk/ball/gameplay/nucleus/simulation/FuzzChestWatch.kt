// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.structures.*

/**
 * Fuzz invariants of the structures domain's run-long state, checked after every frame and choice action: Totem chests
 * stay within their bound with 1..3 charges at finite run-local points, the window never holds a spent structure, the
 * held dungeon is never spent, and a Key is spent only from a running realspace step (a chest opens only there). One
 * watch per harness; a new run (a new model) starts it over.
 */
internal class FuzzChestWatch {
    private var model: MutableGameState? = null
    private var keys = 0
    private var phase = GamePhase.RUNNING
    private var layer = WorldLayer.BASE

    fun check(m: MutableGameState, record: (FuzzInvariant, String) -> Unit) {
        val chests = m.structures.totemChests
        if (chests.size > ContentBounds.MAX_TOTEM_CHESTS) {
            record(FuzzInvariant.COLLECTION_BOUND, "totem chests ${chests.size} > ${ContentBounds.MAX_TOTEM_CHESTS}")
        }
        for (index in chests.indices) {
            val chest = chests[index]
            if (chest.charges !in 1..ContentBounds.MAX_TOTEM_CHEST_CHARGES) record(FuzzInvariant.STAT_RANGE, "chest charges $chest")
            if (!chest.x.isFinite() || !chest.y.isFinite()) record(FuzzInvariant.NON_FINITE, "chest position $chest")
        }
        val structures = m.structures.structureField.structures
        for (index in structures.indices) {
            val id = structures[index].id
            if (m.isSpent(id)) record(FuzzInvariant.ENGAGEMENT_OWNER, "the window holds the spent structure $id")
        }
        m.structures.dungeon?.let { held ->
            if (m.isSpent(held.structureId)) record(FuzzInvariant.ENGAGEMENT_OWNER, "the held dungeon ${held.structureId} is spent")
        }
        val fromRealspaceStep = phase == GamePhase.RUNNING && layer == WorldLayer.BASE && m.worldLayer == WorldLayer.BASE
        if (model === m && m.keys < keys && !fromRealspaceStep) {
            record(FuzzInvariant.STAT_RANGE, "a Key was spent outside a running realspace step ($keys -> ${m.keys} after $phase in $layer)")
        }
        model = m
        keys = m.keys
        phase = m.phase
        layer = m.worldLayer
    }
}
