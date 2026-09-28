// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_BOSS_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_PANEL_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.REGULAR_HUD_BOTTOM_DP
import kinetickk.ball.gameplay.interaction.layout.REGULAR_LOADOUT_HEIGHT_DP
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Feed of run messages (banner plates) and build notifications (toast plates), docked under the
 * chain counter on the right so it never covers the Core: on phones a plate that would reach the
 * Core narrows to the column beside it (landscape) or continues below it (portrait; on short screens
 * it may take the column beside the Core instead). Every line sits on its plate: a toast is its
 * title row plus one line per detail, wrapping (then shrinking) instead of cutting, and a line that
 * cannot show whole is not shown. Synergy changes ("+ " / "− " details) always stay on their toast;
 * stat deltas fill the remaining detail budget. A toast whose title and synergy lines do not fit its
 * room at the current text size steps its text down toward the smallest text setting until they do.
 * The newest toast's title and synergy lines outrank the banner: the banner shows while the toast
 * keeps all of them under it (at some text step, anywhere the toast may go) and yields otherwise.
 * Messages that only restate a state the HUD shows (overheat, polarity strain, overdrive, dash
 * online), trial rules (behind the trial panel's (!)) and instruction tails ("choose a course") are
 * not shown as text.
 *
 * Text follows the text-size setting around the design size: the feed's styles are the board sizes
 * over [REFERENCE_TEXT_SCALE], so the default setting renders them at the board size.
 */
internal fun DrawScope.drawHudFeed(
    engine: GameplayRenderModel,
    fx: VisualFxProjection,
    measurer: TextMeasurer,
    renderTime: Float,
    memory: HudPresentationMemory?,
) {
    FeedProbe.begin()
    val frame = HudScratch.frame.update(size.width, size.height, density)
    val area = FeedArea.update(engine, frame, measurer, density)
    val right = area.right
    val gap = frame.u(8f)
    var top = area.top
    var feedTop = Float.NaN
    var feedLeft = right
    val notices = fx.buildNotifications
    if (engine.showsMessage()) {
        FeedScratch.message.update(engine.message, measurer.language)
        if (bannerKeepsTheNewestToast(area, frame, measurer, notices, top, gap)) {
            val age = if (memory != null) memory.messageAge(renderTime) else Float.POSITIVE_INFINITY
            val alpha = (engine.messageTime / 0.45f).coerceIn(0f, 1f) * entranceAlpha(age)
            val shift = entranceShift(age) * frame.u(80f)
            val height = placeBanner(area, frame, measurer, top)
            if (height > 0f) {
                val lines = FeedLines
                translate(shift, 0f) { drawFeedPlate(measurer, frame, HudPath.MESSAGE, 0, lines, right, top, height, alpha, shift, banner = true) }
                feedLeft = min(feedLeft, right - lines.plateWidth(frame, banner = true))
                feedTop = top
                top += height + gap
            }
        }
    }
    var bandLimit = area.limit
    var shown = 0
    var index = notices.size - 1
    while (index >= 0 && shown < area.maxNotices) {
        val notice = notices[index]
        val slot = shown
        // Should even the smallest step hold no place with all its synergy lines, the newest toast's
        // title shows with the lines that fit.
        val height = placeToast(area, frame, measurer, notice, slot, top, bandLimit, relaxed = shown == 0)
        if (height <= 0f) break
        top = ToastSpot.top
        bandLimit = ToastSpot.bandLimit
        val lines = FeedLines
        val age = NOTICE_LIFE_SECONDS - notice.life
        val alpha = (notice.life / 0.6f).coerceIn(0f, 1f) * entranceAlpha(age)
        val shift = entranceShift(age) * frame.u(80f)
        translate(shift, 0f) { drawFeedPlate(measurer, frame, NoticePaths[slot], 1 + slot, lines, right, top, height, alpha, shift, banner = false) }
        feedLeft = min(feedLeft, right - lines.plateWidth(frame, banner = false))
        if (feedTop.isNaN()) feedTop = top
        top += height + gap
        shown++
        index--
        // Nothing follows a toast beside the Core: the next one would start level with the Core.
        if (ToastSpot.side) break
    }
    if (!feedTop.isNaN()) HudLayoutProbe.record(HudBlock.FEED, feedLeft, feedTop, right, top - gap)
}

/**
 * Where the feed docks in the current frame (px): its right edge and first plate top, the plate
 * width and band limit, the Core's zone, and per layout the narrow column right of the Core
 * (landscape), the band below the Core and the column beside it (portrait). Draw-thread scratch,
 * rewritten every frame.
 */
private object FeedArea {
    var right = 0f
    var top = 0f
    var maxWidth = 0f
    var maxNotices = 0
    var maxDetails = 0
    var limit = 0f
    var coreTop = 0f
    var coreBottom = 0f
    var coreRight = 0f
    var narrowWidth = Float.NaN
    var narrowLimit = 0f
    var lowerTop = Float.NaN
    var lowerLimit = 0f
    var sideWidth = Float.NaN
    var levels = 1

    fun update(engine: GameplayRenderModel, frame: HudFrame, measurer: TextMeasurer, density: Float): FeedArea {
        // Phones keep the Core clear: the camera holds it at the screen center, inside this zone.
        val clearance = frame.u(CORE_CLEARANCE_DP)
        coreTop = frame.height * 0.5f - clearance
        coreBottom = frame.height * 0.5f + clearance
        coreRight = frame.width * 0.5f + clearance
        narrowWidth = Float.NaN
        narrowLimit = 0f
        lowerTop = Float.NaN
        lowerLimit = 0f
        sideWidth = Float.NaN
        levels = shrinkLevels(measurer.scale)
        when (frame.mode) {
            GameplayLayoutMode.REGULAR -> {
                right = frame.width - frame.margin
                top = frame.chainTop + frame.u(90f)
                maxWidth = min(frame.u(380f), frame.width * 0.4f)
                maxNotices = MAX_NOTICES
                maxDetails = DETAILS_PER_NOTICE
                // Stop above the Dash/Brake row that sits over the loadout.
                limit = frame.height - frame.u(REGULAR_HUD_BOTTOM_DP + REGULAR_LOADOUT_HEIGHT_DP + 18f + 52f + 12f)
            }
            GameplayLayoutMode.COMPACT_LANDSCAPE -> {
                right = frame.width - frame.margin - compactLoadoutWidth(engine.content.relicPolicy.maxSlots.coerceIn(0, 8), frame.unit) - frame.u(12f)
                maxWidth = min(frame.u(260f) * frame.factor, right - frame.width * 0.5f + frame.u(60f)).coerceAtLeast(frame.u(140f))
                top = frame.u(62f)
                // Under the elite / Architect block whenever a plate may reach its span: the boss name
                // grows with the text size and pushes its bar down.
                val boss = FeedBoss.select(engine)
                if (boss.id >= 0) {
                    val architect = boss.type == EnemyType.ARCHITECT
                    if (right - maxWidth < frame.width * 0.5f + HudTopGeometry.bossBarWidth(frame, architect) * 0.5f) {
                        top = max(top, HudTopGeometry.bossBottom(frame, architect, measurer.scale) + frame.u(8f))
                    }
                }
                maxNotices = 1
                maxDetails = 2
                // A plate that would reach the Core's band narrows to the column right of the Core, which
                // stays free down to the controls under it and the bottom cluster. Without that column
                // (short, narrow screens) plates stop above the Core's band.
                narrowWidth = (right - coreRight).let { if (it >= frame.u(NARROW_COLUMN_MIN_DP)) it else Float.NaN }
                limit = if (narrowWidth.isNaN()) min(frame.height * 0.55f, coreTop - frame.u(8f)) else frame.height * 0.55f
                narrowLimit = frame.height - frame.u(96f)
                forEachRunningControlBounds(frame.width, frame.height, density) { _, controlLeft, controlTop, controlRight, _ ->
                    if (controlTop > frame.height * 0.5f && controlRight > coreRight && controlLeft < right) {
                        narrowLimit = min(narrowLimit, controlTop - frame.u(12f))
                    }
                }
            }
            GameplayLayoutMode.COMPACT_PORTRAIT -> {
                right = frame.width - frame.margin
                top = if (engine.activeTrial() != null) {
                    TrialFeedLayout.update(frame.width, frame.height, density, measurer.scale).bottom + frame.u(8f)
                } else if (FeedBoss.select(engine).id >= 0) {
                    frame.top + frame.u(PORTRAIT_PANEL_ROW_DP)
                } else {
                    frame.top + frame.u(PORTRAIT_BOSS_ROW_DP)
                }
                maxWidth = frame.width - frame.margin * 2f
                maxNotices = 2
                maxDetails = 2
                // Plates span the width here: they stop above the Core, and toasts that no longer fit
                // there continue in the band between the Core and the bottom cluster.
                limit = min(frame.height - frame.u(238f + 12f), coreTop - frame.u(8f))
                lowerTop = max(top, coreBottom + frame.u(8f))
                lowerLimit = frame.height - frame.u(238f + 12f)
                // Short screens under a trial panel or a boss row can leave neither band room for a
                // toast: it then takes the column right of the Core, down to the bottom cluster.
                sideWidth = (right - coreRight).let { if (it >= frame.u(NARROW_COLUMN_MIN_DP)) it else Float.NaN }
            }
        }
        return this
    }
}

/** Where [placeToast] put the toast: its top, the band limit that follows it, and whether it is beside the Core. */
private object ToastSpot {
    var top = 0f
    var bandLimit = 0f
    var side = false

    fun set(top: Float, bandLimit: Float, side: Boolean, height: Float): Float {
        this.top = top
        this.bandLimit = bandLimit
        this.side = side
        return height
    }
}

/**
 * Lays out the current message's banner at [top] into [FeedLines] and returns its height (0 = not
 * shown): full width above the Core, or (landscape) in the column beside it.
 */
private fun placeBanner(area: FeedArea, frame: HudFrame, measurer: TextMeasurer, top: Float): Float =
    placeFeedPlate(frame, top, area.right, banner = true, area.maxWidth, area.limit - top, area.narrowWidth, area.narrowLimit - top,
        area.coreTop, area.coreBottom, area.coreRight) { width, narrow -> bannerLines(measurer, frame, width, narrow) }

/**
 * Whether the banner at [top] leaves the newest toast every line it outranks the banner with: its
 * title and synergy lines, or where no step holds them all, as many of them as it shows alone. The
 * toast steps its text down (or moves below or beside the Core) to make room for both; the banner
 * yields only when it cannot. The banner's event also shows elsewhere (build toast, weapon slot,
 * world).
 */
private fun bannerKeepsTheNewestToast(
    area: FeedArea,
    frame: HudFrame,
    measurer: TextMeasurer,
    notices: List<BuildNotificationProjection>,
    top: Float,
    gap: Float,
): Boolean {
    if (notices.isEmpty() || area.maxNotices == 0) return true
    val newest = notices[notices.size - 1]
    if (placeToast(area, frame, measurer, newest, 0, top, area.limit, relaxed = true) <= 0f) return true
    val alone = FeedLines.shownRequired()
    val banner = placeBanner(area, frame, measurer, top)
    if (banner <= 0f) return false
    return placeToast(area, frame, measurer, newest, 0, top + banner + gap, area.limit, relaxed = true) > 0f &&
        FeedLines.shownRequired() >= alone
}

/**
 * Lays out toast [notice] in feed position [slot] from [top] into [FeedLines] and returns its height
 * (0 = not shown); [ToastSpot] tells where it went. Each text step tries the band above the Core
 * (ending at [bandLimit]), then the band below the Core; then (portrait) each step tries the column
 * beside the Core. [relaxed]: should even the smallest step hold no place with every synergy line,
 * the title shows with the lines that fit, in the same order of places.
 */
private fun placeToast(
    area: FeedArea,
    frame: HudFrame,
    measurer: TextMeasurer,
    notice: BuildNotificationProjection,
    slot: Int,
    top: Float,
    bandLimit: Float,
    relaxed: Boolean,
): Float {
    val levels = area.levels
    val columns = if (area.sideWidth.isNaN()) 1 else 2
    val passes = levels * columns + if (relaxed) 1 else 0
    for (pass in 0 until passes) {
        val last = pass == levels * columns
        val level = if (last) levels - 1 else pass % levels
        val sidePass = !last && pass >= levels
        if (!sidePass) {
            var height = placeFeedPlate(frame, top, area.right, banner = false, area.maxWidth, bandLimit - top, area.narrowWidth,
                area.narrowLimit - top, area.coreTop, area.coreBottom, area.coreRight, last,
            ) { width, narrow -> noticeLines(notice, slot, measurer, frame, width, area.maxDetails, narrow, level) }
            if (height > 0f) return ToastSpot.set(top, bandLimit, side = false, height)
            if (!area.lowerTop.isNaN() && bandLimit != area.lowerLimit) {
                // Continue below the Core.
                val lower = max(top, area.lowerTop)
                height = placeFeedPlate(frame, lower, area.right, banner = false, area.maxWidth, area.lowerLimit - lower, area.narrowWidth,
                    area.narrowLimit - lower, area.coreTop, area.coreBottom, area.coreRight, last,
                ) { width, narrow -> noticeLines(notice, slot, measurer, frame, width, area.maxDetails, narrow, level) }
                if (height > 0f) return ToastSpot.set(lower, area.lowerLimit, side = false, height)
            }
        }
        if (!area.sideWidth.isNaN() && (sidePass || last)) {
            val height = placeFeedPlate(frame, top, area.right, banner = false, area.sideWidth, area.lowerLimit - top, Float.NaN, 0f,
                area.coreTop, area.coreBottom, area.coreRight, last,
            ) { width, _ -> noticeLines(notice, slot, measurer, frame, width, area.maxDetails, true, level) }
            if (height > 0f) return ToastSpot.set(top, bandLimit, side = true, height)
        }
    }
    return 0f
}

/**
 * The game's default text-size setting (`PlayerProfile.textScale`): at this setting the feed renders
 * its text at the board sizes; UI text follows the setting from there.
 */
private const val REFERENCE_TEXT_SCALE = 1.25f

/**
 * Half size of the zone around the screen center that phone plates keep clear: the Core's halo
 * (2.2 Core radii, 35 dp) with its stroke and a small gap.
 */
private const val CORE_CLEARANCE_DP = 46f

/**
 * Narrowest column beside the Core that a plate narrows to (landscape) or that a portrait toast
 * takes when neither band holds it. Lines that cannot show whole in it are not shown there.
 */
private const val NARROW_COLUMN_MIN_DP = 110f

/**
 * A toast that does not fit its room steps its text size down by [SHRINK_STEP] of text scale at a
 * time, never below the size of the smallest text setting ([SMALLEST_TEXT_SCALE]).
 */
private const val SMALLEST_TEXT_SCALE = 1f
private const val SHRINK_STEP = 0.125f
private const val SHRINK_LEVELS = 7

/** Text steps available at the text-size setting [scale]: from the setting down to the smallest one. */
private fun shrinkLevels(scale: Float): Int =
    min(SHRINK_LEVELS, 1 + ceil((scale - SMALLEST_TEXT_SCALE) / SHRINK_STEP - 0.001f).toInt().coerceAtLeast(0))

/** The feed's text sizes at step [level] relative to the setting [scale]. */
private fun shrinkFactor(level: Int, scale: Float): Float =
    if (level == 0) 1f else max(scale - level * SHRINK_STEP, SMALLEST_TEXT_SCALE) / scale

private const val NOTICE_LIFE_SECONDS = 6f
private const val MAX_NOTICES = 3

/**
 * Detail lines per toast. A build change toggles at most four synergies (the relic that leaves
 * ends its aspect pair and its named pair, the one that arrives starts at most the same two), so
 * every synergy line of a notice fits these slots.
 */
private const val DETAILS_PER_NOTICE = 4
private const val ENTRANCE_SECONDS = 0.3f

/** Text slots: the message title and detail, then per toast its title and detail lines. */
private const val MESSAGE_TITLE_SLOT = 0
private const val FEED_TEXT_SLOTS = 2 + MAX_NOTICES * (1 + DETAILS_PER_NOTICE)
private fun noticeTitleSlot(notice: Int) = 2 + notice * (1 + DETAILS_PER_NOTICE)

private val NoticePaths = arrayOf(HudPath.NOTICE_0, HudPath.NOTICE_1, HudPath.NOTICE_2)

/** Synergy switched on ("+ name") or off ("− name") in a build notice (`emitBuildChange`). */
internal fun isSynergyLine(detail: String): Boolean = detail.startsWith("+ ") || detail.startsWith("\u2212 ")

/**
 * Which details of a notice appear: every synergy line, then stat deltas in order while the
 * layout's detail budget lasts, kept in the notice's order. Draw-thread scratch, no allocation.
 */
private object FeedPick {
    private val picked = IntArray(DETAILS_PER_NOTICE)

    operator fun get(index: Int): Int = picked[index]

    /** Fills the picked detail indices and returns their count. */
    fun pick(details: List<String>, budget: Int): Int {
        var synergies = 0
        for (index in details.indices) if (isSynergyLine(details[index])) synergies++
        var deltas = (min(budget, DETAILS_PER_NOTICE) - synergies).coerceAtLeast(0)
        var count = 0
        for (index in details.indices) {
            if (count == DETAILS_PER_NOTICE) break
            if (isSynergyLine(details[index])) {
                picked[count++] = index
            } else if (deltas > 0) {
                picked[count++] = index
                deltas--
            }
        }
        return count
    }
}

/**
 * Lays out one plate at [top] into [FeedLines] and returns its height (0 = not shown): full width
 * within [room] while it stays clear of the Core's band, otherwise (or when it does not fit) in the
 * narrow column beside the Core within [narrowRoom], where the layout has one. A plate never meets
 * the Core. [relaxed] shows the title with the required lines that fit instead of nothing.
 */
private inline fun placeFeedPlate(
    frame: HudFrame,
    top: Float,
    right: Float,
    banner: Boolean,
    maxWidth: Float,
    room: Float,
    narrowWidth: Float,
    narrowRoom: Float,
    coreTop: Float,
    coreBottom: Float,
    coreRight: Float,
    relaxed: Boolean = false,
    build: (width: Float, narrow: Boolean) -> FeedLines,
): Float {
    var height = build(maxWidth, false).fit(frame, room, banner, relaxed)
    val meetsCore = height > 0f && top + height > coreTop && top < coreBottom && right - FeedLines.plateWidth(frame, banner) < coreRight
    if (narrowWidth.isNaN()) return if (meetsCore) 0f else height
    if (height <= 0f || meetsCore) height = build(narrowWidth, true).fit(frame, narrowRoom, banner, relaxed)
    return height
}

/** The lines of the current message's banner, [narrow] in the column beside the Core. */
private fun bannerLines(measurer: TextMeasurer, frame: HudFrame, maxWidth: Float, narrow: Boolean): FeedLines {
    val lines = FeedLines.clear(narrow, measurer.scale, level = 0)
    lines.title(measurer, frame, MESSAGE_TITLE_SLOT, FeedScratch.message.title, maxWidth, banner = true)
    val detail = FeedScratch.message.detail
    // Message details arrive display-cased ("Lvl 7" keeps its short level form).
    if (detail != null) lines.detail(measurer, frame, MESSAGE_TITLE_SLOT + 1, detail, maxWidth, banner = true, required = true, uppercase = false)
    return lines
}

/** The lines of the toast in feed position [slot] for [notice] at text step [level] (measured once per change). */
private fun noticeLines(
    notice: BuildNotificationProjection,
    slot: Int,
    measurer: TextMeasurer,
    frame: HudFrame,
    maxWidth: Float,
    maxDetails: Int,
    narrow: Boolean,
    level: Int,
): FeedLines {
    val language = measurer.language
    val titleSlot = noticeTitleSlot(slot)
    val lines = FeedLines.clear(narrow, measurer.scale, level)
    lines.title(measurer, frame, titleSlot, FeedScratch.titles[slot].of(notice.title, language), maxWidth, banner = false)
    val picked = FeedPick.pick(notice.details, maxDetails)
    if (picked == 0) {
        lines.detail(measurer, frame, titleSlot + 1, FeedScratch.fallback.of(language, 0L) { language.text(GameplayText.BuildUpdated) },
            maxWidth, banner = false, required = false, uppercase = true)
    }
    for (line in 0 until picked) {
        val source = notice.details[FeedPick[line]]
        val text = FeedScratch.details[slot * DETAILS_PER_NOTICE + line].of(source, language)
        lines.detail(measurer, frame, titleSlot + 1 + line, text, maxWidth, banner = false, required = isSynergyLine(source), uppercase = true)
    }
    return lines
}

/** Banner entrance (`kk-fx-banner`): slides in from the left with the Pull overshoot. */
private fun entranceShift(age: Float): Float {
    if (age >= ENTRANCE_SECONDS) return 0f
    return -(1f - KkEase.Pull.transform((age / ENTRANCE_SECONDS).coerceIn(0f, 1f)))
}

private fun entranceAlpha(age: Float): Float = (age / (ENTRANCE_SECONDS * 0.4f)).coerceIn(0f, 1f)

/**
 * The lines of one feed plate (title first, then details), measured once per change through
 * [FeedTexts] at one text step. [fit] decides which lines show: the title and required (synergy)
 * lines always, optional lines in order while they fit. Draw-thread scratch: reused for every plate.
 */
private object FeedLines {
    private val layouts = arrayOfNulls<TextLayoutResult>(1 + DETAILS_PER_NOTICE)
    private val widths = FloatArray(1 + DETAILS_PER_NOTICE)
    private val required = BooleanArray(1 + DETAILS_PER_NOTICE)
    private val whole = BooleanArray(1 + DETAILS_PER_NOTICE)
    private val visible = BooleanArray(1 + DETAILS_PER_NOTICE)
    var count = 0
        private set

    /**
     * Text slots of each text step, and of the narrow column (beside the Core), are kept apart from
     * the others, so a plate that settles on a step or column re-measures nothing per frame.
     */
    private var bank = 0
    private var shrink = 1f

    fun clear(narrow: Boolean, scale: Float, level: Int): FeedLines {
        count = 0
        bank = (level * 2 + if (narrow) 1 else 0) * FEED_TEXT_SLOTS
        shrink = shrinkFactor(level, scale)
        return this
    }

    operator fun get(index: Int): TextLayoutResult = layouts[index]!!

    fun isVisible(index: Int): Boolean = visible[index]

    /** How many of the title and required lines the last [fit] that placed the plate shows. */
    fun shownRequired(): Int {
        var shown = 0
        for (index in 0 until count) if (required[index] && visible[index]) shown++
        return shown
    }

    fun title(measurer: TextMeasurer, frame: HudFrame, slot: Int, text: String, maxWidth: Float, banner: Boolean) {
        val size = (if (frame.regular) frame.t(if (banner) 24f else 20f) else if (banner) 18f else 16f) / REFERENCE_TEXT_SCALE * shrink
        val available = maxWidth - startPadding(frame, banner) - endPadding(frame) - leadSpace(frame, banner)
        layouts[0] = FeedTexts.fit(bank + slot, measurer, text, mono = false, size, available.coerceAtLeast(1f), lines = 1, uppercase = true)
        widths[0] = FeedTexts.contentWidth(bank + slot)
        whole[0] = FeedTexts.isWhole(bank + slot)
        required[0] = true
        count = 1
    }

    fun detail(
        measurer: TextMeasurer,
        frame: HudFrame,
        slot: Int,
        text: String,
        maxWidth: Float,
        banner: Boolean,
        required: Boolean,
        uppercase: Boolean,
    ) {
        if (count == 0 || count >= layouts.size) return
        val size = (if (frame.regular) frame.t(11f) else 10f) / REFERENCE_TEXT_SCALE * shrink
        val available = maxWidth - startPadding(frame, banner) - endPadding(frame) - leadSpace(frame, banner)
        // Details take up to two lines, then shrink or wrap further on their plate rather than being cut.
        layouts[count] = FeedTexts.fit(bank + slot, measurer, text, mono = true, size, available.coerceAtLeast(1f), lines = 2, uppercase = uppercase)
        widths[count] = FeedTexts.contentWidth(bank + slot)
        whole[count] = FeedTexts.isWhole(bank + slot)
        this.required[count] = required
        count++
    }

    /** Height of the plate with only its title and required lines (infinite when one of them is cut). */
    fun requiredHeight(frame: HudFrame, banner: Boolean): Float {
        if (count == 0) return 0f
        for (index in 0 until count) if (required[index] && !whole[index]) return Float.POSITIVE_INFINITY
        var height = verticalPadding(frame) * 2f + titleRow(frame, banner)
        for (index in 1 until count) if (required[index]) height += detailGap(frame) + layouts[index]!!.kkBoxHeight
        return height
    }

    /** The title row: the board row height, taller when the title grows or wraps. */
    fun titleRow(frame: HudFrame, banner: Boolean): Float = max(titleRowHeight(frame, banner), layouts[0]!!.kkBoxHeight)

    /**
     * Plate height for the lines that fit in [room]: 0 when the title and the required lines do not
     * fit; optional lines follow in order until the first that does not fit. [relaxed] only needs the
     * title to fit: required lines then follow in order while they fit, before any optional line. A
     * line that cannot show whole in the plate's width (it would be cut) never fits.
     */
    fun fit(frame: HudFrame, room: Float, banner: Boolean, relaxed: Boolean = false): Float {
        if (count == 0 || !whole[0]) return 0f
        var height = if (relaxed) verticalPadding(frame) * 2f + titleRow(frame, banner) else requiredHeight(frame, banner)
        if (height > room) return 0f
        visible[0] = true
        var open = true
        for (index in 1 until count) {
            if (!required[index]) continue
            if (!relaxed) {
                visible[index] = true
                continue
            }
            val next = height + detailGap(frame) + layouts[index]!!.kkBoxHeight
            visible[index] = open && whole[index] && next <= room
            if (visible[index]) height = next else open = false
        }
        for (index in 1 until count) {
            if (required[index]) continue
            val next = height + detailGap(frame) + layouts[index]!!.kkBoxHeight
            visible[index] = open && whole[index] && next <= room
            if (visible[index]) height = next else open = false
        }
        return height
    }

    fun plateWidth(frame: HudFrame, banner: Boolean): Float {
        var content = 0f
        for (index in 0 until count) if (visible[index]) content = max(content, widths[index])
        return startPadding(frame, banner) + leadSpace(frame, banner) + content + endPadding(frame)
    }
}

private fun startPadding(frame: HudFrame, banner: Boolean) = frame.u(if (banner) 18f else 12f)
private fun endPadding(frame: HudFrame) = frame.u(18f)
private fun leadSize(frame: HudFrame) = frame.u(if (frame.regular) 14f else 11f)
private fun leadSpace(frame: HudFrame, banner: Boolean) = if (banner) 0f else leadSize(frame) + frame.u(12f)
private fun verticalPadding(frame: HudFrame) = frame.u(if (frame.regular) 9f else 6f)
private fun detailGap(frame: HudFrame) = frame.u(3f)
private fun titleRowHeight(frame: HudFrame, banner: Boolean) = frame.u(if (frame.regular) 26f else 22f) + if (banner) frame.u(2f) else 0f

/**
 * One feed plate right-aligned at [right]: banner (ink-4, message) or toast (ink-2 + gem, build).
 * A fading plate is composited through one layer at [alpha]; its text is always painted opaque, so
 * the paragraphs keep their colour and are never laid out again while the plate fades.
 */
private fun DrawScope.drawFeedPlate(
    measurer: TextMeasurer,
    frame: HudFrame,
    path: HudPath,
    plate: Int,
    lines: FeedLines,
    right: Float,
    top: Float,
    height: Float,
    alpha: Float,
    shift: Float,
    banner: Boolean,
) {
    if (alpha <= 0f) return
    val width = lines.plateWidth(frame, banner)
    val left = right - width
    val bottom = top + height
    val fading = alpha < 1f
    if (fading) drawContext.canvas.saveLayer(FeedLayer.bounds(plate, left, top, right, bottom), FeedLayer.paint(alpha))
    drawPath(HudDrawCache.paths.slab(path.ordinal, left, top, right, bottom, frame.u(10f)), if (banner) Kk.Ink4 else Kk.Ink2)
    FeedProbe.plate(left + shift, top, right + shift, bottom, banner)
    val textLeft = left + startPadding(frame, banner) + leadSpace(frame, banner)
    val rowTop = top + verticalPadding(frame)
    val row = lines.titleRow(frame, banner)
    val rowCenter = rowTop + row * 0.5f
    if (!banner) {
        val lead = leadSize(frame)
        drawKkGem(Offset(left + startPadding(frame, banner) + lead * 0.5f, rowCenter), measurer.roles.you, lead / density)
    }
    val title = lines[0]
    drawKkText(title, textLeft, rowCenter, Kk.Bone, valign = KkVAlign.CENTER)
    val titleTop = rowCenter - (title.kkBoxTop + title.kkBoxBottom) * 0.5f
    FeedProbe.line(title, textLeft + shift, titleTop, textLeft + shift + title.size.width, titleTop + title.size.height)
    var y = rowTop + row
    for (index in 1 until lines.count) {
        if (!lines.isVisible(index)) continue
        val detail = lines[index]
        y += detailGap(frame)
        drawKkText(detail, textLeft, y, Kk.Mute)
        val detailTop = y - detail.kkBoxTop
        FeedProbe.line(detail, textLeft + shift, detailTop, textLeft + shift + detail.size.width, detailTop + detail.size.height)
        y += detail.kkBoxHeight
    }
    if (fading) drawContext.canvas.restore()
}

/** The layer paint and per-plate layer bounds of fading plates (rebuilt only when a plate moves). */
private object FeedLayer {
    private val layerPaint = Paint()
    private val keys = FloatArray((1 + MAX_NOTICES) * 4) { Float.NaN }
    private val rects = arrayOfNulls<Rect>(1 + MAX_NOTICES)

    fun paint(alpha: Float): Paint {
        layerPaint.alpha = alpha
        return layerPaint
    }

    fun bounds(plate: Int, left: Float, top: Float, right: Float, bottom: Float): Rect {
        val base = plate * 4
        val cached = rects[plate]
        if (cached != null && keys[base] == left && keys[base + 1] == top && keys[base + 2] == right && keys[base + 3] == bottom) return cached
        keys[base] = left
        keys[base + 1] = top
        keys[base + 2] = right
        keys[base + 3] = bottom
        return Rect(left, top, right, bottom).also { rects[plate] = it }
    }
}

/**
 * Feed text layouts per slot, fitted once per change: the text wraps at word boundaries on its
 * plate and shrinks when a word would otherwise break or the lines would be cut, so it is never
 * ellipsized. Keyed by (text, typography, measurer, scale, size, width, flags); one bank of slots
 * per text step and column.
 */
private object FeedTexts {
    private const val SLOTS = FEED_TEXT_SLOTS * 2 * SHRINK_LEVELS
    private val texts = arrayOfNulls<String>(SLOTS)
    private val typographies = arrayOfNulls<Any>(SLOTS)
    private val delegates = arrayOfNulls<Any>(SLOTS)
    private val scales = FloatArray(SLOTS)
    private val sizes = FloatArray(SLOTS)
    private val widths = FloatArray(SLOTS)
    private val flags = IntArray(SLOTS)
    private val layouts = arrayOfNulls<TextLayoutResult>(SLOTS)
    private val contentWidths = FloatArray(SLOTS)
    private val wholes = BooleanArray(SLOTS)

    fun fit(slot: Int, measurer: TextMeasurer, text: String, mono: Boolean, size: Float, width: Float, lines: Int, uppercase: Boolean): TextLayoutResult {
        val flag = (if (mono) 1 else 0) or (if (uppercase) 2 else 0) or (lines shl 2)
        val cached = layouts[slot]
        if (cached != null && typographies[slot] === measurer.typography && delegates[slot] === measurer.delegate &&
            scales[slot] == measurer.scale && sizes[slot] == size && widths[slot] == width && flags[slot] == flag && texts[slot] == text
        ) return cached
        val layout = fitFeedText(measurer, text, mono, size, width, lines, uppercase)
        texts[slot] = text
        typographies[slot] = measurer.typography
        delegates[slot] = measurer.delegate
        scales[slot] = measurer.scale
        sizes[slot] = size
        widths[slot] = width
        flags[slot] = flag
        layouts[slot] = layout
        var content = 0f
        for (line in 0 until layout.lineCount) content = max(content, layout.getLineRight(line))
        contentWidths[slot] = min(content, layout.size.width.toFloat())
        wholes[slot] = layout.fitsWithoutCuts()
        return layout
    }

    /** Width of the widest line of the slot's last fitted layout. */
    fun contentWidth(slot: Int): Float = contentWidths[slot]

    /** Whether the slot's last fitted layout shows its whole text (see [fitsWithoutCuts]). */
    fun isWhole(slot: Int): Boolean = wholes[slot]
}

/**
 * Size steps (fractions of the size): long text first shrinks a little on the requested line
 * count, then wraps onto up to [EXTRA_LINES] more lines at the largest size that fits. When none
 * fits, the last attempt is returned and [FeedLines] does not show it.
 */
private val SameLinesSteps = floatArrayOf(1f, 0.92f, 0.85f)
private val MoreLinesSteps = floatArrayOf(1f, 0.92f, 0.85f, 0.78f, 0.72f, 0.66f, 0.6f)
private const val EXTRA_LINES = 2

private fun fitFeedText(measurer: TextMeasurer, text: String, mono: Boolean, size: Float, width: Float, lines: Int, uppercase: Boolean): TextLayoutResult {
    var layout = measureFeedText(measurer, text, mono, size, width, lines, uppercase)
    if (layout.fitsWithoutCuts()) return layout
    for (step in 1 until SameLinesSteps.size) {
        layout = measureFeedText(measurer, text, mono, size * SameLinesSteps[step], width, lines, uppercase)
        if (layout.fitsWithoutCuts()) return layout
    }
    for (step in MoreLinesSteps.indices) for (extra in 1..EXTRA_LINES) {
        layout = measureFeedText(measurer, text, mono, size * MoreLinesSteps[step], width, lines + extra, uppercase)
        if (layout.fitsWithoutCuts()) return layout
    }
    return layout
}

private fun measureFeedText(measurer: TextMeasurer, text: String, mono: Boolean, size: Float, width: Float, lines: Int, uppercase: Boolean) =
    measureKkText(
        measurer, text,
        if (mono) measurer.typography.monoStyle(size) else measurer.typography.condStyle(size, lineHeightEm = 1f),
        uppercase, maxWidth = width, maxLines = lines,
    )

/** Whether every character shows, without an ellipsis and without a line break inside a word. */
internal fun TextLayoutResult.fitsWithoutCuts(): Boolean {
    if (didOverflowWidth || didOverflowHeight || isLineEllipsized(lineCount - 1)) return false
    val shown = layoutInput.text.text
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end <= 0 || end >= shown.length) continue
        if (!shown[end - 1].isWhitespace() && !shown[end].isWhitespace() && shown[end - 1] != '-') return false
    }
    return true
}

