// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
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
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.floor
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises the production catalog, feature composition, persistence and local command completion. */
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

    @Test
    fun portraitVolumeControlsRemainUsableAtBothTextScales() = exerciseSettingsNavigation(390, 720, onlySettings = true)

    private fun exerciseSettingsNavigation(width: Int, height: Int, onlySettings: Boolean = false) {
        enableKinetickkComposeRuntimeOptimizations()
        runComposeUiTest {
            // Home and Armory animate continuously; advance only the frames needed by each action.
            mainClock.autoAdvance = false
            val catalog = createContentCatalog()
            val persistence = InMemorySettingsPersistence()
            val profile = createProfileComponent(persistence, catalog.profilePolicy())
            val audio = SettingsSilentAudio()
            val gameplay = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, audio)
            val owner = AppCompositionOwner(
                contentCatalog = catalog,
                profileComponent = profile,
                audioService = audio,
                gameplayComponent = gameplay,
            )
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
                    System.getenv("KINETICKK_RENDER_CAPTURE_DIR")?.let { directory ->
                        val phase = gameplay.activeRun()?.query(GameplayQuery.GetRunStatus)?.phase
                        val output = File(directory, "app-${width}x${height}-${expected}-${phase ?: "idle"}.png")
                        output.parentFile.mkdirs()
                        Image.makeFromBitmap(rendered.asSkiaBitmap()).use { image ->
                            image.encodeToData()!!.use { output.writeBytes(it.bytes) }
                        }
                    }
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
                val panelHeight = min(468f, height - 30f)
                val right = (width + panelWidth) * 0.5f
                val bottom = (height + panelHeight) * 0.5f
                val startY = (height - panelHeight) * 0.5f + 116f
                val rowsPerPage = floor((panelHeight - 180f) / 32f).toInt().coerceAtLeast(1)
                fun selectGroup(group: String) {
                    val before = profile.query(ProfileQuery.GetPreferences).preferences
                    onNodeWithTag("kinetickk.settings.group.$group").performClick()
                    render(AppDestination.Settings)
                    onNodeWithTag("kinetickk.settings.group.$group").assertIsSelected()
                    assertEquals(before, profile.query(ProfileQuery.GetPreferences).preferences)
                    if (group != "game") onNodeWithTag("kinetickk.settings.language.en").assertDoesNotExist()
                }
                fun captureSettings(group: String) {
                    val preferences = profile.query(ProfileQuery.GetPreferences).preferences
                    val output = File("build/reports/settings-screenshots/${width}x${height}-$group-${preferences.textScale}-${preferences.language.code}.png")
                    output.parentFile.mkdirs()
                    Image.makeFromBitmap(onNodeWithTag(APP_TAG).captureToImage().asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { output.writeBytes(it.bytes) }
                    }
                }
                fun adjust(row: Int, increase: Boolean, rowCount: Int) {
                    val before = profile.query(ProfileQuery.GetPreferences).preferences
                    val pageStart = (row / rowsPerPage) * rowsPerPage
                    val visibleCount = min(rowsPerPage, rowCount - pageStart)
                    val spacing = min(48f, (panelHeight - 180f) / visibleCount)
                    tap(right - if (increase) 41f else 169f, startY + spacing * (row % rowsPerPage + 0.5f))
                    assertNotEquals(before, profile.query(ProfileQuery.GetPreferences).preferences, "Settings row $row did not change")
                }
                fun exerciseVolume() {
                    val input = onNodeWithTag("kinetickk.settings.volume.input")
                    val slider = onNodeWithTag("kinetickk.settings.volume.slider")
                    val initial = profile.query(ProfileQuery.GetPreferences).preferences
                    fun assertVolume(percent: Int) {
                        render(AppDestination.Settings)
                        assertEquals(initial.copy(masterVolume = percent / 100f), profile.query(ProfileQuery.GetPreferences).preferences)
                        assertEquals(percent / 100f, audio.preferences?.masterVolume)
                        input.assertTextEquals(percent.toString())
                        slider.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(percent.toFloat(), 0f..100f, 99))
                    }
                    input.performTextReplacement("37")
                    assertVolume(37)
                    for (letter in listOf(Key.M, Key.S, Key.L, Key.A, Key.C)) {
                        input.performKeyInput { pressKey(letter) }
                        assertVolume(37)
                    }
                    for (invalid in listOf("101", "-1", "abc", "1.5", "9999")) {
                        input.performTextReplacement(invalid)
                        assertVolume(37)
                    }
                    input.performTextClearance()
                    input.performTextInput("100")
                    assertVolume(100)
                    // This key must reach the editor before the session's Armory shortcut.
                    val selectAllModifier = if (System.getProperty("os.name").startsWith("Mac")) Key.MetaLeft else Key.CtrlLeft
                    input.performKeyInput { keyDown(selectAllModifier); pressKey(Key.A); keyUp(selectAllModifier) }
                    render(AppDestination.Settings)
                    assertEquals(androidx.compose.ui.text.TextRange(0, 3), input.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange])
                    input.performTextInput("42")
                    assertVolume(42)
                    key(Key.Enter, AppDestination.Settings)
                    input.assertIsNotFocused()
                    key(Key.Escape, owner.sessionPort.query(AppSessionQuery.GetShell).base)
                    key(Key.S, AppDestination.Settings)
                    selectGroup("sound")
                    assertVolume(42)
                    input.performTextClearance()
                    slider.performTouchInput { click(center) }
                    assertVolume(50)
                    slider.performTouchInput { swipe(center, centerRight) }
                    assertVolume(100)
                    slider.performTouchInput { swipe(centerRight, centerLeft) }
                    assertVolume(0)
                    slider.assertIsFocused()
                    key(Key.DirectionRight, AppDestination.Settings)
                    assertVolume(1)
                    key(Key.MoveEnd, AppDestination.Settings)
                    assertVolume(100)
                    key(Key.MoveHome, AppDestination.Settings)
                    assertVolume(0)
                    slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(61f)) }
                    assertVolume(61)
                    selectGroup("game")
                    selectGroup("sound")
                    assertVolume(61)
                    val reloaded = createProfileComponent(persistence, catalog.profilePolicy())
                    assertEquals(0.61f, reloaded.query(ProfileQuery.GetPreferences).preferences.masterVolume)
                    captureSettings("sound-volume")
                }
                fun sweepSettings() {
                    onNodeWithTag("kinetickk.settings.language.en").performClick()
                    render(AppDestination.Settings)
                    assertEquals(AppLanguage.English, profile.query(ProfileQuery.GetPreferences).preferences.language)
                    onNodeWithTag("kinetickk.settings.group.game").assertContentDescriptionEquals("Game")
                    onNodeWithTag("kinetickk.settings.group.sound").assertContentDescriptionEquals("Sound")
                    onNodeWithTag("kinetickk.settings.group.graphics").assertContentDescriptionEquals("Graphics")
                    onNodeWithTag("kinetickk.settings.group.interface").assertContentDescriptionEquals("Interface")
                    captureSettings("game")
                    onNodeWithTag("kinetickk.settings.language.ru").performClick()
                    render(AppDestination.Settings)
                    assertEquals(AppLanguage.Russian, profile.query(ProfileQuery.GetPreferences).preferences.language)
                    for ((group, count) in listOf("game" to 2, "sound" to 3, "graphics" to 6, "interface" to 2)) {
                        selectGroup(group)
                        captureSettings(group)
                        val maxPage = (count - 1) / rowsPerPage
                        for (page in 0..maxPage) {
                            for (row in maxOf(if (group == "game") 1 else 0, page * rowsPerPage)..minOf(count - 1, (page + 1) * rowsPerPage - 1)) {
                                if (group == "sound" && row == 2) {
                                    exerciseVolume()
                                } else {
                                    adjust(row, increase = true, rowCount = count)
                                    adjust(row, increase = false, rowCount = count)
                                }
                            }
                            if (page < maxPage) tap(right - 40f, bottom - 25f)
                        }
                    }
                    selectGroup("game")
                    onNodeWithTag("kinetickk.settings.language.en").assertExists()
                    val soundTab = onNodeWithTag("kinetickk.settings.group.sound")
                    soundTab.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
                    key(Key.Enter, AppDestination.Settings)
                    soundTab.assertIsSelected()
                    selectGroup("game")
                }

                render(AppDestination.Home)
                key(Key.S, AppDestination.Settings)
                sweepSettings()
                tap(right - panelWidth + 40f, bottom - 25f, AppDestination.Home)
                key(Key.S, AppDestination.Settings)
                selectGroup("interface")
                // Exercise every accepted text size up to the UI's maximum through its control.
                repeat(50) {
                    if (profile.query(ProfileQuery.GetPreferences).preferences.textScale < 1.75f) {
                        adjust(row = 0, increase = true, rowCount = 2)
                    }
                }
                assertEquals(1.75f, profile.query(ProfileQuery.GetPreferences).preferences.textScale)
                for (group in listOf("game", "sound", "graphics", "interface")) {
                    selectGroup(group)
                    if (group == "sound") exerciseVolume()
                    captureSettings(group)
                }
                tap(right - panelWidth + 40f, bottom - 25f, AppDestination.Home)

                if (onlySettings) return@runComposeUiTest
                fun scrollProfileToEnd(tag: String) {
                    repeat(3) { mainClock.advanceTimeByFrame() }
                    onNodeWithTag(tag).performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, 10_000f) }
                    mainClock.advanceTimeBy(1_000)
                    waitForIdle()
                }
                for ((shortcut, destination) in listOf(Key.L to AppDestination.Lab, Key.A to AppDestination.Armory, Key.B to AppDestination.Rebirth)) {
                    key(shortcut, destination)
                    when (destination) {
                        AppDestination.Lab -> {
                            scrollProfileToEnd("profile-lab-scroll")
                            onNodeWithTag("profile-lab-buy-${kinetickk.ball.content.api.MetaUpgradeId.entries.last()}").assertIsDisplayed()
                        }
                        AppDestination.Rebirth -> {
                            scrollProfileToEnd("profile-rebirth-scroll")
                            onNodeWithTag("profile-rebirth-advance").assertIsDisplayed()
                        }
                        AppDestination.Armory -> {
                            repeat(3) { onNodeWithTag("profile-armory-next").performClick() }
                            scrollProfileToEnd("profile-armory-scroll")
                            render(AppDestination.Armory)
                            onNodeWithTag("profile-armory-equip-${catalog.uiCatalog().weapons.last().id}").assertIsDisplayed()
                            onNodeWithTag("profile-armory-next").assertIsNotEnabled()
                        }
                        else -> Unit
                    }
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
                selectGroup("interface")
                adjust(row = 0, increase = false, rowCount = 2)
                selectGroup("graphics")
                adjust(row = 2, increase = true, rowCount = 6)
                adjust(row = 3, increase = true, rowCount = 6)
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
    var preferences: AudioPreferences? = null
    override fun updatePreferences(preferences: AudioPreferences) { this.preferences = preferences }
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
