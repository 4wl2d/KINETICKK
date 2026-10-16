// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.ElementBurstProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.BuildProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkTextStats
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Enemy statuses read from [EnemyProjection.statusBits] as drawn: the dominant element's pattern
 * inside the silhouette, an arc whose sweep is the stacks over the Break threshold, the rim pip of
 * a ready reaction, the crowded level of detail, two-tone reaction flashes, and no text at all.
 * Affinity ([EnemyProjection.affinityBits]) keeps clear of those channels: an inner contour in the
 * aspect's hue under the threat outline, and a weakness tab at the lower left.
 */
class WorldStatusRenderingTest {
    private val roles = KkRolePalette.Default
    private val thermal = roles.element(StatusElement.THERMAL.ordinal)
    private val cryo = roles.element(StatusElement.CRYO.ordinal)
    private val ion = roles.element(StatusElement.ION.ordinal)
    private val entropy = roles.element(StatusElement.ENTROPY.ordinal)

    @Test
    fun theDominantPatternFillsTheSilhouetteInItsElementHue() {
        val plain = render(listOf(enemy(0)))
        val burning = render(listOf(enemy(4 shl (StatusElement.THERMAL.ordinal * 4))))
        // Inside the silhouette (radius 30 around the screen center), well inside the status ring.
        assertEquals(0, plain.countNear(thermal, CENTER, 0f, 18f, minStrength = 0.35f), "no status, no element hue")
        assertTrue(burning.countNear(thermal, CENTER, 0f, 18f, minStrength = 0.35f) > 30, "Thermal stacks tint the body in the Thermal hue")
        // The fill grows stronger as the stacks approach the Break.
        val nearBreak = render(listOf(enemy(7 shl (StatusElement.THERMAL.ordinal * 4))))
        assertTrue(nearBreak.alphaSum(thermal, CENTER, 0f, 18f) > burning.alphaSum(thermal, CENTER, 0f, 18f))
    }

    @Test
    fun theArcSweepsTheStacksOverTheBreakThreshold() {
        val ring = RADIUS + 4f
        // Strong pixels only: the faint full track under the arc does not count.
        fun arc(stacks: Int) = render(listOf(enemy(stacks shl (StatusElement.THERMAL.ordinal * 4))))
            .countNear(thermal, CENTER, ring - 1.5f, ring + 1.5f, minStrength = 0.5f)
        val quarter = arc(2)
        val threeQuarters = arc(6)
        assertTrue(quarter > 0, "two of eight stacks draw part of the arc")
        assertTrue(threeQuarters > quarter * 2, "six of eight stacks sweep further ($quarter, $threeQuarters)")
    }

    @Test
    fun anEmbrittledEnemyReadsCryosBreakTwoStacksSooner() {
        val model = model(emptyList())
        val cryoIndex = StatusElement.CRYO.ordinal
        assertEquals(6, model.enemyBreakThresholdOf(cryoIndex, 0))
        assertEquals(4, model.enemyBreakThresholdOf(cryoIndex, EnemyStatusBits.EMBRITTLED), "6 − 2, at the Cryo floor of 4")
        assertEquals(8, model.enemyBreakThresholdOf(StatusElement.THERMAL.ordinal, EnemyStatusBits.EMBRITTLED), "only Cryo comes sooner")
        // The arc sweeps two of four stacks, not two of six.
        val ring = RADIUS + 4f
        fun arc(bits: Int) = render(listOf(enemy(bits))).countNear(cryo, CENTER, ring - 1.5f, ring + 1.5f, minStrength = 0.5f)
        val plain = arc(2 shl (cryoIndex * 4))
        val embrittled = arc((2 shl (cryoIndex * 4)) or EnemyStatusBits.EMBRITTLED)
        assertTrue(embrittled > plain * 1.3f, "the Embrittled arc sweeps further ($plain, $embrittled)")
    }

