// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.foundation.design.CanvasRuneStyle
import kinetickk.foundation.design.drawRuneMedallion
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kinetickk.foundation.design.drawPolygon

private val RelicInk = Color(0xFFF4F6FF)
private val RelicBackground = Color(0xFF050610)
private val RelicCyan = Color(0xFF42F5E9)
private val RelicViolet = Color(0xFFA96CFF)
private val RelicBlue = Color(0xFF73A6FF)
private val RelicMagenta = Color(0xFFFF4DC4)
private val RelicAcid = Color(0xFFB6FF5B)
private val RelicOrange = Color(0xFFFF714B)
private val RelicGold = Color(0xFFFFD45B)

internal fun relicAspectColor(aspect: RelicAspect): Color = when (aspect) {
    RelicAspect.VECTOR -> RelicCyan
    RelicAspect.GRAVITIC -> RelicViolet
    RelicAspect.ION -> RelicBlue
    RelicAspect.RIFT -> RelicMagenta
    RelicAspect.PRISM -> RelicAcid
    RelicAspect.ENTROPY -> RelicOrange
    RelicAspect.SOVEREIGN -> RelicGold
}

/** Interaction maps semantic identity, aspect and rank into a shared geometric mark. */
internal fun DrawScope.drawRelicIcon(
    definition: RelicDefinition,
    policy: RelicPolicy,
    center: Offset,
    radius: Float,
    rank: Int? = null,
    time: Float = 0f,
    alpha: Float = 1f,
) {
    drawRuneMedallion(
        style = definition.id.toCanvasRuneStyle(),
        center = center,
        radius = radius,
        accent = relicAspectColor(definition.aspect).copy(alpha = alpha.coerceIn(0f, 1f)),
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

/** Generic signal used before an elite pickup resolves into a catalog relic. */
internal fun DrawScope.drawUnresolvedRelicIcon(center: Offset, radius: Float, time: Float) {
    val accent = RelicGold
    val stroke = (radius * 0.085f).coerceAtLeast(0.7f)
    drawCircle(accent.copy(alpha = 0.11f), radius * 1.35f, center)
    drawPolygon(center, radius, 6, time * 0.35f, RelicBackground.copy(alpha = 0.88f), Fill)
    drawPolygon(center, radius, 6, time * 0.35f, accent, Stroke(stroke))
    drawPolygon(center, radius * 0.58f, 4, -time * 0.65f, RelicInk, Stroke(stroke))
    drawCircle(RelicMagenta, radius * 0.13f, center)
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
