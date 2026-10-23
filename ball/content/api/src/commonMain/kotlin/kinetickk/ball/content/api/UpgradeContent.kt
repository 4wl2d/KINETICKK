// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.math.roundToInt

/**
 * Permanent Refit (character) and Honing (weapon) tracks. A track runs from level 0 to [maxLevel];
 * the next level costs `base × 2^level` Matter ([nextCost]). Refit adds [characterIntegrityPerLevel]
 * maximum integrity and [characterPowerPerLevel] character-mechanic damage per level; Honing adds
 * [weaponPowerPerLevel] to the equipped weapon's own hits per level.
 */
data class UpgradePolicy(
    val maxLevel: Int = ContentBounds.MAX_UPGRADE_LEVEL,
    val characterIntegrityPerLevel: Float = 2f,
    val characterPowerPerLevel: Float = 0.02f,
    val weaponPowerPerLevel: Float = 0.03f,
) {
    init {
        require(maxLevel in 1..ContentBounds.MAX_UPGRADE_LEVEL) {
            "Upgrade max level must be within 1..${ContentBounds.MAX_UPGRADE_LEVEL}"
        }
        require(characterIntegrityPerLevel.isFinite() && characterIntegrityPerLevel >= 0f)
        require(characterPowerPerLevel.isFinite() && characterPowerPerLevel >= 0f)
        require(weaponPowerPerLevel.isFinite() && weaponPowerPerLevel >= 0f)
    }

    /** Matter for the level after [level]: `base × 2^level`, saturating at [Long.MAX_VALUE]. */
    fun nextCost(base: Int, level: Int): Long {
        require(base >= 0) { "Upgrade base cost must be non-negative" }
        require(level >= 0) { "Upgrade level must be non-negative" }
        if (base == 0) return 0L
        if (level >= Long.SIZE_BITS - 1 || base.toLong() > (Long.MAX_VALUE shr level)) return Long.MAX_VALUE
        return base.toLong() shl level
    }
}

/**
 * The one signature parameter each Refit level moves, in the unit of the character's mechanic (see
 * [defaultCharacterUpgradeSignature]); [secondaryPerLevel] is used only by a mechanic with two radii.
 */
data class CharacterUpgradeSignature(
    val perLevel: Float,
    val secondaryPerLevel: Float = 0f,
) {
    init {
        require(perLevel.isFinite() && secondaryPerLevel.isFinite()) { "Upgrade signature steps must be finite" }
    }
}

/**
 * Per Refit level: Orbit charge arc −2 % (fraction), Facet regrowth −3 % (fraction), Puncture heat refund
 * +0.8, Halo extra radius +3, Parry window +0.004 s, Lattice node life +0.2 s, Supernova base radius +4,
 * Echo absorb interval −0.03 s, Polarity Attract radius +4 and Repel radius +3, Pulse on-beat burst
 * radius +3, Blink recharge −0.04 s, Hive drone life +0.4 s.
 */
fun defaultCharacterUpgradeSignature(shape: CoreShape): CharacterUpgradeSignature = when (shape) {
    CoreShape.ORB -> CharacterUpgradeSignature(-0.02f)
    CoreShape.PRISM -> CharacterUpgradeSignature(-0.03f)
    CoreShape.SHARD -> CharacterUpgradeSignature(0.8f)
    CoreShape.RING -> CharacterUpgradeSignature(3f)
    CoreShape.DIAMOND -> CharacterUpgradeSignature(0.004f)
    CoreShape.TESSERACT -> CharacterUpgradeSignature(0.2f)
    CoreShape.STAR -> CharacterUpgradeSignature(4f)
    CoreShape.SPIRAL -> CharacterUpgradeSignature(-0.03f)
    CoreShape.DIPOLE -> CharacterUpgradeSignature(4f, secondaryPerLevel = 3f)
    CoreShape.PENTAGON -> CharacterUpgradeSignature(3f)
    CoreShape.CRESCENT -> CharacterUpgradeSignature(-0.04f)
    CoreShape.HEXAGON -> CharacterUpgradeSignature(0.4f)
}

/** Refit base price of a character class: 55, raised by 20 % per Reborn-rank unlock step (rounded to 5). */
fun defaultCharacterUpgradeBaseCost(shape: CoreShape): Int = when (shape) {
    CoreShape.SPIRAL -> rebirthScaledUpgradeBase(CHARACTER_UPGRADE_BASE_COST, 1)
    CoreShape.PENTAGON -> rebirthScaledUpgradeBase(CHARACTER_UPGRADE_BASE_COST, 3)
    CoreShape.CRESCENT -> rebirthScaledUpgradeBase(CHARACTER_UPGRADE_BASE_COST, 5)
    CoreShape.HEXAGON -> rebirthScaledUpgradeBase(CHARACTER_UPGRADE_BASE_COST, 7)
    CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD, CoreShape.RING, CoreShape.DIAMOND, CoreShape.TESSERACT,
    CoreShape.STAR, CoreShape.DIPOLE,
    -> CHARACTER_UPGRADE_BASE_COST
}

/** Honing base price of a weapon class: 40, raised by 20 % per Rebirth tier of its gate (rounded to 5). */
fun defaultWeaponUpgradeBaseCost(minimumRebirth: Int): Int =
    rebirthScaledUpgradeBase(WEAPON_UPGRADE_BASE_COST, minimumRebirth)

private const val CHARACTER_UPGRADE_BASE_COST: Int = 55
private const val WEAPON_UPGRADE_BASE_COST: Int = 40

private fun rebirthScaledUpgradeBase(base: Int, rebirth: Int): Int =
    ((base * (1f + 0.2f * rebirth.coerceAtLeast(0))) / 5f).roundToInt() * 5
