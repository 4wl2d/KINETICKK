// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_BOSS_ROW_DP
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.layout.*

internal fun portraitRowCenter(frame: HudFrame): Float = frame.top + frame.u(11f + 20f)

internal fun DrawScope.drawClockAndBoss(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    boss: BossTarget,
    renderTime: Float,
    memory: HudPresentationMemory?,
) {
    val roles = measurer.roles
    val architect = boss.type == EnemyType.ARCHITECT
    val text = HudScratch.timer.of(engine.elapsed.toLong())
    // Display type: the same size at every text-size setting.
    val layout = HudDrawCache.layout(HudText.TIMER, HudMeasurers.display(measurer), text,
        measurer.typography.wideStyle(HudTopGeometry.clockSize(frame), tabular = true, lineHeightEm = WIDE_LINE_HEIGHT_EM))
    val color = if (architect) roles.threat else Kk.Bone
    val x = frame.width * 0.5f
    val clockTop = HudTopGeometry.clockTop(frame)
    drawKkText(layout, x, clockTop, color, KkAlign.CENTER)
    HudLayoutProbe.record(HudBlock.CLOCK, x - layout.size.width * 0.5f, clockTop, x + layout.size.width * 0.5f, clockTop + layout.kkBoxHeight)
    if (boss.id < 0) return
    val ghost = if (memory != null) memory.bossGhost(renderTime) else 0f
    drawBossBar(engine, measurer, frame, boss, HudTopGeometry.bossTop(frame), HudTopGeometry.bossBarWidth(frame, wideBossBlock(boss.rank)), ghost)
}

/** Line height of the wide display type (`.hud-num`, `.t-wide`). */
internal const val WIDE_LINE_HEIGHT_EM = 0.9f

/** Line height of the condensed type (`.t-cond`). */
internal const val COND_LINE_HEIGHT_EM = 0.86f

/**
 * Geometry of the HUD's top-center column (clock, then the elite / Architect block under it),
 * shared by the renderer and the world keep-out ([WorldHudKeepOut]) so world labels and edge
 * markers follow the HUD at every text size. px for the [HudFrame]'s viewport. A `wide` flag selects the wide block
 * ([wideBossBlock]: the Architect and the secret bosses).
 */
internal object HudTopGeometry {
    /** Clock size in sp (display type: never scaled by the text-size setting). */
    fun clockSize(frame: HudFrame): Float = when {
        frame.regular -> frame.t(44f)
        frame.portrait -> 28f
        else -> 26f
    }

    /** Height of the clock's line box in px. */
    fun clockHeight(frame: HudFrame): Float = clockSize(frame) * WIDE_LINE_HEIGHT_EM * frame.density

    fun clockTop(frame: HudFrame): Float = when {
        frame.portrait -> portraitRowCenter(frame) - clockHeight(frame) * 0.5f
        frame.regular -> frame.u(18f)
        else -> frame.u(8f)
    }

    /** Top of the boss block: under the clock, or its own reserved row on portrait phones. */
    fun bossTop(frame: HudFrame): Float = when {
        frame.regular -> clockTop(frame) + clockHeight(frame) + frame.u(20f)
        // Portrait: its own row under the chips and chain (the chips share the row below the clock).
        frame.portrait -> frame.top + frame.u(PORTRAIT_BOSS_ROW_DP)
        else -> clockTop(frame) + clockHeight(frame) + frame.u(6f)
    }

    fun bossBarWidth(frame: HudFrame, wide: Boolean): Float = when {
        frame.regular -> min(frame.u(if (wide) 700f else 320f), frame.width - frame.margin * 2f - frame.u(660f))
        frame.portrait -> min(frame.width - frame.margin * 2f, frame.u(320f))
        else -> min(frame.u(260f) * frame.factor, frame.width * 0.3f)
    }.coerceAtLeast(frame.u(140f))

    /** Board size (sp) of the boss name; UI text, so it grows with the text-size setting. */
    fun bossNameSize(frame: HudFrame, wide: Boolean): Float = when {
        frame.regular -> frame.t(if (wide) 20f else 22f)
        else -> if (wide) 14f else 16f
    }

    fun bossNameLineHeight(wide: Boolean): Float = if (wide) WIDE_LINE_HEIGHT_EM else COND_LINE_HEIGHT_EM

    fun bossNameGap(frame: HudFrame): Float = frame.u(if (frame.regular) 9f else 5f)

    fun bossBarHeight(frame: HudFrame, wide: Boolean): Float =
        frame.u(if (wide) (if (frame.regular) 14f else 8f) else (if (frame.regular) 8f else 6f))

    /** Gap between the boss bar and its status rail. */
    fun bossRailGap(frame: HudFrame): Float = frame.u(if (frame.regular) 6f else 4f)

