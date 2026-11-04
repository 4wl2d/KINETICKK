// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.REGULAR_HUD_BOTTOM_DP
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.layout.*

/** Portrait phones: the integrity cluster's width (dp, before the narrow-phone factor) and the loadout slot's side. */
internal const val PORTRAIT_INTEGRITY_WIDTH_DP = 170f
internal const val PORTRAIT_LOADOUT_SLOT_DP = 34f

/** Bottom-right (desktop) or top-right (landscape) relic diamonds and the active weapon slot. */
internal fun DrawScope.drawLoadout(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame) {
    // "Lvl N" is UI text: it follows the text size and shrinks to fit its slot.
    val measurer = HudMeasurers.ui(hostMeasurer)
    val roles = measurer.roles
    val content = engine.content
    val slots = content.relicPolicy.maxSlots.coerceIn(0, 8)
    val language = measurer.language
    val levelText = HudScratch.weaponLevel.of(language, engine.weaponLevel.toLong()) {
        language.text(HudRedesignText.WeaponLevel, engine.weaponLevel)
    }
    val icon = weaponIcon(engine.weapon)
    val maxLevel = weaponMasteriesReached(content, engine.weaponLevel) >= content.weaponMasteries.size
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            val right = frame.width - frame.margin
            val bottom = frame.height - frame.u(REGULAR_HUD_BOTTOM_DP)
            val pipsTop = bottom - frame.u(4f)
            val slotSize = frame.u(54f)
            val slotBottom = pipsTop - frame.u(5f)
            val slot = HudDrawCache.rect(HudRect.WEAPON, right - slotSize, slotBottom - slotSize, right, slotBottom)
            // Drawn end-aligned 9 dp in from the slot's right edge; the slab's left edge meets the bottom at its left.
            val levelLayout = HudDrawCache.fitted(HudText.WEAPON_LEVEL, measurer, levelText, 10f * frame.textFactor,
                slotSize - d(9f) - d(4f)) { size -> weaponLevelStyle(measurer, size) }
            drawKkWeaponSlot(measurer, slot, icon, maxLevel = maxLevel, levelLayout = levelLayout, iconSizeDp = 28f * frame.textFactor)
            drawMasteryPips(engine, frame, roles, slot.center.x, pipsTop)
            val relicSize = frame.u(36f)
            val relicCenter = slot.top - frame.u(12f) - relicSize * 0.5f
            val rowLeft = drawRelicRow(engine, content, slots, right, relicCenter, relicSize, frame, roles)
            HudLayoutProbe.record(HudBlock.LOADOUT, min(slot.left, rowLeft),
                relicCenter - relicSize * 0.5f - relicBracketReach(frame), right, bottom)
            HudLayoutProbe.extend(HudBlock.LOADOUT, HudBlock.SEVERED)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val right = frame.width - frame.margin
            val top = frame.u(62f)
            val slotSize = frame.u(34f)
            val slot = HudDrawCache.rect(HudRect.WEAPON, right - slotSize, top, right, top + slotSize)
            drawKkWeaponSlot(measurer, slot, icon, maxLevel = maxLevel, iconSizeDp = 20f)
            // Centered under the slot, never past the screen's right margin.
            val levelLayout = HudDrawCache.fitted(HudText.WEAPON_LEVEL, measurer, levelText, 9f,
                slotSize + frame.margin) { size -> weaponLevelStyle(measurer, size) }
            val levelX = min(slot.center.x, right + frame.margin * 0.5f - levelLayout.size.width * 0.5f)
            drawKkText(levelLayout, levelX, slot.bottom + frame.u(4f), if (maxLevel) Kk.RLegend else Kk.Bone, KkAlign.CENTER)
            val relicSize = frame.u(22f)
            val rowLeft = drawRelicRow(engine, content, slots, slot.left - frame.u(8f), slot.center.y, relicSize, frame, roles)
            HudLayoutProbe.record(HudBlock.LOADOUT, rowLeft,
                slot.center.y - relicSize * 0.5f - relicBracketReach(frame), max(right, levelX + levelLayout.size.width * 0.5f),
                slot.bottom + frame.u(4f) + levelLayout.kkBoxHeight)
            HudLayoutProbe.extend(HudBlock.LOADOUT, HudBlock.SEVERED)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val top = frame.portraitLoadoutTop
            val slotSize = frame.u(PORTRAIT_LOADOUT_SLOT_DP)
            val slot = HudDrawCache.rect(HudRect.WEAPON, frame.margin, top, frame.margin + slotSize, top + slotSize)
            drawKkWeaponSlot(measurer, slot, icon, maxLevel = maxLevel, iconSizeDp = 20f)
            val relicSize = frame.u(22f)
            // The row (and the ghosts of replaced relics left of it) is drawn first: the level text takes what is left.
            val relicLeft = drawRelicRow(engine, content, slots, frame.width - frame.margin, slot.center.y, relicSize, frame, roles)
            val levelLayout = HudDrawCache.fitted(HudText.WEAPON_LEVEL, measurer, levelText, 11f,
                relicLeft - frame.u(12f) - slot.right - frame.u(8f)) { size -> weaponLevelStyle(measurer, size) }
            drawKkText(levelLayout, slot.right + frame.u(8f), slot.center.y, if (maxLevel) Kk.RLegend else Kk.Bone, valign = KkVAlign.CENTER)
            HudLayoutProbe.record(HudBlock.LOADOUT, slot.left, min(slot.top, slot.center.y - relicSize * 0.5f - relicBracketReach(frame)),
                frame.width - frame.margin, slot.bottom)
        }
    }
}

