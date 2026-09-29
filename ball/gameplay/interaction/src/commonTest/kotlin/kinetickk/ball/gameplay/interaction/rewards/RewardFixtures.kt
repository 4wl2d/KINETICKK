// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.RunStatistics
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage

private val RelicCopy = mapOf(
    RelicId.KINETIC_FLYWHEEL to ("The weapon stores excess velocity as striking force instead of losing it to drag." to
        "+5% weapon damage above 500 u/s per rank; the bonus doubles above 1,600 u/s."),
    RelicId.GHOST_VECTOR to ("Every dash leaves a cutting vector through enemies near the Core's departure point." to
        "+24 dash-cut damage and +14 effect radius per rank."),
    RelicId.ORBITAL_NAIL to ("Weapon contact nails a target to the Core's local gravity well." to
        "Pulls the target 24 u/s toward the Core per rank on each qualified hit."),
    RelicId.PERIAPSIS_HOOK to ("Distant targets fall harder through the weapon's curved approach." to
        "+8% weapon damage per rank beyond 300 units from the Core."),
    RelicId.VOLTAIC_FILAMENT to ("Weapon contact extrudes a live filament toward the nearest untouched enemy." to
        "Arcs 16% of the triggering damage per rank, with a 0.28-second gate."),
    RelicId.ECHO_CHAMBER to ("A portion of every strike is hidden outside time and returned after the target has moved." to
        "Repeats 12% of qualified-hit damage per rank after 0.45 seconds."),
    RelicId.CROWN_OF_FOUR_WINDS to ("The Crown rewards a matrix whose four slots disagree without falling out of resonance." to
        "+4% damage and +3% activation speed per rank for each distinct non-Sovereign aspect."),
)

