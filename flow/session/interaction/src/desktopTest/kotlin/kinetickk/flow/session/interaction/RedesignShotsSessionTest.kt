// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.*
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.gameplay.interaction.GameplayPresentation
import kinetickk.ball.profile.api.*
import kinetickk.ball.profile.interaction.rebirth.api.*
import kinetickk.ball.profile.interaction.settings.api.*
import kinetickk.flow.session.api.*
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.flow.session.interaction.codex.impl.CodexContent
import kinetickk.flow.session.interaction.home.api.*
import kinetickk.flow.session.interaction.home.impl.DefaultHomeFeature
import kinetickk.flow.session.interaction.profile.api.ProfileUnavailableFeature
import kinetickk.flow.session.interaction.profile.impl.DefaultProfileUnavailableFeature
import kinetickk.foundation.collections.*
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.resource.audio.api.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Local visual QA harness: renders session screens to build/redesign-shots/session.
 *
 * With `KK_SHOTS=1` every state renders at every size and in both languages and is written as a PNG. Otherwise
 * the run is a smoke and nothing is written: within each test method a request renders only when its state or its
 * viewport (size and density) is new for that screen, so every state and every viewport each render at least once
 * (in the first language requested, Russian for the loops over [AppLanguage.entries]) with their interactions, and
 * a state or viewport that fails to compose, lay out or draw still fails.
 */