private fun weaponLevelStyle(measurer: TextMeasurer, size: Float) =
    measurer.typography.monoStyle(size, weight = FontWeight.Bold, trackingEm = 0f, lineHeightEm = 1f)

/** Height above the relic row that the current synergy brackets reach. */
private fun DrawScope.relicBracketReach(frame: HudFrame): Float {
    val highest = HudScratch.brackets.maxLevel()
    return if (highest < 0) 0f else frame.u(4f) + highest * bracketLevelStep(frame) + d(1f)
}

/** Width of the landscape loadout row (relic diamonds, gap, weapon slot) in px. */
internal fun compactLoadoutWidth(slots: Int, unit: Float): Float =
    unit * (34f + 8f + slots * 22f + (slots - 1).coerceAtLeast(0) * 6f)

/** Mastery milestones reached at [level] (1 for the first milestone), without iterators. */
internal fun weaponMasteriesReached(content: GameplayContentSnapshot, level: Int): Int {
    val masteries = content.weaponMasteries
    var reached = 0
    for (index in masteries.indices) if (level >= masteries[index].minimumLevel) reached = index + 1
    return reached
}

/** Mastery pips under the weapon slot: `you`, then bone, then the legendary top milestone. */
private fun DrawScope.drawMasteryPips(engine: GameplayRenderModel, frame: HudFrame, roles: KkRolePalette, centerX: Float, top: Float) {
    val count = engine.content.weaponMasteries.size.coerceIn(1, 6)
    val reached = weaponMasteriesReached(engine.content, engine.weaponLevel).coerceIn(0, count)
    val pipWidth = 8f * frame.textFactor
    val pipHeight = 4f * frame.textFactor
    val total = count * d(pipWidth) + (count - 1) * d(2f)
    var x = centerX - total * 0.5f
    for (index in 0 until count) {
        val color = when {
            index >= reached -> Kk.Ink4
            index == count - 1 -> Kk.RLegend
            index == count - 2 -> Kk.Bone
            else -> roles.you
        }
        drawKkPips(Offset(x, top), 1, 1, color, sizeDp = pipHeight, widthDp = pipWidth)
        x += d(pipWidth) + d(2f)
    }
}

/**
 * Relic diamonds right-aligned at [right] on an even pitch. Links come from [hudRelicLinks] (named
 * pairings and dissonance, in any slots, the same reading as pause and the relic matrix):
 * neighbouring members are joined by a link bar, members further apart by a bracket above the
 * row. Brackets whose slot spans overlap or touch stack on separate levels (as the relic matrix
 * does), each level's legs ending above the level below, so interleaved links never merge into one
 * line. Replaced relics whose cost lingers follow as ghosts ([drawSeveredRelics]); returns the
 * left edge of the row and its ghosts (px).
 */
