// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

enum class ModifierUnit {
    PERCENT,
    FLAT,
    PER_SECOND,
    SECONDS,
}

enum class ItemEffect(
    val displayLabel: String,
    val unit: ModifierUnit,
) {
    IMPACT_DAMAGE("Impact damage", ModifierUnit.PERCENT),
    WEAPON_POWER("Weapon power", ModifierUnit.PERCENT),
    MASS("Mass", ModifierUnit.PERCENT),
    MAGNETISM("Magnetism", ModifierUnit.PERCENT),
    COOLING("Cooling", ModifierUnit.PERCENT),
    MAX_INTEGRITY("Maximum integrity", ModifierUnit.FLAT),
    REGEN("Integrity regeneration", ModifierUnit.PER_SECOND),
    DASH_POWER("Dash power", ModifierUnit.PERCENT),
    DASH_EFFICIENCY("Dash efficiency", ModifierUnit.PERCENT),
    CRIT_CHANCE("Critical chance", ModifierUnit.PERCENT),
    CRIT_DAMAGE("Critical damage", ModifierUnit.PERCENT),
    PICKUP_RADIUS("Pickup radius", ModifierUnit.FLAT),
    LUCK("Luck", ModifierUnit.PERCENT),
    DATA_GAIN("Data gain", ModifierUnit.PERCENT),
    MATTER_GAIN("Matter gain", ModifierUnit.PERCENT),
    ATTACK_SPEED("Attack speed", ModifierUnit.PERCENT),
    SHIELD_CAPACITY("Shield capacity", ModifierUnit.FLAT),
    DAMAGE_REDUCTION("Damage reduction", ModifierUnit.PERCENT),
    COMBO_WINDOW("Combo window", ModifierUnit.SECONDS),
    OVERDRIVE_GAIN("Overdrive gain", ModifierUnit.PERCENT),
}

enum class ItemRarity(
    val displayLabel: String,
    val rank: Int,
) {
    COMMON("Common", 1),
    UNCOMMON("Uncommon", 2),
    RARE("Rare", 3),
    EPIC("Epic", 4),
    LEGENDARY("Legendary", 5),
}

data class ItemModifier(
    val effect: ItemEffect,
    val amount: Float,
) {
    init {
        require(amount.isFinite() && amount > 0f) { "Item modifier amount must be finite and positive" }
    }
}

data class ItemDefinition(
    val id: Int,
    val name: String,
    val description: String,
    val rarity: ItemRarity,
    val primary: ItemModifier,
    val secondary: ItemModifier,
    val maxStacks: Int,
    val unlockLevel: Int,
    val family: String,
) {
    init {
        require(id >= 0) { "Item id must be non-negative" }
        require(name.isNotBlank()) { "Item name must not be blank" }
        require(description.isNotBlank()) { "Item description must not be blank" }
        require(maxStacks > 0) { "Item maxStacks must be positive" }
        require(unlockLevel > 0) { "Item unlockLevel must be positive" }
        require(family.isNotBlank()) { "Item family must not be blank" }
    }
}

private data class ItemFamilyTemplate(
    val name: String,
    val noun: String,
    val effect: ItemEffect,
)

private data class ItemComponentTemplate(
    val name: String,
    val effect: ItemEffect,
)

