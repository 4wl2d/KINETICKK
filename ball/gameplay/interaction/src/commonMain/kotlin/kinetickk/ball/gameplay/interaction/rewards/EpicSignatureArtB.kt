// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.ball.gameplay.interaction.canvas.elementFill
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.kkLuminance
import kinetickk.foundation.design.kkMix
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Epic signature illustrations (static, faint, behind the text), part B.
//
// Every illustration is built around one spot in the lower face (its center and reach, from the
// frame's clear region), fades out radially through cached gradient brushes (no offscreen layer)
// and keeps all geometry in the frame's cache, so a steady card draws it without allocating.

/**
 * Phoenix Coil: phoenix wing feathers spiralling into a coil. Short rounded coverts lap round the
 * coil's outer turn, and from its end a wing of long primaries opens over a 120° sweep, longest at
 * the leading edge; each feather has a shaft and a rounded, slightly split tip. Embers rise past the
 * wing tips.
 */
internal fun DrawScope.drawPhoenixCoilArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.5f, 0.56f, 1.0f)
    val r = spot[2]
    val g = spot[3]
    // The coil sits left of and below the spot; the wing opens to its right.
    val cx = spot[0] - r * 0.5f
    val cy = spot[1] + r * 0.22f
    val cache = frame.cache
    val feathers = cache.floats(0, PhoenixAll * 6) { phoenixFeatherFrames(it, cx, cy, r) }
    val coverts = cache.path(0) { phoenixFeathers(feathers, 0, PhoenixCoverts) }
    val primaries = cache.path(1) { phoenixFeathers(feathers, PhoenixCoverts, PhoenixAll) }
    val shafts = cache.path(2) { phoenixQuills(feathers) }
    val leading = cache.path(3) { phoenixLeadingEdges(feathers) }
    val coil = cache.path(4) { phoenixCoil(cx, cy, r, 1f) }
    val rail = cache.path(5) { phoenixCoil(cx, cy, r, 0.72f) }
    val embers = cache.path(6) {
        // Rising past the wing tips, smaller as they climb.
        for (index in 0 until PhoenixEmbers) {
            val x = cx + r * (1.0f + 0.2f * index + 0.1f * frame.random(index))
            val y = cy - r * (0.7f + 0.16f * index + 0.08f * frame.random(10 + index))
            diamond(x, y, r * (0.04f - index * 0.006f))
        }
    }
    val hue = cache.brush(0) { fadeBrush(frame.hue, cx + r * 0.5f, cy, r * 1.9f) }
    val bone = cache.brush(1) { fadeBrush(Kk.Bone, cx + r * 0.4f, cy, r * 1.6f, 0.3f) }
    val heart = cache.brush(2) { glowBrush(frame.hue, cx, cy, r * 0.42f) }
    val edge = cache.stroke(frame.u(1.1f))
    drawCircle(heart, r * 0.42f, Offset(cx, cy), alpha = alphaOf(RewardArtAlpha.MID, g))
    // Back to front: the primaries, then the coverts lapping over their roots.
    drawPath(primaries, hue, alpha = alphaOf(RewardArtAlpha.LOW * 1.1f, g))
    drawPath(primaries, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = edge)
    drawPath(coverts, Kk.Ink2, alpha = 0.55f)
    drawPath(coverts, hue, alpha = alphaOf(RewardArtAlpha.LOW * 1.3f, g))
    drawPath(coverts, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = edge)
    drawPath(shafts, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(0.9f), StrokeCap.Round))
    drawPath(leading, bone, alpha = RewardArtAlpha.MID, style = cache.stroke(frame.u(1.2f), StrokeCap.Round))
    drawPath(rail, hue, alpha = alphaOf(RewardArtAlpha.MID, g), style = cache.stroke(frame.u(1.2f), StrokeCap.Round, StrokeJoin.Round))
    drawPath(coil, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2.6f), StrokeCap.Round, StrokeJoin.Round))
    drawCircle(hue, r * 0.11f, Offset(cx, cy), alpha = alphaOf(RewardArtAlpha.LOW, g))
    drawCircle(bone, r * 0.04f, Offset(cx, cy), alpha = RewardArtAlpha.HIGH)
    drawPath(embers, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g))
}

/** Shatterglass: frozen shards bursting outward (straight cut ends). */
internal fun DrawScope.drawShatterglassArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.6f, 0.6f, 1.05f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val crack = cache.floats(0, ShatterCracks * ShatterJoints * 2) { shatterCrackPoints(it, frame, cx, cy, r) }
    val pieces = cache.floats(1, ShatterPieces * 9) { shatterPieces(it, crack, frame, cx, cy, r) }
    val lit = cache.path(0) { shatterPieceFills(pieces, 0) }
    val mid = cache.path(1) { shatterPieceFills(pieces, 1) }
    val dim = cache.path(2) { shatterPieceFills(pieces, 2) }
    val edges = cache.path(3) { shatterPieceFills(pieces, -1) }
    val facets = cache.path(4) {
        for (piece in 0 until ShatterPieces) {
            val b = piece * 9
            moveTo(pieces[b + 2], pieces[b + 3])
            lineTo((pieces[b + 4] + pieces[b + 6]) * 0.5f, (pieces[b + 5] + pieces[b + 7]) * 0.5f)
        }
    }
    val runs = cache.path(5) {
        // The cracks run on past the burst, kinking once more.
        for (k in 0 until ShatterCracks) {
            val end = (k * ShatterJoints + ShatterJoints - 1) * 2
            val x = crack[end]
            val y = crack[end + 1]
            val dx = x - cx
            val dy = y - cy
            val turn = (if (k % 2 == 0) 0.12f else -0.12f)
            moveTo(x, y)
            lineTo(x + dx * 0.22f - dy * turn * 0.3f, y + dy * 0.22f + dx * turn * 0.3f)
            lineTo(x + dx * 0.42f - dy * turn * 0.1f, y + dy * 0.42f + dx * turn * 0.1f)
        }
    }
    val chips = cache.path(6) {
        for (index in 0 until 10) {
            val angle = (index * 36f + frame.random(30 + index) * 20f) * DEG
            val reach = r * (1.12f + 0.32f * frame.random(40 + index))
            val size = r * (0.026f + 0.026f * frame.random(50 + index))
            val c = cos(angle + 0.7f)
            val s = sin(angle + 0.7f)
            val ox = cx + reach * cos(angle)
            val oy = cy + reach * sin(angle)
            local(true, ox, oy, c, s, -size, -size * 0.6f)
            local(false, ox, oy, c, s, size, -size * 0.3f)
            local(false, ox, oy, c, s, size * 0.7f, size * 0.6f)
            local(false, ox, oy, c, s, -size * 0.8f, size * 0.4f)
            close()
        }
    }
    val hue = cache.brush(0) { fadeBrush(frame.hue, cx, cy, r * 1.55f) }
    val bone = cache.brush(1) { fadeBrush(Kk.Bone, cx, cy, r * 1.3f, 0.35f) }
    drawPath(dim, hue, alpha = alphaOf(RewardArtAlpha.LOW * 0.7f, g))
    drawPath(mid, hue, alpha = alphaOf(RewardArtAlpha.MID, g))
    drawPath(lit, hue, alpha = alphaOf(RewardArtAlpha.MID * 1.15f, g))
    drawPath(lit, bone, alpha = RewardArtAlpha.LOW * 0.6f)
    drawPath(facets, bone, alpha = RewardArtAlpha.LOW, style = cache.stroke(frame.u(0.8f)))
    drawPath(edges, hue, alpha = alphaOf(RewardArtAlpha.MID * 1.2f, g), style = cache.stroke(frame.u(1.1f)))
    drawPath(runs, hue, alpha = alphaOf(RewardArtAlpha.MID, g), style = cache.stroke(frame.u(1f)))
    drawPath(chips, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawCircle(bone, r * 0.05f, Offset(cx, cy), alpha = RewardArtAlpha.HIGH)
}

