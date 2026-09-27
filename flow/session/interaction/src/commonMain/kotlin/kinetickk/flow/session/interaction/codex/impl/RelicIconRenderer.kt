// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.foundation.design.CanvasRuneStyle
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.drawRuneMedallion
import kotlin.math.PI

/** Aspect color from the foundation aspect tokens (index = [RelicAspect.ordinal]). */
internal fun relicAspectColor(aspect: RelicAspect): Color = Kk.aspect(aspect.ordinal)

/** Interaction maps semantic identity, aspect and rank into a shared geometric mark. */
internal fun DrawScope.drawRelicIcon(
    definition: RelicDefinition,
    policy: RelicPolicy,
    center: Offset,
    radius: Float,
    rank: Int? = null,
    time: Float = 0f,
    alpha: Float = 1f,
    color: Color = relicAspectColor(definition.aspect),
) {
    drawRuneMedallion(
        style = definition.id.toCanvasRuneStyle(),
        center = center,
        radius = radius,
        accent = color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)),
        frameSides = when (definition.aspect) {
            RelicAspect.VECTOR -> 3
            RelicAspect.GRAVITIC -> 6
            RelicAspect.ION -> 8
            RelicAspect.RIFT -> 4
            RelicAspect.PRISM -> 5
            RelicAspect.ENTROPY -> 7
            RelicAspect.SOVEREIGN -> 10
        },
        frameRotation = -PI.toFloat() * 0.5f + if (definition.isSovereign) time * 0.08f else 0f,
        markerCount = if (rank == null) 0 else policy.maxRank,
        filledMarkerCount = rank?.coerceIn(1, policy.maxRank) ?: 0,
        time = time,
    )
}

internal fun RelicId.toCanvasRuneStyle(): CanvasRuneStyle = when (this) {
    RelicId.KINETIC_FLYWHEEL -> CanvasRuneStyle.RADIAL_WHEEL
    RelicId.GHOST_VECTOR -> CanvasRuneStyle.OFFSET_CHEVRONS
    RelicId.OVERTAKE_PROTOCOL -> CanvasRuneStyle.PARALLEL_ARROW
    RelicId.SLIPSTREAM_RELAY -> CanvasRuneStyle.THREE_SPEED_LINES
    RelicId.BRAKEPOINT_MEMORY -> CanvasRuneStyle.BARS_AND_FAN
    RelicId.POLARITY_SLING -> CanvasRuneStyle.OPEN_ELLIPSE_DOT
    RelicId.ORBITAL_NAIL -> CanvasRuneStyle.ELLIPSE_AND_NAIL
    RelicId.EVENTIDE_ANCHOR -> CanvasRuneStyle.RINGED_ANCHOR
    RelicId.PERIAPSIS_HOOK -> CanvasRuneStyle.HOOK_AND_DOT
    RelicId.CRUSH_DEPTH -> CanvasRuneStyle.INWARD_CHEVRONS
    RelicId.MASS_ECHO -> CanvasRuneStyle.OFFSET_RINGS
    RelicId.TIDAL_LOCK -> CanvasRuneStyle.ELLIPSE_LINK
    RelicId.VOLTAIC_FILAMENT -> CanvasRuneStyle.BOLT_AND_DOT
    RelicId.STATIC_CHORUS -> CanvasRuneStyle.THREE_SLASHES
    RelicId.ION_DEBT -> CanvasRuneStyle.FIVE_DOT_RECTANGLE
    RelicId.CIRCUIT_BREAKER -> CanvasRuneStyle.BROKEN_SWITCH
    RelicId.RETURN_CIRCUIT -> CanvasRuneStyle.RETURN_ARROW
    RelicId.STORM_INDEX -> CanvasRuneStyle.FOUR_SWIRL_ARMS
    RelicId.ECHO_CHAMBER -> CanvasRuneStyle.NESTED_DIAMONDS
    RelicId.PALIMPSEST_ROUND -> CanvasRuneStyle.OFFSET_DIAGONALS
    RelicId.SECOND_HAND -> CanvasRuneStyle.TWO_HAND_DIAL
    RelicId.FRACTURE_GATE -> CanvasRuneStyle.BOLT_BETWEEN_BARS
    RelicId.SPLIT_HORIZON -> CanvasRuneStyle.SPLIT_ARCS
    RelicId.BORROWED_MOMENT -> CanvasRuneStyle.CROSSED_DIAMOND
    RelicId.GLASS_WITNESS -> CanvasRuneStyle.DIAMOND_EYE
    RelicId.FRACTURE_LENS -> CanvasRuneStyle.CRACKED_RING
    RelicId.SPECTRAL_FAN -> CanvasRuneStyle.FIVE_RAY_FAN
    RelicId.HARDLIGHT_EDGE -> CanvasRuneStyle.SLASH_BLADE
    RelicId.CHROMA_FEEDBACK -> CanvasRuneStyle.THREE_NODE_FAN
    RelicId.MIRROR_CUT -> CanvasRuneStyle.SLASHED_DIAMONDS
    RelicId.HEAT_DEBT -> CanvasRuneStyle.OPEN_DIAL
    RelicId.SCAR_TISSUE -> CanvasRuneStyle.STITCHED_DIAGONAL
    RelicId.QUIETUS_BLOOM -> CanvasRuneStyle.SIX_PETAL_ROSETTE
    RelicId.DEVOURERS_TOLL -> CanvasRuneStyle.THREE_TOOTH_ARC
    RelicId.DOOM_CLOCK -> CanvasRuneStyle.FOUR_TICK_DIAL
    RelicId.LAST_LIGHT -> CanvasRuneStyle.DIAMOND_FLAME
    RelicId.AGONY_SCEPTER -> CanvasRuneStyle.RADIATING_STAFF
    RelicId.CROWN_OF_FOUR_WINDS -> CanvasRuneStyle.FOUR_DOT_CROWN
    RelicId.MIRROR_OF_THE_HUNT -> CanvasRuneStyle.SLASHED_EYE_DIAMOND
    RelicId.ENGINE_OF_PARADOX -> CanvasRuneStyle.COUNTER_ROTATING_ARCS
}