private val itemFamilies = listOf(
    ItemFamilyTemplate("Impact", "Ram", ItemEffect.IMPACT_DAMAGE),
    ItemFamilyTemplate("Arsenal", "Dynamo", ItemEffect.WEAPON_POWER),
    ItemFamilyTemplate("Density", "Ballast", ItemEffect.MASS),
    ItemFamilyTemplate("Polarity", "Compass", ItemEffect.MAGNETISM),
    ItemFamilyTemplate("Cryogenic", "Vent", ItemEffect.COOLING),
    ItemFamilyTemplate("Integrity", "Lattice", ItemEffect.MAX_INTEGRITY),
    ItemFamilyTemplate("Renewal", "Seed", ItemEffect.REGEN),
    ItemFamilyTemplate("Vector", "Thruster", ItemEffect.DASH_POWER),
    ItemFamilyTemplate("Efficiency", "Reclaimer", ItemEffect.DASH_EFFICIENCY),
    ItemFamilyTemplate("Precision", "Lens", ItemEffect.CRIT_CHANCE),
    ItemFamilyTemplate("Ruin", "Crucible", ItemEffect.CRIT_DAMAGE),
    ItemFamilyTemplate("Collection", "Harvester", ItemEffect.PICKUP_RADIUS),
    ItemFamilyTemplate("Fortune", "Die", ItemEffect.LUCK),
    ItemFamilyTemplate("Archive", "Codex", ItemEffect.DATA_GAIN),
    ItemFamilyTemplate("Salvage", "Siphon", ItemEffect.MATTER_GAIN),
    ItemFamilyTemplate("Tempo", "Metronome", ItemEffect.ATTACK_SPEED),
    ItemFamilyTemplate("Shield", "Aegis", ItemEffect.SHIELD_CAPACITY),
    ItemFamilyTemplate("Bulwark", "Dampener", ItemEffect.DAMAGE_REDUCTION),
    ItemFamilyTemplate("Combo", "Relay", ItemEffect.COMBO_WINDOW),
    ItemFamilyTemplate("Overdrive", "Reactor", ItemEffect.OVERDRIVE_GAIN),
)

private val itemComponents = listOf(
    ItemComponentTemplate("Cinder", ItemEffect.IMPACT_DAMAGE),
    ItemComponentTemplate("Neon", ItemEffect.WEAPON_POWER),
    ItemComponentTemplate("Gravitic", ItemEffect.MASS),
    ItemComponentTemplate("Lodestar", ItemEffect.MAGNETISM),
    ItemComponentTemplate("Rime", ItemEffect.COOLING),
    ItemComponentTemplate("Bastion", ItemEffect.MAX_INTEGRITY),
    ItemComponentTemplate("Verdant", ItemEffect.REGEN),
    ItemComponentTemplate("Comet", ItemEffect.DASH_POWER),
    ItemComponentTemplate("Frugal", ItemEffect.DASH_EFFICIENCY),
    ItemComponentTemplate("Hawkeye", ItemEffect.CRIT_CHANCE),
    ItemComponentTemplate("Cataclysm", ItemEffect.CRIT_DAMAGE),
    ItemComponentTemplate("Trawler", ItemEffect.PICKUP_RADIUS),
    ItemComponentTemplate("Serendipity", ItemEffect.LUCK),
    ItemComponentTemplate("Mnemonic", ItemEffect.DATA_GAIN),
    ItemComponentTemplate("Alchemical", ItemEffect.MATTER_GAIN),
    ItemComponentTemplate("Pulse", ItemEffect.ATTACK_SPEED),
    ItemComponentTemplate("Prismatic", ItemEffect.SHIELD_CAPACITY),
    ItemComponentTemplate("Adamant", ItemEffect.DAMAGE_REDUCTION),
    ItemComponentTemplate("Echo", ItemEffect.COMBO_WINDOW),
    ItemComponentTemplate("Nova", ItemEffect.OVERDRIVE_GAIN),
)

object ItemCatalog {
    const val ITEM_COUNT: Int = 400

    val all: ImmutableList<ItemDefinition> = buildList(ITEM_COUNT) {
        itemFamilies.forEachIndexed { familyIndex, family ->
            itemComponents.forEachIndexed { componentIndex, component ->
                val id = familyIndex * itemComponents.size + componentIndex
                val rarity = rarityFor(id)
                val primary = ItemModifier(
                    effect = family.effect,
                    amount = scaledAmount(
                        effect = family.effect,
                        scale = 1f + rarity.rank * 0.22f + componentIndex * 0.017f,
                    ),
                )
                val secondary = ItemModifier(
                    effect = component.effect,
                    amount = scaledAmount(
                        effect = component.effect,
                        scale = 0.42f + rarity.rank * 0.07f + familyIndex * 0.013f,
                    ),
                )
                val name = "${component.name} ${family.noun}"
                val maxStacks = 9 - rarity.rank
                add(
                    ItemDefinition(
                        id = id,
                        name = name,
                        description = "$name binds the ${family.name} family to a ${component.name} component: " +
                            "${describe(primary)} and ${describe(secondary)} per stack (max $maxStacks).",
                        rarity = rarity,
                        primary = primary,
                        secondary = secondary,
                        maxStacks = maxStacks,
                        unlockLevel = 1 + (id * 37) % 80,
                        family = family.name,
                    ),
                )
            }
        }
    }.toImmutableList().also(::validateItems)