@OptIn(ExperimentalTestApi::class)
class RedesignShotsSessionTest {
    private val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)

    // What this method's smoke has rendered. JUnit creates an instance per test method, so a method never skips a
    // request because another method rendered the same state name at a different viewport.
    private val smokeStates = HashSet<String>()
    private val smokeViewports = HashSet<String>()
    private var rendered = 0

    @AfterTest fun everyMethodRenders() {
        assertTrue(rendered > 0, "a screenshot harness method rendered nothing")
    }

    @Test fun home() {
        val targets = listOf("start", "workshop", "rebirth", "codex", "settings")
        for ((w, h) in sizes) for (language in AppLanguage.entries) {
            for (target in targets) {
                shot("home", target, w, h, language, { ShotProfile() }) {
                    onNodeWithTag("kinetickk.home.$target").performSemanticsAction(SemanticsActions.RequestFocus)
                }
            }
            // The Workshop row's Affordable stamp (the Profile would accept a purchase now).
            shot("home", "workshop-affordable", w, h, language, { ShotProfile(workshopAffordable = true) }) {
                onNodeWithTag("kinetickk.home.workshop").performSemanticsAction(SemanticsActions.RequestFocus)
            }
            for (shape in CoreShape.entries) {
                shot("home", "form-${shape.name.lowercase()}", w, h, language, { ShotProfile(coreShape = shape) }) {
                    onNodeWithTag("kinetickk.home.start").performSemanticsAction(SemanticsActions.RequestFocus)
                }
            }
            shot("home", "form-info", w, h, language, { ShotProfile() }) {
                onNodeWithTag("kinetickk.home.start").performSemanticsAction(SemanticsActions.RequestFocus)
                mainClock.advanceTimeBy(300)
                onNodeWithTag("kinetickk.home.info.form").performSemanticsAction(SemanticsActions.OnClick)
            }
            if (w > 900) shot("home", "facts-info", w, h, language, { ShotProfile() }) {
                onNodeWithTag("kinetickk.home.workshop").performSemanticsAction(SemanticsActions.RequestFocus)
                mainClock.advanceTimeBy(300)
                onNodeWithTag("kinetickk.home.info.facts").performSemanticsAction(SemanticsActions.OnClick)
            }
        }
    }

    /** The desktop Quit button: idle, focused, and the form's (!) open beside it, with the longest form name. */
    @Test fun homeQuit() {
        for ((w, h) in listOf(1440 to 810, 1000 to 602)) for (language in AppLanguage.entries) {
            shot("home-quit", "idle", w, h, language, { ShotProfile() }, quit = true) {
                onNodeWithTag("kinetickk.home.settings").performSemanticsAction(SemanticsActions.RequestFocus)
            }
            shot("home-quit", "focused", w, h, language, { ShotProfile() }, quit = true) {
                onNodeWithTag("kinetickk.home.quit").performSemanticsAction(SemanticsActions.RequestFocus)
            }
            for (shape in CoreShape.entries) {
                shot("home-quit", "form-${shape.name.lowercase()}", w, h, language, { ShotProfile(coreShape = shape) }, quit = true) {
                    onNodeWithTag("kinetickk.home.info.form").performSemanticsAction(SemanticsActions.OnClick)
                }
            }
        }
    }

    /** Text size 175 % (the setting's maximum) and the Rebirth "Ready" and Workshop "Affordable" stamps, the widest menu rows. */
    @Test fun homeTextSizes() {
        for ((w, h) in sizes + listOf(600 to 390, 360 to 800)) for (language in AppLanguage.entries) {
            for (target in listOf("start", "workshop", "rebirth", "codex", "settings")) {
                shot("home", "$target-ready-text175", w, h, language, { ShotProfile(textScale = 1.75f, canRebirth = true, workshopAffordable = true) }) {
                    onNodeWithTag("kinetickk.home.$target").performSemanticsAction(SemanticsActions.RequestFocus)
                }
                shot("home", "$target-ready", w, h, language, { ShotProfile(canRebirth = true, workshopAffordable = true) }) {
                    onNodeWithTag("kinetickk.home.$target").performSemanticsAction(SemanticsActions.RequestFocus)
                }
            }
            shot("home", "form-info-text175", w, h, language, { ShotProfile(textScale = 1.75f) }) {
                onNodeWithTag("kinetickk.home.start").performSemanticsAction(SemanticsActions.RequestFocus)
                mainClock.advanceTimeBy(300)
                onNodeWithTag("kinetickk.home.info.form").performSemanticsAction(SemanticsActions.OnClick)
            }
        }
    }

    /** 360-px-tall landscape (1080 × 2400 phones turned sideways): the lowest rows over the version line. */
    @Test fun homeShortLandscape() {
        for (language in AppLanguage.entries) {
            for (target in listOf("start", "rebirth", "codex", "settings")) {
                for ((suffix, scale) in listOf("text100" to 1f, "text175" to 1.75f)) {
                    shot("home", "$target-ready-$suffix", 800, 360, language, { ShotProfile(textScale = scale, canRebirth = true) }) {
                        onNodeWithTag("kinetickk.home.$target").performSemanticsAction(SemanticsActions.RequestFocus)
                    }
                }
                shot("home", target, 804, 360, language, { ShotProfile() }, density = 3f) {
                    onNodeWithTag("kinetickk.home.$target").performSemanticsAction(SemanticsActions.RequestFocus)
                }
            }
        }
    }

    @Test fun homeExtraViewports() {
        for ((w, h) in listOf(390 to 600, 600 to 390, 1000 to 700, 1920 to 1080, 800 to 360)) for (language in AppLanguage.entries) {
            for (target in listOf("workshop", "codex", "settings")) shot("home", target, w, h, language, { ShotProfile() }) {
                onNodeWithTag("kinetickk.home.$target").performSemanticsAction(SemanticsActions.RequestFocus)
            }
            shot("home", "form-info", w, h, language, { ShotProfile() }) {
                onNodeWithTag("kinetickk.home.start").performSemanticsAction(SemanticsActions.RequestFocus)
                mainClock.advanceTimeBy(300)
                onNodeWithTag("kinetickk.home.info.form").performSemanticsAction(SemanticsActions.OnClick)
            }
        }
    }

    /** Fix round 3: the form name at every text size, and the two-column phone menu (Play across both columns). */
    @Test fun homeFix3() {
        for ((w, h) in listOf(1000 to 700, 1440 to 810, 1920 to 1080)) for (language in AppLanguage.entries) {
            for ((suffix, scale) in listOf("text100" to 1f, "text125" to 1.25f, "text175" to 1.75f)) {
                for (shape in listOf(CoreShape.PRISM, CoreShape.RING, CoreShape.SHARD)) {
                    shot("home", "form-${shape.name.lowercase()}-$suffix", w, h, language, { ShotProfile(coreShape = shape, textScale = scale) }) { }
                }
            }
        }
        for ((w, h) in listOf(390 to 560, 360 to 580)) for (language in AppLanguage.entries) {
            for (target in listOf("start", "workshop", "rebirth", "codex", "settings")) {
                for ((suffix, scale) in listOf("" to 1.25f, "-text175" to 1.75f)) {
                    shot("home", "two-column-$target$suffix", w, h, language, { ShotProfile(textScale = scale, canRebirth = true) }) {
                        onNodeWithTag("kinetickk.home.$target").performSemanticsAction(SemanticsActions.RequestFocus)
                    }
                }
            }
        }
    }

    /** Fix round 3, second pass: the two-column phone menu down to 320 px wide. */
    @Test fun homeTwoColumnNarrow() {
        for ((w, h) in listOf(320 to 568, 328 to 568, 332 to 568, 360 to 580, 390 to 590)) for (language in AppLanguage.entries) {
            for (target in listOf("start", "workshop", "rebirth", "codex", "settings")) {
                for ((suffix, scale) in listOf("" to 1.25f, "-text175" to 1.75f)) {
                    shot("home", "two-column-$target$suffix", w, h, language, { ShotProfile(textScale = scale, canRebirth = true) }) {
                        onNodeWithTag("kinetickk.home.$target").performSemanticsAction(SemanticsActions.RequestFocus)
                    }
                }
            }
        }
    }

    @Test fun codex() {
        for ((w, h) in sizes) for (language in AppLanguage.entries) {
            shotCodex("default", w, h, language) { }
            shotCodex("filtered", w, h, language) {
                onNodeWithTag("codex-category-2").performClick()
            }
            shotCodex("searched", w, h, language) {
                onNodeWithTag("codex-search").performTextInput(if (language == AppLanguage.English) "ram" else "таран")
            }
            shotCodex("selected", w, h, language) { codexState("selected") }
            shotCodex("build", w, h, language, run = true) {
                onNodeWithTag("codex-tab-0").performSemanticsAction(SemanticsActions.OnClick)
            }
            shotCodex("weapon", w, h, language, run = true) {
                onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-category-1").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-weapon/FLUX_WAKE").performSemanticsAction(SemanticsActions.OnClick)
            }
            shotCodex("build-item", w, h, language, run = true) { codexState("build-item") }
            shotCodex("reactions", w, h, language) {
                onNodeWithTag("codex-tab-2").performClick()
            }
            shotCodex("reaction-selected", w, h, language, run = true) {
                onNodeWithTag("codex-tab-2").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-reaction/PLASMA_BURST").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-residue").performSemanticsAction(SemanticsActions.OnClick)
            }
            shotCodex("pairings", w, h, language, run = true) {
                onNodeWithTag("codex-tab-3").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-pairing/PERPETUAL_VECTOR/GYRE_ENGINE").performSemanticsAction(SemanticsActions.OnClick)
            }
            shotCodex("tags", w, h, language, run = true) {
                onNodeWithTag("codex-tab-4").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-tag/RAM").performSemanticsAction(SemanticsActions.OnClick)
            }
            shotCodex("relic-selected", w, h, language, run = true) {
                onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-category-2").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-relic/FURNACE_COVENANT").performSemanticsAction(SemanticsActions.OnClick)
            }
            shotCodex("build-empty-slot", w, h, language, run = true) {
                onNodeWithTag("codex-tab-0").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-empty-relic/2").performSemanticsAction(SemanticsActions.OnClick)
            }
            shotCodex("item-offer", w, h, language, run = true, lifetimeMatter = 1_000L) { codexState("item-offer") }
            // Active items: one selected in the catalog (its Active section), the Active filter, one held in the build.
            for (state in listOf("active-selected", "active-filter", "build-active")) {
                shotCodex(state, w, h, language, run = state == "build-active") { codexState(state) }
            }
            shotCodex("active-selected-text175", w, h, language, scale = 1.75f) { codexState("active-selected") }
            for (state in listOf("build", "default", "selected", "build-stats", "item-offer", "build-item")) shotCodex("$state-text175", w, h, language,
                run = state.startsWith("build") || state == "item-offer", scale = 1.75f, lifetimeMatter = if (state == "item-offer") 1_000L else 9_640L) {
                codexState(state)
            }
            shotCodex("build-stats", w, h, language, run = true) { codexState("build-stats") }
            // The characters (an unlocked one, a locked one with its progress) and the enemy roster (an unlocked enemy, the
            // Rebirth-locked Reflector), at the default text size and the largest.
            for (state in listOf("forms", "forms-locked", "enemies", "enemies-locked")) {
                shotCodex(state, w, h, language, run = true) { codexState(state) }
                shotCodex("$state-text175", w, h, language, run = true, scale = 1.75f) { codexState(state) }
            }
        }
        // Text size 100 % and a shorter desktop window (the nav scrolls there, with its cue).
        for (language in AppLanguage.entries) {
            for (state in listOf("default", "selected")) shotCodex("$state-text100", 1440, 810, language, scale = 1f) { codexState(state) }
            shotCodex("default-text175", 1280, 720, language, scale = 1.75f) { codexState("default") }
        }
        // The smoke renders the Items matrix once, in [codexItems].
        if (FullShots) codexItemsShots()
    }

    /** The Items matrix alone (also part of [codex] with `KK_SHOTS=1`). */
    @Test fun codexItems() = codexItemsShots()

    /** The Items matrix: each sort, a previewed cell's row and column, and a fresh profile's hatches. */
    private fun codexItemsShots() {
        for ((w, h) in sizes) for (language in AppLanguage.entries) {
            for (state in ItemsMatrixStates) {
                shotCodex(state, w, h, language) { codexState(state) }
                shotCodex("$state-text175", w, h, language, scale = 1.75f) { codexState(state) }
            }
            shotCodex("items-fresh", w, h, language, fresh = true) { }
            shotCodex("items-fresh-text175", w, h, language, scale = 1.75f, fresh = true) { }
            // A search matching items of several rows, and the items held in a run: rows drop, counts stay, cells pack.
            shotCodex("items-searched", w, h, language) {
                onNodeWithTag("codex-search").performTextInput(if (language == AppLanguage.English) "co" else "ая")
            }
            shotCodex("items-in-build", w, h, language, run = true) {
                onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-filter-2").performSemanticsAction(SemanticsActions.OnClick)
            }
        }
        // Smaller desktop windows, where the matrix cells are smallest.
        for (language in AppLanguage.entries) {
            for ((w, h) in listOf(1280 to 720, 1000 to 700)) for (state in listOf("sort-tag", "items-preview", "sort-element")) {
                shotCodex(state, w, h, language) { codexState(state) }
            }
            shotCodex("items-preview-text175", 1000, 700, language, scale = 1.75f) { codexState("items-preview") }
            shotCodex("items-preview-text100", 1440, 810, language, scale = 1f) { codexState("items-preview") }
        }
        // The rarity fills in the colour-vision presets whose gold or spectrum differ.
        for ((name, palette) in listOf("protan" to KkRolePalette.Protan, "mono" to KkRolePalette.Mono)) {
            shotCodex("items-preview-$name", 1440, 810, AppLanguage.English, palette = palette) { codexState("items-preview") }
        }
    }

    private fun ComposeUiTest.codexState(state: String) {
        when (state) {
            "build" -> onNodeWithTag("codex-tab-0").performSemanticsAction(SemanticsActions.OnClick)
            "build-stats" -> {
                onNodeWithTag("codex-tab-0").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-grid").performScrollToKey("stats-heading")
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-stats").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-grid").performScrollToKey("stat/Critical damage")
                mainClock.advanceTimeBy(100)
            }
            "selected" -> {
                // The clock is paused: the scroll lands, and composes a slot below the fold, on the next frame.
                onNodeWithTag("codex-grid").performScrollToKey("item/10")
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-item/10").performSemanticsAction(SemanticsActions.OnClick)
            }
            "sort-tag", "sort-element", "sort-rarity" -> {
                onNodeWithTag("codex-sort-${ItemsMatrixStates.indexOf(state)}").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
            }
            // Item 10 pinned beside the side panel, or focused where a pin would open the sheet over the matrix; its
            // row (Drive) scrolled to its name, as the keyboard brings a wrapped line into view.
            "items-preview" -> {
                onNodeWithTag("codex-grid").performScrollToKey("items/label/tag/DRIVE")
                mainClock.advanceTimeBy(100)
                val wide = onAllNodesWithTag("codex-side-panel").fetchSemanticsNodes().isNotEmpty()
                onNodeWithTag("codex-slot-item/10").performSemanticsAction(if (wide) SemanticsActions.OnClick else SemanticsActions.RequestFocus)
            }
            "item-offer" -> {
                onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-grid").performScrollToKey("item/10")
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-item/10").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
            }
            "build-item" -> {
                onNodeWithTag("codex-tab-0").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-grid").performScrollToKey("item/10")
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-item/10").performSemanticsAction(SemanticsActions.OnClick)
            }
            "active-selected", "build-active" -> {
                if (state == "build-active") {
                    onNodeWithTag("codex-tab-0").performSemanticsAction(SemanticsActions.OnClick)
                    mainClock.advanceTimeBy(100)
                }
                val key = "item/${ShotActives.first().id}"
                onNodeWithTag("codex-grid").performScrollToKey(key)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-$key").performSemanticsAction(SemanticsActions.OnClick)
            }
            "active-filter" -> {
                onNodeWithTag("codex-filter-3").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
            }
            "forms", "forms-locked", "enemies", "enemies-locked" -> {
                val forms = state.startsWith("forms")
                val key = when (state) {
                    "forms" -> "shape/PRISM"
                    "forms-locked" -> "shape/SPIRAL"
                    "enemies" -> "enemy/HERALD"
                    else -> "enemy/REFLECTOR"
                }
                onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag(if (forms) "codex-category-3" else "codex-category-4").performSemanticsAction(SemanticsActions.OnClick)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-grid").performScrollToKey(key)
                mainClock.advanceTimeBy(100)
                onNodeWithTag("codex-slot-$key").performSemanticsAction(SemanticsActions.OnClick)
            }
        }
    }

    @Test fun profileUnavailable() {
        for ((w, h) in sizes) for (language in AppLanguage.entries) {
            capture("profile-unavailable", "default", w, h, language, {}) {
                DefaultProfileUnavailableFeature().Content()
            }
        }
    }

    @Test fun shutter() {
        for ((w, h) in sizes) for (language in AppLanguage.entries) {
            for (at in listOf(150L, 330L, 450L)) {
                val port = ShotSessionPort()
                capture("transition", "shutter-${at}ms", w, h, language, {
                    mainClock.advanceTimeBy(500)
                    onNodeWithTag("shot-open").performSemanticsAction(SemanticsActions.OnClick)
                    mainClock.advanceTimeBy(at)
                }) {
                    ShotShell(port, language)
                }
            }
        }
    }

    private fun shot(
        screen: String,
        state: String,
        w: Int,
        h: Int,
        language: AppLanguage,
        profile: () -> ShotProfile,
        density: Float = 1f,
        quit: Boolean = false,
        interact: ComposeUiTest.() -> Unit,
    ) {
        val port = profile()
        val feature = DefaultHomeFeature(port, ShotCatalog, ShotSilentAudio, quitAvailable = quit)
        capture(screen, state, w, h, language, density = density, interact = {
            mainClock.advanceTimeBy(2_000)
            interact()
            mainClock.advanceTimeBy(900)
        }) {
            feature.Content(inputEnabled = true) { }
        }
    }

    private fun shotCodex(state: String, w: Int, h: Int, language: AppLanguage, run: Boolean = false, scale: Float = 1.25f,
        lifetimeMatter: Long = 9_640L, fresh: Boolean = false, palette: KkRolePalette = KkRolePalette.Default, interact: ComposeUiTest.() -> Unit) {
        capture("codex", state, w, h, language, {
            mainClock.advanceTimeBy(200)
            interact()
            mainClock.advanceTimeBy(600)
        }) {
            val model = if (fresh) CodexRenderModel(immutableSetOf(), CodexRunStacks(), ShotCatalog.items) else ShotCodexModel(run)
            CompositionLocalProvider(LocalKkRolePalette provides palette) {
                CodexContent(ShotCatalog, model, ShotProgress(lifetimeMatter = lifetimeMatter), scale) { }
            }
        }
    }

    private fun capture(
        screen: String,
        state: String,
        w: Int,
        h: Int,
        language: AppLanguage,
        interact: ComposeUiTest.() -> Unit,
        density: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        if (!FullShots) {
            val newState = smokeStates.add("$screen/$state")
            val newViewport = smokeViewports.add("$screen/${w}x$h@$density")
            if (!newState && !newViewport) return
        }
        rendered++
        androidx.compose.ui.test.v2.runDesktopComposeUiTest((w * density).toInt(), (h * density).toInt()) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, 1f), LocalAppLanguage provides language) {
                    Box(Modifier.requiredSize(w.dp, h.dp)) { content() }
                }
            }
            mainClock.advanceTimeByFrame()
            interact()
            mainClock.advanceTimeByFrame()
            val bitmap = onRoot().captureToImage()
            if (!FullShots) return@runDesktopComposeUiTest
            val pixels = bitmap.toPixelMap()
            val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
            val folder = File("build/redesign-shots/session").apply { mkdirs() }
            ImageIO.write(image, "png", File(folder, "$screen-$state-${w}x$h${if (density != 1f) "@${density.toInt()}x" else ""}-${language.code}.png"))
        }
    }

    private companion object {
        val FullShots = System.getenv("KK_SHOTS") == "1"
    }
}

