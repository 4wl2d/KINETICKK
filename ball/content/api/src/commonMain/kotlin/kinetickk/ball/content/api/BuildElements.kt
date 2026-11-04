// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * Mechanical archetypes. A build is never a fixed class: every owned item adds points to its tags
 * and the totals unlock resonance tiers.
 */
enum class BuildTag(val displayLabel: String) {
    RAM("Ram"),
    DRIVE("Drive"),
    ANCHOR("Anchor"),
    ARSENAL("Arsenal"),
    PRECISION("Precision"),
    BULWARK("Bulwark"),
    OVERCLOCK("Overclock"),
    CHAIN("Chain"),
    FORTUNE("Fortune"),
}

/**
 * Elemental fields. Impacts and weapons deal Kinetic damage, which carries no status. New elements are
 * appended: gameplay, presentation and the profile index cells and colors by ordinal.
 */
enum class StatusElement(
    val displayLabel: String,
    val statusLabel: String,
    val breakLabel: String,
) {
    THERMAL("Thermal", "Scorch", "Ignite"),
    CRYO("Cryo", "Chill", "Freeze"),
    ION("Ion", "Charge", "Overload"),
    GRAVITIC("Gravitic", "Mass", "Collapse"),
    ENTROPY("Entropy", "Decay", "Rupture"),
    SONIC("Sonic", "Resonance", "Shatter"),
    VOID("Void", "Hollow", "Unmake"),
}

/** Captured status tuning; every number is mechanical content, never presentation. */
data class ElementDefinition(
    val element: StatusElement,
    val statusDescription: String,
    val breakDescription: String,
    val breakThreshold: Int,
    val decaySeconds: Float,
    /** Per-stack strength: damage per second, slow fraction, arc damage or damage-taken bonus. */
    val perStack: Float,
    /** Break strength: area damage per stack, freeze seconds or a fraction of maximum integrity. */
    val breakPower: Float,
    val breakRadius: Float,
    /** No combination of affinity and item rules lowers the threshold below this floor. */
    val breakFloor: Int,
) {
    init {
        require(statusDescription.isNotBlank() && breakDescription.isNotBlank())
        require(breakThreshold in 2..ContentBounds.MAX_STATUS_THRESHOLD)
        require(breakFloor in 2..breakThreshold)
        require(decaySeconds.isFinite() && decaySeconds > 0f)
        require(perStack.isFinite() && perStack > 0f)
        require(breakPower.isFinite() && breakPower > 0f)
        require(breakRadius.isFinite() && breakRadius >= 0f)
    }

    /** Stacks above the threshold only deepen reactions and breaks. */
    val stackCap: Int get() = breakThreshold + STATUS_OVERFLOW_STACKS

    companion object {
        const val STATUS_OVERFLOW_STACKS: Int = 4
    }
}

/**
 * The twenty-one unordered element pairs. Each pair has exactly one reaction. Entries are appended in
 * release order; the profile stores discoveries by a stable wire id and gameplay by ordinal.
 */