/** Storm Lattice: a lightning-filled triangular lattice. */
internal fun DrawScope.drawStormLatticeArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.55f, 0.56f, 1.0f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val pitch = r * 0.44f
    val nodes = cache.floats(0, LatticeCols * LatticeRows * 2) { out ->
        for (j in 0 until LatticeRows) for (i in 0 until LatticeCols) {
            val row = j - LatticeRows / 2
            val base = (j * LatticeCols + i) * 2
            out[base] = cx + (i - LatticeCols / 2 + 0.5f * (row and 1)) * pitch
            out[base + 1] = cy + row * pitch * SQRT3_HALF
        }
    }
    val edges = cache.path(0) {
        for (j in 0 until LatticeRows) for (i in 0 until LatticeCols) {
            val row = j - LatticeRows / 2
            if (i + 1 < LatticeCols) segment(nodes, i, j, i + 1, j)
            if (j + 1 < LatticeRows) {
                val left = if (row and 1 == 0) i - 1 else i
                if (left in 0 until LatticeCols) segment(nodes, i, j, left, j + 1)
                if (left + 1 in 0 until LatticeCols) segment(nodes, i, j, left + 1, j + 1)
            }
        }
    }
    val dots = cache.path(1) {
        for (index in 0 until LatticeCols * LatticeRows) {
            val half = pitch * 0.045f
            diamond(nodes[index * 2], nodes[index * 2 + 1], half)
        }
    }
    val cells = cache.path(2) {
        for (index in LatticeCells.indices step 6) {
            moveTo(node(nodes, LatticeCells[index], LatticeCells[index + 1], 0), node(nodes, LatticeCells[index], LatticeCells[index + 1], 1))
            lineTo(node(nodes, LatticeCells[index + 2], LatticeCells[index + 3], 0), node(nodes, LatticeCells[index + 2], LatticeCells[index + 3], 1))
            lineTo(node(nodes, LatticeCells[index + 4], LatticeCells[index + 5], 0), node(nodes, LatticeCells[index + 4], LatticeCells[index + 5], 1))
            close()
        }
    }
    val bolt = cache.path(3) {
        bolt(nodes, LatticeBolt, pitch, frame, 0)
        bolt(nodes, LatticeForkA, pitch, frame, 20)
        bolt(nodes, LatticeForkB, pitch, frame, 40)
        bolt(nodes, LatticeForkC, pitch, frame, 60)
    }
    val joints = cache.path(4) {
        for (index in LatticeBolt.indices step 2) {
            diamond(node(nodes, LatticeBolt[index], LatticeBolt[index + 1], 0), node(nodes, LatticeBolt[index], LatticeBolt[index + 1], 1), pitch * 0.09f)
        }
    }
    val hue = cache.brush(0) { fadeBrush(frame.hue, cx, cy, r * 1.6f, 0.4f) }
    val bone = cache.brush(1) { fadeBrush(Kk.Bone, cx, cy, r * 1.35f, 0.4f) }
    val hubX = node(nodes, LatticeBolt[6], LatticeBolt[7], 0)
    val hubY = node(nodes, LatticeBolt[6], LatticeBolt[7], 1)
    val charge = cache.brush(2) { glowBrush(frame.hue, hubX, hubY, pitch * 1.3f) }
    drawCircle(charge, pitch * 1.3f, Offset(hubX, hubY), alpha = alphaOf(RewardArtAlpha.MID, g))
    drawPath(cells, hue, alpha = alphaOf(RewardArtAlpha.MID, g))
    drawPath(edges, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1f)))
    drawPath(dots, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(bolt, hue, alpha = alphaOf(RewardArtAlpha.LOW, g), style = cache.stroke(frame.u(6f), StrokeCap.Round, StrokeJoin.Round))
    drawPath(bolt, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2.2f), StrokeCap.Round, StrokeJoin.Miter))
    drawPath(bolt, bone, alpha = RewardArtAlpha.MID, style = cache.stroke(frame.u(0.8f), StrokeCap.Round, StrokeJoin.Miter))
    drawPath(joints, hue, alpha = alphaOf(RewardArtAlpha.HIGH, g))
}

/** Dark Star: an accretion disk around a black core. */
internal fun DrawScope.drawDarkStarArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.58f, 0.6f, 1.0f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val center = Offset(cx, cy)
    val disk = r * 1.25f
    val hole = r * 0.22f
    val inner = r * 0.32f
    val back = cache.path(0) { annulusHalf(cx, cy, inner, disk, upper = true) }
    val front = cache.path(1) { annulusHalf(cx, cy, inner, disk, upper = false) }
    val stars = cache.path(2) {
        for (index in 0 until 7) {
            val angle = (index * 51f + frame.random(70 + index) * 30f) * DEG
            val reach = r * (0.55f + 0.7f * frame.random(80 + index))
            diamond(cx + reach * cos(angle), cy + reach * sin(angle) * 0.85f, r * (0.012f + 0.014f * frame.random(90 + index)))
        }
    }
    val hueColor = frame.hue
    val glow = cache.brush(0) {
        Brush.radialGradient(
            0f to hueColor.copy(alpha = 0f), (inner / disk) * 0.96f to hueColor.copy(alpha = 0f), (inner / disk) to hueColor,
            0.5f to hueColor.copy(alpha = 0.55f), 1f to hueColor.copy(alpha = 0f), center = center, radius = disk,
        )
    }
    val ring = cache.brush(1) { fadeBrush(hueColor, cx, cy, disk, 0.35f) }
    val halo = cache.brush(2) {
        Brush.verticalGradient(0f to hueColor, 0.55f to hueColor.copy(alpha = 0.35f), 1f to hueColor.copy(alpha = 0.7f),
            startY = cy - hole * 1.5f, endY = cy + hole * 1.5f)
    }
    val bone = cache.brush(3) { fadeBrush(Kk.Bone, cx, cy, r * 1.3f, 0.3f) }
    val thin = cache.stroke(frame.u(1f))
    // The far half of the disk, behind the core: the plane tilts up to the right, seen nearly edge on.
    rotate(DarkStarTilt, center) {
        scale(1f, DarkStarSquash, center) {
            drawPath(back, glow, alpha = alphaOf(RewardArtAlpha.HIGH, g))
            for (index in DarkStarRings.indices) {
                val radius = disk * DarkStarRings[index]
                drawArc(ring, 180f, 180f, false, Offset(cx - radius, cy - radius), Size(radius * 2f, radius * 2f),
                    alpha = alphaOf(RewardArtAlpha.HIGH, g), style = thin)
            }
        }
    }
    drawPath(stars, bone, alpha = RewardArtAlpha.MID)
    // The lensed far side of the disk wraps over the core; then the core itself, opaque.
    drawCircle(halo, hole * 1.42f, center, alpha = alphaOf(RewardArtAlpha.LOW, g), style = cache.stroke(frame.u(6f)))
    drawCircle(halo, hole * 1.22f, center, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2f)))
    drawCircle(Kk.Ink, hole, center)
    drawCircle(Kk.Bone, hole * 1.04f, center, alpha = RewardArtAlpha.MID, style = thin)
    // The near half crosses in front of the core.
    rotate(DarkStarTilt, center) {
        scale(1f, DarkStarSquash, center) {
            drawPath(front, glow, alpha = alphaOf(RewardArtAlpha.HIGH * 1.25f, g))
            drawArc(bone, 0f, 180f, false, Offset(cx - inner, cy - inner), Size(inner * 2f, inner * 2f),
                alpha = RewardArtAlpha.HIGH, style = cache.stroke(frame.u(2f)))
            for (index in DarkStarRings.indices) {
                val radius = disk * DarkStarRings[index]
                drawArc(ring, 0f, 180f, false, Offset(cx - radius, cy - radius), Size(radius * 2f, radius * 2f),
                    alpha = alphaOf(RewardArtAlpha.HIGH, g), style = thin)
            }
            for (index in DarkStarStreaks.indices step 3) {
                val radius = disk * DarkStarStreaks[index]
                drawArc(bone, DarkStarStreaks[index + 1], DarkStarStreaks[index + 2], false, Offset(cx - radius, cy - radius),
                    Size(radius * 2f, radius * 2f), alpha = RewardArtAlpha.HIGH, style = cache.stroke(frame.u(1.6f), StrokeCap.Round))
            }
        }
    }
}