/** The shipped content catalog. */
internal val ShotCatalog: UiCatalogSnapshot by lazy { createContentCatalog().uiCatalog() }

/** Items matrix shot states: the three sorts (in segment order), then a previewed cell. */
private val ItemsMatrixStates = listOf("sort-tag", "sort-element", "sort-rarity", "items-preview")

/** The first six shipped active items (Solar Flare first): discovered, and the first held by the shot run at rank II. */
private val ShotActives: List<kinetickk.ball.content.api.ItemDefinition> by lazy { ShotCatalog.items.filter { it.isActive }.take(6) }

private val DiscoveredItems: Set<Int> by lazy {
    (0 until 80).filter { (it * 2654435761L % 100) < 54 }.toSet() + 10 + ShotActives.map { it.id }
}

internal fun ShotProgress(
    coreShape: CoreShape = CoreShape.PRISM,
    lifetimeMatter: Long = 9_640L,
    canRebirth: Boolean = false,
    workshopAffordable: Boolean = false,
): HomeProgressProjection = HomeProgressProjection(
    LOCAL_PROFILE_INSTANCE_ID, ProfileRevision.ZERO,
    PlayerEconomy(matter = 1_284, lifetimeMatter = lifetimeMatter),
    PlayerLoadout(coreShape, WeaponId.FLUX_WAKE, WeaponId.entries.take(7).toSet()),
    PlayerCollection(DiscoveredItems, newItemIds = setOf(10), discoveredRelicIds = RelicId.entries.take(23).toSet()),
    RebirthProgress(level = 3, highestCleared = 2),
    canAdvanceRebirth = canRebirth,
    characterAchievements = CharacterAchievementProgress(eliteKills = 5, dashHits = 40, completedOrbits = 2, architectVictories = 3,
        victoriousCharacters = immutableSetOf(CoreShape.ORB, CoreShape.PRISM)),
    unlockedCoreShapes = immutableSetOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.SHARD, CoreShape.RING, CoreShape.DIAMOND),
    workshopAffordable = workshopAffordable,
    upgradeLevels = 23,
)

