// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.localeList
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The run feed (banners and build toasts) drawn with the real fonts in both languages. */
class HudFeedTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)

    @Test
    fun everyDrawnFeedLineFitsOnItsPlateAtEveryTextScale() {
        val scenes = listOf(
            "ELITE SIGNAL" to listOf(
                notice("Neon Ram", "Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver"),
                notice("Ghost Vector", "Dash power +12"),
            ),
            // Long Russian title, banner and synergy names; the widest toast comes last (drawn first).
            "TWO ANOMALIES DETECTED // CHOOSE A COURSE" to listOf(
                notice("Neon Ram", "Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver"),
                notice("Amplify Singularity Spear", "Weapon power +0.04"),
                notice("Singularity Spear mastery advanced", "Critical chance +2.5%", "Damage reduction +3%", "+ Gravitic grouping", "\u2212 Vector maneuver"),
            ),
            "SINGULARITY SPEAR SYNCHRONIZED" to listOf(notice("Neon Ram", "Impact damage +0.05", "Weapon power +0.04", "+ Prism refraction")),
            "FLUX WAKE // LEVEL 7" to listOf(notice("Ghost Vector")),
        )
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            // The chain counter is display type: it keeps its size under every text setting, and the
            // feed docks under that size. While the chain still grows with the setting (a HUD
            // finding of its own), the feed is checked against the chain's display-size box.
            var chainAtDisplaySize: androidx.compose.ui.geometry.Rect? = null
            for (textScale in TEXT_SCALES) for ((message, notices) in scenes) {
                val model = feedModel(w, h, message)
                val fx = VisualFxProjection.EMPTY.copy(buildNotifications = notices.toImmutableList())
                drawFeed(w, h, language, textScale) { measurer ->
                    drawHud(model, measurer, 1f)
                    drawHudFeed(model, fx, measurer, 1f, null)
                }
                val context = "$language $w x $h x$textScale \"$message\""
                // At the default size and below the banner always shows; larger text may make it yield
                // to the newest toast's synergy lines.
                assertTrue(FeedProbe.plateCount >= 1, "$context: nothing drawn")
                if (textScale <= 1.25f) assertTrue(FeedProbe.isBanner(0), "$context: banner not drawn")
                assertFeedLinesFit(context, w, h)
                assertFeedClearsTheCore(context, w, h)
                val feed = assertNotNull(HudLayoutProbe.rect(HudBlock.FEED), context)
                val chain = assertNotNull(HudLayoutProbe.rect(HudBlock.CHAIN), context)
                if (textScale == 1f) chainAtDisplaySize = chain
                val displayChain = assertNotNull(chainAtDisplaySize)
                val shownChain = if (chain.height > displayChain.height + 0.5f) displayChain else chain
                assertFalse(feed.overlaps(shownChain), "$context: feed $feed meets CHAIN $shownChain")
                listOf(HudBlock.CLOCK, HudBlock.MATTER_CHIP, HudBlock.KEY_CHIP, HudBlock.BOSS, HudBlock.TRIAL_PANEL).forEach { block ->
                    val other = HudLayoutProbe.rect(block) ?: return@forEach
                    assertFalse(feed.overlaps(other), "$context: feed $feed meets $block $other")
                }
                runningControlBounds(w.toFloat(), h.toFloat(), 1f).forEach { control ->
                    assertFalse(feed.overlaps(control.bounds), "$context: feed $feed covers ${control.target}")
                }
            }
        }
    }

    @Test
    fun synergyLinesAlwaysReachThePhoneToasts() {
        val switched = notice("Neon Ram", "Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver")
        val swapped = notice("Replace Ghost Vector", "Impact damage +0.05", "Weapon power +0.04", "Dash power +12",
            "\u2212 Vector maneuver", "+ Gravitic grouping")
        // With and without an active trial (its panel pushes the portrait feed down).
        val trial = PointOfInterestProjection(PointOfInterestKind.SEALED_ANOMALY, "Sealed anomaly", 0f, 0f, true, 12f, 0, 1f / 3f,
            immutableListOf(1, 2, 3), 0f, 0f)
        for (language in AppLanguage.entries) listOf(844 to 390, 390 to 844).forEach { (w, h) ->
            for (textScale in TEXT_SCALES) for (message in listOf("", "ELITE SIGNAL")) for (toast in listOf(switched, swapped)) for (trials in 0..1) {
                val model = feedModel(w, h, message).with("pointsOfInterest" to List(trials) { trial }.toImmutableList())
                val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(toast))
                drawFeed(w, h, language, textScale) { measurer -> drawHudFeed(model, fx, measurer, 1f, null) }
                val context = "$language $w x $h x$textScale \"$message\" ${toast.title} trial=$trials"
                val plate = (0 until FeedProbe.plateCount).firstOrNull { !FeedProbe.isBanner(it) }
                assertNotNull(plate, "$context: toast not drawn")
                val drawn = (0 until FeedProbe.lineCount).filter { FeedProbe.linePlate(it) == plate }
                    .map { FeedProbe.lineLayout(it).layoutInput.text.text.replace('\u00A0', ' ') }
                toast.details.filter { it.startsWith("+ ") || it.startsWith("\u2212 ") }.forEach { synergy ->
                    val expected = synergy.localizedContent(language).uppercase()
                    assertTrue(expected in drawn, "$context: $expected missing from $drawn")
                }
                // Stat deltas fill the rest of the phone budget (as room allows) in the notice's order.
                val deltas = toast.details.filterNot { it.startsWith("+ ") || it.startsWith("\u2212 ") }.map { it.localizedContent(language).uppercase() }
                val drawnDeltas = drawn.drop(1).filter { it in deltas }
                assertEquals(deltas.take(drawnDeltas.size), drawnDeltas, context)
                if (message.isEmpty() && trials == 0 && textScale <= 1.25f && toast === switched) assertEquals(1, drawnDeltas.size, "$context: $drawn")
                assertFeedLinesFit(context, w, h)
                assertFeedClearsTheCore(context, w, h)
            }
        }
    }

    @Test
    fun toastsWithFourSynergyLinesStayOnPhonesAtEveryTextScale() {
        // A relic replace can end two synergies and start two (aspect pair and named pair each).
        val toasts = listOf(
            notice("Replace Ghost Vector", *FOUR_SYNERGIES),
            notice("Replace Ghost Vector", "Impact damage +0.05", *FOUR_SYNERGIES),
            notice("Replace Chroma Feedback", "Critical chance +2.5%", "Damage reduction +3%", *FOUR_SYNERGIES),
        )
        drawPhoneToasts(listOf(844 to 390, 390 to 844), toasts) { context, language, toast, drawn ->
            toast.details.filter { it.startsWith("+ ") || it.startsWith("\u2212 ") }.forEach { synergy ->
                val expected = synergy.localizedContent(language).uppercase()
                assertTrue(expected in drawn, "$context: $expected missing from $drawn")
            }
        }
    }

    @Test
    fun smallPhonesStillShowTheNewestToastClearOfTheCore() {
        // Short screens (and a trial panel in portrait) leave little room: the toast still shows, at
        // least its title, whole and clear of the Core, the controls and the other HUD blocks.
        val toasts = listOf(
            notice("Neon Ram", "Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver"),
            notice("Replace Ghost Vector", "Impact damage +0.05", *FOUR_SYNERGIES),
            notice("Replace Chroma Feedback", "Critical chance +2.5%", "Damage reduction +3%", *FOUR_SYNERGIES),
        )
        drawPhoneToasts(listOf(667 to 375, 640 to 360, 780 to 360, 375 to 667, 360 to 640, 360 to 780), toasts) { _, _, _, _ -> }
    }

    /**
     * Draws each toast (alone, with and without a banner and a trial panel) at every text scale and
     * checks that it shows its title first, fits, and keeps clear of the Core, the controls and the
     * other HUD blocks; [check] adds per-case assertions on the toast's drawn lines.
     */
    private fun drawPhoneToasts(
        sizes: List<Pair<Int, Int>>,
        toasts: List<BuildNotificationProjection>,
        check: (context: String, language: AppLanguage, toast: BuildNotificationProjection, drawn: List<String>) -> Unit,
    ) {
        val trial = PointOfInterestProjection(PointOfInterestKind.SEALED_ANOMALY, "Sealed anomaly", 0f, 0f, true, 12f, 0, 1f / 3f,
            immutableListOf(1, 2, 3), 0f, 0f)
        val messages = listOf("", "ELITE SIGNAL", "TWO ANOMALIES DETECTED // CHOOSE A COURSE")
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            var chainAtDisplaySize: androidx.compose.ui.geometry.Rect? = null
            for (textScale in TEXT_SCALES) for (message in messages) for (toast in toasts) for (trials in 0..1) {
                val model = feedModel(w, h, message).with("pointsOfInterest" to List(trials) { trial }.toImmutableList())
                val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(toast))
                drawFeed(w, h, language, textScale) { measurer ->
                    drawHud(model, measurer, 1f)
                    drawHudFeed(model, fx, measurer, 1f, null)
                }
                val context = "$language $w x $h x$textScale \"$message\" ${toast.title} ${toast.details.size} details trial=$trials"
                val drawn = drawnToastLines(context)
                assertEquals(toast.title.localizedContent(language).uppercase(), drawn.first(), context)
                check(context, language, toast, drawn)
                if (textScale == 1f) chainAtDisplaySize = HudLayoutProbe.rect(HudBlock.CHAIN)
                assertFeedClearOfTheHud(context, w, h, assertNotNull(chainAtDisplaySize))
            }
        }
    }

    @Test
    fun theBannerStaysAboveANewestToastThatTakesTheColumnBesideTheCore() {
        // Short portrait screens under a trial panel or a boss row: no text step holds a four-synergy
        // toast above or below the Core, so it takes the column beside the Core. The toast keeps its
        // title and every synergy line there under the banner (stepping its text down where needed),
        // so the banner does not yield.
        val toasts = listOf(
            notice("Replace Ghost Vector", *FOUR_SYNERGIES),
            notice("Replace Ghost Vector", "Impact damage +0.05", *FOUR_SYNERGIES),
            notice("Replace Chroma Feedback", "Critical chance +2.5%", "Damage reduction +3%", *FOUR_SYNERGIES),
        )
        val scenes = listOf(
            Triple(360 to 780, listOf(1f, 1.1f), "pointsOfInterest" to immutableListOf(TRIAL)),
            Triple(360 to 640, TEXT_SCALES, "enemies" to immutableListOf(ELITE)),
        )
        for (language in AppLanguage.entries) for ((screen, textScales, panel) in scenes) for (toast in toasts) {
            val (w, h) = screen
            var chainAtDisplaySize: androidx.compose.ui.geometry.Rect? = null
            for (textScale in textScales) {
                val model = feedModel(w, h, "ELITE SIGNAL").with(panel)
                val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(toast))
                drawFeed(w, h, language, textScale) { measurer ->
                    drawHud(model, measurer, 1f)
                    drawHudFeed(model, fx, measurer, 1f, null)
                }
                val context = "$language $w x $h x$textScale ${panel.first} ${toast.title} ${toast.details.size} details"
                assertTrue((0 until FeedProbe.plateCount).any { FeedProbe.isBanner(it) }, "$context: banner not drawn")
                val drawn = drawnToastLines(context)
                assertEquals(toast.title.localizedContent(language).uppercase(), drawn.first(), context)
                FOUR_SYNERGIES.forEach { synergy ->
                    val expected = synergy.localizedContent(language).uppercase()
                    assertTrue(expected in drawn, "$context: $expected missing from $drawn")
                }
                if (textScale == 1f) chainAtDisplaySize = HudLayoutProbe.rect(HudBlock.CHAIN)
                assertFeedClearOfTheHud(context, w, h, assertNotNull(chainAtDisplaySize, context))
            }
        }
    }

    @Test
    fun aBannerNeverCostsTheNewestToastItsTitleOrASynergyLine() {
        // Wherever the newest toast goes (above, below or beside the Core, at any text step), a banner
        // above it shows only while the toast keeps every title and synergy line it shows alone. A
        // banner with a detail line is tall enough to push a toast out of the column beside the Core.
        val toasts = listOf(
            notice("Neon Ram", "Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver"),
            notice("Replace Ghost Vector", "Impact damage +0.05", *FOUR_SYNERGIES),
            notice("Replace Chroma Feedback", "Critical chance +2.5%", "Damage reduction +3%", *FOUR_SYNERGIES),
        )
        val panels = listOf(null, "pointsOfInterest" to immutableListOf(TRIAL), "enemies" to immutableListOf(ELITE))
        val phones = listOf(360 to 640, 375 to 667, 360 to 740, 360 to 780, 390 to 844, 640 to 360, 667 to 375, 780 to 360, 844 to 390)
        for (language in AppLanguage.entries) for ((w, h) in phones) for (panel in panels) for (textScale in TEXT_SCALES) for (toast in toasts) {
            val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(toast))
            val outranking = (listOf(toast.title) + toast.details.filter { it.startsWith("+ ") || it.startsWith("\u2212 ") })
                .map { it.localizedContent(language).uppercase() }
            fun shown(message: String): List<String>? {
                val model = feedModel(w, h, message).let { if (panel == null) it else it.with(panel) }
                drawFeed(w, h, language, textScale) { measurer -> drawHudFeed(model, fx, measurer, 1f, null) }
                if ((0 until FeedProbe.plateCount).none { !FeedProbe.isBanner(it) }) return null
                return drawnToastLines("").filter { it in outranking }
            }
            val alone = shown("") ?: continue
            for (message in listOf("ELITE SIGNAL", "FLUX WAKE // LEVEL 7")) {
                val context = "$language $w x $h x$textScale ${panel?.first} ${toast.title} ${toast.details.size} details \"$message\""
                val underBanner = assertNotNull(shown(message), "$context: toast not drawn under the banner")
                assertTrue(underBanner.containsAll(alone), "$context: alone $alone, under the banner $underBanner")
            }
        }
    }

    /**
     * On landscape phones the boss name grows with the text size and pushes the elite / Architect
     * bar down: no feed plate may meet the boss block at any text size.
     */
    @Test
    fun landscapeFeedPlatesStayUnderTheBossBlockAtEveryTextSize() {
        val toasts = listOf(
            notice("Neon Ram", "Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver"),
            notice("Replace Ghost Vector", "Impact damage +0.05", *FOUR_SYNERGIES),
        )
        val bosses = listOf(ELITE, ELITE.copy(type = EnemyType.ARCHITECT))
        for (language in AppLanguage.entries) listOf(844 to 390, 780 to 360, 667 to 375, 640 to 360).forEach { (w, h) ->
            for (textScale in TEXT_SCALES) for (boss in bosses) for (message in listOf("ELITE SIGNAL", "TWO ANOMALIES DETECTED // CHOOSE A COURSE"))
                for (toast in toasts) {
                    val model = feedModel(w, h, message).with("enemies" to immutableListOf(boss))
                    val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(toast))
                    drawFeed(w, h, language, textScale) { measurer ->
                        drawHud(model, measurer, 1f)
                        drawHudFeed(model, fx, measurer, 1f, null)
                    }
                    val context = "$language $w x $h x$textScale ${boss.type} \"$message\" ${toast.title}"
                    val bossBlock = assertNotNull(HudLayoutProbe.rect(HudBlock.BOSS), context)
                    assertTrue(FeedProbe.plateCount >= 1, "$context: nothing drawn")
                    for (plate in 0 until FeedProbe.plateCount) {
                        val box = FeedProbe.plateBox(plate)
                        assertFalse(box.overlaps(bossBlock), "$context: plate $plate $box meets the boss block $bossBlock")
                    }
                    assertFeedLinesFit(context, w, h)
                    assertFeedClearsTheCore(context, w, h)
                }
        }
    }

    /**
     * The detail lines of one toast share one size: when a long word shrinks its own line (a synergy
     * name in the narrow column beside the Core), its sibling lines shrink with it.
     */
    @Test
    fun detailLinesOfOneToastShareOneSize() {
        val toasts = listOf(
            notice("Replace Ghost Vector", "Impact damage +0.05", "Weapon power +0.04", "Dash power +12", "− Vector maneuver", "+ Gravitic grouping"),
            notice("Replace Chroma Feedback", "Critical chance +2.5%", "Damage reduction +3%", *FOUR_SYNERGIES),
            notice("Singularity Spear mastery advanced", "Critical chance +2.5%", "Damage reduction +3%", "+ Gravitic grouping", "− Vector maneuver"),
        )
        val screens = sizes + listOf(780 to 360, 640 to 360, 360 to 780, 360 to 640)
        for (language in AppLanguage.entries) screens.forEach { (w, h) ->
            for (textScale in TEXT_SCALES) for (message in listOf("", "GHOST VECTOR // SLOT 2 BOUND")) for (toast in toasts) {
                val model = feedModel(w, h, message)
                val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(toast))
                drawFeed(w, h, language, textScale) { measurer -> drawHudFeed(model, fx, measurer, 1f, null) }
                val context = "$language $w x $h x$textScale \"$message\" ${toast.title}"
                for (plate in 0 until FeedProbe.plateCount) {
                    if (FeedProbe.isBanner(plate)) continue
                    val details = (0 until FeedProbe.lineCount).filter { FeedProbe.linePlate(it) == plate }.drop(1).map { FeedProbe.lineLayout(it) }
                    val sizes = details.map { it.layoutInput.style.fontSize.value }.distinct()
                    assertTrue(sizes.size <= 1, "$context: detail sizes $sizes in ${details.map { it.layoutInput.text.text }}")
                }
                assertFeedLinesFit(context, w, h)
            }
        }
    }

    /** The lines of the first toast plate drawn in the last frame, in drawing order. */
    private fun drawnToastLines(context: String): List<String> {
        val plate = (0 until FeedProbe.plateCount).firstOrNull { !FeedProbe.isBanner(it) }
        assertNotNull(plate, "$context: toast not drawn")
        return (0 until FeedProbe.lineCount).filter { FeedProbe.linePlate(it) == plate }
            .map { FeedProbe.lineLayout(it).layoutInput.text.text.replace('\u00A0', ' ') }
    }

    /**
     * The last frame's feed fits, keeps clear of the Core and does not meet the controls or the other
     * HUD blocks. The chain counter is display type; while it still grows with the text setting (a
     * HUD finding of its own), the feed is checked against [displayChain], its box at the smallest
     * setting, whenever the drawn one is taller.
     */
    private fun assertFeedClearOfTheHud(context: String, w: Int, h: Int, displayChain: androidx.compose.ui.geometry.Rect) {
        assertFeedLinesFit(context, w, h)
        assertFeedClearsTheCore(context, w, h)
        val feed = assertNotNull(HudLayoutProbe.rect(HudBlock.FEED), context)
        runningControlBounds(w.toFloat(), h.toFloat(), 1f).forEach { control ->
            assertFalse(feed.overlaps(control.bounds), "$context: feed $feed covers ${control.target}")
        }
        val chain = assertNotNull(HudLayoutProbe.rect(HudBlock.CHAIN), context)
        val shownChain = if (chain.height > displayChain.height + 0.5f) displayChain else chain
        assertFalse(feed.overlaps(shownChain), "$context: feed $feed meets CHAIN $shownChain")
        listOf(HudBlock.CLOCK, HudBlock.MATTER_CHIP, HudBlock.KEY_CHIP, HudBlock.BOSS, HudBlock.TRIAL_PANEL).forEach { block ->
            val other = HudLayoutProbe.rect(block) ?: return@forEach
            assertFalse(feed.overlaps(other), "$context: feed $feed meets $block $other")
        }
    }

    @Test
    fun weaponLevelTailsReadAsTheShortLevelFormat() {
        assertEquals("FLUX WAKE" to "Lvl 7", feedMessageParts("FLUX WAKE // LEVEL 7", AppLanguage.English))
        assertEquals("Ур. 7", feedMessageParts("FLUX WAKE // LEVEL 7", AppLanguage.Russian).second)
        assertEquals("Ур. 11", feedMessageParts("SINGULARITY SPEAR // LEVEL 11", AppLanguage.Russian).second)
        for (language in AppLanguage.entries) for (message in listOf("FLUX WAKE // LEVEL 7", "SINGULARITY SPEAR // LEVEL 11")) {
            val detail = feedMessageParts(message, language).second.orEmpty().uppercase()
            assertFalse("LEVEL" in detail || "УРОВЕНЬ" in detail, "$language $message: $detail")
        }
        // Mastery milestones keep their names.
        assertEquals("FLUX WAKE" to "AMPLIFIED", feedMessageParts("FLUX WAKE // AMPLIFIED", AppLanguage.English))
    }

    @Test
    fun replacedRelicTailsShowNoSlotNumeral() {
        for (language in AppLanguage.entries) for (slot in 1..4) {
            val (title, detail) = feedMessageParts("GHOST VECTOR // SLOT $slot BOUND", language)
            assertTrue(detail != null && detail.none(Char::isDigit), "$language slot $slot: $detail")
            assertTrue(title.none(Char::isDigit), "$language slot $slot: $title")
        }
        // A replace reads like a fresh bind.
        assertEquals(feedMessageParts("GHOST VECTOR // BOUND", AppLanguage.English), feedMessageParts("GHOST VECTOR // SLOT 2 BOUND", AppLanguage.English))
        assertEquals(feedMessageParts("GHOST VECTOR // BOUND", AppLanguage.Russian), feedMessageParts("GHOST VECTOR // SLOT 2 BOUND", AppLanguage.Russian))
    }

    @Test
    fun fadingFeedPlatesAllocateNothingPerFrame() {
        sizes.forEach { (width, height) ->
            val base = hudTestModel(width.toFloat(), height.toFloat())
            val model = base.with("message" to "ELITE SIGNAL", "messageTime" to 1.5f)
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
                AppLanguage.Russian, HudTestFonts.typography.copy(localeList = AppLanguage.Russian.localeList()))
            // The message appears at SHOWN_AT: every measured frame lies inside its entrance (0..0.12 s)
            // while both toasts fade out (life 0.6 .. 0).
            val memory = HudPresentationMemory()
            memory.observe(base, SHOWN_AT - 1f, BossTarget().select(base))
            memory.observe(model, SHOWN_AT, BossTarget().select(model))
            val frames = List(FRAMES) { frame ->
                val life = 0.6f * (1f - (frame + 1f) / (FRAMES + 1f))
                VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(
                    BuildNotificationProjection("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver"), life),
                    BuildNotificationProjection("Ghost Vector", immutableListOf("Dash power +12"), life * 0.5f),
                ))
            }
            val times = FloatArray(FRAMES) { SHOWN_AT + 0.12f * it / FRAMES }
            val canvas = Canvas(ImageBitmap(width, height))
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun feed(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawHudFeed(model, frames[frame], measurer, times[frame], memory)
            }
            // Warm every layout and path once, then measure the same fading frames again.
            repeat(FRAMES) { empty(); feed(it) }
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val feedBytes = allocated { repeat(FRAMES) { feed(it) } }
            val perFrame = (feedBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "fading feed at $width x $height allocates $perFrame bytes above an empty frame")
        }
    }

    @Test
    fun toastsOnASmallerTextStepAllocateNothingPerFrame() {
        // At the largest text size these toasts only fit a few steps down (and, in portrait, below
        // the Core): every frame searches the steps again through the per-step layout banks.
        val trial = PointOfInterestProjection(PointOfInterestKind.SEALED_ANOMALY, "Sealed anomaly", 0f, 0f, true, 12f, 0, 1f / 3f,
            immutableListOf(1, 2, 3), 0f, 0f)
        listOf(
            Triple(390 to 844, AppLanguage.Russian, notice("Replace Ghost Vector", "Impact damage +0.05", *FOUR_SYNERGIES)),
            Triple(844 to 390, AppLanguage.English, notice("Replace Chroma Feedback", "Critical chance +2.5%", "Damage reduction +3%", *FOUR_SYNERGIES)),
        ).forEach { (screen, language, toast) ->
            val (width, height) = screen
            val model = feedModel(width, height, "ELITE SIGNAL").with("pointsOfInterest" to immutableListOf(trial))
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.75f,
                language, HudTestFonts.typography.copy(localeList = language.localeList()))
            val frames = List(FRAMES) { frame ->
                VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(toast.copy(life = 5f - frame * 0.01f)))
            }
            val canvas = Canvas(ImageBitmap(width, height))
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun feed(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawHudFeed(model, frames[frame], measurer, 1f, null)
            }
            repeat(FRAMES) { empty(); feed(it) }
            val context = "$language $width x $height ${toast.title}"
            val plate = (0 until FeedProbe.plateCount).firstOrNull { !FeedProbe.isBanner(it) }
            assertNotNull(plate, "$context: toast not drawn")
            assertEquals(1 + FOUR_SYNERGIES.size, (0 until FeedProbe.lineCount).count { FeedProbe.linePlate(it) == plate }, context)
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val feedBytes = allocated { repeat(FRAMES) { feed(it) } }
            val perFrame = (feedBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "$context allocates $perFrame bytes above an empty frame")
        }
    }

    private fun notice(title: String, vararg details: String) =
        BuildNotificationProjection(title, details.toList().toImmutableList(), 5f)

    private fun feedModel(width: Int, height: Int, message: String) = hudTestModel(width.toFloat(), height.toFloat()).with(
        "message" to message, "messageTime" to if (message.isEmpty()) 0f else 1.5f,
        "keys" to 1, "runMatter" to 486L, "combo" to 12, "comboTime" to 1.7f, "comboWindow" to 2.8f,
    )

    private inline fun allocated(block: () -> Unit): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().threadId()
        val before = threads.getThreadAllocatedBytes(id)
        block()
        return threads.getThreadAllocatedBytes(id) - before
    }

    private companion object {
        const val FRAMES = 240
        val TEXT_SCALES = listOf(1f, 1.25f, 1.75f)
        val FOUR_SYNERGIES = arrayOf("+ Gravitic grouping", "+ Brake compression", "\u2212 Vector maneuver", "\u2212 Ghost mirror")
        val TRIAL = PointOfInterestProjection(PointOfInterestKind.SEALED_ANOMALY, "Sealed anomaly", 0f, 0f, true, 12f, 0, 1f / 3f,
            immutableListOf(1, 2, 3), 0f, 0f)
        val ELITE = EnemyProjection(4, EnemyType.ELITE, 0f, 0f, 0f, 0f, 500f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false)
        const val SHOWN_AT = 20f
    }
}

