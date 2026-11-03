// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.StructureNotice
import kinetickk.ball.content.api.StructurePolicy
import kinetickk.ball.content.api.StructureTemplate
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.TotemChest
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*

/*
 * Totem chests, the dungeon reward that outlives its structure. The single writer of StructureRunState.totemChests and
 * the only place that spends a Key (keys--, in openTotemChest).
 * - A placed structure's chest is a pure hash of the run seed, the layer and its cell (never a run stream), so the
 *   dungeon panel and the promise plates show it before entry and nothing can re-roll it.
 * - Clearing the final room materialises it at the final room's centre; a full list salvages its charges as Matter.
 * - While running in realspace with a Key, the Core's swept path meeting an armed chest spends one Key and one charge
 *   and opens the TOTEM choice; the chest re-arms after a moment of play and shatters with its last charge. At most
 *   one chest opens per step. Without a Key it waits, locked, for the rest of the run, also through the Architect.
 * Nothing here draws from a stream or allocates outside a finish, an open or a rebase.
 */

/** Salt of the Totem chest roll (the structure family uses 0002 placement, 0003 garrison, 0004 pool). */
internal const val TOTEM_CHEST_SALT: Long = 0x57C0_C7E5_5EED_0005L

/**
 * Charges of the Totem chest a [template] structure in the absolute cell rolls (0: none): the chance, then a weighted
 * charge count raised to the template's minimum. Both hash draws are always taken, in this order.
 */
internal fun totemChestCharges(
    policy: StructurePolicy,
    template: StructureTemplate,
    runSeed: Int,
    layer: WorldLayer,
    cellX: Int,
    cellY: Int,
): Int {
    val random = HashRandom(worldHash(runSeed, layer, cellX, cellY, TOTEM_CHEST_SALT))
    val chance = random.nextFloat()
    val pick = random.nextFloat()
    val chest = policy.totemChest
    if (!chest.enabled || chance >= template.totemChestChance) return 0
    val weights = chest.chargeWeights
    var total = 0f
    for (index in weights.indices) total += weights[index]
    val target = pick * total
    var sum = 0f
    // The fallback covers float rounding as the pick nears 1.
    var charges = weights.size
    for (index in weights.indices) {
        sum += weights[index]
        if (target < sum) {
            charges = index + 1
            break
        }
    }
    return maxOf(charges, template.totemChestMinCharges)
}

/**
 * The chest of the structure [structureId] appears at ([x], [y]) with [charges] (none for 0). A full list salvages the
 * charges as base Matter instead (the run's Matter bonuses apply) with its notice.
 */
internal fun MutableGameState.materializeTotemChest(structureId: Long, x: Float, y: Float, charges: Int) {
    if (charges <= 0) return
    val policy = content.structures.totemChest
    val chests = structures.totemChests
    if (chests.size >= ContentBounds.MAX_TOTEM_CHESTS) {
        grantMatter(charges * policy.salvageMatterPerCharge.toFloat())
        message = StructureNotice.TOTEM_CHEST_SALVAGED.key
        messageTime = 2.2f
        return
    }
    val chest = TotemChest(structureId, x, y, charges, bornAt = elapsed, armedAt = elapsed + policy.materializeSeconds)
    val next = ArrayList<TotemChest>(chests.size + 1)
    next += chests
    next += chest
    structures.totemChests = next
}

/**
 * The claim step, just before pending choices open: while running in realspace with a Key, the first armed chest the
 * Core's swept path meets opens one charge. Allocation-free when nothing opens.
 */
internal fun MutableGameState.updateTotemChests() {
    if (phase != GamePhase.RUNNING || !inBaseLayer() || keys <= 0) return
    val chests = structures.totemChests
    if (chests.isEmpty()) return
    val radius = content.structures.totemChest.contactRadius
    for (index in chests.indices) {
        val chest = chests[index]
        if (elapsed < chest.armedAt) continue
        if (!segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, chest.x, chest.y, radius)) continue
        openTotemChest(index)
        return
    }
}

/** Spends one Key and one charge of chest [index]: it re-arms, or shatters with its last charge; the TOTEM choice opens. */
private fun MutableGameState.openTotemChest(index: Int) {
    val chests = structures.totemChests
    val chest = chests[index]
    keys--
    val left = chest.charges - 1
    if (left == 0) {
        val next = ArrayList<TotemChest>(chests.size - 1)
        for (at in chests.indices) if (at != index) next += chests[at]
        structures.totemChests = next
        shockwave(chest.x, chest.y, 0.6f, 220f, 3)
        burst(chest.x, chest.y, 32, 3)
        emitSound(GameplayAudioCue.IMPACT)
    } else {
        val next = ArrayList<TotemChest>(chests)
        next[index] = chest.copy(charges = left, armedAt = elapsed + content.structures.totemChest.rearmSeconds)
        structures.totemChests = next
    }
    emitSound(GameplayAudioCue.PICKUP)
    openTotemChoice()
}

/** A new run: no chest waits. */
internal fun MutableGameState.resetTotemChests() {
    structures.totemChests = emptyList()
}

/** Chests follow a rebase (their times are run times and stay); an empty list keeps its instance. */
internal fun MutableGameState.rebaseTotemChests(dx: Float, dy: Float) {
    val chests = structures.totemChests
    if (chests.isEmpty()) return
    val next = ArrayList<TotemChest>(chests.size)
    for (index in chests.indices) next += chests[index].shifted(dx, dy)
    structures.totemChests = next
}