internal fun ShotCodexModel(run: Boolean = false): CodexRenderModel = CodexRenderModel(
    DiscoveredItems.toImmutableSet(), if (run) ShotRun else CodexRunStacks(), ShotCatalog.items,
    newItemIds = immutableSetOf(10), discoveredRelicIds = RelicId.entries.take(23).toImmutableSet(),
    discoveredReactionIds = immutableSetOf(ReactionId.THERMAL_SHOCK, ReactionId.PLASMA_BURST, ReactionId.SUPERCONDUCT, ReactionId.ROT_WELL),
    discoveredPairingIds = immutableSetOf(RelicPairingId.MELTDOWN, RelicPairingId.THERMAL_PARADOX),
)

private val ShotRun: CodexRunStacks by lazy {
    val held = ShotActives.first().id
    val stacks = List(ShotCatalog.items.size) { id -> when (id) { 10 -> 1; 21 -> 1; 57 -> 1; 68 -> 1; 40 -> 2; held -> 2; else -> 0 } }.toImmutableList()
    CodexRunStacks(stacks, kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection(
        kinetickk.ball.gameplay.api.GameplayInstanceId(kinetickk.ball.gameplay.api.RunId(1)),
        kinetickk.ball.gameplay.api.GameplayRevision.ZERO, stacks,
        character = CoreShape.PRISM, weapon = WeaponId.FLUX_WAKE, weaponLevel = 7, mastery = "Adept",
        relics = immutableListOf(kinetickk.ball.content.api.EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), kinetickk.ball.content.api.EquippedRelic(RelicId.GYRE_ENGINE, 1)),
        stats = immutableListOf(kinetickk.ball.gameplay.api.BuildStatSummary("Impact damage", 38f, "%",
            immutableListOf(kinetickk.ball.gameplay.api.BuildStatContribution(kinetickk.ball.gameplay.api.BuildStatSource.ITEMS, 30f),
                kinetickk.ball.gameplay.api.BuildStatContribution(kinetickk.ball.gameplay.api.BuildStatSource.LAB, 8f))),
            kinetickk.ball.gameplay.api.BuildStatSummary("Critical damage", 12.5f, "%",
                immutableListOf(kinetickk.ball.gameplay.api.BuildStatContribution(kinetickk.ball.gameplay.api.BuildStatSource.ITEMS, 12.5f)))),
        tags = immutableListOf(kinetickk.ball.gameplay.api.BuildTagSummary(BuildTag.RAM, 5, 2), kinetickk.ball.gameplay.api.BuildTagSummary(BuildTag.DRIVE, 3, 1),
            kinetickk.ball.gameplay.api.BuildTagSummary(BuildTag.CHAIN, 1, 0)),
        elements = immutableListOf(kinetickk.ball.gameplay.api.BuildElementSummary(StatusElement.THERMAL, 4, 2, true),
            kinetickk.ball.gameplay.api.BuildElementSummary(StatusElement.ION, 2, 1, false)),
        relicLinks = immutableListOf(kinetickk.ball.gameplay.api.BuildRelicLinkSummary(RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE,
            kinetickk.ball.gameplay.api.BuildRelicLinkKind.CONCORD, RelicPairingId.ENDLESS_GYRE, discovered = false)),
        reachableReactions = immutableListOf(ReactionId.PLASMA_BURST),
        rebornItemIds = immutableListOf(21),
    ))
}

