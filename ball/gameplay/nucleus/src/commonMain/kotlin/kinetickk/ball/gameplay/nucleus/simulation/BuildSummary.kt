// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.lifetimeMatterOfferLevel
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.BuildProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.ball.gameplay.nucleus.render.RelicLinkProjection
import kinetickk.ball.gameplay.nucleus.render.SeveredRelicProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOfSize
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.max

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
    eligibleItemIds = content.items.filter { item ->
        val ordinaryOfferEligible = item.unlockLevel <= maxOf(level, lifetimeMatterOfferLevel(lifetimeMatter)) &&
            item.minimumRebirth <= rebirthLevel
        val currentlyOffered = phase == GamePhase.CHOICE && choices.any { it.itemId == item.id }
        (ordinaryOfferEligible || currentlyOffered) && hasUsefulItemEffect(item)
    }.map { it.id }.toImmutableList(),
    tags = BuildTag.entries.map { BuildTagSummary(it, build.tagPoints(it), tagTier(it)) }.toImmutableList(),
    elements = StatusElement.entries.map {
        BuildElementSummary(it, build.affinity(it), affinityTier(it), build.dominant == it)
    }.toImmutableList(),
    relicLinks = build.links.map { link ->
        BuildRelicLinkSummary(
            first = link.first,
            second = link.second,
            kind = when (link.kind) {
                RelicLinkKind.CONCORD -> BuildRelicLinkKind.CONCORD
                RelicLinkKind.DISCORD -> BuildRelicLinkKind.DISCORD
                RelicLinkKind.HARMONY -> BuildRelicLinkKind.HARMONY
                RelicLinkKind.DISSONANCE -> BuildRelicLinkKind.DISSONANCE
            },
            pairing = link.pairing,
            discovered = link.pairing == null || discoveredPairingMask and (1 shl link.pairing.ordinal) != 0,
        )
    }.toImmutableList(),
    reachableReactions = ReactionId.entries.filter(build::reactionReachable).toImmutableList(),
    suppressedItemIds = ownedSuppressedItems(),
    rebornItemIds = content.items.filter { rebornItems[it.id] != 0 }.map { it.id }.toImmutableList(),
    severedRelicIds = severedRelics.map { it.id }.toImmutableList(),
)

private fun MutableGameState.ownedSuppressedItems(): ImmutableList<Int> =
    content.items.filter { itemStacks[it.id] > 0 && itemSuppressedShare(it) >= 1f }.map { it.id }.toImmutableList()

internal fun MutableGameState.hasUsefulItemEffect(item: ItemDefinition): Boolean =
    itemStacks[item.id] < item.maxStacks && itemSuppressedShare(item) < 1f

/** The presentation projection of the compiled build and the relic gauges. */
internal fun MutableGameState.buildProjection(): BuildProjection = BuildProjection(
    tagPoints = immutableListOfSize(BuildTag.entries.size) { build.tagPoints(BuildTag.entries[it]) },
    tagTiers = immutableListOfSize(BuildTag.entries.size) { tagTier(BuildTag.entries[it]) },
    affinity = immutableListOfSize(StatusElement.entries.size) { build.affinity(StatusElement.entries[it]) },
    affinityTiers = immutableListOfSize(StatusElement.entries.size) { affinityTier(StatusElement.entries[it]) },
    dominant = build.dominant,
    secondary = build.secondary,
    links = build.links.map { link ->
        RelicLinkProjection(link.first, link.second, link.kind, link.pairing,
            link.pairing == null || discoveredPairingMask and (1 shl link.pairing.ordinal) != 0)
    }.toImmutableList(),
    relicUpsides = immutableListOfSize(RelicId.entries.size) { index ->
        val id = RelicId.entries[index]
        if (build.upside(id)) build.relicUpside(id) else 1f
    },
    severed = severedRelics.map { SeveredRelicProjection(it.id, max(0f, it.until - elapsed)) }.toImmutableList(),
    suppressedItemIds = ownedSuppressedItems(),
    rebornItemIds = content.items.filter { rebornItems[it.id] != 0 }.map { it.id }.toImmutableList(),
    reachableReactions = ReactionId.entries.filter(build::reactionReachable).toImmutableList(),
    stormCharge = stormCharge,
    stasisMeter = stasisMeter / MutableGameState.STASIS_SECONDS,
    stasisBank = stasisBank,
    ouroborosSpent = ouroborosSpent,
    invulnerable = invulnerableUntil > elapsed,
    overdriveBurnout = overdriveBurnoutUntil > elapsed,
    breakThresholds = immutableListOfSize(StatusElement.entries.size) { breakThreshold(StatusElement.entries[it]) },
)

