// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.BuildProjection
import kinetickk.ball.gameplay.nucleus.render.SeveredRelicProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponSynergyProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.pauseLayoutGeometry
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.interaction.rewards.rewardActiveFixtureContent
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureActiveBase
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureModel
import kinetickk.ball.gameplay.nucleus.render.SkillBarProjection
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.design.label
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.rememberInterfaceTypography
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real pause build view at the three reference frames, in English and Russian, at 100 %, the
 * default 125 % and 175 % text size: every text it lays out fits the width it was given, is never
 * cut with an ellipsis, never breaks inside a word, stays on screen inside its area and meets no
 * other text.
 */
@OptIn(ExperimentalTestApi::class)
class PauseTextFitTest {
    private val frames = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val relics = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1), EquippedRelic(RelicId.FURNACE_COVENANT, 1))
    private val build = BuildProjection(
        tagPoints = immutableListOf(5, 3, 0, 2, 0, 1, 4, 0, 0),
        tagTiers = immutableListOf(2, 1, 0, 0, 0, 0, 1, 0, 0),
        affinity = immutableListOf(4, 2, 0, 1, 0),
        affinityTiers = immutableListOf(2, 1, 0, 0, 0),
        dominant = StatusElement.THERMAL,
        reachableReactions = immutableListOf(ReactionId.THERMAL_SHOCK, ReactionId.WILDFIRE),
        rebornItemIds = immutableListOf(3),
    )

    @Test
    fun everyPauseTextFitsItsBoxWholeAtEveryTextSize() {
        for ((width, height) in frames) for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) {
            val scene = "pause ${width}x$height $language ${textScale}x"
            val records = drawPause(width, height, language, textScale)
            val layout = pauseLayoutGeometry(width.toFloat(), height.toFloat(), 1f)
            listOf(PauseTextKind.MULTIPLIER, PauseTextKind.COST, PauseTextKind.TAG_NAME, PauseTextKind.REACTION).forEach { kind ->
                assertTrue(records.any { it.kind == kind }, "$scene draws $kind")
            }
            // The desktop board's item grid leads with the three active items, each with its slot's key on a cap (the
            // longest key names included); phones show no item grid.
            val keys = records.filter { it.kind == PauseTextKind.KEY }.map { it.layout.layoutInput.text.text }
            if (layout.mode == GameplayLayoutMode.REGULAR) {
                assertEquals(listOf(BindableKey.SPACE, BindableKey.ARROW_RIGHT, BindableKey.C).map { it.label(language).uppercase() }, keys, scene)
            } else {
                assertEquals(emptyList(), keys, scene)
            }
            // The weapon's synergies show on the desktop board at every size and on phones up to the default size
            // (a section that no longer fits a phone at a larger size is left out, never cut).
            if (layout.mode == GameplayLayoutMode.REGULAR || textScale <= 1.25f) {
                assertTrue(records.any { it.kind == PauseTextKind.SYNERGY }, "$scene draws the weapon synergies")
            }
            records.forEach { record ->
                val text = record.layout.layoutInput.text.text
                assertTrue(record.layout.size.width <= record.box + 0.5f, "$scene: \"$text\" (${record.kind}) ${record.layout.size.width} fits ${record.box}")
                assertWhole(record.layout, "$scene: ${record.kind}")
                val r = record.rect
                assertTrue(r.left >= -0.5f && r.right <= width + 0.5f && r.top >= -0.5f && r.bottom <= height + 0.5f, "$scene: \"$text\" on screen: $r")
                if (record.kind != PauseTextKind.TITLE && record.kind != PauseTextKind.TIMER) {
                    val build = layout.build
                    assertTrue(r.left >= build.left - 1f && r.right <= build.right + 1f && r.bottom <= build.bottom + 1f,
                        "$scene: \"$text\" (${record.kind}) inside the build area $build: $r")
                } else {
                    assertTrue(r.right <= layout.panelRight + 1f, "$scene: \"$text\" inside the pause panel")
                }
            }
            val heading = records.single { it.kind == PauseTextKind.HEADING }.rect
            records.filter { it.kind == PauseTextKind.TAG }.forEach { tag ->
                assertTrue(heading.right <= tag.rect.left + 0.5f, "$scene: the heading ends before the tags")
            }
            // No two drawn texts overlap (the sections stack without colliding at every size).
            val drawn = records.filter { it.kind !in setOf(PauseTextKind.TAG, PauseTextKind.TITLE, PauseTextKind.TIMER) }
            drawn.forEachIndexed { index, a ->
                drawn.drop(index + 1).forEach { b ->
                    val overlap = a.rect.intersect(b.rect)
                    assertTrue(overlap.width <= 1f || overlap.height <= 1f,
                        "$scene: \"${a.layout.layoutInput.text}\" (${a.kind}) ${a.rect} meets \"${b.layout.layoutInput.text}\" (${b.kind}) ${b.rect}")
                }
            }
            // The cost list keeps one entry per liability, each on at most three lines, all at one size.
            val costs = records.filter { it.kind == PauseTextKind.COST }
            assertEquals(relics.size, costs.size, "$scene: one cost line per bound relic")
            costs.forEach { assertTrue(it.layout.lineCount <= 3, "$scene: a cost takes at most three lines") }
            assertEquals(1, costs.map { it.layout.layoutInput.style.fontSize }.distinct().size, "$scene: costs share one size")
            // The weapon and its level head the view.
            assertTrue(records.any { it.kind == PauseTextKind.TAG && it.layout.layoutInput.text.text.contains(if (language == AppLanguage.English)
                (if (layout.mode == GameplayLayoutMode.REGULAR) "Flux Wake" else "Lvl 7") else (if (layout.mode == GameplayLayoutMode.REGULAR)
                "Потоковый след" else "Ур. 7"), ignoreCase = true) }, "$scene shows the weapon chip")
            val labels = records.filter { it.kind == PauseTextKind.STAT_LABEL }
            if (labels.isNotEmpty()) assertEquals(1, labels.map { it.layout.layoutInput.style.fontSize }.distinct().size, "$scene: stat labels share one size")
            val columns = labels.map { it.rect.left }.distinct().size
            when (layout.mode) {
                GameplayLayoutMode.COMPACT_PORTRAIT -> assertTrue(labels.isEmpty(), "$scene: portrait keeps relics, resonance and reactions")
                // Phone landscape shows the stats in two columns while the costs keep two readable lines.
                GameplayLayoutMode.COMPACT_LANDSCAPE -> if (textScale <= 1.25f) assertEquals(2, columns, "$scene: phone landscape keeps two stat columns")
                    else assertTrue(columns in setOf(0, 2), "$scene: phone landscape stats in two columns or left out")
                GameplayLayoutMode.REGULAR -> {
                    assertEquals(1, columns, "$scene: the desktop stats column")
                    // One row of run panels, one label size and one value size.
                    for (kind in listOf(PauseTextKind.RUN_LABEL, PauseTextKind.RUN_VALUE)) {
                        val sizes = records.filter { it.kind == kind }.map { it.layout.layoutInput.style.fontSize.value }
                        assertTrue(sizes.size >= 4, "$scene draws the run panels")
                        assertEquals(1, sizes.distinct().size, "$scene: run panel ${kind.name} share one size: $sizes")
                    }
                    assertTrue(records.any { it.kind == PauseTextKind.SYNERGY_NAME }, "$scene names the matrix brackets")
                }
            }
        }
    }

    /**
     * Four bound relics and a replaced one whose cost lingers: every cost shows (the lingering one on
     * the desktop board), all at one size, in entries as tall as their text, a larger text size
     * never leaves the costs smaller than the default did, and the weapon's synergies keep a row.
     */
    @Test
    fun costsShareOneSizeThatNeverShrinksWhenTheTextSizeGrows() {
        val bound = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1),
            EquippedRelic(RelicId.FURNACE_COVENANT, 1), EquippedRelic(RelicId.HOLLOW_CROWN, 1))
        val lingering = build.copy(severed = immutableListOf(SeveredRelicProjection(RelicId.EVENT_HORIZON, 20f)))
        for ((width, height) in frames) for (language in AppLanguage.entries) {
            val sizes = listOf(1f, 1.25f, 1.75f).map { textScale ->
                val scene = "pause ${width}x$height $language ${textScale}x"
                val records = drawPause(width, height, language, textScale, bound, lingering)
                val regular = pauseLayoutGeometry(width.toFloat(), height.toFloat(), 1f).mode == GameplayLayoutMode.REGULAR
                val costs = records.filter { it.kind == PauseTextKind.COST }
                assertEquals(if (regular) 5 else 4, costs.size, "$scene: every cost shows")
                // A cost list that counts entries away ends with a MORE line in its own column (the tag rows' count sits
                // further right, under the resonance label).
                assertTrue(records.none {
                    it.kind == PauseTextKind.MORE && it.rect.top in costs.first().rect.top..costs.last().rect.bottom + 40f &&
                        kotlin.math.abs(it.rect.left - costs.first().rect.left) <= 1f
                }, "$scene: no cost is counted away")
                val fonts = costs.map { it.layout.layoutInput.style.fontSize.value }.distinct()
                assertEquals(1, fonts.size, "$scene: costs share one size: $fonts")
                // A full Relic loadout still leaves the weapon's synergies a row, up to the default text size.
                if (textScale <= 1.25f) {
                    assertTrue(records.any { it.kind == PauseTextKind.SYNERGY }, "$scene draws the weapon synergies beside four costs")
                }
                // Entries follow one another at their own height (no fixed pitch leaving holes).
                costs.zipWithNext().forEach { (a, b) ->
                    val gap = b.rect.top - a.layout.size.height - a.rect.top
                    assertTrue(gap in -1f..14f, "$scene: ${gap}px between cost entries")
                }
                fonts.single()
            }
            val scene = "pause ${width}x$height $language"
            assertTrue(sizes[2] >= sizes[1] - 0.01f, "$scene: costs at 175 % (${sizes[2]}) are not smaller than at 125 % (${sizes[1]})")
        }
    }

    @Test
    fun statLabelsNeverShrinkWhenTheTextSizeGrows() {
        fun labelSize(records: List<PauseTextRecord>) =
            records.first { it.kind == PauseTextKind.STAT_LABEL }.layout.layoutInput.style.fontSize.value
        for ((width, height) in frames.filter { it.first >= 1_000 }) for (language in AppLanguage.entries) {
            val sizes = listOf(1f, 1.25f, 1.75f).map { labelSize(drawPause(width, height, language, it)) }
            val scene = "pause ${width}x$height $language"
            assertTrue(sizes[1] >= sizes[0] - 0.01f, "$scene: stat labels at 125 % (${sizes[1]}) are not smaller than at 100 % (${sizes[0]})")
            assertTrue(sizes[2] >= sizes[1] - 0.01f, "$scene: stat labels at 175 % (${sizes[2]}) are not smaller than at 125 % (${sizes[1]})")
        }
    }

    @Test
    fun displayTypeKeepsItsSizeWhileUiTextFollowsTheSetting() {
        val default = drawPause(1440, 810, AppLanguage.English, 1.25f)
        val large = drawPause(1440, 810, AppLanguage.English, 1.75f)
        fun size(records: List<PauseTextRecord>, kind: PauseTextKind) =
            records.first { it.kind == kind }.layout.layoutInput.style.fontSize.value
        assertEquals(size(default, PauseTextKind.TITLE), size(large, PauseTextKind.TITLE), "Paused ignores the text size")
        assertEquals(size(default, PauseTextKind.TIMER), size(large, PauseTextKind.TIMER), "the frozen clock ignores the text size")
        assertEquals(size(default, PauseTextKind.LABEL) * 1.4f, size(large, PauseTextKind.LABEL), 0.05f, "UI labels follow the text size")
        // At the default the UI text renders at the board size (Pause board: section labels 15 px).
        assertEquals(15f, size(default, PauseTextKind.LABEL), 0.05f)
    }

    @Test
    fun aTouchPlayerSeesTheActiveSlotsByNumberOnTheirCaps() {
        for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.75f)) {
            val records = drawPause(1440, 810, language, textScale, touch = true)
            assertEquals(listOf("1", "2", "3"), records.filter { it.kind == PauseTextKind.KEY }.map { it.layout.layoutInput.text.text },
                "$language ${textScale}x")
        }
    }

    /**
     * A run with every condition (Glass Sun taken, the Tidewave pattern, a Fortune Cryo Keynote whose tag has no point
     * yet, a meteor shower in progress): every text still fits whole, inside the build area and clear of the others; the
     * board lists the Keynote tag without points, keeps one cost entry per relic, the reactions and the weapon's
     * synergies, and names every condition with what the omen does and costs up to the default text size (larger sizes
     * show them where the column leaves room, the synergies first); phones show them as chips where a row is left.
     */
    @Test
    fun theRunConditionsFitAndTheKeynoteTagIsListedAtEveryTextSize() {
        for ((width, height) in frames) for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) {
            val scene = "pause conditions ${width}x$height $language ${textScale}x"
            val records = drawPause(width, height, language, textScale, conditions = true)
            val layout = pauseLayoutGeometry(width.toFloat(), height.toFloat(), 1f)
            records.forEach { record ->
                val text = record.layout.layoutInput.text.text
                assertTrue(record.layout.size.width <= record.box + 0.5f, "$scene: \"$text\" (${record.kind}) fits ${record.box}")
                assertWhole(record.layout, "$scene: ${record.kind}")
                if (record.kind != PauseTextKind.TITLE && record.kind != PauseTextKind.TIMER) {
                    val r = record.rect
                    assertTrue(r.left >= layout.build.left - 1f && r.right <= layout.build.right + 1f && r.bottom <= layout.build.bottom + 1f,
                        "$scene: \"$text\" (${record.kind}) inside the build area ${layout.build}: $r")
                }
            }
            val drawn = records.filter { it.kind !in setOf(PauseTextKind.TAG, PauseTextKind.TITLE, PauseTextKind.TIMER) }
            drawn.forEachIndexed { index, a ->
                drawn.drop(index + 1).forEach { b ->
                    val overlap = a.rect.intersect(b.rect)
                    assertTrue(overlap.width <= 1f || overlap.height <= 1f,
                        "$scene: \"${a.layout.layoutInput.text}\" (${a.kind}) ${a.rect} meets \"${b.layout.layoutInput.text}\" (${b.kind}) ${b.rect}")
                }
            }
            val names = records.filter { it.kind == PauseTextKind.CONDITION }.map { it.layout.layoutInput.text.text.uppercase() }
            val expected = listOf("Glass Sun", "Tidewave", "Meteor shower").map { it.localizedContent(language).uppercase() } +
                (BuildTag.FORTUNE.displayLabel.localizedContent(language) + " " + StatusElement.CRYO.displayLabel.localizedContent(language)).uppercase()
            assertTrue(expected.containsAll(names), "$scene names only the run's conditions: $names")
            if (layout.mode == GameplayLayoutMode.REGULAR) {
                val fortune = BuildTag.FORTUNE.displayLabel.localizedContent(language).uppercase()
                assertTrue(records.any { it.kind == PauseTextKind.TAG_NAME && it.layout.layoutInput.text.text.uppercase() == fortune },
                    "$scene lists the Keynote tag without points")
                assertEquals(relics.size, records.count { it.kind == PauseTextKind.COST }, "$scene: one cost line per bound relic")
                assertTrue(records.any { it.kind == PauseTextKind.SYNERGY }, "$scene draws the weapon synergies")
                assertTrue(records.any { it.kind == PauseTextKind.REACTION }, "$scene draws the reactions")
                if (textScale <= 1.25f) {
                    assertEquals(expected.sorted(), names.sorted(), "$scene names every condition")
                    val lines = records.filter { it.kind == PauseTextKind.CONDITION_TEXT }.map { it.layout.layoutInput.text.text }
                    assertEquals(listOf("+25% impact damage and +20% weapon power.", "−30 maximum integrity.",
                        "Swarms and packs roll in on long surges, then the field goes quiet.").map { it.localizedContent(language) }, lines,
                        "$scene: the omen's upside and cost and the pattern's description read")
                }
            } else {
                // Phones show the conditions as chips where the board leaves a row, never as text lines.
                assertTrue(records.none { it.kind == PauseTextKind.CONDITION_TEXT || it.kind == PauseTextKind.CONDITION_LABEL }, "$scene: chips only")
            }
        }
    }

    private fun drawPause(
        width: Int,
        height: Int,
        language: AppLanguage,
        textScale: Float,
        relics: List<EquippedRelic> = this.relics,
        build: BuildProjection = this.build,
        touch: Boolean = false,
        conditions: Boolean = false,
    ): List<PauseTextRecord> {
        // A full build: two named brackets, tags and elements with points, two reactions (one met), and three active
        // items held in Z/X/C (Solar Flare I, Meteor II, Frost Line I) bound to the longest key names.
        val base = rewardFixtureActiveBase
        val actives = SkillBarProjection(immutableListOf(SkillSlotProjection(SkillId.SOLAR_FLARE.ordinal, 1),
            SkillSlotProjection(SkillId.METEOR.ordinal, 2), SkillSlotProjection(SkillId.FROST_LINE.ordinal, 1)))
        val model = rewardFixtureModel(phase = GamePhase.PAUSED, content = rewardActiveFixtureContent, relics = relics, language = language,
            width = width.toFloat(), height = height.toFloat(), textScale = textScale, build = build,
            discoveredPairings = setOf(RelicPairingId.ENDLESS_GYRE),
            itemStacks = List(rewardActiveFixtureContent.items.size) { id ->
                when (id) { 0, 1, 2 -> 1; 3, 4 -> 2; base -> 1; base + 1 -> 2; base + 3 -> 1; else -> 0 }
            },
            skillBar = actives,
            keyBindings = KeyBindings.DEFAULT.with(GameAction.SKILL_1, BindableKey.SPACE).with(GameAction.SKILL_2, BindableKey.ARROW_RIGHT))
            .with("discoveredReactionMask" to (1 shl ReactionId.WILDFIRE.ordinal), "weaponSynergy" to FullSynergy)
            .let { if (conditions) it.with(*RunConditions) else it }
        val records = mutableListOf<PauseTextRecord>()
        PauseTextProbe.records = records
        try {
            runDesktopComposeUiTest(width, height) {
                setContent {
                    CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                        val measurer = CanvasTextMeasurer(rememberTextMeasurer(), textScale, language, rememberInterfaceTypography(), LocalKkRolePalette.current)
                        Box(Modifier.requiredSize(width.dp, height.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                records.clear()
                                drawPause(model, measurer, pauseLayoutGeometry(size.width, size.height, 1f), touch = touch)
                            }
                        }
                    }
                }
                waitForIdle()
            }
        } finally {
            PauseTextProbe.records = null
        }
        return records.toList()
    }
}