private fun DrawScope.drawRelicRow(
    engine: GameplayRenderModel,
    content: GameplayContentSnapshot,
    slots: Int,
    right: Float,
    centerY: Float,
    size: Float,
    frame: HudFrame,
    roles: KkRolePalette,
): Float {
    if (slots <= 0) return right
    val relics = engine.equippedRelics
    val gap = relicRowGap(frame)
    val pitch = size + gap
    val firstCenter = right - slots * size - (slots - 1) * gap + size * 0.5f
    val half = size * 0.5f
    val links = hudRelicLinks(content, relics)
    for (linkIndex in links.indices) {
        val link = links[linkIndex]
        val members = link.slots
        for (step in 0 until members.size - 1) {
            val a = members[step]
            val b = members[step + 1]
            if (b != a + 1 || b >= slots) continue
            val ax = firstCenter + a * pitch + half
            val bx = firstCenter + b * pitch - half
            drawKkSynergyLink(Offset(ax, centerY), Offset(bx, centerY), link.overlayColor())
            HudLayoutProbe.recordLink(false, a, b, ax, centerY - d(4.5f), bx, centerY + d(4.5f))
        }
    }
    val brackets = HudScratch.brackets
    val baseY = centerY - half - frame.u(4f)
    val step = bracketLevelStep(frame)
    val stroke = d(2f)
    for (index in 0 until brackets.count) {
        val a = brackets.from[index]
        val b = brackets.to[index]
        if (b >= slots) continue
        val level = brackets.level[index]
        val color = links[brackets.link[index]].overlayColor()
        val ax = firstCenter + a * pitch
        val bx = firstCenter + b * pitch
        val y = baseY - level * step
        // The lowest level's legs reach the diamonds (`.bracket`, 8 dp); higher legs stop above the level below.
        val leg = if (level == 0) d(8f) else step - frame.u(3f)
        drawLine(color, Offset(ax - stroke * 0.5f, y), Offset(bx + stroke * 0.5f, y), stroke)
        drawLine(color, Offset(ax, y), Offset(ax, y + leg), stroke)
        drawLine(color, Offset(bx, y), Offset(bx, y + leg), stroke)
        HudLayoutProbe.recordLink(true, a, b, ax - stroke * 0.5f, y - stroke * 0.5f, bx + stroke * 0.5f, y + leg)
    }
    val sizeDp = size / density
    val build = engine.build
    for (index in 0 until slots) {
        val relic = relics.getOrNull(index)
        val center = Offset(firstCenter + index * pitch, centerY)
        if (relic == null) {
            drawKkRelicSlot(center, Color.Unspecified, sizeDp = sizeDp)
            continue
        }
        val definition = hudRelicDefinition(content, relic.id)
        val color = relicHue(definition, roles)
        val spent = relic.id == RelicId.OUROBOROS_SEAL && build.ouroborosSpent
        // The aspect rim carries the element; the relic's own rune sits inside it.
        drawKkRelicSlot(center, color, sizeDp = sizeDp, alpha = if (spent) 0.45f else 1f)
        drawRelicRune(definition, center, half * RUNE_IN_SLOT, color, if (spent) 0.4f else 1f)
        drawRelicGauge(engine, relic.id, center, half, color, frame, roles)
    }
    // The diamonds with the reach of their gauges (Stormheart's ring, Glass Comet's diamond, the Stasis bar).
    val reach = relicGaugeReach(frame)
    HudLayoutProbe.record(HudBlock.RELIC_ROW, firstCenter - half - reach, centerY - half - reach, right + reach,
        centerY + half + reach)
    return drawSeveredRelics(engine, content, firstCenter, centerY, size, frame, roles)
}

/** Space between the relic diamonds of the HUD row. */
private fun relicRowGap(frame: HudFrame): Float = frame.u(if (frame.regular) 10f else 6f)

/** How far a relic's gauge reaches past its diamond (Stormheart's ring with its stroke, the invulnerability diamond). */
private fun relicGaugeReach(frame: HudFrame): Float = frame.u(6f)

/** Medallion radius of a rune inside its HUD diamond, as a share of the diamond's half size. */
private const val RUNE_IN_SLOT = 0.62f