/** Necrotic Exchange: rot tendrils weaving through repair hex cells. */
internal fun DrawScope.drawNecroticExchangeArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    // A long text covers most of this card: the honeycomb grows from the lower right corner and fades
    // out well below the name and chips; the rot creeps along the foot and up the right margin.
    val g = frame.spot(0.5f, 0.5f, 1f)[3]
    val cache = frame.cache
    val w = frame.bounds.width
    val h = frame.bounds.height
    val cx = frame.bounds.left + w * 0.8f
    val cy = frame.bounds.top + h * 0.84f
    val r = w * 0.36f
    val a = w * 0.088f
    val outline = cache.path(0) { hexCells(frame, cx, cy, a, HexPart.OUTLINE) }
    val rot = cache.path(1) { hexCells(frame, cx, cy, a, HexPart.ROT) }
    val mend = cache.path(2) { hexCells(frame, cx, cy, a, HexPart.MEND) }
    val samples = cache.floats(0, TendrilCount * 2 * TendrilSamples * 2) {
        tendrilSamples(it, frame.bounds.left, frame.bounds.top, w, h)
    }
    val tendrils = cache.path(3) { tendrils(samples, r) }
    val spores = cache.path(4) {
        for (index in 0 until TendrilCount) {
            for (step in 1 until 4) {
                val sample = index * TendrilSamples * 2 + step * (TendrilSamples * 2 / 4)
                val side = if ((index + step) % 2 == 0) 1f else -1f
                val x = samples[sample * 2] + side * r * 0.07f
                val y = samples[sample * 2 + 1] - r * 0.05f
                val radius = r * (0.016f + 0.01f * step)
                addOval(Rect(x - radius, y - radius, x + radius, y + radius))
            }
        }
    }
    // Faded radially from the lower right: nearly gone above 45 % of the face height.
    val reach = h * 0.5f
    val hue = cache.brush(0) { fadeBrush(frame.hue, cx, cy + h * 0.04f, reach, 0.42f) }
    val bone = cache.brush(1) { fadeBrush(Kk.Bone, cx, cy + h * 0.04f, reach * 0.85f, 0.42f) }
    val creep = cache.brush(2) { fadeBrush(frame.hue, frame.bounds.left + w * 0.62f, frame.bounds.top + h * 0.86f, w * 0.75f, 0.6f) }
    drawPath(rot, hue, alpha = min(alphaOf(RewardArtAlpha.LOW, g), NecroticCellAlpha))
    drawPath(outline, hue, alpha = min(alphaOf(RewardArtAlpha.HIGH, g), NecroticCellAlpha), style = cache.stroke(frame.u(1.3f)))
    drawPath(mend, bone, alpha = NecroticCellAlpha, style = cache.stroke(frame.u(1.6f)))
    // A dark casing cuts the cell lines where a tendril crosses, so the rot reads in front of them.
    drawPath(tendrils, Kk.Ink, alpha = 0.7f, style = cache.stroke(frame.u(3.5f), StrokeCap.Round, StrokeJoin.Round))
    drawPath(tendrils, creep, alpha = alphaOf(RewardArtAlpha.HIGH * 1.15f, g))
    drawPath(spores, creep, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.1f)))
}

/** Deadeye Reticle: a long-range scope reticle with range marks. */
internal fun DrawScope.drawDeadeyeReticleArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    // A whole scope low on the right, centred in the room below the text (about 45 % of the card wide
    // when there is room); its range lines run on to the card's edges.
    val g = frame.spot(0.66f, 0.9f, 1.05f)[3]
    val clear = frame.clear
    val top = max(clear.top, frame.textFloor)
    val bottom = frame.bounds.bottom - frame.u(8f)
    val room = (bottom - top).coerceAtLeast(frame.bounds.width * 0.3f)
    val housing = min(frame.bounds.width * 0.45f * 0.5f, room * 0.5f)
    val lens = housing / 1.16f
    val cx = clear.left + clear.width * (if (frame.compact) 0.7f else 0.66f)
    val cy = min(top + room * 0.5f, bottom - housing)
    val cache = frame.cache
    val center = Offset(cx, cy)
    val gap = lens * 0.08f
    val reach = lens * 1.34f
    val posts = cache.path(0) {
        val half = lens * 0.045f
        for (side in 0 until 4) {
            val c = cos(side * HALF_PI)
            val s = sin(side * HALF_PI)
            local(true, cx, cy, c, s, lens * 0.52f, -half)
            local(false, cx, cy, c, s, lens * 1.01f, -half * 1.5f)
            local(false, cx, cy, c, s, lens * 1.01f, half * 1.5f)
            local(false, cx, cy, c, s, lens * 0.52f, half)
            close()
        }
    }
    val hairs = cache.path(1) {
        for (side in 0 until 4) {
            val c = cos(side * HALF_PI)
            val s = sin(side * HALF_PI)
            local(true, cx, cy, c, s, gap, 0f)
            local(false, cx, cy, c, s, lens * 0.52f, 0f)
        }
        // The range lines beyond the housing, out to the card's edges.
        moveTo(frame.bounds.left, cy)
        lineTo(cx - reach, cy)
        moveTo(cx + reach, cy)
        lineTo(frame.bounds.right, cy)
        moveTo(cx, cy + reach)
        lineTo(cx, frame.bounds.bottom)
        moveTo(cx, cy - reach)
        lineTo(cx, cy - reach - lens * 0.9f)
    }
    val marks = cache.path(2) {
        // Mil dots along the hairs, then the holdover ladder under the center.
        for (side in 0 until 4) for (step in 1..3) {
            val c = cos(side * HALF_PI)
            val s = sin(side * HALF_PI)
            val d = gap + (lens * 0.52f - gap) * step / 4f
            val radius = lens * 0.022f
            addOval(Rect(cx + d * c - radius, cy + d * s - radius, cx + d * c + radius, cy + d * s + radius))
        }
        for (step in 0 until 3) {
            val y = cy + lens * (0.16f + step * 0.11f)
            val half = lens * (0.2f - step * 0.05f)
            addRect(Rect(cx - half, y - lens * 0.011f, cx + half, y + lens * 0.011f))
        }
    }
    val ruler = cache.path(3) {
        // Range ticks along the outer lines: long every third.
        val step = lens * 0.22f
        for (tick in 1 until 12) {
            val tall = if (tick % 3 == 0) lens * 0.13f else lens * 0.06f
            val left = cx - reach - tick * step
            if (left > frame.bounds.left) {
                moveTo(left, cy - tall)
                lineTo(left, cy + tall)
            }
            val right = cx + reach + tick * step
            if (right < frame.bounds.right) {
                moveTo(right, cy - tall)
                lineTo(right, cy + tall)
            }
        }
        for (tick in 0 until 36) {
            val angle = tick * 10f * DEG
            val r0 = lens * 1.2f
            val r1 = lens * (if (tick % 3 == 0) 1.3f else 1.25f)
            moveTo(cx + r0 * cos(angle), cy + r0 * sin(angle))
            lineTo(cx + r1 * cos(angle), cy + r1 * sin(angle))
        }
    }
    val glare = cache.path(4) {
        arcTo(Rect(cx - lens * 0.84f, cy - lens * 0.84f, cx + lens * 0.84f, cy + lens * 0.84f), 196f, 58f, true)
    }
    val bone = cache.brush(0) { fadeBrush(frame.hue, cx, cy, lens * 3.4f, 0.4f) }
    val gunmetal = cache.brush(3) { fadeBrush(Kk.Mute2, cx, cy, lens * 3f, 0.5f) }
    val glass = cache.brush(2) {
        Brush.radialGradient(0f to Kk.Bone.copy(alpha = 0.1f), 0.75f to Kk.Bone.copy(alpha = 0.35f), 1f to Kk.Bone.copy(alpha = 0.7f),
            center = Offset(cx - lens * 0.25f, cy - lens * 0.3f), radius = lens * 1.25f)
    }
    drawCircle(glass, lens, center, alpha = RewardArtAlpha.LOW)
    drawCircle(gunmetal, lens * 1.1f, center, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(6f)))
    drawCircle(bone, lens * 1.16f, center, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1f)))
    drawCircle(bone, lens, center, alpha = RewardArtAlpha.HIGH, style = cache.stroke(frame.u(2.4f)))
    drawPath(ruler, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.1f)))
    drawPath(posts, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(hairs, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.1f)))
    drawPath(marks, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawCircle(bone, gap * 0.4f, center, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(glare, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2.6f), StrokeCap.Round))
}

