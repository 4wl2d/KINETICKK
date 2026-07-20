// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.armory.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import kinetickk.core.content.WeaponCatalog
import kinetickk.core.content.WeaponDefinition
import kinetickk.core.content.WeaponId
import kinetickk.core.content.WeaponMastery
import kinetickk.core.design.*
import kinetickk.core.profile.api.LoadoutCapability
import kinetickk.core.profile.api.PreferencesReader
import kinetickk.feature.armory.api.ArmoryFeature
import kinetickk.feature.armory.api.ArmoryOutput
import kinetickk.feature.armory.api.ArmoryRenderModel
import kotlin.math.min

class DefaultArmoryFeature(
    loadoutCapability: LoadoutCapability,
    private val preferencesReader: PreferencesReader,
) : ArmoryFeature {
    private val reducer = ArmoryReducer(loadoutCapability)

    @Composable
    override fun Content(activeRunWeapon: WeaponId?, onOutput: (ArmoryOutput) -> Unit) {
        val density = LocalDensity.current.density
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        var pageValue by rememberSaveable { mutableIntStateOf(0) }
        var revisionValue by remember { mutableIntStateOf(0) }
        var viewportValue by remember { mutableStateOf(ArmoryViewport(1f, 1f, density)) }
        var renderTimeSecondsValue by remember { mutableFloatStateOf(0f) }
        @Suppress("UNUSED_EXPRESSION")
        revisionValue
        val model = reducer.renderModel(activeRunWeapon)
        val textMeasurer = CanvasTextMeasurer(
            composeTextMeasurer,
            preferencesReader.preferences().textScale,
        )

        fun dispatch(action: ArmoryAction) {
            val reduction = reducer.reduce(pageValue, action)
            pageValue = reduction.page
            if (reduction.profileChanged) revisionValue++
            reduction.feedbackCue?.let { cue -> onOutput(ArmoryOutput.Cue(cue)) }
            if (reduction.close) onOutput(ArmoryOutput.Back)
        }

        LaunchedEffect(Unit) {
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                renderTimeSecondsValue += ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.1f)
                previousFrame = frame
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewportValue = ArmoryViewport(size.width.toFloat(), size.height.toFloat(), density)
                }
                .pointerInput(viewportValue, pageValue) {
                    detectTapGestures { position ->
                        resolveArmoryPress(viewportValue, pageValue, position.x, position.y)?.let(::dispatch)
                    }
                },
        ) {
            drawArmory(model, pageValue, reducer.maxPage, textMeasurer, renderTimeSecondsValue)
        }
    }
}

private val WeaponMasteryProgressionLabel = WeaponMastery.entries.drop(1).joinToString("  ") {
    "L${it.minimumLevel} ${it.displayLabel.uppercase()}"
}

private fun DrawScope.drawArmory(
    engine: ArmoryRenderModel,
    page: Int,
    maxPage: Int,
    textMeasurer: TextMeasurer,
    renderTime: Float,
) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Cyan)
    drawLabel(textMeasurer, "WEAPON ARMORY", bounds.left + d(25f), bounds.top + d(24f), 20f, Cyan, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "${WeaponCatalog.all.size} SYSTEMS // ${engine.unlockedWeapons.size} UNLOCKED // MATTER ${formatCompact(engine.totalMatter)}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val cardWidth = min(d(245f), (bounds.width - d(80f)) / 3f)
    val gap = d(16f)
    val total = cardWidth * 3f + gap * 2f
    val startX = (size.width - total) * 0.5f
    val cardTop = bounds.top + d(118f)
    val cardBottom = bounds.bottom - d(85f)
    val start = page.coerceIn(0, maxPage) * ARMORY_PAGE_SIZE
    WeaponCatalog.all.subList(start, min(start + ARMORY_PAGE_SIZE, WeaponCatalog.all.size))
        .forEachIndexed { index, definition ->
            drawWeaponCard(engine, textMeasurer, definition, startX + index * (cardWidth + gap), cardTop, cardWidth, cardBottom - cardTop, renderTime)
        }
    drawPagedFooter(textMeasurer, bounds, page.coerceIn(0, maxPage), maxPage, Cyan)
}

private fun DrawScope.drawWeaponCard(
    engine: ArmoryRenderModel,
    textMeasurer: TextMeasurer,
    definition: WeaponDefinition,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    renderTime: Float,
) {
    val unlocked = definition.id in engine.unlockedWeapons
    val equipped = engine.selectedWeapon == definition.id
    val active = engine.activeRunWeapon == definition.id
    val accent = if (unlocked) weaponColor(definition.id) else Muted
    drawRect(Color(0xB00B0D1D), Offset(x, y), Size(width, height))
    drawRect(accent, Offset(x, y), Size(width, height), style = Stroke(d(if (equipped) 2.2f else 1f)))
    drawRect(accent.copy(alpha = 0.12f), Offset(x, y), Size(width, d(50f)))
    drawWeaponGlyph(definition.id, Offset(x + width * 0.5f, y + d(95f)), d(28f), renderTime, accent)
    drawLabel(textMeasurer, definition.name.uppercase(), x + width * 0.5f, y + d(139f), 11f, accent, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, definition.tags.joinToString(" / "), x + width * 0.5f, y + d(164f), 7f, Muted, centered = true)
    drawLabel(textMeasurer, definition.description, x + d(14f), y + d(193f), 7f, White, maxWidth = width - d(28f), maxLines = 3)
    drawLabel(textMeasurer, WeaponMasteryProgressionLabel, x + width * 0.5f, y + d(274f), 6f, accent, centered = true, maxWidth = width - d(20f), maxLines = 2)
    drawLabel(textMeasurer, "MILESTONES BOOST DAMAGE + ACTIVATION", x + width * 0.5f, y + d(295f), 6f, Muted, centered = true)
    val state = when {
        equipped -> "EQUIPPED LOADOUT"
        active -> "ACTIVE THIS RUN"
        unlocked -> "EQUIP"
        else -> "UNLOCK ${formatCompact(definition.permanentUnlockCost.toLong())}"
    }
    drawLabel(textMeasurer, state, x + width * 0.5f, y + height - d(34f), 9f, if (equipped) Acid else accent, centered = true, weight = FontWeight.Bold)
}
