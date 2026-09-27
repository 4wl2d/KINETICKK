// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

/** Layer styles from `icons.json` (`styles`). */
enum class KkIconStyle(val jsonName: String) {
    /** 2-unit stroke, square caps, miter joins. */
    STROKE("stroke"),

    /** Solid fill. */
    FILL("fill"),

    /** 2-unit stroke dashed 3/3. */
    STROKE_DASHED("stroke-dashed"),

    /** 2-unit stroke dotted 1/4. */
    STROKE_DOTTED("stroke-dotted"),
}

/** One SVG path layer on the 24-unit icon grid. */
data class KkIconLayer(val d: String, val style: KkIconStyle)

/**
 * The redesign icon set, generated once from `docs/design/redesign/icons.json` (the source of
 * truth; `KkIconTableTest` asserts this table matches it exactly). [key] is the JSON key,
 * [label] the design reference name (not localized UI text). Screens map game identifiers
 * (weapon, core shape, relic aspect) to entries; no entry is an arrow shape.
 */
enum class KkIcon(val key: String, val label: String, vararg layerData: KkIconLayer) {
    // region generated from icons.json
    FORMS_CIRCLE(
        "forms.circle",
        "Circle",
        KkIconLayer("M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z", KkIconStyle.STROKE),
    ),
    FORMS_SQUARE(
        "forms.square",
        "Square",
        KkIconLayer("M5 5h14v14h-14Z", KkIconStyle.STROKE),
    ),
    FORMS_TRIANGLE(
        "forms.triangle",
        "Triangle",
        KkIconLayer("M12 4 20.5 19h-17Z", KkIconStyle.STROKE),
    ),
    FORMS_RING(
        "forms.ring",
        "Ring",
        KkIconLayer("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z", KkIconStyle.STROKE),
        KkIconLayer("M7.5 12a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0Z", KkIconStyle.STROKE),
    ),
    FORMS_DIAMOND(
        "forms.diamond",
        "Diamond",
        KkIconLayer("M12 3 21 12 12 21 3 12Z", KkIconStyle.STROKE),
    ),
    FORMS_TESSERACT(
        "forms.tesseract",
        "Tesseract",
        KkIconLayer("M3 3h12v12h-12Z", KkIconStyle.STROKE),
        KkIconLayer("M9 9h12v12h-12Z", KkIconStyle.STROKE),
        KkIconLayer("M3 3 9 9M15 3l6 6M3 15l6 6M15 15l6 6", KkIconStyle.STROKE),
    ),
    WEAPONS_FLUX_WAKE(
        "weapons.flux_wake",
        "Flux Wake",
        KkIconLayer("M2 9c3 0 3.5-3 6.5-3s3.5 3 6.5 3 3.5-3 7-3M2 15c3 0 3.5-3 6.5-3s3.5 3 6.5 3 3.5-3 7-3M2 20h8", KkIconStyle.STROKE),
    ),
    WEAPONS_MORNINGSTAR(
        "weapons.morningstar",
        "Morningstar",
        KkIconLayer("M11 9a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", KkIconStyle.STROKE),
        KkIconLayer("M15 2v2.5M15 13.5V16M8 9h2.5M19.5 9H22M10 14 3 21", KkIconStyle.STROKE),
    ),
    WEAPONS_PHASE_LATTICE(
        "weapons.phase_lattice",
        "Phase Lattice",
        KkIconLayer("M12 3 21 12 12 21 3 12Z", KkIconStyle.STROKE),
        KkIconLayer("M7.5 7.5l9 9M16.5 7.5l-9 9", KkIconStyle.STROKE),
    ),
    WEAPONS_NULL_LANCE(
        "weapons.null_lance",
        "Null Lance",
        KkIconLayer("M3 21 14 10M14 10l1-5 4 4-5 1ZM7 13l4 4", KkIconStyle.STROKE),
    ),
    WEAPONS_GRAVITY_MINES(
        "weapons.gravity_mines",
        "Gravity Mines",
        KkIconLayer("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", KkIconStyle.FILL),
        KkIconLayer("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z", KkIconStyle.STROKE_DASHED),
    ),
    WEAPONS_ION_SWARM(
        "weapons.ion_swarm",
        "Ion Swarm",
        KkIconLayer("M4 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", KkIconStyle.STROKE),
        KkIconLayer("M15 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", KkIconStyle.STROKE),
        KkIconLayer("M9.5 13a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0Z", KkIconStyle.FILL),
        KkIconLayer("M17 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", KkIconStyle.STROKE),
        KkIconLayer("M4.5 19a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", KkIconStyle.STROKE),
    ),
    WEAPONS_RIFT_BLADES(
        "weapons.rift_blades",
        "Rift Blades",
        KkIconLayer("M3 15 15 3M9 21 21 9", KkIconStyle.STROKE),
        KkIconLayer("M5 21 21 5", KkIconStyle.STROKE_DASHED),
    ),
    WEAPONS_ARC_COIL(
        "weapons.arc_coil",
        "Arc Coil",
        KkIconLayer("M2 12l3-6 3 12 3-12 3 12 3-12 3 12 2-6", KkIconStyle.STROKE),
    ),
    WEAPONS_QUASAR_CANNON(
        "weapons.quasar_cannon",
        "Quasar Cannon",
        KkIconLayer("M2 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", KkIconStyle.STROKE),
        KkIconLayer("M11 8h11M11 16h11M12 12h10", KkIconStyle.STROKE),
    ),
    WEAPONS_ENTROPY_FIELD(
        "weapons.entropy_field",
        "Entropy Field",
        KkIconLayer("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0Z", KkIconStyle.STROKE_DOTTED),
        KkIconLayer("M7 12a5 5 0 1 0 10 0a5 5 0 1 0 -10 0Z", KkIconStyle.STROKE_DASHED),
        KkIconLayer("M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0Z", KkIconStyle.FILL),
    ),
    WEAPONS_SINGULARITY_SPEAR(
        "weapons.singularity_spear",
        "Singularity Spear",
        KkIconLayer("M2 12h14M17 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0M5 9v6M8 10v4", KkIconStyle.STROKE),
    ),
    WEAPONS_PRISM_RELAY(
        "weapons.prism_relay",
        "Prism Relay",
        KkIconLayer("M8 5 14 17H2Z", KkIconStyle.STROKE),
        KkIconLayer("M13 11l9-5M14 13h8M13 15l9 5", KkIconStyle.STROKE),
    ),
    SYSTEM_INTEGRITY(
        "system.integrity",
        "Integrity",
        KkIconLayer("M12 2 21 7v10l-9 5-9-5V7Z", KkIconStyle.STROKE),
    ),
    SYSTEM_SHIELD(
        "system.shield",
        "Shield",
        KkIconLayer("M12 2 21 7v10l-9 5-9-5V7Z", KkIconStyle.STROKE_DASHED),
        KkIconLayer("M12 7 16.5 9.5v5L12 17l-4.5-2.5v-5Z", KkIconStyle.STROKE),
    ),
    SYSTEM_HEAT(
        "system.heat",
        "Heat",
        KkIconLayer("M12 2c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 2-5 2-7 1.5 1 2.5 2.5 2.5 4.5C12 9 12 5 12 2Z", KkIconStyle.STROKE),
    ),
    SYSTEM_POLARITY(
        "system.polarity",
        "Polarity",
        KkIconLayer("M5 3v9a7 7 0 0 0 14 0V3h-4v9a3 3 0 0 1-6 0V3Z", KkIconStyle.STROKE),
    ),
    SYSTEM_OVERDRIVE(
        "system.overdrive",
        "Overdrive",
        KkIconLayer("M3 17a9 9 0 0 1 18 0M12 17l6-6M3 20h18", KkIconStyle.STROKE),
    ),
    SYSTEM_DATA(
        "system.data",
        "Data",
        KkIconLayer("M12 3 20 12 12 21 4 12Z", KkIconStyle.STROKE),
        KkIconLayer("M12 8 16 12 12 16 8 12Z", KkIconStyle.FILL),
    ),
    SYSTEM_MATTER(
        "system.matter",
        "Matter",
        KkIconLayer("M12 2 20 7v10l-8 5-8-5V7Z", KkIconStyle.STROKE),
        KkIconLayer("M4 7l8 5 8-5M12 12v10", KkIconStyle.STROKE),
    ),
    SYSTEM_KEY(
        "system.key",
        "Key",
        KkIconLayer("M3 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", KkIconStyle.STROKE),
        KkIconLayer("M11 12h11M18 12v4M21 12v3", KkIconStyle.STROKE),
    ),
    SYSTEM_DASH(
        "system.dash",
        "Dash",
        KkIconLayer("M2 7h7M2 12h9M2 17h7M13 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0", KkIconStyle.STROKE),
    ),
    SYSTEM_BRAKE(
        "system.brake",
        "Brake",
        KkIconLayer("M8 3h8l5 5v8l-5 5H8l-5-5V8Z", KkIconStyle.STROKE),
        KkIconLayer("M8 12h8", KkIconStyle.STROKE),
    ),
    SYSTEM_ELITE(
        "system.elite",
        "Elite",
        KkIconLayer("M12 5 19 9v8l-7 4-7-4V9Z", KkIconStyle.STROKE),
        KkIconLayer("M5 9 3 3l5 3M19 9l2-6-5 3", KkIconStyle.STROKE),
    ),
    SYSTEM_DISMANTLED(
        "system.dismantled",
        "Dismantled",
        KkIconLayer("M12 2 22 12 12 22 2 12Z", KkIconStyle.STROKE),
        KkIconLayer("M12 2l-2 7 4 3-3 10", KkIconStyle.STROKE),
    ),
    SYSTEM_ANOMALY(
        "system.anomaly",
        "Anomaly",
        KkIconLayer("M12 2 22 12 12 22 2 12Z", KkIconStyle.STROKE),
        KkIconLayer("M12 7v6M12 16v1.5", KkIconStyle.STROKE),
    ),
    SYSTEM_CYCLE_CLOCK(
        "system.cycle_clock",
        "Cycle clock",
        KkIconLayer("M4 13a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z", KkIconStyle.STROKE),
        KkIconLayer("M12 9v4l3 2M9 2h6", KkIconStyle.STROKE),
    ),
    SYSTEM_REROLL(
        "system.reroll",
        "Reroll",
        KkIconLayer("M4 4h16v16H4ZM8 8h.01M16 16h.01M12 12h.01M16 8h.01M8 16h.01", KkIconStyle.STROKE),
    ),
    SYSTEM_LOCKED(
        "system.locked",
        "Locked",
        KkIconLayer("M5 11h14v10h-14Z", KkIconStyle.STROKE),
        KkIconLayer("M8 11V7a4 4 0 0 1 8 0v4", KkIconStyle.STROKE),
    ),
    ASPECT_VECTOR(
        "aspect.vector",
        "Vector",
        KkIconLayer("M6 19 11 5M13 19 18 5", KkIconStyle.STROKE),
    ),
    ASPECT_GRAVITIC(
        "aspect.gravitic",
        "Gravitic",
        KkIconLayer("M3 5c4 4 14 4 18 0M5 10c3 3 11 3 14 0M8 15c2 2 6 2 8 0", KkIconStyle.STROKE),
    ),
    ASPECT_ION(
        "aspect.ion",
        "Ion",
        KkIconLayer("M13 2 5 13h6l-1 9 8-12h-6Z", KkIconStyle.STROKE),
    ),
    ASPECT_RIFT(
        "aspect.rift",
        "Rift",
        KkIconLayer("M12 2 9 9l5 4-3 9", KkIconStyle.STROKE),
    ),
    ASPECT_PRISM(
        "aspect.prism",
        "Prism",
        KkIconLayer("M12 3 21 20H3Z", KkIconStyle.STROKE),
        KkIconLayer("M12 3v17", KkIconStyle.STROKE),
    ),
    ASPECT_ENTROPY(
        "aspect.entropy",
        "Entropy",
        KkIconLayer("M12 12a2 2 0 1 1 2 2a4 4 0 1 1-4-4a6 6 0 1 1 6 6", KkIconStyle.STROKE),
    ),
    ASPECT_SOVEREIGN(
        "aspect.sovereign",
        "Sovereign",
        KkIconLayer("M3 20h18M4 17 3 7l5 4 4-7 4 7 5-4-1 10Z", KkIconStyle.STROKE),
    ),
    UI_PLUS(
        "ui.plus",
        "Add / empty slot",
        KkIconLayer("M12 7v10M7 12h10", KkIconStyle.STROKE),
    ),
    // endregion
    ;