/** A run with every condition: an omen taken, a director pattern, a Keynote whose tag has no point yet and a world event. */
private val RunConditions: Array<Pair<String, Any?>> = arrayOf(
    "runOmen" to OmenId.GLASS_SUN,
    "directorPersonality" to DirectorPersonalityId.TIDE,
    "keynoteTag" to BuildTag.FORTUNE,
    "keynoteElement" to StatusElement.CRYO,
    "worldEvent" to WorldEventProjection(WorldEventKind.METEOR_SHOWER, telegraphAt = 440f, startsAt = 443f, endsAt = 473f),
)

/** Every synergy chip at once: resonance, signature, a harmonizing Relic, an element, scaling and two forms. */
private val FullSynergy = WeaponSynergyProjection(
    tagPoints = 6, tagBonus = 0.3f, signatureActive = true, harmonizingRelic = RelicId.PERPETUAL_VECTOR,
    infusionElement = StatusElement.THERMAL, scalingMultiplier = 1.42f, masteryForms = 2, activeMasteryForm = "The trail lasts 4 s",
)

/** No line of [layout] is ellipsized and no line ends inside a word. */
private fun assertWhole(layout: TextLayoutResult, scene: String) {
    val text = layout.layoutInput.text.text
    for (line in 0 until layout.lineCount) {
        assertTrue(!layout.isLineEllipsized(line) && !layout.isCut(), "$scene: \"$text\" is cut off")
        if (line == layout.lineCount - 1) continue
        val end = layout.getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        assertTrue(text[end - 1].isWhitespace() || text[end].isWhitespace() || text[end - 1] in "-–—/", "$scene: \"$text\" breaks inside a word")
    }
    assertTrue(!layout.hasVisualOverflow, "$scene: \"$text\" overflows its box")
}