/** Contributions are additive deltas in display units and sum to the effective value. */
internal fun MutableGameState.buildStats(includeTemporary: Boolean = true): ImmutableList<BuildStatSummary> {
    val rows = mutableListOf<BuildStatSummary>()
    fun stat(name: String, current: Float, base: Float, lab: Float = 0f, relics: Float = 0f, unit: String = "") {
        rows += BuildStatSummary(name, current, unit, listOf(
            BuildStatContribution(BuildStatSource.CHARACTER, base),
            BuildStatContribution(BuildStatSource.LAB, lab),
            BuildStatContribution(BuildStatSource.ITEMS, current - base - lab - relics),
            BuildStatContribution(BuildStatSource.RELICS, relics),
        ).filter { it.amount != 0f }.toImmutableList())
    }
    stat("Impact power", damageMultiplier, 1f, metaLevel(MetaUpgradeId.KINETIC_AMPLIFIER) * 0.05f, unit = "×")
    val globalPower = rebirthProfile.playerPowerMultiplier
    // Run growth reports with mastery: both are progression the run grants, not items.
    val masteryPower = (1f + (weaponLevel - 1) * 0.08f) * (1f + currentWeaponMastery.damageBonus) * weaponRunGrowth()
    val permanentPower = weaponPower * globalPower * masteryPower
    val effectivePower = permanentPower * if (includeTemporary && overdriveTime > 0f) 1.45f else 1f
    rows += BuildStatSummary("Weapon power", effectivePower, "×", listOf(
        BuildStatContribution(BuildStatSource.CHARACTER, 1f),
        BuildStatContribution(BuildStatSource.LAB, metaLevel(MetaUpgradeId.ARMORY_LICENSE) * 0.04f + weaponPower * (globalPower - 1f)),
        BuildStatContribution(BuildStatSource.ITEMS, weaponPower - 1f - metaLevel(MetaUpgradeId.ARMORY_LICENSE) * 0.04f),
        BuildStatContribution(BuildStatSource.MASTERY, weaponPower * globalPower * (masteryPower - 1f)),
        BuildStatContribution(BuildStatSource.TEMPORARY, effectivePower - permanentPower),
    ).filter { it.amount != 0f }.toImmutableList())
    val masterySpeed = attackSpeed * (1f + currentWeaponMastery.activationSpeedBonus)
    val buildSpeed = masterySpeed * buildActivationBonus()
    val effectiveSpeed = buildSpeed * if (includeTemporary && overdriveTime > 0f) 1.35f else 1f
    rows += BuildStatSummary("Activation speed", effectiveSpeed, "×", listOf(
        BuildStatContribution(BuildStatSource.CHARACTER, 1f),
        BuildStatContribution(BuildStatSource.ITEMS, attackSpeed - 1f),
        BuildStatContribution(BuildStatSource.MASTERY, masterySpeed - attackSpeed),
        BuildStatContribution(BuildStatSource.SYNERGIES, buildSpeed - masterySpeed),
        BuildStatContribution(BuildStatSource.TEMPORARY, effectiveSpeed - buildSpeed),
    ).filter { it.amount != 0f }.toImmutableList())
    stat("Integrity", maxHp, 100f, metaLevel(MetaUpgradeId.CORE_INTEGRITY) * 10f + rebirthProfile.playerIntegrityBonus)
    stat("Shield", shieldCapacity(), 0f, relics = shieldCapacity() - maxShield)
    stat("Damage reduction", effectiveDamageReduction() * 100f, 0f, relics = (effectiveDamageReduction() - damageReduction) * 100f, unit = "%")
    stat("Regeneration", regenPerSecond, 0f, unit = "/s")
    stat("Mass", effectiveMass(), 1f, relics = effectiveMass() - mass)
    stat("Magnetism", effectiveMagnetism(), 4.65f, 4.65f * metaLevel(MetaUpgradeId.MAGNETIC_RESONANCE) * 0.04f,
        relics = effectiveMagnetism() - magnetStrength)
    stat("Cooling", effectiveCooling(), 19f, 19f * metaLevel(MetaUpgradeId.CRYO_VENTS) * 0.05f,
        relics = effectiveCooling() - coolingRate, unit = "/s")
    stat("Dash impulse", effectiveDashImpulse(), 590f, 590f * metaLevel(MetaUpgradeId.DASH_CAPACITOR) * 0.05f,
        relics = effectiveDashImpulse() - dashImpulse)
    stat("Dash heat", effectiveDashHeatCost(), 36f, relics = effectiveDashHeatCost() - dashHeatCost)
    stat("Critical chance", effectiveCritChance() * 100f, 5f, relics = (effectiveCritChance() - critChance) * 100f, unit = "%")
    stat("Critical power", critMultiplier, 1.5f, unit = "×")
    stat("Pickup radius", effectivePickupRadius(), 150f, relics = effectivePickupRadius() - pickupRadius)
    stat("Luck", effectiveLuck(), 0f, relics = effectiveLuck() - luck)
    stat("Data gain", dataGain, 1f, metaLevel(MetaUpgradeId.DATA_ARCHIVE) * 0.05f, unit = "×")
    stat("Matter gain", matterGain, 1f, (1f + metaLevel(MetaUpgradeId.SALVAGE_PROTOCOL) * 0.05f) * rebirthProfile.matterGainMultiplier - 1f, unit = "×")
    stat("Combo window", effectiveComboWindow(), 2.8f, relics = effectiveComboWindow() - comboWindow, unit = "s")
    stat("Overdrive gain", overdriveGainFactor(), 1f, relics = overdriveGainFactor() - overdriveGain, unit = "×")
    return rows.toImmutableList()
}