/** Prismatic Core: a prism splitting one beam into the element hues. */
internal fun DrawScope.drawPrismaticCoreArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.4f, 0.58f, 1.0f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val side = r * 1.08f
    val geometry = cache.floats(0, PrismFloats) { prismGeometry(it, frame, cx, cy, side, r) }
    val prism = cache.path(0) { triangle(geometry, 0) }
    val inset = cache.path(1) { triangle(geometry, 6) }
    val wedge = cache.path(2) {
        moveTo(geometry[PrismEntry], geometry[PrismEntry + 1])
        lineTo(geometry[PrismExits], geometry[PrismExits + 1])
        lineTo(geometry[PrismExits + 10], geometry[PrismExits + 11])
        close()
    }
    val beamIn = cache.path(3) {
        moveTo(geometry[PrismSource], geometry[PrismSource + 1])
        lineTo(geometry[PrismEntry], geometry[PrismEntry + 1])
    }
    val bone = cache.brush(0) { fadeBrush(Kk.Bone, cx, cy, r * 1.6f, 0.45f) }
    val source = cache.brush(1) {
        Brush.linearGradient(0f to Kk.Bone.copy(alpha = 0.15f), 1f to Kk.Bone,
            start = Offset(geometry[PrismSource], geometry[PrismSource + 1]), end = Offset(geometry[PrismEntry], geometry[PrismEntry + 1]))
    }
    drawPath(beamIn, source, alpha = RewardArtAlpha.LOW, style = cache.stroke(frame.u(10f), StrokeCap.Butt))
    drawPath(beamIn, source, alpha = RewardArtAlpha.HIGH, style = cache.stroke(frame.u(3f), StrokeCap.Butt))
    // Without hue (Mono) the beams step down in lightness instead, so the split still reads.
    val lowChroma = prismLowChroma(frame)
    for (band in 0 until PrismBands) {
        val path = cache.path(4 + band) { prismBand(geometry, band, 0f, 1f) }
        val element = PrismOrder[band]
        val color = if (lowChroma) kkMix(Kk.Bone, Kk.Ink, 0.1f + 0.13f * band) else frame.roles.element(element)
        val brush = cache.brush(2 + band) {
            Brush.linearGradient(0f to color, 0.55f to color.copy(alpha = 0.6f), 1f to color.copy(alpha = 0f),
                start = Offset(geometry[PrismExits + band * 2], geometry[PrismExits + band * 2 + 1]),
                end = Offset(geometry[PrismFar + band * 2], geometry[PrismFar + band * 2 + 1]))
        }
        drawPath(path, brush, alpha = RewardArtAlpha.HIGH)
        // Each beam carries its element's pattern near the prism, fading out in two steps.
        val pattern = kkFillBrush(elementFill(element), color)
        drawPath(cache.path(9 + band * 2) { prismBand(geometry, band, 0f, 0.22f) }, pattern, alpha = RewardArtAlpha.HIGH)
        drawPath(cache.path(10 + band * 2) { prismBand(geometry, band, 0.22f, 0.42f) }, pattern, alpha = RewardArtAlpha.LOW)
    }
    val entry = Offset(geometry[PrismEntry], geometry[PrismEntry + 1])
    val flare = cache.brush(7) { glowBrush(Kk.Bone, entry.x, entry.y, side * 0.32f) }
    drawPath(wedge, bone, alpha = RewardArtAlpha.LOW)
    drawPath(prism, bone, alpha = RewardArtAlpha.LOW * 0.5f)
    drawCircle(flare, side * 0.32f, entry, alpha = RewardArtAlpha.LOW)
    drawPath(inset, bone, alpha = alphaOf(RewardArtAlpha.MID, g), style = cache.stroke(frame.u(0.9f)))
    drawPath(prism, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2f), join = StrokeJoin.Miter))
}

/** Volatile Compound: a flask with rising bubbles and a fizzing meniscus. */
internal fun DrawScope.drawVolatileCompoundArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.64f, 0.62f, 1.0f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val f = cache.floats(0, FlaskFloats) { flaskGeometry(it, cx, cy, r) }
    val glass = cache.path(0) { flask(f, 0f) }
    val wall = cache.path(1) { flask(f, r * 0.045f) }
    val liquid = cache.path(2) { flaskLiquid(f) }
    val surface = cache.path(3) {
        moveTo(f[FlaskLiquidLeft], f[FlaskLevel] - f[FlaskMeniscus])
        quadraticTo(cx, f[FlaskLevel] + f[FlaskMeniscus], f[FlaskLiquidRight], f[FlaskLevel] - f[FlaskMeniscus])
    }
    val bubbles = cache.path(4) {
        for (index in FlaskBubbles.indices step 3) {
            val x = cx + FlaskBubbles[index] * r
            val y = f[FlaskBase] - FlaskBubbles[index + 1] * r
            val radius = FlaskBubbles[index + 2] * r
            addOval(Rect(x - radius, y - radius, x + radius, y + radius))
        }
    }
    val glints = cache.path(7) {
        for (index in FlaskBubbles.indices step 3) {
            val radius = FlaskBubbles[index + 2] * r
            if (radius < r * 0.03f) continue
            val x = cx + FlaskBubbles[index] * r - radius * 0.38f
            val y = f[FlaskBase] - FlaskBubbles[index + 1] * r - radius * 0.38f
            val dot = radius * 0.26f
            addOval(Rect(x - dot, y - dot, x + dot, y + dot))
        }
    }
    val fizz = cache.path(5) {
        // Fine spray just over the surface, densest in the middle.
        for (index in 0 until 12) {
            val t = (index + 0.5f) / 12f
            val x = f[FlaskLiquidLeft] + (f[FlaskLiquidRight] - f[FlaskLiquidLeft]) * (0.1f + 0.8f * t)
            val lift = 1f - kotlin.math.abs(t - 0.5f) * 1.4f
            val y = f[FlaskLevel] - r * (0.03f + 0.09f * lift * frame.random(110 + index))
            val radius = r * (0.008f + 0.009f * frame.random(120 + index))
            addOval(Rect(x - radius, y - radius, x + radius, y + radius))
        }
    }
    val ticks = cache.path(6) {
        for (step in 0 until 4) {
            val y = f[FlaskBase] - r * (0.2f + step * 0.16f)
            val x = flaskWallX(f, y, left = true) + r * 0.05f
            moveTo(x, y)
            lineTo(x + r * (if (step % 2 == 0) 0.14f else 0.08f), y)
        }
    }
    val bone = cache.brush(0) { fadeBrush(frame.hue, cx, cy - r * 0.2f, r * 1.5f, 0.45f) }
    // The liquid and the glass take a steel tinted toward the card hue, so the compound reads as its colour.
    val steel = cache.brush(1) { fadeBrush(kkMix(Kk.Mute, frame.hue, 0.5f), cx, cy, r * 1.5f, 0.45f) }
    drawPath(liquid, steel, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(liquid, bone, alpha = alphaOf(RewardArtAlpha.LOW * 0.6f, g))
    drawPath(wall, steel, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1f), join = StrokeJoin.Miter))
    drawPath(glass, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2f), join = StrokeJoin.Miter))
    drawPath(ticks, bone, alpha = alphaOf(RewardArtAlpha.MID, g), style = cache.stroke(frame.u(1f)))
    drawPath(surface, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.6f), StrokeCap.Round))
    drawPath(bubbles, steel, alpha = alphaOf(RewardArtAlpha.LOW, g))
    drawPath(bubbles, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.2f)))
    drawPath(glints, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(fizz, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g))
}