    @Test
    fun thePrimedMarkFollowsTheElementOneStackShortOfItsBreak() {
        val model = model(emptyList())
        fun stacks(element: StatusElement, count: Int) = count shl (element.ordinal * 4)
        fun primed(bits: Int) = model.primedElement(bits or EnemyStatusBits.PRIMED, EnemyStatusBits.dominant(bits))
        assertEquals(StatusElement.THERMAL.ordinal, primed(stacks(StatusElement.THERMAL, 7) or stacks(StatusElement.CRYO, 2)))
        // Thermal leads by stacks, but Ion (threshold 7) is the one a stack short: the pip, not the arc, pulses.
        assertEquals(StatusElement.ION.ordinal, primed(stacks(StatusElement.THERMAL, 6) or stacks(StatusElement.ION, 6)))
        // On an Embrittled enemy, three Cryo stacks are a stack short of its nearer Break.
        assertEquals(StatusElement.CRYO.ordinal, primed(stacks(StatusElement.CRYO, 3) or EnemyStatusBits.EMBRITTLED))
        assertEquals(-1, model.primedElement(stacks(StatusElement.THERMAL, 7), StatusElement.THERMAL.ordinal), "no flag, no mark")
    }

    @Test
    fun frozenEnemiesHoldTheirPoseAndEaseBackIntoTheirTurn() {
        val id = 4_093
        assertEquals(10f, FrozenPoses.rotation(id, frozen = true, elapsed = 1f, turning = 10f))
        assertEquals(10f, FrozenPoses.rotation(id, frozen = true, elapsed = 1.1f, turning = 12f), "the ice holds the pose")
        assertEquals(10f, FrozenPoses.rotation(id, frozen = true, elapsed = 1.2f, turning = 30f))
        // Thawed, it starts from the held pose and is back on its turn after the ease.
        assertEquals(10f, FrozenPoses.rotation(id, frozen = false, elapsed = 1.22f, turning = 30.4f), 0.01f)
        val easing = FrozenPoses.rotation(id, frozen = false, elapsed = 1.4f, turning = 34f)
        assertTrue(easing > 10f && easing < 34f, "easing back: $easing")
        assertEquals(36f, FrozenPoses.rotation(id, frozen = false, elapsed = 1.7f, turning = 36f), "back on its turn")
        // An enemy that was never frozen just turns.
        assertEquals(50f, FrozenPoses.rotation(id + 1, frozen = false, elapsed = 1.7f, turning = 50f))
    }

    @Test
    fun theRimPipShowsASecondElementReadyToReact() {
        val ring = RADIUS + 4f
        val pip = androidx.compose.ui.geometry.Offset(CENTER.x + ring * 0.7071f, CENTER.y - ring * 0.7071f)
        val dominant = 5 shl (StatusElement.THERMAL.ordinal * 4)
        val one = render(listOf(enemy(dominant or (1 shl (StatusElement.CRYO.ordinal * 4)))))
        val two = render(listOf(enemy(dominant or (2 shl (StatusElement.CRYO.ordinal * 4)))))
        assertEquals(0, one.countNear(cryo, pip, 0f, 3f), "a single Cryo stack sets off nothing yet")
        assertTrue(two.countNear(cryo, pip, 0f, 3f) > 4, "two Cryo stacks next to Thermal mean a reaction is ready")
    }

    @Test
    fun crowdedScreensTintOrdinaryEnemiesFlatAndKeepEliteDetail() {
        val model = model(emptyList())
        fun crowd(count: Int) = model.with("enemies" to List(count) { index ->
            EnemyProjection(index + 10, EnemyType.DRIFTER, (index % 10) * 36f - 170f, (index / 10) * 36f - 120f, 0f, 0f, 50f, 100f, 12f,
                1f, 0f, 0f, 0f, 0f, 0f, false)
        }.toImmutableList())
        assertFalse(draw(crowd(STATUS_PATTERN_ENEMY_LIMIT)) { crowdedForStatusPatterns(it, 0f, 0f) }, "sixty enemies keep their patterns")
        assertTrue(draw(crowd(STATUS_PATTERN_ENEMY_LIMIT + 1)) { crowdedForStatusPatterns(it, 0f, 0f) }, "the sixty-first drops them")
        // Drawn in a crowd, an ordinary enemy's body becomes one flat tint; an elite keeps its pattern.
        val status = 6 shl (StatusElement.ION.ordinal * 4)
        listOf(EnemyType.DRIFTER to true, EnemyType.ELITE to false).forEach { (type, flattens) ->
            val alone = drawEnemyAt(type, status, detail = true)
            val crowded = drawEnemyAt(type, status, detail = false)
            val alonePixels = alone.distinctNear(ion, CENTER, 0f, 10f)
            val crowdedPixels = crowded.distinctNear(ion, CENTER, 0f, 10f)
            if (flattens) assertTrue(crowdedPixels < alonePixels, "$type flattens in a crowd ($alonePixels, $crowdedPixels)")
            else assertEquals(alonePixels, crowdedPixels, "$type keeps its pattern")
        }
    }

