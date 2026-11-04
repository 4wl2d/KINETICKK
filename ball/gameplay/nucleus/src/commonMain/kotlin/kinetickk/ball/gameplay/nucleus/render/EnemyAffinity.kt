// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

/**
 * Codec for [EnemyProjection.affinityBits]. Bits 0-2 hold the aspect element ordinal + 1 (0 for none),
 * bit 3 the immune flag, bits 4-6 the weakness ordinal + 1, bit 7 an armored frame (impact taken below 1),
 * bit 8 a fragile frame (above 1), bit 9 the Architect's shift and bits 10-11 its phase. Bits 12-15 are reserved
 * for the enemy roster's own facts, bits 16-18 hold an Elite affix ordinal + 1 (0 for none) and bit 19 marks a
 * champion. Element and affix decoders return [NONE] when there is none.
 */
object EnemyAffinity {
    const val NONE: Int = -1

    private const val ELEMENT_MASK: Int = 0x7
    private const val IMMUNE_BIT: Int = 1 shl 3
    private const val WEAKNESS_SHIFT: Int = 4
    private const val ARMORED_BIT: Int = 1 shl 7
    private const val FRAGILE_BIT: Int = 1 shl 8
    private const val SHIFTING_BIT: Int = 1 shl 9
    private const val PHASE_SHIFT: Int = 10
    private const val PHASE_MASK: Int = 0x3
    private const val AFFIX_SHIFT: Int = 16
    private const val AFFIX_MASK: Int = 0x7
    private const val CHAMPION_BIT: Int = 1 shl 19

    fun encode(
        aspect: Int,
        immune: Boolean,
        weakness: Int,
        armored: Boolean,
        fragile: Boolean,
        shifting: Boolean,
        phase: Int,
        affix: Int = NONE,
        champion: Boolean = false,
    ): Int {
        var bits = elementField(aspect)
        if (immune && aspect >= 0) bits = bits or IMMUNE_BIT
        bits = bits or (elementField(weakness) shl WEAKNESS_SHIFT)
        if (armored) bits = bits or ARMORED_BIT
        if (fragile) bits = bits or FRAGILE_BIT
        if (shifting) bits = bits or SHIFTING_BIT
        bits = bits or ((phase and PHASE_MASK) shl PHASE_SHIFT)
        if (affix in 0 until AFFIX_MASK) bits = bits or ((affix + 1) shl AFFIX_SHIFT)
        if (champion) bits = bits or CHAMPION_BIT
        return bits
    }

    /** Aspect element ordinal, or [NONE]. */
    fun aspect(bits: Int): Int = (bits and ELEMENT_MASK) - 1

    fun immune(bits: Int): Boolean = bits and IMMUNE_BIT != 0

    /** Weakness element ordinal, or [NONE]. */
    fun weakness(bits: Int): Int = ((bits shr WEAKNESS_SHIFT) and ELEMENT_MASK) - 1

    fun armored(bits: Int): Boolean = bits and ARMORED_BIT != 0

    fun fragile(bits: Int): Boolean = bits and FRAGILE_BIT != 0

    fun shifting(bits: Int): Boolean = bits and SHIFTING_BIT != 0

    fun phase(bits: Int): Int = (bits shr PHASE_SHIFT) and PHASE_MASK

    /** Elite affix ordinal, or [NONE]. */
    fun affix(bits: Int): Int = ((bits shr AFFIX_SHIFT) and AFFIX_MASK) - 1

    fun champion(bits: Int): Boolean = bits and CHAMPION_BIT != 0

    private fun elementField(element: Int): Int = if (element in 0 until ELEMENT_MASK) element + 1 else 0
}
