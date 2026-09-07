// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs

/** Reads never choose an offer, draw randomness, dispatch outputs or publish the scratch state. */
internal fun MutableGameState.rewardPreviews(): ImmutableList<RewardPreview> {
    if (phase != GamePhase.CHOICE) return immutableListOf()
    val beforeStats = buildStats()
    val beforeSynergies = buildSynergySummaries().filter { it.active }
    return choices.map { option ->
        if (option.rewardFocus != null) return@map RewardPreview()
        val candidate = copyForReduction()
        when (option.type) {
            ChoiceType.ITEM -> option.itemId?.let(candidate::acquireItem)
            ChoiceType.TOTEM -> if (option.totemAction == TotemAction.AMPLIFY_CURRENT) candidate.amplifyCurrentWeapon()
            // Weapon level and global bonuses are preserved when switching systems.
            ChoiceType.WEAPON -> option.weaponId?.let { candidate.weapon = it }
            ChoiceType.RELIC -> if (option.relicAction == RelicChoiceAction.ACQUIRE) {
                val id = option.relicId ?: return@map RewardPreview()
                if (relicRank(id) == 0 && equippedRelics.size >= content.relicPolicy.maxSlots) {
                    return@map RewardPreview(requiresSlot = true)
                }
                candidate.acquireRelic(id)
            }
            ChoiceType.RELIC_BIND -> option.relicSlot?.let { slot ->
                when (option.relicAction) {
                    RelicChoiceAction.REPLACE -> (option.relicId ?: pendingBindingRelic)?.let { candidate.replaceRelic(slot, it) }
                    RelicChoiceAction.MELD_TARGET -> candidate.meldRelic(slot)
                    else -> Unit
                }
            }
        }
        val changes = candidate.buildStats().mapIndexedNotNull { index, stat ->
            val before = beforeStats[index].value
            if (abs(stat.value - before) < 0.00001f) null
            else RewardStatChange(stat.name, before, stat.value, stat.unit, stat.name == "Dash heat")
        }.toMutableList()
        val beforeCounts = rewardWeaponCounts()
        val afterCounts = candidate.rewardWeaponCounts()
        (beforeCounts.keys + afterCounts.keys).forEach { name ->
            val before = beforeCounts[name] ?: 0
            val after = afterCounts[name] ?: 0
            if (before != after) changes += RewardStatChange(name, before.toFloat(), after.toFloat(), "")
        }
        // Conditional relic effects compare their own parameters, without pretending
        // that a target-dependent proc is an unconditional player stat.
        (equippedRelics.map { it.id } + candidate.equippedRelics.map { it.id }).distinct().forEach { id ->
            changes += relicEffectChanges(id, candidate)
        }
        val afterSynergies = candidate.buildSynergySummaries().filter { it.active }
        RewardPreview(
            changes = changes.toImmutableList(),
            addedSynergies = afterSynergies.filter { next -> beforeSynergies.none { it.id == next.id } }.map { it.name }.toImmutableList(),
            removedSynergies = beforeSynergies.filter { old -> afterSynergies.none { it.id == old.id } }.map { it.name }.toImmutableList(),
        )
    }.toImmutableList()
}

private fun MutableGameState.rewardWeaponCounts(): Map<String, Int> = when (weapon) {
    WeaponId.ION_SWARM -> mapOf("Drones" to weaponOrbitalCount())
    WeaponId.RIFT_BLADES -> mapOf("Blades" to weaponOrbitalCount())
    WeaponId.ARC_COIL -> mapOf("Chain targets" to arcCoilTargetCount())
    WeaponId.PRISM_RELAY -> mapOf("Projectiles" to prismRelayCount(), "Ricochets" to prismRelayBounces())
    else -> emptyMap()
}
