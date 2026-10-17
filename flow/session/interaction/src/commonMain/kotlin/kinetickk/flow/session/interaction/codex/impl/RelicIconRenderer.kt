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
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.drawRuneMedallion
import kotlin.math.PI

/**
 * Aspect color in the player's color-vision palette: an element's hue, the preset's volt for
 * Kinetic, gold for Sovereign. The Default palette gives the foundation aspect tokens.
 */
internal fun relicAspectColor(aspect: RelicAspect, roles: KkRolePalette = KkRolePalette.Default): Color =
    aspect.element?.let(roles::of) ?: if (aspect == RelicAspect.KINETIC) roles.kinetic else Kk.aspect(aspect.ordinal)

/** Interaction maps semantic identity, aspect and rank into a shared geometric mark. */
internal fun DrawScope.drawRelicIcon(
    definition: RelicDefinition,
    policy: RelicPolicy,
    center: Offset,
    radius: Float,
    rank: Int? = null,
    time: Float = 0f,
    alpha: Float = 1f,
    roles: KkRolePalette = KkRolePalette.Default,
    color: Color = relicAspectColor(definition.aspect, roles),
) {
    drawRuneMedallion(
        style = definition.id.toCanvasRuneStyle(),
        center = center,
        radius = radius,
        accent = color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)),
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