// region shared

private const val DEG = 0.017453292f
private const val HALF_PI = 1.5707964f
private const val SQRT3_HALF = 0.8660254f

/** The floats slot holding the art's spot (center x, y, reach and the hue's alpha gain). */
private const val SpotSlot = RewardArtCache.SLOTS - 1

/**
 * The spot an illustration is built around: at ([fx], [fy]) of the clear region, reaching [scale]
 * times the region's comfortable radius; [3] is an alpha gain that lifts dark hues and calms light
 * ones, so every element reads equally faint.
 */
private fun RewardArtFrame.spot(fx: Float, fy: Float, scale: Float): FloatArray = cache.floats(SpotSlot, 4) { out ->
    val area = clear
    // A phone card's text runs lower: its art sits further right and down, a little larger.
    val x = if (compact) (fx + 0.1f).coerceAtMost(0.8f) else fx
    val y = if (compact) fy + 0.18f else fy
    out[0] = area.left + area.width * x
    out[1] = area.top + area.height * y
    out[2] = min(area.width * 0.5f, area.height * 0.78f) * scale * (if (compact) 1.18f else 1f)
    out[3] = (1.3f - 0.5f * kkLuminance(hue)).coerceIn(0.9f, 1.22f)
}

/** [base] lifted by the hue's [gain], never past the art's ceiling ([RewardArtAlpha.HIGH]). */
private fun alphaOf(base: Float, gain: Float): Float = (base * gain).coerceAtMost(RewardArtAlpha.HIGH)

/** [color] at full strength out to [hold] of [radius] from the spot, clear at [radius]. */
private fun fadeBrush(color: Color, cx: Float, cy: Float, radius: Float, hold: Float = 0.55f): Brush =
    Brush.radialGradient(0f to color, hold to color, 1f to color.copy(alpha = 0f), center = Offset(cx, cy), radius = radius)

/** A soft round glow of [color]: full at its center, clear at [radius]. */
private fun glowBrush(color: Color, cx: Float, cy: Float, radius: Float): Brush =
    Brush.radialGradient(0f to color, 0.35f to color.copy(alpha = 0.55f), 1f to color.copy(alpha = 0f), center = Offset(cx, cy), radius = radius)

/** Moves (or draws) to the point [x] along and [y] across a local frame at ([ox], [oy]) turned to cos [c], sin [s]. */
private fun Path.local(move: Boolean, ox: Float, oy: Float, c: Float, s: Float, x: Float, y: Float) {
    val px = ox + x * c - y * s
    val py = oy + x * s + y * c
    if (move) moveTo(px, py) else lineTo(px, py)
}

/** A quadratic curve through the control point ([x1], [y1]) to ([x2], [y2]), in the same local frame as [local]. */
private fun Path.localQuad(ox: Float, oy: Float, c: Float, s: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
    quadraticTo(ox + x1 * c - y1 * s, oy + x1 * s + y1 * c, ox + x2 * c - y2 * s, oy + x2 * s + y2 * c)
}

/** A whole square turned 45° (never a cut spark). */
private fun Path.diamond(x: Float, y: Float, half: Float) {
    moveTo(x, y - half)
    lineTo(x + half, y)
    lineTo(x, y + half)
    lineTo(x - half, y)
    close()
}

// endregion

// region Phoenix Coil

/** Coverts along the coil's outer turn, then the primaries of the wing. */
private const val PhoenixCoverts = 7
private const val PhoenixPrimaries = 7
private const val PhoenixAll = PhoenixCoverts + PhoenixPrimaries
private const val PhoenixEmbers = 4

/** The coil turns clockwise and ends at its upper right, heading down and right, where the wing opens. */
private const val PhoenixTurn = 1.6f * 3.1415927f
private const val PhoenixEnd = -0.25f * 3.1415927f

/** How far a covert turns out from the coil's tangent (radians): low, so they overlap like scales. */
private const val PhoenixLean = 0.55f

/** The wing's sweep (radians) and the heading of its leading primary (up and to the right). */
private const val WingSweep = 2.0943951f
private const val WingLead = -0.62f

/** Primary lengths (share of the reach), leading edge first: longest near the lead, short at the trailing end. */
private val PrimaryLength = floatArrayOf(0.86f, 1.02f, 1.06f, 0.98f, 0.86f, 0.72f, 0.58f)

private fun phoenixRho(t: Float, r: Float): Float = r * 0.16f * exp(1.5f * t)

private fun phoenixTheta(t: Float): Float = PhoenixEnd - PhoenixTurn * (1f - t)

/** Per feather: base x, y, cos and sin of its heading, length and width (coverts first, then primaries). */
private fun phoenixFeatherFrames(out: FloatArray, cx: Float, cy: Float, r: Float) {
    for (index in 0 until PhoenixCoverts) {
        // Short, broad and rounded, growing along the coil's outer turn.
        val t = 0.25f + 0.7f * index / (PhoenixCoverts - 1f)
        val rho = phoenixRho(t, r)
        val theta = phoenixTheta(t)
        val heading = theta + HALF_PI - PhoenixLean
        val length = r * 0.14f + rho * 0.42f
        val base = index * 6
        out[base] = cx + rho * cos(theta)
        out[base + 1] = cy + rho * sin(theta)
        out[base + 2] = cos(heading)
        out[base + 3] = sin(heading)
        out[base + 4] = length
        out[base + 5] = length * 0.42f
    }
    // The wing's root: the coil's outer end, the roots spread a little along the wing's arm.
    val endRho = phoenixRho(1f, r)
    val rootX = cx + endRho * cos(PhoenixEnd)
    val rootY = cy + endRho * sin(PhoenixEnd)
    for (index in 0 until PhoenixPrimaries) {
        val share = index / (PhoenixPrimaries - 1f)
        val heading = WingLead + WingSweep * share
        val length = r * PrimaryLength[index]
        val base = (PhoenixCoverts + index) * 6
        out[base] = rootX + r * (0.04f + 0.16f * share)
        out[base + 1] = rootY + r * 0.16f * share
        out[base + 2] = cos(heading)
        out[base + 3] = sin(heading)
        out[base + 4] = length
        // Long primaries are slender; the short trailing ones a little broader.
        out[base + 5] = length * (0.15f + 0.07f * share)
    }
}

/** The lit leading edge of every primary. */
private fun Path.phoenixLeadingEdges(f: FloatArray) {
    for (index in PhoenixCoverts until PhoenixAll) {
        val b = index * 6
        local(true, f[b], f[b + 1], f[b + 2], f[b + 3], 0.3f * f[b + 4], -0.48f * f[b + 5])
        local(false, f[b], f[b + 1], f[b + 2], f[b + 3], 0.86f * f[b + 4], -0.4f * f[b + 5])
    }
}

/**
 * Feathers [from] until [to]: a vane swelling from the quill to a rounded tip with a small split in it
 * (never a point, which would read as an arrowhead).
 */
private fun Path.phoenixFeathers(f: FloatArray, from: Int, to: Int) {
    for (index in from until to) {
        val b = index * 6
        val ox = f[b]
        val oy = f[b + 1]
        val c = f[b + 2]
        val s = f[b + 3]
        val l = f[b + 4]
        val w = f[b + 5]
        local(true, ox, oy, c, s, 0f, -0.14f * w)
        localQuad(ox, oy, c, s, 0.22f * l, -0.56f * w, 0.62f * l, -0.5f * w)
        localQuad(ox, oy, c, s, 1.02f * l, -0.42f * w, 0.99f * l, -0.04f * w)
        // The split: a small notch just behind the tip's middle.
        local(false, ox, oy, c, s, 0.9f * l, 0.04f * w)
        localQuad(ox, oy, c, s, 0.95f * l, 0.5f * w, 0.6f * l, 0.5f * w)
        localQuad(ox, oy, c, s, 0.2f * l, 0.52f * w, 0f, 0.14f * w)
        close()
    }
}

