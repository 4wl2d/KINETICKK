// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ReactionId

/**
 * The 21 reaction looks (hud-fx §4.3 with the keystone deltas of §4.4), indexed by reaction ordinal.
 * The exhaustive `when` makes a new reaction fail to compile until it has a look.
 */
internal object ReactionFxSpecs {
    private val specs: Array<ReactionFxSpec> = Array(ReactionId.entries.size) { build(ReactionId.entries[it]) }

    operator fun get(reaction: Int): ReactionFxSpec = specs[reaction.coerceIn(0, specs.lastIndex)]

    operator fun get(reaction: ReactionId): ReactionFxSpec = specs[reaction.ordinal]

    /** The longest reaction look (seconds). */
    val maxLife: Float = specs.maxOf { it.life }

    private fun build(reaction: ReactionId): ReactionFxSpec = when (reaction) {
        // Spall: a white blast, a split ring in both hues, cracks, steam and spall flakes (Flash Freeze: a frost hexagon).
        ReactionId.THERMAL_SHOCK -> spec(0.60f, FxReach.TARGET, 0f,
            layer(FxPrimitive.DISC, 0f, 0.18f, FxEase.S, FxColor.BONE, r0 = 0.30f, r1 = 0.70f, alpha = 0.35f, flags = SIG),
            layer(FxPrimitive.SPLIT_RING, 0f, 0.70f, FxEase.O, FxColor.AB, r0 = 0.35f, r1 = 1f, w = 3f, spin = 120f, param = 172f, alpha = 0.95f,
                flags = SIG),
            layer(FxPrimitive.CRACKS, 0.05f, 0.55f, FxEase.O, FxColor.BONE, n = 3, r0 = 0.10f, r1 = 0.65f, w = 2f, param = 0.6f),
            layer(FxPrimitive.DOTS, 0.10f, 0.90f, FxEase.O, FxColor.BONE, n = 6, r0 = 0.20f, r1 = 0.90f, param = 3f, alpha = 0.5f,
                flags = SEC or FxFlag.RISE),
            layer(FxPrimitive.SPOKES, 0f, 0.25f, FxEase.S, FxColor.A, n = 4, r0 = 0.90f, r1 = 1.25f, w = 2f, param = 0.4f, flags = SEC or BASE),
            layer(FxPrimitive.HEX, 0f, 0.35f, FxEase.O, FxColor.B, r0 = 0.50f, r1 = 0.80f, w = 2f, flags = KEY),
        )
        // Plasma Pool: a flash with four forks and embers settling into the pool (Arc Plasma: six forks, no embers).
        ReactionId.PLASMA_BURST -> spec(0.65f, FxReach.AREA, 0f,
            layer(FxPrimitive.DISC, 0f, 0.30f, FxEase.S, FxColor.A, r0 = 0.25f, r1 = 0.80f, alpha = 0.30f, flags = SIG),
            layer(FxPrimitive.FORKS, 0f, 0.40f, FxEase.O, FxColor.B, n = 4, r0 = 0.20f, r1 = 1f, w = 2f, param = 0.12f, flags = SIG or BASE),
            layer(FxPrimitive.FORKS, 0f, 0.40f, FxEase.O, FxColor.B, n = 6, r0 = 0.20f, r1 = 1f, w = 2f, param = 0.12f, flags = SIG or KEY),
            layer(FxPrimitive.RING, 0.10f, 0.65f, FxEase.O, FxColor.A, r0 = 0.40f, r1 = 1.05f, w = 3f),
            layer(FxPrimitive.DOTS, 0.25f, 1f, FxEase.L, FxColor.A, n = 8, r0 = 0.20f, r1 = 0.95f, param = 3f, alpha = 0.6f, flags = SEC or BASE),
        )
        // Accretion: a thick ring closes and a star forms; the field carries the rest (Dwarf Star: four short spokes).
        ReactionId.FUSION -> spec(0.55f, FxReach.AREA, 0f,
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.20f, w = 4.5f, flags = SIG),
            layer(FxPrimitive.DISC, 0.20f, 0.80f, FxEase.O, FxColor.A, r0 = 0.05f, r1 = 0.18f, alpha = 0.35f, flags = SIG),
            layer(FxPrimitive.SPOKES, 0.25f, 0.85f, FxEase.O, FxColor.A, n = 6, r0 = 0.12f, r1 = 0.38f, w = 2f, spin = 30f, flags = BASE),
            layer(FxPrimitive.SPOKES, 0.25f, 0.85f, FxEase.O, FxColor.A, n = 4, r0 = 0.12f, r1 = 0.28f, w = 2f, spin = 30f, flags = KEY),
            layer(FxPrimitive.STREAKS_IN, 0f, 0.45f, FxEase.I, FxColor.B, n = 8, r0 = 1.10f, r1 = 0.30f, w = 1.5f, param = 0.18f, flags = SEC),
        )
        // Ablaze: flame tongues, rotting embers; the Core catches fire (Firewalker: always).
        ReactionId.WILDFIRE -> spec(0.70f, FxReach.FIXED, 96f,
            layer(FxPrimitive.FLAMES, 0f, 0.60f, FxEase.O, FxColor.A, n = 6, r0 = 0.40f, r1 = 0.80f, param = 0.22f, flags = SIG),
            layer(FxPrimitive.DOTS, 0.10f, 0.70f, FxEase.O, FxColor.B, n = 6, r0 = 0.20f, r1 = 0.90f, param = 3f),
            layer(FxPrimitive.RING, 0f, 0.45f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 0.70f, w = 2f, flags = SEC),
            layer(FxPrimitive.FLAMES, 0f, 0.50f, FxEase.O, FxColor.A, n = 8, r0 = 6f, r1 = 12f, param = 10f,
                flags = CORE or VERB or FxFlag.KEY_ALWAYS or SIG),
        )
        // Frictionless: an ice crystal with cross ticks; drag stripped from the Core (Zero Resistance: no Core layers).
        ReactionId.SUPERCONDUCT -> spec(0.55f, FxReach.FIXED, 72f,
            layer(FxPrimitive.SPOKES, 0f, 0.40f, FxEase.O, FxColor.A, n = 6, r0 = 0.10f, r1 = 0.80f, w = 2f, flags = SIG),
            layer(FxPrimitive.TICKS, 0f, 0.40f, FxEase.O, FxColor.A, n = 6, r0 = 0.55f, r1 = 0.55f, w = 1.5f, param = 0.20f,
                flags = SIG or FxFlag.TANGENT),
            layer(FxPrimitive.FORKS, 0f, 0.30f, FxEase.S, FxColor.B, n = 3, r0 = 0.10f, r1 = 0.60f, w = 1.5f, param = 0.08f, flags = SEC),
            layer(FxPrimitive.RING, 0f, 0.45f, FxEase.O, FxColor.A, r0 = 8f, r1 = 40f, w = 2f, flags = CORE or VERB or BASE),
            layer(FxPrimitive.SPOKES, 0f, 0.30f, FxEase.O, FxColor.BONE, n = 6, r0 = 4f, r1 = 20f, w = 1.5f, flags = CORE or VERB or SEC or BASE),
        )
        // Ice Fan: five shards fanning away from the Core over a gravity crush at the origin.
        ReactionId.GLACIER_CRUSH -> spec(0.55f, FxReach.FIXED, 220f,
            layer(FxPrimitive.SHARDS, 0f, 0.45f, FxEase.O, FxColor.A, n = 5, r0 = 0.05f, r1 = 1f, param = 18f, param2 = 25f, flags = SIG or DIR),
            layer(FxPrimitive.RING, 0f, 0.30f, FxEase.I, FxColor.B, r0 = 0.30f, r1 = 0.08f, w = 3f, flags = SIG),
            layer(FxPrimitive.SPOKES, 0f, 0.25f, FxEase.S, FxColor.B, n = 5, r0 = 0.05f, r1 = 0.35f, w = 1.5f, param2 = 25f, flags = DIR or SEC),
            layer(FxPrimitive.DOTS, 0.20f, 0.70f, FxEase.L, FxColor.A, n = 10, r0 = 0.30f, r1 = 1f, param = 2f, param2 = 55f, flags = DIR or SEC),
        )
        // Glass Charge: a glass pane cracks and the Embrittled ring appears (Glass Burst: the ring solid in A).
        ReactionId.EMBRITTLE -> spec(0.60f, FxReach.TARGET, 0f,
            layer(FxPrimitive.PLATE, 0f, 0.35f, FxEase.P, FxColor.A, r0 = 1.10f, r1 = 0.95f, w = 3f, spin = -8f, flags = SIG),
            layer(FxPrimitive.CRACKS, 0.10f, 0.60f, FxEase.O, FxColor.B, n = 4, r0 = 0.15f, r1 = 0.85f, w = 1.5f, param = 0.7f, flags = SIG),
            layer(FxPrimitive.DOTS, 0.20f, 0.80f, FxEase.O, FxColor.B, n = 6, r0 = 0.30f, r1 = 0.90f, param = 2.5f),
            layer(FxPrimitive.RING, 0.25f, 0.60f, FxEase.O, FxColor.A, r0 = 0.90f, r1 = 1.10f, w = 1.6f, flags = FxFlag.DOTTED or SEC or BASE),
            layer(FxPrimitive.RING, 0.25f, 0.60f, FxEase.O, FxColor.A, r0 = 0.90f, r1 = 1.10f, w = 1.5f, flags = SEC or KEY),
        )
        // Attractor: the yank inward and field lines spinning up (Repulsor: the pair pushes outward).
        ReactionId.MAGNETIZE -> spec(0.60f, FxReach.AREA, 0f,
            layer(FxPrimitive.STREAKS_IN, 0f, 0.40f, FxEase.I, FxColor.A, n = 10, r0 = 1f, r1 = 0.15f, w = 2f, param = 0.22f, flags = SIG or BASE),
            layer(FxPrimitive.RING, 0f, 0.35f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.30f, w = 3f, flags = SIG or BASE),
            layer(FxPrimitive.STREAKS_OUT, 0f, 0.40f, FxEase.O, FxColor.A, n = 10, r0 = 0.15f, r1 = 1f, w = 2f, param = 0.22f, flags = SIG or KEY),
            layer(FxPrimitive.RING, 0f, 0.35f, FxEase.O, FxColor.B, r0 = 0.30f, r1 = 1f, w = 3f, flags = SIG or KEY),
            layer(FxPrimitive.ARCS, 0.30f, 0.90f, FxEase.O, FxColor.AB, n = 4, r0 = 0.30f, r1 = 0.30f, w = 2f, spin = 180f, param = 50f),
            layer(FxPrimitive.DOTS_IN, 0.05f, 0.45f, FxEase.I, FxColor.B, n = 10, r0 = 1f, r1 = 0.20f, param = 2.5f, flags = SEC),
        )
        // Overcurrent: jagged forks, decay pits; the weapon kicked (Short Circuit: the Core layers in bone).
        ReactionId.FEEDBACK -> spec(0.55f, FxReach.FIXED, 90f,
            layer(FxPrimitive.FORKS, 0f, 0.35f, FxEase.S, FxColor.A, n = 3, r0 = 0.15f, r1 = 0.90f, w = 2f, param = 0.22f, flags = SIG),
            layer(FxPrimitive.DOTS, 0.05f, 0.70f, FxEase.O, FxColor.B, n = 8, r0 = 0.20f, r1 = 0.85f, param = 3f, flags = SIG),
            layer(FxPrimitive.SPLIT_RING, 0f, 0.50f, FxEase.O, FxColor.AB, r0 = 0.30f, r1 = 0.80f, w = 1.5f, spin = -90f, param = 172f, flags = SEC),
            layer(FxPrimitive.RING, 0f, 0.30f, FxEase.O, FxColor.A, r0 = 4f, r1 = 28f, w = 2f, flags = CORE or VERB or BASE),
            layer(FxPrimitive.ARCS, 0f, 0.30f, FxEase.O, FxColor.A, n = 3, r0 = 18f, r1 = 18f, w = 2f, spin = 90f, param = 40f,
                flags = CORE or VERB or SEC or BASE),
            layer(FxPrimitive.RING, 0f, 0.30f, FxEase.O, FxColor.BONE, r0 = 4f, r1 = 28f, w = 2f, flags = CORE or VERB or KEY),
            layer(FxPrimitive.ARCS, 0f, 0.30f, FxEase.O, FxColor.BONE, n = 3, r0 = 18f, r1 = 18f, w = 2f, spin = 90f, param = 40f,
                flags = CORE or VERB or SEC or KEY),
        )
        // Compost: a spiral drawing in under a growing rim (Plague Pit: eight arms).
        ReactionId.ROT_WELL -> spec(0.55f, FxReach.AREA, 0f,
            layer(FxPrimitive.SPIRAL, 0f, 0.55f, FxEase.I, FxColor.B, n = 6, r0 = 1f, r1 = 0.25f, w = 2f, param = 50f, flags = SIG or BASE),
            layer(FxPrimitive.SPIRAL, 0f, 0.55f, FxEase.I, FxColor.B, n = 8, r0 = 1f, r1 = 0.25f, w = 2f, param = 50f, flags = SIG or KEY),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.A, r0 = 0.60f, r1 = 1f, w = 3f, flags = SIG),
            layer(FxPrimitive.DOTS_IN, 0f, 0.55f, FxEase.I, FxColor.B, n = 12, r0 = 0.95f, r1 = 0.20f, param = 2.5f),
            layer(FxPrimitive.DISC, 0f, 0.50f, FxEase.O, FxColor.INK, r0 = 0.20f, r1 = 0.60f, alpha = 0.30f, flags = SEC),
        )
        // Blast Jump: a double sound ring and heat shimmer, handing over to the Core's ring (Rocket Clap: the Core launched).
        ReactionId.THUNDERCLAP -> spec(0.60f, FxReach.AREA, 0f,
            layer(FxPrimitive.RING, 0f, 0.45f, FxEase.O, FxColor.B, r0 = 0.15f, r1 = 1.10f, w = 4.5f, flags = SIG),
            layer(FxPrimitive.RING, 0.12f, 0.60f, FxEase.O, FxColor.B, r0 = 0.15f, r1 = 1.25f, w = 2f, flags = SIG),
            layer(FxPrimitive.DISC, 0f, 0.15f, FxEase.S, FxColor.A, r0 = 0.20f, r1 = 0.50f, alpha = 0.35f),
            layer(FxPrimitive.SPOKES, 0f, 0.30f, FxEase.O, FxColor.A, n = 12, r0 = 0.30f, r1 = 0.60f, w = 1.5f, param = 0.2f, flags = SEC),
            layer(FxPrimitive.RING, 0f, 0.15f, FxEase.O, FxColor.B, r0 = 30f, r1 = 14f, w = 2f, flags = CORE or VERB or BASE),
            layer(FxPrimitive.STREAKS_OUT, 0f, 0.30f, FxEase.O, FxColor.B, n = 6, r0 = 8f, r1 = 60f, w = 2f, param = 14f,
                flags = CORE or VERB or KEY),
        )
        // Shard Guard: radial shards and ringing rings far away; near the Core the shards fly to their orbit
        // (Crystal Choir: seven radial shards and the three rings always).
        ReactionId.SHATTERPOINT -> spec(0.60f, FxReach.FIXED, 240f,
            layer(FxPrimitive.SHARDS, 0f, 0.45f, FxEase.O, FxColor.A, n = 5, r0 = 0.05f, r1 = 1f, param = 16f, flags = DIR or NOT_VERB or SIG or BASE),
            layer(FxPrimitive.SHARDS, 0f, 0.45f, FxEase.O, FxColor.A, n = 7, r0 = 0.05f, r1 = 1f, param = 16f, flags = DIR or SIG or KEY),
            layer(FxPrimitive.RING, 0f, 0.35f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.35f, w = 3f,
                flags = NOT_VERB or FxFlag.KEY_ALWAYS or SIG),
            layer(FxPrimitive.RING, 0.10f, 0.45f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.45f, w = 2f, flags = NOT_VERB or FxFlag.KEY_ALWAYS),
            layer(FxPrimitive.RING, 0.20f, 0.55f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.55f, w = 1.5f,
                flags = NOT_VERB or FxFlag.KEY_ALWAYS or SEC),
            layer(FxPrimitive.SHARDS, 0f, 0.35f, FxEase.O, FxColor.A, n = 5, r0 = 0f, r1 = 1f, param = 16f, flags = FxFlag.CORE_TARGET or VERB or SIG),
            layer(FxPrimitive.RING, 0f, 0.35f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.40f, w = 2f, flags = VERB),
        )
        // Arc Weave: three arc stacks, a split ring and short forks; the weave comes online (Standing Wave: always).
        ReactionId.RESONANT_ARC -> spec(0.55f, FxReach.FIXED, 96f,
            layer(FxPrimitive.WAVES, 0f, 0.50f, FxEase.O, FxColor.B, n = 3, r0 = 0.30f, r1 = 0.90f, w = 2f, param = 70f, param2 = 0.15f,
                flags = SIG or FxFlag.TRI),
            layer(FxPrimitive.SPLIT_RING, 0f, 0.45f, FxEase.O, FxColor.AB, r0 = 0.20f, r1 = 0.60f, w = 2f, spin = 140f, param = 172f),
            layer(FxPrimitive.FORKS, 0f, 0.25f, FxEase.S, FxColor.A, n = 4, r0 = 0.10f, r1 = 0.50f, w = 1.5f, param = 0.1f, flags = SEC),
            layer(FxPrimitive.ARCS, 0f, 0.40f, FxEase.O, FxColor.B, n = 3, r0 = 18f, r1 = 18f, w = 2f, spin = 120f, param = 30f,
                flags = CORE or VERB or FxFlag.KEY_ALWAYS),
        )
        // Rock Echo: two quake rings and long ground cracks (the echoes are jagged shockwaves).
        ReactionId.SEISMIC -> spec(0.70f, FxReach.AREA, 0f,
            layer(FxPrimitive.QUAKE, 0f, 0.50f, FxEase.O, FxColor.A, r0 = 0.15f, r1 = 1f, w = 3f, flags = SIG),
            layer(FxPrimitive.QUAKE, 0.15f, 0.70f, FxEase.O, FxColor.A, r0 = 0.15f, r1 = 0.80f, w = 2f),
            layer(FxPrimitive.CRACKS, 0f, 0.45f, FxEase.O, FxColor.B, n = 6, r0 = 0.10f, r1 = 0.70f, w = 2f, param = 0.5f, flags = SIG),
            layer(FxPrimitive.DISC, 0f, 0.30f, FxEase.O, FxColor.INK, r0 = 0.20f, r1 = 0.60f, alpha = 0.35f, flags = SEC),
        )
        // Breakpoint: long cracks, decay dots and ticks on the rim (Crevasse: no ticks).
        ReactionId.FRACTURE -> spec(0.55f, FxReach.AREA, 0f,
            layer(FxPrimitive.CRACKS, 0f, 0.45f, FxEase.O, FxColor.A, n = 6, r0 = 0.10f, r1 = 1f, w = 2f, param = 0.45f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.B, r0 = 0.30f, r1 = 0.90f, w = 1.5f),
            layer(FxPrimitive.DOTS, 0.10f, 0.70f, FxEase.O, FxColor.A, n = 10, r0 = 0.20f, r1 = 1f, param = 2.5f, flags = SEC),
            layer(FxPrimitive.TICKS, 0.05f, 0.45f, FxEase.O, FxColor.B, n = 6, r0 = 0.95f, r1 = 0.95f, w = 2f, param = 0.12f, flags = SEC or BASE),
        )
        // Hush: bars collapse onto a punched-out centre (Dead Air: the punch at the Core).
        ReactionId.SILENCE -> spec(0.65f, FxReach.AREA, 0f,
            layer(FxPrimitive.BARS, 0f, 0.45f, FxEase.I, FxColor.A, n = 6, r0 = 0.80f, r1 = 0.10f, param = 0.18f, flags = SIG),
            layer(FxPrimitive.PUNCH, 0.20f, 0.65f, FxEase.O, FxColor.B, r0 = 0.05f, r1 = 0.25f, w = 2f, flags = SIG or BASE),
            layer(FxPrimitive.PUNCH, 0.20f, 0.65f, FxEase.O, FxColor.B, r0 = 6f, r1 = 30f, w = 2f, flags = CORE or SIG or KEY),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.40f, w = 2f),
            layer(FxPrimitive.DOTS, 0.30f, 0.90f, FxEase.L, FxColor.B, n = 6, r0 = 0.40f, r1 = 0.95f, param = 2f, alpha = 0.5f, flags = SEC),
        )
        // Flame Leap: ink flames rimmed in Void around a punch (Hellfire: ten longer flames).
        ReactionId.BLACKFIRE -> spec(0.70f, FxReach.TARGET, 0f,
            layer(FxPrimitive.FLAMES, 0f, 0.70f, FxEase.O, FxColor.B, n = 8, r0 = 0.45f, r1 = 0.85f, param = 0.30f,
                flags = FxFlag.INK_CORE or SIG or BASE),
            layer(FxPrimitive.FLAMES, 0f, 0.70f, FxEase.O, FxColor.B, n = 10, r0 = 0.45f, r1 = 0.85f, param = 0.36f,
                flags = FxFlag.INK_CORE or SIG or KEY),
            layer(FxPrimitive.PUNCH, 0f, 0.30f, FxEase.O, FxColor.B, r0 = 0.10f, r1 = 0.35f, w = 2f, flags = SIG),
            layer(FxPrimitive.DOTS, 0.10f, 0.70f, FxEase.O, FxColor.A, n = 6, r0 = 0.50f, r1 = 1f, param = 2.5f),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.A, r0 = 0.40f, r1 = 0.95f, w = 1.5f, flags = SEC),
        )
        // Time Bubble: clock ticks freeze and a clock hand stops.
        ReactionId.STASIS -> spec(0.65f, FxReach.AREA, 0f,
            layer(FxPrimitive.TICKS, 0f, 0.65f, FxEase.O, FxColor.A, n = 12, r0 = 0.70f, r1 = 0.70f, w = 2f, spin = 120f, param = 0.14f,
                param2 = 0.30f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.30f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.70f, w = 3f, flags = SIG),
            layer(FxPrimitive.DISC, 0f, 0.20f, FxEase.S, FxColor.BONE, r0 = 0.20f, r1 = 0.40f, alpha = 0.30f),
            layer(FxPrimitive.SPOKES, 0.05f, 0.35f, FxEase.O, FxColor.B, n = 1, r0 = 0f, r1 = 0.60f, w = 2f, spin = 360f, flags = SEC),
        )
        // Void Anchor: a punch that grows and closes late (Event Strike: anchor rings in A and bone).
        ReactionId.ANNIHILATE -> spec(0.60f, FxReach.TARGET, 0f,
            layer(FxPrimitive.PUNCH, 0f, 0.45f, FxEase.O, FxColor.B, r0 = 0.15f, r1 = 0.55f, w = 3f, flags = SIG),
            layer(FxPrimitive.RING, 0.30f, 0.60f, FxEase.I, FxColor.B, r0 = 0.60f, r1 = 0.05f, w = 3f, flags = SIG),
            layer(FxPrimitive.FORKS, 0f, 0.25f, FxEase.S, FxColor.A, n = 2, r0 = 0.10f, r1 = 0.70f, w = 2f, param = 0.1f),
            layer(FxPrimitive.DOTS, 0.10f, 0.60f, FxEase.O, FxColor.A, n = 5, r0 = 0.60f, r1 = 1f, param = 2f, flags = SEC),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.BONE, r0 = 0.30f, r1 = 0.60f, w = 1.5f, flags = VERB or SEC),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.O, FxColor.A, r0 = 0.40f, r1 = 0.75f, w = 1.5f, flags = VERB or SEC or KEY),
        )
        // Maw: a punched void swallowing dots (Hungry Void: no inward arcs).
        ReactionId.NULL_WELL -> spec(0.55f, FxReach.AREA, 0f,
            layer(FxPrimitive.PUNCH, 0f, 0.55f, FxEase.O, FxColor.B, r0 = 0.05f, r1 = 0.40f, w = 3f, flags = SIG),
            layer(FxPrimitive.DOTS_IN, 0f, 0.55f, FxEase.I, FxColor.A, n = 11, r0 = 1f, r1 = 0.10f, param = 3f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.I, FxColor.B, r0 = 1f, r1 = 0.55f, w = 2f),
            layer(FxPrimitive.ARCS, 0.10f, 0.55f, FxEase.O, FxColor.A, n = 6, r0 = 0.70f, r1 = 0.70f, w = 2f, spin = -120f, param = 30f,
                flags = SEC or BASE),
        )
        // Erasure: a thin ring closes to a point, then the erasure slab (or a heavy burst without the execute).
        ReactionId.OBLIVION -> spec(0.65f, FxReach.TARGET, 0f,
            layer(FxPrimitive.RING, 0f, 0.40f, FxEase.I, FxColor.B, r0 = 1.10f, r1 = 0.02f, w = 3f, flags = SIG),
            layer(FxPrimitive.DOTS_IN, 0f, 0.40f, FxEase.I, FxColor.A, n = 8, r0 = 1f, r1 = 0.05f, param = 2.5f, flags = SIG),
            layer(FxPrimitive.DISC, 0.38f, 0.55f, FxEase.S, FxColor.BONE, r0 = 0.05f, r1 = 0.45f, alpha = 0.35f, flags = VERB),
            layer(FxPrimitive.WIPE, 0.40f, 0.75f, FxEase.S, FxColor.BONE, param = 0.35f, flags = VERB or SIG),
            layer(FxPrimitive.RING, 0.40f, 0.75f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 0.80f, w = 2f, flags = NOT_VERB),
        )
    }
}
