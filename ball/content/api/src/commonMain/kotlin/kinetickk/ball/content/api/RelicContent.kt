// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/**
 * The field a Relic draws on: Kinetic, one of the seven elements, or Sovereign. Elemental aspects follow
 * [StatusElement] order and Sovereign stays last; presentation colors map by ordinal.
 */
enum class RelicAspect(val displayLabel: String, val element: StatusElement?) {
    KINETIC("Kinetic", null),
    THERMAL("Thermal", StatusElement.THERMAL),
    CRYO("Cryo", StatusElement.CRYO),
    ION("Ion", StatusElement.ION),
    GRAVITIC("Gravitic", StatusElement.GRAVITIC),
    ENTROPY("Entropy", StatusElement.ENTROPY),
    SONIC("Sonic", StatusElement.SONIC),
    VOID("Void", StatusElement.VOID),
    SOVEREIGN("Sovereign", null),
}

/**
 * Public resonance keys. Players predict Relic interactions from them: a shared key harmonizes and an
 * opposed pair of keys interferes. Named pairings refine specific combinations.
 */
enum class RelicKey(val displayLabel: String) {
    MOTION("Motion"),
    STILLNESS("Stillness"),
    HEAT("Heat"),
    COLD("Cold"),
    CHARGE("Charge"),
    GRAVITY("Gravity"),
    DECAY("Decay"),
    LIFE("Life"),
    SHIELD("Shield"),
    FRAGILE("Fragile"),
    ECHO("Echo"),
    CRITICAL("Critical"),
    SPREAD("Spread"),
    VOID("Void");

    companion object {
        private val Opposed: Set<Pair<RelicKey, RelicKey>> = setOf(
            HEAT to COLD,
            MOTION to STILLNESS,
            SHIELD to FRAGILE,
            LIFE to FRAGILE,
        )

        fun opposed(a: RelicKey, b: RelicKey): Boolean = (a to b) in Opposed || (b to a) in Opposed
    }
}

enum class RelicId {
    PERPETUAL_VECTOR,
    BLINK_DRIVE,
    GYRE_ENGINE,
    GLASS_COMET,
    EXECUTIONERS_PRISM,
    ECHO_CHAMBER,
    FURNACE_COVENANT,
    CINDER_CROWN,
    OVERDRIVE_CASCADE,
    ABSOLUTE_ZERO,
    STASIS_ANCHOR,
    STORMHEART_CAPACITOR,
    AEGIS_INVERSION,
    NEUTRON_HEART,
    EVENT_HORIZON,
    VERDANT_ROT,
    PANDEMIC_CODEX,
    AGONY_SCEPTER,
    HOLLOW_CROWN,
    OUROBOROS_SEAL,
    THUNDER_CHOIR,
    NULL_COVENANT,
}

/**
 * A numeric Relic parameter: [base] at rank 1 plus [perRank] for every rank above it. Upside text
 * references values positionally as `{0}`, `{1}`, … so balance never edits prose.
 */
data class RelicValue(val base: Float, val perRank: Float = 0f) {
    init {
        require(base.isFinite() && perRank.isFinite())
    }

    fun at(rank: Int): Float = base + perRank * (rank - 1).coerceAtLeast(0)
}

data class RelicDefinition(
    val id: RelicId,
    val name: String,
    val aspect: RelicAspect,
    val keys: ImmutableList<RelicKey>,
    val attunedTags: ImmutableList<BuildTag>,
    /** The rule rewrite. May reference [values] as `{n}`. */
    val upside: String,
    /** The mandatory cost. Never scales with rank. */
    val liability: String,
    val values: ImmutableList<RelicValue> = immutableListOf(),
    val minimumRebirth: Int = 0,
    val meldable: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "Relic name must not be blank" }
        require(upside.isNotBlank()) { "Relic upside must not be blank" }
        require(liability.isNotBlank()) { "Every Relic must carry a liability" }
        require(keys.size in 1..2 && keys.toSet().size == keys.size) { "Relics carry one or two distinct keys" }
        require(attunedTags.size <= 2 && attunedTags.toSet().size == attunedTags.size)
        require(minimumRebirth in ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL)
        require(values.size <= ContentBounds.MAX_RELIC_VALUES)
    }

    val isSovereign: Boolean get() = aspect == RelicAspect.SOVEREIGN

    fun value(index: Int, rank: Int): Float = values[index].at(rank)

    /** True when the two Relics share a key; named pairings take precedence. */
    fun harmonizes(other: RelicDefinition): Boolean =
        keys.any { it in other.keys } && !dissonates(other)

    fun dissonates(other: RelicDefinition): Boolean =
        keys.any { mine -> other.keys.any { theirs -> RelicKey.opposed(mine, theirs) } }
}

enum class RelicPairingKind { CONCORD, DISCORD }

enum class RelicPairingId {
    ENDLESS_GYRE,
    ASSASSINS_LINE,
    MELTDOWN,
    COLD_VERDICT,
    LIGHTNING_ROD,
    BLACK_STAR,
    BLIGHT_GARDEN,
    FIRESTORM,
    FORBIDDEN_ECHO,
    ROT_WARD,
    STILL_WATERS,
    FOLD_VECTOR,
    HOLLOW_VOW,
    FARADAY_CAGE,
    THERMAL_PARADOX,
    EXTINGUISHED,
    SHATTERED_AEGIS,
    CONTRADICTION,
    HEAVY_BLINK,
    FRAGILE_REBIRTH,
}

/** A named relic-to-relic interaction: amplification (concord) or interference (discord). */
data class RelicPairingDefinition(
    val id: RelicPairingId,
    val first: RelicId,
    val second: RelicId,
    val kind: RelicPairingKind,
    val name: String,
    val description: String,
) {
    init {
        require(first != second) { "A pairing joins two different Relics" }
        require(name.isNotBlank() && description.isNotBlank())
    }

    fun joins(a: RelicId, b: RelicId): Boolean = (first == a && second == b) || (first == b && second == a)

    fun involves(relic: RelicId): Boolean = first == relic || second == relic
}

data class EquippedRelic(
    val id: RelicId,
    val rank: Int,
) {
    init {
        require(rank > 0) { "Relic rank must be positive" }
    }
}

data class RelicPolicy(
    val maxSlots: Int,
    val maxRank: Int,
    /** Matter salvaged when a relic offer is left hollow or a resonance cannot rank any further. */
    val salvageMatter: Int = 8,
    /** Rerolls granted for leaving an Heirloom offer. */
    val heirloomDeclineRerolls: Int = 2,
) {
    init {
        require(maxSlots > 0) { "Relic maxSlots must be positive" }
        require(maxRank > 0) { "Relic maxRank must be positive" }
        require(salvageMatter >= 0 && heirloomDeclineRerolls >= 0) { "Relic salvage must not be negative" }
    }

    fun acceptsRank(rank: Int): Boolean = rank in 1..maxRank

    /** Heirlooms offered at the start of a Reborn run: two, three from Rebirth 5. */
    fun heirloomOffers(rebirthLevel: Int): Int = if (rebirthLevel >= 5) 3 else 2
}