    /** Layers in drawing order. */
    val layers: List<KkIconLayer> = layerData.toList()

    companion object {
        /** Icon for an `icons.json` key such as `"weapons.flux_wake"`, or null. */
        fun byKey(key: String): KkIcon? = entries.firstOrNull { it.key == key }

        /** Form icons in `icons.json` order: circle, square, triangle, ring, diamond, tesseract. */
        val Forms: List<KkIcon> = entries.filter { it.key.startsWith("forms.") }

        /** Weapon icons in `icons.json` order. */
        val Weapons: List<KkIcon> = entries.filter { it.key.startsWith("weapons.") }

        /** Aspect icons in relic-aspect order: vector, gravitic, ion, rift, prism, entropy, sovereign. */
        val Aspects: List<KkIcon> = entries.filter { it.key.startsWith("aspect.") }

        /** System icons in `icons.json` order. */
        val System: List<KkIcon> = entries.filter { it.key.startsWith("system.") }
    }
}

/** Icon grid size (`icons.json grid`). */
const val KK_ICON_GRID = 24f

/**
 * Draws [icon] centered at [center] scaled to [size] px (the 24-unit grid maps to [size]).
 * [strokeWidth] is in grid units (2 = `.ic`, 1.5 = `.ic-thin`). Paths are parsed once per icon and
 * strokes are cached, so this is allocation-free and safe for HUD/world use.
 */