/**
 * Where the feed drew its plates and lines in the last frame (px, screen space), for layout tests:
 * plain slots rewritten every frame, no allocation. [begin] clears the frame's marks.
 */
internal object FeedProbe {
    private val lineLayouts = arrayOfNulls<TextLayoutResult>(FEED_TEXT_SLOTS)
    private val lineBoxes = FloatArray(FEED_TEXT_SLOTS * 4)
    private val linePlates = IntArray(FEED_TEXT_SLOTS)
    private val plateBoxes = FloatArray((1 + MAX_NOTICES) * 4)
    private val plateBanners = BooleanArray(1 + MAX_NOTICES)
    var lineCount = 0
        private set
    var plateCount = 0
        private set

    fun begin() {
        lineCount = 0
        plateCount = 0
    }

    fun plate(left: Float, top: Float, right: Float, bottom: Float, banner: Boolean) {
        if (plateCount >= plateBanners.size) return
        val base = plateCount * 4
        plateBoxes[base] = left
        plateBoxes[base + 1] = top
        plateBoxes[base + 2] = right
        plateBoxes[base + 3] = bottom
        plateBanners[plateCount] = banner
        plateCount++
    }

    fun line(layout: TextLayoutResult, left: Float, top: Float, right: Float, bottom: Float) {
        if (lineCount >= lineLayouts.size || plateCount == 0) return
        val base = lineCount * 4
        lineLayouts[lineCount] = layout
        lineBoxes[base] = left
        lineBoxes[base + 1] = top
        lineBoxes[base + 2] = right
        lineBoxes[base + 3] = bottom
        linePlates[lineCount] = plateCount - 1
        lineCount++
    }