/** Draws [block] on a density-1 canvas with the bundled fonts at [textScale] (tests). */
internal fun drawFeed(
    width: Int,
    height: Int,
    language: AppLanguage,
    textScale: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.(CanvasTextMeasurer) -> Unit,
) {
    val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), textScale, language,
        HudTestFonts.typography.copy(localeList = language.localeList()))
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(width, height)), Size(width.toFloat(), height.toFloat())) {
        block(measurer)
    }
}

/**
 * Every line the feed drew in the last frame shows all of its text (no ellipsis, no overflow, no
 * break inside a word) and lies inside its plate, and every plate lies on screen.
 */
internal fun assertFeedLinesFit(context: String, width: Int, height: Int) {
    for (plate in 0 until FeedProbe.plateCount) {
        val box = FeedProbe.plateBox(plate)
        assertTrue(box.left >= 0f && box.top >= 0f && box.right <= width && box.bottom <= height, "$context: plate $plate $box off screen")
        assertTrue((0 until FeedProbe.lineCount).any { FeedProbe.linePlate(it) == plate }, "$context: plate $plate has no line")
    }
    for (line in 0 until FeedProbe.lineCount) {
        val layout = FeedProbe.lineLayout(line)
        val text = layout.layoutInput.text.text
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$context: \"$text\" is ellipsized")
        assertFalse(layout.didOverflowWidth || layout.didOverflowHeight, "$context: \"$text\" overflows its layout")
        for (row in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(row)
            if (end <= 0 || end >= text.length) continue
            assertTrue(text[end - 1].isWhitespace() || text[end].isWhitespace() || text[end - 1] == '-',
                "$context: \"$text\" breaks inside a word at $end")
        }
        // The drawn glyph extent: the layout's origin plus its widest line and its last line bottom.
        val origin = FeedProbe.lineBox(line)
        var right = 0f
        for (row in 0 until layout.lineCount) right = maxOf(right, layout.getLineRight(row))
        val box = androidx.compose.ui.geometry.Rect(origin.left, origin.top, origin.left + right, origin.top + layout.getLineBottom(layout.lineCount - 1))
        val plate = FeedProbe.plateBox(FeedProbe.linePlate(line))
        assertTrue(box.left >= plate.left - 0.5f && box.top >= plate.top - 0.5f && box.right <= plate.right + 0.5f &&
            box.bottom <= plate.bottom + 0.5f, "$context: \"$text\" $box leaves its plate $plate")
    }
}

/**
 * No plate covers the Core: on phones the camera holds it at the screen center, drawn with its
 * halo ([GameplayRenderModel.CORE_RADIUS] x 2.2) and the halo stroke.
 */
internal fun assertFeedClearsTheCore(context: String, width: Int, height: Int) {
    if (width >= 1_000) return
    val radius = kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel.CORE_RADIUS * 2.2f + 4f
    val core = androidx.compose.ui.geometry.Rect(width * 0.5f - radius, height * 0.5f - radius, width * 0.5f + radius, height * 0.5f + radius)
    for (plate in 0 until FeedProbe.plateCount) {
        val box = FeedProbe.plateBox(plate)
        assertFalse(box.overlaps(core), "$context: plate $plate $box covers the Core $core")
    }
}
