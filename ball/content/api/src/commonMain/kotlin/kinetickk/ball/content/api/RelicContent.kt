// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/** A non-elemental resonance carried by a Relic. */
enum class RelicAspect(val displayLabel: String) {
    VECTOR("Vector"),
    GRAVITIC("Gravitic"),
    ION("Ion"),
    RIFT("Rift"),
    PRISM("Prism"),
    ENTROPY("Entropy"),
    SOVEREIGN("Sovereign"),
}

enum class RelicId {
    KINETIC_FLYWHEEL,
    GHOST_VECTOR,
    OVERTAKE_PROTOCOL,
    SLIPSTREAM_RELAY,
    BRAKEPOINT_MEMORY,
    POLARITY_SLING,

    ORBITAL_NAIL,
    EVENTIDE_ANCHOR,
    PERIAPSIS_HOOK,
    CRUSH_DEPTH,
    MASS_ECHO,
    TIDAL_LOCK,

    VOLTAIC_FILAMENT,
    STATIC_CHORUS,
    ION_DEBT,
    CIRCUIT_BREAKER,
    RETURN_CIRCUIT,
    STORM_INDEX,

    ECHO_CHAMBER,
    PALIMPSEST_ROUND,
    SECOND_HAND,
    FRACTURE_GATE,
    SPLIT_HORIZON,
    BORROWED_MOMENT,

    GLASS_WITNESS,
    FRACTURE_LENS,
    SPECTRAL_FAN,
    HARDLIGHT_EDGE,
    CHROMA_FEEDBACK,
    MIRROR_CUT,

    HEAT_DEBT,
    SCAR_TISSUE,
    QUIETUS_BLOOM,
    DEVOURERS_TOLL,
    DOOM_CLOCK,
    LAST_LIGHT,

    AGONY_SCEPTER,
    CROWN_OF_FOUR_WINDS,
    MIRROR_OF_THE_HUNT,
    ENGINE_OF_PARADOX,
}

data class RelicDefinition(
    val id: RelicId,
    val name: String,
    val aspect: RelicAspect,
    val description: String,
    val rankEffect: String,
) {
    init {
        require(name.isNotBlank()) { "Relic name must not be blank" }
        require(description.isNotBlank()) { "Relic description must not be blank" }
        require(rankEffect.isNotBlank()) { "Relic rank effect must not be blank" }
    }

    val isSovereign: Boolean get() = aspect == RelicAspect.SOVEREIGN
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
) {
    init {
        require(maxSlots > 0) { "Relic maxSlots must be positive" }
        require(maxRank > 0) { "Relic maxRank must be positive" }
    }

    fun acceptsRank(rank: Int): Boolean = rank in 1..maxRank
}