    /** Height of the boss's status rail (an ink slab of element glyph cells with one segment per stack). */
    fun bossRailHeight(frame: HudFrame): Float = frame.u(if (frame.regular) 16f else 12f)

    /**
     * Bottom of the boss block at the text size [textScale] (the name is the largest it gets),
     * including the reserve of its status rail under the bar, so plates and labels never land where
     * it appears: always on desktop, on landscape phones while the boss carries a status ([rail]);
     * portrait phones draw the rail on the name's row. One px covers the name's layout box, which
     * rounds up to whole pixels.
     */
    fun bossBottom(frame: HudFrame, wide: Boolean, textScale: Float, rail: Boolean = true): Float {
        val name = bossNameSize(frame, wide) * bossNameLineHeight(wide) * frame.density * hudUiScale(textScale)
        val reserve = if (frame.regular || (rail && !frame.portrait)) bossRailGap(frame) + bossRailHeight(frame) else 0f
        return bossTop(frame) + name + frame.density + bossNameGap(frame) + bossBarHeight(frame, wide) + reserve
    }
}

private fun DrawScope.drawBossBar(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    boss: BossTarget,
    top: Float,
    width: Float,
    ghost: Float,
) {
    val roles = measurer.roles
    val language = measurer.language
    val type = boss.type ?: return
    val rank = boss.rank
    // The Architect and the secret bosses take the wide block; mini-bosses and Elites the narrow one.
    val wide = wideBossBlock(rank)
    val center = frame.width * 0.5f
    val name = HudScratch.bossName.of(language, type.ordinal + 1L) {
        if (type == EnemyType.ARCHITECT) language.text(HudRedesignText.ArchitectTitle) else engine.content.enemy(type).name.localizedContent(language)
    }
    val nameSize = HudTopGeometry.bossNameSize(frame, wide)
    val lineHeight = HudTopGeometry.bossNameLineHeight(wide)
    // The icon slot: a wide boss's weakness glyph while it has one, else a secret boss's anomaly glyph; the narrow
    // block always shows the Elite glyph; the Architect without a weakness shows none.
    val weakness = if (wide) EnemyAffinity.weakness(boss.affinityBits) else EnemyAffinity.NONE
    val slotIcon = when {
        weakness >= 0 -> null
        rank == EnemyRank.SECRET_BOSS -> KkIcon.SYSTEM_ANOMALY
        !wide -> KkIcon.SYSTEM_ELITE
        else -> null
    }
    val icon = weakness >= 0 || slotIcon != null
    val iconSize = if (icon) frame.u(if (frame.regular) 18f else 14f) else 0f
    val gap = if (icon) frame.u(8f) else 0f
    // The status rail runs under the bar; portrait phones keep the boss row's height, so there the
    // rail follows the name on its row.
    val rail = bossRailWidth(engine, frame, boss.statusBits)
    val inlineRail = if (frame.portrait && rail > 0f) rail + frame.u(10f) else 0f
    // UI text: grows with the text size, shrinking to fit the bar's width rather than being cut.
    val layout = HudDrawCache.fitted(HudText.BOSS_NAME, HudMeasurers.ui(measurer), name, nameSize, width - iconSize - gap - inlineRail,
        uppercase = true) { size ->
        if (wide) measurer.typography.wideStyle(size, lineHeightEm = lineHeight) else measurer.typography.condStyle(size, lineHeightEm = lineHeight)
    }
    val rowWidth = iconSize + gap + layout.size.width + inlineRail
    val rowLeft = center - rowWidth * 0.5f
    val rowCenter = top + layout.kkBoxHeight * 0.5f
    if (weakness >= 0) drawKkIcon(elementIcon(weakness), Offset(rowLeft + iconSize * 0.5f, rowCenter), iconSize, roles.element(weakness))
    else if (slotIcon != null) drawKkIcon(slotIcon, Offset(rowLeft + iconSize * 0.5f, rowCenter), iconSize, roles.threat)
    drawKkText(layout, rowLeft + iconSize + gap, rowCenter, Kk.Bone, valign = KkVAlign.CENTER)
    val barTop = top + layout.kkBoxHeight + HudTopGeometry.bossNameGap(frame)
    val left = center - width * 0.5f
    val barHeight = HudTopGeometry.bossBarHeight(frame, wide)
    drawApexStrikes(engine, frame, roles, center + width * 0.5f, barTop)
    var bottom = barTop + barHeight
    if (rail > 0f) {
        if (inlineRail > 0f) {
            drawBossStatusRail(engine, measurer, frame, boss.statusBits, rowLeft + rowWidth - rail,
                rowCenter - HudTopGeometry.bossRailHeight(frame) * 0.5f, rail)
        } else {
            val railTop = barTop + barHeight + HudTopGeometry.bossRailGap(frame)
            drawBossStatusRail(engine, measurer, frame, boss.statusBits, center - rail * 0.5f, railTop, rail)
            bottom = railTop + HudTopGeometry.bossRailHeight(frame)
        }
    }
    val railHalf = if (inlineRail > 0f) 0f else rail * 0.5f
    HudLayoutProbe.record(HudBlock.BOSS, min(min(left, rowLeft), center - railHalf), top, max(max(left + width, rowLeft + rowWidth), center + railHalf),
        bottom)
    val parts = bossBarParts(engine.content, engine.rebirthLevel, type)
    if (parts == 0) {
        // A kind without an encounter spec (the Elite, the Herald): the 16-segment meter.
        val height = barHeight
        val bar = HudDrawCache.rect(HudRect.BOSS_0, left, barTop, left + width, barTop + height)
        drawKkMeter(bar, boss.integrity, roles.threat, roles, background = roles.threat.copy(alpha = 0.18f),
            segments = 16, ghost = ghost, ghostColor = Kk.Bone, threat = true)
        return
    }
    // A boss with a spec: equal parts that empty from the left, one per phase (the Architect three, four at the
    // Singularity tier; a mini-boss three; the Antecedent four). Each part's fill sits at its right end, so a part empties
    // from the left like the bar and a hit's ghost reads as one block across a part boundary. The active part's track
    // takes the hue of the aspect it hardened into, and it stripes while the boss shifts.
    val height = barHeight
    val partGap = frame.u(6f)
    val partWidth = (width - partGap * (parts - 1)) / parts
    val active = EnemyAffinity.phase(boss.affinityBits)
    val aspect = EnemyAffinity.aspect(boss.affinityBits)
    val shifting = EnemyAffinity.shifting(boss.affinityBits)
    for (part in 0 until parts) {
        val partLeft = left + part * (partWidth + partGap)
        val fill = (boss.integrity * parts - (parts - 1 - part)).coerceIn(0f, 1f)
        val partGhost = ((boss.integrity + ghost) * parts - (parts - 1 - part)).coerceIn(0f, 1f) - fill
        val slot = when (part) {
            0 -> HudRect.BOSS_0
            1 -> HudRect.BOSS_1
            2 -> HudRect.BOSS_2
            else -> HudRect.BOSS_3
        }
        val bar = HudDrawCache.rect(slot, partLeft, barTop, partLeft + partWidth, barTop + height)
        val track = if (part == active && aspect >= 0) roles.element(aspect).copy(alpha = 0.22f) else roles.threat.copy(alpha = 0.18f)
        val stripes = part == active && shifting
        drawKkMeter(bar, fill, roles.threat, roles, background = track, ghost = partGhost, ghostColor = Kk.Bone,
            stripes = if (stripes) KkMeterStripes.THREAT else KkMeterStripes.NONE, time = engine.renderElapsed, edge = stripes, threat = true,
            fromEnd = true)
    }
}

