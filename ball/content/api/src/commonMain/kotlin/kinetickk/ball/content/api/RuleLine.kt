// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/**
 * Values that turn an item's data into concrete numbers for one owner. Without a scale, flat,
 * weapon and shield amounts read as neutral phrases; without an affinity, dominant and secondary
 * picks read as "your dominant element" and "your second element".
 */
data class RuleTextContext(
    /** Multiplies percentages, amounts and status stacks (stacks round half up, minimum 1). */
    val stackMagnitude: Float = 1f,
    val dominant: StatusElement? = null,
    val secondary: StatusElement? = null,
    /** Run progress and Rebirth factor applied to [DamageBasis.FLAT] amounts. */
    val flatScale: Float? = null,
    /** Damage of one [DamageBasis.WEAPON] unit ([WEAPON_BASIS_UNIT_DAMAGE] × ability power). */
    val weaponUnit: Float? = null,
    val shieldCapacity: Float? = null,
    /**
     * The item's Reborn and Relic attunement multiplier, as gameplay applies it: it scales damage,
     * stats, buffs, system powers and resource gains, never stack counts, heat, durations or terms
     * based on maximum integrity.
     */
    val bonus: Float = 1f,
    /** Copies of the item the text describes; threshold cuts and overfill grow per copy, not by magnitude. */
    val stacks: Int = 1,
    /**
     * Display scale of absolute integrity, shield and damage amounts (power units × this, compact from 1,000): the run's
     * [RebirthProfile.numberScale]. 1 writes the tier-0 base values, as the Codex does.
     */
    val numberScale: Double = 1.0,
) {
    init {
        require(stackMagnitude.isFinite() && stackMagnitude > 0f) { "Stack magnitude must be finite and positive" }
        require(stacks in 1..ContentBounds.MAX_ITEM_STACKS) { "Rule text stack count is outside the item bound" }
        require(bonus.isFinite() && bonus > 0f) { "Item bonus must be finite and positive" }
        require(listOfNotNull(flatScale, weaponUnit, shieldCapacity).all { it.isFinite() && it >= 0f }) {
            "Rule text scales must be finite and non-negative"
        }
        require(numberScale.isFinite() && numberScale > 0.0) { "Rule text number scale must be finite and positive" }
    }
}

enum class RuleLineKind { PASSIVE, TRIGGER, EFFECT, RULE, COST }

/** One display line. [negative] marks a cost; [element] tints the line when it names one. */
data class RuleLine(
    val kind: RuleLineKind,
    val text: String,
    val element: StatusElement? = null,
    val negative: Boolean = false,
)