    fun byId(id: Int): ItemDefinition? = all.getOrNull(id)
}

private fun rarityFor(id: Int): ItemRarity = when ((id * 53 + id / 20 * 11 + id % 20 * 7) % 100) {
    in 0..41 -> ItemRarity.COMMON
    in 42..69 -> ItemRarity.UNCOMMON
    in 70..86 -> ItemRarity.RARE
    in 87..96 -> ItemRarity.EPIC
    else -> ItemRarity.LEGENDARY
}

private fun baseAmount(effect: ItemEffect): Float = when (effect) {
    ItemEffect.IMPACT_DAMAGE -> 0.04f
    ItemEffect.WEAPON_POWER -> 0.035f
    ItemEffect.MASS -> 0.025f
    ItemEffect.MAGNETISM -> 0.035f
    ItemEffect.COOLING -> 0.04f
    ItemEffect.MAX_INTEGRITY -> 6f
    ItemEffect.REGEN -> 0.15f
    ItemEffect.DASH_POWER -> 0.04f
    ItemEffect.DASH_EFFICIENCY -> 0.025f
    ItemEffect.CRIT_CHANCE -> 0.01f
    ItemEffect.CRIT_DAMAGE -> 0.08f
    ItemEffect.PICKUP_RADIUS -> 8f
    ItemEffect.LUCK -> 0.02f
    ItemEffect.DATA_GAIN -> 0.03f
    ItemEffect.MATTER_GAIN -> 0.025f
    ItemEffect.ATTACK_SPEED -> 0.03f
    ItemEffect.SHIELD_CAPACITY -> 5f
    ItemEffect.DAMAGE_REDUCTION -> 0.012f
    ItemEffect.COMBO_WINDOW -> 0.08f
    ItemEffect.OVERDRIVE_GAIN -> 0.04f
}

private fun scaledAmount(effect: ItemEffect, scale: Float): Float = baseAmount(effect) * scale

private fun describe(modifier: ItemModifier): String {
    val scaled = when (modifier.effect.unit) {
        ModifierUnit.PERCENT -> modifier.amount * 100f
        ModifierUnit.FLAT, ModifierUnit.PER_SECOND, ModifierUnit.SECONDS -> modifier.amount
    }
    val roundedTenths = (scaled * 10f + 0.5f).toInt()
    val value = if (roundedTenths % 10 == 0) {
        (roundedTenths / 10).toString()
    } else {
        "${roundedTenths / 10}.${roundedTenths % 10}"
    }
    val suffix = when (modifier.effect.unit) {
        ModifierUnit.PERCENT -> "%"
        ModifierUnit.FLAT -> ""
        ModifierUnit.PER_SECOND -> "/s"
        ModifierUnit.SECONDS -> "s"
    }
    return "+$value$suffix ${modifier.effect.displayLabel}"
}

private fun validateItems(items: List<ItemDefinition>) {
    check(ItemEffect.entries.size == 20) { "ItemEffect must contain exactly 20 effects" }
    check(itemFamilies.size == 20) { "Item catalog must contain exactly 20 families" }
    check(itemComponents.size == 20) { "Item catalog must contain exactly 20 components" }
    check(items.size == ItemCatalog.ITEM_COUNT) { "Item catalog must contain exactly 400 items" }
    check(items.withIndex().all { (index, item) -> item.id == index }) { "Item ids must be contiguous from 0 to 399" }
    check(items.map { it.name }.toSet().size == items.size) { "Item names must be unique" }
    check(items.map { it.description }.toSet().size == items.size) { "Item descriptions must be unique" }
    check(items.groupingBy { it.family }.eachCount().values.all { it == 20 }) {
        "Every item family must contain exactly 20 components"
    }
    check(items.map(::mechanicalSignature).toSet().size == items.size) {
        "Every catalog item must have a distinct mechanical profile"
    }
}

