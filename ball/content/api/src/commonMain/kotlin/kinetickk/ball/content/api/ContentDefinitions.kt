// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
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
    /** A plain scalar, not a share: offers, drops and Luck-sensitive every-Nth gates read it directly. */
    LUCK("Luck", ModifierUnit.FLAT),
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

/**
 * A hand-authored item. Passive [stats] apply per stack; [clauses] are Trigger–Condition–Effect
 * rules; [rules] modify whole systems while the item is owned. Ids are dense catalog indices.
 */
data class ItemDefinition(
    val id: Int,
    val name: String,
    /** Flavor line; mechanical text is generated from the data below. */
    val description: String,
    val rarity: ItemRarity,
    val tags: ImmutableList<BuildTag>,
    val elements: ImmutableList<StatusElement> = immutableListOf(),
    val stats: ImmutableList<StatDelta> = immutableListOf(),
    val clauses: ImmutableList<EffectClause> = immutableListOf(),
    val rules: ImmutableList<ItemRule> = immutableListOf(),
    val maxStacks: Int,
    val unlockLevel: Int,
    val minimumRebirth: Int = 0,
    /** High-risk items carry a liability and feed the Volatile offer lane. */
    val risk: Boolean = false,
) {
    init {
        require(id >= 0) { "Item id must be non-negative" }
        require(name.isNotBlank()) { "Item name must not be blank" }
        require(description.isNotBlank()) { "Item description must not be blank" }
        require(maxStacks in 1..ContentBounds.MAX_ITEM_STACKS) { "Item maxStacks must be within the stack bound" }
        require(unlockLevel > 0) { "Item unlockLevel must be positive" }
        require(minimumRebirth in ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL)
        require(tags.size <= 2 && tags.toSet().size == tags.size) { "Items carry at most two distinct tags" }
        require(elements.size <= 2 && elements.toSet().size == elements.size) { "Items carry at most two distinct elements" }
        require(stats.size <= ContentBounds.MAX_ITEM_STATS)
        require(clauses.size <= ContentBounds.MAX_ITEM_CLAUSES)
        require(rules.size <= ContentBounds.MAX_ITEM_RULES)
        require(stats.isNotEmpty() || clauses.isNotEmpty() || rules.isNotEmpty()) { "Item must do something" }
        require(!risk || stats.any { it.amount < 0f } || rules.any(ItemRule::isLiability)) {
            "A high-risk item must declare its liability"
        }
        require(rules.count { it is ItemRule.ReactionKeystone } <= 1) { "An item switches at most one reaction to its keystone" }
        require(keystone == null || rarity.rank >= ItemRarity.EPIC.rank) { "Keystone items are Epic or Legendary" }
    }

    /** The reaction this item switches to its keystone form, or null. */
    val keystone: ReactionId?
        get() = rules.firstNotNullOfOrNull { (it as? ItemRule.ReactionKeystone)?.reaction }
}

/** Conversions that cost the owner something (risk items may declare one as their liability). */
private val LiabilityConversions: Set<ConversionKind> = setOf(
    ConversionKind.REGENERATION_TO_DECAY,
    ConversionKind.CRIT_CHANCE_TO_DAMAGE,
    ConversionKind.DASH_HEAT_TO_SKILLS,
    ConversionKind.DATA_TO_SKILLS,
    ConversionKind.SKILLS_COST_HEAT,
)

/** Rules that cost the owner something; risk items must declare at least one. */
val ItemRule.isLiability: Boolean
    get() = when (this) {
        is ItemRule.WhileModifier -> (stat == BuffStat.DAMAGE_TAKEN) == (amount > 0f)
        is ItemRule.NonCriticalDamage -> amount < 0f
        is ItemRule.DamageVersus -> amount < 0f
        is ItemRule.ReactionRecoil -> true
        is ItemRule.Convert -> kind in LiabilityConversions
        else -> false
    }

/**
 * An item of the Data economy: a positive Data gain, a Data grant, or a Spored mark (Spored deaths drop Data).
 * The deck, the bots and the level calibration read it; a skill refund paid in Data is not Data economy.
 */
val ItemDefinition.isDataEconomy: Boolean
    get() = stats.any { it.effect == ItemEffect.DATA_GAIN && it.amount > 0f } ||
        clauses.any { clause ->
            clause.actions.any { it is EffectAction.GainData || it is EffectAction.ApplyMark && it.mark == MarkPick.SPORED }
        }

/** Append only: persisted by stable codec ids and indexed by ordinal (Agony forms, icons, card marks). */
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
    GYRE_DISC,
    SCATTER_BLOOM,
    TETHER_ARC,
    SENTRY_GRID,
    HUNTER_SALVO,
    CONCUSSION_ENGINE,
    GLACIER_BELL,
    EVENT_LENS,
    SOLAR_SWEEP,
    AEGIS_SHARDS,
    HOLLOW_RIFT,
    RESONANCE_DRUM,
}

/** How a weapon reaches enemies; balance compares each family on its primary metric. */
enum class WeaponFamily(val displayLabel: String) {
    MOMENTUM("Momentum"),
    ORBITAL("Orbital"),
    AURA("Aura"),
    PROJECTILE("Projectile"),
    TRAP("Trap"),
    CHAIN("Chain"),
    BEAM("Beam"),
    PULSE("Pulse"),
}

