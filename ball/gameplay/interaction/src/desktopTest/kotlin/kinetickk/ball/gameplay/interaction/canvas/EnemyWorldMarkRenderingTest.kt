// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Enemy marks drawn by the real world renderer: a secret boss keeps its anomaly glyph readable where threats are hatched
 * (Mono).
 */
class EnemyWorldMarkRenderingTest {
    @Test
    fun aSecretBossGlyphSitsOnAnInkPlateWhereThreatsAreHatched() {
        for (type in listOf(EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT)) {
            val radius = 54f
            val enemy = EnemyProjection(1, type, 0f, 0f, 0f, 0f, 600f, 1_000f, radius, 0f, 0f, 0f, 0f, 0f, 0f, false)
            val mono = drawn(enemy, KkRolePalette.Mono)
            // The glyph's diamond (24-grid half extent 10) at 0.75 radii: inside it, away from its outline and from the
            // "!" stroke and dot, only the ink plate shows; the body's white threat hatch never reaches the glyph.
            val scale = radius * 0.75f / 24f
            val inner = (10f - 1f - 1.2f) * scale
            var inside = 0
            var lit = 0
            for (y in -inner.toInt()..inner.toInt()) for (x in -inner.toInt()..inner.toInt()) {
                val dx = x + 0.5f
                val dy = y + 0.5f
                if (abs(dx) + abs(dy) > inner) continue
                if (abs(dx) < 1.6f * scale && dy > -6f * scale && dy < 6.6f * scale) continue
                inside++
                val pixel = mono[CENTER_X + x, CENTER_Y + y]
                if (pixel.red + pixel.green + pixel.blue > 1.2f) lit++
            }
            assertTrue(inside > 200, "$type: the sampled plate is the glyph's inside ($inside px)")
            assertEquals(0, lit, "$type: the Mono hatch shows through the glyph's diamond at $lit of $inside px")
        }
    }

    private fun drawn(enemy: EnemyProjection, roles: KkRolePalette): PixelMap {
        val model = hudTestModel(W.toFloat(), H.toFloat()).with(
            "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "elapsed" to 0f, "enemies" to immutableListOf(enemy),
        )
        val bitmap = ImageBitmap(W, H)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(W.toFloat(), H.toFloat())) {
            drawRect(Kk.Ink)
            drawEnemy(model, model.enemies.single(), 0f, 0f, roles, statusDetail = true)
        }
        return bitmap.toPixelMap()
    }

    private companion object {
        const val W = 640
        const val H = 400
        const val CENTER_X = W / 2
        const val CENTER_Y = H / 2
    }
}
