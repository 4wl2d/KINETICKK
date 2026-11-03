// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_BOSS_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_PANEL_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.layout.*

/**
 * Where the feed docks in the current frame (px): its right edge and first plate top, the plate
 * width and band limit, the Core's zone, and per layout the narrow column right of the Core
 * (landscape), the band below the Core and the column beside it (portrait). Draw-thread scratch,
 * rewritten every frame.
 */
internal object FeedArea {
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
                // Stop above the control row (Dash, Brake and the skill squares) that sits over the loadout.
                var rowTop = frame.height
                forEachRunningControlBounds(frame.width, frame.height, density, RunningControlMask.ALL) { target, _, controlTop, _, _ ->
                    if (target != RunningControlTarget.PAUSE && target != RunningControlTarget.APEX &&
                        target != RunningControlTarget.PERFORMANCE
                    ) rowTop = min(rowTop, controlTop)
                }
                limit = rowTop - frame.u(12f)
            }
            GameplayLayoutMode.COMPACT_LANDSCAPE -> {
                right = frame.width - frame.margin - compactLoadoutWidth(engine.content.relicPolicy.maxSlots.coerceIn(0, 8), frame.unit) - frame.u(12f)
                maxWidth = min(frame.u(260f) * frame.factor, right - frame.width * 0.5f + frame.u(60f)).coerceAtLeast(frame.u(140f))
                top = frame.u(62f)
                // Under the elite / Architect block whenever a plate may reach its span: the boss name
                // grows with the text size and pushes its bar down.
                val boss = FeedBoss.select(engine)
                if (boss.id >= 0) {
                    val wide = wideBossBlock(boss.rank)
                    if (right - maxWidth < frame.width * 0.5f + HudTopGeometry.bossBarWidth(frame, wide) * 0.5f) {
                        top = max(top, HudTopGeometry.bossBottom(frame, wide, measurer.scale, rail = boss.statusBits != 0) + frame.u(8f))
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
                forEachRunningControlBounds(frame.width, frame.height, density, RunningControlMask.ALL) { _, controlLeft, controlTop, controlRight, _ ->
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
internal object ToastSpot {
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
internal fun placeBanner(area: FeedArea, frame: HudFrame, measurer: TextMeasurer, top: Float): Float =
    placeFeedPlate(frame, top, area.right, banner = true, area.maxWidth, area.limit - top, area.narrowWidth, area.narrowLimit - top,
        area.coreTop, area.coreBottom, area.coreRight) { width, narrow -> bannerLines(measurer, frame, width, narrow) }

/**
 * Whether the banner at [top] leaves the newest toast every line it outranks the banner with: its
 * title and synergy lines, or where no step holds them all, as many of them as it shows alone. The
 * toast steps its text down (or moves below or beside the Core) to make room for both; the banner
 * yields only when it cannot. The banner's event also shows elsewhere (build toast, weapon slot,
 * world).
 */
internal fun bannerKeepsTheNewestToast(
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
internal fun placeToast(
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
 * Half size of the zone around the screen center that phone plates (feed, trial rules) keep clear:
 * the Core's halo (2.2 Core radii, 35 dp) with its stroke and a small gap.
 */
internal const val CORE_CLEARANCE_DP = 46f

/**
 * Narrowest column beside the Core that a plate narrows to (landscape) or that a portrait toast (or
 * the trial rules) takes when neither band holds it. Lines that cannot show whole in it are not
 * shown there.
 */
internal const val NARROW_COLUMN_MIN_DP = 110f

/**
 * A toast (or the trial rules) that does not fit its room steps its text size down by [SHRINK_STEP]
 * of text scale at a time, never below the size of the smallest text setting ([SMALLEST_TEXT_SCALE]).
 */
internal const val SMALLEST_TEXT_SCALE = 1f
internal const val SHRINK_STEP = 0.125f
internal const val SHRINK_LEVELS = 7

/** Text steps available at the text-size setting [scale]: from the setting down to the smallest one. */
private fun shrinkLevels(scale: Float): Int =
    min(SHRINK_LEVELS, 1 + ceil((scale - SMALLEST_TEXT_SCALE) / SHRINK_STEP - 0.001f).toInt().coerceAtLeast(0))

/** The feed's text sizes at step [level] relative to the setting [scale]. */
internal fun shrinkFactor(level: Int, scale: Float): Float =
    if (level == 0) 1f else max(scale - level * SHRINK_STEP, SMALLEST_TEXT_SCALE) / scale

internal const val MAX_NOTICES = 3

/**
 * Detail lines per toast. A build change toggles at most four synergies (the relic that leaves
 * ends its aspect pair and its named pair, the one that arrives starts at most the same two), so
 * every synergy line of a notice fits these slots.
 */
internal const val DETAILS_PER_NOTICE = 4

/** Text slots: the message title and detail, then per toast its title and detail lines. */
private const val MESSAGE_TITLE_SLOT = 0
internal const val FEED_TEXT_SLOTS = 2 + MAX_NOTICES * (1 + DETAILS_PER_NOTICE)
private fun noticeTitleSlot(notice: Int) = 2 + notice * (1 + DETAILS_PER_NOTICE)

/** Resonance tier pips after a tier line ("+ Ram" and three sheared pips, the reached ones lit). */
internal const val TIER_PIPS = 3
internal const val TIER_PIP_WIDTH_DP = 8f
internal const val TIER_PIP_HEIGHT_DP = 5f
internal const val TIER_PIP_GAP_DP = 2f
private const val TIER_PIPS_GAP_DP = 7f

private fun tierPipsWidth(frame: HudFrame, shrink: Float): Float =
    (TIER_PIPS * TIER_PIP_WIDTH_DP + (TIER_PIPS - 1) * TIER_PIP_GAP_DP) * frame.unit * shrink

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
    return lines.seal(measurer)
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
        val localized = FeedScratch.details[slot * DETAILS_PER_NOTICE + line]
        val text = localized.of(source, language)
        lines.detail(measurer, frame, titleSlot + 1 + line, text, maxWidth, banner = false, required = isSynergyLine(source), uppercase = true,
            tier = localized.tier, element = localized.element)
    }
    return lines.seal(measurer)
}

/**
 * The lines of one feed plate (title first, then details), measured once per change through
 * [FeedTexts] at one text step. The details share one size: [seal] lays them all out at the size
 * the tightest of them needs on the plate. [fit] decides which lines show: the title and required
 * (synergy) lines always, optional lines in order while they fit. Draw-thread scratch: reused for
 * every plate; a builder adds the title and details, then calls [seal].
 */
internal object FeedLines {
    private val layouts = arrayOfNulls<TextLayoutResult>(1 + DETAILS_PER_NOTICE)
    private val widths = FloatArray(1 + DETAILS_PER_NOTICE)
    private val required = BooleanArray(1 + DETAILS_PER_NOTICE)
    private val whole = BooleanArray(1 + DETAILS_PER_NOTICE)
    private val visible = BooleanArray(1 + DETAILS_PER_NOTICE)

    /** Details waiting for [seal]: text slot, text, casing and the size fraction each needs alone. */
    private val detailSlots = IntArray(1 + DETAILS_PER_NOTICE)
    private val detailTexts = arrayOfNulls<String>(1 + DETAILS_PER_NOTICE)
    private val detailUpper = BooleanArray(1 + DETAILS_PER_NOTICE)
    private val detailNeeds = FloatArray(1 + DETAILS_PER_NOTICE)

    /** Resonance tier (1..3) a line ends in as pips, 0 for none, and the line's element (-1 none). */
    private val tiers = IntArray(1 + DETAILS_PER_NOTICE)
    private val elements = IntArray(1 + DETAILS_PER_NOTICE)
    private val trailings = FloatArray(1 + DETAILS_PER_NOTICE)
    private val gaps = FloatArray(1 + DETAILS_PER_NOTICE)
    private val pipLeft = FloatArray(1 + DETAILS_PER_NOTICE)
    private val pipCenter = FloatArray(1 + DETAILS_PER_NOTICE)
    private var detailSize = 0f
    private var detailWidth = 0f
    var count = 0
        private set

    /**
     * Text slots of each text step, and of the narrow column (beside the Core), are kept apart from
     * the others, so a plate that settles on a step or column re-measures nothing per frame.
     */
    private var bank = 0
    private var shrink = 1f

    /** The text step's size factor (pips shrink with the text). */
    val shrinkFactor: Float get() = shrink

    fun clear(narrow: Boolean, scale: Float, level: Int): FeedLines {
        count = 0
        bank = (level * 2 + if (narrow) 1 else 0) * FEED_TEXT_SLOTS
        shrink = shrinkFactor(level, scale)
        return this
    }

    operator fun get(index: Int): TextLayoutResult = layouts[index]!!

    fun isVisible(index: Int): Boolean = visible[index]

    fun tier(index: Int): Int = tiers[index]

    fun element(index: Int): Int = elements[index]

    /** Where a tier line's pips start (px right of the text's left) and their center (px below the line box top). */
    fun pipLeft(index: Int): Float = pipLeft[index]

    fun pipCenter(index: Int): Float = pipCenter[index]

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
        tiers[0] = 0
        elements[0] = -1
        trailings[0] = 0f
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
        tier: Int = 0,
        element: Int = -1,
    ) {
        if (count == 0 || count >= layouts.size) return
        detailSize = (if (frame.regular) frame.t(11f) else 10f) / REFERENCE_TEXT_SCALE * shrink
        detailWidth = (maxWidth - startPadding(frame, banner) - endPadding(frame) - leadSpace(frame, banner)).coerceAtLeast(1f)
        val gap = if (tier > 0) frame.u(TIER_PIPS_GAP_DP) * shrink else 0f
        val trailing = if (tier > 0) tierPipsWidth(frame, shrink) + gap else 0f
        // Details take up to two lines, then shrink or wrap further on their plate rather than being cut.
        detailSlots[count] = bank + slot
        detailTexts[count] = text
        detailUpper[count] = uppercase
        detailNeeds[count] = FeedTexts.need(bank + slot, measurer, text, mono = true, detailSize, (detailWidth - trailing).coerceAtLeast(1f),
            lines = 2, uppercase)
        tiers[count] = tier
        elements[count] = element
        trailings[count] = trailing
        gaps[count] = gap
        this.required[count] = required
        count++
    }

    /**
     * Lays the details out at one size: the smallest fraction any of them needs to show whole on the
     * plate, so a long word that shrinks its own line shrinks its sibling lines with it. A detail
     * that cannot show whole at any step keeps no say (it is not shown).
     */
    fun seal(measurer: TextMeasurer): FeedLines {
        var cap = 1f
        for (index in 1 until count) if (!detailNeeds[index].isNaN()) cap = min(cap, detailNeeds[index])
        for (index in 1 until count) {
            val slot = detailSlots[index]
            layouts[index] = FeedTexts.fit(slot, measurer, detailTexts[index]!!, mono = true, detailSize,
                (detailWidth - trailings[index]).coerceAtLeast(1f), lines = 2, uppercase = detailUpper[index], cap = cap)
            widths[index] = FeedTexts.contentWidth(slot) + trailings[index]
            whole[index] = FeedTexts.isWhole(slot)
            pipLeft[index] = FeedTexts.lastLineRight(slot) + gaps[index]
            pipCenter[index] = FeedTexts.lastLineCenter(slot)
        }
        return this
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

internal fun startPadding(frame: HudFrame, banner: Boolean) = frame.u(if (banner) 18f else 12f)
private fun endPadding(frame: HudFrame) = frame.u(18f)
internal fun leadSize(frame: HudFrame) = frame.u(if (frame.regular) 14f else 11f)
internal fun leadSpace(frame: HudFrame, banner: Boolean) = if (banner) 0f else leadSize(frame) + frame.u(12f)
internal fun verticalPadding(frame: HudFrame) = frame.u(if (frame.regular) 9f else 6f)
internal fun detailGap(frame: HudFrame) = frame.u(3f)
private fun titleRowHeight(frame: HudFrame, banner: Boolean) = frame.u(if (frame.regular) 26f else 22f) + if (banner) frame.u(2f) else 0f

private val TrialFeedLayout = HudTrialPanelLayout()
private val FeedBoss = BossTarget()
