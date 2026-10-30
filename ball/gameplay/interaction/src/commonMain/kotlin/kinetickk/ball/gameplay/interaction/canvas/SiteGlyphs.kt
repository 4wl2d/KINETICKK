// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.foundation.design.KK_ICON_GRID
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.drawKkIcon

/** One part of a site glyph on the 24-unit icon grid. */
internal sealed interface SiteGlyphPart

/**
 * A polyline through [points] (x, y pairs), closed into a plate when [closed] and then filled when [fill]; [dashed]
 * strokes it in short dashes.
 */
internal class GlyphLine(
    vararg val points: Float,
    val closed: Boolean = false,
    val fill: Boolean = false,
    val dashed: Boolean = false,
) : SiteGlyphPart

/** An arc of the circle ([cx], [cy], [radius]) from [startDegrees] over [sweepDegrees]; a full circle is a ring or, [fill]ed, a dot. */
internal class GlyphArc(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val startDegrees: Float = 0f,
    val sweepDegrees: Float = 360f,
    val fill: Boolean = false,
) : SiteGlyphPart

/**
 * The marks of sites and world places (trial panel, edge markers, Codex): lines, rings and plates on the icon grid,
 * never an arrowhead or a numeral (SPEC rule 3). A glyph may start from a foundation [icon] and add its parts over it.
 */
enum class SiteGlyph(internal val icon: KkIcon?, internal vararg val parts: SiteGlyphPart) {
    ANOMALY(KkIcon.SYSTEM_ANOMALY),
    /** Two sheared gate posts and the dashed line between them. */
    RACE(
        null,
        GlyphLine(5f, 4f, 9f, 4f, 7f, 20f, 3f, 20f, closed = true, fill = true),
        GlyphLine(17f, 4f, 21f, 4f, 19f, 20f, 15f, 20f, closed = true, fill = true),
        GlyphLine(8f, 12f, 16f, 12f, dashed = true),
    ),
    /** Four corner brackets around a centre bar: a rule to keep. */
    CHALLENGE(
        null,
        GlyphLine(3f, 8f, 3f, 3f, 8f, 3f),
        GlyphLine(16f, 3f, 21f, 3f, 21f, 8f),
        GlyphLine(21f, 16f, 21f, 21f, 16f, 21f),
        GlyphLine(8f, 21f, 3f, 21f, 3f, 16f),
        GlyphLine(8f, 12f, 16f, 12f),
    ),
    /** The Brake control, struck through. */
    BRAKELESS(KkIcon.SYSTEM_BRAKE, GlyphLine(5f, 19f, 19f, 5f)),
    RAM(KkIcon.SYSTEM_DASH),
    UNTOUCHED(KkIcon.SYSTEM_SHIELD),
    /** A gauge: three stacked bars of rising length. */
    VELOCITY(null, GlyphLine(4f, 18f, 10f, 18f), GlyphLine(4f, 12f, 15f, 12f), GlyphLine(4f, 6f, 20f, 6f)),
    /** Three poles on alternating sides of the course line. */
    SLALOM(
        null,
        GlyphLine(3f, 12f, 21f, 12f),
        GlyphArc(6f, 7f, 2f, fill = true),
        GlyphArc(12f, 17f, 2f, fill = true),
        GlyphArc(18f, 7f, 2f, fill = true),
    ),
    /** Nested diamonds: a marked target. */
    BOUNTY(
        null,
        GlyphLine(12f, 2f, 22f, 12f, 12f, 22f, 2f, 12f, closed = true),
        GlyphLine(12f, 7f, 17f, 12f, 12f, 17f, 7f, 12f, closed = true),
    ),
    /** A sheared crate under its lid line. */
    CACHE(null, GlyphLine(6f, 9f, 22f, 9f, 19f, 21f, 3f, 21f, closed = true), GlyphLine(7f, 5f, 23f, 5f)),
    /** A miniature singularity: a ring around its dot. */
    LOST_CURSOR(null, GlyphArc(12f, 12f, 7f), GlyphArc(12f, 12f, 1.75f, fill = true)),
    /** The drifter's outline with a glint at its heart. */
    GOLDEN(null, GlyphLine(12f, 3f, 21f, 20f, 3f, 20f, closed = true), GlyphArc(12f, 14.5f, 1.75f, fill = true)),
    /** A ring with a gap. */
    SPIN(null, GlyphArc(12f, 12f, 8f, startDegrees = -90f, sweepDegrees = 300f)),
    /** The Orb, the Core's own outline. */
    DOODLE(KkIcon.FORMS_CIRCLE),
    /** Three concentric arcs. */
    QUIET(
        null,
        GlyphArc(4f, 12f, 5f, startDegrees = -40f, sweepDegrees = 80f),
        GlyphArc(4f, 12f, 10f, startDegrees = -40f, sweepDegrees = 80f),
        GlyphArc(4f, 12f, 15f, startDegrees = -40f, sweepDegrees = 80f),
    ),
    /** A ring with three blunt ticks pointing in at its dot. */
    SIGIL(
        null,
        GlyphArc(12f, 12f, 9f),
        GlyphLine(12f, 3f, 12f, 6.5f),
        GlyphLine(19.79f, 16.5f, 16.76f, 14.75f),
        GlyphLine(4.21f, 16.5f, 7.24f, 14.75f),
        GlyphArc(12f, 12f, 1.75f, fill = true),
    ),
    /** A rift: two rings around the dot of the layer beyond. */
    PORTAL(null, GlyphArc(12f, 12f, 9f), GlyphArc(12f, 12f, 5f), GlyphArc(12f, 12f, 1.75f, fill = true)),
    /** A dot sending two arcs to each side. */
    SIGNAL(
        null,
        GlyphArc(12f, 12f, 2f, fill = true),
        GlyphArc(12f, 12f, 6f, startDegrees = 135f, sweepDegrees = 90f),
        GlyphArc(12f, 12f, 6f, startDegrees = -45f, sweepDegrees = 90f),
        GlyphArc(12f, 12f, 10f, startDegrees = 135f, sweepDegrees = 90f),
        GlyphArc(12f, 12f, 10f, startDegrees = -45f, sweepDegrees = 90f),
    ),
    /** A floor plan: a room split by a wall with a doorway. */
    STRUCTURE(
        null,
        GlyphLine(3f, 3f, 21f, 3f, 21f, 21f, 3f, 21f, closed = true),
        GlyphLine(3f, 12f, 9f, 12f),
        GlyphLine(15f, 12f, 21f, 12f),
    ),
    /** A world crossed by its horizon. */
    DIMENSION(null, GlyphArc(12f, 12f, 9f), GlyphLine(1f, 12f, 23f, 12f)),

