// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.foundation.design.CanvasRuneStyle
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.drawRuneMedallion
import kinetickk.foundation.design.kkStroke
import kotlin.math.PI

/** Aspect colors come from the redesign aspect tokens, in RelicAspect order. */
internal fun relicAspectColor(aspect: RelicAspect): Color = Kk.aspect(aspect.ordinal)

/**
 * Interaction maps semantic identity, aspect and rank into a shared geometric mark. [accent] is
 * the aspect hue; overlays pass it resolved under the player's color-vision preset so the rune
 * matches the plate or slot it sits on.
 */
internal fun DrawScope.drawRelicIcon(
    definition: RelicDefinition,
    policy: RelicPolicy,
    center: Offset,
    radius: Float,
    rank: Int? = null,
    time: Float = 0f,
    alpha: Float = 1f,
    accent: Color = relicAspectColor(definition.aspect),
) {
    drawRuneMedallion(
        style = definition.id.toCanvasRuneStyle(),
        center = center,
        radius = radius,
        accent = accent.copy(alpha = accent.alpha * alpha.coerceIn(0f, 1f)),
        frameSides = when (definition.aspect) {
            RelicAspect.KINETIC -> 3
            RelicAspect.THERMAL -> 6
            RelicAspect.CRYO -> 8
            RelicAspect.ION -> 4
            RelicAspect.GRAVITIC -> 5
            RelicAspect.ENTROPY -> 7
            RelicAspect.SONIC -> 9
            RelicAspect.VOID -> 12
            RelicAspect.SOVEREIGN -> 10
        },
        frameRotation = -PI.toFloat() * 0.5f + if (definition.isSovereign) time * 0.08f else 0f,
        markerCount = if (rank == null) 0 else policy.maxRank,
        filledMarkerCount = rank?.coerceIn(1, policy.maxRank) ?: 0,
        time = time,
    )
}

/**
 * Generic signal used before an elite pickup resolves into a catalog relic: an empty relic slot
 * (ink diamond, bone outline) holding a small legendary diamond that slowly turns.
 */
internal fun DrawScope.drawUnresolvedRelicIcon(center: Offset, radius: Float, time: Float) {
    val stroke = (radius * 0.14f).coerceAtLeast(1f)
    val half = radius * 0.72f
    rotate(45f, center) {
        drawRect(Kk.RLegend.copy(alpha = 0.12f), Offset(center.x - half * 1.35f, center.y - half * 1.35f), Size(half * 2.7f, half * 2.7f))
        drawRect(Kk.Ink2, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f))
        drawRect(Kk.Bone, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f), style = kkStroke(stroke))
    }
    val inner = radius * 0.3f
    rotate(45f + time * 20f, center) {
        drawRect(Kk.RLegend, Offset(center.x - inner, center.y - inner), Size(inner * 2f, inner * 2f))
    }
}

/** One distinct rune per relic, chosen for what the relic does (40 styles serve the 22 relics). */
internal fun RelicId.toCanvasRuneStyle(): CanvasRuneStyle = when (this) {
    RelicId.PERPETUAL_VECTOR -> CanvasRuneStyle.RADIAL_WHEEL
    RelicId.BLINK_DRIVE -> CanvasRuneStyle.OFFSET_CHEVRONS
    RelicId.GYRE_ENGINE -> CanvasRuneStyle.FOUR_SWIRL_ARMS
    RelicId.GLASS_COMET -> CanvasRuneStyle.CRACKED_RING
    RelicId.EXECUTIONERS_PRISM -> CanvasRuneStyle.SLASH_BLADE
    RelicId.ECHO_CHAMBER -> CanvasRuneStyle.NESTED_DIAMONDS
    RelicId.FURNACE_COVENANT -> CanvasRuneStyle.DIAMOND_FLAME
    RelicId.CINDER_CROWN -> CanvasRuneStyle.FOUR_DOT_CROWN
    RelicId.OVERDRIVE_CASCADE -> CanvasRuneStyle.COUNTER_ROTATING_ARCS
    RelicId.ABSOLUTE_ZERO -> CanvasRuneStyle.SIX_PETAL_ROSETTE
    RelicId.STASIS_ANCHOR -> CanvasRuneStyle.RINGED_ANCHOR
    RelicId.STORMHEART_CAPACITOR -> CanvasRuneStyle.BOLT_AND_DOT
    RelicId.AEGIS_INVERSION -> CanvasRuneStyle.CROSSED_DIAMOND
    RelicId.NEUTRON_HEART -> CanvasRuneStyle.OFFSET_RINGS
    RelicId.EVENT_HORIZON -> CanvasRuneStyle.INWARD_CHEVRONS
    RelicId.VERDANT_ROT -> CanvasRuneStyle.STITCHED_DIAGONAL
    RelicId.PANDEMIC_CODEX -> CanvasRuneStyle.FIVE_RAY_FAN
    RelicId.AGONY_SCEPTER -> CanvasRuneStyle.RADIATING_STAFF
    RelicId.HOLLOW_CROWN -> CanvasRuneStyle.OPEN_DIAL
    RelicId.OUROBOROS_SEAL -> CanvasRuneStyle.RETURN_ARROW
    RelicId.THUNDER_CHOIR -> CanvasRuneStyle.SPLIT_ARCS
    RelicId.NULL_COVENANT -> CanvasRuneStyle.OPEN_ELLIPSE_DOT
}
