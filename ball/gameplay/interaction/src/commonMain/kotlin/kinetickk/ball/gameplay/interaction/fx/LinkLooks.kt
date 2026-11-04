// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement

/** How a styled link is drawn (hud-fx §4.5). */
internal enum class LinkKind {
    /** The weapon's Arc Coil zigzag. */
    WEAPON,

    /** Dots hopping along a bulging arc (Wildfire, Flame Leap). */
    EMBER,

    /** Straight jittered lightning with a glow. */
    LIGHTNING,

    /** A frost trail brightening toward its end. */
    FROST,

    /** An ink beam with colour rims and travelling sparks (Annihilate). */
    BEAM,

    /** A taut line with a bone core (Arc Hook, Catalyze, Apex). */
    TAUT,

    /** Small sound arcs along the link (Resonator). */
    SOUND,
}

/** Which colours a link look takes: the style's element pair, its skill, element or the gold. */
internal enum class LinkTone { REACTION, SKILL, ELEMENT, GOLD, BONE }

/**
 * One link look: [kind], its presentation [life] (a styled link lives at least this long),
 * [segments] and [jitter] for lightning, and the end and middle marks it carries.
 */
internal class LinkLook(
    val kind: LinkKind,
    val tone: LinkTone,
    val life: Float,
    val segments: Int = 5,
    val jitter: Float = 8f,
    /** Colour A on the dots (Wildfire) or ink dots rimmed B with an A head (Flame Leap). */
    val inkDots: Boolean = false,
    /** Lightning: B pits at ¼, ½, ¾ (Feedback). */
    val pits: Boolean = false,
    /** Lightning: perpendicular B bars at ⅓ and ⅔ (Resonant Arc). */
    val bars: Boolean = false,
    /** A hexagon of radius 5 at the end (Superconduct). */
    val endHex: Boolean = false,
    /** An end shard along the ray (frost trails). */
    val endShard: Boolean = false,
    /** Two B rings at the end (Shatterpoint). */
    val endRings: Boolean = false,
    /** An end ring of this radius (0 none). */
    val endRing: Float = 0f,
    /** A bone core under the colour for lightning (Storm Coil). */
    val boneCore: Boolean = false,
)

/** Link looks by `LinkStyleCode` (0 weapon, 1 + reaction, 32 + skill, 64 + element Break, 96 + character Apex). */
internal object LinkLooks {
    const val REACTION_BASE: Int = 1
    const val SKILL_BASE: Int = 32
    const val BREAK_BASE: Int = 64
    const val APEX_BASE: Int = 96
    const val CODES: Int = 128

    /** The longest styled link presentation (seconds). */
    const val MAX_LIFE: Float = 0.35f

    private val weapon = LinkLook(LinkKind.WEAPON, LinkTone.REACTION, 0f)
    private val looks: Array<LinkLook> = Array(CODES) { build(it) }

    operator fun get(style: Int): LinkLook = if (style in 0 until CODES) looks[style] else weapon

    /** Whether [style] names a reaction, skill, element or character ([style] 0 and unknown codes draw the weapon look). */
    fun isKnown(style: Int): Boolean = style == 0 || (style in 0 until CODES && looks[style] !== weapon)

    /** The reaction ordinal of a reaction style, else −1. */
    fun reaction(style: Int): Int = (style - REACTION_BASE).takeIf { style >= REACTION_BASE && it < ReactionId.entries.size } ?: -1

    /** The skill ordinal of a skill style, else −1. */
    fun skill(style: Int): Int = (style - SKILL_BASE).takeIf { style >= SKILL_BASE && it < SkillId.entries.size } ?: -1

    /** The element ordinal of a Break style, else −1. */
    fun element(style: Int): Int = (style - BREAK_BASE).takeIf { style >= BREAK_BASE && it < StatusElement.entries.size } ?: -1

    private fun build(style: Int): LinkLook {
        if (style == 0) return weapon
        val reaction = ReactionId.entries.getOrNull(style - REACTION_BASE)?.takeIf { style < SKILL_BASE }
        if (reaction != null) return when (reaction) {
            ReactionId.WILDFIRE -> LinkLook(LinkKind.EMBER, LinkTone.REACTION, 0.30f)
            ReactionId.SUPERCONDUCT -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f, segments = 4, jitter = 4f, endHex = true)
            ReactionId.GLACIER_CRUSH -> LinkLook(LinkKind.FROST, LinkTone.REACTION, 0.25f, endShard = true)
            ReactionId.FEEDBACK -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f, segments = 6, jitter = 12f, pits = true)
            ReactionId.SHATTERPOINT -> LinkLook(LinkKind.FROST, LinkTone.REACTION, 0.25f, endRings = true)
            ReactionId.RESONANT_ARC -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f, segments = 5, jitter = 8f, bars = true)
            ReactionId.BLACKFIRE -> LinkLook(LinkKind.EMBER, LinkTone.REACTION, 0.35f, inkDots = true)
            ReactionId.ANNIHILATE -> LinkLook(LinkKind.BEAM, LinkTone.REACTION, 0.30f)
            else -> LinkLook(LinkKind.LIGHTNING, LinkTone.REACTION, 0.22f, segments = 5, jitter = 8f)
        }
        val skill = SkillId.entries.getOrNull(style - SKILL_BASE)?.takeIf { style >= SKILL_BASE && style < BREAK_BASE }
        if (skill != null) return when (skill) {
            SkillId.ARC_HOOK -> LinkLook(LinkKind.TAUT, LinkTone.SKILL, 0.25f, endRing = 9f)
            SkillId.STORM_COIL -> LinkLook(LinkKind.LIGHTNING, LinkTone.SKILL, 0.18f, segments = 4, jitter = 10f, boneCore = true)
            SkillId.RESONATOR -> LinkLook(LinkKind.SOUND, LinkTone.SKILL, 0.30f)
            SkillId.CATALYZE -> LinkLook(LinkKind.TAUT, LinkTone.BONE, 0.25f, endRing = 6f)
            else -> LinkLook(LinkKind.LIGHTNING, LinkTone.SKILL, 0.22f, segments = 5, jitter = 8f)
        }
        val element = StatusElement.entries.getOrNull(style - BREAK_BASE)?.takeIf { style >= BREAK_BASE && style < APEX_BASE }
        if (element != null) {
            return if (element == StatusElement.ION) LinkLook(LinkKind.LIGHTNING, LinkTone.ELEMENT, 0.20f, segments = 6, jitter = 10f)
            else LinkLook(LinkKind.LIGHTNING, LinkTone.ELEMENT, 0.20f, segments = 5, jitter = 8f)
        }
        if (style >= APEX_BASE && style - APEX_BASE < kinetickk.ball.content.api.CoreShape.entries.size) {
            return LinkLook(LinkKind.TAUT, LinkTone.GOLD, 0.25f)
        }
        return weapon
    }
}
