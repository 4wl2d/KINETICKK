// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs

/** Reads never choose an offer, draw randomness, dispatch outputs or publish the scratch state. */
internal fun MutableGameState.rewardPreviews(): ImmutableList<RewardPreview> {
    if (phase != GamePhase.CHOICE) return immutableListOf()
    val beforeStats = buildStats()
    return choices.map { option ->
        if (option.rewardFocus != null) return@map RewardPreview()
        val candidate = copyForReduction()
        when (option.type) {
            ChoiceType.ITEM -> option.itemId?.let { id ->
                val item = content.item(id) ?: return@map RewardPreview()
                // An active item that still needs a slot forecasts what it brings itself; which slot it replaces is asked next.
                if (needsActiveSlot(item)) {
                    candidate.stackItem(item, option.reborn)
                    candidate.recompileBuild()
                    return@map RewardPreview(forecasts = forecastsAgainst(candidate, option), requiresSlot = true)
                }
                candidate.acquireItem(id, option.reborn)
            }
            ChoiceType.TOTEM -> if (option.totemAction == TotemAction.AMPLIFY_CURRENT) candidate.amplifyCurrentWeapon()
            // Weapon level and global bonuses are preserved when switching systems.
            ChoiceType.WEAPON -> option.weaponId?.let {
                candidate.weapon = it
                candidate.recompileBuild()
            }
            ChoiceType.RELIC -> if (option.relicAction == RelicChoiceAction.ACQUIRE) {
                val id = option.relicId ?: return@map RewardPreview()
                if (relicRank(id) == 0 && equippedRelics.size >= content.relicPolicy.maxSlots) {
                    return@map RewardPreview(forecasts = relicForecasts(id), requiresSlot = true)
                }
                candidate.acquireRelic(id)
            }
            // Replacing previews the whole replacement; keeping the loadout changes nothing to preview.
            ChoiceType.ACTIVE_SLOT -> {
                val slot = option.activeSlot ?: return@map RewardPreview()
                val incoming = pendingActiveItemId ?: return@map RewardPreview()
                candidate.replaceActiveItem(slot, incoming, pendingActiveReborn)
            }
            // An omen previews its run modifiers on the candidate (no world rebuilt); No omen changes nothing.
            ChoiceType.OMEN -> option.omenId?.let { candidate.acceptRunOmen(it, preview = true) }
            // A signal card previews only its deterministic stat part (the Shrine blessings).
            ChoiceType.SIGNAL -> option.signalOption?.let { candidate.previewSignalOption(it) }
            // A boon previews its run modifiers on the candidate; the Refuse card changes nothing.
            ChoiceType.BOON -> option.boonId?.let { candidate.applyRunBoon(it, preview = true) } ?: return@map RewardPreview()
            ChoiceType.RELIC_BIND -> option.relicSlot?.let { slot ->
                when (option.relicAction) {
                    RelicChoiceAction.REPLACE -> (option.relicId ?: pendingBindingRelic)?.let { candidate.replaceRelic(slot, it) }
                    RelicChoiceAction.MELD_TARGET -> candidate.meldRelic(slot)
                    else -> Unit
                }
            }
        }
        val changes = candidate.buildStats().mapIndexedNotNull { index, stat ->
            val before = beforeStats.getOrNull(index)?.takeIf { it.name == stat.name }?.value ?: return@mapIndexedNotNull null
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
        RewardPreview(
            changes = changes.toImmutableList(),
            forecasts = forecastsAgainst(candidate, option),
        )
    }.toImmutableList()
}

/**
 * Build consequences of taking an option, most important first (the card shows the first few). Suppression reads the
 * item the option adds: a replace card's incoming item, never the held item it removes; the keep card adds none.
 */
private fun MutableGameState.forecastsAgainst(candidate: MutableGameState, option: ChoiceOption): ImmutableList<BuildForecast> {
    val forecasts = mutableListOf<BuildForecast>()
    val added = if (option.type == ChoiceType.ACTIVE_SLOT) option.incomingItemId?.takeIf { option.activeSlot != null } else option.itemId
    added?.let { id ->
        val item = content.item(id)
        if (item != null && itemSuppressedShare(item) > 0f) forecasts += BuildForecast(ForecastKind.SUPPRESSED)
    }
    for (reaction in ReactionId.entries) {
        if (!build.reactionReachable(reaction) && candidate.build.reactionReachable(reaction)) {
            forecasts += BuildForecast(ForecastKind.REACTION, reaction = reaction,
                discovered = discoveredReactionMask and (1 shl reaction.ordinal) != 0)
        }
    }
    for (tag in BuildTag.entries) {
        val before = tagTier(tag)
        val after = candidate.tagTier(tag)
        if (after > before) forecasts += BuildForecast(ForecastKind.TAG_TIER, tag = tag, before = before, after = after)
    }
    for (element in StatusElement.entries) {
        val before = build.affinity(element)
        val after = candidate.build.affinity(element)
        if (after > before) forecasts += BuildForecast(ForecastKind.AFFINITY, element = element, before = before, after = after)
    }
    // A new link always involves the incoming relic; the forecast names its bound partner.
    val incoming = option.relicId ?: pendingBindingRelic
    candidate.build.links.forEach { link ->
        if (build.links.none { it.first == link.first && it.second == link.second }) {
            val kind = when (link.kind) {
                RelicLinkKind.CONCORD -> ForecastKind.CONCORD
                RelicLinkKind.DISCORD -> ForecastKind.DISCORD
                RelicLinkKind.HARMONY -> ForecastKind.HARMONY
                RelicLinkKind.DISSONANCE -> ForecastKind.DISSONANCE
            }
            forecasts += BuildForecast(kind, pairing = link.pairing, relic = if (link.second == incoming) link.first else link.second,
                discovered = link.pairing == null || discoveredPairingMask and (1 shl link.pairing.ordinal) != 0)
        }
    }
    return forecasts.toImmutableList()
}

/** Relic offer forecasts when the relic still needs a slot: links it would form with each bound relic. */
private fun MutableGameState.relicForecasts(id: kinetickk.ball.content.api.RelicId): ImmutableList<BuildForecast> {
    val incoming = content.relic(id)
    return equippedRelics.mapNotNull { equipped ->
        val bound = content.relic(equipped.id)
        val named = content.build.pairing(id, equipped.id)
        val kind = when {
            named != null -> if (named.kind == kinetickk.ball.content.api.RelicPairingKind.CONCORD) ForecastKind.CONCORD else ForecastKind.DISCORD
            incoming.dissonates(bound) -> ForecastKind.DISSONANCE
            incoming.harmonizes(bound) -> ForecastKind.HARMONY
            else -> return@mapNotNull null
        }
        BuildForecast(kind, pairing = named?.id, relic = equipped.id,
            discovered = named == null || discoveredPairingMask and (1 shl named.id.ordinal) != 0)
    }.toImmutableList()
}

/** Counts a weapon offer or amplification changes; the reward card lists each one that moves. */
private fun MutableGameState.rewardWeaponCounts(): Map<String, Int> = when (weapon) {
    WeaponId.ION_SWARM -> mapOf("Drones" to ionOrbitalCount())
    WeaponId.RIFT_BLADES -> mapOf("Blades" to riftBladeCount())
    WeaponId.ARC_COIL -> mapOf("Chain targets" to arcCoilTargetCount())
    WeaponId.PRISM_RELAY -> mapOf("Projectiles" to prismRelayCount(), "Ricochets" to prismRelayBounces())
    WeaponId.NULL_LANCE -> mapOf("Pierce" to NULL_LANCE_PIERCE + if (hasMasteryForm(1)) 3 else 0)
    WeaponId.GRAVITY_MINES -> mapOf("Mines" to if (hasMasteryForm(2)) MutableGameState.MAX_WEAPON_NODES else BASE_MINE_LIMIT)
    WeaponId.MORNINGSTAR -> mapOf("Stars" to if (hasMasteryForm(2)) 2 else 1)
    WeaponId.GYRE_DISC -> mapOf("Discs" to 1 + (if (hasMasteryForm(2)) 1 else 0) + if (agonyPower() > 0) 1 else 0)
    WeaponId.SCATTER_BLOOM -> mapOf("Pellets" to SCATTER_PELLETS + (if (hasMasteryForm(1)) 2 else 0) + if (agonyPower() > 0) 2 else 0)
    WeaponId.TETHER_ARC -> mapOf("Tethers" to if (hasMasteryForm(2) || agonyPower() > 0) 2 else 1)
    WeaponId.SENTRY_GRID -> mapOf("Sentries" to SENTRY_LIMIT + (if (hasMasteryForm(1)) 1 else 0) + if (agonyPower() > 0) 1 else 0)
    WeaponId.HUNTER_SALVO -> mapOf("Missiles" to HUNTER_MISSILES + (if (hasMasteryForm(1)) 1 else 0) + if (agonyPower() > 0) 1 else 0)
    WeaponId.EVENT_LENS -> mapOf("Lenses" to 1 + (if (hasMasteryForm(2)) 1 else 0) + if (agonyPower() > 0) 1 else 0)
    WeaponId.SOLAR_SWEEP -> mapOf("Beams" to 2 + (if (hasMasteryForm(2)) 1 else 0) + if (agonyPower() > 0) 1 else 0)
    WeaponId.AEGIS_SHARDS -> mapOf("Shards" to aegisShardCount())
    WeaponId.HOLLOW_RIFT -> mapOf("Detonations" to if (hasMasteryForm(2)) 2 else 1)
    WeaponId.RESONANCE_DRUM -> mapOf("Beats" to if (hasMasteryForm(2)) 2 else 1)
    WeaponId.GLACIER_BELL -> mapOf("Tolls" to if (hasMasteryForm(3)) 2 else 1)
    WeaponId.FLUX_WAKE, WeaponId.PHASE_LATTICE, WeaponId.QUASAR_CANNON, WeaponId.ENTROPY_FIELD,
    WeaponId.SINGULARITY_SPEAR, WeaponId.CONCUSSION_ENGINE -> emptyMap()
}
