// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage

/** What one rendered profile scene drew: the probed texts, icons and cues, and its pixels. */
internal class ProbedFrame(val drawn: List<ProfileDrawnText>, val image: ImageBitmap) {
    /** The last drawing of each [role] record per owner (a scene may draw several frames while it settles). */
    fun latest(role: String): List<ProfileDrawnText> =
        drawn.filter { it.role == role }.associateBy { it.owner to it.layout?.layoutInput?.text?.text }.values.toList()

    /** The last drawn record of [role]. */
    fun last(role: String): ProfileDrawnText? = drawn.lastOrNull { it.role == role }
}

/**
 * Renders each of [scenes] at [width]×[height] (density 1, the bundled fonts) in its [language]
 * and hands what [ProfileTextProbe] recorded, plus the captured pixels, to [check].
 */
@OptIn(ExperimentalTestApi::class)
internal fun <T> renderProbed(
    width: Int,
    height: Int,
    scenes: List<T>,
    language: (T) -> AppLanguage,
    content: @Composable (T) -> Unit,
    check: (T, ProbedFrame) -> Unit,
) = runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), Density(1f)) {
    mainClock.autoAdvance = false
    val current = mutableIntStateOf(-1)
    setContent {
        val index = current.intValue
        val scene = scenes.getOrNull(index)
        if (scene != null) key(index) {
            CompositionLocalProvider(LocalAppLanguage provides language(scene)) {
                Box(Modifier.requiredSize(width.dp, height.dp).testTag("probed-root")) { content(scene) }
            }
        }
    }
    val drawn = ArrayList<ProfileDrawnText>()
    ProfileTextProbe.sink = { drawn += it }
    try {
        scenes.forEachIndexed { index, scene ->
            drawn.clear()
            runOnIdle { current.intValue = index }
            mainClock.advanceTimeBy(64)
            waitForIdle()
            val image = onNodeWithTag("probed-root").captureToImage()
            check(scene, ProbedFrame(drawn.toList(), image))
        }
    } finally {
        ProfileTextProbe.sink = null
    }
}