internal data class BuildChangeSnapshot(
    val stats: ImmutableList<BuildStatSummary>,
    val tagTiers: List<Int>,
    val affinityTiers: List<Int>,
)

internal fun MutableGameState.captureBuildChange(): BuildChangeSnapshot = BuildChangeSnapshot(
    buildStats(),
    BuildTag.entries.map { tagTier(it) },
    StatusElement.entries.map { affinityTier(it) },
)

/** Emits the grouped acquisition toast: stat deltas, then reached tiers as "+ " lines. */
internal fun MutableGameState.emitBuildChange(before: BuildChangeSnapshot, title: String) {
    val after = buildStats()
    val deltas = after.mapIndexedNotNull { index, stat ->
        val previous = before.stats.getOrNull(index)?.takeIf { it.name == stat.name } ?: return@mapIndexedNotNull null
        val delta = stat.value - previous.value
        if (abs(delta) < 0.0001f) null else stat.name + " " +
            (if (delta > 0f) "+" else "") + ((delta * 100f).toInt() / 100f) + stat.unit
    }
    val tiers = BuildTag.entries.filter { tagTier(it) > before.tagTiers[it.ordinal] }.map {
        "+ " + it.displayLabel + " " + tierNumeral(tagTier(it))
    } + StatusElement.entries.filter { affinityTier(it) > before.affinityTiers[it.ordinal] }.map {
        "+ " + it.displayLabel + " " + tierNumeral(affinityTier(it))
    }
    emitVisualFx(VisualFxCue.BuildChanged(title, (deltas + tiers).toImmutableList()))
}

internal fun tierNumeral(tier: Int): String = when (tier) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    else -> ""
}