    @Test
    fun anAspectContoursTheBodyInsideTheThreatOutlineAndClearOfTheStatusRing() {
        val shooter = runAffinity(EnemyType.SHOOTER)
        val neutral = drawEnemyAt(EnemyType.SHOOTER, 0, detail = true)
        val aspected = drawEnemyAt(EnemyType.SHOOTER, 0, detail = true, affinity = shooter)
        // The square's corners reach 1.13 radii; its outline keeps the threat role.
        val threat = roles.threat
        val neutralOutline = neutral.countNear(threat, CENTER, 0f, RADIUS * 1.2f, minStrength = 0.5f)
        val aspectedOutline = aspected.countNear(threat, CENTER, 0f, RADIUS * 1.2f, minStrength = 0.5f)
        assertTrue(neutralOutline > 100, "the neutral outline draws ($neutralOutline px)")
        assertTrue(aspectedOutline >= neutralOutline * 0.9f, "the aspect keeps the threat outline ($neutralOutline, $aspectedOutline)")
        // The Ion contour sits inside the body; a neutral Shooter has none.
        assertEquals(0, neutral.countNear(ion, CENTER, 0f, RADIUS * 1.2f, minStrength = 0.5f))
        assertTrue(aspected.countNear(ion, CENTER, RADIUS * 0.6f, RADIUS, minStrength = 0.5f) > 40, "the Ion inner contour")
        // Without stacks, no aspect mark on the status ring's band or at the rim pip.
        val ring = RADIUS + 4f
        assertEquals(0, aspected.countNear(ion, CENTER, ring - 1.5f, ring + 1.5f, minStrength = 0.5f), "nothing in the ring band")
        val pip = androidx.compose.ui.geometry.Offset(CENTER.x + ring * 0.7071f, CENTER.y - ring * 0.7071f)
        assertEquals(0, aspected.countNear(ion, pip, 0f, 4f, minStrength = 0.5f), "nothing at the rim pip")
    }

    @Test
    fun theWeaknessTabShowsTheCounterElementAtTheLowerLeft() {
        // An Ion Shooter is weak to Entropy: its glyph on the tab, in the Entropy hue.
        val shooter = drawEnemyAt(EnemyType.SHOOTER, 0, detail = true, affinity = runAffinity(EnemyType.SHOOTER))
        assertTrue(shooter.countNear(entropy, TAB, 0f, 7f, minStrength = 0.5f) > 8, "the Entropy glyph on the tab")
        assertEquals(0, shooter.countNear(entropy, CENTER, 0f, RADIUS + 4f), "the weakness stays off the body and the ring")
        // A neutral Drifter has no tab, and no element mark anywhere near it (none never falls back to
        // Thermal). Strong pixels only: the threat outline's faintest edge pixels lean toward Thermal.
        val drifter = drawEnemyAt(EnemyType.DRIFTER, 0, detail = true, affinity = runAffinity(EnemyType.DRIFTER))
        assertEquals(0, drifter.countNear(entropy, TAB, 0f, 7f))
        StatusElement.entries.forEach { element ->
            assertEquals(0, drifter.countNear(roles.element(element.ordinal), CENTER, 0f, RADIUS + 25f, minStrength = 0.5f),
                "no $element mark on a neutral Drifter")
        }
    }