    /** Test accessors. */
    fun lineLayout(index: Int): TextLayoutResult = lineLayouts[index]!!
    fun lineBox(index: Int): Rect = Rect(lineBoxes[index * 4], lineBoxes[index * 4 + 1], lineBoxes[index * 4 + 2], lineBoxes[index * 4 + 3])
    fun linePlate(index: Int): Int = linePlates[index]
    fun plateBox(index: Int): Rect = Rect(plateBoxes[index * 4], plateBoxes[index * 4 + 1], plateBoxes[index * 4 + 2], plateBoxes[index * 4 + 3])
    fun isBanner(index: Int): Boolean = plateBanners[index]
}

/** Game messages the HUD shows as state (stamps, stripes, glows, pips) rather than as text. */
private val StateMessages = hashSetOf("OVERHEAT", "POLARITY FIELD STRAIN", "DASH ONLINE", "KINETIC OVERDRIVE")

/** Whether the current run message appears in the feed (see [drawHudFeed]). */
internal fun GameplayRenderModel.showsMessage(): Boolean {
    if (messageTime <= 0f || message.isBlank() || message in StateMessages) return false
    val definitions = content.pointsOfInterest.definitions
    for (index in definitions.indices) if (definitions[index].instruction == message) return false
    return true
}

/**
 * Message tails that only tell the player what to do; the feed shows the event, not the
 * instruction (explanations live behind (!) buttons).
 */
