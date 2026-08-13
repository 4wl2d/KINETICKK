// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

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
        require(amount.isFinite() && amount > 0f) {
            "Item modifier amount must be finite and positive"
        }
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
        require(tags.isNotEmpty() && tags.none(String::isBlank)) {
            "Weapon tags must not be empty or blank"
        }
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
        require(level in 0 until maxRanks) {
            "level must be between 0 and ${maxRanks - 1}"
        }
        val rank = level.toLong() + 1L
        return (baseCost.toLong() * rank * rank)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }
}
