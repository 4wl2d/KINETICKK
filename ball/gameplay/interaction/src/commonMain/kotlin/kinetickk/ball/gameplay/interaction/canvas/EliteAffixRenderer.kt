// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Side of an affix glyph (world units) and the ink seam around its bone fill. */
internal const val AFFIX_GLYPH_SIZE = 9f
private const val AFFIX_SEAM = 2f

/** The affix glyph sits on top of the Elite halo, this many radii above the centre. */
internal const val AFFIX_GLYPH_LIFT = 1.45f

/** A champion's dashed bone aura: its radius in enemy radii, alpha, a turn every 24 s; its outline is 0.5 dp heavier. */
internal const val CHAMPION_AURA = 1.35f
private const val CHAMPION_AURA_ALPHA = 0.55f
private const val CHAMPION_TURN_DEGREES_PER_SECOND = 360f * 1_000f / KkTime.AmbientMin
private const val CHAMPION_OUTLINE_BONUS = 0.5f

/**
 * An Elite's affix and a champion's aura, after the Elite halo ([drawEnemy]): the affix as one 9-unit bone glyph with an
 * ink seam on top of the halo, upright (a blunt shield plate, three dots, two concentric arcs, two interlocked rings, a
 * blunt burst, two sheared bars; never an arrow), and a champion as a dashed bone ring at 1.35 radii turning once in
 * 24 s. Like status patterns, [detail] false (a crowd above the status-pattern limit) drops them on ordinary enemies;
 * Elites keep their detail, so the affix stays. A Shielded Elite's live shield is the impact-shield ring ([drawEnemy]).
 */
internal fun DrawScope.drawEliteVariety(engine: GameplayRenderModel, enemy: EnemyProjection, center: Offset, detail: Boolean) {
    if (!detail) return
    val bits = enemy.affinityBits
    if (EnemyAffinity.champion(bits)) {
        rotate(engine.renderElapsed * CHAMPION_TURN_DEGREES_PER_SECOND, center) {
            drawCircle(Kk.Bone.copy(alpha = CHAMPION_AURA_ALPHA), enemy.radius * CHAMPION_AURA, center, style = WorldStrokes.dashedMedium)
        }
    }
    val affix = EnemyAffinity.affix(bits)
    if (affix < 0 || affix >= EliteAffix.entries.size) return
    val glyph = AffixGlyphs.of(affix)
    translate(center.x, center.y - enemy.radius * AFFIX_GLYPH_LIFT) {
        drawPath(glyph, Kk.Ink, style = kkStroke(AFFIX_SEAM))
        drawPath(glyph, Kk.Bone)
    }
}

/** Extra outline weight of a champion (its threat outline stays threat). */
internal fun championOutlineBonus(affinity: Int): Float = if (EnemyAffinity.champion(affinity)) CHAMPION_OUTLINE_BONUS else 0f

/**
 * The six affix glyphs, centred on the origin in a [AFFIX_GLYPH_SIZE] box, built once (world units are dp at every
 * density) and drawn under a translate. Draw-thread confined.
 */
internal object AffixGlyphs {
    private val paths = arrayOfNulls<Path>(EliteAffix.entries.size)

    /** The glyph of the affix with [ordinal]. */
    fun of(ordinal: Int): Path = paths[ordinal] ?: build(EliteAffix.entries[ordinal]).also { paths[ordinal] = it }

    /** The glyph of [affix]. */
    fun of(affix: EliteAffix): Path = of(affix.ordinal)

    private fun build(affix: EliteAffix): Path {
        val h = AFFIX_GLYPH_SIZE * 0.5f
        val path = Path()
        when (affix) {
            // A blunt shield plate: square shoulders, the foot cut flat.
            EliteAffix.SHIELDED -> polygon(path, -h * 0.9f, -h, h * 0.9f, -h, h * 0.9f, h * 0.1f, h * 0.35f, h, -h * 0.35f, h, -h * 0.9f, h * 0.1f)
            // Three dots in a triangle.
            EliteAffix.SPLITTING -> {
                val r = h * 0.36f
                circle(path, 0f, -h + r, r)
                circle(path, -h + r, h - r, r)
                circle(path, h - r, h - r, r)
            }
            // Two concentric arcs opening downward, like field lines over a pole.
            EliteAffix.MAGNETIC -> {
                arcBand(path, 0f, h * 0.49f, h * 0.2f, h * 0.48f)
                arcBand(path, 0f, h * 0.49f, h * 0.7f, h * 0.98f)
            }
            // Two interlocked rings (even-odd: the crossings open, as links read).
            EliteAffix.LINKED -> {
                path.fillType = PathFillType.EvenOdd
                val r = h * 0.62f
                circle(path, -h * 0.38f, 0f, r)
                circle(path, -h * 0.38f, 0f, r * 0.55f)
                circle(path, h * 0.38f, 0f, r)
                circle(path, h * 0.38f, 0f, r * 0.55f)
            }
            // A burst with eight blunt (cut flat) points.
            EliteAffix.VOLATILE -> burst(path, h, h * 0.62f)
            // Two bars sheared like the board's slashes (KkShape.Shear).
            EliteAffix.HASTED -> {
                val lean = kkShearOffset(AFFIX_GLYPH_SIZE)
                val bar = h * 0.42f
                for (x in floatArrayOf(-h * 0.5f, h * 0.5f)) {
                    path.moveTo(x - bar * 0.5f + lean, -h)
                    path.lineTo(x + bar * 0.5f + lean, -h)
                    path.lineTo(x + bar * 0.5f - lean, h)
                    path.lineTo(x - bar * 0.5f - lean, h)
                    path.close()
                }
            }
        }
        return path
    }

    private fun polygon(path: Path, vararg points: Float) {
        path.moveTo(points[0], points[1])
        for (index in 2 until points.size step 2) path.lineTo(points[index], points[index + 1])
        path.close()
    }

    private fun circle(path: Path, x: Float, y: Float, r: Float) = path.addOval(Rect(x - r, y - r, x + r, y + r))

    /** A 180° band between radii [inner] and [outer] around ([x], [y]), over the top. */
    private fun arcBand(path: Path, x: Float, y: Float, inner: Float, outer: Float) {
        val steps = 12
        for (step in 0..steps) {
            val angle = PI.toFloat() + PI.toFloat() * step / steps
            val px = x + cos(angle) * outer
            val py = y + sin(angle) * outer
            if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        for (step in steps downTo 0) {
            val angle = PI.toFloat() + PI.toFloat() * step / steps
            path.lineTo(x + cos(angle) * inner, y + sin(angle) * inner)
        }
        path.close()
    }

    /** Eight points between radii [outer] and [inner], each point cut flat across a short chord. */
    private fun burst(path: Path, outer: Float, inner: Float) {
        val points = 8
        val tip = PI.toFloat() / points * 0.28f
        for (point in 0 until points) {
            val angle = -PI.toFloat() / 2f + 2f * PI.toFloat() * point / points
            val valley = angle + PI.toFloat() / points
            if (point == 0) path.moveTo(cos(angle - tip) * outer, sin(angle - tip) * outer)
            else path.lineTo(cos(angle - tip) * outer, sin(angle - tip) * outer)
            path.lineTo(cos(angle + tip) * outer, sin(angle + tip) * outer)
            path.lineTo(cos(valley) * inner, sin(valley) * inner)
        }
        path.close()
    }
}