/** The shaft of every feather, and barbs angling from it across the larger vanes. */
private fun Path.phoenixQuills(f: FloatArray) {
    for (index in 0 until PhoenixAll) {
        val b = index * 6
        val ox = f[b]
        val oy = f[b + 1]
        val c = f[b + 2]
        val s = f[b + 3]
        val l = f[b + 4]
        val w = f[b + 5]
        local(true, ox, oy, c, s, 0.02f * l, 0f)
        local(false, ox, oy, c, s, 0.86f * l, 0.02f * w)
        if (index >= PhoenixCoverts) {
            for (barb in 0 until 4) {
                val at = 0.28f + barb * 0.15f
                local(true, ox, oy, c, s, at * l, 0.02f * w)
                local(false, ox, oy, c, s, (at + 0.08f) * l, 0.4f * w)
                local(true, ox, oy, c, s, (at + 0.05f) * l, -0.02f * w)
                local(false, ox, oy, c, s, (at + 0.12f) * l, -0.38f * w)
            }
        }
    }
}

/** The coil the feathers sit on, wound tighter inward ([scale] draws a second rail inside it). */
private fun Path.phoenixCoil(cx: Float, cy: Float, r: Float, scale: Float) {
    val steps = 84
    for (step in 0..steps) {
        val t = -1f + 2f * step / steps
        val rho = phoenixRho(t, r) * scale
        val theta = phoenixTheta(t)
        val x = cx + rho * cos(theta)
        val y = cy + rho * sin(theta)
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
}

// endregion

// region Shatterglass

private const val ShatterCracks = 8
private const val ShatterJoints = 4
private val ShatterAngles = floatArrayOf(-168f, -124f, -80f, -36f, 6f, 46f, 88f, 130f)
private val ShatterReach = floatArrayOf(1.0f, 0.8f, 1.1f, 0.9f, 1.18f, 0.94f, 1.06f, 0.84f)
private val ShatterJointAt = floatArrayOf(0.06f, 0.34f, 0.62f, 1f)
private val ShatterWobble = floatArrayOf(0f, 4f, -5f, 3f)

/** Per crack, its joints from the impact outward (x, y each). */
private fun shatterCrackPoints(out: FloatArray, frame: RewardArtFrame, cx: Float, cy: Float, r: Float) {
    for (k in 0 until ShatterCracks) {
        val base = ShatterAngles[k] + (frame.random(k) - 0.5f) * 10f
        for (j in 0 until ShatterJoints) {
            val angle = (base + ShatterWobble[j] * (if (k % 2 == 0) 1f else -1f)) * DEG
            val reach = r * ShatterReach[k] * ShatterJointAt[j] * (if (j in 1..2) 0.94f + 0.12f * frame.random(10 + k * 4 + j) else 1f)
            val index = (k * ShatterJoints + j) * 2
            out[index] = cx + reach * cos(angle)
            out[index + 1] = cy + reach * sin(angle)
        }
    }
}

private const val ShatterPieces = ShatterCracks * (ShatterJoints - 1)

/**
 * Per piece of the burst (between two cracks and two rings): its four corners, pulled apart from
 * its neighbours and pushed outward the further out it sits (some outer pieces fly loose and turn),
 * and its tone (0 lit, 1 mid, 2 dim facet).
 */
private fun shatterPieces(out: FloatArray, crack: FloatArray, frame: RewardArtFrame, cx: Float, cy: Float, r: Float) {
    for (k in 0 until ShatterCracks) for (band in 0 until ShatterJoints - 1) {
        val next = (k + 1) % ShatterCracks
        val a = (k * ShatterJoints + band) * 2
        val b = (next * ShatterJoints + band) * 2
        val c = (next * ShatterJoints + band + 1) * 2
        val d = (k * ShatterJoints + band + 1) * 2
        val mx = (crack[a] + crack[b] + crack[c] + crack[d]) * 0.25f
        val my = (crack[a + 1] + crack[b + 1] + crack[c + 1] + crack[d + 1]) * 0.25f
        val dx = mx - cx
        val dy = my - cy
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        val loose = band == ShatterJoints - 2 && frame.random(300 + k) < 0.35f
        val push = r * (0.015f + 0.03f * band + (if (loose) 0.14f else 0f))
        val spin = if (loose) (frame.random(310 + k) - 0.5f) * 0.6f else 0f
        val cs = cos(spin)
        val sn = sin(spin)
        val ox = mx + dx / length * push
        val oy = my + dy / length * push
        val base = (k * (ShatterJoints - 1) + band) * 9
        var slot = 0
        for (corner in intArrayOf(a, b, c, d)) {
            val vx = (crack[corner] - mx) * 0.88f
            val vy = (crack[corner + 1] - my) * 0.88f
            out[base + slot] = ox + vx * cs - vy * sn
            out[base + slot + 1] = oy + vx * sn + vy * cs
            slot += 2
        }
        out[base + 8] = (frame.random(320 + k * 3 + band) * 2.999f).toInt().toFloat()
    }
}

/** The pieces of [tone] (all of them for −1). */
private fun Path.shatterPieceFills(f: FloatArray, tone: Int) {
    for (piece in 0 until ShatterPieces) {
        val b = piece * 9
        if (tone >= 0 && f[b + 8].toInt() != tone) continue
        moveTo(f[b], f[b + 1])
        lineTo(f[b + 2], f[b + 3])
        lineTo(f[b + 4], f[b + 5])
        lineTo(f[b + 6], f[b + 7])
        close()
    }
}

// endregion

// region Storm Lattice

private const val LatticeCols = 9
private const val LatticeRows = 7

/** Lattice nodes as (column, row) pairs: the main bolt, upper left to lower right through the center. */
private val LatticeBolt = intArrayOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 5, 6, 6)
private val LatticeForkA = intArrayOf(3, 3, 4, 3, 4, 2, 5, 2)
private val LatticeForkB = intArrayOf(5, 5, 4, 6)
private val LatticeForkC = intArrayOf(4, 4, 5, 4, 6, 4, 7, 3)

/** Charged cells (three nodes each), filled faintly along the bolt. */
private val LatticeCells = intArrayOf(
    1, 2, 2, 2, 2, 3,
    3, 4, 4, 4, 4, 3,
    5, 5, 6, 5, 5, 6,
    3, 3, 4, 3, 3, 2,
    5, 4, 6, 4, 6, 5,
)

private fun node(nodes: FloatArray, i: Int, j: Int, axis: Int): Float = nodes[(j * LatticeCols + i) * 2 + axis]

private fun Path.segment(nodes: FloatArray, i0: Int, j0: Int, i1: Int, j1: Int) {
    moveTo(node(nodes, i0, j0, 0), node(nodes, i0, j0, 1))
    lineTo(node(nodes, i1, j1, 0), node(nodes, i1, j1, 1))
}

/** A lightning bolt along lattice edges: every edge kinks twice across its line. */
private fun Path.bolt(nodes: FloatArray, route: IntArray, pitch: Float, frame: RewardArtFrame, salt: Int) {
    var x0 = node(nodes, route[0], route[1], 0)
    var y0 = node(nodes, route[0], route[1], 1)
    moveTo(x0, y0)
    for (index in 2 until route.size step 2) {
        val x1 = node(nodes, route[index], route[index + 1], 0)
        val y1 = node(nodes, route[index], route[index + 1], 1)
        val dx = x1 - x0
        val dy = y1 - y0
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        val nx = -dy / length
        val ny = dx / length
        val kick = pitch * (0.1f + 0.08f * frame.random(salt + index))
        val sign = if ((index / 2 + salt) % 2 == 0) 1f else -1f
        lineTo(x0 + dx * 0.36f + nx * kick * sign, y0 + dy * 0.36f + ny * kick * sign)
        lineTo(x0 + dx * 0.64f - nx * kick * sign, y0 + dy * 0.64f - ny * kick * sign)
        lineTo(x1, y1)
        x0 = x1
        y0 = y1
    }
}

