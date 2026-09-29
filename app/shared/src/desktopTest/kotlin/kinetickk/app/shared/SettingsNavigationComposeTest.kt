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
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.profile.api.ColorVision
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
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.skia.Image
import kinetickk.foundation.dispatch.call
import java.io.File
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

    @Test
    fun anOpenExplanationTakesPressesOverTheControlsItCoversOnPhones() {
        // Portrait: the Color vision slip lies over its own cells; landscape: over Screen shake.
        exerciseExplanationSlip(390, 844, coveredControl = "kinetickk.settings.colorvision.")
        exerciseExplanationSlip(844, 390, coveredControl = "kinetickk.settings.screen_shake.toggle")
    }

    /**
     * Opens the Color vision (!) with a tap and presses the open slip at its center and over every
     * control it covers: each press closes the slip and changes no preference, while the same
     * point reaches the control once the slip is closed.
     */
    private fun exerciseExplanationSlip(width: Int, height: Int, coveredControl: String) {
        enableKinetickkComposeRuntimeOptimizations()
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            mainClock.autoAdvance = false
            val catalog = createContentCatalog()
            val profile = createProfileComponent(InMemorySettingsPersistence(), catalog.profilePolicy())
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
                fun settle() {
                    mainClock.advanceTimeBy(260)
                    waitForIdle()
                }
                fun preferences() = profile.query(ProfileQuery.GetPreferences).preferences
                // Touch taps, as on a phone: no mouse pointer rests on the (!) to hold its slip open.
                fun click(tag: String) {
                    onNodeWithTag(tag).performTouchInput { click() }
                    settle()
                }
                fun press(point: Offset) {
                    onRoot().performTouchInput { click(point) }
                    settle()
                }
                val info = "kinetickk.settings.colorvision.info"
                val slip = "kinetickk.settings.colorvision.slip"
                fun openSlip() {
                    if (onAllNodesWithTag(slip).fetchSemanticsNodes().isEmpty()) click(info)
                    onNodeWithTag(slip).assertExists()
                }
                settle()
                onRoot().performKeyInput { pressKey(Key.S) }
                settle()
                assertEquals(AppDestination.Settings, owner.sessionPort.query(AppSessionQuery.GetShell).active)
                for (language in listOf("ru", "en")) {
                    click("kinetickk.settings.group.game")
                    click("kinetickk.settings.language.$language")
                    click("kinetickk.settings.group.graphics")
                    val where = "$width x $height $language"
                    openSlip()
                    val bounds = onNodeWithTag(slip).fetchSemanticsNode().boundsInRoot
                    val coveredNodes = onAllNodes(SemanticsMatcher("a Settings control under the slip") { node ->
                        val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: return@SemanticsMatcher false
                        tag.startsWith("kinetickk.settings.") && tag != slip && node.boundsInRoot.overlaps(bounds)
                    }).fetchSemanticsNodes()
                    val covered = coveredNodes.map { it.config[SemanticsProperties.TestTag] to it.boundsInRoot.intersect(bounds) }
                    assertTrue(covered.any { (tag, _) -> tag.startsWith(coveredControl) }, "$where slip covers ${covered.map { it.first }}")
                    val initial = preferences()
                    for (point in listOf(bounds.center) + covered.map { it.second.center }) {
                        openSlip()
                        press(point)
                        assertEquals(initial, preferences(), "$where press at $point")
                        onNodeWithTag(slip).assertDoesNotExist()
                    }
                    // Without the slip the same point reaches the covered control (a choice not yet made).
                    val (probe, area) = covered.filterIndexed { index, (tag, _) ->
                        tag.startsWith(coveredControl) && coveredNodes[index].config.getOrNull(SemanticsProperties.Selected) != true
                    }.first()
                    press(area.center)
                    assertNotEquals(initial, preferences(), "$where $probe is live under the slip")
                }
            } finally {
                owner.close()
            }
        }
    }

    private fun exerciseSettingsNavigation(width: Int, height: Int, onlySettings: Boolean = false) {
        enableKinetickkComposeRuntimeOptimizations()
        // Walks every screen and page; slow CI runners need far longer than the 60 s default.
        runComposeUiTest(testTimeout = 10.minutes) {
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
                    mainClock.advanceTimeBy(260)
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
                fun exists(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
                fun preferences() = profile.query(ProfileQuery.GetPreferences).preferences
                fun click(tag: String, expected: AppDestination = AppDestination.Settings) {
                    // A real pointer press at the control's center; the Settings canvas resolves it.
                    onNodeWithTag(tag).performClick()
                    render(expected)
                }
                fun changes(tag: String) {
                    val before = preferences()
                    click(tag)
                    assertNotEquals(before, preferences(), "Settings control $tag did not change")
                }
                fun selectGroup(group: String) {
                    val before = preferences()
                    click("kinetickk.settings.group.$group")
                    onNodeWithTag("kinetickk.settings.group.$group").assertIsSelected()
                    assertEquals(before, preferences())
                    if (group != "game") onNodeWithTag("kinetickk.settings.language.en").assertDoesNotExist()
                }
                fun pageCount(): Int = generateSequence(0) { it + 1 }.takeWhile { exists("kinetickk.settings.page.$it") }.count()
                fun showPageWith(tag: String) {
                    if (exists(tag)) return
                    for (page in 0 until pageCount()) {
                        click("kinetickk.settings.page.$page")
                        if (exists(tag)) return
                    }
                    error("$tag is on no Settings page")
                }
                fun captureSettings(group: String) {
                    val preferences = preferences()
                    val output = File("build/reports/settings-screenshots/${width}x${height}-$group-${preferences.textScale}-${preferences.language.code}.png")
                    output.parentFile.mkdirs()
                    Image.makeFromBitmap(onNodeWithTag(APP_TAG).captureToImage().asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { output.writeBytes(it.bytes) }
                    }
                }
                fun exerciseVolume() {
                    val input = onNodeWithTag("kinetickk.settings.volume.input")
                    val slider = onNodeWithTag("kinetickk.settings.volume.slider")
                    val initial = preferences()
                    fun assertVolume(percent: Int) {
                        render(AppDestination.Settings)
                        assertEquals(initial.copy(masterVolume = percent / 100f), preferences())
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
                    // The -/+ steppers beside the slider move the volume by one percent.
                    click("kinetickk.settings.master_volume.increase")
                    assertVolume(43)
                    click("kinetickk.settings.master_volume.decrease")
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
                fun exerciseRow(row: SettingsRowTags) {
                    val prefix = "kinetickk.settings.${row.id}"
                    when {
                        row.options.isNotEmpty() -> {
                            val original = row.options.first { onNodeWithTag("$prefix.$it").fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Selected) { false } }
                            for (option in row.options - original) {
                                changes("$prefix.$option")
                                onNodeWithTag("$prefix.$option").assertIsSelected()
                                onNodeWithTag("$prefix.$original").assertIsNotSelected()
                                if (row.id == "colorvision") {
                                    // The choice is persisted through the strict codec: non-default ids only.
                                    assertEquals(ColorVision.valueOf(option.uppercase()), preferences().colorVision)
                                    assertTrue(persistence.payload!!.contains("\"colorVisionId\":\"${option.uppercase()}\""))
                                }
                                // Choosing the selected option again is inert.
                                val same = preferences()
                                click("$prefix.$option")
                                assertEquals(same, preferences())
                            }
                            changes("$prefix.$original")
                            if (row.id == "colorvision") assertTrue(!persistence.payload!!.contains("colorVisionId"))
                        }
                        row.toggle -> {
                            changes("$prefix.toggle")
                            changes("$prefix.toggle")
                        }
                        row.stepper -> {
                            changes("$prefix.increase")
                            changes("$prefix.decrease")
                        }
                    }
                    // The (!) explanation opens and closes without touching the preference.
                    val before = preferences()
                    click("$prefix.info")
                    click("$prefix.info")
                    assertEquals(before, preferences())
                }
                fun sweepSettings() {
                    onNodeWithTag("kinetickk.settings.language.en").performClick()
                    render(AppDestination.Settings)
                    assertEquals(AppLanguage.English, preferences().language)
                    onNodeWithTag("kinetickk.settings.group.game").assertContentDescriptionEquals("Game")
                    onNodeWithTag("kinetickk.settings.group.sound").assertContentDescriptionEquals("Sound")
                    onNodeWithTag("kinetickk.settings.group.graphics").assertContentDescriptionEquals("Graphics")
                    onNodeWithTag("kinetickk.settings.group.interface").assertContentDescriptionEquals("Interface")
                    captureSettings("game")
                    onNodeWithTag("kinetickk.settings.language.ru").performClick()
                    render(AppDestination.Settings)
                    assertEquals(AppLanguage.Russian, preferences().language)
                    for ((group, rows) in SETTINGS_GROUPS) {
                        selectGroup(group)
                        captureSettings(group)
                        for (row in rows) {
                            val probe = "kinetickk.settings.${row.id}.info"
                            showPageWith(probe)
                            when {
                                row.id == "language" -> Unit
                                row.id == "master_volume" -> exerciseVolume()
                                else -> exerciseRow(row)
                            }
                        }
                    }
                    selectGroup("graphics")
                    // Keyboard: a focused choice is confirmed with Enter and the session stays in Settings.
                    showPageWith("kinetickk.settings.colorvision.protan")
                    val protan = onNodeWithTag("kinetickk.settings.colorvision.protan")
                    protan.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
                    key(Key.Enter, AppDestination.Settings)
                    assertEquals(ColorVision.PROTAN, preferences().colorVision)
                    protan.assertIsSelected()
                    changes("kinetickk.settings.colorvision.default")
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
                click("kinetickk.settings.back", AppDestination.Home)
                key(Key.S, AppDestination.Settings)
                selectGroup("interface")
                // Exercise every accepted text size up to the UI's maximum through its control.
                repeat(50) {
                    if (preferences().textScale < 1.75f) {
                        showPageWith("kinetickk.settings.text_size.increase")
                        changes("kinetickk.settings.text_size.increase")
                    }
                }
                assertEquals(1.75f, preferences().textScale)
                for (group in listOf("game", "sound", "graphics", "interface")) {
                    selectGroup(group)
                    if (group == "sound") {
                        showPageWith("kinetickk.settings.volume.slider")
                        exerciseVolume()
                    }
                    captureSettings(group)
                }
                click("kinetickk.settings.back", AppDestination.Home)

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
                onNodeWithTag("codex-filter-0").performClick()
                render(AppDestination.Codex)
                onNodeWithTag("codex-empty-EMPTY_INVENTORY").assertExists()
                onNodeWithTag("codex-filter-0").performClick()
                onNodeWithTag("codex-search").performTextInput("no-such-catalog-entry")
                render(AppDestination.Codex)
                onNodeWithTag("codex-empty-EMPTY_SEARCH").assertExists()
                onNodeWithTag("codex-search").performTextClearance()
                render(AppDestination.Codex)
                val ui = catalog.uiCatalog()
                // Populate the collection through Profile's real accepted gameplay-progress path.
                val discoveryCall = kinetickk.foundation.dispatch.InlineAcceptance(kinetickk.foundation.dispatch.BoundedCompletionDeque<Boolean>(1))
                discoveryCall.dispatch {
                    discoveryCall.acceptAndDrain(rootItem = false, rootFrame = kinetickk.foundation.collections.immutableListOf(Unit),
                        outputs = { it }, acceptFrame = { _, _ -> },
                        decideCompletion = { accepted -> assertTrue(accepted); kinetickk.foundation.collections.immutableListOf<Unit>() },
                        execute = { _, _ -> discoveryCall.call(
                            invoke = { reply: kinetickk.foundation.dispatch.InlineReply<kinetickk.ball.profile.api.ProfileProgressApplied, kinetickk.ball.profile.api.ProfileRefusal> ->
                                profile.applyGameplayProgress(kinetickk.ball.profile.api.GameplayProgressUpdate(
                                    discoveredItemIds = setOf(ui.items.last().id), discoveredRelicIds = setOf(ui.relics.last().id)), reply) },
                            acceptedInput = { true }, refusedInput = { false },
                        ) })
                }
                onNodeWithTag("codex-close").performClick()
                render(AppDestination.Home)
                key(Key.C, AppDestination.Codex)
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
                    if (category == 0 || category == 2) {
                        onNodeWithTag("codex-new-$entryKey", useUnmergedTree = true).assertIsDisplayed()
                    }
                    onNodeWithTag("codex-slot-$entryKey").performClick()
                    render(AppDestination.Codex)
                    if (category == 0 || category == 2) {
                        onNodeWithTag("codex-new-$entryKey", useUnmergedTree = true).assertDoesNotExist()
                        val collection = profile.query(ProfileQuery.GetCollection).collection
                        if (category == 0) assertTrue(ui.items.last().id !in collection.newItemIds)
                        else assertTrue(ui.relics.last().id !in collection.newRelicIds)
                    }
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

                onNodeWithTag("kinetickk.home.start").performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
                key(Key.Enter, AppDestination.Gameplay)
                key(Key.P, AppDestination.Gameplay)
                assertEquals(GameplayRunPhase.PAUSED, gameplay.activeRun()?.query(GameplayQuery.GetRunStatus)?.phase)
                onNodeWithTag("kinetickk.gameplay.settings").performClick()
                render(AppDestination.Settings)
                // A paused run must receive the newly accepted preferences when Settings closes.
                selectGroup("interface")
                changes("kinetickk.settings.text_size.decrease")
                selectGroup("graphics")
                showPageWith("kinetickk.settings.particles.high")
                changes("kinetickk.settings.particles.high")
                showPageWith("kinetickk.settings.damage_numbers.toggle")
                changes("kinetickk.settings.damage_numbers.toggle")
                click("kinetickk.settings.back", AppDestination.Gameplay)
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

/** Test tags of one Settings row: segmented option ids, a toggle, or -/+ steppers. */
private class SettingsRowTags(val id: String, val options: List<String> = emptyList(), val toggle: Boolean = false, val stepper: Boolean = false)

/** The game's Settings tabs and rows in order (Color vision leads Graphics). */
private val SETTINGS_GROUPS: Map<String, List<SettingsRowTags>> = linkedMapOf(
    "game" to listOf(
        SettingsRowTags("language", listOf("ru", "en")),
        SettingsRowTags("simulation_speed", listOf("75", "100", "115", "135", "160", "200")),
    ),
    "sound" to listOf(
        SettingsRowTags("sfx", toggle = true),
        SettingsRowTags("music", toggle = true),
        SettingsRowTags("master_volume"),
    ),
    "graphics" to listOf(
        SettingsRowTags("colorvision", listOf("default", "protan", "deutan", "tritan", "mono")),
        SettingsRowTags("screen_shake", toggle = true),
        SettingsRowTags("particles", listOf("low", "normal", "high")),
        SettingsRowTags("damage_numbers", toggle = true),
        SettingsRowTags("damage_number_size", listOf("small", "normal", "large", "huge")),
        SettingsRowTags("damage_number_format", listOf("compact", "full")),
        SettingsRowTags("damage_color_thresholds", stepper = true),
    ),
    "interface" to listOf(
        SettingsRowTags("text_size", stepper = true),
        SettingsRowTags("run_statistics_side", listOf("left", "right")),
    ),
)

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