/**
 * Small state marks of the relics that keep one (all from the build projection): Stormheart's
 * charge as an arc around its diamond (a pulsing ring when full), the Stasis meter as a bar under
 * it, Glass Comet's invulnerability as a bright ring, a spent Ouroboros seal struck through.
 */
private fun DrawScope.drawRelicGauge(
    engine: GameplayRenderModel,
    id: RelicId,
    center: Offset,
    half: Float,
    color: Color,
    frame: HudFrame,
    roles: KkRolePalette,
) {
    val build = engine.build
    when (id) {
        RelicId.STORMHEART_CAPACITOR -> {
            val charge = build.stormCharge.coerceIn(0f, 1f)
            val radius = half + frame.u(4f)
            val stroke = kkStroke(frame.u(2f))
            drawCircle(color, radius, center, alpha = 0.18f, style = stroke)
            if (charge > 0f) {
                val alpha = if (charge >= 1f) 0.6f + 0.4f * kotlin.math.sin(engine.renderElapsed * 12f) else 0.95f
                drawArc(color, -90f, 360f * charge, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
                    alpha = alpha, style = stroke)
            }
        }
        RelicId.STASIS_ANCHOR -> {
            val meter = build.stasisMeter.coerceIn(0f, 1f)
            val top = center.y + half + frame.u(3f)
            val height = frame.u(3f)
            val left = center.x - half * 0.8f
            val width = half * 1.6f
            drawRect(color, Offset(left, top), Size(width, height), alpha = 0.2f)
            drawRect(color, Offset(left, top), Size(width * meter, height))
            if (build.stasisBank > 0) drawCircle(Kk.Bone, frame.u(2f), Offset(left + width + frame.u(4f), top + height * 0.5f))
        }
        RelicId.GLASS_COMET -> if (build.invulnerable) {
            drawPath(HudDrawCache.paths.diamond(HudPath.INVULNERABLE.ordinal, center.x, center.y, half + frame.u(5f)), Kk.Bone,
                alpha = 0.55f + 0.45f * kotlin.math.sin(engine.renderElapsed * 18f), style = kkStroke(frame.u(1.5f)))
        }
        RelicId.OUROBOROS_SEAL -> if (build.ouroborosSpent) {
            drawLine(roles.threat, Offset(center.x - half * 0.7f, center.y + half * 0.7f), Offset(center.x + half * 0.7f, center.y - half * 0.7f),
                frame.u(2f))
        }
        else -> Unit
    }
}

/**
 * Replaced relics whose cost still lingers ([BuildProjection.severed]): a faded rune ringed by a
 * threat arc that drains with the remaining time, clear of the row's gauges. Desktop and portrait
 * put them left of the row, nearest first; landscape phones, where the feed docks left of the
 * loadout, put them under the row from its first slot. Returns the left edge of the row with them.
 */
private fun DrawScope.drawSeveredRelics(
    engine: GameplayRenderModel,
    content: GameplayContentSnapshot,
    firstCenter: Float,
    centerY: Float,
    size: Float,
    frame: HudFrame,
    roles: KkRolePalette,
): Float {
    val half = size * 0.5f
    val rowLeft = firstCenter - half - relicGaugeReach(frame)
    val severed = engine.build.severed
    if (severed.isEmpty()) return rowLeft
    val total = content.build.policy.severanceSeconds.coerceAtLeast(1f)
    val ghostHalf = size * 0.36f
    val radius = ghostHalf * 1.25f
    val stroke = frame.u(2f)
    val outer = radius + stroke * 0.5f
    val step = outer * 2f + frame.u(4f)
    val below = frame.mode == GameplayLayoutMode.COMPACT_LANDSCAPE
    val shown = minOf(severed.size, MAX_SEVERED_SHOWN)
    val firstX = if (below) firstCenter else rowLeft - relicRowGap(frame) - outer
    val ghostY = if (below) centerY + half + relicGaugeReach(frame) + frame.u(2f) + outer else centerY
    for (index in 0 until shown) {
        val ghost = severed[index]
        val center = Offset(if (below) firstX + index * step else firstX - index * step, ghostY)
        val definition = hudRelicDefinition(content, ghost.id)
        drawRelicRune(definition, center, ghostHalf, relicHue(definition, roles), 0.38f)
        val share = (ghost.remaining / total).coerceIn(0f, 1f)
        drawArc(roles.threat, -90f, 360f * share, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
            style = kkStroke(stroke))
    }
    val left = if (below) firstX - outer else firstX - (shown - 1) * step - outer
    val right = if (below) firstX + (shown - 1) * step + outer else firstX + outer
    HudLayoutProbe.record(HudBlock.SEVERED, left, ghostY - outer, right, ghostY + outer)
    return if (below) rowLeft else left
}