// endregion

// region Dark Star

private const val DarkStarTilt = -14f
private const val DarkStarSquash = 0.3f
private val DarkStarRings = floatArrayOf(0.31f, 0.36f, 0.42f, 0.5f, 0.6f, 0.72f, 0.86f, 1f)

/** Hot clumps of matter on the disk: radius share, start angle and sweep (near half). */
private val DarkStarStreaks = floatArrayOf(0.4f, 140f, 30f, 0.58f, 24f, 26f, 0.74f, 104f, 38f, 0.5f, 62f, 18f)

/** The upper (far) or lower (near) half of the disk ring between [inner] and [outer]. */
private fun Path.annulusHalf(cx: Float, cy: Float, inner: Float, outer: Float, upper: Boolean) {
    val start = if (upper) 180f else 0f
    arcTo(Rect(cx - outer, cy - outer, cx + outer, cy + outer), start, 180f, true)
    arcTo(Rect(cx - inner, cy - inner, cx + inner, cy + inner), start + 180f, -180f, false)
    close()
}

// endregion

// region Necrotic Exchange

private enum class HexPart { OUTLINE, ROT, MEND }

private const val HexCols = 9
private const val HexRows = 11

/** What fills a cell at ([i], [j]): 0 a whole repair cell with its mend mark, 1 an eaten cell, 2 an empty one. */
private fun hexKind(frame: RewardArtFrame, i: Int, j: Int): Int {
    val roll = frame.random(200 + j * HexCols + i)
    return when {
        roll < 0.3f -> 1
        roll < 0.52f -> 0
        else -> 2
    }
}