/** Width of the boss's status rail ([drawBossStatusRail]) for [bits], 0 without statuses. */
private fun bossRailWidth(engine: GameplayRenderModel, frame: HudFrame, bits: Int): Float {
    if (bits == 0) return 0f
    val glyph = railGlyph(frame)
    val segment = railSegment(frame)
    val segmentGap = frame.u(2f)
    val cellGap = railCellGap(frame)
    var width = 0f
    var cells = 0
    for (element in 0 until STATUS_ELEMENT_COUNT) {
        val stacks = EnemyStatusBits.stacks(bits, element)
        if (stacks == 0) continue
        val count = max(stacks, engine.enemyBreakThresholdOf(element, bits)).coerceAtMost(MAX_RAIL_SEGMENTS)
        width += (if (cells > 0) cellGap else 0f) + glyph + frame.u(4f) + count * (segment + segmentGap) - segmentGap
        cells++
    }
    for (mark in RailMarks) if (bits and mark != 0) width += (if (width > 0f) cellGap else 0f) + glyph
    return width + railPad(frame) * 2f
}

/** The Frozen, Brittle and Embrittled marks after the element cells, in this order. */
private val RailMarks = intArrayOf(EnemyStatusBits.FROZEN, EnemyStatusBits.BRITTLE, EnemyStatusBits.EMBRITTLED)

private fun railPad(frame: HudFrame) = frame.u(if (frame.regular) 7f else 5f)

private fun railGlyph(frame: HudFrame) = HudTopGeometry.bossRailHeight(frame) * 0.68f

private fun railSegment(frame: HudFrame) = frame.u(if (frame.regular) 6f else 4.5f)

private fun railCellGap(frame: HudFrame) = frame.u(if (frame.regular) 14f else 10f)