    // Each structure template's own floor plan (the Codex World entries tell them apart).

    /** The Derelict Station: a hull breached in its top wall, a bulkhead half across it. */
    STATION(
        null,
        GlyphLine(14f, 3f, 21f, 3f, 21f, 21f, 3f, 21f, 3f, 3f, 9f, 3f),
        GlyphLine(3f, 13f, 13f, 13f),
    ),
    /** The Vault: a sealed room inside a room. */
    VAULT(
        null,
        GlyphLine(3f, 3f, 21f, 3f, 21f, 21f, 3f, 21f, closed = true),
        GlyphLine(8f, 8f, 16f, 8f, 16f, 16f, 8f, 16f, closed = true, fill = true),
    ),
    /** The Spire: a narrow tower of three floors. */
    SPIRE(
        null,
        GlyphLine(8f, 2f, 16f, 2f, 16f, 22f, 8f, 22f, closed = true),
        GlyphLine(8f, 9f, 16f, 9f),
        GlyphLine(8f, 15f, 16f, 15f),
    ),
    /** The Labyrinth: a room with two staggered walls. */
    LABYRINTH(
        null,
        GlyphLine(3f, 3f, 21f, 3f, 21f, 21f, 3f, 21f, closed = true),
        GlyphLine(3f, 9f, 15f, 9f),
        GlyphLine(9f, 15f, 21f, 15f),
    ),
    /** The Foundry: a hall round its furnace. */
    FOUNDRY(
        null,
        GlyphLine(3f, 3f, 21f, 3f, 21f, 21f, 3f, 21f, closed = true),
        GlyphArc(12f, 12f, 4.5f),
        GlyphArc(12f, 12f, 1.75f, fill = true),
    ),
    /** The Reliquary: a nave under its apse with the relic inside. */
    RELIQUARY(
        null,
        GlyphLine(6f, 10f, 6f, 22f, 18f, 22f, 18f, 10f),
        GlyphArc(12f, 10f, 6f, startDegrees = 180f, sweepDegrees = 180f),
        GlyphArc(12f, 16f, 1.75f, fill = true),
    ),
}