private val InstructionTails = hashSetOf("CHOOSE A COURSE")

/** A weapon amplify that reaches no mastery milestone (`amplifyCurrentWeapon`): " // LEVEL n". */
private val LevelTail = Regex("LEVEL ([0-9]+)")

/** A relic replace (`replaceRelic`): " // SLOT n BOUND"; the slot's position is not shown. */
private val SlotBoundTail = Regex("SLOT [0-9]+ BOUND")
private const val BOUND_TAIL = "BOUND"

/**
 * A run message as feed text: the localized title (before the first " // ") and its display-cased
 * detail, or no detail when the tail is an instruction. A weapon level reads in the short level
 * form ("Lvl 7" / "Ур. 7"); a replaced relic reads as bound, without its slot number. Allocates;
 * called only when the message changes.
 */
internal fun feedMessageParts(message: String, language: AppLanguage): Pair<String, String?> {
    val localized = message.localizedContent(language)
    val split = localized.indexOf(" // ")
    if (split < 0) return localized to null
    val title = localized.substring(0, split)
    val sourceSplit = message.indexOf(" // ")
    val tail = if (sourceSplit >= 0) message.substring(sourceSplit + 4).trim() else ""
    if (tail in InstructionTails) return title to null
    LevelTail.matchEntire(tail)?.let { level ->
        return title to language.text(HudRedesignText.WeaponLevel, level.groupValues[1].toInt())
    }
    if (SlotBoundTail.matches(tail)) return title to BOUND_TAIL.localizedContent(language).uppercase()
    return title to localized.substring(split + 4).replace(" // ", "  ").uppercase()
}

