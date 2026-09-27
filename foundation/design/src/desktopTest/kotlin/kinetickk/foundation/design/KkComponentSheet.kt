// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight

/**
 * Review sheet mirroring `docs/design/redesign/boards/Components.dc.html` (1440 × 3060 at density
 * 1), built only from the public foundation helpers. Rendered by [KkComponentSheetTest].
 */
internal fun DrawScope.drawKkComponentsSheet(m: CanvasTextMeasurer, time: Float) {
    val t = m.typography
    val left = 64f
    val right = 1376f
    val contentWidth = right - left

    fun sectionTitle(title: String, y: Float, caption: String? = null) {
        val layout = drawKkText(m, title, t.condStyle(36f), left, y, Kk.Bone, valign = KkVAlign.CENTER, uppercase = true)
        val captionLayout = caption?.let { measureKkText(m, it, t.monoStyle(10f), uppercase = true) }
        val ruleEnd = right - (captionLayout?.size?.width?.plus(16f) ?: 0f)
        drawLine(Kk.Line, Offset(left + layout.size.width + 16f, y + 8f), Offset(ruleEnd, y + 8f), 1f)
        if (captionLayout != null) drawKkText(captionLayout, right, y + 8f, Kk.Mute, KkAlign.END, KkVAlign.CENTER)
    }

    fun cap(text: String, x: Float, y: Float) {
        drawKkText(m, text, t.monoStyle(10f), x, y, Kk.Mute, valign = KkVAlign.CENTER, uppercase = true)
    }

    // Header.
    drawKkText(m, "Components & states", t.monoStyle(), left, 50f, m.roles.you, uppercase = true)
    val title = measureKkText(m, "Every control, every state", t.wideStyle(44f), uppercase = true, maxWidth = 780f, maxLines = 2)
    drawKkText(title, left, 71f, Kk.Bone)
    val intro = measureKkText(m, "Hover anything on this board. The live states are the real CSS. Static columns show the same states frozen for review.",
        t.bodyStyle(16f), maxWidth = 520f, maxLines = 3)
    drawKkText(intro, right - 520f, 104f, Kk.Mute)

    // Buttons.
    sectionTitle("Buttons", 203f, "Face slab + echo slab, echo moves on hover, 1-frame bone flash on press")
    val labelColumn = 110f
    val column = (contentWidth - labelColumn - 18f * 6) / 6f
    val states = listOf("Idle", "Hover", "Focus", "Pressed", "Disabled", "Locked")
    states.forEachIndexed { index, state -> cap(state, left + labelColumn + 18f + index * (column + 18f), 248f) }
    val rows = listOf(
        KkButtonVariant.PRIMARY to "Deploy",
        KkButtonVariant.BONE to "Resume",
        KkButtonVariant.GHOST to "Reroll",
        KkButtonVariant.HAZARD to "Replace",
    )
    rows.forEachIndexed { row, (variant, label) ->
        val cy = 303f + row * 74f
        drawKkText(m, variant.name.lowercase(), t.labelStyle(12f), left, cy, Kk.Bone, valign = KkVAlign.CENTER, uppercase = true)
        states.indices.forEach { index ->
            val x = left + labelColumn + 18f + index * (column + 18f)
            val locked = index == 5
            val text = if (locked && variant == KkButtonVariant.PRIMARY) "430" else label
            val icon = if (locked && variant == KkButtonVariant.PRIMARY) KkIcon.SYSTEM_LOCKED else null
            val width = kkButtonWidth(m, text, KkButtonSize.MD, density, icon)
            drawKkButton(
                m, Rect(x, cy - 26f, x + width, cy + 26f), text, variant,
                hover = if (index == 1) 1f else 0f,
                focused = index == 2,
                pressed = index == 3,
                enabled = index != 4,
                locked = locked,
                icon = icon,
                time = time,
            )
        }
    }
    // Sizes, arm, hold.
    cap("Sizes: xs 30, sm 38, md 52, lg 72 (touch min 44)", left, 590f)
    var x = left
    KkButtonSize.entries.forEach { size ->
        val width = kkButtonWidth(m, "Take", size, density)
        drawKkButton(m, Rect(x, 673f - size.heightDp, x + width, 673f), "Take", size = size)
        x += width + 14f
    }
    val armX = left + (contentWidth - 80f) / 3.3f * 1.3f + 40f
    cap("Two-press arm: rebirth", armX, 610f)
    val advanceWidth = kkButtonWidth(m, "Advance", KkButtonSize.MD, density)
    drawKkButton(m, Rect(armX, 621f, armX + advanceWidth, 673f), "Advance")
    val confirmWidth = kkButtonWidth(m, "Confirm tier 4", KkButtonSize.MD, density)
    drawKkButton(m, Rect(armX + advanceWidth + 12f, 621f, armX + advanceWidth + 12f + confirmWidth, 673f), "Confirm tier 4", armed = true, time = time)
    val holdX = armX + (contentWidth - 80f) / 3.3f + 40f
    cap("Hold to confirm: 0.8 s", holdX, 610f)
    drawKkButton(m, Rect(holdX, 621f, holdX + 260f, 673f), "Exit to home", KkButtonVariant.GHOST, holdProgress = 0.55f)

    // Menu items.
    sectionTitle("Menu items", 751f, "Selected = bone slab to the screen edge, volt echo, trail, -2 deg tilt")
    val panel = Rect(left, 791f, left + (contentWidth - 30f) / 2f, 1121f)
    drawRect(Kk.Ink1, panel.topLeft, panel.size)
    clipRect(panel.left, panel.top, panel.right, panel.bottom) {
        val itemLeft = panel.left + 160f
        drawKkMenuItem(m, itemLeft, panel.top + 26f + 33f, "Deploy", selection = 1f, time = time, sub = "Rebirth 3", edgeRight = panel.right)
        drawKkMenuItem(m, itemLeft, panel.top + 26f + 33f + 76f, "Armory", stamp = "Unlockable", edgeRight = panel.right)
        drawKkMenuItem(m, itemLeft, panel.top + 26f + 33f + 152f, "Quit", dim = true, edgeRight = panel.right)
        drawKkMenuItem(m, itemLeft, panel.top + 26f + 33f + 228f, "Rebirth", locked = true, edgeRight = panel.right)
    }
    val notes = listOf(
        "Idle" to "Bone type on ink. No box, no numbering: the type is the target.",
        "Selected, hover, focus" to "Same state for mouse, keys and pad. Slab grows from the edge in 240 ms with overshoot.",
        "Event" to "A stamp rides the label when something changed: unlockable, new, ready.",
        "Locked, secondary" to "Mute type and a lock glyph; still focusable, the reason sits behind its (!) button.",
    )
    notes.forEachIndexed { index, (caption, body) ->
        val nx = panel.right + 30f + (index % 2) * ((right - panel.right - 30f - 18f) / 2f + 18f)
        val ny = panel.top + (index / 2) * 78f
        cap(caption, nx, ny + 6f)
        drawKkText(measureKkText(m, body, t.bodyStyle(14f), maxWidth = 300f, maxLines = 3), nx, ny + 18f, Kk.Mute)
    }

    // Selection & input.
    sectionTitle("Selection & input", 1190f)
    val third = (contentWidth - 68f) / 3f
    val col2 = left + third + 34f
    val col3 = col2 + third + 34f
    cap("Tiles: idle, selected, locked", left, 1232f)
    drawKkTile(m, Rect(left, 1250f, left + 110f, 1334f), "Circle", KkIcon.FORMS_CIRCLE)
    drawKkTile(m, Rect(left + 120f, 1250f, left + 230f, 1334f), "Square", KkIcon.FORMS_SQUARE, selected = 1f)
    drawKkTile(m, Rect(left + 240f, 1250f, left + 350f, 1334f), "Locked", KkIcon.FORMS_TESSERACT, locked = true)
    cap("List rows: idle, hover, selected", col2, 1232f)
    drawKkListRow(m, Rect(col2, 1250f, col2 + third, 1298f), "Morningstar", "Orbital")
    drawKkListRow(m, Rect(col2, 1300f, col2 + third, 1348f), "Null Lance", "Pierce", hovered = true)
    drawKkListRow(m, Rect(col2, 1350f, col2 + third, 1398f), "Flux Wake", "Trail", selected = 1f)
    cap("Tabs", col3, 1232f)
    x = col3
    listOf("Game", "Interface", "Sound").forEachIndexed { index, label ->
        val w = kkTabWidth(m, label, density)
        drawKkTab(m, Rect(x, 1250f, x + w, 1290f), label, selected = index == 1)
        x += w + 6f
    }
    cap("Segmented", col3, 1328f)
    x = col3
    listOf("Off", "Low", "Normal", "High").forEachIndexed { index, label ->
        val w = kkSegmentWidth(m, label, density)
        drawKkSegment(m, Rect(x, 1344f, x + w, 1378f), label, selected = index == 2)
        x += w + 3f
    }
    cap("Toggle: off, on", left, 1440f)
    drawKkToggle(m, Rect(left, 1458f, left + 62f, 1486f), 0f, "On", "Off")
    drawKkToggle(m, Rect(left + 76f, 1458f, left + 138f, 1486f), 1f, "On", "Off")
    cap("Slider with steppers: 10% steps", col2, 1440f)
    drawKkStepButton(Rect(col2, 1455f, col2 + 34f, 1489f), plus = false)
    drawKkSlider(Rect(col2 + 46f, 1458f, col2 + third - 100f, 1486f), 0.7f, m.roles)
    drawKkStepButton(Rect(col2 + third - 88f, 1455f, col2 + third - 54f, 1489f), plus = true)
    drawKkText(m, "70%", t.condStyle(20f, trackingEm = 0.04f, tabular = true), col2 + third - 42f, 1472f, Kk.Bone, valign = KkVAlign.CENTER)
    cap("Text input: focus", col3, 1440f)
    drawKkText(m, "Search", t.labelStyle(12f), col3, 1464f, Kk.Mute, valign = KkVAlign.CENTER, uppercase = true)
    drawKkField(Rect(col3, 1478f, col3 + third, 1518f), focused = true, roles = m.roles)
    drawKkText(m, "Comet", t.bodyStyle(15f, FontWeight.Medium), col3 + 36f, 1498f, Kk.Bone, valign = KkVAlign.CENTER)
    drawCircle(Kk.Mute, 4.5f, Offset(col3 + 18f, 1496f), style = kkStroke(1.5f))
    drawLine(Kk.Mute, Offset(col3 + 21f, 1499f), Offset(col3 + 25f, 1503f), 1.5f)

    // Info button.
    sectionTitle("Info button", 1597f, "No hint lines in the UI, explanations live behind (!)")
    val quarter = (contentWidth - 3 * 34f) / 4f
    cap("Idle", left, 1640f)
    val mastery = drawKkText(m, "Mastery", t.labelStyle(), left, 1671f, Kk.Bone, valign = KkVAlign.CENTER, uppercase = true)
    drawKkInfoButton(m, Rect(left + mastery.size.width + 10f, 1659f, left + mastery.size.width + 34f, 1683f))
    val hoverX = left + quarter + 34f
    cap("Hover, focus", hoverX, 1640f)
    drawKkText(m, "Mastery", t.labelStyle(), hoverX, 1671f, Kk.Bone, valign = KkVAlign.CENTER, uppercase = true)
    drawKkInfoButton(m, Rect(hoverX + mastery.size.width + 10f, 1659f, hoverX + mastery.size.width + 34f, 1683f), active = true)
    val openX = hoverX + quarter + 34f
    cap("Open: bone slip, cut corner, 14px body", openX, 1640f)
    drawKkText(m, "Mastery", t.labelStyle(), openX, 1671f, Kk.Bone, valign = KkVAlign.CENTER, uppercase = true)
    val infoRect = Rect(openX + mastery.size.width + 10f, 1659f, openX + mastery.size.width + 34f, 1683f)
    drawKkInfoButton(m, infoRect, active = true)
    val tipBody = measureKkTooltip(m, "Mastery climbs during the run and resets with the run build.", density, 290f)
    val slip = Rect(infoRect.right + 10f, 1659f, infoRect.right + 300f, 1659f + tipBody.size.height + 22f)
    drawPath(KkPathCache(1).chamferTopRight(0, slip, 10f), Kk.Bone)
    drawKkText(tipBody, slip.left + 13f, slip.top + 11f, Kk.Ink)

    // Level badges.
    sectionTitle("Level badge", 1790f, "Always Lvl X, grows louder every ten levels")
    val fifth = (contentWidth - 4 * 22f) / 5f
    val badges = listOf(
        Triple(7, "Lvl 1-9", "Bone slab"),
        Triple(14, "Lvl 10-19", "Volt echo"),
        Triple(24, "Lvl 20-29", "Volt face, bone echo, sheen"),
        Triple(34, "Lvl 30-39", "Ink face, striped echo, sheen"),
        Triple(45, "Lvl 40+", "Gold foil and rays"),
    )
    badges.forEachIndexed { index, (level, range, note) ->
        val px = left + index * (fifth + 22f)
        val panelRect = Rect(px, 1830f, px + fifth, 2000f)
        drawRect(Kk.Ink2, panelRect.topLeft, panelRect.size)
        clipRect(panelRect.left, panelRect.top, panelRect.right, panelRect.bottom) {
            val size = kkLevelBadgeSize(m, level, density)
            drawKkLevelBadge(m, Offset(panelRect.center.x - size.width / 2f, 1830f + 16f + 40f - size.height / 2f), level, time = time)
        }
        drawKkText(m, range, t.labelStyle(14f), px + 18f, 1930f, Kk.Bone, uppercase = true)
        drawKkText(measureKkText(m, note, t.monoStyle(), uppercase = true, maxWidth = fifth - 36f, maxLines = 2), px + 18f, 1950f, Kk.Mute)
    }

    // Meters.
    sectionTitle("Meters", 2069f, "All meters lean -12 deg, segmented where the unit matters")
    val meterWidth = (contentWidth - 3 * 34f) / 4f
    fun mx(index: Int) = left + index * (meterWidth + 34f)
    cap("Integrity, shield, hit ghost", mx(0), 2110f)
    drawKkMeter(Rect(mx(0), 2124f, mx(0) + meterWidth, 2138f), 0.66f, Kk.Bone, m.roles, segments = 15, ghost = 0.12f, ghostColor = m.roles.threat)
    drawKkPips(Offset(mx(0), 2146f), 3, 2, m.roles.shield, sizeDp = 9f, widthDp = 26f, emptyColor = m.roles.shield, nextOutlined = true)
    cap("Integrity, critical", mx(1), 2110f)
    drawKkMeter(Rect(mx(1), 2124f, mx(1) + meterWidth, 2138f), 0.12f, m.roles.threat, m.roles, segments = 15, threat = true)
    drawKkText(m, "Pulse at heart rate", t.monoStyle(), mx(1), 2156f, m.roles.threat, valign = KkVAlign.CENTER, uppercase = true)
    cap("Heat: ready, cooling", mx(2), 2110f)
    drawKkMeter(Rect(mx(2), 2124f, mx(2) + meterWidth, 2132f), 0.28f, m.roles.heat, m.roles)
    drawKkMeter(Rect(mx(2), 2142f, mx(2) + meterWidth, 2150f), 0.72f, m.roles.heat, m.roles)
    cap("Heat: overheat (dash offline)", mx(3), 2110f)
    drawKkMeter(Rect(mx(3), 2124f, mx(3) + meterWidth, 2132f), 1f, m.roles.threat, m.roles, stripes = KkMeterStripes.THREAT, time = time)
    drawKkText(m, "Offline 2.4 s", t.monoStyle(), mx(3), 2150f, m.roles.threat, valign = KkVAlign.CENTER, uppercase = true)
    cap("Polarity: normal, draining", mx(0), 2198f)
    drawKkMeter(Rect(mx(0), 2212f, mx(0) + meterWidth, 2220f), 0.86f, m.roles.pol, m.roles)
    drawKkMeter(Rect(mx(0), 2230f, mx(0) + meterWidth, 2238f), 0.18f, m.roles.threat, m.roles, threat = true)
    cap("Overdrive: charging, active", mx(1), 2198f)
    drawKkMeter(Rect(mx(1), 2212f, mx(1) + meterWidth, 2220f), 0.58f, m.roles.you, m.roles)
    drawKkMeter(Rect(mx(1), 2230f, mx(1) + meterWidth, 2238f), 0.72f, m.roles.you, m.roles, stripes = KkMeterStripes.YOU, time = time)
    cap("Velocity ladder: 30 ticks", mx(2), 2198f)
    drawKkTickLadder(Rect(mx(2), 2212f, mx(2) + meterWidth, 2228f), m.roles, 30, lit = 24, hotFrom = 20)
    cap("Rank pips: owned, next, empty", mx(3), 2198f)
    drawKkPips(Offset(mx(3), 2212f), 5, 2, m.roles.you, sizeDp = 22f, gapDp = 4f, nextOutlined = true, emptyOutlined = true)

    // Cards, relics, weapon slots.
    sectionTitle("Cards, relics, weapon slots", 2315f, "Rarity reads through effects first, color second")
    val cards = listOf(
        listOf("Common", "Stack 1/4", "Bastion Lattice", "+14", "Flat face"),
        listOf("Uncommon", "Stack 2/4", "Verdant Seed", "+0.6/s", "Inner glow"),
        listOf("Rare", "Stack 1/5", "Rime Thruster", "+24%", "Glow, sheen sweep, echo"),
        listOf("Epic", "New", "Echo Reactor", "+31%", "Halftone, rising sparks, pulsing echo"),
        listOf("Legendary", "New", "Cataclysm Lens", "+19%", "Rays, foil name, striped echo, burst"),
    )
    cards.forEachIndexed { index, card ->
        val rank = index + 1
        val cx = left + index * (fifth + 22f)
        val bounds = Rect(cx, 2356f, cx + fifth, 2606f)
        val selected = if (rank == 5) 1f else 0f
        withKkCardLift(bounds, selected) {
            drawKkCard(m, bounds, rank, time, selected, card[0], card[1], bandHeightDp = 32f)
            drawKkIconPlate(Rect(cx + 26f, 2408f, cx + 84f, 2466f), KkIcon.SYSTEM_INTEGRITY, Kk.rarity(rank))
            drawKkCardName(m, card[2], Offset(cx + 24f, 2480f), rank, time, maxWidth = fifth - 40f)
            drawKkText(m, card[3], t.wideStyle(18f), cx + 24f, 2516f, Kk.rarity(rank), uppercase = true)
            drawKkText(measureKkText(m, card[4], t.monoStyle(9f), uppercase = true, maxWidth = fifth - 46f, maxLines = 2), cx + 22f, 2570f, Kk.Mute)
        }
    }
    cap("Relic diamonds: 7 aspects, synergy link", left, 2668f)
    var rx = left + 22f
    val relicY = 2702f
    drawKkRelicSlot(Offset(rx, relicY), Kk.AVector, KkIcon.ASPECT_VECTOR)
    drawKkSynergyLink(Offset(rx + 22f, relicY), Offset(rx + 22f + 14f, relicY), m.roles.you)
    rx += 58f
    Kk.Aspects.forEachIndexed { index, color ->
        drawKkRelicSlot(Offset(rx, relicY), color, KkIcon.Aspects[index])
        rx += 54f
    }
    drawKkRelicSlot(Offset(rx, relicY), Color.Unspecified)
    val slotX = left + (contentWidth - 50f) * 1.3f / 2.3f + 50f
    cap("Weapon slot: ready, cooldown, max, empty", slotX, 2668f)
    drawKkWeaponSlot(m, Rect(slotX, 2680f, slotX + 62f, 2742f), KkIcon.WEAPONS_ARC_COIL, "Lvl 3", ready = true)
    drawKkWeaponSlot(m, Rect(slotX + 70f, 2680f, slotX + 132f, 2742f), KkIcon.WEAPONS_NULL_LANCE, "Lvl 1", cooldown = 0.6f)
    drawKkWeaponSlot(m, Rect(slotX + 140f, 2680f, slotX + 202f, 2742f), KkIcon.WEAPONS_FLUX_WAKE, "Lvl 10", ready = true, maxLevel = true)
    drawKkWeaponSlot(m, Rect(slotX + 210f, 2680f, slotX + 272f, 2742f), null)

    // Messages & labels.
    sectionTitle("Messages & labels", 2794f)
    cap("Toasts: reward, signal, warning", left, 2836f)
    drawKkToast(m, Offset(left, 2852f), "Build updated", "Echo Reactor", gem = true)
    drawKkToast(m, Offset(left, 2904f), "Elite signal", "Warden", icon = KkIcon.SYSTEM_ELITE, iconColor = m.roles.threat)
    drawKkToast(m, Offset(left, 2956f), "Singularity proximity", tone = KkToastTone.WARNING)
    cap("Tooltip: stat compare", col2, 2836f)
    val tip = Rect(col2, 2852f, col2 + 300f, 2940f)
    drawKkSlip(tip)
    drawKkText(m, "Dash power", t.labelStyle(13f), tip.left + 14f, tip.top + 18f, Kk.Ink, valign = KkVAlign.CENTER, uppercase = true)
    var sx = tip.left + 14f
    val now = drawKkText(m, "Now", t.monoStyle(), sx, tip.top + 46f, Color(0xFF3A3A34), valign = KkVAlign.BASELINE, uppercase = true)
    sx += now.size.width + 10f
    val nowValue = drawKkText(m, "+48%", t.wideStyle(22f, tabular = true), sx, tip.top + 46f, Kk.Ink, valign = KkVAlign.BASELINE)
    sx += nowValue.size.width + 20f
    val next = drawKkText(m, "Next", t.monoStyle(), sx, tip.top + 46f, Color(0xFF3A3A34), valign = KkVAlign.BASELINE, uppercase = true)
    sx += next.size.width + 10f
    drawKkText(m, "+72%", t.wideStyle(22f, tabular = true), sx, tip.top + 46f, Color(0xFF3D5200), valign = KkVAlign.BASELINE)
    sx = tip.left + 14f
    listOf("Items +36", "Synergy +12", "Lab 0").forEachIndexed { index, part ->
        if (index > 0) {
            drawKkSeparator(sx + 8f, tip.top + 68f, 11f, Color(0xFF3A3A34))
            sx += 16f
        }
        sx += drawKkText(m, part, t.monoStyle(), sx, tip.top + 68f, Color(0xFF3A3A34), valign = KkVAlign.CENTER, uppercase = true).size.width
    }
    cap("Tags, stamps, chips", col3, 2836f)
    x = col3
    listOf("Tag" to KkTagVariant.DEFAULT, "Volt" to KkTagVariant.YOU, "Hazard" to KkTagVariant.THREAT,
        "Bone" to KkTagVariant.BONE, "Line" to KkTagVariant.LINE).forEach { (label, variant) ->
        x = drawKkTag(m, label, Offset(x, 2852f), variant).right + 6f
    }
    x = col3
    x = drawKkStamp(m, "New!", Offset(x, 2892f)).right + 14f
    x = drawKkStamp(m, "Overheat", Offset(x, 2892f), KkStampVariant.THREAT).right + 14f
    drawKkStamp(m, "Marked", Offset(x, 2892f), KkStampVariant.OUTLINE)
    x = col3
    x = drawKkChip(m, Offset(x, 2936f), "1,284", "Matter").right + 8f
    drawKkChip(m, Offset(x, 2936f), "1", "Key", icon = KkIcon.SYSTEM_KEY)
}

