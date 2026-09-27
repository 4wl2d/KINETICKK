// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

internal enum class PauseTarget {
    RESUME,
    SETTINGS,
    CODEX,
    PERFORMANCE,
    EXIT,
}

internal class PauseActionBounds(
    val target: PauseTarget,
    val bounds: Rect,
)

/**
 * Pause overlay geometry in px. [actions] are the menu item hit rects (drawing uses their left
 * edge and vertical center); [unit] converts board px (Pause 1440 x 810, Mobile-Pause 844 x 390,
 * portrait 390 x 844) to screen px. [panelRight] is the top-right x of the skewed ink panel,
 * [navClip] clips the selected item's slab and [build] holds the build overview.
 */
internal class PauseLayoutGeometry(
    val mode: GameplayLayoutMode,
    val titleY: Float,
    val actions: List<PauseActionBounds>,
    val titleX: Float = 0f,
    val unit: Float = 1f,
    val titleSize: Float = 92f,
    val timerSize: Float = 22f,
    val menuFontSize: Float = 46f,
    val panelRight: Float = 0f,
    val panelBottom: Float = 0f,
    val navClip: Rect = Rect.Zero,
    val build: Rect = Rect.Zero,
)

internal fun pauseLayoutGeometry(width: Float, height: Float, scale: Float): PauseLayoutGeometry {
    val safeScale = scale.coerceAtLeast(1f)
    val mode = gameplayLayoutMode(width, height, safeScale)
    return when (mode) {
        GameplayLayoutMode.REGULAR -> {
            val unit = min(width / 1440f, height / 810f).coerceIn(0.55f * safeScale, 1.4f * safeScale)
            val ox = max(0f, (width - 1440f * unit) * 0.5f)
            val oy = max(0f, (height - 810f * unit) * 0.5f)
            val targets = listOf(PauseTarget.RESUME, PauseTarget.SETTINGS, PauseTarget.CODEX, PauseTarget.EXIT)
            val itemHeight = max(54f * unit, 48f * safeScale)
            val pitch = max(66f * unit, itemHeight + 4f * safeScale)
            PauseLayoutGeometry(
                mode = mode,
                titleY = oy + 44f * unit,
                titleX = ox + 48f * unit,
                unit = unit,
                titleSize = 92f,
                timerSize = 22f,
                menuFontSize = 46f,
                actions = targets.mapIndexed { index, target ->
                    val left = ox + (60f + index * 10f) * unit
                    val center = oy + 257f * unit + index * pitch
                    PauseActionBounds(target, Rect(left, center - itemHeight * 0.5f, left + 420f * unit, center + itemHeight * 0.5f))
                },
                panelRight = ox + 560f * unit,
                panelBottom = oy + 810f * unit,
                navClip = Rect(0f, 0f, ox + 590f * unit, height),
                build = Rect(ox + 650f * unit, oy + 44f * unit, ox + 1392f * unit, oy + 640f * unit),
            )
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val unit = min(width / 844f, height / 390f).coerceIn(0.6f * safeScale, 1.4f * safeScale)
            val ox = max(0f, (width - 844f * unit) * 0.5f)
            val oy = max(0f, (height - 390f * unit) * 0.5f)
            val targets = listOf(PauseTarget.RESUME, PauseTarget.SETTINGS, PauseTarget.CODEX, PauseTarget.PERFORMANCE, PauseTarget.EXIT)
            val itemHeight = max(44f * unit, 48f * safeScale)
            val pitch = max(50f * unit, itemHeight + 2f * safeScale)
            val first = min(oy + 118f * unit, height - 4f * safeScale - pitch * (targets.size - 1) - itemHeight * 0.5f)
            PauseLayoutGeometry(
                mode = mode,
                titleY = oy + 18f * unit,
                titleX = ox + 44f * unit,
                unit = unit,
                titleSize = 44f,
                timerSize = 16f,
                menuFontSize = 32f,
                actions = targets.mapIndexed { index, target ->
                    val left = ox + 44f * unit
                    val center = first + index * pitch
                    PauseActionBounds(target, Rect(left, center - itemHeight * 0.5f, left + 290f * unit, center + itemHeight * 0.5f))
                },
                panelRight = ox + 360f * unit,
                panelBottom = oy + 390f * unit,
                navClip = Rect(0f, 0f, ox + 350f * unit, height),
                build = Rect(ox + 400f * unit, oy + 18f * unit, ox + 800f * unit, oy + 380f * unit),
            )
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val unit = min(width / 390f, height / 844f).coerceIn(0.6f * safeScale, 1.4f * safeScale)
            val ox = max(0f, (width - 390f * unit) * 0.5f)
            val oy = max(0f, (height - 844f * unit) * 0.5f)
            val targets = listOf(PauseTarget.RESUME, PauseTarget.SETTINGS, PauseTarget.CODEX, PauseTarget.PERFORMANCE, PauseTarget.EXIT)
            val itemHeight = max(46f * unit, 48f * safeScale)
            val pitch = max(54f * unit, itemHeight + 2f * safeScale)
            val first = oy + 142f * unit
            val menuBottom = first + pitch * (targets.size - 1) + itemHeight * 0.5f
            PauseLayoutGeometry(
                mode = mode,
                titleY = oy + 26f * unit,
                titleX = ox + 24f * unit,
                unit = unit,
                titleSize = 44f,
                timerSize = 16f,
                menuFontSize = 32f,
                actions = targets.mapIndexed { index, target ->
                    val left = ox + 24f * unit
                    val center = first + index * pitch
                    PauseActionBounds(target, Rect(left, center - itemHeight * 0.5f, left + 300f * unit, center + itemHeight * 0.5f))
                },
                panelRight = ox + 330f * unit,
                panelBottom = menuBottom + 20f * unit,
                navClip = Rect(0f, 0f, ox + 330f * unit, menuBottom + 20f * unit),
                build = Rect(ox + 16f * unit, menuBottom + 36f * unit, ox + 374f * unit, height - 12f * safeScale),
            )
        }
    }
}