/** The glyph of one site kind (the trial panel's family glyph is [siteFamilyGlyph]). */
fun siteGlyph(kind: SiteKind): SiteGlyph = when (kind) {
    SiteKind.RESONANT_CIRCUIT, SiteKind.SEALED_ANOMALY, SiteKind.COLLAPSING_ORBIT -> SiteGlyph.ANOMALY
    SiteKind.GATE_RACE -> SiteGlyph.RACE
    SiteKind.BRAKELESS_TRIAL -> SiteGlyph.BRAKELESS
    SiteKind.RAM_TRIAL -> SiteGlyph.RAM
    SiteKind.UNTOUCHED_TRIAL -> SiteGlyph.UNTOUCHED
    SiteKind.VELOCITY_TRIAL -> SiteGlyph.VELOCITY
    SiteKind.SLALOM_TRIAL -> SiteGlyph.SLALOM
    SiteKind.BOUNTY_ARENA -> SiteGlyph.BOUNTY
    SiteKind.DRIFT_CACHE -> SiteGlyph.CACHE
    SiteKind.LOST_CURSOR -> SiteGlyph.LOST_CURSOR
    SiteKind.GOLDEN_DRIFTER -> SiteGlyph.GOLDEN
    SiteKind.QUIET_SIGNAL -> SiteGlyph.QUIET
    SiteKind.FAR_SIGIL -> SiteGlyph.SIGIL
    SiteKind.ZENITH_RIFT, SiteKind.NADIR_RIFT -> SiteGlyph.PORTAL
}

/** The glyph a whole family shares (the trial panel's first row). */
fun siteFamilyGlyph(family: SiteFamily): SiteGlyph = when (family) {
    SiteFamily.ANOMALY -> SiteGlyph.ANOMALY
    SiteFamily.RACE -> SiteGlyph.RACE
    SiteFamily.CHALLENGE -> SiteGlyph.CHALLENGE
    SiteFamily.BOUNTY -> SiteGlyph.BOUNTY
    SiteFamily.CACHE -> SiteGlyph.CACHE
    SiteFamily.SECRET -> SiteGlyph.SIGIL
    SiteFamily.PORTAL -> SiteGlyph.PORTAL
    SiteFamily.SIGNAL -> SiteGlyph.SIGNAL
}

/**
 * The glyph of a world discovery (the Codex World category): a site family's own glyph, the dimension's horizon for the
 * three layers, each structure template's own floor plan and each secret's own mark.
 */
fun worldDiscoveryGlyph(id: WorldDiscovery): SiteGlyph = when (id) {
    WorldDiscovery.GATE_RACE -> SiteGlyph.RACE
    WorldDiscovery.TRIALS -> SiteGlyph.CHALLENGE
    WorldDiscovery.BOUNTY -> SiteGlyph.BOUNTY
    WorldDiscovery.DRIFT_CACHE -> SiteGlyph.CACHE
    WorldDiscovery.ZENITH, WorldDiscovery.NADIR, WorldDiscovery.ARCHIVE -> SiteGlyph.DIMENSION
    WorldDiscovery.DERELICT_STATION -> SiteGlyph.STATION
    WorldDiscovery.VAULT -> SiteGlyph.VAULT
    WorldDiscovery.SPIRE -> SiteGlyph.SPIRE
    WorldDiscovery.LABYRINTH -> SiteGlyph.LABYRINTH
    WorldDiscovery.FOUNDRY -> SiteGlyph.FOUNDRY
    WorldDiscovery.RELIQUARY -> SiteGlyph.RELIQUARY
    WorldDiscovery.LOST_CURSOR -> SiteGlyph.LOST_CURSOR
    WorldDiscovery.GOLDEN_DRIFTER -> SiteGlyph.GOLDEN
    WorldDiscovery.SPIN_CYCLE -> SiteGlyph.SPIN
    WorldDiscovery.ARCHITECTS_DOODLE -> SiteGlyph.DOODLE
    WorldDiscovery.QUIET_SIGNAL -> SiteGlyph.QUIET
    WorldDiscovery.CARTOGRAPHER, WorldDiscovery.ANTECEDENT -> SiteGlyph.SIGIL
}