/**
 * The boss's statuses on an ink slab [width] wide from [left], [top], so the world never shows
 * through: one cell per element it carries (its glyph, then one segment per stack with a notch at
 * the Break threshold on this enemy, the last segment before it pulsing while the element is one
 * stack short), then the Frozen, Brittle and Embrittled marks. No numbers. Allocation-free.
 */
private fun DrawScope.drawBossStatusRail(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    bits: Int,
    left: Float,
    top: Float,
    width: Float,
) {
    val roles = measurer.roles
    val height = HudTopGeometry.bossRailHeight(frame)
    drawPath(HudDrawCache.paths.slab(HudPath.RAIL_SLAB.ordinal, left, top, left + width, top + height, height * 0.35f), Kk.Ink2)
    val glyph = railGlyph(frame)
    val segment = railSegment(frame)
    val segmentGap = frame.u(2f)
    val cellGap = railCellGap(frame)
    var x = left + railPad(frame)
    val middle = top + height * 0.5f
    val segmentHeight = glyph * 0.62f
    val segmentTop = middle - segmentHeight * 0.5f
    val primed = engine.primedElement(bits, EnemyStatusBits.dominant(bits))
    val pulse = 0.35f + 0.65f * (kotlin.math.sin(engine.renderElapsed * RAIL_PRIMED_PULSE) + 1f) * 0.5f
    var first = true
    for (element in 0 until STATUS_ELEMENT_COUNT) {
        val stacks = EnemyStatusBits.stacks(bits, element)
        if (stacks == 0) continue
        if (!first) x += cellGap
        first = false
        val color = roles.element(element)
        drawKkIcon(elementIcon(element), Offset(x + glyph * 0.5f, middle), glyph, color)
        x += glyph + frame.u(4f)
        val threshold = engine.enemyBreakThresholdOf(element, bits)
        val count = max(stacks, threshold).coerceAtMost(MAX_RAIL_SEGMENTS)
        drawKkPips(Offset(x, segmentTop), count, stacks, color, sizeDp = segmentHeight / density, widthDp = segment / density,
            gapDp = segmentGap / density, emptyColor = Kk.Ink4)
        if (element == primed && stacks < count) {
            drawKkPips(Offset(x + stacks * (segment + segmentGap), segmentTop), 1, 1, color.copy(alpha = pulse),
                sizeDp = segmentHeight / density, widthDp = segment / density, gapDp = segmentGap / density)
        }
        if (threshold in 1..count) {
            val notch = x + threshold * (segment + segmentGap) - segmentGap * 0.75f
            drawRect(Kk.Bone, Offset(notch, top + height * 0.14f), Size(segmentGap * 0.5f, height * 0.72f))
        }
        x += count * (segment + segmentGap) - segmentGap
    }
    if (bits and EnemyStatusBits.FROZEN != 0) {
        if (!first) x += cellGap
        first = false
        drawPath(HudDrawCache.paths.diamond(HudPath.RAIL_FROZEN.ordinal, x + glyph * 0.5f, middle, glyph * 0.45f), roles.element(1),
            style = kkStroke(frame.u(1.5f)))
        x += glyph
    }
    if (bits and EnemyStatusBits.BRITTLE != 0) {
        if (!first) x += cellGap
        first = false
        drawLine(Kk.Bone, Offset(x, middle - glyph * 0.4f), Offset(x + glyph * 0.5f, middle), frame.u(1.5f))
        drawLine(Kk.Bone, Offset(x + glyph * 0.5f, middle), Offset(x + glyph * 0.2f, middle + glyph * 0.45f), frame.u(1.5f))
        drawLine(Kk.Bone, Offset(x + glyph * 0.5f, middle), Offset(x + glyph, middle - glyph * 0.1f), frame.u(1.5f))
        x += glyph
    }
    if (bits and EnemyStatusBits.EMBRITTLED != 0) {
        if (!first) x += cellGap
        // Embrittled: the world's dotted ring, as dots.
        val center = Offset(x + glyph * 0.5f, middle)
        for (dot in 0 until EMBRITTLED_DOTS) {
            val angle = dot * (2f * kotlin.math.PI.toFloat() / EMBRITTLED_DOTS)
            drawCircle(Kk.Bone2, frame.u(1.1f), Offset(center.x + kotlin.math.cos(angle) * glyph * 0.42f, center.y + kotlin.math.sin(angle) * glyph * 0.42f))
        }
    }
}

private const val EMBRITTLED_DOTS = 8

/** A rail segment one stack short of its Break pulses four times a second (radians per second). */
private const val RAIL_PRIMED_PULSE = (2.0 * kotlin.math.PI * 4.0).toFloat()

/** Segments a rail cell shows at most (a stack cap is the threshold plus four). */
private const val MAX_RAIL_SEGMENTS = 14
