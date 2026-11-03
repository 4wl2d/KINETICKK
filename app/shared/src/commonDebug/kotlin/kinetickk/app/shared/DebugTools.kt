// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kinetickk.app.shared.diagnostics.DebugRuntimeSample
import kinetickk.app.shared.diagnostics.DebugReportPages
import kinetickk.app.shared.diagnostics.DebugSession
import kinetickk.app.shared.diagnostics.EXPORT_MARKER
import kinetickk.app.shared.diagnostics.createDebugClipEntry
import kinetickk.app.shared.diagnostics.decimal
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

fun createDebugDiagnostics(downstream: CrashDiagnostics): CrashDiagnostics =
    if (downstream is DebugSession) downstream else DebugSession(downstream).also { session ->
        session.context("build") { DebugBuildIdentity.description }
    }

@Composable
internal fun rememberDebugDiagnostics(downstream: CrashDiagnostics): CrashDiagnostics =
    remember(downstream) { createDebugDiagnostics(downstream) }

private val DebugBackground = Color(0xff14181e)
private val DebugForeground = Color(0xfff2f5ee)
private val DebugAccent = Color(0xffc5f766)
private val DebugTextStyle = TextStyle(color = DebugForeground, fontSize = 12.sp, fontFamily = FontFamily.Monospace)

/** Only Debug adds an overlay layout node to the application. */
@Composable
internal fun DebugToolsHost(
    diagnostics: CrashDiagnostics,
    displayHost: PlatformDisplayHost,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        DebugTools(diagnostics, displayHost)
    }
}

