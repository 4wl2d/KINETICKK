// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs

/** One pure, detached read of the accepted simulation revision. */
internal fun MutableGameState.buildSummary(
    instanceId: GameplayInstanceId,
    revision: GameplayRevision,
): GameplayBuildSummaryProjection = GameplayBuildSummaryProjection(
    instanceId = instanceId,
    revision = revision,
    itemStacks = itemStacks.toImmutableList(),
    character = coreShape,
    characterName = content.coreShape(coreShape).displayName,
    characterMechanic = content.coreShape(coreShape).mechanicDescription,
    weapon = weapon,
    weaponName = currentWeaponDefinition.name,
    weaponLevel = weaponLevel,
    mastery = currentWeaponMastery.displayLabel,
    relics = equippedRelics.toImmutableList(),
    stats = buildStats(),
    synergies = buildSynergySummaries(),
    eligibleItemIds = content.items.filter { item ->
        val ordinaryOfferEligible = item.unlockLevel <=
            maxOf(level, (1L + lifetimeMatter / 40L).coerceAtMost(80L).toInt())
        val currentlyOffered = phase == GamePhase.CHOICE && choices.any { it.itemId == item.id }
        (ordinaryOfferEligible || currentlyOffered) && hasUsefulItemEffect(item)
    }.map { it.id }.toImmutableList(),
)

internal fun MutableGameState.hasUsefulItemEffect(item: ItemDefinition): Boolean =
    itemStacks[item.id] < item.maxStacks &&
        (hasUsefulModifier(item.primary) || hasUsefulModifier(item.secondary) || familyStacks[item.id / 20] % 3 == 2)

private fun MutableGameState.hasUsefulModifier(modifier: ItemModifier): Boolean =
    modifier.amount != 0f && when (modifier.effect) {
        ItemEffect.CRIT_CHANCE -> critChance < 0.75f
        ItemEffect.DAMAGE_REDUCTION -> damageReduction < 0.65f
        ItemEffect.DASH_EFFICIENCY -> dashHeatCost > 12f
        else -> true
    }