/** Catalog-shaped content for overlay tests: real weapon, relic and item names. */
internal val rewardFixtureContent: GameplayContentSnapshot = GameplayContentSnapshot(
    version = KINETICKK_CONTENT_VERSION,
    items = listOf(
        item(0, "Rime Thruster", ItemRarity.RARE, ItemEffect.DASH_POWER, 0.24f, ItemEffect.COOLING, 0.07f, "Vector", 5),
        item(1, "Cataclysm Lens", ItemRarity.LEGENDARY, ItemEffect.CRIT_CHANCE, 0.19f, ItemEffect.CRIT_DAMAGE, 0.14f, "Precision", 3),
        item(2, "Echo Reactor", ItemRarity.EPIC, ItemEffect.OVERDRIVE_GAIN, 0.31f, ItemEffect.COMBO_WINDOW, 0.18f, "Overdrive", 4),
        item(3, "Pulse Dynamo", ItemRarity.COMMON, ItemEffect.WEAPON_POWER, 0.12f, ItemEffect.ATTACK_SPEED, 0.03f, "Arsenal", 4),
        item(4, "Hawkeye Ram", ItemRarity.UNCOMMON, ItemEffect.IMPACT_DAMAGE, 0.22f, ItemEffect.CRIT_CHANCE, 0.04f, "Impact", 4),
    ).toImmutableList(),
    weapons = listOf(
        WeaponDefinition(WeaponId.FLUX_WAKE, "Flux Wake", "High-speed movement leaves a damaging trail that lingers behind the Core.", listOf("MOMENTUM", "TRAIL", "AREA"), 0),
        WeaponDefinition(WeaponId.MORNINGSTAR, "Morningstar", "A heavy orbital mass converts velocity and mass into crushing contact damage.", listOf("ORBITAL", "IMPACT", "MASS"), 25),
        WeaponDefinition(WeaponId.PHASE_LATTICE, "Phase Lattice", "A pulsing gravity ring damages enemies held near its unstable perimeter.", listOf("AURA", "CONTROL", "PHASE"), 55),
        WeaponDefinition(WeaponId.NULL_LANCE, "Null Lance", "Momentum periodically projects a piercing lance along the Core's travel vector.", listOf("PIERCE", "DIRECTIONAL", "MOMENTUM"), 95),
        WeaponDefinition(WeaponId.GRAVITY_MINES, "Gravity Mines", "Braking plants implosive mines that pull enemies inward before detonating.", listOf("MINE", "PULL", "BRAKE"), 145),
        WeaponDefinition(WeaponId.ION_SWARM, "Ion Swarm", "Autonomous ion motes orbit the Core, seek targets, and accelerate with attack speed.", listOf("DRONE", "SEEKING", "RAPID"), 215),
        WeaponDefinition(WeaponId.RIFT_BLADES, "Rift Blades", "Paired blades tear outward through enemies and return across their original paths.", listOf("BLADE", "RETURNING", "CRITICAL"), 305),
        WeaponDefinition(WeaponId.ARC_COIL, "Arc Coil", "Stored kinetic charge erupts as lightning that chains between nearby enemies.", listOf("CHAIN", "LIGHTNING", "CHARGE"), 430),
        WeaponDefinition(WeaponId.QUASAR_CANNON, "Quasar Cannon", "A slow-charging cannon compresses momentum into a colossal piercing projectile.", listOf("HEAVY", "CHARGE", "PIERCE"), 610),
        WeaponDefinition(WeaponId.ENTROPY_FIELD, "Entropy Field", "A widening decay field slows hostile motion and deals escalating damage over time.", listOf("AURA", "DECAY", "SLOW"), 860),
        WeaponDefinition(WeaponId.SINGULARITY_SPEAR, "Singularity Spear", "Every overdrive cycle forges a boss-piercing spear from condensed kinetic matter.", listOf("ULTIMATE", "OVERDRIVE", "BOSS"), 1_200),
        WeaponDefinition(WeaponId.PRISM_RELAY, "Prism Relay", "Launches a seeking light shard that refracts between targets.", listOf("SEEKING", "RICOCHET", "REFRACTION"), 1_650),
    ).toImmutableList(),
    weaponMasteries = WeaponMastery.entries.toImmutableList(),
    metaUpgrades = MetaUpgradeId.entries.map { id ->
        MetaUpgradeDefinition(id, "Fixture", "Fixture", 1, 1, ItemModifier(ItemEffect.MAX_INTEGRITY, 1f))
    }.toImmutableList(),
    relics = RelicId.entries.map { id ->
        val aspect = when {
            id.ordinal >= 36 -> RelicAspect.SOVEREIGN
            else -> RelicAspect.entries[id.ordinal / 6]
        }
        val copy = RelicCopy[id] ?: ("A relic of the " + aspect.displayLabel + " aspect." to "+1 effect per rank.")
        RelicDefinition(id, id.name.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }, aspect, copy.first, copy.second)
    }.toImmutableList(),
    rebirth = RebirthPolicySnapshot(
        minimumLevel = 0, maximumLevel = 0,
        profiles = immutableListOf(RebirthProfile(0, RebirthDirective.BASELINE, 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0, 120, 0.09f, 24f)),
        maxActiveEnemies = 120, minSpawnIntervalSeconds = 0.09f, minEliteIntervalSeconds = 24f,
    ),
    relicPolicy = RelicPolicy(4, 5),
)

private fun item(
    id: Int,
    name: String,
    rarity: ItemRarity,
    primary: ItemEffect,
    primaryAmount: Float,
    secondary: ItemEffect,
    secondaryAmount: Float,
    family: String,
    maxStacks: Int,
) = ItemDefinition(
    id, name, "$name binds the $family family.", rarity,
    ItemModifier(primary, primaryAmount),
    ItemModifier(secondary, secondaryAmount), maxStacks, 1, family,
)

