// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureModel
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.rememberInterfaceTypography
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real pause renderer at the three reference frames, in English and Russian, at 100 %, the
 * default 125 % and 175 % text size: every text it lays out fits the width it was given, is never
 * cut with an ellipsis, never breaks inside a word and stays on screen inside its area.
 */
@OptIn(ExperimentalTestApi::class)
class PauseTextFitTest {
    private val frames = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val relics = listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, 2), EquippedRelic(RelicId.GHOST_VECTOR, 1), EquippedRelic(RelicId.ORBITAL_NAIL, 1))

    @Test
    fun everyPauseTextFitsItsBoxWholeAtEveryTextSize() {
        for ((width, height) in frames) for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) {
            val scene = "pause ${width}x$height $language ${textScale}x"
            val records = drawPause(width, height, language, textScale)
            val layout = pauseLayoutGeometry(width.toFloat(), height.toFloat(), 1f)
            assertTrue(records.any { it.kind == PauseTextKind.STAT_LABEL }, "$scene draws the stats")
            records.forEach { record ->
                val text = record.layout.layoutInput.text.text
                assertTrue(record.layout.size.width <= record.box + 0.5f, "$scene: \"$text\" (${record.kind}) ${record.layout.size.width} fits ${record.box}")
                assertWhole(record.layout, "$scene: ${record.kind}")
                val r = record.rect
                assertTrue(r.left >= -0.5f && r.right <= width + 0.5f && r.top >= -0.5f && r.bottom <= height + 0.5f, "$scene: \"$text\" on screen: $r")
                if (record.kind != PauseTextKind.TITLE && record.kind != PauseTextKind.TIMER) {
                    val build = layout.build
                    assertTrue(r.left >= build.left - 1f && r.right <= build.right + 1f && r.bottom <= build.bottom + 1f,
                        "$scene: \"$text\" (${record.kind}) inside the build area $build: $r")
                } else {
                    assertTrue(r.right <= layout.panelRight + 1f, "$scene: \"$text\" inside the pause panel")
                }
            }
            val heading = records.single { it.kind == PauseTextKind.HEADING }.rect
            records.filter { it.kind == PauseTextKind.TAG }.forEach { tag ->
                assertTrue(heading.right <= tag.rect.left + 0.5f, "$scene: the heading ends before the tags")
            }
            val labels = records.filter { it.kind == PauseTextKind.STAT_LABEL }
            // One list, one type size.
            assertEquals(1, labels.map { it.layout.layoutInput.style.fontSize }.distinct().size, "$scene: stat labels share one size")
            val columns = labels.map { it.rect.left }.distinct().size
            when (layout.mode) {
                GameplayLayoutMode.COMPACT_PORTRAIT -> {
                    assertEquals(1, columns, "$scene: portrait shows the stats in one column")
                    val description = records.single { it.kind == PauseTextKind.SYNERGY_DESCRIPTION }
                    assertTrue(description.layout.lineCount <= 2, "$scene: the synergy description takes at most two lines")
                }
                GameplayLayoutMode.COMPACT_LANDSCAPE -> assertEquals(2, columns, "$scene: phone landscape keeps two stat columns")
                GameplayLayoutMode.REGULAR -> assertEquals(1, columns, "$scene: the desktop stats column")
            }
        }
    }

    @Test
    fun displayTypeKeepsItsSizeWhileUiTextFollowsTheSetting() {
        val default = drawPause(1440, 810, AppLanguage.English, 1.25f)
        val large = drawPause(1440, 810, AppLanguage.English, 1.75f)
        fun size(records: List<PauseTextRecord>, kind: PauseTextKind) =
            records.first { it.kind == kind }.layout.layoutInput.style.fontSize.value
        assertEquals(size(default, PauseTextKind.TITLE), size(large, PauseTextKind.TITLE), "Paused ignores the text size")
        assertEquals(size(default, PauseTextKind.TIMER), size(large, PauseTextKind.TIMER), "the frozen clock ignores the text size")
        assertEquals(size(default, PauseTextKind.LEVEL), size(large, PauseTextKind.LEVEL), "the weapon level numeral ignores the text size")
        assertEquals(size(default, PauseTextKind.LABEL) * 1.4f, size(large, PauseTextKind.LABEL), 0.05f, "UI labels follow the text size")
        // At the default the UI text renders at the board size (Pause board: section labels 15 px).
        assertEquals(15f, size(default, PauseTextKind.LABEL), 0.05f)
    }

    private fun drawPause(width: Int, height: Int, language: AppLanguage, textScale: Float): List<PauseTextRecord> {
        val model = rewardFixtureModel(phase = GamePhase.PAUSED, relics = relics, language = language,
            width = width.toFloat(), height = height.toFloat(), textScale = textScale)
        val records = mutableListOf<PauseTextRecord>()
        PauseTextProbe.records = records
        try {
            runDesktopComposeUiTest(width, height) {
                setContent {
                    CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                        val measurer = CanvasTextMeasurer(rememberTextMeasurer(), textScale, language, rememberInterfaceTypography(), LocalKkRolePalette.current)
                        Box(Modifier.requiredSize(width.dp, height.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                records.clear()
                                drawPause(model, measurer, pauseLayoutGeometry(size.width, size.height, 1f))
                            }
                        }
                    }
                }
                waitForIdle()
            }
        } finally {
            PauseTextProbe.records = null
        }
        return records.toList()
    }
}

/** No line of [layout] is ellipsized and no line ends inside a word. */
private fun assertWhole(layout: TextLayoutResult, scene: String) {
    val text = layout.layoutInput.text.text
    for (line in 0 until layout.lineCount) {
        assertTrue(!layout.isLineEllipsized(line) && !layout.isCut(), "$scene: \"$text\" is cut off")
        if (line == layout.lineCount - 1) continue
        val end = layout.getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        assertTrue(text[end - 1].isWhitespace() || text[end].isWhitespace() || text[end - 1] in "-–—/", "$scene: \"$text\" breaks inside a word")
    }
    assertTrue(!layout.hasVisualOverflow, "$scene: \"$text\" overflows its box")
}