enum class ReactionId(val first: StatusElement, val second: StatusElement) {
    THERMAL_SHOCK(StatusElement.THERMAL, StatusElement.CRYO),
    PLASMA_BURST(StatusElement.THERMAL, StatusElement.ION),
    FUSION(StatusElement.THERMAL, StatusElement.GRAVITIC),
    WILDFIRE(StatusElement.THERMAL, StatusElement.ENTROPY),
    SUPERCONDUCT(StatusElement.CRYO, StatusElement.ION),
    GLACIER_CRUSH(StatusElement.CRYO, StatusElement.GRAVITIC),
    EMBRITTLE(StatusElement.CRYO, StatusElement.ENTROPY),
    MAGNETIZE(StatusElement.ION, StatusElement.GRAVITIC),
    FEEDBACK(StatusElement.ION, StatusElement.ENTROPY),
    ROT_WELL(StatusElement.GRAVITIC, StatusElement.ENTROPY),
    THUNDERCLAP(StatusElement.THERMAL, StatusElement.SONIC),
    SHATTERPOINT(StatusElement.CRYO, StatusElement.SONIC),
    RESONANT_ARC(StatusElement.ION, StatusElement.SONIC),
    SEISMIC(StatusElement.GRAVITIC, StatusElement.SONIC),
    FRACTURE(StatusElement.ENTROPY, StatusElement.SONIC),
    SILENCE(StatusElement.SONIC, StatusElement.VOID),
    BLACKFIRE(StatusElement.THERMAL, StatusElement.VOID),
    STASIS(StatusElement.CRYO, StatusElement.VOID),
    ANNIHILATE(StatusElement.ION, StatusElement.VOID),
    NULL_WELL(StatusElement.GRAVITIC, StatusElement.VOID),
    OBLIVION(StatusElement.ENTROPY, StatusElement.VOID);

    fun involves(element: StatusElement): Boolean = first == element || second == element

    companion object {
        fun between(a: StatusElement, b: StatusElement): ReactionId? =
            entries.firstOrNull { (it.first == a && it.second == b) || (it.first == b && it.second == a) }
    }
}

data class ReactionDefinition(
    val id: ReactionId,
    val name: String,
    val description: String,
    /** Damage per consumed stack (or a fraction of maximum integrity for Feedback and Null Well). */
    val power: Float,
    val radius: Float,
    /** Multi-stage residue applied to every enemy the reaction touches. */
    val residue: StatusElement? = null,
    val residueStacks: Int = 0,
    /**
     * Content-owned numbers of the reaction's play-style behaviour (its verb), read by index in the order
     * the gameplay rules document; empty while the reaction has none, and a reaction without values keeps
     * only its base effect.
     */
    val values: ImmutableList<Float> = immutableListOf(),
    /** The play-style behaviour's name (Spall, Plasma Pool, ...); blank while the reaction has none. */
    val verb: String = "",
    /** The decision the verb asks of the player, at most [ContentBounds.MAX_REACTION_CUE_CHARS] characters. */
    val cue: String = "",
    /** The name of the reaction's keystone form, which a keystone item switches it to; blank when none. */
    val keystone: String = "",
    /** What the keystone form changes. */
    val keystoneDescription: String = "",
) {
    init {
        require(name.isNotBlank() && description.isNotBlank())
        require(power.isFinite() && power > 0f)
        require(radius.isFinite() && radius >= 0f)
        require((residue == null) == (residueStacks == 0)) { "Residue element and stacks are declared together" }
        require(residueStacks in 0..3)
        require(values.size <= ContentBounds.MAX_REACTION_VALUES) {
            "Reaction values size ${values.size} exceeds ${ContentBounds.MAX_REACTION_VALUES}"
        }
        require(values.all(Float::isFinite)) { "Reaction values must be finite" }
        require(verb.isBlank() == cue.isBlank()) { "A verb and its cue are declared together" }
        require(cue.length <= ContentBounds.MAX_REACTION_CUE_CHARS) {
            "Reaction cue length ${cue.length} exceeds ${ContentBounds.MAX_REACTION_CUE_CHARS}"
        }
        require(keystone.isBlank() == keystoneDescription.isBlank()) { "A keystone and its description are declared together" }
    }
}

/** Resonance tier text for one archetype; thresholds are shared [BuildPolicy] values. */
data class TagResonanceDefinition(
    val tag: BuildTag,
    val description: String,
    val tiers: ImmutableList<String>,
) {
    init {
        require(description.isNotBlank())
        require(tiers.size == ContentBounds.RESONANCE_TIERS && tiers.none(String::isBlank))
    }
}

/** StatusElement affinity tier text, shared by all elements. */
data class AffinityTierDefinition(val description: String) {
    init {
        require(description.isNotBlank())
    }
}