internal class ShotProfile(
    private val coreShape: CoreShape = CoreShape.PRISM,
    private val textScale: Float = 1.25f,
    private val canRebirth: Boolean = false,
    private val workshopAffordable: Boolean = false,
) : ProfileReadPort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("unused")
    override fun query(query: ProfileQuery.GetMatrixProgress) = MatrixProgressProjection(instanceId, ProfileRevision.ZERO,
        MatrixProfileSnapshot(PlayerEconomy(1_284), MatrixProgress(MatrixNodeId.entries.mapIndexed { index, _ -> index % 2 })))
    override fun query(query: ProfileQuery.GetHangar): kinetickk.ball.profile.api.HangarProjection = error("unused")
    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("unused")
    override fun query(query: ProfileQuery.GetPreferences) = PreferencesProjection(instanceId, ProfileRevision.ZERO, PlayerPreferences(textScale = textScale))
    override fun query(query: ProfileQuery.GetHomeProgress) = ShotProgress(coreShape, canRebirth = canRebirth, workshopAffordable = workshopAffordable)
    override fun query(query: ProfileQuery.GetCollection) = CollectionProjection(instanceId, ProfileRevision.ZERO, PlayerCollection(DiscoveredItems))
}

internal class ShotSessionPort : AppSessionPort {
    override val instanceId = LOCAL_APP_SESSION_INSTANCE_ID
    var overlay: AppDestination? = null
    private var revision = 0L
    override fun accept(pulse: SessionInteractionPulse): SessionAcceptance {
        revision++
        if (pulse is SessionInteractionPulse.OpenOverlay) overlay = pulse.destination
        if (pulse == SessionInteractionPulse.CloseOverlay) overlay = null
        return SessionAcceptance.Accepted(instanceId, SessionRevision(revision))
    }
    override fun query(query: AppSessionQuery.GetShell) = AppShellProjection(
        instanceId, SessionRevision(revision), SessionRevision.ZERO, AppDestination.Home, overlay,
        null, false, null, SessionLifecycle.READY, false, null,
    )
}