fun DrawScope.drawKkIcon(
    icon: KkIcon,
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float = 1f,
    strokeWidth: Float = 2f,
) {
    if (size <= 0f || alpha <= 0f) return
    val paths = KkIconPaths.of(icon)
    val scale = size / KK_ICON_GRID
    translate(center.x - size * 0.5f, center.y - size * 0.5f) {
        scale(scale, scale, Offset.Zero) {
            for (index in paths.indices) {
                val style = when (icon.layers[index].style) {
                    KkIconStyle.FILL -> Fill
                    KkIconStyle.STROKE -> KkIconStrokes.solid(strokeWidth)
                    KkIconStyle.STROKE_DASHED -> KkIconStrokes.dashed(strokeWidth)
                    KkIconStyle.STROKE_DOTTED -> KkIconStrokes.dotted(strokeWidth)
                }
                drawPath(paths[index], color, alpha, style)
            }
        }
    }
}

/** Parsed icon paths, one array per icon, created on first draw. */
internal object KkIconPaths {
    private val cache = arrayOfNulls<Array<Path>>(KkIcon.entries.size)

    fun of(icon: KkIcon): Array<Path> = cache[icon.ordinal] ?: Array(icon.layers.size) { index ->
        PathParser().parsePathString(icon.layers[index].d).toPath()
    }.also { cache[icon.ordinal] = it }
}

