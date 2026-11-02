// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.LocalInterfaceTypography
import kinetickk.foundation.design.rememberInterfaceTypographyLoader
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kotlinx.coroutines.CancellationException

/** Nothing below this barrier can start a run or request an unprepared bundled resource. */
@Composable
internal fun AppStartupContent(
    resources: AppStartupResources,
    language: AppLanguage,
    diagnostics: CrashDiagnostics,
    prepareAudio: suspend () -> Unit,
    prepareGraphics: suspend () -> Unit,
    content: @Composable () -> Unit,
) {
    val ready = resources.readyValue
    if (ready != null) {
        CompositionLocalProvider(
            LocalInterfaceTypography provides ready.typography,
            LocalFontFamilyResolver provides ready.fontFamilyResolver,
            content = content,
        )
        return
    }
    val loader = rememberInterfaceTypographyLoader()
    var attemptValue by remember(resources) { mutableIntStateOf(0) }
    var progressValue by remember(resources) { mutableStateOf(StartupProgress()) }
    var failedValue by remember(resources) { mutableStateOf(false) }
    LaunchedEffect(resources, loader, attemptValue) {
        failedValue = false
        try {
            val typography = resources.prepare(
                loadFonts = { onProgress -> loader.load(onProgress) },
                resolveFonts = { loader.resolve(it) },
                prepareAudio = prepareAudio,
                prepareGraphics = prepareGraphics,
                onProgress = {
                    progressValue = it
                    // Let the loading screen draw before work on native rendering resources.
                    withFrameNanos { }
                },
                onFontProgress = { progressValue = it },
            )
            resources.publish(typography, loader.fontFamilyResolver)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnostics.event("startup.failed", "${progressValue.stage}: $error")
            failedValue = true
        }
    }
    StartupLoadingScreen(progressValue, failedValue, language, onRetry = { attemptValue++ })
}

/** Uses only system text until the game's own fonts are ready. Progress counts completed stages. */
@Composable
internal fun StartupLoadingScreen(
    progress: StartupProgress,
    failed: Boolean,
    language: AppLanguage,
    onRetry: () -> Unit,
) {
    val russian = language == AppLanguage.Russian
    val caption = if (failed) {
        if (russian) "Не удалось подготовить игру" else "Couldn't prepare the game"
    } else when (progress.stage) {
        StartupStage.Fonts -> if (russian) "Загружаем шрифты" else "Loading fonts"
        StartupStage.Audio -> if (russian) "Готовим звук" else "Preparing audio"
        StartupStage.Graphics -> if (russian) "Готовим графику" else "Preparing graphics"
    }
    val foreground = Kk.Bone
    val accent = Kk.Volt
    Box(
        Modifier.fillMaxSize().background(Kk.Ink).testTag("startup-loading"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp).widthIn(max = 420.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                "KINETICKK",
                style = TextStyle(
                    color = foreground, fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black, fontSize = 36.sp, letterSpacing = 2.sp,
                ),
            )
            Box(
                Modifier.fillMaxWidth().height(4.dp).background(foreground.copy(alpha = 0.15f))
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.fraction, 0f..1f) },
            ) {
                Box(Modifier.fillMaxWidth(progress.fraction.coerceIn(0f, 1f)).height(4.dp).background(accent))
            }
            BasicText(
                caption,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = TextStyle(color = foreground, fontSize = 16.sp, textAlign = TextAlign.Center),
            )
            if (failed) {
                BasicText(
                    if (russian) "ПОВТОРИТЬ" else "RETRY",
                    modifier = Modifier.testTag("startup-retry")
                        .clickable(role = Role.Button, onClick = onRetry).padding(16.dp),
                    style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                )
            } else {
                BasicText(
                    if (russian) "ЭТАП ${progress.stage.ordinal + 1} / 3" else "STEP ${progress.stage.ordinal + 1} / 3",
                    style = TextStyle(color = foreground.copy(alpha = 0.5f), fontSize = 12.sp, letterSpacing = 2.sp),
                )
            }
        }
    }
}
