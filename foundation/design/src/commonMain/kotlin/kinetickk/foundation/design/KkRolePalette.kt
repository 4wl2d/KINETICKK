// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The five role colors remapped by the Color vision setting (`tokens.json colorVision`).
 *
 * This is the one way screens read the role colors: `you` (volt: player, confirm), `threat`
 * (hazard: enemies, danger), `heat`, `shield` and `pol` (polarity/cursor). Composables read
 * [LocalKkRolePalette]; Canvas renderers read `CanvasTextMeasurer.roles`. Structural tokens
 * (ink/bone/mute/line) and rarity/aspect colors do not change with Color vision.
 *
 * [hatchThreats] is set for [Mono]: every threat (enemies, hostile projectiles, hazard meters)
 * additionally gets [drawKkThreatHatch].
 */
@Immutable
data class KkRolePalette(
    val you: Color,
    val threat: Color,
    val heat: Color,
    val shield: Color,
    val pol: Color,
    val hatchThreats: Boolean = false,
) {
    companion object {
        val Default = KkRolePalette(
            you = Color(0xFFD8FF3E),
            threat = Color(0xFFFF3B6B),
            heat = Color(0xFFFF8A1F),
            shield = Color(0xFF45E0FF),
            pol = Color(0xFFA98BFF),
        )
        val Protan = KkRolePalette(
            you = Color(0xFFFFE14D),
            threat = Color(0xFF3D8BFF),
            heat = Color(0xFFFFB000),
            shield = Color(0xFF9AD0FF),
            pol = Color(0xFFE0E0E0),
        )
        val Deutan = KkRolePalette(
            you = Color(0xFFFFD84A),
            threat = Color(0xFF2F6BFF),
            heat = Color(0xFFFF9E1A),
            shield = Color(0xFFA6D8FF),
            pol = Color(0xFFD6D6D6),
        )
        val Tritan = KkRolePalette(
            you = Color(0xFF5CF2E0),
            threat = Color(0xFFFF3B3B),
            heat = Color(0xFFFF7A9A),
            shield = Color(0xFFE4E4E4),
            pol = Color(0xFFFF9ED8),
        )
        val Mono = KkRolePalette(
            you = Color(0xFFFFFFFF),
            threat = Color(0xFFFFFFFF),
            heat = Color(0xFFBDBDBD),
            shield = Color(0xFF8A8A8A),
            pol = Color(0xFFD9D9D9),
            hatchThreats = true,
        )
    }
}

/**
 * Presentation-level Color vision mode. The profile preference owns the persisted choice; the
 * app maps it to this enum and provides [palette] through [LocalKkRolePalette] and
 * `CanvasTextMeasurer.roles`.
 */
enum class KkVisionMode(val palette: KkRolePalette) {
    DEFAULT(KkRolePalette.Default),
    PROTAN(KkRolePalette.Protan),
    DEUTAN(KkRolePalette.Deutan),
    TRITAN(KkRolePalette.Tritan),
    MONO(KkRolePalette.Mono),
}

/** Role palette for composables. Canvas renderers use `CanvasTextMeasurer.roles` instead. */
val LocalKkRolePalette = staticCompositionLocalOf { KkRolePalette.Default }

/**
 * Draws the MONO threat hatch (45° lines) inside [path]; a no-op unless [roles] hatches threats.
 *
 * Use [tone] = `roles.threat` (default) over ink-filled/outlined threats such as enemies, and
 * `Kk.Ink` over threat-filled shapes (projectiles, hazard meters). Allocation-free after the
 * first call per tone and density (cached shader tile).
 */
fun DrawScope.drawKkThreatHatch(path: Path, roles: KkRolePalette, tone: Color = roles.threat) {
    if (!roles.hatchThreats) return
    drawPath(path, kkFillBrush(KkFillKind.THREAT_HATCH, tone))
}

/** Rect overload of [drawKkThreatHatch]. */
fun DrawScope.drawKkThreatHatch(rect: Rect, roles: KkRolePalette, tone: Color = roles.threat) {
    if (!roles.hatchThreats) return
    drawRect(kkFillBrush(KkFillKind.THREAT_HATCH, tone), rect.topLeft, rect.size)
}
