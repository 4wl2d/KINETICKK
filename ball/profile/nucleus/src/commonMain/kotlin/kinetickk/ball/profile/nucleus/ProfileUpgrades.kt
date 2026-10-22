// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.rank
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.mapIndexedToImmutableList

/** A purchase the Profile would accept now for [Priced.cost] Matter, or the first check it fails. */
internal sealed interface PurchaseQuote {
    data class Priced(val cost: Long) : PurchaseQuote
    data class Refused(val reason: ProfileRejection) : PurchaseQuote
}

/**
 * The next rank of a Core Matrix node, checked in order: a rank below the maximum, the Rebirth rank at
 * the node's gate, any one prerequisite owned, then the next price in Matter.
 */
internal fun matrixNodeQuote(profile: PlayerProfile, definition: MatrixNodeDefinition): PurchaseQuote {
    val matrix = profile.matrix
    val currentRank = matrix.rank(definition.id)
    if (currentRank >= definition.maxRanks) return PurchaseQuote.Refused(ProfileRejection.MatrixNodeMaxRank)
    if (profile.rebirthProgress.rank < definition.minimumRebirth) {
        return PurchaseQuote.Refused(ProfileRejection.MatrixRebirthLocked)
    }
    if (definition.prerequisites.isNotEmpty() && definition.prerequisites.none(matrix::owns)) {
        return PurchaseQuote.Refused(ProfileRejection.MatrixPrerequisiteMissing)
    }
    val cost = definition.cost(currentRank).toLong()
    if (profile.economy.matter < cost) return PurchaseQuote.Refused(ProfileRejection.InsufficientMatter)
    return PurchaseQuote.Priced(cost)
}

/** Unlocking a weapon the profile does not own: the Rebirth rank at its gate, then its price in Matter. */
internal fun weaponUnlockQuote(profile: PlayerProfile, definition: WeaponDefinition): PurchaseQuote {
    if (profile.rebirthProgress.rank < definition.minimumRebirth) {
        return PurchaseQuote.Refused(ProfileRejection.WeaponRebirthLocked)
    }
    val cost = definition.permanentUnlockCost.toLong()
    if (profile.economy.matter < cost) return PurchaseQuote.Refused(ProfileRejection.InsufficientMatter)
    return PurchaseQuote.Priced(cost)
}

/**
 * The next Refit level of [shape], checked in order: the character is unlocked, its level is below
 * the policy maximum, then the next price `base × 2^level` against the Matter balance.
 */
internal fun characterUpgradeQuote(
    profile: PlayerProfile,
    policy: ProfilePolicySnapshot,
    shape: CoreShape,
): PurchaseQuote {
    val definition = policy.coreShapes.firstOrNull { it.id == shape }
    if (definition == null || !isCoreShapeUnlocked(profile, definition)) {
        return PurchaseQuote.Refused(ProfileRejection.CoreShapeLocked)
    }
    return upgradeQuote(profile.economy, policy.upgrades, definition.upgradeBaseCost, profile.upgrades.characterLevel(shape))
}

/**
 * The next Honing level of [id], checked in order: the weapon is owned, its level is below the
 * policy maximum, then the next price `base × 2^level` against the Matter balance.
 */
internal fun weaponUpgradeQuote(
    profile: PlayerProfile,
    policy: ProfilePolicySnapshot,
    id: WeaponId,
): PurchaseQuote {
    val definition = policy.weapons.firstOrNull { it.id == id }
    if (definition == null || id !in profile.loadout.unlockedWeapons) {
        return PurchaseQuote.Refused(ProfileRejection.WeaponNotOwned)
    }
    return upgradeQuote(profile.economy, policy.upgrades, definition.upgradeBaseCost, profile.upgrades.weaponLevel(id))
}

private fun upgradeQuote(economy: PlayerEconomy, policy: UpgradePolicy, base: Int, level: Int): PurchaseQuote {
    if (level >= policy.maxLevel) return PurchaseQuote.Refused(ProfileRejection.UpgradeMaxLevel)
    val cost = policy.nextCost(base, level)
    if (economy.matter < cost) return PurchaseQuote.Refused(ProfileRejection.InsufficientMatter)
    return PurchaseQuote.Priced(cost)
}

/**
 * True when the Workshop would sell something now: a weapon unlock, a Core Matrix rank, a Refit or a
 * Honing level. Evaluates the same quotes the purchase decisions use.
 */
internal fun isWorkshopAffordable(profile: PlayerProfile, policy: ProfilePolicySnapshot): Boolean =
    policy.weapons.any { it.id !in profile.loadout.unlockedWeapons && weaponUnlockQuote(profile, it) is PurchaseQuote.Priced } ||
        policy.matrixNodes.any { matrixNodeQuote(profile, it) is PurchaseQuote.Priced } ||
        policy.coreShapes.any { characterUpgradeQuote(profile, policy, it.id) is PurchaseQuote.Priced } ||
        policy.weapons.any { weaponUpgradeQuote(profile, policy, it.id) is PurchaseQuote.Priced }

/** Every bought Refit and Honing level. */
internal val PermanentUpgrades.totalLevels: Int get() = characterLevels.sum() + weaponLevels.sum()

/** [levels] with the entry at [index] one level higher. */
internal fun ImmutableList<Int>.raisedAt(index: Int): ImmutableList<Int> =
    mapIndexedToImmutableList { position, level -> if (position == index) level + 1 else level }

/**
 * Levels above the policy maximum (a rebalance lowered it) are clamped on load and the Matter paid
 * for the removed levels, `base × (2^level − 2^max)`, is refunded (saturating). Ownership and unlock
 * gates are purchase rules only, so a level is never removed for them. Lists of the wrong size are
 * left for the compatibility check to refuse.
 */
internal fun PlayerProfile.withUpgradePolicy(policy: ProfilePolicySnapshot): PlayerProfile {
    val characterLevels = upgrades.characterLevels
    val weaponLevels = upgrades.weaponLevels
    if (characterLevels.size != CoreShape.entries.size || weaponLevels.size != WeaponId.entries.size) return this
    val maxLevel = policy.upgrades.maxLevel
    if (characterLevels.all { it <= maxLevel } && weaponLevels.all { it <= maxLevel }) return this
    var refund = 0L
    fun clamp(level: Int, base: Int?): Int {
        if (level <= maxLevel) return level
        if (base != null) {
            for (removed in maxLevel until level) refund = saturatedAdd(refund, policy.upgrades.nextCost(base, removed))
        }
        return maxLevel
    }
    val clamped = PermanentUpgrades(
        characterLevels = characterLevels.mapIndexedToImmutableList { index, level ->
            clamp(level, policy.coreShapes.firstOrNull { it.id.ordinal == index }?.upgradeBaseCost)
        },
        weaponLevels = weaponLevels.mapIndexedToImmutableList { index, level ->
            clamp(level, policy.weapons.firstOrNull { it.id.ordinal == index }?.upgradeBaseCost)
        },
    )
    val matter = saturatedAdd(economy.matter, refund)
    return copy(
        economy = PlayerEconomy(matter = matter, lifetimeMatter = maxOf(economy.lifetimeMatter, matter)),
        upgrades = clamped,
    )
}

/** Upgrade lists sized for the closed catalogs, every level within `0..maxLevel`. */
internal fun PermanentUpgrades.isWithin(policy: UpgradePolicy): Boolean =
    characterLevels.size == CoreShape.entries.size && weaponLevels.size == WeaponId.entries.size &&
        characterLevels.all { it in 0..policy.maxLevel } && weaponLevels.all { it in 0..policy.maxLevel }
