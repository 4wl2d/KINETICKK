// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange

/** Parameters of conditional effects; units and triggers are explicit, not inferred from catalog prose. */
internal fun MutableGameState.relicEffectValues(id: RelicId): List<Pair<String, Pair<Float, String>>> {
    val rank = relicRank(id)
    fun value(name: String, perRank: Float, unit: String = "", base: Float = 0f) =
        name to ((if (rank == 0) 0f else base + perRank * rank) to unit)
    fun damage(name: String, percent: Float) = value(name, percent, "%")
    return when (id) {
        RelicId.KINETIC_FLYWHEEL -> listOf(damage("Damage · speed ≥500", 5f), damage("Damage · speed ≥1600", 10f))
        RelicId.GHOST_VECTOR -> listOf(value("Damage · dash", 24f), value("Radius · dash", 14f, base = 72f))
        RelicId.OVERTAKE_PROTOCOL -> listOf(damage("Damage · enemy speed ≥170", 7f))
        RelicId.SLIPSTREAM_RELAY -> listOf(damage("Activation · fast kill, 3s", 6f))
        RelicId.BRAKEPOINT_MEMORY -> listOf(damage("Next hit · braking, max", 18f))
        RelicId.POLARITY_SLING -> listOf(damage("Damage · zero polarity", 8f))
        RelicId.ORBITAL_NAIL -> listOf(value("Pull · hit", 24f, "u/s"))
        RelicId.EVENTIDE_ANCHOR -> listOf(value("Damage · kill", 18f), value("Radius · kill", 18f, base = 72f))
        RelicId.PERIAPSIS_HOOK -> listOf(damage("Damage · distance >300", 8f))
        RelicId.CRUSH_DEPTH -> listOf(damage("Damage · distance ≤155", 6f))
        RelicId.MASS_ECHO -> listOf(damage("Damage · current mass", 4f * mass), value("Knockback · hit", 18f * mass, "u/s"))
        RelicId.TIDAL_LOCK -> listOf(damage("Damage · per hit, max 5", 2f))
        RelicId.VOLTAIC_FILAMENT -> listOf(damage("Arc · hit, every 0.28s", 16f))
        RelicId.STATIC_CHORUS -> listOf(value("Arc targets · every 7 hits", 1f))
        RelicId.ION_DEBT -> listOf(value("Damage · every 5 hits", 14f), value("Discharge radius", 10f, base = 95f))
        RelicId.CIRCUIT_BREAKER -> listOf(value("Damage · first hit", 11f), value("Slow · first hit", 8f, "%"))
        RelicId.RETURN_CIRCUIT -> listOf(damage("Damage · isolated target", 9f))
        RelicId.STORM_INDEX -> listOf(value("Arc · every 4 fast hits", 10f), value("Arc range", 45f, base = 220f))
        RelicId.ECHO_CHAMBER -> listOf(damage("Repeat damage · after 0.45s", 12f))
        RelicId.PALIMPSEST_ROUND -> listOf(damage("Repeat damage · every 7 hits", 20f))
        RelicId.SECOND_HAND -> listOf(value("Cooldown removed · kill", 0.08f, "s"))
        RelicId.FRACTURE_GATE -> listOf(value("Rupture · every 6 hits", 12f))
        RelicId.SPLIT_HORIZON -> listOf(value("Seeking shard · kill", 18f))
        RelicId.BORROWED_MOMENT -> listOf(damage("Activation · hurt, 2.5s", 9f))
        RelicId.GLASS_WITNESS -> listOf(damage("Damage · exposed, 3s", 7f))
        RelicId.FRACTURE_LENS -> listOf(damage("Critical chance · injured target", 2.5f))
        RelicId.SPECTRAL_FAN -> listOf(damage("2 refractions · every 6 hits", 14f))
        RelicId.HARDLIGHT_EDGE -> listOf(damage("Critical damage bonus", 12f))
        RelicId.CHROMA_FEEDBACK -> listOf(value("Shield · first hit", 1.5f), value("Overdrive · shield full", 2f))
        RelicId.MIRROR_CUT -> listOf(damage("Mirrored damage · critical", 15f))
        RelicId.HEAT_DEBT -> listOf(value("Rupture · every 5 hits", 16f), damage("Stored damage · hit", 8f))
        RelicId.SCAR_TISSUE -> listOf(value("Decay · 3s, max 5 stacks", 3f, "/s"))
        RelicId.QUIETUS_BLOOM -> listOf(value("Damage · kill", 10f), damage("Slow · kill", 10f), value("Radius · kill", 16f, base = 68f))
        RelicId.DEVOURERS_TOLL -> listOf(value("Integrity + shield · every 3 kills", 1.5f))
        RelicId.DOOM_CLOCK -> listOf(damage("Damage · elite / boss, now", 8f * (1f + runProgress)))
        RelicId.LAST_LIGHT -> listOf(damage("Damage · integrity ≤35%", 12f), damage("Activation · integrity ≤35%", 8f))
        RelicId.AGONY_SCEPTER -> listOf(value("Weapon mutation rank", 1f))
        RelicId.CROWN_OF_FOUR_WINDS -> listOf(damage("Damage · matrix aspects", 4f * distinctRelicAspectCount()))
        RelicId.MIRROR_OF_THE_HUNT -> listOf(damage("Mirrored damage · first hit", 20f))
        RelicId.ENGINE_OF_PARADOX -> listOf(damage("Activation · overdrive", 10f))
    }
}

internal fun MutableGameState.relicEffectChanges(id: RelicId, candidate: MutableGameState): List<RewardStatChange> {
    val before = relicEffectValues(id)
    return candidate.relicEffectValues(id).mapIndexedNotNull { index, (name, result) ->
        val prior = before[index].second.first
        if (prior == result.first) null else RewardStatChange(name, prior, result.first, result.second, sourceRelic = id)
    }
}