/** The real session shell; it provides the language from its own state, seeded with [language]. */
@Composable
private fun ShotShell(port: ShotSessionPort, language: AppLanguage) {
    val home = remember { DefaultHomeFeature(ShotProfile(), ShotCatalog, ShotSilentAudio) }
    AppSessionContent(
        sessionPort = port,
        audioExecutor = remember { SessionAudioExecutor(ShotSilentAudio) },
        gameplayPresentation = object : GameplayPresentation {
            override fun activePresentation() = null
            @Composable override fun Content(inputEnabled: Boolean, onOutput: (GameplayInteractionOutput) -> Unit) = Unit
        },
        homeFeature = object : HomeFeature {
            @Composable override fun Content(inputEnabled: Boolean, onOutput: (HomeOutput) -> Unit) {
                home.Content(inputEnabled) { }
                Box(Modifier.requiredSize(1.dp).testTag("shot-open").semantics {
                    onClick { onOutput(HomeOutput.OpenCodex); true }
                })
            }
        },
        settingsFeature = object : SettingsFeature {
            @Composable override fun Content(routeToken: Long, onOutput: (SettingsOutput) -> Unit) = Unit
        },
        workshopFeature = NoWorkshopFeature,
        rebirthFeature = object : RebirthFeature {
            override fun playAcceptedFeedback() = Unit
            @Composable override fun Content(routeToken: Long, eligible: Boolean, confirmationArmed: Boolean, onOutput: (RebirthOutput) -> Unit) = Unit
        },
        codexFeature = object : CodexFeature {
            @Composable override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) {
                CodexContent(ShotCatalog, ShotCodexModel(), ShotProgress(), 1.25f) { onOutput(CodexOutput.Back) }
            }
        },
        profileUnavailableFeature = object : ProfileUnavailableFeature {
            @Composable override fun Content() = Unit
        },
        initialLanguage = language,
    )
}

private object ShotSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
