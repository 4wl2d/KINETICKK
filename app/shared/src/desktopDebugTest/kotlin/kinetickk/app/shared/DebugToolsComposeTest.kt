// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kinetickk.app.shared.diagnostics.DebugSession
import kinetickk.foundation.diagnostics.DiagnosticPhase
import kotlinx.coroutines.CompletableDeferred
import java.awt.datatransfer.DataFlavor
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises the public controls without changing the machine's clipboard or player profile. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class DebugToolsComposeTest {
    @Test
    fun collapsedToggleStaysBetweenTheSideControlsBeforeAndDuringRecording() =
        runSkikoComposeUiTest(Size(320f, 640f), Density(1f)) {
            mainClock.autoAdvance = false
            val session = DebugSession()
            setContent { DebugTools(session) }
            settle()
            val idleBounds = onNodeWithTag("kinetickk.debug.toggle").fetchSemanticsNode().boundsInRoot
            assertEquals(160f, idleBounds.center.x, 0.5f)
            runOnIdle { session.start() }
            settle()
            val recordingBounds = onNodeWithTag("kinetickk.debug.toggle").fetchSemanticsNode().boundsInRoot
            assertEquals(160f, recordingBounds.center.x, 0.5f)
            assertTrue(recordingBounds.left > 64f && recordingBounds.right < 256f)
            runOnIdle { session.stop() }
        }

    @Test
    fun recordHideStopCopyAndClearKeepTheSameSession() =
        runSkikoComposeUiTest(Size(420f, 900f), Density(1f)) {
            mainClock.autoAdvance = false
            val session = DebugSession()
            var copied: String? = null
            setContent {
                val systemClipboard = LocalClipboard.current
                val clipboard = remember(systemClipboard) {
                    object : Clipboard by systemClipboard {
                        override suspend fun setClipEntry(clipEntry: ClipEntry?) { copied = clipEntry.text() }
                    }
                }
                CompositionLocalProvider(LocalClipboard provides clipboard) { DebugTools(session) }
            }
            settle()
            onNodeWithText("DEBUG · F3").performClick()
            settle()
            clickPanel("Начать запись")
            runOnIdle {
                assertTrue(session.recording)
                session.frame(1L, 16_666_667L)
                session.phase(DiagnosticPhase.Simulation, 2_000_000L)
                session.event("player.action", "Dash")
                session.frame(16_666_668L, 16_666_667L)
            }
            clickPanel("Скрыть")
            runOnIdle {
                assertTrue(session.recording)
                assertFalse(session.panelVisible)
                session.frame(33_333_335L, 16_666_667L)
            }
            onNodeWithText("● REC · DEBUG").performClick()
            settle()
            clickPanel("Стоп + копия")
            val report = runOnIdle {
                assertFalse(session.recording)
                assertEquals(2L, session.recordedFrames)
                assertNotNull(copied)
            }
            assertContains(report, "KINETICKK_DEBUG_DIAGNOSTICS_V1")
            assertContains(report, "player.action\tDash")
            assertContains(report, "frames_total=2")
            runOnIdle {
                session.event("player.action", "after-stop")
                session.frame(50_000_002L, 16_666_667L)
            }
            clickPanel("Копировать")
            assertEquals(report, copied, "A stopped recording remains frozen while live diagnostics continue")
            clickPanel("Очистить")
            runOnIdle { assertFalse(session.hasRecording) }
            onNodeWithText("Копировать").assertIsNotEnabled()
        }

    @Test
    fun aDelayedClipboardDenialKeepsTheSessionFrozenAndOffersManualCopy() =
        runSkikoComposeUiTest(Size(420f, 900f), Density(1f)) {
            mainClock.autoAdvance = false
            val session = DebugSession()
            val clipboardDecision = CompletableDeferred<Unit>()
            var attempts = 0
            setContent {
                val systemClipboard = LocalClipboard.current
                val clipboard = remember(systemClipboard) {
                    object : Clipboard by systemClipboard {
                        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                            attempts++
                            clipboardDecision.await()
                            error("Clipboard denied asynchronously")
                        }
                    }
                }
                CompositionLocalProvider(LocalClipboard provides clipboard) { DebugTools(session) }
            }
            settle()
            onNodeWithText("DEBUG · F3").performClick()
            settle()
            clickPanel("Начать запись")
            runOnIdle { session.event("warning", "reproducible warning") }
            clickPanel("Стоп + копия")
            runOnIdle {
                assertFalse(session.recording)
                assertTrue(session.hasRecording)
                assertEquals(1, attempts)
            }
            onNodeWithTag("kinetickk.debug.copy").assertIsNotEnabled()
            onNodeWithTag("kinetickk.debug.startStop").assertIsNotEnabled()
            onNodeWithText("Очистить").assertIsNotEnabled()
            onNodeWithText("Скопировано в буфер обмена.").assertDoesNotExist()
            runOnIdle { clipboardDecision.complete(Unit) }
            settle()
            onNodeWithText("Буфер недоступен; откройте текст отчёта и скопируйте вручную.").assertExists()
            clickPanel("Текст отчёта")
            onNodeWithContentDescription("Диагностический отчёт для копирования").assertExists()
            assertContains(session.export(), "reproducible warning")
        }

    @Test
    fun largeReportFallbackRendersBoundedPagesAndCanCopyAnIdentifiedFragment() =
        runSkikoComposeUiTest(Size(420f, 900f), Density(1f)) {
            mainClock.autoAdvance = false
            val session = DebugSession().apply {
                start()
                repeat(180) { event("warning", "event-$it: " + "x".repeat(640)) }
                stop()
                panelVisible = true
            }
            val report = session.export()
            assertTrue(report.length > 100_000)
            var denyClipboard = true
            var copied: String? = null
            setContent {
                val systemClipboard = LocalClipboard.current
                val clipboard = remember(systemClipboard) {
                    object : Clipboard by systemClipboard {
                        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                            if (denyClipboard) error("Clipboard denied")
                            copied = clipEntry.text()
                        }
                    }
                }
                CompositionLocalProvider(LocalClipboard provides clipboard) { DebugTools(session) }
            }
            settle()
            clickPanel("Копировать")
            onNodeWithText("Буфер недоступен; откройте текст отчёта и скопируйте вручную.").assertExists()
            clickPanel("Текст отчёта")
            onNodeWithTag("kinetickk.debug.previousPage").assertIsNotEnabled()
            val renderedPages = mutableListOf<String>()
            while (true) {
                assertTrue(renderedPages.size < 64, "Report navigation must reach its last page")
                val text = onNodeWithContentDescription("Диагностический отчёт для копирования")
                    .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
                assertTrue(text.length in 1..8_192, "Text layout must only receive the current bounded page")
                renderedPages += text
                val next = onNodeWithTag("kinetickk.debug.nextPage")
                if (next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) break
                scrollIntoView(next).performClick()
                settle()
            }
            assertTrue(renderedPages.size > 2)
            assertEquals(report, renderedPages.joinToString(""), "Every exported character must remain reachable")
            onNodeWithText("Страница ${renderedPages.size}/${renderedPages.size} · часть полного отчёта").assertExists()
            runOnIdle { denyClipboard = false }
            scrollIntoView(onNodeWithTag("kinetickk.debug.copyPage")).performClick()
            settle()
            assertEquals(
                "KINETICKK_DEBUG_DIAGNOSTICS_V1\nREPORT_FRAGMENT ${renderedPages.size}/${renderedPages.size}\n" + renderedPages.last(),
                copied,
            )
            scrollIntoView(onNodeWithTag("kinetickk.debug.previousPage")).performClick()
            settle()
            val previousPage = onNodeWithContentDescription("Диагностический отчёт для копирования")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
            assertEquals(renderedPages[renderedPages.lastIndex - 1], previousPage)
        }

    private fun ClipEntry?.text(): String =
        assertNotNull(this).asAwtTransferable!!.getTransferData(DataFlavor.stringFlavor) as String

    private fun ComposeUiTest.clickPanel(label: String) {
        scrollIntoView(onNodeWithText(label)).performClick()
        settle()
    }

    private fun ComposeUiTest.scrollIntoView(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        // ScrollBy is asynchronous in Compose 1.12. performScrollTo retries until geometry moves;
        // keeping the test clock paused here queues unbounded scroll coroutines instead of scrolling.
        val previousAutoAdvance = mainClock.autoAdvance
        try {
            mainClock.autoAdvance = true
            return node.performScrollTo()
        } finally {
            mainClock.autoAdvance = previousAutoAdvance
        }
    }

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeByFrame()
        waitForIdle()
    }
}