private object KkIconStrokes {
    // Dash intervals are in grid units: the draw transform scales them with the icon.
    private val dashEffect: PathEffect by lazy { PathEffect.dashPathEffect(floatArrayOf(3f, 3f)) }
    private val dotEffect: PathEffect by lazy { PathEffect.dashPathEffect(floatArrayOf(1f, 4f)) }
    private val defaults: Array<Stroke> by lazy { create(2f) }
    private var customWidth = Float.NaN
    private var custom: Array<Stroke>? = null

    fun solid(w: Float): Stroke = set(w)[0]
    fun dashed(w: Float): Stroke = set(w)[1]
    fun dotted(w: Float): Stroke = set(w)[2]

    private fun set(w: Float): Array<Stroke> {
        if (w == 2f) return defaults
        custom?.let { if (w == customWidth) return it }
        return create(w).also {
            custom = it
            customWidth = w
        }
    }

    private fun create(w: Float): Array<Stroke> = arrayOf(
        Stroke(w, miter = 4f, cap = StrokeCap.Square, join = StrokeJoin.Miter),
        Stroke(w, miter = 4f, cap = StrokeCap.Square, join = StrokeJoin.Miter, pathEffect = dashEffect),
        Stroke(w, miter = 4f, cap = StrokeCap.Square, join = StrokeJoin.Miter, pathEffect = dotEffect),
    )
}