private fun mechanicalSignature(item: ItemDefinition): String {
    val totals = FloatArray(ItemEffect.entries.size)
    totals[item.primary.effect.ordinal] += item.primary.amount
    totals[item.secondary.effect.ordinal] += item.secondary.amount
    return buildString {
        totals.forEachIndexed { index, amount ->
            if (amount != 0f) append(index).append(':').append(amount.toBits()).append(';')
        }
        append("stacks:").append(item.maxStacks)
    }
}

enum class WeaponId {
    FLUX_WAKE,
    MORNINGSTAR,
    PHASE_LATTICE,
    NULL_LANCE,
    GRAVITY_MINES,
    ION_SWARM,
    RIFT_BLADES,
    ARC_COIL,
    QUASAR_CANNON,
    ENTROPY_FIELD,
    SINGULARITY_SPEAR,
    PRISM_RELAY,
}

enum class WeaponMastery(
    val displayLabel: String,
    val minimumLevel: Int,
    val damageBonus: Float,
    val activationSpeedBonus: Float,
) {
    CALIBRATED("Calibrated", 1, 0f, 0f),
    AMPLIFIED("Amplified", 3, 0.12f, 0.08f),
    RESONANT("Resonant", 6, 0.25f, 0.16f),
    ASCENDED("Ascended", 10, 0.45f, 0.25f),
    ;

    companion object {
        fun forLevel(level: Int): WeaponMastery = entries.last { level >= it.minimumLevel }
        fun after(level: Int): WeaponMastery? = entries.firstOrNull { level < it.minimumLevel }
    }
}

class WeaponDefinition(
    val id: WeaponId,
    val name: String,
    val description: String,
    tags: List<String>,
    val permanentUnlockCost: Int,
) {
    val tags: ImmutableList<String> = tags.toImmutableList()

    init {
        require(name.isNotBlank()) { "Weapon name must not be blank" }
        require(description.isNotBlank()) { "Weapon description must not be blank" }
        require(tags.isNotEmpty() && tags.none(String::isBlank)) { "Weapon tags must not be empty or blank" }
        require(permanentUnlockCost >= 0) { "Weapon unlock cost must be non-negative" }
    }

    override fun equals(other: Any?): Boolean =
        other is WeaponDefinition &&
            id == other.id &&
            name == other.name &&
            description == other.description &&
            tags == other.tags &&
            permanentUnlockCost == other.permanentUnlockCost

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + tags.hashCode()
        return 31 * result + permanentUnlockCost
    }

    override fun toString(): String =
        "WeaponDefinition(id=$id, name=$name, description=$description, " +
            "tags=$tags, permanentUnlockCost=$permanentUnlockCost)"
}

object WeaponCatalog {
    val all: ImmutableList<WeaponDefinition> = listOf(
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
        WeaponDefinition(WeaponId.PRISM_RELAY, "Prism Relay", "Launches a seeking light shard that refracts between targets; mastery adds ricochets and twin relays.", listOf("SEEKING", "RICOCHET", "REFRACTION"), 1_650),
    ).toImmutableList().also(::validateWeapons)

    fun byId(id: WeaponId): WeaponDefinition = all[id.ordinal]
}

private fun validateWeapons(weapons: List<WeaponDefinition>) {
    check(WeaponId.entries.size == 12) { "WeaponId must contain exactly 12 weapons" }
    check(weapons.size == 12) { "Weapon catalog must contain exactly 12 weapons" }
    check(weapons.withIndex().all { (index, weapon) -> weapon.id.ordinal == index }) {
        "Weapon catalog order must match WeaponId order"
    }
    check(weapons.map { it.id }.toSet().size == weapons.size) { "Weapon ids must be unique" }
    check(weapons.map { it.name }.toSet().size == weapons.size) { "Weapon names must be unique" }
}