    @Test
    fun crowdedScreensShowTheWeaknessAsADot() {
        val affinity = runAffinity(EnemyType.SHOOTER)
        val alone = drawEnemyAt(EnemyType.SHOOTER, 0, detail = true, affinity = affinity)
        val crowded = drawEnemyAt(EnemyType.SHOOTER, 0, detail = false, affinity = affinity)
        assertTrue(crowded.countNear(entropy, TAB, 0f, 3f, minStrength = 0.5f) > 12, "a dot in the weakness hue where the tab was")
        // The glyph reaches past the dot; in a crowd nothing does.
        assertTrue(alone.countNear(entropy, TAB, 4f, 7f) > 0, "the glyph spans the tab")
        assertEquals(0, crowded.countNear(entropy, TAB, 4f, 7f), "the crowd draws only the dot")
        // An elite keeps its full tab in a crowd.
        val elite = runAffinity(EnemyType.ELITE, eliteAspect = StatusElement.ION)
        assertTrue(drawEnemyAt(EnemyType.ELITE, 0, detail = false, affinity = elite).countNear(entropy, TAB, 4f, 7f) > 0, "the elite keeps its glyph")
    }

    @Test
    fun reactionFlashesCarryBothElementsAndBreaksOne() {
        val reaction = ElementBurstProjection(0f, 0f, StatusElement.THERMAL.ordinal, StatusElement.CRYO.ordinal,
            ReactionId.THERMAL_SHOCK.ordinal, 90f, 0.4f, 0.7f, 1)
        val shock = render(emptyList(), VisualFxProjection.EMPTY.copy(elementBursts = immutableListOf(reaction)))
        assertTrue(shock.countNear(thermal, CENTER, 0f, 140f) > 40, "Thermal half")
        assertTrue(shock.countNear(cryo, CENTER, 0f, 140f) > 40, "Cryo half")
        val overload = render(emptyList(), VisualFxProjection.EMPTY.copy(elementBursts = immutableListOf(
            ElementBurstProjection(0f, 0f, StatusElement.ION.ordinal, -1, -1, 90f, 0.3f, 0.5f, 1))))
        assertTrue(overload.countNear(ion, CENTER, 0f, 140f) > 40, "Overload flashes in Ion")
        assertEquals(0, overload.countNear(thermal, CENTER, 0f, 140f), "a Break carries only its element")
    }

    @Test
    fun statusesDrawNoNumbersOrText() {
        val statuses = listOf(
            enemy(5 or (2 shl 4) or (1 shl 23)),
            enemy((3 shl 8) or (1 shl 20) or (1 shl 21) or (1 shl 22), id = 2, x = 120f),
        )
        render(statuses) // warm every cache
        val measured = KkTextStats.layoutsMeasured
        render(listOf(enemy(0)))
        val plain = KkTextStats.layoutsMeasured - measured
        val before = KkTextStats.layoutsMeasured
        render(statuses)
        assertEquals(plain, KkTextStats.layoutsMeasured - before, "statuses measure no text")
    }

    private fun enemy(bits: Int, id: Int = 1, x: Float = 0f, type: EnemyType = EnemyType.DRIFTER, affinity: Int = 0) =
        EnemyProjection(id, type, x, 0f, 0f, 0f, 50f, 100f, RADIUS, 1f, 0f, 0f, 0f, x, 0f, false, statusBits = bits, affinityBits = affinity)

    private fun model(enemies: List<EnemyProjection>): GameplayRenderModel = hudTestModel(WIDTH.toFloat(), HEIGHT.toFloat()).with(
        "enemies" to enemies.toImmutableList(),
        "coreX" to -2_000f, "coreY" to -2_000f, "cameraX" to 0f, "cameraY" to 0f, "pointerX" to 5f, "pointerY" to 5f,
        "elapsed" to 0f,
        "build" to BuildProjection(breakThresholds = immutableListOf(8, 6, 7, 6, 10)),
    )

