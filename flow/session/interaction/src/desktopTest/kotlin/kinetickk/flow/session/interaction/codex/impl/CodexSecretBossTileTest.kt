// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A secret boss's Codex tile keeps its shape secret until its discovery is recorded: no threat outline, nothing apart. */
class CodexSecretBossTileTest {
    private val catalog = codexTestCatalog()
    private val progress = codexTestProgress()
    private val roles = KkRolePalette.Default

    @Test
    fun aHiddenSecretBossTileShowsNoSilhouetteAndNoHiddenTileTellsTheTwoApart() {
        val hidden = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items)
        val found = hidden.copy(discoveredWorldEntries = immutableSetOf(WorldDiscovery.CARTOGRAPHER, WorldDiscovery.ANTECEDENT))
        val cartographer = tile(EnemyType.CARTOGRAPHER, hidden)
        val antecedent = tile(EnemyType.ANTECEDENT, hidden)
        assertContentEquals(pixels(cartographer), pixels(antecedent), "the two hidden tiles are the same tile")
        assertEquals(0, near(antecedent, roles.threat), "no threat outline of the shape")
        val revealed = tile(EnemyType.ANTECEDENT, found)
        assertTrue(near(revealed, roles.threat) > 40, "the found Antecedent shows its silhouette")
        assertTrue(near(tile(EnemyType.DRIFTER, hidden), roles.threat) > 40, "ordinary kinds always show theirs")
    }

    private fun tile(type: EnemyType, model: CodexRenderModel): PixelMap {
        val entry = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL, model, catalog, progress)
            .single { it.key == "enemy/$type" }
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(DENSITY), LayoutDirection.Ltr), 1f, roles = roles)
        val bitmap = ImageBitmap(SIDE, SIDE)
        CanvasDrawScope().draw(Density(DENSITY), LayoutDirection.Ltr, Canvas(bitmap), Size(SIDE.toFloat(), SIDE.toFloat())) {
            drawCodexCell(entry, catalog, measurer, band = false, hovered = false, markBand = 0f)
        }
        return bitmap.toPixelMap()
    }

    private fun pixels(map: PixelMap): IntArray = IntArray(SIDE * SIDE) { map[it % SIDE, it / SIDE].toArgb() }

    private fun near(map: PixelMap, color: Color): Int {
        var count = 0
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            val p = map[x, y]
            if (abs(p.red - color.red) + abs(p.green - color.green) + abs(p.blue - color.blue) < 0.15f) count++
        }
        return count
    }

    private companion object {
        /** A board-size Codex tile: 96 dp at density 2. */
        const val DENSITY = 2f
        const val SIDE = 192
    }
}
