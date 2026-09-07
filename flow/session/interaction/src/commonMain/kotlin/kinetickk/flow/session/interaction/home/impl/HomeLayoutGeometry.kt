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
    val compactPhone = logicalWidth <= 480f || (logicalHeight <= 480f && logicalWidth <= 1_000f)
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
    val actions = when (mode) {
        HomeLayoutMode.REGULAR -> {
            val center = width * 0.5f
            val cardY = height * 0.49f
            val cardWidth = minOf(d(108f), (width - d(48f)) / 6f)
            val centers = List(6) { center + (it - 2.5f) * cardWidth }
            val startY = height * 0.70f
            val navY = height * 0.84f
            val spacing = minOf(d(132f), width * 0.19f)
            val navStart = center - spacing * 2f
            buildList {
                coreTargets.forEachIndexed { index, target ->
                    add(HomeActionBounds(target, Rect(centers[index] - cardWidth * 0.46f, cardY - d(40f), centers[index] + cardWidth * 0.46f, cardY + d(40f))))
                }
                add(HomeActionBounds(HomeLayoutTarget.START, Rect(center - d(120f), startY - d(25f), center + d(120f), startY + d(25f))))
                navigationTargets.forEachIndexed { index, target ->
                    val itemCenter = navStart + spacing * index
                    add(HomeActionBounds(target, Rect(itemCenter - spacing * 0.44f, navY - d(28f), itemCenter + spacing * 0.44f, navY + d(28f))))
                }
            }
        }
        HomeLayoutMode.COMPACT_PORTRAIT -> {
            val margin = d(12f)
            val gap = d(8f)
            val cardWidth = (width - margin * 2f - gap * 2f) / 3f
            val cardHeight = d(68f)
            val cardTop = height * 0.43f
            val startTop = height * 0.68f
            val startHeight = d(56f)
            val navTop = height * 0.79f
            // Keep a small rounding margin above the 48 dp accessibility floor.
            val navHeight = d(50f)
            val navWidth = (width - margin * 2f - gap * 2f) / 3f
            buildList {
                coreTargets.forEachIndexed { index, target ->
                    val left = margin + (index % 3) * (cardWidth + gap)
                    val top = cardTop + (index / 3) * (cardHeight + gap)
                    add(HomeActionBounds(target, Rect(left, top, left + cardWidth, top + cardHeight)))
                }
                add(HomeActionBounds(HomeLayoutTarget.START, Rect(margin, startTop, width - margin, startTop + startHeight)))
                navigationTargets.forEachIndexed { index, target ->
                    val row = index / 3
                    val rowCount = if (row == 0) 3 else 2
                    val column = index % 3
                    val rowStart = (width - (navWidth * rowCount + gap * (rowCount - 1))) * 0.5f
                    val left = rowStart + column * (navWidth + gap)
                    val top = navTop + row * (navHeight + gap)
                    add(HomeActionBounds(target, Rect(left, top, left + navWidth, top + navHeight)))
                }
            }
        }
        HomeLayoutMode.COMPACT_LANDSCAPE -> {
            val margin = d(12f)
            val gap = d(8f)
            val leftPaneWidth = minOf(d(250f), width * 0.32f)
            val contentLeft = leftPaneWidth + margin
            val contentWidth = width - contentLeft - margin
            val cardWidth = (contentWidth - gap * 2f) / 3f
            val cardTop = d(30f)
            val cardHeight = d(60f)
            val startTop = d(170f)
            // Keep a small rounding margin above the 48 dp accessibility floor.
            val navHeight = d(50f)
            val navTop = height - margin - navHeight
            val navWidth = (contentWidth - gap * 4f) / 5f
            buildList {
                coreTargets.forEachIndexed { index, target ->
                    val left = contentLeft + (index % 3) * (cardWidth + gap)
                    val top = cardTop + (index / 3) * (cardHeight + gap)
                    add(HomeActionBounds(target, Rect(left, top, left + cardWidth, top + cardHeight)))
                }
                add(HomeActionBounds(HomeLayoutTarget.START, Rect(contentLeft, startTop, width - margin, startTop + d(56f))))
                navigationTargets.forEachIndexed { index, target ->
                    val left = contentLeft + index * (navWidth + gap)
                    add(HomeActionBounds(target, Rect(left, navTop, left + navWidth, navTop + navHeight)))
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