/**
 * Draws [glyph] centered at [center], [size] px across (the 24-unit grid maps to [size]); [strokeWidth] is in grid
 * units. Paths are built once per glyph and strokes cached, so drawing allocates nothing.
 */
fun DrawScope.drawSiteGlyph(
    glyph: SiteGlyph,
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float = 1f,
    strokeWidth: Float = 2f,
) {
    if (size <= 0f || alpha <= 0f) return
    glyph.icon?.let { drawKkIcon(it, center, size, color, alpha, strokeWidth) }
    val parts = glyph.parts
    if (parts.isEmpty()) return
    val paths = SiteGlyphPaths.of(glyph)
    val scale = size / KK_ICON_GRID
    translate(center.x - size * 0.5f, center.y - size * 0.5f) {
        scale(scale, scale, Offset.Zero) {
            for (index in parts.indices) drawPath(paths[index], color, alpha, SiteGlyphStrokes.style(parts[index], strokeWidth))
        }
    }
}

/** Glyph paths in grid units, one array per glyph, built on first draw (draw-thread confined). */
private object SiteGlyphPaths {
    private val cache = arrayOfNulls<Array<Path>>(SiteGlyph.entries.size)

    fun of(glyph: SiteGlyph): Array<Path> = cache[glyph.ordinal] ?: Array(glyph.parts.size) { index ->
        Path().apply { add(glyph.parts[index]) }
    }.also { cache[glyph.ordinal] = it }

    private fun Path.add(part: SiteGlyphPart) {
        when (part) {
            is GlyphLine -> {
                val points = part.points
                moveTo(points[0], points[1])
                var index = 2
                while (index + 1 < points.size) {
                    lineTo(points[index], points[index + 1])
                    index += 2
                }
                if (part.closed) close()
            }
            is GlyphArc -> {
                val bounds = Rect(part.cx - part.radius, part.cy - part.radius, part.cx + part.radius, part.cy + part.radius)
                if (part.sweepDegrees >= 360f) addOval(bounds) else arcTo(bounds, part.startDegrees, part.sweepDegrees, true)
            }
        }
    }
}

/** Blunt-ended strokes in grid units for a few widths at once (the draw transform scales them with the glyph). */
private object SiteGlyphStrokes {
    private val dashEffect: PathEffect by lazy { PathEffect.dashPathEffect(floatArrayOf(2.5f, 2f)) }
    private val widths = FloatArray(4) { Float.NaN }
    private val solids = arrayOfNulls<Stroke>(4)
    private val dashes = arrayOfNulls<Stroke>(4)
    private var next = 0

    fun style(part: SiteGlyphPart, width: Float): DrawStyle = when (part) {
        is GlyphLine -> if (part.fill) Fill else if (part.dashed) dashed(width) else solid(width)
        is GlyphArc -> if (part.fill) Fill else solid(width)
    }

    private fun solid(width: Float): Stroke = solids[slot(width)]!!

    private fun dashed(width: Float): Stroke = dashes[slot(width)]!!

    private fun slot(width: Float): Int {
        for (index in widths.indices) if (widths[index] == width) return index
        val slot = next
        next = (next + 1) % widths.size
        widths[slot] = width
        solids[slot] = Stroke(width, miter = 4f, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
        dashes[slot] = Stroke(width, miter = 4f, cap = StrokeCap.Butt, join = StrokeJoin.Miter, pathEffect = dashEffect)
        return slot
    }
}