/** The one run stat that scales a weapon's own power; it never also feeds the weapon's base or count formula. */
enum class WeaponScaling(val displayLabel: String) {
    SPEED("Speed"),
    MASS("Mass"),
    HEAT("Heat"),
    COMBO("Combo"),
    SHIELD("Shield"),
    BRAKE("Brake"),
    DASH("Dash"),
    CRIT("Critical chance"),
    STATUS("Status"),
    OVERDRIVE("Overdrive"),
    LUCK("Luck"),
    ATTACK_SPEED("Attack speed"),
}

/**
 * How attack speed reaches a weapon: [DISCRETE] activations wait on the cooldown, while [CONTINUOUS] and
 * [CONTACT] damage and rotation scale with it instead.
 */
enum class WeaponCadence { CONTINUOUS, CONTACT, DISCRETE }

/**
 * Weapon level milestones. [damageBonus] is the generic mastery power (0/+10/+20/+35 %); each milestone
 * after the first also opens the weapon's next concrete form ([WeaponDefinition.masteryForms]).
 */
enum class WeaponMastery(
    val displayLabel: String,
    val minimumLevel: Int,
    val damageBonus: Float,
    val activationSpeedBonus: Float,
) {
    CALIBRATED("Calibrated", 1, 0f, 0f),
    AMPLIFIED("Amplified", 3, 0.10f, 0.08f),
    RESONANT("Resonant", 6, 0.20f, 0.16f),
    ASCENDED("Ascended", 10, 0.35f, 0.25f),
}

/**
 * A weapon's typed identity: what it is ([family], [element], [cadence]), what it resonates with (two
 * build [tags], a Relic [key], a [scaling] stat and a [signature] character) and how it unlocks
 * ([permanentUnlockCost] Matter at Rebirth rank [minimumRebirth] or higher). [masteryForms] name the
 * level 3, 6 and 10 forms and [signatureText] the twist the signature character adds.
 */
class WeaponDefinition(
    val id: WeaponId,
    val name: String,
    val description: String,
    val family: WeaponFamily,
    val element: StatusElement?,
    tags: List<BuildTag>,
    val key: RelicKey,
    val signature: CoreShape,
    val scaling: WeaponScaling,
    val cadence: WeaponCadence,
    val permanentUnlockCost: Int,
    val minimumRebirth: Int,
    masteryForms: List<String>,
    val signatureText: String,
    /** Matter for Honing level 1; level `n + 1` costs `upgradeBaseCost × 2^n` ([UpgradePolicy.nextCost]). */
    val upgradeBaseCost: Int = defaultWeaponUpgradeBaseCost(minimumRebirth),
) {
    val tags: ImmutableList<BuildTag> = tags.toImmutableList()
    val masteryForms: ImmutableList<String> = masteryForms.toImmutableList()

    init {
        require(name.isNotBlank()) { "Weapon name must not be blank" }
        require(description.isNotBlank()) { "Weapon description must not be blank" }
        require(this.tags.size == WEAPON_TAG_COUNT && this.tags.toSet().size == WEAPON_TAG_COUNT) {
            "A weapon carries exactly two distinct build tags"
        }
        require(permanentUnlockCost >= 0) { "Weapon unlock cost must be non-negative" }
        require(minimumRebirth in ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL) {
            "Weapon Rebirth gate must be within the Rebirth tiers"
        }
        require(this.masteryForms.size == WEAPON_MASTERY_FORMS && this.masteryForms.none(String::isBlank)) {
            "A weapon names exactly three mastery forms"
        }
        require(signatureText.isNotBlank()) { "Weapon signature text must not be blank" }
        require(upgradeBaseCost in 1..ContentBounds.MAX_UPGRADE_BASE_COST) {
            "Honing base cost must be within 1..${ContentBounds.MAX_UPGRADE_BASE_COST}"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is WeaponDefinition &&
            id == other.id &&
            name == other.name &&
            description == other.description &&
            family == other.family &&
            element == other.element &&
            tags == other.tags &&
            key == other.key &&
            signature == other.signature &&
            scaling == other.scaling &&
            cadence == other.cadence &&
            permanentUnlockCost == other.permanentUnlockCost &&
            minimumRebirth == other.minimumRebirth &&
            masteryForms == other.masteryForms &&
            signatureText == other.signatureText &&
            upgradeBaseCost == other.upgradeBaseCost

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + family.hashCode()
        result = 31 * result + (element?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + signature.hashCode()
        result = 31 * result + scaling.hashCode()
        result = 31 * result + cadence.hashCode()
        result = 31 * result + permanentUnlockCost
        result = 31 * result + minimumRebirth
        result = 31 * result + masteryForms.hashCode()
        result = 31 * result + signatureText.hashCode()
        return 31 * result + upgradeBaseCost
    }

    override fun toString(): String =
        "WeaponDefinition(id=$id, name=$name, description=$description, family=$family, element=$element, " +
            "tags=$tags, key=$key, signature=$signature, scaling=$scaling, cadence=$cadence, " +
            "permanentUnlockCost=$permanentUnlockCost, minimumRebirth=$minimumRebirth, " +
            "masteryForms=$masteryForms, signatureText=$signatureText, upgradeBaseCost=$upgradeBaseCost)"

    companion object {
        /** Build tags per weapon; tag resonance reads both. */
        const val WEAPON_TAG_COUNT: Int = 2

        /** Mastery forms per weapon, unlocked at weapon levels 3, 6 and 10. */
        const val WEAPON_MASTERY_FORMS: Int = 3
    }
}