private fun Path.hexCells(frame: RewardArtFrame, cx: Float, cy: Float, a: Float, part: HexPart) {
    val w = a * 1.7320508f
    for (j in 0 until HexRows) for (i in 0 until HexCols) {
        val row = j - HexRows / 2
        val x = cx + (i - HexCols / 2 + 0.5f * (row and 1)) * w
        val y = cy + row * a * 1.5f
        val kind = hexKind(frame, i, j)
        val radius = a * 0.86f
        when (part) {
            HexPart.OUTLINE -> {
                // Eaten cells lose two of their walls.
                for (edge in 0 until 6) {
                    if (kind == 1 && (edge == (i + j) % 6 || edge == (i + j + 2) % 6)) continue
                    val a0 = (-90f + edge * 60f) * DEG
                    val a1 = (-30f + edge * 60f) * DEG
                    moveTo(x + radius * cos(a0), y + radius * sin(a0))
                    lineTo(x + radius * cos(a1), y + radius * sin(a1))
                }
            }
            HexPart.ROT -> if (kind == 1) {
                for (corner in 0 until 6) {
                    val angle = (-90f + corner * 60f) * DEG
                    val px = x + radius * 0.9f * cos(angle)
                    val py = y + radius * 0.9f * sin(angle)
                    if (corner == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            HexPart.MEND -> if (kind == 0) {
                val arm = a * 0.2f
                moveTo(x - arm, y)
                lineTo(x + arm, y)
                moveTo(x, y - arm)
                lineTo(x, y + arm)
            }
        }
    }
}

private const val TendrilSamples = 14

/** The honeycomb's strongest cell lines and fills: faint enough that the cells never fight the text. */
private const val NecroticCellAlpha = 0.12f

/** Tendrils in card fractions, two cubic segments each: start, two controls, joint, two controls, end (14 floats). */
private val NecroticTendrils = floatArrayOf(
    // From the lower left corner along the foot of the card, then up the right margin.
    -0.02f, 1.0f, 0.3f, 0.92f, 0.5f, 1.03f, 0.74f, 0.9f,
    0.98f, 0.76f, 0.88f, 0.64f, 0.95f, 0.54f,
    // In from the right edge, curling down into the cells of the lower right.
    1.03f, 0.62f, 0.9f, 0.64f, 0.95f, 0.74f, 0.82f, 0.8f,
    0.7f, 0.86f, 0.72f, 0.95f, 0.84f, 0.95f,
    // Up from the foot, winding into the honeycomb.
    0.3f, 1.03f, 0.36f, 0.94f, 0.48f, 0.99f, 0.56f, 0.91f,
    0.64f, 0.84f, 0.6f, 0.78f, 0.7f, 0.75f,
)

private val TendrilCount = NecroticTendrils.size / 14

/** Samples (x, y) along every tendril across the card: [TendrilSamples] per segment, both segments in a row. */
private fun tendrilSamples(out: FloatArray, left: Float, top: Float, width: Float, height: Float) {
    for (t in 0 until TendrilCount) {
        val p = t * 14
        for (segment in 0 until 2) {
            val o = p + segment * 6
            val x0 = NecroticTendrils[o]
            val y0 = NecroticTendrils[o + 1]
            val x1 = NecroticTendrils[o + 2]
            val y1 = NecroticTendrils[o + 3]
            val x2 = NecroticTendrils[o + 4]
            val y2 = NecroticTendrils[o + 5]
            val x3 = NecroticTendrils[o + 6]
            val y3 = NecroticTendrils[o + 7]
            for (step in 0 until TendrilSamples) {
                val u = if (segment == 0) step / TendrilSamples.toFloat() else step / (TendrilSamples - 1f)
                val v = 1f - u
                val bx = v * v * v * x0 + 3f * v * v * u * x1 + 3f * v * u * u * x2 + u * u * u * x3
                val by = v * v * v * y0 + 3f * v * v * u * y1 + 3f * v * u * u * y2 + u * u * u * y3
                val index = ((t * 2 + segment) * TendrilSamples + step) * 2
                out[index] = left + bx * width
                out[index + 1] = top + by * height
            }
        }
    }
}

/** Each tendril as a filled sliver tapering from its root to a fine tip. */
private fun Path.tendrils(samples: FloatArray, r: Float) {
    val count = TendrilSamples * 2
    val tendrils = samples.size / (count * 2)
    for (t in 0 until tendrils) {
        val start = t * count
        for (pass in 0 until 2) {
            for (n in 0 until count) {
                val step = if (pass == 0) n else count - 1 - n
                val index = (start + step) * 2
                val prev = (start + (step - 1).coerceAtLeast(0)) * 2
                val next = (start + (step + 1).coerceAtMost(count - 1)) * 2
                val dx = samples[next] - samples[prev]
                val dy = samples[next + 1] - samples[prev + 1]
                val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
                val progress = step / (count - 1f)
                val half = r * (0.045f * (1f - progress) + 0.004f)
                val side = if (pass == 0) 1f else -1f
                val x = samples[index] - dy / length * half * side
                val y = samples[index + 1] + dx / length * half * side
                if (pass == 0 && n == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        close()
    }
}

// endregion

// region Prismatic Core

private const val PrismBands = 5

/** Element order of the fanned beams, least bent first: thermal, entropy, cryo, ion, gravitic (a spectrum). */
private val PrismOrder = intArrayOf(0, 4, 1, 2, 3)

private const val PrismEntry = 12
private const val PrismSource = 14
private const val PrismExits = 16
private const val PrismFar = 28
private const val PrismFloats = 40

/**
 * The prism (apex up): outline 0..5, inset 6..11, the beam entry 12, its source at the left edge 14,
 * the six exit points along the right face 16..27 and the six far ends of the fan 28..39.
 */
private fun prismGeometry(out: FloatArray, frame: RewardArtFrame, cx: Float, cy: Float, side: Float, r: Float) {
    val height = side * SQRT3_HALF
    val topX = cx
    val topY = cy - height * 0.62f
    val leftX = cx - side * 0.5f
    val rightX = cx + side * 0.5f
    val baseY = cy + height * 0.38f
    out[0] = topX; out[1] = topY
    out[2] = rightX; out[3] = baseY
    out[4] = leftX; out[5] = baseY
    val inset = side * 0.075f
    out[6] = topX; out[7] = topY + inset * 2f
    out[8] = rightX - inset * 1.73f; out[9] = baseY - inset
    out[10] = leftX + inset * 1.73f; out[11] = baseY - inset
    // The white beam meets the left face 45 % up, climbing gently from the card's left edge.
    val ex = leftX + (topX - leftX) * 0.45f
    val ey = baseY + (topY - baseY) * 0.45f
    out[PrismEntry] = ex; out[PrismEntry + 1] = ey
    val sourceX = frame.face.left - side * 0.1f
    out[PrismSource] = sourceX
    out[PrismSource + 1] = ey + (ex - sourceX) * 0.2f
    // Exits from 34 % to 58 % up the right face; the fan spreads from 4° to 30° below the horizon.
    for (k in 0..PrismBands) {
        val f = 0.58f - 0.24f * k / PrismBands
        val x = rightX + (topX - rightX) * f
        val y = baseY + (topY - baseY) * f
        out[PrismExits + k * 2] = x
        out[PrismExits + k * 2 + 1] = y
        val angle = (4f + 26f * k / PrismBands) * DEG
        val reach = r * 2.2f
        out[PrismFar + k * 2] = x + reach * cos(angle)
        out[PrismFar + k * 2 + 1] = y + reach * sin(angle)
    }
}

private fun Path.triangle(f: FloatArray, at: Int) {
    moveTo(f[at], f[at + 1])
    lineTo(f[at + 2], f[at + 3])
    lineTo(f[at + 4], f[at + 5])
    close()
}

/** The stretch of beam [band] from [from] to [to] of its length (0 at the prism face, 1 at its far end). */
private fun Path.prismBand(f: FloatArray, band: Int, from: Float, to: Float) {
    fun x(edge: Int, t: Float): Float = f[PrismExits + edge * 2] + (f[PrismFar + edge * 2] - f[PrismExits + edge * 2]) * t
    fun y(edge: Int, t: Float): Float = f[PrismExits + edge * 2 + 1] + (f[PrismFar + edge * 2 + 1] - f[PrismExits + edge * 2 + 1]) * t
    moveTo(x(band, from), y(band, from))
    lineTo(x(band + 1, from), y(band + 1, from))
    lineTo(x(band + 1, to), y(band + 1, to))
    lineTo(x(band, to), y(band, to))
    close()
}

/** Whether the palette's element ramp has (almost) no hue: Mono, where the beams must split by lightness. */
private fun prismLowChroma(frame: RewardArtFrame): Boolean {
    for (element in 0 until PrismBands) {
        val c = frame.roles.element(element)
        if (max(c.red, max(c.green, c.blue)) - min(c.red, min(c.green, c.blue)) > 0.12f) return false
    }
    return true
}

// endregion

// region Volatile Compound

private const val FlaskBase = 0
private const val FlaskTop = 1
private const val FlaskNeck = 2
private const val FlaskLevel = 3
private const val FlaskMeniscus = 4
private const val FlaskLiquidLeft = 5
private const val FlaskLiquidRight = 6
private const val FlaskCx = 7
private const val FlaskBodyHalf = 8
private const val FlaskNeckHalf = 9
private const val FlaskChamfer = 10
private const val FlaskLip = 11
private const val FlaskFloats = 12

/** Bubbles: x from the center and height above the base (spot radii), and radius. Inside the liquid, then rising out of the neck. */
private val FlaskBubbles = floatArrayOf(
    -0.32f, 0.11f, 0.05f, 0.2f, 0.15f, 0.035f, -0.08f, 0.24f, 0.06f, 0.38f, 0.08f, 0.03f,
    0.04f, 0.1f, 0.025f, -0.2f, 0.3f, 0.022f, 0.12f, 0.3f, 0.028f,
    0.03f, 0.56f, 0.03f, -0.04f, 0.74f, 0.04f, 0.05f, 0.94f, 0.05f, -0.02f, 1.16f, 0.04f,
    0.08f, 1.5f, 0.032f, -0.07f, 1.66f, 0.024f,
)

private fun flaskGeometry(out: FloatArray, cx: Float, cy: Float, r: Float) {
    val base = cy + r * 0.62f
    out[FlaskBase] = base
    out[FlaskTop] = base - r * 1.38f
    out[FlaskNeck] = base - r * 0.84f
    out[FlaskCx] = cx
    out[FlaskBodyHalf] = r * 0.64f
    out[FlaskNeckHalf] = r * 0.15f
    out[FlaskChamfer] = r * 0.07f
    out[FlaskLip] = r * 0.05f
    out[FlaskLevel] = base - r * 0.42f
    out[FlaskMeniscus] = r * 0.035f
    out[FlaskLiquidLeft] = flaskWallX(out, out[FlaskLevel] - out[FlaskMeniscus], left = true)
    out[FlaskLiquidRight] = flaskWallX(out, out[FlaskLevel] - out[FlaskMeniscus], left = false)
}

/** The inner face of the cone wall at height [y]. */
private fun flaskWallX(f: FloatArray, y: Float, left: Boolean): Float {
    val bottom = f[FlaskBase] - f[FlaskChamfer]
    val t = ((bottom - y) / (bottom - f[FlaskNeck])).coerceIn(0f, 1f)
    val half = f[FlaskBodyHalf] + (f[FlaskNeckHalf] - f[FlaskBodyHalf]) * t
    return if (left) f[FlaskCx] - half else f[FlaskCx] + half
}

/** The conical flask outline, [inset] inside the glass for its inner wall. */
private fun Path.flask(f: FloatArray, inset: Float) {
    val cx = f[FlaskCx]
    val base = f[FlaskBase] - inset
    val top = f[FlaskTop] + inset
    val neck = f[FlaskNeck]
    val body = f[FlaskBodyHalf] - inset * 1.4f
    val neckHalf = f[FlaskNeckHalf] - inset
    val chamfer = f[FlaskChamfer]
    val lip = if (inset == 0f) f[FlaskLip] else 0f
    val lipHeight = f[FlaskLip] * 0.9f
    if (lip > 0f) {
        moveTo(cx - neckHalf - lip, top)
        lineTo(cx + neckHalf + lip, top)
        lineTo(cx + neckHalf + lip, top + lipHeight)
        lineTo(cx + neckHalf, top + lipHeight)
    } else {
        // The inner wall stays open at the mouth.
        moveTo(cx + neckHalf, top + lipHeight)
    }
    lineTo(cx + neckHalf, neck)
    lineTo(cx + body, base - chamfer)
    lineTo(cx + body - chamfer, base)
    lineTo(cx - body + chamfer, base)
    lineTo(cx - body, base - chamfer)
    lineTo(cx - neckHalf, neck)
    if (lip > 0f) {
        lineTo(cx - neckHalf, top + lipHeight)
        lineTo(cx - neckHalf - lip, top + lipHeight)
        close()
    } else {
        lineTo(cx - neckHalf, top + lipHeight)
    }
}

/** The liquid below the meniscus, inside the glass. */
private fun Path.flaskLiquid(f: FloatArray) {
    val cx = f[FlaskCx]
    val base = f[FlaskBase] - f[FlaskChamfer] * 0.4f
    val level = f[FlaskLevel]
    val rise = f[FlaskMeniscus]
    val body = f[FlaskBodyHalf] - f[FlaskChamfer] * 0.6f
    moveTo(f[FlaskLiquidLeft], level - rise)
    quadraticTo(cx, level + rise, f[FlaskLiquidRight], level - rise)
    lineTo(cx + body, base - f[FlaskChamfer])
    lineTo(cx + body - f[FlaskChamfer], base)
    lineTo(cx - body + f[FlaskChamfer], base)
    lineTo(cx - body, base - f[FlaskChamfer])
    close()
}

// endregion
