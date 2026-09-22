// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.content.api.CoreShape

internal enum class HomeLayoutMode {
    REGULAR,
    COMPACT_PORTRAIT,
    COMPACT_LANDSCAPE,
}

internal enum class HomeLayoutTarget {
    CORE_ORB,
    CORE_PRISM,
    CORE_SHARD,
    CORE_RING,
    CORE_DIAMOND,
    CORE_TESSERACT,
    START,
    LAB,
    ARMORY,
    REBIRTH,
    CODEX,
    SETTINGS,
}

internal class HomeActionBounds(
    val target: HomeLayoutTarget,
    val bounds: Rect,
)

internal class HomeLayoutGeometry(
    val mode: HomeLayoutMode,
    val actions: List<HomeActionBounds>,
) {
    fun bounds(target: HomeLayoutTarget): Rect =
        requireNotNull(actions.firstOrNull { it.target == target }) { "Missing Home target $target" }.bounds
}

internal fun homeLayoutGeometry(width: Float, height: Float, density: Float): HomeLayoutGeometry {
    val scale = density.coerceAtLeast(1f)
    val logicalWidth = width / scale
    val logicalHeight = height / scale
    val compactPhone = logicalWidth < 900f || logicalHeight < 560f
    val mode = when {
        !compactPhone -> HomeLayoutMode.REGULAR
        logicalWidth <= logicalHeight -> HomeLayoutMode.COMPACT_PORTRAIT
        else -> HomeLayoutMode.COMPACT_LANDSCAPE
    }
    fun d(value: Float): Float = value * scale

    val coreTargets = listOf(
        HomeLayoutTarget.CORE_ORB,
        HomeLayoutTarget.CORE_PRISM,
        HomeLayoutTarget.CORE_SHARD,
        HomeLayoutTarget.CORE_RING,
        HomeLayoutTarget.CORE_DIAMOND,
        HomeLayoutTarget.CORE_TESSERACT,
    )
    val navigationTargets = listOf(
        HomeLayoutTarget.LAB,
        HomeLayoutTarget.ARMORY,
        HomeLayoutTarget.REBIRTH,
        HomeLayoutTarget.CODEX,
        HomeLayoutTarget.SETTINGS,
    )
    val menuTargets = listOf(HomeLayoutTarget.START) + navigationTargets
    val actions = buildList {
        when (mode) {
            HomeLayoutMode.REGULAR -> {
                val margin = d(36f)
                val menuRight = width * 0.48f
                val rowHeight = minOf(d(72f), height * 0.085f)
                val menuTop = height * 0.34f
                menuTargets.forEachIndexed { index, target ->
                    val top = menuTop + index * rowHeight
                    add(HomeActionBounds(target, Rect(margin, top, menuRight, top + rowHeight - d(3f))))
                }
                val coreLeft = width * 0.54f
                val coreWidth = (width - coreLeft - margin) / 6f
                val coreTop = minOf(height * 0.78f, height - d(165f))
                coreTargets.forEachIndexed { index, target ->
                    val left = coreLeft + index * coreWidth
                    add(HomeActionBounds(target, Rect(left, coreTop, left + coreWidth - d(3f), coreTop + d(68f))))
                }
            }
            HomeLayoutMode.COMPACT_PORTRAIT -> {
                val margin = d(16f)
                val gap = d(3f)
                val coreWidth = (width - margin * 2f - gap * 5f) / 6f
                val coreTop = height * 0.40f
                coreTargets.forEachIndexed { index, target ->
                    val left = margin + index * (coreWidth + gap)
                    add(HomeActionBounds(target, Rect(left, coreTop, left + coreWidth, coreTop + d(52f))))
                }
                val rowHeight = d(51f)
                val columns = if (logicalHeight < 660f) 2 else 1
                val menuWidth = (width - margin * 2f - d(8f) * (columns - 1)) / columns
                val menuTop = height - d(28f) - rowHeight * (6 / columns)
                menuTargets.forEachIndexed { index, target ->
                    val left = margin + (index % columns) * (menuWidth + d(8f))
                    val top = menuTop + (index / columns) * rowHeight
                    add(HomeActionBounds(target, Rect(left, top, left + menuWidth, top + d(49f))))
                }
            }
            HomeLayoutMode.COMPACT_LANDSCAPE -> {
                val margin = d(12f)
                val menuRight = width * 0.46f
                val rowHeight = ((height - d(60f)) / 6f).coerceAtLeast(d(49f))
                menuTargets.forEachIndexed { index, target ->
                    val top = d(48f) + index * rowHeight
                    add(HomeActionBounds(target, Rect(margin, top, menuRight, top + rowHeight - d(1f))))
                }
                val coreLeft = width * 0.53f
                val coreWidth = (width - coreLeft - margin - d(8f)) / 3f
                coreTargets.forEachIndexed { index, target ->
                    val left = coreLeft + (index % 3) * (coreWidth + d(4f))
                    val top = height - d(130f) + (index / 3) * d(54f)
                    add(HomeActionBounds(target, Rect(left, top, left + coreWidth, top + d(50f))))
                }
            }
        }
    }
    return HomeLayoutGeometry(mode, actions)
}

internal fun HomeLayoutTarget.toHomeAction(): HomeAction = when (this) {
    HomeLayoutTarget.CORE_ORB -> HomeAction.SelectCoreShape(CoreShape.ORB)
    HomeLayoutTarget.CORE_PRISM -> HomeAction.SelectCoreShape(CoreShape.PRISM)
    HomeLayoutTarget.CORE_SHARD -> HomeAction.SelectCoreShape(CoreShape.SHARD)
    HomeLayoutTarget.CORE_RING -> HomeAction.SelectCoreShape(CoreShape.RING)
    HomeLayoutTarget.CORE_DIAMOND -> HomeAction.SelectCoreShape(CoreShape.DIAMOND)
    HomeLayoutTarget.CORE_TESSERACT -> HomeAction.SelectCoreShape(CoreShape.TESSERACT)
    HomeLayoutTarget.START -> HomeAction.StartRun
    HomeLayoutTarget.LAB -> HomeAction.OpenLab
    HomeLayoutTarget.ARMORY -> HomeAction.OpenArmory
    HomeLayoutTarget.REBIRTH -> HomeAction.OpenRebirth
    HomeLayoutTarget.CODEX -> HomeAction.OpenCodex
    HomeLayoutTarget.SETTINGS -> HomeAction.OpenSettings
}

internal fun HomeLayoutTarget.coreShapeOrNull(): CoreShape? = when (this) {
    HomeLayoutTarget.CORE_ORB -> CoreShape.ORB
    HomeLayoutTarget.CORE_PRISM -> CoreShape.PRISM
    HomeLayoutTarget.CORE_SHARD -> CoreShape.SHARD
    HomeLayoutTarget.CORE_RING -> CoreShape.RING
    HomeLayoutTarget.CORE_DIAMOND -> CoreShape.DIAMOND
    HomeLayoutTarget.CORE_TESSERACT -> CoreShape.TESSERACT
    else -> null
}