/** The current message's feed text, rebuilt only when the message or language changes. */
private class SplitMessage {
    private var source: String? = null
    private var language: AppLanguage? = null
    var title = ""
        private set
    var detail: String? = null
        private set

    fun update(message: String, language: AppLanguage) {
        if (message == source && language === this.language) return
        source = message
        this.language = language
        val parts = feedMessageParts(message, language)
        title = parts.first
        detail = parts.second
    }
}

/**
 * A string localized once per (source, language). A synergy line keeps its sign on the name's line
 * (a no-break space follows "+" / "−") when it wraps.
 */
private class LocalizedText {
    private var source: String? = null
    private var language: AppLanguage? = null
    private var text = ""

    fun of(value: String, language: AppLanguage): String {
        if (value != source || language !== this.language) {
            source = value
            this.language = language
            val localized = value.localizedContent(language)
            text = if (isSynergyLine(localized)) localized[0] + "\u00A0" + localized.substring(2) else localized
        }
        return text
    }
}

private object FeedScratch {
    val message = SplitMessage()
    val titles = Array(MAX_NOTICES) { LocalizedText() }
    val details = Array(MAX_NOTICES * DETAILS_PER_NOTICE) { LocalizedText() }
    val fallback = HudKeyedText()
}

private val TrialFeedLayout = HudTrialPanelLayout()
private val FeedBoss = BossTarget()