/**
 * Extras sheet: icon set, fills, shapes, role palettes per Color vision mode, type roles and the
 * shutter at several moments (Main/Motion boards).
 */
internal fun DrawScope.drawKkFoundationExtrasSheet(m: CanvasTextMeasurer, time: Float) {
    val t = m.typography
    val left = 64f
    drawKkGrid(Rect(0f, 0f, size.width, 520f), Kk.Bone.copy(alpha = 0.045f), 48f)
    drawKkRadialFade(Rect(930f, -40f, 1530f, 560f)) { drawKkHalftone(Rect(930f, -40f, 1530f, 560f), Kk.Bone.copy(alpha = 0.13f)) }
    drawKkText(m, "Kinetickk", t.wideStyle(130f), left, 132f, Kk.Bone, uppercase = true)
    drawKkText(m, "Your movement is the weapon.", t.condStyle(50f), left, 312f, Kk.Bone, uppercase = true)
    drawKkText(m, "Your cursor is the threat.", t.condStyle(50f), left, 356f, m.roles.threat, uppercase = true)
    drawKkText(m, "Momentum", t.wideStyle(64f), left, 420f, Kk.Bone, uppercase = true)
    drawKkText(m, "Impact ×3", t.condStyle(90f), 620f, 400f, m.roles.you, uppercase = true)
    drawKkText(m, "Твоё движение", t.condStyle(38f), 1040f, 420f, Kk.Bone, uppercase = true)
    drawKkText(m, "Архитектор", t.wideStyle(22f), 1040f, 470f, Kk.Bone, uppercase = true)
    drawKkText(m, "SPD 684 U/S  07:42", t.monoStyle(14f), left, 500f, Kk.Bone)

    // Icons.
    KkIcon.entries.forEachIndexed { index, icon ->
        val x = left + (index % 14) * 94f + 43f
        val y = 580f + (index / 14) * 90f
        drawKkIcon(icon, Offset(x, y), 32f, if (icon.key.startsWith("weapons.")) m.roles.you else Kk.Bone)
        drawKkText(m, icon.label, t.monoStyle(9f), x, y + 26f, Kk.Mute, align = KkAlign.CENTER, uppercase = true, maxWidth = 90f)
    }

    // Fills and shapes.
    val fillsTop = 880f
    val cell = 300f
    drawRect(Kk.Ink2, Offset(left, fillsTop), Size(cell, 170f))
    drawKkRadialFade(Rect(left, fillsTop, left + cell, fillsTop + 120f)) { drawKkHalftone(Rect(left, fillsTop, left + cell, fillsTop + 120f), m.roles.you) }
    drawRect(Kk.Ink2, Offset(left + 320f, fillsTop), Size(cell, 170f))
    drawKkStripes(Rect(left + 320f, fillsTop + 36f, left + 320f + cell, fillsTop + 80f), m.roles.threat, Kk.Ink, time)
    drawKkStripes(Rect(left + 320f, fillsTop + 86f, left + 320f + cell, fillsTop + 100f), m.roles.heat, Kk.Ink)
    drawRect(Kk.Ink2, Offset(left + 640f, fillsTop), Size(cell, 170f))
    val hatchRect = Rect(left + 670f, fillsTop + 34f, left + 820f, fillsTop + 100f)
    drawKkSlab(hatchRect, Kk.Ink3, 13f)
    drawKkHatch(KkPathCache(1).slab(0, hatchRect, 13f))
    drawKkIcon(KkIcon.SYSTEM_LOCKED, hatchRect.center, 24f, Kk.Mute)
    drawRect(Kk.Ink2, Offset(left + 960f, fillsTop), Size(cell, 170f))
    drawKkSlab(Rect(left + 1000f, fillsTop + 36f, left + 1190f, fillsTop + 94f), Kk.Bone, 13f)
    val shapesTop = 1080f
    val shapes = KkPathCache(8)
    drawPath(shapes.slab(0, Rect(left, shapesTop, left + 160f, shapesTop + 60f), 14f), Kk.Bone)
    drawPath(shapes.slab(1, Rect(left + 180f, shapesTop, left + 340f, shapesTop + 60f), 14f, KkSlabKind.RIGHT), Kk.Bone2)
    drawPath(shapes.slab(2, Rect(left + 360f, shapesTop, left + 520f, shapesTop + 60f), 14f, KkSlabKind.LEFT), Kk.Mute)
    drawPath(shapes.chamfer(3, Rect(left + 540f, shapesTop, left + 700f, shapesTop + 60f), 14f), Kk.Ink4)
    drawPath(shapes.diamond(4, left + 750f, shapesTop + 30f, 30f), m.roles.you)
    drawPath(shapes.sheared(5, Rect(left + 810f, shapesTop, left + 970f, shapesTop + 60f)), m.roles.pol)
    drawKkSeparator(left + 1000f, shapesTop + 30f, 30f, Kk.Bone)

    // Role palettes per Color vision mode.
    KkVisionMode.entries.forEachIndexed { row, mode ->
        val y = 1180f + row * 44f
        drawKkText(m, mode.name, t.labelStyle(13f), left, y + 14f, Kk.Bone, valign = KkVAlign.CENTER)
        val p = mode.palette
        listOf(p.you, p.threat, p.heat, p.shield, p.pol).forEachIndexed { index, color ->
            val cellRect = Rect(left + 120f + index * 120f, y, left + 230f + index * 120f, y + 28f)
            drawKkSlab(cellRect, color, 8f)
            if (index == 1) drawKkThreatHatch(KkPathCache(1).slab(0, cellRect, 8f), p, Kk.Ink)
        }
    }

    // Shutter moments.
    listOf(60f, 200f, KkShutter.SWAP_MS.toFloat(), 450f).forEachIndexed { index, ms ->
        val frame = Rect(left + index * 330f, 1420f, left + index * 330f + 310f, 1594f)
        drawRect(Kk.Ink2, frame.topLeft, frame.size)
        clipRect(frame.left, frame.top, frame.right, frame.bottom) {
            drawKkText(m, "Deploy", t.condStyle(40f), frame.left + 20f, frame.top + 20f, m.roles.you, uppercase = true)
        }
        drawKkShutterInto(frame, ms, m.roles)
        drawKkText(m, "${ms.toInt()} ms", t.monoStyle(), frame.left, frame.bottom + 14f, Kk.Mute, uppercase = true)
    }
}

private fun DrawScope.drawKkShutterInto(frame: Rect, elapsedMs: Float, roles: KkRolePalette) {
    clipRect(frame.left, frame.top, frame.right, frame.bottom) {
        translate(frame.left, frame.top) {
            val saved = drawContext.size
            drawContext.size = frame.size
            drawKkShutter(elapsedMs, roles)
            drawContext.size = saved
        }
    }
}