/** Separate composition scope: the game never observes the panel's 2Hz presentation state. */
@Composable
internal fun DebugTools(diagnostics: CrashDiagnostics, displayHost: PlatformDisplayHost = PlatformDisplayHost.None) {
    val session = diagnostics as? DebugSession ?: return
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var snapshotValue by remember(session) { mutableStateOf(session.snapshot()) }
    var runtimeValue by remember(session) { mutableStateOf(DebugRuntimeSample()) }
    var statusValue by remember { mutableStateOf("") }
    var exportedValue by remember { mutableStateOf("") }
    var showReportValue by remember { mutableStateOf(false) }
    var reportPageValue by remember { mutableStateOf(0) }
    var copyingValue by remember { mutableStateOf(false) }
    val reportPages = remember(exportedValue) { DebugReportPages(exportedValue) }
    val copyText = { text: String ->
        if (!copyingValue) {
            copyingValue = true
            statusValue = "Копирование…"
            // Start inside the click gesture, preserving browser transient user activation.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    clipboard.setClipEntry(createDebugClipEntry(text))
                    statusValue = "Скопировано в буфер обмена."
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    statusValue = "Буфер недоступен; откройте текст отчёта и скопируйте вручную."
                } finally {
                    copyingValue = false
                }
            }
        }
    }
    val visible = session.panelVisible
    val recording = session.recording
    LaunchedEffect(session, visible, recording, displayHost) {
        var ticks = 0
        while (visible || recording) {
            val refreshHz = runCatching { displayHost.diagnosticRefreshHz() }.getOrDefault(0)
            session.nominalFrameBudgetNanos = if (refreshHz > 0) 1_000_000_000L / refreshHz else 0L
            session.context("display.refresh") { "observed_hz=$refreshHz; 0=unknown_or_uncapped" }
            if (visible) snapshotValue = session.snapshot()
            if (ticks % 4 == 0) runtimeValue = session.sampleRuntime()
            ticks++
            delay(500)
        }
    }
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp),
        contentAlignment = if (visible) Alignment.BottomEnd else Alignment.BottomCenter,
    ) {
        if (!visible) {
            DebugButton(if (recording) "● REC · DEBUG" else "DEBUG · F3", tag = "kinetickk.debug.toggle") { session.panelVisible = true }
        } else {
            Column(
                Modifier.widthIn(max = 370.dp).fillMaxWidth().heightIn(max = 620.dp)
                    .background(DebugBackground).border(1.dp, DebugAccent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DebugText(if (recording) "DEBUG · ЗАПИСЬ" else "DEBUG · ПРОФИЛИРОВАНИЕ")
                    DebugButton("Скрыть", tag = "kinetickk.debug.hide") { session.panelVisible = false }
                }
                DebugText("FPS ${decimal(snapshotValue.fps)} · кадр ${decimal(snapshotValue.meanMillis)} мс")
                DebugText("p95 ${decimal(snapshotValue.p95Millis)} · p99 ${decimal(snapshotValue.p99Millis)} · max ${decimal(snapshotValue.maxMillis)} мс")
                DebugText("Просадки: ${snapshotValue.jankFrames}/${snapshotValue.budgetedFrames} · пропущено ≈${snapshotValue.missedIntervals}")
                if (snapshotValue.budgetedFrames == 0) DebugText("Целевой FPS не задан: оценка просадок недоступна.")
                DebugText("CPU: dispatch ${decimal(snapshotValue.dispatchMillis)} мс\nсимуляция ${decimal(snapshotValue.simulationMillis)} мс (внутри dispatch)\nпроекция ${decimal(snapshotValue.projectionMillis)} · аудио ${decimal(snapshotValue.audioMillis)} мс\nCanvas ${decimal(snapshotValue.drawMillis)} мс (без GPU)")
                DebugText("Объекты: враги ${snapshotValue.enemies}, снаряды ${snapshotValue.projectiles}, предметы ${snapshotValue.pickups}, эффекты ${snapshotValue.effects}")
                DebugText("Heap ${runtimeValue.heapUsedBytes?.let { decimal(it / 1_048_576.0) + " MiB" } ?: "недоступен"} · GC ${runtimeValue.gcCount ?: "н/д"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugButton(if (recording) "Стоп" else "Начать запись", enabled = !copyingValue, tag = "kinetickk.debug.startStop") {
                        if (session.recording) session.stop() else session.start()
                        exportedValue = ""
                        reportPageValue = 0
                        showReportValue = false
                        statusValue = if (session.recording) "Запись идёт; панель можно скрыть." else "Сессия остановлена."
                    }
                    DebugButton(if (copyingValue) "Копирование…" else if (recording) "Стоп + копия" else "Копировать", enabled = session.hasRecording && !copyingValue, tag = "kinetickk.debug.copy") {
                        session.stop()
                        exportedValue = session.export()
                        reportPageValue = 0
                        copyText(exportedValue)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugButton("Текст отчёта", enabled = session.hasRecording && !recording, tag = "kinetickk.debug.report") {
                        if (!showReportValue) {
                            exportedValue = session.export()
                            reportPageValue = 0
                        }
                        showReportValue = !showReportValue
                    }
                    DebugButton("Очистить", enabled = session.hasRecording && !recording && !copyingValue) {
                        session.clear()
                        exportedValue = ""
                        reportPageValue = 0
                        showReportValue = false
                        statusValue = "Запись очищена."
                    }
                }
                DebugText("В записи ${session.recordedFrames} кадров. Перезаписано: ${session.overwrittenFrames} кадров, ${session.overwrittenEvents} событий.")
                if (statusValue.isNotEmpty()) DebugText(statusValue)
                DebugText("Метрики: последние 600 интервалов, обновление 2 раза/с. Запись: до 12 000 кадров и 2 048 событий; при заполнении остаётся хвост. CPU/GC снимаются раз в 2 с. Скрывайте панель во время записи.")
                DebugText("Интервал callback не равен времени GPU. Причины в отчёте — гипотезы; события, фазы и нагрузка помогают их проверить.")
                if (showReportValue) {
                    val page = reportPageValue.coerceIn(0, reportPages.count - 1)
                    DebugText("Страница ${page + 1}/${reportPages.count} · часть полного отчёта")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DebugButton("Назад", enabled = page > 0, tag = "kinetickk.debug.previousPage") { reportPageValue = page - 1 }
                        DebugButton("Далее", enabled = page + 1 < reportPages.count, tag = "kinetickk.debug.nextPage") { reportPageValue = page + 1 }
                    }
                    DebugButton("Копировать часть", enabled = !copyingValue, tag = "kinetickk.debug.copyPage") {
                        copyText("$EXPORT_MARKER\nREPORT_FRAGMENT ${page + 1}/${reportPages.count}\n" + reportPages.page(page))
                    }
                    BasicTextField(
                        value = reportPages.page(page),
                        onValueChange = {},
                        readOnly = true,
                        textStyle = DebugTextStyle,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                            .border(1.dp, DebugForeground).padding(8.dp)
                            .semantics { contentDescription = "Диагностический отчёт для копирования" },
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugText(text: String) {
    BasicText(text, style = DebugTextStyle)
}

@Composable
private fun DebugButton(label: String, enabled: Boolean = true, tag: String = label, onClick: () -> Unit) {
    BasicText(
        label,
        style = DebugTextStyle.copy(color = if (enabled) DebugAccent else Color.Gray),
        modifier = Modifier.testTag(tag).defaultMinSize(minHeight = 48.dp)
            .background(DebugBackground).border(1.dp, if (enabled) DebugAccent else Color.Gray)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    )
}