    private fun render(enemies: List<EnemyProjection>, fx: VisualFxProjection = VisualFxProjection.EMPTY): PixelMap {
        val model = model(enemies)
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawRect(Kk.Ink)
            for (index in model.enemies.indices) drawEnemy(model, model.enemies[index], 0f, 0f, roles)
            drawElementBursts(model, fx, 0f, 0f, roles)
            drawReactionStamps(model, fx, 0f, 0f, measurer)
        }
        return bitmap.toPixelMap()
    }

    private fun drawEnemyAt(type: EnemyType, bits: Int, detail: Boolean, affinity: Int = 0): PixelMap {
        val model = model(listOf(enemy(bits, type = type, affinity = affinity)))
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawRect(Kk.Ink)
            drawEnemy(model, model.enemies.single(), 0f, 0f, roles, statusDetail = detail)
        }
        return bitmap.toPixelMap()
    }

    private fun <T> draw(model: GameplayRenderModel, block: androidx.compose.ui.graphics.drawscope.DrawScope.(GameplayRenderModel) -> T): T {
        var result: T? = null
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(WIDTH, HEIGHT)), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            result = block(model)
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
        AppLanguage.English, HudTestFonts.typography, roles)

    /** Pixels within [inner]..[outer] of [center] whose hue is [color]'s (not ink, not threat). */
    private fun PixelMap.countNear(
        color: Color,
        center: androidx.compose.ui.geometry.Offset,
        inner: Float,
        outer: Float,
        minStrength: Float = 0.12f,
    ): Int {
        var count = 0
        forEachIn(center, inner, outer) { pixel -> if (sameHue(pixel, color, minStrength)) count++ }
        return count
    }

    private fun PixelMap.alphaSum(color: Color, center: androidx.compose.ui.geometry.Offset, inner: Float, outer: Float): Float {
        var sum = 0f
        forEachIn(center, inner, outer) { pixel -> if (sameHue(pixel, color)) sum += pixel.red + pixel.green + pixel.blue }
        return sum
    }

    private fun PixelMap.distinctNear(color: Color, center: androidx.compose.ui.geometry.Offset, inner: Float, outer: Float): Int {
        val seen = HashSet<Int>()
        forEachIn(center, inner, outer) { pixel ->
            if (sameHue(pixel, color)) seen += ((pixel.red * 64).toInt() shl 12) or ((pixel.green * 64).toInt() shl 6) or (pixel.blue * 64).toInt()
        }
        return seen.size
    }

    private inline fun PixelMap.forEachIn(center: androidx.compose.ui.geometry.Offset, inner: Float, outer: Float, action: (Color) -> Unit) {
        val left = (center.x - outer).toInt().coerceAtLeast(0)
        val right = (center.x + outer).toInt().coerceAtMost(width - 1)
        val top = (center.y - outer).toInt().coerceAtLeast(0)
        val bottom = (center.y + outer).toInt().coerceAtMost(height - 1)
        for (y in top..bottom) for (x in left..right) {
            val d = hypot(x + 0.5f - center.x, y + 0.5f - center.y)
            if (d < inner || d > outer) continue
            action(this[x, y])
        }
    }

    /** A pixel blended from ink toward [color]: its channels keep [color]'s proportions. */
    private fun sameHue(pixel: Color, color: Color, minStrength: Float = 0.12f): Boolean {
        val ink = Kk.Ink
        val dr = pixel.red - ink.red
        val dg = pixel.green - ink.green
        val db = pixel.blue - ink.blue
        val tr = color.red - ink.red
        val tg = color.green - ink.green
        val tb = color.blue - ink.blue
        val strength = (dr * tr + dg * tg + db * tb) / (tr * tr + tg * tg + tb * tb)
        if (strength < minStrength) return false
        return abs(dr - tr * strength) < 0.06f && abs(dg - tg * strength) < 0.06f && abs(db - tb * strength) < 0.06f
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
        const val RADIUS = 30f
        val CENTER = androidx.compose.ui.geometry.Offset(200f, 150f)

        /** The weakness tab: 9 outside the status ring (radius + 4) at +135°, lower left. */
        val TAB = androidx.compose.ui.geometry.Offset(CENTER.x - (RADIUS + 13f) * 0.7071f, CENTER.y + (RADIUS + 13f) * 0.7071f)
    }
}
