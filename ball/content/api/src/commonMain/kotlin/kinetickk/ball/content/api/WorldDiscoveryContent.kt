// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

enum class WorldDiscoveryKind { SITE, DIMENSION, STRUCTURE, SECRET, SECRET_BOSS }

/** Persisted as bit `ordinal` of the Profile's world discovery mask: append-only, never reorder or remove. */
enum class WorldDiscovery(val kind: WorldDiscoveryKind) {
    GATE_RACE(WorldDiscoveryKind.SITE),
    TRIALS(WorldDiscoveryKind.SITE),
    BOUNTY(WorldDiscoveryKind.SITE),
    DRIFT_CACHE(WorldDiscoveryKind.SITE),
    ZENITH(WorldDiscoveryKind.DIMENSION),
    NADIR(WorldDiscoveryKind.DIMENSION),
    ARCHIVE(WorldDiscoveryKind.DIMENSION),
    DERELICT_STATION(WorldDiscoveryKind.STRUCTURE),
    VAULT(WorldDiscoveryKind.STRUCTURE),
    SPIRE(WorldDiscoveryKind.STRUCTURE),
    LABYRINTH(WorldDiscoveryKind.STRUCTURE),
    FOUNDRY(WorldDiscoveryKind.STRUCTURE),
    RELIQUARY(WorldDiscoveryKind.STRUCTURE),
    LOST_CURSOR(WorldDiscoveryKind.SECRET),
    GOLDEN_DRIFTER(WorldDiscoveryKind.SECRET),
    SPIN_CYCLE(WorldDiscoveryKind.SECRET),
    ARCHITECTS_DOODLE(WorldDiscoveryKind.SECRET),
    QUIET_SIGNAL(WorldDiscoveryKind.SECRET),
    /** Set when the secret boss is defeated; the Codex reveals its enemy entry by it. */
    CARTOGRAPHER(WorldDiscoveryKind.SECRET_BOSS),
    ANTECEDENT(WorldDiscoveryKind.SECRET_BOSS),
    ;

    val bit: Int get() = 1 shl ordinal

    companion object {
        /** Every defined bit; the entry count stays at most [ContentBounds.MAX_WORLD_DISCOVERIES]. */
        val VALID_MASK: Int = (1 shl entries.size) - 1
    }
}

/** Codex text of one discovery; every text is an English content key. */
data class WorldDiscoveryDefinition(val id: WorldDiscovery, val name: String, val description: String, val hint: String) {
    init {
        require(name.isNotBlank() && description.isNotBlank() && hint.isNotBlank()) { "World discovery texts must not be blank" }
    }
}

/**
 * The discoveries in [WorldDiscovery] order. Names reuse the keys of the content that owns each discovery (site,
 * layer, structure template and enemy names); the structure and dimension lanes may refine their rows.
 */
fun defaultWorldDiscoveryDefinitions(): ImmutableList<WorldDiscoveryDefinition> = listOf(
    WorldDiscoveryDefinition(
        WorldDiscovery.GATE_RACE, "Gate race", "Gates laid in a loop, timed against a par.", "Some anomalies want speed.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.TRIALS, "Trials", "Rings with a rule: no brakes, no hits, only rams.", "Some anomalies set a rule.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.BOUNTY, "Bounty", "A marked champion waits inside its arena.", "Some anomalies hold a target.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.DRIFT_CACHE, "Drift cache", "Sealed supplies adrift far from the start.", "Fly farther than you need to.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.ZENITH, "Zenith", "A realm of grace above the field: halos, choirs and judgement.",
        "Some windows open upward.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.NADIR, "Nadir", "A furnace beneath the field where stopping burns.", "Some windows open downward.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.ARCHIVE, "Archive", "The Architect's memory, where time moves only when you do.",
        "Finish both realms in one run, or listen far away.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.DERELICT_STATION, "Derelict Station", "A gutted station still guarded by its crew of machines.",
        "Ruins stand far from the start.",
    ),
    WorldDiscoveryDefinition(WorldDiscovery.VAULT, "Vault", "Sealed rooms around one guarded treasure.", "Some ruins are locked."),
    WorldDiscoveryDefinition(WorldDiscovery.SPIRE, "Spire", "A tower of narrow halls climbing to its crown.", "Some ruins rise high."),
    WorldDiscoveryDefinition(WorldDiscovery.LABYRINTH, "Labyrinth", "Corridors that fold back on themselves.", "Some ruins twist."),
    WorldDiscoveryDefinition(
        WorldDiscovery.FOUNDRY, "Foundry", "Hot halls where something is still being made.", "Some ruins are still warm.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.RELIQUARY, "Reliquary", "A shrine that keeps one relic behind its doors.", "Some ruins keep relics.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.LOST_CURSOR, "The Lost Cursor", "A tiny grey cursor adrift far from the start. Now it follows you.",
        "Something small drifts where no one steers.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.GOLDEN_DRIFTER, "The Golden Drifter", "A rare drifter that flees and bursts into Data when caught.",
        "Deep space hides something that runs.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.SPIN_CYCLE, "Spin Cycle", "Ten turns around the singularity in ten seconds.", "Round and round, faster.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.ARCHITECTS_DOODLE, "The Architect's Doodle", "Someone built a ruin in the shape of the Core.",
        "One ruin looks familiar.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.QUIET_SIGNAL, "The Quiet Signal", "The Architect was not the first.",
        "Very far away, something is listening.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.CARTOGRAPHER, "Cartographer",
        "A secret boss that draws lanes of fire and lines of mines, then jumps between obstacles.",
        "Travel very far, very early.",
    ),
    WorldDiscoveryDefinition(
        WorldDiscovery.ANTECEDENT, "Antecedent",
        "A secret boss, the Architect's forerunner. It alternates Zenith and Nadir phases and hardens against your build.",
        "It sleeps in the Architect's memory.",
    ),
).toImmutableList()
