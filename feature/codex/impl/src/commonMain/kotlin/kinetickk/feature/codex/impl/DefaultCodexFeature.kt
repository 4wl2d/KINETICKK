// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.codex.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.content.ItemCatalog
import kinetickk.core.content.ItemDefinition
import kinetickk.core.design.*
import kinetickk.core.profile.api.CollectionCapability
import kinetickk.core.profile.api.PreferencesReader
import kinetickk.feature.codex.api.CodexFeature
import kinetickk.feature.codex.api.CodexOutput
import kinetickk.feature.codex.api.CodexRenderModel
import kinetickk.feature.codex.api.CodexRunStacks
import kotlin.math.min

class DefaultCodexFeature(
    collectionCapability: CollectionCapability,
    private val preferencesReader: PreferencesReader,
) : CodexFeature {
    private val reducer = CodexReducer(collectionCapability)

    @Composable
    override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) {
        val density = LocalDensity.current.density
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        var pageValue by rememberSaveable { mutableIntStateOf(0) }
        var viewportValue by remember { mutableStateOf(CodexViewport(1f, 1f, density)) }
        val model = reducer.renderModel(runStacks)
        val textMeasurer = CanvasTextMeasurer(
            composeTextMeasurer,
            preferencesReader.preferences().textScale,
        )

        fun dispatch(action: CodexAction) {
            val reduction = reducer.reduce(pageValue, action)
            pageValue = reduction.page
            onOutput(CodexOutput.Cue(AudioCue.UI_CLICK))
            if (reduction.close) onOutput(CodexOutput.Back)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewportValue = CodexViewport(size.width.toFloat(), size.height.toFloat(), density)
                }
                .pointerInput(viewportValue) {
                    detectTapGestures { position ->
                        resolveCodexPress(viewportValue, position.x, position.y)?.let(::dispatch)
                    }
                },
        ) {
            drawCodex(model, pageValue, reducer.maxPage, textMeasurer)
        }
    }
}

private fun DrawScope.drawCodex(
    engine: CodexRenderModel,
    page: Int,
    maxPage: Int,
    textMeasurer: TextMeasurer,
) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Magenta)
    drawLabel(textMeasurer, "ARTIFACT CODEX", bounds.left + d(25f), bounds.top + d(24f), 20f, Magenta, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "${engine.discoveredItemIds.size}/400 DISCOVERED // PAGE ${page + 1}/${maxPage + 1}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val contentTop = bounds.top + d(76f)
    val contentWidth = bounds.width - d(50f)
    val columnWidth = contentWidth * 0.5f
    val rowHeight = (bounds.height - d(146f)) / 5f
    val start = page.coerceIn(0, maxPage) * CODEX_PAGE_SIZE
    ItemCatalog.all.subList(start, min(start + CODEX_PAGE_SIZE, ItemCatalog.all.size))
        .forEachIndexed { index, item ->
            val column = index % 2
            val row = index / 2
            val x = bounds.left + d(25f) + column * columnWidth
            val y = contentTop + row * rowHeight
            drawCodexItem(engine, textMeasurer, item, x, y, columnWidth - d(10f), rowHeight - d(8f))
        }
    drawPagedFooter(textMeasurer, bounds, page.coerceIn(0, maxPage), maxPage, Magenta)
}

private fun DrawScope.drawCodexItem(
    engine: CodexRenderModel,
    textMeasurer: TextMeasurer,
    item: ItemDefinition,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    val discovered = engine.isDiscovered(item.id)
    val stack = engine.itemStack(item.id)
    val accent = if (discovered) rarityColor(item.rarity) else DarkLine
    drawRect(Color(0x8A0B0D1D), Offset(x, y), Size(width, height))
    drawRect(accent, Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "#${item.id.toString().padStart(3, '0')}", x + d(10f), y + d(9f), 7f, Muted)
    drawLabel(textMeasurer, if (discovered) item.name.uppercase() else "UNKNOWN SIGNAL", x + d(47f), y + d(9f), 8f, if (discovered) accent else Muted, weight = FontWeight.Bold)
    drawLabel(textMeasurer, if (discovered) "${item.rarity.displayLabel.uppercase()} // STACK $stack/${item.maxStacks}" else "LOCKED // LEVEL ${item.unlockLevel}", x + width - d(10f), y + d(9f), 7f, if (discovered) White else Muted, alignRight = true)
    drawItemIcon(
        item = item,
        center = Offset(x + d(25f), y + d(43f)),
        radius = d(14f),
        accent = accent,
        stack = if (discovered) stack else null,
        obscured = !discovered,
    )
    drawLabel(
        textMeasurer,
        if (discovered) item.description else "Acquire during a run to decode this artifact.",
        x + d(49f),
        y + d(33f),
        7f,
        Muted,
        maxWidth = width - d(59f),
        maxLines = 2,
    )
}
