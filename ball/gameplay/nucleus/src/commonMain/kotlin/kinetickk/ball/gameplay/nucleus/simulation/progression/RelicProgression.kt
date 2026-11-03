// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*

internal fun MutableGameState.openRelicChoice() {
    activeChoiceType = ChoiceType.RELIC
    buildRelicChoices()
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

internal fun MutableGameState.openRelicBindChoice() {
    activeChoiceType = ChoiceType.RELIC_BIND
    val action = pendingRelicBindAction ?: return
    val incoming = pendingBindingRelic
    choices = equippedRelics.mapIndexedNotNull { index, equipped ->
        val current = content.relic(equipped.id)
        when (action) {
            RelicChoiceAction.REPLACE -> {
                val replacement = content.relic(incoming ?: return)
                if (equipped.id == RelicId.OUROBOROS_SEAL && ouroborosLockedUntil > elapsed) return@mapIndexedNotNull null
                ChoiceOption(
                    type = ChoiceType.RELIC_BIND,
                    title = "Replace ${current.name}",
                    description = "Break slot ${index + 1} and bind ${replacement.name} at rank 1.",
                    tag = "SLOT ${index + 1} // REPLACE",
                    relicId = replacement.id,
                    relicAction = RelicChoiceAction.REPLACE,
                    relicSlot = index,
                )
            }
            RelicChoiceAction.MELD_TARGET -> ChoiceOption(
                type = ChoiceType.RELIC_BIND,
                title = "Meld ${current.name}",
                description = if (canMeld(equipped.id)) {
                    "Collapse the offering into slot ${index + 1} and advance rank ${equipped.rank} to ${equipped.rank + 1}."
                } else {
                    "Slot ${index + 1} cannot be melded further; salvage the excess resonance."
                },
                tag = "SLOT ${index + 1} // RANK ${equipped.rank}",
                relicId = equipped.id,
                relicAction = RelicChoiceAction.MELD_TARGET,
                relicSlot = index,
            )
            RelicChoiceAction.ACQUIRE, RelicChoiceAction.MELD, RelicChoiceAction.DECLINE -> return
        }
    }
    if (choices.isEmpty()) {
        pendingBindingRelic = null
        pendingRelicBindAction = null
        grantMatter(content.relicPolicy.salvageMatter.toFloat())
        return
    }
    clearInputBuffers()
    phase = GamePhase.CHOICE
}

private fun MutableGameState.discoverRelic(id: RelicId) {
    val bit = 1L shl id.ordinal
    if (discoveredRelicMask and bit == 0L) {
        discoveredRelicMask = discoveredRelicMask or bit
        pendingDiscoveredRelicMask = pendingDiscoveredRelicMask or bit
    }
}

internal fun MutableGameState.acquireRelic(id: RelicId) {
    val currentIndex = equippedRelics.indexOfFirst { it.id == id }
    if (currentIndex >= 0) {
        discoverRelic(id)
        val current = equippedRelics[currentIndex]
        if (current.rank >= content.relicPolicy.maxRank || id == RelicId.HOLLOW_CROWN) {
            grantMatter(content.relicPolicy.salvageMatter.toFloat())
            message = content.relic(id).name.uppercase() + " // RESONANCE SALVAGED"
        } else {
            setRelicRank(currentIndex, current.rank + 1)
            message = content.relic(id).name.uppercase() + " // RANK " + (current.rank + 1)
        }
        messageTime = 1.7f
        return
    }
    if (equippedRelics.size >= content.relicPolicy.maxSlots) return
    discoverRelic(id)
    equippedRelics = equippedRelics + EquippedRelic(id, 1)
    relicRanks[id.ordinal] = 1
    severedRelics = severedRelics.filter { it.id != id }
    recordRelicGain(id)
    onRelicSetChanged()
    message = content.relic(id).name.uppercase() + " // BOUND"
    messageTime = 1.7f
}

private fun MutableGameState.setRelicRank(slot: Int, rank: Int) {
    val current = equippedRelics[slot]
    val updated = equippedRelics.toMutableList()
    updated[slot] = current.copy(rank = rank)
    equippedRelics = updated.toList()
    relicRanks[current.id.ordinal] = rank
    // Rank three adds a second affinity point, which is a newer gain than the bind.
    if (rank >= 3) recordRelicGain(current.id)
    onRelicSetChanged()
}

/** Replacing a relic severs it: its liability lingers for the policy's severance time. */
internal fun MutableGameState.replaceRelic(slot: Int, id: RelicId) {
    if (slot !in equippedRelics.indices) return
    val replaced = equippedRelics[slot]
    if (replaced.id == RelicId.OUROBOROS_SEAL && ouroborosLockedUntil > elapsed) return
    clearRelicRuntime(replaced.id)
    val updated = equippedRelics.toMutableList()
    discoverRelic(id)
    updated[slot] = EquippedRelic(id, 1)
    equippedRelics = updated.toList()
    relicRanks[replaced.id.ordinal] = 0
    relicRanks[id.ordinal] = 1
    recordRelicGain(id)
    val severance = content.build.policy.severanceSeconds
    if (severance > 0f) {
        severedRelics = (severedRelics.filter { it.id != replaced.id && it.id != id } +
            SeveredRelic(replaced.id, elapsed + severance)).takeLast(MutableGameState.MAX_SEVERED_RELICS)
    }
    onRelicSetChanged()
    message = content.relic(id).name.uppercase() + " // SLOT ${slot + 1} BOUND"
    messageTime = 1.7f
}

internal fun MutableGameState.meldRelic(slot: Int) {
    if (slot !in equippedRelics.indices) return
    val current = equippedRelics[slot]
    if (!canMeld(current.id)) {
        grantMatter(content.relicPolicy.salvageMatter.toFloat())
        message = content.relic(current.id).name.uppercase() + " // RESONANCE SALVAGED"
    } else {
        setRelicRank(slot, current.rank + 1)
        message = content.relic(current.id).name.uppercase() + " // RANK " + (current.rank + 1)
    }
    messageTime = 1.7f
}

/** Declining a relic keeps the slot empty (or skips an Heirloom) for a small salvage. */
internal fun MutableGameState.declineRelic(heirloom: Boolean) {
    if (heirloom) {
        rerollsRemaining += content.relicPolicy.heirloomDeclineRerolls
        pendingHeirloomResolved = true
        heirloomCandidates = emptyList()
        message = "HEIRLOOM LEFT // +2 REROLLS"
    } else {
        grantMatter(content.relicPolicy.salvageMatter.toFloat())
        message = "SLOT LEFT HOLLOW"
    }
    messageTime = 1.6f
}

private fun MutableGameState.onRelicSetChanged() {
    recompileBuild()
    discoverBoundPairings()
    // Bound relics change effective capacities; keep the gauges within them.
    shield = min(shield, shieldCapacity() * kotlin.math.max(1f, build.overhealOverfill))
}

internal fun MutableGameState.clearRelicRuntime(id: RelicId) {
    val index = id.ordinal
    relicCooldowns[index] = 0f
    relicCounters[index] = 0
    when (id) {
        RelicId.STORMHEART_CAPACITOR -> stormCharge = 0f
        RelicId.STASIS_ANCHOR -> {
            stasisBank = 0
            stasisMeter = MutableGameState.STASIS_SECONDS
        }
        RelicId.ECHO_CHAMBER -> echoHits.clear()
        RelicId.CINDER_CROWN -> for (enemyIndex in enemies.indices) enemies[enemyIndex].status[kinetickk.ball.gameplay.nucleus.model.StatusCell.CINDER_BANK] = 0f
        else -> Unit
    }
}
