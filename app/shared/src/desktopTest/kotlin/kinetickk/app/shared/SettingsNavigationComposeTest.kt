// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.math.floor
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises the production catalog, feature composition, persistence and module result routes. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeApi::class)
class SettingsNavigationComposeTest {
    private val previousLinkBufferFlag = ComposeRuntimeFlags.isLinkBufferComposerEnabled

    @AfterTest
    fun restoreRuntimeFlags() {
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = previousLinkBufferFlag
    }

    @Test
    fun desktopSettingsAndFeatureNavigationRemainDrawable() = exerciseSettingsNavigation(1000, 700)

    @Test
    fun landscapeSettingsPagesAndFeatureNavigationRemainDrawable() = exerciseSettingsNavigation(720, 360)

    private fun exerciseSettingsNavigation(width: Int, height: Int) {
        enableKinetickkComposeRuntimeOptimizations()
        runComposeUiTest {
            // Home and Armory animate continuously; advance only the frames needed by each action.
            mainClock.autoAdvance = false
            val catalog = createContentCatalog()
            val results = ProfileModuleResultRouter()
            val persistence = InMemorySettingsPersistence()
            val profile = createProfileComponent(persistence, catalog.profilePolicy(), results::route)
            val audio = SettingsSilentAudio()
            val gameplay = DefaultGameplayFeature(catalog.gameplayContent(), profile, audio)
            val owner = AppCompositionOwner(
                contentCatalog = catalog,
                profileComponent = profile,
                audioService = audio,
                gameplayComponent = gameplay,
            )
            results.bind(owner.sessionPort::receiveProfileModuleResult, gameplay::receiveProfileModuleResult)
            try {
                setContent {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag(APP_TAG)) {
                        owner.Content()
                    }
                }
                fun render(expected: AppDestination) {
                    repeat(3) { mainClock.advanceTimeByFrame() }
                    waitForIdle()
                    assertEquals(expected, owner.sessionPort.query(AppSessionQuery.GetShell).active)
                    val rendered = onNodeWithTag(APP_TAG).captureToImage()
                    assertTrue(rendered.width > 0 && rendered.height > 0)
                }
                fun key(key: Key, expected: AppDestination) {
                    onRoot().performKeyInput { pressKey(key) }
                    render(expected)
                }
                fun tap(x: Float, y: Float, expected: AppDestination = AppDestination.Settings) {
                    onNodeWithTag(APP_TAG).performTouchInput {
                        click(Offset(x * this.width / width, y * this.height / height))
                    }
                    render(expected)
                }
                val panelWidth = min(640f, width - 30f)
                val panelHeight = min(620f, height - 30f)
                val right = (width + panelWidth) * 0.5f
                val bottom = (height + panelHeight) * 0.5f
                val startY = (height - panelHeight) * 0.5f + 72f
                val rowsPerPage = floor((panelHeight - 136f) / 32f).toInt().coerceIn(1, 12)
                val spacing = min(48f, (panelHeight - 136f) / rowsPerPage)
                val maxPage = 11 / rowsPerPage
                fun adjust(row: Int, increase: Boolean) {
                    val before = profile.query(ProfileQuery.GetPreferences).preferences
                    tap(right - if (increase) 41f else 169f, startY + spacing * (row % rowsPerPage + 0.5f))
                    assertNotEquals(before, profile.query(ProfileQuery.GetPreferences).preferences, "Settings row $row did not change")
                }
                fun sweepSettings() {
                    onNodeWithTag("kinetickk.settings.language.en").performClick()
                    render(AppDestination.Settings)
                    assertEquals(AppLanguage.English, profile.query(ProfileQuery.GetPreferences).preferences.language)
                    onNodeWithTag("kinetickk.settings.language.ru").performClick()
                    render(AppDestination.Settings)
                    assertEquals(AppLanguage.Russian, profile.query(ProfileQuery.GetPreferences).preferences.language)
                    for (page in 0..maxPage) {
                        for (row in maxOf(1, page * rowsPerPage)..minOf(11, (page + 1) * rowsPerPage - 1)) {
                            adjust(row, increase = true)
                            adjust(row, increase = false)
                        }
                        if (page < maxPage) tap(right - 40f, bottom - 25f)
                    }
                }

                render(AppDestination.Home)
                key(Key.S, AppDestination.Settings)
                sweepSettings()
                tap(right - panelWidth + 40f, bottom - 25f, AppDestination.Home)
                key(Key.S, AppDestination.Settings)
                // Exercise every accepted text size up to the UI's maximum through its control.
                repeat(50) {
                    if (profile.query(ProfileQuery.GetPreferences).preferences.textScale < 1.75f) {
                        adjust(row = 5, increase = true)
                    }
                }
                assertEquals(1.75f, profile.query(ProfileQuery.GetPreferences).preferences.textScale)
                tap(right - panelWidth + 40f, bottom - 25f, AppDestination.Home)

                for ((shortcut, destination) in listOf(Key.L to AppDestination.Lab, Key.A to AppDestination.Armory, Key.B to AppDestination.Rebirth)) {
                    key(shortcut, destination)
                    key(Key.Escape, AppDestination.Home)
                }
                key(Key.C, AppDestination.Codex)
                onNodeWithTag("codex-tab-0").performClick()
                render(AppDestination.Codex)
                onNodeWithTag("codex-empty-NO_RUN").assertExists()
                onNodeWithTag("codex-tab-1").performClick()
                render(AppDestination.Codex)
                onNodeWithTag("codex-filter-1").performClick()
                render(AppDestination.Codex)
                onNodeWithTag("codex-empty-EMPTY_INVENTORY").assertExists()
                onNodeWithTag("codex-filter-0").performClick()
                onNodeWithTag("codex-search").performTextInput("no-such-catalog-entry")
                render(AppDestination.Codex)
                onNodeWithTag("codex-empty-EMPTY_SEARCH").assertExists()
                onNodeWithTag("codex-search").performTextClearance()
                render(AppDestination.Codex)
                val ui = catalog.uiCatalog()
                for ((category, entryKey) in listOf(
                    0 to "item/${ui.items.last().id}",
                    1 to "weapon/${ui.weapons.last().id}",
                    2 to "relic/${ui.relics.last().id}",
                    3 to "shape/${ui.coreShapes.last().id}",
                )) {
                    onNodeWithTag("codex-category-$category").performClick()
                    render(AppDestination.Codex)
                    onNodeWithTag("codex-grid").performScrollToKey(entryKey)
                    render(AppDestination.Codex)
                    onNodeWithTag("codex-slot-$entryKey").performClick()
                    render(AppDestination.Codex)
                    onNodeWithTag("codex-detail-title").assertExists()
                    if (width < 900 || height < 480) {
                        onNodeWithTag("codex-sheet-close").performClick()
                        render(AppDestination.Codex)
                    }
                }
                onNodeWithTag("codex-tab-2").performClick()
                render(AppDestination.Codex)
                onNodeWithTag("codex-close").performClick()
                render(AppDestination.Home)

                key(Key.Enter, AppDestination.Gameplay)
                key(Key.P, AppDestination.Gameplay)
                assertEquals(GameplayRunPhase.PAUSED, gameplay.activeRun()?.query(GameplayQuery.GetRunStatus)?.phase)
                onNodeWithTag("kinetickk.gameplay.settings").performClick()
                render(AppDestination.Settings)
                // A paused run must receive the newly accepted preferences when Settings closes.
                adjust(row = 5, increase = false)
                if (maxPage > 0) tap(right - 40f, bottom - 25f)
                adjust(row = 8, increase = true)
                adjust(row = 9, increase = true)
                tap(right - panelWidth + 40f, bottom - 25f, AppDestination.Gameplay)
                onNodeWithTag("kinetickk.gameplay.resume").performClick()
                render(AppDestination.Gameplay)
                assertEquals(GameplayRunPhase.RUNNING, gameplay.activeRun()?.query(GameplayQuery.GetRunStatus)?.phase)
                assertNotNull(persistence.payload)
            } finally {
                owner.close()
            }
        }
    }
}

private const val APP_TAG = "settings-navigation-app"

private class InMemorySettingsPersistence : ProfilePersistenceCapability {
    var payload: String? = null
    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(payload)
    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        this.payload = payload
        return ProfilePersistenceMutationResult.COMPLETED
    }
}

private class SettingsSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