private const val MAX_SEVERED_SHOWN = 2

/** Relic definitions by id, indexed once per content snapshot (content lookups iterate the catalog). */
internal fun hudRelicDefinition(content: GameplayContentSnapshot, id: RelicId): kinetickk.ball.content.api.RelicDefinition {
    val cache = HudScratch
    if (cache.definitionContent !== content) {
        cache.definitionContent = content
        cache.definitions.fill(null)
    }
    return cache.definitions[id.ordinal] ?: content.relic(id).also { cache.definitions[id.ordinal] = it }
}

/** Vertical distance between stacked synergy bracket levels, in px. */
private fun bracketLevelStep(frame: HudFrame): Float = frame.u(if (frame.regular) 8f else 6f)

/**
 * Links of the equipped [relics] (slot order) that the relic row marks, as [overlayRelicLinks]
 * reads them: named pairings and dissonance only. A shared key's harmony shows as the upside
 * multipliers in pause and on relic cards; four relics of one key would otherwise stack six
 * brackets. Recomputed (with the stacked levels of their brackets) only when the relic list or the
 * content changes. The HUD never names a link, so pairing discovery does not matter here.
 */
internal fun hudRelicLinks(content: GameplayContentSnapshot, relics: List<EquippedRelic>): List<OverlayRelicLink> {
    val cache = HudScratch
    if (cache.linkRelics !== relics || cache.linkContent !== content) {
        cache.linkRelics = relics
        cache.linkContent = content
        cache.links = overlayRelicLinks(relics.map { it.id }, content) { true }.filter { it.kind != RelicLinkKind.HARMONY }
        cache.brackets.place(cache.links)
    }
    return cache.links
}

/**
 * Brackets of the relic row: one per pair of consecutive members of a link that are not
 * neighbours, each on the lowest level where no bracket over an overlapping or touching slot span
 * sits (the relic matrix's `relicBrackets` rule).
 */
internal class HudBracketLevels {
    var count = 0
        private set
    val from = IntArray(MAX)
    val to = IntArray(MAX)
    val level = IntArray(MAX)
    val link = IntArray(MAX)

    fun place(links: List<OverlayRelicLink>) {
        count = 0
        for (linkIndex in links.indices) {
            val members = links[linkIndex].slots
            for (step in 0 until members.size - 1) {
                val a = members[step]
                val b = members[step + 1]
                if (b == a + 1 || count >= MAX) continue
                var candidate = 0
                while (occupied(candidate, a, b)) candidate++
                from[count] = a
                to[count] = b
                level[count] = candidate
                link[count] = linkIndex
                count++
            }
        }
    }

    /** Highest level in use, -1 without brackets. */
    fun maxLevel(): Int {
        var highest = -1
        for (index in 0 until count) highest = max(highest, level[index])
        return highest
    }

    private fun occupied(candidate: Int, a: Int, b: Int): Boolean {
        for (index in 0 until count) {
            if (level[index] == candidate && from[index] <= b && a <= to[index]) return true
        }
        return false
    }

    private companion object {
        const val MAX = 16
    }
}

/** Aspect of a relic, indexed once per content snapshot (content lookups iterate the catalog). */
internal fun relicAspect(content: GameplayContentSnapshot, id: RelicId): RelicAspect {
    val cache = HudScratch
    if (cache.aspectContent !== content) {
        cache.aspectContent = content
        cache.aspects.fill(null)
    }
    return cache.aspects[id.ordinal] ?: content.relic(id).aspect.also { cache.aspects[id.ordinal] = it }
}