/** Contributions are additive deltas in display units and sum to the effective value. */
internal fun MutableGameState.buildStats(includeTemporary: Boolean = true): ImmutableList<BuildStatSummary> {
    val rows = mutableListOf<BuildStatSummary>()
    fun stat(name: String, current: Float, base: Float, lab: Float = 0f, synergy: Float = 0f, unit: String = "") {
        rows += BuildStatSummary(name, current, unit, listOf(
            BuildStatContribution(BuildStatSource.CHARACTER, base),
            BuildStatContribution(BuildStatSource.LAB, lab),
            BuildStatContribution(BuildStatSource.ITEMS, current - base - lab - synergy),
            BuildStatContribution(BuildStatSource.SYNERGIES, synergy),
        ).filter { it.amount != 0f }.toImmutableList())
    }
    val families = content.items.map { it.id / 20 }.distinct().sumOf { familyStacks[it] / 3 }
    stat("Impact power", damageMultiplier, 1f, metaLevel(MetaUpgradeId.KINETIC_AMPLIFIER) * 0.05f, families * 0.04f, "×")
    val globalPower = rebirthProfile.playerPowerMultiplier
    val masteryPower = (1f + (weaponLevel - 1) * 0.08f) * (1f + currentWeaponMastery.damageBonus)
    val permanentPower = weaponPower * globalPower * masteryPower
    val effectivePower = permanentPower * if (includeTemporary && overdriveTime > 0f) 1.45f else 1f
    rows += BuildStatSummary("Weapon power", effectivePower, "×", listOf(
        BuildStatContribution(BuildStatSource.CHARACTER, 1f),
        BuildStatContribution(BuildStatSource.LAB, metaLevel(MetaUpgradeId.ARMORY_LICENSE) * 0.04f + weaponPower * (globalPower - 1f)),
        BuildStatContribution(BuildStatSource.ITEMS, weaponPower - 1f - metaLevel(MetaUpgradeId.ARMORY_LICENSE) * 0.04f - families * 0.06f),
        BuildStatContribution(BuildStatSource.SYNERGIES, families * 0.06f),
        BuildStatContribution(BuildStatSource.MASTERY, weaponPower * globalPower * (masteryPower - 1f)),
        BuildStatContribution(BuildStatSource.TEMPORARY, effectivePower - permanentPower),
    ).filter { it.amount != 0f }.toImmutableList())
    val masterySpeed = attackSpeed * (1f + currentWeaponMastery.activationSpeedBonus)
    val staticRelicBonus = 0.03f * relicRank(RelicId.CROWN_OF_FOUR_WINDS) * distinctRelicAspectCount()
    val permanentSpeed = masterySpeed * (1f + staticRelicBonus)
    val effectiveSpeed = if (includeTemporary) masterySpeed * (1f + relicActivationSpeedBonus()) *
        (if (overdriveTime > 0f) 1.35f else 1f) else permanentSpeed
    rows += BuildStatSummary("Activation speed", effectiveSpeed, "×", listOf(
        BuildStatContribution(BuildStatSource.CHARACTER, 1f),
        BuildStatContribution(BuildStatSource.ITEMS, attackSpeed - 1f),
        BuildStatContribution(BuildStatSource.MASTERY, masterySpeed - attackSpeed),
        BuildStatContribution(BuildStatSource.RELICS, permanentSpeed - masterySpeed),
        BuildStatContribution(BuildStatSource.TEMPORARY, effectiveSpeed - permanentSpeed),
    ).filter { it.amount != 0f }.toImmutableList())
    stat("Integrity", maxHp, 100f, metaLevel(MetaUpgradeId.CORE_INTEGRITY) * 10f + rebirthProfile.playerIntegrityBonus)
    stat("Shield", maxShield, 0f)
    stat("Damage reduction", damageReduction * 100f, 0f, unit = "%")
    stat("Regeneration", regenPerSecond, 0f, unit = "/s")
    stat("Mass", mass, 1f)
    stat("Magnetism", magnetStrength, 4.65f, 4.65f * metaLevel(MetaUpgradeId.MAGNETIC_RESONANCE) * 0.04f)
    stat("Cooling", coolingRate, 19f, 19f * metaLevel(MetaUpgradeId.CRYO_VENTS) * 0.05f, unit = "/s")
    stat("Dash impulse", dashImpulse, 590f, 590f * metaLevel(MetaUpgradeId.DASH_CAPACITOR) * 0.05f)
    stat("Dash heat", dashHeatCost, 36f)
    stat("Critical chance", critChance * 100f, 5f, unit = "%")
    stat("Critical power", critMultiplier, 1.5f, unit = "×")
    stat("Pickup radius", pickupRadius, 150f)
    stat("Luck", luck, 0f)
    stat("Data gain", dataGain, 1f, metaLevel(MetaUpgradeId.DATA_ARCHIVE) * 0.05f, unit = "×")
    stat("Matter gain", matterGain, 1f, (1f + metaLevel(MetaUpgradeId.SALVAGE_PROTOCOL) * 0.05f) * rebirthProfile.matterGainMultiplier - 1f, unit = "×")
    stat("Combo window", comboWindow, 2.8f, unit = "s")
    stat("Overdrive gain", overdriveGain, 1f, unit = "×")
    return rows.toImmutableList()
}

internal data class BuildChangeSnapshot(
    val stats: ImmutableList<BuildStatSummary>,
    val activeSynergies: ImmutableList<BuildSynergySummary>,
)
internal fun MutableGameState.captureBuildChange(): BuildChangeSnapshot = BuildChangeSnapshot(
    buildStats(),
    buildSynergySummaries().filter { it.active }.toImmutableList(),
)
internal fun MutableGameState.emitBuildChange(before: BuildChangeSnapshot, title: String) {
    val after = buildStats()
    val deltas = after.mapIndexedNotNull { index, stat ->
        val delta = stat.value - before.stats[index].value
        if (abs(delta) < 0.0001f) null else stat.name + " " +
            (if (delta > 0f) "+" else "") + ((delta * 100f).toInt() / 100f) + stat.unit
    }
    val synergies = buildSynergySummaries().filter { it.active }
    val added = synergies.filter { next -> before.activeSynergies.none { it.id == next.id } }.map { "+ ${it.name}" }
    val removed = before.activeSynergies.filter { old -> synergies.none { it.id == old.id } }.map { "− ${it.name}" }
    emitVisualFx(VisualFxCue.BuildChanged(title, (deltas + added + removed).toImmutableList()))
}