enum class MetaUpgradeId {
    CORE_INTEGRITY,
    KINETIC_AMPLIFIER,
    MAGNETIC_RESONANCE,
    CRYO_VENTS,
    DASH_CAPACITOR,
    SALVAGE_PROTOCOL,
    DATA_ARCHIVE,
    ARMORY_LICENSE,
}

data class MetaUpgradeDefinition(
    val id: MetaUpgradeId,
    val name: String,
    val description: String,
    val maxRanks: Int,
    val baseCost: Int,
    val modifierPerRank: ItemModifier,
) {
    init {
        require(name.isNotBlank()) { "Meta-upgrade name must not be blank" }
        require(description.isNotBlank()) { "Meta-upgrade description must not be blank" }
        require(maxRanks > 0) { "Meta-upgrade maxRanks must be positive" }
        require(baseCost > 0) { "Meta-upgrade baseCost must be positive" }
    }

    /** Returns the price of the next rank when [level] ranks are already owned. */
    fun cost(level: Int): Int {
        require(level in 0 until maxRanks) { "level must be between 0 and ${maxRanks - 1}" }
        val rank = level.toLong() + 1L
        return (baseCost.toLong() * rank * rank).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

object MetaUpgradeCatalog {
    val all: ImmutableList<MetaUpgradeDefinition> = listOf(
        MetaUpgradeDefinition(MetaUpgradeId.CORE_INTEGRITY, "Core Integrity", "+10 maximum integrity at the start of every run.", 10, 18, ItemModifier(ItemEffect.MAX_INTEGRITY, 10f)),
        MetaUpgradeDefinition(MetaUpgradeId.KINETIC_AMPLIFIER, "Kinetic Amplifier", "+5% collision damage at the start of every run.", 10, 22, ItemModifier(ItemEffect.IMPACT_DAMAGE, 0.05f)),
        MetaUpgradeDefinition(MetaUpgradeId.MAGNETIC_RESONANCE, "Magnetic Resonance", "+4% magnetic pull strength at the start of every run.", 8, 24, ItemModifier(ItemEffect.MAGNETISM, 0.04f)),
        MetaUpgradeDefinition(MetaUpgradeId.CRYO_VENTS, "Cryo Vents", "+5% heat dissipation at the start of every run.", 8, 26, ItemModifier(ItemEffect.COOLING, 0.05f)),
        MetaUpgradeDefinition(MetaUpgradeId.DASH_CAPACITOR, "Dash Capacitor", "+5% dash impulse at the start of every run.", 8, 30, ItemModifier(ItemEffect.DASH_POWER, 0.05f)),
        MetaUpgradeDefinition(MetaUpgradeId.SALVAGE_PROTOCOL, "Salvage Protocol", "+5% Kinetic Matter gained during runs.", 10, 34, ItemModifier(ItemEffect.MATTER_GAIN, 0.05f)),
        MetaUpgradeDefinition(MetaUpgradeId.DATA_ARCHIVE, "Data Archive", "+5% Data gained during runs.", 10, 38, ItemModifier(ItemEffect.DATA_GAIN, 0.05f)),
        MetaUpgradeDefinition(MetaUpgradeId.ARMORY_LICENSE, "Armory License", "+4% power for every unlocked weapon.", 12, 45, ItemModifier(ItemEffect.WEAPON_POWER, 0.04f)),
    ).toImmutableList().also(::validateMetaUpgrades)

    fun byId(id: MetaUpgradeId): MetaUpgradeDefinition = all[id.ordinal]
}

private fun validateMetaUpgrades(upgrades: List<MetaUpgradeDefinition>) {
    check(MetaUpgradeId.entries.size == 8) { "MetaUpgradeId must contain exactly 8 upgrades" }
    check(upgrades.size == 8) { "Meta-upgrade catalog must contain exactly 8 upgrades" }
    check(upgrades.withIndex().all { (index, upgrade) -> upgrade.id.ordinal == index }) {
        "Meta-upgrade catalog order must match MetaUpgradeId order"
    }
    check(upgrades.map { it.id }.toSet().size == upgrades.size) { "Meta-upgrade ids must be unique" }
    check(upgrades.map { it.name }.toSet().size == upgrades.size) { "Meta-upgrade names must be unique" }
}
