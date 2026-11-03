// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.SkillId

/**
 * Skill cast and delayed-impact looks (hud-fx §4.7), by skill and phase. Reach is the cue's radius
 * (the effect's own radius), or [ReactionFxSpec.fixedReach] where the look has a fixed one.
 */
internal object SkillFxSpecs {
    const val PHASES: Int = 2
    private val specs: Array<ReactionFxSpec?> = Array(SkillId.entries.size * PHASES) { index ->
        build(SkillId.entries[index / PHASES], index % PHASES)
    }

    /** The look of [skill] at [phase], or null when that skill has no such phase. */
    fun of(skill: Int, phase: Int): ReactionFxSpec? {
        if (skill !in SkillId.entries.indices || phase !in 0 until PHASES) return null
        return specs[skill * PHASES + phase]
    }

    fun of(skill: SkillId, phase: Int): ReactionFxSpec? = of(skill.ordinal, phase)

    /** The longest skill look (seconds). */
    val maxLife: Float = specs.maxOf { it?.life ?: 0f }

    private fun build(skill: SkillId, phase: Int): ReactionFxSpec? = when (skill) {
        SkillId.SOLAR_FLARE -> if (phase != 0) null else spec(0.60f, FxReach.CUE, 0f,
            layer(FxPrimitive.DISC, 0f, 0.25f, FxEase.S, FxColor.A, r0 = 0.20f, r1 = 0.70f, alpha = 0.30f, flags = SIG),
            layer(FxPrimitive.FLAMES, 0f, 0.80f, FxEase.O, FxColor.A, n = 10, r0 = 0.30f, r1 = 1f, param = 0.12f, flags = SIG or FxFlag.PER_RANK),
            layer(FxPrimitive.SPOKES, 0f, 0.30f, FxEase.O, FxColor.BONE, n = 8, r0 = 4f, r1 = 24f, w = 1.5f, flags = CORE),
            layer(FxPrimitive.RING, 0.10f, 0.60f, FxEase.O, FxColor.A, r0 = 0.50f, r1 = 1f, w = 3f),
        )
        SkillId.METEOR -> when (phase) {
            0 -> spec(0.25f, FxReach.CUE, 0f,
                layer(FxPrimitive.DISC, 0f, 1f, FxEase.S, FxColor.BONE, r0 = 6f, r1 = 14f, alpha = 0.30f, flags = CORE or SIG),
            )
            else -> spec(0.70f, FxReach.CUE, 0f,
                layer(FxPrimitive.DISC, 0f, 0.30f, FxEase.S, FxColor.A, r0 = 0.20f, r1 = 0.80f, alpha = 0.35f, flags = SIG),
                layer(FxPrimitive.RING, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 0.30f, r1 = 1.15f, w = 4.5f, flags = SIG),
                layer(FxPrimitive.SHARDS, 0f, 0.60f, FxEase.O, FxColor.BONE, n = 8, r0 = 0.20f, r1 = 1f, param = 12f),
                layer(FxPrimitive.CRACKS, 0f, 0.50f, FxEase.O, FxColor.A, n = 5, r0 = 0.10f, r1 = 0.70f, w = 2f, param = 0.5f),
                layer(FxPrimitive.RING, 0f, 0.30f, FxEase.O, FxColor.YOU, r0 = 6f, r1 = 60f, w = 2f, flags = CORE or FxFlag.CORE_INSIDE),
            )
        }
        SkillId.FROST_LINE -> if (phase != 0) null else spec(0.35f, FxReach.CUE, 0f,
            layer(FxPrimitive.HEX, 0f, 1f, FxEase.O, FxColor.A, r0 = 0.05f, r1 = 0.12f, w = 3f, flags = SIG),
        )
        SkillId.FROST_SNAP -> if (phase != 0) null else spec(0.55f, FxReach.CUE, 0f,
            layer(FxPrimitive.HEX, 0f, 0.35f, FxEase.O, FxColor.A, r0 = 0.10f, r1 = 1f, w = 3f, flags = SIG),
            layer(FxPrimitive.SPOKES, 0f, 0.50f, FxEase.O, FxColor.A, n = 6, r0 = 0.10f, r1 = 0.90f, w = 2f, flags = SIG),
            layer(FxPrimitive.TICKS, 0f, 0.60f, FxEase.O, FxColor.A, n = 6, r0 = 0.60f, r1 = 0.60f, w = 1.5f, param = 0.12f, flags = FxFlag.TANGENT),
            layer(FxPrimitive.DISC, 0f, 0.12f, FxEase.S, FxColor.BONE, r0 = 0.20f, r1 = 0.40f, alpha = 0.30f),
        )
        SkillId.ARC_HOOK -> if (phase != 0) null else spec(0.40f, FxReach.CUE, 0f,
            layer(FxPrimitive.RING, 0f, 1f, FxEase.O, FxColor.A, r0 = 0f, r1 = 30f, w = 2f, flags = CORE or SIG),
        )
        SkillId.STORM_COIL -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.FORKS, 0f, 0.50f, FxEase.S, FxColor.A, n = 4, r0 = 0.05f, r1 = 0.45f, w = 2f, param = 0.12f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.70f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 1f, w = 1.5f),
        )
        SkillId.GRAVITY_WELL -> when (phase) {
            0 -> spec(0.50f, FxReach.CUE, 0f,
                layer(FxPrimitive.RING, 0f, 0.60f, FxEase.I, FxColor.A, r0 = 1.20f, r1 = 0.30f, w = 4.5f, flags = SIG),
                layer(FxPrimitive.STREAKS_IN, 0f, 0.60f, FxEase.I, FxColor.A, n = 8, r0 = 1f, r1 = 0.30f, w = 1.5f, param = 0.15f),
            )
            else -> spec(0.30f, FxReach.CUE, 0f,
                layer(FxPrimitive.RING, 0f, 1f, FxEase.I, FxColor.A, r0 = 1f, r1 = 0.05f, w = 4.5f, flags = SIG),
            )
        }
        SkillId.MASS_DRIVER -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.RING, 0f, 0.60f, FxEase.I, FxColor.A, r0 = 60f, r1 = 8f, w = 4.5f, flags = CORE or SIG),
            layer(FxPrimitive.TICKS, 0f, 0.70f, FxEase.O, FxColor.A, n = 4, r0 = 14f, r1 = 14f, w = 3f, param = 8f, flags = CORE),
        )
        SkillId.DECAY_SPORES -> if (phase != 0) null else spec(0.70f, FxReach.CUE, 0f,
            layer(FxPrimitive.DOTS, 0f, 0.80f, FxEase.O, FxColor.A, n = 16, r0 = 0f, r1 = 1f, param = 3f, flags = SIG),
            layer(FxPrimitive.DISC, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 0.80f, alpha = 0.12f),
            layer(FxPrimitive.RING, 0.10f, 0.70f, FxEase.O, FxColor.A, r0 = 0.60f, r1 = 1f, w = 1.6f, flags = FxFlag.DOTTED),
        )
        SkillId.ENTROPIC_TIDE -> if (phase != 0) null else spec(0.70f, FxReach.CUE, 0f,
            layer(FxPrimitive.DOTS_IN, 0f, 0.60f, FxEase.I, FxColor.A, n = 20, r0 = 1f, r1 = 0.05f, param = 2.5f, flags = SIG),
            layer(FxPrimitive.RING, 0f, 0.60f, FxEase.I, FxColor.A, r0 = 1f, r1 = 0.20f, w = 2f),
            layer(FxPrimitive.RING, 0.55f, 0.75f, FxEase.O, FxColor.BONE, r0 = 2f, r1 = 30f, w = 3f, flags = CORE or SIG),
        )
        SkillId.SONIC_BOOM -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.WAVES, 0f, 0.70f, FxEase.O, FxColor.A, n = 3, r0 = 0.10f, r1 = 1f, w = 3f, param = 70f, param2 = 0.12f,
                flags = DIR or SIG),
            layer(FxPrimitive.LANES, 0f, 0.40f, FxEase.O, FxColor.BONE, n = 3, r0 = 0f, r1 = 30f, w = 1.5f, param2 = 8f,
                flags = CORE or DIR or FxFlag.BACK),
            layer(FxPrimitive.DOTS, 0.10f, 0.80f, FxEase.O, FxColor.A, n = 6, r0 = 0.30f, r1 = 1f, param = 2.5f, param2 = 35f, flags = DIR),
        )
        SkillId.RESONATOR -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.RING, 0f, 0.70f, FxEase.O, FxColor.A, r0 = 0.20f, r1 = 1f, w = 2f, flags = SIG),
            layer(FxPrimitive.PLATE, 0f, 0.70f, FxEase.P, FxColor.A, r0 = 0.03f, r1 = 0.04f, w = 2f),
        )
        SkillId.VOID_STEP -> when (phase) {
            0 -> spec(0.45f, FxReach.CUE, 0f,
                layer(FxPrimitive.LANES, 0f, 0.60f, FxEase.O, FxColor.A, n = 1, r0 = 1f, r1 = 1f, w = 1.5f,
                    flags = DIR or FxFlag.INK_CORE or SIG),
                layer(FxPrimitive.PUNCH, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 2f, r1 = 10f, w = 1.5f, flags = FxFlag.WORLD),
            )
            else -> spec(0.50f, FxReach.FIXED, 90f,
                layer(FxPrimitive.PUNCH, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 0.10f, r1 = 0.90f, w = 3f, flags = SIG),
                layer(FxPrimitive.RING, 0f, 0.60f, FxEase.O, FxColor.A, r0 = 0.30f, r1 = 1.10f, w = 3f),
                layer(FxPrimitive.DOTS, 0.10f, 0.80f, FxEase.O, FxColor.A, n = 6, r0 = 0.30f, r1 = 1f, param = 2.5f),
            )
        }
        SkillId.NULL_SHROUD -> when (phase) {
            0 -> spec(0.40f, FxReach.CUE, 0f,
                layer(FxPrimitive.RING, 0f, 1f, FxEase.I, FxColor.A, r0 = 80f, r1 = 44f, w = 3f, flags = CORE or SIG),
            )
            else -> spec(0.40f, FxReach.CUE, 0f,
                layer(FxPrimitive.RING, 0f, 1f, FxEase.O, FxColor.A, r0 = 44f, r1 = 120f, w = 2f, flags = CORE or SIG),
            )
        }
        SkillId.SLINGSHOT -> if (phase != 0) null else spec(0.45f, FxReach.CUE, 0f,
            layer(FxPrimitive.ARCS, 0f, 0.70f, FxEase.O, FxColor.A, n = 1, r0 = 24f, r1 = 24f, w = 3f, param = 120f, flags = CORE or DIR or SIG),
            layer(FxPrimitive.LANES, 0f, 0.50f, FxEase.O, FxColor.A, n = 3, r0 = 0f, r1 = 30f, w = 1.5f, param2 = 8f, flags = CORE or DIR),
        )
        SkillId.FULL_STOP -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.RING, 0f, 0.70f, FxEase.O, FxColor.A, r0 = 0.10f, r1 = 1f, w = 4.5f, flags = SIG),
            layer(FxPrimitive.RING, 0.10f, 0.60f, FxEase.O, FxColor.BONE, r0 = 0.10f, r1 = 0.80f, w = 1.5f),
            layer(FxPrimitive.LANES, 0f, 0.40f, FxEase.O, FxColor.BONE, n = 2, r0 = 40f, r1 = 40f, w = 2f, param2 = 10f,
                flags = CORE or DIR or FxFlag.BACK),
            layer(FxPrimitive.TICKS, 0f, 0.70f, FxEase.O, FxColor.BONE, n = 8, r0 = 0.50f, r1 = 0.50f, w = 2f, param = 0.10f, flags = FxFlag.RANK3),
        )
        SkillId.CATALYZE -> if (phase != 0) null else spec(0.55f, FxReach.CUE, 0f,
            layer(FxPrimitive.SPLIT_RING, 0f, 0.70f, FxEase.O, FxColor.AB, r0 = 0.20f, r1 = 1f, w = 2f, spin = 90f, param = 172f, flags = SIG),
        )
        SkillId.DUAL_INFUSION -> if (phase != 0) null else spec(0.50f, FxReach.CUE, 0f,
            layer(FxPrimitive.ARCS, 0f, 1f, FxEase.O, FxColor.AB, n = 2, r0 = 14f, r1 = 14f, w = 3f, spin = 180f, param = 170f, flags = CORE or SIG),
        )
    }
}

/**
 * The Apex activation (hud-fx §4.8): gold rings out to 280, gold spokes and a bone flash at the
 * Core's position; the character's silhouette outline growing to three Core radii is drawn by the
 * renderer over the first half.
 */
internal object ApexFxSpec {
    const val REACH: Float = 280f
    const val SILHOUETTE_END: Float = 0.5f

    val activation: ReactionFxSpec = spec(1f, FxReach.FIXED, REACH,
        layer(FxPrimitive.RING, 0f, 0.60f, FxEase.O, FxColor.GOLD, r0 = 0.093f, r1 = 1f, w = 4.5f, flags = SIG),
        layer(FxPrimitive.RING, 0.15f, 0.75f, FxEase.O, FxColor.GOLD, r0 = 0.093f, r1 = 0.714f, w = 2f),
        layer(FxPrimitive.SPOKES, 0f, 0.40f, FxEase.O, FxColor.GOLD_LIGHT, n = 12, r0 = 0.15f, r1 = 0.45f, w = 2f),
        layer(FxPrimitive.DISC, 0f, 0.15f, FxEase.S, FxColor.BONE, r0 = 0.107f, r1 = 0.286f, alpha = 0.30f),
    )
}