/** A committed frame with the given reward choice, relic matrix and run totals. */
internal fun rewardFixtureModel(
    phase: GamePhase = GamePhase.CHOICE,
    choiceType: ChoiceType = ChoiceType.ITEM,
    choices: List<ChoiceOption> = emptyList(),
    previews: List<RewardPreview> = emptyList(),
    relics: List<EquippedRelic> = emptyList(),
    itemStacks: List<Int> = List(rewardFixtureContent.items.size) { 0 },
    discoveredItems: Set<Int> = emptySet(),
    discoveredRelics: Set<RelicId> = emptySet(),
    level: Int = 15,
    weapon: WeaponId = WeaponId.FLUX_WAKE,
    weaponLevel: Int = 7,
    rerolls: Int = 2,
    directedChoice: Boolean = false,
    language: AppLanguage = AppLanguage.English,
    textScale: Float = 1f,
    width: Float = 1440f,
    height: Float = 810f,
    message: String = "",
    kills: Int = 612,
    runMatter: Long = 486L,
    totalMatter: Long = 1_924L,
    elapsed: Float = 462f,
    statistics: RunStatistics = RunStatistics(
        damageDealt = 184_320.0, damageTaken = 388.0, damageAbsorbed = 240.0, dataCollected = 31_260L,
        pickupsCollected = 2_904L, keysCollected = 4L, eliteKills = 2, bestCombo = 31,
    ),
    rebirthLevel: Int = 3,
    runStatisticsOnLeft: Boolean = false,
): GameplayRenderModel = GameplayRenderModel(
    content = rewardFixtureContent,
    phase = phase,
    settings = PlayerPreferences(language = language, textScale = textScale, runStatisticsOnLeft = runStatisticsOnLeft),
    rebirthLevel = rebirthLevel,
    screenWidth = width,
    screenHeight = height,
    uiScale = 1f,
    coreX = 0f, coreY = 0f, velocityX = 0f, velocityY = 0f, cameraX = 0f, cameraY = 0f,
    pointerX = width * 0.5f, pointerY = height * 0.5f, pointerActive = false, braking = false,
    elapsed = elapsed,
    heat = 0f, overheated = false, dashPhaseTime = 0f,
    hp = 142f, maxHp = 180f, shield = 0f, maxShield = 0f,
    level = level, data = 0, nextLevelData = 100, keys = 1, kills = kills,
    runStatistics = statistics,
    combo = 0, comboTime = 0f, runMatter = runMatter, totalMatter = totalMatter,
    lastImpact = 0f, lastImpactTime = 0f, damageFlash = 0f, runGrace = 0f, screenShake = 0f,
    message = message, messageTime = 0f,
    mass = 1.18f, damageMultiplier = 1.64f, weaponPower = 1.36f, coolingRate = 23.2f, magnetStrength = 6.7f,
    dashImpulse = 873f, dashHeatCost = 36f, regenPerSecond = 0f, critChance = 0.08f, critMultiplier = 1.5f,
    pickupRadius = 174f, luck = 0f, dataGain = 1f, matterGain = 1f, attackSpeed = 1f, damageReduction = 0f,
    comboWindow = 2.8f, overdriveGain = 1.2f, dragCoefficient = 0f, polarityStability = 1f,
    weapon = weapon, weaponLevel = weaponLevel,
    overdriveCharge = 0f, overdriveTime = 0f,
    rerollsRemaining = rerolls,
    acquiredItemCount = 4,
    recentItem = null,
    equippedRelics = relics.toImmutableList(),
    morningstarAngle = 0f, morningstarX = 0f, morningstarY = 0f,
    weaponBeamTime = 0f, weaponBeamStartX = 0f, weaponBeamStartY = 0f, weaponBeamEndX = 0f, weaponBeamEndY = 0f,
    totem = null,
    coreShape = CoreShape.ORB,
    enemies = immutableListOf(), projectiles = immutableListOf(), pickups = immutableListOf(), trail = immutableListOf(),
    weaponNodes = immutableListOf(), weaponOrbitals = immutableListOf(),
    choices = choices.toImmutableList(),
    choiceType = choiceType,
    pendingRelicChoiceCount = 0,
    itemStacks = itemStacks.toImmutableList(),
    discoveredItemIds = discoveredItems.toImmutableSet(),
    relicRanks = RelicId.entries.map { id -> relics.firstOrNull { it.id == id }?.rank ?: 0 }.toImmutableList(),
    directedChoice = directedChoice,
    rewardPreviews = previews.toImmutableList(),
    discoveredRelicMask = discoveredRelics.fold(0L) { mask, id -> mask or (1L shl id.ordinal) },
)

internal fun itemChoice(id: Int): ChoiceOption {
    val item = requireNotNull(rewardFixtureContent.item(id))
    return ChoiceOption(ChoiceType.ITEM, item.name, item.description, item.rarity.displayLabel.uppercase(), itemId = id)
}
